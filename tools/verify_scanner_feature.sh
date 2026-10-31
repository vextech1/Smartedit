#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
rm -rf tools/scanner_test/out; mkdir -p tools/scanner_test/out
kotlinc app/src/main/java/com/vextech/smart_edit/feature/scanner/domain/ScannerModels.kt app/src/main/java/com/vextech/smart_edit/feature/scanner/domain/DocumentDetector.kt app/src/main/java/com/vextech/smart_edit/feature/scanner/domain/QuadGeometry.kt app/src/main/java/com/vextech/smart_edit/feature/scanner/domain/PageAnalysis.kt app/src/main/java/com/vextech/smart_edit/feature/scanner/domain/DocumentModes.kt app/src/main/java/com/vextech/smart_edit/core/library/OcrPolicies.kt tools/scanner_test/ScannerPolicyMain.kt -d tools/scanner_test/out
kotlin -classpath tools/scanner_test/out ScannerPolicyMainKt
for f in app/src/main/res/values*/strings.xml; do for k in scanner_title scanner_new_session scanner_export_pdf scanner_error_export scanner_edit_page; do grep -q "name=\"$k\"" "$f" || { echo "Missing $k in $f"; exit 1; }; done; done
grep -q 'ScanSessionEntity::class' app/src/main/java/com/vextech/smart_edit/core/database/SmartEditDatabase.kt
grep -q 'ScanPageEntity::class' app/src/main/java/com/vextech/smart_edit/core/database/SmartEditDatabase.kt
grep -q 'bindScannerRepository' app/src/main/java/com/vextech/smart_edit/core/di/AppModule.kt
grep -q 'PerspectiveCropper' app/src/main/java/com/vextech/smart_edit/feature/scanner/data/ScannerRepository.kt
grep -q 'scanner.addPages(sessionId' app/src/main/java/com/vextech/smart_edit/feature/scanner/LiveCameraViewModel.kt
grep -q 'purgeEmptySessions' app/src/main/java/com/vextech/smart_edit/feature/scanner/ScannerViewModel.kt
grep -q 'applyEditToAll' app/src/main/java/com/vextech/smart_edit/feature/scanner/ScannerViewModel.kt
grep -q 'OcrTextSheet(' app/src/main/java/com/vextech/smart_edit/feature/scanner/ScannerScreen.kt
grep -q 'ImageAnalysis' app/src/main/java/com/vextech/smart_edit/feature/scanner/LiveCameraScreen.kt
grep -q 'DocumentDetector.detect' app/src/main/java/com/vextech/smart_edit/feature/scanner/LiveCameraViewModel.kt
grep -q 'bakeExifOrientation' app/src/main/java/com/vextech/smart_edit/feature/scanner/data/ScannerRepository.kt
grep -q 'FilterAdvisor.suggest' app/src/main/java/com/vextech/smart_edit/feature/scanner/data/ScannerRepository.kt
grep -q 'detectOrientation' app/src/main/java/com/vextech/smart_edit/core/ocr/OcrService.kt
grep -q 'detectDragGesturesAfterLongPress' app/src/main/java/com/vextech/smart_edit/feature/scanner/ScannerScreen.kt
grep -q 'flattenBackground' app/src/main/java/com/vextech/smart_edit/core/processing/ImageProcessor.kt
grep -q 'addIdCard' app/src/main/java/com/vextech/smart_edit/feature/scanner/LiveCameraViewModel.kt
grep -q 'extractCutout' app/src/main/java/com/vextech/smart_edit/feature/scanner/ScannerScreen.kt
for k in scanner_filter_clean scanner_mode_id_card scanner_mode_book scanner_camera_id_back scanner_cutout_signature; do grep -q "name=\"$k\"" app/src/main/res/values/strings.xml || { echo "Missing $k"; exit 1; }; done
for k in scanner_select scanner_apply_filter scanner_camera_retake scanner_auto_rotate; do grep -q "name=\"$k\"" app/src/main/res/values/strings.xml || { echo "Missing $k"; exit 1; }; done
for k in scanner_camera_hint_closer scanner_camera_hint_hold scanner_camera_done_count scanner_camera_pdf; do grep -q "name=\"$k\"" app/src/main/res/values/strings.xml || { echo "Missing $k"; exit 1; }; done
for k in scanner_default_title scanner_undo scanner_apply_all scanner_ocr_text_title scanner_ocr_copy scanner_ocr_share scanner_ocr_empty; do grep -q "name=\"$k\"" app/src/main/res/values/strings.xml || { echo "Missing $k in default strings"; exit 1; }; done
! grep -R -E 'TODO|FIXME|NotImplementedError' app/src/main/java/com/vextech/smart_edit/feature/scanner
echo 'Scanner structural verification: PASS'
