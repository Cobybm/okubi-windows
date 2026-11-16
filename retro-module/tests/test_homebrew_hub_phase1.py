"""Phase 1 legal Homebrew Hub catalog wiring and downloader safety guards."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
CLIENT=(ROOT/"retro-module/java/bo/nexoplay/app/retro/OkubiHomebrewHubClient.java").read_text(encoding="utf-8")
CATALOG=(ROOT/"retro-module/java/bo/nexoplay/app/retro/OkubiCatalogActivity.java").read_text(encoding="utf-8")
RETRO=(ROOT/"retro-module/java/bo/nexoplay/app/retro/OkubiGameBoyActivity.java").read_text(encoding="utf-8")
INTEGRATE=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")

class HomebrewHubPhase1Tests(unittest.TestCase):
    def test_client_is_in_actual_android_build(self):
        self.assertIn('"OkubiHomebrewHubClient.java"',INTEGRATE)
        self.assertIn("showHomebrewHubSearch(root)",CATALOG)
        self.assertIn("OkubiHomebrewHubClient.search(selected, query)",CATALOG)
        self.assertIn("matches.removeAllViews()",CATALOG)

    def test_search_is_readonly_and_limited_to_oss_cartidges(self):
        for expected in ('https://hh3.gbdev.io/api/search?platform=',
                         'conn.setInstanceFollowRedirects(false)',
                         'buffer.size() + n > 512 * 1024',
                         '!openLicense(license)', 'getJSONArray("entries")',
                         'optBoolean("playable",false)',
                         '"game".equalsIgnoreCase(entry.optString("typetag"))',
                         '"GB".equals(platform)', '"GBC".equals(platform)',
                         '"GBA".equals(platform)'):
            self.assertIn(expected,CLIENT)
        self.assertNotIn('ROMarr',CLIENT)
        self.assertNotIn('fullgbaroms',CATALOG.lower())

    def test_downloader_only_accepts_known_licensed_sources(self):
        for expected in ('game.hubApproved','"hh3.gbdev.io".equals(uri.getHost())',
                         'uri.getQuery() != null || uri.getFragment() != null',
                         'game.privateFileName', 'hasGameBoyHeader(file)',
                         'AtomicFile(target)','connection.setInstanceFollowRedirects(false)',
                         'MAX_ROM_BYTES', 'privateHomebrew = verifiedHomebrewPath'):
            self.assertIn(expected,CATALOG+"\n"+RETRO)
        self.assertIn('requested.getName().endsWith(".gba")',RETRO)
        self.assertIn('requested.getName().endsWith(".gbc")',RETRO)

if __name__=="__main__":
    unittest.main()
