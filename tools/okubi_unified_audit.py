#!/usr/bin/env python3
"""Read-only release-preparation audit for OKUBI Android + Desktop.

This tool does not build, merge, edit files, touch user saves, or sign APKs.
It checks that the full stacked branch contains core Phase 6-52 contracts
before a draft PR is considered for review.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path
from typing import Iterable

ANDROID_APP_ID = "bo.nexoplay.app.unified"
REQUIRED_FILES = (
    ".github/workflows/okubi-desktop.yml",
    ".github/workflows/okubi-retro.yml",
    "android-app/app/build.gradle",
    "android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java",
    "android-app/app/src/main/java/bo/nexoplay/app/OkubiMigrationRules.java",
    "retro-module/integrate.py",
    "retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java",
    "retro-module/tests/java/bo/nexoplay/app/retro/OkubiSaveArchiveTest.java",
    "retro-module/tests/test_safe_update_phase30.py",
    "desktop/src/bo/okubi/desktop/OkubiDesktop.java",
    "desktop/src/bo/okubi/desktop/OnlineCoverService.java",
    "desktop/src/bo/okubi/desktop/CinemaHeroBackdrop.java",
    "desktop/src/bo/okubi/desktop/DesktopGameHubPanel.java",
    "desktop/src/bo/okubi/desktop/DesktopGameHubInput.java",
    "desktop/src/bo/okubi/desktop/DesktopControllerFocusGate.java",
    "desktop/src/bo/okubi/desktop/DesktopSaveVault.java",
    "desktop/src/bo/okubi/desktop/DesktopSmartSaveGuard.java",
    "desktop/src/bo/okubi/desktop/DesktopCrossSave.java",
    "desktop/src/bo/okubi/desktop/DesktopCloudSave.java",
    "desktop/tests/bo/okubi/desktop/DesktopCrossSaveTest.java",
    "desktop/tests/bo/okubi/desktop/DesktopSaveVaultTest.java",
    "desktop/tests/bo/okubi/desktop/DesktopSaveVaultStabilityTest.java",
    "desktop/tests/bo/okubi/desktop/DesktopSmartSaveGuardTest.java",
    "desktop/tests/bo/okubi/desktop/DesktopControllerFocusGateTest.java",
    "desktop/tests/bo/okubi/desktop/EmbeddedCoreTest.java",
    "desktop/build-bundled-cores.ps1",
)
PERSONAL_EXTENSIONS = {".gba", ".gb", ".gbc", ".sfc", ".smc",
                       ".nes", ".rom", ".sav", ".srm", ".rtc", ".okubisave",
                       ".state", ".dsv"}
PROHIBITED_PATH_SEGMENTS = {"vidas"}


def unsafe_changes(paths: Iterable[str]) -> list[str]:
    """Only pathnames, never user data bytes. Case-insensitive and cross-OS."""
    violations: list[str] = []
    for raw in paths:
        normalized = raw.replace("\\", "/").strip("/")
        parts = normalized.casefold().split("/")
        if any(part in PROHIBITED_PATH_SEGMENTS for part in parts):
            violations.append(f"VIDAS must remain isolated: {raw}")
        if Path(normalized).suffix.casefold() in PERSONAL_EXTENSIONS:
            violations.append(f"ROM or save artifact must not be committed: {raw}")
        if "appdata" in parts or ".okubi" in parts:
            violations.append(f"Personal application data path in commit: {raw}")
    return violations


def changed_paths(root: Path, base: str) -> list[str]:
    proc = subprocess.run(
        ["git", "-C", str(root), "diff", "--name-only", "-z",
         "--diff-filter=ACMR", f"{base}...HEAD"],
        check=True, capture_output=True)
    return [p.decode("utf-8", errors="replace") for p in proc.stdout.split(b"\0") if p]


def inspect(root: Path, paths: Iterable[str] = ()) -> dict:
    root = root.resolve()
    issues: list[str] = []
    for relative in REQUIRED_FILES:
        if not (root / relative).is_file():
            issues.append(f"Required phase component missing: {relative}")

    inputs = {
        "gradle": "android-app/app/build.gradle",
        "android_ci": ".github/workflows/okubi-retro.yml",
        "windows_ci": ".github/workflows/okubi-desktop.yml",
        "desktop": "desktop/src/bo/okubi/desktop/OkubiDesktop.java",
        "vault": "desktop/src/bo/okubi/desktop/DesktopSaveVault.java",
    }
    code = {key: (root / relative).read_text(encoding="utf-8")
            if (root / relative).is_file() else ""
            for key, relative in inputs.items()}

    contracts = {
        "Android app ID unchanged": ANDROID_APP_ID in code["gradle"],
        "Android minSdk remains 26+": bool(re.search(r"\bminSdk\s+(?:2[6-9]|[3-9]\d)\b",
                                                      code["gradle"])),
        "Android debuggable beta (owner signing not bypassed)":
            "signingConfigs" in code["gradle"]
            and "OKUBI_KEYSTORE_PATH" in code["gradle"],
        "Android emulator + APK CI": "assembleDebug" in code["android_ci"]
                                     and "apksigner" in code["android_ci"],
        "Android integrator + Python regressions":
            "retro-module/integrate.py" in code["android_ci"]
            and "unittest discover" in code["android_ci"],
        "Windows executable CI": "jpackage --type app-image" in code["windows_ci"]
                                 and "windows-latest" in code["windows_ci"],
        "Windows and Android use one save archive format":
            "OkubiSaveArchive.java" in code["windows_ci"]
            and "OkubiSaveArchiveTest" in code["android_ci"],
        "Windows integrated Libretro test": "EmbeddedCoreTest" in code["windows_ci"],
        "Windows Cross Save tests": "DesktopCrossSaveTest" in code["windows_ci"],
        "Windows Vault tests": "DesktopSaveVaultTest" in code["windows_ci"]
                               and "DesktopSaveVaultStabilityTest" in code["windows_ci"],
        "Windows smart guard tests": "DesktopSmartSaveGuardTest" in code["windows_ci"],
        "Windows controller isolation tests":
            "DesktopControllerFocusGateTest" in code["windows_ci"],
        "Windows online box art and game hub":
            "showGameHub" in code["desktop"] and "OnlineCoverArtwork" in code["desktop"],
        "Windows Vault prelaunch protection":
            "smartGuard.beforePlay(game)" in code["desktop"]
            and "backupAutomaticIfChanged" in code["vault"],
        "Save Vault only restores after explicit user selection":
            "public synchronized Entry restore(" in code["vault"]
            and "private void restore()" in (root /
                "desktop/src/bo/okubi/desktop/DesktopSaveVaultDialog.java"
                ).read_text(encoding="utf-8") if (root /
                "desktop/src/bo/okubi/desktop/DesktopSaveVaultDialog.java").is_file()
            else False,
    }
    for label, verified in contracts.items():
        if not verified:
            issues.append(f"Integration contract failed: {label}")
    paths = list(paths)
    issues.extend(unsafe_changes(paths))
    return {
        "status": "PASS" if not issues else "FAIL",
        "required_components": len(REQUIRED_FILES),
        "contracts_passed": sum(contracts.values()),
        "contracts_total": len(contracts),
        "changed_paths_inspected": len(paths),
        "contracts": contracts,
        "issues": issues,
        "notes": [
            "Read-only static audit; requires Windows and Android CI for executable verification.",
            "A green CI is not a physical save-restore or hardware controller test.",
            "No branches are merged or marked as release-ready by this audit.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--base", help="Optional git base ref, e.g. origin/main")
    parser.add_argument("--report", type=Path, help="Optional JSON report outside tracked files")
    args = parser.parse_args()
    try:
        changes = changed_paths(args.root, args.base) if args.base else []
        report = inspect(args.root, changes)
    except (OSError, subprocess.CalledProcessError) as error:
        report = {"status": "FAIL", "issues": [f"Cannot run audit: {error}"]}
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False),
                               encoding="utf-8")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
