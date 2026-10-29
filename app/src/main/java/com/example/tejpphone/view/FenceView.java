package com.example.tejpphone.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tejpphone.function.Modification;
import com.example.tejpphone.function.MoveModification;
import com.example.tejpphone.function.MovePicModification;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Picture;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;

/**
 * Draws a fence and its strips. Replaces the desktop FencePanel, LayeredFencePanel and EdgePanels.
 *
 * <p>Touch:
 * <ul>
 *   <li>One finger on the fence is passed to the TouchController as if it were the mouse,
 *       with a magnifier above the finger.</li>
 *   <li>One finger that starts where the controller has nothing to do, such as outside the fence, pans.</li>
 *   <li>Two fingers pan and pinch-zoom.</li>
 *   <li>A double tap fits the whole fence on screen.</li>
 *   <li>With a SwipeListener and no controller, as in the layers viewer, a one-finger swipe
 *       sideways is reported instead of panning.</li>
 * </ul>
 */
public class FenceView extends View {

    /** Receives one-finger gestures in content coordinates. */
    public interface TouchController {
        /** Returns true if a gesture started. Only then are the other methods called. */
        boolean onDown(float x, float y, float touchRadius);

        /** Called once the finger has moved further than a tap would, and on every move after that. */
        void onMove(float x, float y);

        /** @param tap true if the finger never moved further than a tap would */
        void onUp(boolean tap);

        /** A second finger landed, or the system cancelled the gesture. */
        void onCancel();

        /** The finger stayed still for a long press. Returns true if that did something. */
        boolean onLongPress();
    }

    /** For the layers viewer. */
    public interface SwipeListener {
        /** @param direction +1 for a swipe to the left (next), -1 for a swipe to the right (previous) */
        void onSwipe(int direction);
    }

    private static final int S = Fence.CELL_SIZE;
    private static final int BACKGROUND = 0xFF808080;   // java.awt.Color.GRAY
    private static final int ORANGE = 0xFFFFC800;       // java.awt.Color.ORANGE
    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 40f;
    /** Picture corner handles: drawn size, and how far from a corner a touch still grabs it. */
    private static final float HANDLE_DP = 14f;
    private static final float HANDLE_TOUCH_DP = 28f;
    /** Magnifier: radius, distance above the finger, and smallest cell size inside it. */
    private static final float LOUPE_RADIUS_DP = 56f;
    private static final float LOUPE_OFFSET_DP = 96f;
    private static final float LOUPE_MIN_CELL_DP = 28f;
    private static final float SWIPE_MIN_DP = 64f;

    private final float density;
    private final int touchSlop;
    private final int doubleTapSlop;
    private final int doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
    private final int longPressTimeout = ViewConfiguration.getLongPressTimeout();

    private Fence fence;
    /** Clear the area around the fence instead of filling it, so what is behind the window shows. */
    private boolean transparentBackground = false;
    /** Non-null in the layers viewer: strips from the layers underneath, drawn opaque. */
    private Fence below;
    private TouchController controller;
    private SwipeListener swipeListener;

    private float scale = 1f, tx = 0f, ty = 0f;
    private int fittedWidth = -1, fittedHeight = -1;

    private float[] gridLines;
    private Fence gridFor;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint picturePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loupeBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final StripRenderer renderer;
    private final PictureBitmaps pictureBitmaps = new PictureBitmaps();
    private final RectF pictureRect = new RectF();
    private final Path loupeClip = new Path();

    // Touch state
    private boolean toolActive = false;
    private boolean panning = false;
    private boolean dragging = false;
    private boolean multiTouch = false;
    private boolean pinchValid = false;
    private float lastFocusX, lastFocusY, lastSpan;
    private float downX, downY, lastX, lastY;
    private long lastTapTime = 0;
    private float lastTapX, lastTapY;
    private final Runnable longPress = this::fireLongPress;

    public FenceView(Context context) {
        this(context, null);
    }

    public FenceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        doubleTapSlop = vc.getScaledDoubleTapSlop();
        renderer = new StripRenderer(density);

        // Stroke width 0 is a one-pixel hairline at any zoom, like the desktop's 1 px lines.
        gridPaint.setColor(Color.BLACK);
        gridPaint.setStrokeWidth(0f);
        selectedEdgePaint.setColor(ORANGE);
        selectedEdgePaint.setStrokeWidth(0f);
        rowLinePaint.setColor(Color.BLACK);
        rowLinePaint.setStrokeWidth(0f);
        stripPaint.setStyle(Paint.Style.FILL);
        handleOutline.setStyle(Paint.Style.STROKE);
        handleOutline.setColor(Color.BLACK);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(0xFFFF9100);
        loupeBorder.setStyle(Paint.Style.STROKE);
        loupeBorder.setStrokeWidth(2 * density);
        loupeBorder.setColor(Color.WHITE);
        loupeBorder.setShadowLayer(4 * density, 0, density, 0x80000000);
    }

    public void setFence(Fence f) {
        boolean sizeChanged = fence == null || fence.width != f.width || fence.height != f.height;
        fence = f;
        if (sizeChanged) {
            fittedWidth = -1;
            fitIfNeeded();
        }
        invalidate();
    }

    public void setTransparentBackground(boolean transparent) {
        transparentBackground = transparent;
        invalidate();
    }

    public void setBelow(@Nullable Fence b) {
        below = b;
        invalidate();
    }

    public void setController(@Nullable TouchController c) {
        controller = c;
    }

    public void setSwipeListener(@Nullable SwipeListener l) {
        swipeListener = l;
    }

    /** Zooms so the whole fence fits on screen. */
    public void fitToView() {
        if (fence == null || getWidth() == 0 || getHeight() == 0) return;
        float cw = fence.width * S, ch = fence.height * S;
        scale = Math.min(getWidth() / cw, getHeight() / ch) * 0.95f;
        tx = (getWidth() - cw * scale) / 2f;
        ty = (getHeight() - ch * scale) / 2f;
        fittedWidth = getWidth();
        fittedHeight = getHeight();
        invalidate();
    }

    /** Zooms around the middle of the view. For zoom buttons. */
    public void zoomBy(float factor) {
        zoomAround(scale * factor, getWidth() / 2f, getHeight() / 2f);
        invalidate();
    }

    /** Sets the scale, keeping the content at screen point (fx, fy) where it is. */
    private void zoomAround(float newScale, float fx, float fy) {
        float ns = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        float f = ns / scale;
        tx = fx - (fx - tx) * f;
        ty = fy - (fy - ty) * f;
        scale = ns;
    }

    /** A mouse wheel, or a controller thumbstick when the system sends it as scrolling, zooms. */
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v != 0) {
                zoomAround(scale * (float) Math.pow(1.2, v), e.getX(), e.getY());
                invalidate();
                return true;
            }
        }
        return super.onGenericMotionEvent(e);
    }

    private void fitIfNeeded() {
        if (fittedWidth != getWidth() || fittedHeight != getHeight()) fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // CLEAR also wipes out the parent's background under this view, which makes a see-through
        // hole as long as the window itself is translucent.
        if (transparentBackground) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        else canvas.drawColor(BACKGROUND);
        if (fence == null) return;

        canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scale, scale);
        drawContent(canvas, scale);
        canvas.restore();

        if (toolActive && showsLoupe()) drawLoupe(canvas);
    }

    /** Everything on the fence, in content coordinates. zoom is the current content-to-screen scale. */
    private void drawContent(Canvas canvas, float zoom) {
        boolean layered = below != null;

        // As on the desktop, the picture lies under the grid lines and the strips.
        Picture picture = fence.getPicture();
        if (!layered && State.showingPic && picture != null) drawPicture(canvas, picture);

        ensureGrid();
        canvas.drawLines(gridLines, gridPaint);

        if (!layered) {
            Edge se = State.selectedEdge;
            if (se != null && se.isSelected() && fence.edgeAtOrNull(se.cellX(), se.cellY()) == se) {
                drawEdge(canvas, se.cellX(), se.cellY(), se.getSlope(), selectedEdgePaint);
            }
        } else {
            for (Strip s : below.getStrips()) drawStrip(canvas, s, 1f, false, zoom);
        }

        float baseAlpha = layered ? 0.5f : 0.75f;

        if (fence instanceof VerticalFence) {
            rowLinePaint.setAlpha(Math.round(255 * baseAlpha));
            for (int n = 3; n < fence.height; n += 3) {
                canvas.drawLine(0, n * S, fence.width * S, n * S, rowLinePaint);
            }
        }

        Modification am = State.activeModification;
        for (Strip s : fence.getStrips()) {
            float a = baseAlpha;
            boolean selected = !layered && State.selectedStrips.contains(s);
            if (selected && !(am instanceof MoveModification)) a = 0.5f;
            // Outline and shadow also while moving, so the dragged strips stay easy to see.
            drawStrip(canvas, s, a, selected, zoom);
        }

        if (!layered && State.activeStrip != null) drawStrip(canvas, State.activeStrip, 0.75f, false, zoom);

        if (!layered && State.getMode() == State.Mode.PICTURE && State.showingPic && picture != null) {
            drawHandles(canvas, picture, zoom);
        }

        float[] box = State.selectionBox;
        if (!layered && box != null) {
            boxPaint.setStrokeWidth(2 * density / zoom);
            boxPaint.setPathEffect(new DashPathEffect(new float[]{6 * density / zoom, 4 * density / zoom}, 0));
            canvas.drawRect(box[0], box[1], box[2], box[3], boxPaint);
        }
    }

    private boolean showsLoupe() {
        State.Mode m = State.getMode();
        // Box select only applies in Edit mode, so it must not hide the magnifier elsewhere.
        return controller != null && m != State.Mode.PICTURE
                && !(m == State.Mode.EDITING && State.boxSelect);
    }

    /**
     * A round magnifier above the finger, so the edge under the fingertip stays visible.
     * Below the finger instead when there is no room above.
     */
    private void drawLoupe(Canvas canvas) {
        float r = LOUPE_RADIUS_DP * density;
        float cx = lastX;
        float cy = lastY - LOUPE_OFFSET_DP * density;
        if (cy - r < 0) cy = lastY + LOUPE_OFFSET_DP * density;
        cx = Math.max(r, Math.min(getWidth() - r, cx));

        float zoom = Math.max(scale * 2f, LOUPE_MIN_CELL_DP * density / S);
        float fx = (lastX - tx) / scale, fy = (lastY - ty) / scale;   // content point under the finger

        loupeClip.reset();
        loupeClip.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(loupeClip);
        canvas.drawColor(BACKGROUND);
        canvas.translate(cx, cy);
        canvas.scale(zoom, zoom);
        canvas.translate(-fx, -fy);
        drawContent(canvas, zoom);
        canvas.restore();

        canvas.drawCircle(cx, cy, r, loupeBorder);
        float c = 6 * density;
        canvas.drawLine(cx - c, cy, cx + c, cy, loupeBorder);
        canvas.drawLine(cx, cy - c, cx, cy + c, loupeBorder);
    }

    private void drawPicture(Canvas canvas, Picture p) {
        Bitmap bmp = pictureBitmaps.get(p);
        if (bmp == null) return;
        double[] c = p.getCorners();
        pictureRect.set((float) (c[0] * S), (float) (c[1] * S), (float) (c[2] * S), (float) (c[3] * S));
        picturePaint.setAlpha(Math.round(255 * State.pictureOpacity));
        canvas.drawBitmap(bmp, null, pictureRect, picturePaint);
    }

    /**
     * Corner handles in Picture mode, like the desktop's unused Corner class:
     * white squares, cyan for the corner being dragged. Sized in dp, so they ignore zoom.
     */
    private void drawHandles(Canvas canvas, Picture p, float zoom) {
        double[] c = p.getCorners();
        float[][] corners = {
                {(float) c[0] * S, (float) c[1] * S}, {(float) c[2] * S, (float) c[1] * S},
                {(float) c[0] * S, (float) c[3] * S}, {(float) c[2] * S, (float) c[3] * S}};

        int active = -1;
        if (State.activeModification instanceof MovePicModification) {
            MovePicModification m = (MovePicModification) State.activeModification;
            if (m.isResizing()) active = m.getCorner();
        }

        float half = HANDLE_DP * density / zoom / 2f;
        handleOutline.setStrokeWidth(1.5f * density / zoom);
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0], y = corners[i][1];
            handleFill.setColor(i == active ? Color.CYAN : Color.WHITE);
            canvas.drawRect(x - half, y - half, x + half, y + half, handleFill);
            canvas.drawRect(x - half, y - half, x + half, y + half, handleOutline);
        }
    }

    private void drawStrip(Canvas canvas, Strip s, float alpha, boolean selected, float zoom) {
        int col = State.inverted ? invert(s.getColor()) : s.getColor();
        stripPaint.setColor(col);
        stripPaint.setAlpha(Math.round(Color.alpha(col) * alpha));
        if (selected) {
            renderer.drawSelected(canvas, s, stripPaint, zoom);
        } else {
            renderer.draw(canvas, s, stripPaint);
        }
    }

    private static int invert(int c) {
        return Color.argb(Color.alpha(c), 255 - Color.red(c), 255 - Color.green(c), 255 - Color.blue(c));
    }

    private void ensureGrid() {
        if (gridFor == fence && gridLines != null) return;
        int count = 0;
        for (int j = 0; j < fence.width; j++)
            for (int i = 0; i < fence.height; i++)
                if (fence.getEdgeAt(j, i) != null) count++;

        float[] pts = new float[count * 4];
        int k = 0;
        for (int j = 0; j < fence.width; j++) {
            for (int i = 0; i < fence.height; i++) {
                Edge e = fence.getEdgeAt(j, i);
                if (e == null) continue;
                edgeLine(j, i, e.getSlope(), pts, k);
                k += 4;
            }
        }
        gridLines = pts;
        gridFor = fence;
    }

    /** The line an EdgePanel drew inside its cell. */
    private static void edgeLine(int j, int i, int slope, float[] out, int k) {
        float x0 = j * S, y0 = i * S, x1 = x0 + S, y1 = y0 + S;
        float mx = x0 + S / 2f, my = y0 + S / 2f;
        switch (slope) {
            case 0: out[k] = x0; out[k + 1] = y1; out[k + 2] = x1; out[k + 3] = y0; break;
            case 1: out[k] = x0; out[k + 1] = y0; out[k + 2] = x1; out[k + 3] = y1; break;
            case 2: out[k] = x0; out[k + 1] = my; out[k + 2] = x1; out[k + 3] = my; break;
            default: out[k] = mx; out[k + 1] = y0; out[k + 2] = mx; out[k + 3] = y1; break;
        }
    }

    private void drawEdge(Canvas canvas, int j, int i, int slope, Paint paint) {
        float[] l = new float[4];
        edgeLine(j, i, slope, l, 0);
        canvas.drawLine(l[0], l[1], l[2], l[3], paint);
    }

    // ------------------------------------------------------------------ touch

    private float toContentX(float x) {
        return (x - tx) / scale;
    }

    private float toContentY(float y) {
        return (y - ty) / scale;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                multiTouch = false;
                dragging = false;
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                toolActive = controller != null && controller.onDown(
                        toContentX(downX), toContentY(downY), HANDLE_TOUCH_DP * density / scale);
                panning = !toolActive;
                if (toolActive) postDelayed(longPress, longPressTimeout);
                invalidate();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(longPress);
                if (toolActive) {
                    controller.onCancel();
                    toolActive = false;
                }
                panning = false;
                multiTouch = true;
                resetPinch(e, -1);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (multiTouch) {
                    pinch(e);
                    return true;
                }
                float x = e.getX(), y = e.getY();
                if (!dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                    removeCallbacks(longPress);
                }
                if (toolActive) {
                    lastX = x;
                    lastY = y;
                    if (dragging) {
                        // Replay batched samples so fast swipes do not skip edges.
                        for (int h = 0; h < e.getHistorySize(); h++) {
                            controller.onMove(toContentX(e.getHistoricalX(h)), toContentY(e.getHistoricalY(h)));
                        }
                        controller.onMove(toContentX(x), toContentY(y));
                    }
                    invalidate();
                } else if (panning && dragging && swipeListener == null) {
                    tx += x - lastX;
                    ty += y - lastY;
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (multiTouch) resetPinch(e, e.getActionIndex());
                return true;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                boolean tap = !dragging && !multiTouch;
                if (toolActive) controller.onUp(tap);
                if (panning && swipeListener != null && !multiTouch) checkSwipe(e.getX(), e.getY());
                toolActive = false;
                panning = false;
                multiTouch = false;
                if (tap) checkDoubleTap(e.getX(), e.getY());
                performClick();
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (toolActive) controller.onCancel();
                toolActive = false;
                panning = false;
                multiTouch = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void fireLongPress() {
        if (toolActive && !dragging && controller.onLongPress()) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        }
    }

    private void checkDoubleTap(float x, float y) {
        long now = System.currentTimeMillis();
        if (now - lastTapTime < doubleTapTimeout && Math.hypot(x - lastTapX, y - lastTapY) < doubleTapSlop) {
            fitToView();
            lastTapTime = 0;
        } else {
            lastTapTime = now;
            lastTapX = x;
            lastTapY = y;
        }
    }

    private void checkSwipe(float x, float y) {
        float dx = x - downX, dy = y - downY;
        if (Math.abs(dx) > SWIPE_MIN_DP * density && Math.abs(dx) > 2 * Math.abs(dy)) {
            swipeListener.onSwipe(dx < 0 ? 1 : -1);
        }
    }

    /** Records the focus and span of the first two pointers, skipping the one being lifted. */
    private void resetPinch(MotionEvent e, int skipIndex) {
        float[] p = new float[4];
        int n = 0;
        for (int i = 0; i < e.getPointerCount() && n < 2; i++) {
            if (i == skipIndex) continue;
            p[n * 2] = e.getX(i);
            p[n * 2 + 1] = e.getY(i);
            n++;
        }
        pinchValid = n == 2;
        if (!pinchValid) return;
        lastFocusX = (p[0] + p[2]) / 2f;
        lastFocusY = (p[1] + p[3]) / 2f;
        lastSpan = (float) Math.hypot(p[2] - p[0], p[3] - p[1]);
    }

    private void pinch(MotionEvent e) {
        if (e.getPointerCount() < 2) return;
        if (!pinchValid) {
            resetPinch(e, -1);
            return;
        }
        float x0 = e.getX(0), y0 = e.getY(0), x1 = e.getX(1), y1 = e.getY(1);
        float fx = (x0 + x1) / 2f, fy = (y0 + y1) / 2f;
        float span = (float) Math.hypot(x1 - x0, y1 - y0);

        // Pan first, then zoom around the new focus, so the content under the fingers stays put.
        tx += fx - lastFocusX;
        ty += fy - lastFocusY;
        if (lastSpan > 0 && span > 0) zoomAround(scale * span / lastSpan, fx, fy);
        lastFocusX = fx;
        lastFocusY = fy;
        lastSpan = span;
        invalidate();
    }
}
