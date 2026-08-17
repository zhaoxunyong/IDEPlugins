#!/usr/bin/env bash
# Export the ZeroGit Eclipse plugin as a p2 update site + installer zip.
#
# Requirements:
#   - An Eclipse/STS installation (plugins provide compile classpath and p2 publisher)
#   - A JDK (javac + jar)
#
# Usage:
#   ECLIPSE_HOME=/path/to/eclipse ./export-plugin.sh [version]
#   ECLIPSE_BIN=/path/to/eclipse.exe ./export-plugin.sh
#
# Optional env:
#   STS_JAVAC   /path/to/javac  (default: STS bundled JRE javac, then $JAVA_HOME/bin/javac, then PATH)
#   OUTPUT_DIR  output dir      (default: $ROOT_DIR/build/export)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_DIR="$ROOT_DIR/com.zerofinance.zerogit.eclipse"
FEATURE_DIR="$ROOT_DIR/com.zerofinance.zerogit.eclipse.feature"
SITE_SRC_DIR="$ROOT_DIR/com.zerofinance.zerogit.eclipse.updatesite"

# --- locate Eclipse ---------------------------------------------------------
ECLIPSE_BIN="${ECLIPSE_BIN:-}"
if [ -z "$ECLIPSE_BIN" ] && [ -n "${ECLIPSE_HOME:-}" ]; then
  if [ -x "$ECLIPSE_HOME/eclipse.exe" ]; then
    ECLIPSE_BIN="$ECLIPSE_HOME/eclipse.exe"
  elif [ -x "$ECLIPSE_HOME/eclipse" ]; then
    ECLIPSE_BIN="$ECLIPSE_HOME/eclipse"
  fi
fi
if [ -z "$ECLIPSE_BIN" ]; then
  ECLIPSE_BIN="$(command -v eclipse 2>/dev/null || true)"
fi
if [ -z "$ECLIPSE_BIN" ] || [ ! -x "$ECLIPSE_BIN" ]; then
  echo "Eclipse not found. Set ECLIPSE_HOME (Eclipse install dir) or ECLIPSE_BIN (eclipse/eclipse.exe)." >&2
  exit 69
fi
ECLIPSE_HOME="$(cd "$(dirname "$ECLIPSE_BIN")" && pwd)"

# --- locate javac / jar -----------------------------------------------------
if [ -z "${STS_JAVAC:-}" ]; then
  # STS 5.x 自带 full JRE(含 javac)，类版本与 STS 插件一致，优先使用
  # 注：不用 glob 匹配 */*/bin（MSYS 不展开），改用 find
  STS_JAVAC="$(find "$ECLIPSE_HOME/plugins" -maxdepth 4 -type f -name 'javac.exe' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "${STS_JAVAC:-}" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    STS_JAVAC="$JAVA_HOME/bin/javac"
  else
    STS_JAVAC="$(command -v javac 2>/dev/null || true)"
  fi
fi
if [ -z "${STS_JAVAC:-}" ] || [ ! -x "$STS_JAVAC" ]; then
  echo "javac not found. Set STS_JAVAC or JAVA_HOME." >&2
  exit 69
fi
JAR_BIN="$(cd "$(dirname "$STS_JAVAC")" && pwd)/jar"
JRE_BIN="$(cd "$(dirname "$STS_JAVAC")" && pwd)"
# 用与 javac 同目录的 JVM 启动 Eclipse launcher（避免 PATH 里旧 JDK 加载不了 launcher）
VM_ARGS=()
if [[ "$JRE_BIN" == "$ECLIPSE_HOME"/* ]]; then
  VM_ARGS=(-vm "$JRE_BIN")
fi

# --- version ----------------------------------------------------------------
TS="${1:-$(date +%Y%m%d%H%M)}"
MANIFEST_FILE="$PLUGIN_DIR/META-INF/MANIFEST.MF"
base_version="$(awk '/^Bundle-Version:/{print $2; exit}' "$MANIFEST_FILE")"
if [ -z "$base_version" ]; then
  echo "Bundle-Version not found in $MANIFEST_FILE" >&2
  exit 66
fi
if [[ "$base_version" == *.qualifier ]]; then
  VERSION="${base_version%.qualifier}.$TS"
else
  VERSION="$base_version"
fi

# --- stage dirs -------------------------------------------------------------
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/build/export}"
STAGE="$OUTPUT_DIR/.stage/$VERSION"
DIST="$OUTPUT_DIR/$VERSION"
SITE_DIR="$DIST/updatesite"
rm -rf "$STAGE" "$DIST"
mkdir -p "$STAGE/classes" "$STAGE/features" "$STAGE/plugins" "$SITE_DIR"

cleanup() {
  rm -rf "$STAGE"
}
trap cleanup EXIT

echo "Building zerofinance-git Eclipse plugin $VERSION ..."

# --- compile ----------------------------------------------------------------
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) CP_SEP=";" ;;
  *) CP_SEP=":" ;;
esac

CLASSES_DIR="$STAGE/classes"
mkdir -p "$CLASSES_DIR"

if command -v cygpath >/dev/null 2>&1; then
  to_win() { cygpath -m "$1"; }
else
  to_win() { printf '%s' "$1"; }
fi

# classpath 显式列出全部 jar，避免 javac 通配符被 MSYS 参数转换干扰
CP=""
shopt -s nullglob
for jar in "$PLUGIN_DIR"/lib/*.jar "$ECLIPSE_HOME"/plugins/*.jar; do
  CP="${CP}${CP_SEP}$(to_win "$jar")"
done
CP="${CP#?}"

# javac 参数写入 argfile（规避 Windows 命令行长度限制与参数转换）
ARGS_FILE="$STAGE/javac.args"
{
  echo "-encoding UTF-8"
  echo "-source 8"
  echo "-target 8"
  echo "-cp"
  echo "$CP"
  echo "-d"
  to_win "$CLASSES_DIR"
  find "$PLUGIN_DIR/src" -name '*.java' | while IFS= read -r src; do
    to_win "$src"
  done
} > "$ARGS_FILE"
"$STS_JAVAC" "@$ARGS_FILE"

# --- assemble plugin jar ----------------------------------------------------
PLUGIN_JAR="$STAGE/plugins/com.zerofinance.zerogit.eclipse_$VERSION.jar"
mkdir -p "$STAGE/manifest"
sed "s/^Bundle-Version: .*/Bundle-Version: $VERSION/" "$MANIFEST_FILE" > "$STAGE/manifest/MANIFEST.MF"
"$JAR_BIN" cfm "$PLUGIN_JAR" "$STAGE/manifest/MANIFEST.MF" \
  -C "$CLASSES_DIR" . \
  -C "$PLUGIN_DIR" plugin.xml \
  -C "$PLUGIN_DIR" lib
echo "  plugin  -> $(basename "$PLUGIN_JAR")"

# --- assemble feature jar ---------------------------------------------------
FEATURE_JAR="$STAGE/features/com.zerofinance.zerogit.eclipse.feature_$VERSION.jar"
mkdir -p "$STAGE/feature"
sed "s/$base_version/$VERSION/g" "$FEATURE_DIR/feature.xml" > "$STAGE/feature/feature.xml"
( cd "$STAGE/feature" && "$JAR_BIN" cfM "$FEATURE_JAR" feature.xml )
echo "  feature -> $(basename "$FEATURE_JAR")"

# --- publish p2 update site -------------------------------------------------
if command -v cygpath >/dev/null 2>&1; then
  SITE_PATH="$(cygpath -m "$SITE_DIR")"
else
  SITE_PATH="$SITE_DIR"
fi
FILE_URL="file:///$SITE_PATH"

"$ECLIPSE_BIN" -nosplash "${VM_ARGS[@]}" \
  -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
  -metadataRepository "$FILE_URL" \
  -artifactRepository "$FILE_URL" \
  -source "$STAGE" \
  -compress -publishArtifacts
echo "  update site published to $SITE_DIR"

# --- apply categories (best effort) -----------------------------------------
if [ -f "$SITE_SRC_DIR/site.xml" ]; then
  sed "s/$base_version/$VERSION/g" "$SITE_SRC_DIR/site.xml" > "$SITE_DIR/site.xml"
  if ! "$ECLIPSE_BIN" -nosplash "${VM_ARGS[@]}" \
      -application org.eclipse.equinox.p2.publisher.CategoryPublisher \
      -metadataRepository "$FILE_URL" \
      -categoryDefinition "$FILE_URL/site.xml" \
      -categoryQualifier >/dev/null 2>&1; then
    echo "  warning: category publishing skipped (site still installable)" >&2
  fi
fi

# --- package installer zip --------------------------------------------------
ZIP_FILE="$DIST/zerofinance-git-eclipse-$VERSION-updatesite.zip"
( cd "$SITE_DIR" && "$JAR_BIN" cfM "$ZIP_FILE" . )
echo "  zip     -> $ZIP_FILE"

echo
echo "Done. Install via:"
echo "  1) Update site : Help > Install New Software > Add... > Local... > $SITE_DIR"
echo "  2) Or unzip $ZIP_FILE and add the extracted folder as a local update site."
