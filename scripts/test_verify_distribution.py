import tempfile
import unittest
from pathlib import Path
from verify_distribution import verify


class DistributionGuardTest(unittest.TestCase):
    def check_manifest(self, text, direct=False):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'AndroidManifest.xml'
            path.write_text(text)
            verify(path, direct)

    def test_play_privacy_boundary(self):
        manifest = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application android:allowBackup="false" android:usesCleartextTraffic="false" />
        </manifest>'''
        self.check_manifest(manifest)
        for change in [
            manifest.replace('allowBackup="false"', 'allowBackup="true"'),
            manifest.replace('usesCleartextTraffic="false"', 'usesCleartextTraffic="true"'),
            manifest.replace('<application ', '<application android:debuggable="true" '),
            manifest.replace('<application ', '<uses-permission android:name="android.permission.READ_SMS"/><application '),
            manifest.replace('<application ', '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/><application '),
        ]:
            with self.subTest(change=change), self.assertRaises(AssertionError):
                self.check_manifest(change)

    def test_direct_receiver_requires_system_sender(self):
        manifest = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <uses-permission android:name="android.permission.READ_SMS" />
          <uses-permission android:name="android.permission.RECEIVE_SMS" />
          <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
          <uses-permission android:name="android.permission.INTERNET" />
          <application android:allowBackup="false" android:usesCleartextTraffic="false">
            <provider android:name="androidx.core.content.FileProvider" android:exported="false" />
            <receiver android:name="com.simple.mobile.data.telephony.CarrierSmsReceiver" android:permission="android.permission.BROADCAST_SMS"/>
          </application>
        </manifest>'''
        self.check_manifest(manifest, True)
        with self.assertRaises(AssertionError):
            self.check_manifest(manifest.replace('android:permission="android.permission.BROADCAST_SMS"', ''), True)
