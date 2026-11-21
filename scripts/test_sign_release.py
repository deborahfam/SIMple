import unittest
from sign_release import verify_certificate


class SigningCertificateTest(unittest.TestCase):
    def test_accepts_expected_production_certificate(self):
        verify_certificate('Signer #1 certificate DN: CN=SIMple\nSigner #1 certificate SHA-256 digest: ' + 'a'*64, 'a'*64)

    def test_rejects_wrong_missing_or_multiple_signers(self):
        for report in ['', 'Signer #1 certificate SHA-256 digest: ' + 'b'*64,
                       'Signer #1 certificate SHA-256 digest: ' + 'a'*64 + '\nSigner #2 certificate SHA-256 digest: ' + 'a'*64]:
            with self.subTest(report=report), self.assertRaises(ValueError):
                verify_certificate(report, 'a'*64)

    def test_debug_certificate_only_allowed_for_explicit_legacy_platforms(self):
        report = 'Signer #1 certificate DN: CN=Android Debug\nSigner #1 certificate SHA-256 digest: ' + 'a'*64
        with self.assertRaises(ValueError):
            verify_certificate(report, 'a'*64)
        verify_certificate(report, 'a'*64, legacy=True)
