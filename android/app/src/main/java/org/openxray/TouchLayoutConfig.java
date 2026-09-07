package org.openxray;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class TouchLayoutConfig {

    public static final String PREFS_NAME = "openxray_touch_layout";
    private static final String KEY_LAYOUT_JSON = "layout_json";

    public static class ButtonPos {
        public String id;
        public float relX; // 0.0 to 1.0 (relative to screen width)
        public float relY; // 0.0 to 1.0 (relative to screen height)
        public float relRadius; // relative to screen height
        public float customRadius = 0; // if > 0, overrides default radius

        public ButtonPos(String id, float relX, float relY, float relRadius) {
            this.id = id;
            this.relX = relX;
            this.relY = relY;
            this.relRadius = relRadius;
        }

        public ButtonPos copy() {
            ButtonPos cp = new ButtonPos(id, relX, relY, relRadius);
            cp.customRadius = this.customRadius;
            return cp;
        }
    }

    public static class LayoutData {
        public float joyRelX;
        public float joyRelY;
        public float joyRelRadius;
        public final Map<String, ButtonPos> buttons = new HashMap<>();

        public LayoutData copy() {
            LayoutData cp = new LayoutData();
            cp.joyRelX = this.joyRelX;
            cp.joyRelY = this.joyRelY;
            cp.joyRelRadius = this.joyRelRadius;
            for (Map.Entry<String, ButtonPos> entry : this.buttons.entrySet()) {
                cp.buttons.put(entry.getKey(), entry.getValue().copy());
            }
            return cp;
        }
    }

    /**
     * Default positions calculated proportionally for landscape screens.
     */
    public static LayoutData getDefaultLayout() {
        LayoutData ld = new LayoutData();

        // 1. Movement Joystick
        ld.joyRelX = 0.16f;
        ld.joyRelY = 0.72f;
        ld.joyRelRadius = 0.16f;

        float baseRadius = 0.082f;
        float smallRadius = 0.058f;

        // 2. Primary Fire & Aim
        ld.buttons.put("lmb_main", new ButtonPos("lmb_main", 0.90f, 0.85f, baseRadius * 1.35f));
        ld.buttons.put("rmb_main", new ButtonPos("rmb_main", 0.78f, 0.85f, baseRadius * 1.15f));
        ld.buttons.put("lmb_left", new ButtonPos("lmb_left", 0.16f, 0.44f, smallRadius * 1.30f));

        // 3. Q and E (Camera Turn / Lean)
        ld.buttons.put("q", new ButtonPos("q", 0.07f, 0.44f, smallRadius * 1.15f));
        ld.buttons.put("e", new ButtonPos("e", 0.25f, 0.44f, smallRadius * 1.15f));

        // 4. Action buttons (Right side)
        ld.buttons.put("r", new ButtonPos("r", 0.91f, 0.63f, baseRadius * 0.90f));
        ld.buttons.put("f", new ButtonPos("f", 0.81f, 0.68f, baseRadius * 0.90f));
        ld.buttons.put("jump", new ButtonPos("jump", 0.92f, 0.44f, baseRadius * 0.95f));
        ld.buttons.put("crouch", new ButtonPos("crouch", 0.82f, 0.50f, baseRadius * 0.95f));
        ld.buttons.put("shift", new ButtonPos("shift", 0.72f, 0.50f, baseRadius * 0.95f));
        ld.buttons.put("sprint", new ButtonPos("sprint", 0.32f, 0.82f, smallRadius * 1.15f));

        // 5. Quick Medical Items (Medkit & Bandage)
        ld.buttons.put("medkit", new ButtonPos("medkit", 0.29f, 0.67f, smallRadius * 1.10f));
        ld.buttons.put("bandage", new ButtonPos("bandage", 0.37f, 0.67f, smallRadius * 1.10f));

        // 6. Weapon Selection 1-6 (Bottom Center)
        ld.buttons.put("wpn_1", new ButtonPos("wpn_1", 0.28f, 0.92f, smallRadius * 0.92f));
        ld.buttons.put("wpn_2", new ButtonPos("wpn_2", 0.36f, 0.92f, smallRadius * 0.92f));
        ld.buttons.put("wpn_3", new ButtonPos("wpn_3", 0.44f, 0.92f, smallRadius * 0.92f));
        ld.buttons.put("wpn_4", new ButtonPos("wpn_4", 0.52f, 0.92f, smallRadius * 0.92f));
        ld.buttons.put("wpn_5", new ButtonPos("wpn_5", 0.60f, 0.92f, smallRadius * 0.92f));
        ld.buttons.put("wpn_6", new ButtonPos("wpn_6", 0.68f, 0.92f, smallRadius * 0.92f));

        // 7. Top Bar System / UI Buttons
        float topY = smallRadius * 1.6f;
        ld.buttons.put("esc", new ButtonPos("esc", 0.05f, topY, smallRadius));
        ld.buttons.put("inv", new ButtonPos("inv", 0.35f, topY, smallRadius));
        ld.buttons.put("pda", new ButtonPos("pda", 0.48f, topY, smallRadius));
        ld.buttons.put("torch", new ButtonPos("torch", 0.61f, topY, smallRadius));
        ld.buttons.put("qsave", new ButtonPos("qsave", 0.85f, topY, smallRadius));
        ld.buttons.put("qload", new ButtonPos("qload", 0.95f, topY, smallRadius));

        return ld;
    }

    public static LayoutData loadLayout(Context context) {
        LayoutData ld = getDefaultLayout();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_LAYOUT_JSON, null);

        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return ld;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.has("joy_x") && root.has("joy_y")) {
                ld.joyRelX = (float) root.getDouble("joy_x");
                ld.joyRelY = (float) root.getDouble("joy_y");
            }
            if (root.has("buttons")) {
                JSONObject btns = root.getJSONObject("buttons");
                for (String id : ld.buttons.keySet()) {
                    if (btns.has(id)) {
                        JSONObject bObj = btns.getJSONObject(id);
                        ButtonPos bp = ld.buttons.get(id);
                        if (bp != null) {
                            bp.relX = (float) bObj.getDouble("x");
                            bp.relY = (float) bObj.getDouble("y");
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e("TouchLayoutConfig", "Failed to parse saved touch layout json: " + e.getMessage(), e);
        }

        return ld;
    }

    public static void saveLayout(Context context, LayoutData layout) {
        try {
            JSONObject root = new JSONObject();
            root.put("joy_x", (double) layout.joyRelX);
            root.put("joy_y", (double) layout.joyRelY);

            JSONObject btns = new JSONObject();
            for (Map.Entry<String, ButtonPos> entry : layout.buttons.entrySet()) {
                JSONObject bObj = new JSONObject();
                bObj.put("x", (double) entry.getValue().relX);
                bObj.put("y", (double) entry.getValue().relY);
                btns.put(entry.getKey(), bObj);
            }
            root.put("buttons", btns);

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAYOUT_JSON, root.toString()).apply();
            AppLog.i("TouchLayoutConfig", "Saved custom touch layout successfully");
        } catch (Exception e) {
            AppLog.e("TouchLayoutConfig", "Failed to save touch layout: " + e.getMessage(), e);
        }
    }

    public static void resetLayout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LAYOUT_JSON).apply();
        AppLog.i("TouchLayoutConfig", "Reset touch layout to defaults");
    }
}
