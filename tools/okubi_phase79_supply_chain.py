#!/usr/bin/env python3
"""Phase 79: offline supply-chain consistency audit (no downloads or user data).

A verified ZIP/DLL hash proves integrity of bytes, not corresponding source,
license compliance, identity of a signing certificate or safe public release.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

EVIDENCE = "tools/okubi_phase79_windows_core_evidence.json"
BUNDLER = "desktop/build-bundled-cores.ps1"
CORES = {
    "sameboy_libretro.dll": ("MIT", "https://github.com/libretro/SameBoy"),
    "mgba_libretro.dll": ("MPL-2.0", "https://github.com/libretro/mgba"),
    "mednafen_supafaust_libretro.dll": (
        "GPL-2.0-or-later", "https://github.com/libretro/supafaust"),
}
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def inspect(evidence: dict, bundler: str) -> dict:
    errors: list[str] = []
    blockers = [
        "exact_corresponding_source_revision_for_each_windows_dll_missing",
        "windows_core_build_recipes_and_modifications_not_established",
        "windows_and_android_third_party_license_review_outstanding",
        "android_canonical_release_certificate_not_verified",
        "physical_cross_save_and_device_regression_not_verified_here",
        "owner_release_approval_not_granted",
    ]
    if (evidence.get("schema") != 1
            or evidence.get("releases_authorized") is not False
            or evidence.get("audit_run") !=
            "https://github.com/Cobybm/okubi-windows/actions/runs/43604912900"
            or evidence.get("audit_consensus") != "success"
            or sorted(evidence.get("audit_runners", [])) !=
            ["ubuntu-latest", "windows-latest"]):
        errors.append("audited_evidence_metadata_invalid")

    if evidence.get("official_buildbot") != (
            "https://buildbot.libretro.com/nightly/windows/x86_64/latest/"):
        errors.append("unapproved_binary_upstream")

    cores = evidence.get("cores", [])
    if not isinstance(cores, list):
        cores = []
        errors.append("core_evidence_not_a_list")
    names = [core.get("name") for core in cores if isinstance(core, dict)]
    if len(cores) != len(CORES) or len(names) != len(CORES) or set(names) != set(CORES):
        errors.append("required_core_evidence_missing_or_duplicate")
    for entry in cores:
        if not isinstance(entry, dict):
            errors.append("invalid_core_evidence")
            continue
        name = entry.get("name")
        if name not in CORES:
            errors.append("unknown_core_identity")
            continue
        license_name, upstream = CORES[name]
        if entry.get("license") != license_name or entry.get("source_repository") != upstream:
            errors.append("core_license_or_source_repository_changed")
        zipsha, dllsha = entry.get("zip_sha256"), entry.get("dll_sha256")
        if (not isinstance(zipsha, str) or not SHA256.fullmatch(zipsha)
                or not isinstance(dllsha, str) or not SHA256.fullmatch(dllsha)):
            errors.append("invalid_sha256_in_evidence")
            continue
        # Require the exact ZIP and DLL pair in ONE PowerShell package row,
        # as well as the runtime check guarding the extracted DLL.
        lines = [line for line in bundler.splitlines()
                 if 'Dll="' + name + '"' in line and "Sha256=@(" in line]
        if len(lines) != 1:
            errors.append("core_bundler_record_missing_or_duplicate")
            continue
        line = lines[0]
        if not all(token in line for token in (
                'Archive="' + name + '.zip"',
                '"' + zipsha + '"',
                'AuditedZipSha256="' + zipsha + '"',
                'AuditedDllSha256="' + dllsha + '"')):
            errors.append("core_bundler_sha_pair_mismatch")
        if (entry.get("corresponding_windows_source_commit") is not None
                or entry.get("corresponding_windows_build_recipe") is not None):
            # No unverified source claims can be slipped in as plain text.
            errors.append("windows_source_provenance_asserted_without_independent_review")
    for expected in (
        "$zipSha -ceq $package.AuditedZipSha256",
        "$sha -cne $package.AuditedDllSha256",
        "Audited ZIP/DLL fingerprint pair mismatch",
        "Official Libretro core SHA-256 mismatch. FAIL CLOSED"):
        if expected not in bundler:
            errors.append("core_runtime_fingerprint_guard_missing")

    return {
        "phase": 79,
        "audit_scope": "source_metadata_only_offline",
        "static_integrity_gate": "pass" if not errors else "fail",
        "issues": sorted(set(errors)),
        "audited_windows_cores": len(CORES),
        "missing_public_release_evidence": blockers,
        "windows_binary_source_provenance": "unverified",
        "android_release_update_certificate": "not_supplied",
        "physical_device_qa": "not_tested_by_this_tool",
        "public_release_authorized": False,
        "merge_performed": False,
    }


def audit(root: Path) -> dict:
    root = root.resolve(strict=True)
    paths = [root / EVIDENCE, root / BUNDLER]
    for file in paths:
        if (file.is_symlink() or not file.is_file()
                or file.stat().st_size > 250_000):
            raise ValueError("missing_or_unsafe_phase79_source")
    return inspect(
        json.loads(paths[0].read_text(encoding="utf-8")),
        paths[1].read_text(encoding="utf-8"),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--require-public-release", action="store_true",
                        help="Fail closed until all manual evidence and approvals are complete")
    args = parser.parse_args()
    try:
        result = audit(args.root)
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        result = inspect({}, "")
        result["issues"].append("phase79_audit_unavailable")
        result["static_integrity_gate"] = "fail"
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("OKUBI Phase79 static integrity:", result["static_integrity_gate"])
    print("Remaining release blockers:", len(result["missing_public_release_evidence"]))
    print("Public distribution authorized:", result["public_release_authorized"])
    if args.require_public_release and not result["public_release_authorized"]:
        print("BLOCKED: certificate, hardware, corresponding source and approval required")
        return 2
    return 0 if result["static_integrity_gate"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
