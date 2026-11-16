package bo.nexoplay.app.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Pokémon personal games library: catalog metadata + user-imported SAF ROMs.
 * Commercial ROMs are neither bundled nor downloaded.
 * Two independently created GBC monster-taming demos link to their authors.
 */
public final class OkubiPokemonActivity extends Activity {
    public static final String EXTRA_OPEN_PATCHES = "okubi.open.patches";
    private static final int REQUEST_IMPORT = 4109;
    private static final int REQUEST_MULTI = 4111;
    private static final int REQUEST_LINK_FREE_INDIE = 4117;
    private static final int REQUEST_PATCH_BASE = 4122;
    private static final int REQUEST_PATCH_FILE = 4123;
    private static final int REQUEST_PATCH_OUTPUT = 4124;
    private static final int BG = 0xFF0B0712;
    private static final int SURFACE = 0xFF23152D;
    private static final int ACCENT = 0xFFFF3E70;
    private static final int WHITE = 0xFFFAF7FD;
    private static final int SECONDARY = 0xFFD3C0D7;
    private static final String[][] GAMES = {
        {"Pokémon Rojo", "GB", "Kanto"}, {"Pokémon Azul", "GB", "Kanto"},
        {"Pokémon Verde (Japón)", "GB", "Kanto"}, {"Pokémon Amarillo", "GB", "Kanto"},
        {"Pokémon Oro", "GBC", "Johto"}, {"Pokémon Plata", "GBC", "Johto"},
        {"Pokémon Cristal", "GBC", "Johto"},
        {"Pokémon Rubí", "GBA", "Hoenn"}, {"Pokémon Zafiro", "GBA", "Hoenn"},
        {"Pokémon Esmeralda", "GBA", "Hoenn"}, {"Pokémon Rojo Fuego", "GBA", "Kanto"},
        {"Pokémon Verde Hoja", "GBA", "Kanto"},
        {"Pokémon Trading Card Game", "GBC", "Cartas"},
        {"Pokémon Pinball", "GBC", "Pinball"},
        {"Pokémon Puzzle Challenge", "GBC", "Puzles"},
        {"Pokémon Trading Card Game 2 (Japón)", "GBC", "Cartas"},
        {"Pokémon Pinball: Rubí y Zafiro", "GBA", "Pinball"},
        {"Pokémon Mundo Misterioso: Equipo de Rescate Rojo", "GBA", "RPG"},
        {"Pokémon Diamante", "NDS", "Sinnoh"}, {"Pokémon Perla", "NDS", "Sinnoh"},
        {"Pokémon Platino", "NDS", "Sinnoh"}, {"Pokémon Oro HeartGold", "NDS", "Johto"},
        {"Pokémon Plata SoulSilver", "NDS", "Johto"},
        {"Pokémon Negro", "NDS", "Teselia"}, {"Pokémon Blanco", "NDS", "Teselia"},
        {"Pokémon Negro 2", "NDS", "Teselia"}, {"Pokémon Blanco 2", "NDS", "Teselia"},
        {"Pokémon X", "3DS", "Kalos"}, {"Pokémon Y", "3DS", "Kalos"},
        {"Pokémon Rubí Omega", "3DS", "Hoenn"}, {"Pokémon Zafiro Alfa", "3DS", "Hoenn"},
        {"Pokémon Sol", "3DS", "Alola"}, {"Pokémon Luna", "3DS", "Alola"},
        {"Pokémon Ultrasol", "3DS", "Alola"}, {"Pokémon Ultraluna", "3DS", "Alola"},
        {"Pokémon Espada", "Switch", "Galar"}, {"Pokémon Escudo", "Switch", "Galar"},
        {"Pokémon Diamante Brillante", "Switch", "Sinnoh"},
        {"Pokémon Perla Reluciente", "Switch", "Sinnoh"},
        {"Leyendas Pokémon: Arceus", "Switch", "Hisui"},
        {"Pokémon Escarlata", "Switch", "Paldea"}, {"Pokémon Púrpura", "Switch", "Paldea"},
        {"Leyendas Pokémon: Z-A", "Switch", "Ciudad Luminalia"}
    };
    private static final String[][] HACKS = {
        {"Pokémon Unbound", "GBA", "ROM hack, requiere parche y juego base compatible"},
        {"Pokémon Radical Red", "GBA", "ROM hack, requiere parche y juego base compatible"},
        {"Pokémon Emerald Rogue", "GBA", "ROM hack, requiere parche y juego base compatible"},
        {"Pokémon Crystal Clear", "GBC", "ROM hack, requiere parche y juego base compatible"},
        {"Pokémon Prism", "GBC", "ROM hack, requiere parche y juego base compatible"},
        {"Pokémon Gaia", "GBA", "ROM hack, requiere parche y juego base compatible"}
    };

    // Game pages owned by original creators; these are NOT Pokémon games.
    private static final String[][] FREE_MONSTER_GAMES = {
        {"Monpact (demo)", "GBC", "RPG de capturar criaturas · ROM .gbc publicada por su creador",
            "https://retrokuro.itch.io/monpact"},
        {"PachiPals (demo)", "GBC", "Captura monstruos y pinball · demo .gbc del creador",
            "https://rebusmind.itch.io/pachipals-demo-gbc"},
        {"D-MONS", "GB", "RPG original de coleccionar criaturas · ROM ofrecida por el autor",
            "https://lazydevs.itch.io/d-mons"},
        {"CritterNet", "GB", "Mascotas virtuales · archivo CritterNet.gb publicado por el creador",
            "https://thealexcar.itch.io/critternet"},
        {"Pummel Pets", "GBC", "Roguelike de mascotas · ROM con aporte opcional/de pago según el autor",
            "https://gibbongl.itch.io/pummel-pets-game-boy"}
    };
    /* Official creator release pages for patches, not prepatched commercial ROMs. */
    private static final String[] HACK_PATCH_PAGES = {
        "https://pokeromcodex.notion.site/Unbound-081f9972dc5b4ee8b77ae7d2c343ca54",
        "https://patch.radicalred.net/",
        "https://github.com/Pokabbie/pokeemerald-rogue/releases",
        "https://shockslayer.com/crystal-clear/",
        "https://rainbowdevs.com/title/prism/",
        "https://sphericalice.com/"
    };
    // Official, verified Google Play app IDs, not unofficial APK mirrors.
    private static final String[][] ANDROID_GAMES = {
        {"Pokémon Champions", "Combates por turnos", "jp.pokemon.pokemonchampions"},
        {"Pokémon GO", "Captura y exploración", "com.nianticlabs.pokemongo"},
        {"Pokémon Masters EX", "Aventuras y combates", "com.dena.a12026418"},
        {"Pokémon UNITE", "Combates 5 contra 5", "jp.pokemon.pokemonunite"},
        {"Pokémon Café ReMix", "Puzles y colección", "jp.pokemon.pokemoncafemix"},
        {"Pokémon Quest", "RPG de aventuras", "jp.pokemon.pokemonquest"}
    };

    private String tab = "Míos";
    private String filter = "Todos";
    private String searchTerm = "";
    private String pendingFreeIndieSource;
    private boolean showHacks; // Kept for saved-state compatibility.
    private int importingIndex = -1;
    private boolean importingHack;
    private Uri patchBase;
    private Uri patchDocument;
    private String patchSystem;
    private String patchResultName;
    private final ExecutorService fileIo = Executors.newSingleThreadExecutor();
    private int importSerial;
    private ScrollView pageScroll;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            tab = state.getString("tab", "Míos");
            filter = state.getString("filter", "Todos");
            searchTerm = state.getString("search", "");
            pendingFreeIndieSource = state.getString("pendingFreeIndieSource");
            importingIndex = state.getInt("importIndex", -1);
            importingHack = state.getBoolean("importHack", false);
            String baseSaved = state.getString("patchBase");
            String patchSaved = state.getString("patchFile");
            if (baseSaved != null) patchBase = Uri.parse(baseSaved);
            if (patchSaved != null) patchDocument = Uri.parse(patchSaved);
            patchSystem = state.getString("patchSystem");
            patchResultName = state.getString("patchName");
        }
        if (state == null && getIntent().getBooleanExtra(EXTRA_OPEN_PATCHES, false))
            tab = "ROM hacks";
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        migrateLegacyBookmarksOnce();
        draw();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("tab", tab);
        state.putString("filter", filter);
        state.putString("search", searchTerm);
        state.putString("pendingFreeIndieSource", pendingFreeIndieSource);
        state.putInt("importIndex", importingIndex);
        state.putBoolean("importHack", importingHack);
        if (patchBase != null) state.putString("patchBase", patchBase.toString());
        if (patchDocument != null) state.putString("patchFile", patchDocument.toString());
        state.putString("patchSystem", patchSystem);
        state.putString("patchName", patchResultName);
    }
    @Override protected void onDestroy() {
        importSerial++;
        fileIo.shutdownNow();
        super.onDestroy();
    }
    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + .5f);
    }
    private GradientDrawable bg(int fill, int round) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(round));
        return g;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
    private Button button(String title, boolean primary, Runnable click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(13);
        b.setMinHeight(dp(42));
        b.setBackground(bg(primary ? ACCENT : 0xFF34233D, 12));
        b.setTextColor(WHITE);
        b.setOnClickListener(v -> click.run());
        return b;
    }
    private void space(LinearLayout parent, int h) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
    }
    private LinearLayout section(LinearLayout root, String title, String info) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(15), dp(14), dp(15), dp(14));
        section.setBackground(bg(SURFACE, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        root.addView(section, p);
        section.addView(label(title, 17, WHITE, true));
        space(section, 4);
        section.addView(label(info, 12, SECONDARY, false));
        return section;
    }
    private boolean includes(String title) {
        return searchTerm.trim().isEmpty()
                || title.toLowerCase(Locale.ROOT).contains(searchTerm.trim().toLowerCase(Locale.ROOT));
    }
    private void actionRow(LinearLayout parent, Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(9);
        parent.addView(row, layout);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i > 0) p.leftMargin = dp(7);
            row.addView(buttons[i], p);
        }
    }
    private void draw() { draw(false); }
    private void draw(boolean keepScroll) {
        int oldY = pageScroll == null ? 0 : pageScroll.getScrollY();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        pageScroll = scroll;
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(15), dp(18), dp(15), dp(28));
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(label("OKUBI  /  POKÉMON", 23, WHITE, true));
        space(root, 5);
        root.addView(label("Tu colección de aventuras, organizada para jugar", 13, SECONDARY, false));
        space(root, 13);

        LinearLayout hero = section(root, "Tu biblioteca Pokémon",
                "Archivos personales en el teléfono · sin ROMs comerciales incluidas");
        int extras = OkubiPokemonRepository.list(this).size();
        int bundled = OkubiPersonalLibrary.available(this).size();
        int originals = countImportedOfficial();
        hero.addView(label("   " + (extras + originals + bundled) +
                " juegos importados    •    " + GAMES.length + " clásicos    •    " +
                FREE_MONSTER_GAMES.length + " demos gratis", 12, 0xFFFFB9D0, true));
        actionRow(hero,
                button("+ Importar varios", true, this::importSeveral),
                button("Buscar", false, this::showSearch));
        space(root, 12);

        HorizontalScrollView tabs = new HorizontalScrollView(this);
        tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(choices);
        for (String category : new String[]{"Míos", "Clásicos", "ROM hacks", "Gratis", "Android"}) {
            Button b = button((tab.equals(category) ? "● " : "") + category, tab.equals(category),
                    () -> { tab = category; filter = "Todos"; searchTerm = ""; draw(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(127), dp(46));
            p.rightMargin = dp(7);
            choices.addView(b, p);
        }
        root.addView(tabs);

        switch (tab) {
            case "Clásicos": renderCatalog(root, false); break;
            case "ROM hacks": renderCatalog(root, true); break;
            case "Gratis": renderFree(root); break;
            case "Android": renderAndroid(root); break;
            default: renderLibrary(root); break;
        }
        if (keepScroll && oldY > 0) scroll.post(() -> scroll.scrollTo(0, oldY));
    }
    /** One-time, additive migration of older single-slot Pokémon document links.
     * Existing activity preferences and original URI permissions remain untouched.
     */
    private void migrateLegacyBookmarksOnce() {
        SharedPreferences legacy=getPreferences(0);
        if(legacy.getBoolean("okubi_unified_index_migrated_v1",false))return;
        for(int i=0;i<GAMES.length;i++){
            String address=legacy.getString("entry_official_"+i,null);
            if(address==null||!address.startsWith("content://"))continue;
            OkubiPokemonRepository.add(this,Uri.parse(address),GAMES[i][0],GAMES[i][1]);
        }
        for(int i=0;i<HACKS.length;i++){
            String address=legacy.getString("entry_hack_"+i,null);
            if(address==null||!address.startsWith("content://"))continue;
            OkubiPokemonRepository.add(this,Uri.parse(address),HACKS[i][0],HACKS[i][1]);
        }
        legacy.edit().putBoolean("okubi_unified_index_migrated_v1",true).apply();
    }

    private int countImportedOfficial() {
        SharedPreferences p = getPreferences(0);
        int count = 0;
        for (int i = 0; i < GAMES.length; i++)
            if (p.contains("entry_official_" + i)) count++;
        return count;
    }
    private void renderLibrary(LinearLayout root) {
        SharedPreferences p = getPreferences(0);
        String lastAsset = p.getString("last_pokemon_asset", null);
        if (lastAsset != null && OkubiPersonalLibrary.exists(this, lastAsset)) {
            OkubiPersonalLibrary.Game last = OkubiPersonalLibrary.byAsset(lastAsset);
            if (last != null) {
                LinearLayout resume = section(root, "Continuar jugando", last.title);
                actionRow(resume, button("▶ JUGAR AHORA", true,
                        () -> playIncluded(last)));
            }
        }
        String lastUri = p.getString("last_pokemon_uri", null);
        if (lastAsset == null && lastUri != null
                && p.getString("last_pokemon_system", null) != null) {
            LinearLayout resume = section(root, "Continuar jugando",
                    p.getString("last_pokemon_name", "Tu último juego"));
            String system = p.getString("last_pokemon_system", "GB");
            actionRow(resume, button("▶ JUGAR AHORA", true,
                    () -> play(Uri.parse(lastUri),
                            p.getString("last_pokemon_name", "Pokémon"), system, true)));
        }
        List<OkubiPersonalLibrary.Game> personalGames = OkubiPersonalLibrary.available(this);
        if (!personalGames.isEmpty()) {
            LinearLayout bundle = section(root, "Mis cartuchos incluidos",
                    "Tus copias de Game Boy Advance ya están dentro de esta APK personal.");
            bundle.addView(label("Puedes jugar sin importar archivos. Cada juego conserva sus partidas de forma independiente.",
                    12, SECONDARY, false));
            for (OkubiPersonalLibrary.Game game : personalGames) {
                LinearLayout card = section(root, game.title, "GBA · Incluido en esta APK · mGBA");
                actionRow(card, button("▶ JUGAR AHORA", true,
                        () -> playIncluded(game)));
            }
        }
        actionRow(root, button("Importar ROM o ZIP (varios)", true, this::importSeveral));
        root.addView(label("GB/GBC usan SameBoy y GBA usa mGBA. SNES, NES, Mega Drive, Master System, Game Gear, PC Engine y Atari 2600 son archivos propios y requieren emuladores externos; sus partidas pertenecen a esas apps. Extrae primero ZIP/7z/RAR clásicos. DS y N64 también son externos.",
                12, SECONDARY, false));
        List<OkubiPokemonRepository.Item> items = OkubiPokemonRepository.list(this);
        int count = personalGames.size();
        for (OkubiPokemonRepository.Item game : items) {
            if (!includes(game.title)) continue;
            count++;
            LinearLayout panel = section(root, game.title,
                    game.system + " · " + OkubiRomFormats.status(game.system) +
                    (game.favorite ? "   ★ Favorito" : "") +
                    (game.lastPlayed > 0 ? "   · Jugado" : "   · Importado"));
            actionRow(panel,
                    button(("GB".equals(game.system) || "GBC".equals(game.system)
                                    || ("SNES".equals(game.system)
                                            && SameBoyNative.isSnesAvailable())
                                    || ("GBA".equals(game.system) &&
                                            SameBoyNative.isGbaAvailable()))
                                    ? "▶ JUGAR" :
                            (RetroExpansionFormats.isExternalClassic(game.system)
                                    || "NDS".equals(game.system) || "N64".equals(game.system)
                                    || ("GBA".equals(game.system) &&
                                            !SameBoyNative.isGbaAvailable()))
                                    ? "ABRIR EMULADOR" : "VER COMPATIBILIDAD", true,
                            () -> play(Uri.parse(game.uri), game.title, game.system, true)),
                    button(game.favorite ? "★" : "☆", false, () -> {
                        OkubiPokemonRepository.setFavorite(this, game.uri, !game.favorite);
                        draw(true);
                    }),
                    button("Quitar", false, () -> new AlertDialog.Builder(this)
                            .setTitle("Quitar de la biblioteca")
                            .setMessage("No se borra el archivo original ni las partidas guardadas.")
                            .setPositiveButton("Quitar", (d, w) -> {
                                OkubiPokemonRepository.remove(this, game.uri);
                                draw(true);
                            })
                            .setNegativeButton("Cancelar", null).show()));
        }
        java.util.Set<String> indexedUris = OkubiPokemonRepository.knownUris(this);
        for (int i = 0; i < GAMES.length; i++) {
            String address = p.getString("entry_official_" + i, null);
            if (address == null || indexedUris.contains(address)
                    || !includes(GAMES[i][0])) continue;
            count++;
            final int index = i;
            String[] game = GAMES[i];
            LinearLayout panel = section(root, game[0], game[1] + " · " + game[2]);
            actionRow(panel,
                    button(("GB".equals(game[1]) || "GBC".equals(game[1])
                                    || ("GBA".equals(game[1]) && SameBoyNative.isGbaAvailable()))
                                    ? "▶ JUGAR" : "ABRIR", true,
                            () -> play(Uri.parse(address), game[0], game[1], true)),
                    button("Cambiar", false, () -> importSingle(index, false)));
        }
        if (count == 0) {
            LinearLayout empty = section(root, "Todavía no hay archivos",
                    "Usa Importar varios para añadir ROMs propias o entra en Gratis para probar demos originales.");
            actionRow(empty, button("Ver juegos gratis", true,
                    () -> { tab = "Gratis"; draw(); }));
        }
    }

    private void renderCatalog(LinearLayout root, boolean hacks) {
        String[][] list = hacks ? HACKS : GAMES;
        root.addView(label(hacks ? "ROM hacks · parches" : "Catálogo de juegos Pokémon",
                20, WHITE, true));
        root.addView(label(hacks
                ? "Aplica parches IPS o BPS sobre tu ROM base original dentro de OKUBI. Se genera otro archivo; UPS requiere un parcheador externo."
                : "Los juegos oficiales se importan desde archivos que tengas derecho a utilizar; no se distribuyen copias comerciales.",
                12, SECONDARY, false));
        if (hacks) {
            actionRow(root, button("Aplicar parche IPS / BPS a mi copia", true,
                    this::beginPatch));
            LinearLayout sources = section(root, "Explorar más ROM hacks",
                    "Catálogos externos. Verifica permisos, versiones y parches; " +
                    "OKUBI no descarga ni distribuye juegos comerciales.");
            actionRow(sources, button("↓ Descargar parches .bps directamente", true,
                    () -> startActivity(new Intent(this, OkubiCatalogActivity.class))));
            actionRow(sources, button("Whack a Hack  ↗", false,
                    () -> openWeb("https://whackahack.com/juegos/")));
            actionRow(sources, button("PokéMundo GBA  ↗", false,
                    () -> openWeb("https://pokemundo.com/category/hack-roms-pokemon-gba/")));
            actionRow(sources, button("Ver todas las fuentes de Nintendo", true,
                    () -> startActivity(new Intent(this, OkubiCatalogActivity.class))));
        }
        space(root, 8);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);
        for (String option : new String[]{"Todos", "GB", "GBC", "GBA", "NDS", "3DS", "Switch"}) {
            if (hacks && !(option.equals("Todos") || option.equals("GBA")
                    || option.equals("GBC"))) continue;
            Button chip = button((filter.equals(option) ? "● " : "") + option, filter.equals(option),
                    () -> { filter = option; draw(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(88), dp(43));
            p.rightMargin = dp(6);
            row.addView(chip, p);
        }
        root.addView(scroll);

        SharedPreferences prefs = getPreferences(0);
        for (int i = 0; i < list.length; i++) {
            String[] game = list[i];
            if (!filter.equals("Todos") && !filter.equals(game[1])) continue;
            if (!includes(game[0])) continue;
            final int index = i;
            String address = prefs.getString("entry_" + (hacks ? "hack_" : "official_") + i, null);
            boolean embedded = "GB".equals(game[1]) || "GBC".equals(game[1])
                    || ("GBA".equals(game[1]) && SameBoyNative.isGbaAvailable());
            String state = hacks ? "Parche · no es ROM ejecutable" :
                    embedded ? "Jugable en OKUBI al importar" :
                            ("GBA".equals(game[1]) || "NDS".equals(game[1]))
                                    ? "Requiere emulador externo" : "Solo catálogo · no compatible internamente";
            LinearLayout panel = section(root, game[0], game[1] + " · " + game[2] +
                    "\n" + (address == null ? state : "✓ Archivo vinculado · " + state));
            if (address != null && !hacks &&
                    !("3DS".equals(game[1]) || "Switch".equals(game[1]))) {
                actionRow(panel, button(embedded ? "▶ JUGAR EN OKUBI" : "ABRIR EMULADOR", true,
                        () -> play(Uri.parse(address), game[0], game[1], true)));
            }
            if (hacks) {
                LinearLayout.LayoutParams sourceAction = new LinearLayout.LayoutParams(-1, dp(46));
                sourceAction.topMargin = dp(8);
                panel.addView(button("Ver parche del creador ↗", false,
                        () -> openWeb(HACK_PATCH_PAGES[index])), sourceAction);
            }
            if (hacks || !("3DS".equals(game[1]) || "Switch".equals(game[1]))) {
                actionRow(panel, button(hacks ? "Aplicar parche con mi copia" :
                        address == null ? "+ Importar juego" : "Cambiar archivo",
                        false, () -> {
                            if (hacks) beginPatch(); else importSingle(index, false);
                        }));
            }
        }
    }

    private void renderFree(LinearLayout root) {
        root.addView(label("Aventuras de criaturas · demos gratis", 20, WHITE, true));
        root.addView(label("Estas creaciones son juegos originales de otros autores. " +
                "Si ya descargaste la ROM desde el navegador, selecciónala una vez: " +
                "OKUBI guardará el vínculo y cambiará DESCARGAR por JUGAR. " +
                "Algunos creadores piden aporte opcional o pago.", 12, SECONDARY, false));

        for (String[] indie : FREE_MONSTER_GAMES) {
            LinearLayout card = section(root, indie[0], indie[2]);
            OkubiIndieLibrary.Linked linked =
                    OkubiIndieLibrary.saved(this, indie[3], indie[0]);
            card.addView(label(linked == null
                    ? "Sin archivo vinculado. Abre la página del autor y luego elige tu ROM."
                    : "✓ Archivo disponible en OKUBI · " + linked.system,
                    12, SECONDARY, false));
            Button main = button(linked == null ? "↗ DESCARGAR DEL CREADOR"
                    : "▶ JUGAR EN OKUBI", true, () -> {
                if (OkubiIndieLibrary.saved(this, indie[3], indie[0]) != null)
                    OkubiIndieLibrary.play(this, indie[3], indie[0]);
                else openWeb(indie[3]);
            });
            card.addView(main,new LinearLayout.LayoutParams(-1,dp(48)));
            Button bind = button(linked == null ? "＋ VINCULAR ROM DESCARGADA"
                    : "↻ CAMBIAR ROM VINCULADA",false,()->chooseFreeIndieFile(indie));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
            lp.topMargin=dp(8);
            card.addView(bind,lp);
        }
        LinearLayout more = section(root, "Más juegos homebrew",
                "Explora otras creaciones legalmente distribuidas para Game Boy.");
        actionRow(more, button("Abrir Descubre Retro", false,
                () -> startActivity(new Intent(this, OkubiCatalogActivity.class))));
    }

    private void chooseFreeIndieFile(String[] indie) {
        pendingFreeIndieSource = indie[3];
        Intent chooser = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        chooser.addCategory(Intent.CATEGORY_OPENABLE);
        chooser.setType("*/*");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(chooser, REQUEST_LINK_FREE_INDIE);
        } catch (Exception error) {
            pendingFreeIndieSource = null;
            Toast.makeText(this, "No se pudo seleccionar el archivo",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void renderAndroid(LinearLayout root) {
        root.addView(label("Pokémon oficiales en Android", 20, WHITE, true));
        root.addView(label("Si el juego ya está instalado, OKUBI intenta abrirlo; si no, abre su ficha oficial en Google Play. Disponibilidad sujeta a país y dispositivo.",
                12, SECONDARY, false));
        for (String[] game : ANDROID_GAMES) {
            LinearLayout card = section(root, game[0], game[1] + " · Aplicación oficial Android");
            actionRow(card, button("Abrir o instalar", true,
                    () -> openOfficialAndroid(game[2])));
        }
    }

    private void openWeb(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la página del creador", Toast.LENGTH_LONG).show();
        }
    }
    private void openOfficialAndroid(String packageName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) {
                startActivity(launch);
                return;
            }
        } catch (Exception ignored) {}
        openWeb("https://play.google.com/store/apps/details?id=" + packageName);
    }

    private void playIncluded(OkubiPersonalLibrary.Game game) {
        if (game == null || !OkubiPersonalLibrary.exists(this, game.asset)) {
            Toast.makeText(this, "La copia no está incluida en esta instalación",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!SameBoyNative.isGbaAvailable()) {
            Toast.makeText(this, "El motor mGBA no está disponible en este teléfono",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent launch = new Intent(this, OkubiGameBoyActivity.class);
        launch.putExtra(OkubiPersonalLibrary.EXTRA_ASSET, game.asset);
        launch.putExtra(OkubiGameBoyActivity.EXTRA_NAME, game.title);
        launch.putExtra(OkubiGameBoyActivity.EXTRA_SYSTEM, "GBA");
        startActivity(launch);
        getPreferences(0).edit()
                .remove("last_pokemon_uri")
                .putString("last_pokemon_asset", game.asset)
                .putString("last_pokemon_name", game.title)
                .putString("last_pokemon_system", "GBA")
                .apply();
    }

    private void play(Uri uri, String title, String system, boolean remember) {
        if (uri == null || !"content".equals(uri.getScheme())) {
            Toast.makeText(this, "Vuelve a importar el archivo: acceso no válido", Toast.LENGTH_LONG).show();
            return;
        }
        boolean hasInternal = "SNES".equals(system)
                ? SameBoyNative.isSnesAvailable()
                : ("GB".equals(system) || "GBC".equals(system))
                ? SameBoyNative.isAvailable()
                : "GBA".equals(system) && SameBoyNative.isGbaAvailable();
        if (hasInternal) {
            try {
                Intent launch = new Intent(this, OkubiGameBoyActivity.class);
                launch.setData(uri);
                launch.putExtra(OkubiGameBoyActivity.EXTRA_ROM, uri);
                launch.putExtra(OkubiGameBoyActivity.EXTRA_NAME, title);
                launch.putExtra(OkubiGameBoyActivity.EXTRA_SYSTEM, system);
                launch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(launch);
            } catch (SecurityException | IllegalArgumentException error) {
                Toast.makeText(this, "El archivo ya no está disponible: vuelve a importarlo",
                        Toast.LENGTH_LONG).show();
                return;
            }
        } else if (RetroExpansionFormats.isExternalClassic(system)
                || "GBA".equals(system) || "NDS".equals(system)
                || "N64".equals(system) || "GB".equals(system) || "GBC".equals(system)) {
            RetroExternalLauncher.open(this, uri, system);
        } else {
            Toast.makeText(this, "Solo biblioteca: no hay emulador configurado para " + system, Toast.LENGTH_LONG).show();
            return;
        }
        // An external chooser can be cancelled; only successful internal
        // starts can be recorded as recent game launches.
        if (remember && hasInternal) {
            getPreferences(0).edit()
                    .remove("last_pokemon_asset")
                    .putString("last_pokemon_uri", uri.toString())
                    .putString("last_pokemon_name", title)
                    .putString("last_pokemon_system", system).apply();
            OkubiPokemonRepository.markPlayed(this, uri.toString());
        }
    }

    private void showSearch() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Nombre del juego");
        field.setText(searchTerm);
        new AlertDialog.Builder(this)
                .setTitle("Buscar en " + tab)
                .setView(field)
                .setPositiveButton("Buscar", (d, w) -> {
                    searchTerm = field.getText().toString().trim();
                    draw();
                })
                .setNeutralButton("Limpiar", (d, w) -> { searchTerm = ""; draw(); })
                .setNegativeButton("Cancelar", null).show();
    }
    private void beginPatch() {
        patchBase = null;
        patchDocument = null;
        patchSystem = null;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Toast.makeText(this, "Elige tu ROM base .gb, .gbc o .gba sin modificar",
                Toast.LENGTH_LONG).show();
        try { startActivityForResult(intent, REQUEST_PATCH_BASE); }
        catch (Exception error) {
            Toast.makeText(this, "No se pudo elegir la ROM base", Toast.LENGTH_LONG).show();
        }
    }
    private void choosePatch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Toast.makeText(this, "Ahora selecciona el parche .ips o .bps del creador",
                Toast.LENGTH_LONG).show();
        try { startActivityForResult(intent, REQUEST_PATCH_FILE); }
        catch (Exception error) {
            Toast.makeText(this, "No se pudo elegir el parche", Toast.LENGTH_LONG).show();
        }
    }
    private void choosePatchOutput() {
        if (patchBase == null || patchDocument == null || patchSystem == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, patchResultName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_PATCH_OUTPUT); }
        catch (Exception error) {
            Toast.makeText(this, "No se pudo elegir dónde guardar la ROM parcheada",
                    Toast.LENGTH_LONG).show();
        }
    }
    private static byte[] readWithLimit(InputStream source, int limit)
            throws java.io.IOException {
        if (source == null) throw new java.io.IOException("No se puede leer el archivo");
        try (InputStream in = source; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count > limit - out.size())
                    throw new java.io.IOException("Archivo demasiado grande");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }
    private void writePatchedRom(Uri output) {
        Uri base = patchBase;
        Uri patch = patchDocument;
        String console = patchSystem;
        String name = patchResultName;
        if (base == null || patch == null || console == null || name == null) return;
        int serial = ++importSerial;
        try {
            fileIo.execute(() -> {
                String feedback;
                boolean success = false;
                try {
                    byte[] original = readWithLimit(getContentResolver().openInputStream(base),
                            OkubiPatchEngine.MAX_OUTPUT);
                    byte[] delta = readWithLimit(getContentResolver().openInputStream(patch),
                            OkubiPatchEngine.MAX_PATCH);
                    byte[] modified = OkubiPatchEngine.apply(original, delta);
                    if (("GB".equals(console) || "GBC".equals(console))
                            && modified.length > OkubiRomFormats.MAX_GAME_BOY)
                        throw new java.io.IOException("El resultado excede el tamaño GB/GBC");
                    try (OutputStream stream = getContentResolver().openOutputStream(output, "wt")) {
                        if (stream == null) throw new java.io.IOException("Salida no disponible");
                        stream.write(modified);
                        stream.flush();
                    }
                    success = OkubiPokemonRepository.add(this, output, name, console);
                    feedback = success ? "Parche aplicado. Juego añadido a tu biblioteca." :
                            "Parche aplicado. El archivo ya estaba registrado.";
                } catch (Exception error) {
                    feedback = "No se pudo aplicar el parche: " + error.getMessage();
                }
                final String message = feedback;
                final boolean registered = success;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || serial != importSerial) return;
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (registered) { tab = "Míos"; draw(); }
                });
            });
        } catch (RejectedExecutionException ignored) {
            Toast.makeText(this, "El parcheador no está disponible", Toast.LENGTH_LONG).show();
        }
    }

    private void importSingle(int index, boolean hack) {
        importingIndex = index;
        importingHack = hack;
        Intent file = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        file.addCategory(Intent.CATEGORY_OPENABLE);
        file.setType("*/*");
        file.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(file, REQUEST_IMPORT); }
        catch (Exception error) {
            Toast.makeText(this, "No se pudo abrir el selector", Toast.LENGTH_LONG).show();
        }
    }
    private void importSeveral() {
        importingIndex = -1;
        Intent files = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        files.addCategory(Intent.CATEGORY_OPENABLE);
        files.setType("*/*");
        files.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        files.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(files, REQUEST_MULTI); }
        catch (Exception error) {
            Toast.makeText(this, "No se pudo abrir el selector", Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (Exception ignored) {}
        return "Archivo sin nombre";
    }
    private void retainPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Providers without persistable grants require reimport after restart.
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_LINK_FREE_INDIE) {
            String creator = pendingFreeIndieSource;
            pendingFreeIndieSource = null;
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            String[] selected = null;
            for (String[] indie : FREE_MONSTER_GAMES)
                if (indie[3].equals(creator)) { selected = indie; break; }
            if (selected == null) return;
            try {
                OkubiIndieLibrary.link(this, selected[3], selected[0],
                        selected[1], data.getData());
                Toast.makeText(this, "Juego vinculado y añadido a tu biblioteca",
                        Toast.LENGTH_LONG).show();
            } catch (java.io.IOException | SecurityException error) {
                Toast.makeText(this, "No se pudo vincular: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            draw(true);
            return;
        }
        if (result != RESULT_OK || data == null) return;
        if (request == REQUEST_PATCH_BASE && data.getData() != null) {
            Uri selected = data.getData();
            String sourceName = displayName(selected);
            patchSystem = OkubiRomFormats.systemFromName(sourceName);
            if (!("GB".equals(patchSystem) || "GBC".equals(patchSystem)
                    || "GBA".equals(patchSystem))) {
                Toast.makeText(this, "Usa una ROM base .gb, .gbc o .gba sin comprimir",
                        Toast.LENGTH_LONG).show();
                patchSystem = null;
                return;
            }
            patchBase = selected;
            retainPermission(selected);
            String clean = sourceName.replaceAll("[^\\p{L}\\p{N}._-]", "_");
            if (clean.length() > 70) clean = clean.substring(clean.length() - 70);
            patchResultName = "OKUBI-patched-" + clean;
            choosePatch();
            return;
        }
        if (request == REQUEST_PATCH_FILE && data.getData() != null) {
            Uri selected = data.getData();
            String name = displayName(selected).toLowerCase(Locale.ROOT);
            if (!name.endsWith(".ips") && !name.endsWith(".bps")) {
                Toast.makeText(this, "OKUBI solo puede aplicar parches .ips y .bps",
                        Toast.LENGTH_LONG).show();
                return;
            }
            patchDocument = selected;
            retainPermission(selected);
            choosePatchOutput();
            return;
        }
        if (request == REQUEST_PATCH_OUTPUT && data.getData() != null) {
            Uri output = data.getData();
            retainPermission(output);
            writePatchedRom(output);
            return;
        }
        if (request == REQUEST_IMPORT) {
            if (data.getData() == null || importingIndex < 0) return;
            Uri uri = data.getData();
            retainPermission(uri);
            int index = importingIndex;
            boolean hack = importingHack;
            importingIndex = -1;
            String system = (hack ? HACKS : GAMES)[index][1];
            int serial = ++importSerial;
            try {
                fileIo.execute(() -> {
                    RetroFileInspector.Result check =
                            RetroFileInspector.inspect(getContentResolver(), uri, system, hack);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || serial != importSerial) return;
                        if (!check.accepted) {
                            Toast.makeText(this, check.reason, Toast.LENGTH_LONG).show();
                            return;
                        }
                        getPreferences(0).edit().putString(
                                "entry_" + (hack ? "hack_" : "official_") + index,
                                uri.toString()).apply();
                        // Mirror the same SAF document into the unified index;
                        // this never copies game bytes or deletes the old slot.
                        OkubiPokemonRepository.add(this,uri,
                                (hack ? HACKS : GAMES)[index][0],system);
                        Toast.makeText(this, "Archivo agregado a tu colección",
                                Toast.LENGTH_SHORT).show();
                        draw(true);
                    });
                });
            } catch (RejectedExecutionException ignored) {}
        } else if (request == REQUEST_MULTI) {
            ArrayList<Uri> selected = new ArrayList<>();
            ClipData clips = data.getClipData();
            if (clips != null) {
                for (int i = 0; i < Math.min(clips.getItemCount(), 30); i++) {
                    Uri uri = clips.getItemAt(i).getUri();
                    if (uri != null && !selected.contains(uri)) selected.add(uri);
                }
            } else if (data.getData() != null) selected.add(data.getData());
            if (selected.isEmpty()) return;
            for (Uri uri : selected) retainPermission(uri);
            int serial = ++importSerial;
            try {
                fileIo.execute(() -> {
                    int added = 0, rejected = 0;
                    for (Uri uri : selected) {
                        if (Thread.currentThread().isInterrupted()) break;
                        String name = displayName(uri);
                        try {
                            OkubiRomFormats.Detected detected =
                                    OkubiRomFormats.detect(getContentResolver(), uri);
                            String system = detected.system;
                            RetroFileInspector.Result check =
                                    RetroFileInspector.inspect(getContentResolver(), uri, system, false);
                            if (!check.accepted) { rejected++; continue; }
                            String title = OkubiRomFormats.isZip(name)
                                    ? detected.innerName + "  [ZIP]" : name;
                            if (OkubiPokemonRepository.add(this, uri, title, system)) added++;
                        } catch (java.io.IOException error) {
                            rejected++;
                        }
                    }
                    final int success = added, bad = rejected;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || serial != importSerial) return;
                        Toast.makeText(this,
                                success + " juegos añadidos; " + bad + " archivos no compatibles",
                                Toast.LENGTH_LONG).show();
                        tab = "Míos";
                        draw();
                    });
                });
            } catch (RejectedExecutionException ignored) {
                Toast.makeText(this, "No se pudo importar la selección",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
