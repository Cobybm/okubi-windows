package bo.nexoplay.app.retro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewParent;

/**
 * Low-allocation multi-touch gamepad. Visible buttons are smaller than their
 * hitboxes; layout/skins/drag-edit profiles survive orientation changes.
 * All event dispatch stays on Android's UI thread; core reads atomic masks.
 */
public final class RetroTouchPad extends View {
    public interface InputListener {
        void onButton(RetroCore.Key key, boolean pressed);
    }
    private static final RetroCore.Key[] KEYS = RetroCore.Key.values();
    private final RectF[] regions = new RectF[KEYS.length];
    private final RectF[] hitboxes = new RectF[KEYS.length];
    private final boolean[] visible = new boolean[KEYS.length];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface cinemaTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD);
    private final float density;
    private RetroSystems.Controller controller = RetroSystems.Controller.TWO_BUTTON;
    private RetroControlProfile profile;
    private InputListener listener;
    private int pressedMask;
    private float crossX, crossY, crossR, crossStride, crossHalf;
    private float dragOffsetX, dragOffsetY;
    private boolean editing;
    private boolean cinemaLayout;
    private RetroCinemaGeometry.Layout cinemaGeometry;
    private RetroCore.Key dragged;
    private int dragPointer = -1;

    public RetroTouchPad(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < regions.length; i++) {
            regions[i] = new RectF();
            hitboxes[i] = new RectF();
        }
        profile = RetroControlProfile.load(context);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        setClickable(true);
        setFocusable(true);
        setContentDescription("Controles táctiles configurables de OKUBI");
    }

    public void setInputListener(InputListener next) {
        releaseAll();
        listener = next;
    }
    public void setController(RetroSystems.Controller next) {
        if (next == null) next = RetroSystems.Controller.TWO_BUTTON;
        releaseAll();
        controller = next;
        calculateTargets(getWidth(), getHeight());
        invalidate();
    }
    public void setProfile(RetroControlProfile next) {
        if (next == null) return;
        releaseAll();
        profile = next;
        calculateTargets(getWidth(), getHeight());
        invalidate();
    }
    public RetroControlProfile getProfile() { return profile; }

    /** Called during stage measure: video and buttons share one viewport. */
    public void setCinemaGeometry(RetroCinemaGeometry.Layout next) {
        if (next == null || !cinemaLayout) return;
        RetroCinemaGeometry.Layout old = cinemaGeometry;
        if (old != null && old.stageWidth == next.stageWidth
                && old.stageHeight == next.stageHeight
                && old.gameLeft == next.gameLeft && old.gameTop == next.gameTop
                && old.gameWidth == next.gameWidth && old.gameHeight == next.gameHeight) {
            return;
        }
        releaseAll(); // Never retain a pointer pressed across a resized view.
        cinemaGeometry = next;
        calculateTargets(next.stageWidth, next.stageHeight);
        invalidate();
    }

    /** A full-stage two-thumb overlay only for landscape cinema mode. */
    public void setCinemaLayout(boolean enabled) {
        if (cinemaLayout == enabled) return;
        releaseAll();
        cinemaLayout = enabled;
        calculateTargets(getWidth(), getHeight());
        invalidate();
    }
    public void setEditing(boolean edit) {
        releaseAll();
        editing = edit;
        dragged = null;
        dragPointer = -1;
        dragOffsetX = dragOffsetY = 0f;
        invalidate();
    }
    public boolean isEditing() { return editing; }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private void target(RetroCore.Key key, float x, float y, float radius) {
        int i = key.ordinal();
        x = profile.x(key, x / Math.max(1, getWidth())) * getWidth();
        y = profile.y(key, y / Math.max(1, getHeight())) * getHeight();
        // Custom positions and landscape split layouts must keep the actual
        // painted button on screen, even for narrow phone widths.
        float margin = Math.max(radius, 3f * density);
        x = clamp(x, margin, Math.max(margin, getWidth() - margin));
        y = clamp(y, margin, Math.max(margin, getHeight() - margin));
        regions[i].set(x - radius, y - radius, x + radius, y + radius);
        float hit = Math.max(radius * 1.35f, radius + 9f * density);
        hitboxes[i].set(x - hit, y - hit, x + hit, y + hit);
        visible[i] = true;
    }
    private void calculateTargets(int width, int height) {
        for (int i = 0; i < visible.length; i++) visible[i] = false;
        if (width < 80 || height < 80) return;
        if (cinemaLayout) {
            calculateCinemaTargets(width, height);
            return;
        }
        float r = clamp(Math.min(width * .088f, height * .16f),
                18f * density, 32f * density) * profile.size;
        // Compact separated segments (reference: classic handheld cross).
        // A center gap and NON-overlapping rectangular arms stop one finger
        // from generating two or three directions at once.
        r = Math.min(r, Math.min(width * .18f, height * .21f));
        crossR = r;
        crossStride = r * 1.22f;
        crossHalf = r * .57f; // stride > 2*half: zero overlapping corners
        float edge = crossStride + crossHalf + 3f * density;
        crossX = clamp(profile.dpadX() * width, edge, Math.max(edge, width - edge));
        crossY = clamp(profile.dpadY() * height, edge, Math.max(edge, height - edge));
        target(RetroCore.Key.UP, crossX, crossY - crossStride, crossHalf);
        target(RetroCore.Key.DOWN, crossX, crossY + crossStride, crossHalf);
        target(RetroCore.Key.LEFT, crossX - crossStride, crossY, crossHalf);
        target(RetroCore.Key.RIGHT, crossX + crossStride, crossY, crossHalf);

        float actionX = width * (profile.leftHanded ? .24f : .76f);
        float actionY = height * .49f;
        if (controller == RetroSystems.Controller.SIX_BUTTON) {
            float step = r * 1.34f;
            target(RetroCore.Key.A, actionX - step, actionY + r * .70f, r * .68f);
            target(RetroCore.Key.B, actionX, actionY + r * .70f, r * .68f);
            target(RetroCore.Key.C, actionX + step, actionY + r * .70f, r * .68f);
            target(RetroCore.Key.X, actionX - step, actionY - r * .70f, r * .68f);
            target(RetroCore.Key.Y, actionX, actionY - r * .70f, r * .68f);
            target(RetroCore.Key.Z, actionX + step, actionY - r * .70f, r * .68f);
        } else {
            target(RetroCore.Key.A, actionX + r * .73f, actionY - r * .58f, r * .77f);
            if (controller != RetroSystems.Controller.ONE_BUTTON)
                target(RetroCore.Key.B, actionX - r * .85f, actionY + r * .55f, r * .77f);
            if (controller == RetroSystems.Controller.FOUR_BUTTON
                    || controller == RetroSystems.Controller.DUAL_SCREEN
                    || controller == RetroSystems.Controller.ANALOG_REQUIRED) {
                target(RetroCore.Key.X, actionX + r * .64f, actionY - r * 1.82f, r * .70f);
                target(RetroCore.Key.Y, actionX - r * .80f, actionY - r * .91f, r * .70f);
            }
        }
        target(RetroCore.Key.SELECT, width * .44f, height * .86f, r * .51f);
        target(RetroCore.Key.START, width * .57f, height * .86f, r * .51f);
        if (profile.showShoulders
                && controller != RetroSystems.Controller.TWO_BUTTON
                && controller != RetroSystems.Controller.ONE_BUTTON) {
            target(RetroCore.Key.L, width * .17f, height * .12f, r * .68f);
            target(RetroCore.Key.R, width * .83f, height * .12f, r * .68f);
        }
    }

    /**
     * Rendered and clickable geometry never leaves the assigned thumb rail.
     * This also prevents expanded hitboxes from triggering through the game.
     */
    private void placeCinema(RetroCore.Key key, float defaultX, float defaultY,
                             float halfWidth, float halfHeight, int width, int height,
                             float laneStart, float laneEnd) {
        int i = key.ordinal();
        float x = profile.x(key, defaultX / width) * width;
        float y = profile.y(key, defaultY / height) * height;
        float touchX = halfWidth + Math.min(7f * density, halfWidth * .30f);
        float touchY = halfHeight + Math.min(7f * density, halfHeight * .35f);
        float lowX = laneStart + touchX;
        float highX = laneEnd - touchX;
        if (lowX > highX) x = (laneStart + laneEnd) * .5f;
        else x = clamp(x, lowX, highX);
        y = clamp(y, touchY, Math.max(touchY, height - touchY));
        regions[i].set(x - halfWidth, y - halfHeight,
                x + halfWidth, y + halfHeight);
        hitboxes[i].set(Math.max(laneStart, x - touchX),
                Math.max(0, y - touchY),
                Math.min(laneEnd, x + touchX),
                Math.min(height, y + touchY));
        visible[i] = true;
    }

    private void placeCinemaDpad(RetroCore.Key key, float x, float y, float half,
                                 int width, int height) {
        int i = key.ordinal();
        regions[i].set(x - half, y - half, x + half, y + half);
        hitboxes[i].set(Math.max(0, x - half), Math.max(0, y - half),
                Math.min(width, x + half), Math.min(height, y + half));
        visible[i] = true;
    }

    private void calculateCinemaTargets(int width, int height) {
        RetroCinemaGeometry.Layout g = cinemaGeometry;
        if (g == null || g.stageWidth != width || g.stageHeight != height)
            g = RetroCinemaGeometry.fit(width, height, 1.5f, density);

        // Keep cinema profile positions independent of the pre-CINEMA land_.
        float r = Math.max(4f, Math.min(g.radius * profile.size,
                (Math.min(g.leftRail, g.rightRail) * .5f - 5f * density) / 1.82f));
        crossR = r;
        crossStride = r * 1.22f;
        crossHalf = r * .57f;
        boolean leftDpad = !profile.leftHanded;
        float padMin = leftDpad ? 0 : g.gameRight();
        float padMax = leftDpad ? g.gameLeft : width;
        float reach = crossStride + crossHalf + 4f * density;
        float padCenter = profile.dpadX() * width;
        crossX = clamp(padCenter, padMin + reach, padMax - reach);
        if (padMin + reach > padMax - reach)
            crossX = (padMin + padMax) * .5f;
        crossY = clamp(profile.dpadY() * height, reach,
                Math.max(reach, height - g.bottomRail - reach));
        placeCinemaDpad(RetroCore.Key.UP, crossX, crossY - crossStride,
                crossHalf, width, height);
        placeCinemaDpad(RetroCore.Key.DOWN, crossX, crossY + crossStride,
                crossHalf, width, height);
        placeCinemaDpad(RetroCore.Key.LEFT, crossX - crossStride, crossY,
                crossHalf, width, height);
        placeCinemaDpad(RetroCore.Key.RIGHT, crossX + crossStride, crossY,
                crossHalf, width, height);

        boolean actionsLeft = profile.leftHanded;
        float actionsMin = actionsLeft ? 0 : g.gameRight();
        float actionsMax = actionsLeft ? g.gameLeft : width;
        float thumbX = actionsLeft ? g.leftThumbX : g.rightThumbX;
        float direction = actionsLeft ? -1f : 1f;
        if (controller == RetroSystems.Controller.FOUR_BUTTON) {
            // SNES diamond with non-overlapping action targets in the thumb rail.
            float half = r * .54f, dx = r * .68f, dy = r * .85f;
            float middleY = (g.actionAY + g.actionBY) * .5f;
            placeCinema(RetroCore.Key.B, thumbX - direction * dx,
                    middleY + dy, half, half, width, height,
                    actionsMin, actionsMax);
            placeCinema(RetroCore.Key.A, thumbX + direction * dx,
                    middleY + dy, half, half, width, height,
                    actionsMin, actionsMax);
            placeCinema(RetroCore.Key.Y, thumbX - direction * dx,
                    middleY - dy, half, half, width, height,
                    actionsMin, actionsMax);
            placeCinema(RetroCore.Key.X, thumbX + direction * dx,
                    middleY - dy, half, half, width, height,
                    actionsMin, actionsMax);
        } else {
            float actionR = r * .84f;
            placeCinema(RetroCore.Key.A, thumbX + direction * r * .58f,
                    g.actionAY, actionR, actionR, width, height,
                    actionsMin, actionsMax);
            if (controller != RetroSystems.Controller.ONE_BUTTON)
                placeCinema(RetroCore.Key.B, thumbX - direction * r * .66f,
                        g.actionBY, actionR, actionR, width, height,
                        actionsMin, actionsMax);
        }

        // START and SELECT have their own lane below the game, never
        // require a finger in front of dialogue text.
        float pillW = g.utilityHalfWidth;
        float pillH = g.utilityHalfHeight;
        placeCinema(RetroCore.Key.SELECT, width * .445f, g.utilityY,
                pillW, pillH, width, height, 0, width);
        placeCinema(RetroCore.Key.START, width * .555f, g.utilityY,
                pillW, pillH, width, height, 0, width);

        if (profile.showShoulders
                && controller != RetroSystems.Controller.ONE_BUTTON
                && controller != RetroSystems.Controller.TWO_BUTTON) {
            placeCinema(RetroCore.Key.L, g.leftThumbX, g.shoulderY,
                    r * 1.18f, r * .45f, width, height, 0, g.gameLeft);
            placeCinema(RetroCore.Key.R, g.rightThumbX, g.shoulderY,
                    r * 1.18f, r * .45f, width, height, g.gameRight(), width);
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        releaseAll();
        calculateTargets(w, h);
    }

    private RetroCore.Key nearestKey(float x, float y) {
        // During editing, dragging the cross moves the four directions together.
        if (Math.abs(x - crossX) <= crossStride + crossHalf
                && Math.abs(y - crossY) <= crossStride + crossHalf)
            return RetroCore.Key.UP;
        RetroCore.Key best = null;
        float nearest = Float.MAX_VALUE;
        for (RetroCore.Key key : KEYS) {
            int i = key.ordinal();
            if (i <= RetroCore.Key.RIGHT.ordinal() || !visible[i]
                    || !hitboxes[i].contains(x, y)) continue;
            float dx = (x - regions[i].centerX()) / regions[i].width();
            float dy = (y - regions[i].centerY()) / regions[i].height();
            float distance = dx * dx + dy * dy;
            if (distance < nearest) { nearest = distance; best = key; }
        }
        return best;
    }

    private int maskAt(float x, float y) {
        // No invisible hitbox or edge of a large A/B button may interact
        // with the game viewport. Editing still permits repositioning.
        if (cinemaLayout && !editing && cinemaGeometry != null
                && x >= cinemaGeometry.gameLeft
                && x <= cinemaGeometry.gameRight()
                && y >= cinemaGeometry.gameTop
                && y <= cinemaGeometry.gameBottom()) return 0;
        // One pointer -> one direction. Do NOT infer diagonals from x/y signs.
        // The pointer OR in onTouchEvent still supports intentional two-finger
        // diagonals without shared hitboxes or a center ghost press.
        int direction = RetroDpadHitMap.hit(
                x, y, crossX, crossY, crossStride, crossHalf);
        if (direction == RetroDpadHitMap.UP) return 1 << RetroCore.Key.UP.ordinal();
        if (direction == RetroDpadHitMap.DOWN) return 1 << RetroCore.Key.DOWN.ordinal();
        if (direction == RetroDpadHitMap.LEFT) return 1 << RetroCore.Key.LEFT.ordinal();
        if (direction == RetroDpadHitMap.RIGHT) return 1 << RetroCore.Key.RIGHT.ordinal();
        // nearestKey() also selects the cross in EDIT mode. During gameplay
        // a touch on the empty center or between arms must stay RELEASED.
        // Never fall through to the editor's UP group hit target.
        if (Math.abs(x - crossX) <= crossStride + crossHalf
                && Math.abs(y - crossY) <= crossStride + crossHalf) return 0;
        RetroCore.Key selected = nearestKey(x, y);
        if (selected == null) return 0;
        if (profile.swapAB) {
            if (selected == RetroCore.Key.A) selected = RetroCore.Key.B;
            else if (selected == RetroCore.Key.B) selected = RetroCore.Key.A;
        }
        return 1 << selected.ordinal();
    }

    private void applyMask(int next) {
        int changes = pressedMask ^ next;
        if (changes == 0) return;
        pressedMask = next;
        // One haptic pulse per DOWN transition, not per frame/move.
        // Android honors the user's system vibration/accessibility settings.
        if (profile.hapticEnabled && !editing && (changes & next) != 0) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        for (RetroCore.Key key : KEYS) {
            int bit = 1 << key.ordinal();
            if ((changes & bit) != 0 && listener != null)
                listener.onButton(key, (next & bit) != 0);
        }
        postInvalidateOnAnimation();
    }
    public void releaseAll() { applyMask(0); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            if (editing && dragged != null) profile.save(getContext());
            releaseAll();
            dragged = null;
            dragPointer = -1;
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_POINTER_DOWN
                && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_POINTER_UP
                && action != MotionEvent.ACTION_UP) return super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN) {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }

        if (editing) {
            if (action == MotionEvent.ACTION_DOWN) {
                dragPointer = event.getPointerId(0);
                dragged = nearestKey(event.getX(0), event.getY(0));
                if (dragged != null) {
                    float originX = dragged == RetroCore.Key.UP
                            ? crossX : regions[dragged.ordinal()].centerX();
                    float originY = dragged == RetroCore.Key.UP
                            ? crossY : regions[dragged.ordinal()].centerY();
                    dragOffsetX = originX - event.getX(0);
                    dragOffsetY = originY - event.getY(0);
                }
            } else if (action == MotionEvent.ACTION_MOVE && dragged != null) {
                int i = event.findPointerIndex(dragPointer);
                if (i >= 0) {
                    profile.move(dragged,
                            (event.getX(i) + dragOffsetX) / Math.max(1f, getWidth()),
                            (event.getY(i) + dragOffsetY) / Math.max(1f, getHeight()));
                    calculateTargets(getWidth(), getHeight());
                    postInvalidateOnAnimation();
                }
            } else if (action == MotionEvent.ACTION_POINTER_UP
                    && event.getPointerId(event.getActionIndex()) == dragPointer) {
                // The editing finger may lift while a second finger remains.
                profile.save(getContext());
                dragged = null;
                dragPointer = -1;
            } else if (action == MotionEvent.ACTION_UP) {
                if (dragged != null) profile.save(getContext());
                dragged = null;
                dragPointer = -1;
                performClick();
                ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }

        int leaving = (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP)
                ? event.getActionIndex() : -1;
        int combined = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i != leaving) combined |= maskAt(event.getX(i), event.getY(i));
        }
        // Two simultaneous fingers on opposite D-pad arms must not send
        // contradictory input to any GB/GBC/GBA/SNES core.
        combined = RetroInputMask.resolveOpposites(combined,
                1 << RetroCore.Key.UP.ordinal(), 1 << RetroCore.Key.DOWN.ordinal(),
                1 << RetroCore.Key.LEFT.ordinal(), 1 << RetroCore.Key.RIGHT.ordinal());
        applyMask(combined);
        if (action == MotionEvent.ACTION_UP) {
            performClick();
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) releaseAll();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) releaseAll();
    }
    @Override protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    private String label(RetroCore.Key key) {
        switch (key) {
            case UP: return "▲";
            case DOWN: return "▼";
            case LEFT: return "◀";
            case RIGHT: return "▶";
            case SELECT: return cinemaLayout ? "SELECT" : "SEL";
            case START: return cinemaLayout ? "START" : "STA";
            case A: return profile.swapAB ? "B" : "A";
            case B: return profile.swapAB ? "A" : "B";
            default: return key.name();
        }
    }
    private int fillColor() {
        switch (profile.skin) {
            case CLASSIC_GBA: return 0xFF464852;
            case MINIMAL_GHOST: return 0xFF16121E;
            case ARCADE_BOLD: return 0xFFE83068;
            case RETRO_POCKET: return 0xFF5B5966;
            case PRO_TOUCH: return 0xFF203448;
            default: return 0xFF24132F;
        }
    }
    private int accentColor() {
        switch (profile.skin) {
            case CLASSIC_GBA: return 0xFFD4D2D9;
            case MINIMAL_GHOST: return 0xFFEDE7F7;
            case ARCADE_BOLD: return 0xFFFFC0D5;
            case RETRO_POCKET: return 0xFFBC9AA4;
            case PRO_TOUCH: return 0xFF37E4F5;
            default: return 0xFFFF3E83;
        }
    }
    private int tinted(int color, float opacity) {
        return (((int)(clamp(opacity, .08f, 1f) * 255f)) << 24) | (color & 0x00FFFFFF);
    }

    private void drawCinemaControls(Canvas canvas) {
        float opacity = profile.opacity;
        int accent = accentColor();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStyle(Paint.Style.FILL);
        // Floating translucent D-pad disc from the approved landscape design.
        paint.setColor(tinted(0xFF292136, opacity * .45f));
        canvas.drawCircle(crossX, crossY, crossStride + crossHalf + crossR * .20f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(tinted(accent, opacity * .38f));
        canvas.drawCircle(crossX, crossY, crossStride + crossHalf + crossR * .20f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF505063);
        canvas.drawCircle(crossX, crossY, crossR * .38f, paint);
        paint.setColor(0xFF77798A);
        canvas.drawCircle(crossX, crossY, crossR * .12f, paint);

        for (RetroCore.Key key : KEYS) {
            int i = key.ordinal();
            if (!visible[i]) continue;
            RectF shape = regions[i];
            boolean pressed = (pressedMask & (1 << i)) != 0;
            boolean dpad = key == RetroCore.Key.UP || key == RetroCore.Key.DOWN
                    || key == RetroCore.Key.LEFT || key == RetroCore.Key.RIGHT;
            boolean pill = key == RetroCore.Key.L || key == RetroCore.Key.R
                    || key == RetroCore.Key.START || key == RetroCore.Key.SELECT;
            float corner = dpad ? shape.height() * .22f : shape.height() * .48f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? 0xFFB52D7A
                    : dpad ? 0xFF343044 : 0xFF1D102B);
            if (dpad || pill) canvas.drawRoundRect(shape, corner, corner, paint);
            else canvas.drawOval(shape, paint);
            // Soft double outline creates a neon bezel without bitmap assets.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f * density);
            paint.setColor(tinted(accent, .18f * opacity));
            if (dpad || pill) canvas.drawRoundRect(shape, corner, corner, paint);
            else canvas.drawOval(shape, paint);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(tinted(dpad ? 0xFFB896C5 : accent, opacity));
            if (dpad || pill) canvas.drawRoundRect(shape, corner, corner, paint);
            else canvas.drawOval(shape, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            paint.setTypeface(cinemaTypeface);
            float textSize = (dpad ? 15.5f : pill ? 11.5f : 21f)
                    * density * Math.min(profile.size, 1.12f);
            paint.setTextSize(Math.min(textSize, shape.height() * .60f));
            canvas.drawText(label(key), shape.centerX(),
                    shape.centerY() - (paint.ascent() + paint.descent()) * .5f, paint);
        }
        if (editing) {
            paint.setTextSize(11f * density);
            paint.setColor(0xFFFFD3E2);
            canvas.drawText("MOVER BOTONES · ARRASTRA", getWidth() / 2f,
                    13f * density, paint);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() < 80) return;
        if (cinemaLayout) {
            drawCinemaControls(canvas);
            return;
        }
        int fill = fillColor(), accent = accentColor();
        float alpha = profile.opacity;
        paint.setTextAlign(Paint.Align.CENTER);
        // Decorative center cap is deliberately NOT a hit target.
        // This keeps the four arms visually grouped without fake diagonals.
        if (visible[RetroCore.Key.UP.ordinal()]) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tinted(profile.skin == RetroControlProfile.Skin.CLASSIC_GBA
                    ? 0xFFB5BAC4 : 0xFF343447, Math.min(1f, alpha + .10f)));
            canvas.drawCircle(crossX, crossY, crossR * .40f, paint);
            paint.setColor(tinted(0xFF727685, Math.min(1f, alpha + .08f)));
            canvas.drawCircle(crossX, crossY, crossR * .13f, paint);
        }
        for (RetroCore.Key key : KEYS) {
            int i = key.ordinal();
            if (!visible[i]) continue;
            RectF shape = regions[i];
            boolean pressed = (pressedMask & (1 << i)) != 0;
            RetroControlProfile.Skin skin = profile.skin;
            boolean dpad = key == RetroCore.Key.UP || key == RetroCore.Key.DOWN
                    || key == RetroCore.Key.LEFT || key == RetroCore.Key.RIGHT;
            boolean utility = key == RetroCore.Key.SELECT || key == RetroCore.Key.START
                    || key == RetroCore.Key.L || key == RetroCore.Key.R;
            boolean squareStyle = skin == RetroControlProfile.Skin.RETRO_POCKET
                    || skin == RetroControlProfile.Skin.PRO_TOUCH;
            float corner = dpad ? shape.height() * .20f
                    : squareStyle ? shape.height() * .23f : shape.height() * .48f;
            float fillOpacity = skin == RetroControlProfile.Skin.MINIMAL_GHOST && !pressed
                    ? alpha * .18f : skin == RetroControlProfile.Skin.ARCADE_BOLD
                    ? Math.min(1f, alpha + .14f) : alpha;
            paint.setStyle(Paint.Style.FILL);
            // Classic handheld D-pad: pale separated directional caps.
            int buttonFill = dpad && (skin == RetroControlProfile.Skin.OKUBI_NEON
                    || skin == RetroControlProfile.Skin.CLASSIC_GBA)
                    ? 0xFFC2C6D0 : fill;
            int buttonAccent = dpad && (skin == RetroControlProfile.Skin.OKUBI_NEON
                    || skin == RetroControlProfile.Skin.CLASSIC_GBA)
                    ? 0xFF727888 : accent;
            paint.setColor(tinted(pressed ? buttonAccent : buttonFill,
                    pressed ? 1f : fillOpacity));
            if (dpad || utility || squareStyle) canvas.drawRoundRect(shape, corner, corner, paint);
            else canvas.drawOval(shape, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(skin == RetroControlProfile.Skin.MINIMAL_GHOST
                    ? 1.2f * density : skin == RetroControlProfile.Skin.ARCADE_BOLD
                    ? 2.8f * density : 1.7f * density);
            paint.setColor(tinted(buttonAccent, Math.min(1f, alpha + .17f)));
            if (dpad || utility || squareStyle) canvas.drawRoundRect(shape, corner, corner, paint);
            else canvas.drawOval(shape, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tinted(dpad ? 0xFF313747 : 0xFFFFFFFF,
                    Math.min(1f, alpha + .18f)));
            paint.setTextSize((key == RetroCore.Key.START || key == RetroCore.Key.SELECT ? 10f : 17f)
                    * density * Math.min(profile.size, 1.16f));
            canvas.drawText(label(key), shape.centerX(),
                    shape.centerY() - (paint.ascent() + paint.descent()) * .5f, paint);
        }
        if (editing) {
            paint.setTextSize(12f * density);
            paint.setColor(0xFFFFD3E2);
            canvas.drawText("MODO EDICIÓN · ARRASTRA LOS BOTONES", getWidth() / 2f,
                    17f * density, paint);
        }
    }
}
