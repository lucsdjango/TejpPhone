package com.example.tejpphone;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tejpphone.function.State;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.view.FenceView;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

/**
 * Port of the desktop "genlayers" button, which opened one OutputFrame window per layer.
 * Here the layers are paged through instead. Each page shows the layers underneath at full
 * opacity and the current layer at half opacity, as the desktop LayeredFencePanel did.
 */
public class LayersActivity extends AppCompatActivity {

    private static final String KEY_INDEX = "index";

    private List<Fence> layers;
    private int index = 0;
    private FenceView fenceView;
    private TextView label;
    private MaterialButton prev, next;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        if (State.fence == null) {
            finish();
            return;
        }
        layers = State.fence.getLayers();
        if (savedInstanceState != null) {
            index = Math.min(savedInstanceState.getInt(KEY_INDEX, 0), layers.size() - 1);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_background));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, 0, pad, 0);

        prev = new MaterialButton(this);
        prev.setText("Previous");
        prev.setOnClickListener(v -> show(index - 1));

        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(0xFFFFFFFF);

        next = new MaterialButton(this);
        next.setText("Next");
        next.setOnClickListener(v -> show(index + 1));

        MaterialButton close = new MaterialButton(this);
        close.setText("Close");
        close.setOnClickListener(v -> finish());

        bar.addView(prev);
        bar.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(next);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeParams.setMarginStart(pad);
        bar.addView(close, closeParams);

        fenceView = new FenceView(this);

        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(fenceView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        show(index);
    }

    private void show(int i) {
        index = Math.max(0, Math.min(i, layers.size() - 1));
        Fence layer = layers.get(index);

        Fence below = new NormalFence(layer.width, layer.height);
        for (int n = 0; n < index; n++) {
            below.getStrips().addAll(layers.get(n).getStrips());
        }

        fenceView.setFence(layer);
        fenceView.setBelow(below);

        label.setText(String.format(Locale.US, "Layer %d of %d", index + 1, layers.size()));
        prev.setEnabled(index > 0);
        next.setEnabled(index < layers.size() - 1);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_INDEX, index);
    }
}
