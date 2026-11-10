"""Safe Phase 72 tests using synthetic project source files only."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from okubi_phase72_release_audit import CORES, inspect, read, AuditError

PINS = (
    "aa158a889a48b538a0302873704a34577c8eb67d",
    "7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6",
    "642d1d1b6684aa7e306a02a89885f3f5456a5157",
)


class Phase72Tests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.builders = []
        notices = []
        for (name, path, const, url, win_path, license), pin in zip(CORES, PINS):
            self.write(path, f'{const} = "{pin}"\nUPSTREAM = "{url}"\n'
                             'git fetch --depth=1 origin\n'
                             'git checkout --detach revision\n'
                             'copy license to output\n')
            self.builders.append(path)
            notices.append(
                f"https://raw.githubusercontent.com/libretro/{win_path}{pin}/{license}"
            )
        self.write("desktop/build-bundled-cores.ps1",
                   'https://buildbot.libretro.com/nightly/windows/x86_64/latest/\n'
                   'Sha256 Get-FileHash CORE-SHA256.properties\n' + "\n".join(notices))
        self.write("android-app/app/build.gradle",
                   "applicationId 'bo.nexoplay.app.unified'\n"
                   "applicationIdSuffix '.qa57'\n"
                   "versionCode 36\n"
                   "OKUBI_KEYSTORE_PATH\nOKUBI_KEYSTORE_PASSWORD\n"
                   "OKUBI_KEY_ALIAS\nOKUBI_KEY_PASSWORD\n")
        self.write(".github/workflows/okubi-android-migrated-qa.yml",
                   "gradle :app:assembleDebug -PokubiQaSideBySide=true\napksigner\n")
        self.write(".github/workflows/okubi-desktop.yml",
                   "--verify-windows-release\nSHA256SUMS\n")
        self.write("tools/okubi_release_guard.py",
                   "current.cert_sha256 != previous.cert_sha256\n"
                   "current.code <= previous.code\n"
                   "if not pinned_sha256:\n"
                   "debug_certificate\n")
        self.write("tools/create_okubi_release_key.ps1",
                   "Test-Path -LiteralPath $target\n"
                   "NEVER upload\n-genkeypair\n")

    def write(self, rel, value):
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value, encoding="utf-8")

    def edit(self, rel, before, after):
        old = (self.root / rel).read_text(encoding="utf-8")
        self.assertIn(before, old)
        self.write(rel, old.replace(before, after))

    def test_clean_contract_still_blocks_public_release(self):
        result = inspect(self.root)
        self.assertEqual(result["automated_checks"], "pass")
        self.assertFalse(result["public_release_authorized"])
        self.assertFalse(result["release_signing_performed"])
        self.assertEqual(len(result["code_provenance"]["android_pinned_revisions"]), 3)
        self.assertIn("windows_libretro_binary_exact_source_unverified",
                      result["remaining_release_blockers"])

    def test_rollback_or_wrong_source_notice_is_rejected(self):
        self.edit("desktop/build-bundled-cores.ps1", PINS[0], "f" * 40)
        self.assertIn("notice_source_revision_mismatch:SameBoy",
                      inspect(self.root)["checks_failed"])

    def test_unpinned_builder_is_rejected(self):
        self.edit(self.builders[1], PINS[1], "branch-main")
        self.assertIn("source_revision_missing:MGBA_COMMIT",
                      inspect(self.root)["checks_failed"])

    def test_upstream_move_is_rejected(self):
        self.edit(self.builders[2], "https://github.com/libretro/supafaust.git",
                  "https://example.org/different.git")
        self.assertIn("source_repository_changed:SUPAFAUST_COMMIT",
                      inspect(self.root)["checks_failed"])

    def test_owner_signer_variable_is_required(self):
        self.edit("android-app/app/build.gradle", "OKUBI_KEY_PASSWORD", "ANOTHER")
        self.assertIn("android_owner_signature_contract_missing:OKUBI_KEY_PASSWORD",
                      inspect(self.root)["checks_failed"])

    def test_qa_does_not_overwrite_normal_android(self):
        self.edit("android-app/app/build.gradle", "applicationIdSuffix '.qa57'",
                  "applicationIdSuffix '.main'")
        self.assertIn("android_parallel_qa_identity_missing",
                      inspect(self.root)["checks_failed"])

    def test_signature_update_check_cannot_disappear(self):
        self.edit("tools/okubi_release_guard.py", "current.cert_sha256 != previous.cert_sha256",
                  "True")
        self.assertIn("android_update_cert_or_version_rejection_missing",
                      inspect(self.root)["checks_failed"])

    def test_windows_core_hash_guard_is_mandatory(self):
        self.edit("desktop/build-bundled-cores.ps1", "Get-FileHash", "ignore-hash")
        self.assertIn("windows_core_checksum_check_missing",
                      inspect(self.root)["checks_failed"])

    def test_symlinked_builder_is_rejected(self):
        path = self.root / self.builders[0]
        path.unlink()
        path.symlink_to(self.root / self.builders[1])
        with self.assertRaises(AuditError):
            read(self.root, self.builders[0])

    def test_a_nonrolling_url_alone_is_not_proof_of_binary_source(self):
        self.edit("desktop/build-bundled-cores.ps1",
                  "nightly/windows/x86_64/latest/",
                  "nightly/windows/x86_64/immutable/")
        result = inspect(self.root)
        self.assertEqual(result["automated_checks"], "pass")
        self.assertIn("windows_libretro_binary_exact_source_still_requires_evidence",
                      result["remaining_release_blockers"])


if __name__ == "__main__":
    unittest.main()
