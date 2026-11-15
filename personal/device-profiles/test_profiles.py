import copy
import unittest
import profiles


class PublicProfileTest(unittest.TestCase):
    def profile(self):
        return {"schema": 1, "model": "Chromecast", "theme": {"wallpaper": True},
                "preferences": {"personal_home_font": "blackletter", "dock_opacity": 30,
                                "remote_key_choice_hold:scan:485": "org.smarttube.stable"},
                "order": "tvhome-order 1\ndock com.example.app\n", "wallpapers": ["01-stars.jpg"]}

    def test_public_home_choices_are_accepted_without_mutation(self):
        profile = self.profile()
        before = copy.deepcopy(profile)
        profiles.check_public(profile)
        self.assertEqual(before, profile)

    def test_private_fields_are_rejected_at_every_supported_level(self):
        for field, location in [("serial", None), ("greeting_name", "theme"), ("password", "preferences")]:
            with self.subTest(field=field):
                profile = self.profile()
                (profile if location is None else profile[location])[field] = "private"
                with self.assertRaises(ValueError):
                    profiles.check_public(profile)

    def test_nested_addresses_and_credentials_are_rejected(self):
        for value in ["192.168.1.50", "user@example.org", "smb://user:pass@nas/share", "https://example.org"]:
            with self.subTest(value=value):
                profile = self.profile()
                profile["theme"]["app_labels"] = {"com.example.app": value}
                with self.assertRaises(ValueError):
                    profiles.check_public(profile)

    def test_older_theme_snapshot_is_explicit_and_accepted(self):
        profiles.check_public({"schema": 1, "model": "BRAVIA 4K VH22", "scope": "theme-only",
                               "captured_date": "2026-11-15", "theme": {"columns": 4}})

    def test_malformed_shapes_are_rejected_before_publishing(self):
        for field, value in [("schema", True), ("theme", []), ("preferences", []), ("model", 12)]:
            with self.subTest(field=field):
                profile = self.profile()
                profile[field] = value
                with self.assertRaises(ValueError):
                    profiles.check_public(profile)


if __name__ == '__main__':
    unittest.main()
