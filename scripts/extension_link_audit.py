#!/usr/bin/env python3
"""Static link audit for CloudStream extensions (.cs3) against a built Yumeiro APK.

For every .cs3 in a folder, reads the plugin's own dex files, collects every class and
member it references, and reports those that neither the plugin, the Android platform nor
the APK defines. Any hit is a NoClassDefFoundError / NoSuchMethodError waiting to happen
when the extension loads or runs. See docs/EXTENSION-COMPAT.md.

Usage:
  mkdir -p /tmp/apkdex && unzip -o -q app/build/outputs/apk/preview/*universal*.apk 'classes*.dex' -d /tmp/apkdex
  python3 scripts/extension_link_audit.py --apk-dex /tmp/apkdex --plugins ~/cs3 --out audit.json

Needs `dexdump` from the Android build tools (set DEXDUMP or ANDROID_HOME).
"""
import argparse
import glob
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile

PLATFORM = (
    'Ljava/', 'Ljavax/', 'Landroid/', 'Ldalvik/', 'Lorg/w3c/', 'Lorg/xml/', 'Lorg/xmlpull/',
    'Lcom/android/org/', 'Lsun/', 'Lorg/json/', 'Lorg/apache/http/', 'Ljunit',
)


def find_dexdump():
    if os.environ.get('DEXDUMP'):
        return os.environ['DEXDUMP']
    home = os.environ.get('ANDROID_HOME', os.path.expanduser('~/android'))
    hits = sorted(glob.glob(os.path.join(home, 'build-tools', '*', 'dexdump')))
    if not hits:
        sys.exit('dexdump not found: set DEXDUMP or ANDROID_HOME')
    return hits[-1]


def defs(dexdump, dexes):
    """Class -> {super, interfaces, members} for every class defined in the dex files."""
    cls = {}
    for d in dexes:
        out = subprocess.run([dexdump, d], capture_output=True, text=True, errors='replace').stdout
        cur = mode = name = None
        for line in out.splitlines():
            m = re.match(r"\s*Class descriptor\s*: '(.+)'", line)
            if m:
                cur = m.group(1)
                cls[cur] = {'super': None, 'ifs': [], 'm': set()}
                mode = None
                continue
            if cur is None:
                continue
            m = re.match(r"\s*Superclass\s*: '(.+)'", line)
            if m:
                cls[cur]['super'] = m.group(1)
                continue
            if re.match(r"\s*Interfaces\s*-", line):
                mode = 'if'
                continue
            if mode == 'if':
                m = re.match(r"\s*#\d+\s*: '(.+)'", line)
                if m:
                    cls[cur]['ifs'].append(m.group(1))
                    continue
            if re.search(r"(Direct|Virtual) methods|(Static|Instance) fields", line):
                mode = 'mem'
                continue
            m = re.match(r"\s*name\s*: '(.+)'", line)
            if m:
                name = m.group(1)
                continue
            m = re.match(r"\s*type\s*: '(.+)'", line)
            if m and name:
                cls[cur]['m'].add(name + ':' + m.group(1))
                name = None
    return cls


def has(apk, c, mem):
    """True / False if the member is (not) found up the hierarchy; None if it can't be checked."""
    if c not in apk:
        return None
    if mem in apk[c]['m']:
        return True
    for p in [apk[c]['super']] + apk[c]['ifs']:
        if p is None:
            continue
        if p.startswith(PLATFORM):
            return None
        r = has(apk, p, mem)
        if r is not False:
            return r
    return False


def audit(dexdump, apk, path, work):
    z = zipfile.ZipFile(path)
    names = [n for n in z.namelist() if n.endswith('.dex')]
    man = json.loads(z.read('manifest.json')) if 'manifest.json' in z.namelist() else None
    for n in names:
        z.extract(n, work)
    dexes = [os.path.join(work, n) for n in names]
    own = defs(dexdump, dexes)
    refs = set()
    for d in dexes:
        out = subprocess.run([dexdump, '-d', d], capture_output=True, text=True, errors='replace').stdout
        for m in re.finditer(r"(L[\w/$\-]+;)\.([\w$<>\-]+):(\S+)", out):
            refs.add((m.group(1), m.group(2) + ':' + m.group(3)))
        for m in re.finditer(r"(?:new-instance|const-class|check-cast|instance-of|filled-new-array)[^,]*, [^,]*?(L[\w/$\-]+;)", out):
            refs.add((m.group(1), None))
    missing_cls, missing_mem = set(), set()
    for c, mem in refs:
        if c in own or c.startswith(PLATFORM):
            continue
        if c not in apk:
            missing_cls.add(c)
            continue
        if mem and has(apk, c, mem) is False:
            missing_mem.add(c + '.' + mem)
    return {
        'mc': sorted(missing_cls), 'mm': sorted(missing_mem), 'dex': len(names),
        'man': bool(man and man.get('pluginClassName')),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apk-dex', required=True, help='folder with the APK classes*.dex')
    ap.add_argument('--plugins', required=True, help='folder with .cs3 files')
    ap.add_argument('--out', default='extension_link_audit.json')
    a = ap.parse_args()
    dexdump = find_dexdump()
    apk = defs(dexdump, sorted(glob.glob(os.path.join(a.apk_dex, '*.dex'))))
    files = sorted(glob.glob(os.path.join(a.plugins, '*.cs3')))
    res = {}
    for i, f in enumerate(files):
        with tempfile.TemporaryDirectory() as work:
            try:
                res[os.path.basename(f)] = audit(dexdump, apk, f, work)
            except Exception as e:  # broken zip, missing dex
                res[os.path.basename(f)] = {'bad': str(e)[:120]}
        if i % 100 == 0:
            print(f'{i}/{len(files)}', flush=True)
    json.dump(res, open(a.out, 'w'), indent=1)
    broken = {k: v for k, v in res.items() if v.get('bad') or v.get('mc') or v.get('mm')}
    print(f'{len(files)} extensions, {len(files) - len(broken)} link clean, {len(broken)} with gaps')
    for k, v in sorted(broken.items()):
        print(' ', k, v.get('bad') or (v['mc'] + v['mm'])[:3])


if __name__ == '__main__':
    main()
