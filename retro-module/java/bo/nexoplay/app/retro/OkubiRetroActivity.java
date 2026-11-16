package bo.nexoplay.app.retro;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * OKUBI retro UI. Lightweight state updates when switching systems,
 * native-aspect preview, non-blocking file checks and one touch surface.
 *
 * IMPORTANT: This UI does not yet contain an executable emulator core.
 */
public final class OkubiRetroActivity extends Activity {
    public static final String EXTRA_SYSTEM = "okubi.open.console";
    private static final int PICK_ROM = 5217;
    private static final int BG = 0xFF0B0712;
    private static final int SURFACE = 0xFF1E1529;
    private static final int TEXT = 0xFFFAF7FD;
    private final Button[] consoleButtons = new Button[RetroSystems.ALL.size()];
    private final ExecutorService fileIo = Executors.newSingleThreadExecutor();

    private int selected = 0;
    private int importRequest = 0;
    private Uri chosenRom;
    private String chosenName;
    private ScrollView pageScroll;
    private TextView systemCard;
    private RetroTouchPad touchPad;
    private TextView selectedTitle;
    private TextView coreLabel;
    private TextView performanceLabel;
    private TextView fileLabel;
    private Button externalLaunch;
    private Button officialEmulatorButton;
    private Button internalLaunch;
    private boolean wide;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (state == null) {
            String requested=getIntent().getStringExtra(EXTRA_SYSTEM);
            if(requested != null)
                for(int i=0;i<RetroSystems.ALL.size();i++)
                    if(requested.equals(RetroSystems.byPosition(i).id)) {
                        selected=i;
                        break;
                    }
        }
        if (state != null) {
            selected = state.getInt("system", 0);
            chosenName = state.getString("name");
            String saved = state.getString("uri");
            if (saved != null) chosenRom = Uri.parse(saved);
        }
        buildInterface();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("system", selected);
        state.putString("name", chosenName);
        if (chosenRom != null) state.putString("uri", chosenRom.toString());
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        buildInterface();
    }

    @Override protected void onPause() {
        super.onPause();
        if (touchPad != null) touchPad.releaseAll();
    }

    @Override protected void onDestroy() {
        importRequest++;
        fileIo.shutdownNow();
        if (touchPad != null) touchPad.releaseAll();
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView label(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(TEXT);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button button(String title, Runnable onClick) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(TEXT);
        button.setBackground(background(SURFACE, 12));
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this),
                new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void buildInterface() {
        final int previousScrollY = pageScroll == null ? 0 : pageScroll.getScrollY();
        wide = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE
                && getResources().getConfiguration().screenWidthDp >= 600;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        pageScroll = scroll;
        setContentView(scroll);

        root.addView(label("OKUBI  /  RETRO", 24, true));
        root.addView(label("Biblioteca de consolas • Vertical y horizontal", 13, false));
        TextView device = label(RetroDeviceProfile.summary(this), 11, false);
        device.setTextColor(0xFFBBABBF);
        root.addView(device);
        gap(root, 16);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout display = vertical();
        display.setPadding(dp(14), dp(14), dp(14), dp(16));
        display.setBackground(background(SURFACE, 20));
        columns.addView(display, wide
                ? new LinearLayout.LayoutParams(0, -2, 1f)
                : new LinearLayout.LayoutParams(-1, -2));

        selectedTitle = label("", 18, true);
        selectedTitle.setGravity(Gravity.CENTER);
        display.addView(selectedTitle);
        gap(display, 12);

        systemCard = label("", 14, false);
        systemCard.setPadding(dp(18), dp(20), dp(18), dp(20));
        systemCard.setMinHeight(dp(110));
        systemCard.setBackground(background(0xFF140A1D, 14));
        display.addView(systemCard, new LinearLayout.LayoutParams(-1, -2));
        gap(display, 12);

        // Controls belong inside a running GameBoyActivity, never as a
        // deceptive non-functional controller on the library chooser.
        touchPad = null;

        if (wide) {
            View spacer = new View(this);
            columns.addView(spacer, new LinearLayout.LayoutParams(dp(14), 1));
        } else gap(columns, 14);

        LinearLayout menu = vertical();
        columns.addView(menu, wide
                ? new LinearLayout.LayoutParams(0, -2, 1f)
                : new LinearLayout.LayoutParams(-1, -2));
        menu.addView(button("Juegos retro legales",
                () -> startActivity(new Intent(this, OkubiCatalogActivity.class))),
                new LinearLayout.LayoutParams(-1, dp(52)));
        gap(menu, 10);
        menu.addView(label("Jugables dentro de OKUBI", 17, true));
        gap(menu, 6);

        HorizontalScrollView supportedScroll = new HorizontalScrollView(this);
        supportedScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout supportedRow = new LinearLayout(this);
        supportedRow.setOrientation(LinearLayout.HORIZONTAL);
        supportedScroll.addView(supportedRow);
        menu.addView(supportedScroll);
        gap(menu, 12);
        menu.addView(label("Otras consolas · requieren emulador externo", 13, false));
        HorizontalScrollView experimentalScroll = new HorizontalScrollView(this);
        experimentalScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout experimentalRow = new LinearLayout(this);
        experimentalRow.setOrientation(LinearLayout.HORIZONTAL);
        experimentalScroll.addView(experimentalRow);
        menu.addView(experimentalScroll);
        for (int i = 0; i < RetroSystems.ALL.size(); i++) {
            final int index = i;
            RetroSystems.SystemSpec spec = RetroSystems.byPosition(i);
            Button candidate = button(spec.title, () -> chooseSystem(index));
            consoleButtons[i] = candidate;
            LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(dp(158), dp(54));
            chip.rightMargin = dp(8);
            if ("GB".equals(spec.id) || "GBC".equals(spec.id)
                    || "GBA".equals(spec.id) || "SNES".equals(spec.id)) {
                supportedRow.addView(candidate, chip);
            } else {
                experimentalRow.addView(candidate, chip);
            }
        }

        gap(menu, 10);
        coreLabel = label("", 13, false);
        performanceLabel = label("", 12, false);
        menu.addView(coreLabel);
        gap(menu, 4);
        menu.addView(performanceLabel);
        gap(menu, 10);
        menu.addView(button("Importar archivo propio", this::chooseRom),
                new LinearLayout.LayoutParams(-1, dp(52)));
        gap(menu, 8);
        fileLabel = label("", 12, false);
        menu.addView(fileLabel);
        gap(menu, 8);
        internalLaunch = button("Jugar dentro de OKUBI", this::launchInternalGameBoy);
        menu.addView(internalLaunch, new LinearLayout.LayoutParams(-1, dp(50)));
        gap(menu, 6);
        externalLaunch = button("Abrir con emulador instalado",
                () -> RetroExternalLauncher.open(this, chosenRom,
                        RetroSystems.byPosition(selected).id));
        menu.addView(externalLaunch, new LinearLayout.LayoutParams(-1, dp(50)));
        gap(menu, 7);
        officialEmulatorButton=button("Ver emulador externo oficial",
                () -> RetroExternalLauncher.openOfficialEmulatorPage(
                        this,RetroSystems.byPosition(selected).id));
        menu.addView(officialEmulatorButton,new LinearLayout.LayoutParams(-1,dp(50)));
        gap(menu, 12);

        TextView disclaimer = label("GB/GBC usan SameBoy, GBA usa mGBA y SNES usa "
                +"Beetle Supafaust si está disponible. NES y las demás consolas "
                +"clásicas todavía requieren emulador externo. No se copian ROMs.",
                12,false);
        disclaimer.setTextColor(0xFFBBABBF);
        menu.addView(disclaimer);
        refreshSelected(-1);
        if (previousScrollY > 0)
            scroll.post(() -> scroll.scrollTo(0, previousScrollY));
    }

    private void chooseSystem(int next) {
        if (next == selected) return;
        int before = selected;
        selected = next;
        chosenRom = null;
        chosenName = null;
        ++importRequest;  // discard any stale async validation result
        refreshSelected(before);
    }

    /** Update only the affected views; do not recreate 16 buttons on selection. */
    private void refreshSelected(int previous) {
        RetroSystems.SystemSpec spec = RetroSystems.byPosition(selected);
        boolean gba = "GBA".equals(spec.id);
        boolean snes = "SNES".equals(spec.id);
        boolean internal = "GB".equals(spec.id) || "GBC".equals(spec.id) || gba || snes;
        boolean installed = internal && (snes ? SameBoyNative.isSnesAvailable()
                : gba ? SameBoyNative.isGbaAvailable() : SameBoyNative.isAvailable());
        selectedTitle.setText(spec.title + "  ·  " +
                (installed ? "Motor interno" : internal ? "Núcleo no disponible" : "Catálogo / externo"));
        String description = installed
                ? (snes ? "Beetle Supafaust integrado\nImporta una ROM propia .sfc o .smc, sin ZIP." :
                    gba ? "mGBA integrado\nPuedes importar archivos .gba, también en ZIP." :
                    "SameBoy integrado\nPuedes importar archivos ." +
                        ("GB".equals(spec.id) ? "gb" : "gbc") +
                        " o encontrar juegos homebrew legales en Descubre.")
                : "No hay núcleo interno para esta consola.\n" +
                    "Puedes organizar tus archivos y probar con un emulador externo instalado.";
        systemCard.setText(description +
                "\n\nFormato aceptado: " + spec.extensions +
                "\nPerfil de hardware: " + spec.status());
        coreLabel.setText(installed ? (snes ? "Motor: Supafaust · SNES nativo listo" :
                    gba ? "Motor: mGBA · listo para cargar un archivo" :
                    "Motor: SameBoy · listo para cargar un archivo") :
                "Motor previsto: " + spec.coreCandidate + " · no incluido en OKUBI");
        performanceLabel.setText(RetroDeviceProfile.advice(spec));
        if (previous >= 0 && previous < consoleButtons.length)
            styleSystemButton(previous, false);
        if (selected >= 0 && selected < consoleButtons.length)
            styleSystemButton(selected, true);
        fileLabel.setText(chosenName == null ? "Ningún archivo seleccionado" :
                "Archivo reconocido: " + chosenName);
        boolean playable = installed && chosenRom != null;
        internalLaunch.setEnabled(playable);
        internalLaunch.setText(installed ? "Jugar dentro de OKUBI" :
                "Motor interno no disponible");
        internalLaunch.setAlpha(playable ? 1f : 0.5f);
        externalLaunch.setEnabled(chosenRom != null);
        externalLaunch.setAlpha(chosenRom == null ? 0.5f : 1f);
        officialEmulatorButton.setVisibility(
                RetroExpansionFormats.isExternalClassic(spec.id) && !installed
                        ? View.VISIBLE:View.GONE);
    }

    private void styleSystemButton(int index, boolean active) {
        Button button = consoleButtons[index];
        if (button == null) return;
        button.setBackground(background(active ? 0xFF50223D : SURFACE, 12));
        button.setTextColor(active ? 0xFFFFCBDB : TEXT);
    }

    private void launchInternalGameBoy() {
        if (chosenRom == null) return;
        String chosenSystem = RetroSystems.byPosition(selected).id;
        if (!("SNES".equals(chosenSystem) ? SameBoyNative.isSnesAvailable()
                : "GBA".equals(chosenSystem) ? SameBoyNative.isGbaAvailable()
                : SameBoyNative.isAvailable())) return;
        Intent game = new Intent(this, OkubiGameBoyActivity.class);
        game.setData(chosenRom);
        game.putExtra(OkubiGameBoyActivity.EXTRA_ROM, chosenRom);
        game.putExtra(OkubiGameBoyActivity.EXTRA_NAME, chosenName);
        game.putExtra(OkubiGameBoyActivity.EXTRA_SYSTEM, RetroSystems.byPosition(selected).id);
        game.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(game);
        OkubiPokemonRepository.markPlayed(this, chosenRom.toString());
    }

    private void chooseRom() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, PICK_ROM);
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "No hay selector de archivos disponible",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ROM || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        final Uri file = data.getData();
        final int expectedSystem = selected;
        final int request = ++importRequest;
        try {
            getContentResolver().takePersistableUriPermission(file,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { /* Some providers grant only temporary access. */ }
        fileLabel.setText("Analizando archivo sin bloquear la pantalla…");
        try {
            fileIo.execute(() -> {
                RetroFileInspector.Result result = RetroFileInspector.inspect(
                        getContentResolver(), file,
                        RetroSystems.byPosition(expectedSystem).id, false);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || request != importRequest || selected != expectedSystem) return;
                    if (!result.accepted) {
                        fileLabel.setText(result.reason);
                        Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show();
                        return;
                    }
                    chosenRom = file;
                    chosenName = result.name;
                    // A successful SAF selection is a personal library import.
                    // Unsupported systems stay in their own selector and are
                    // never advertised as internally emulatable.
                    boolean added=OkubiPokemonRepository.add(this,file,result.name,
                            RetroSystems.byPosition(expectedSystem).id);
                    if(!added) {
                        boolean alreadyLinked=false;
                        for(OkubiPokemonRepository.Item known :
                                OkubiPokemonRepository.list(this)) {
                            if(file.toString().equals(known.uri)
                                    && RetroSystems.byPosition(expectedSystem).id
                                            .equals(known.system)) {
                                alreadyLinked=true;break;
                            }
                        }
                        if(!alreadyLinked) {
                            chosenRom=null;
                            chosenName=null;
                            fileLabel.setText("Biblioteca llena o archivo no registrable");
                            return;
                        }
                    }
                    refreshSelected(-1);
                });
            });
        } catch (RejectedExecutionException error) {
            fileLabel.setText("No se pudo comenzar la comprobación");
        }
    }
}
