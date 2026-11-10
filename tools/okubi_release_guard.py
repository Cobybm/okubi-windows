#!/usr/bin/env python3
"""Offline APK release preflight for OKUBI 遊.

Read-only verification of actual APK manifest, cryptographic signatures and
optional previous-version comparison. This tool never opens a keystore, asks for
a password, installs/uninstalls an app, or touches ROMs/user saves.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

CANONICAL_PACKAGE = "bo.nexoplay.app.unified"


class ReleaseCheckError(ValueError):
    pass


@dataclass(frozen=True)
class ApkIdentity:
    package: str
    code: int
    name: str
    cert_sha256: str
    debug_certificate: bool
    verified_scheme: bool


def normalized_fingerprint(value: str) -> str:
    """Accept keytool colon format and apksigner plain format."""
    clean = value.strip().replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", clean):
        raise ReleaseCheckError("Huella SHA-256 inválida (deben ser 64 hexadecimales)")
    return clean


def parse_badging(output: str) -> tuple[str, int, str]:
    match = re.search(
        r"(?m)^package:\s+name='([^']+)'\s+versionCode='([^']+)'"
        r"\s+versionName='([^']*)'",
        output,
    )
    if not match:
        raise ReleaseCheckError("No se pudo leer paquete y versión del APK")
    try:
        code = int(match.group(2), 10)
    except ValueError as error:
        raise ReleaseCheckError("versionCode no es un número entero") from error
    if code <= 0:
        raise ReleaseCheckError("versionCode debe ser positivo")
    return match.group(1), code, match.group(3)


def parse_signer(output: str) -> tuple[str, bool, bool]:
    # Verify output is emitted only after apksigner verify exits successfully.
    digest = re.findall(
        r"(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$",
        output,
    )
    if len(digest) != 1:
        raise ReleaseCheckError("Debe haber exactamente un firmante SHA-256")
    fingerprint = normalized_fingerprint(digest[0])
    debug = bool(re.search(
        r"(?im)^Signer #\d+ certificate DN:.*\bCN\s*=\s*Android Debug\b",
        output,
    ))
    verified_scheme = bool(re.search(
        r"(?im)^Verified using v[234] scheme[^\n]*:\s*true\s*$",
        output,
    ))
    if not verified_scheme:
        raise ReleaseCheckError("Falta comprobación de firma APK v2/v3/v4")
    return fingerprint, debug, verified_scheme


def parse_apk(badging: str, signer_output: str) -> ApkIdentity:
    package, code, name = parse_badging(badging)
    fingerprint, debug, scheme = parse_signer(signer_output)
    return ApkIdentity(package, code, name, fingerprint, debug, scheme)


def validate(
    current: ApkIdentity,
    *,
    kind: str,
    expected_name: str | None,
    minimum_code: int,
    pinned_sha256: str | None,
    previous: ApkIdentity | None,
) -> None:
    if kind not in ("debug", "release"):
        raise ReleaseCheckError("Tipo de APK desconocido")
    if current.package != CANONICAL_PACKAGE:
        raise ReleaseCheckError(
            f"Paquete incorrecto: {current.package}. Debe ser {CANONICAL_PACKAGE}"
        )
    if current.code < minimum_code:
        raise ReleaseCheckError("versionCode menor al mínimo requerido")
    if expected_name and current.name != expected_name:
        raise ReleaseCheckError("versionName no coincide con la versión anunciada")
    if kind == "release":
        if current.debug_certificate:
            raise ReleaseCheckError("No se permiten certificados Android Debug en release")
        if not pinned_sha256:
            raise ReleaseCheckError(
                "Falta la huella pública OKUBI_RELEASE_CERT_SHA256. "
                "La versión estable no se publica sin identidad fijada."
            )
        if current.cert_sha256 != normalized_fingerprint(pinned_sha256):
            raise ReleaseCheckError(
                "Firma distinta a la identidad de release; actualización bloqueada"
            )
    if previous is not None:
        if previous.package != current.package:
            raise ReleaseCheckError("El APK previo pertenece a otra aplicación")
        if current.code <= previous.code:
            raise ReleaseCheckError(
                "versionCode debe aumentar para actualizar la misma aplicación"
            )
        if current.cert_sha256 != previous.cert_sha256:
            raise ReleaseCheckError(
                "No se puede actualizar sin pérdida de instalación: firma diferente"
            )


def run_checked(command: list[str]) -> str:
    try:
        r = subprocess.run(
            command, capture_output=True, encoding="utf-8",
            errors="replace", timeout=45, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ReleaseCheckError(f"No se pudo ejecutar la herramienta: {command[0]}") from error
    if r.returncode != 0:
        # Avoid leaking tool arguments, environment or potentially sensitive logs.
        raise ReleaseCheckError(f"Error al verificar APK con {Path(command[0]).name}")
    return r.stdout


def examine(apk: Path, aapt: str, apksigner: str) -> ApkIdentity:
    if not apk.is_file() or apk.stat().st_size < 10000:
        raise ReleaseCheckError("APK ausente o demasiado pequeño")
    badging = run_checked([aapt, "dump", "badging", str(apk)])
    signature = run_checked([apksigner, "verify", "--verbose",
                             "--print-certs", str(apk)])
    return parse_apk(badging, signature)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verificar una actualización OKUBI sin instalarla"
    )
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aapt", default="aapt")
    parser.add_argument("--apksigner", default="apksigner")
    parser.add_argument("--kind", choices=("debug", "release"), required=True)
    parser.add_argument("--minimum-version-code", type=int, default=1)
    parser.add_argument("--expected-version-name")
    parser.add_argument("--pinned-sha256",
                        help="Huella pública SHA-256 del certificado RELEASE, no una clave")
    parser.add_argument("--previous-apk", type=Path,
                        help="APK anterior para comprobar compatibilidad de actualización")
    args = parser.parse_args()

    try:
        current = examine(args.apk, args.aapt, args.apksigner)
        previous = None
        if args.previous_apk:
            previous = examine(args.previous_apk, args.aapt, args.apksigner)
        validate(
            current,
            kind=args.kind,
            expected_name=args.expected_version_name,
            minimum_code=args.minimum_version_code,
            pinned_sha256=args.pinned_sha256,
            previous=previous,
        )
    except ReleaseCheckError as error:
        print(f"OKUBI UPDATE GUARD: RECHAZADO: {error}", file=sys.stderr)
        return 1
    print(f"OKUBI UPDATE GUARD: APROBADO | {current.package} "
          f"v{current.code} ({current.name})")
    print("Huella pública de la firma SHA-256:", current.cert_sha256)
    print("No se instaló ni desinstaló ninguna aplicación.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
