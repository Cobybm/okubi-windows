package bo.nexoplay.app.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Native PlayStation 1 player INSIDE OKUBI Android.
 * Uses PCSX ReARMed Libretro from this APK, never DuckStation or RetroArch.
 * No commercial ROM or BIOS is included. User-owned discs were imported by
 * OkubiPs1DiscStore to private storage and are opened by native file path.
 *
 * All core calls/save/state and video/audio writes run on ONE worker thread.
 * UI thread owns views; Android OS lifecycle pauses emulation safely.
 */
public final class OkubiPs1PlayerActivity extends Activity {
    public static final String EXTRA_PATH="okubi.ps1.path";
    public static final String EXTRA_HASH="okubi.ps1.hash";
    public static final String EXTRA_TITLE="okubi.ps1.title";
    private static final int BG=Color.rgb(17,12,28);
    private static final int PINK=Color.rgb(248,67,162);
    private static final int WHITE=Color.rgb(245,241,250);
    private static final int WIDTH=640, HEIGHT=480;
    private final AtomicBoolean running=new AtomicBoolean(true);
    private final AtomicBoolean saveRequested=new AtomicBoolean();
    private final AtomicBoolean loadRequested=new AtomicBoolean();
    private final AtomicInteger touchButtons=new AtomicInteger();
    private final AtomicInteger hardwareButtons=new AtomicInteger();
    private final AtomicInteger joystickButtons=new AtomicInteger();
    private volatile boolean paused=true;
    private volatile boolean mute;
    private volatile int speed=1;
    private volatile SurfaceHolder videoHolder;
    private Thread worker;
    private Bitmap bitmap;
    private int[] pixels;
    private TextView status;
    private Path disc;
    private Path saves;
    private String title;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        String original=getIntent().getStringExtra(EXTRA_PATH);
        String identity=getIntent().getStringExtra(EXTRA_HASH);
        title=getIntent().getStringExtra(EXTRA_TITLE);
        if(title==null || title.isBlank())title="Juego PlayStation";
        if(original==null || identity==null || !identity.matches("[a-f0-9]{64}")
                || !OkubiPs1Native.available()){
            unavailable("No se encontró el núcleo PS1 integrado en esta versión.");
            return;
        }
        try {
            Path root=OkubiPs1DiscStore.importsRoot(this).toRealPath();
            disc=Path.of(original).toRealPath();
            if(!disc.startsWith(root) || disc.equals(root)
                    || !Files.isRegularFile(disc,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("El disco no está en la biblioteca privada PS1.");
            saves=getFilesDir().toPath().resolve("ps1-saves").resolve(identity);
            Files.createDirectories(saves);
            Files.createDirectories(getFilesDir().toPath().resolve("ps1-system"));
        }catch(IOException error) {
            unavailable(error.getMessage());return;
        }
        bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        pixels=new int[WIDTH*HEIGHT];
        buildLayout();
        worker=new Thread(this::emulate,"okubi-ps1-libretro");
        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
        getSharedPreferences("okubi_ps1_help_v1",MODE_PRIVATE)
                .edit().putBoolean("opened",true).apply();
        if(!getSharedPreferences("okubi_ps1_help_v1",MODE_PRIVATE)
                .getBoolean("seen",false)) {
            getWindow().getDecorView().post(()->new AlertDialog.Builder(this)
                    .setTitle("OKUBI 遊 · Controles de PlayStation 1")
                    .setMessage("✕ confirma y ○ regresa en la mayoría de juegos.\n"
                            +"La cruceta mueve al personaje. △ y □ son los otros "
                            +"botones principales; START abre el menú.\n\n"
                            +"Guardar almacena un estado rápido y las tarjetas "
                            +"de memoria se conservan automáticamente.\n"
                            +"Los mandos Bluetooth/USB compatibles también sirven.")
                    .setPositiveButton("ENTENDIDO",(dialog,which)->
                        getSharedPreferences("okubi_ps1_help_v1",MODE_PRIVATE)
                            .edit().putBoolean("seen",true).apply())
                    .show());
        }
    }

    private void unavailable(String reason) {
        new AlertDialog.Builder(this).setTitle("OKUBI · PS1 no disponible")
                .setMessage(reason+"\n\nNo hace falta descargar otro emulador. "
                        +"Utiliza una APK de OKUBI con PCSX ReARMed incorporado.")
                .setPositiveButton("Volver",(dialog,which)->finish())
                .setCancelable(false).show();
    }
    private int dp(int n) {
        return (int)(getResources().getDisplayMetrics().density*n+0.5f);
    }
    private TextView text(String caption,int size,int color) {
        TextView view=new TextView(this);
        view.setText(caption);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }
    private GradientDrawable background(int fill,int stroke) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(13));
        d.setStroke(dp(1),stroke);
        return d;
    }
    private TextView action(String text,Runnable action) {
        TextView v=text(text,11,WHITE);
        v.setPadding(dp(10),dp(9),dp(10),dp(9));
        v.setBackground(background(Color.rgb(72,38,91),Color.rgb(135,69,160)));
        v.setOnClickListener(view->action.run());
        return v;
    }
    private LinearLayout row() {
        LinearLayout result=new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER);
        return result;
    }
    private TextView pad(String caption,int bit) {
        TextView v=text(caption,20,WHITE);
        v.setBackground(background(Color.rgb(76,51,110),
                Color.rgb(159,96,196)));
        v.setAlpha(.74f);
        v.setOnTouchListener((view,event)->{
            int what=event.getActionMasked();
            if(what==MotionEvent.ACTION_DOWN || what==MotionEvent.ACTION_POINTER_DOWN) {
                touchButtons.getAndUpdate(value->value|(1<<bit));
                v.setAlpha(.95f);return true;
            }
            if(what==MotionEvent.ACTION_UP || what==MotionEvent.ACTION_POINTER_UP
                    || what==MotionEvent.ACTION_CANCEL) {
                touchButtons.getAndUpdate(value->value&~(1<<bit));
                v.setAlpha(.74f);return true;
            }
            return true;
        });
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        return v;
    }
    private TextView empty() {
        TextView v=text("",12,WHITE);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        return v;
    }
    private LinearLayout controlsDpad() {
        LinearLayout column=new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first=row();first.addView(empty());first.addView(pad("▲",0));
        first.addView(empty());column.addView(first);
        LinearLayout middle=row();middle.addView(pad("◀",2));middle.addView(pad("▼",1));
        middle.addView(pad("▶",3));column.addView(middle);
        return column;
    }
    private LinearLayout controlsFace() {
        LinearLayout column=new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first=row();first.addView(pad("□",8));first.addView(pad("△",7));
        column.addView(first);
        LinearLayout second=row();second.addView(pad("○",4));second.addView(pad("✕",5));
        column.addView(second);
        return column;
    }
    private void buildLayout() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout header=row();
        header.setPadding(dp(5),dp(4),dp(5),dp(4));
        TextView name=text("OKUBI 遊 · PS1",13,PINK);
        header.addView(name,new LinearLayout.LayoutParams(0,dp(45),1));
        header.addView(action("GUARDAR",()->saveRequested.set(true)));
        header.addView(action("CARGAR",this::confirmLoad));
        header.addView(action("x1 / x2",()->{
            speed=speed==1?2:1;status("Velocidad "+speed+"× · audio también acelerado");
        }));
        header.addView(action("AUDIO",()->{
            mute=!mute;status(mute?"Audio silenciado":"Audio activado");
        }));
        header.addView(action("SALIR",this::finish));
        root.addView(header);
        FrameLayout scene=new FrameLayout(this);
        scene.setBackgroundColor(Color.BLACK);
        scene.setMotionEventSplittingEnabled(true);
        SurfaceView display=new SurfaceView(this);
        display.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                videoHolder=holder;
            }
            @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) {
                videoHolder=holder;
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                if(videoHolder==holder)videoHolder=null;
            }
        });
        scene.addView(display,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=row();
        overlay.setPadding(dp(7),dp(8),dp(7),dp(12));
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.addView(controlsDpad());
        LinearLayout middle=new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        middle.addView(pad("L1",10));middle.addView(pad("R1",11));
        LinearLayout ss=row();
        ss.addView(pad("SEL",13));ss.addView(pad("ST",12));
        middle.addView(ss);
        overlay.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
        overlay.addView(controlsFace());
        FrameLayout.LayoutParams overlayPosition=new FrameLayout.LayoutParams(
                -1,-2,Gravity.BOTTOM);
        scene.addView(overlay,overlayPosition);
        root.addView(scene,new LinearLayout.LayoutParams(-1,0,1));
        status=text("Cargando motor PS1 integrado · "+title,12,WHITE);
        status.setPadding(dp(6),dp(5),dp(6),dp(7));
        root.addView(status);
        setContentView(root);
    }

    private void status(String label) {
        runOnUiThread(()->{
            if(!isFinishing()&&!isDestroyed()&&status!=null)
                status.setText(label);
        });
    }
    private void confirmLoad() {
        new AlertDialog.Builder(this).setTitle("Cargar estado rápido")
                .setMessage("Se restaurará el último estado guardado de este "
                        +"juego. El progreso actual sin guardar podría perderse.")
                .setPositiveButton("CARGAR",(dialog,which)->loadRequested.set(true))
                .setNegativeButton("Cancelar",null).show();
    }
    private int keyBit(int keyCode) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:return 0;
            case KeyEvent.KEYCODE_DPAD_DOWN:return 1;
            case KeyEvent.KEYCODE_DPAD_LEFT:return 2;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return 3;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_Z:return 4; // circle
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_X:return 5; // cross
            case KeyEvent.KEYCODE_BUTTON_Y:return 7; // triangle
            case KeyEvent.KEYCODE_BUTTON_X:return 8; // square
            case KeyEvent.KEYCODE_BUTTON_L1:return 10;
            case KeyEvent.KEYCODE_BUTTON_R1:return 11;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_ENTER:return 12;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_SPACE:return 13;
            default:return -1;
        }
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event) {
        int bit=keyBit(keyCode);
        if(bit>=0) {
            hardwareButtons.getAndUpdate(value->value|(1<<bit));return true;
        }
        return super.onKeyDown(keyCode,event);
    }
    @Override public boolean onKeyUp(int keyCode,KeyEvent event) {
        int bit=keyBit(keyCode);
        if(bit>=0) {
            hardwareButtons.getAndUpdate(value->value&~(1<<bit));return true;
        }
        return super.onKeyUp(keyCode,event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if((event.getSource()&InputDevice.SOURCE_JOYSTICK)
                ==InputDevice.SOURCE_JOYSTICK
                && event.getAction()==MotionEvent.ACTION_MOVE) {
            float x=event.getAxisValue(MotionEvent.AXIS_X);
            float y=event.getAxisValue(MotionEvent.AXIS_Y);
            int bits=0;
            if(x<-.45f)bits|=1<<2;
            if(x>.45f)bits|=1<<3;
            if(y<-.45f)bits|=1<<0;
            if(y>.45f)bits|=1<<1;
            joystickButtons.set(bits);return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void draw(int[] frame) {
        SurfaceHolder holder=videoHolder;
        if(holder==null || !holder.getSurface().isValid())return;
        bitmap.setPixels(frame,0,WIDTH,0,0,WIDTH,HEIGHT);
        Canvas canvas=null;
        try {
            canvas=holder.lockCanvas();
            if(canvas==null)return;
            canvas.drawColor(Color.BLACK);
            int w=canvas.getWidth(),h=canvas.getHeight();
            int outW=Math.min(w,h*4/3);
            int outH=Math.min(h,w*3/4);
            Rect bounds=new Rect((w-outW)/2,(h-outH)/2,
                    (w+outW)/2,(h+outH)/2);
            Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bitmap,null,bounds,paint);
        }catch(RuntimeException error){
            // Rotation destroys the Surface asynchronously; next frame will
            // use the new holder, never touches the Libretro session.
        }finally{
            if(canvas!=null)try{holder.unlockCanvasAndPost(canvas);}
            catch(RuntimeException ignored){}
        }
    }
    private static void atomicWrite(Path file,byte[] data,int maximum)
            throws IOException {
        if(data==null || data.length==0 || data.length>maximum)
            throw new IOException("Guardado vacío o demasiado grande.");
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),".okubi-ps1-",".tmp");
        try {
            Files.write(temp,data);
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
                Files.copy(file,file.resolveSibling(file.getFileName()+".previous"),
                        StandardCopyOption.REPLACE_EXISTING);
            try{
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }catch(java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(temp);}
    }
    private void saveMemoryCard() throws IOException {
        byte[] card=OkubiPs1Native.nativeSaveRam();
        if(card!=null && card.length>0)
            atomicWrite(saves.resolve("memorycard.srm"),card,1024*1024);
    }
    private void restoreMemoryCard() throws IOException {
        Path card=saves.resolve("memorycard.srm");
        if(Files.isRegularFile(card,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=Files.readAllBytes(card);
            if(bytes.length==0 || bytes.length>1024*1024 ||
                    !OkubiPs1Native.nativeLoadRam(bytes))
                throw new IOException("Tarjeta de memoria anterior incompatible; no se modificó.");
        }
    }
    private AudioTrack audio(int rate) {
        try {
            int valid=Math.max(8000,Math.min(96000,rate));
            int min=AudioTrack.getMinBufferSize(valid,
                    AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)return null;
            AudioAttributes attrs=new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            AudioFormat format=new AudioFormat.Builder()
                    .setSampleRate(valid).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build();
            AudioTrack track=new AudioTrack(attrs,format,
                    Math.max(min*3,16384),AudioTrack.MODE_STREAM,
                    0);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED){track.release();return null;}
            track.play();return track;
        }catch(RuntimeException unavailable){return null;}
    }
    private void emulate() {
        AudioTrack speaker=null;
        boolean opened=false;
        try {
            String system=getFilesDir().toPath().resolve("ps1-system").toString();
            if(!OkubiPs1Native.nativeOpen(disc.toString(),system,saves.toString()))
                throw new IOException("El núcleo PCSX ReARMed no abrió el disco. "
                        +"Comprueba el formato, las pistas y la BIOS.");
            opened=true;
            restoreMemoryCard();
            int rate=OkubiPs1Native.nativeSampleRate();
            speaker=audio(rate);
            int lastSpeed=1;
            int[] frame=pixels;
            short[] samples=new short[8192];
            double fps=OkubiPs1Native.nativeFps();
            if(fps<40 || fps>120)fps=60;
            long baseInterval=(long)(1_000_000_000.0/fps);
            long next=System.nanoTime();
            long lastBattery=System.nanoTime();
            status("JUGANDO · "+title+" · F5/Guardar · PS1 dentro de OKUBI");
            while(running.get()) {
                if(paused){
                    LockSupport.parkNanos(25_000_000L);
                    next=System.nanoTime();continue;
                }
                if(saveRequested.getAndSet(false)){
                    byte[] state=OkubiPs1Native.nativeSaveState();
                    if(state==null)status("Este juego no permite estados rápidos.");
                    else {
                        atomicWrite(saves.resolve("slot1.state"),state,32*1024*1024);
                        saveMemoryCard();
                        status("Estado y tarjeta PS1 guardados");
                    }
                }
                if(loadRequested.getAndSet(false)){
                    Path saved=saves.resolve("slot1.state");
                    if(!Files.isRegularFile(saved,LinkOption.NOFOLLOW_LINKS))
                        status("Todavía no hay un estado rápido.");
                    else {
                        long n=Files.size(saved);
                        if(n>0 && n<=32L*1024*1024 &&
                                OkubiPs1Native.nativeLoadState(Files.readAllBytes(saved))){
                            touchButtons.set(0);hardwareButtons.set(0);
                            status("Estado PS1 restaurado");
                        } else status("Estado no compatible; no se modificó la partida.");
                    }
                }
                int input=touchButtons.get()|hardwareButtons.get()|joystickButtons.get();
                if((input&3)==3)input&=~3;
                if((input&12)==12)input&=~12;
                int count=OkubiPs1Native.nativeStep(input,frame,samples);
                if(count<0)throw new IOException("El núcleo PS1 dejó de producir fotogramas.");
                draw(frame);
                if(speaker!=null && !mute && count>0){
                    if(lastSpeed!=speed){
                        try{speaker.setPlaybackRate(rate*speed);}
                        catch(RuntimeException ignored){speaker.setPlaybackRate(rate);}
                        lastSpeed=speed;
                    }
                    speaker.write(samples,0,Math.min(count,samples.length),
                            AudioTrack.WRITE_NON_BLOCKING);
                }
                if(System.nanoTime()-lastBattery>30_000_000_000L){
                    saveMemoryCard();
                    lastBattery=System.nanoTime();
                }
                next+=Math.max(7_000_000L,baseInterval/speed);
                long remaining=next-System.nanoTime();
                if(remaining>0)LockSupport.parkNanos(remaining);
                else if(remaining<-baseInterval*5)next=System.nanoTime();
            }
        }catch(IOException|UnsatisfiedLinkError|RuntimeException error){
            status("PS1 · "+error.getMessage());
            runOnUiThread(()->{
                if(!isFinishing()&&!isDestroyed())
                    new AlertDialog.Builder(this)
                    .setTitle("OKUBI · PS1 interrumpido")
                    .setMessage(error.getMessage()
                            +"\nTus archivos originales y partidas anteriores siguen intactos.")
                    .setPositiveButton("VOLVER",(dialog,which)->finish()).show();
            });
        }finally {
            if(opened){
                try{saveMemoryCard();}catch(IOException error) {
                    status("No se pudo guardar la tarjeta PS1: "+error.getMessage());
                }
                OkubiPs1Native.nativeClose();
            }
            if(speaker!=null){speaker.stop();speaker.release();}
        }
    }
    @Override protected void onPause() {
        paused=true;super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();paused=false;
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        buildLayout();
    }
    @Override protected void onDestroy() {
        running.set(false);
        touchButtons.set(0);hardwareButtons.set(0);joystickButtons.set(0);
        super.onDestroy();
    }
}
