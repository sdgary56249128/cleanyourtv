#!/usr/bin/env python3
"""Export, sync or restore public Home profiles through the repository's paired ADB backend.

Use tools/venv/bin/python -I personal/device-profiles/profiles.py --help.
Set TV_HOST and TV_ADB_BINARY for the intended television first.
"""
import argparse
import datetime
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import uuid

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = Path(__file__).resolve().parent
PREFERENCE_KEYS = set("columns rows_below_dock dock_opacity home_idle_clock_minutes wallpaper_rotation_minutes wallpaper_dim animations advanced_effects clock_format wallpaper_choice wallpaper_order open_on_start personal_home_font weekly_wallpaper_mode".split())
REMOTE_CHOICE = re.compile(r"remote_key_choice_(?:hold:)?(?:[1-9][0-9]{0,5}|scan:[1-9][0-9]{0,5})")
PRIVATE = re.compile(r"(?:\b(?:\d{1,3}\.){3}\d{1,3}\b|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}|(?:smb|https?|tailscale)://)", re.I)
THEME_KEYS = set("wallpaper wallpaper_rotation_minutes wallpaper_order wallpaper_dim wallpaper_true_color ambient_glow app_glow dock_apps columns rows_below_dock dock_opacity dock_glass show_inputs_pill live_tv_package stock_home_redirect app_keys remote_key_names launcher_settings_button home_idle_clock_minutes clock_wallpaper clock_wallpaper_dim clock_glow clock_format app_labels launch_actions hidden_apps key_filter inputs_connected_only inputs_cec_state_only wallpaper_captions".split())


def check_public(profile):
    if type(profile.get("schema")) is not int or profile.get("schema") != 1 or not isinstance(profile.get("model"), str):
        raise ValueError("Unsupported profile schema")
    if not isinstance(profile.get("theme"), dict):
        raise ValueError("Profile theme must be an object")
    if not set(profile["theme"]) <= THEME_KEYS:
        raise ValueError("Profile contains an unsupported theme field")
    prefs = profile.get("preferences", {})
    if not isinstance(prefs, dict):
        raise ValueError("Profile preferences must be an object")
    for key, value in prefs.items():
        if key not in PREFERENCE_KEYS and not REMOTE_CHOICE.fullmatch(key):
            raise ValueError("Unsupported Home preference")
        if type(value) not in (str, int, bool):
            raise ValueError("Unsupported preference value")
    if PRIVATE.search(json.dumps(profile, ensure_ascii=False)):
        raise ValueError("Profile contains a network address, URL or email. Review it locally before publishing.")
    allowed = {"schema", "model", "theme", "preferences", "order", "wallpapers", "scope", "captured_date", "launcher"}
    if not set(profile) <= allowed:
        raise ValueError("Unexpected profile fields")


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True, timeout=90).strip()


def save(path, profile):
    payload = json.dumps(profile, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(payload)
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["export", "sync", "restore", "mark-synced"])
    parser.add_argument("device", help="Git profile name, for example chromecast-4k")
    parser.add_argument("--theme-only", action="store_true", help="capture the theme from an older launcher")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,49}", args.device):
        parser.error("device must be a short lowercase profile name")
    path = DIRECTORY / (args.device + ".json")
    sys.path.insert(0, str(ROOT / "tools"))
    import tv
    import theme_restart
    device = tv.connect()
    shell = lambda text: theme_restart.shell(device, text)
    try:
        model = shell("getprop ro.product.model")
        if args.action in {"export", "sync"}:
            if args.theme_only:
                theme = json.loads(shell("content read --uri content://com.gathin.tvhome.theme/config.json"))
                profile = {"schema": 1, "scope": "theme-only", "model": model,
                           "captured_date": datetime.date.today().isoformat(),
                           "theme": {key: value for key, value in theme.items() if key in THEME_KEYS}}
            else:
                profile = json.loads(shell("content read --uri content://com.gathin.tvhome.profile/snapshot.json"))
            package = shell("dumpsys package com.gathin.tvhome")
            version = re.search(r"versionName=([^\s]+)", package)
            code = re.search(r"versionCode=(\d+)", package)
            if version is None or code is None:
                raise ValueError("Could not identify the installed launcher version")
            profile["launcher"] = {"version_name": version[1], "version_code": int(code[1])}
            if profile.get("model") != model:
                raise ValueError("Connected model does not match the snapshot")
            if path.exists():
                previous = json.loads(path.read_text())
                if previous.get("model") != model:
                    raise ValueError("This profile belongs to a different model")
                if previous.get("scope") != "theme-only" and args.theme_only:
                    raise ValueError("A theme-only snapshot cannot replace an existing full profile")
            check_public(profile)
            save(path, profile)
            print(path.relative_to(ROOT))
            if args.action == "sync":
                relative = str(path.relative_to(ROOT))
                git("add", "--", relative)
                changed = subprocess.run(["git", "-C", str(ROOT), "diff", "--cached", "--quiet", "--", relative]).returncode
                if changed == 1:
                    git("commit", "--only", "-m", "profiles: sync " + args.device, "--", relative)
                elif changed != 0:
                    raise RuntimeError("Could not compare the Git profile")
                git("push", "origin", "HEAD")
                commit = git("log", "-1", "--format=%h", "--", relative)
                if not args.theme_only:
                    print(shell("content call --uri content://com.gathin.tvhome.profile --method synced --arg " + commit))
        elif args.action == "restore":
            profile = json.loads(path.read_text())
            check_public(profile)
            if profile.get("scope") == "theme-only":
                raise ValueError("This older snapshot contains the theme only. Install the matching theme before making a full backup.")
            if profile.get("model") != model:
                raise ValueError("Refusing to restore a profile from a different model")
            token = uuid.uuid4().hex
            remote = "/data/local/tmp/cyt-profile-" + token + ".json"
            try:
                subprocess.run([sys.executable, "-I", str(ROOT / "tools/b64push.py"), str(path), remote], check=True)
                shell("content write --uri content://com.gathin.tvhome.profile/incoming/" + token + " < " + remote)
                result = shell("content call --uri content://com.gathin.tvhome.profile --method restore --arg " + token)
                if "ok=true" not in result:
                    raise RuntimeError(result)
                theme_restart.restart(device)
                print("Home profile restored")
            finally:
                shell("rm -f " + remote)
        else:
            relative = str(path.relative_to(ROOT))
            commit = git("log", "-1", "--format=%h", "--", relative)
            if not re.fullmatch(r"[a-f0-9]{7,40}", commit):
                raise ValueError("The profile has no Git commit yet")
            print(shell("content call --uri content://com.gathin.tvhome.profile --method synced --arg " + commit))
    finally:
        device.close()


if __name__ == "__main__":
    try:
        # Serialise profile file and Git-index changes made through this tool.
        (ROOT / "work").mkdir(exist_ok=True)
        with (ROOT / "work/profile-sync.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        sys.exit(str(error))
