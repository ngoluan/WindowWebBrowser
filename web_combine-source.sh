#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="${1:-$SCRIPT_DIR}"
OUTPUT_DIR="${2:-$HOME/Downloads}"
OUTPUT_FILE="$OUTPUT_DIR/windowweb_combined-source.txt"

echo "Combining source from: $SRC_DIR"
echo "Output: $OUTPUT_FILE"

rm -f "$OUTPUT_FILE"
touch "$OUTPUT_FILE"

append() {
    local file="$1"
    local rel="${file#$SRC_DIR/}"
    echo "" >> "$OUTPUT_FILE"
    printf '%*s\n' 80 '' | tr ' ' '=' >> "$OUTPUT_FILE"
    echo "// FILE: $rel" >> "$OUTPUT_FILE"
    printf '%*s\n' 80 '' | tr ' ' '=' >> "$OUTPUT_FILE"
    cat "$file" >> "$OUTPUT_FILE"
}

# Kotlin source files
find "$SRC_DIR/app/src/main/java" -type f -name '*.kt' | sort | while read -r f; do append "$f"; done

# Android resources
find "$SRC_DIR/app/src/main/res" -type f \( -name '*.xml' -o -name '*.kt' \) | sort | while read -r f; do append "$f"; done

# Manifest
[ -f "$SRC_DIR/app/src/main/AndroidManifest.xml" ] && append "$SRC_DIR/app/src/main/AndroidManifest.xml"

# Build files
[ -f "$SRC_DIR/build.gradle.kts" ] && append "$SRC_DIR/build.gradle.kts"
[ -f "$SRC_DIR/app/build.gradle.kts" ] && append "$SRC_DIR/app/build.gradle.kts"
[ -f "$SRC_DIR/settings.gradle.kts" ] && append "$SRC_DIR/settings.gradle.kts"
[ -f "$SRC_DIR/gradle.properties" ] && append "$SRC_DIR/gradle.properties"

line_count=$(wc -l < "$OUTPUT_FILE")
size=$(du -h "$OUTPUT_FILE" | cut -f1)
echo "Done — $line_count lines, $size written to $OUTPUT_FILE"
