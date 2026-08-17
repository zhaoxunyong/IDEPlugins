#!/usr/bin/env bash
# 免输 ECLIPSE_HOME 的导出包装脚本：固定使用本机 STS 5.1.1。
# 用法: ./scripts/export-sts.sh [version]   (等价于 ECLIPSE_HOME=/d/Developer/sts-5.1.1.RELEASE ./scripts/export-plugin.sh [version])
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec env ECLIPSE_HOME="${ECLIPSE_HOME:-/d/Developer/sts-5.1.1.RELEASE}" "$SCRIPT_DIR/export-plugin.sh" "$@"
