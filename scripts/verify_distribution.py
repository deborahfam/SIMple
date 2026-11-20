#!/usr/bin/env python3
"""Fail closed when merged release manifests violate distribution/privacy constraints."""
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = '{http://schemas.android.com/apk/res/android}'
RESTRICTED = {'android.permission.READ_SMS', 'android.permission.RECEIVE_SMS',
              'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.INTERNET'}


def verify(path, direct):
    root = ET.parse(path).getroot()
    app = root.find('application')
    assert app is not None, 'Missing application'
    assert app.get(ANDROID + 'debuggable', 'false') == 'false', 'Debuggable release'
    assert app.get(ANDROID + 'allowBackup') == 'false', 'Backup must be disabled'
    assert app.get(ANDROID + 'usesCleartextTraffic') == 'false', 'Cleartext HTTP must be disabled'
    permissions = {n.get(ANDROID + 'name') for n in root.findall('uses-permission')}
    assert (permissions & RESTRICTED) == (RESTRICTED if direct else set()), 'Unexpected distribution permissions'
    providers = [n for n in app.findall('provider') if n.get(ANDROID + 'name') == 'androidx.core.content.FileProvider']
    assert bool(providers) == direct, 'Unexpected update provider'
    for provider in providers:
        assert provider.get(ANDROID + 'exported') == 'false', 'Exported update provider'
    receivers = [n for n in app.findall('receiver') if n.get(ANDROID + 'name', '').endswith('CarrierSmsReceiver')]
    assert bool(receivers) == direct, 'Unexpected SMS receiver'
    for receiver in receivers:
        assert receiver.get(ANDROID + 'permission') == 'android.permission.BROADCAST_SMS', 'Unprotected SMS receiver'


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-dir', type=Path, default=Path('app/build'))
    args = parser.parse_args()
    for flavor in ('direct', 'play'):
        variant = flavor + 'Release'
        manifest = args.build_dir / 'intermediates/merged_manifests' / variant / ('process' + variant[0].upper() + variant[1:] + 'Manifest') / 'AndroidManifest.xml'
        verify(manifest, flavor == 'direct')
        print(f'{variant}: privacy/distribution manifest checks passed')
