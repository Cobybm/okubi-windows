package bo.nexoplay.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A real, live spotlight tour: highlights existing on-screen controls.
 * No screenshots, image assets, broad permissions, or outside libraries.
 * All touches outside the card are consumed while a step is visible.
 */
public final class OkubiGuidedTour {
    private final Activity activity;
    private final FrameLayout host;
    private final FrameLayout overlay;
    private final Shade shade;
    private final LinearLayout card;
    private final TextView direction;
    private final TextView title;
    private final TextView description;
    private final TextView progress;
    private final Button back;
    private final Button next;
    private final Button skip;
    private View focusTarget;
    private boolean closed;
    private final int padding;
    private final int cardMargin;

    public OkubiGuidedTour(Activity activity, FrameLayout host) {
        this.activity = activity;
        this.host = host;
        padding = dp(7);
        cardMargin = dp(16);
        overlay = new FrameLayout(activity);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setContentDescription("Tutorial interactivo de OKUBI");
        overlay.setOnClickListener(v -> { /* never activate underlying controls */ });
        shade = new Shade(activity);
        overlay.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(12));
        card.setElevation(dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff241b30);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xffe7bc69);
        card.setBackground(bg);
        direction = new TextView(activity);
        direction.setTextSize(22);
        direction.setTextColor(0xffffd284);
        card.addView(direction);
        title = new TextView(activity);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);
        description = new TextView(activity);
        description.setTextSize(15);
        description.setTextColor(0xffecdfef);
        description.setPadding(0,dp(9),0,dp(12));
        description.setLineSpacing(dp(3),1.0f);
        card.addView(description);
        progress = new TextView(activity);
        progress.setTextColor(0xffffd284);
        progress.setTextSize(12);
        card.addView(progress);
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(9),0,0);
        back = button("Anterior");
        next = button("Siguiente");
        skip = button("Saltar");
        actions.addView(skip, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        actions.addView(back);
        actions.addView(next);
        card.addView(actions);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = cardMargin;
        params.rightMargin = cardMargin;
        overlay.addView(card,params);
        host.addView(overlay,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private int dp(int value) {
        return (int) (activity.getResources().getDisplayMetrics().density*value+0.5f);
    }

    private Button button(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(7),0,dp(7),0);
        return button;
    }

    public void showStep(View target, String heading, String explanation,
                         int step, int total, Runnable previous,
                         Runnable advance, Runnable dismiss) {
        if(closed || target==null) return;
        focusTarget = target;
        title.setText(heading);
        description.setText(explanation);
        progress.setText("Paso "+step+" de "+total);
        back.setVisibility(step>1?View.VISIBLE:View.GONE);
        next.setText(step==total?"Terminar":"Siguiente");
        back.setOnClickListener(v->previous.run());
        next.setOnClickListener(v->advance.run());
        skip.setOnClickListener(v->dismiss.run());
        // The target itself must not be activated accidentally by the overlay.
        final Rect local = new Rect(0,0,
                Math.max(1,target.getWidth()),Math.max(1,target.getHeight()));
        target.requestRectangleOnScreen(local,false);
        overlay.bringToFront();
        overlay.post(this::updateGeometry);
        overlay.postDelayed(this::updateGeometry,100);
        next.requestFocus();
    }

    private void updateGeometry() {
        if(closed || !overlay.isAttachedToWindow() || focusTarget==null) return;
        Rect visible = new Rect();
        int[] origin = new int[2];
        overlay.getLocationOnScreen(origin);
        boolean found = focusTarget.getGlobalVisibleRect(visible);
        if(!found) {
            shade.setHole(null);
            direction.setText("↓");
            centerCard();
            return;
        }
        RectF spot = new RectF(
                Math.max(0,visible.left-origin[0]-padding),
                Math.max(0,visible.top-origin[1]-padding),
                Math.min(overlay.getWidth(),visible.right-origin[0]+padding),
                Math.min(overlay.getHeight(),visible.bottom-origin[1]+padding));
        if(spot.width()<4 || spot.height()<4) {
            shade.setHole(null);
            centerCard();
            return;
        }
        shade.setHole(spot);
        int width = Math.max(dp(180),overlay.getWidth()-cardMargin*2);
        card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(overlay.getHeight(),
                        View.MeasureSpec.AT_MOST));
        int cardHeight = Math.max(dp(130),card.getMeasuredHeight());
        int below = (int)spot.bottom+dp(17);
        int above = (int)spot.top-cardHeight-dp(17);
        boolean useBelow = below+cardHeight <= overlay.getHeight()-dp(12);
        int top = useBelow ? below : above>=dp(12) ? above
                : Math.max(dp(12),Math.min(below,overlay.getHeight()-cardHeight-dp(12)));
        direction.setText(useBelow ? "↑  ESTE BOTÓN" : "↓  ESTE BOTÓN");
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)card.getLayoutParams();
        p.gravity=Gravity.TOP;
        p.topMargin=top;
        p.leftMargin=cardMargin;
        p.rightMargin=cardMargin;
        card.setLayoutParams(p);
    }

    private void centerCard() {
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)card.getLayoutParams();
        params.gravity=Gravity.CENTER;
        params.topMargin=0;
        card.setLayoutParams(params);
    }

    public void close() {
        if(closed) return;
        closed=true;
        shade.setHole(null);
        focusTarget=null;
        if(overlay.getParent()==host)host.removeView(overlay);
    }

    private final class Shade extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private RectF hole;
        Shade(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            paint.setColor(0xdd080915);
            border.setColor(0xffffd284);
            border.setStrokeWidth(dp(2));
            border.setStyle(Paint.Style.STROKE);
        }
        void setHole(RectF rect) {
            hole=rect==null?null:new RectF(rect);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(hole==null) {
                canvas.drawColor(0xdd080915);
                return;
            }
            // EVEN_ODD punches a transparent rounded hole; the original
            // real button stays at its exact normal color and position.
            path.reset();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);
            path.addRoundRect(hole,dp(13),dp(13),Path.Direction.CW);
            canvas.drawPath(path,paint);
            canvas.drawRoundRect(hole,dp(13),dp(13),border);
        }
    }
}
