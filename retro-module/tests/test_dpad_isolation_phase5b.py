"""Regression contracts for the OKUBI Phase 5B isolated D-pad.

These checks supplement a real pure-Java hit-map test and Android APK build.
Touch latency and actual A05s ergonomics still need real-device verification.
"""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
RETRO=ROOT/"retro-module/java/bo/nexoplay/app/retro"
CONTROLS=(RETRO/"RetroTouchPad.java").read_text(encoding="utf-8")
MAPPER=(RETRO/"RetroDpadHitMap.java").read_text(encoding="utf-8")
INTEGRATE=(ROOT/"retro-module/integrate.py").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/okubi-retro.yml").read_text(encoding="utf-8")


class IsolatedDpadContracts(unittest.TestCase):
    def test_touch_uses_exclusive_cardinal_mapper_not_sign_combinations(self):
        self.assertIn("RetroDpadHitMap.hit(",CONTROLS)
        for direction in ("UP","DOWN","LEFT","RIGHT"):
            self.assertIn("direction == RetroDpadHitMap."+direction,CONTROLS)
        self.assertNotIn("float threshold = crossR * .22f",CONTROLS)
        self.assertNotIn("if (dx < -threshold)",CONTROLS)
        self.assertNotIn("if (dy < -threshold)",CONTROLS)
        self.assertIn("for (int i = 0; i < event.getPointerCount(); i++)",CONTROLS)
        self.assertIn("combined |= maskAt(event.getX(i), event.getY(i));",CONTROLS)
        self.assertIn("if (i != leaving)",CONTROLS)
        self.assertIn("&& Math.abs(y - crossY) <= crossStride + crossHalf) return 0;",CONTROLS)
        self.assertIn("Never fall through to the editor's UP group hit target.",CONTROLS)

    def test_cross_arms_are_separated_bounded_and_group_draggable(self):
        self.assertIn("crossStride = r * 1.22f",CONTROLS)
        self.assertIn("crossHalf = r * .57f",CONTROLS)
        self.assertIn("float edge = crossStride + crossHalf + 3f * density",CONTROLS)
        self.assertIn("target(RetroCore.Key.UP, crossX, crossY - crossStride, crossHalf)",CONTROLS)
        self.assertIn("target(RetroCore.Key.LEFT, crossX - crossStride, crossY, crossHalf)",CONTROLS)
        self.assertIn("canvas.drawCircle(crossX, crossY, crossR * .40f, paint)",CONTROLS)
        self.assertIn("(event.getX(i) + dragOffsetX)",CONTROLS)
        self.assertIn("(event.getY(i) + dragOffsetY)",CONTROLS)
        self.assertIn("profile.move(dragged,",CONTROLS)

    def test_no_press_on_dead_center_or_overlap_fallback(self):
        self.assertIn("if (!Float.isFinite(x)",MAPPER)
        self.assertIn("return NONE",MAPPER)
        self.assertIn("if (score < best)",MAPPER)
        self.assertNotIn("return UP | RIGHT",MAPPER)

    def test_old_user_layout_saved_rom_and_menus_untouched(self):
        profile=(RETRO/"RetroControlProfile.java").read_text(encoding="utf-8")
        game=(RETRO/"OkubiGameBoyActivity.java").read_text(encoding="utf-8")
        self.assertIn("okubi_retro_controls_v1",profile)
        self.assertIn('profile.save(this);',game)
        self.assertIn('RetroSystems.Controller.TWO_WITH_SHOULDERS',game)
        self.assertIn("else if (key == RetroCore.Key.L)",game)
        self.assertIn("else if (key == RetroCore.Key.R)",game)
        self.assertIn("controls.releaseAll();",game)

    def test_ci_and_integrator_compile_mapper(self):
        self.assertIn('"RetroDpadHitMap.java"',INTEGRATE)
        self.assertIn("RetroDpadHitMapTest.java",WORKFLOW)
        self.assertIn("RetroDpadHitMapTest",WORKFLOW)
        self.assertIn("Compilar APK único OKUBI UNIFIED",WORKFLOW)


if __name__=="__main__":
    unittest.main()
