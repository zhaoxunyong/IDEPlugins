#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_DIR="$ROOT_DIR/com.zerofinance.zerogit.eclipse"
MANIFEST_FILE="$PLUGIN_DIR/META-INF/MANIFEST.MF"
SRC_DIR="$PLUGIN_DIR/src"

if [ ! -f "$MANIFEST_FILE" ]; then
  echo "Manifest not found: $MANIFEST_FILE" >&2
  exit 1
fi

if [ ! -d "$SRC_DIR" ]; then
  echo "Source directory not found: $SRC_DIR" >&2
  exit 1
fi

export_packages="$(
  awk '
    /^Export-Package:/ {
      in_export = 1
      sub(/^Export-Package:[[:space:]]*/, "", $0)
      print
      next
    }
    in_export && /^[[:space:]]/ {
      sub(/^[[:space:]]*/, "", $0)
      print
      next
    }
    in_export {
      exit
    }
  ' "$MANIFEST_FILE"
)"

if [ -z "$export_packages" ]; then
  echo "Export-Package section is empty or missing" >&2
  exit 1
fi

if printf '%s\n' "$export_packages" | grep -q '\\'; then
  echo "Export-Package contains invalid backslash continuations" >&2
  printf '%s\n' "$export_packages" >&2
  exit 1
fi

printf '%s\n' "$export_packages" | tr ',' '\n' | while IFS= read -r package_name; do
  package_name="$(printf '%s' "$package_name" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"

  if [ -z "$package_name" ]; then
    continue
  fi

  package_path="$SRC_DIR/${package_name//./\/}"
  if [ ! -d "$package_path" ]; then
    echo "Exported package directory is missing: $package_name ($package_path)" >&2
    exit 1
  fi
done

echo "Export-Package section is valid."
