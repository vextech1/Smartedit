#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/converter_test/out"
rm -rf "$OUT" && mkdir -p "$OUT"

kotlinc \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter/domain/ConverterModels.kt" \
  "$ROOT/tools/converter_test/ConverterPolicyMain.kt" \
  -include-runtime -d "$OUT/converter-policy.jar"
java -jar "$OUT/converter-policy.jar"

kotlinc \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter/data/SimpleDocxWriter.kt" \
  "$ROOT/tools/converter_test/DocxWriterMain.kt" \
  -include-runtime -d "$OUT/docx-writer.jar"
java -jar "$OUT/docx-writer.jar"

REPO="$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter/data/AndroidConverterRepository.kt"
VM="$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter/ConverterViewModel.kt"
SCREEN="$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter/ConverterScreen.kt"
DI="$ROOT/app/src/main/java/com/vextech/smart_edit/core/di/AppModule.kt"
PDF="$ROOT/app/src/main/java/com/vextech/smart_edit/core/pdf/PdfEngine.kt"

grep -q "interface ConverterRepository" "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter/data/ConverterRepository.kt"
grep -q "class AndroidConverterRepository" "$REPO"
grep -q "override fun observeJobs" "$REPO"
grep -q "override suspend fun convert" "$REPO"
grep -q "@IoDispatcher" "$REPO"
grep -q "ensureActive" "$REPO"
grep -q "NonCancellable" "$REPO"
grep -q 'STATUS_CANCELLED = "CANCELLED"' "$REPO"
grep -q "registerFile" "$REPO"
grep -q "OpenMultipleDocuments" "$SCREEN"
grep -q "OpenDocument" "$SCREEN"
grep -q "fun cancel" "$VM"
grep -q "bindConverterRepository" "$DI"
grep -q "fun renderPageImage" "$PDF"
grep -q "PdfRenderFormat.PNG" "$PDF"
grep -q "imageFilesToPdf" "$PDF"

if find "$ROOT/app/src/main/java/com/vextech/smart_edit/core" -path '*/conversion/*' -type f | grep -q .; then
  echo "Legacy core conversion implementation still exists" >&2
  exit 1
fi

if grep -R -nE 'TODO|FIXME|NotImplementedError' \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/converter"; then
  echo "Converter placeholders found" >&2
  exit 1
fi

python3 - <<'PY' "$ROOT"
import sys, xml.etree.ElementTree as ET
from pathlib import Path
root=Path(sys.argv[1])
files=sorted(root.glob('app/src/main/res/values*/strings.xml'))
for p in files:
    tree=ET.parse(p)
    names=[e.attrib['name'] for e in tree.getroot() if e.tag=='string']
    dup={n for n in names if names.count(n)>1}
    if dup: raise SystemExit(f'{p} duplicate strings: {sorted(dup)}')
base_path=root/'app/src/main/res/values/strings.xml'
base={e.attrib['name'] for e in ET.parse(base_path).getroot() if e.tag=='string' and e.attrib['name'].startswith('converter_')}
if not base: raise SystemExit('No converter resources in base strings.xml')
for p in files:
    names={e.attrib['name'] for e in ET.parse(p).getroot() if e.tag=='string'}
    missing=base-names
    if missing: raise SystemExit(f'{p} missing Converter strings: {sorted(missing)}')
print('CONVERTER_RESOURCE_XML_PASS')
PY

echo "CONVERTER_FEATURE_VERIFIER_PASS"
