#!/usr/bin/env python3
"""Strict release gate for internal PS1 with 4 independently audited cores.

Includes complete corresponding GPLv2 source snapshot, pinned build recipe,
Windows x64 DLL and SHA256. No commercial ROM, BIOS or user saves included.
QA only until a real PC and user-owned game boot test passes.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path
from okubi_phase80_rc_preflight import (
    WINDOWS_ZIP, CheckError, verify_checksum, safe_members, required, digest_member)
from okubi_phase81b_source_bundle import EXPECTED
from okubi_ps1_integrate_source_core import PINNED, REPO, DLL, ARCHIVE, LICENSE, BUILD

ROOT="OKUBI Desktop/"
CORE=ROOT+"app/cores/"
SOURCE=CORE+"source-packages/"
PROVENANCE=SOURCE+"SOURCE-TO-BINARY-MANIFEST.json"

def inspect(archive: Path,checksums: Path,scripts: Path,recipe: Path) -> dict:
    if archive.name != WINDOWS_ZIP:
        raise CheckError("unexpected_windows_zip_name")
    digest=verify_checksum(archive,checksums)
    with zipfile.ZipFile(archive) as z:
        files=safe_members(z)
        for file in (ROOT+"OKUBI Desktop.exe",
                     ROOT+"app/okubi-desktop.jar",
                     ROOT+"app/okubi_libretro_jni.dll",
                     CORE+"CORE-SHA256.properties",
                     PROVENANCE,
                     CORE+DLL, CORE+"licenses/"+LICENSE,
                     SOURCE+ARCHIVE, SOURCE+BUILD):
            required(files,file)
        records=json.loads(z.read(required(files,PROVENANCE)).decode("utf-8"))
        if records.get("status")!="experimental_qa" or records.get("signed_release") is not False:
            raise CheckError("untrusted_release_status")
        data=records.get("provenance")
        base={spec["dll"] for spec in EXPECTED.values()}
        all_names=base|{DLL}
        if not isinstance(data,dict) or set(data)!=all_names:
            raise CheckError("expected_four_source_built_cores")
        sha={}
        for line in z.read(required(files,CORE+"CORE-SHA256.properties")).decode("ascii").splitlines():
            if not line or line.startswith("#"):continue
            if "=" not in line: raise CheckError("malformed_core_manifest")
            k,v=line.split("=",1)
            if k in sha or not re.fullmatch("[a-f0-9]{64}",v):
                raise CheckError("duplicate_or_bad_hash")
            sha[k]=v
        if set(sha)!=all_names:
            raise CheckError("exact_four_core_hashes_required")
        for name,spec in EXPECTED.items():
            file=spec["dll"]
            record=data[file]
            if (record.get("source_repository")!=spec["url"]
                    or record.get("exact_source_commit")!=spec["sha"]):
                raise CheckError("historic_retro_source_changed")
            if (record.get("source_archive")!=SOURCE+spec["archive"]
                    or record.get("build_script")!=SOURCE+spec["script"]):
                raise CheckError("historic_retro_source_missing")
            if digest_member(z,required(files,CORE+file))!=sha[file]:
                raise CheckError("retro_core_hash_mismatch")
            if record.get("dll_sha256")!=sha[file]:
                raise CheckError("provenance_legacy_dll_hash_mismatch")
            if digest_member(z,required(files,SOURCE+spec["archive"])) != record.get("source_archive_sha256"):
                raise CheckError("historic_corresponding_source_hash_mismatch")
            script=required(files,SOURCE+spec["script"])
            if z.read(script)!=(scripts/spec["script"]).read_bytes():
                raise CheckError("historic_recipe_not_reproducible")
            licpath = CORE+"licenses/"+{
                "sameboy":"SameBoy-MIT.txt",
                "mgba":"mGBA-MPL-2.0.txt",
                "supafaust":"Supafaust-GPL-2.0-plus.txt"}[name]
            if digest_member(z,required(files,licpath))!=record.get("license_sha256"):
                raise CheckError("historic_core_license_hash_mismatch")
        ps1=data[DLL]
        if ps1.get("source_repository")!=REPO or ps1.get("exact_source_commit")!=PINNED:
            raise CheckError("wrong_beetle_psx_revision")
        if ps1.get("source_archive")!=SOURCE+ARCHIVE or ps1.get("build_script")!=SOURCE+BUILD:
            raise CheckError("missing_beetle_source_or_build_recipe")
        if sha[DLL]!=ps1.get("dll_sha256") or digest_member(z,required(files,CORE+DLL))!=sha[DLL]:
            raise CheckError("beetle_ps1_core_hash_mismatch")
        if digest_member(z,required(files,SOURCE+ARCHIVE))!=ps1.get("source_archive_sha256"):
            raise CheckError("beetle_source_archive_hash_mismatch")
        if z.read(required(files,SOURCE+BUILD))!=recipe.read_bytes():
            raise CheckError("beetle_recipe_not_reproducible")
        if digest_member(z,required(files,CORE+"licenses/"+LICENSE))!=ps1.get("license_sha256"):
            raise CheckError("beetle_gpl_license_hash_mismatch")
        for path in files:
            normalized=path.lower()
            if normalized.startswith(ROOT.lower()+"app/") and normalized.endswith(
                    (".cue",".chd",".iso",".pbp",".sfc",".gba",".gbc",".vpk",".pkg")):
                raise CheckError("commercial_roms_or_firmware_not_allowed_in_app")
        return {
            "build":"OKUBI_PS1_INTERNAL_QA","platform":"Windows x64",
            "ps1_core":"Beetle PSX Libretro software","ps1_source_sha":PINNED,
            "sha256_zip":digest,"sha256_ps1_dll":sha[DLL],
            "verified_cores":len(sha),"source_archives_verified":len(sha),
            "physical_gameplay_verified":False,"release_stable_authorized":False
        }

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--archive",type=Path,required=True)
    p.add_argument("--checksum",type=Path,required=True)
    p.add_argument("--source-scripts",type=Path,required=True)
    p.add_argument("--ps1-recipe",type=Path,required=True)
    p.add_argument("--report",type=Path,required=True)
    args=p.parse_args()
    try:
        record=inspect(args.archive,args.checksum,args.source_scripts,args.ps1_recipe)
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(json.dumps(record,indent=2)+"\n","utf-8")
        print("PASS: OKUBI native PS1 Windows image, four source-pinned cores verified.")
        print("PS1_DLL_SHA256:",record["sha256_ps1_dll"])
    except (ValueError,OSError,RuntimeError,UnicodeError,zipfile.BadZipFile) as e:
        print("PS1 INTERNAL RELEASE BLOCKED:",str(e))
        raise SystemExit(1)

if __name__=="__main__":
    main()
