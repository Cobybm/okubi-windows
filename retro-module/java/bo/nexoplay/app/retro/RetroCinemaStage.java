package bo.nexoplay.app.retro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Landscape-only cinema stage. The game always keeps its native GB/GBC/GBA
 * aspect ratio and is centered between two thumb-reachable controller rails.
 * The translucent touch layer covers the stage but has no gameplay surface
 * interaction: all input comes from the left/right control regions.
 */
public final class RetroCinemaStage extends FrameLayout {
    private final RetroAspectFrame game;
    private final RetroTouchPad pad;
    private final float aspect;
    private final float density;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outline = new RectF();
    private int gameLeft, gameTop, gameWidth, gameHeight;
    private int bottomRail;
    private boolean expandedVideo;

    public RetroCinemaStage(Context context, int nativeWidth, int nativeHeight,
                            RetroAspectFrame game, RetroTouchPad pad) {
        super(context);
        if (nativeWidth <= 0 || nativeHeight <= 0)
            throw new IllegalArgumentException("Dimensiones retro inválidas");
        this.aspect = nativeWidth / (float) nativeHeight;
        this.density = getResources().getDisplayMetrics().density;
        this.game = game;
        this.pad = pad;
        setWillNotDraw(false);
        setClipChildren(false);
        addView(game);
        addView(pad);
    }

    public void setExpandedVideo(boolean expanded) {
        if (expandedVideo == expanded) return;
        expandedVideo = expanded;
        game.setFillAvailable(expanded);
        requestLayout();
        invalidate();
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int width = Math.max(1, MeasureSpec.getSize(wSpec));
        int height = Math.max(1, MeasureSpec.getSize(hSpec));
        setMeasuredDimension(width, height);

        RetroCinemaGeometry.Layout geometry = RetroCinemaGeometry.fit(
                width, height, aspect, density);
        if (expandedVideo) {
            gameWidth = width;
            gameHeight = height;
            gameLeft = 0;
            gameTop = 0;
        } else {
            // The original CINEMA mode is byte-for-byte geometry-compatible.
            gameWidth = geometry.gameWidth;
            gameHeight = geometry.gameHeight;
            gameLeft = geometry.gameLeft;
            gameTop = geometry.gameTop;
        }
        bottomRail = geometry.bottomRail;
        // Measure and touch hitboxes use the SAME immutable geometry.
        // Do not assume display cutouts or viewport dimensions are constant.
        pad.setCinemaGeometry(geometry);

        game.measure(MeasureSpec.makeMeasureSpec(gameWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(gameHeight, MeasureSpec.EXACTLY));
        pad.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        game.layout(gameLeft, gameTop, gameLeft + gameWidth, gameTop + gameHeight);
        pad.layout(0, 0, r - l, b - t);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        canvas.drawColor(0xFF0B0712);
        background.setStyle(Paint.Style.FILL);
        background.setColor(0x442C103C);
        canvas.drawCircle(-w * .04f, h * 1.23f, w * .34f, background);
        background.setColor(0x443D0C38);
        canvas.drawCircle(w * 1.05f, h * 1.16f, w * .34f, background);

        if (expandedVideo) return; // Gameplay covers the stage; no bezel masks its edges.
        // A restrained two-pass border conveys the approved magenta frame.
        outline.set(gameLeft - dp(4), gameTop - dp(4),
                gameLeft + gameWidth + dp(4), gameTop + gameHeight + dp(4));
        background.setStyle(Paint.Style.STROKE);
        background.setStrokeWidth(dp(7));
        background.setColor(0x44FF2D98);
        canvas.drawRoundRect(outline, dp(18), dp(18), background);
        background.setStrokeWidth(dp(1.7f));
        background.setColor(0xFFFF4EB2);
        canvas.drawRoundRect(outline, dp(18), dp(18), background);
        background.setStyle(Paint.Style.FILL);
        // Bottom visual accents from the approved reference, kept outside
        // the game and away from interactive START / SELECT hitboxes.
        float footerY = h - Math.max(dp(10), bottomRail * .5f);
        background.setStyle(Paint.Style.STROKE);
        background.setStrokeWidth(dp(1));
        background.setColor(0x99FF3C9C);
        canvas.drawLine(w * .25f, footerY, w * .35f, footerY, background);
        canvas.drawLine(w * .65f, footerY, w * .75f, footerY, background);
        background.setStyle(Paint.Style.FILL);
    }

}
