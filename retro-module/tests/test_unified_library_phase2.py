"""Phase 2 source contracts for non-destructive unified local game library.

Static checks cover regression-prone integration points; passing them is not a
substitute for running Android/JNI builds and testing a real phone.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "android-app/app/src/main/java/bo/nexoplay/app"
RETRO = ROOT / "retro-module/java/bo/nexoplay/app/retro"


class UnifiedLibraryContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = (ANDROID / "OkubiUnifiedLibrary.java").read_text(encoding="utf-8")
        cls.main = (ANDROID / "MainActivity.java").read_text(encoding="utf-8")
        cls.store = (ANDROID / "GameStore.java").read_text(encoding="utf-8")
        cls.repo = (RETRO / "OkubiPokemonRepository.java").read_text(encoding="utf-8")
        cls.pokemon = (RETRO / "OkubiPokemonActivity.java").read_text(encoding="utf-8")

    def test_reads_existing_sources_without_moving_game_bytes(self):
        source = self.index
        self.assertIn("for (GameApp game : catalog.selected(store))", source)
        self.assertIn("for (OkubiPokemonRepository.Item game : OkubiPokemonRepository.list(context))", source)
        self.assertIn("OkubiPersonalLibrary.available(context)", source)
        self.assertIn("game.id, game.title, \"ANDROID\"", source)
        self.assertIn('"retro:uri:" + digest(uri)', source)
        self.assertIn('"retro:asset:" + digest(asset)', source)
        self.assertNotIn("getAssets().open(", source)
        self.assertNotIn("FileOutputStream", source)

    def test_favorites_remain_in_original_stores(self):
        self.assertIn("store.toggleFavorite(entry.id)", self.index)
        self.assertIn("OkubiPokemonRepository.setFavorite(context, entry.uri, favorite)", self.index)
        self.assertIn("prefs.edit().putBoolean(\"fav_\" + entry.id, favorite)", self.index)
        self.assertIn("OkubiPokemonRepository.markPlayed(context, entry.uri)", self.index)

    def test_unselect_android_does_not_erase_customization(self):
        # Selection and exclusion keys still exist, but removal is not erasure.
        self.assertIn('.putStringSet(SELECTED,next)', self.store)
        self.assertIn('"okubi_hidden_components"', self.store)
        self.assertNotIn('edit.remove("cover_v05_"+id)', self.store)
        self.assertNotIn('edit.remove("launch_ms_"+id)', self.store)
        self.assertNotIn("edit.remove(PersonalGoals.key(id))", self.store)
        self.assertNotIn("lists.removeGame(id)", self.store)

    def test_retro_hide_and_readd_preserve_original_metadata(self):
        self.assertIn('"hidden_game_files_v2"', self.repo)
        self.assertIn("public static List<Item> list(Context context)", self.repo)
        self.assertIn("visible.removeIf(item -> hidden.contains(item.uri))", self.repo)
        self.assertIn("if (known) setHidden(context, address, true)", self.repo)
        self.assertIn("setHidden(context, address, false)", self.repo)
        self.assertIn("List<Item> list = allItems(context)", self.repo)

    def test_unified_filters_tiles_and_collections(self):
        main = self.main
        for word in (
            '"all"', '"android"', '"retro"',
            "unifiedLibrary.all()", "unifiedTile(entry,pos)",
            "unifiedLibrary.setFavorite(entry,!entry.favorite)",
            "openUnifiedDetails(entry)", "launchRetroEntry(entry)",
            "collections.setMembership(all.get(i).id,entry.id,checked[i])",
            "Set<String> members=new HashSet<>(target.gameIds)",
        ):
            self.assertIn(word, main)
        self.assertIn("star.setContentDescription", main)
        self.assertIn("loadRetroCover(entry,art)", main)
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", main)
        self.assertIn("takePersistableUriPermission", main)
        self.assertIn("coverWorker.execute", main)

    def test_legacy_official_imports_mirrored_once(self):
        self.assertIn('migrateLegacyBookmarksOnce()', self.pokemon)
        self.assertIn('"okubi_unified_index_migrated_v1"', self.pokemon)
        self.assertIn('getString("entry_official_"+i,null)', self.pokemon)
        self.assertIn('getString("entry_hack_"+i,null)', self.pokemon)
        self.assertIn("OkubiPokemonRepository.add(this,uri,", self.pokemon)


if __name__ == "__main__":
    unittest.main()
