"""Phase81b: synthetic evidence, tamper and no-data-loss tests."""
from __future__ import annotations

import hashlib
import io
import json
import os
import sys
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import okubi_phase81b_source_bundle as b
from okubi_phase80_rc_preflight import CheckError


def hash_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class SourceBundleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.inputs = self.root / "inputs"
        self.inputs.mkdir()
        self.tools = self.root / "scripts"
        self.tools.mkdir()
        self.old_zip = self.root / b.WINDOWS_ZIP
        self.old_checksums = self.root / "old-sha.txt"
        self.new_zip = self.root / "out" / b.OUTPUT_NAME
        self.previous = {}
        self.baseline = {}
        for name, spec in b.EXPECTED.items():
            out = self.inputs / (name + "-artifact")
            out.mkdir()
            dll_bytes = b"MZ" + (name.encode("ascii") + b"X") * 100
            source_data = io.BytesIO()
            with tarfile.open(fileobj=source_data, mode="w:gz") as tar:
                for i in range(11):
                    payload = (name + ":source:" + str(i)).encode()
                    info = tarfile.TarInfo(name + "-source/" + str(i) + ".txt")
                    info.size = len(payload)
                    tar.addfile(info, io.BytesIO(payload))
            source = source_data.getvalue()
            license_data = b"TEST LICENSE NOTICE " * 10
            script_data = b"#!/bin/sh\n# synthetic source recipe only\n" * 3
            (out / spec["dll"]).write_bytes(dll_bytes)
            (out / spec["archive"]).write_bytes(source)
            (out / spec["license"]).write_bytes(license_data)
            (self.tools / spec["script"]).write_bytes(script_data)
            files = [spec["dll"], spec["archive"], spec["license"]]
            if name == "mgba":
                (out / "SOURCE-BUILD-SCRIPT.sh").write_bytes(script_data)
                files.append("SOURCE-BUILD-SCRIPT.sh")
            (out / "SHA256SUMS.txt").write_text(
                "".join(hash_bytes((out / filename).read_bytes()) + " *" + filename + "\n"
                        for filename in files), encoding="ascii")
            prev_core = b"MZ" + b"OLD" * 120
            self.previous[spec["dll"]] = prev_core
            self.baseline[spec["dll"]] = {"dll_sha256": hash_bytes(prev_core)}
        manifest = "".join(f"{key}={hash_bytes(value)}\n"
                           for key, value in self.previous.items()).encode()
        app = "OKUBI Desktop/app/"
        files = {
            "OKUBI Desktop/OKUBI Desktop.exe": b"MZ" * 70,
            app + "okubi-desktop.jar": b"synthetic Java app" * 10,
            app + "okubi_libretro_jni.dll": b"MZ" * 70,
            app + "RUN-QA57-ISOLATED.cmd":
                b'@echo off\nset "APPDATA=OKUBI-QA57"\n',
            app + "QA57-READ-ME-FIRST.md": b"synthetic testing text " * 4,
            app + "PHASE80-READ-ME-FIRST.md": b"another safety text" * 4,
            b.MANIFEST: manifest,
            app + "unrelated-settings.json": b'{"theme":"cinema","userFiles":false}',
            app + "unrelated-app.jar": b"DO NOT CHANGE" * 50,
            app + "large-test-fixture.dat": os.urandom(14000),
        }
        for filename, data in self.previous.items():
            files[b.ROOT + filename] = data
        for filename in ("SameBoy-MIT.txt", "mGBA-MPL-2.0.txt",
                         "Supafaust-GPL-2.0-plus.txt", "THIRD-PARTY-SOURCES.txt"):
            files[b.ROOT + "licenses/" + filename] = b"Old license notice" * 5
        with zipfile.ZipFile(self.old_zip, "w", compression=zipfile.ZIP_STORED) as z:
            for filename, data in files.items():
                z.writestr(filename, data)
        self.old_checksums.write_text(
            hash_bytes(self.old_zip.read_bytes()) + " *" + self.old_zip.name + "\n",
            encoding="ascii")
        self.evidence = {"releases_authorized": False, "cores": [
            {"name": key, "dll_sha256": hash_bytes(value)}
            for key, value in self.previous.items()]}

    def build(self):
        return b.assemble(self.old_zip, self.old_checksums, self.evidence,
                          self.inputs, self.tools, self.new_zip)

    def test_all_source_built_cores_and_corresponding_archives_are_packaged(self):
        report = self.build()
        self.assertEqual(report["cores_verified"], 3)
        self.assertEqual(report["corresponding_source_archives_included"], 3)
        self.assertFalse(report["public_release_authorized"])
        with zipfile.ZipFile(self.new_zip) as z:
            self.assertEqual(z.read("OKUBI Desktop/app/unrelated-app.jar"),
                             b"DO NOT CHANGE" * 50)
            for name, spec in b.EXPECTED.items():
                self.assertEqual(z.read(b.ROOT + spec["dll"]),
                                 (self.inputs / (name + "-artifact") / spec["dll"]).read_bytes())
                self.assertGreater(len(z.read(b.SOURCE_ROOT + spec["archive"])), 100)
            self.assertIn(b"source-built", z.read(b.README))
        self.assertEqual(self.old_zip.name, b.WINDOWS_ZIP)
        self.assertEqual(self.old_checksums.read_text("ascii").split()[0],
                         hash_bytes(self.old_zip.read_bytes()))

    def test_bad_source_built_dll_checksum_is_rejected(self):
        filename = self.inputs / "mgba-artifact" / "mgba_libretro.dll"
        filename.write_bytes(b"MZ" + b"attacker content" * 30)
        with self.assertRaisesRegex(CheckError, "hash_mismatch"):
            self.build()

    def test_missing_source_archive_is_rejected(self):
        (self.inputs / "supafaust-artifact" /
         b.EXPECTED["supafaust"]["archive"]).unlink()
        with self.assertRaisesRegex(CheckError, "source_artifact"):
            self.build()

    def test_bad_original_zip_checksum_is_rejected(self):
        self.old_checksums.write_text("0" * 64 + " *" + self.old_zip.name + "\n")
        with self.assertRaisesRegex(CheckError, "checksum_mismatch"):
            self.build()

    def test_unexpected_output_name_or_overwrite_blocked(self):
        with self.assertRaisesRegex(CheckError, "unsafe_output_path"):
            b.assemble(self.old_zip, self.old_checksums, self.evidence,
                       self.inputs, self.tools, self.old_zip)

    def test_modified_build_script_is_rejected(self):
        (self.tools / b.EXPECTED["mgba"]["script"]).write_text("malicious script")
        with self.assertRaisesRegex(CheckError, "build_recipe_hash_mismatch"):
            self.build()


if __name__ == "__main__":
    unittest.main()
