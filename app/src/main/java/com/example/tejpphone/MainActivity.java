package com.example.tejpphone;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.tejpphone.function.FenceController;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.TjpFile;
import com.example.tejpphone.view.FenceView;
import com.example.tejpphone.view.ImageExporter;
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

    /** Default fence size from the desktop TejpMain. */
    private static final int DEFAULT_WIDTH = 64;
    private static final int DEFAULT_HEIGHT = 32;

    private FenceView fenceView;
    private FenceController controller;
    private Chip widthChip, colorChip;

    private FileViewModel files;
    private ActivityResultLauncher<String[]> openLauncher;
    private ActivityResultLauncher<String> createLauncher;
    private ActivityResultLauncher<String[]> pictureLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    private Chip showPicChip;
    /** Static, like State, so the panel stays open across rotation. */
    private static boolean autoPanelOpen = false;
    private Slider thresholdSlider, contrastSlider;
    private boolean warnedThisDrag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // State is static, so the drawing survives rotation. Only create a fence on first start.
        if (State.fence == null) {
            State.fence = new NormalFence(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        fenceView = findViewById(R.id.fenceView);
        controller = new FenceController(fenceView::invalidate);
        fenceView.setController(controller);
        fenceView.setFence(State.fence);

        buildModeChips();
        buildToolRow();
        buildActionRow();
        buildAutoPanel();
        setUpFiles();
    }

    // ------------------------------------------------------------------ auto strips

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
        showAutoValues();

        // Like the desktop, moving a slider stores its value for the current direction
        // and recomputes the auto strips straight away.
        thresholdSlider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            State.autoThreshold[State.autoDirection.ordinal()] = value;
            runAutoStrips();
        });
        contrastSlider.addOnChangeListener((s, value, fromUser) -> {
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

        setAutoPanelOpen(autoPanelOpen);
    }

    private void showAutoValues() {
        int d = State.autoDirection.ordinal();
        thresholdSlider.setValue(State.autoThreshold[d]);
        contrastSlider.setValue(State.autoContrast[d]);
    }

    private void runAutoStrips() {
        if (controller.runAutoStrips() || warnedThisDrag) return;
        warnedThisDrag = true;
        String why = State.fence.getPicture() == null || !State.fence.getPicture().hasPixels()
                ? "Load a picture first."
                : "Auto strips work on normal and vertical fences only.";
        Toast.makeText(this, why, Toast.LENGTH_SHORT).show();
    }

    private void setAutoPanelOpen(boolean open) {
        autoPanelOpen = open;
        findViewById(R.id.autoPanel).setVisibility(open ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ picture export

    private void exportJpg() {
        String base = State.fileName != null ? State.fileName : "tejp";
        if (base.toLowerCase(Locale.ROOT).endsWith(".tjp")) base = base.substring(0, base.length() - 4);
        exportLauncher.launch(base + ".jpg");
    }

    // ------------------------------------------------------------------ save and load

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
            files.save(Uri.parse(State.fileUri), serialize(), true);
        }
    }

    private void saveAs() {
        String name = State.fileName != null ? State.fileName : "tejp.tjp";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".tjp")) name += ".tjp";
        createLauncher.launch(name);
    }

    private void load() {
        openLauncher.launch(new String[]{"*/*"});
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
                Toast.makeText(this, "Loaded " + r.name, Toast.LENGTH_SHORT).show();
                break;
            case SAVED:
                State.fileUri = r.uri.toString();
                State.fileName = r.name;
                Toast.makeText(this, "Saved " + r.name, Toast.LENGTH_SHORT).show();
                break;
            case PICTURE_LOADED:
                controller.loadPicture(r.pixels, r.width, r.height);
                showPicChip.setChecked(true);
                Toast.makeText(this, "Loaded picture " + r.name
                        + ". Use Picture mode to move it, or drag its corners.", Toast.LENGTH_LONG).show();
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
    }

    private void buildModeChips() {
        ChipGroup group = findViewById(R.id.modeGroup);
        State.Mode[] modes = {State.Mode.DRAWING, State.Mode.EDITING, State.Mode.MOVING, State.Mode.PICTURE};
        String[] labels = {"Draw", "Edit", "Move", "Picture"};
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < modes.length; i++) {
            Chip chip = inflateChip(R.layout.chip_toggle, group, labels[i]);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            ids.add(chip.getId());
            group.addView(chip);
            if (modes[i] == State.getMode()) group.check(chip.getId());
        }
        group.setOnCheckedStateChangeListener((g, checked) -> {
            if (checked.isEmpty()) return;
            controller.setMode(modes[ids.indexOf(checked.get(0))]);
        });
    }

    private void buildToolRow() {
        ChipGroup row = findViewById(R.id.toolRow);

        toggle(row, "Shift", State.shiftPressed, on -> State.shiftPressed = on);
        toggle(row, "Ctrl", State.ctrlPressed, on -> State.ctrlPressed = on);
        action(row, "Undo", controller::undo);

        widthChip = action(row, "", this::showWidthMenu);
        colorChip = action(row, "", this::showColorMenu);
        updateWidthChip();
        updateColorChip();
    }

    private void buildActionRow() {
        ChipGroup row = findViewById(R.id.actionRow);
        action(row, "Save", this::save);
        action(row, "Save as", this::saveAs);
        action(row, "Load", this::load);
        action(row, "Export JPG", this::exportJpg);
        action(row, "Load picture", () -> pictureLauncher.launch(new String[]{"image/*"}));
        showPicChip = toggle(row, "Show picture", State.showingPic, controller::setShowingPic);
        action(row, "Remove picture", controller::removePicture);
        toggle(row, "Auto strips", autoPanelOpen, this::setAutoPanelOpen);
        action(row, "Select all", controller::selectAll);
        action(row, "Select none", controller::deselectAll);
        action(row, "Select last", controller::selectLast);
        action(row, "Same width", controller::selectSameWidth);
        action(row, "Same colour", controller::selectSameColor);
        action(row, "Delete", controller::deleteSelected);
        action(row, "Copy", controller::copySelected);
        action(row, "Mirror", controller::mirrorSelected);
        action(row, "To back", controller::moveToBack);
        action(row, "To front", controller::moveToFront);
        toggle(row, "Invert", State.inverted, controller::setInverted);
        action(row, "Layers", () -> startActivity(new Intent(this, LayersActivity.class)));
        action(row, "Fit", fenceView::fitToView);
        action(row, "New normal", () -> confirmNewFence("normal", controller::newNormalFence));
        action(row, "New vertical", () -> confirmNewFence("vertical", controller::newVerticalFence));
    }

    private void confirmNewFence(String kind, Runnable create) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("New " + kind + " fence?")
                .setMessage("This clears the current drawing and the undo history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> {
                    create.run();
                    fenceView.setFence(State.fence);
                })
                .show();
    }

    private void showWidthMenu() {
        PopupMenu menu = new PopupMenu(this, widthChip);
        for (int i = 0; i < State.WIDTH_LABELS.length; i++) {
            menu.getMenu().add(0, i, i, State.WIDTH_LABELS[i]);
        }
        menu.setOnMenuItemClickListener(item -> {
            controller.setWidth(State.parseWidth(State.WIDTH_LABELS[item.getItemId()]));
            updateWidthChip();
            return true;
        });
        menu.show();
    }

    private void showColorMenu() {
        PopupMenu menu = new PopupMenu(this, colorChip);
        List<Integer> values = new ArrayList<>();
        for (Map.Entry<String, Integer> e : State.colors.entrySet()) {
            menu.getMenu().add(0, values.size(), values.size(), e.getKey());
            values.add(e.getValue());
        }
        menu.setOnMenuItemClickListener(item -> {
            controller.setColor(values.get(item.getItemId()));
            updateColorChip();
            return true;
        });
        menu.show();
    }

    private void updateWidthChip() {
        String label = String.format(Locale.US, "%s cm", State.currentStripWidth);
        for (String l : State.WIDTH_LABELS) {
            if (State.parseWidth(l) == State.currentStripWidth) label = l;
        }
        widthChip.setText("Width " + label);
    }

    private void updateColorChip() {
        String name = "";
        for (Map.Entry<String, Integer> e : State.colors.entrySet()) {
            if (e.getValue() == State.currentColor) name = e.getKey();
        }
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(State.currentColor);
        swatch.setStroke(2, 0xFF000000);
        colorChip.setChipIcon(swatch);
        colorChip.setChipIconVisible(true);
        colorChip.setText(name);
    }

    // ------------------------------------------------------------------ chip helpers

    private interface Toggle {
        void set(boolean on);
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
