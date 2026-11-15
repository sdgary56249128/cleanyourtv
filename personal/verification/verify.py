#!/usr/bin/env python3
"""Build and verify both personal launchers; device addresses stay in work/tv-verification.json."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.gathin.tvhome"
TARGETS = {
    "chromecast": {"project": "chromecast-font-home", "model": "Chromecast", "home": "FontHomeActivity", "settings": "FontSettingsActivity"},
    "sony": {"project": "sony-video-home", "model": "BRAVIA 4K VH22", "sku": "KM-55X85L", "home": "VideoHomeActivity", "settings": "VideoLauncherSettingsActivity"},
}
MODES = ["default", "off", "subtle", "standard"]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    stage = path.with_suffix(path.suffix + ".part")
    stage.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    stage.replace(path)


def expected_version(name):
    source = (ROOT / "personal" / TARGETS[name]["project"] / "app/build.gradle").read_text()
    match = re.search(r"versionCode\s+(\d+)", source)
    require(match is not None, "No versionCode in project " + name)
    return int(match[1])


def check_identity(name, model, sku=""):
    require(model == TARGETS[name]["model"], "Wrong model for " + name + "; stopped before making changes")
    if "sku" in TARGETS[name]:
        require(sku == TARGETS[name]["sku"], "Wrong Sony SKU; stopped before making changes")



def installed_apk_path(output):
    require(output.startswith("package:"), "Home is not installed or its APK path is unavailable")
    path = output.splitlines()[0].removeprefix("package:")
    require(re.fullmatch(r"/data/app/[A-Za-z0-9_./=+~\-]+\.apk", path) is not None, "Unexpected APK path")
    return path


def check_pose(motion):
    scale, y, height = motion["scale"], motion["translation_y"], motion["height"]
    require(1 <= scale <= 1.101, "Unexpected background scale")
    require(height > 0, "Home was not laid out")
    require(abs(y) <= (scale - 1) * height / 2 + .05, "Wallpaper can expose an uncovered edge")




def configured_device(config, name):
    entry = config.get("devices", {}).get(name, {})
    require(not entry.get("paused", False), name + " is paused in the private configuration; no device command was sent")
    require(isinstance(entry.get("host"), str) and entry["host"], "Missing private device host for " + name + "; see config.example.json")
    return entry


def resolve_endpoint(entry, mdns):
    """Only refresh the endpoint of the explicitly configured paired service."""
    wanted = entry.get("mdns_name")
    if wanted:
        for line in mdns.splitlines():
            parts = line.split()
            if len(parts) == 3 and parts[0] == wanted and parts[1].rstrip(".") == "_adb-tls-connect._tcp":
                return parts[2]
    return entry.get("host", "")


def same_settings(before, after):
    for key in ("settings_fingerprint", "order_fingerprint"):
        require(before[key] == after[key], "Test did not restore " + key)


class Device:
    def __init__(self, name, output):
        # One process per TV so tools/tv.py's environment-based target cannot leak to another TV.
        sys.path.insert(0, str(ROOT / "tools"))
        import tv
        import theme_restart
        self.tv, self.maintenance = tv, theme_restart
        self.dev = tv.connect()
        self.name, self.target, self.output = name, TARGETS[name], output
        self.pending = ROOT / "work/tv-verification-pending" / (name + ".json")
        self.checks = []

    def shell(self, command):
        return self.maintenance.shell(self.dev, command)

    def key(self, value):
        require(re.fullmatch(r"KEYCODE_[A-Z_0-9]+", value) is not None, "Invalid test key")
        self.shell("input keyevent " + value)
        time.sleep(.12)

    def state(self):
        state = json.loads(self.shell("content read --uri content://com.gathin.tvhome.diagnostics/snapshot.json"))
        require(state.get("schema") == 1 and state.get("model") == self.target["model"], "Unexpected diagnostics schema or model")
        return state

    def settled(self, active=True):
        deadline = time.monotonic() + 18
        while time.monotonic() < deadline:
            state = self.state()
            motion = state["motion"]
            if motion["active"] == active and not motion.get("animating", False):
                if not active or motion.get("item_count", 0) > 0:
                    return state
            time.sleep(.2)
        raise RuntimeError("Home motion did not settle")

    def focus(self):
        text = self.shell("dumpsys window")
        return "\n".join(line for line in text.splitlines() if "mCurrentFocus=" in line)

    def wait_screen(self, screen):
        alternatives = [screen]
        if screen == self.target["home"]: alternatives.append(".MainActivity")
        if screen == self.target["settings"]: alternatives.append(".LauncherSettingsActivity")
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            focus = self.focus()
            if PACKAGE in focus and any(item in focus for item in alternatives): return
            time.sleep(.2)
        raise RuntimeError("Screen not reached: " + screen)

    def home(self):
        self.shell("am start -W -n " + PACKAGE + "/.MainActivity -a android.intent.action.MAIN -c android.intent.category.HOME")
        self.wait_screen(self.target["home"])
        return self.settled()

    def tree(self):
        remote = "/data/local/tmp/cyt-verification-ui.xml"
        self.shell("uiautomator dump --compressed " + remote + " >/dev/null")
        try: return ET.fromstring(self.shell("cat " + remote))
        finally: self.shell("rm -f " + remote)

    def selected(self, tree, suffix=None):
        return [n.attrib for n in tree.iter("node") if (suffix is None or n.get("resource-id", "").endswith(suffix))
                and (n.get("focused") == "true" or n.get("selected") == "true")]

    def shot(self, name):
        require(self.tv.screenshot(self.dev, str(self.output / (name + ".png"))) == 0, "Screenshot failed")

    def open_settings(self, through_remote=False):
        self.home()
        if through_remote:
            self.key("KEYCODE_DPAD_UP")
            for _ in range(10):
                if self.selected(self.tree(), "/btnSettings"): break
                self.key("KEYCODE_DPAD_RIGHT")
            else: raise RuntimeError("Settings pill not reachable with D-pad")
            self.key("KEYCODE_DPAD_CENTER")
        else:
            self.shell("am start -W -n " + PACKAGE + "/.MainActivity -a com.gathin.tvhome.action.LAUNCHER_SETTINGS")
        self.wait_screen(self.target["settings"])
        require(not self.settled(False)["motion"].get("animating", False), "Animation continued in settings")

    def open_row(self, suffix):
        self.open_settings()
        for _ in range(45):
            if self.selected(self.tree(), suffix): break
            self.key("KEYCODE_DPAD_DOWN")
        else: raise RuntimeError("Settings row not reachable: " + suffix)
        self.key("KEYCODE_DPAD_CENTER")
        return self.tree()

    def feature_menus(self):
        if self.name == "chromecast":
            for suffix, count, shot in [("/personalFontChoice", 3, "font-picker"),
                                        ("/personalWeeklyChoice", 4, "weekly-picker"),
                                        ("/personalBackupChoice", 3, "backup-menu")]:
                tree = self.open_row(suffix)
                require(len([n for n in tree.iter("node") if n.get("resource-id") == "android:id/text1"]) == count,
                        "Missing choices in " + suffix)
                self.shot(shot)
                self.key("KEYCODE_BACK")
            self.open_row("/personalInfoChoice")
            self.wait_screen("SystemInfoActivity")
            text = "\n".join(n.get("text", "") for n in self.tree().iter("node"))
            require(self.state()["version_name"] in text, "System information shows the wrong launcher version")
            self.key("KEYCODE_DPAD_CENTER")
            self.shot("system-info-refreshed")
            self.key("KEYCODE_BACK")
            profile = json.loads(self.shell("content read --uri content://com.gathin.tvhome.profile/snapshot.json"))
            expected = sorted(p.name for p in (ROOT / "personal/chromecast-space/wallpapers").glob("*.jpg"))
            require(sorted(profile["wallpapers"]) == expected, "Chromecast wallpaper inventory differs from project assets")
            self.checks.append("Font, weekly, backup menus, system information refresh and wallpaper inventory available")
        else:
            tree = self.open_row("/remoteKeysChoice")
            require(len([n for n in tree.iter("node") if n.get("resource-id") == "android:id/text1"]) == 6,
                    "Sony six-button menu is incomplete")
            self.shot("six-shortcuts")
            self.key("KEYCODE_BACK")
            self.open_row("/personalVideoChoice")
            self.wait_screen("VideoSettingsActivity")
            self.shot("video-settings")
            self.key("KEYCODE_BACK")
            self.checks.append("Sony six-button and local video settings available without changing them")

    def depth_picker(self):
        self.open_settings()
        for _ in range(45):
            if self.selected(self.tree(), "/personalDepthChoice"): break
            self.key("KEYCODE_DPAD_DOWN")
        else: raise RuntimeError("Wallpaper depth row not reachable with D-pad")
        self.key("KEYCODE_DPAD_CENTER")
        tree = self.tree()
        rows = [n for n in tree.iter("node") if n.get("resource-id") == "android:id/text1"]
        require(len(rows) == len(MODES), "Depth picker does not expose every mode")
        return tree

    def choose_mode(self, mode):
        require(mode in MODES, "Invalid depth mode")
        tree = self.depth_picker()
        for _ in range(8):
            selected = self.selected(tree, "/text1")
            rows = [n.attrib for n in tree.iter("node") if n.get("resource-id") == "android:id/text1"]
            if not selected:
                self.key("KEYCODE_DPAD_DOWN")
                tree = self.tree()
                continue
            index = next(i for i, row in enumerate(rows) if row["text"] == selected[0]["text"])
            target = MODES.index(mode)
            if index == target:
                self.key("KEYCODE_DPAD_CENTER")
                self.wait_screen(self.target["settings"])
                self.key("KEYCODE_BACK")
                self.wait_screen(self.target["home"])
                return self.settled()
            self.key("KEYCODE_DPAD_DOWN" if index < target else "KEYCODE_DPAD_UP")
            tree = self.tree()
        raise RuntimeError("Could not select depth mode")

    def recover(self):
        if not self.pending.exists(): return
        journal = json.loads(self.pending.read_text())
        require(journal["model"] == self.target["model"] and journal["mode"] in MODES, "Invalid test recovery journal")
        print("Restoring the original wallpaper choice", flush=True)
        state = self.choose_mode(journal["mode"])
        require(state["motion"]["saved_mode"] == journal["mode"], "Original depth mode was not restored")
        self.pending.unlink()

    def backup(self):
        backup = ROOT / "backup" / self.output.parent.name / self.name
        backup.mkdir(parents=True, exist_ok=True)
        for filename, command in {
            "packages.txt": "pm list packages", "disabled.txt": "pm list packages -d", "getprop.txt": "getprop",
            "secure.txt": "settings list secure", "global.txt": "settings list global", "system.txt": "settings list system",
            "theme.json": "content read --uri content://com.gathin.tvhome.theme/config.json",
            "package.txt": "dumpsys package " + PACKAGE,
        }.items():
            (backup / filename).write_text(self.shell(command), encoding="utf-8")
        path = installed_apk_path(self.shell("pm path " + PACKAGE))
        self.dev.pull(path, str(backup / "previous.apk"), read_timeout_s=90)
        # App-only external files include current photos/video; VLC/NAS data are outside this directory.
        self.dev.pull("/sdcard/Android/data/" + PACKAGE + "/files", str(backup / "files"), read_timeout_s=180)
        if self.name == "chromecast":
            (backup / "profile.json").write_text(self.shell("content read --uri content://com.gathin.tvhome.profile/snapshot.json"))
        return str(backup.relative_to(ROOT))

    def apk_matches(self, apk):
        path = installed_apk_path(self.shell("pm path " + PACKAGE))
        return self.shell("md5sum " + path).split()[0] == hashlib.md5(apk.read_bytes()).hexdigest()

    def install(self, apk):
        backup = self.backup()
        user = self.shell("am get-current-user")
        require(user.isdecimal(), "Unknown Android user")
        flag = self.shell("settings --user " + user + " get secure accessibility_enabled")
        saved = self.maintenance.components(self.shell("settings --user " + user + " get secure enabled_accessibility_services"))
        remote = "/data/local/tmp/cyt-verified-home.apk"
        try:
            self.dev.push(str(apk), remote, read_timeout_s=90)
            require(self.shell("md5sum " + remote).split()[0] == hashlib.md5(apk.read_bytes()).hexdigest(), "APK upload checksum failed")
            result = self.shell("pm install -r " + remote)
            require(result.strip() == "Success", "TV refused installation: " + result)
        finally: self.shell("rm -f " + remote)
        if saved:
            current = self.maintenance.components(self.shell("settings --user " + user + " get secure enabled_accessibility_services"))
            saved.update(current)
            self.shell(self.maintenance.settings_command(int(user), saved, 1 if flag == "1" else 0))
        self.maintenance.restart(self.dev)
        require(self.apk_matches(apk), "Installed APK is different from the tested build")
        return backup

    def verify(self):
        self.shell("input keyevent KEYCODE_WAKEUP")
        self.home()
        self.recover()
        before = self.settled()
        require(before["motion"].get("image_ready"), "No wallpaper image to exercise")
        theme = self.shell("content read --uri content://com.gathin.tvhome.theme/config.json")
        disabled = self.shell("pm list packages -d")
        user = self.shell("am get-current-user")
        helpers = self.maintenance.components(self.shell("settings --user " + user + " get secure enabled_accessibility_services"))
        self.open_settings(through_remote=True)
        self.shot("settings")
        self.checks.append("D-pad settings entry and animation paused while Home hidden")
        self.feature_menus()
        original = before["motion"]["saved_mode"]
        write_json(self.pending, {"model": self.target["model"], "mode": original})
        try:
            state = self.choose_mode("standard")
            require(state["motion"]["effective_mode"] != "off", "Reduced motion or animations are disabled; enable them before testing depth")
            near = self.home()["motion"]
            require(near["progress"] < .01, "Home did not return to the first row")
            check_pose(near)
            self.shot("home-near")
            for _ in range(35):
                self.key("KEYCODE_DPAD_DOWN")
                far = self.settled()["motion"]
                if far["progress"] >= .99: break
            else: raise RuntimeError("Could not reach the last grid row")
            check_pose(far)
            require(far["scale"] < near["scale"] - .04, "Down navigation did not move the wallpaper away")
            require(far["translation_y"] < near["translation_y"], "Wallpaper did not follow vertical navigation")
            self.shot("home-far")
            frames = far["frames"]
            time.sleep(2)
            idle = self.settled()["motion"]
            require(idle["frames"] == frames and not idle["animating"], "Wallpaper keeps animating while idle")
            self.checks.append("Last row zooms out, stays covered and stops animating when idle")
            for _ in range(35):
                self.key("KEYCODE_DPAD_UP")
                back = self.settled()["motion"]
                if back["progress"] < .01: break
            else: raise RuntimeError("Could not return to the top row")
            require(abs(back["scale"] - near["scale"]) < .002, "Up navigation did not return to the original camera scale")
            self.checks.append("Up navigation brings wallpaper close again")
            off = self.choose_mode("off")["motion"]
            require(abs(off["scale"] - 1) < .001 and abs(off["translation_y"]) < .001, "Off mode is not a still, uncropped wallpaper")
            self.key("KEYCODE_DPAD_DOWN")
            off_after = self.settled()["motion"]
            require(off_after["scale"] == 1 and off_after["translation_y"] == 0, "Off mode moves during navigation")
            self.checks.append("Off mode disables both zoom and parallax")
            self.measurements = {"near": near, "far": far, "idle": idle, "back": back, "off": off_after}
        finally:
            # Leave the journal in place if the transport disappears; the next run restores first.
            self.recover()
        after = self.home()
        same_settings(before, after)
        require(self.shell("content read --uri content://com.gathin.tvhome.theme/config.json") == theme, "Test changed the theme")
        require(self.shell("pm list packages -d") == disabled, "Test changed enabled packages")
        current = self.maintenance.components(self.shell("settings --user " + user + " get secure enabled_accessibility_services"))
        require(helpers.keys() <= current.keys(), "Enabled key helper was removed")
        bound = self.maintenance.bound_services(self.shell("dumpsys activity services " + PACKAGE), int(user))
        require({key for key in helpers if key.startswith(PACKAGE + "/")} <= bound, "Home key helper is not bound")
        self.shot("home-restored")
        self.checks.append("Original depth choice, settings, order, theme, disabled apps and key helpers preserved")
        return {"status": "passed", "version_code": after["version_code"], "checks": self.checks,
                "measurements": self.measurements, "restored_mode": after["motion"]["saved_mode"]}


def worker(name, output, install):
    result = {"status": "failed"}
    device = None
    try:
        output.mkdir(parents=True, exist_ok=True)
        device = Device(name, output)
        model = device.shell("getprop ro.product.model")
        sku = device.shell("getprop ro.boot.product.hardware.sku") if "sku" in TARGETS[name] else ""
        check_identity(name, model, sku)
        apk = ROOT / "personal" / TARGETS[name]["project"] / "app/build/outputs/apk/release/app-release.apk"
        require(apk.is_file(), "Build the personal APK before device verification")
        backup = None
        if not device.apk_matches(apk):
            require(install, "Installed APK is stale; rerun with --install to back up and install the tested build")
            backup = device.install(apk)
        require(device.state()["version_code"] == expected_version(name), "Installed version does not match source")
        result = device.verify()
        if backup: result["backup"] = backup
    except Exception as error:
        result["error"] = str(error)
        if error.__context__ is not None: result["earlier_error"] = str(error.__context__)
        if device:
            result["checks_completed"] = device.checks
            result["pending_restore"] = device.pending.exists()
    finally:
        if device: device.dev.close()
        write_json(output / "result.json", result)
    print(name + ": " + result["status"] + (" · " + result.get("error", "") if result.get("error") else ""), flush=True)
    return 0 if result["status"] == "passed" else 1


def command(log, args, env=None, cwd=ROOT):
    print("Running " + log.stem, flush=True)
    with log.open("w") as stream:
        completed = subprocess.run(args, cwd=cwd, env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=1800)
    require(completed.returncode == 0, "Failed " + log.stem + "; see " + str(log))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", choices=["all", *TARGETS], default="chromecast")
    parser.add_argument("--config", type=Path, default=ROOT / "work/tv-verification.json")
    parser.add_argument("--offline", action="store_true", help="build and host tests only; no TV contact")
    parser.add_argument("--skip-build", action="store_true", help="reuse local APKs; device checks still require the exact installed APK")
    parser.add_argument("--skip-host-tests", action="store_true", help="skip repository and profile Python tests")
    parser.add_argument("--install", action="store_true", help="back up and install a stale APK before device checks")
    parser.add_argument("--worker", choices=TARGETS, help=argparse.SUPPRESS)
    parser.add_argument("--output", type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.worker: return worker(args.worker, args.output, args.install)
    selected = list(TARGETS) if args.device == "all" else [args.device]
    stamp = "verification-" + dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    output = ROOT / "work/verification" / stamp
    output.mkdir(parents=True, exist_ok=True)
    config = json.loads(args.config.read_text()) if args.config.exists() else {}
    env = os.environ.copy()
    for key, value in config.get("build_env", {}).items():
        require(key in {"JAVA_HOME", "ANDROID_HOME"}, "Unsupported build environment setting")
        env[key] = value
    report = {"status": "failed", "devices_requested": selected, "hardware": "not run" if args.offline else {}, "output": str(output.relative_to(ROOT))}
    try:
        if not args.skip_host_tests:
            for name, directory, pattern in [("repository-tests", "tests", "test*.py"), ("profile-tests", "personal/device-profiles", "test_*.py"), ("verification-tests", "personal/verification", "test_*.py")]:
                command(output / (name + ".log"), [sys.executable, "-m", "unittest", "discover", "-s", directory, "-p", pattern, "-v"], env)
            report["host_tests"] = "passed"
        else: report["host_tests"] = "not run"
        report["builds"] = {}
        for name in selected:
            project = ROOT / "personal" / TARGETS[name]["project"]
            if not args.skip_build:
                command(output / (name + "-build.log"), [str(project / "gradlew"), "--no-daemon", "--no-watch-fs", "--max-workers=2", "testReleaseUnitTest", "lintRelease", "assembleRelease", "--console=plain"], env, project)
                report["builds"][name] = "passed"
            else: report["builds"][name] = "not run"
        if not args.offline:
            for name in selected:
                entry = configured_device(config, name)
                binary = config.get("adb") or env.get("TV_ADB_BINARY") or shutil.which("adb") or "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
                mdns = subprocess.run([binary, "mdns", "services"], capture_output=True, text=True, timeout=15).stdout if entry.get("mdns_name") else ""
                endpoint = resolve_endpoint(entry, mdns)
                connected = subprocess.run([binary, "connect", endpoint], capture_output=True, text=True, timeout=20)
                require(connected.returncode == 0, "Could not connect to " + name + "; check Wireless debugging")
                device_env = env.copy()
                device_env.update(TV_HOST=endpoint, TV_ADB_BINARY=binary)
                device_env.pop("TV_PORT", None)
                child = [sys.executable, "-I", str(Path(__file__).resolve()), "--worker", name, "--output", str(output / name)]
                if args.install: child.append("--install")
                try: command(output / (name + "-device.log"), child, device_env)
                except RuntimeError:
                    report["hardware"][name] = json.loads((output / name / "result.json").read_text()) if (output / name / "result.json").exists() else {"status": "failed", "error": "Worker failed; see device log"}
                else: report["hardware"][name] = json.loads((output / name / "result.json").read_text())
            require(all(item["status"] == "passed" for item in report["hardware"].values()), "Device verification failed")
        report["status"] = "passed"
    except Exception as error:
        report["error"] = str(error)
    finally:
        write_json(output / "result.json", report)
        (ROOT / "work/verification-latest.txt").write_text(str(output.relative_to(ROOT)) + "\n")
    print("Result " + report["status"] + " · " + str(output / "result.json"), flush=True)
    if report.get("error"): print(report["error"], file=sys.stderr)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
