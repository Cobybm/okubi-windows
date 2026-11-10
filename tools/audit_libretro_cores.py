#!/usr/bin/env python3
"""Audita las descargas Libretro Windows x64: no publica ni ejecuta binarios.

El reporte compara dos runners independientes. Un hash nuevo no autoriza
por sí solo actualizar pins o publicar una versión estable.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import struct
import sys
import zipfile
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

BASE = "https://buildbot.libretro.com/nightly/windows/x86_64/latest/"
PACKAGES = (
    ("sameboy_libretro.dll", 40_000, 12_000_000),
    ("mgba_libretro.dll", 80_000, 16_000_000),
    ("mednafen_supafaust_libretro.dll", 80_000, 20_000_000),
)
MAX_ZIP = 22_000_000


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def inspect(name: str, min_bytes: int, max_bytes: int) -> dict:
    filename = name + ".zip"
    request = Request(BASE + filename, headers={"User-Agent": "OKUBI-Phase69-SupplyChainAudit/1.0"})
    with urlopen(request, timeout=120) as response:
        final_url = response.geturl()
        parsed = urlparse(final_url)
        if parsed.scheme != "https" or parsed.netloc != "buildbot.libretro.com":
            raise ValueError(f"Untrusted download redirect for {filename}")
        archive_data = response.read(MAX_ZIP + 1)
    if not (15_000 <= len(archive_data) <= MAX_ZIP):
        raise ValueError(f"Unexpected archive length for {filename}: {len(archive_data)}")
    with zipfile.ZipFile(io.BytesIO(archive_data)) as archive:
        files = [f for f in archive.infolist() if not f.is_dir()]
        if len(files) != 1 or files[0].filename != name:
            raise ValueError(f"Archive must contain exactly {name}, no paths or extras")
        entry = files[0]
        if not (min_bytes <= entry.file_size <= max_bytes):
            raise ValueError(f"Invalid DLL size for {name}: {entry.file_size}")
        if archive.testzip() is not None:
            raise ValueError(f"ZIP CRC failure: {name}")
        payload = archive.read(entry)
    if len(payload) != entry.file_size or len(payload) < 0x40 or payload[:2] != b"MZ":
        raise ValueError(f"Invalid executable: {name}")
    peoffset = struct.unpack_from("<I", payload, 0x3C)[0]
    if peoffset < 64 or peoffset + 6 > len(payload):
        raise ValueError(f"Invalid PE offset: {name}")
    if payload[peoffset:peoffset + 4] != b"PE\\0\\0".replace(b"\\0", b"\x00"):
        raise ValueError(f"Missing PE signature: {name}")
    machine = struct.unpack_from("<H", payload, peoffset + 4)[0]
    if machine != 0x8664:
        raise ValueError(f"Not Windows x64 PE: {name}")
    return {
        "archive": filename,
        "zip_sha256": digest(archive_data),
        "zip_bytes": len(archive_data),
        "dll_sha256": digest(payload),
        "dll_bytes": len(payload),
        "pe_machine": "0x8664",
        "host": "buildbot.libretro.com",
    }


def compare(first: Path, second: Path) -> None:
    a = json.loads(first.read_text("utf-8"))
    b = json.loads(second.read_text("utf-8"))
    expected = {name for name, _, _ in PACKAGES}
    if set(a) != expected or set(b) != expected:
        raise ValueError("Missing or unexpected core audit entries")
    for name in sorted(expected):
        if a[name] != b[name]:
            raise ValueError(f"FAIL CLOSED: runners disagree for {name}: "
                             f"{a[name]['zip_sha256']} vs {b[name]['zip_sha256']}")
    print("CONSENSUS PASS: Both runners downloaded identical verified ZIP and DLL bytes")
    print("NOTE: independent audit is not a security signature or release authorization")


def main() -> None:
    cli = argparse.ArgumentParser()
    cli.add_argument("--output", type=Path)
    cli.add_argument("--compare", nargs=2, type=Path)
    args = cli.parse_args()
    if args.compare:
        compare(*args.compare)
    elif args.output:
        report = {name: inspect(name, lower, upper) for name, lower, upper in PACKAGES}
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", "utf-8")
        print(json.dumps(report, indent=2, sort_keys=True))
        print("AUDIT ONLY: no ROMs, BIOS, core DLLs or ZIPs are uploaded")
    else:
        cli.error("choose --output or --compare")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f"SECURITY AUDIT BLOCKED: {exc}", file=sys.stderr)
        sys.exit(1)
