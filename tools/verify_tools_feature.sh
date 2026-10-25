#!/usr/bin/env bash
# Phase 3 verification: file actions, compression, OCR and image-edit rules (compiled and run) plus structure checks.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/tools_test/out"
SRC="$ROOT/app/src/main/java/com/vextech/smart_edit/core/library"
rm -rf "$OUT" && mkdir -p "$OUT"
kotlinc "$SRC/FileActionPolicies.kt" "$SRC/CompressionPolicies.kt" "$SRC/OcrPolicies.kt" "$SRC/ImageEditModel.kt" \
  "$ROOT/tools/tools_test/Phase3PolicyMain.kt" -include-runtime -d "$OUT/tools.jar" 2>&1 | grep -v "^warning:" || true
java -jar "$OUT/tools.jar"
python3 "$ROOT/tools/static_checks.py"
echo "TOOLS_FEATURE_VERIFIER_PASS"
