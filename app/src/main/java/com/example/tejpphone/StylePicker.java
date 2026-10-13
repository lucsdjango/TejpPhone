package com.example.tejpphone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.widget.NestedScrollView;

import com.example.tejpphone.function.FenceController;
import com.example.tejpphone.function.State;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sheet for choosing the strip width and colour, with each width drawn as a line and each colour
 * as a swatch. Replaces the desktop's two combo boxes. As there, a choice also applies to the
 * selected strips.
 */
final class StylePicker {

    private final AppCompatActivity activity;
    private final FenceController controller;
    private final Runnable onChanged;
    private final float dp;

    private final List<View> widthCells = new ArrayList<>();
    private final List<Float> widthValues = new ArrayList<>();
    private ChipGroup colourRow;

    private StylePicker(AppCompatActivity activity, FenceController controller, Runnable onChanged) {
        this.activity = activity;
        this.controller = controller;
        this.onChanged = onChanged;
        this.dp = activity.getResources().getDisplayMetrics().density;
    }

    static void show(AppCompatActivity activity, FenceController controller, Runnable onChanged) {
        new StylePicker(activity, controller, onChanged).build();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private void build() {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(16), px(8), px(16), px(24));

        content.addView(heading("Width"));
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(4);
        for (String label : State.WIDTH_LABELS) {
            float w = State.parseWidth(label);
            View cell = widthCell(w, label.replace(" cm", ""));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            grid.addView(cell, lp);
            widthCells.add(cell);
            widthValues.add(w);
        }
        content.addView(grid);

        content.addView(heading("Colour"));
        colourRow = new ChipGroup(activity);
        colourRow.setChipSpacing(px(12));
        content.addView(colourRow);
        TextView hint = new TextView(activity);
        hint.setText("Tap + to mix a colour. Hold a mixed colour to remove it.");
        hint.setTextColor(0xB3FFFFFF);
        hint.setPadding(0, px(8), 0, 0);
        content.addView(hint);

        fillColours();
        markWidth();

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.addView(content);
        sheet.setContentView(scroll);
        sheet.show();
    }

    private TextView heading(String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, px(16), 0, px(8));
        return t;
    }

    // ------------------------------------------------------------------ widths

    private View widthCell(float width, String label) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(px(6), px(8), px(6), px(8));
        cell.setContentDescription("Width " + label + " cm");

        View sample = new WidthSample(activity, width);
        cell.addView(sample, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(30)));

        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        cell.addView(t);

        cell.setOnClickListener(v -> {
            controller.setWidth(width);
            markWidth();
            onChanged.run();
        });
        return cell;
    }

    private void markWidth() {
        for (int i = 0; i < widthCells.size(); i++) {
            boolean current = widthValues.get(i) == State.currentStripWidth;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(8));
            bg.setColor(current ? 0xFF4A4458 : 0x00000000);
            if (current) bg.setStroke(px(2), 0xFFFF9100);
            widthCells.get(i).setBackground(bg);
        }
    }

    /** A line as thick as a strip of this width would be, relative to the others. */
    private static final class WidthSample extends View {
        private final float width;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WidthSample(Context c, float width) {
            super(c);
            this.width = width;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float max = getHeight() - 2;
            // 6 cm, the widest, fills the cell's height.
            float h = Math.max(1f, Math.min(max, width / 6f * max));
            paint.setColor(State.currentColor);
            float cy = getHeight() / 2f;
            canvas.drawRect(4, cy - h / 2, getWidth() - 4, cy + h / 2, paint);
        }
    }

    // ------------------------------------------------------------------ colours

    private void fillColours() {
        colourRow.removeAllViews();
        for (Map.Entry<String, Integer> e : State.colors.entrySet()) {
            colourRow.addView(swatch(e.getValue(), e.getKey().charAt(0) + e.getKey().substring(1).toLowerCase(Locale.ROOT), false));
        }
        for (int c : State.customColors) {
            colourRow.addView(swatch(c, String.format(Locale.US, "Colour #%06X", c & 0xFFFFFF), true));
        }

        ImageButton add = new ImageButton(activity);
        add.setImageResource(R.drawable.ic_add);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(px(2), 0x80FFFFFF);
        add.setBackground(ring);
        add.setContentDescription("Mix a colour");
        TooltipCompat.setTooltipText(add, "Mix a colour");
        add.setOnClickListener(v -> mixColour());
        colourRow.addView(add, new ViewGroup.LayoutParams(px(40), px(40)));
    }

    private View swatch(int colour, String name, boolean custom) {
        View v = new View(activity);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(colour);
        boolean current = colour == State.currentColor;
        d.setStroke(current ? px(4) : px(1), current ? 0xFFFF9100 : 0x80000000);
        v.setBackground(d);
        v.setContentDescription(name);
        TooltipCompat.setTooltipText(v, name);
        v.setLayoutParams(new ViewGroup.LayoutParams(px(40), px(40)));
        v.setOnClickListener(x -> {
            controller.setColor(colour);
            fillColours();
            redrawWidthSamples();
            onChanged.run();
        });
        if (custom) {
            v.setOnLongClickListener(x -> {
                controller.removeCustomColor(colour);
                fillColours();
                Toast.makeText(activity, "Removed " + name, Toast.LENGTH_SHORT).show();
                return true;
            });
        }
        return v;
    }

    /** The width samples are drawn in the current colour. */
    private void redrawWidthSamples() {
        for (View cell : widthCells) ((ViewGroup) cell).getChildAt(0).invalidate();
    }

    /** A small hue, saturation and brightness mixer. */
    private void mixColour() {
        float[] hsv = new float[3];
        Color.colorToHSV(State.currentColor, hsv);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(24), px(8), px(24), 0);

        View preview = new View(activity);
        GradientDrawable pd = new GradientDrawable();
        pd.setCornerRadius(px(8));
        pd.setColor(Color.HSVToColor(hsv));
        preview.setBackground(pd);
        box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48)));

        String[] names = {"Hue", "Saturation", "Brightness"};
        float[] max = {360f, 1f, 1f};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView label = new TextView(activity);
            label.setText(names[i]);
            label.setPadding(0, px(12), 0, 0);
            box.addView(label);
            Slider s = new Slider(activity);
            s.setValueFrom(0);
            s.setValueTo(max[i]);
            s.setValue(Math.max(0, Math.min(max[i], hsv[i])));
            s.setContentDescription(names[i]);
            s.addOnChangeListener((sl, value, fromUser) -> {
                hsv[k] = value;
                pd.setColor(Color.HSVToColor(hsv));
            });
            box.addView(s);
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Mix a colour")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use", (d, w) -> {
                    int c = Color.HSVToColor(hsv);
                    controller.addCustomColor(c);
                    controller.setColor(c);
                    fillColours();
                    redrawWidthSamples();
                    onChanged.run();
                })
                .show();
    }
}
