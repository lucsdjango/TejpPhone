package com.example.tejpphone;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.appcompat.widget.TooltipCompat;

import com.google.android.material.slider.Slider;

/**
 * Puts a − and a + button at the ends of a slider, each moving it by 1% of its range. Fine
 * adjustments are hard to make by dragging on a phone.
 */
final class SliderSteps {

    private SliderSteps() {}

    /**
     * Wraps the slider in place with its buttons. A step sets the slider's value and then calls
     * {@code onStep} with fromUser true, as a drag would.
     */
    static void attach(Slider slider, Slider.OnChangeListener onStep) {
        Context c = slider.getContext();
        ViewGroup parent = (ViewGroup) slider.getParent();
        int index = parent.indexOfChild(slider);
        ViewGroup.LayoutParams lp = slider.getLayoutParams();
        parent.removeViewAt(index);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String name = slider.getContentDescription() == null ? "" : slider.getContentDescription() + " ";
        row.addView(button(c, R.drawable.ic_remove, "Decrease " + name + "by 1%", () -> step(slider, -1, onStep)));
        row.addView(slider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button(c, R.drawable.ic_add, "Increase " + name + "by 1%", () -> step(slider, 1, onStep)));
        parent.addView(row, index, lp);
    }

    /** Enables or disables the slider together with its buttons. */
    static void setEnabled(Slider slider, boolean enabled) {
        ViewGroup row = (ViewGroup) slider.getParent();
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setEnabled(enabled);
    }

    private static void step(Slider slider, int direction, Slider.OnChangeListener onStep) {
        float from = slider.getValueFrom();
        float to = slider.getValueTo();
        float step = (to - from) / 100f;
        // Snap to whole percents so repeated steps don't drift.
        float v = from + (Math.round((slider.getValue() - from) / step) + direction) * step;
        v = Math.max(from, Math.min(to, v));
        if (v == slider.getValue()) return;
        slider.setValue(v);
        onStep.onValueChange(slider, v, true);
    }

    private static ImageButton button(Context c, @DrawableRes int icon, String name, Runnable action) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(icon);
        b.setContentDescription(name);
        TooltipCompat.setTooltipText(b, name);
        TypedValue ripple = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        b.setBackgroundResource(ripple.resourceId);
        b.setOnClickListener(v -> action.run());
        int size = Math.round(36 * c.getResources().getDisplayMetrics().density);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return b;
    }
}
