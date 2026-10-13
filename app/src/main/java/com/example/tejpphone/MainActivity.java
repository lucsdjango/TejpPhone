package com.example.tejpphone;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.tejpphone.function.FenceController;
import com.example.tejpphone.function.History;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Picture;
import com.example.tejpphone.model.TjpFile;
import com.example.tejpphone.view.FenceView;
import com.example.tejpphone.view.ImageExporter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int DEFAULT_WIDTH = 64;
    private static final int DEFAULT_HEIGHT = 32;

    private static final State.Mode[] TOOLS =
            {State.Mode.DRAWING, State.Mode.EDITING, State.Mode.MOVING, State.Mode.PICTURE};
    private static final String[] TOOL_NAMES = {"Draw", "Edit", "Move", "Picture"};
    private static final int[] TOOL_ICONS = {R.drawable.ic_draw, R.drawable.ic_select, R.drawable.ic_move, R.drawable.ic_picture};

    /** The drawing state when the last save started; static so it survives rotation. */
    private static Object pendingSaveState;

    private FenceView fenceView;
    private FenceController controller;
    private MaterialToolbar topBar;
    private ChipGroup contextRow;
    private View picturePanel, autoPanel, selectionBar;
    private TextView selectionCount, autoReason;
    private ImageButton undoButton, redoButton;
    private final List<MaterialButton> toolButtons = new ArrayList<>();
    private Slider thresholdSlider, contrastSlider, opacitySlider;
    private TextView thresholdValue, contrastValue, opacityValue;

    private FileViewModel files;
    private ActivityResultLauncher<String[]> openLauncher;
    private ActivityResultLauncher<String> createLauncher;
    private ActivityResultLauncher<String[]> pictureLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    private boolean refreshPending = false;
    private boolean warnedThisDrag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // State is static, so the drawing survives rotation. When the app process starts,
        // bring back the autosave if there is one.
        if (State.fence == null && !Autosave.restore(this)) {
            State.fence = new NormalFence(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        fenceView = findViewById(R.id.fenceView);
        controller = new FenceController(this::onDrawingChanged);
        controller.setSnapFeedback(() -> fenceView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK));
        fenceView.setController(controller);
        fenceView.setFence(State.fence);

        topBar = findViewById(R.id.topBar);
        topBar.setOnMenuItemClickListener(this::onMenuItem);
        contextRow = findViewById(R.id.contextRow);
        picturePanel = findViewById(R.id.picturePanel);
        autoPanel = findViewById(R.id.autoPanel);
        autoReason = findViewById(R.id.autoReason);
        selectionBar = findViewById(R.id.selectionBar);
        selectionCount = findViewById(R.id.selectionCount);

        buildBottomBar();
        buildSelectionBar();
        buildPicturePanel();
        buildAutoPanel();
        buildContextRow();
        setUpFiles();
        refreshUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Autosave.save(this);
    }

    /** Called by the controller after every change. Redraws now, updates the controls once per frame. */
    private void onDrawingChanged() {
        fenceView.invalidate();
        if (refreshPending) return;
        refreshPending = true;
        fenceView.post(() -> {
            refreshPending = false;
            refreshUi();
        });
    }

    private void refreshUi() {
        topBar.setTitle(State.fileName != null ? State.fileName : "Untitled");
        topBar.setSubtitle(History.isDirty() ? "Unsaved changes" : null);
        MenuItem invert = topBar.getMenu().findItem(R.id.action_invert);
        if (invert != null) invert.setChecked(State.inverted);

        setEnabled(undoButton, History.canUndo());
        setEnabled(redoButton, History.canRedo());

        int n = State.selectedStrips.size();
        selectionBar.setVisibility(n > 0 && State.activeModification == null ? View.VISIBLE : View.GONE);
        selectionCount.setText(n == 1 ? "1 strip selected" : n + " strips selected");

        boolean picture = State.getMode() == State.Mode.PICTURE;
        picturePanel.setVisibility(picture ? View.VISIBLE : View.GONE);
        autoPanel.setVisibility(picture && State.autoPanelOpen ? View.VISIBLE : View.GONE);
        refreshAutoPanel();
    }

    private static void setEnabled(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.38f);
    }

    // ------------------------------------------------------------------ bottom bar: tools, undo, redo

    private void buildBottomBar() {
        MaterialButtonToggleGroup group = findViewById(R.id.toolGroup);
        ColorStateList checkedBg = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFF4A4458, 0x00000000});
        for (int i = 0; i < TOOLS.length; i++) {
            MaterialButton b = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
            b.setId(View.generateViewId());
            b.setText(TOOL_NAMES[i]);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setIconResource(TOOL_ICONS[i]);
            b.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);
            b.setIconPadding(0);
            b.setInsetTop(0);
            b.setInsetBottom(0);
            b.setMinHeight(dp(64));
            b.setMinimumHeight(dp(64));
            b.setPadding(0, dp(8), 0, dp(8));
            b.setCheckable(true);
            b.setBackgroundTintList(checkedBg);
            group.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            toolButtons.add(b);
            if (TOOLS[i] == State.getMode()) group.check(b.getId());
        }
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            for (int i = 0; i < toolButtons.size(); i++) {
                if (toolButtons.get(i).getId() == id && State.getMode() != TOOLS[i]) {
                    controller.setMode(TOOLS[i]);
                    buildContextRow();
                    refreshUi();
                }
            }
        });

        undoButton = findViewById(R.id.undoButton);
        redoButton = findViewById(R.id.redoButton);
        TooltipCompat.setTooltipText(undoButton, "Undo");
        TooltipCompat.setTooltipText(redoButton, "Redo");
        undoButton.setOnClickListener(v -> controller.undo());
        redoButton.setOnClickListener(v -> controller.redo());
    }

    // ------------------------------------------------------------------ context row for the current tool

    private void buildContextRow() {
        contextRow.removeAllViews();
        switch (State.getMode()) {
            case DRAWING:
                Chip width = action(contextRow, widthLabel(), this::openStylePicker);
                width.setChipIconResource(R.drawable.ic_width);
                width.setChipIconVisible(true);
                Chip colour = action(contextRow, colourName(State.currentColor), this::openStylePicker);
                colour.setChipIcon(swatch(State.currentColor));
                colour.setChipIconVisible(true);
                hint("Hold a strip's end to drag it");
                break;

            case EDITING:
                toggle(contextRow, "Box select", State.boxSelect, on -> {
                    controller.setBoxSelect(on);
                    buildContextRow();
                });
                action(contextRow, "Select all", controller::selectAll);
                action(contextRow, "Select none", controller::deselectAll);
                action(contextRow, "Select last", controller::selectLast);
                action(contextRow, "Same width", controller::selectSameWidth);
                action(contextRow, "Same colour", controller::selectSameColor);
                hint(State.boxSelect ? "Drag a box to select" : "Tap to add or remove, drag to paint");
                break;

            case MOVING:
                action(contextRow, "Select all", controller::selectAll);
                action(contextRow, "Select none", controller::deselectAll);
                hint("Drag to move the selected strips");
                break;

            case PICTURE:
                action(contextRow, "Load picture", () -> pictureLauncher.launch(new String[]{"image/*"}));
                toggle(contextRow, "Show", State.showingPic, controller::setShowingPic);
                toggle(contextRow, "Keep aspect", State.keepPictureAspect, controller::setKeepPictureAspect);
                action(contextRow, "Remove", controller::removePicture);
                toggle(contextRow, "Auto strips", State.autoPanelOpen, on -> {
                    State.autoPanelOpen = on;
                    refreshUi();
                });
                hint("Drag to move, drag a corner to resize");
                break;
        }
    }

    private void hint(String text) {
        ((TextView) findViewById(R.id.contextHint)).setText(text);
    }

    private String widthLabel() {
        for (String l : State.WIDTH_LABELS) {
            if (State.parseWidth(l) == State.currentStripWidth) return "Width " + l;
        }
        return String.format(Locale.US, "Width %s cm", State.currentStripWidth);
    }

    private static String colourName(int colour) {
        for (Map.Entry<String, Integer> e : State.colors.entrySet()) {
            if (e.getValue() == colour) {
                return e.getKey().charAt(0) + e.getKey().substring(1).toLowerCase(Locale.ROOT);
            }
        }
        return String.format(Locale.US, "#%06X", colour & 0xFFFFFF);
    }

    private GradientDrawable swatch(int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(colour);
        d.setStroke(2, 0xFF000000);
        return d;
    }

    private void openStylePicker() {
        StylePicker.show(this, controller, () -> {
            if (State.getMode() == State.Mode.DRAWING) buildContextRow();
        });
    }

    // ------------------------------------------------------------------ selection bar

    private void buildSelectionBar() {
        LinearLayout row = findViewById(R.id.selectionButtons);
        selectionButton(row, R.drawable.ic_palette, "Colour and width", this::openStylePicker);
        selectionButton(row, R.drawable.ic_copy, "Copy", controller::copySelected);
        selectionButton(row, R.drawable.ic_mirror, "Mirror", controller::mirrorSelected);
        selectionButton(row, R.drawable.ic_to_front, "To front", controller::moveToFront);
        selectionButton(row, R.drawable.ic_to_back, "To back", controller::moveToBack);
        selectionButton(row, R.drawable.ic_delete, "Delete", controller::deleteSelected);
        selectionButton(row, R.drawable.ic_close, "Clear selection", controller::deselectAll);
    }

    private void selectionButton(LinearLayout row, @DrawableRes int icon, String name, Runnable action) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(name);
        TooltipCompat.setTooltipText(b, name);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        b.setBackgroundResource(ripple.resourceId);
        b.setOnClickListener(v -> action.run());
        row.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
    }

    // ------------------------------------------------------------------ picture and auto strips panels

    private void buildPicturePanel() {
        opacitySlider = findViewById(R.id.opacitySlider);
        opacityValue = findViewById(R.id.opacityValue);
        opacitySlider.setValue(State.pictureOpacity);
        opacityValue.setText(percent(State.pictureOpacity));
        opacitySlider.addOnChangeListener((s, value, fromUser) -> {
            opacityValue.setText(percent(value));
            if (fromUser) controller.setPictureOpacity(value);
        });
    }

    private static String percent(float v) {
        return Math.round(v * 100) + "%";
    }

    private void buildAutoPanel() {
        ChipGroup group = findViewById(R.id.autoDirectionGroup);
        State.AutoDirection[] dirs = State.AutoDirection.values();
        String[] labels = {"Horizontal", "Diagonal", "Vertical"};
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < dirs.length; i++) {
            Chip chip = inflateChip(R.layout.chip_toggle, group, labels[i]);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            ids.add(chip.getId());
            group.addView(chip);
            if (dirs[i] == State.autoDirection) group.check(chip.getId());
        }
        group.setOnCheckedStateChangeListener((g, checked) -> {
            if (checked.isEmpty()) return;
            controller.setAutoDirection(dirs[ids.indexOf(checked.get(0))]);
            showAutoValues();
        });

        thresholdSlider = findViewById(R.id.thresholdSlider);
        contrastSlider = findViewById(R.id.contrastSlider);
        thresholdValue = findViewById(R.id.thresholdValue);
        contrastValue = findViewById(R.id.contrastValue);
        showAutoValues();

        // Like the desktop, moving a slider stores its value for the current direction
        // and recomputes the auto strips straight away.
        thresholdSlider.addOnChangeListener((s, value, fromUser) -> {
            thresholdValue.setText(String.format(Locale.US, "%.2f", value));
            if (!fromUser) return;
            State.autoThreshold[State.autoDirection.ordinal()] = value;
            runAutoStrips();
        });
        contrastSlider.addOnChangeListener((s, value, fromUser) -> {
            contrastValue.setText(String.format(Locale.US, "%.2f", value));
            if (!fromUser) return;
            State.autoContrast[State.autoDirection.ordinal()] = value;
            runAutoStrips();
        });
        Slider.OnSliderTouchListener resetWarning = new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                warnedThisDrag = false;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {}
        };
        thresholdSlider.addOnSliderTouchListener(resetWarning);
        contrastSlider.addOnSliderTouchListener(resetWarning);
    }

    private void showAutoValues() {
        int d = State.autoDirection.ordinal();
        thresholdSlider.setValue(State.autoThreshold[d]);
        contrastSlider.setValue(State.autoContrast[d]);
        thresholdValue.setText(String.format(Locale.US, "%.2f", State.autoThreshold[d]));
        contrastValue.setText(String.format(Locale.US, "%.2f", State.autoContrast[d]));
    }

    /** Sliders are only usable with a picture on a fence type that has auto strips. */
    private void refreshAutoPanel() {
        Picture p = State.fence.getPicture();
        String reason = null;
        if (p == null || !p.hasPixels()) {
            reason = "Load a picture to use auto strips.";
        } else if (!(State.fence instanceof NormalFence)
                && !(State.fence instanceof com.example.tejpphone.model.VerticalFence)) {
            reason = "Auto strips work on diagonal and vertical fences only.";
        }
        autoReason.setText(reason);
        autoReason.setVisibility(reason == null ? View.GONE : View.VISIBLE);
        thresholdSlider.setEnabled(reason == null);
        contrastSlider.setEnabled(reason == null);
    }

    private void runAutoStrips() {
        if (controller.runAutoStrips() || warnedThisDrag) return;
        warnedThisDrag = true;
        refreshAutoPanel();
    }

    // ------------------------------------------------------------------ top bar menu

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_save_as) {
            saveAs();
        } else if (id == R.id.action_load) {
            confirmDiscard("Open another drawing?", () -> openLauncher.launch(new String[]{"*/*"}));
        } else if (id == R.id.action_export) {
            exportJpg();
        } else if (id == R.id.action_layers) {
            startActivity(new Intent(this, LayersActivity.class));
        } else if (id == R.id.action_invert) {
            controller.setInverted(!State.inverted);
        } else if (id == R.id.action_fit) {
            fenceView.fitToView();
        } else if (id == R.id.action_new_normal) {
            confirmDiscard("Start a new diagonal fence?", () -> newFence(controller::newNormalFence));
        } else if (id == R.id.action_new_vertical) {
            confirmDiscard("Start a new vertical fence?", () -> newFence(controller::newVerticalFence));
        } else {
            return false;
        }
        return true;
    }

    private void newFence(Runnable create) {
        create.run();
        fenceView.setFence(State.fence);
        fenceView.fitToView();
        refreshUi();
    }

    /** Runs action straight away if everything is saved, otherwise asks first. */
    private void confirmDiscard(String title, Runnable action) {
        if (!History.isDirty()) {
            action.run();
            return;
        }
        String name = State.fileName != null ? State.fileName : "This drawing";
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(name + " has changes that are not saved. They will be lost.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Discard changes", (d, w) -> action.run())
                .show();
    }

    // ------------------------------------------------------------------ files

    private void setUpFiles() {
        files = new ViewModelProvider(this).get(FileViewModel.class);

        // .tjp has no registered MIME type, so the picker shows all files.
        openLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            keepAccess(uri);
            files.load(uri);
        });
        createLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                    if (uri == null) return;
                    keepAccess(uri);
                    pendingSaveState = History.currentState();
                    files.save(uri, serialize(), false);
                });
        pictureLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) files.loadPicture(uri);
        });
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/jpeg"), uri -> {
                    if (uri == null) return;
                    try {
                        files.exportJpg(uri, ImageExporter.render(State.fence));
                    } catch (OutOfMemoryError e) {
                        Toast.makeText(this, "Not enough memory to export", Toast.LENGTH_LONG).show();
                    }
                });

        files.getResults().observe(this, this::onFileResult);
    }

    /** Overwrites the current file, or asks where to save if there is none. */
    private void save() {
        if (State.fileUri == null) {
            saveAs();
        } else {
            pendingSaveState = History.currentState();
            files.save(Uri.parse(State.fileUri), serialize(), true);
        }
    }

    private void saveAs() {
        String name = State.fileName != null ? State.fileName : "tejp.tjp";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".tjp")) name += ".tjp";
        createLauncher.launch(name);
    }

    private void exportJpg() {
        String base = State.fileName != null ? State.fileName : "tejp";
        if (base.toLowerCase(Locale.ROOT).endsWith(".tjp")) base = base.substring(0, base.length() - 4);
        exportLauncher.launch(base + ".jpg");
    }

    /** Snapshot of the drawing, taken on the main thread so it cannot change while being written. */
    private byte[] serialize() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            TjpFile.write(State.fence, out);
        } catch (IOException e) {
            throw new IllegalStateException(e);   // Writing to memory does not fail.
        }
        return out.toByteArray();
    }

    /** Keeps read and write access, so Save can overwrite the file later. */
    private void keepAccess(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            // The provider does not allow it. Save will fall back to Save as if overwriting fails.
        }
    }

    private void onFileResult(FileViewModel.Result r) {
        if (r == null || !r.markHandled()) return;
        switch (r.kind) {
            case LOADED:
                controller.loadFence(r.fence, r.uri.toString(), r.name);
                fenceView.setFence(State.fence);
                fenceView.fitToView();
                Toast.makeText(this, "Opened " + r.name, Toast.LENGTH_SHORT).show();
                break;
            case SAVED:
                State.fileUri = r.uri.toString();
                State.fileName = r.name;
                History.markSaved(pendingSaveState);
                Toast.makeText(this, "Saved " + r.name, Toast.LENGTH_SHORT).show();
                break;
            case PICTURE_LOADED:
                controller.loadPicture(r.pixels, r.width, r.height);
                buildContextRow();
                Toast.makeText(this, "Loaded picture " + r.name, Toast.LENGTH_SHORT).show();
                break;
            case EXPORTED:
                Toast.makeText(this, "Exported " + r.name, Toast.LENGTH_SHORT).show();
                break;
            case SAVE_NEEDS_NEW_FILE:
                Toast.makeText(this, "Could not overwrite " + r.name + ". Choose where to save.",
                        Toast.LENGTH_LONG).show();
                saveAs();
                break;
            case ERROR:
                new MaterialAlertDialogBuilder(this)
                        .setTitle("File problem")
                        .setMessage(r.message)
                        .setPositiveButton("OK", null)
                        .show();
                break;
        }
        refreshUi();
    }

    // ------------------------------------------------------------------ chip helpers

    private interface Toggle {
        void set(boolean on);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Chip inflateChip(@LayoutRes int layout, ChipGroup parent, String text) {
        Chip chip = (Chip) getLayoutInflater().inflate(layout, parent, false);
        chip.setText(text);
        return chip;
    }

    private Chip action(ChipGroup row, String text, Runnable onClick) {
        Chip chip = inflateChip(R.layout.chip_action, row, text);
        chip.setOnClickListener(v -> onClick.run());
        row.addView(chip);
        return chip;
    }

    private Chip toggle(ChipGroup row, String text, boolean initial, Toggle onChange) {
        Chip chip = inflateChip(R.layout.chip_toggle, row, text);
        chip.setCheckable(true);
        chip.setChecked(initial);
        chip.setOnCheckedChangeListener((b, on) -> onChange.set(on));
        row.addView(chip);
        return chip;
    }
}
