package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global editor state, as in the desktop app.
 * Being static, it also survives screen rotation while the app process lives.
 */
public final class State {

    private State() {}

    public enum Mode {
        DRAWING,
        EDITING,
        MOVING
    }

    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int BLUE = 0xFF0000FF;
    public static final int GREEN = 0xFF00FF00;

    /** The colour menu. Same entries as the desktop combo box. */
    public static final Map<String, Integer> colors = new LinkedHashMap<>();

    static {
        colors.put("WHITE", WHITE);
        colors.put("RED", RED);
        colors.put("BLUE", BLUE);
        colors.put("GREEN", GREEN);
    }

    /** The width menu. Same entries as the desktop combo box. */
    public static final String[] WIDTH_LABELS = {
            "0.0 cm", "0.5 cm", "1.0 cm", "1.1875 cm", "1.25 cm", "1.5 cm", "1.51 cm", "2.0 cm",
            "2.5 cm", "3.0 cm", "3.75 cm", "4 cm", "4.5 cm", "4.51 cm", "5 cm", "6 cm"};

    public static float parseWidth(String label) {
        return Float.parseFloat(label.substring(0, label.length() - 3));
    }

    public static Fence fence;

    /** The document the drawing was loaded from or last saved to, as a content Uri string. */
    public static String fileUri;
    /** Its display name, for example "36.tjp". */
    public static String fileName;

    public static float currentStripWidth = 1f;
    public static int currentColor = WHITE;

    public static Strip activeStrip;
    public static HashSet<Strip> selectedStrips = new HashSet<>();
    public static Edge selectedEdge;
    public static Modification activeModification;

    public static boolean inverted = false;
    public static boolean shiftPressed, ctrlPressed;

    private static Mode mode = Mode.DRAWING;

    public static void setMode(Mode m) {
        mode = m;
    }

    public static Mode getMode() {
        return mode;
    }
}
