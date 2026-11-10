package bo.nexoplay.app;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.os.Build;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.lang.ref.WeakReference;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import android.util.LruCache;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.net.Uri;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OKUBI / CINEMA 1.0
 * Standalone game hub; optional device-usage access only when user explicitly approves.
 * No root, network, accessibility, automated system changes or process termination.
 */
public final class MainActivity extends Activity {
    private enum Screen { HOME, LIBRARY, RETRO, DISCOVER, COLLECTIONS, COLLECTION_DETAIL, ACTIVITY, PROFILE, SETTINGS }
    private Screen screen=Screen.HOME;
    private GameStore store;
    private GameCollections collections;
    private GameCatalog catalog;
    private OkubiUnifiedLibrary unifiedLibrary;
    private String libraryScope = "all";
    private String pendingCoverEntryId;
    private static final int REQUEST_RETRO_COVER = 7289;
    private static final int REQUEST_UNIFIED_EXPORT = 7290;
    private static final int REQUEST_UNIFIED_IMPORT = 7291;
    // One task maximum in flight; never accumulate many archive copies in RAM.
    private final ThreadPoolExecutor migrationQueue = new ThreadPoolExecutor(
            1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.AbortPolicy());
    private boolean migrationBusy;
    // Decode only visible tiles. The old unbounded thumbnail queue could
    // decode hundreds of ROM covers while the user saw just four at once.
    private static final class RetroCoverTarget {
        final String gameId, uri, cacheKey;
        boolean pending, failed, displayed;
        RetroCoverTarget(String gameId, String uri) {
            this.gameId=gameId;
            this.uri=uri;
            this.cacheKey=gameId+"|"+uri;
        }
    }
    private final Map<ImageView,RetroCoverTarget> coverTargets=new LinkedHashMap<>();
    private final Rect coverViewport=new Rect();
    private boolean coverScanQueued;
    private final ThreadPoolExecutor coverWorker = new ThreadPoolExecutor(
            1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(12),
            new ThreadPoolExecutor.AbortPolicy());
    // Memory budget is KB, not a count of bitmaps of arbitrary dimensions.
    private final LruCache<String, Bitmap> retroCovers =
            new LruCache<String, Bitmap>(4 * 1024) {
                @Override protected int sizeOf(String key,Bitmap bitmap) {
                    return Math.max(1,(bitmap.getByteCount()+1023)/1024);
                }
            };
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout body;
    private boolean favoritesOnly=false;
    private boolean compact=false;
    private String libraryQuery="";
    private static final int LIBRARY_PAGE_SIZE = 36;
    private int libraryShown = LIBRARY_PAGE_SIZE;
    private String collectionOrder="name";
    private String activeCollectionId="";
    private String libraryOrder="recent";
    private int usagePeriodDays=7;
    private boolean usageFavoritesOnly=false;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AppearancePrefs.apply(this);
        compact=AppearancePrefs.compactLibrary(this);
        favoritesOnly=getPreferences(MODE_PRIVATE).getBoolean("library_fav_only_08",false);
        if(bundle!=null) {
            libraryQuery=bundle.getString("library_query","");
            libraryScope=bundle.getString("library_scope","all");
            pendingCoverEntryId=bundle.getString("pending_cover_id");
            libraryShown=Math.max(LIBRARY_PAGE_SIZE,
                    Math.min(250,bundle.getInt("library_shown",LIBRARY_PAGE_SIZE)));
        }
        getWindow().setStatusBarColor(CinemaUi.BG);
        getWindow().setNavigationBarColor(CinemaUi.BG);
        store=new GameStore(this); collections=new GameCollections(this); catalog=new GameCatalog(this);
        unifiedLibrary=new OkubiUnifiedLibrary(this,catalog,store);
        collectionOrder=getPreferences(MODE_PRIVATE).getString("collection_order_v06","name");
        libraryOrder=getPreferences(MODE_PRIVATE).getString("library_order_v09","recent");
        if(bundle!=null) activeCollectionId=bundle.getString("active_collection_id","");
        catalog.reload(); OkubiGameScanner.sync(this, catalog, store); createShell();
        String tab=getIntent().getStringExtra("open_tab");
        Screen initial="activity".equals(tab)?Screen.ACTIVITY:Screen.HOME;
        if(bundle!=null)try{initial=Screen.valueOf(bundle.getString("current_screen","HOME"));}catch(Exception ignored){}
        showScreen(initial);
    }
    @Override protected void onResume() {
        super.onResume();
        if (catalog != null && body != null) {
            if (catalog.reload()) OkubiGameScanner.sync(this, catalog, store);
            showScreen(screen);
        }
    }
    @Override protected void onSaveInstanceState(Bundle outState){
        outState.putString("current_screen",screen.name());
        outState.putString("library_query",libraryQuery);
        outState.putInt("library_shown",libraryShown);
        outState.putString("library_scope",libraryScope);
        outState.putString("pending_cover_id",pendingCoverEntryId);
        outState.putString("active_collection_id",activeCollectionId);
        super.onSaveInstanceState(outState);
    }
    @Override public void onBackPressed(){
        if(screen==Screen.COLLECTION_DETAIL)showScreen(Screen.COLLECTIONS);
        else if(screen!=Screen.HOME)showScreen(Screen.HOME);
        else super.onBackPressed();
    }
    private boolean liteInterface(){return getPreferences(MODE_PRIVATE).getBoolean("cinema_lite_v07",false);}
    private int dp(int n) { return CinemaUi.dp(this,n); }
    private LinearLayout col() { return CinemaUi.column(this); }
    private LinearLayout row() { return CinemaUi.row(this); }
    private TextView text(String s,int sp,int color,boolean strong) { return CinemaUi.text(this,s,sp,color,strong); }
    private void pad(View v,int a,int b,int c,int d) { CinemaUi.pad(this,v,a,b,c,d); }
    private LinearLayout.LayoutParams lp(int w,int h) { return CinemaUi.lp(this,w,h); }
    private View gap(int h) { return CinemaUi.gap(this,h); }

    private void createShell() {
        root = new FrameLayout(this); root.setBackgroundColor(CinemaUi.BG); setContentView(root);
        scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.setFillViewport(true);
        scroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->scanVisibleRetroCovers());
        FrameLayout.LayoutParams sc = new FrameLayout.LayoutParams(-1,-1); sc.bottomMargin=dp(82);
        root.addView(scroll,sc);
        body=col(); pad(body,18,21,18,36); scroll.addView(body);
    }
    private void showScreen(Screen next) {
        screen=next;
        coverTargets.clear();
        body.removeAllViews();
        makeHeader();
        if (next==Screen.HOME) showHome();
        else if (next==Screen.LIBRARY) showLibrary();
        else if (next==Screen.RETRO) showRetroLanding();
        else if (next==Screen.DISCOVER) showDiscover();
        else if (next==Screen.COLLECTIONS) showCollections();
        else if (next==Screen.COLLECTION_DETAIL) showCollectionDetail();
        else if (next==Screen.ACTIVITY) showActivity();
        else if (next==Screen.PROFILE) showProfile();
        else showSettings();
        buildNavigation();
        scroll.post(() -> scroll.scrollTo(0,0));
        scanVisibleRetroCovers();
        body.animate().cancel();
        boolean enabled=Build.VERSION.SDK_INT<26 || ValueAnimator.areAnimatorsEnabled();
        if(!AppearancePrefs.reducedMotion(this) && !liteInterface() && enabled){
            body.setAlpha(.88f);
            body.setTranslationY(dp(9));
            body.animate().alpha(1f).translationY(0f).setDuration(160).start();
        } else {
            body.setAlpha(1f);
            body.setTranslationY(0f);
        }
    }
    private void makeHeader() {
        LinearLayout header=row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.ic_okubi);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setContentDescription("Logotipo OKUBI: espada y mando");
        mark.setBackground(CinemaUi.gradient(this,0xff302039,CinemaUi.DEEP,13));
        header.addView(mark,lp(49,49));

        LinearLayout brand=col();
        LinearLayout.LayoutParams brandLp=new LinearLayout.LayoutParams(0,-2,1f);
        brandLp.leftMargin=dp(12);
        header.addView(brand,brandLp);
        LinearLayout title=row();
        TextView logo=text("OKUBI",21,CinemaUi.WHITE,true);
        logo.setLetterSpacing(.10f);
        title.addView(logo);
        TextView kanji=text(" 遊",16,CinemaUi.GOLD,true);
        title.addView(kanji);
        brand.addView(title);
        brand.addView(gap(5));
        TextView subtitle=text("JUEGA A TU MANERA",10,CinemaUi.MUTED,true);
        subtitle.setLetterSpacing(.10f);
        brand.addView(subtitle);

        TextView profile=CinemaUi.chip(this,"◉  PERFIL",false);
        profile.setContentDescription("Abrir mi perfil de OKUBI");
        header.addView(profile,lp(-2,42));
        profile.setOnClickListener(v->showScreen(Screen.PROFILE));
        body.addView(header);
        body.addView(gap(24));
    }
    private TextView eyebrow(String s) { return CinemaUi.eyebrow(this,s); }
    private void section(String title,String caption) {
        body.addView(gap(26));
        LinearLayout line=row();
        View marker=new View(this);
        marker.setBackground(CinemaUi.shape(this,CinemaUi.GOLD,3));
        line.addView(marker,lp(4,25));
        LinearLayout textStack=col();
        LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-2,1f);
        textParams.leftMargin=dp(11);
        line.addView(textStack,textParams);
        textStack.addView(text(title,20,CinemaUi.WHITE,true));
        textStack.addView(gap(5));
        TextView hint=text(caption,11,CinemaUi.MUTED,false);
        hint.setMaxLines(2);
        textStack.addView(hint);
        body.addView(line);
        body.addView(gap(15));
    }
    private List<GameApp> games() { return catalog.selected(store); }
    private GameApp featured(List<GameApp> games) {
        GameApp g=catalog.get(store.lastGame());
        if(g != null && store.selected().contains(g.id))return g;
        return games.isEmpty()?null:games.get(0);
    }
    private void showHome() {
        List<OkubiUnifiedLibrary.Entry> allEntries=unifiedLibrary.all();
        // One home spotlight across BOTH Android and retro. A user pin takes
        // priority; automatic fallback uses the actual most recent launch.
        OkubiUnifiedLibrary.Entry spotlight=unifiedLibrary.spotlight(allEntries);
        GameApp featured=spotlight!=null
                && spotlight.kind==OkubiUnifiedLibrary.Kind.ANDROID
                ?spotlight.androidGame:null;
        final OkubiUnifiedLibrary.Entry retroHero=spotlight!=null
                && spotlight.kind!=OkubiUnifiedLibrary.Kind.ANDROID
                ?spotlight:null;
        final boolean hasHero=featured!=null||retroHero!=null;
        // One fully interactive cinematic hero. Tapping artwork/text opens
        // details directly; the play button remains a separate direct launch.
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(CinemaUi.spotlight(this,0xff3b294e,0xff141824,26));
        hero.setClipToOutline(true);
        body.addView(hero,lp(-1,320));

        TextView backupPoster=text(
                !hasHero?"遊":(featured!=null?"▶":retroHero.system),
                74,0x779e93b7,true);
        backupPoster.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams fallback=new FrameLayout.LayoutParams(
                dp(145),dp(145),Gravity.RIGHT|Gravity.TOP);
        fallback.topMargin=dp(50);fallback.rightMargin=dp(8);
        hero.addView(backupPoster,fallback);

        if(featured!=null) {
            ImageView image=new ImageView(this);
            boolean cover=CoverArt.apply(this,image,store,featured);
            if(cover){
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setAlpha(.78f);
                hero.addView(image,new FrameLayout.LayoutParams(-1,-1));
            }else{
                FrameLayout.LayoutParams iconPos=new FrameLayout.LayoutParams(
                        dp(112),dp(112),Gravity.RIGHT|Gravity.TOP);
                iconPos.topMargin=dp(62);iconPos.rightMargin=dp(29);
                image.setAlpha(.85f);
                hero.addView(image,iconPos);
            }
        } else if(retroHero!=null) {
            ImageView image=new ImageView(this);
            image.setTag(retroHero.id);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAlpha(.76f);
            hero.addView(image,new FrameLayout.LayoutParams(-1,-1));
            loadRetroCover(retroHero,image);
        }

        View shade=new View(this);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xff16111f,0xd4181123,0x5c131522}));
        hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View bottomShade=new View(this);
        bottomShade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xe8090913,0x00090913}));
        hero.addView(bottomShade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout contents=col();
        pad(contents,22,24,20,22);
        hero.addView(contents,new FrameLayout.LayoutParams(-1,-1));
        TextView eyebrow=eyebrow(!hasHero?"●  TU NUEVA BIBLIOTECA"
                :unifiedLibrary.isSpotlightPinned(spotlight)
                ?"◆  TU JUEGO DESTACADO":"●  SELECCIÓN DEL MOMENTO");
        contents.addView(eyebrow);
        contents.addView(gap(8));
        TextView hint=text("OKUBI  /  TU ESPACIO GAMER",
                10,0xffded2e5,true);
        hint.setLetterSpacing(.075f);
        contents.addView(hint);
        View stretch=new View(this);
        contents.addView(stretch,new LinearLayout.LayoutParams(1,0,1f));
        String headline=!hasHero?"TU UNIVERSO.\nTUS JUEGOS."
                :(featured!=null?featured.title:retroHero.title);
        TextView heading=text(headline,30,CinemaUi.WHITE,true);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        contents.addView(heading);
        contents.addView(gap(9));
        String descriptor=!hasHero
                ?"Juegos Android y retro en una sola colección."
                :(featured!=null?"ANDROID  •  LISTO PARA INICIAR"
                :retroHero.system+"  •  CARTUCHO DE TU BIBLIOTECA");
        TextView subheading=text(descriptor,11,0xffe5dbe9,true);
        subheading.setMaxLines(2);
        contents.addView(subheading);
        contents.addView(gap(21));

        LinearLayout action=row();
        contents.addView(action);
        TextView play=CinemaUi.action(this,
                !hasHero?"＋  AGREGAR JUEGOS":"▶  JUGAR",true);
        LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(0,-2,1f);
        action.addView(play,playLp);
        play.setOnClickListener(v->{
            if(!hasHero)showScreen(Screen.LIBRARY);
            else if(featured!=null)launch(featured);
            else launchRetroEntry(retroHero);
        });
        if(hasHero){
            TextView detail=CinemaUi.action(this,"VER FICHA",false);
            LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(0,-2,1f);
            detailLp.leftMargin=dp(9);
            action.addView(detail,detailLp);
            detail.setOnClickListener(v->{
                if(featured!=null)openDetails(featured);
                else openUnifiedDetails(retroHero);
            });
        }
        CinemaUi.actionable(this,hero,!hasHero
                ?"Abrir biblioteca de juegos"
                :"Ver ficha de "+(featured!=null?featured.title:retroHero.title));
        hero.setOnClickListener(v->{
            if(!hasHero)showScreen(Screen.LIBRARY);
            else if(featured!=null)openDetails(featured);
            else openUnifiedDetails(retroHero);
        });
        body.addView(gap(14));
        LinearLayout metrics=row();body.addView(metrics);
        metric(metrics,"JUEGOS",String.valueOf(allEntries.size()));
        int favCount=0;
        for(OkubiUnifiedLibrary.Entry entry:allEntries)if(entry.favorite)favCount++;
        metric(metrics,"FAVORITOS",String.valueOf(favCount));
        metric(metrics,"DATOS","LOCAL");
        body.addView(gap(12));
        LinearLayout shortcuts=row();
        body.addView(shortcuts);
        TextView rescan=CinemaUi.action(this,"↻  REESCANEAR",false);
        shortcuts.addView(rescan,new LinearLayout.LayoutParams(0,-2,1f));
        rescan.setOnClickListener(v->{
            catalog.forceReload();
            int added=OkubiGameScanner.sync(this,catalog,store);
            Toast.makeText(this,added==0?"Biblioteca actualizada":added+" juegos nuevos detectados",Toast.LENGTH_SHORT).show();
            showScreen(Screen.HOME);
        });
        TextView retro=CinemaUi.action(this,"🎮  CONSOLAS",true);
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(0,-2,1f);rlp.leftMargin=dp(9);
        shortcuts.addView(retro,rlp);
        retro.setOnClickListener(v->showScreen(Screen.RETRO));
        body.addView(gap(10));
        LinearLayout highlightTools=row();
        body.addView(highlightTools);
        TextView pickSpotlight=CinemaUi.action(this,"◆  ELEGIR DESTACADO",false);
        highlightTools.addView(pickSpotlight,new LinearLayout.LayoutParams(0,-2,1f));
        pickSpotlight.setOnClickListener(v->showSpotlightPicker());
        if(unifiedLibrary.hasSpotlightPin()) {
            TextView auto=CinemaUi.action(this,"↻ AUTOMÁTICO",false);
            LinearLayout.LayoutParams autoP=new LinearLayout.LayoutParams(0,-2,1f);
            autoP.leftMargin=dp(8);
            highlightTools.addView(auto,autoP);
            auto.setOnClickListener(v->{
                unifiedLibrary.clearSpotlight();
                showScreen(Screen.HOME);
            });
        }

        section("Continuar jugando","Android y retro abiertos desde OKUBI; no equivale a horas jugadas");
        List<OkubiUnifiedLibrary.Entry> recent=new ArrayList<>();
        for(OkubiUnifiedLibrary.Entry entry:allEntries)
            if(entry.lastPlayed>0)recent.add(entry);
        recent.sort((a,b)->Long.compare(b.lastPlayed,a.lastPlayed));
        if(recent.isEmpty()){
            emptyCard("Todavía no hay juegos recientes",
                    "Abre un juego de tu biblioteca para verlo aquí.",false);
        }else{
            HorizontalScrollView hs=new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout shelf=row();pad(shelf,0,3,1,6);
            hs.addView(shelf);body.addView(hs);
            int index=0;
            for(OkubiUnifiedLibrary.Entry entry:recent){
                if(index>=8)break;
                LinearLayout recentCard=col();
                View tile=unifiedTile(entry,index++);
                recentCard.addView(tile,lp(-1,213));
                recentCard.addView(gap(6));
                TextView launchNow=CinemaUi.action(this,"▶  JUGAR AHORA",true);
                CinemaUi.actionable(this,launchNow,"Continuar jugando "+entry.title);
                recentCard.addView(launchNow,lp(-1,-2));
                launchNow.setOnClickListener(v->launchUnifiedEntry(entry));
                LinearLayout.LayoutParams tileP=lp(166,270);
                tileP.rightMargin=dp(10);
                shelf.addView(recentCard,tileP);
            }
        }
        section("Tu actividad","Datos de uso disponibles solo si tú los autorizas");
        TextView activity=CinemaUi.action(this,"VER TIEMPO DE USO  →",false);
        body.addView(activity,lp(-1,-2));activity.setOnClickListener(v->showScreen(Screen.ACTIVITY));
        section("Tu espacio gaming","Personalizado y separado del inicio de Samsung");
        LinearLayout calls=row();body.addView(calls);
        TextView library=CinemaUi.action(this,"VER BIBLIOTECA",true);
        calls.addView(library,new LinearLayout.LayoutParams(0,-2,1f));library.setOnClickListener(v->showScreen(Screen.LIBRARY));
        TextView settings=CinemaUi.action(this,"AJUSTES",false);
        LinearLayout.LayoutParams settingsLp=lp(-2,-2);settingsLp.leftMargin=dp(9);
        calls.addView(settings,settingsLp);settings.setOnClickListener(v->showScreen(Screen.SETTINGS));
        body.addView(gap(12));
        TextView collectionsButton=CinemaUi.action(this,"MIS COLECCIONES  →",false);
        body.addView(collectionsButton,lp(-1,-2));
        collectionsButton.setOnClickListener(v->showScreen(Screen.COLLECTIONS));
    }
    /** Pin only visible library items; never copy ROMs or states. */
    private void pinSpotlight(OkubiUnifiedLibrary.Entry entry) {
        if(entry==null)return;
        unifiedLibrary.setSpotlight(entry);
        Toast.makeText(this,"◆ "+entry.title+" destacado en Inicio",
                Toast.LENGTH_SHORT).show();
        showScreen(screen);
    }

    private void showSpotlightPicker() {
        List<OkubiUnifiedLibrary.Entry> choices=unifiedLibrary.all();
        if(choices.isEmpty()) {
            Toast.makeText(this,"Primero añade un juego a tu biblioteca",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // Bounded modal for low-memory phones. Any game outside the first
        // 40 can still be pinned with a long press in the full library.
        choices.sort((a,b)->OkubiLibrarySpotlight.compare(
                new OkubiLibrarySpotlight.Candidate(
                        a.id,a.title,a.lastPlayed,a.favorite),
                new OkubiLibrarySpotlight.Candidate(
                        b.id,b.title,b.lastPlayed,b.favorite)));
        int shown=Math.min(40,choices.size());
        String[] labels=new String[shown];
        for(int i=0;i<shown;i++){
            OkubiUnifiedLibrary.Entry entry=choices.get(i);
            labels[i]=(unifiedLibrary.isSpotlightPinned(entry)?"◆  ":"")
                    +entry.title+"  ·  "+entry.system;
        }
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · Elegir juego destacado")
                // Keep list visible: on Android setMessage+setItems can hide it.
                .setItems(labels,(dialog,which)->{
                    unifiedLibrary.setSpotlight(choices.get(which));
                    showScreen(Screen.HOME);
                })
                .setNeutralButton("Selección automática",(dialog,which)->{
                    unifiedLibrary.clearSpotlight();
                    showScreen(Screen.HOME);
                })
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void metric(LinearLayout parent,String name,String val){
        LinearLayout c=col();pad(c,12,14,10,14);c.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(83),1f);p.rightMargin=dp(7);parent.addView(c,p);
        c.addView(text(name,9,CinemaUi.MUTED,true));c.addView(gap(10));
        c.addView(text(val,val.length()>4?15:21,CinemaUi.WHITE,true));
    }
    private void emptyCard(String name,String detail,boolean add){
        LinearLayout c=col();c.setGravity(Gravity.CENTER);pad(c,20,23,20,23);
        c.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,19));body.addView(c,lp(-1,165));
        c.addView(text("✦",26,CinemaUi.GOLD,true));c.addView(gap(9));
        TextView t=text(name,16,CinemaUi.WHITE,true);t.setGravity(Gravity.CENTER);c.addView(t);
        c.addView(gap(7));TextView d=text(detail,12,CinemaUi.MUTED,false);d.setGravity(Gravity.CENTER);c.addView(d);
        if(add)c.setOnClickListener(v->showSelector());
    }
    private void showRetroLanding() {
        body.addView(eyebrow("CONSOLAS · OKUBI"));
        body.addView(gap(12));
        body.addView(text("Tu sala retro",30,CinemaUi.WHITE,true));
        body.addView(gap(8));
        body.addView(text("GB y GBC con SameBoy, GBA con mGBA, y Super Nintendo con Supafaust si el núcleo está disponible. NES y otras consolas requieren un emulador externo.",13,CinemaUi.MUTED,false));
        body.addView(gap(20));
        LinearLayout spotlight=col();
        pad(spotlight,20,22,20,22);
        spotlight.setBackground(CinemaUi.gradient(this,0xff51244a,0xff1d1229,23));
        body.addView(spotlight,lp(-1,-2));
        spotlight.addView(text("GAME BOY  /  GAME BOY COLOR",11,CinemaUi.GOLD,true));
        spotlight.addView(gap(8));
        spotlight.addView(text("La aventura comienza aquí",23,CinemaUi.WHITE,true));
        spotlight.addView(gap(10));
        spotlight.addView(text("Juega tus cartuchos GB, GBC y GBA con los controles táctiles o un mando Bluetooth.",12,CinemaUi.MUTED,false));
        spotlight.addView(gap(15));
        TextView enter=CinemaUi.action(this,"ABRIR CONSOLAS  →",true);
        spotlight.addView(enter,lp(-1,-2));
        enter.setOnClickListener(v->startActivity(new Intent(this,bo.nexoplay.app.retro.OkubiRetroActivity.class)));
        section("RETRO EXPANSION · SNES nativo y NES",
                "SNES dentro de OKUBI con Supafaust; NES requiere emulador externo");
        LinearLayout classics=row();
        TextView snes=CinemaUi.action(this,"＋ SNES",true);
        classics.addView(snes,new LinearLayout.LayoutParams(0,-2,1f));
        snes.setOnClickListener(v->openClassicConsole("SNES"));
        TextView nes=CinemaUi.action(this,"＋ NES",false);
        LinearLayout.LayoutParams nesLp=new LinearLayout.LayoutParams(0,-2,1f);
        nesLp.leftMargin=dp(8);
        classics.addView(nes,nesLp);
        nes.setOnClickListener(v->openClassicConsole("NES"));
        body.addView(classics);
        body.addView(gap(9));
        TextView otherClassics=CinemaUi.action(this,"VER OTRAS CONSOLAS CLÁSICAS",false);
        body.addView(otherClassics,lp(-1,-2));
        otherClassics.setOnClickListener(v->startActivity(new Intent(this,
                bo.nexoplay.app.retro.OkubiRetroActivity.class)));
        java.util.List<bo.nexoplay.app.retro.OkubiPersonalLibrary.Game> included =
                bo.nexoplay.app.retro.OkubiPersonalLibrary.available(this);
        if (!included.isEmpty()) {
            section("Mis cartuchos incluidos", "Juega directamente tus copias personales, sin importar archivos");
            for (bo.nexoplay.app.retro.OkubiPersonalLibrary.Game game : included) {
                TextView launch = CinemaUi.action(this, "▶  " + game.title.toUpperCase(java.util.Locale.ROOT), true);
                body.addView(launch,lp(-1,-2));
                body.addView(gap(10));
                launch.setOnClickListener(v -> {
                    Intent play = new Intent(this, bo.nexoplay.app.retro.OkubiGameBoyActivity.class);
                    play.putExtra(bo.nexoplay.app.retro.OkubiPersonalLibrary.EXTRA_ASSET, game.asset);
                    play.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_NAME, game.title);
                    play.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_SYSTEM, "GBA");
                    startActivity(play);
                });
            }
        }
        section("Colecciones retro","Tus juegos y descubrimientos");
        TextView pokemon=CinemaUi.action(this,"POKÉMON · BIBLIOTECA PERSONAL",false);
        body.addView(pokemon,lp(-1,-2));
        pokemon.setOnClickListener(v->startActivity(new Intent(this,bo.nexoplay.app.retro.OkubiPokemonActivity.class)));
        body.addView(gap(12));
        TextView demos=CinemaUi.action(this,"JUEGOS RETRO GRATUITOS Y LEGALES",false);
        body.addView(demos,lp(-1,-2));
        demos.setOnClickListener(v->startActivity(new Intent(this,bo.nexoplay.app.retro.OkubiCatalogActivity.class)));
        section("Compatibilidad","Elige la consola adecuada para tu teléfono");
        body.addView(text("GB/GBC funcionan con SameBoy y GBA con mGBA. DS, N64 y otras consolas requieren un emulador compatible o están solo en catálogo.",12,CinemaUi.MUTED,false));
    }

    private void openClassicConsole(String system) {
        Intent console=new Intent(this,bo.nexoplay.app.retro.OkubiRetroActivity.class);
        console.putExtra(bo.nexoplay.app.retro.OkubiRetroActivity.EXTRA_SYSTEM,system);
        startActivity(console);
    }

    private void showDiscover() {
        body.addView(eyebrow("DESCUBRE · OKUBI"));
        body.addView(gap(12));
        body.addView(text("Más formas de jugar",29,CinemaUi.WHITE,true));
        body.addView(gap(8));
        body.addView(text("Explora los juegos Android que tienes instalados y la biblioteca retro. Sin anuncios ni enlaces a ROMs comerciales.",12,CinemaUi.MUTED,false));
        section("Android","Juegos instalados y biblioteca local");
        TextView scan=CinemaUi.action(this,"DETECTAR JUEGOS DE MI TELÉFONO",true);
        body.addView(scan,lp(-1,-2));
        scan.setOnClickListener(v->{
            catalog.forceReload();
            int added=OkubiGameScanner.sync(this,catalog,store);
            Toast.makeText(this,added+" nuevos juegos detectados",Toast.LENGTH_SHORT).show();
            showScreen(Screen.LIBRARY);
        });
        body.addView(gap(10));
        TextView choose=CinemaUi.action(this,"AÑADIR JUEGO MANUALMENTE",false);
        body.addView(choose,lp(-1,-2));
        choose.setOnClickListener(v->showSelector());
        section("Mis descargas","Estado de archivos, progreso, errores y juegos ya disponibles");
        TextView manager=CinemaUi.action(this,"↓  ABRIR MI GESTOR DE DESCARGAS",true);
        body.addView(manager,lp(-1,-2));
        manager.setOnClickListener(v->startActivity(new Intent(this,
                bo.nexoplay.app.retro.OkubiCatalogActivity.class)));
        body.addView(gap(10));
        section("Retro","Juegos indie, fangames y fuentes de Nintendo");
        TextView nintendo=CinemaUi.action(this,"NINTENDO / DESCARGAS Y ROM HACKS  ↓",false);
        body.addView(nintendo,lp(-1,-2));
        nintendo.setOnClickListener(v->startActivity(new Intent(this,
                bo.nexoplay.app.retro.OkubiCatalogActivity.class)));
        body.addView(gap(12));
        TextView homebrew=CinemaUi.action(this,"EXPLORAR HOMEBREW  →",false);
        body.addView(homebrew,lp(-1,-2));
        homebrew.setOnClickListener(v->startActivity(new Intent(this,bo.nexoplay.app.retro.OkubiCatalogActivity.class)));
        body.addView(gap(12));
        TextView pokemon=CinemaUi.action(this,"COLECCIÓN POKÉMON  →",false);
        body.addView(pokemon,lp(-1,-2));
        pokemon.setOnClickListener(v->startActivity(new Intent(this,bo.nexoplay.app.retro.OkubiPokemonActivity.class)));
    }

    private void showLibrary() {
        body.addView(eyebrow("BIBLIOTECA PERSONAL"));body.addView(gap(8));
        body.addView(text("Todos tus juegos",28,CinemaUi.WHITE,true));body.addView(gap(6));
        body.addView(text("Android y retro juntos. Mantén presionado un juego para destacarlo en Inicio; tus partidas no se modifican.",12,CinemaUi.MUTED,false));
        body.addView(gap(17));
        LinearLayout addActions=row();body.addView(addActions);
        TextView add=CinemaUi.action(this,"＋ ANDROID",true);
        addActions.addView(add,new LinearLayout.LayoutParams(0,-2,1f));
        add.setOnClickListener(v->showSelector());
        TextView addRetro=CinemaUi.action(this,"＋ IMPORTAR RETRO",false);
        LinearLayout.LayoutParams retroAction=new LinearLayout.LayoutParams(0,-2,1f);
        retroAction.leftMargin=dp(8);addActions.addView(addRetro,retroAction);
        addRetro.setOnClickListener(v->startActivity(new Intent(this,
                bo.nexoplay.app.retro.OkubiPokemonActivity.class)));
        body.addView(gap(15));
        LinearLayout types=row();body.addView(types);
        TextView every=CinemaUi.chip(this,"TODOS",libraryScope.equals("all"));
        TextView androidTab=CinemaUi.chip(this,"ANDROID",libraryScope.equals("android"));
        TextView retro=CinemaUi.chip(this,"RETRO",libraryScope.equals("retro"));
        TextView played=CinemaUi.chip(this,"JUGADOS",libraryScope.equals("played"));
        types.addView(every,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams typeP=new LinearLayout.LayoutParams(0,-2,1f);
        typeP.leftMargin=dp(6);types.addView(androidTab,typeP);
        LinearLayout.LayoutParams retroP=new LinearLayout.LayoutParams(0,-2,1f);
        retroP.leftMargin=dp(6);types.addView(retro,retroP);
        LinearLayout.LayoutParams playedP=new LinearLayout.LayoutParams(0,-2,1f);
        playedP.leftMargin=dp(6);types.addView(played,playedP);
        every.setOnClickListener(v->chooseLibraryScope("all"));
        androidTab.setOnClickListener(v->chooseLibraryScope("android"));
        retro.setOnClickListener(v->chooseLibraryScope("retro"));
        played.setOnClickListener(v->chooseLibraryScope("played"));
        body.addView(gap(17));
        EditText search=new EditText(this);search.setTextColor(CinemaUi.WHITE);
        search.setHintTextColor(CinemaUi.MUTED);search.setTextSize(13);
        search.setSingleLine(true);search.setHint("Buscar juegos o consolas");
        search.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_search,0,0,0);
        search.setCompoundDrawablePadding(dp(12));
        search.setBackground(CinemaUi.outlined(this,CinemaUi.DEEP,16));
        pad(search,15,12,15,12);
        search.setText(libraryQuery);
        body.addView(search,lp(-1,48));body.addView(gap(12));
        LinearLayout options=row();body.addView(options);
        TextView all=CinemaUi.chip(this,favoritesOnly?"TODOS":"✓ TODOS",!favoritesOnly);
        TextView fav=CinemaUi.chip(this,favoritesOnly?"✓ FAVORITOS":"☆ FAVORITOS",favoritesOnly);
        TextView view=CinemaUi.chip(this,compact?"▦ GRID":"≡ LISTA",false);
        options.addView(all, new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.leftMargin=dp(6);options.addView(fav,fp);
        LinearLayout.LayoutParams vp=lp(-2,-2);vp.leftMargin=dp(6);options.addView(view,vp);
        body.addView(gap(11));
        LinearLayout orderControls=row();body.addView(orderControls);
        TextView recentOrder=CinemaUi.chip(this,(libraryOrder.equals("recent")?"✓ ":"")+"RECIENTES",libraryOrder.equals("recent"));
        TextView alphabeticOrder=CinemaUi.chip(this,(libraryOrder.equals("az")?"✓ ":"")+"A–Z",libraryOrder.equals("az"));
        orderControls.addView(recentOrder,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams orderLp=new LinearLayout.LayoutParams(0,-2,1);orderLp.leftMargin=dp(7);
        orderControls.addView(alphabeticOrder,orderLp);
        recentOrder.setOnClickListener(v->setLibraryOrder("recent"));
        alphabeticOrder.setOnClickListener(v->setLibraryOrder("az"));
        LinearLayout result=col();body.addView(gap(16));body.addView(result);
        Runnable rerender=()->renderLibrary(result,search.getText().toString());
        all.setOnClickListener(v->{favoritesOnly=false;libraryShown=LIBRARY_PAGE_SIZE;saveLibraryFilter();showScreen(Screen.LIBRARY);});
        fav.setOnClickListener(v->{favoritesOnly=true;libraryShown=LIBRARY_PAGE_SIZE;saveLibraryFilter();showScreen(Screen.LIBRARY);});
        view.setOnClickListener(v->{compact=!compact;AppearancePrefs.setCompactLibrary(this,compact);showScreen(Screen.LIBRARY);});
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                libraryQuery=s.toString();libraryShown=LIBRARY_PAGE_SIZE;rerender.run();
            }
            @Override public void afterTextChanged(Editable e){}
        });
        rerender.run();
    }
    private void saveLibraryFilter(){
        getPreferences(MODE_PRIVATE).edit().putBoolean("library_fav_only_08",favoritesOnly).apply();
    }
    private void setLibraryOrder(String value){
        libraryOrder="az".equals(value)?"az":"recent";
        libraryShown=LIBRARY_PAGE_SIZE;
        getPreferences(MODE_PRIVATE).edit().putString("library_order_v09",libraryOrder).apply();
        showScreen(Screen.LIBRARY);
    }
    private void chooseLibraryScope(String selected) {
        libraryScope=selected;
        libraryShown=LIBRARY_PAGE_SIZE;
        showScreen(Screen.LIBRARY);
    }

    private void renderLibrary(LinearLayout area,String term) {
        area.removeAllViews();
        coverTargets.clear(); // Previous page ImageViews must not stay retained.
        List<OkubiUnifiedLibrary.Entry> matches=new ArrayList<>();
        String needle=term.trim().toLowerCase(Locale.ROOT);
        for(OkubiUnifiedLibrary.Entry entry : unifiedLibrary.all()){
            if(favoritesOnly && !entry.favorite)continue;
            if("android".equals(libraryScope)
                    && entry.kind!=OkubiUnifiedLibrary.Kind.ANDROID)continue;
            if("retro".equals(libraryScope)
                    && entry.kind==OkubiUnifiedLibrary.Kind.ANDROID)continue;
            if("played".equals(libraryScope) && entry.lastPlayed<=0)continue;
            if(!entry.title.toLowerCase(Locale.ROOT).contains(needle)
                    && !entry.system.toLowerCase(Locale.ROOT).contains(needle))continue;
            matches.add(entry);
        }
        if("az".equals(libraryOrder)) {
            matches.sort((a,b)->a.title.compareToIgnoreCase(b.title));
        } else {
            matches.sort((a,b)->{
                int order=Long.compare(b.lastPlayed,a.lastPlayed);
                return order!=0?order:a.title.compareToIgnoreCase(b.title);
            });
        }
        int androidCount=0,retroCount=0;
        for(OkubiUnifiedLibrary.Entry entry:matches){
            if(entry.kind==OkubiUnifiedLibrary.Kind.ANDROID)androidCount++;
            else retroCount++;
        }
        int shown=Math.min(matches.size(),libraryShown);
        TextView count=text("  "+shown+" / "+matches.size()
                +" JUEGOS     •     "+androidCount+" ANDROID     •     "+retroCount+" RETRO",
                10,CinemaUi.MUTED,true);
        pad(count,12,14,12,14);
        count.setBackground(CinemaUi.outlined(this,CinemaUi.DEEP,14));
        area.addView(count);
        area.addView(gap(14));
        if(matches.isEmpty()){
            TextView empty=text("No hay juegos que coincidan. Importa tus copias retro, añade aplicaciones o cambia el filtro.",13,CinemaUi.MUTED,false);
            pad(empty,16,25,16,25);
            empty.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,17));
            area.addView(empty);
            return;
        }
        if(compact){
            for(int entryIndex=0;entryIndex<shown;entryIndex++) {
                OkubiUnifiedLibrary.Entry entry=matches.get(entryIndex);
                LinearLayout item=row();pad(item,12,12,12,12);
                item.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,15));
                LinearLayout.LayoutParams il=lp(-1,75);il.bottomMargin=dp(8);
                area.addView(item,il);
                if(entry.kind==OkubiUnifiedLibrary.Kind.ANDROID) {
                    ImageView icon=new ImageView(this);
                    icon.setImageDrawable(entry.androidGame.icon);
                    item.addView(icon,lp(47,47));
                } else {
                    TextView device=text(entry.system,12,CinemaUi.GOLD,true);
                    device.setGravity(Gravity.CENTER);
                    device.setBackground(CinemaUi.gradient(this,0xff49304d,0xff21152c,11));
                    item.addView(device,lp(47,47));
                }
                LinearLayout details=col();
                LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1f);
                dl.leftMargin=dp(13);
                item.addView(details,dl);
                TextView name=text(entry.title,14,CinemaUi.WHITE,true);
                name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);
                details.addView(name);details.addView(gap(4));
                details.addView(text(entry.system+" · VER FICHA",10,CinemaUi.MUTED,false));
                TextView star=text(entry.favorite?"★":"☆",22,CinemaUi.GOLD,true);
                star.setContentDescription(entry.favorite?"Quitar favorito":"Marcar favorito");
                pad(star,8,9,8,9);
                item.addView(star);
                star.setOnClickListener(v->{
                    unifiedLibrary.setFavorite(entry,!entry.favorite);
                    renderLibrary(area,term);
                });
                item.setOnClickListener(v->openUnifiedDetails(entry));
                item.setOnLongClickListener(v->{pinSpotlight(entry);return true;});
            }
        } else {
            for(int i=0;i<shown;i+=2){
                LinearLayout line=row();line.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams lineP=lp(-1,-2);
                lineP.bottomMargin=dp(10);area.addView(line,lineP);
                for(int j=0;j<2;j++){
                    int pos=i+j;
                    if(pos>=shown){
                        line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
                        break;
                    }
                    OkubiUnifiedLibrary.Entry entry=matches.get(pos);
                    View tile=unifiedTile(entry,pos);
                    tile.setOnLongClickListener(v->{pinSpotlight(entry);return true;});
                    LinearLayout.LayoutParams cardP=new LinearLayout.LayoutParams(0,dp(224),1f);
                    if(j==0)cardP.rightMargin=dp(9);
                    line.addView(tile,cardP);
                }
            }
        }
        if(shown<matches.size()){
            TextView more=CinemaUi.action(this,
                    "MOSTRAR MÁS JUEGOS · "+(matches.size()-shown)+" RESTANTES",false);
            LinearLayout.LayoutParams moreParams=lp(-1,-2);
            moreParams.topMargin=dp(12);
            area.addView(more,moreParams);
            more.setOnClickListener(v->{
                libraryShown=Math.min(matches.size(),libraryShown+LIBRARY_PAGE_SIZE);
                renderLibrary(area,term);
            });
        }
    }

    /** Unified poster format for Android apps and retro personal cartridges. */
    private View unifiedTile(OkubiUnifiedLibrary.Entry entry,int position) {
        if(entry.kind==OkubiUnifiedLibrary.Kind.ANDROID)
            return gameTile(entry.androidGame,position,true);

        LinearLayout tile=col();
        pad(tile,9,9,9,13);
        tile.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,21));
        FrameLayout cover=new FrameLayout(this);
        int[][] palettes={{0xff39315a,0xff1a172c},{0xff254961,0xff171c30},
                {0xff564057,0xff26182c},{0xff2b554e,0xff15292b}};
        int[] shades=palettes[position%palettes.length];
        cover.setBackground(CinemaUi.gradient(this,shades[0],shades[1],15));
        cover.setClipToOutline(true);
        tile.addView(cover,new LinearLayout.LayoutParams(-1,0,1f));

        TextView emblem=text(entry.system,38,0x88ffffff,true);
        emblem.setGravity(Gravity.CENTER);
        cover.addView(emblem,new FrameLayout.LayoutParams(-1,-1));
        ImageView art=new ImageView(this);
        art.setTag(entry.id);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(art,new FrameLayout.LayoutParams(-1,-1));
        loadRetroCover(entry,art);

        View bottomShade=new View(this);
        bottomShade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x9e12101c,0x0012101c}));
        cover.addView(bottomShade,new FrameLayout.LayoutParams(-1,-1));

        TextView console=text(entry.system,10,CinemaUi.WHITE,true);
        pad(console,10,7,10,7);
        console.setBackground(CinemaUi.shape(this,0xd5211f35,10));
        FrameLayout.LayoutParams consoleLp=new FrameLayout.LayoutParams(
                -2,-2,Gravity.TOP|Gravity.LEFT);
        consoleLp.topMargin=dp(10);consoleLp.leftMargin=dp(10);
        cover.addView(console,consoleLp);

        TextView star=text(entry.favorite?"★":"☆",22,CinemaUi.GOLD,true);
        star.setGravity(Gravity.CENTER);
        star.setBackground(CinemaUi.shape(this,0xf21b1929,12));
        CinemaUi.actionable(this,star,entry.favorite
                ?"Quitar favorito: "+entry.title:"Marcar favorito: "+entry.title);
        FrameLayout.LayoutParams starLp=new FrameLayout.LayoutParams(
                dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);
        starLp.topMargin=dp(7);starLp.rightMargin=dp(7);
        cover.addView(star,starLp);
        star.setOnClickListener(v->{
            unifiedLibrary.setFavorite(entry,!entry.favorite);
            showScreen(screen);
        });

        tile.addView(gap(11));
        TextView label=text(entry.title,14,CinemaUi.WHITE,true);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label);
        tile.addView(gap(6));
        TextView detail=text("RETRO  •  VER FICHA",10,CinemaUi.GOLD,true);
        detail.setLetterSpacing(.035f);
        tile.addView(detail);
        CinemaUi.actionable(this,tile,"Abrir ficha retro de "+entry.title);
        tile.setOnClickListener(v->openUnifiedDetails(entry));
        return tile;
    }

    private View gameTile(GameApp game,int index,boolean grid) {
        LinearLayout tile=col();
        pad(tile,9,9,9,13);
        tile.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,21));
        if(!grid) {
            LinearLayout.LayoutParams p=lp(166,213);
            p.rightMargin=dp(10);
            tile.setLayoutParams(p);
        }
        FrameLayout artFrame=new FrameLayout(this);
        int[][] shades={{0xff31566c,0xff172335},{0xff62455c,0xff271a2d},
                {0xff495c57,0xff162831},{0xff5c4d69,0xff242038}};
        int[] palette=shades[index%shades.length];
        artFrame.setBackground(CinemaUi.gradient(this,palette[0],palette[1],15));
        artFrame.setClipToOutline(true);
        tile.addView(artFrame,new LinearLayout.LayoutParams(-1,0,1f));

        ImageView image=new ImageView(this);
        boolean hasCover=CoverArt.apply(this,image,store,game);
        FrameLayout.LayoutParams imageLp=hasCover
                ?new FrameLayout.LayoutParams(-1,-1)
                :new FrameLayout.LayoutParams(dp(80),dp(80),Gravity.CENTER);
        artFrame.addView(image,imageLp);
        View shade=new View(this);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x8e11121b,0x0011121b}));
        artFrame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        TextView platform=text("ANDROID",10,CinemaUi.WHITE,true);
        pad(platform,10,7,10,7);
        platform.setBackground(CinemaUi.shape(this,0xda161c2c,10));
        FrameLayout.LayoutParams platformLp=new FrameLayout.LayoutParams(
                -2,-2,Gravity.TOP|Gravity.LEFT);
        platformLp.leftMargin=dp(10);platformLp.topMargin=dp(10);
        artFrame.addView(platform,platformLp);

        TextView fav=text(store.favorite(game.id)?"★":"☆",22,CinemaUi.GOLD,true);
        fav.setGravity(Gravity.CENTER);
        fav.setBackground(CinemaUi.shape(this,0xf2191929,12));
        CinemaUi.actionable(this,fav,store.favorite(game.id)
                ?"Quitar favorito: "+game.title:"Marcar favorito: "+game.title);
        FrameLayout.LayoutParams favLp=new FrameLayout.LayoutParams(
                dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);
        favLp.topMargin=dp(7);favLp.rightMargin=dp(7);
        artFrame.addView(fav,favLp);
        fav.setOnClickListener(v->{
            store.toggleFavorite(game.id);
            showScreen(screen);
        });
        tile.addView(gap(11));
        TextView name=text(game.title,14,CinemaUi.WHITE,true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(name);
        tile.addView(gap(6));
        TextView more=text("ANDROID  •  VER FICHA",10,CinemaUi.GOLD,true);
        more.setLetterSpacing(.035f);
        tile.addView(more);
        CinemaUi.actionable(this,tile,"Abrir ficha de "+game.title);
        tile.setOnClickListener(v->openDetails(game));
        return tile;
    }

    private void showCollections() {
        body.addView(eyebrow("BIBLIOTECAS PERSONALES"));body.addView(gap(8));
        body.addView(text("Mis colecciones",28,CinemaUi.WHITE,true));body.addView(gap(7));
        body.addView(text("Organiza juegos de tu biblioteca sin mover ni desinstalar aplicaciones.",12,CinemaUi.MUTED,false));
        body.addView(gap(16));
        TextView add=CinemaUi.action(this,"＋ CREAR COLECCIÓN",true);
        body.addView(add,lp(-1,-2));add.setOnClickListener(v->editCollection(null));
        List<GameCollections.Collection> list=collections.all();
        List<OkubiUnifiedLibrary.Entry> libraryEntries=unifiedLibrary.all();
        LinearLayout sortControls=row();body.addView(gap(13));body.addView(sortControls);
        TextView sortName=CinemaUi.action(this,collectionOrder.equals("name")?"✓ NOMBRE":"NOMBRE",collectionOrder.equals("name"));
        TextView sortCount=CinemaUi.action(this,collectionOrder.equals("count")?"✓ MÁS JUEGOS":"MÁS JUEGOS",collectionOrder.equals("count"));
        sortControls.addView(sortName,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams countLp=new LinearLayout.LayoutParams(0,-2,1);countLp.leftMargin=dp(8);sortControls.addView(sortCount,countLp);
        sortName.setOnClickListener(v->setCollectionOrder("name"));
        sortCount.setOnClickListener(v->setCollectionOrder("count"));
        if(collectionOrder.equals("count"))list.sort((a,b)->{int n=Integer.compare(b.gameIds.size(),a.gameIds.size());return n!=0?n:a.name.compareToIgnoreCase(b.name);});
        else list.sort((a,b)->a.name.compareToIgnoreCase(b.name));
        if(list.isEmpty()){
            body.addView(gap(20));emptyCard("Tu primera colección","Crea una colección, por ejemplo: Aventuras, Relajantes o Favoritos para jugar.",false);
            return;
        }
        for(GameCollections.Collection c:list){
            body.addView(gap(14));
            LinearLayout tile=col();pad(tile,17,17,17,15);
            tile.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,18));body.addView(tile);
            tile.addView(text(c.name,19,CinemaUi.WHITE,true));tile.addView(gap(6));
            int present=0;
            for(OkubiUnifiedLibrary.Entry entry:libraryEntries)
                if(c.gameIds.contains(entry.id))present++;
            tile.addView(text(present+(present==1?" juego":" juegos")+" · Android y retro",11,CinemaUi.MUTED,false));
            LinearLayout previews=row();tile.addView(gap(13));tile.addView(previews);
            int shown=0;
            for(OkubiUnifiedLibrary.Entry candidate:libraryEntries){
                if(!c.gameIds.contains(candidate.id)||shown>=4)continue;
                shown++;
                if(candidate.kind==OkubiUnifiedLibrary.Kind.ANDROID){
                    ImageView thumb=new ImageView(this);
                    CoverArt.apply(this,thumb,store,candidate.androidGame);
                    LinearLayout.LayoutParams thumbP=lp(45,45);thumbP.rightMargin=dp(8);
                    thumb.setBackground(CinemaUi.outlined(this,CinemaUi.DEEP,9));
                    previews.addView(thumb,thumbP);
                }else{
                    TextView device=text(candidate.system,10,CinemaUi.GOLD,true);
                    device.setGravity(Gravity.CENTER);
                    device.setBackground(CinemaUi.outlined(this,CinemaUi.DEEP,9));
                    LinearLayout.LayoutParams previewP=lp(45,45);
                    previewP.rightMargin=dp(8);previews.addView(device,previewP);
                }
            }
            if(shown==0)previews.addView(text("Sin juegos visibles · Toca ORGANIZAR para elegir",11,CinemaUi.MUTED,false));
            tile.addView(gap(13));
            LinearLayout buttons=row();tile.addView(buttons);
            TextView view=CinemaUi.action(this,"VER JUEGOS",true);
            buttons.addView(view,new LinearLayout.LayoutParams(0,-2,1));
            view.setOnClickListener(v->openCollection(c));
            TextView manage=CinemaUi.action(this,"ORGANIZAR",false);
            LinearLayout.LayoutParams mp=lp(-2,-2);mp.leftMargin=dp(7);
            buttons.addView(manage,mp);manage.setOnClickListener(v->manageCollection(c));
            TextView edit=CinemaUi.action(this,"⋯",false);
            LinearLayout.LayoutParams ep=lp(-2,-2);ep.leftMargin=dp(7);
            buttons.addView(edit,ep);edit.setContentDescription("Cambiar nombre de "+c.name);edit.setOnClickListener(v->editCollection(c));
            TextView delete=CinemaUi.action(this,"×",false);
            LinearLayout.LayoutParams del=lp(-2,-2);del.leftMargin=dp(8);
            buttons.addView(delete,del);
            delete.setContentDescription("Eliminar colección "+c.name);
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Eliminar "+c.name+"?")
                .setMessage("Se eliminará solo esta colección. No se desinstalará ningún juego.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(d,w)->{collections.delete(c.id);showScreen(Screen.COLLECTIONS);}).show());
        }
    }
    private void setCollectionOrder(String order){
        collectionOrder=order;
        getPreferences(MODE_PRIVATE).edit().putString("collection_order_v06",order).apply();
        showScreen(Screen.COLLECTIONS);
    }
    private void manageCollection(GameCollections.Collection target){
        List<OkubiUnifiedLibrary.Entry> all=unifiedLibrary.all();
        if(all.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Primero añade juegos")
                .setMessage("Agrega juegos Android o importa ROMs desde la biblioteca retro.")
                .setPositiveButton("Entendido",null).show();
            return;
        }
        all.sort((a,b)->a.title.compareToIgnoreCase(b.title));
        String[] names=new String[all.size()];
        boolean[] checked=new boolean[all.size()];
        for(int i=0;i<all.size();i++){
            OkubiUnifiedLibrary.Entry entry=all.get(i);
            names[i]=entry.title+" · "+entry.system;
            checked[i]=target.gameIds.contains(entry.id);
        }
        new AlertDialog.Builder(this).setTitle("Organizar · "+target.name)
            .setMultiChoiceItems(names,checked,(dialog,which,on)->checked[which]=on)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(dialog,which)->{
                // Do not discard IDs for temporarily missing or hidden games.
                Set<String> members=new HashSet<>(target.gameIds);
                for(int i=0;i<all.size();i++){
                    String id=all.get(i).id;
                    if(checked[i])members.add(id);else members.remove(id);
                }
                collections.replaceMembership(target.id,members);
                showScreen(screen==Screen.COLLECTION_DETAIL
                        ?Screen.COLLECTION_DETAIL:Screen.COLLECTIONS);
            }).show();
    }
    private void editCollection(GameCollections.Collection collection){
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(15);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        input.setHint("Nombre de la colección");
        if(collection!=null)input.setText(collection.name);
        LinearLayout wrap=col();pad(wrap,20,4,20,2);wrap.addView(input);
        new AlertDialog.Builder(this).setTitle(collection==null?"Crear colección":"Cambiar nombre")
            .setView(wrap).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                boolean ok=collection==null?collections.create(input.getText().toString())
                    :collections.rename(collection.id,input.getText().toString());
                if(!ok)Toast.makeText(this,"Usa otro nombre (1 a 32 caracteres; máximo 20 listas).",Toast.LENGTH_LONG).show();
                showScreen(screen==Screen.COLLECTION_DETAIL?Screen.COLLECTION_DETAIL:Screen.COLLECTIONS);
            }).show();
    }
    /** Native CINEMA collection screen, not a generic AlertDialog with titles. */
    private void openCollection(GameCollections.Collection collection){
        activeCollectionId=collection.id;
        showScreen(Screen.COLLECTION_DETAIL);
    }
    private void showCollectionDetail(){
        GameCollections.Collection selected=null;
        for(GameCollections.Collection c:collections.all())if(c.id.equals(activeCollectionId)){selected=c;break;}
        if(selected==null){showScreen(Screen.COLLECTIONS);return;}
        final GameCollections.Collection collection=selected;
        TextView back=CinemaUi.action(this,"‹  MIS COLECCIONES",false);
        body.addView(back,lp(-1,-2));back.setOnClickListener(v->showScreen(Screen.COLLECTIONS));
        body.addView(gap(18));
        LinearLayout hero=col();pad(hero,21,23,21,23);
        hero.setBackground(CinemaUi.gradient(this,0xff33465c,0xff161c2b,22));
        body.addView(hero,lp(-1,-2));
        hero.addView(eyebrow("COLECCIÓN PERSONAL · OKUBI / CINEMA"));hero.addView(gap(13));
        hero.addView(text(collection.name,29,CinemaUi.WHITE,true));hero.addView(gap(9));
        List<OkubiUnifiedLibrary.Entry> members=new ArrayList<>();
        for(OkubiUnifiedLibrary.Entry entry:unifiedLibrary.all())
            if(collection.gameIds.contains(entry.id))members.add(entry);
        hero.addView(text(members.size()+(members.size()==1?" juego seleccionado":" juegos seleccionados"),12,CinemaUi.MUTED,false));
        body.addView(gap(15));
        LinearLayout buttons=row();body.addView(buttons);
        TextView organize=CinemaUi.action(this,"ORGANIZAR JUEGOS",true);
        buttons.addView(organize,new LinearLayout.LayoutParams(0,-2,1));
        organize.setOnClickListener(v->manageCollection(collection));
        TextView rename=CinemaUi.action(this,"RENOMBRAR",false);
        LinearLayout.LayoutParams renameLp=lp(-2,-2);renameLp.leftMargin=dp(8);buttons.addView(rename,renameLp);
        rename.setOnClickListener(v->editCollection(collection));
        section("Juegos de esta colección","Toca una carátula para ver su ficha");
        if(members.isEmpty()){
            emptyCard("Tu colección está lista","Toca ORGANIZAR JUEGOS para elegir los primeros títulos.",false);
            return;
        }
        for(int i=0;i<members.size();i+=2){
            LinearLayout line=row();line.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams lineLp=lp(-1,-2);lineLp.bottomMargin=dp(10);body.addView(line,lineLp);
            for(int j=0;j<2;j++){
                int pos=i+j;
                if(pos>=members.size()){
                    line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));break;
                }
                LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(0,dp(224),1f);
                if(j==0)cardLp.rightMargin=dp(9);
                line.addView(unifiedTile(members.get(pos),pos),cardLp);
            }
        }
    }
    private void showProfile(){
        body.addView(eyebrow("IDENTIDAD DE JUGADOR"));body.addView(gap(8));
        body.addView(text("Tu perfil gaming",28,CinemaUi.WHITE,true));body.addView(gap(15));
        LinearLayout card=row();pad(card,17,19,17,19);
        card.setBackground(CinemaUi.gradient(this,0xff33445c,CinemaUi.DEEP,19));
        body.addView(card);
        TextView avatar=text(store.avatar(),33,CinemaUi.GOLD,true);
        avatar.setGravity(Gravity.CENTER);avatar.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,18));
        card.addView(avatar,lp(68,68));
        LinearLayout copy=col();LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);
        copyParams.leftMargin=dp(15);card.addView(copy,copyParams);
        copy.addView(eyebrow("MI PERFIL"));copy.addView(gap(7));
        copy.addView(text(store.nickname(),18,CinemaUi.WHITE,true));
        copy.addView(gap(5));copy.addView(text("Información guardada en este teléfono",10,CinemaUi.MUTED,false));
        body.addView(gap(13));
        TextView edit=CinemaUi.action(this,"EDITAR NOMBRE Y AVATAR",true);
        body.addView(edit,lp(-1,-2));edit.setOnClickListener(v->editProfile());
        section("Mi colección","Valores reales de tu biblioteca local");
        List<GameApp> all=games();int favorites=0,launched=0;
        GameApp mostRecent=null;long recentTime=0;
        for(GameApp g:all){if(store.favorite(g.id))favorites++;
            long t=store.lastLaunched(g.id);if(t>0)launched++;
            if(t>recentTime){recentTime=t;mostRecent=g;}}
        LinearLayout metrics=row();body.addView(metrics);
        metric(metrics,"JUEGOS",String.valueOf(all.size()));
        metric(metrics,"FAVORITOS",String.valueOf(favorites));
        metric(metrics,"ABIERTOS",String.valueOf(launched));
        body.addView(gap(16));
        int[] goals=new PersonalGoals(this).summary(store.selected());
        LinearLayout goalsBox=col();pad(goalsBox,16,15,16,16);
        goalsBox.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,16));
        body.addView(goalsBox);
        goalsBox.addView(eyebrow("OBJETIVOS PERSONALES"));goalsBox.addView(gap(8));
        goalsBox.addView(text(goals[1]+" de "+goals[0]+" completados",19,CinemaUi.WHITE,true));
        goalsBox.addView(gap(7));
        goalsBox.addView(text("Metas creadas por ti en las fichas. No son logros oficiales de Google Play.",11,CinemaUi.MUTED,false));
        body.addView(gap(20));
        if(mostRecent!=null){
            final GameApp recent=mostRecent;
            TextView last=CinemaUi.action(this,"ÚLTIMO JUEGO: "+recent.title+"  →",false);
            last.setMaxLines(2);body.addView(last,lp(-1,-2));last.setOnClickListener(v->openDetails(recent));
        }
        section("Resumen de actividad","Tiempo aproximado que Android observó en primer plano");
        LinearLayout insightArea=col();body.addView(insightArea);
        if(!ActivityStats.permitted(this)){
            TextView prompt=CinemaUi.action(this,"PERMITIR ESTADÍSTICAS DE USO  →",false);
            insightArea.addView(prompt,lp(-1,-2));
            insightArea.addView(gap(9));
            insightArea.addView(text("Permiso opcional. Sin autorización no mostraremos horas ni rankings.",12,CinemaUi.MUTED,false));
            prompt.setOnClickListener(v->showScreen(Screen.ACTIVITY));
        }else if(all.isEmpty()){
            insightArea.addView(text("Añade juegos a tu biblioteca para obtener un resumen.",13,CinemaUi.MUTED,false));
        }else{
            insightArea.addView(text("Consultando datos locales disponibles…",13,CinemaUi.MUTED,false));
            final List<GameApp> snapshot=new ArrayList<>(all);
            new Thread(()->{
                Set<String> pkgs=new HashSet<>();for(GameApp g:snapshot)pkgs.add(g.packageName);
                UsageInsights.Result durations=UsageInsights.read(getApplicationContext(),pkgs);
                long today=0,week=0,month=0;String topName="";long topMs=0;
                Set<String> countedPackages=new HashSet<>();
                for(GameApp g:snapshot){
                    if(!countedPackages.add(g.packageName))continue;
                    long day=durations.gameTotal(g.packageName,1);
                    long seven=durations.gameTotal(g.packageName,7);
                    long thirty=durations.gameTotal(g.packageName,30);
                    today+=day;week+=seven;month+=thirty;
                    if(seven>topMs){topMs=seven;topName=g.title;}
                }
                final long t=today,w=week,m=month,ranked=topMs;
                final String top=topName;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||screen!=Screen.PROFILE||insightArea.getParent()==null)return;
                    insightArea.removeAllViews();
                    if(!durations.available){insightArea.addView(text("Estadísticas no disponibles en este momento.",12,CinemaUi.MUTED,false));return;}
                    LinearLayout cells=row();insightArea.addView(cells);
                    metric(cells,"HOY",ActivityStats.format(t));metric(cells,"7 DÍAS",ActivityStats.format(w));
                    metric(cells,"30 DÍAS",ActivityStats.format(m));
                    insightArea.addView(gap(13));
                    insightArea.addView(text(ranked>0?"Más utilizado (7 días): "+top+" · "+ActivityStats.format(ranked):
                        "Sin actividad observada en los 7 días consultados.",12,CinemaUi.WHITE,false));
                    insightArea.addView(gap(9));
                    insightArea.addView(text("Datos diarios agregados de Android; algunos intervalos pueden estar incompletos.",11,CinemaUi.MUTED,false));
                });
            },"nexo-profile-usage").start();
        }
        section("Mi espacio","Colecciones y ajustes personalizados");
        TextView lists=CinemaUi.action(this,"MIS COLECCIONES  →",false);
        body.addView(lists,lp(-1,-2));lists.setOnClickListener(v->showScreen(Screen.COLLECTIONS));body.addView(gap(10));
        TextView settings=CinemaUi.action(this,"CENTRO DE CONTROL  →",false);
        body.addView(settings,lp(-1,-2));settings.setOnClickListener(v->showScreen(Screen.SETTINGS));
        body.addView(gap(16));
        body.addView(text("Las horas aparecen solamente con el permiso de uso. OKUBI no inventa logros oficiales.",11,CinemaUi.MUTED,false));
    }
    private void editProfile(){
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setText(store.nickname());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        LinearLayout wrap=col();pad(wrap,20,6,20,7);
        wrap.addView(text("Nombre visible",12,CinemaUi.MUTED,true));wrap.addView(input);
        wrap.addView(CinemaUi.gap(this,10));
        TextView avatarButton=CinemaUi.action(this,"AVATAR ACTUAL: "+store.avatar()+"  · TOCAR PARA CAMBIAR",false);
        wrap.addView(avatarButton);
        final String[] avatars={"✦","◈","★","☄","🎮","🚀","🛡"};
        final String[] selected={store.avatar()};
        avatarButton.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Elegir avatar")
            .setItems(avatars,(d,which)->{selected[0]=avatars[which];avatarButton.setText("AVATAR: "+selected[0]);}).show());
        new AlertDialog.Builder(this).setTitle("Editar perfil").setView(wrap)
            .setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()){Toast.makeText(this,"El nombre no puede estar vacío",Toast.LENGTH_SHORT).show();return;}
                store.setNickname(name);store.setAvatar(selected[0]);showScreen(Screen.PROFILE);
            }).show();
    }

    private void showActivity() {
        body.addView(eyebrow("TIEMPO OBSERVADO · LOCAL"));body.addView(gap(8));
        body.addView(text("Tu actividad",28,CinemaUi.WHITE,true));body.addView(gap(7));
        body.addView(text("Tiempo aproximado en primer plano, no horas de juego efectivas. Consultas privadas en tu Samsung.",12,CinemaUi.MUTED,false));
        body.addView(gap(20));
        if(!ActivityStats.permitted(this)) {
            LinearLayout prompt=col();pad(prompt,17,20,17,20);
            prompt.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,20));body.addView(prompt);
            prompt.addView(text("EL PERMISO ES OPCIONAL",11,CinemaUi.GOLD,true));
            prompt.addView(gap(12));
            prompt.addView(text("Para consultar estadísticas, Android requiere autorización en Acceso a datos de uso. Sin este permiso tu biblioteca y tus juegos siguen funcionando.",13,CinemaUi.WHITE,false));
            prompt.addView(gap(17));
            TextView request=CinemaUi.action(this,"AUTORIZAR EN AJUSTES  →",true);
            prompt.addView(request,lp(-1,-2));
            request.setOnClickListener(v->{try{
                Intent intent=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                intent.setData(Uri.parse("package:"+getPackageName()));
                startActivity(intent);
            } catch(Exception e){try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}
                catch(Exception ignored){Toast.makeText(this,"Abre Acceso a datos de uso en Ajustes",Toast.LENGTH_LONG).show();}}
            });
            return;
        }
        List<GameApp> snapshot=new ArrayList<>(games());
        if(snapshot.isEmpty()) {
            emptyCard("Todavía no hay juegos","Añade juegos a OKUBI para consultar su actividad.",false);return;
        }
        TextView status=text("Consultando datos agregados de Android…",12,CinemaUi.MUTED,false);
        body.addView(status);body.addView(gap(12));
        LinearLayout results=col();body.addView(results);
        Set<String> packages=new HashSet<>();for(GameApp game:snapshot)packages.add(game.packageName);
        new Thread(()->{
            UsageInsights.Result report=UsageInsights.read(getApplicationContext(),packages);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||screen!=Screen.ACTIVITY||results.getParent()==null)return;
                status.setText("Estadísticas agregadas por día · datos disponibles de Android");
                renderActivityInsights(results,snapshot,report);
            });
        },"nexo-usage-insights").start();
    }

    private void renderActivityInsights(LinearLayout area,List<GameApp> listed,UsageInsights.Result report) {
        area.removeAllViews();
        if(!ActivityStats.permitted(this)||!report.available){
            area.addView(text("Estadísticas no disponibles o permiso revocado. Revisa el acceso en Ajustes.",13,CinemaUi.MUTED,false));return;
        }
        LinearLayout periods=row();area.addView(periods);
        int[] choices={1,7,30};String[] labels={"HOY","7 DÍAS","30 DÍAS"};
        for(int i=0;i<choices.length;i++){
            final int period=choices[i];
            TextView option=CinemaUi.action(this,(usagePeriodDays==period?"✓ ":"")+labels[i],usagePeriodDays==period);
            LinearLayout.LayoutParams optionParams=new LinearLayout.LayoutParams(0,dp(49),1f);
            if(i>0)optionParams.leftMargin=dp(7);
            periods.addView(option,optionParams);option.setGravity(Gravity.CENTER);
            option.setContentDescription("Mostrar actividad "+labels[i]);
            option.setOnClickListener(v->{usagePeriodDays=period;renderActivityInsights(area,listed,report);});
        }
        area.addView(gap(13));
        TextView onlyFav=CinemaUi.action(this,usageFavoritesOnly?"★ SOLO FAVORITOS  ·  VER TODOS":"☆ FILTRAR SOLO FAVORITOS",false);
        area.addView(onlyFav,lp(-1,-2));onlyFav.setOnClickListener(v->{usageFavoritesOnly=!usageFavoritesOnly;renderActivityInsights(area,listed,report);});
        Set<String> included=new HashSet<>();List<GameApp> filtered=new ArrayList<>();
        for(GameApp game:listed)if(!usageFavoritesOnly||store.favorite(game.id)){
            if(included.add(game.packageName))filtered.add(game);
        }
        area.addView(gap(17));
        LinearLayout summary=row();area.addView(summary);
        metric(summary,"PERÍODO",usagePeriodDays==1?"HOY":usagePeriodDays+" DÍAS");
        metric(summary,"USO",ActivityStats.format(report.total(included,usagePeriodDays)));
        int active=0;for(String pkg:included)if(report.gameTotal(pkg,usagePeriodDays)>0)active++;
        metric(summary,"ACTIVOS",String.valueOf(active));
        area.addView(gap(19));
        area.addView(text("ACTIVIDAD POR DÍA",12,CinemaUi.GOLD,true));area.addView(gap(7));
        area.addView(text(usagePeriodDays==1?"Hoy · día actual":"Desliza horizontalmente para recorrer los días",11,CinemaUi.MUTED,false));
        area.addView(gap(13));
        long[] daily=report.chart(included);
        int start=UsageInsights.DAYS-usagePeriodDays;
        long maximum=0;for(int i=start;i<UsageInsights.DAYS;i++)maximum=Math.max(maximum,daily[i]);
        if(!report.hasRecords||maximum==0){
            TextView noData=text("No hay tiempo de uso observado para los juegos seleccionados en este período. Esto no significa necesariamente que no hayas jugado.",12,CinemaUi.MUTED,false);
            pad(noData,16,20,16,20);noData.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,16));area.addView(noData);
        }else{
            HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
            LinearLayout bars=row();bars.setGravity(Gravity.BOTTOM);pad(bars,1,6,1,4);
            horizontal.addView(bars);area.addView(horizontal);
            for(int i=start;i<UsageInsights.DAYS;i++){
                UsageInsights.Day day=report.days.get(i);long value=daily[i];
                LinearLayout column=col();column.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams cp=lp(usagePeriodDays==1?115:usagePeriodDays==7?43:32,177);
                if(i>start)cp.leftMargin=dp(usagePeriodDays==30?5:7);
                bars.addView(column,cp);
                String label=day.dayOfMonth+"/"+day.month;
                TextView valueText=text(value>0?ActivityStats.format(value):"0",9,CinemaUi.MUTED,false);
                valueText.setGravity(Gravity.CENTER);valueText.setMaxLines(1);
                column.addView(valueText);column.addView(gap(5));
                int height=value==0?dp(5):Math.max(dp(9),(int)Math.round(dp(115)*value/(double)maximum));
                View bar=new View(this);bar.setBackground(CinemaUi.gradient(this,value>0?CinemaUi.GOLD:CinemaUi.STROKE,CinemaUi.BLUE,7));
                column.addView(bar,lp(usagePeriodDays==30?19:24,height));
                column.addView(gap(8));
                TextView date=text(label,10,CinemaUi.MUTED,false);date.setGravity(Gravity.CENTER);column.addView(date);
                column.setContentDescription("Día "+label+": "+ActivityStats.format(value));
                column.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        }
        area.addView(gap(24));
        area.addView(text("JUEGOS MÁS UTILIZADOS",12,CinemaUi.GOLD,true));area.addView(gap(12));
        filtered.sort((a,b)->Long.compare(report.gameTotal(b.packageName,usagePeriodDays),report.gameTotal(a.packageName,usagePeriodDays)));
        if(filtered.isEmpty())area.addView(text("No hay favoritos que mostrar. Cambia el filtro.",12,CinemaUi.MUTED,false));
        int place=0;
        for(GameApp game:filtered){
            long value=report.gameTotal(game.packageName,usagePeriodDays);
            LinearLayout tile=row();pad(tile,11,11,11,11);tile.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,15));
            LinearLayout.LayoutParams tp=lp(-1,-2);tp.bottomMargin=dp(8);area.addView(tile,tp);
            TextView number=text(String.valueOf(++place),13,CinemaUi.GOLD,true);pad(number,4,6,9,6);tile.addView(number);
            ImageView icon=new ImageView(this);icon.setImageDrawable(game.icon);tile.addView(icon,lp(38,38));
            LinearLayout detail=col();LinearLayout.LayoutParams detailP=new LinearLayout.LayoutParams(0,-2,1f);detailP.leftMargin=dp(12);
            tile.addView(detail,detailP);detail.addView(text(game.title,13,CinemaUi.WHITE,true));detail.addView(gap(4));
            detail.addView(text("Tiempo en primer plano",10,CinemaUi.MUTED,false));
            tile.addView(text(ActivityStats.format(value),12,CinemaUi.GOLD,true));
            tile.setOnClickListener(v->openDetails(game));
        }
        area.addView(gap(12));
        TextView disclaimer=text("Fuente: estadísticas diarias agregadas de Android, con tu permiso. Los intervalos pueden incluir datos parciales o faltar por políticas del sistema. No se puede inferir tiempo efectivo de juego ni sesiones exactas. No enviamos datos a internet.",11,CinemaUi.MUTED,false);
        disclaimer.setLineSpacing(dp(3),1f);area.addView(disclaimer);
    }

    private void showSettings() {
        body.addView(eyebrow("TU SESIÓN GAMING"));body.addView(gap(9));
        body.addView(text("Centro de control",28,CinemaUi.WHITE,true));body.addView(gap(7));
        body.addView(text("Accesos a ajustes reales: OKUBI no modifica el rendimiento de otros juegos.",12,CinemaUi.MUTED,false));
        section("Estado del dispositivo","Datos consultados localmente al abrir esta pantalla");
        LinearLayout row=row();body.addView(row);
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        String battery="—";
        if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            if(level>=0 && scale>0)battery=Math.round(level*100f/scale)+"%";}
        metric(row,"BATERÍA",battery);
        String ram="—";
        try { ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
            ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mem);
            ram=String.format(Locale.getDefault(),"%.1f GB",mem.availMem/1073741824.0); }catch(Exception ignored){}
        metric(row,"RAM LIBRE",ram);
        String free="—";
        try{StatFs fs=new StatFs(Environment.getDataDirectory().getPath());free=String.format(Locale.getDefault(),"%.1f GB",fs.getAvailableBytes()/1073741824.0);}catch(Exception ignored){}
        metric(row,"ESPACIO",free);
        section("Apariencia CINEMA","Preferencias locales. No modifican otros juegos ni Android");
        TextView palette=CinemaUi.action(this,"PALETA · "+paletteName(),false);
        palette.setContentDescription("Cambiar paleta de OKUBI PLAY");
        body.addView(palette,lp(-1,-2));
        palette.setOnClickListener(v->choosePalette());
        body.addView(gap(10));
        TextView motion=CinemaUi.action(this,AppearancePrefs.reducedMotion(this)
            ?"✓ MOVIMIENTO REDUCIDO":"○ REDUCIR MOVIMIENTO",false);
        body.addView(motion,lp(-1,-2));
        motion.setOnClickListener(v->{AppearancePrefs.setReducedMotion(this,!AppearancePrefs.reducedMotion(this));showScreen(Screen.SETTINGS);});
        body.addView(gap(10));
        TextView density=CinemaUi.action(this,"BIBLIOTECA · "+(compact?"VISTA LISTA":"CARÁTULAS"),false);
        body.addView(density,lp(-1,-2));
        density.setOnClickListener(v->{compact=!compact;AppearancePrefs.setCompactLibrary(this,compact);showScreen(Screen.SETTINGS);});
        body.addView(gap(11));
        body.addView(text("Paleta, animación y vista de biblioteca se guardan en este teléfono. Se respeta el ajuste de animaciones del sistema.",11,CinemaUi.MUTED,false));
        section("Modo visual","Cambia la decoración de OKUBI, no el rendimiento de otros juegos");
        TextView lite=CinemaUi.action(this,liteInterface()?"✓ MODO LIGERO · ACTIVO":"○ ACTIVAR MODO LIGERO",false);
        lite.setContentDescription("Alternar modo ligero: oculta decoración adicional del inicio");
        body.addView(lite,lp(-1,-2));
        lite.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().putBoolean("cinema_lite_v07",!liteInterface()).apply();showScreen(Screen.SETTINGS);});
        body.addView(gap(9));
        body.addView(text("El modo ligero elimina elementos decorativos del inicio. No cierra procesos, no cambia CPU ni aumenta automáticamente los FPS.",11,CinemaUi.MUTED,false));
        section("Accesos del sistema","Confirma cada cambio en Ajustes de Android");
        tool("☾  No molestar","Reducir interrupciones",Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
        tool("◐  Pantalla","Brillo y visualización",Settings.ACTION_DISPLAY_SETTINGS);
        tool("⌁  Wi-Fi","Red y conectividad",Settings.ACTION_WIFI_SETTINGS);
        tool("⚡  Batería","Consumo y ahorro",Settings.ACTION_BATTERY_SAVER_SETTINGS);
        section("Respaldo y migración","Recupera partidas y ajustes de versiones anteriores sin borrarlas");
        body.addView(text("OKUBI UNIFIED admite un respaldo local .zip verificado. " +
                "Incluye partidas de GB/GBC/GBA y ajustes compatibles; nunca ROMs ni " +
                "portadas. Para recuperar datos privados de APK antiguas, usa el " +
                "asistente de rescate por ADB en tu computadora o restaura un .okubisave " +
                "desde el menú de cada juego. No desinstales las versiones antiguas.",
                12,CinemaUi.MUTED,false));
        body.addView(gap(12));
        TextView export=CinemaUi.action(this,"↑  EXPORTAR RESPALDO UNIFICADO",true);
        body.addView(export,lp(-1,-2));
        export.setOnClickListener(v->chooseUnifiedExport());
        body.addView(gap(9));
        TextView restore=CinemaUi.action(this,"✓  VERIFICAR E IMPORTAR RESPALDO",false);
        body.addView(restore,lp(-1,-2));
        restore.setOnClickListener(v->chooseUnifiedImport());
        body.addView(gap(9));
        body.addView(text("Importar nunca reemplaza partidas ni preferencias existentes. " +
                "Las ROMs se vuelven a seleccionar con el explorador de archivos de Android. " +
                "Al exportar, OKUBI reabre el ZIP y comprueba su integridad antes de confirmar.",
                11,CinemaUi.MUTED,false));
        section("Actualizaciones seguras","Una sola aplicación, partidas protegidas");
        body.addView(text(installedBuildInfo(),12,CinemaUi.WHITE,true));
        body.addView(gap(9));
        String installedCert = OkubiSigningFingerprint.currentSha256(this);
        body.addView(text("Certificado de esta instalación (SHA-256):\n" + installedCert,
                11,CinemaUi.MUTED,false));
        if (OkubiSigningFingerprint.isFingerprint(installedCert)) {
            TextView copyCert = CinemaUi.action(this,
                    "COPIAR HUELLA SHA-256 · COMPARAR ANTES DE ACTUALIZAR", false);
            body.addView(copyCert,lp(-1,-2));
            copyCert.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                            "Certificado público de OKUBI", installedCert));
                    Toast.makeText(this,"Huella pública copiada",Toast.LENGTH_SHORT).show();
                }
            });
        }
        body.addView(gap(9));
        body.addView(text("Antes de instalar otra APK, compara esta huella con la firma " +
                "SHA-256 de la APK candidata y verifica que su versionCode sea MAYOR. " +
                "Si no coinciden, NO desinstales. Exporta y verifica el respaldo antes " +
                "de cambiar de teléfono. Compartir esta huella pública NO comparte tu clave.",
                11,CinemaUi.MUTED,false));
        body.addView(gap(9));
        body.addView(text("Las actualizaciones estables deben conservar el identificador " +
                "bo.nexoplay.app.unified, utilizar la misma firma del propietario y " +
                "aumentar el código de versión. Las APK de prueba pueden tener firmas " +
                "distintas: no desinstales una versión con partidas sin exportar primero " +
                "tu respaldo desde Ajustes.",12,CinemaUi.MUTED,false));
        body.addView(gap(9));
        body.addView(text("OKUBI no instala actualizaciones en segundo plano. " +
                "La versión definitiva se publicará cuando se configure una única " +
                "clave privada de firma, conservada por el propietario.",
                11,CinemaUi.MUTED,false));
        section("Acerca de OKUBI","Una plataforma personal, no una ROM ni un optimizador milagroso");
        TextView about=text("OKUBI PLAY 1.0 · OKUBI / CINEMA\n\nTus juegos y favoritos se guardan en el dispositivo. La hora de última apertura solo refleja cuándo lanzaste un juego desde OKUBI. Las estadísticas opcionales son aproximaciones del tiempo en primer plano observado por Android, no tiempo efectivo de juego.\n\nNo requiere root, accesibilidad, internet ni modificar la pantalla de inicio de Samsung.",12,CinemaUi.MUTED,false);
        about.setLineSpacing(dp(4),1f);pad(about,17,18,17,18);about.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,16));body.addView(about);
    }
    private String installedBuildInfo() {
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(),0);
            long code=android.os.Build.VERSION.SDK_INT>=28
                    ? info.getLongVersionCode() : info.versionCode;
            return "Versión instalada: "+info.versionName+" ("+code+")"+
                    "\nPaquete: "+getPackageName();
        }catch(Exception error){
            return "Paquete Android: "+getPackageName();
        }
    }

    private void chooseUnifiedExport(){
        if(migrationBusy)return;
        Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/zip");
        picker.putExtra(Intent.EXTRA_TITLE,"OKUBI_UNIFIED_respaldo.zip");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{startActivityForResult(picker,REQUEST_UNIFIED_EXPORT);}
        catch(Exception error){Toast.makeText(this,
                "No se pudo abrir el selector de destino",Toast.LENGTH_LONG).show();}
    }
    private void chooseUnifiedImport(){
        if(migrationBusy)return;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/zip");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,REQUEST_UNIFIED_IMPORT);}
        catch(Exception error){Toast.makeText(this,
                "No se pudo abrir el respaldo",Toast.LENGTH_LONG).show();}
    }
    private void previewUnified(Uri uri) {
        if(uri==null||!"content".equals(uri.getScheme())||migrationBusy)return;
        migrationBusy=true;
        Toast.makeText(this,"Comprobando archivo y partidas...",Toast.LENGTH_SHORT).show();
        try{
            migrationQueue.execute(()->{
                OkubiUnifiedBackup.Preview preview=null;
                String error=null;
                try(InputStream source=getContentResolver().openInputStream(uri)){
                    preview=OkubiUnifiedBackup.inspect(this,source);
                }catch(Exception failure){
                    error=failure.getMessage();
                }
                final OkubiUnifiedBackup.Preview result=preview;
                final String problem=error;
                runOnUiThread(()->{
                    migrationBusy=false;
                    if(isFinishing()||isDestroyed())return;
                    if(problem!=null||result==null){
                        new AlertDialog.Builder(this)
                                .setTitle("Respaldo rechazado")
                                .setMessage("No se ha importado nada. "+
                                        (problem==null?"Archivo no válido":problem))
                                .setPositiveButton("Entendido",null).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("OKUBI · Verificación aprobada")
                            .setMessage(result.description()+"\n\n¿Importar estos datos?")
                            .setPositiveButton("Importar",(dialog,which)->
                                    transferUnified(uri,true))
                            .setNegativeButton("Cancelar",null).show();
                });
            });
        }catch(RejectedExecutionException error){
            migrationBusy=false;
            Toast.makeText(this,"No se pudo verificar el respaldo",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void transferUnified(Uri uri,boolean importing){
        if(uri==null||!"content".equals(uri.getScheme())||migrationBusy)return;
        migrationBusy=true;
        Toast.makeText(this,importing?"Verificando respaldo...":
                "Creando respaldo local...",Toast.LENGTH_SHORT).show();
        try {
            migrationQueue.execute(()->{
                String message;
                boolean success;
                try {
                    OkubiUnifiedBackup.Outcome outcome;
                    if(importing) {
                        try(InputStream in=getContentResolver().openInputStream(uri)){
                            outcome=OkubiUnifiedBackup.importFrom(this,in);
                        }
                    }else{
                        try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                            outcome=OkubiUnifiedBackup.exportTo(this,out);
                        }
                        // The SAF provider might truncate or fail to persist.
                        // Reopen the actual destination before announcing success.
                        try(InputStream check=getContentResolver().openInputStream(uri)){
                            OkubiUnifiedBackup.Preview verified=
                                    OkubiUnifiedBackup.inspect(this,check);
                            if(verified.newSaves+verified.existingSaves!=outcome.saves)
                                throw new java.io.IOException(
                                        "El archivo recuperado no coincide con lo exportado");
                        }
                    }
                    message=importing?outcome.describe():
                            "Respaldo creado y verificado: "+outcome.saves+
                            " archivos de partida. Guarda este ZIP fuera del teléfono.";
                    success=true;
                }catch(Exception error){
                    message=(importing
                            ? "No se pudo importar: "
                            : "Respaldo no verificado: no lo uses para migrar. ")+
                            error.getMessage();
                    success=false;
                }
                final String result=message;
                final boolean ok=success;
                runOnUiThread(()->{
                    migrationBusy=false;
                    if(isFinishing()||isDestroyed())return;
                    if(ok && importing) {
                        AppearancePrefs.apply(this);
                        compact=AppearancePrefs.compactLibrary(this);
                        store=new GameStore(this);
                        collections=new GameCollections(this);
                        unifiedLibrary=new OkubiUnifiedLibrary(this,catalog,store);
                        showScreen(Screen.SETTINGS);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(ok?"OKUBI · Respaldo completo":
                                    "OKUBI · No se pudo transferir")
                            .setMessage(result)
                            .setPositiveButton("Entendido",null).show();
                });
            });
        }catch(RejectedExecutionException error){
            migrationBusy=false;
            Toast.makeText(this,"La migración no está disponible",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String paletteName(){
        String value=AppearancePrefs.palette(this);
        return AppearancePrefs.BLUE.equals(value)?"AZUL NIEBLA":AppearancePrefs.JADE.equals(value)?"JADE SUAVE":"ROSA NEÓN";
    }
    private void choosePalette(){
        String[] choices={"Rosa neón (OKUBI)","Azul niebla","Jade suave"};
        String[] values={AppearancePrefs.ROSE,AppearancePrefs.BLUE,AppearancePrefs.JADE};
        int selected=AppearancePrefs.BLUE.equals(AppearancePrefs.palette(this))?1:
            AppearancePrefs.JADE.equals(AppearancePrefs.palette(this))?2:0;
        new AlertDialog.Builder(this).setTitle("Paleta de OKUBI / CINEMA")
            .setSingleChoiceItems(choices,selected,(dialog,which)->{
                AppearancePrefs.setPalette(this,values[which]);dialog.dismiss();showScreen(Screen.SETTINGS);
            }).setNegativeButton("Cancelar",null).show();
    }
    private void tool(String title,String description,String action){
        LinearLayout tile=row();pad(tile,16,14,16,14);tile.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,15));
        LinearLayout.LayoutParams t=lp(-1,70);t.bottomMargin=dp(9);body.addView(tile,t);
        LinearLayout inner=col();tile.addView(inner,new LinearLayout.LayoutParams(0,-2,1));
        inner.addView(text(title,14,CinemaUi.WHITE,true));inner.addView(gap(5));
        inner.addView(text(description,11,CinemaUi.MUTED,false));tile.addView(text("→",21,CinemaUi.GOLD,true));
        tile.setOnClickListener(v->{try{startActivity(new Intent(action));}
            catch(Exception e){Toast.makeText(this,"Ajuste no disponible en este dispositivo",Toast.LENGTH_SHORT).show();}});
    }
    private void buildNavigation(){
        View old=root.findViewWithTag("nexo_nav");
        if(old!=null)root.removeView(old);
        LinearLayout nav=row();
        nav.setTag("nexo_nav");
        pad(nav,5,6,5,6);
        nav.setBackground(CinemaUi.spotlight(
                this,0xff242436,0xff151521,21));
        nav.setElevation(dp(12));
        nav.setClipToOutline(true);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
                -1,dp(70),Gravity.BOTTOM);
        params.leftMargin=dp(12);
        params.rightMargin=dp(12);
        params.bottomMargin=dp(7);
        root.addView(nav,params);
        navItem(nav,"⌂","INICIO",Screen.HOME);
        navItem(nav,"▦","JUEGOS",Screen.LIBRARY);
        navItem(nav,"◇","RETRO",Screen.RETRO);
        navItem(nav,"✦","DESCUBRE",Screen.DISCOVER);
        navItem(nav,"◉","PERFIL",Screen.PROFILE);
    }
    private void navItem(LinearLayout bar,String symbol,String title,Screen target){
        boolean active=screen==target;
        LinearLayout item=col();
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2),dp(3),dp(2),dp(3));
        if(active)item.setBackground(CinemaUi.shape(this,CinemaUi.ELEVATED,15));
        bar.addView(item,new LinearLayout.LayoutParams(0,-1,1f));

        View indicator=new View(this);
        indicator.setBackground(CinemaUi.shape(this,CinemaUi.GOLD,4));
        indicator.setVisibility(active?View.VISIBLE:View.INVISIBLE);
        LinearLayout.LayoutParams indicatorLp=lp(24,3);
        indicatorLp.bottomMargin=dp(4);
        item.addView(indicator,indicatorLp);

        int color=active?CinemaUi.GOLD:CinemaUi.MUTED;
        TextView glyph=text(symbol,21,color,true);
        glyph.setGravity(Gravity.CENTER);
        item.addView(glyph,lp(-1,24));
        item.addView(gap(2));
        TextView caption=text(title,9,color,true);
        caption.setGravity(Gravity.CENTER);
        caption.setLetterSpacing(.035f);
        item.addView(caption);

        CinemaUi.actionable(this,item,
                (active?"Sección actual: ":"Ir a ")+title.toLowerCase(Locale.ROOT));
        item.setSelected(active);
        item.setOnClickListener(v->{
            if(screen!=target)showScreen(target);
            else scroll.smoothScrollTo(0,0);
        });
    }
    private void openDetails(GameApp game){
        Intent intent=new Intent(this,GameDetailActivity.class);intent.putExtra("game_id",game.id);
        startActivity(intent);
    }
    private void launchUnifiedEntry(OkubiUnifiedLibrary.Entry entry) {
        if(entry==null)return;
        if(entry.kind==OkubiUnifiedLibrary.Kind.ANDROID) {
            if(entry.androidGame!=null)launch(entry.androidGame);
        } else launchRetroEntry(entry);
    }

    private void launch(GameApp game){
        if(!store.launch(this,game)){
            Toast.makeText(this,"No se pudo abrir este juego. Comprueba si sigue instalado.",Toast.LENGTH_LONG).show();
            catalog.reload();showScreen(screen);
        }
    }
    private void openUnifiedDetails(OkubiUnifiedLibrary.Entry entry) {
        if(entry.kind==OkubiUnifiedLibrary.Kind.ANDROID) {
            openDetails(entry.androidGame);
            return;
        }
        // A native scrollable game sheet instead of the old unstyled list.
        // All original commands still delegate to the same, safe handlers.
        LinearLayout sheet=col();
        pad(sheet,18,9,18,12);
        ScrollView scrollSheet=new ScrollView(this);
        scrollSheet.setFillViewport(false);
        scrollSheet.addView(sheet);

        LinearLayout titleRow=row();
        FrameLayout poster=new FrameLayout(this);
        poster.setBackground(CinemaUi.spotlight(this,0xff48405c,0xff25213b,16));
        poster.setClipToOutline(true);
        LinearLayout.LayoutParams posterLp=lp(98,108);
        titleRow.addView(poster,posterLp);
        TextView console=text(entry.system,26,CinemaUi.WHITE,true);
        console.setGravity(Gravity.CENTER);
        poster.addView(console,new FrameLayout.LayoutParams(-1,-1));
        ImageView posterArt=new ImageView(this);
        posterArt.setTag(entry.id);
        posterArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(posterArt,new FrameLayout.LayoutParams(-1,-1));
        loadRetroCover(entry,posterArt);

        LinearLayout caption=col();
        LinearLayout.LayoutParams captionLp=new LinearLayout.LayoutParams(0,-2,1f);
        captionLp.leftMargin=dp(15);
        titleRow.addView(caption,captionLp);
        caption.addView(eyebrow("OKUBI  /  FICHA RETRO"));
        caption.addView(gap(8));
        TextView title=text(entry.title,20,CinemaUi.WHITE,true);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        caption.addView(title);
        caption.addView(gap(8));
        caption.addView(text(entry.kind==OkubiUnifiedLibrary.Kind.PERSONAL_ASSET
                ?"ARCHIVO PERSONAL  •  "+entry.system
                :"ROM IMPORTADA  •  "+entry.system,
                10,CinemaUi.MUTED,true));
        sheet.addView(titleRow);
        sheet.addView(gap(18));
        TextView note=text("Tus partidas y archivos permanecen en el almacenamiento "
                +"elegido. Las acciones de esta ficha no alteran el cartucho original.",
                11,CinemaUi.MUTED,false);
        sheet.addView(note);
        sheet.addView(gap(14));

        AlertDialog popup=new AlertDialog.Builder(this)
                .setView(scrollSheet)
                .setNegativeButton("Cerrar",null)
                .create();
        CinemaUi.actionable(this,poster,"Jugar "+entry.title);
        poster.setOnClickListener(v->{popup.dismiss();launchRetroEntry(entry);});
        popup.setOnDismissListener(dialog->coverTargets.remove(posterArt));
        String[] labels={
                "▶  JUGAR / ABRIR",
                entry.favorite?"★  QUITAR FAVORITO":"☆  AGREGAR FAVORITO",
                "▧  ELEGIR PORTADA PERSONAL",
                "↺  RESTAURAR PORTADA ORIGINAL",
                "▦  ORGANIZAR EN COLECCIONES",
                unifiedLibrary.isSpotlightPinned(entry)
                        ?"◆  DESTACADO EN INICIO":"◇  DESTACAR EN INICIO",
                "⊖  QUITAR DE LA BIBLIOTECA"
        };
        for(int i=0;i<labels.length;i++){
            final int which=i;
            TextView action=CinemaUi.action(this,labels[i],i==0);
            LinearLayout.LayoutParams rowLp=lp(-1,-2);
            rowLp.bottomMargin=dp(9);
            sheet.addView(action,rowLp);
            action.setOnClickListener(v->{
                popup.dismiss();
                switch(which){
                    case 0: launchRetroEntry(entry);break;
                    case 1:
                        unifiedLibrary.setFavorite(entry,!entry.favorite);
                        showScreen(screen);
                        break;
                    case 2: chooseRetroCover(entry);break;
                    case 3:
                        unifiedLibrary.setRetroCover(entry,null);
                        retroCovers.evictAll();
                        showScreen(screen);
                        break;
                    case 4: chooseCollectionsFor(entry);break;
                    case 5: pinSpotlight(entry);break;
                    case 6: confirmHideRetro(entry);break;
                    default: break;
                }
            });
        }
        popup.show();
    }

    private void launchRetroEntry(OkubiUnifiedLibrary.Entry entry) {
        boolean gba="GBA".equals(entry.system);
        boolean snes="SNES".equals(entry.system);
        boolean internal=(snes
                ? bo.nexoplay.app.retro.SameBoyNative.isSnesAvailable()
                : gba ? bo.nexoplay.app.retro.SameBoyNative.isGbaAvailable()
                : ("GB".equals(entry.system)||"GBC".equals(entry.system))
                && bo.nexoplay.app.retro.SameBoyNative.isAvailable());
        if(entry.kind==OkubiUnifiedLibrary.Kind.PERSONAL_ASSET) {
            if(!internal || !bo.nexoplay.app.retro.OkubiPersonalLibrary.exists(this,entry.asset)){
                Toast.makeText(this,"El cartucho o el motor GBA no está disponible",Toast.LENGTH_LONG).show();
                return;
            }
            Intent game=new Intent(this,bo.nexoplay.app.retro.OkubiGameBoyActivity.class);
            game.putExtra(bo.nexoplay.app.retro.OkubiPersonalLibrary.EXTRA_ASSET,entry.asset);
            game.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_NAME,entry.title);
            game.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_SYSTEM,entry.system);
            try {
                startActivity(game);
                unifiedLibrary.markOpened(entry);
            }catch(Exception error){
                Toast.makeText(this,"No se pudo abrir el cartucho",Toast.LENGTH_LONG).show();
            }
            return;
        }
        if(entry.uri==null)return;
        Uri uri=Uri.parse(entry.uri);
        if(!"content".equals(uri.getScheme())){
            Toast.makeText(this,"Vuelve a importar este archivo retro",Toast.LENGTH_LONG).show();
            return;
        }
        if(internal) {
            Intent game=new Intent(this,bo.nexoplay.app.retro.OkubiGameBoyActivity.class);
            game.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_ROM,uri);
            game.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_NAME,entry.title);
            game.putExtra(bo.nexoplay.app.retro.OkubiGameBoyActivity.EXTRA_SYSTEM,entry.system);
            game.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(game);
                unifiedLibrary.markOpened(entry);
            }catch(SecurityException | IllegalArgumentException error){
                Toast.makeText(this,"No se puede leer la ROM. Vuelve a importarla.",Toast.LENGTH_LONG).show();
            }
        }else if(gba||"GB".equals(entry.system)||"GBC".equals(entry.system)
                ||"NDS".equals(entry.system)||"N64".equals(entry.system)
                ||bo.nexoplay.app.retro.RetroExpansionFormats.isExternalClassic(
                        entry.system)){
            bo.nexoplay.app.retro.RetroExternalLauncher.open(this,uri,entry.system);
            // An external chooser may be cancelled: do not invent play history.
        }else{
            Toast.makeText(this,"Solo biblioteca: no hay motor configurado para "
                    +entry.system,Toast.LENGTH_LONG).show();
        }
    }

    private void chooseCollectionsFor(OkubiUnifiedLibrary.Entry entry){
        List<GameCollections.Collection> all=collections.all();
        if(all.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Sin colecciones")
                .setMessage("Crea primero una colección desde Mis colecciones.")
                .setPositiveButton("Ir a colecciones",(dialog,which)->showScreen(Screen.COLLECTIONS))
                .setNegativeButton("Cancelar",null).show();
            return;
        }
        String[] names=new String[all.size()];
        boolean[] checked=new boolean[all.size()];
        for(int i=0;i<all.size();i++){
            GameCollections.Collection group=all.get(i);
            names[i]=group.name;
            checked[i]=group.gameIds.contains(entry.id);
        }
        new AlertDialog.Builder(this).setTitle("Colecciones · "+entry.title)
            .setMultiChoiceItems(names,checked,(dialog,which,on)->checked[which]=on)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(dialog,which)->{
                for(int i=0;i<all.size();i++)
                    collections.setMembership(all.get(i).id,entry.id,checked[i]);
                Toast.makeText(this,"Colecciones actualizadas",Toast.LENGTH_SHORT).show();
            }).show();
    }

    private void confirmHideRetro(OkubiUnifiedLibrary.Entry entry){
        if(entry.kind==OkubiUnifiedLibrary.Kind.PERSONAL_ASSET){
            Toast.makeText(this,"Los cartuchos incluidos se administran en la sección Retro",Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Quitar de la biblioteca")
            .setMessage("Se oculta solo esta entrada. El archivo ROM, sus partidas, "
                    +"portada, favoritos y colecciones se conservan. "
                    +"Puedes volver a agregarla desde Importar retro.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Quitar",(dialog,which)->{
                bo.nexoplay.app.retro.OkubiPokemonRepository.remove(this,entry.uri);
                showScreen(screen);
            }).show();
    }

    private void chooseRetroCover(OkubiUnifiedLibrary.Entry entry) {
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pendingCoverEntryId=entry.id;
        try {
            startActivityForResult(picker,REQUEST_RETRO_COVER);
        }catch(Exception error){
            pendingCoverEntryId=null;
            Toast.makeText(this,"No se pudo abrir el selector de portada",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_UNIFIED_EXPORT || requestCode==REQUEST_UNIFIED_IMPORT) {
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            Uri selected=data.getData();
            if(requestCode==REQUEST_UNIFIED_EXPORT) {
                transferUnified(selected,false);
            }else{
                // Verify the whole archive BEFORE asking to import. Verification
                // never modifies live saved games or SharedPreferences.
                previewUnified(selected);
            }
            return;
        }
        if(requestCode!=REQUEST_RETRO_COVER)return;
        String id=pendingCoverEntryId;pendingCoverEntryId=null;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null||id==null)return;
        OkubiUnifiedLibrary.Entry entry=unifiedLibrary.find(id);
        if(entry==null||entry.kind==OkubiUnifiedLibrary.Kind.ANDROID)return;
        Uri image=data.getData();
        if(!"content".equals(image.getScheme()))return;
        try {
            getContentResolver().takePersistableUriPermission(image,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            unifiedLibrary.setRetroCover(entry,image);
            retroCovers.evictAll();
            showScreen(screen);
        }catch(SecurityException error){
            Toast.makeText(this,"No se pudo conservar permiso para esa portada",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadRetroCover(OkubiUnifiedLibrary.Entry entry,ImageView view){
        String image=unifiedLibrary.retroCover(entry);
        if(image.isEmpty())return;
        coverTargets.put(view,new RetroCoverTarget(entry.id,image));
        scanVisibleRetroCovers();
    }

    private void scanVisibleRetroCovers(){
        if(coverScanQueued||scroll==null||coverWorker.isShutdown())return;
        coverScanQueued=true;
        scroll.post(()->{
            coverScanQueued=false;
            if(isFinishing()||isDestroyed())return;
            for(Map.Entry<ImageView,RetroCoverTarget> pair:coverTargets.entrySet()){
                ImageView targetView=pair.getKey();
                RetroCoverTarget target=pair.getValue();
                boolean visible=targetView.getWindowToken()!=null
                        && targetView.getGlobalVisibleRect(coverViewport);
                if(!visible){
                    // Invisible tiles must not keep decoded bitmaps alive.
                    if(target.displayed){
                        targetView.setImageDrawable(null);
                        target.displayed=false;
                    }
                    continue;
                }
                Bitmap cached=retroCovers.get(target.cacheKey);
                if(cached!=null){
                    if(!target.displayed){
                        targetView.setImageBitmap(cached);
                        target.displayed=true;
                    }
                    continue;
                }
                if(target.pending||target.failed)continue;
                target.pending=true;
                WeakReference<ImageView> weakView=new WeakReference<>(targetView);
                try{
                    coverWorker.execute(()->{
                        Bitmap bitmap=decodeRetroCover(target.uri);
                        final Bitmap ready=bitmap;
                        runOnUiThread(()->{
                            target.pending=false;
                            if(isFinishing()||isDestroyed())return;
                            ImageView current=weakView.get();
                            if(current!=null && coverTargets.get(current)==target){
                                if(ready==null)target.failed=true;
                                else{
                                    retroCovers.put(target.cacheKey,ready);
                                    if(current.getGlobalVisibleRect(coverViewport)){
                                        current.setImageBitmap(ready);
                                        target.displayed=true;
                                    }
                                }
                            }
                            scanVisibleRetroCovers();
                        });
                    });
                }catch(java.util.concurrent.RejectedExecutionException rejected){
                    target.pending=false;
                    // The next completed decode or scroll will retry.
                }
            }
        });
    }

    /** Called only on the cover worker; never decode documents on the UI thread. */
    private Bitmap decodeRetroCover(String address){
        try{
            Uri uri=Uri.parse(address);
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)return null;
                BitmapFactory.decodeStream(input,null,bounds);
            }
            if(bounds.outWidth<1||bounds.outHeight<1
                    ||bounds.outWidth>12000||bounds.outHeight>12000)return null;
            BitmapFactory.Options scaled=new BitmapFactory.Options();
            scaled.inSampleSize=1;
            while(Math.max(bounds.outWidth/scaled.inSampleSize,
                    bounds.outHeight/scaled.inSampleSize)>384)
                scaled.inSampleSize*=2;
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)return null;
                return BitmapFactory.decodeStream(input,null,scaled);
            }
        }catch(Exception | OutOfMemoryError failure){
            return null;
        }
    }

    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(level>=TRIM_MEMORY_RUNNING_LOW){
            for(Map.Entry<ImageView,RetroCoverTarget> pair:coverTargets.entrySet()){
                pair.getKey().setImageDrawable(null);
                pair.getValue().displayed=false;
            }
            retroCovers.evictAll();
        }
    }

    @Override protected void onDestroy(){
        coverTargets.clear();
        coverWorker.shutdownNow();
        migrationQueue.shutdownNow();
        retroCovers.evictAll();
        super.onDestroy();
    }

    private void showSelector(){
        catalog.forceReload();
        List<GameApp> available=catalog.available();
        if(available.isEmpty()){
            new AlertDialog.Builder(this).setTitle("No encontramos aplicaciones")
                .setMessage("Comprueba que tengas aplicaciones instaladas que aparezcan en el inicio del teléfono.")
                .setPositiveButton("Entendido",null).show();return;
        }
        Set<String> chosen=new HashSet<>(store.selected());
        LinearLayout layout=col();pad(layout,16,3,16,4);
        EditText search=new EditText(this);search.setSingleLine(true);
        search.setHint("Buscar juego o aplicación...");search.setTextSize(14);
        layout.addView(search,lp(-1,54));
        TextView tip=text("Selecciona solo las aplicaciones que quieres incluir como juegos.",11,CinemaUi.MUTED,false);
        layout.addView(tip);layout.addView(gap(8));
        ListView list=new ListView(this);list.setDividerHeight(0);layout.addView(list,lp(-1,355));
        List<GameApp> visible=new ArrayList<>(available);
        BaseAdapter adapter=new BaseAdapter(){
            @Override public int getCount(){return visible.size();}
            @Override public Object getItem(int i){return visible.get(i);}
            @Override public long getItemId(int i){return i;}
            @Override public View getView(int position,View convert,ViewGroup parent){
                GameApp app=visible.get(position);LinearLayout item=row();pad(item,4,8,4,8);
                ImageView icon=new ImageView(MainActivity.this);icon.setImageDrawable(app.icon);item.addView(icon,lp(38,38));
                TextView title=text(app.title,14,CinemaUi.WHITE,false);
                LinearLayout.LayoutParams name=new LinearLayout.LayoutParams(0,-2,1);name.leftMargin=dp(12);
                item.addView(title,name);item.addView(text(chosen.contains(app.id)?"✓":"○",21,chosen.contains(app.id)?CinemaUi.GOLD:CinemaUi.MUTED,true));
                item.setOnClickListener(v->{if(!chosen.remove(app.id))chosen.add(app.id);notifyDataSetChanged();});return item;
            }
        };
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                visible.clear();String needle=s.toString().toLowerCase(Locale.ROOT);
                for(GameApp game:available)if(game.title.toLowerCase(Locale.ROOT).contains(needle))visible.add(game);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable e){}
        });
        new AlertDialog.Builder(this).setTitle("Añadir a tu biblioteca")
            .setView(layout).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{store.saveSelected(chosen);showScreen(screen);})
            .show();
    }
}
