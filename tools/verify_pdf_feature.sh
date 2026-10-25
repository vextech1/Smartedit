#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/pdf_test/out"
rm -rf "$OUT" && mkdir -p "$OUT"
kotlinc \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf/domain/PdfEditorModels.kt" \
  "$ROOT/tools/pdf_test/PdfPolicyMain.kt" \
  -include-runtime -d "$OUT/pdf-policy.jar"
java -jar "$OUT/pdf-policy.jar"

kotlinc \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf/data/WorkingCopyHistory.kt" \
  "$ROOT/tools/pdf_test/WorkingCopyMain.kt" \
  -include-runtime -d "$OUT/pdf-history.jar"
java -jar "$OUT/pdf-history.jar"

grep -q "interface PdfEditorRepository" "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf/data/PdfEditorRepository.kt"
grep -q "class AndroidPdfEditorRepository" "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf/data/AndroidPdfEditorRepository.kt"
grep -q "class PdfBoxEngine" "$ROOT/app/src/main/java/com/vextech/smart_edit/core/pdf/PdfEngine.kt"
grep -q "fun undo" "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf/PdfEditorViewModel.kt"
grep -q "fun redo" "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf/PdfEditorViewModel.kt"
if grep -R -nE 'TODO|FIXME|NotImplementedError' "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/pdf" "$ROOT/app/src/main/java/com/vextech/smart_edit/core/pdf"; then
  echo "PDF placeholders found" >&2; exit 1
fi
python3 - <<'PY' "$ROOT"
import sys, xml.etree.ElementTree as ET
from pathlib import Path
root=Path(sys.argv[1])
for p in root.glob('app/src/main/res/values*/strings.xml'): ET.parse(p)

keys=[]
for p in root.glob('app/src/main/res/values*/strings.xml'):
    names={e.attrib['name'] for e in ET.parse(p).getroot() if e.tag=='string'}
    pdf={n for n in names if n.startswith('pdf_')}
    keys.append((p,pdf))
base=keys[0][1]
for p,k in keys[1:]:
    missing=base-k
    if missing: raise SystemExit(f'{p} missing PDF strings: {sorted(missing)}')
print('PDF_RESOURCE_XML_PASS')
PY
python3 "$ROOT/tools/pdf_test/verify_interface_parity.py" "$ROOT"
echo "PDF_FEATURE_VERIFIER_PASS"
