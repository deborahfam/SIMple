"""Allocate Android update versions from GitHub releases, including draft reservations."""
import json
import os
import re
from pathlib import Path


def allocate(releases, sha, base_code, base_name):
    marker = f'<!-- simple-source:{sha} -->'
    matching = [r for r in releases if marker in (r.get('body') or '')]
    if matching:
        release = matching[0]
        codes = [int(m.group(1)) for a in release.get('assets', [])
                 if (m := re.fullmatch(r'SIMple-(\d+)\.apk', a['name']))]
        # Draft notes reserve a code even before the APK upload succeeds.
        reserved = re.search(r'<!-- simple-code:(\d+) -->', release.get('body') or '')
        code = int(reserved[1]) if reserved else max(codes)
        return code, release['tag_name'].removeprefix('v'), not release['draft']
    codes = [base_code - 1]
    versions = []
    for release in releases:
        for asset in release.get('assets', []):
            match = re.fullmatch(r'SIMple-(\d+)\.apk', asset['name'])
            if match:
                codes.append(int(match[1]))
        reserved = re.search(r'<!-- simple-code:(\d+) -->', release.get('body') or '')
        if reserved:
            codes.append(int(reserved[1]))
        match = re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)', release['tag_name'])
        if match:
            versions.append(tuple(map(int, match.groups())))
    base = tuple(map(int, base_name.split('.')))
    latest = max(versions, default=(0, 0, 0))
    version = base if base > latest else (*latest[:2], latest[2] + 1)
    code = max(codes) + 1
    if code > 2100000000:
        raise ValueError('Android versionCode exhausted')
    return code, '.'.join(map(str, version)), False


if __name__ == '__main__':
    source = Path('app/build.gradle.kts').read_text()
    base_code = int(re.search(r'val baseVersionCode = (\d+)', source)[1])
    base_name = re.search(r'val baseVersionName = "([\d.]+)"', source)[1]
    releases = json.loads(Path(os.environ['RELEASES_JSON']).read_text())
    code, name, published = allocate(releases, os.environ['GITHUB_SHA'], base_code, base_name)
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'code={code}\nname={name}\ntag=v{name}\npublished={str(published).lower()}\n')
