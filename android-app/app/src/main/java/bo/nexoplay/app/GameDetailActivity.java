package bo.nexoplay.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import java.util.List;
import java.text.DateFormat;
import java.util.Date;

/** Dedicated game profile. No fabricated playtime or achievements. */
public final class GameDetailActivity extends Activity {
    private GameCatalog catalog;
    private GameStore store;
    private GameCollections collections;
    private PersonalGoals personalGoals;
    private static final int PICK_COVER=55;
    private GameApp game;
    private LinearLayout content;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AppearancePrefs.apply(this);
        getWindow().setStatusBarColor(CinemaUi.BG);
        getWindow().setNavigationBarColor(CinemaUi.BG);
        store=new GameStore(this);collections=new GameCollections(this);
        personalGoals=new PersonalGoals(this);catalog=new GameCatalog(this);catalog.reload();
        String id=getIntent().getStringExtra("game_id");
        game=catalog.get(id);
        if(game==null||!store.selected().contains(id)){
            Toast.makeText(this,"El juego ya no está disponible en tu biblioteca",Toast.LENGTH_LONG).show();
            finish();return;
        }
        ScrollView root=new ScrollView(this);root.setBackgroundColor(CinemaUi.BG);
        setContentView(root);
        content=CinemaUi.column(this);CinemaUi.pad(this,content,20,22,20,32);
        root.addView(content);render();
    }
    private LinearLayout.LayoutParams lp(int w,int h){return CinemaUi.lp(this,w,h);}
    private TextView text(String s,int sp,int color,boolean bold){return CinemaUi.text(this,s,sp,color,bold);}
    private void gap(int h){content.addView(CinemaUi.gap(this,h));}
    private void render(){
        content.removeAllViews();
        LinearLayout toolbar=CinemaUi.row(this);
        TextView back=CinemaUi.chip(this,"←  MIS JUEGOS",false);
        toolbar.addView(back,lp(-2,46));
        back.setOnClickListener(v->finish());
        TextView context=text("OKUBI  /  ANDROID",10,CinemaUi.MUTED,true);
        context.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        toolbar.addView(context,new LinearLayout.LayoutParams(0,-1,1f));
        content.addView(toolbar);
        gap(18);

        FrameLayout artwork=new FrameLayout(this);
        artwork.setBackground(CinemaUi.spotlight(this,0xff303e58,0xff191a2c,25));
        artwork.setClipToOutline(true);
        content.addView(artwork,lp(-1,282));
        ImageView image=new ImageView(this);
        boolean hasCover=CoverArt.apply(this,image,store,game);
        FrameLayout.LayoutParams img=hasCover
                ?new FrameLayout.LayoutParams(-1,-1)
                :new FrameLayout.LayoutParams(CinemaUi.dp(this,112),
                        CinemaUi.dp(this,112),Gravity.CENTER);
        artwork.addView(image,img);

        View scrim=new View(this);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xff0d0e1b,0x880d0e1b,0x000d0e1b}));
        artwork.addView(scrim,new FrameLayout.LayoutParams(-1,-1));

        TextView series=CinemaUi.eyebrow(this,"●  OKUBI  /  TU JUEGO");
        FrameLayout.LayoutParams upper=new FrameLayout.LayoutParams(-2,-2,
                Gravity.TOP|Gravity.LEFT);
        upper.leftMargin=CinemaUi.dp(this,20);
        upper.topMargin=CinemaUi.dp(this,20);
        artwork.addView(series,upper);

        LinearLayout overlay=CinemaUi.column(this);
        FrameLayout.LayoutParams overlayLp=new FrameLayout.LayoutParams(
                -1,-2,Gravity.BOTTOM);
        overlayLp.leftMargin=CinemaUi.dp(this,19);
        overlayLp.rightMargin=CinemaUi.dp(this,19);
        overlayLp.bottomMargin=CinemaUi.dp(this,21);
        artwork.addView(overlay,overlayLp);
        TextView artworkTitle=text(game.title,27,CinemaUi.WHITE,true);
        artworkTitle.setMaxLines(2);
        artworkTitle.setEllipsize(TextUtils.TruncateAt.END);
        overlay.addView(artworkTitle);
        overlay.addView(CinemaUi.gap(this,8));
        TextView hint=text("▶  TOCA LA PORTADA PARA JUGAR",10,0xffe5dbe9,true);
        hint.setLetterSpacing(.09f);
        overlay.addView(hint);
        CinemaUi.actionable(this,artwork,"Iniciar "+game.title);
        artwork.setOnClickListener(v->launchCurrentGame());

        gap(17);
        LinearLayout status=CinemaUi.row(this);
        TextView platform=CinemaUi.chip(this,"ANDROID  •  INSTALADO",true);
        platform.setClickable(false);
        platform.setFocusable(false);
        status.addView(platform,lp(-2,42));
        TextView local=text("DATOS LOCALES",10,CinemaUi.MUTED,true);
        local.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        status.addView(local,new LinearLayout.LayoutParams(0,-1,1f));
        content.addView(status);
        gap(15);
        LinearLayout chips=CinemaUi.row(this);
        List<GameCollections.Collection> memberships=collections.all();int linked=0;
        for(GameCollections.Collection c:memberships){
            if(!c.gameIds.contains(game.id))continue;
            linked++;
            if(linked>3)continue;
            TextView chip=text(c.name,10,CinemaUi.GOLD,true);
            CinemaUi.pad(this,chip,10,7,10,7);
            chip.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,12));
            LinearLayout.LayoutParams cp=lp(-2,-2);cp.rightMargin=CinemaUi.dp(this,7);chips.addView(chip,cp);
        }
        if(linked>0){content.addView(chips);gap(14);}
        LinearLayout actions=CinemaUi.row(this);content.addView(actions);
        TextView play=CinemaUi.action(this,"▶  JUGAR AHORA",true);
        actions.addView(play,new LinearLayout.LayoutParams(0,-2,1));
        play.setOnClickListener(v->launchCurrentGame());
        TextView fav=CinemaUi.action(this,store.favorite(game.id)?"★ FAVORITO":"☆ FAVORITO",false);
        LinearLayout.LayoutParams p=lp(-2,-2);p.leftMargin=CinemaUi.dp(this,9);
        actions.addView(fav,p);fav.setOnClickListener(v->{store.toggleFavorite(game.id);render();});
        gap(27);
        content.addView(text("ACTIVIDAD",10,CinemaUi.GOLD,true));gap(13);
        LinearLayout data=CinemaUi.column(this);
        CinemaUi.pad(this,data,16,17,16,17);
        data.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,17));
        content.addView(data,lp(-1,-2));
        long last=store.lastLaunched(game.id);
        String label=last==0?"Aún no se ha abierto desde OKUBI":DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(last));
        data.addView(text("ÚLTIMA APERTURA DESDE OKUBI",10,CinemaUi.MUTED,true));
        data.addView(CinemaUi.gap(this,8));data.addView(text(label,15,CinemaUi.WHITE,true));
        data.addView(CinemaUi.gap(this,15));
        data.addView(text("HORAS DE JUEGO",10,CinemaUi.MUTED,true));
        data.addView(CinemaUi.gap(this,7));
        TextView usage=text(ActivityStats.permitted(this)?"Consultando datos disponibles…":"Activa Acceso a datos de uso en ACTIVIDAD",14,CinemaUi.WHITE,true);
        data.addView(usage);
        data.addView(CinemaUi.gap(this,8));
        data.addView(text("Tiempo aproximado de la app en primer plano; no representa necesariamente tiempo real jugando.",11,CinemaUi.MUTED,false));
        if(ActivityStats.permitted(this)) {
            String packageName=game.packageName;
            new Thread(()->{
                java.util.Set<String> one=java.util.Collections.singleton(packageName);
                UsageInsights.Result result=UsageInsights.read(getApplicationContext(),one);
                runOnUiThread(()->{if(!isFinishing() && !isDestroyed() && usage.getParent()!=null)
                    usage.setText(!result.available?"Sin datos disponibles":
                        "Hoy: "+ActivityStats.format(result.gameTotal(packageName,1))+
                        " · 7 días: "+ActivityStats.format(result.gameTotal(packageName,7))+
                        " · 30 días: "+ActivityStats.format(result.gameTotal(packageName,30)));});
            },"nexo-game-usage").start();
        }
        gap(25);
        renderPersonalGoals();
        gap(23);
        content.addView(text("MI COLECCIÓN",10,CinemaUi.GOLD,true));gap(9);
        content.addView(text(linked==0?"Este juego todavía no pertenece a ninguna colección.":
            "Este juego está organizado en "+linked+(linked==1?" colección.":" colecciones."),12,CinemaUi.MUTED,false));
        gap(14);
        content.addView(text("ACCIONES",10,CinemaUi.GOLD,true));gap(13);
        action("Ver estadísticas y acceso a permisos",()->{
            Intent intent=new Intent(this,MainActivity.class);
            intent.putExtra("open_tab","activity");startActivity(intent);
        });
        action("Ver en Google Play",()->openStore());
        action("Gestionar colecciones",()->chooseCollections());
        action("Elegir portada desde mis imágenes",()->chooseCover());
        if(!store.cover(game.id).isEmpty())action("Restaurar icono original",()->{store.clearCover(game.id);render();});
        action("Quitar de mi biblioteca",()->confirmRemoval());
        gap(23);
        TextView packageInfo=text("Identificador de Android: "+game.packageName,10,CinemaUi.MUTED,false);
        packageInfo.setTextIsSelectable(true);content.addView(packageInfo);
    }
    private void launchCurrentGame(){
        if(!store.launch(this,game))
            Toast.makeText(this,"No se pudo iniciar. Comprueba la instalación.",
                    Toast.LENGTH_LONG).show();
    }

    /** Personal goals are created and confirmed by the player; no external achievements are claimed. */
    private void renderPersonalGoals(){
        List<PersonalGoals.Goal> goals=personalGoals.forGame(game.id);
        int done=0;for(PersonalGoals.Goal goal:goals)if(goal.complete)done++;
        content.addView(text("MIS OBJETIVOS PERSONALES",11,CinemaUi.GOLD,true));gap(8);
        content.addView(text(done+" / "+goals.size()+" completados · registro local, NO logros oficiales",11,CinemaUi.MUTED,false));
        gap(10);
        if(goals.isEmpty()){
            LinearLayout empty=CinemaUi.column(this);
            CinemaUi.pad(this,empty,15,18,15,18);
            empty.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,16));
            content.addView(empty);
            empty.addView(text("Aún no tienes objetivos para este juego.",13,CinemaUi.WHITE,true));
            empty.addView(CinemaUi.gap(this,6));
            empty.addView(text("Crea retos propios, como terminar una campaña o completar una misión.",11,CinemaUi.MUTED,false));
        }
        for(PersonalGoals.Goal goal:goals){
            LinearLayout tile=CinemaUi.row(this);
            CinemaUi.pad(this,tile,14,13,14,13);
            tile.setBackground(CinemaUi.outlined(this,CinemaUi.SURFACE,14));
            LinearLayout.LayoutParams params=lp(-1,-2);params.bottomMargin=CinemaUi.dp(this,8);
            content.addView(tile,params);
            TextView check=text(goal.complete?"✓":"○",22,CinemaUi.GOLD,true);
            tile.addView(check,lp(32,-2));
            TextView name=text(goal.title,13,CinemaUi.WHITE,goal.complete);
            tile.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
            TextView erase=text("×",22,CinemaUi.MUTED,true);
            erase.setGravity(Gravity.CENTER);
            erase.setContentDescription("Eliminar objetivo "+goal.title);
            tile.addView(erase,lp(40,43));
            erase.setOnClickListener(v->confirmGoalRemoval(goal));
            tile.setContentDescription((goal.complete?"Completado: ":"Pendiente: ")+goal.title+". Toca para cambiar el estado.");
            tile.setFocusable(true);tile.setClickable(true);
            tile.setOnClickListener(v->{personalGoals.toggle(game.id,goal.id);render();});
            tile.setOnLongClickListener(v->{confirmGoalRemoval(goal);return true;});
        }
        gap(9);
        TextView create=CinemaUi.action(this,"＋ CREAR OBJETIVO PERSONAL",false);
        content.addView(create,lp(-1,-2));create.setOnClickListener(v->createGoal());
    }
    private void createGoal(){
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setHint("Ej. Terminar el capítulo 1");
        input.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(PersonalGoals.MAX_TITLE_LENGTH)});
        LinearLayout area=CinemaUi.column(this);CinemaUi.pad(this,area,18,7,18,8);
        area.addView(input);
        new AlertDialog.Builder(this).setTitle("Crear objetivo propio")
            .setMessage("Este objetivo es personal. No se sincroniza con Google Play Games.")
            .setView(area).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                if(!personalGoals.add(game.id,input.getText().toString())){
                    Toast.makeText(this,"Revisa el nombre (máximo 60 caracteres, sin duplicados; hasta 25 objetivos).",Toast.LENGTH_LONG).show();
                }else render();
            }).show();
    }
    private void confirmGoalRemoval(PersonalGoals.Goal goal){
        new AlertDialog.Builder(this).setTitle("Eliminar objetivo")
            .setMessage("¿Eliminar «"+goal.title+"»? Esto no afecta al juego ni a logros oficiales.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Eliminar",(d,w)->{personalGoals.remove(game.id,goal.id);render();}).show();
    }
    private void action(String name,Runnable callback){
        TextView row=CinemaUi.action(this,name+"   →",false);
        LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=CinemaUi.dp(this,9);content.addView(row,p);
        row.setOnClickListener(v->callback.run());
    }
    private void chooseCollections(){
        List<GameCollections.Collection> all=collections.all();
        if(all.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Sin colecciones")
                .setMessage("Primero crea una colección desde la pestaña LISTAS de OKUBI.")
                .setPositiveButton("Entendido",null).show();return;
        }
        String[] names=new String[all.size()];boolean[] selected=new boolean[all.size()];
        for(int i=0;i<all.size();i++){
            names[i]=all.get(i).name;selected[i]=all.get(i).gameIds.contains(game.id);
        }
        new AlertDialog.Builder(this).setTitle("Colecciones de "+game.title)
            .setMultiChoiceItems(names,selected,(d,which,checked)->selected[which]=checked)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",(d,w)->{
                for(int i=0;i<all.size();i++)collections.setMembership(all.get(i).id,game.id,selected[i]);
                Toast.makeText(this,"Colecciones actualizadas",Toast.LENGTH_SHORT).show();render();
            }).show();
    }
    private void chooseCover(){
        try{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,PICK_COVER);
        }catch(Exception e){Toast.makeText(this,"No se pudo abrir el selector de imágenes",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_COVER||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            store.setCover(game.id,uri.toString());render();
        }catch(SecurityException e){
            Toast.makeText(this,"No se pudo conservar acceso a esa portada",Toast.LENGTH_LONG).show();
        }
    }
    private void openStore(){
        try{
            Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="+game.packageName));
            startActivity(intent);
        }catch(Exception e){
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+game.packageName)));}
            catch(Exception ignored){Toast.makeText(this,"No hay una aplicación para abrir Google Play",Toast.LENGTH_SHORT).show();}
        }
    }
    private void confirmRemoval(){
        new AlertDialog.Builder(this).setTitle("Quitar de OKUBI")
            .setMessage("¿Ocultar "+game.title+" de OKUBI? No se desinstala. "
                    +"Sus favoritos, portada, objetivos y colecciones se conservarán.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Ocultar",(d,w)->{store.remove(game.id);finish();}).show();
    }
}
