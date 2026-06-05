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
TEST_PLUGIN_DIR="$ROOT_DIR/$PLUGIN_NAME"
MAIN_PLUGIN_DIR="$ROOT_DIR/${PLUGIN_NAME%.tests}"
ECLIPSE_BIN="${ECLIPSE_BIN:-$(command -v eclipse || true)}"
PYTHON_BIN="${PYTHON_BIN:-$(command -v python3 || true)}"

if [ -z "$ECLIPSE_BIN" ]; then
  echo "Eclipse executable not found. Set ECLIPSE_BIN to your Eclipse launcher path." >&2
  exit 69
fi

ECLIPSE_HOME="$(cd "$(dirname "$ECLIPSE_BIN")" && pwd)"
STS_JAVAC="${STS_JAVAC:-$(find "$ECLIPSE_HOME/plugins" -path '*org.eclipse.justj.openjdk.hotspot.jre.full*/jre/bin/javac' | head -n 1 || true)}"

if [ -z "$PYTHON_BIN" ]; then
  echo "python3 executable not found. Set PYTHON_BIN to your Python 3 launcher path." >&2
  exit 69
fi

if [ ! -d "$TEST_PLUGIN_DIR" ]; then
  echo "Test plugin directory not found: $TEST_PLUGIN_DIR" >&2
  exit 66
fi

if [ ! -d "$MAIN_PLUGIN_DIR" ]; then
  echo "Main plugin directory not found: $MAIN_PLUGIN_DIR" >&2
  exit 66
fi

if [ -z "$STS_JAVAC" ] || [ ! -x "$STS_JAVAC" ]; then
  echo "STS javac not found. Set STS_JAVAC to the JDK used by your Spring Tools installation." >&2
  exit 69
fi

TEST_SOURCE_FILE="$TEST_PLUGIN_DIR/src/${CLASS_NAME//./\/}.java"
if [ ! -f "$TEST_SOURCE_FILE" ]; then
  echo "Test source file not found: $TEST_SOURCE_FILE" >&2
  exit 66
fi

rm -rf "$WORKSPACE_DIR"
mkdir -p "$WORKSPACE_DIR"
mkdir -p "$MAIN_PLUGIN_DIR/bin" "$TEST_PLUGIN_DIR/bin"

READY_FILE="$(mktemp)"
LOG_FILE="$(mktemp)"
DROPINS_DIR="$(mktemp -d)"

cleanup() {
  if [ -n "${LISTENER_PID:-}" ] && kill -0 "$LISTENER_PID" 2>/dev/null; then
    kill "$LISTENER_PID" 2>/dev/null || true
    wait "$LISTENER_PID" 2>/dev/null || true
  fi
  rm -f "$READY_FILE" "$LOG_FILE"
  rm -rf "$DROPINS_DIR"
}
trap cleanup EXIT

"$STS_JAVAC" \
  -cp "$MAIN_PLUGIN_DIR/lib/*:$ECLIPSE_HOME/plugins/*" \
  -d "$MAIN_PLUGIN_DIR/bin" \
  $(find "$MAIN_PLUGIN_DIR/src" -name '*.java')

"$STS_JAVAC" \
  -cp "$MAIN_PLUGIN_DIR/bin:$MAIN_PLUGIN_DIR/lib/*:$ECLIPSE_HOME/plugins/*" \
  -sourcepath "$TEST_PLUGIN_DIR/src" \
  -d "$TEST_PLUGIN_DIR/bin" \
  "$TEST_SOURCE_FILE"

mkdir -p "$DROPINS_DIR/eclipse/plugins"
ln -s "$MAIN_PLUGIN_DIR" "$DROPINS_DIR/eclipse/plugins/$(basename "$MAIN_PLUGIN_DIR")"
ln -s "$TEST_PLUGIN_DIR" "$DROPINS_DIR/eclipse/plugins/$(basename "$TEST_PLUGIN_DIR")"

"$PYTHON_BIN" - "$READY_FILE" "$LOG_FILE" <<'PY' &
import socket
import sys

ready_file = sys.argv[1]
log_file = sys.argv[2]

exit_code = 0
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(("127.0.0.1", 0))
server.listen(1)
server.settimeout(120)
port = server.getsockname()[1]

with open(ready_file, "w", encoding="utf-8") as ready:
    ready.write(str(port))

try:
    conn, _ = server.accept()
    conn.settimeout(120)
    with conn, conn.makefile("r", encoding="utf-8", errors="replace") as reader, \
            open(log_file, "w", encoding="utf-8") as log:
        saw_run_end = False
        for line in reader:
            log.write(line)
            if line.startswith("%FAILED") or line.startswith("%ERROR"):
                exit_code = 1
            if line.startswith("%RUNTIME") or line.startswith("%TSTSTP"):
                saw_run_end = True
        if not saw_run_end and exit_code == 0:
            exit_code = 2
except Exception as exc:
    with open(log_file, "a", encoding="utf-8") as log:
        log.write(f"listener error: {exc}\n")
    exit_code = 2
finally:
    server.close()

sys.exit(exit_code)
PY
LISTENER_PID=$!

for _ in $(seq 1 100); do
  if [ -s "$READY_FILE" ]; then
    break
  fi
  sleep 0.1
done

if [ ! -s "$READY_FILE" ]; then
  echo "Failed to start PDE JUnit listener." >&2
  exit 70
fi

PORT="$(cat "$READY_FILE")"

set +e
"$ECLIPSE_BIN" \
  -nosplash \
  -consoleLog \
  -clean \
  -dev bin/ \
  -application org.eclipse.pde.junit.runtime.coretestapplication \
  -data "$WORKSPACE_DIR" \
  -testpluginname "$PLUGIN_NAME" \
  -loaderpluginname org.eclipse.jdt.junit4.runtime \
  -testloaderclass org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader \
  -classname "$CLASS_NAME" \
  -host 127.0.0.1 \
  -port "$PORT" \
  -vmargs \
  -Dorg.eclipse.equinox.p2.reconciler.dropins.directory="$DROPINS_DIR" \
  -Dosgi.checkConfiguration=true
ECLIPSE_EXIT=$?

wait "$LISTENER_PID"
LISTENER_EXIT=$?
set -e

cat "$LOG_FILE"

if [ "$ECLIPSE_EXIT" -ne 0 ]; then
  exit "$ECLIPSE_EXIT"
fi

exit "$LISTENER_EXIT"
