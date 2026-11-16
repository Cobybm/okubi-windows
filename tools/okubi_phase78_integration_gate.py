#!/usr/bin/env python3
"""OKUBI Phase 78: integration readiness only, never approval to release.

All inspections operate on tracked source and synthetic contracts. They do not
open user data directories, download ROMs, sign binaries, or modify Git refs.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import okubi_phase70_release_gate as phase70
import okubi_phase72_release_audit as phase72
import okubi_unified_audit as unified


MANUAL_BLOCKERS = (
    "third_party_core_licenses_and_corresponding_windows_binary_source",
    "android_owner_certificate_and_update_path",
    "physical_android_to_windows_cross_save_with_backup",
    "final_windows_and_android_hardware_qa_on_integrated_candidate",
    "explicit_owner_approval_before_merging_or_releasing",
)


def summarize(p70: dict, p72: dict, integrated: dict) -> dict:
    checks = {
        "phase70_private_data_and_package_contract":
            p70.get("automated_release_gate") == "pass"
            and p70.get("public_release_authorized") is False,
        "phase72_source_and_signing_contract":
            p72.get("automated_checks") == "pass"
            and p72.get("public_release_authorized") is False,
        "unified_windows_android_and_save_contract":
            integrated.get("status") == "PASS",
    }
    issues = []
    if not checks["phase70_private_data_and_package_contract"]:
        issues.append("phase70_contract_not_passed_or_release_flag_unsafe")
    if not checks["phase72_source_and_signing_contract"]:
        issues.append("phase72_contract_not_passed_or_release_flag_unsafe")
    if not checks["unified_windows_android_and_save_contract"]:
        issues.append("unified_contract_not_passed")
    # No personal file names, tokens, certificates or ROM paths in the report.
    return {
        "phase": 78,
        "scope": "offline_non_destructive_premerge_source_audit",
        "automated_integration_gate": "pass" if not issues else "fail",
        "checks": checks,
        "failed_check_codes": issues,
        "source_files_examined": p70.get("tracked_files_examined", 0),
        "manual_release_blockers": list(MANUAL_BLOCKERS),
        "ci_build_artifacts_verified_by_this_script": False,
        "hardware_tests_verified_by_this_script": False,
        "public_release_authorized": False,
        "merge_performed": False,
    }


def inspect(root: Path) -> dict:
    root = root.resolve(strict=True)
    tracked = phase70.tracked_paths(root)
    return summarize(
        phase70.audit(root, paths=tracked),
        phase72.inspect(root),
        unified.inspect(root, paths=tracked),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = inspect(args.root)
    except (OSError, ValueError, RuntimeError, phase72.AuditError) as exc:
        report = summarize({}, {}, {})
        report["failed_check_codes"].append("integration_audit_unavailable")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print("Phase 78 integration source gate:", report["automated_integration_gate"])
    print("Source files examined:", report["source_files_examined"])
    print("Pending physical/license/signing gates:", len(MANUAL_BLOCKERS))
    print("Public release authorized: False")
    return 0 if report["automated_integration_gate"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
