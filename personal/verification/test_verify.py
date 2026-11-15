import importlib.util
from pathlib import Path
import unittest
import tempfile
import json

spec = importlib.util.spec_from_file_location("home_verify", Path(__file__).with_name("verify.py"))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class VerificationGuardsTest(unittest.TestCase):
    def test_android_randomized_apk_directories_are_accepted_without_shell_text(self):
        self.assertEqual("/data/app/~~abc-12==/com.gathin.tvhome-random==/base.apk",
                         verify.installed_apk_path("package:/data/app/~~abc-12==/com.gathin.tvhome-random==/base.apk"))
        for value in ["", "package:/data/app/ok/base.apk;touch bad", "package:/sdcard/base.apk"]:
            with self.assertRaises(RuntimeError): verify.installed_apk_path(value)

    def test_wrong_device_and_sony_sku_are_rejected(self):
        verify.check_identity("chromecast", "Chromecast")
        verify.check_identity("sony", "BRAVIA 4K VH22", "KM-55X85L")
        with self.assertRaises(RuntimeError): verify.check_identity("chromecast", "BRAVIA 4K VH22")
        with self.assertRaises(RuntimeError): verify.check_identity("sony", "BRAVIA 4K VH22", "other")

    def test_edge_exposure_and_missing_layout_fail_verification(self):
        verify.check_pose({"scale": 1.02, "translation_y": -8.64, "height": 1080})
        with self.assertRaises(RuntimeError): verify.check_pose({"scale": 1.02, "translation_y": -20, "height": 1080})
        with self.assertRaises(RuntimeError): verify.check_pose({"scale": 1.1, "translation_y": 0, "height": 0})

    def test_user_settings_or_order_changes_are_detected(self):
        before = {"settings_fingerprint": "prefs", "order_fingerprint": "order"}
        verify.same_settings(before, dict(before))
        for key in before:
            changed = dict(before); changed[key] = "changed"
            with self.assertRaises(RuntimeError): verify.same_settings(before, changed)

    def test_discovery_only_refreshes_the_configured_paired_service(self):
        entry = {"host": "old:5555", "mdns_name": "paired-tv"}
        text = "other-tv _adb-tls-connect._tcp wrong:1234\npaired-tv _adb-tls-connect._tcp new:6789\n"
        self.assertEqual("new:6789", verify.resolve_endpoint(entry, text))
        self.assertEqual("old:5555", verify.resolve_endpoint(entry, "other-tv _adb-tls-connect._tcp wrong:1234"))
        self.assertEqual("old:5555", verify.resolve_endpoint(entry, "paired-tv _adb-tls-pairing._tcp wrong:1234"))

    def test_recovery_journal_survives_a_failed_restore_and_clears_only_after_success(self):
        with tempfile.TemporaryDirectory() as folder:
            device = object.__new__(verify.Device)
            device.pending = Path(folder) / "pending.json"
            device.target = verify.TARGETS["chromecast"]
            verify.write_json(device.pending, {"model": "Chromecast", "mode": "off"})
            def disconnected(mode): raise RuntimeError("Disconnected")
            device.choose_mode = disconnected
            with self.assertRaises(RuntimeError): device.recover()
            self.assertTrue(device.pending.exists())
            device.choose_mode = lambda mode: {"motion": {"saved_mode": mode}}
            device.recover()
            self.assertFalse(device.pending.exists())

    def test_a_paused_device_is_rejected_before_any_connection_is_resolved(self):
        config = {"devices": {"sony": {"host": "tv:5555", "paused": True}, "chromecast": {"host": "cc:1234"}}}
        with self.assertRaisesRegex(RuntimeError, "paused"): verify.configured_device(config, "sony")
        self.assertEqual("cc:1234", verify.configured_device(config, "chromecast")["host"])

    def test_versions_are_taken_from_each_actual_project(self):
        self.assertGreater(verify.expected_version("chromecast"), 17)
        self.assertGreater(verify.expected_version("sony"), 13)


if __name__ == "__main__": unittest.main()
