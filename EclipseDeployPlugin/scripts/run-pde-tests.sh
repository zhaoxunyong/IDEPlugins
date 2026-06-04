#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <test-plugin-name> <test-class>" >&2
  exit 64
fi

PLUGIN_NAME="$1"
CLASS_NAME="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$ROOT_DIR/.pde-test-workspace"
ECLIPSE_BIN="${ECLIPSE_BIN:-$(command -v eclipse || true)}"

if [ -z "$ECLIPSE_BIN" ]; then
  echo "Eclipse executable not found. Set ECLIPSE_BIN to your Eclipse launcher path." >&2
  exit 69
fi

rm -rf "$WORKSPACE_DIR"
mkdir -p "$WORKSPACE_DIR"

"$ECLIPSE_BIN" \
  -nosplash \
  -consoleLog \
  -application org.eclipse.pde.junit.runtime.coretestapplication \
  -data "$WORKSPACE_DIR" \
  -testpluginname "$PLUGIN_NAME" \
  -classname "$CLASS_NAME"
