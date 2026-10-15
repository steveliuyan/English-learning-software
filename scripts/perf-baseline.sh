#!/usr/bin/env bash
# SP-B 性能基线脚本：冷启动（am start -W，5 轮取中位数）+ 五项 instrument 计时（PERF| 行）。
# 用法：scripts/perf-baseline.sh [adb-serial]
#   不带参数 = 当前唯一连接设备；多台时用 adb -s。
# 输出：stdout 上的 markdown 片段（粘贴/重定向进 docs/verification/perf-baseline-2026-10.md）。
# 前提：app 与 androidTest APK 已 adb install -r（见 android-repo-session-handoff §3）。
set -euo pipefail

PKG="com.example.englishlearning"
ACTIVITY="${PKG}/.MainActivity"
TEST_CLASS="com.example.englishlearning.perf.PerfSixMetricsInstrumentedTest"
TEST_APP="${PKG}.test/androidx.test.runner.AndroidJUnitRunner"
RUNS=5

ADB=(adb)
if [ $# -ge 1 ]; then
  ADB=(adb -s "$1")
fi

"${ADB[@]}" get-state >/dev/null || { echo "设备不可达：${ADB[*]}" >&2; exit 1; }

median() {
  # stdin: 每行一个整数
  sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}'
}

cold_times=()
for _ in $(seq 1 "$RUNS"); do
  "${ADB[@]}" shell am force-stop "$PKG"
  sleep 2
  total=$("${ADB[@]}" shell am start -W -n "$ACTIVITY" | awk -F': ' '/^TotalTime/{print $2}')
  [ -n "$total" ] || { echo "冷启动计时失败：am start 无 TotalTime" >&2; exit 1; }
  cold_times+=("$total")
done
"${ADB[@]}" shell am force-stop "$PKG"

cold_median=$(printf '%s\n' "${cold_times[@]}" | median)

# instrument 的 println 走 stdout；顺带把 logcat 的 PERF 行也扫一遍兜底。
"${ADB[@]}" logcat -c || true
perf_lines=$("${ADB[@]}" shell am instrument -w -e class "$TEST_CLASS" "$TEST_APP" | grep -E '^PERF\|' || true)
if [ -z "$perf_lines" ]; then
  perf_lines=$("${ADB[@]}" logcat -d -s PERF:I | sed -n 's/.*PERF|/PERF|/p' | grep -E '^PERF\|' || true)
fi
[ -n "$perf_lines" ] || { echo "instrument 未输出 PERF| 行——检查测试是否实跑" >&2; exit 1; }

device_model=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
android_version=$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')
api_level=$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')

echo "## perf-baseline ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
echo
echo "- 设备：${device_model} / Android ${android_version} (API ${api_level}) / serial ${1:-default}"
echo "- 口径：冷启动为 am start -W TotalTime 中位数；其余为 instrument 内 5 轮中位数（首次冷启动含首启初始化，单独注明）"
echo
echo "| metric | medianMillis | runs |"
echo "|---|---|---|"
echo "| cold_start | ${cold_median} | ${RUNS} |"
echo "$perf_lines" | while IFS='|' read -r _tag metric median runs; do
  [ -n "$median" ] || continue
  echo "| ${metric} | ${median} | ${runs} |"
done
