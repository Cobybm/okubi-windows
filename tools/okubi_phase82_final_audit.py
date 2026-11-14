#!/usr/bin/env python3
"""Phase 82 cross-artifact acceptance audit: Windows source-built ZIP + Android QA APK.

Read-only: refuses unknown/modified APK or ZIP, does not install, run games,
touch saves, read signing keys or claim device testing / legal compliance.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import tarfile
import zipfile
from pathlib import Path

from okubi_phase80_rc_preflight import (
    ANDROID_APK, CheckError, android_archive, digest_member, required,
    safe_members, verify_checksum,
)
from okubi_phase81b_source_bundle import (
    EXPECTED, MANIFEST, OUTPUT_NAME, PROVENANCE, README, ROOT, SOURCE_ROOT,
)

REQUIRED_EVIDENCE = {
    "windows_source_built_ci": 43615378972,
    "android_parallel_qa_ci": 43612388104,
    "cross_save_synthetic_ci": 43605014667,
}
MANUAL_GATES = (
    "windows_real_device_gameplay",
    "android_real_device_gameplay",
    "physical_cross_save_backup_restore",
    "canonical_android_signing_and_upgrade",
    "third_party_license_and_corresponding_source_review",
    "explicit_owner_public_release_approval",
)


def digest_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_built_windows(path: Path) -> dict:
    if path.name != OUTPUT_NAME:
        raise CheckError("unexpected_windows_qa_filename")
    with zipfile.ZipFile(path) as archive:
        members = safe_members(archive)
        obj = json.loads(archive.read(required(members, PROVENANCE)))
        if (obj.get("phase") != 81 or obj.get("public_release_authorized") is not False
                or obj.get("signed_release") is not False
                or obj.get("physical_devices_tested") is not False):
            raise CheckError("windows_public_release_claim_not_verified")
        provenance = obj.get("provenance", {})
        if not isinstance(provenance, dict) or set(provenance) != {
            spec["dll"] for spec in EXPECTED.values()
        }:
            raise CheckError("source_built_core_provenance_incomplete")
        raw_manifest = archive.read(required(members, MANIFEST)).decode("ascii")
        manifest = {}
        for line in raw_manifest.splitlines():
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                raise CheckError("windows_dll_manifest_invalid")
            name, expected_sha = line.split("=", 1)
            if name in manifest or not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
                raise CheckError("windows_dll_manifest_invalid")
            manifest[name] = expected_sha
        if set(manifest) != set(provenance):
            raise CheckError("windows_dll_manifest_missing_core")
        for spec in EXPECTED.values():
            dll = spec["dll"]
            record = provenance[dll]
            if (record.get("exact_source_commit") != spec["sha"]
                    or record.get("source_repository") != spec["url"]):
                raise CheckError("core_source_identity_does_not_match_pinned_revision")
            files = [
                (ROOT + dll, "dll_sha256"),
                (SOURCE_ROOT + spec["archive"], "source_archive_sha256"),
            ]
            for name, key in files:
                expected = record.get(key)
                if (not isinstance(expected, str) or not re.fullmatch(r"[a-f0-9]{64}", expected)
                        or digest_member(archive, required(members, name)) != expected):
                    raise CheckError("core_or_source_sha256_mismatch")
            if manifest[dll] != record["dll_sha256"]:
                raise CheckError("windows_core_manifest_does_not_match_payload")
            required(members, SOURCE_ROOT + spec["script"])
            license_name = {
                "sameboy_libretro.dll": "SameBoy-MIT.txt",
                "mgba_libretro.dll": "mGBA-MPL-2.0.txt",
                "mednafen_supafaust_libretro.dll": "Supafaust-GPL-2.0-plus.txt",
            }[dll]
            required(members, ROOT + "licenses/" + license_name)
            if len(archive.read(required(members, SOURCE_ROOT + spec["script"]))) < 100:
                raise CheckError("incomplete_source_build_recipe")
        for key in (
            "OKUBI Desktop/OKUBI Desktop.exe",
            "OKUBI Desktop/app/okubi-desktop.jar",
            "OKUBI Desktop/app/RUN-QA57-ISOLATED.cmd",
            README,
            ROOT + "licenses/THIRD-PARTY-SOURCES.txt",
        ):
            required(members, key)
        launcher = archive.read(required(members,
            "OKUBI Desktop/app/RUN-QA57-ISOLATED.cmd"))
        if b'APPDATA=' not in launcher or b"OKUBI-QA57" not in launcher:
            raise CheckError("isolated_windows_launcher_missing")
        return {
            "zip_members": len(members),
            "source_built_cores": 3,
            "git_revisions_pinned": 3,
            "included_corresponding_source_archives": 3,
            "source_license_files": 3,
            "windows_real_device_tested_by_audit": False,
        }


def verify_manual_evidence(evidence: dict) -> dict:
    if (evidence.get("schema") != 1
            or evidence.get("public_release_authorized") is not False
            or evidence.get("automated_ci") != REQUIRED_EVIDENCE):
        raise CheckError("unrecognized_release_gate_evidence")
    gates = evidence.get("manual_gates")
    if not isinstance(gates, dict) or set(gates) != set(MANUAL_GATES):
        raise CheckError("manual_evidence_gates_missing")
    for gate, status in gates.items():
        if (not isinstance(status, dict)
                or status.get("status") != "pending"
                or status.get("evidence") is not None):
            # The public repo must not fabricate private device QA, legal approval,
            # original Android signing cert or owner's authorization.
            raise CheckError("unreviewed_manual_evidence_assertion")
    return gates


def audit(
    win_zip: Path, win_checksums: Path,
    apk: Path, apk_checksums: Path,
    badging: str, signer: str, evidence: dict
) -> dict:
    if not (win_zip.name == OUTPUT_NAME and apk.name == ANDROID_APK):
        raise CheckError("unexpected_candidate_artifact_filename")
    win_sha = verify_checksum(win_zip, win_checksums)
    android_sha = verify_checksum(apk, apk_checksums)
    win = source_built_windows(win_zip)
    android = android_archive(apk, badging, signer)
    gates = verify_manual_evidence(evidence)
    return {
        "phase": 82,
        "automated_artifact_integrity": "pass",
        "windows_source_built_qa_sha256": win_sha,
        "android_parallel_qa_sha256": android_sha,
        "windows": win,
        "android": android,
        "canonical_android_apk_upgraded": False,
        "manual_gates_not_verified": list(gates),
        "manual_gates_pending": len(gates),
        "source_license_compliance_legally_reviewed": False,
        "physical_cross_save_verified": False,
        "public_release_authorized": False,
        "merged_to_main": False,
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--windows", required=True, type=Path)
    p.add_argument("--windows-checksums", required=True, type=Path)
    p.add_argument("--android", required=True, type=Path)
    p.add_argument("--android-checksums", required=True, type=Path)
    p.add_argument("--android-badging", required=True, type=Path)
    p.add_argument("--android-signer", required=True, type=Path)
    p.add_argument("--evidence", required=True, type=Path)
    p.add_argument("--report", required=True, type=Path)
    p.add_argument("--require-release", action="store_true")
    a = p.parse_args()
    try:
        report = audit(
            a.windows, a.windows_checksums, a.android, a.android_checksums,
            a.android_badging.read_text("utf-8"),
            a.android_signer.read_text("utf-8"),
            json.loads(a.evidence.read_text("utf-8")),
        )
    except (OSError, ValueError, UnicodeError, zipfile.BadZipFile, tarfile.TarError):
        print("FAIL: QA candidate artifact or release evidence rejected")
        return 1
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")
    print("PHASE82 AUTOMATED CROSS-ARTIFACT QA: PASS")
    print("Manual gates pending:", report["manual_gates_pending"])
    print("PUBLIC RELEASE AUTHORIZED: False")
    if a.require_release:
        print("BLOCKED: never publish before explicit device, signing, license and owner checks")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
