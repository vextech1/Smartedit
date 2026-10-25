#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/onboarding_test/out"
rm -rf "$OUT" && mkdir -p "$OUT"
kotlinc "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/onboarding/OnboardingScatter.kt" \
  "$ROOT/tools/onboarding_test/OnboardingScatterMain.kt" -include-runtime -d "$OUT/onboarding.jar" 2>&1 | grep -v "^warning:" || true
java -jar "$OUT/onboarding.jar"
python3 "$ROOT/tools/static_checks.py"
echo "ONBOARDING_FEATURE_VERIFIER_PASS"
