#!/usr/bin/env python3
"""Check ACTUAL PS1 side-by-side APK, manifest, debug signature, native cores.

CI cannot sign an upgrade for the existing canonical OKUBI installation
without the owner's signing key. This QA APK is intentionally a new
package identity, so it installs alongside existing OKUBI and QA57.
"""
import argparse
import json
import zipfile
from pathlib import Path
from okubi_phase80_rc_preflight import (
    CheckError, verify_checksum, safe_members, required, checked_output
)
from okubi_qa57_verify import metadata as parse_badging
from okubi_release_guard import parse_signer, ReleaseCheckError

PACKAGE = "bo.nexoplay.app.unified.ps1native"
LABEL = "OKUBI 遊"
APK_NAME = "OKUBI-Android-PS1-Native-Isolated.apk"

def inspect(apk: Path, checksums: Path, aapt: str, apksigner: str) -> dict:
    if apk.name != APK_NAME:
        raise CheckError("wrong_ps1_artifact_identity")
    digest = verify_checksum(apk, checksums)
    badging = checked_output(aapt, "dump", "badging", str(apk))
    identity = parse_badging(badging)
    if identity.get("package") != PACKAGE:
        raise CheckError("ps1_must_not_overwrite_owner_or_qa57")
    if identity.get("versionCode") != 36 or identity.get("minSdk") != 26:
        raise CheckError("unexpected_minimum_android_sdk_or_version")
    if not str(identity.get("versionName", "")).endswith("-ps1native"):
        raise CheckError("wrong_ps1_version_suffix")
    if str(identity.get("label", "")) != LABEL:
        raise CheckError("unexpected_launcher_label")
    signer = checked_output(apksigner, "verify", "--verbose",
                            "--print-certs", str(apk))
    try:
        _, is_debug, has_v2 = parse_signer(signer)
    except ReleaseCheckError as error:
        raise CheckError("invalid_apk_signature") from error
    if not is_debug or not has_v2:
        raise CheckError("unsigned_or_unknown_qa_certificate")
    with zipfile.ZipFile(apk) as z:
        entries = safe_members(z)
        required(entries, "AndroidManifest.xml")
        required(entries, "classes.dex")
        for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            for core in ("sameboy", "mgba", "supafaust", "pcsx_rearmed"):
                required(entries, f"lib/{abi}/lib{core}_libretro_android.so")
            required(entries, f"lib/{abi}/libokubi_sameboy_bridge.so")
            required(entries, f"lib/{abi}/libokubi_ps1_bridge.so")
        for license_name in ("SameBoy-LICENSE.txt","mGBA-LICENSE.txt",
                             "Supafaust-GPL-2.0.txt","Supafaust-SOURCE.txt",
                             "PCSX-ReARMed-GPLv2.txt","PCSX-ReARMed-SOURCE.txt"):
            required(entries, f"assets/licenses/{license_name}")
        result = {"phase": 2, "platform":"android", "package": PACKAGE,
                  "launcher": LABEL, "installable": True, "debug_signed": True,
                  "signable_upgrade_to_existing_app": False,
                  "owner_key_available": False,
                  "target_user_data_modified": False,
                  "physical_phone_tested": False,
                  "abis": 4, "cores": 16,
                  "members": len(entries), "sha256": digest,
                  "qa_only_not_stable": True}
    return result

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--archive", required=True, type=Path)
    p.add_argument("--checksum", required=True, type=Path)
    p.add_argument("--aapt", required=True)
    p.add_argument("--apksigner", required=True)
    p.add_argument("--report", required=True, type=Path)
    args=p.parse_args()
    try:
        report=inspect(args.archive,args.checksum,args.aapt,args.apksigner)
    except (ValueError,OSError,RuntimeError,zipfile.BadZipFile) as error:
        print("PS1 ANDROID BLOCKED:",str(error))
        return 1
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",
                           encoding="utf-8")
    print("PS1 ANDROID APK VERIFIED SHA256:",report["sha256"])
    print("ISOLATED ID:",report["package"])
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
