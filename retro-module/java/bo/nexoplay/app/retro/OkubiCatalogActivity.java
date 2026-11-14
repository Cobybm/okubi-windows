package bo.nexoplay.app.retro;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Curated legal homebrew catalogue. Does not bundle ROMs.
 * A direct URL is used only where the author has linked their ROM publicly.
 * Downloaded GB/GBC/GBA homebrew can be played with SameBoy/mGBA when present.
 * Downloads from external browsers are linked only after an explicit SAF pick.
 */
public class OkubiCatalogActivity extends Activity {
    private final ExecutorService downloadQueue = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private boolean directDownloading;
    private volatile long directDownloadedBytes;
    private volatile long directTotalBytes = -1;
    private String directLastError;
    private static final int REQUEST_LINK_INDIE = 4181;
    private String pendingCreatorUrl, pendingCreatorTitle;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override public void run() {
            if (closed || isFinishing()) return;
            refreshDownloadActions();
            startProgressTicker();
        }
    };
    private TextView centerSummary, centerDirectStatus, centerExternalStatus;
    private Button centerDirectAction;
    private final Map<DirectPatch,TextView> centerPatchStatuses = new IdentityHashMap<>();
    private final Map<DirectPatch,Button> centerPatchActions = new IdentityHashMap<>();
    private final Map<Game, Button> indieActions = new IdentityHashMap<>();
    private final Map<Game, Button> indieLinkActions = new IdentityHashMap<>();
    private final Map<Game, TextView> indieStatuses = new IdentityHashMap<>();
    private final Map<DirectPatch, Button> patchActions = new IdentityHashMap<>();

    @Override protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString("pending_indie_source", pendingCreatorUrl);
        bundle.putString("pending_indie_title", pendingCreatorTitle);
    }
    @Override protected void onResume() {
        super.onResume();
        refreshDownloadActions();
        startProgressTicker();
    }
    @Override protected void onPause() {
        progressHandler.removeCallbacks(progressTicker);
        super.onPause();
    }
    @Override protected void onDestroy() {
        closed = true;
        progressHandler.removeCallbacks(progressTicker);
        downloadQueue.shutdownNow();
        super.onDestroy();
    }

    private static class Game {
        final String name, system, genre, url;
        final boolean direct;
        Game(String n, String s, String g, String u, boolean d) {
            name=n; system=s; genre=g; url=u; direct=d;
        }
    }
    private static final Game[] GAMES = {
        new Game("Ascension", "GB", "Plataformas • ROM original del autor",
            "https://raw.githubusercontent.com/wolandark/Ascension/master/build/rom/game.gb", true),
        new Game("Punch-land", "GB / GBC", "Acción y plataformas",
            "https://monarchwisteria.itch.io/punch-land", false),
        new Game("Lunar Lancer", "GB", "Disparos arcade",
            "https://the-green-screen.com/", false),
        new Game("Retroid", "GB", "Rompebloques",
            "https://the-green-screen.com/", false),
        new Game("Into the Blue", "GB", "Puzles",
            "https://the-green-screen.com/", false),
        new Game("Ember Banner", "GBC", "RPG táctico (alfa)",
            "https://emberbannerdev.itch.io/ember-banner", false),
        new Game("Hang-On GB", "GB", "Carreras • adaptación hecha por fans",
            "https://plainlazy2097.itch.io/hang-on-gb", false),
        new Game("Nitro Adventure", "GB", "Aventura",
            "https://kyleandligames.itch.io/nitro-rad-venture-gameboy", false),
        new Game("Tobu Tobu Girl Deluxe", "GB / GBC", "Plataformas • código abierto",
            "https://github.com/SimonLarsen/tobutobugirl-dx", false),
        new Game("Homebrew Hub", "GB / GBC / GBA", "Explorar más creaciones",
            "https://hh.gbdev.io/", false)
    };
    // User-selected external sites: directory links only, never automatic ROM downloads.
    private static final String[][] NINTENDO_SOURCES = {
        {
            "Whack a Hack!",
            "Fangames y ROM hacks · GB / GBC / GBA / NDS / RPG Maker",
            "Catálogo de proyectos de sus autores, con filtros e información de versiones.",
            "https://whackahack.com/juegos/"
        },
        {
            "PokéMundo",
            "ROM hacks Pokémon · GBA",
            "Índice de proyectos de fans. Comprueba si se ofrece un parche y las condiciones del autor.",
            "https://pokemundo.com/category/hack-roms-pokemon-gba/"
        },
        {
            "Full GBA ROMs",
            "Índice externo · Pokémon GBA",
            "Enlaces de terceros a juegos comerciales. OKUBI no verifica sus permisos ni descarga archivos de esta página.",
            "https://fullgbaroms.blogspot.com/p/roms-de-pokemon-para-gba-gameboy-avance.html"
        }
    };

    /**
     * Official author-published PATCH files (no pre-patched ROMs), each reviewed
     * against the author's public release metadata. Pinned tags avoid silently
     * swapping the binary downloaded by the user's tap.
     */
    private static final class DirectPatch {
        final String title, description, url, fileName, source;
        DirectPatch(String title, String description, String url, String fileName,
                    String source) {
            this.title = title;
            this.description = description;
            this.url = url;
            this.fileName = fileName;
            this.source = source;
        }
    }

    private static final DirectPatch[] VERIFIED_PATCHES = {
        new DirectPatch("Radical Red · Español · v0.992",
            "Parche BPS del traductor. Requiere FireRed USA 1.0 (CRC32 DD88761C), " +
            "NO Rojo Fuego español.",
            "https://github.com/SeCaVa/RadicalRed-TraduccionCastellano/releases/download/v0.992/RadicalRed_ES_v0.992.bps",
            "OKUBI-RadicalRed-ES-v0.992.bps",
            "https://github.com/SeCaVa/RadicalRed-TraduccionCastellano/releases/tag/v0.992"),
        new DirectPatch("Emerald Rogue · Español · v0.66",
            "Parche BPS del traductor. Requiere Emerald USA (CRC32 1F1C08FB), " +
            "NO Esmeralda español.",
            "https://github.com/SeCaVa/EmeraldRogue-Castellano/releases/download/v0.66/emeraldrogue_ex_es.bps",
            "OKUBI-EmeraldRogue-ES-v0.66.bps",
            "https://github.com/SeCaVa/EmeraldRogue-Castellano/releases/tag/v0.66"),
        new DirectPatch("Ghost Grey · Español · v0.992",
            "Parche BPS del traductor. Requiere FireRed USA 1.0 (CRC32 DD88761C), " +
            "NO Rojo Fuego español.",
            "https://github.com/SeCaVa/GhostGrey-Traduccion/releases/download/v0.992/GhostGrey_ES.bps",
            "OKUBI-GhostGrey-ES-v0.992.bps",
            "https://github.com/SeCaVa/GhostGrey-Traduccion/releases/tag/v0.992")
    };

    private void showDownloadCenter(LinearLayout root) {
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(16),dp(15),dp(16),dp(15));
        GradientDrawable panel = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF28203E,0xFF181328});
        panel.setCornerRadius(dp(20));
        panel.setStroke(dp(1),0xFF6A518B);
        center.setBackground(panel);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(15);
        params.bottomMargin=dp(16);
        root.addView(center,params);
        center.addView(label("MIS DESCARGAS  /  OKUBI",19));
        centerSummary=label("",12);
        centerSummary.setTextColor(0xFFDDD2EC);
        center.addView(centerSummary);

        TextView romName=label("ASCENSION · JUEGO INDEPENDIENTE · GB",15);
        center.addView(romName);
        centerDirectStatus=label("",12);
        centerDirectStatus.setTextColor(0xFFFFD0E7);
        center.addView(centerDirectStatus);
        centerDirectAction=new Button(this);
        centerDirectAction.setAllCaps(false);
        centerDirectAction.setOnClickListener(v->{
            Game direct=GAMES[0];
            if(verifiedDirectGame(direct))playDirectGame(direct);
            else if(!directDownloading)download(direct);
        });
        center.addView(centerDirectAction);

        for(DirectPatch patch:VERIFIED_PATCHES){
            center.addView(label(patch.title+" · PARCHE BPS",14));
            TextView progress=label("",12);
            progress.setTextColor(0xFFDACDDE);
            center.addView(progress);
            centerPatchStatuses.put(patch,progress);
            Button action=new Button(this);
            action.setAllCaps(false);
            action.setOnClickListener(v->{
                if(verifiedPatchStatus(patch)==DownloadManager.STATUS_SUCCESSFUL){
                    openPatcher();
                }else if(!isPatchPending(patch)){
                    enqueueVerifiedPatch(patch);
                    refreshDownloadActions();
                    startProgressTicker();
                }
            });
            centerPatchActions.put(patch,action);
            center.addView(action);
        }

        centerExternalStatus=label("",12);
        centerExternalStatus.setTextColor(0xFFCEC2D9);
        center.addView(centerExternalStatus);
        Button androidDownloads=new Button(this);
        androidDownloads.setAllCaps(false);
        androidDownloads.setText("↗ ABRIR DESCARGAS DE ANDROID");
        androidDownloads.setOnClickListener(v->{
            try {startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}
            catch(Exception error){
                Toast.makeText(this,"Abre la app Archivos → Descargas",
                        Toast.LENGTH_LONG).show();
            }
        });
        center.addView(androidDownloads);
        center.addView(label("Las descargas hechas en tu navegador solo aparecen aquí " +
                "tras vincular su ROM mediante el selector de Android. Los parches " +
                ".bps no son juegos ni se ejecutan directamente.",11));
        refreshDownloadCenter();
    }

    private static final class PatchSnapshot {
        final int status;
        final long done,total;
        final boolean readable;
        PatchSnapshot(int status,long done,long total,boolean readable){
            this.status=status; this.done=done; this.total=total; this.readable=readable;
        }
    }

    private PatchSnapshot patchSnapshot(DirectPatch patch) {
        long id=getPreferences(MODE_PRIVATE).getLong("patch_id_"+patch.fileName,-1);
        if(id<=0)return new PatchSnapshot(OkubiDownloadProgress.MISSING,0,-1,false);
        DownloadManager manager=
                (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        if(manager==null)return new PatchSnapshot(OkubiDownloadProgress.MISSING,0,-1,false);
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){
            if(cursor==null||!cursor.moveToFirst())
                return new PatchSnapshot(OkubiDownloadProgress.MISSING,0,-1,false);
            int state=cursor.getInt(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_STATUS));
            long done=cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total=cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            boolean available=false;
            if(state==DownloadManager.STATUS_SUCCESSFUL){
                try(android.os.ParcelFileDescriptor file=manager.openDownloadedFile(id)){
                    available=file!=null && file.getStatSize()!=0;
                }catch(IOException|SecurityException error){
                    available=false;
                }
            }
            return new PatchSnapshot(state,done,total,available);
        }catch(Exception error){
            return new PatchSnapshot(OkubiDownloadProgress.MISSING,0,-1,false);
        }
    }

    private void refreshDownloadCenter(){
        if(centerSummary==null)return;
        Game direct=GAMES[0];
        boolean downloaded=verifiedDirectGame(direct);
        int directState=directDownloading?OkubiDownloadProgress.RUNNING:
                downloaded?OkubiDownloadProgress.COMPLETE:
                directLastError!=null?OkubiDownloadProgress.FAILED:
                OkubiDownloadProgress.MISSING;
        centerDirectStatus.setText(OkubiDownloadProgress.message(
                directState,directDownloadedBytes,directTotalBytes,downloaded)
                +(directState==OkubiDownloadProgress.FAILED
                   ?" · "+directLastError:""));
        centerDirectAction.setText(OkubiDownloadProgress.action(
                directState,downloaded,false));
        centerDirectAction.setEnabled(!directDownloading);
        int patchesReady=0,patchesActive=0,linked=0;
        for(DirectPatch patch:VERIFIED_PATCHES){
            PatchSnapshot snapshot=patchSnapshot(patch);
            if(snapshot.status==OkubiDownloadProgress.COMPLETE && snapshot.readable)
                patchesReady++;
            if(OkubiDownloadProgress.active(snapshot.status))patchesActive++;
            TextView info=centerPatchStatuses.get(patch);
            Button action=centerPatchActions.get(patch);
            if(info!=null)info.setText(OkubiDownloadProgress.message(
                    snapshot.status,snapshot.done,snapshot.total,snapshot.readable));
            if(action!=null){
                action.setText(OkubiDownloadProgress.action(snapshot.status,
                        snapshot.readable,true));
                action.setEnabled(!OkubiDownloadProgress.active(snapshot.status));
            }
        }
        StringBuilder known=new StringBuilder();
        for(Game game:GAMES){
            if(game.direct)continue;
            if(OkubiIndieLibrary.saved(this,game.url,game.name)!=null){
                linked++;
                if(known.length()>0)known.append(" · ");
                known.append(game.name);
            }
        }
        centerSummary.setText("1 juego directo · "+VERIFIED_PATCHES.length+
                " parches disponibles · "+linked+" indies vinculados"+
                (patchesActive>0?" · "+patchesActive+" descarga(s) activa(s)":""));
        centerExternalStatus.setText(linked==0
                ?"Juegos del navegador: ninguno vinculado todavía. Elige "+
                 "«Vincular archivo descargado» en las fichas de abajo."
                :"✓ JUEGOS VINCULADOS · "+known.toString());
    }

    private void startProgressTicker(){
        progressHandler.removeCallbacks(progressTicker);
        if(closed||isFinishing())return;
        boolean any=directDownloading;
        if(!any)for(DirectPatch patch:VERIFIED_PATCHES)
            if(isPatchPending(patch)){any=true;break;}
        if(any)progressHandler.postDelayed(progressTicker,1500);
    }

    private void showDirectDownloads(LinearLayout root) {
        root.addView(label("DESCARGA DIRECTA  /  PARCHES VERIFICADOS", 19));
        TextView explanation = label(
                "Un toque guarda el archivo .bps en Descargas, sin abrir un navegador. " +
                "Estos archivos no son juegos completos: usa el parcheador de OKUBI " +
                "con una ROM original de la región y versión indicadas. " +
                "Tus copias españolas NO coinciden con las bases USA requeridas.", 12);
        explanation.setTextColor(0xFFDACDDE);
        root.addView(explanation);

        for (DirectPatch patch : VERIFIED_PATCHES) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF262037, 0xFF171027});
            background.setCornerRadius(dp(18));
            background.setStroke(dp(1), 0xFF8A4375);
            panel.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(11);
            root.addView(panel, params);
            panel.addView(label(patch.title, 17));
            TextView details = label(patch.description, 12);
            details.setTextColor(0xFFCEC2D9);
            panel.addView(details);

            Button direct = new Button(this);
            direct.setAllCaps(false);
            direct.setText("↓ Descargar parche .bps");
            direct.setOnClickListener(v -> {
                if (verifiedPatchStatus(patch) == DownloadManager.STATUS_SUCCESSFUL) {
                    openPatcher();
                } else if (!isPatchPending(patch)) {
                    enqueueVerifiedPatch(patch);
                    updatePatchAction(patch);
                }
            });
            patchActions.put(patch, direct);
            panel.addView(direct);

            Button website = new Button(this);
            website.setAllCaps(false);
            website.setText("Ver versión y autor  ↗");
            website.setOnClickListener(v -> openExternal(patch.source));
            panel.addView(website);
        }
        Button patcher = new Button(this);
        patcher.setAllCaps(false);
        patcher.setText("Aplicar un parche a mi ROM en OKUBI  →");
        patcher.setOnClickListener(v -> openPatcher());
        root.addView(patcher);
    }

    private void enqueueVerifiedPatch(DirectPatch patch) {
        // Only files explicitly included by OKUBI are accepted. Never accept
        // arbitrary web-page redirects or user-supplied commercial ROM URLs.
        boolean approved = false;
        for (DirectPatch known : VERIFIED_PATCHES)
            if (known == patch) { approved = true; break; }
        Uri uri = Uri.parse(patch.url);
        if (!approved || !"https".equals(uri.getScheme())
                || !"github.com".equals(uri.getHost())
                || !uri.getPath().startsWith("/SeCaVa/")
                || !uri.getPath().contains("/releases/download/")
                || !uri.getPath().endsWith(".bps")) {
            Toast.makeText(this, "Esta descarga no está autorizada por OKUBI",
                    Toast.LENGTH_LONG).show();
            return;
        }
        DownloadManager manager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "El gestor de descargas no está disponible",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String key = "patch_id_" + patch.fileName;
        long previousId = getPreferences(0).getLong(key, -1);
        if (previousId > 0) {
            try (Cursor cursor = manager.query(new DownloadManager.Query()
                    .setFilterById(previousId))) {
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_RUNNING
                            || status == DownloadManager.STATUS_PENDING
                            || status == DownloadManager.STATUS_PAUSED) {
                        Toast.makeText(this, "El parche ya se está descargando",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL
                            && verifiedPatchStatus(patch)
                                    == DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(this, "Este parche ya está descargado. " +
                                "Puedes aplicarlo en Pokémon → ROM hacks.",
                                Toast.LENGTH_LONG).show();
                        updatePatchAction(patch);
                        return;
                    }
                }
            } catch (Exception ignored) {
                // An obsolete system download ID should not block retries.
            }
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(patch.title);
            request.setDescription("Parche original .bps del creador · OKUBI");
            request.setMimeType("application/octet-stream");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // Android 10+ writes to public Downloads without storage permission.
            // Android 8-9 instead uses this application's external download folder.
            // Unique name also after APK reinstall, avoiding a Downloads file collision.
            String filename = patch.fileName.substring(0, patch.fileName.length() - 4)
                    + "-" + System.currentTimeMillis() + ".bps";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, filename);
            } else {
                request.setDestinationInExternalFilesDir(
                        this, Environment.DIRECTORY_DOWNLOADS, filename);
            }
            long id = manager.enqueue(request);
            getPreferences(0).edit().putLong(key, id).apply();
            refreshDownloadCenter();
            startProgressTicker();
            Toast.makeText(this, "Descargando en Android. Revisa Descargas " +
                    "y luego entra en Pokémon → ROM hacks → Aplicar parche.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "No se pudo iniciar la descarga: " +
                    error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void openPatcher() {
        Intent pokemon = new Intent(this, OkubiPokemonActivity.class);
        pokemon.putExtra(OkubiPokemonActivity.EXTRA_OPEN_PATCHES, true);
        startActivity(pokemon);
    }

    private int verifiedPatchStatus(DirectPatch patch) {
        PatchSnapshot snapshot=patchSnapshot(patch);
        return snapshot.status==DownloadManager.STATUS_SUCCESSFUL
                && !snapshot.readable ? -1 : snapshot.status;
    }

    private boolean isPatchPending(DirectPatch patch) {
        int status = verifiedPatchStatus(patch);
        return status == DownloadManager.STATUS_RUNNING
                || status == DownloadManager.STATUS_PENDING
                || status == DownloadManager.STATUS_PAUSED;
    }

    private void updatePatchAction(DirectPatch patch) {
        Button action = patchActions.get(patch);
        if (action == null) return;
        int status = verifiedPatchStatus(patch);
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            action.setText("✓ DESCARGADO · APLICAR PARCHE");
            action.setEnabled(true);
        } else if (status == DownloadManager.STATUS_PENDING
                || status == DownloadManager.STATUS_RUNNING
                || status == DownloadManager.STATUS_PAUSED) {
            action.setText("⌛ DESCARGANDO PARCHE...");
            action.setEnabled(false);
        } else {
            action.setText(status == DownloadManager.STATUS_FAILED
                    ? "↻ REINTENTAR DESCARGA" : "↓ DESCARGAR PARCHE .BPS");
            action.setEnabled(true);
        }
    }

    private void showNintendoSources(LinearLayout root) {
        root.addView(label("NINTENDO  /  FANGAMES Y ROM HACKS", 19));
        root.addView(label(
                "Sitios externos aportados por el usuario. Consulta cada proyecto y sus permisos. " +
                "OKUBI no aloja ni descarga ROMs comerciales desde estas páginas.", 12));
        for (String[] source : NINTENDO_SOURCES) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF291729, 0xFF161024});
            background.setCornerRadius(dp(18));
            background.setStroke(dp(1), 0xFF844368);
            panel.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(11);
            root.addView(panel, params);
            panel.addView(label(source[0] + "  •  EXTERNO", 17));
            TextView category = label(source[1], 12);
            category.setTextColor(0xFFFFAFC8);
            panel.addView(category);
            TextView explanation = label(source[2], 12);
            explanation.setTextColor(0xFFCEC5D4);
            panel.addView(explanation);
            Button open = new Button(this);
            open.setAllCaps(false);
            open.setText("Explorar sitio  ↗");
            open.setOnClickListener(v -> openExternal(source[3]));
            panel.addView(open);
        }
    }

    private void openExternal(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception error) {
            Toast.makeText(this, "No se pudo abrir la página externa",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            pendingCreatorUrl = state.getString("pending_indie_source");
            pendingCreatorTitle = state.getString("pending_indie_title");
        }
        getWindow().setStatusBarColor(0xFF0B0712);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0712);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(20),dp(16),dp(24));
        scroll.addView(root);
        setContentView(scroll);
        TextView header = label("OKUBI • DESCUBRE RETRO",23);
        root.addView(header);
        TextView info = label("Descarga parches gratuitos directamente a tu teléfono y explora juegos independientes. Los juegos comerciales se importan desde tus propias copias.",13);
        root.addView(info);
        Button pokemon = new Button(this);
        pokemon.setAllCaps(false);
        pokemon.setText("Colección Pokémon • Oficiales y ROM hacks");
        pokemon.setOnClickListener(v -> startActivity(new Intent(this, OkubiPokemonActivity.class)));
        root.addView(pokemon);
        showDownloadCenter(root);
        showDirectDownloads(root);
        showNintendoSources(root);
        root.addView(label("HOMEBREW  /  JUEGOS INDEPENDIENTES", 19));
        root.addView(label("Los juegos descargados dentro de OKUBI muestran JUGAR. " +
                "Si descargaste desde el navegador, vincula una sola vez tu archivo " +
                ".gb/.gbc/.gba o ZIP para abrirlo desde aquí.", 12));
        for (Game g : GAMES) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(14),dp(12),dp(14),dp(12));
            GradientDrawable card = new GradientDrawable();
            card.setColor(0xFF1E1529);
            card.setCornerRadius(dp(16));
            panel.setBackground(card);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
            p.topMargin=dp(12);
            root.addView(panel,p);
            panel.addView(label(g.name + "   •   " + g.system,18));
            panel.addView(label(g.genre,13));
            TextView status = label("", 12);
            status.setTextColor(0xFFDACDDE);
            panel.addView(status);
            indieStatuses.put(g, status);

            Button action = new Button(this);
            action.setAllCaps(false);
            indieActions.put(g, action);
            action.setOnClickListener(v -> {
                if (g.direct) {
                    if (verifiedDirectGame(g)) playDirectGame(g);
                    else if (!directDownloading) download(g);
                } else if (OkubiIndieLibrary.saved(this, g.url, g.name) != null) {
                    OkubiIndieLibrary.play(this, g.url, g.name);
                } else {
                    openExternal(g.url);
                }
            });
            panel.addView(action);

            if (!g.direct) {
                Button link = new Button(this);
                link.setAllCaps(false);
                link.setOnClickListener(v -> chooseIndieFile(g));
                panel.addView(link);
                indieLinkActions.put(g, link);
            }
            updateIndieAction(g);
        }
        refreshDownloadActions();
    }
    /** The app-owned Ascension path is private and cannot be modified by other apps. */
    private File directHomebrewFile(Game game) {
        return new File(getFilesDir(), "okubi/homebrew/ascension.gb");
    }

    private boolean verifiedDirectGame(Game game) {
        if (!game.direct) return false;
        File file = directHomebrewFile(game);
        return file.isFile() && file.length() >= 32768
                && file.length() <= 8L * 1024 * 1024;
    }

    private void playDirectGame(Game game) {
        if (!verifiedDirectGame(game)) {
            updateIndieAction(game);
            Toast.makeText(this, "No está descargado. Descárgalo de nuevo.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!SameBoyNative.isAvailable()) {
            Toast.makeText(this, "Descargado, pero el núcleo GB no está disponible",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent play = new Intent(this, OkubiGameBoyActivity.class);
        play.putExtra(OkubiGameBoyActivity.EXTRA_HOMEBREW_PATH,
                directHomebrewFile(game).getAbsolutePath());
        play.putExtra(OkubiGameBoyActivity.EXTRA_NAME, game.name);
        play.putExtra(OkubiGameBoyActivity.EXTRA_SYSTEM, "GB");
        startActivity(play);
    }

    private void updateIndieAction(Game game) {
        Button action = indieActions.get(game);
        TextView status = indieStatuses.get(game);
        if (action == null || status == null) return;
        if (game.direct) {
            if (directDownloading) {
                action.setText("⌛ DESCARGANDO...");
                action.setEnabled(false);
                status.setText("Descarga directa del autor en curso");
            } else if (verifiedDirectGame(game)) {
                action.setText(SameBoyNative.isAvailable()
                        ? "▶ JUGAR EN OKUBI" : "✓ DESCARGADO");
                action.setEnabled(true);
                status.setText("✓ Archivo guardado dentro de OKUBI · no es una app Android");
            } else {
                action.setText("↓ DESCARGAR Y JUGAR");
                action.setEnabled(true);
                status.setText("Aún no descargado en esta instalación");
            }
        } else {
            OkubiIndieLibrary.Linked linked = OkubiIndieLibrary.saved(this, game.url, game.name);
            Button link = indieLinkActions.get(game);
            if (linked != null) {
                action.setText("▶ JUGAR EN OKUBI");
                status.setText("✓ Archivo vinculado · " + linked.system
                        + " · accesible desde esta instalación");
                if (link != null) link.setText("↻ CAMBIAR ARCHIVO DESCARGADO");
            } else {
                action.setText("↗ VER DESCARGA DEL CREADOR");
                status.setText("Archivo aún no vinculado. Si ya lo descargaste, elígelo abajo.");
                if (link != null) link.setText("＋ VINCULAR ARCHIVO DESCARGADO");
            }
            action.setEnabled(true);
        }
    }

    private void refreshDownloadActions() {
        for (Game game : GAMES) updateIndieAction(game);
        for (DirectPatch patch : VERIFIED_PATCHES) updatePatchAction(patch);
        refreshDownloadCenter();
    }

    private void chooseIndieFile(Game game) {
        if (game == null || game.direct) return;
        pendingCreatorUrl = game.url;
        pendingCreatorTitle = game.name;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(picker, REQUEST_LINK_INDIE);
        } catch (Exception error) {
            pendingCreatorUrl = null;
            pendingCreatorTitle = null;
            Toast.makeText(this, "No se pudo abrir el selector de archivos",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_LINK_INDIE) return;
        String source = pendingCreatorUrl;
        String title = pendingCreatorTitle;
        pendingCreatorUrl = null;
        pendingCreatorTitle = null;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Game game = null;
        for (Game available : GAMES) {
            if (!available.direct && available.url.equals(source)
                    && available.name.equals(title)) {
                game = available;
                break;
            }
        }
        if (game == null) return;
        try {
            OkubiIndieLibrary.link(this, game.url, game.name,
                    game.system, data.getData());
            Toast.makeText(this, game.name + " vinculado. Ahora puedes jugarlo.",
                    Toast.LENGTH_LONG).show();
        } catch (IOException | SecurityException error) {
            Toast.makeText(this, "No se pudo vincular: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        updateIndieAction(game);
        refreshDownloadCenter();
    }

    private void download(Game game) {
        Uri uri = Uri.parse(game.url);
        if (!game.direct || !"https".equals(uri.getScheme())
                || !"raw.githubusercontent.com".equals(uri.getHost())
                || !"/wolandark/Ascension/master/build/rom/game.gb".equals(uri.getPath())) {
            Toast.makeText(this, "Solo se permiten ROMs homebrew autorizadas",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (verifiedDirectGame(game)) {
            playDirectGame(game);
            return;
        }
        if (directDownloading) return;
        directDownloading = true;
        directDownloadedBytes=0;
        directTotalBytes=-1;
        directLastError=null;
        updateIndieAction(game);
        refreshDownloadCenter();
        startProgressTicker();
        Toast.makeText(this, "Descargando el juego del creador...",
                Toast.LENGTH_SHORT).show();
        try {
            downloadQueue.execute(() -> {
                File target = new File(getFilesDir(), "okubi/homebrew/ascension.gb");
                try {
                    File directory = target.getParentFile();
                    if (!directory.isDirectory() && !directory.mkdirs())
                        throw new IllegalStateException("No se pudo crear la biblioteca");
                    if (!target.isFile() || target.length() < 32768) {
                        HttpURLConnection connection = (HttpURLConnection)new URL(game.url).openConnection();
                        connection.setConnectTimeout(12000);
                        connection.setReadTimeout(12000);
                        connection.setInstanceFollowRedirects(false);
                        connection.setRequestProperty("User-Agent", "OKUBI-Homebrew/1.0");
                        try {
                            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                                throw new IllegalStateException("Error HTTP " + connection.getResponseCode());
                            directTotalBytes=connection.getContentLengthLong();
                            if(directTotalBytes>8L*1024*1024)
                                throw new IllegalStateException("Archivo mayor que 8 MB");
                            AtomicFile atomic = new AtomicFile(target);
                            FileOutputStream out = null;
                            try (InputStream in = connection.getInputStream()) {
                                out = atomic.startWrite();
                                byte[] buffer = new byte[16384];
                                long bytes = 0, lastReport=0;
                                int count;
                                while ((count = in.read(buffer)) != -1) {
                                    if(Thread.currentThread().isInterrupted())
                                        throw new IOException("Descarga interrumpida");
                                    bytes += count;
                                    if (bytes > 8L * 1024 * 1024)
                                        throw new IllegalStateException("Archivo mayor que 8 MB");
                                    out.write(buffer, 0, count);
                                    directDownloadedBytes=bytes;
                                    if(bytes-lastReport>=131072){
                                        lastReport=bytes;
                                        runOnUiThread(()->{
                                            if(!closed&&!isFinishing())refreshDownloadCenter();
                                        });
                                    }
                                }
                                if(directTotalBytes>0&&bytes!=directTotalBytes)
                                    throw new IOException("Descarga truncada");
                                if (bytes < 32768)
                                    throw new IllegalStateException("Descarga incompleta");
                                atomic.finishWrite(out);
                                out = null;
                            } catch (Exception error) {
                                if (out != null) atomic.failWrite(out);
                                throw error;
                            }
                        } finally {
                            connection.disconnect();
                        }
                    }
                    runOnUiThread(() -> {
                        directDownloading = false;
                        if (closed || isFinishing()) return;
                        updateIndieAction(game);
                        refreshDownloadCenter();
                        if (!SameBoyNative.isAvailable()) {
                            Toast.makeText(this,
                                "Juego descargado. El motor SameBoy aún no está disponible en este APK",
                                Toast.LENGTH_LONG).show();
                            return;
                        }
                        Intent play = new Intent(this, OkubiGameBoyActivity.class);
                        play.putExtra(OkubiGameBoyActivity.EXTRA_HOMEBREW_PATH,
                                target.getAbsolutePath());
                        play.putExtra(OkubiGameBoyActivity.EXTRA_NAME, game.name);
                        play.putExtra(OkubiGameBoyActivity.EXTRA_SYSTEM, "GB");
                        startActivity(play);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        directDownloading = false;
                        directLastError=error.getClass().getSimpleName();
                        if (closed || isFinishing()) return;
                        updateIndieAction(game);
                        refreshDownloadCenter();
                        Toast.makeText(this,
                            "No se pudo descargar: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (RejectedExecutionException error) {
            directDownloading = false;
            directLastError="Gestor no disponible";
            updateIndieAction(game);
            refreshDownloadCenter();
            Toast.makeText(this, "Descarga no disponible", Toast.LENGTH_LONG).show();
        }
    }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private TextView label(String s,int size) {
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);
        t.setPadding(0,dp(5),0,dp(5));
        return t;
    }
}
