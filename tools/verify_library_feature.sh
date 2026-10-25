#!/usr/bin/env bash
# Phase 2 verification: pure library logic (compiled and run) plus structure and string checks.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/library_test/out"
SRC="$ROOT/app/src/main/java/com/vextech/smart_edit"
rm -rf "$OUT" && mkdir -p "$OUT"
kotlinc \
  "$SRC/core/model/Models.kt" \
  "$SRC"/core/library/*.kt \
  "$ROOT/tools/library_test/LibraryPolicyMain.kt" \
  -include-runtime -d "$OUT/library-policy.jar" 2>&1 | grep -v "^warning:" || true
java -jar "$OUT/library-policy.jar"
python3 "$ROOT/tools/static_checks.py"
echo "LIBRARY_FEATURE_VERIFIER_PASS"
