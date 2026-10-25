#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
rm -rf tools/scanner_test/out; mkdir -p tools/scanner_test/out
kotlinc app/src/main/java/com/vextech/smart_edit/feature/scanner/domain/ScannerModels.kt tools/scanner_test/ScannerPolicyMain.kt -d tools/scanner_test/out
kotlin -classpath tools/scanner_test/out ScannerPolicyMainKt
for f in app/src/main/res/values*/strings.xml; do for k in scanner_title scanner_new_session scanner_export_pdf scanner_error_export scanner_edit_page; do grep -q "name=\"$k\"" "$f" || { echo "Missing $k in $f"; exit 1; }; done; done
grep -q 'ScanSessionEntity::class' app/src/main/java/com/vextech/smart_edit/core/database/SmartEditDatabase.kt
grep -q 'ScanPageEntity::class' app/src/main/java/com/vextech/smart_edit/core/database/SmartEditDatabase.kt
grep -q 'bindScannerRepository' app/src/main/java/com/vextech/smart_edit/core/di/AppModule.kt
grep -q 'PerspectiveCropper' app/src/main/java/com/vextech/smart_edit/feature/scanner/data/ScannerRepository.kt
grep -q 'scanner.addPages(sessionId' app/src/main/java/com/vextech/smart_edit/feature/scanner/LiveCameraViewModel.kt
! grep -R -E 'TODO|FIXME|NotImplementedError' app/src/main/java/com/vextech/smart_edit/feature/scanner
echo 'Scanner structural verification: PASS'
