#!/usr/bin/env python3
"""OKUBI Phase 70: offline, fail-closed checks before reviewing a public release.

This checks committed source paths/configuration; it never reads private user
directories, installs applications, uses tokens, or signs a release. A passing
result is NOT permission to publish: hardware and license gates stay manual.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path, PurePosixPath

BLOCKED_SUFFIXES = frozenset({
    ".apk", ".aab", ".exe", ".dll", ".so", ".jar", ".zip", ".7z", ".rar",
    ".p12", ".pfx", ".pem", ".key", ".jks", ".keystore", ".kdbx",
    ".gba", ".gb", ".gbc", ".nes", ".sfc", ".smc", ".n64", ".z64",
    ".iso", ".chd", ".cue", ".cso", ".rom", ".sav", ".srm", ".rtc",
    ".state", ".okubisave", ".sqlite", ".sqlite3", ".db", ".bin",
})
BLOCKED_DIRS = frozenset({
    "bios", "firmware", "roms", "saves", "userdata", "personal-roms",
    "keystores", "private-keys",
})
SECRET_PATTERNS = (
    re.compile(r"\b(?:ghp|gho|ghu|ghr|github_pat)_[a-zA-Z0-9_]{20,}\b"),
    re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}\b"),
    re.compile(r"-----BEGIN (?:RSA |OPENSSH |EC )?" + r"PRIVATE KEY-----"),
)
MANDATORY_FILES = (
    ".gitignore",
    "android-app/app/build.gradle",
    "android-app/app/src/main/AndroidManifest.xml",
    ".github/workflows/okubi-android-migrated-qa.yml",
    ".github/workflows/okubi-desktop.yml",
    "desktop/QA57-LAUNCHER.cmd",
    "retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java",
)

def tracked_paths(root: Path) -> list[str]:
    output = subprocess.check_output(
        ["git", "-C", str(root), "ls-files", "-z"], stderr=subprocess.DEVNULL
    )
    return [item.decode("utf-8", "replace") for item in output.split(b"\0") if item]


def inspect_paths(root: Path, paths: list[str]) -> list[str]:
    issues: list[str] = []
    for name in paths:
        rel = PurePosixPath(name)
        if (rel.is_absolute() or ".." in rel.parts
                or name.startswith("/") or "\\" in name):
            issues.append("invalid_tracked_path")
            continue
        file = root.joinpath(*rel.parts)
        if (rel.suffix.lower() in BLOCKED_SUFFIXES
                or rel.name.lower().startswith(".env")
                or any(part.lower() in BLOCKED_DIRS for part in rel.parts[:-1])):
            issues.append("private_or_binary_file_tracked")
            continue
        if file.is_symlink():
            issues.append("symlink_tracked")
            continue
        if not file.is_file():
            issues.append("tracked_file_missing")
            continue
        if file.stat().st_size > 2_000_000:
            issues.append("unexpected_large_source")
            continue
        try:
            content = file.read_text(encoding="utf-8")
        except UnicodeError:
            issues.append("non_utf8_tracked_content")
            continue
        if any(regex.search(content) for regex in SECRET_PATTERNS):
            issues.append("possible_embedded_secret")
    return issues


def inspect_release_contract(root: Path) -> list[str]:
    issues = []
    for name in MANDATORY_FILES:
        if not (root / name).is_file():
            issues.append("mandatory_release_source_missing")
    if issues:
        return issues
    gradle = (root / "android-app/app/build.gradle").read_text(encoding="utf-8")
    manifest = (root / "android-app/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    android_ci = (root / ".github/workflows/okubi-android-migrated-qa.yml").read_text(encoding="utf-8")
    win_ci = (root / ".github/workflows/okubi-desktop.yml").read_text(encoding="utf-8")
    launcher = (root / "desktop/QA57-LAUNCHER.cmd").read_text(encoding="utf-8")
    ignored = (root / ".gitignore").read_text(encoding="utf-8")

    if not re.search(r"(?m)^\s*applicationId\s+['\"]bo\.nexoplay\.app\.unified['\"]", gradle):
        issues.append("android_canonical_package_changed")
    if "applicationIdSuffix '.qa57'" not in gradle:
        issues.append("android_qa_package_not_isolated")
    if not re.search(r"\bminSdk\s+26\b", gradle):
        issues.append("android_minimum_sdk_changed")
    if not re.search(r"\bversionCode\s+([3-9][0-9]|[1-9][0-9]{2,})\b", gradle):
        issues.append("android_version_code_downgrade")
    if ("OKUBI_KEYSTORE_PATH" not in gradle
            or "OKUBI_KEYSTORE_PASSWORD" not in gradle
            or "signingConfigs.unifiedOwner" not in gradle):
        issues.append("android_owner_signing_contract_missing")
    if 'android:allowBackup="false"' not in manifest:
        issues.append("android_backup_isolation_changed")
    if ("-PokubiQaSideBySide=true" not in android_ci
            or "bo.nexoplay.app.unified.qa57" not in android_ci
            or "apksigner" not in android_ci
            or "SHA256SUMS" not in android_ci):
        issues.append("android_qa_verification_missing")
    if ("--verify-windows-release" not in win_ci
            or "SHA256SUMS" not in win_ci
            or "Forbidden personal artifact found in Windows ZIP" not in win_ci):
        issues.append("windows_package_verification_missing")
    if 'set "APPDATA=' not in launcher or "OKUBI-QA57" not in launcher:
        issues.append("windows_qa_user_data_not_isolated")
    for expected in ("*.apk", "*.gba", "*.sav", "*.okubisave", "*.jks", ".env"):
        if expected not in ignored:
            issues.append("gitignore_sensitive_rule_missing")
    for ci in (android_ci, win_ci):
        if re.search(r"(?m)^\s*pull_request_target\s*:", ci):
            issues.append("unsafe_privileged_pr_trigger")
        if re.search(r"(?m)^\s*permissions:\s*write-all\s*$", ci):
            issues.append("unsafe_workflow_permissions")
    return issues


def audit(root: Path, paths: list[str] | None = None) -> dict:
    root = root.resolve(strict=True)
    names = paths if paths is not None else tracked_paths(root)
    issues = inspect_paths(root, names) + inspect_release_contract(root)
    return {
        "phase": 70,
        "tracked_files_examined": len(names),
        "automated_release_gate": "pass" if not issues else "fail",
        "failed_check_codes": sorted(set(issues)),
        "manual_gates": {
            "android_real_device_controls_audio_and_saves": "pending",
            "windows_real_device_controller_audio_and_saves": "pending",
            "third_party_licenses_and_corresponding_source": "pending",
            "owner_release_signing_identity": "pending",
            "review_and_explicit_merge_approval": "pending",
        },
        "public_release_authorized": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.root)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("OKUBI Phase70 automated gate:", result["automated_release_gate"])
    print("Files examined:", result["tracked_files_examined"])
    print("Check codes:", result["failed_check_codes"])
    print("Manual release gates remain PENDING; release is NOT authorized.")
    return 1 if result["failed_check_codes"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
