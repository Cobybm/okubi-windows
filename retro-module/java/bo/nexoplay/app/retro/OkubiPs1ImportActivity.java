package bo.nexoplay.app.retro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit Android file-import screen for the *built-in* PCSX ReARMed core.
 * The user may select one CHD/PBP, or CUE plus ALL associated BIN files at
 * once. Progress and cancellation are real; no external emulator is opened.
 */
public final class OkubiPs1ImportActivity extends Activity {
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private volatile boolean completed;
    private TextView progressText;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(Color.rgb(18,14,32));
        getWindow().setNavigationBarColor(Color.rgb(18,14,32));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,48,28,32);
        root.setBackgroundColor(Color.rgb(19,14,32));
        TextView title=new TextView(this);
        title.setText("OKUBI 遊  ·  PLAYSTATION 1");
        title.setTextColor(Color.rgb(255,77,178));
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView note=new TextView(this);
        note.setText("Preparando el disco para el motor PS1 integrado.\n"
                + "El archivo original permanece intacto. Una imagen CHD "
                + "o PBP necesita un solo archivo; CUE requiere "
                + "seleccionar también todos sus BIN.");
        note.setTextColor(Color.WHITE);
        note.setTextSize(15);
        note.setPadding(0,32,0,30);
        root.addView(note);
        ProgressBar bar=new ProgressBar(this,null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        root.addView(bar,new LinearLayout.LayoutParams(-1,36));
        progressText=new TextView(this);
        progressText.setText("Comprobando documentos...");
        progressText.setTextColor(Color.rgb(180,180,207));
        progressText.setTextSize(13);
        progressText.setPadding(0,16,0,30);
        root.addView(progressText);
        Button cancel=new Button(this);
        cancel.setText("CANCELAR IMPORTACIÓN");
        cancel.setOnClickListener(v->{
            cancelled.set(true);
            cancel.setEnabled(false);
            progressText.setText("Cancelando... No se borrarán partidas ni discos originales.");
        });
        root.addView(cancel);
        setContentView(root);
        List<Uri> selected=new ArrayList<>();
        ClipData clip=getIntent().getClipData();
        if(clip!=null) {
            for(int i=0;i<clip.getItemCount();i++)
                selected.add(clip.getItemAt(i).getUri());
        }
        if(selected.isEmpty() && getIntent().getData()!=null)
            selected.add(getIntent().getData());
        if(selected.isEmpty()) {
            error("No se seleccionó ningún archivo PS1.");return;
        }
        Thread copy=new Thread(()->{
            try {
                OkubiPs1DiscStore.ImportResult result=OkubiPs1DiscStore.stage(
                        this,selected,new OkubiPs1DiscStore.Progress(){
                    private long shownAt;
                    @Override public boolean cancelled(){return cancelled.get();}
                    @Override public void onProgress(int done,int total,long bytes) {
                        long now=android.os.SystemClock.uptimeMillis();
                        if(now-shownAt<180 && done<total)return;
                        shownAt=now;
                        final String label=done+" / "+total+" archivos · "+
                                (bytes/(1024*1024))+" MiB copiados";
                        runOnUiThread(()->{
                            if(!isFinishing()&&!isDestroyed())
                                progressText.setText(label);
                        });
                    }
                });
                if(cancelled.get())return;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    completed=true;
                    Intent play=new Intent(this,OkubiPs1PlayerActivity.class);
                    play.putExtra(OkubiPs1PlayerActivity.EXTRA_PATH,
                            result.disc.toString());
                    play.putExtra(OkubiPs1PlayerActivity.EXTRA_HASH,result.sha256);
                    play.putExtra(OkubiPs1PlayerActivity.EXTRA_TITLE,
                            result.disc.getFileName().toString());
                    startActivity(play);
                    finish();
                });
            }catch(IOException problem) {
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed()&&!cancelled.get())
                        error(problem.getMessage());
                    else if(cancelled.get())finish();
                });
            }
        },"okubi-ps1-import");
        copy.start();
    }

    private void error(String message) {
        new AlertDialog.Builder(this)
                .setTitle("OKUBI · No se pudo preparar PS1")
                .setMessage(message+"\n\nSelecciona CHD/PBP o todos los "
                        +"archivos CUE/BIN del mismo juego.")
                .setPositiveButton("Entendido",(dialog,which)->finish())
                .setCancelable(false).show();
    }

    @Override public void onBackPressed() {
        cancelled.set(true);
        if(completed)super.onBackPressed();
        else progressText.setText("Cancelando copia del disco...");
    }
}
