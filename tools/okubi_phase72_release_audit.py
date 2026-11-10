#!/usr/bin/env python3
"""OKUBI Phase 72 — offline source, licensing and signing readiness audit.

No secrets, user saves, ROMs, APKs or DLLs are opened or uploaded.
Passing the source contract is NOT authorization to distribute binaries.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

CORES = (
    ("SameBoy", "retro-module/build_sameboy.py", "SAMEBOY_COMMIT",
     "https://github.com/libretro/SameBoy.git", "SameBoy/", "LICENSE"),
    ("mGBA", "retro-module/build_mgba.py", "MGBA_COMMIT",
     "https://github.com/libretro/mgba.git", "mgba/", "LICENSE"),
    ("Supafaust", "retro-module/build_supafaust.py", "SUPAFAUST_COMMIT",
     "https://github.com/libretro/supafaust.git", "supafaust/", "COPYING"),
)

class AuditError(Exception):
    pass


def read(root: Path, path: str) -> str:
    try:
        item = root.joinpath(*path.split("/"))
        if item.is_symlink() or not item.is_file() or item.stat().st_size > 200_000:
            raise AuditError("required_source_missing_or_unsafe:" + path)
        return item.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise AuditError("required_source_unreadable:" + path) from error


def ref_from_builder(builder: str, constant: str, upstream: str) -> str:
    m = re.search(r"(?m)^" + constant + r"\s*=\s*[\"']([a-f0-9]{40})[\"']\s*$", builder)
    if not m:
        raise AuditError("source_revision_missing:" + constant)
    if 'UPSTREAM = "' + upstream + '"' not in builder:
        raise AuditError("source_repository_changed:" + constant)
    if ("git" not in builder or "--detach" not in builder or "--depth=1" not in builder):
        raise AuditError("immutable_git_checkout_not_confirmed:" + constant)
    return m.group(1)


def inspect(root: Path) -> dict:
    root = root.resolve(strict=True)
    warnings = []
    errors = []
    pins = {}

    windows = read(root, "desktop/build-bundled-cores.ps1")
    android_gradle = read(root, "android-app/app/build.gradle")
    android_workflow = read(root, ".github/workflows/okubi-android-migrated-qa.yml")
    windows_workflow = read(root, ".github/workflows/okubi-desktop.yml")
    release_guard = read(root, "tools/okubi_release_guard.py")
    signing_helper = read(root, "tools/create_okubi_release_key.ps1")

    for name, path, constant, upstream, upstream_path, license_name in CORES:
        content = read(root, path)
        try:
            revision = ref_from_builder(content, constant, upstream)
            pins[name] = revision
            notice = "https://raw.githubusercontent.com/libretro/" + upstream_path + revision + "/" + license_name
            if notice not in windows:
                errors.append("notice_source_revision_mismatch:" + name)
            if "git" not in content or "license" not in content.lower():
                errors.append("android_source_license_not_handled:" + name)
        except AuditError as err:
            errors.append(str(err))

    windows_latest = "https://buildbot.libretro.com/nightly/windows/x86_64/latest/" in windows
    if windows_latest:
        warnings.append("windows_libretro_binary_exact_source_unverified")
    else:
        # A non-rolling URL does not by itself establish binary provenance.
        warnings.append("windows_libretro_binary_exact_source_still_requires_evidence")

    if "Sha256" not in windows or "Get-FileHash" not in windows:
        errors.append("windows_core_checksum_check_missing")
    if ("CORE-SHA256.properties" not in windows
            or "--verify-windows-release" not in windows_workflow
            or "SHA256SUMS" not in windows_workflow):
        errors.append("windows_integrity_guard_missing")

    if not re.search(r"(?m)^\s*applicationId\s+['\"]bo\.nexoplay\.app\.unified['\"]", android_gradle):
        errors.append("canonical_android_package_changed")
    if "applicationIdSuffix '.qa57'" not in android_gradle:
        errors.append("android_parallel_qa_identity_missing")
    if "versionCode 36" not in android_gradle:
        warnings.append("version_code_changed_verify_installed_app_before_update")
    for env in ("OKUBI_KEYSTORE_PATH", "OKUBI_KEYSTORE_PASSWORD",
                "OKUBI_KEY_ALIAS", "OKUBI_KEY_PASSWORD"):
        if env not in android_gradle:
            errors.append("android_owner_signature_contract_missing:" + env)
    if ("assembleDebug -PokubiQaSideBySide=true" not in android_workflow
            or "apksigner" not in android_workflow):
        errors.append("android_qa_identity_or_verification_missing")
    if ("current.cert_sha256 != previous.cert_sha256" not in release_guard
            or "current.code <= previous.code" not in release_guard
            or "if not pinned_sha256" not in release_guard
            or "debug_certificate" not in release_guard):
        errors.append("android_update_cert_or_version_rejection_missing")
    if ("Test-Path -LiteralPath $target" not in signing_helper
            or "NEVER upload" not in signing_helper
            or "-genkeypair" not in signing_helper):
        errors.append("owner_key_creation_safeguard_missing")

    warnings.extend([
        "physical_android_windows_cross_save_not_reported",
        "owner_android_release_certificate_not_verified",
        "legal_gpl_mpl_mit_and_source_distribution_review_required",
        "explicit_owner_merge_and_release_approval_required",
    ])

    return {
        "phase": 72,
        "automated_checks": "pass" if not errors else "fail",
        "code_provenance": {"android_pinned_revisions": pins,
                            "windows_dll_source_reproducibility": "unverified"},
        "package_identity": "android_canonical_plus_qa_side_by_side",
        "checks_failed": sorted(set(errors)),
        "remaining_release_blockers": sorted(set(warnings)),
        "public_release_authorized": False,
        "release_signing_performed": False,
        "source_binaries_or_user_saves_uploaded": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = inspect(args.root)
    except (AuditError, OSError) as exc:
        print("BLOCKED:", str(exc).split(":")[0])
        return 1
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Phase72 source & signing contract:", result["automated_checks"])
    print("Android pinned source revisions:", len(result["code_provenance"]["android_pinned_revisions"]))
    print("Unresolved manual release gates:", len(result["remaining_release_blockers"]))
    print("Release authorization:", result["public_release_authorized"])
    return 1 if result["checks_failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
