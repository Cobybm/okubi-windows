#!/usr/bin/env python3
"""OKUBI update preflight: read-only on ADB phone, no installation.

Checks a candidate Beta 17 APK against the actually installed public base.apk
using aapt and apksigner. Commands on phone: adb devices, shell pm path,
and adb pull of the APK binary to temporary PC storage only.
Never reads user data or modifies app state.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory

from okubi_release_guard import CANONICAL_PACKAGE, ReleaseCheckError, examine, validate

EXPECTED_CODE = 36
EXPECTED_NAME = "1.0.0-unified-beta17"


class PreinstallError(RuntimeError):
    pass


def authorized_serial(adb: str) -> str:
    result = subprocess.run([adb, "devices"], capture_output=True,
                            text=True, encoding="utf-8", errors="replace",
                            timeout=25, check=False)
    if result.returncode:
        raise PreinstallError("ADB no pudo leer los dispositivos autorizados")
    devices = []
    for row in result.stdout.splitlines():
        parts = row.split()
        if len(parts) >= 2 and not row.startswith(("List of devices", "* daemon")):
            devices.append((parts[0], parts[1]))
    if len(devices) != 1 or devices[0][1] != "device":
        raise PreinstallError("Conecta un solo teléfono autorizado por ADB")
    return devices[0][0]  # Never printed or included in result.


def installed_base_path(output: str) -> str | None:
    """Validate remote APK path before local-only extraction."""
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines:
        return None
    paths = [line[len("package:"):] for line in lines
             if line.startswith("package:")]
    if len(paths) != len(lines):
        raise PreinstallError("Android devolvió una ruta APK no reconocida")
    base = [p for p in paths if p.endswith("/base.apk")]
    if len(base) != 1:
        raise PreinstallError("No se encontró una única APK base instalada")
    path = base[0]
    if (not path.startswith("/") or ".." in path or "\\" in path
            or any(ord(char) < 32 or ord(char) > 126 for char in path)
            or not re.fullmatch(r"/[A-Za-z0-9_+./=:@~-]+", path)):
        raise PreinstallError("Ruta APK insegura o no reconocida")
    return path


def adb_command(adb: str, serial: str, *parts: str) -> str:
    result = subprocess.run([adb, "-s", serial, *parts],
                            capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=60, check=False)
    if result.returncode:
        raise PreinstallError("No se pudo consultar/copiar la APK instalada")
    return result.stdout


def sdk_tool(name: str, explicit: str | None, sdk: Path | None) -> str:
    if explicit:
        path = Path(explicit)
        if path.is_file():
            return str(path)
        found = shutil.which(explicit)
        if found:
            return found
        raise PreinstallError("No se encontró la herramienta: " + name)
    extensions = (".exe", ".bat", "") if os.name == "nt" else ("",)
    for suffix in extensions:
        found = shutil.which(name + suffix)
        if found:
            return found
    candidates = []
    roots = [sdk, os.environ.get("ANDROID_HOME"),
             os.environ.get("ANDROID_SDK_ROOT")]
    local = os.environ.get("LOCALAPPDATA")
    if local:
        roots.append(Path(local) / "Android" / "Sdk")
    for root in roots:
        if not root:
            continue
        build_tools = Path(root) / "build-tools"
        if not build_tools.is_dir():
            continue
        for directory in build_tools.iterdir():
            if directory.is_dir():
                for suffix in extensions:
                    tool = directory / (name + suffix)
                    if tool.is_file():
                        candidates.append((directory.name, tool))
    if candidates:
        # Most recent SDK Build Tools; no executing arbitrary download scripts.
        return str(sorted(candidates, key=lambda item: tuple(
            int(v) if v.isdigit() else v
            for v in re.split(r"(\d+)", item[0])
        ))[-1][1])
    raise PreinstallError(
        "No se encontró " + name + ". Instala Android SDK Build Tools "
        "o proporciona su ruta mediante --" + name + "."
    )


def decision(candidate, previous) -> str:
    """Only signature/version decision, not real device or game QA."""
    try:
        validate(candidate, kind="debug", expected_name=EXPECTED_NAME,
                 minimum_code=EXPECTED_CODE, pinned_sha256=None, previous=previous)
    except ReleaseCheckError as error:
        raise PreinstallError(str(error)) from error
    if previous is None:
        return ("NUEVA INSTALACIÓN CANÓNICA: no existe UNIFIED en el móvil. "
                "Las partidas de otros paquetes no se importan solas.")
    return ("ACTUALIZACIÓN DE IDENTIDAD COMPATIBLE: mismo paquete y firma, "
            "versionCode superior. NO demuestra funcionamiento de partidas.")


def perform(apk: Path, *, adb: str, aapt: str, apksigner: str) -> str:
    if not apk.is_file():
        raise PreinstallError("No se encontró el APK candidato")
    # Reject invalid APK before querying anything from the phone.
    candidate = examine(apk, aapt, apksigner)
    decision(candidate, None)
    serial = authorized_serial(adb)
    output = adb_command(adb, serial, "shell", "pm", "path", CANONICAL_PACKAGE)
    remote = installed_base_path(output)
    if remote is None:
        verdict = decision(candidate, None)
    else:
        with TemporaryDirectory(prefix="okubi-apk-check-") as scratch:
            local = Path(scratch) / "installed-base.apk"
            # Pull only publicly installed executable, not private game data.
            adb_command(adb, serial, "pull", remote, str(local))
            installed = examine(local, aapt, apksigner)
            verdict = decision(candidate, installed)
    return (verdict + "\n"
            "No se instaló, desinstaló, abrió ni limpió ninguna aplicación.\n"
            "No se leyeron ROMs ni archivos privados de partida.")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Comprobar firma de actualización sin instalar APK")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--aapt")
    parser.add_argument("--apksigner")
    parser.add_argument("--sdk", type=Path)
    args = parser.parse_args()
    try:
        aapt = sdk_tool("aapt", args.aapt, args.sdk)
        apksigner = sdk_tool("apksigner", args.apksigner, args.sdk)
        if not (shutil.which(args.adb) or Path(args.adb).is_file()):
            raise PreinstallError("ADB no encontrado. Instala Android Platform Tools.")
        print(perform(args.apk, adb=args.adb, aapt=aapt,
                      apksigner=apksigner))
        return 0
    except (PreinstallError, ReleaseCheckError, OSError,
            subprocess.TimeoutExpired) as error:
        print("OKUBI: INSTALACIÓN BLOQUEADA: " + str(error),
              file=sys.stderr)
        print("Conserva las versiones anteriores. No desinstales ni borres datos.",
              file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
