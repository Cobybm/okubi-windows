package bo.nexoplay.app.retro;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A letterboxed display frame. Children retain the console's pixel aspect
 * even when height is capped on tablets or in landscape orientation.
 */
public final class RetroAspectFrame extends FrameLayout {
    private int nativeWidth = 240;
    private int nativeHeight = 160;
    private int maxHeightDp = 550;
    private int surfaceWidth;
    private int surfaceHeight;
    private boolean fillAvailable;

    public RetroAspectFrame(Context context) {
        super(context);
        setClipToOutline(true);
    }
    public RetroAspectFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipToOutline(true);
    }

    /** When game video fills CINEMA stage, overlay pad stays above SurfaceView. */
    public void setFillAvailable(boolean fill) {
        if (fillAvailable == fill) return;
        fillAvailable = fill;
        requestLayout();
    }

    public void setNativeSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (nativeWidth == w && nativeHeight == h) return;
        nativeWidth = w;
        nativeHeight = h;
        requestLayout();
    }

    public void setMaxHeightDp(int value) {
        maxHeightDp = Math.max(180, value);
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = Math.max(1, View.MeasureSpec.getSize(widthMeasureSpec));
        int maxHeightPx = Math.round(maxHeightDp * getResources().getDisplayMetrics().density);
        int frameHeight = Math.min(maxHeightPx,
                Math.max(1, Math.round(availableWidth * nativeHeight / (float) nativeWidth)));
        int parentHeightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int parentHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        if (parentHeightMode == View.MeasureSpec.EXACTLY) frameHeight = parentHeight;
        else if (parentHeightMode == View.MeasureSpec.AT_MOST)
            frameHeight = Math.min(frameHeight, parentHeight);

        if (fillAvailable) {
            surfaceWidth = availableWidth;
            surfaceHeight = frameHeight;
            int childW = View.MeasureSpec.makeMeasureSpec(surfaceWidth, View.MeasureSpec.EXACTLY);
            int childH = View.MeasureSpec.makeMeasureSpec(surfaceHeight, View.MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) child.measure(childW, childH);
            }
            setMeasuredDimension(availableWidth, frameHeight);
            return;
        }
        // The outer frame can use the full width, while child content stays
        // centered in an exact native-ratio rectangle with black side bars.
        surfaceWidth = Math.min(availableWidth,
                Math.max(1, Math.round(frameHeight * nativeWidth / (float) nativeHeight)));
        surfaceHeight = Math.min(frameHeight,
                Math.max(1, Math.round(surfaceWidth * nativeHeight / (float) nativeWidth)));
        int childWidth = View.MeasureSpec.makeMeasureSpec(surfaceWidth, View.MeasureSpec.EXACTLY);
        int childHeight = View.MeasureSpec.makeMeasureSpec(surfaceHeight, View.MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) child.measure(childWidth, childHeight);
        }
        setMeasuredDimension(availableWidth, frameHeight);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int centerX = (r - l - surfaceWidth) / 2;
        int centerY = (b - t - surfaceHeight) / 2;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE)
                child.layout(centerX, centerY,
                        centerX + surfaceWidth, centerY + surfaceHeight);
        }
    }
}
