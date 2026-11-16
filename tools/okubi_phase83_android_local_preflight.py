#!/usr/bin/env python3
"""Fase 83: offline, read-only Android package/signature compatibility check.

Only inspects explicitly supplied APK files using Android SDK public tools.
Never uses adb, installs/uninstalls, opens a keystore or accesses save data.
The optional JSON report contains neither paths nor certificate fingerprints.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from okubi_release_guard import (
    ApkIdentity, CANONICAL_PACKAGE, ReleaseCheckError, examine, validate,
)
from okubi_qa57_verify import QA_PACKAGE


def assess(
    *,
    qa: ApkIdentity | None = None,
    previous: ApkIdentity | None = None,
    candidate: ApkIdentity | None = None,
) -> dict:
    if qa is None and previous is None:
        raise ReleaseCheckError("Debes proporcionar QA57 o la APK original.")
    if qa is not None:
        if (qa.package != QA_PACKAGE or qa.code != 36
                or not qa.verified_scheme or not qa.debug_certificate
                or not qa.name.endswith("-qa57")):
            raise ReleaseCheckError(
                "La APK de prueba no tiene la identidad/firma paralela QA57.")
    if previous is not None:
        if previous.package != CANONICAL_PACKAGE or not previous.verified_scheme:
            raise ReleaseCheckError(
                "La APK de referencia no es el OKUBI canónico con firma válida.")
    if candidate is not None:
        if previous is None:
            raise ReleaseCheckError(
                "No se permite validar actualizaciones sin APK canónica anterior.")
        # Never treat a debug identity or an unrelated certificate as a release.
        validate(candidate, kind="release",
                 expected_name=None,
                 minimum_code=previous.code + 1,
                 pinned_sha256=previous.cert_sha256,
                 previous=previous)
    if previous is None:
        upgrade = "pending_original_canonical_apk"
    elif candidate is None:
        upgrade = "reference_certificate_observed_but_update_not_tested"
    else:
        upgrade = "certificate_and_version_compatible_on_supplied_apks_only"
    return {
        "phase": 83,
        "operation": "offline_read_only_apk_preflight",
        "android_parallel_qa_verified": qa is not None,
        "canonical_previous_apk_checked": previous is not None,
        "candidate_canonical_update_checked": candidate is not None,
        "canonical_upgrade_preflight": upgrade,
        "previous_version_code": previous.code if previous is not None else None,
        "candidate_version_code": candidate.code if candidate is not None else None,
        "physical_android_installation_tested": False,
        "installed_android_app_replaced": False,
        "original_keystore_used": False,
        "rom_or_save_data_accessed": False,
        "private_paths_or_certificate_fingerprints_in_report": False,
        "public_release_authorized": False,
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--qa-apk", type=Path,
                   help="QA57 APK descargada de GitHub; no instala nada")
    p.add_argument("--original-apk", type=Path,
                   help="Copia privada de la APK canónica anterior, si la tienes")
    p.add_argument("--candidate-update-apk", type=Path,
                   help="Futura APK canónica de actualización, si existe")
    p.add_argument("--aapt", default="aapt")
    p.add_argument("--apksigner", default="apksigner")
    p.add_argument("--report", type=Path,
                   help="JSON no sensible, solo identificadores y estados genéricos")
    args = p.parse_args()
    if args.qa_apk is None and args.original_apk is None:
        p.error("Especifica --qa-apk o --original-apk.")
    try:
        identity_qa = (examine(args.qa_apk, args.aapt, args.apksigner)
                       if args.qa_apk is not None else None)
        identity_original = (
            examine(args.original_apk, args.aapt, args.apksigner)
            if args.original_apk is not None else None)
        identity_candidate = (
            examine(args.candidate_update_apk, args.aapt, args.apksigner)
            if args.candidate_update_apk is not None else None)
        outcome = assess(
            qa=identity_qa, previous=identity_original,
            candidate=identity_candidate)
    except (ReleaseCheckError, OSError, ValueError):
        print("OKUBI FASE 83: RECHAZADO — identidad o certificado no comprobado.",
              file=sys.stderr)
        return 1
    if args.report is not None:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(outcome, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")
    print("OKUBI FASE 83: inspección de APK completada sin instalar nada.")
    print("Estado de actualización:", outcome["canonical_upgrade_preflight"])
    print("Publicación autorizada: NO")
    return 0


if __name__ == "__main__":
    sys.exit(main())
