#!/usr/bin/env bash
# AC5-05 密钥扫描门禁：git 跟踪内容与已构建产物中不得出现
# API Key、Authorization Header、明文密码样例、设备私钥 PEM 或 ELPAIR1 挑战码明文。
# 退出码：0=干净，1=命中，2=运行环境异常（区别于命中，供自测纪律使用）。
# 豁免：行尾注释含 secret-scan-allowlist 的行不判命中；本脚本与自测文件自身豁免。
# 环境开关（仅供自测）：SECRET_SCAN_SKIP_TRACKED=1 跳过跟踪内容与产物扫描。
set -eu
cd "$(git rev-parse --show-toplevel)"

PATTERN='Authorization:[[:space:]]*(Bearer|Basic)|[?&]api_?key=[A-Za-z0-9._-]{16,}|sk-[A-Za-z0-9]{20,}|-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----|ELPAIR1\|[A-Za-z0-9_-]{16,}'
status=0

if [ "${SECRET_SCAN_SKIP_TRACKED:-}" != "1" ]; then
  # 1) git 跟踪文件
  if hits=$(git grep -nIE "$PATTERN" -- ':!scripts/secret-scan*' | grep -v 'secret-scan-allowlist'); then
    echo "$hits"
    echo "SECRET SCAN HIT (tracked files)"; status=1
  fi
  # 2) 构建产物（存在才扫；存在但无 unzip 视为环境异常，不得静默放行）
  artifacts=$(ls app/build/outputs/apk/debug/app-debug.apk app/build/outputs/bundle/release/*.aab 2>/dev/null || true)
  if [ -n "$artifacts" ] && ! command -v unzip >/dev/null 2>&1; then
    echo "ENV ERROR: artifacts present but unzip missing"; exit 2
  fi
  for apk in $artifacts; do
    d=$(mktemp -d)
    unzip -qq "$apk" -d "$d" 2>/dev/null || { echo "ENV ERROR: cannot unzip $apk"; rm -rf "$d"; exit 2; }
    if hits=$(grep -rIlE "$PATTERN" "$d" | grep -v 'secret-scan-allowlist'); then
      echo "$hits"
      echo "SECRET SCAN HIT ($apk)"; status=1
    fi
    rm -rf "$d"
  done
fi

# 3) 自测注入路径（供红/绿测试使用）
if [ -n "${SECRET_SCAN_EXTRA_PATHS:-}" ]; then
  set +e
  out=$(grep -nHE "$PATTERN" $SECRET_SCAN_EXTRA_PATHS | grep -v 'secret-scan-allowlist')
  rc=$?
  set -e
  if [ "$rc" -eq 0 ]; then
    echo "$out"
    echo "SECRET SCAN HIT (extra paths)"; status=1
  elif [ "$rc" -gt 1 ]; then
    echo "ENV ERROR: grep failed on ${SECRET_SCAN_EXTRA_PATHS}"; exit 2
  fi
fi

exit $status
