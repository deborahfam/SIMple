#!/usr/bin/env python3
"""Sign a release with authenticated key rotation; credentials are read only from environment."""
import argparse
import base64
import os
from pathlib import Path
import re
import subprocess
import tempfile


def verify_certificate(output, expected, legacy=False):
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise ValueError('Expected certificate fingerprint must be lowercase SHA-256')
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)$', output, re.M)
    if fingerprints != [expected]:
        raise ValueError('Signing certificate does not match the configured identity')
    if not legacy and 'cn=android debug' in output.lower():
        raise ValueError('Production platforms must not use a debug signing certificate')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--version-code', type=int, required=True)
    args = parser.parse_args()
    if args.apk.resolve() == args.out.resolve():
        raise ValueError('Input and output must differ')
    names = ['KEYSTORE_BASE64', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD',
             'PRODUCTION_KEYSTORE_BASE64', 'PRODUCTION_KEYSTORE_PASSWORD',
             'PRODUCTION_KEY_ALIAS', 'PRODUCTION_KEY_PASSWORD', 'SIGNING_LINEAGE_BASE64',
             'EXPECTED_CERT', 'EXPECTED_PRODUCTION_CERT']
    for name in names:
        if not os.environ.get(name):
            raise ValueError('Missing signing configuration: ' + name)
    if os.environ['EXPECTED_CERT'] == os.environ['EXPECTED_PRODUCTION_CERT']:
        raise ValueError('Production key must differ from the legacy key')
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
    signer = str(sdk / 'apksigner')
    badging = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    if 'application-debuggable' in badging:
        raise ValueError('Refusing a debuggable release')
    if f"package: name='com.simple.mobile' versionCode='{args.version_code}'" not in badging.splitlines()[0]:
        raise ValueError('Unexpected package ID or version code')
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='simple-sign-', dir=os.environ.get('RUNNER_TEMP')) as directory:
        root = Path(directory)
        for filename, variable in [('legacy.keystore', 'KEYSTORE_BASE64'),
                                   ('production.keystore', 'PRODUCTION_KEYSTORE_BASE64'),
                                   ('rotation.lineage', 'SIGNING_LINEAGE_BASE64')]:
            data = base64.b64decode(''.join(os.environ[variable].split()), validate=True)
            if not data:
                raise ValueError('Empty signing material: ' + variable)
            path = root / filename
            with path.open('xb') as handle:
                path.chmod(0o600)
                handle.write(data)
        try:
            subprocess.run([signer, 'sign', '--debuggable-apk-permitted', 'false',
                            '--v4-signing-enabled', 'false', '--rotation-min-sdk-version', '28',
                            '--lineage', str(root / 'rotation.lineage'),
                            '--ks', str(root / 'legacy.keystore'), '--ks-key-alias', os.environ['KEY_ALIAS'],
                            '--ks-pass', 'env:KEYSTORE_PASSWORD', '--key-pass', 'env:KEY_PASSWORD',
                            '--next-signer', '--ks', str(root / 'production.keystore'),
                            '--ks-key-alias', os.environ['PRODUCTION_KEY_ALIAS'],
                            '--ks-pass', 'env:PRODUCTION_KEYSTORE_PASSWORD', '--key-pass', 'env:PRODUCTION_KEY_PASSWORD',
                            '--out', str(args.out), str(args.apk)], check=True)
            for api in (26, 27, 28, 32, 33, 36):
                report = subprocess.check_output([signer, 'verify', '--min-sdk-version', str(api),
                                                  '--max-sdk-version', str(api), '--print-certs', str(args.out)], text=True)
                verify_certificate(report, os.environ['EXPECTED_CERT' if api < 28 else 'EXPECTED_PRODUCTION_CERT'], legacy=api < 28)
                print(f'API {api}: signature and expected certificate verified')
        except BaseException:
            args.out.unlink(missing_ok=True)
            raise


if __name__ == '__main__':
    main()
