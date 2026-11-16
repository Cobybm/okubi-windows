"""Phase 5 visual system regression contracts for the OKUBI Android launcher.

Source-level checks protect accessibility, navigation, screen wiring and
previous-phase data preservation. Physical Android visual / touch QA is required.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT / "android-app/app/src/main/java/bo/nexoplay/app"
MAIN=(JAVA/"MainActivity.java")
UI=(JAVA/"CinemaUi.java")
DETAIL=(JAVA/"GameDetailActivity.java")


class Phase5CinemaContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main=MAIN.read_text(encoding="utf-8")
        cls.ui=UI.read_text(encoding="utf-8")
        cls.detail=DETAIL.read_text(encoding="utf-8")

    def test_midnight_brand_identity_and_accessible_components(self):
        u=self.ui
        for marker in ("BG = 0xFF090911", "SURFACE = 0xFF181824",
                       "WHITE = 0xFFF8F7FA", "MUTED = 0xFFA9A8BD",
                       "GOLD = 0xFFFF3E70", "public static TextView chip",
                       "public static TextView eyebrow", "public static void actionable"):
            self.assertIn(marker,u)
        self.assertIn("t.setMinHeight(dp(c,48))",u)
        self.assertIn("new RippleDrawable(",u)
        self.assertIn("node.setClassName(android.widget.Button.class.getName())",u)
        self.assertIn("view.setContentDescription(description)",u)

    def test_home_hero_reuses_real_library_and_play_handlers(self):
        m=self.main
        self.assertIn("List<OkubiUnifiedLibrary.Entry> allEntries=unifiedLibrary.all()",m)
        self.assertIn("body.addView(hero,lp(-1,320))",m)
        self.assertIn("CoverArt.apply(this,image,store,featured)",m)
        self.assertIn("loadRetroCover(retroHero,image)",m)
        self.assertIn("hero.setOnClickListener(v->{",m)
        self.assertIn("else if(featured!=null)openDetails(featured)",m)
        self.assertIn("else openUnifiedDetails(retroHero)",m)
        self.assertIn("else if(featured!=null)launch(featured)",m)
        self.assertIn("else launchRetroEntry(retroHero)",m)

    def test_touchable_tiles_without_losing_favorite_or_cover(self):
        m=self.main
        self.assertIn("CinemaUi.actionable(this,tile",m)
        self.assertIn("CinemaUi.actionable(this,star",m)
        self.assertIn("CinemaUi.actionable(this,fav",m)
        self.assertIn("unifiedLibrary.setFavorite(entry,!entry.favorite)",m)
        self.assertIn("store.toggleFavorite(game.id)",m)
        self.assertIn("loadRetroCover(entry,art)",m)
        self.assertIn("CoverArt.apply(this,image,store,game)",m)
        self.assertIn('TextView platform=text("ANDROID"',m)

    def test_retro_game_sheet_preserves_every_existing_command(self):
        m=self.main
        self.assertIn("private void openUnifiedDetails(OkubiUnifiedLibrary.Entry entry)",m)
        self.assertIn("new AlertDialog.Builder(this)",m)
        self.assertIn(".setView(scrollSheet)",m)
        self.assertIn("poster.setOnClickListener(v->{popup.dismiss();launchRetroEntry(entry);})",m)
        self.assertIn("popup.setOnDismissListener(dialog->coverTargets.remove(posterArt))",m)
        for expected in ("launchRetroEntry(entry)", "chooseRetroCover(entry)",
                         "unifiedLibrary.setRetroCover(entry,null)",
                         "chooseCollectionsFor(entry)", "confirmHideRetro(entry)"):
            self.assertIn(expected,m)

    def test_android_profile_stays_functional_and_nondestructive(self):
        d=self.detail
        self.assertIn('CinemaUi.actionable(this,artwork,"Iniciar "+game.title)',d)
        self.assertIn("artwork.setOnClickListener(v->launchCurrentGame())",d)
        self.assertIn("play.setOnClickListener(v->launchCurrentGame())",d)
        self.assertIn("store.toggleFavorite(game.id)",d)
        self.assertIn("personalGoals.toggle(game.id,goal.id)",d)
        self.assertIn("collections.setMembership(",d)
        self.assertIn("store.remove(game.id);finish();",d)
        self.assertNotIn("collections.removeGame(game.id)",d)

    def test_reduced_motion_nav_and_paginated_library_preserved(self):
        m=self.main
        self.assertIn("AppearancePrefs.reducedMotion(this)",m)
        self.assertIn("body.animate().alpha(1f).translationY(0f).setDuration(160)",m)
        self.assertIn("body.setTranslationY(0f)",m)
        self.assertIn("nav.setElevation(dp(12))",m)
        self.assertIn("indicator.setVisibility(active?View.VISIBLE:View.INVISIBLE)",m)
        self.assertIn("if(screen!=target)showScreen(target)",m)
        for marker in ('Screen.HOME','Screen.LIBRARY','Screen.RETRO',
                       'Screen.DISCOVER','Screen.PROFILE','LIBRARY_PAGE_SIZE = 36',
                       'new ArrayBlockingQueue<>(12)', 'coverTargets.clear()'):
            self.assertIn(marker,m)

if __name__=="__main__":
    unittest.main()
