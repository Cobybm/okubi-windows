#!/usr/bin/env python3
"""OKUBI RC1 non-destructive release-readiness preflight.

Policy verification only. It NEVER signs, installs, merges, publishes,
downloads, edits ROMs, accesses saves, or reads any owner keystore.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

EXPECTED_PACKAGE = "bo.nexoplay.app.unified"
WINDOWS_APP_VERSION = "1.0.6"
ANDROID_BETA_VERSION = "1.0.0-unified-beta17"
REQUIRED = {
    "windows": ".github/workflows/okubi-desktop.yml",
    "android": ".github/workflows/okubi-retro.yml",
    "gradle": "android-app/app/build.gradle",
    "manifest": "android-app/app/src/main/AndroidManifest.xml",
    "guard": "tools/okubi_release_guard.py",
    "desktop": "desktop/src/bo/okubi/desktop/OkubiDesktop.java",
    "shutdown": "desktop/src/bo/okubi/desktop/DesktopShutdownPolicy.java",
    "shutdown_test": "desktop/tests/bo/okubi/desktop/DesktopShutdownPolicyTest.java",
    "release_notes": "docs/OKUBI_RELEASE_CANDIDATE_PHASE56.md",
}


def inspect(root: Path) -> dict:
    root = root.resolve()
    issues: list[str] = []
    code: dict[str, str] = {}
    for key, relative in REQUIRED.items():
        path = root / relative
        if not path.is_file():
            issues.append("Falta el archivo obligatorio: " + relative)
            code[key] = ""
        else:
            code[key] = path.read_text(encoding="utf-8")

    checks = {
        "Firma del propietario solo manual desde main": (
            "github.event_name == 'workflow_dispatch' && github.ref_name == 'main'"
            in code["android"]),
        "Sin firma release de depuración": (
            "--kind release" in code["android"]
            and "--pinned-sha256" in code["android"]
            and "OKUBI_RELEASE_CERT_SHA256" in code["android"]),
        "APK debug se verifica antes de subir": (
            "apksigner" in code["android"] and "--kind debug" in code["android"]),
        "Identidad Android única": (
            re.search(r"(?m)^\s*applicationId\s+['\"]"
                      + re.escape(EXPECTED_PACKAGE) + r"['\"]",
                      code["gradle"]) is not None),
        "VersionCode Android no disminuido": (
            re.search(r"(?m)^\s*versionCode\s+36\s*$", code["gradle"]) is not None),
        "Android sigue etiquetado beta17": (
            f"versionName '{ANDROID_BETA_VERSION}'" in code["gradle"]),
        "Datos Android sin copia automática": (
            'android:allowBackup="false"' in code["manifest"]),
        "Windows RC1 sin instalador automático": (
            f'--app-version "{WINDOWS_APP_VERSION}"' in code["windows"]
            and "--type app-image" in code["windows"]),
        "Windows checksum externo del ZIP": (
            "SHA256SUMS" in code["windows"]
            and "Get-FileHash" in code["windows"]),
        "Windows verifica las tres licencias Libretro": all(
            name in code["windows"] for name in (
                "SameBoy-MIT.txt", "mGBA-MPL-2.0.txt",
                "Supafaust-GPL-2.0-plus.txt")),
        "Windows ZIP sin archivos personales": (
            "Forbidden personal artifact" in code["windows"]
            and "ZipFile" in code["windows"]),
        "Bloqueo de cierre durante guardados": (
            "DesktopShutdownPolicy.mayExit" in code["desktop"]
            and "cloudBusy" in code["desktop"]
            and "crossSaveBusy" in code["desktop"]
            and "saveGuardBusy" in code["desktop"]
            and "DesktopShutdownPolicyTest" in code["windows"]),
        "Pruebas Cross Save y Save Vault retenidas": all(
            item in code["windows"] for item in (
                "DesktopCrossSaveTest", "DesktopSaveVaultTest",
                "DesktopSmartSaveGuardTest", "DesktopUniversalSavePreflightTest")),
        "Licencias Android comprobadas": all(
            label in code["android"] for label in (
                "mGBA-LICENSE.txt", "Supafaust-GPL-2.0.txt",
                "Supafaust-SOURCE.txt")),
        "Guía desaconseja update de firma diferente": (
            "misma huella SHA-256" in code["release_notes"]
            and "NO ESTABLE" in code["release_notes"]),
    }
    issues += [f"Contrato RC rechazado: {label}"
               for label, passed in checks.items() if not passed]
    return {
        "phase": 56,
        "status": "PASS" if not issues else "FAIL",
        "checks_passed": sum(checks.values()),
        "checks_total": len(checks),
        "checks": checks,
        "issues": issues,
        "note": "Solo auditoría de código/CI; no certifica pruebas físicas ni licencias completas.",
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, default=Path("."))
    p.add_argument("--report", type=Path)
    args = p.parse_args()
    result = inspect(args.root)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2, ensure_ascii=False),
                               encoding="utf-8")
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
