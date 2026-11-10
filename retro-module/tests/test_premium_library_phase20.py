"""Phase 20: owner-selected spotlight + direct recent-play shelf.

The selection policy also runs in Java, not just text checks.
No new permissions, ROM copying or save file changes.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
MAIN=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/MainActivity.java").read_text(encoding="utf-8")
LIB=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/OkubiUnifiedLibrary.java").read_text(encoding="utf-8")
SPOT=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/OkubiLibrarySpotlight.java").read_text(encoding="utf-8")
STORE=(ROOT/"android-app/app/src/main/java/bo/nexoplay/app/GameStore.java").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")
GRADLE=(ROOT/"android-app/app/build.gradle").read_text(encoding="utf-8")


class PremiumLibraryPhase20(unittest.TestCase):
    def test_no_android_bias_and_stale_pin_falls_back(self):
        self.assertIn("unifiedLibrary.spotlight(allEntries)",MAIN)
        self.assertIn("spotlight.kind==OkubiUnifiedLibrary.Kind.ANDROID",MAIN)
        self.assertIn("spotlight.kind!=OkubiUnifiedLibrary.Kind.ANDROID",MAIN)
        self.assertIn("OkubiLibrarySpotlight.select(",LIB)
        self.assertIn("if (pinnedId != null && pinnedId.equals(candidate.id))",SPOT)
        self.assertIn("int played = Long.compare(right.lastPlayed, left.lastPlayed)",SPOT)
        self.assertIn("if (best == null || compare(candidate, best) < 0)",SPOT)
        self.assertIn("return best == null ? null : best.id;",SPOT)
        self.assertIn("CoverArt.apply(this,image,store,featured)",MAIN)
        self.assertIn("loadRetroCover(retroHero,image)",MAIN)

    def test_pin_only_existing_entry_and_store_in_own_prefs(self):
        self.assertIn('private static final String SPOTLIGHT_KEY = "home_spotlight_id_v20";',LIB)
        self.assertIn("if (entry == null || find(entry.id) == null) return;",LIB)
        self.assertIn("prefs.edit().putString(SPOTLIGHT_KEY, entry.id).apply();",LIB)
        self.assertIn("prefs.edit().remove(SPOTLIGHT_KEY).apply();",LIB)
        self.assertIn("prefs.getString(SPOTLIGHT_KEY, null)",LIB)
        self.assertIn("public boolean isSpotlightPinned(Entry entry)",LIB)
        self.assertIn("public void clearSpotlight()",LIB)
        self.assertNotIn("FileOutputStream",LIB)
        self.assertNotIn("getFilesDir()",LIB)
        self.assertIn("markLaunch(game.id)",STORE)

    def test_hero_picker_is_real_and_bounded(self):
        self.assertIn('private void showSpotlightPicker()',MAIN)
        self.assertIn('Math.min(40,choices.size())',MAIN)
        self.assertIn("unifiedLibrary.setSpotlight(choices.get(which));",MAIN)
        self.assertIn("unifiedLibrary.clearSpotlight();",MAIN)
        self.assertIn("pickSpotlight.setOnClickListener(v->showSpotlightPicker())",MAIN)
        self.assertIn("unifiedLibrary.isSpotlightPinned(spotlight)",MAIN)
        self.assertIn("pinned",LIB.lower())
        self.assertIn("pinSpotlight(entry)",MAIN)
        self.assertIn("item.setOnLongClickListener",MAIN)
        self.assertIn("tile.setOnLongClickListener",MAIN)

    def test_one_tap_recent_games_and_library_filters(self):
        self.assertIn('TextView launchNow=CinemaUi.action(this,"▶  JUGAR AHORA",true)',MAIN)
        self.assertIn("launchNow.setOnClickListener(v->launchUnifiedEntry(entry));",MAIN)
        self.assertIn("private void launchUnifiedEntry(OkubiUnifiedLibrary.Entry entry)",MAIN)
        self.assertIn("if(entry.androidGame!=null)launch(entry.androidGame);",MAIN)
        self.assertIn("else launchRetroEntry(entry);",MAIN)
        self.assertIn("public void markOpened(Entry entry)",LIB)
        self.assertIn('CinemaUi.chip(this,"JUGADOS",libraryScope.equals("played"))',MAIN)
        self.assertIn('if("played".equals(libraryScope) && entry.lastPlayed<=0)continue;',MAIN)
        self.assertIn('"all"',MAIN)
        self.assertIn('"android"',MAIN)
        self.assertIn('"retro"',MAIN)
        self.assertIn("unifiedLibrary.setFavorite(entry,!entry.favorite)",MAIN)

    def test_no_save_data_or_installer_changes(self):
        for forbidden in ("new FileOutputStream(", "SameBoyNative.native",
                          "new AtomicFile(", "getFilesDir(", "deleteRecursively("):
            self.assertNotIn(forbidden,SPOT)
        self.assertIn("versionCode 36",GRADLE)
        self.assertIn("versionName '1.0.0-unified-beta17'",GRADLE)
        self.assertIn("applicationId 'bo.nexoplay.app.unified'",GRADLE)
        self.assertIn("--minimum-version-code 36",WORKFLOW)
        self.assertIn("OKUBI-UNIFIED-1.0.0-beta17-APK",WORKFLOW)
        self.assertIn("OkubiLibrarySpotlightTest.java",WORKFLOW)
        self.assertIn("RetroSlotFileStampTest.java",WORKFLOW)
        self.assertIn("RetroSlotPreviewsTest.java",WORKFLOW)
        self.assertIn("RetroSaveSlotsTest.java",WORKFLOW)


if __name__ == "__main__":
    unittest.main()
