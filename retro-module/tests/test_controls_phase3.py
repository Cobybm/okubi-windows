"""Post-audit Phase 3: controller, rotation and touch-safety contracts.

These tests catch accidental regressions in source wiring. Pure Java tests
exercise joystick math; Android Actions and physical-device QA are separate.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT / "java/bo/nexoplay/app/retro"
PLAYER=SOURCE / "OkubiGameBoyActivity.java"
PAD=SOURCE / "RetroTouchPad.java"
AXES=SOURCE / "RetroControllerAxes.java"
INTEGRATE=ROOT / "integrate.py"
CI=ROOT.parent / ".github/workflows/okubi-retro.yml"


class ControlPhase3Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.player=PLAYER.read_text(encoding="utf-8")
        cls.pad=PAD.read_text(encoding="utf-8")
        cls.axes=AXES.read_text(encoding="utf-8")

    def test_hardware_dpad_and_stick_share_decoder_not_touch_mask(self):
        p=self.player
        for key in ("MotionEvent.AXIS_HAT_X", "MotionEvent.AXIS_HAT_Y",
                    "MotionEvent.AXIS_X", "MotionEvent.AXIS_Y",
                    "RetroControllerAxes.mask(", "analogKeys.set(next)",
                    "KeyEvent.KEYCODE_BUTTON_L2", "KeyEvent.KEYCODE_BUTTON_R2"):
            self.assertIn(key,p)
        self.assertIn("keys.get() | padKeys.get() | analogKeys.get()",p)
        self.assertIn("private RetroCore.Key adjustedGamepadKey",p)
        self.assertIn("controls.getProfile().swapAB",p)

    def test_no_shortcut_auto_repeat(self):
        p=self.player
        self.assertIn("physicalShortcutMask & bit",p)
        self.assertIn("if (down) physicalShortcutMask |= bit",p)
        self.assertIn("else physicalShortcutMask &= ~bit",p)
        self.assertIn("RetroSlotRequests.Action.SAVE",p)
        self.assertIn("RetroSlotRequests.Action.LOAD",p)
        self.assertIn("requestSnapshot(key == RetroCore.Key.L",p)
        self.assertIn('if (!"GBA".equals(system) && !"SNES".equals(system))',p)
        self.assertIn('"GBA".equals(system) || "SNES".equals(system)',p)

    def test_disconnection_orientation_and_focus_release_keys(self):
        p=self.player
        self.assertIn("onInputDeviceRemoved(int id)",p)
        self.assertIn("onInputDeviceChanged(int id)",p)
        self.assertIn("manager.registerInputDeviceListener(controllerListener, null)",p)
        self.assertIn("manager.unregisterInputDeviceListener(controllerListener)",p)
        self.assertIn("onWindowFocusChanged(boolean focused)",p)
        self.assertIn("clearPhysicalController();",p)
        self.assertIn("keys.set(0);",p)
        self.assertIn("controls.releaseAll();",p)
        self.assertIn("if (editingControls && controls != null) controls.getProfile().save(this);",p)
        self.assertIn("buildCinemaLayout();",p)
        self.assertIn("new RetroCinemaStage(this,frameWidth,frameHeight,",p)
        self.assertIn("getWindow().getDecorView().setSystemUiVisibility(0);",p)

    def test_drag_state_and_screen_bounds(self):
        t=self.pad
        self.assertIn("Math.min(width * .18f, height * .21f)",t)
        self.assertIn("x = clamp(x, margin",t)
        self.assertIn("y = clamp(y, margin",t)
        self.assertIn("MotionEvent.ACTION_POINTER_UP",t)
        self.assertIn("event.getPointerId(event.getActionIndex()) == dragPointer",t)
        self.assertIn("if (editing && dragged != null) profile.save(getContext());",t)
        self.assertIn("profile.swapAB",t)
        self.assertIn("requestDisallowInterceptTouchEvent(true)",t)
        body=t.split("@Override public boolean onTouchEvent(MotionEvent event)",1)[1]
        body=body.split("@Override public boolean performClick()",1)[0]
        self.assertNotIn("new ",body,"Touch handling must not allocate per event")

    def test_axis_helper_and_java_gate(self):
        axes=self.axes
        self.assertIn("public static final float ENGAGE = 0.50f",axes)
        self.assertIn("public static final float RELEASE = 0.32f",axes)
        self.assertIn("Float.isNaN(value)",axes)
        self.assertIn("Float.isInfinite(value)",axes)
        self.assertIn("Math.abs(hx) >= HAT_ENGAGE",axes)
        self.assertIn("Math.abs(hy) >= HAT_ENGAGE",axes)
        self.assertIn('"RetroControllerAxes.java"',INTEGRATE.read_text(encoding="utf-8"))
        self.assertIn("RetroControllerAxesTest.java",CI.read_text(encoding="utf-8"))

    def test_shows_live_controller_help(self):
        p=self.player
        self.assertIn("Mando Bluetooth/USB · Ayuda",p)
        self.assertIn("InputDevice.getDeviceIds()",p)
        self.assertIn("showControllerHelp()",p)
        self.assertIn("beginDialog();",p)
        self.assertIn("dialog -> endDialog()",p)


if __name__=="__main__":
    unittest.main()
