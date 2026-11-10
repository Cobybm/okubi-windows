#!/usr/bin/env python3
"""OKUBI UNIFIED - read-only Android device preflight.

Only checks ADB authorization, model, Android version, installed OKUBI package
identities, APK version metadata and whether legacy debug packages permit
the standard run-as rescue route. Does NOT install, delete, launch, pull,
back up, or open any user ROM, save or application-private file.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Callable

CANONICAL = "bo.nexoplay.app.unified"
LEGACY = (
    "bo.nexoplay.app.indietest",
    "bo.nexoplay.app.dpadtest",
    "bo.nexoplay.app.personal",
    "bo.nexoplay.app",
)
PACKAGES = (CANONICAL,) + LEGACY
EXPECTED_VERSION_CODE = 36
Run = Callable[[list[str]], tuple[int, str]]


class PreflightError(RuntimeError):
    pass


def adb_runner(executable: str) -> Run:
    def invoke(parts: list[str]) -> tuple[int, str]:
        try:
            process = subprocess.run(
                [executable, *parts],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=25,
                check=False,
                shell=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            raise PreflightError("ADB no respondió. Revisa el cable USB.") from error
        return process.returncode, process.stdout
    return invoke


def parse_devices(output: str) -> list[tuple[str, str]]:
    result: list[tuple[str, str]] = []
    for line in output.splitlines():
        line = line.strip()
        if (not line or line.startswith("List of devices attached")
                or line.startswith("* daemon")):
            continue
        parts = line.split()
        if len(parts) >= 2:
            result.append((parts[0], parts[1]))
    return result


def safe_label(value: str) -> str:
    """Keep device/package labels printable, without terminal sequences."""
    return re.sub(r"[^A-Za-z0-9 ._()+/-]", "", value).strip()[:96] or "desconocido"


def package_version(dumpsys: str) -> tuple[str, str]:
    code = re.search(r"\bversionCode=(\d+)\b", dumpsys)
    name = re.search(r"\bversionName=([^\r\n]+)", dumpsys)
    return (code.group(1) if code else "no disponible",
            safe_label(name.group(1)) if name else "no disponible")


def collect(runner: Run) -> dict:
    code, raw = runner(["devices"])
    if code != 0:
        raise PreflightError("ADB no pudo enumerar los dispositivos")
    devices = parse_devices(raw)
    # Never automatically choose one among multiple connected phones.
    if len(devices) != 1 or devices[0][1] != "device":
        raise PreflightError(
            "Conecta exactamente un móvil con depuración USB autorizada. "
            "El dispositivo debe aparecer como 'device' en adb devices."
        )
    serial = devices[0][0]  # Used only in commands; NEVER included in report.
    def inspect(*args: str) -> tuple[int, str]:
        return runner(["-s", serial, *args])

    _, model_raw = inspect("shell", "getprop", "ro.product.model")
    _, sdk_raw = inspect("shell", "getprop", "ro.build.version.sdk")
    _, android_raw = inspect("shell", "getprop", "ro.build.version.release")
    model = safe_label(model_raw.strip())
    sdk = safe_label(sdk_raw.strip())
    android = safe_label(android_raw.strip())
    rows = []
    for pkg in PACKAGES:
        rc, path = inspect("shell", "pm", "path", pkg)
        installed = rc == 0 and any(
            line.startswith("package:") for line in path.splitlines()
        )
        row = {
            "package": pkg,
            "installed": installed,
            "version_code": "no disponible",
            "version_name": "no disponible",
            "rescue_debug": "no comprobado",
        }
        if installed:
            rc, raw_package = inspect("shell", "dumpsys", "package", pkg)
            if rc == 0:
                row["version_code"], row["version_name"] = package_version(raw_package)
            if pkg in LEGACY:
                # 'id' only checks permissions; never reads save contents.
                rc, _ = inspect("shell", "run-as", pkg, "id")
                row["rescue_debug"] = "posible" if rc == 0 else "no disponible"
        rows.append(row)
    return {"model": model, "sdk": sdk, "android": android, "packages": rows}


def make_report(data: dict) -> str:
    lines = [
        "OKUBI - DIAGNOSTICO A05s (SOLO LECTURA)",
        "Este informe NO comprueba partidas, guardados ni firma de actualizacion.",
        "Modelo: " + data["model"],
        "Android: " + data["android"] + " (API " + data["sdk"] + ")",
        "Identidades de OKUBI instaladas:",
    ]
    for row in data["packages"]:
        if not row["installed"]:
            lines.append("  - " + row["package"] + ": no instalada")
            continue
        lines.append("  - " + row["package"] + ": instalada | versionCode "
                     + row["version_code"] + " | " + row["version_name"])
        if row["package"] in LEGACY:
            lines.append("      Rescate ADB run-as: " + row["rescue_debug"])
    unified = data["packages"][0]
    lines.extend([
        "",
        "No se instalo ni desinstalo nada. No se leyeron ROMs ni partidas.",
        "No se extrajo ninguna clave, certificado privado, URI o identificador del movil.",
        "",
        "SIGUIENTE PRUEBA FISICA:",
        "1. Conserva todas las APK previas y exporta respaldos existentes.",
        "2. Prueba iniciar juegos GB/GBC/GBA con tus archivos personales.",
        "3. Comprueba SRAM, RTC, ranuras 1/2/3, giro y cruceta.",
        "4. Exporta ZIP, confirma verificacion e importa en instalacion segura.",
        "5. No fusionar ni desinstalar hasta comprobar las partidas reales.",
    ])
    if unified["installed"]:
        try:
            v = int(unified["version_code"])
        except ValueError:
            v = -1
        if v != EXPECTED_VERSION_CODE:
            lines.append(
                "AVISO: La version instalada no coincide con la Beta 16 "
                "(versionCode 35) de esta fase de pruebas."
            )
        else:
            lines.append(
                "AVISO: Beta 16 detectada; la firma y jugabilidad siguen sin verificar."
            )
    else:
        lines.append(
            "AVISO: UNIFIED no instalada; este diagnostico NO instala la Beta 16."
        )
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Diagnostico ADB de solo lectura para OKUBI UNIFIED."
    )
    parser.add_argument("--adb", help="Ruta local a adb si no esta en PATH")
    parser.add_argument("--report", type=Path,
                        help="Guardar TXT local sin serie del dispositivo ni partidas")
    args = parser.parse_args(argv)
    adb = args.adb or shutil.which("adb")
    if not adb:
        print("ADB no encontrado. Instala Android Platform Tools y agrega adb a PATH.",
              file=sys.stderr)
        return 2
    try:
        result = collect(adb_runner(adb))
    except PreflightError as error:
        print("OKUBI: " + str(error), file=sys.stderr)
        return 2
    report = make_report(result)
    print(report)
    if args.report:
        if args.report.exists():
            print("No se reemplazo un informe existente. Elige otro nombre.",
                  file=sys.stderr)
            return 2
        try:
            with args.report.open("x", encoding="utf-8") as output:
                output.write(report)
        except OSError as error:
            print("No se pudo guardar el TXT: " + str(error), file=sys.stderr)
            return 2
        print("Informe local creado: " + str(args.report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
