#!/usr/bin/env python3
"""Create/verify a source-built OKUBI Windows QA ZIP, never a public release.

Starts with an independently verified unmodified Fase 80 QA ZIP, then swaps
ONLY its three libretro DLLs with locally source-built, ABI-tested DLLs.
Bundles the exact corresponding Git source archives and build recipes.
No ROM, BIOS, user save or signing key is read, installed or modified.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

from okubi_phase80_rc_preflight import (
    WINDOWS_ZIP, CheckError, digest_member, required, safe_members,
    verify_checksum, windows_archive,
)

ROOT = "OKUBI Desktop/app/cores/"
SOURCE_ROOT = ROOT + "source-packages/"
OUTPUT_NAME = "OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip"
EXPECTED = {
    "sameboy": {
        "dll": "sameboy_libretro.dll",
        "sha": "aa158a889a48b538a0302873704a34577c8eb67d",
        "archive": "sameboy-corresponding-source.tar.gz",
        "license": "SameBoy-MIT.txt",
        "script": "phase81_build_windows_sameboy.sh",
        "url": "https://github.com/libretro/SameBoy",
    },
    "mgba": {
        "dll": "mgba_libretro.dll",
        "sha": "7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6",
        "archive": "mgba-corresponding-source.tar.gz",
        "license": "mGBA-MPL-2.0.txt",
        "script": "phase81_build_windows_mgba.sh",
        "url": "https://github.com/libretro/mgba",
    },
    "supafaust": {
        "dll": "mednafen_supafaust_libretro.dll",
        "sha": "642d1d1b6684aa7e306a02a89885f3f5456a5157",
        "archive": "supafaust-corresponding-source.tar.gz",
        "license": "Supafaust-GPL-2.0-or-later.txt",
        "script": "phase81_build_windows_supafaust.sh",
        "url": "https://github.com/libretro/supafaust",
    },
}
PREVIOUS_ALLOWED = set(
    [ROOT + spec["dll"] for spec in EXPECTED.values()] +
    [ROOT + "CORE-SHA256.properties"] +
    [ROOT + "licenses/SameBoy-MIT.txt",
     ROOT + "licenses/mGBA-MPL-2.0.txt",
     ROOT + "licenses/Supafaust-GPL-2.0-plus.txt",
     ROOT + "licenses/THIRD-PARTY-SOURCES.txt"]
)
MANIFEST = ROOT + "CORE-SHA256.properties"
NOTICE = ROOT + "licenses/THIRD-PARTY-SOURCES.txt"
README = ROOT + "PHASE81-SOURCE-BUILT-QA.txt"
PROVENANCE = SOURCE_ROOT + "SOURCE-TO-BINARY-MANIFEST.json"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_bytes(path: Path, *, max_size: int = 100_000_000) -> bytes:
    if not path.is_file() or path.is_symlink():
        raise CheckError("missing_or_unsafe_source_artifact")
    size = path.stat().st_size
    if size < 10 or size > max_size:
        raise CheckError("unexpected_source_artifact_size")
    return path.read_bytes()


def provenance_payload(source_dir: Path, tool_dir: Path) -> tuple[dict, dict[str, bytes]]:
    replacements: dict[str, bytes] = {}
    identities = {}
    for name, spec in EXPECTED.items():
        directory = source_dir / (name + "-artifact")
        archive = spec["archive"]
        dll = spec["dll"]
        script = spec["script"]
        lic = spec["license"]
        checksum_lines = file_bytes(directory / "SHA256SUMS.txt", max_size=2048).decode("utf-8").splitlines()
        expected_hashes = {}
        for line in checksum_lines:
            match = re.fullmatch(r"([0-9a-f]{64})\s+\*?([A-Za-z0-9_.-]+)", line.strip())
            if not match or match.group(2) in expected_hashes:
                raise CheckError("invalid_source_build_hash_manifest")
            expected_hashes[match.group(2)] = match.group(1)
        dll_bytes = file_bytes(directory / dll)
        source_bytes = file_bytes(directory / archive, max_size=140_000_000)
        license_bytes = file_bytes(directory / lic, max_size=120_000)
        recipe_bytes = file_bytes(tool_dir / script, max_size=100_000)
        if expected_hashes.get(dll) != sha(dll_bytes) or expected_hashes.get(archive) != sha(source_bytes):
            raise CheckError("source_built_dll_or_archive_hash_mismatch")
        if expected_hashes.get(lic) != sha(license_bytes):
            raise CheckError("source_build_license_hash_mismatch")
        if name == "mgba" and expected_hashes.get("SOURCE-BUILD-SCRIPT.sh") != sha(recipe_bytes):
            raise CheckError("mGBA_build_recipe_hash_mismatch")
        if dll_bytes[:2] != b"MZ":
            raise CheckError("source_built_core_not_windows_pe")
        try:
            with __import__("tarfile").open(fileobj=io.BytesIO(source_bytes), mode="r:gz") as tf:
                members = tf.getmembers()
                if len(members) < 10 or len(members) > 10000:
                    raise CheckError("unexpected_source_archive_member_count")
                prefix = name + "-source/"
                if any(not (m.name == prefix.rstrip("/") or m.name.startswith(prefix))
                       or ".." in Path(m.name).parts or m.issym() or m.islnk()
                       or m.size > 30_000_000 for m in members):
                    raise CheckError("unsafe_source_archive_member")
        except (__import__("tarfile").TarError, OSError, EOFError) as exc:
            raise CheckError("invalid_corresponding_source_archive") from exc
        replacements[ROOT + dll] = dll_bytes
        replacements[SOURCE_ROOT + archive] = source_bytes
        replacements[SOURCE_ROOT + script] = recipe_bytes
        license_path = {"sameboy": "SameBoy-MIT.txt",
                        "mgba": "mGBA-MPL-2.0.txt",
                        "supafaust": "Supafaust-GPL-2.0-plus.txt"}[name]
        replacements[ROOT + "licenses/" + license_path] = license_bytes
        identities[dll] = {
            "project": name, "source_repository": spec["url"],
            "exact_source_commit": spec["sha"],
            "source_archive": SOURCE_ROOT + archive,
            "build_script": SOURCE_ROOT + script,
            "dll_sha256": sha(dll_bytes),
            "source_archive_sha256": sha(source_bytes),
            "license_sha256": sha(license_bytes),
        }
    replacements[MANIFEST] = (
        "# Fase 81 source-built cores; corresponding source archives and recipes bundled.\n" +
        "".join(f"{dll}={info['dll_sha256']}\n" for dll, info in sorted(identities.items()))
    ).encode("ascii")
    provenance = {
        "phase": 81, "status": "experimental_qa",
        "signed_release": False, "public_release_authorized": False,
        "provenance": identities, "physical_devices_tested": False,
        "android_app_updated": False,
    }
    replacements[PROVENANCE] = json.dumps(provenance, indent=2, sort_keys=True).encode("utf-8")
    replacements[NOTICE] = (
        "OKUBI Windows Fase81 source-built QA only.\n"
        "All three Libretro cores were built by GitHub CI from exact pinned Git revisions.\n"
        "The exact corresponding source snapshots, build scripts, notices and SHA256 manifest\n"
        "are inside cores/source-packages/ and cores/licenses/.\n"
        "GB/GBC SameBoy MIT; GBA mGBA MPL-2.0; SNES Supafaust GPL-2.0-or-later.\n"
        "Source-to-binary manifest: cores/source-packages/SOURCE-TO-BINARY-MANIFEST.json\n"
        "No game ROMs, BIOS or user saves are included.\n"
        "No public release is authorized without full licensing/signing/device checks.\n"
    ).encode("utf-8")
    replacements[README] = (
        "OKUBI Windows 81 - Experimental source-built QA, not stable 1.0.\n"
        "Preserves the Phase80 application and QA57 isolated launch script.\n"
        "Run the existing RUN-QA57-ISOLATED.cmd from app/ for testing only.\n"
        "The same-commit GPL/MPL/MIT source archives and recipes travel with the DLLs.\n"
        "Do NOT install over another OKUBI, replace user saves, or publish as stable.\n"
    ).encode("utf-8")
    return provenance, replacements


def assemble(original: Path, original_checksums: Path, old_evidence: dict,
             source_dir: Path, tool_dir: Path, output: Path) -> dict:
    if original.name != WINDOWS_ZIP:
        raise CheckError("not_phase80_qa_zip")
    baseline_sha = verify_checksum(original, original_checksums)
    baseline = windows_archive(original, old_evidence)
    provenance, new_files = provenance_payload(source_dir, tool_dir)
    # Store a distinct archive, and do not edit or overwrite the Phase80 input.
    if output.resolve() == original.resolve() or output.name != OUTPUT_NAME:
        raise CheckError("unsafe_output_path_or_name")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(original) as zin, zipfile.ZipFile(
            output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6,
            allowZip64=True) as zout:
        safe_members(zin)
        previous_files = set()
        for entry in zin.infolist():
            if entry.is_dir():
                continue
            previous_files.add(entry.filename)
            data = new_files.pop(entry.filename, None)
            if data is None:
                data = zin.read(entry)
            zout.writestr(entry, data)
        for path, data in sorted(new_files.items()):
            if path in previous_files or not path.startswith(ROOT):
                raise CheckError("duplicate_or_unsafe_new_archive_path")
            zout.writestr(path, data)
    with zipfile.ZipFile(original) as zin, zipfile.ZipFile(output) as zout:
        prior = safe_members(zin)
        updated = safe_members(zout)
        allow = PREVIOUS_ALLOWED | set(updated).difference(prior)
        if set(prior) - set(updated):
            raise CheckError("existing_application_payload_was_removed")
        for key in set(prior) & set(updated):
            if key not in {x.casefold() for x in PREVIOUS_ALLOWED}:
                if digest_member(zin, prior[key]) != digest_member(zout, updated[key]):
                    raise CheckError("unrelated_application_component_changed")
        for dll, identity in provenance["provenance"].items():
            if digest_member(zout, required(updated, ROOT + dll)) != identity["dll_sha256"]:
                raise CheckError("final_core_sha256_invalid")
            if digest_member(zout, required(updated, identity["source_archive"])) != identity["source_archive_sha256"]:
                raise CheckError("final_corresponding_source_sha256_invalid")
        manifest = zout.read(required(updated, MANIFEST)).decode("ascii")
        if set(x.split("=", 1)[0] for x in manifest.splitlines()
               if x and not x.startswith("#")) != set(provenance["provenance"]):
            raise CheckError("final_core_manifest_incomplete")
        if len(updated) != len(set(updated)):
            raise CheckError("duplicate_archive_member_after_repacking")
    checksum = sha(file_bytes(output, max_size=1_000_000_000))
    checksums = output.parent / "SHA256SUMS.txt"
    checksums.write_text(checksum + " *" + output.name + "\n", encoding="ascii")
    return {
        "phase": 81, "candidate_type": "qa_only_source_built",
        "baseline_phase80_sha256": baseline_sha,
        "baseline_entries_checked": baseline["members_examined"],
        "candidate_sha256": checksum,
        "cores_verified": len(provenance["provenance"]),
        "corresponding_source_archives_included": 3,
        "unmodified_application_payload_checked": True,
        "real_hardware_qa": False, "public_release_authorized": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase80-zip", type=Path, required=True)
    parser.add_argument("--phase80-checksums", type=Path, required=True)
    parser.add_argument("--phase79-evidence", type=Path, required=True)
    parser.add_argument("--source-builds", type=Path, required=True)
    parser.add_argument("--source-scripts", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        evidence = json.loads(args.phase79_evidence.read_text("utf-8"))
        report = assemble(args.phase80_zip, args.phase80_checksums, evidence,
                          args.source_builds, args.source_scripts,
                          args.output_dir / OUTPUT_NAME)
        (args.output_dir / "SOURCE-BUILT-QA-REPORT.json").write_text(
            json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("PASS: new isolated Windows source-built QA zip with 3 source archives")
        print("Public release authorized: False")
        return 0
    except (OSError, ValueError, zipfile.BadZipFile) as e:
        print("BLOCKED: integrity or source evidence failed", type(e).__name__)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
