import unittest
from release import app_version, check_version, changelog_entry


class ReleaseTests(unittest.TestCase):
    def test_reads_version(self):
        self.assertEqual(('0.10.2', 12), app_version("versionName '0.10.2'\nversionCode 12"))

    def test_rejects_invalid_versions(self):
        for text in ["versionName 'bad'\nversionCode 12", "versionName '1.0.0'", "versionName '1.0.0'\nversionCode 0"]:
            with self.assertRaises(ValueError):
                app_version(text)

    def test_requires_both_versions_to_increase(self):
        previous = ('0.10.2', 12)
        for current in [('0.10.2', 13), ('0.10.3', 12), ('0.9.0', 13)]:
            with self.assertRaises(ValueError):
                check_version(current, previous)
        check_version(('0.10.3', 13), previous)
        check_version(('0.11.0', 13), previous)

    def test_changelog_only_uses_requested_version(self):
        text = '# Changelog\n\n## 0.11.0\n\n- New feature.\n\n## 0.10.2\n\n- Old feature.\n'
        self.assertEqual('- New feature.', changelog_entry(text, '0.11.0'))
        self.assertEqual('- Old feature.', changelog_entry(text, '0.10.2'))
        self.assertEqual('', changelog_entry(text, '0.10.3'))


if __name__ == '__main__':
    unittest.main()
