import unittest
from release_metadata import allocate


class ReleaseVersions(unittest.TestCase):
    def test_first_automated_release(self):
        releases = [{'tag_name': 'v0.5.1', 'assets': [{'name': 'SIMple-7.apk'}]}]
        self.assertEqual(allocate(releases, 'abc', 7, '0.5.1'), (8, '0.5.2', False))

    def test_manual_version_bump(self):
        releases = [{'tag_name': 'v0.5.1', 'assets': [{'name': 'SIMple-7.apk'}]}]
        self.assertEqual(allocate(releases, 'abc', 10, '0.6.0'), (10, '0.6.0', False))

    def test_draft_reserves_version(self):
        releases = [{'tag_name': 'v0.5.2', 'body': '<!-- simple-source:old -->\n<!-- simple-code:8 -->', 'assets': [], 'draft': True}]
        self.assertEqual(allocate(releases, 'new', 7, '0.5.1'), (9, '0.5.3', False))
        self.assertEqual(allocate(releases, 'old', 7, '0.5.1'), (8, '0.5.2', False))

    def test_published_rerun_does_not_duplicate(self):
        releases = [{'tag_name': 'v0.5.2', 'body': '<!-- simple-source:abc -->\n<!-- simple-code:8 -->', 'assets': [], 'draft': False}]
        self.assertEqual(allocate(releases, 'abc', 7, '0.5.1'), (8, '0.5.2', True))

    def test_unsorted_releases_and_unrelated_assets(self):
        releases = [{'tag_name': 'v0.6.0', 'assets': [{'name': 'SIMple-12.apk'}, {'name': 'other-999.apk'}]}, {'tag_name': 'v0.5.1', 'assets': [{'name': 'SIMple-7.apk'}]}]
        self.assertEqual(allocate(releases, 'abc', 7, '0.5.1'), (13, '0.6.1', False))


if __name__ == '__main__':
    unittest.main()
