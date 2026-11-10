package bo.nexoplay.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * OKUBI Cinema design system: one restrained midnight palette, deliberate
 * typographic hierarchy, large tappable surfaces and GPU-cheap gradients.
 * All components are ordinary native Android views; no network or UI SDKs.
 */
public final class CinemaUi {
    public static final int BG = 0xFF090911;
    public static final int SURFACE = 0xFF181824;
    public static final int DEEP = 0xFF11131F;
    public static final int ELEVATED = 0xFF252537;
    public static final int WHITE = 0xFFF8F7FA;
    public static final int MUTED = 0xFFA9A8BD;
    public static int GOLD = 0xFFFF3E70;
    public static final int STROKE = 0xFF3B394C;
    public static final int BLUE = 0xFF8F9CFF;

    private CinemaUi() {}

    public static int dp(Context c,float d) {
        return (int)(c.getResources().getDisplayMetrics().density*d+0.5f);
    }
    public static LinearLayout column(Context c) {
        LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    public static LinearLayout row(Context c) {
        LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;
    }
    public static void pad(Context c,View v,int l,int t,int r,int b) {
        v.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b));
    }
    public static TextView text(Context c,String str,int size,int color,boolean bold) {
        TextView t=new TextView(c);
        t.setText(str);t.setTextSize(size);t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        return t;
    }
    public static GradientDrawable shape(Context c,int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    public static GradientDrawable outlined(Context c,int color,int radius) {
        GradientDrawable d=shape(c,color,radius);
        d.setStroke(dp(c,1),STROKE);
        return d;
    }
    public static GradientDrawable gradient(Context c,int start,int end,int radius) {
        GradientDrawable d=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(c,radius));
        return d;
    }
    public static GradientDrawable spotlight(Context c,int start,int end,int radius) {
        GradientDrawable d=new GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                new int[]{start,end});
        d.setCornerRadius(dp(c,radius));
        d.setStroke(dp(c,1),STROKE);
        return d;
    }
    public static View gap(Context c,int height) {
        View v=new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,height)));
        return v;
    }
    /** Consistent accessible native pseudo-button: 48dp minimum and feedback. */
    public static TextView action(Context c,String label,boolean primary) {
        TextView t=text(c,label,12,primary?BG:WHITE,true);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(.035f);
        t.setBackground(primary
                ?gradient(c,GOLD,lighten(GOLD,.13f),14)
                :outlined(c,SURFACE,14));
        pad(c,t,13,13,13,13);
        t.setMinHeight(dp(c,48));
        actionable(c,t,label);
        return t;
    }
    public static TextView chip(Context c,String label,boolean selected) {
        TextView t=text(c,label,10,selected?WHITE:MUTED,true);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(.055f);
        t.setBackground(selected
                ?spotlight(c,ELEVATED,SURFACE,14)
                :shape(c,DEEP,14));
        pad(c,t,12,8,12,8);
        t.setMinHeight(dp(c,48));
        actionable(c,t,label);
        return t;
    }
    public static TextView eyebrow(Context c,String label) {
        TextView t=text(c,label,10,GOLD,true);
        t.setLetterSpacing(.14f);
        return t;
    }
    /** Ripple foreground preserves complex poster art and gradient overlays. */
    public static void actionable(Context c,View view,String description) {
        view.setClickable(true);view.setFocusable(true);
        view.setContentDescription(description);
        view.setForeground(new RippleDrawable(
                ColorStateList.valueOf(0x43FFFFFF),null,shape(c,Color.WHITE,18)));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host,AccessibilityNodeInfo node){
                super.onInitializeAccessibilityNodeInfo(host,node);
                node.setClassName(android.widget.Button.class.getName());
            }
        });
    }
    public static int lighten(int color,float amount) {
        amount=Math.max(0f,Math.min(1f,amount));
        int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
        return Color.rgb(Math.min(255,r+Math.round((255-r)*amount)),
                Math.min(255,g+Math.round((255-g)*amount)),
                Math.min(255,b+Math.round((255-b)*amount)));
    }
    public static LinearLayout.LayoutParams lp(Context c,int width,int height) {
        return new LinearLayout.LayoutParams(width<0?width:dp(c,width),
                height<0?height:dp(c,height));
    }
}
