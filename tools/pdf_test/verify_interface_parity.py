import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
repo_interface = (root / 'app/src/main/java/com/vextech/smart_edit/feature/pdf/data/PdfEditorRepository.kt').read_text()
repo_impl = (root / 'app/src/main/java/com/vextech/smart_edit/feature/pdf/data/AndroidPdfEditorRepository.kt').read_text()
required = set(re.findall(r'\bsuspend\s+fun\s+(\w+)\s*\(', repo_interface))
actual = set(re.findall(r'\boverride\s+suspend\s+fun\s+(\w+)\s*\(', repo_impl))
missing = required - actual
if missing:
    raise SystemExit(f'AndroidPdfEditorRepository missing methods: {sorted(missing)}')

engine = (root / 'app/src/main/java/com/vextech/smart_edit/core/pdf/PdfEngine.kt').read_text()
interface_text = engine.split('@Singleton', 1)[0]
implementation_text = engine.split('class PdfBoxEngine', 1)[1]
required = set(re.findall(r'\bfun\s+(\w+)\s*\(', interface_text))
actual = set(re.findall(r'\boverride\s+fun\s+(\w+)\s*\(', implementation_text))
missing = required - actual
if missing:
    raise SystemExit(f'PdfBoxEngine missing methods: {sorted(missing)}')
print('PDF_INTERFACE_PARITY_PASS')
