#!/usr/bin/env python3
"""Verifies QA57 APK can coexist with the installed OKUBI package.

Uses aapt/apksigner as read-only inspectors. Does not install, uninstall,
read device data, move ROMs, change certificates or talk to adb.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import zipfile
from pathlib import Path

ORIGINAL = "bo.nexoplay.app.unified"
QA_PACKAGE = ORIGINAL + ".qa57"
BASE_VERSION = "1.0.0-unified-beta17"
QA_VERSION = BASE_VERSION + "-qa57"
REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
REQUIRED_CORES = ("libsameboy_libretro_android.so",
                  "libmgba_libretro_android.so",
                  "libsupafaust_libretro_android.so")
FORBIDDEN_SUFFIXES = (".gba", ".gbc", ".gb", ".sfc", ".smc",
                      ".sav", ".srm", ".rtc", ".state", ".okubisave")


def metadata(badging: str) -> dict[str, str | int]:
    line = next((l for l in badging.splitlines()
                 if l.startswith("package: ")), "")
    values = dict(re.findall(r"([A-Za-z]+)='([^']*)'", line))
    minimum = re.search(r"^sdkVersion:'(\d+)'$", badging, re.M)
    label = re.search(r"^application-label:'([^']*)'$", badging, re.M)
    if not all(k in values for k in ("name", "versionCode", "versionName")):
        raise ValueError("No se pudo verificar el identificador real del APK.")
    if minimum is None or label is None:
        raise ValueError("No se pudo verificar SDK mínimo o etiqueta Android.")
    return dict(package=values["name"], versionCode=int(values["versionCode"]),
                versionName=values["versionName"], minSdk=int(minimum.group(1)),
                label=label.group(1))


def inspect_archive(path: Path) -> dict[str, int]:
    if not path.is_file() or path.stat().st_size < 10000:
        raise ValueError("APK inexistente o demasiado pequeño.")
    with zipfile.ZipFile(path) as z:
        entries = z.namelist()
        if z.testzip() is not None:
            raise ValueError("Contenido ZIP del APK corrupto.")
        if "AndroidManifest.xml" not in entries or "classes.dex" not in entries:
            raise ValueError("APK sin manifiesto o clases ejecutables.")
        for abi in REQUIRED_ABIS:
            for core in REQUIRED_CORES:
                if f"lib/{abi}/{core}" not in entries:
                    raise ValueError(f"Falta emulador {core} para {abi}.")
        for name in entries:
            low = name.replace("\\", "/").lower()
            if (low.endswith(FORBIDDEN_SUFFIXES)
                    or "/personal-roms/" in "/" + low
                    or "/save-vault-v1/" in "/" + low):
                raise ValueError("Archivo de juego o partida personal en APK: " + name)
        return {"bytes": path.stat().st_size, "entries": len(entries)}


def compare(original: dict, qa: dict) -> None:
    if original["package"] != ORIGINAL:
        raise ValueError("El APK normal cambió de applicationId.")
    if original["versionName"] != BASE_VERSION or original["versionCode"] != 36:
        raise ValueError("La beta anterior perdió su identidad/versionCode.")
    if qa["package"] != QA_PACKAGE or qa["versionName"] != QA_VERSION:
        raise ValueError("La app de pruebas no es una instalación separada QA57.")
    if qa["versionCode"] != 36:
        raise ValueError("QA57 no conserva el versionCode de la beta base.")
    if "QA57" not in qa["label"]:
        raise ValueError("La app de pruebas debe distinguirse visualmente.")
    if qa["package"] == original["package"]:
        raise ValueError("QA57 sustituiría la instalación existente.")
    if qa["minSdk"] > 26 or original["minSdk"] > 26:
        raise ValueError("QA57 elevó el SDK mínimo sobre Android 8.")


def run(*arguments: str) -> str:
    result = subprocess.run(arguments, check=True, capture_output=True,
                            encoding="utf-8", errors="replace")
    return result.stdout


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--original", type=Path, required=True)
    p.add_argument("--qa", type=Path, required=True)
    p.add_argument("--aapt", required=True)
    p.add_argument("--apksigner", required=True)
    p.add_argument("--report", type=Path)
    a = p.parse_args()
    if a.original.resolve() == a.qa.resolve():
        raise ValueError("QA57 y APK normal deben ser dos archivos independientes.")
    originals = metadata(run(a.aapt, "dump", "badging", str(a.original)))
    qa = metadata(run(a.aapt, "dump", "badging", str(a.qa)))
    compare(originals, qa)
    a_details = inspect_archive(a.original)
    qa_details = inspect_archive(a.qa)
    for apk in (a.original, a.qa):
        run(a.apksigner, "verify", "--verbose", "--print-certs", str(apk))
    report = {
        "status": "PASS", "original": originals, "qa57": qa,
        "original_zip": a_details, "qa57_zip": qa_details,
        "installation": "Distinct Android package; never replaces installed OKUBI.",
        "caveat": "QA57 starts with separate private data. Device QA still required.",
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if a.report:
        a.report.parent.mkdir(parents=True, exist_ok=True)
        a.report.write_text(json.dumps(report, ensure_ascii=False, indent=2),
                            encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
