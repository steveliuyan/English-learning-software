#!/usr/bin/env bash
# apk-secret-scan.sh 的自测（AC5-05 口径）：先红后绿纪律——扫描器对植入密钥的假包必须 exit 1，
# 对干净假包必须 exit 0。本机 Git Bash 无 zip 命令，fixtures 用 python zipfile 造（实测可用）。
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCANNER="$ROOT/scripts/apk-secret-scan.sh"
TMP="$(mktemp -d)"
export TMP
trap 'rm -rf "$TMP"' EXIT

fail() { echo "SELFTEST FAIL: $1"; exit 1; }
[ -f "$SCANNER" ] || { echo "apk-secret-scan.sh 不存在（RED 的正常状态）"; exit 1; }

python - <<'PYEOF' || fail "python zipfile 造包失败"
import zipfile, os
tmp = os.environ["TMP"]
with zipfile.ZipFile(os.path.join(tmp, "bad.apk"), "w") as z:
    z.writestr("assets/leak.txt", "token sk-abcdefghijklmnopqrstuvwx and ELPAIR1|Zm9vYmFyMTIzNDU2Nzg5MA")
    z.writestr("payload.keystore", "\0" * 64)
    z.writestr("META-INF/MANIFEST.MF", "ManifestVersion: 2\n")
with zipfile.ZipFile(os.path.join(tmp, "ksonly.apk"), "w") as z:
    z.writestr("payload.jks", "\0" * 64)
with zipfile.ZipFile(os.path.join(tmp, "env.apk"), "w") as z:
    z.writestr("assets/.env", "API_KEY=sk-abcdefghijklmnopqrstuvwx\n")
    z.writestr("classes.dex", "not-real-dex")
with zipfile.ZipFile(os.path.join(tmp, "authhdr.apk"), "w") as z:
    z.writestr("assets/config.txt", "Authorization: Bearer abcdefghijklmnop")
with zipfile.ZipFile(os.path.join(tmp, "clean.apk"), "w") as z:
    z.writestr("assets/third-party-notices.md", "license ledger line: 名称: Demo\n")
    z.writestr("META-INF/MANIFEST.MF", "ManifestVersion: 2\n")
print("fixtures built")
PYEOF

"$SCANNER" "$TMP/bad.apk" && fail "坏包（sk-/ELPAIR1/keystore）竟被判干净"
"$SCANNER" "$TMP/ksonly.apk" && fail "含 .jks 的包竟被判干净"
"$SCANNER" "$TMP/env.apk" && fail "assets 内 .env 竟逃过文件名单检查"
"$SCANNER" "$TMP/authhdr.apk" && fail "Authorization: 明文头样本竟不被内容级命中"
"$SCANNER" "$TMP/clean.apk" || fail "干净包被误报（假阳性会挡发布）"

# 未签名单文件命名只是提示，不改变 exit 0
mv "$TMP/clean.apk" "$TMP/app-release-unsigned.apk"
"$SCANNER" "$TMP/app-release-unsigned.apk" || fail "未签名命名不应 fail 门禁"

# 用法错误必须 exit 2
"$SCANNER" "$TMP/nonexistent.apk" 2>/dev/null; rc=$?
[ "$rc" = 2 ] || fail "不存在的包应 exit 2，实际 $rc"

echo "SELFTEST OK"
exit 0
