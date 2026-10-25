#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/files_test/files-feature-tests.jar"
mkdir -p "$ROOT/tools/files_test"

kotlinc \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/files/domain/FileModels.kt" \
  "$ROOT/app/src/main/java/com/vextech/smart_edit/feature/files/data/ManagedFileOperator.kt" \
  "$ROOT/tools/files_test/FilesFeatureVerification.kt" \
  -include-runtime -d "$OUT"
java -jar "$OUT"

python3 - "$ROOT" <<'PY'
import re, sys, xml.etree.ElementTree as ET
from pathlib import Path
root=Path(sys.argv[1])
files=root/'app/src/main/java/com/vextech/smart_edit/feature/files'
vm=(files/'FilesViewModel.kt').read_text()
repo=(files/'data/AndroidFilesRepository.kt').read_text()
dao=(root/'app/src/main/java/com/vextech/smart_edit/core/database/dao/Daos.kt').read_text()
di=(root/'app/src/main/java/com/vextech/smart_edit/core/di/AppModule.kt').read_text()
screen=(files/'FilesScreen.kt').read_text()
sharing=(root/'app/src/main/java/com/vextech/smart_edit/core/sharing/ShareService.kt').read_text()
android_test=(root/'app/src/androidTest/java/com/vextech/smart_edit/feature/files/AndroidFilesRepositoryTest.kt').read_text()
entity=(root/'app/src/main/java/com/vextech/smart_edit/core/database/entity/Entities.kt').read_text()

checks={
    'feature repository boundary': 'FilesRepository' in vm and 'SmartEditRepository' not in vm,
    'blocking file IO dispatched off main': '@IoDispatcher' in repo and 'withContext(ioDispatcher)' in repo,
    'managed files have trash state': 'val isTrashed: Boolean = false' in entity,
    'active file queries exclude trash': 'WHERE isTrashed = 0' in dao,
    'Hilt binds FilesRepository': 'bindFilesRepository' in di,
    'folder cycle protection': 'isDescendant' in repo,
    'strict user filename validation': 'FileNamePolicy.validateUserName' in repo,
    'multi-file native document picker': 'OpenMultipleDocuments' in screen,
    'folder list/grid UI': 'LazyVerticalGrid' in screen and 'LazyColumn' in screen,
    'open/share use FileProvider-backed service': 'share.openManaged' in screen and 'share.shareManaged' in screen,
    'managed open/share rejects paths outside app storage': 'canonicalFile' in sharing and 'managedRoot.path' in sharing,
    'typed localized UI messages': 'FilesUiMessage' in vm and 'filesMessageText' in screen,
    'repository Android integration coverage': android_test.count('@Test') >= 7 and 'importDocuments_copiesContentUriIntoManagedStorage' in android_test,
}
for name, ok in checks.items():
    if not ok:
        raise SystemExit(f'FAILED structural check: {name}')

base=ET.parse(root/'app/src/main/res/values/strings.xml').getroot()
resource_names={e.attrib['name'] for e in base.findall('string')}
refs=set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', screen))
missing=sorted(refs-resource_names)
if missing:
    raise SystemExit('FAILED missing string resources: '+', '.join(missing))

for locale in ['fr','es','de','ar','hi']:
    path=root/f'app/src/main/res/values-{locale}/strings.xml'
    names={e.attrib['name'] for e in ET.parse(path).getroot().findall('string')}
    missing_locale=sorted(refs-names)
    if missing_locale:
        raise SystemExit(f'FAILED values-{locale} missing Files strings: '+', '.join(missing_locale))

for source in files.rglob('*.kt'):
    text=source.read_text()
    if 'TODO(' in text or 'NotImplementedError' in text:
        raise SystemExit(f'FAILED placeholder in {source}')

print('Files feature structural verification passed')
PY
