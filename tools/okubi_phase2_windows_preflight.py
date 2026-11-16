#!/usr/bin/env python3
"""Release-gate verification of a REAL Windows jpackage EXE/ZIP.

Uses verified exact pinned corresponding-source packages from Phase81
rather than the OBSOLETE Phase79 buildbot DLL hashes. Never disables
checksum checks, omits source notices, reads player files, or promotes
unsigned QA to a stable public release.
"""
from __future__ import annotations
import argparse
import json
import re
import zipfile
from pathlib import Path
from okubi_phase80_rc_preflight import (
    WINDOWS_ZIP, CheckError, verify_checksum, safe_members,
    required, digest_member,
)
from okubi_phase81b_source_bundle import EXPECTED

ROOT="OKUBI Desktop/"
APP=ROOT+"app/"
CORES=APP+"cores/"
SOURCE=CORES+"source-packages/"
PROVENANCE=SOURCE+"SOURCE-TO-BINARY-MANIFEST.json"

def inspect(archive: Path, checksums: Path, source_scripts: Path) -> dict:
    if archive.name!=WINDOWS_ZIP:
        raise CheckError("unexpected_windows_archive_name")
    zip_sha=verify_checksum(archive,checksums)
    with zipfile.ZipFile(archive) as z:
        files=safe_members(z)
        for name in (ROOT+"OKUBI Desktop.exe",APP+"okubi-desktop.jar",
                     APP+"okubi_libretro_jni.dll",
                     APP+"RUN-QA57-ISOLATED.cmd",
                     APP+"QA57-READ-ME-FIRST.md",
                     CORES+"CORE-SHA256.properties",PROVENANCE):
            required(files,name)
        app_launcher=z.read(required(files,APP+"RUN-QA57-ISOLATED.cmd"))
        if b"OKUBI-QA57" not in app_launcher or b'set "APPDATA=' not in app_launcher:
            raise CheckError("test_profile_must_remain_isolated")
        records=json.loads(z.read(required(files,PROVENANCE)).decode("utf-8"))
        if records.get("status")!="experimental_qa" or records.get("signed_release") is not False:
            raise CheckError("untrusted_binary_source_manifest")
        entries=records.get("provenance")
        if not isinstance(entries,dict) or set(entries)!={s["dll"] for s in EXPECTED.values()}:
            raise CheckError("missing_one_or_more_corresponding_sources")
        hashes={}
        properties={}
        manifest=z.read(required(files,CORES+"CORE-SHA256.properties")).decode("ascii")
        for line in manifest.splitlines():
            if not line or line.startswith("#"):continue
            if "=" not in line:raise CheckError("malformed_checksum_manifest")
            name, value=line.split("=",1)
            if name in properties:raise CheckError("duplicate_core_manifest_key")
            properties[name]=value.strip()
        if set(properties)!=set(entries):raise CheckError("incomplete_core_checksum_manifest")
        for name, spec in EXPECTED.items():
            dll=spec["dll"]
            record=entries[dll]
            if record.get("exact_source_commit")!=spec["sha"] or record.get("source_repository")!=spec["url"]:
                raise CheckError("incorrect_upstream_source_revision")
            binary_sha=digest_member(z,required(files,CORES+dll))
            if binary_sha!=record.get("dll_sha256") or binary_sha!=properties[dll]:
                raise CheckError("source_built_core_checksum_mismatch")
            source_path=SOURCE+spec["archive"]
            if record.get("source_archive")!=source_path:
                raise CheckError("unrecognized_corresponding_source_archive")
            if digest_member(z,required(files,source_path))!=record.get("source_archive_sha256"):
                raise CheckError("source_archive_hash_mismatch")
            script_path=SOURCE+spec["script"]
            if record.get("build_script")!=script_path:
                raise CheckError("missing_exact_source_build_recipe")
            installed=z.read(required(files,script_path))
            official=(source_scripts/spec["script"]).read_bytes()
            if installed!=official:raise CheckError("build_recipe_does_not_match_audited_code")
            license_path=CORES+"licenses/"+{
                "sameboy":"SameBoy-MIT.txt",
                "mgba":"mGBA-MPL-2.0.txt",
                "supafaust":"Supafaust-GPL-2.0-plus.txt",
            }[name]
            if digest_member(z,required(files,license_path))!=record.get("license_sha256"):
                raise CheckError("corresponding_source_license_hash_mismatch")
            hashes[dll]=binary_sha
        required(files,CORES+"licenses/THIRD-PARTY-SOURCES.txt")
        required(files,CORES+"PHASE81-SOURCE-BUILT-QA.txt")
        return {"phase":2,"platform":"windows","qa_integrity":"pass",
                "zip_sha256":zip_sha,"cores_verified":len(hashes),
                "source_archives_verified":len(hashes),
                "core_sha256":hashes,"zip_entries_examined":len(files),
                "updatable_owner_signature_verified":False,
                "physical_pc_qa_verified":False,
                "public_release_authorized":False}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--archive",required=True,type=Path)
    p.add_argument("--checksum",required=True,type=Path)
    p.add_argument("--source-scripts",required=True,type=Path)
    p.add_argument("--report",required=True,type=Path)
    args=p.parse_args()
    try: report=inspect(args.archive,args.checksum,args.source_scripts)
    except (OSError,ValueError,RuntimeError,UnicodeError,zipfile.BadZipFile) as exc:
        print("PHASE2 WINDOWS BLOCKED:",str(exc))
        return 1
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("PHASE2 WINDOWS PACKAGE VERIFIED:",report["zip_sha256"])
    return 0

if __name__=="__main__":
    raise SystemExit(main())
