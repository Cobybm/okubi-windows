#!/usr/bin/env python3
"""OKUBI UNIFIED: optional read-only ADB rescue of old debug-install data.

Reads ONLY allowlisted UI preferences and SHA-named emulator save files
through Android's standard 'run-as' for debuggable builds. Does not require
root, install an APK, change a phone file or remove an old application.
Exports local player-selected ZIP files compatible with OkubiUnifiedBackup.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

CANDIDATE_PACKAGES = (
    "bo.nexoplay.app.indietest", "bo.nexoplay.app.dpadtest",
    "bo.nexoplay.app.personal", "bo.nexoplay.app",
)
PREF_NAMES = (
    "nexo_visual_08", "okubi_audio_v1", "okubi_performance_v1",
    "okubi_retro_controls_v1", "okubi_retro_slots_v1", "nexo_play", "okubi_unified_library_v1",
    "bo.nexoplay.app.MainActivity", "MainActivity",
)
SAVE_RE = re.compile(r"^[0-9a-f]{64}\.(srm|rtc|state|slot[23]\.state)$")
CONTROLS_RE = re.compile(
    r"^(port_|land_)(skin|size|alpha|left|swap|shoulder|dpadX|dpadY|"
    r"[xy]_(UP|DOWN|LEFT|RIGHT|A|B|C|X|Y|Z|L|R|START|SELECT))$")
GAME_ID_RE = re.compile(r"^[A-Za-z0-9.:_/\\-]{1,120}$")
RETRO_ASSET_RE = re.compile(r"^(fav_|played_)retro:asset:[0-9a-f]{64}$")
SAVE_LIMIT = {"srm": 1024 * 1024, "rtc": 65536, "state": 16 * 1024 * 1024}
MAX_ENTRIES = 512
MAX_TOTAL = 192 * 1024 * 1024
MAX_PREFERENCES = 1024 * 1024


class RescueError(RuntimeError):
    pass


def allowed_pref(file: str, key: str) -> bool:
    if not key or len(key) > 180:
        return False
    if file == "nexo_visual_08":
        return key in {"palette", "reduced_motion", "compact_library"}
    if file == "okubi_audio_v1":
        return key in {"volume", "muted", "safe"}
    if file == "okubi_performance_v1":
        return key == "render"
    if file == "okubi_retro_slots_v1":
        return bool(re.fullmatch(r"slot_[0-9a-f]{64}", key))
    if file == "okubi_retro_controls_v1":
        return bool(CONTROLS_RE.fullmatch(key))
    if file == "nexo_play":
        return key in {
            "selected_components", "favorite_components",
            "okubi_hidden_components", "player_nickname_v05",
            "player_avatar_v05", "last_game_component", "collections_v05",
        } or key.startswith("personal_goals_v10_") and bool(
            GAME_ID_RE.fullmatch(key[len("personal_goals_v10_") :])
        ) or key.startswith("launch_ms_") and bool(
            GAME_ID_RE.fullmatch(key[len("launch_ms_") :])
        )
    if file == "okubi_unified_library_v1":
        return bool(RETRO_ASSET_RE.fullmatch(key))
    if file in {"MainActivity", "bo.nexoplay.app.MainActivity"}:
        return key in {"library_fav_only_08", "collection_order_v06",
                       "library_order_v09", "cinema_lite_v07"}
    return False


def parse_pref_xml(data: bytes, pref_file: str) -> dict:
    """Convert Android SharedPreferences XML to typed, portable safe JSON."""
    if not data or len(data) > MAX_PREFERENCES:
        return {}
    try:
        root = ET.fromstring(data)
    except ET.ParseError:
        return {}
    if root.tag != "map":
        return {}
    result = {}
    for child in root:
        key = child.get("name", "")
        if not allowed_pref(pref_file, key):
            continue
        tag, value = child.tag, child.get("value")
        try:
            if tag == "string":
                val = child.text or ""
                if len(val) > 65536 or val.startswith(("content://", "file://")):
                    continue
                item = {"type": "string", "value": val}
            elif tag == "boolean" and value in {"true", "false"}:
                item = {"type": "boolean", "value": value == "true"}
            elif tag in {"int", "long"}:
                number = int(value)
                if tag == "int" and not -(2**31) <= number < 2**31:
                    continue
                if tag == "long" and not -(2**63) <= number < 2**63:
                    continue
                item = {"type": "integer" if tag == "int" else "long",
                        "value": number}
            elif tag == "float":
                from math import isfinite
                number = float(value)
                if not isfinite(number):
                    continue
                item = {"type": "float", "value": number}
            elif tag == "set":
                values = [x.text or "" for x in child if x.tag == "string"]
                if len(values) > 300 or any(
                    len(x) > 256 or x.startswith(("content://", "file://"))
                    for x in values
                ):
                    continue
                item = {"type": "set", "value": values}
            else:
                continue
            result[key] = item
        except (ValueError, TypeError, OverflowError):
            continue
    return result


def run_adb(adb: str, *args: str, allow_failure: bool = False) -> bytes | None:
    try:
        proc = subprocess.run([adb, *args], capture_output=True, timeout=35)
    except (OSError, subprocess.TimeoutExpired) as exc:
        if allow_failure:
            return None
        raise RescueError(f"No se pudo usar ADB: {exc}") from exc
    if proc.returncode:
        if allow_failure:
            return None
        msg = proc.stderr.decode("utf-8", "replace").strip()[:250]
        raise RescueError(f"ADB falló ({proc.returncode}): {msg}")
    return proc.stdout


def read_old(adb: str, pkg: str, relative_path: str) -> bytes | None:
    if (relative_path.startswith("/") or ".." in relative_path
            or not re.fullmatch(r"[A-Za-z0-9_./-]{1,220}", relative_path)):
        return None
    return run_adb(adb, "exec-out", "run-as", pkg, "cat",
                   relative_path, allow_failure=True)


def build_archive(source: str, preferences: dict,
                  saves: dict[str, bytes], destination: Path) -> tuple[int, int]:
    """Public for test. Validates first; never stores ROMs."""
    files = {}
    serialized = json.dumps(preferences, ensure_ascii=False,
                            separators=(",", ":")).encode("utf-8")
    if len(serialized) > MAX_PREFERENCES:
        raise RescueError("Demasiadas preferencias para el respaldo")
    files["preferences.json"] = serialized
    total = len(serialized)
    for name, data in saves.items():
        match = re.fullmatch(
            r"saves/(gameboy|gba)/([0-9a-f]{64})\.(srm|rtc|state|slot[23]\.state)", name
        )
        if not match or not isinstance(data, bytes) or not data or len(data) > SAVE_LIMIT[match[3].split(".")[-1]]:
            raise RescueError(f"Nombre/tamaño no permitido: {name}")
        total += len(data)
        if total > MAX_TOTAL:
            raise RescueError("El respaldo excede el límite de seguridad")
        files[name] = data
    if len(files) + 1 > MAX_ENTRIES:
        raise RescueError("Demasiados archivos")
    records = [
        {"path": name, "size": len(data),
         "sha256": hashlib.sha256(data).hexdigest()}
        for name, data in files.items()
    ]
    manifest = json.dumps({
        "format": "okubi-unified", "version": 1,
        "source": source, "files": records,
    }, separators=(",", ":")).encode("utf-8")
    if len(manifest) > 512 * 1024:
        raise RescueError("Índice de respaldo demasiado grande")
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation: never truncate a previous rescue, even on reruns or
    # if another process creates the path between selecting and opening it.
    created = False
    try:
        with destination.open("xb") as output:
            created = True
            with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED,
                                 compresslevel=6) as target:
                for name, data in files.items():
                    target.writestr(name, data)
                target.writestr("manifest.json", manifest)
        # Independently check the new ZIP and every indexed record.
        with zipfile.ZipFile(destination, "r") as archive:
            if archive.testzip():
                raise RescueError("ZIP corrupto")
            for record in records:
                if hashlib.sha256(archive.read(record["path"])).hexdigest() != record["sha256"]:
                    raise RescueError("Error verificando respaldo")
    except Exception:
        # Only remove the NEW, incomplete file, never a pre-existing backup.
        if created:
            destination.unlink(missing_ok=True)
        raise
    return len(saves), sum(len(group) for group in preferences.values())


def next_free_backup(directory: Path, stem: str) -> Path:
    """Choose a distinct filename; build_archive still opens exclusively."""
    first = directory / f"{stem}.zip"
    if not first.exists():
        return first
    for index in range(2, 1001):
        candidate = directory / f"{stem}_{index}.zip"
        if not candidate.exists():
            return candidate
    raise RescueError("Demasiados respaldos; elige otra carpeta de salida")


def read_stable_save(adb: str, pkg: str, relative_path: str,
                     maximum: int) -> bytes | None:
    """Reject an unstable or truncated read instead of backing it up blindly.

    Two independent run-as reads must match byte-for-byte. This does not
    replace closing the emulator before rescue, but detects mid-copy writes.
    """
    first = read_old(adb, pkg, relative_path)
    if not first or len(first) > maximum:
        return None
    second = read_old(adb, pkg, relative_path)
    if not second or len(second) != len(first) or second != first:
        return None
    return first


def collect_package(adb: str, pkg: str, target_dir: Path) -> Path | None:
    if run_adb(adb, "exec-out", "run-as", pkg, "id", allow_failure=True) is None:
        return None
    print(f"\n{pkg}: lectura autorizada por run-as")
    prefs = {}
    for file in PREF_NAMES:
        raw = read_old(adb, pkg, f"shared_prefs/{file}.xml")
        if raw is None:
            continue
        value = parse_pref_xml(raw, file)
        if value:
            prefs[file] = value

    saves = {}
    for folder in ("gameboy", "gba"):
        listing = run_adb(
            adb, "exec-out", "run-as", pkg, "ls", "-1",
            f"files/okubi/{folder}", allow_failure=True
        )
        if listing is None:
            continue
        for file in sorted(set(listing.decode("utf-8", "replace").splitlines())):
            if not SAVE_RE.fullmatch(file):
                continue
            name = f"saves/{folder}/{file}"
            content = read_stable_save(
                adb, pkg, f"files/okubi/{folder}/{file}",
                SAVE_LIMIT[file.rsplit(".", 1)[-1]]
            )
            if content is not None:
                saves[name] = content
            else:
                print("  ADVERTENCIA: partida omitida por lectura inestable,"
                      " incompleta o no autorizada. Cierra el juego e intenta"
                      " el rescate de nuevo: " + file)
            if len(saves) >= MAX_ENTRIES - 2:
                break
    if not prefs and not saves:
        print("  No hay preferencias/partidas legibles; no se creó ZIP.")
        return None
    short = pkg.rsplit(".", 1)[-1]
    output = next_free_backup(target_dir, f"OKUBI_Rescate_{short}")
    count, settings = build_archive(pkg, prefs, saves, output)
    print(f"  ZIP verificado: {output} ({count} partidas, {settings} preferencias)")
    return output


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extraer datos OKUBI desde APK de depuración con ADB sin root.")
    parser.add_argument("--adb", default="adb",
                        help="Ruta al ejecutable ADB (predeterminado: adb)")
    parser.add_argument("--source", choices=CANDIDATE_PACKAGES,
                        help="Solo leer este identificador de APK antigua")
    parser.add_argument("--output", default="OKUBI_Rescates",
                        help="Carpeta de salida en el ordenador")
    args = parser.parse_args()
    if shutil.which(args.adb) is None and not Path(args.adb).is_file():
        print("No se encontró adb. Instala Android Platform Tools y añade adb a PATH.")
        return 2
    devices = run_adb(args.adb, "devices").decode("utf-8", "replace")
    connected = [line for line in devices.splitlines()
                 if "\tdevice" in line]
    if len(connected) != 1:
        print("Conecta exactamente un teléfono con depuración USB autorizada.")
        return 2
    out = Path(args.output).resolve()
    written = []
    for package in (args.source,) if args.source else CANDIDATE_PACKAGES:
        result = collect_package(args.adb, package, out)
        if result is not None:
            written.append(result)
    if not written:
        print("No se pudo rescatar ningún paquete. El APK podría no ser depurable.")
        print("Usa el menú del emulador anterior → Exportar .okubisave por juego.")
        return 1
    print("\nSIGUIENTE: Abre OKUBI UNIFIED → Ajustes → Importar respaldo")
    print("Selecciona cada ZIP desde el teléfono, sin borrar las APK anteriores.")
    print("Los archivos ZIP permanecen solo en tu ordenador hasta que los copies.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RescueError, OSError) as exc:
        print(f"Error de rescate: {exc}")
        sys.exit(2)
