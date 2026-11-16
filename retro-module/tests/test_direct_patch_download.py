"""Safe one-click patch downloads must never fetch commercial game ROMs."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
CATALOG = (ROOT / "java/bo/nexoplay/app/retro/OkubiCatalogActivity.java").read_text(encoding="utf-8")
POKEMON = (ROOT / "java/bo/nexoplay/app/retro/OkubiPokemonActivity.java").read_text(encoding="utf-8")

class VerifiedPatchDownloads(unittest.TestCase):
    def test_only_author_published_patches_and_pinned_releases(self):
        self.assertIn("private static final DirectPatch[] VERIFIED_PATCHES", CATALOG)
        for project, version in (
            ("RadicalRed-TraduccionCastellano", "v0.992"),
            ("EmeraldRogue-Castellano", "v0.66"),
            ("GhostGrey-Traduccion", "v0.992"),
        ):
            self.assertIn("https://github.com/SeCaVa/" + project +
                          "/releases/download/" + version + "/", CATALOG)
        self.assertEqual(3, len(re.findall(r"new DirectPatch\(", CATALOG)))
        self.assertIn("NO Rojo Fuego español", CATALOG)
        self.assertIn("NO Esmeralda español", CATALOG)
        self.assertNotIn("FullGBAroms/releases/download", CATALOG)

    def test_os_download_manager_and_https_enforced(self):
        for marker in (
            "DownloadManager manager",
            "new DownloadManager.Request(uri)",
            "manager.enqueue(request)",
            "setDestinationInExternalPublicDir(",
            "setDestinationInExternalFilesDir(",
            "DownloadManager.STATUS_SUCCESSFUL",
            "DownloadManager.STATUS_RUNNING",
            '"https".equals(uri.getScheme())',
            '"github.com".equals(uri.getHost())',
            'uri.getPath().endsWith(".bps")',
            "VISIBILITY_VISIBLE_NOTIFY_COMPLETED",
        ):
            self.assertIn(marker, CATALOG)

    def test_patch_flow_connected_to_pokemon_tab(self):
        self.assertIn("EXTRA_OPEN_PATCHES", CATALOG)
        self.assertIn("EXTRA_OPEN_PATCHES", POKEMON)
        self.assertIn('tab = "ROM hacks"', POKEMON)
        self.assertIn("↓ Descargar parches .bps directamente", POKEMON)

if __name__ == "__main__":
    unittest.main()
