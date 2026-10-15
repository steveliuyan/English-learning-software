#!/usr/bin/env bash
# AC5-05 发布产物扫描（F5-01，决策 2026-10-15 §4）：APK 文件名单与 assets 内容不得含密钥材料。
# exit 0=干净；1=命中（发布阻断）；2=用法/环境错误（不当作绿）。
# 自测：scripts/apk-secret-scan-selftest.sh（假包植入必红、净包必绿）。
set -u
APK="${1:-}"
[ -n "$APK" ] || { echo "usage: apk-secret-scan.sh <apk>"; exit 2; }
[ -f "$APK" ] || { echo "APK 不存在: $APK"; exit 2; }
command -v unzip >/dev/null 2>&1 || { echo "需要 unzip 命令"; exit 2; }
export LC_ALL=C

hits=0

# 1) 禁入文件名：密钥库/私钥/环境文件绝不进包
listing="$(unzip -l "$APK" | awk '{print $NF}')"
badfiles="$(printf '%s\n' "$listing" | grep -iE '\.(jks|keystore|p12|pfx|env)$|(^|/)local\.properties$' || true)"
if [ -n "$badfiles" ]; then
    echo "命中禁入文件名:"
    printf '%s\n' "$badfiles"
    hits=1
fi

# 2) assets 内容密钥模式：sk- 密钥、已消费的 ELPAIR1 挑战码格式、Authorization 明文头样本
#    （ELPAIR1 模式沿用 SP-A secret-scan 的收紧口径，只咬挑战码格式不咬文档提及）
assets_content="$(unzip -p "$APK" 'assets/*' 2>/dev/null || true)"
if printf '%s' "$assets_content" | grep -qaE 'sk-[A-Za-z0-9]{20,}|ELPAIR1\|[A-Za-z0-9_=-]{16,}|Authorization:[[:space:]]*(Bearer|key)'; then
    echo "命中 assets 内容密钥模式"
    hits=1
fi

# 3) 未签名产物命名只是提示，不 fail 门禁（决策 §3：验签 TODO-USER，但不得谎称已验签）
case "$APK" in
    *unsigned*) echo "WARN: 未签名产物——验签待发布 keystore（TODO-USER）" ;;
esac

if [ "$hits" = 0 ]; then
    echo "apk-secret-scan: CLEAN $APK"
    exit 0
fi
exit 1
