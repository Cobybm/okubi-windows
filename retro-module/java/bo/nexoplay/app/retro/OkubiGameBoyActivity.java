package bo.nexoplay.app.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.input.InputManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * First playable GB/GBC session. SameBoy Libretro executes on one worker;
 * the main thread handles UI and atomic button state only.
 *
 * Battery SRAM is stored atomically in private application storage, keyed
 * by the SHA-256 of the player's own ROM. No game or BIOS is bundled.
 */
public final class OkubiGameBoyActivity extends Activity {
    public static final String EXTRA_ROM = "okubi.rom";
    public static final String EXTRA_NAME = "okubi.name";
    public static final String EXTRA_HOMEBREW_PATH = "okubi.homebrew.private";
    public static final String EXTRA_SYSTEM = "okubi.console";
    private static final int REQUEST_IMPORT_SAV = 4321;
    private static final int REQUEST_EXPORT_BACKUP = 4322;
    private static final int REQUEST_IMPORT_BACKUP = 4323;

    private static final int GB_WIDTH = 160;
    private static final int GB_HEIGHT = 144;
    private static final int GBA_WIDTH = 240;
    private static final int GBA_HEIGHT = 160;
    private static final int SNES_WIDTH = 256;
    private static final int SNES_HEIGHT = 224;
    private static final int MAX_ROM_BYTES = 8 * 1024 * 1024;
    private static final int BG = 0xFF0B0712;

    private final AtomicInteger keys = new AtomicInteger();
    private final AtomicInteger padKeys = new AtomicInteger();
    private final AtomicInteger analogKeys = new AtomicInteger();
    // UI-thread-only controller state. The native core only sees atomic masks.
    private int physicalShortcutMask;
    private int lastControllerDeviceId = -1;
    private boolean controllerListenerRegistered;
    private final InputManager.InputDeviceListener controllerListener =
            new InputManager.InputDeviceListener() {
                @Override public void onInputDeviceAdded(int id) { }
                @Override public void onInputDeviceRemoved(int id) {
                    if (id == lastControllerDeviceId) clearPhysicalController();
                }
                @Override public void onInputDeviceChanged(int id) {
                    if (id == lastControllerDeviceId) clearPhysicalController();
                }
            };

    private final Paint bitmapPaint = new Paint();
    private final Rect src = new Rect(0, 0, GB_WIDTH, GB_HEIGHT);
    private int frameWidth = GB_WIDTH;
    private int frameHeight = GB_HEIGHT;
    private final Rect dst = new Rect();

    private volatile boolean playing = true;
    private volatile boolean destroyed = false;
    private volatile boolean saveRequested = false;
    private volatile Uri pendingBatteryImport;
    private volatile Uri pendingBackupExport;
    private volatile Uri pendingBackupImport;
    private volatile boolean pendingRestorePrevious;
    private volatile String activeRomHash;
    // User intent is pinned at click time, not inferred from a later UI selection.
    private final RetroSlotRequests slotRequests = new RetroSlotRequests();
    /** Immutable request; only the emulation worker touches save files. */
    private volatile SlotCopyRequest pendingSlotCopy;
    private static final class SlotCopyRequest {
        final String romHash;
        final int from;
        final int to;
        SlotCopyRequest(String hash, int source, int target) {
            romHash=hash;
            from=source;
            to=target;
        }
    }
    // One per-ROM choice. Slot 1 is the EXACT legacy <SHA>.state file.
    private volatile int selectedSnapshotSlot = RetroSaveSlots.FIRST;
    // Worker-thread-only copy of last rendered frame, for OPTIONAL preview PNGs.
    private Bitmap previewSourceFrame;
    private boolean previewFrameReady;
    private volatile boolean audioResetRequested = false;
    private volatile int volumePercent = 35;
    private volatile boolean audioMuted;
    private volatile boolean safeAudio = true;
    private volatile RetroRenderPolicy.Mode performanceMode = RetroRenderPolicy.Mode.AUTOMATIC;
    // Presentation-only settings, independent from save games and controls.
    private volatile RetroVisualFilters.Mode visualMode = RetroVisualFilters.DEFAULT;
    private volatile RetroScreenFit.Mode screenFitMode = RetroScreenFit.DEFAULT;
    private float overlayOpacity = .45f;
    private final Rect backgroundSource = new Rect();
    private final Rect backgroundTarget = new Rect();
    private final Paint fullscreenBackdropPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private RetroCinemaStage activeCinemaStage;
    private Bitmap classicFrame;
    private int[] classicPixels;
    private final Paint lcdScanlinePaint = new Paint();
    // Session-only speed; always start at x1 after reopening the game.
    private volatile int speedStep = RetroSpeedControl.NORMAL;
    private TextView cinemaSpeedLabel;
    private boolean activityResumed;
    private boolean editingControls;
    private int openDialogs;
    private Button editDone;
    private volatile SurfaceHolder currentHolder;
    private Thread worker;
    private RetroTouchPad controls;
    private TextView message;
    // Rebuilding the screen on rotation must not show a false "starting" state.
    // Only the UI thread updates this text, including from worker notifications.
    private String lastStatus = "Inicializando el núcleo...";
    private Uri romUri;
    private File privateHomebrew;
    private String bundledAsset;
    private String title;
    private String system;
    // Emulation worker publishes these to the UI; volatile makes menu reads safe.
    private volatile File saveFile;
    private File rtcFile;
    private File snapshotFile;
    private volatile boolean coreIsOpen;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        romUri = getIntent().getParcelableExtra(EXTRA_ROM);
        String requestedAsset = getIntent().getStringExtra(OkubiPersonalLibrary.EXTRA_ASSET);
        bundledAsset = OkubiPersonalLibrary.exists(this, requestedAsset) ? requestedAsset : null;
        privateHomebrew = verifiedHomebrewPath(
                getIntent().getStringExtra(EXTRA_HOMEBREW_PATH));
        system = getIntent().getStringExtra(EXTRA_SYSTEM);
        if (!"GBA".equals(system) && !"GBC".equals(system)
                && !"SNES".equals(system)) system = "GB";
        if ("GBA".equals(system)) {
            frameWidth = GBA_WIDTH;
            frameHeight = GBA_HEIGHT;
        } else if ("SNES".equals(system)) {
            frameWidth = SNES_WIDTH;
            frameHeight = SNES_HEIGHT;
        }
        src.set(0, 0, frameWidth, frameHeight);
        title = getIntent().getStringExtra(EXTRA_NAME);
        if (title == null) title = "Juego retro";
        SharedPreferences audioPrefs = getSharedPreferences("okubi_audio_v1", MODE_PRIVATE);
        volumePercent = Math.max(0, Math.min(100, audioPrefs.getInt("volume", 35)));
        audioMuted = audioPrefs.getBoolean("muted", false);
        safeAudio = audioPrefs.getBoolean("safe", true);
        performanceMode = RetroRenderPolicy.parseMode(
                getSharedPreferences("okubi_performance_v1", MODE_PRIVATE)
                        .getString("render", "AUTOMATIC"));
        visualMode = RetroVisualFilters.parse(
                getSharedPreferences("okubi_visual_v1", MODE_PRIVATE)
                        .getString(system, RetroVisualFilters.DEFAULT.name()));
        screenFitMode = RetroScreenFit.parse(
                getSharedPreferences("okubi_screen_fit_v1", MODE_PRIVATE)
                        .getString(system, RetroScreenFit.DEFAULT.name()));
        overlayOpacity = RetroScreenFit.clampOverlayOpacity(
                getSharedPreferences("okubi_overlay_opacity_v1", MODE_PRIVATE)
                        .getFloat(system, .45f));
        if (((romUri == null || !"content".equals(romUri.getScheme()))
                && privateHomebrew == null && bundledAsset == null) ||
                !("SNES".equals(system) ? SameBoyNative.isSnesAvailable()
                        : "GBA".equals(system) ? SameBoyNative.isGbaAvailable()
                        : SameBoyNative.isAvailable())) {
            showUnavailable();
            return;
        }
        bitmapPaint.setFilterBitmap(false);
        bitmapPaint.setAntiAlias(false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildLayout();
        worker = new Thread(this::emulate, "okubi-libretro");
        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    private void showUnavailable() {
        TextView warning = new TextView(this);
        warning.setText("No se encontró el núcleo integrado de " + system
                + " o el archivo no está disponible.");
        warning.setTextColor(Color.WHITE);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(32, 32, 32, 32);
        warning.setBackgroundColor(BG);
        setContentView(warning);
    }

    private int dp(int pixels) {
        return (int)(pixels * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button button(String label, Runnable command) {
        Button result = new Button(this);
        result.setText(label);
        result.setAllCaps(false);
        result.setOnClickListener(v -> command.run());
        return result;
    }

    private void setKey(RetroCore.Key key, boolean pressed) {
        int bit = 1 << key.ordinal();
        int before, after;
        do {
            before = keys.get();
            after = pressed ? before | bit : before & ~bit;
        } while (!keys.compareAndSet(before, after));
    }

    private RetroCore.Key gamepadKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return RetroCore.Key.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return RetroCore.Key.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return RetroCore.Key.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return RetroCore.Key.RIGHT;
            case KeyEvent.KEYCODE_BUTTON_A: return RetroCore.Key.A;
            case KeyEvent.KEYCODE_BUTTON_B: return RetroCore.Key.B;
            case KeyEvent.KEYCODE_BUTTON_X:
                return "SNES".equals(system) ? RetroCore.Key.X : RetroCore.Key.B;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return "SNES".equals(system) ? RetroCore.Key.Y : RetroCore.Key.A;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2: return RetroCore.Key.L;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2: return RetroCore.Key.R;
            case KeyEvent.KEYCODE_BUTTON_START: return RetroCore.Key.START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return RetroCore.Key.SELECT;
            default: return null;
        }
    }

    private void setPadKey(RetroCore.Key key, boolean down) {
        int bit = 1 << key.ordinal();
        if (!"GBA".equals(system) && !"SNES".equals(system)) {
            if (key == RetroCore.Key.L || key == RetroCore.Key.R) {
                // Physical shoulder buttons trigger save/load ONLY on the
                // first key down. Android autorepeat must not spam snapshots.
                if (down && (physicalShortcutMask & bit) == 0) {
                    requestSnapshot(key == RetroCore.Key.L
                            ? RetroSlotRequests.Action.SAVE : RetroSlotRequests.Action.LOAD,
                            selectedSnapshotSlot);
                }
                if (down) physicalShortcutMask |= bit;
                else physicalShortcutMask &= ~bit;
                return;
            }
        }
        int current, next;
        do {
            current = padKeys.get();
            next = down ? (current | bit) : (current & ~bit);
        } while (!padKeys.compareAndSet(current, next));
    }

    /** Physical A/B follows the same optional preference as the touch pad. */
    private RetroCore.Key adjustedGamepadKey(int code) {
        RetroCore.Key mapped = gamepadKey(code);
        if (mapped != null && controls != null && controls.getProfile().swapAB) {
            if (mapped == RetroCore.Key.A) return RetroCore.Key.B;
            if (mapped == RetroCore.Key.B) return RetroCore.Key.A;
        }
        return mapped;
    }

    private void clearPhysicalController() {
        padKeys.set(0);
        analogKeys.set(0);
        physicalShortcutMask = 0;
        lastControllerDeviceId = -1;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        RetroCore.Key key = adjustedGamepadKey(keyCode);
        if (key != null) {
            if (event != null) lastControllerDeviceId = event.getDeviceId();
            setPadKey(key, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        RetroCore.Key key = adjustedGamepadKey(keyCode);
        if (key != null) {
            setPadKey(key, false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK)
                == InputDevice.SOURCE_JOYSTICK
                && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            lastControllerDeviceId = event.getDeviceId();
            int next = RetroControllerAxes.mask(
                    event.getAxisValue(MotionEvent.AXIS_X),
                    event.getAxisValue(MotionEvent.AXIS_Y),
                    event.getAxisValue(MotionEvent.AXIS_HAT_X),
                    event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                    analogKeys.get());
            analogKeys.set(next);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    /** Keep portrait ergonomics intact; cinema layout is LANDSCAPE ONLY. */
    private void buildLayout() {
        if (controls != null) controls.releaseAll();
        activeCinemaStage = null;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) {
            buildCinemaLayout();
            return;
        }
        // Restore system bars when returning to portrait.
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText("OKUBI · " + title);
        name.setTextColor(Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1f));
        editDone = button("✓ Listo", () -> stopEditingControls());
        editDone.setVisibility(editingControls ? View.VISIBLE : View.GONE);
        header.addView(editDone);
        header.addView(button("◈ Imagen", this::showVisualSettings));
        header.addView(button("☰ Menú", this::showQuickMenu));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        message = new TextView(this);
        message.setTextColor(0xFFEAC7DC);
        message.setTextSize(12);
        message.setText(lastStatus);
        root.addView(message, new LinearLayout.LayoutParams(-1, dp(25)));

        LinearLayout gameArea = new LinearLayout(this);
        gameArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(gameArea, new LinearLayout.LayoutParams(-1, 0, 1f));
        RetroAspectFrame frame = new RetroAspectFrame(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.setNativeSize(frameWidth, frameHeight);
        frame.setMaxHeightDp(1000);
        SurfaceView screen = new SurfaceView(this);
        frame.addView(screen);
        currentHolder = screen.getHolder();
        gameArea.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1.15f));
        controls = createRetroControls(false);
        gameArea.addView(controls, new LinearLayout.LayoutParams(-1, 0, .85f));
    }

    private RetroTouchPad createRetroControls(boolean cinema) {
        RetroTouchPad pad = new RetroTouchPad(this);
        pad.setController("SNES".equals(system)
                ? RetroSystems.Controller.FOUR_BUTTON
                : RetroSystems.Controller.TWO_WITH_SHOULDERS);
        pad.setCinemaLayout(cinema);
        pad.setProfile("SNES".equals(system)
                ? (cinema ? RetroControlProfile.loadCinemaSnes(this)
                          : RetroControlProfile.loadSnes(this))
                : (cinema ? RetroControlProfile.loadCinema(this)
                          : RetroControlProfile.load(this)));
        pad.setInputListener((key, down) -> {
            if ("GBA".equals(system) || "SNES".equals(system)) setKey(key, down);
            else if (key == RetroCore.Key.L) {
                if (down) requestSnapshot(RetroSlotRequests.Action.SAVE, selectedSnapshotSlot);
            } else if (key == RetroCore.Key.R) {
                if (down) requestSnapshot(RetroSlotRequests.Action.LOAD, selectedSnapshotSlot);
            } else setKey(key, down);
        });
        pad.setEditing(editingControls);
        return pad;
    }

    private GradientDrawable cinemaBackground(int color,int strokeColor,float radiusDp) {
        GradientDrawable bg=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color, 0xFF190E25, color});
        bg.setCornerRadius(dp((int)radiusDp));
        bg.setStroke(dp(1),strokeColor);
        return bg;
    }

    private TextView cinemaAction(String icon,String description,Runnable action) {
        TextView result=new TextView(this);
        result.setText(icon);
        result.setTextColor(Color.WHITE);
        result.setTextSize(17f);
        result.setGravity(Gravity.CENTER);
        result.setContentDescription(description);
        result.setClickable(true);
        result.setBackground(cinemaBackground(0xFF1B1026,0x88E73B91,12));
        result.setOnClickListener(v->action.run());
        return result;
    }

    /**
     * Approved OKUBI neon cinema design. Fullscreen gaming avoids the side
     * Android navigation rail, while Game Boy pixels retain exact aspect ratio.
     * Every toolbar icon executes an existing real command, not decoration.
     */
    private void buildCinemaLayout() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(6), dp(5), dp(6), dp(4));
        // Immersive fullscreen may place controls behind a camera notch on
        // some phones. Honor ONLY display-cutout safe insets; system bars are
        // deliberately hidden and should not waste game space.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                int left = dp(6), top = dp(5), right = dp(6), bottom = dp(4);
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
                if (view.getPaddingLeft() != left || view.getPaddingTop() != top
                        || view.getPaddingRight() != right
                        || view.getPaddingBottom() != bottom)
                    view.setPadding(left, top, right, bottom);
                return insets;
            });
        }
        setContentView(root);
        if (android.os.Build.VERSION.SDK_INT >= 28) root.requestApplyInsets();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(10),0,dp(8),0);
        header.setBackground(cinemaBackground(0xFF170E21,0xFFE42D93,20));
        Configuration configuration = getResources().getConfiguration();
        int widthDp = configuration.screenWidthDp;
        boolean compactHeader = RetroAdaptiveLayout.compactBrand(
                widthDp, configuration.fontScale);
        boolean hideStatus = RetroAdaptiveLayout.hideHeaderStatus(
                widthDp, configuration.fontScale);

        TextView brand = new TextView(this);
        brand.setText(compactHeader ? "遊" : "▣  OKUBI");
        brand.setTextColor(0xFFFF4AA9);
        brand.setTextSize(15);
        brand.setTypeface(null,android.graphics.Typeface.BOLD_ITALIC);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(brand,new LinearLayout.LayoutParams(-2,-1));

        TextView divider = new TextView(this);
        divider.setText("  │  ");
        divider.setTextColor(0x99FFFFFF);
        if (!compactHeader)
            header.addView(divider,new LinearLayout.LayoutParams(-2,-1));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMinWidth(dp(RetroAdaptiveLayout.minimumTitleDp(widthDp)));
        header.addView(name,new LinearLayout.LayoutParams(0,-1,.35f));

        // Small status line; rotation retains the real value (Phase 15).
        message = new TextView(this);
        message.setText(lastStatus);
        message.setTextColor(0xFFCFABC5);
        message.setTextSize(9);
        message.setSingleLine(true);
        message.setEllipsize(android.text.TextUtils.TruncateAt.END);
        message.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(message,new LinearLayout.LayoutParams(0,-1,.55f));
        if (hideStatus) message.setVisibility(View.GONE);

        editDone = button("✓ Listo", this::stopEditingControls);
        editDone.setVisibility(editingControls ? View.VISIBLE : View.GONE);
        header.addView(editDone,new LinearLayout.LayoutParams(-2,dp(34)));

        TextView snapshot = cinemaAction("▣","Guardar punto actual",
                ()->requestSnapshot(RetroSlotRequests.Action.SAVE,selectedSnapshotSlot));
        TextView slots = cinemaAction("▤","Tres ranuras de guardado",this::showSaveSlots);
        // Compact inline speed bar inspired by the player's handheld reference.
        TextView speedDown = cinemaAction("−","Ralentizar emulación",()->changeSpeed(-1));
        cinemaSpeedLabel = cinemaAction(RetroSpeedControl.label(speedStep),
                "Restaurar velocidad normal",()->changeSpeed(0));
        cinemaSpeedLabel.setTextSize(12f);
        cinemaSpeedLabel.setSingleLine(true);
        TextView speedUp = cinemaAction("+","Acelerar emulación",()->changeSpeed(1));
        TextView visuals = cinemaAction("◈","Filtros de imagen",this::showVisualSettings);
        TextView screenMode = cinemaAction("⛶","Tamaño de pantalla",this::showScreenFitSettings);
        screenMode.setOnLongClickListener(v -> {
            showOverlayOpacitySettings();
            return true;
        });
        TextView performance = cinemaAction("⚡","Rendimiento",this::showPerformanceMode);
        TextView menu = cinemaAction("☰","Menú rápido OKUBI",this::showQuickMenu);
        LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(dp(39),dp(33));
        small.setMargins(dp(3),0,0,0);
        LinearLayout speedBar = new LinearLayout(this);
        speedBar.setGravity(Gravity.CENTER_VERTICAL);
        speedBar.setOrientation(LinearLayout.HORIZONTAL);
        speedBar.setBackground(cinemaBackground(0xFF0F0D17,0xFFFF4AA9,12));
        speedBar.addView(speedDown,new LinearLayout.LayoutParams(dp(29),dp(31)));
        speedBar.addView(cinemaSpeedLabel,new LinearLayout.LayoutParams(dp(42),dp(31)));
        speedBar.addView(speedUp,new LinearLayout.LayoutParams(dp(29),dp(31)));
        LinearLayout.LayoutParams speedBarParams = new LinearLayout.LayoutParams(dp(100),dp(33));
        speedBarParams.setMargins(dp(2),0,dp(3),0);
        // Scrollable actions preserve access on narrow phones, split-screen
        // windows and enlarged system text; the ☰ menu remains pinned.
        LinearLayout toolbarActions = new LinearLayout(this);
        toolbarActions.setOrientation(LinearLayout.HORIZONTAL);
        toolbarActions.setGravity(Gravity.CENTER_VERTICAL);
        toolbarActions.addView(speedBar,speedBarParams);
        toolbarActions.addView(snapshot,small);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(dp(39),dp(33));
        p2.setMargins(dp(3),0,0,0);
        toolbarActions.addView(slots,p2);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(dp(39),dp(33));
        p3.setMargins(dp(3),0,0,0);
        toolbarActions.addView(performance,p3);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(36),dp(33));
        filterParams.setMargins(dp(3),0,0,0);
        toolbarActions.addView(visuals,filterParams);
        LinearLayout.LayoutParams viewportParams = new LinearLayout.LayoutParams(dp(36),dp(33));
        viewportParams.setMargins(dp(3),0,0,0);
        toolbarActions.addView(screenMode,viewportParams);
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(dp(39),dp(33));
        p4.setMargins(dp(3),0,0,0);
        android.widget.HorizontalScrollView scrollActions =
                new android.widget.HorizontalScrollView(this);
        scrollActions.setHorizontalScrollBarEnabled(false);
        scrollActions.setFillViewport(false);
        scrollActions.addView(toolbarActions,
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        header.addView(scrollActions,new LinearLayout.LayoutParams(0,dp(35),1f));
        header.addView(menu,p4);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(43)));

        RetroAspectFrame frame = new RetroAspectFrame(this);
        frame.setNativeSize(frameWidth,frameHeight);
        frame.setMaxHeightDp(1000);
        GradientDrawable black = new GradientDrawable();
        black.setColor(Color.BLACK);
        black.setCornerRadius(dp(16));
        frame.setBackground(black);
        SurfaceView screen = new SurfaceView(this);
        frame.addView(screen);
        currentHolder = screen.getHolder();

        controls = createRetroControls(true);
        RetroCinemaStage stage = new RetroCinemaStage(this,frameWidth,frameHeight,
                frame,controls);
        activeCinemaStage = stage;
        stage.setExpandedVideo(RetroScreenFit.fillsStage(screenFitMode));
        updateOverlayOpacity();
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1f));
    }

    private void changeSpeed(int direction) {
        int previous = speedStep;
        speedStep = direction < 0 ? RetroSpeedControl.slower(previous)
                : direction > 0 ? RetroSpeedControl.faster(previous)
                : RetroSpeedControl.NORMAL;
        if (cinemaSpeedLabel != null) cinemaSpeedLabel.setText(RetroSpeedControl.label(speedStep));
        if (previous != speedStep) setStatus("Velocidad " + RetroSpeedControl.label(speedStep)
                + (RetroSpeedControl.normal(speedStep) ? " · sonido normal" : " · sonido pausado"));
    }

    private void syncPlaying() {
        playing = activityResumed && !destroyed && openDialogs == 0 && !editingControls;
    }
    private void beginDialog() { openDialogs++; syncPlaying(); }
    private void endDialog() { openDialogs = Math.max(0, openDialogs - 1); syncPlaying(); }

    private void startEditingControls() {
        editingControls = true;
        if (controls != null) controls.setEditing(true);
        if (editDone != null) editDone.setVisibility(View.VISIBLE);
        syncPlaying();
        setStatus("Arrastra cada botón. La cruceta se mueve como un grupo.");
    }
    private void stopEditingControls() {
        editingControls = false;
        if (controls != null) {
            controls.setEditing(false);
            controls.getProfile().save(this);
        }
        if (editDone != null) editDone.setVisibility(View.GONE);
        syncPlaying();
        setStatus("Posiciones guardadas para esta orientación");
    }

    private void updateOverlayOpacity() {
        if (controls != null) controls.setAlpha(
                RetroScreenFit.fillsStage(screenFitMode) ? overlayOpacity : 1.0f);
    }

    private void showOverlayOpacitySettings() {
        beginDialog();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(22);
        layout.setPadding(margin, dp(12), margin, dp(8));
        TextView description = new TextView(this);
        description.setText("Transparencia del mando sobre el juego. Los botones siguen funcionando.");
        layout.addView(description);
        TextView percentage = new TextView(this);
        percentage.setText(Math.round(overlayOpacity * 100f) + "% visible");
        percentage.setPadding(0, dp(10), 0, 0);
        layout.addView(percentage);
        SeekBar slider = new SeekBar(this);
        slider.setMax(80);
        slider.setProgress(Math.round(overlayOpacity * 100f) - 20);
        layout.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean user) {
                overlayOpacity = RetroScreenFit.clampOverlayOpacity((20 + value) / 100f);
                percentage.setText(Math.round(overlayOpacity * 100f) + "% visible");
                updateOverlayOpacity();
            }
            @Override public void onStartTrackingTouch(SeekBar seek) { }
            @Override public void onStopTrackingTouch(SeekBar seek) { }
        });
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Transparencia del mando")
                .setView(layout)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    getSharedPreferences("okubi_overlay_opacity_v1", MODE_PRIVATE)
                            .edit().putFloat(system, overlayOpacity).apply();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    overlayOpacity = RetroScreenFit.clampOverlayOpacity(
                            getSharedPreferences("okubi_overlay_opacity_v1", MODE_PRIVATE)
                                    .getFloat(system, .45f));
                    updateOverlayOpacity();
                })
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void showScreenFitSettings() {
        beginDialog();
        RetroScreenFit.Mode[] choices = RetroScreenFit.Mode.values();
        String[] labels = new String[choices.length];
        int selected = 0;
        for (int i = 0; i < choices.length; i++) {
            labels[i] = choices[i].label;
            if (choices[i] == screenFitMode) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Tamaño de pantalla")
                .setSingleChoiceItems(labels, selected, (dialog, choice) -> {
                    if (choice < 0 || choice >= choices.length) return;
                    screenFitMode = choices[choice];
                    getSharedPreferences("okubi_screen_fit_v1", MODE_PRIVATE).edit()
                            .putString(system, screenFitMode.name()).apply();
                    if (activeCinemaStage != null)
                        activeCinemaStage.setExpandedVideo(
                                RetroScreenFit.fillsStage(screenFitMode));
                    updateOverlayOpacity();
                    setStatus("Pantalla: " + screenFitMode.label);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void showVisualSettings() {
        beginDialog();
        RetroVisualFilters.Mode[] modes = RetroVisualFilters.Mode.values();
        String[] titles = new String[modes.length];
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            titles[i] = modes[i].label;
            if (modes[i] == visualMode) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Imagen / Upscaling")
                .setSingleChoiceItems(titles, checked, (dialog, selected) -> {
                    if (selected < 0 || selected >= modes.length) return;
                    visualMode = modes[selected];
                    getSharedPreferences("okubi_visual_v1", MODE_PRIVATE).edit()
                            .putString(system, visualMode.name()).apply();
                    setStatus("Imagen: " + visualMode.label);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void showQuickMenu() {
        beginDialog();
        String[] choices = {
            "Guardar batería del cartucho",
            "Guardar punto · ranura actual",
            "Cargar punto · ranura actual",
            "Puntos de guardado · 3 ranuras",
            "Audio y volumen",
            "Estilos y tamaño de los botones",
            "Mover botones en pantalla",
            "Reiniciar sonido",
            "Importar partida .sav / .srm",
            "Exportar respaldo .okubisave",
            "Restaurar respaldo .okubisave",
            "Recuperar partida anterior",
            "Mando Bluetooth/USB · Ayuda",
            "Rendimiento y ahorro de batería",
            "Duplicar punto · solo a ranura vacía",
            "Salir del juego",
            "MANDO PRO · perfiles rápidos",
            "Imagen · filtros y reescalado",
            "Pantalla · ampliar, completa o estirar",
            "Transparencia del mando · 20–100%"
        };
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Menú rápido")
                .setItems(choices, (dialog, index) -> {
                    switch (index) {
                        case 0: saveRequested = true; setStatus("Guardando cartucho..."); break;
                        case 1: requestSnapshot(RetroSlotRequests.Action.SAVE,selectedSnapshotSlot); break;
                        case 2: requestSnapshot(RetroSlotRequests.Action.LOAD,selectedSnapshotSlot); break;
                        case 3: showSaveSlots(); break;
                        case 4: showAudioSettings(); break;
                        case 5: showControlSettings(); break;
                        case 6: startEditingControls(); break;
                        case 7: audioResetRequested = true; setStatus("Reiniciando sonido..."); break;
                        case 8: chooseBatterySave(); break;
                        case 9: chooseBackupExport(); break;
                        case 10: confirmBackupRestore(); break;
                        case 11: confirmRestorePrevious(); break;
                        case 12: showControllerHelp(); break;
                        case 13: showPerformanceMode(); break;
                        case 14: chooseCopySource(); break;
                        case 15: finish(); break;
                        case 16: showProPresets(); break;
                        case 17: showVisualSettings(); break;
                        case 18: showScreenFitSettings(); break;
                        case 19: showOverlayOpacitySettings(); break;
                        default: break;
                    }
                })
                .setNegativeButton("Volver al juego", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private File snapshotForSlot(int slot) {
        if(activeRomHash == null || saveFile == null || !RetroSaveSlots.validSlot(slot))
            return null;
        return new File(saveFile.getParentFile(),
                RetroSaveSlots.filename(activeRomHash,slot));
    }

    /** Persist only the slot number for this ROM; no ROM path or URI is stored. */
    private void selectSaveSlot(int slot) {
        if(activeRomHash == null || !RetroSaveSlots.validSlot(slot))return;
        selectedSnapshotSlot=slot;
        getSharedPreferences("okubi_retro_slots_v1",MODE_PRIVATE).edit()
                .putInt("slot_"+activeRomHash,slot).apply();
    }

    /**
     * Keep every save command anchored to the actual ROM, slot and file
     * version seen when the user requested it. ALL entry points use this,
     * including the menu, the header and Game Boy L shoulder shortcut.
     */
    private void requestSnapshot(RetroSlotRequests.Action action,int chosenSlot) {
        String hash=activeRomHash;
        if(!coreIsOpen || !RetroSaveSlots.validHash(hash)
                || !RetroSaveSlots.validSlot(chosenSlot)) {
            setStatus("Espera a que el cartucho termine de cargar");
            return;
        }
        if(action == RetroSlotRequests.Action.SAVE) {
            File destination=snapshotForSlot(chosenSlot);
            RetroSlotRequests.FileStamp stamp=
                    RetroSlotRequests.FileStamp.capture(destination);
            if(stamp == null) {
                setStatus("Ranura protegida: archivo inválido o recuperación pendiente. "
                        +"Exporta un respaldo antes de continuar.");
                return;
            }
            if(stamp.exists) {
                // Never silently overwrite an occupied state, even with L
                // on a physical controller or the quick-save header action.
                beginDialog();
                new AlertDialog.Builder(this)
                        .setTitle("Sobrescribir "+RetroSaveSlots.label(chosenSlot)+"?")
                        .setMessage("Esta ranura ya contiene una partida. "
                                +"Guardar reemplazará SOLO esta ranura.\n\n"
                                +"Comprueba que sea la correcta. El cambio no se "
                                +"puede deshacer sin un respaldo externo.")
                        .setPositiveButton("Sobrescribir ranura",(dialog,which)->
                                enqueueGuardedSave(hash,chosenSlot,stamp))
                        .setNegativeButton("Conservar partida",null)
                        .setOnDismissListener(dialog->endDialog())
                        .show();
            } else {
                enqueueGuardedSave(hash,chosenSlot,stamp);
            }
            return;
        }
        if(action != RetroSlotRequests.Action.LOAD
                || !slotRequests.offer(action,hash,chosenSlot)) {
            setStatus("No se pudo solicitar la carga. Reintenta después.");
            return;
        }
        setStatus("Cargando "+RetroSaveSlots.label(chosenSlot)+"...");
    }

    private void enqueueGuardedSave(String hash,int slot,
                                    RetroSlotRequests.FileStamp expected) {
        File destination=snapshotForSlot(slot);
        if(!hash.equals(activeRomHash) || expected == null
                || !expected.matches(destination)) {
            setStatus("La ranura cambió o está protegida. No se sobrescribió nada.");
            return;
        }
        if(!slotRequests.offerGuardedSave(hash,slot,expected)) {
            setStatus("Cola de puntos ocupada. La partida anterior sigue intacta.");
            return;
        }
        setStatus("Guardando "+RetroSaveSlots.label(slot)+"...");
    }

    private File previewForSlot(int slot) {
        if(activeRomHash == null || saveFile == null
                || !RetroSaveSlots.validHash(activeRomHash)
                || !RetroSaveSlots.validSlot(slot)) return null;
        return new File(saveFile.getParentFile(),
                RetroSlotPreviews.filename(activeRomHash,slot));
    }

    /** Small private images, never loaded from internet or arbitrary URIs. */
    private Bitmap readSlotPreview(int slot) {
        File preview=previewForSlot(slot);
        if(preview==null || !preview.isFile()
                || !RetroSlotPreviews.validImageSize(preview.length())) return null;
        try {
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(preview.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0
                    || bounds.outWidth>RetroSlotPreviews.MAX_IMAGE_EDGE
                    || bounds.outHeight>RetroSlotPreviews.MAX_IMAGE_EDGE)
                return null;
            BitmapFactory.Options decoded=new BitmapFactory.Options();
            decoded.inPreferredConfig=Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(preview.getAbsolutePath(),decoded);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String slotDetails(int slot) {
        File file=snapshotForSlot(slot);
        if(file==null) return "Ranura no disponible";
        if(new File(file.getPath()+".bak").exists())
            return "⚠ Recuperación de guardado pendiente; no sobrescribir";
        boolean exists=file.exists();
        String result=RetroSlotPreviews.details(exists,
                exists ? file.length() : 0L);
        if(exists && file.isFile() && RetroSaveSlots.validStateSize(file.length())) {
            java.util.Date date=new java.util.Date(file.lastModified());
            result+="\nÚltimo guardado: "+android.text.format.DateFormat
                    .getMediumDateFormat(this).format(date)+" · "
                    +android.text.format.DateFormat.getTimeFormat(this).format(date);
        }
        return result;
    }

    private void updateSlotCard(ImageView image,TextView details,int slot) {
        details.setText(RetroSaveSlots.label(slot)+"\n"+slotDetails(slot));
        File state=snapshotForSlot(slot);
        if(state==null || !state.isFile()
                || !RetroSaveSlots.validStateSize(state.length())) {
            image.setImageDrawable(null);
            image.setVisibility(View.GONE);
            return;
        }
        Bitmap bitmap=readSlotPreview(slot);
        image.setImageBitmap(bitmap);
        image.setVisibility(bitmap==null?View.GONE:View.VISIBLE);
        details.append(bitmap==null
                ? "\nVista previa no disponible (guardado antiguo o sin imagen)"
                : "\nÚltima imagen visible al guardar");
    }

    private void showSaveSlots() {
        if(!coreIsOpen || activeRomHash == null) {
            setStatus("Primero termina de cargar el cartucho");
            return;
        }
        String[] labels=new String[RetroSaveSlots.LAST];
        for(int slot=RetroSaveSlots.FIRST;slot<=RetroSaveSlots.LAST;slot++){
            File file=snapshotForSlot(slot);
            boolean present=file!=null&&file.isFile()
                    &&RetroSaveSlots.validStateSize(file.length());
            boolean blocked=file!=null&&new File(file.getPath()+".bak").exists();
            labels[slot-1]=RetroSaveSlots.label(slot)
                    +(blocked?" ⚠ recuperación pendiente"
                    :present?" ✓ guardada" : file!=null && file.exists()
                    ?" ⚠ inválida":" ○ vacía")
                    +(slot==1?" · compatible con guardado anterior":"");
        }

        LinearLayout previewCard=new LinearLayout(this);
        previewCard.setPadding(dp(14),dp(6),dp(14),dp(8));
        previewCard.setOrientation(LinearLayout.VERTICAL);
        TextView details=dialogLabel("");
        previewCard.addView(details);
        ImageView preview=new ImageView(this);
        preview.setBackgroundColor(0xFF100A18);
        preview.setContentDescription("Vista previa de la partida seleccionada");
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewCard.addView(preview,new LinearLayout.LayoutParams(-1,dp(140)));

        final int[] chosen={RetroSaveSlots.normalized(selectedSnapshotSlot)};
        beginDialog();
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("OKUBI · Partidas inteligentes")
                .setMessage("Selecciona la ranura. Guardar una ranura ocupada "
                        +"siempre pedirá confirmación. Los demás puntos no cambian.")
                .setSingleChoiceItems(labels,chosen[0]-1,
                        (d,which)->{
                            chosen[0]=which+1;
                            updateSlotCard(preview,details,chosen[0]);
                        })
                .setView(previewCard)
                .setPositiveButton("Guardar",(d,which)->{
                    selectSaveSlot(chosen[0]);
                    requestSnapshot(RetroSlotRequests.Action.SAVE,chosen[0]);
                })
                .setNeutralButton("Cargar",(d,which)->confirmLoadSlot(chosen[0]))
                .setNegativeButton("Cancelar",null)
                .setOnDismissListener(d->endDialog())
                .create();
        dialog.show();
        updateSlotCard(preview,details,chosen[0]);
    }

    private void confirmLoadSlot(int slot) {
        File file=snapshotForSlot(slot);
        if(file==null || !file.isFile()
                || !RetroSaveSlots.validStateSize(file.length())
                || new File(file.getPath()+".bak").exists()){
            setStatus(RetroSaveSlots.label(slot)+" vacía, dañada o en recuperación");
            return;
        }
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Cargar "+RetroSaveSlots.label(slot))
                .setMessage("Volverás al momento guardado. El avance actual sin guardar "
                        +"se perderá. Los demás puntos no se modificarán.")
                .setPositiveButton("Cargar punto",(dialog,which)->{
                    selectSaveSlot(slot);
                    requestSnapshot(RetroSlotRequests.Action.LOAD,slot);
                })
                .setNegativeButton("Cancelar",null)
                .setOnDismissListener(dialog->endDialog())
                .show();
    }


    /** Copying a checkpoint never loads it into the native core. */
    private void chooseCopySource() {
        if(!coreIsOpen || activeRomHash==null) {
            setStatus("Primero termina de cargar el cartucho");
            return;
        }
        String[] labels=new String[RetroSaveSlots.LAST];
        for(int slot=RetroSaveSlots.FIRST;slot<=RetroSaveSlots.LAST;slot++){
            File file=snapshotForSlot(slot);
            boolean present=file!=null && file.isFile()
                    && RetroSaveSlots.validStateSize(file.length());
            labels[slot-1]=RetroSaveSlots.label(slot)+(present ? " · disponible" : " · vacía o dañada");
        }
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Duplicar punto · elegir origen")
                .setItems(labels,(dialog,which)->{
                    int source=which+1;
                    File file=snapshotForSlot(source);
                    if(file==null || !file.isFile()
                            || !RetroSaveSlots.validStateSize(file.length())) {
                        setStatus("Elige una ranura guardada y válida");
                        return;
                    }
                    chooseCopyDestination(source);
                })
                .setNegativeButton("Cancelar",null)
                .setOnDismissListener(dialog->endDialog())
                .show();
    }

    private void chooseCopyDestination(int source) {
        String[] labels=new String[RetroSaveSlots.LAST];
        for(int slot=RetroSaveSlots.FIRST;slot<=RetroSaveSlots.LAST;slot++){
            File file=snapshotForSlot(slot);
            labels[slot-1]=RetroSaveSlots.label(slot)+(slot==source
                    ? " · origen" : file!=null && file.exists()
                    ? " · ocupada (protegida)" : " · vacía");
        }
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Duplicar punto · elegir destino vacío")
                .setMessage("Las ranuras ocupadas nunca se sobrescriben.")
                .setItems(labels,(dialog,which)->{
                    int target=which+1;
                    File origin=snapshotForSlot(source);
                    File destination=snapshotForSlot(target);
                    if(origin==null || !origin.isFile() || destination==null
                            || !RetroSaveSlots.canCopy(source,target,
                                    origin.length(),destination.exists())) {
                        setStatus("Destino ocupado, origen inválido o misma ranura");
                        return;
                    }
                    confirmCopySlot(source,target);
                })
                .setNegativeButton("Cancelar",null)
                .setOnDismissListener(dialog->endDialog())
                .show();
    }

    private void confirmCopySlot(int source,int target) {
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Copiar "+RetroSaveSlots.label(source)+" a "+RetroSaveSlots.label(target))
                .setMessage("Se copiará el punto existente a una ranura vacía. " +
                        "No se cargará el juego ni cambiará el guardado actual.")
                .setPositiveButton("Copiar sin sobrescribir",(dialog,which)->{
                    if(pendingSlotCopy!=null) {
                        setStatus("Ya hay una copia en curso");
                        return;
                    }
                    pendingSlotCopy=new SlotCopyRequest(activeRomHash,source,target);
                    setStatus("Verificando y copiando punto...");
                })
                .setNegativeButton("Cancelar",null)
                .setOnDismissListener(dialog->endDialog())
                .show();
    }

    private void showPerformanceMode() {
        beginDialog();
        RetroRenderPolicy.Mode[] modes={
                RetroRenderPolicy.Mode.AUTOMATIC,
                RetroRenderPolicy.Mode.QUALITY,
                RetroRenderPolicy.Mode.BATTERY
        };
        String[] labels={
                "Automático · 60/30 imágenes según estabilidad",
                "Calidad · cada fotograma del núcleo",
                "Ahorro · mostrar una de cada dos imágenes"
        };
        int chosen=0;
        for (int i=0;i<modes.length;i++)
            if (performanceMode==modes[i]) chosen=i;
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Rendimiento")
                .setSingleChoiceItems(labels, chosen, (dialog,index)->{
                    RetroRenderPolicy.Mode choice=modes[index];
                    performanceMode=choice;
                    getSharedPreferences("okubi_performance_v1", MODE_PRIVATE).edit()
                            .putString("render",choice.name()).apply();
                    setStatus("Modo rendimiento: "+labels[index]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar",null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void showControllerHelp() {
        beginDialog();
        int count=0;
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device=InputDevice.getDevice(deviceId);
            if (device == null) continue;
            int src=device.getSources();
            if ((src & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD
                    || (src & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK)
                count++;
        }
        String status=count==0 ? "No hay un mando compatible detectado."
                : count+" mando(s) detectado(s).";
        String shoulder=("GBA".equals(system) || "SNES".equals(system))
                ? "L1/L2 y R1/R2 funcionan como hombros L y R. "
                    +"En SNES X/Y también son botones de juego."
                : "En GB/GBC, L1/L2 guarda punto y R1/R2 carga punto.";
        new AlertDialog.Builder(this)
            .setTitle("OKUBI · Mando físico")
            .setMessage(status+"\n\n"
                    +"Compatible con mandos Bluetooth/USB que Android reconozca. "
                    +"D-pad, stick izquierdo y cruceta HAT controlan movimiento; "
                    +"A/B son acciones, START y SELECT funcionan desde el mando.\n\n"
                    +shoulder+"\n\n"
                    +"Si un mando se desconecta o la app pierde el foco, "
                    +"OKUBI libera las teclas retenidas automáticamente. "
                    +"Los controles táctiles siguen disponibles.")
            .setPositiveButton("Entendido",null)
            .setOnDismissListener(dialog -> endDialog())
            .show();
    }

    private void chooseBatterySave() {
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Importar .sav / .srm")
                .setMessage("La memoria del cartucho cambiará. OKUBI guardará una copia "
                        + "privada del progreso anterior antes de importar. "
                        + "Exporta un respaldo externo si quieres conservarlo fuera de la app.")
                .setPositiveButton("Elegir partida", (dialog, which) -> {
                    Intent file = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    file.addCategory(Intent.CATEGORY_OPENABLE);
                    file.setType("*/*");
                    file.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(file, REQUEST_IMPORT_SAV); }
                    catch (Exception error) { setStatus("No se pudo abrir el selector de partidas"); }
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void chooseBackupExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        String cleanName = title.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (cleanName.length() > 35) cleanName = cleanName.substring(0, 35);
        intent.putExtra(Intent.EXTRA_TITLE, "OKUBI_" + system + "_"
                + cleanName + OkubiSaveArchive.EXTENSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_EXPORT_BACKUP); }
        catch (Exception error) { setStatus("No se pudo abrir el selector de respaldo"); }
    }

    private void confirmBackupRestore() {
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Restaurar respaldo")
                .setMessage("Se sustituirá el progreso de este juego por el respaldo elegido. "
                        + "OKUBI guardará una copia privada anterior antes de cambiar nada. "
                        + "Recomendamos exportar también un respaldo externo.")
                .setPositiveButton("Elegir respaldo", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(intent, REQUEST_IMPORT_BACKUP); }
                    catch (Exception error) { setStatus("No se pudo abrir el selector de respaldo"); }
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void confirmRestorePrevious() {
        if (activeRomHash == null || saveFile == null) {
            setStatus("Todavía se está cargando el juego");
            return;
        }
        File recovery = previousBackupFile();
        if (recovery == null || !recovery.isFile()) {
            setStatus("Este juego todavía no tiene una copia anterior");
            return;
        }
        beginDialog();
        new AlertDialog.Builder(this)
                .setTitle("Recuperar partida anterior")
                .setMessage("Se restaurará la copia privada creada antes de tu última importación. "
                        + "Si quieres conservar el estado actual, expórtalo primero.")
                .setPositiveButton("Restaurar anterior", (dialog, which) -> {
                    pendingRestorePrevious = true;
                    setStatus("Restaurando copia anterior...");
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri selected = data.getData();
        if (request == REQUEST_EXPORT_BACKUP) {
            pendingBackupExport = selected;
            setStatus("Creando respaldo del juego...");
            return;
        }
        if (request == REQUEST_IMPORT_BACKUP) {
            String filename = OkubiRomFormats.documentName(getContentResolver(), selected)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!filename.endsWith(OkubiSaveArchive.EXTENSION)) {
                setStatus("Selecciona un respaldo .okubisave");
                return;
            }
            pendingBackupImport = selected;
            setStatus("Comprobando respaldo antes de restaurar...");
            return;
        }
        if (request != REQUEST_IMPORT_SAV) return;
        String file = OkubiRomFormats.documentName(getContentResolver(), selected)
                .toLowerCase(java.util.Locale.ROOT);
        if (!file.endsWith(".sav") && !file.endsWith(".srm")) {
            setStatus("Selecciona una partida .sav o .srm");
            return;
        }
        pendingBatteryImport = selected;
        setStatus("Importando partida del cartucho...");
    }

    private static byte[] readBattery(InputStream input) throws java.io.IOException {
        if (input == null) throw new java.io.IOException("Archivo no disponible");
        try (InputStream source = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (count > 1024 * 1024 - out.size())
                    throw new java.io.IOException("El guardado supera 1 MB");
                out.write(buffer, 0, count);
            }
            if (out.size() == 0) throw new java.io.IOException("Guardado vacío");
            return out.toByteArray();
        }
    }

    private TextView dialogLabel(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(14);
        label.setPadding(dp(12), dp(8), dp(12), dp(5));
        return label;
    }

    private void showAudioSettings() {
        beginDialog();
        LinearLayout container = new LinearLayout(this);
        container.setPadding(dp(10), dp(4), dp(10), dp(6));
        container.setOrientation(LinearLayout.VERTICAL);
        TextView level = dialogLabel("Volumen del emulador: " + volumePercent + "%");
        container.addView(level);
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(volumePercent);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                level.setText("Volumen del emulador: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        container.addView(volume);
        CheckBox muted = new CheckBox(this);
        muted.setText("Silenciar audio");
        muted.setChecked(audioMuted);
        container.addView(muted);
        CheckBox safe = new CheckBox(this);
        safe.setText("Modo audio seguro (búfer estable)");
        safe.setChecked(safeAudio);
        container.addView(safe);
        container.addView(dialogLabel("Si escuchas un pitido, prueba primero Silenciar y después Reiniciar audio. No subas el volumen hasta comprobarlo."));
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Sonido")
                .setView(container)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    boolean changed = safeAudio != safe.isChecked();
                    volumePercent = volume.getProgress();
                    audioMuted = muted.isChecked();
                    safeAudio = safe.isChecked();
                    getSharedPreferences("okubi_audio_v1", MODE_PRIVATE).edit()
                            .putInt("volume", volumePercent)
                            .putBoolean("muted", audioMuted)
                            .putBoolean("safe", safeAudio)
                            .apply();
                    if (changed) audioResetRequested = true;
                    setStatus(audioMuted ? "Sonido silenciado" :
                            "Sonido al " + volumePercent + "%");
                })
                .setNeutralButton("Reiniciar sonido", (dialog, which) -> {
                    audioResetRequested = true;
                    setStatus("Reiniciando AudioTrack...");
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    /**
     * Presets change only scale/opacity, NEVER user positions, handedness,
     * A/B mapping, shoulder visibility, or any emulator save file.
     */
    private void showProPresets() {
        if (controls == null) return;
        beginDialog();
        final RetroControlPresets.Mode[] options = RetroControlPresets.options();
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].label + " · "
                    + Math.round(options[i].size * 100) + "% tamaño / "
                    + Math.round(options[i].opacity * 100) + "% transparencia";
        }
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · MANDO PRO")
                .setMessage("Elige la comodidad de tus pulgares. Conservaremos "
                        + "todas las posiciones personalizadas de esta orientación.")
                .setItems(labels, (dialog, which) -> {
                    RetroControlProfile active = controls.getProfile();
                    active.applyPreset(options[which]);
                    active.save(this);
                    controls.setProfile(active);
                    setStatus("MANDO PRO · " + options[which].label
                            + " · posiciones conservadas");
                })
                .setNeutralButton("Ajustes avanzados",
                        (dialog, which) -> showControlSettings())
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void showControlSettings() {
        beginDialog();
        RetroControlProfile profile = controls.getProfile();
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(10), dp(4), dp(10), dp(6));
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(dialogLabel("Diseño táctil · " +
                (getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE ? "Horizontal" : "Vertical")));
        Spinner style = new Spinner(this);
        RetroControlProfile.Skin[] skins = RetroControlProfile.Skin.values();
        String[] labels = new String[skins.length];
        for (int i = 0; i < skins.length; i++) labels[i] = skins[i].label;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        style.setAdapter(adapter);
        style.setSelection(profile.skin.ordinal());
        form.addView(style);
        TextView sizeLabel = dialogLabel("Tamaño: " + Math.round(profile.size * 100) + "%");
        form.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(78);
        size.setProgress(Math.round(profile.size * 100) - 72);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                sizeLabel.setText("Tamaño: " + (value + 72) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        form.addView(size);
        TextView opacityLabel = dialogLabel("Opacidad: " + Math.round(profile.opacity * 100) + "%");
        form.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(75);
        opacity.setProgress(Math.round(profile.opacity * 100) - 25);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                opacityLabel.setText("Opacidad: " + (value + 25) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        form.addView(opacity);
        CheckBox left = new CheckBox(this);
        left.setText("Modo zurdo");
        left.setChecked(profile.leftHanded);
        form.addView(left);
        CheckBox inverted = new CheckBox(this);
        inverted.setText("Invertir botones A/B");
        inverted.setChecked(profile.swapAB);
        form.addView(inverted);
        CheckBox shoulder = new CheckBox(this);
        shoulder.setText("Mostrar L/R (guardado/carga rápida en Game Boy)");
        shoulder.setChecked(profile.showShoulders);
        form.addView(shoulder);
        CheckBox vibration = new CheckBox(this);
        vibration.setText("Vibración breve al pulsar (respeta los ajustes de Android)");
        vibration.setChecked(profile.hapticEnabled);
        form.addView(vibration);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(form);
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Personalizar mando")
                .setView(scroll)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    profile.skin = skins[style.getSelectedItemPosition()];
                    profile.size = (size.getProgress() + 72) / 100f;
                    profile.opacity = (opacity.getProgress() + 25) / 100f;
                    profile.leftHanded = left.isChecked();
                    profile.swapAB = inverted.isChecked();
                    profile.showShoulders = shoulder.isChecked();
                    profile.hapticEnabled = vibration.isChecked();
                    profile.save(this);
                    clearPhysicalController();
                    controls.setProfile(profile);
                    setStatus("Diseño " + profile.skin.label + " aplicado");
                })
                .setNeutralButton("Restablecer", (dialog, which) -> {
                    profile.reset();
                    profile.save(this);
                    clearPhysicalController();
                    controls.setProfile(profile);
                    setStatus("Controles restablecidos");
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(dialog -> endDialog())
                .show();
    }

    private void setStatus(String value) {
        runOnUiThread(() -> {
            if (destroyed) return;
            lastStatus = value;
            if (message != null) message.setText(lastStatus);
        });
    }

    private File verifiedHomebrewPath(String value) {
        if (value == null) return null;
        try {
            File approved = new File(getFilesDir(), "okubi/homebrew").getCanonicalFile();
            File requested = new File(value).getCanonicalFile();
            if (!requested.getPath().startsWith(approved.getPath() + File.separator)
                    || !requested.getName().endsWith(".gb") || !requested.isFile()
                    || requested.length() < 32768 || requested.length() > MAX_ROM_BYTES)
                return null;
            return requested;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readRom(InputStream input) throws Exception {
        try (InputStream source = input) {
            return RetroRomReader.readBounded(source, MAX_ROM_BYTES, 32768);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder name = new StringBuilder(64);
        for (byte b : hash) {
            name.append(Character.forDigit((b >> 4) & 15, 16));
            name.append(Character.forDigit(b & 15, 16));
        }
        return name.toString();
    }

    private void drawFrame(Bitmap image, int[] pixels) {
        SurfaceHolder holder = currentHolder;
        if (holder == null) return;
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) return;
        Canvas canvas = null;
        try {
            image.setPixels(pixels, 0, frameWidth, 0, 0, frameWidth, frameHeight);
            canvas = holder.lockCanvas();
            if (canvas == null) return;
            canvas.drawColor(Color.BLACK);
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            int drawWidth = Math.min(width, Math.round(height * (frameWidth / (float)frameHeight)));
            int drawHeight = Math.min(height, Math.round(drawWidth * (frameHeight / (float)frameWidth)));
            RetroVisualFilters.Mode mode = visualMode;
            if (mode == RetroVisualFilters.Mode.PIXEL_PERFECT) {
                int factor = RetroVisualFilters.integerScale(width, height, frameWidth, frameHeight);
                if (factor > 0) {
                    drawWidth = frameWidth * factor;
                    drawHeight = frameHeight * factor;
                }
            }
            int x = (width - drawWidth) / 2;
            int y = (height - drawHeight) / 2;
            dst.set(x, y, x + drawWidth, y + drawHeight);
            src.set(0, 0, frameWidth, frameHeight);
            if (RetroScreenFit.fillsStage(screenFitMode)
                    && getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE) {
                RetroScreenFit.Rects fitted = RetroScreenFit.layout(
                        width, height, frameWidth, frameHeight,
                        screenFitMode,
                        mode == RetroVisualFilters.Mode.PIXEL_PERFECT);
                src.set(fitted.source[0], fitted.source[1],
                        fitted.source[2], fitted.source[3]);
                dst.set(fitted.destination[0], fitted.destination[1],
                        fitted.destination[2], fitted.destination[3]);
                x = dst.left;
                y = dst.top;
                drawWidth = dst.width();
                drawHeight = dst.height();
            }
            bitmapPaint.setFilterBitmap(RetroVisualFilters.smooth(mode));
            Bitmap presented = image;
            if (RetroVisualFilters.classic(mode)) {
                if (classicPixels == null || classicPixels.length != pixels.length) {
                    classicPixels = new int[pixels.length];
                    classicFrame = Bitmap.createBitmap(frameWidth, frameHeight,
                            Bitmap.Config.ARGB_8888);
                }
                RetroVisualFilters.classicPalette(pixels, classicPixels);
                classicFrame.setPixels(classicPixels, 0, frameWidth, 0, 0,
                        frameWidth, frameHeight);
                presented = classicFrame;
            }
            if (screenFitMode == RetroScreenFit.Mode.COVER
                    && getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE) {
                // Edge-to-edge dim backdrop while the full, aspect-correct
                // gameplay frame sits above it. Dialogue is never cropped.
                backgroundSource.set(0, 0, frameWidth, frameHeight);
                backgroundTarget.set(0, 0, width, height);
                fullscreenBackdropPaint.setAlpha(75);
                canvas.drawBitmap(presented, backgroundSource,
                        backgroundTarget, fullscreenBackdropPaint);
                // Black veil keeps controls and game foreground readable.
                canvas.drawColor(0x93000000);
            }
            canvas.drawBitmap(presented, src, dst, bitmapPaint);
            if (RetroVisualFilters.lcd(mode) && drawHeight >= frameHeight * 2) {
                lcdScanlinePaint.setColor(0x30000000);
                int step = Math.max(2, frameHeight / 65);
                for (int row = step - 1; row < frameHeight; row += step) {
                    float scan = y + row * drawHeight / (float) frameHeight;
                    canvas.drawRect(x, scan, x + drawWidth,
                            scan + Math.max(1f, drawHeight / (float)frameHeight * .35f),
                            lcdScanlinePaint);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Surface was destroyed during an Android orientation change.
        } finally {
            if (canvas != null) {
                try { holder.unlockCanvasAndPost(canvas); }
                catch (IllegalArgumentException | IllegalStateException ignored) {}
            }
        }
    }

    private AudioTrack createAudioTrack(int sampleRate, boolean compatibility) {
        int min = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return null;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(attrs).setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(min * (compatibility ? 4 : 2), compatibility ? 16384 : 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            track.setVolume(audioMuted ? 0f : volumePercent / 100f);
            track.play();
            return track;
        } catch (RuntimeException error) {
            setStatus("Audio no disponible; jugando sin sonido");
            return null;
        }
    }

    private void releaseAudioTrack(AudioTrack track) {
        if (track == null) return;
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
            track.flush();
            track.stop();
        } catch (IllegalStateException ignored) { }
        try { track.release(); } catch (RuntimeException ignored) { }
    }

    /** The UI must never report success before the cartridge bytes are verified. */
    private enum BatterySaveResult { SAVED, NO_SAVE_MEMORY, FAILED }

    // Invoked only by the emulation worker, including its shutdown/finally path.
    private BatterySaveResult saveBattery() {
        if (!coreIsOpen || saveFile == null) return BatterySaveResult.FAILED;
        try {
            byte[] ram = SameBoyNative.nativeSaveRam();
            byte[] rtc = SameBoyNative.nativeSaveRtc();
            boolean hasRam = ram != null && ram.length > 0;
            boolean hasRtc = rtc != null && rtc.length > 0;
            if (!hasRam && !hasRtc) return BatterySaveResult.NO_SAVE_MEMORY;
            if (hasRam && !persistAtomic(saveFile, ram)) return BatterySaveResult.FAILED;
            if (hasRtc && !persistAtomic(rtcFile, rtc)) return BatterySaveResult.FAILED;
            return BatterySaveResult.SAVED;
        } catch (RuntimeException | LinkageError error) {
            return BatterySaveResult.FAILED;
        }
    }

    private void reportBatterySave(BatterySaveResult result, boolean imported) {
        if (result == BatterySaveResult.SAVED) {
            setStatus(imported ? "Partida importada y guardada correctamente"
                    : "Cartucho guardado y verificado");
        } else if (result == BatterySaveResult.NO_SAVE_MEMORY) {
            setStatus(imported ? "Partida importada solo en memoria; no hay datos de batería"
                    : "El núcleo no expone memoria de batería para este cartucho");
        } else {
            setStatus(imported ? "Partida importada solo en memoria: no se pudo guardar"
                    : "No se pudo confirmar el guardado del cartucho");
        }
    }

    private boolean persistAtomic(File target, byte[] data) {
        if (target == null || data == null || data.length == 0) return false;
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            output.write(data);
            atomic.finishWrite(output);
            output = null;
            // AtomicFile's completed write is not enough to assert success:
            // read the new bytes back before showing a confirmation.
            return java.util.Arrays.equals(data, atomic.readFully());
        } catch (Exception error) {
            if (output != null) atomic.failWrite(output);
            return false;
        }
    }


    private File previousBackupFile() {
        return saveFile == null || activeRomHash == null ? null
                : new File(saveFile.getParentFile(),
                        activeRomHash + ".before-restore" + OkubiSaveArchive.EXTENSION);
    }

    // Calls to SameBoyNative belong to the same emulation worker as nativeStep().
    private OkubiSaveArchive.Snapshot captureLiveSnapshot() throws IOException {
        if (!coreIsOpen || activeRomHash == null) throw new IOException("Juego no iniciado");
        return new OkubiSaveArchive.Snapshot(system, activeRomHash,
                SameBoyNative.nativeSaveRam(), SameBoyNative.nativeSaveRtc(),
                SameBoyNative.nativeSaveState());
    }

    private void exportBackup(Uri destination) {
        try {
            OkubiSaveArchive.Snapshot exported = captureLiveSnapshot();
            try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("Destino de exportación no disponible");
                OkubiSaveArchive.write(out, exported);
            }
            // Read the SAF document back: provider write success is not necessarily
            // proof that the exported bytes are accessible and uncorrupted.
            try (InputStream input = getContentResolver().openInputStream(destination)) {
                OkubiSaveArchive.Snapshot verified = OkubiSaveArchive.read(
                        input, activeRomHash, system);
                if (!OkubiSaveArchive.samePayload(exported, verified)) {
                    throw new IOException("El contenido exportado no coincide");
                }
            }
            setStatus("Respaldo exportado y verificado; consérvalo fuera de OKUBI");
        } catch (Exception error) {
            setStatus("No se pudo confirmar el respaldo externo: " + error.getMessage());
        }
    }

    private void writeAutomaticRecovery(OkubiSaveArchive.Snapshot before)
            throws IOException {
        File recovery = previousBackupFile();
        if (recovery == null) throw new IOException("Carpeta de recuperación no disponible");
        AtomicFile atomic = new AtomicFile(recovery);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            OkubiSaveArchive.write(output, before);
            atomic.finishWrite(output);
            output = null;
            try (InputStream check = atomic.openRead()) {
                OkubiSaveArchive.Snapshot restored =
                        OkubiSaveArchive.read(check, activeRomHash, system);
                if (!OkubiSaveArchive.samePayload(before, restored)) {
                    throw new IOException("No se pudo verificar la copia previa");
                }
            }
        } catch (Exception error) {
            if (output != null) atomic.failWrite(output);
            throw new IOException("No se pudo proteger la partida anterior", error);
        }
    }

    private static boolean matchesSize(byte[] imported, byte[] active) {
        return imported == null || (active != null && imported.length == active.length);
    }

    private byte[] readStoredFile(File file, int maximum) throws IOException {
        if (file == null || !file.isFile()) return null;
        if (file.length() <= 0 || file.length() > maximum) {
            throw new IOException("Guardado anterior inválido; no se sobrescribió");
        }
        byte[] bytes = new AtomicFile(file).readFully();
        if (bytes.length == 0 || bytes.length > maximum) {
            throw new IOException("Guardado anterior demasiado grande");
        }
        return bytes;
    }

    private boolean restoreStoredFile(File file, byte[] previous) {
        if (file == null) return false;
        if (previous != null) return persistAtomic(file, previous);
        try {
            new AtomicFile(file).delete();
            return !file.exists();
        } catch (Exception error) {
            return false;
        }
    }

    private boolean restoreLiveSnapshot(OkubiSaveArchive.Snapshot before) {
        try {
            boolean success = true;
            if (before.state != null) success &= SameBoyNative.nativeLoadState(before.state);
            if (before.ram != null) success &= SameBoyNative.nativeLoadRam(before.ram);
            if (before.rtc != null) success &= SameBoyNative.nativeLoadRtc(before.rtc);
            return success;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /**
     * Verify backup identity/size and preserve old data BEFORE touching the
     * emulator or persistent files. SAF file I/O and core calls run on worker.
     */
    private void installBackup(OkubiSaveArchive.Snapshot imported,
                               boolean preservePrevious) throws IOException {
        OkubiSaveArchive.Snapshot before = captureLiveSnapshot();
        if (!matchesSize(imported.ram, before.ram)
                || !matchesSize(imported.rtc, before.rtc)
                || !matchesSize(imported.state, before.state)) {
            throw new IOException("El respaldo no es compatible con este núcleo");
        }

        // Keep one private recovery point for the last manual import. This file
        // is not deleted when an import fails, and can be chosen from the menu.
        if (preservePrevious) writeAutomaticRecovery(before);

        // Capture the exact ON-DISK bytes; the last saved checkpoint may be
        // different from the current live emulator state.
        byte[] originalRam = readStoredFile(saveFile, OkubiSaveArchive.MAX_RAM);
        byte[] originalRtc = readStoredFile(rtcFile, OkubiSaveArchive.MAX_RTC);
        byte[] originalState = readStoredFile(snapshotFile, OkubiSaveArchive.MAX_STATE);

        boolean loaded = true;
        try {
            if (imported.state != null) {
                loaded = SameBoyNative.nativeLoadState(imported.state);
            }
            if (loaded && imported.ram != null) {
                loaded = SameBoyNative.nativeLoadRam(imported.ram);
            }
            if (loaded && imported.rtc != null) {
                loaded = SameBoyNative.nativeLoadRtc(imported.rtc);
            }
        } catch (RuntimeException | LinkageError error) {
            loaded = false;
        }
        if (!loaded) {
            boolean recovered = restoreLiveSnapshot(before);
            throw new IOException(recovered ? "El núcleo rechazó este respaldo"
                    : "El núcleo rechazó el respaldo y no pudo recuperar la sesión anterior");
        }

        // A restored state may also contain SRAM. Persist the resulting live
        // values rather than leaving stale cartridge files on disk.
        OkubiSaveArchive.Snapshot active;
        try {
            active = captureLiveSnapshot();
        } catch (IOException | RuntimeException | LinkageError error) {
            restoreLiveSnapshot(before);
            throw new IOException("No se pudo verificar el estado restaurado", error);
        }
        boolean stored = true;
        if (active.ram != null) stored = persistAtomic(saveFile, active.ram) && stored;
        if (active.rtc != null) stored = persistAtomic(rtcFile, active.rtc) && stored;
        if (imported.state != null) {
            stored = persistAtomic(snapshotFile, imported.state) && stored;
        }
        if (!stored) {
            boolean restoredFiles = restoreStoredFile(saveFile, originalRam)
                    & restoreStoredFile(rtcFile, originalRtc)
                    & restoreStoredFile(snapshotFile, originalState);
            boolean restoredCore = restoreLiveSnapshot(before);
            throw new IOException(restoredFiles && restoredCore
                    ? "No se pudo escribir: se conservó la partida anterior"
                    : "Error de escritura. Usa Recuperar partida anterior o el respaldo externo");
        }
        saveRequested = false;
        // The imported state may replace the legacy slot 1 checkpoint.
        // Its old screenshot would describe a different game moment.
        if(imported.state != null) {
            File oldPreview=previewForSlot(RetroSaveSlots.FIRST);
            if(oldPreview != null) {
                try { new AtomicFile(oldPreview).delete(); }
                catch(RuntimeException ignored) {
                    // A cosmetic thumbnail cannot invalidate a good restore.
                }
            }
        }
        previewFrameReady = false;
        // An imported backup invalidates orders created before the import.
        slotRequests.clear();
        keys.set(0);
        padKeys.set(0);
        analogKeys.set(0);
        audioResetRequested = true;
    }

    private void importBackup(Uri source) {
        try (InputStream input = getContentResolver().openInputStream(source)) {
            OkubiSaveArchive.Snapshot imported =
                    OkubiSaveArchive.read(input, activeRomHash, system);
            installBackup(imported, true);
            setStatus("Respaldo restaurado; partida anterior protegida en OKUBI");
        } catch (Exception error) {
            setStatus("No se pudo restaurar: " + error.getMessage());
        }
    }

    private void recoverPreviousBackup() {
        File saved = previousBackupFile();
        if (saved == null || !saved.isFile()) {
            setStatus("No hay copia previa de este juego");
            return;
        }
        try (InputStream input = new AtomicFile(saved).openRead()) {
            OkubiSaveArchive.Snapshot imported =
                    OkubiSaveArchive.read(input, activeRomHash, system);
            installBackup(imported, false);
            setStatus("Se recuperó la partida anterior de este cartucho");
        } catch (Exception error) {
            setStatus("No se pudo recuperar la partida anterior: " + error.getMessage());
        }
    }

    /**
     * Thumbnail is a best-effort sidecar. The .state was already written and
     * verified BEFORE this method runs. A PNG failure must never invalidate
     * or rewrite a successful checkpoint.
     */
    private void writeSlotPreviewOnWorker(int slot) {
        File preview = previewForSlot(slot);
        if(preview == null) return;
        boolean saved = false;
        try {
            if(previewFrameReady && previewSourceFrame != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(96 * 1024);
                if(previewSourceFrame.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    byte[] image = out.toByteArray();
                    if(RetroSlotPreviews.validImageSize(image.length))
                        saved = persistAtomic(preview, image);
                }
            }
        } catch(RuntimeException ignored) {
            // A thumbnail is not part of the saved emulator state.
        }
        if(!saved && preview.exists()) {
            // Never show a stale thumbnail after successfully replacing
            // a checkpoint, even if the screenshot couldn't be generated.
            new AtomicFile(preview).delete();
        }
    }

    /** Only the emulation thread may invoke native state APIs or write files. */
    private void saveSnapshotOnWorker(int slot) {
        File destination=snapshotForSlot(slot);
        try {
            byte[] checkpoint=SameBoyNative.nativeSaveState();
            if(destination!=null && checkpoint!=null
                    && RetroSaveSlots.validStateSize(checkpoint.length)
                    && persistAtomic(destination,checkpoint)) {
                writeSlotPreviewOnWorker(slot);
                setStatus(RetroSaveSlots.label(slot)+" guardada y verificada");
            } else {
                setStatus("No se pudo guardar "+RetroSaveSlots.label(slot)+
                        ". Las demás ranuras no cambiaron.");
            }
        } catch (RuntimeException | LinkageError error) {
            setStatus("Error al guardar "+RetroSaveSlots.label(slot)+
                    "; revisa tus respaldos antes de salir.");
        }
    }

    /** Failed native loads restore the live state rather than altering disk. */
    private void loadSnapshotOnWorker(int slot) {
        File source=snapshotForSlot(slot);
        if(source==null || !source.isFile()
                || !RetroSaveSlots.validStateSize(source.length())) {
            setStatus(RetroSaveSlots.label(slot)+" vacía o dañada; no se restauró");
            return;
        }
        try {
            byte[] previous=SameBoyNative.nativeSaveState();
            byte[] snapshot=new AtomicFile(source).readFully();
            if(!RetroSaveSlots.validStateSize(snapshot.length))
                throw new IOException("Estado fuera de tamaño seguro");
            if(SameBoyNative.nativeLoadState(snapshot)) {
                keys.set(0);
                padKeys.set(0);
                analogKeys.set(0);
                saveRequested=true;
                previewFrameReady = false;
                audioResetRequested=true;
                setStatus(RetroSaveSlots.label(slot)+" restaurada");
            } else {
                boolean rollback=previous!=null
                        &&RetroSaveSlots.validStateSize(previous.length)
                        &&SameBoyNative.nativeLoadState(previous);
                setStatus(RetroSaveSlots.label(slot)+(rollback
                        ? " incompatible; juego anterior recuperado"
                        : " incompatible; no se pudo confirmar recuperación"));
            }
        } catch (Exception | LinkageError error) {
            setStatus(RetroSaveSlots.label(slot)+
                    " no pudo leerse; ninguna ranura modificada");
        }
    }

    private void emulate() {
        AudioTrack audio = null;
        boolean emulationFailed = false;
        RetroAudioRetry audioRetry = new RetroAudioRetry();
        RetroRenderPolicy renderPolicy = new RetroRenderPolicy();
        renderPolicy.setMode(performanceMode);
        try {
            byte[] rom;
            if (bundledAsset != null && "GBA".equals(system)) {
                try (InputStream embedded = OkubiPersonalLibrary.open(this, bundledAsset)) {
                    rom = OkubiRomFormats.readEmbeddedGba(embedded);
                }
            } else if (privateHomebrew != null) {
                rom = readRom(new java.io.FileInputStream(privateHomebrew));
            } else {
                String sourceName = OkubiRomFormats.documentName(getContentResolver(), romUri);
                rom = "SNES".equals(system)
                        ? OkubiRomFormats.readGameSnes(getContentResolver(), romUri, sourceName)
                        : "GBA".equals(system)
                        ? OkubiRomFormats.readGameAdvance(getContentResolver(), romUri, sourceName)
                        : OkubiRomFormats.readGameBoy(getContentResolver(), romUri, sourceName);
            }
            File directory = new File(getFilesDir(),
                    "SNES".equals(system) ? "okubi/snes"
                    : "GBA".equals(system) ? "okubi/gba" : "okubi/gameboy");
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IllegalStateException("No se pudo crear el directorio de guardados");
            String saveName = sha256(rom);
            activeRomHash = saveName;
            saveFile = new File(directory, saveName + ".srm");
            rtcFile = new File(directory, saveName + ".rtc");
            snapshotFile = new File(directory, saveName + ".state");
            int lastSlot=RetroSaveSlots.FIRST;
            try {
                lastSlot=getSharedPreferences("okubi_retro_slots_v1",MODE_PRIVATE)
                        .getInt("slot_"+saveName,RetroSaveSlots.FIRST);
            }catch(ClassCastException ignored){
                // Older or malformed imported preferences must never crash gameplay.
            }
            selectedSnapshotSlot=RetroSaveSlots.normalized(lastSlot);
            if (!SameBoyNative.nativeOpen(rom, directory.getAbsolutePath(), system))
                throw new IllegalStateException("El núcleo retro no pudo cargar el juego");
            coreIsOpen = true;
            if (saveFile.isFile()) {
                try {
                    if (!SameBoyNative.nativeLoadRam(new AtomicFile(saveFile).readFully())) {
                        setStatus("El archivo de batería no corresponde a este cartucho");
                    }
                } catch (Exception ignored) {
                    setStatus("No se pudo recuperar el guardado de batería");
                }
            }
            if (rtcFile.isFile()) {
                try {
                    SameBoyNative.nativeLoadRtc(new AtomicFile(rtcFile).readFully());
                } catch (Exception ignored) {
                    setStatus("No se pudo recuperar el reloj del cartucho");
                }
            }

            int[] imagePixels = new int[frameWidth * frameHeight];
            short[] samples = new short[8192];
            Bitmap frame = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
            previewSourceFrame = frame;
            previewFrameReady = false;
            int sampleRate = SameBoyNative.nativeSampleRate();
            audio = createAudioTrack(sampleRate, safeAudio);
            if (audio == null) audioRetry.failed(SystemClock.elapsedRealtime());
            boolean audioPaused = false;
            float lastGain = -1f;
            int appliedSpeed = RetroSpeedControl.NORMAL;
            long emulatedFrames = 0L;
            long normalFrameNs = RetroFrameTiming.frameIntervalNanos(SameBoyNative.nativeFps());
            long frameNs = normalFrameNs;
            long deadline = SystemClock.elapsedRealtimeNanos();
            setStatus("Jugando · " + ("SNES".equals(system)
                    ? "Super Nintendo (Beetle Supafaust)"
                    : "GBA".equals(system) ? "Game Boy Advance (mGBA)"
                    : "Game Boy / Color (SameBoy)"));

            while (!destroyed) {
                Uri saveDestination = pendingBackupExport;
                if (saveDestination != null) {
                    pendingBackupExport = null;
                    exportBackup(saveDestination);
                }
                Uri restoreSource = pendingBackupImport;
                if (restoreSource != null) {
                    pendingBackupImport = null;
                    importBackup(restoreSource);
                }
                if (pendingRestorePrevious) {
                    pendingRestorePrevious = false;
                    recoverPreviousBackup();
                }
                Uri saveToImport = pendingBatteryImport;
                if (saveToImport != null) {
                    pendingBatteryImport = null;
                    try {
                        byte[] contents = readBattery(getContentResolver().openInputStream(saveToImport));
                        OkubiSaveArchive.Snapshot rawImport = new OkubiSaveArchive.Snapshot(
                                system, activeRomHash, contents, null, null);
                        installBackup(rawImport, true);
                        setStatus("Partida .sav/.srm importada; copia anterior protegida");
                    } catch (Exception error) {
                        setStatus("No se pudo importar la partida: " + error.getMessage());
                    }
                }
                if (saveRequested) {
                    saveRequested = false;
                    reportBatterySave(saveBattery(), false);
                }
                RetroSlotRequests.Request nextSlot=slotRequests.poll();
                if(nextSlot!=null) {
                    // The current UI selection can change while this runs.
                    // Execute ONLY the slot + ROM chosen when the user clicked.
                    if(!nextSlot.romHash.equals(activeRomHash)) {
                        setStatus("Punto cancelado: el cartucho cambió");
                    } else if(nextSlot.action==RetroSlotRequests.Action.SAVE) {
                        File target=snapshotForSlot(nextSlot.slot);
                        if(nextSlot.expectedSaveTarget==null
                                || !nextSlot.expectedSaveTarget.matches(target)) {
                            setStatus("Guardado cancelado: la ranura cambió o está protegida");
                        } else {
                            saveSnapshotOnWorker(nextSlot.slot);
                        }
                    } else {
                        loadSnapshotOnWorker(nextSlot.slot);
                    }
                }
                SlotCopyRequest copy=pendingSlotCopy;
                if(copy!=null) {
                    pendingSlotCopy=null;
                    File source=snapshotForSlot(copy.from);
                    File destination=snapshotForSlot(copy.to);
                    if(!copy.romHash.equals(activeRomHash) || source==null
                            || !source.isFile() || destination==null
                            || !RetroSaveSlots.canCopy(copy.from,copy.to,
                                    source.length(),destination.exists())) {
                        setStatus("Copia cancelada: origen inválido o destino ocupado");
                    } else {
                        try {
                            byte[] bytes=new AtomicFile(source).readFully();
                            if(!RetroSaveSlots.canCopy(copy.from,copy.to,
                                    bytes.length,destination.exists()))
                                throw new IOException("Destino no disponible");
                            if(!persistAtomic(destination,bytes)) {
                                // Only this worker writes state files; target was absent.
                                new AtomicFile(destination).delete();
                                throw new IOException("No se pudo verificar la copia");
                            }
                            // Preview sidecars are OPTIONAL and never copied
                            // before the underlying state has been verified.
                            File sourcePreview=previewForSlot(copy.from);
                            File destPreview=previewForSlot(copy.to);
                            if(sourcePreview != null && destPreview != null
                                    && sourcePreview.isFile()
                                    && RetroSlotPreviews.validImageSize(sourcePreview.length())
                                    && !destPreview.exists()) {
                                try {
                                    byte[] png=new AtomicFile(sourcePreview).readFully();
                                    if(RetroSlotPreviews.validImageSize(png.length))
                                        persistAtomic(destPreview,png);
                                } catch(Exception ignored) { }
                            }
                            setStatus("Punto duplicado en "+RetroSaveSlots.label(copy.to)+
                                    " · copia de archivo verificada");
                        } catch(Exception error) {
                            setStatus("No se copió el punto. Ranura original intacta");
                        }
                    }
                }
                // Reset pacing and audio on a speed transition. AudioTrack at
                // non-1x would throttle the core or play at the wrong pitch.
                int requestedSpeed = RetroSpeedControl.normalize(speedStep);
                if (requestedSpeed != appliedSpeed) {
                    appliedSpeed = requestedSpeed;
                    frameNs = RetroSpeedControl.intervalNanos(normalFrameNs, appliedSpeed);
                    deadline = SystemClock.elapsedRealtimeNanos();
                    renderPolicy.reset();
                    releaseAudioTrack(audio);
                    audio = null;
                    audioPaused = false;
                    lastGain = -1f;
                    audioRetry.reset();
                }
                if (audioResetRequested) {
                    audioResetRequested = false;
                    releaseAudioTrack(audio);
                    audio = null;
                    audioPaused = false;
                    lastGain = -1f;
                    audioRetry.reset();
                }
                if (!playing) {
                    if (audio != null && !audioPaused) {
                        try { audio.pause(); audio.flush(); }
                        catch (IllegalStateException ignored) { audioResetRequested = true; }
                        audioPaused = true;
                    }
                    SystemClock.sleep(24);
                    deadline = SystemClock.elapsedRealtimeNanos();
                    renderPolicy.reset();
                    continue;
                }
                if (RetroSpeedControl.normal(appliedSpeed)
                        && audio == null && audioRetry.mayAttempt(SystemClock.elapsedRealtime())) {
                    audio = createAudioTrack(sampleRate, safeAudio);
                    if (audio == null) audioRetry.failed(SystemClock.elapsedRealtime());
                    else audioRetry.reset();
                    lastGain = -1f;
                }
                if (audio != null && audioPaused) {
                    try { audio.play(); } catch (IllegalStateException ignored) {
                        audioResetRequested = true;
                    }
                    audioPaused = false;
                }
                if (audio != null) {
                    float wantedGain = audioMuted ? 0f :
                            Math.max(0f, Math.min(1f, volumePercent / 100f));
                    if (wantedGain != lastGain) {
                        audio.setVolume(wantedGain);
                        lastGain = wantedGain;
                    }
                }
                // The core and audio ALWAYS advance, even on frames that are
                // not presented. NULL image skips JNI pixel copies on those frames.
                renderPolicy.setMode(performanceMode);
                SurfaceHolder displayHolder=currentHolder;
                boolean surfaceReady=displayHolder!=null
                        && displayHolder.getSurface()!=null
                        && displayHolder.getSurface().isValid();
                boolean present=renderPolicy.shouldPresent(surfaceReady);
                present = present && RetroSpeedControl.presentFrame(appliedSpeed, emulatedFrames++);
                int inputMask = keys.get() | padKeys.get() | analogKeys.get();
                int audioCount = SameBoyNative.nativeStep(inputMask,
                        present ? imagePixels : null, samples);
                if (audioCount < 0) throw new IllegalStateException("Fallo en el núcleo Libretro");
                if (present) {
                    drawFrame(frame, imagePixels);
                    previewFrameReady = true;
                }
                if (audio != null && audioCount > 0) {
                    try {
                        int written = audio.write(samples, 0, audioCount,
                                safeAudio ? AudioTrack.WRITE_BLOCKING : AudioTrack.WRITE_NON_BLOCKING);
                        if (written < 0) {
                            audioResetRequested = true;
                            setStatus("Audio reiniciándose por error de salida");
                        }
                    } catch (IllegalStateException error) {
                        audioResetRequested = true;
                    }
                }

                long now = SystemClock.elapsedRealtimeNanos();
                long scheduled = RetroFrameTiming.nextDeadline(deadline, now, frameNs);
                renderPolicy.observeFrame(Math.max(0L, now - scheduled), frameNs);
                deadline = scheduled;
                long park = RetroFrameTiming.parkNanos(deadline - now);
                if (park > 0L) java.util.concurrent.locks.LockSupport.parkNanos(park);
            }
        } catch (Exception | LinkageError error) {
            emulationFailed = true;
            setStatus("No se pudo iniciar el juego: " + error.getMessage());
        } finally {
            if (coreIsOpen) {
                // On normal exit, complete already requested SAVE operations
                // before closing the core. Never LOAD an old state on exit.
                // An OS force-kill can still interrupt this best-effort cleanup.
                if (!emulationFailed) {
                    RetroSlotRequests.Request remaining;
                    while ((remaining=slotRequests.poll())!=null) {
                        if(remaining.action==RetroSlotRequests.Action.SAVE
                                && remaining.romHash.equals(activeRomHash)
                                && remaining.expectedSaveTarget != null
                                && remaining.expectedSaveTarget.matches(
                                        snapshotForSlot(remaining.slot))) {
                            saveSnapshotOnWorker(remaining.slot);
                        }
                    }
                } else slotRequests.clear();
                if(saveBattery()==BatterySaveResult.FAILED)
                    setStatus("No se pudo confirmar guardado al cerrar");
                SameBoyNative.nativeClose();
                coreIsOpen = false;
            }
            releaseAudioTrack(audio);
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        // Pause native frames while the old SurfaceView is detached. Never
        // re-open the core, replace the ROM or alter on-disk SRAM/RTC/states.
        playing = false;
        currentHolder = null;
        // Persist edits under the OLD orientation prefix before resources
        // switch to the new portrait/landscape profile.
        if (editingControls && controls != null) controls.getProfile().save(this);
        super.onConfigurationChanged(config);
        // Android may omit UP/CANCEL while rotating. Release input to avoid
        // a stuck button, then rebuild using the correct orientation profile.
        keys.set(0);
        clearPhysicalController();
        if (controls != null) controls.releaseAll();
        audioResetRequested = true;
        buildLayout();
        // Only resume if this Activity is resumed and no dialog/editor is open.
        syncPlaying();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) {
            keys.set(0);
            clearPhysicalController();
            if (controls != null) controls.releaseAll();
        }
    }

    @Override protected void onPause() {
        if (editingControls && controls != null) controls.getProfile().save(this);
        activityResumed = false;
        playing = false;
        saveRequested = true;
        keys.set(0);
        clearPhysicalController();
        if (controllerListenerRegistered) {
            InputManager manager=(InputManager)getSystemService(INPUT_SERVICE);
            if (manager != null) manager.unregisterInputDeviceListener(controllerListener);
            controllerListenerRegistered=false;
        }
        if (controls != null) controls.releaseAll();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!controllerListenerRegistered) {
            InputManager manager=(InputManager)getSystemService(INPUT_SERVICE);
            if (manager != null) {
                manager.registerInputDeviceListener(controllerListener, null);
                controllerListenerRegistered=true;
            }
        }
        activityResumed = true;
        audioResetRequested = true;
        syncPlaying();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        saveRequested = true;
        keys.set(0);
        clearPhysicalController();
        if (controllerListenerRegistered) {
            InputManager manager=(InputManager)getSystemService(INPUT_SERVICE);
            if (manager != null) manager.unregisterInputDeviceListener(controllerListener);
            controllerListenerRegistered=false;
        }
        if (controls != null) controls.releaseAll();
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }
}
