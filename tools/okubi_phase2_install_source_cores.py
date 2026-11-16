#!/usr/bin/env python3
"""Install ONLY CI source-built audited Libretro Windows cores into a new OKUBI build.

Reuses Phase81's independently tested corresponding-source verifier. No network
downloads occur here; inputs are exact GitHub Actions artifacts named in the
Phase2 workflow, with explicit immutable run IDs and SHA256SUMS. Refuses to
ship nightly "latest" binaries or any unverified core.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from okubi_phase81b_source_bundle import (
    ROOT, PROVENANCE, provenance_payload, CheckError,
)

def install(source: Path, scripts: Path, destination: Path) -> dict:
    if destination.exists() and any(destination.iterdir()):
        raise CheckError("destination_must_be_empty")
    provenance, replacements = provenance_payload(source, scripts)
    destination.mkdir(parents=True, exist_ok=True)
    for filename, data in replacements.items():
        if not filename.startswith(ROOT):
            raise CheckError("unexpected_core_output_path")
        name = filename[len(ROOT):]
        path = destination / name
        # Inputs returned from phase81b verifier have fixed known names.
        if path.is_symlink() or not path.resolve().is_relative_to(destination.resolve()):
            raise CheckError("unsafe_install_path")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    # The SHA256 manifest includes the three exact built DLL hashes.
    expected = provenance["provenance"]
    actual = (destination / "CORE-SHA256.properties").read_text("ascii")
    for name, record in expected.items():
        if f"{name}={record['dll_sha256']}" not in actual:
            raise CheckError("missing_exact_core_integrity_pin")
    source_manifest = Path(PROVENANCE).relative_to(ROOT)
    (destination / source_manifest).write_text(
        json.dumps(provenance, indent=2, sort_keys=True) + "\n",
        encoding="utf-8"
    )
    print("PASS: source-built cores installed, with complete corresponding sources")
    for name, record in expected.items():
        print(f"SOURCE_BUILT_SHA256 {name} {record['dll_sha256']}")
    return provenance

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-builds", required=True, type=Path)
    parser.add_argument("--source-scripts", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        install(args.source_builds, args.source_scripts, args.output)
        return 0
    except (OSError, ValueError, RuntimeError, CheckError) as exc:
        print("BLOCKED: verify original signed CI artifacts and source-commit proof: "
              + str(exc))
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
