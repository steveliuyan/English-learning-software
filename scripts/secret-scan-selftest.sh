#!/usr/bin/env bash
# secret-scan 自测（AC5-01/AC5-05）：植入样例必须被拦，干净与豁免样例必须放行。
# 判定纪律：扫描器 0=干净、1=命中；其他退出码一律视为自测失败，防止「脚本缺失假通过」。
# 夹具断言在 SECRET_SCAN_SKIP_TRACKED=1 隔离模式下运行，末段对全仓做真扫。
set -eu
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

SCAN="$(dirname "$0")/secret-scan.sh"
if [ ! -f "$SCAN" ]; then
  echo "FAIL: $SCAN does not exist"; exit 2
fi

run_scan() { # $1=file -> sets RC
  set +e
  SECRET_SCAN_SKIP_TRACKED=1 SECRET_SCAN_EXTRA_PATHS="$1" bash "$SCAN" >/dev/null 2>&1
  RC=$?
  set -e
}

printf 'Authorization: Bearer abcdefghijklmnop\n' > "$TMP/dirty-auth.txt"
printf 'sk-abcdefghijklmnopqrstuvwxyz0123456789\n' > "$TMP/dirty-skkey.txt"
printf 'ELPAIR1|c3Nzc3Nzc3Nzc3Nzc3Nz|bm9uY2V2YWx1ZTE2Y2hhcnM=|c2VjcmV0cGF5bG9hZDE2Y2hhcnM=|1700000000|1700001800\n' > "$TMP/dirty-challenge.txt"
printf 'ELPAIR1|b64url(publicKeySpki)|b64url(nonce)|b64url(secret)|issuedAt|expiresAt\n' > "$TMP/format-doc.txt"
printf -- '-----BEGIN PRIVATE KEY-----\nMIIBVAIBADAN\n' > "$TMP/dirty-pkey.txt"
printf 'Authorization: Bearer abcdefghijklmnop // secret-scan-allowlist\n' > "$TMP/allowlisted.txt"
printf 'normal line\nnothing to see\n' > "$TMP/clean.txt"

expect_hit() {
  run_scan "$1"
  if [ "$RC" -eq 1 ]; then
    echo "PASS: catches $2"
  else
    echo "FAIL: $2 not caught (exit=$RC)"; exit 1
  fi
}

expect_clean() {
  run_scan "$1"
  if [ "$RC" -eq 0 ]; then
    echo "PASS: $2 passes"
  else
    echo "FAIL: $2 rejected (exit=$RC)"; exit 1
  fi
}

expect_hit "$TMP/dirty-auth.txt" "Authorization header"
expect_hit "$TMP/dirty-skkey.txt" "sk- API key"
expect_hit "$TMP/dirty-challenge.txt" "ELPAIR1 challenge code"
expect_clean "$TMP/format-doc.txt" "ELPAIR1 format documentation line"
expect_hit "$TMP/dirty-pkey.txt" "private key PEM"
expect_clean "$TMP/allowlisted.txt" "allowlisted line"
expect_clean "$TMP/clean.txt" "clean file"

# 全仓真扫：门禁语义要求跟踪内容零命中（构建产物存在才扫）。
set +e
bash "$SCAN" >/dev/null 2>&1
RC=$?
set -e
if [ "$RC" -eq 0 ]; then
  echo "PASS: repo scan clean (exit=0)"
else
  echo "FAIL: repo scan hit or crashed (exit=$RC)"; bash "$SCAN" | tail -20; exit 1
fi
