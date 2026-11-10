package bo.nexoplay.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;

/** User-selected local cover, never downloaded or uploaded. Returns false for the icon fallback. */
public final class CoverArt {
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(10*1024*1024){
        @Override protected int sizeOf(String key,Bitmap image){return image.getByteCount();}
    };
    private CoverArt(){}
    public static boolean apply(Context context,ImageView image,GameStore store,GameApp game){
        String value=store.cover(game.id);
        if(!value.isEmpty()){
            try{
                Bitmap b=cache.get(value);
                if(b==null){
                    Uri uri=Uri.parse(value);
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    try(InputStream input=context.getContentResolver().openInputStream(uri)){
                        if(input!=null)BitmapFactory.decodeStream(input,null,bounds);
                    }
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    int size=Math.max(bounds.outWidth,bounds.outHeight);int sample=1;
                    while(size/sample>640)sample*=2;
                    opts.inSampleSize=sample;
                    try(InputStream input=context.getContentResolver().openInputStream(uri)){
                        if(input!=null)b=BitmapFactory.decodeStream(input,null,opts);
                    }
                    if(b!=null)cache.put(value,b);
                }
                if(b!=null){image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(b);return true;}
            }catch(Exception | OutOfMemoryError ignored){}
        }
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageDrawable(game.icon);
        return false;
    }
}
