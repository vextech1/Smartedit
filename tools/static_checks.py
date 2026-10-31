#!/usr/bin/env python3
"""Compiler-free structural checks. Run from the project root: python3 tools/static_checks.py
Checks: project imports resolve, no duplicate top-level declarations, R.string references exist,
locale key parity, duplicate keys, XML well-formedness, hard-coded strings in core/ui."""
import os, re, sys, glob, collections, xml.dom.minidom as minidom

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src')
errors = []

DECL = re.compile(r'^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|private|internal|data|sealed|enum|abstract|open|annotation|inline|value|const|suspend|tailrec|operator|infix)\s+)*(class|object|interface|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)')

def kt_files(sub):
    for r, _, fs in os.walk(os.path.join(SRC, sub)):
        for f in fs:
            if f.endswith('.kt'): yield os.path.join(r, f)

decl, pkgs, dup = set(), set(), collections.defaultdict(list)
for p in kt_files('main/java'):
    t = open(p, encoding='utf-8').read()
    pkg = re.search(r'^package\s+([\w.]+)', t, re.M).group(1); pkgs.add(pkg)
    for line in t.split('\n'):
        if line.startswith((' ', '\t')): continue
        m = DECL.match(line)
        if m:
            decl.add(pkg + '.' + m.group(2))
            if m.group(1) in ('class', 'object', 'interface', 'typealias') and not line.lstrip().startswith('private'):
                dup[(pkg, m.group(2))].append(os.path.relpath(p, SRC))

for k, v in dup.items():
    if len(set(v)) > 1: errors.append(f'duplicate declaration {k[0]}.{k[1]} in {v}')

by_name = collections.defaultdict(set)
for (pkg, name), files in dup.items(): by_name[name].add(pkg)
for name, pk in by_name.items():
    if len(pk) > 1: errors.append(f'type name {name} declared in several packages {sorted(pk)} (ambiguous with wildcard imports)')

for sub in ('main/java', 'test/java', 'androidTest/java'):
    for p in kt_files(sub):
        for i in re.findall(r'^import\s+(com\.vextech\.smart_edit\.[\w.*]+)', open(p, encoding='utf-8').read(), re.M):
            if i.endswith(('.R', '.BuildConfig')): continue
            ok = (i[:-2] in pkgs) if i.endswith('.*') else (i in decl or i.rsplit('.', 1)[0] in decl)
            if not ok: errors.append(f'unresolved import {i} in {os.path.relpath(p, SRC)}')

def keys(path): return re.findall(r'<string name="([^"]+)"', open(path, encoding='utf-8').read())
en = keys(os.path.join(SRC, 'main/res/values/strings.xml'))
for k, n in collections.Counter(en).items():
    if n > 1: errors.append(f'duplicate string key {k}')
used = set()
for sub in ('main/java', 'test/java', 'androidTest/java'):
    for p in kt_files(sub): used |= set(re.findall(r'R\.string\.(\w+)', open(p, encoding='utf-8').read()))
for p in glob.glob(os.path.join(SRC, 'main/res/**/*.xml'), recursive=True) + [os.path.join(SRC, 'main/AndroidManifest.xml')]:
    try: minidom.parse(p)
    except Exception as e: errors.append(f'bad xml {os.path.relpath(p, SRC)}: {e}')
    used |= set(re.findall(r'@string/(\w+)', open(p, encoding='utf-8').read()))
for k in sorted(used - set(en)): errors.append(f'missing string key {k}')
for loc in sorted(glob.glob(os.path.join(SRC, 'main/res/values-*/strings.xml'))):
    lk = keys(loc); name = loc.split('/')[-2]
    for k in sorted(set(en) - set(lk)): errors.append(f'{name} missing {k}')
    for k in sorted(set(lk) - set(en)): errors.append(f'{name} extra {k}')

for p in kt_files('main/java/com/vextech/smart_edit/core/ui'):
    t = open(p, encoding='utf-8').read()
    for m in re.finditer(r'(?<![\w.])(Text|Icon)\(\s*"[A-Za-z]', t):
        errors.append(f'hard-coded user string in {os.path.relpath(p, SRC)}')
    if re.search(r'contentDescription\s*=\s*"[A-Za-z]', t) or re.search(r'Icon\([^)]*,\s*"[A-Za-z]', t):
        errors.append(f'hard-coded contentDescription in {os.path.relpath(p, SRC)}')


# ---- plurals parity, placeholder parity, unescaped quotes, known-bad imports
def plural_names(path): return re.findall(r'<plurals name="([^"]+)"', open(path, encoding='utf-8').read())
def string_map(path): return dict(re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(path, encoding='utf-8').read(), re.S))
en_path = os.path.join(SRC, 'main/res/values/strings.xml')
en_plurals = set(plural_names(en_path)); en_map = string_map(en_path)
PH = re.compile(r'%(\d+\$)?[sd]')
for loc in sorted(glob.glob(os.path.join(SRC, 'main/res/values-*/strings.xml'))):
    name = loc.split('/')[-2]; lm = string_map(loc)
    for k in sorted(en_plurals - set(plural_names(loc))): errors.append(f'{name} missing plurals {k}')
    for k, v in lm.items():
        if k in en_map and sorted(PH.findall(v)) != sorted(PH.findall(en_map[k])) and len(PH.findall(v)) != len(PH.findall(en_map[k])):
            errors.append(f'{name} placeholder mismatch in {k}')
for p in glob.glob(os.path.join(SRC, 'main/res/values*/strings.xml')):
    for k, v in string_map(p).items():
        if re.search(r"(?<!\\)'", v) or re.search(r'(?<!\\)"', v): errors.append(f'{os.path.relpath(p, SRC)} unescaped quote in {k}')
BAD_IMPORTS = ['import androidx.compose.foundation.layout.Modifier']
for sub in ('main/java', 'test/java', 'androidTest/java'):
    for p in kt_files(sub):
        txt = open(p, encoding='utf-8').read()
        for bad in BAD_IMPORTS:
            if bad in txt: errors.append(f'wrong import "{bad}" in {os.path.relpath(p, SRC)}')


# ---- every module compiles and targets Android 16 (API 36), with no stray lower pins
REQUIRED_SDK = 36
gradle_files = [p for p in glob.glob(os.path.join(ROOT, '**/*.gradle.kts'), recursive=True) if '/.gradle/' not in p]
app_gradle = open(os.path.join(ROOT, 'app/build.gradle.kts'), encoding='utf-8').read()
target_found = re.findall(r'targetSdk\s*=\s*(\d+)', app_gradle)
if target_found != [str(REQUIRED_SDK)]: errors.append(f'targetSdk must be exactly {REQUIRED_SDK} in app/build.gradle.kts, found {target_found}')
# compileSdk: either `compileSdk = N` or the AGP 9 block `compileSdk { version = release(N) { ... } }`.
compile_found = re.findall(r'compileSdk\s*=\s*(\d+)', app_gradle) + re.findall(r'compileSdk\s*\{[^}]*?release\((\d+)\)', app_gradle, re.S)
if len(compile_found) != 1 or int(compile_found[0]) < REQUIRED_SDK:
    errors.append(f'compileSdk must be declared once and be at least {REQUIRED_SDK} (libraries in use need 37.1), found {compile_found}')
for gf in gradle_files:
    txt = open(gf, encoding='utf-8').read()
    for legacy in ('compileSdkVersion', 'targetSdkVersion'):
        if legacy in txt: errors.append(f'legacy {legacy} in {os.path.relpath(gf, ROOT)}')
manifest_text = open(os.path.join(SRC, 'main/AndroidManifest.xml'), encoding='utf-8').read()
if 'targetSdkVersion' in manifest_text: errors.append('targetSdkVersion pinned in AndroidManifest.xml (set it only in Gradle)')
if not re.search(r'enableEdgeToEdge\(\)', open(os.path.join(SRC, 'main/java/com/vextech/smart_edit/MainActivity.kt'), encoding='utf-8').read()):
    errors.append('MainActivity must call enableEdgeToEdge() (edge-to-edge is enforced when targeting API 35+)')
for removed in ('onBackPressed', 'startActivityForResult', 'getExternalStorageDirectory', 'requestLegacyExternalStorage'):
    for sub in ('main/java',):
        for p in kt_files(sub):
            if removed in open(p, encoding='utf-8').read(): errors.append(f'{removed} used in {os.path.relpath(p, SRC)}')

for p in list(kt_files('main/java')):
    t = open(p, encoding='utf-8').read()
    if re.search(r'\b(TODO|FIXME)\b|NotImplementedError', t): errors.append(f'placeholder marker in {os.path.relpath(p, SRC)}')

unused = sorted(set(en) - used)
print(f'{len(list(kt_files("main/java")))} main files, {len(en)} string keys, {len(unused)} currently unused keys')
if errors:
    print('FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('static checks passed')
