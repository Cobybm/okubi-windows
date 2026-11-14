#!/usr/bin/env python3
"""Phase 80: read-only examination of OKUBI QA artifacts before any installation.

Inspect the *actual* Windows application ZIP or side-by-side Android QA57 APK.
This is a QA safety contract, not a public distribution/license approval.
Never installs software, accesses ROMs/saves, or reads signing private keys.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import subprocess
import zipfile
from pathlib import Path, PurePosixPath

from okubi_qa57_verify import metadata as parse_badging
from okubi_release_guard import parse_signer, ReleaseCheckError

WINDOWS_ZIP = "OKUBI-Desktop-Windows-x64.zip"
ANDROID_APK = "OKUBI-Android-Migration-QA57.apk"
EVIDENCE = Path("tools/okubi_phase79_windows_core_evidence.json")
CORES = (
    "sameboy_libretro.dll",
    "mgba_libretro.dll",
    "mednafen_supafaust_libretro.dll",
)
LICENSES = (
    "SameBoy-MIT.txt",
    "mGBA-MPL-2.0.txt",
    "Supafaust-GPL-2.0-plus.txt",
    "THIRD-PARTY-SOURCES.txt",
)
FORBIDDEN_SUFFIXES = (
    ".gba", ".gb", ".gbc", ".sfc", ".smc", ".nes", ".n64", ".z64",
    ".iso", ".chd", ".cso", ".cue", ".sav", ".srm", ".rtc", ".state",
    ".okubisave", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key",
)
FORBIDDEN_SEGMENTS = {
    "personal-roms", "saves", "save-vault-v1", "userdata",
    "private-keys", "keystores", "vidas",
}
SHA_LINE = re.compile(r"^([0-9a-f]{64})\s+\*?([A-Za-z0-9_.-]+)$")


class CheckError(ValueError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_checksum(artifact: Path, checksum: Path) -> str:
    if not artifact.is_file() or not checksum.is_file():
        raise CheckError("artifact_or_checksum_missing")
    if artifact.is_symlink() or checksum.is_symlink():
        raise CheckError("symlink_not_permitted")
    if not 10_000 <= artifact.stat().st_size <= 1_000_000_000:
        raise CheckError("artifact_size_invalid")
    if checksum.stat().st_size > 300:
        raise CheckError("checksum_manifest_too_large")
    lines = checksum.read_text("utf-8").strip().splitlines()
    if len(lines) != 1:
        raise CheckError("checksum_must_have_one_entry")
    match = SHA_LINE.fullmatch(lines[0].strip())
    if not match or match.group(2) != artifact.name:
        raise CheckError("checksum_wrong_filename_or_format")
    actual = sha256(artifact)
    if match.group(1) != actual:
        raise CheckError("artifact_checksum_mismatch")
    return actual


def safe_members(z: zipfile.ZipFile) -> dict[str, zipfile.ZipInfo]:
    entries: dict[str, zipfile.ZipInfo] = {}
    total = 0
    if len(z.infolist()) > 12_000:
        raise CheckError("too_many_zip_members")
    for info in z.infolist():
        raw = info.filename
        path = PurePosixPath(raw)
        if (not raw or raw.startswith("/") or "\\" in raw
                or ":" in raw or any(part in ("", ".", "..") for part in raw.rstrip("/").split("/"))
                or (info.external_attr >> 16) & 0o170000 == stat.S_IFLNK
                or info.flag_bits & 0x1):
            raise CheckError("unsafe_zip_member")
        if info.is_dir():
            continue
        parts = [p.lower() for p in path.parts]
        if (path.suffix.lower() in FORBIDDEN_SUFFIXES
                or any(p in FORBIDDEN_SEGMENTS for p in parts)):
            raise CheckError("private_game_or_signing_data_in_artifact")
        key = raw.casefold()
        if key in entries:
            raise CheckError("duplicate_archive_member")
        if info.file_size > 800_000_000:
            raise CheckError("excessive_zip_member_size")
        total += info.file_size
        if total > 3_000_000_000:
            raise CheckError("excessive_uncompressed_archive_size")
        entries[key] = info
    if z.testzip() is not None:
        raise CheckError("zip_crc_failed")
    return entries


def required(entries: dict[str, zipfile.ZipInfo], name: str) -> zipfile.ZipInfo:
    info = entries.get(name.casefold())
    if info is None or info.file_size < 20:
        raise CheckError("required_archive_entry_missing")
    return info


def digest_member(z: zipfile.ZipFile, info: zipfile.ZipInfo) -> str:
    hash_value = hashlib.sha256()
    with z.open(info) as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hash_value.update(block)
    return hash_value.hexdigest()


def windows_archive(archive: Path, evidence: dict) -> dict:
    cores = evidence.get("cores")
    if (not isinstance(cores, list) or len(cores) != len(CORES)
            or evidence.get("releases_authorized") is not False):
        raise CheckError("audited_core_evidence_missing")
    pinned = {entry.get("name"): entry.get("dll_sha256") for entry in cores}
    if set(pinned) != set(CORES):
        raise CheckError("unexpected_core_identity")
    root = "OKUBI Desktop/"
    app = root + "app/"
    with zipfile.ZipFile(archive) as z:
        entries = safe_members(z)
        for key in (
            root + "OKUBI Desktop.exe", app + "okubi-desktop.jar",
            app + "okubi_libretro_jni.dll", app + "RUN-QA57-ISOLATED.cmd",
            app + "QA57-READ-ME-FIRST.md",
            app + "cores/CORE-SHA256.properties",
        ):
            required(entries, key)
        launcher = z.read(required(entries, app + "RUN-QA57-ISOLATED.cmd"))
        if b"OKUBI-QA57" not in launcher or b'set "APPDATA=' not in launcher:
            raise CheckError("windows_test_profile_not_isolated")
        manifest = z.read(required(entries, app + "cores/CORE-SHA256.properties"))
        properties = {}
        for line in manifest.decode("ascii").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            if "=" not in line:
                raise CheckError("core_hash_manifest_invalid")
            name, value = line.split("=", 1)
            if name in properties:
                raise CheckError("duplicate_core_manifest_key")
            properties[name] = value.strip()
        if set(properties) != set(CORES):
            raise CheckError("core_hash_manifest_incomplete")
        verified = {}
        for name in CORES:
            info = required(entries, app + "cores/" + name)
            actual = digest_member(z, info)
            if (actual != pinned[name] or actual != properties[name]
                    or not re.fullmatch(r"[a-f0-9]{64}", actual)):
                raise CheckError("windows_core_dll_mismatch")
            verified[name] = actual
        for name in LICENSES:
            required(entries, app + "cores/licenses/" + name)
        return {"members_examined": len(entries), "cores_verified": len(verified),
                "license_notices_present": len(LICENSES)}


def android_archive(archive: Path, badging: str, signer: str) -> dict:
    apk = parse_badging(badging)
    if (apk.get("package") != "bo.nexoplay.app.unified.qa57"
            or apk.get("versionCode") != 36
            or apk.get("minSdk") != 26
            or not str(apk.get("versionName", "")).endswith("-qa57")
            or "QA57" not in str(apk.get("label", ""))):
        raise CheckError("android_qa57_package_identity_invalid")
    try:
        _, is_debug, scheme = parse_signer(signer)
    except ReleaseCheckError as error:
        raise CheckError("apk_signing_validation_failed") from error
    if not is_debug or not scheme:
        raise CheckError("android_qa_must_use_debug_certificate")
    with zipfile.ZipFile(archive) as z:
        entries = safe_members(z)
        for key in ("AndroidManifest.xml", "classes.dex"):
            required(entries, key)
        for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            for core in ("sameboy", "mgba", "supafaust"):
                required(entries, f"lib/{abi}/lib{core}_libretro_android.so")
            required(entries, f"lib/{abi}/libokubi_sameboy_bridge.so")
        for name in ("SameBoy-LICENSE.txt", "mGBA-LICENSE.txt",
                     "Supafaust-GPL-2.0.txt", "Supafaust-SOURCE.txt"):
            required(entries, "assets/licenses/" + name)
        return {"members_examined": len(entries), "abis_verified": 4,
                "retro_cores_verified": 12, "signer": "debug_only"}


def checked_output(*cmd: str) -> str:
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True,
                              errors="replace", timeout=60, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise CheckError("android_sdk_tool_unavailable") from error
    if proc.returncode:
        raise CheckError("android_sdk_verification_rejected_artifact")
    return proc.stdout


def inspect(platform: str, archive: Path, checksum: Path, evidence: dict | None,
            badging: str | None = None, signer: str | None = None) -> dict:
    required_filename = WINDOWS_ZIP if platform == "windows" else ANDROID_APK
    if platform not in ("windows", "android") or archive.name != required_filename:
        raise CheckError("wrong_platform_or_artifact_name")
    verified_sha = verify_checksum(archive, checksum)
    if platform == "windows":
        facts = windows_archive(archive, evidence or {})
    else:
        if badging is None or signer is None:
            raise CheckError("android_signature_tools_not_executed")
        facts = android_archive(archive, badging, signer)
    return {
        "phase": 80,
        "platform": platform,
        "qa_integrity": "pass",
        "artifact_sha256": verified_sha,
        "facts": facts,
        "installed_or_replaced_application": False,
        "real_device_qa_verified": False,
        "binary_source_license_compliance_verified": False,
        "owner_signing_key_verified": False,
        "public_release_authorized": False,
    }


def main() -> int:
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--platform", choices=("windows", "android"), required=True)
    cli.add_argument("--archive", type=Path, required=True)
    cli.add_argument("--checksum", type=Path, required=True)
    cli.add_argument("--evidence", type=Path, default=EVIDENCE)
    cli.add_argument("--aapt", default="aapt")
    cli.add_argument("--apksigner", default="apksigner")
    cli.add_argument("--report", type=Path, required=True)
    args = cli.parse_args()
    try:
        evidence = None
        badging = signer = None
        if args.platform == "windows":
            evidence = json.loads(args.evidence.read_text("utf-8"))
        else:
            badging = checked_output(args.aapt, "dump", "badging", str(args.archive))
            signer = checked_output(args.apksigner, "verify", "--verbose",
                                    "--print-certs", str(args.archive))
        result = inspect(args.platform, args.archive, args.checksum,
                         evidence, badging, signer)
    except (OSError, ValueError, UnicodeError, zipfile.BadZipFile, RuntimeError):
        print("OKUBI PHASE80: BLOCKED — artifact, signature or contents failed QA")
        return 1
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("OKUBI PHASE80 QA INTEGRITY: PASS (" + args.platform + ")")
    print("SHA256:", result["artifact_sha256"])
    print("NOTE: release remains blocked pending source licenses, owner signature"
          " and physical QA.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
