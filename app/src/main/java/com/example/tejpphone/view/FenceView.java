package com.example.tejpphone.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tejpphone.function.Modification;
import com.example.tejpphone.function.MoveModification;
import com.example.tejpphone.function.SelectStripsModification;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;

/**
 * Draws a fence and its strips. Replaces the desktop FencePanel, LayeredFencePanel and EdgePanels.
 * One finger is passed to the TouchController as if it were the mouse.
 * Two fingers pan and pinch-zoom, replacing the scroll bars and the +/- keys.
 */
public class FenceView extends View {

    /** Receives one-finger gestures in content coordinates. */
    public interface TouchController {
        /** Returns true if a gesture started. Only then are onMove/onUp/onCancel called. */
        boolean onDown(float x, float y);

        void onMove(float x, float y);

        void onUp();

        /** A second finger landed, or the system cancelled the gesture. */
        void onCancel();
    }

    private static final int S = Fence.CELL_SIZE;
    private static final int BACKGROUND = 0xFF808080;   // java.awt.Color.GRAY
    private static final int ORANGE = 0xFFFFC800;       // java.awt.Color.ORANGE
    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 40f;

    private Fence fence;
    /** Non-null in the layers viewer: strips from the layers underneath, drawn opaque. */
    private Fence below;
    private TouchController controller;

    private float scale = 1f, tx = 0f, ty = 0f;
    private int fittedWidth = -1, fittedHeight = -1;

    private float[] gridLines;
    private Fence gridFor;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final StripRenderer renderer;

    private boolean toolActive = false;
    private boolean multiTouch = false;
    private boolean pinchValid = false;
    private float lastFocusX, lastFocusY, lastSpan;

    public FenceView(Context context) {
        this(context, null);
    }

    public FenceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        renderer = new StripRenderer(getResources().getDisplayMetrics().density);
        // Stroke width 0 is a one-pixel hairline at any zoom, like the desktop's 1 px lines.
        gridPaint.setColor(Color.BLACK);
        gridPaint.setStrokeWidth(0f);
        selectedEdgePaint.setColor(ORANGE);
        selectedEdgePaint.setStrokeWidth(0f);
        rowLinePaint.setColor(Color.BLACK);
        rowLinePaint.setStrokeWidth(0f);
        stripPaint.setStyle(Paint.Style.FILL);
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

    public void setBelow(@Nullable Fence b) {
        below = b;
        invalidate();
    }

    public void setController(@Nullable TouchController c) {
        controller = c;
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
        canvas.drawColor(BACKGROUND);
        if (fence == null) return;

        canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scale, scale);

        ensureGrid();
        canvas.drawLines(gridLines, gridPaint);

        boolean layered = below != null;

        if (!layered) {
            Edge se = State.selectedEdge;
            if (se != null && se.isSelected() && fence.edgeAtOrNull(se.cellX(), se.cellY()) == se) {
                drawEdge(canvas, se.cellX(), se.cellY(), se.getSlope(), selectedEdgePaint);
            }
        } else {
            for (Strip s : below.getStrips()) drawStrip(canvas, s, 1f);
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
            boolean selected = false;
            if (!layered) {
                boolean inSelection = State.selectedStrips.contains(s);
                boolean beingSelected = am instanceof SelectStripsModification
                        && ((SelectStripsModification) am).getStrips().contains(s);
                if (inSelection && !(am instanceof MoveModification)) a = 0.5f;
                if (beingSelected) a = 0.5f;
                // Outline and shadow also while moving, so the dragged strips stay easy to see.
                selected = inSelection || beingSelected;
            }
            drawStrip(canvas, s, a, selected);
        }

        if (!layered && State.activeStrip != null) drawStrip(canvas, State.activeStrip, 0.75f, false);

        canvas.restore();
    }

    private void drawStrip(Canvas canvas, Strip s, float alpha) {
        drawStrip(canvas, s, alpha, false);
    }

    private void drawStrip(Canvas canvas, Strip s, float alpha, boolean selected) {
        int col = State.inverted ? invert(s.getColor()) : s.getColor();
        stripPaint.setColor(col);
        stripPaint.setAlpha(Math.round(Color.alpha(col) * alpha));
        if (selected) {
            renderer.drawSelected(canvas, s, stripPaint, scale);
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
                toolActive = controller != null
                        && controller.onDown(toContentX(e.getX()), toContentY(e.getY()));
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (toolActive) {
                    controller.onCancel();
                    toolActive = false;
                }
                multiTouch = true;
                resetPinch(e, -1);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (multiTouch) {
                    pinch(e);
                } else if (toolActive) {
                    // Replay batched samples so fast swipes do not skip edges.
                    for (int h = 0; h < e.getHistorySize(); h++) {
                        controller.onMove(toContentX(e.getHistoricalX(h)), toContentY(e.getHistoricalY(h)));
                    }
                    controller.onMove(toContentX(e.getX()), toContentY(e.getY()));
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (multiTouch) resetPinch(e, e.getActionIndex());
                return true;

            case MotionEvent.ACTION_UP:
                if (toolActive) controller.onUp();
                toolActive = false;
                multiTouch = false;
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (toolActive) controller.onCancel();
                toolActive = false;
                multiTouch = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
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
        if (lastSpan > 0 && span > 0) {
            float ns = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * span / lastSpan));
            float f = ns / scale;
            tx = fx - (fx - tx) * f;
            ty = fy - (fy - ty) * f;
            scale = ns;
        }
        lastFocusX = fx;
        lastFocusY = fy;
        lastSpan = span;
        invalidate();
    }
}
