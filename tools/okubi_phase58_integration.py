#!/usr/bin/env python3
"""OKUBI Phase 58: fail-closed, read-only integration eligibility audit.

Checks source/CI contracts, data separation and ancestry against main.
It does NOT merge, tag, install APKs, edit files or access user saves.
Passing this audit does NOT mean a physical device test is complete.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

REQUIRED_FILES = (
    ".github/workflows/okubi-retro.yml",
    ".github/workflows/okubi-desktop.yml",
    ".github/workflows/okubi-unified-stable.yml",
    "android-app/app/build.gradle",
    "android-app/app/src/main/AndroidManifest.xml",
    "retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java",
    "desktop/src/bo/okubi/desktop/DesktopSaves.java",
    "desktop/src/bo/okubi/desktop/DesktopCrossSave.java",
    "desktop/src/bo/okubi/desktop/DesktopSaveVault.java",
    "desktop/src/bo/okubi/desktop/DesktopSmartSaveGuard.java",
    "desktop/src/bo/okubi/desktop/DesktopShutdownPolicy.java",
    "desktop/tests/bo/okubi/desktop/DesktopSaveVaultStabilityTest.java",
    "desktop/tests/bo/okubi/desktop/DesktopUniversalSavePreflightTest.java",
    "desktop/tests/bo/okubi/desktop/DesktopQa57IsolationTest.java",
    "desktop/QA57-LAUNCHER.cmd",
    "tools/okubi_unified_audit.py",
    "tools/okubi_rc_preflight.py",
    "tools/okubi_qa57_verify.py",
    "tools/tests/test_okubi_qa57_verify.py",
    "docs/OKUBI_FINAL_QA_PHASE57.md",
    "docs/OKUBI_RELEASE_CANDIDATE_PHASE56.md",
    "docs/OKUBI_FINAL_INTEGRATION_PHASE58.md",
)
FORBIDDEN_EXT = {
    ".gba", ".gb", ".gbc", ".sfc", ".smc", ".nes", ".rom",
    ".sav", ".srm", ".rtc", ".state", ".okubisave", ".dsv",
    ".keystore", ".jks", ".p12", ".pfx", ".pem", ".key",
    ".apk", ".aab", ".exe",
}
FORBIDDEN_COMPONENTS = {
    "vidas", ".okubi", "appdata", "personal-roms", "user-saves",
    "userdata", "save-vault-v1",
}


def protected_path_issues(paths: list[str]) -> list[str]:
    """Validate paths only: never open or inspect user files or binaries."""
    errors = []
    for item in paths:
        clean = item.replace("\\", "/").strip("/").casefold()
        parts = clean.split("/")
        if any(part in FORBIDDEN_COMPONENTS for part in parts):
            errors.append(f"Ruta privada o proyecto ajeno prohibido: {item}")
        if Path(clean).suffix in FORBIDDEN_EXT:
            errors.append(f"Archivo binario personal o secreto prohibido: {item}")
        if clean.endswith((".env", ".env.local", ".env.production")):
            errors.append(f"Variables privadas del usuario prohibidas: {item}")
    return errors


def read(root: Path, file: str) -> str:
    path = root / file
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def check_contracts(root: Path, changed: list[str]) -> dict:
    issues: list[str] = []
    for path in REQUIRED_FILES:
        if not (root / path).is_file():
            issues.append(f"Falta componente de la cadena Phase53–57: {path}")
    gradle = read(root, "android-app/app/build.gradle")
    manifest = read(root, "android-app/app/src/main/AndroidManifest.xml")
    android_ci = read(root, ".github/workflows/okubi-retro.yml")
    windows_ci = read(root, ".github/workflows/okubi-desktop.yml")
    desktop = read(root, "desktop/src/bo/okubi/desktop/OkubiDesktop.java")
    qa_launcher = read(root, "desktop/QA57-LAUNCHER.cmd")
    shared_archive = read(root, "retro-module/java/bo/nexoplay/app/retro/OkubiSaveArchive.java")

    checks = {
        "Canon Android ID fijo": bool(re.search(
            r"(?m)^\s*applicationId\s+'bo\.nexoplay\.app\.unified'\s*$", gradle)),
        "Android versionCode original 36": bool(re.search(
            r"(?m)^\s*versionCode\s+36\s*$", gradle)),
        "Android variante QA57 opcional": (
            "project.findProperty('okubiQaSideBySide')" in gradle
            and "applicationIdSuffix '.qa57'" in gradle
            and "OKUBI QA57 遊" in gradle),
        "Android sin copia de datos del sistema": 'android:allowBackup="false"' in manifest,
        "Firma release solo manual desde main": (
            "github.event_name == 'workflow_dispatch' && github.ref_name == 'main'"
            in android_ci and "--pinned-sha256" in android_ci),
        "CI Android compila ambas variantes": (
            "gradle :app:assembleDebug --no-daemon" in android_ci
            and "gradle :app:assembleDebug -PokubiQaSideBySide=true" in android_ci
            and "OKUBI-A05S-QA57-SIDE-BY-SIDE-APK" in android_ci
            and "OKUBI-UNIFIED-1.0.0-beta17-APK" in android_ci),
        "CI Android verifica ambas firmas e identidades": (
            "tools/okubi_qa57_verify.py" in android_ci
            and "apksigner" in android_ci
            and "OKUBI-QA57-Android-SHA256SUMS.txt" in android_ci),
        "Windows empaqueta ZIP y manifiesto SHA256": (
            "jpackage --type app-image" in windows_ci
            and "OKUBI-Desktop-Windows-x64-SHA256SUMS.txt" in windows_ci
            and "Forbidden personal artifact" in windows_ci),
        "Windows QA aislamiento APPDATA": (
            "QA57-LAUNCHER.cmd" in windows_ci
            and 'set "APPDATA=' in qa_launcher
            and "OKUBI-QA57" in qa_launcher),
        "Windows conserva pruebas de partidas y ROMs": all(
            test in windows_ci for test in (
                "DesktopSaveVaultStabilityTest",
                "DesktopSmartSaveGuardTest",
                "DesktopUniversalSavePreflightTest",
                "DesktopQa57IsolationTest",
                "DesktopShutdownPolicyTest")),
        "Windows conserva núcleos y controles": all(
            test in windows_ci for test in (
                "EmbeddedCoreTest", "DesktopControllerFocusGateTest",
                "DesktopGameHubInputTest")),
        "Mismo serializador Cross Save en Windows y Android": (
            "OkubiSaveArchive.java" in windows_ci
            and "OkubiSaveArchiveTest" in android_ci
            and "class OkubiSaveArchive" in shared_archive),
        "Protección mutua de operaciones SAVE VAULT/CROSS/CLOUD": (
            "DesktopShutdownPolicy.mayExit" in desktop
            and "crossSaveBusy" in desktop
            and "cloudBusy" in desktop
            and "saveGuardBusy" in desktop),
        "No modificar estado de preparación existente": (
            "NO se fusion" in read(root, "docs/OKUBI_FINAL_INTEGRATION_PHASE58.md")
            or "NO FUSIONAR" in read(root, "docs/OKUBI_FINAL_INTEGRATION_PHASE58.md")),
    }
    issues.extend(f"Contrato fallido: {key}" for key, ok in checks.items() if not ok)
    issues.extend(protected_path_issues(changed))
    return {
        "status": "PASS" if not issues else "FAIL",
        "files_required": len(REQUIRED_FILES),
        "files_changed_audited": len(changed),
        "contracts_passed": sum(checks.values()),
        "contracts_total": len(checks),
        "contracts": checks,
        "issues": issues,
        "manual_checks": {
            "Windows PC / gamepad / save recovery": "PENDING",
            "Samsung A05s physical side-by-side installation": "PENDING",
            "Owner signing certificate continuity": "PENDING",
            "Libretro source and redistribution compliance review": "PENDING",
            "Approval to merge main and release": "PENDING",
        },
    }


def git(root: Path, *arguments: str, binary: bool = False):
    proc = subprocess.run(["git", "-C", str(root), *arguments],
                          check=True, capture_output=True)
    return proc.stdout if binary else proc.stdout.decode("utf-8", "replace").strip()


def audit_git(root: Path, base: str) -> dict:
    base_sha = git(root, "rev-parse", base)
    head_sha = git(root, "rev-parse", "HEAD")
    ancestor = subprocess.run(
        ["git", "-C", str(root), "merge-base", "--is-ancestor", base, "HEAD"],
        capture_output=True)
    if ancestor.returncode != 0:
        raise ValueError("main no es ancestro del candidato: revisar divergencia antes de integrar")
    names = git(root, "diff", "--name-only", "-z", f"{base}...HEAD",
                binary=True)
    changed = [x.decode("utf-8", "replace")
               for x in names.split(b"\0") if x]
    report = check_contracts(root, changed)
    report.update({
        "git_main_sha": base_sha,
        "git_candidate_sha": head_sha,
        "main_is_ancestor": True,
        "commits_ahead_main": int(git(root, "rev-list", "--count",
                                     f"{base}..HEAD")),
        "merge": "NOT PERFORMED",
        "release": "BLOCKED until user approval and physical/owner/license checks",
    })
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--base", required=True,
                        help="main origin reference fetched read-only")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        result = audit_git(args.root.resolve(), args.base)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        result = {"status": "FAIL", "issues": [str(error)]}
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2),
                               encoding="utf-8")
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
