#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 准备 Android 签名密钥（release 构建共用）
#
# 供 .github/workflows/release.yml 与 release-preview.yml 调用，避免两份拷贝
# 走样——此前正是因为两处逻辑不一致，出现「日志说用了正式密钥、实际是 debug」。
#
# 输入（由 workflow 的 env 段注入，缺失时为空串）：
#   RELEASE_KEYSTORE_B64  base64 编码的 keystore
#   STORE_PW              密钥库口令
#   KEY_ALIAS_V           别名
#   KEY_PW                私钥口令（PKCS12 下必须与 STORE_PW 相同）
#
# 输出：
#   stdout：成功时输出 4 行 KEY=value，由调用方追加到 $GITHUB_ENV
#   stderr：人类可读的状态说明
#   退出码：始终为 0（缺失时回退 debug 签名），是否放行交给后续的
#           「校验签名信息」步骤判断——那里检查 APK 里证书的 Owner。
#
# 用法：
#   bash .github/scripts/prepare-signing.sh >> "$GITHUB_ENV"
# ---------------------------------------------------------------------------
set -euo pipefail

log() { echo "$*" >&2; }

MISSING=""
[ -n "${RELEASE_KEYSTORE_B64:-}" ] || MISSING="$MISSING RELEASE_KEYSTORE_BASE64"
[ -n "${STORE_PW:-}" ]            || MISSING="$MISSING KEYSTORE_PASSWORD"
[ -n "${KEY_ALIAS_V:-}" ]         || MISSING="$MISSING KEY_ALIAS"
[ -n "${KEY_PW:-}" ]              || MISSING="$MISSING KEY_PASSWORD"

if [ -n "$MISSING" ]; then
  log "⚠️  缺少 Secret:$MISSING"
  log "   → 将回退使用仓库内的 debug.keystore（仅供自用测试，不建议对外分发）"
  log "   补齐路径：Settings → Secrets and variables → Actions → Repository secrets"
  exit 0
fi

# 解码失败要显式报错：静默写个空文件会让 Gradle 侧回退 debug，
# 而本脚本已打印「✅ 使用正式密钥」，排查时极易被误导。
KS_DIR="${RUNNER_TEMP:-/tmp}"
mkdir -p "$KS_DIR"          # RUNNER_TEMP 一定存在，但本地调试时兜个底
KS_FILE="$KS_DIR/lifelog-release.keystore"
if ! echo "$RELEASE_KEYSTORE_B64" | base64 -d > "$KS_FILE" 2>/dev/null; then
  log "❌ keystore base64 解码失败：RELEASE_KEYSTORE_BASE64 内容不是合法的 base64"
  log "   → 将回退 debug 签名。请重新生成：base64 -w 0 lifelog-release.keystore"
  exit 0
fi
if [ ! -s "$KS_FILE" ]; then
  log "❌ 解码后 keystore 为空文件"
  exit 0
fi

log "✅ 使用 Secrets 中的正式签名密钥"
log "   别名：$KEY_ALIAS_V"
log "   密钥库：$KS_FILE（$(du -h "$KS_FILE" | cut -f1)）"

# PKCS12 只有一把锁：私钥即用 storepass 加密，keytool 会忽略 -keypass，
# 所以 KEY_PASSWORD 必须与 KEYSTORE_PASSWORD 一致。不一致时提前警告，
# 免得到签名阶段才报 Cannot recover key。
if [ "$STORE_PW" != "$KEY_PW" ]; then
  log "⚠️  KEY_PASSWORD 与 KEYSTORE_PASSWORD 不一致"
  log "   PKCS12 私钥即用 storepass 加密，两者必须相同，"
  log "   否则签名阶段会失败：Cannot recover key / keystore password was incorrect"
fi

# 只有这 4 行走 stdout，供调用方追加进 $GITHUB_ENV
echo "LIFELOG_KEYSTORE_FILE=$KS_FILE"
echo "LIFELOG_STORE_PASSWORD=$STORE_PW"
echo "LIFELOG_KEY_ALIAS=$KEY_ALIAS_V"
echo "LIFELOG_KEY_PASSWORD=$KEY_PW"
