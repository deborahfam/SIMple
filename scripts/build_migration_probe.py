#!/usr/bin/env python3
"""Build a platform-only probe that can instrument an R8-optimized release."""
import argparse
from pathlib import Path
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', type=Path, required=True)
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--alias', required=True)
parser.add_argument('--password-file', type=Path, required=True)
parser.add_argument('--out-dir', type=Path, default=Path('.local/migration-probe'))
args = parser.parse_args()
sdk = args.sdk / 'build-tools/36.0.0'
android = args.sdk / 'platforms/android-36/android.jar'
classes, dex = args.out_dir / 'classes', args.out_dir / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '-source', '8', '-target', '8', '-classpath', str(android), '-d', str(classes), 'tests/migration/AfterUpgradeProbe.java'], check=True)
subprocess.run([str(sdk / 'd8'), '--lib', str(android), '--output', str(dex), *map(str, classes.rglob('*.class'))], check=True)
unsigned = args.out_dir / 'probe-unsigned.apk'
subprocess.run([str(sdk / 'aapt'), 'package', '-f', '-M', 'tests/migration/AndroidManifest.xml', '-I', str(android), '-F', str(unsigned)], check=True)
with zipfile.ZipFile(unsigned, 'a') as archive:
    archive.write(dex / 'classes.dex', 'classes.dex')
subprocess.run([str(sdk / 'apksigner'), 'sign', '--v4-signing-enabled', 'false', '--ks', str(args.keystore),
                '--ks-key-alias', args.alias, '--ks-pass', 'file:' + str(args.password_file),
                '--out', str(args.out_dir / 'probe.apk'), str(unsigned)], check=True)
print('Probe APK:', args.out_dir / 'probe.apk')
