#!/usr/bin/env python3
"""Add one SOURCE-BUILT PS1 Libretro core to a verified OKUBI Windows image.

The prior three cores have already been verified by Phase81. This extension
requires the exact pinned Beetle PSX commit, validates Windows PE x64, source
tarball, GPLv2 license, build recipe and CI artifact hashes. No downloads,
ROMs, firmware, executable shell commands or player data are touched here.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import tarfile
from pathlib import Path

from okubi_phase81b_source_bundle import ROOT, SOURCE_ROOT, PROVENANCE, CheckError

PINNED = "dd7e2aee57d1e590e45b7e642ba76bf1a4cdbc61"
REPO = "https://github.com/libretro/beetle-psx-libretro"
DLL = "mednafen_psx_libretro.dll"
ARCHIVE = "Beetle-PSX-corresponding-source.tar.gz"
LICENSE = "Beetle-PSX-GPLv2.txt"
BUILD = "okubi-ps1-beetle-source-build.yml"

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for data in iter(lambda: f.read(1024 * 1024), b""):
            h.update(data)
    return h.hexdigest()

def regular(path: Path, smallest: int, largest: int) -> Path:
    if not path.is_file() or path.is_symlink():
        raise CheckError("missing_or_unsafe_ps1_core_file")
    size = path.stat().st_size
    if not smallest <= size <= largest:
        raise CheckError("incorrect_ps1_core_file_size")
    return path

def verify_artifact(core: Path, recipe: Path) -> dict:
    dll = regular(core / DLL, 100_000, 100_000_000)
    source = regular(core / ARCHIVE, 40_000, 140_000_000)
    license = regular(core / LICENSE, 500, 150_000)
    evidence = regular(core / "BUILD-PROVENANCE.txt", 50, 4096).read_text("utf-8")
    sums = regular(core / "PS1-CORE-SHA256SUMS.txt", 70, 512).read_text("ascii")
    match = re.search(r"(?m)^([a-f0-9]{64})\\s+.*mednafen_psx_libretro\\.dll\\s*$", sums)
    if not match or digest(dll) != match.group(1):
        raise CheckError("ps1_artifact_hash_mismatch")
    if PINNED not in evidence or REPO not in evidence or "GPL-2.0" not in evidence:
        raise CheckError("ps1_corresponding_source_metadata_mismatch")
    text = regular(recipe, 500, 30000).read_text("utf-8")
    if PINNED not in text or REPO not in text:
        raise CheckError("ps1_build_recipe_revision_mismatch")
    raw = dll.read_bytes()
    if raw[:2] != b"MZ" or len(raw) < 64:
        raise CheckError("ps1_not_windows_pe")
    offset = struct.unpack_from("<I", raw, 0x3c)[0]
    if offset + 6 >= len(raw) or raw[offset:offset + 4] != b"PE\\0\\0":
        raise CheckError("ps1_invalid_pe_signature")
    machine = struct.unpack_from("<H", raw, offset + 4)[0]
    if machine != 0x8664:
        raise CheckError("ps1_dll_not_windows_x64")
    with tarfile.open(source, "r:gz") as tf:
        members = tf.getmembers()
        if len(members) < 100 or len(members) > 50000:
            raise CheckError("ps1_source_archive_manifest_unexpected")
        for entry in members:
            parts = Path(entry.name).parts
            if entry.name.startswith("/") or ".." in parts or entry.issym() or entry.islnk():
                raise CheckError("ps1_unsafe_corresponding_source_archive")
            if entry.size > 40_000_000:
                raise CheckError("ps1_source_archive_member_too_large")
    return {
        "project": "beetle_psx", "source_repository": REPO,
        "exact_source_commit": PINNED,
        "source_archive": SOURCE_ROOT + ARCHIVE,
        "build_script": SOURCE_ROOT + BUILD,
        "dll_sha256": digest(dll),
        "source_archive_sha256": digest(source),
        "license_sha256": digest(license)
    }

def integrate(core: Path, output: Path, recipe: Path) -> dict:
    if not output.is_dir() or output.is_symlink():
        raise CheckError("expected_verified_three_core_directory")
    evidence = verify_artifact(core, recipe)
    manifest = output / "CORE-SHA256.properties"
    provenance = output / Path(PROVENANCE).relative_to(ROOT)
    if not manifest.is_file() or not provenance.is_file():
        raise CheckError("missing_existing_source_provenance")
    data = json.loads(provenance.read_text("utf-8"))
    records = data.get("provenance", {})
    originals = {"sameboy_libretro.dll", "mgba_libretro.dll",
                 "mednafen_supafaust_libretro.dll"}
    if set(records) != originals or data.get("signed_release") is not False:
        raise CheckError("unexpected_existing_cores_or_release_status")
    hashes = {}
    lines = manifest.read_text("ascii").splitlines()
    for line in lines:
        if "=" not in line or line.startswith("#"):
            continue
        name, sha = line.split("=", 1)
        if name in hashes or not re.fullmatch("[0-9a-f]{64}", sha):
            raise CheckError("duplicate_or_invalid_existing_core_hash")
        hashes[name] = sha
    if set(hashes) != originals:
        raise CheckError("existing_core_manifest_mismatch")
    for name, expected in hashes.items():
        if digest(regular(output / name, 10000, 100_000_000)) != expected:
            raise CheckError("existing_legacy_core_tampered")
    additions = {
        DLL: core / DLL,
        "licenses/" + LICENSE: core / LICENSE,
        "source-packages/" + ARCHIVE: core / ARCHIVE,
        "source-packages/" + BUILD: recipe
    }
    for name, original in additions.items():
        target = output / name
        if target.exists() or target.is_symlink():
            raise CheckError("ps1_target_must_not_preexist")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(original.read_bytes())
    records[DLL] = evidence
    provenance.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", "utf-8")
    with manifest.open("a", encoding="ascii") as f:
        f.write(f"{DLL}={evidence['dll_sha256']}\n")
    notice = output / "licenses/THIRD-PARTY-SOURCES.txt"
    with notice.open("a", encoding="utf-8") as f:
        f.write("\nPS1 Beetle PSX GPLv2 from libretro Git commit " + PINNED + "\n")
        f.write("Its complete corresponding source, license and build recipe "
                "are included in cores/source-packages and cores/licenses.\n")
    print("PASS PS1: four independently verified bundled cores. SHA256 "
          + evidence["dll_sha256"])
    return evidence

if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source-built-ps1", type=Path, required=True)
    p.add_argument("--output-cores", type=Path, required=True)
    p.add_argument("--recipe", type=Path, required=True)
    args=p.parse_args()
    try: integrate(args.source_built_ps1, args.output_cores, args.recipe)
    except (OSError, RuntimeError, ValueError, tarfile.TarError) as error:
        print("BLOCKED: PS1 source-built integration: " + str(error))
        raise SystemExit(1)
