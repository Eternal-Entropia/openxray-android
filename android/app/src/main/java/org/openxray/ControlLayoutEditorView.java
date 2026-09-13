package org.openxray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Map;

public class ControlLayoutEditorView extends View {

    private TouchLayoutConfig.LayoutData mLayout;

    private final Paint mGridPaint;
    private final Paint mJoyBasePaint;
    private final Paint mJoyThumbPaint;

    private final Paint mButtonPaint;
    private final Paint mButtonStrokePaint;
    private final Paint mHighlightStrokePaint;
    private final Paint mTextPaint;
    private final Paint mInfoBadgePaint;
    private final Paint mInfoTextPaint;

    private String mDraggedId = null;
    private boolean mDraggingJoy = false;
    private float mTouchOffsetX = 0;
    private float mTouchOffsetY = 0;

    private final RectF mTempRect = new RectF();
    private String mSelectedButtonName = null;

    public ControlLayoutEditorView(Context context) {
        this(context, null);
    }

    public ControlLayoutEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setColor(Color.argb(45, 255, 255, 255));
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1.5f);
        mGridPaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        mJoyBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mJoyBasePaint.setColor(Color.argb(120, 255, 255, 255));
        mJoyBasePaint.setStyle(Paint.Style.STROKE);
        mJoyBasePaint.setStrokeWidth(4);

        mJoyThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mJoyThumbPaint.setColor(Color.argb(140, 255, 255, 255));
        mJoyThumbPaint.setStyle(Paint.Style.FILL);

        mButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonPaint.setColor(Color.argb(180, 35, 35, 40));
        mButtonPaint.setStyle(Paint.Style.FILL);

        mButtonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonStrokePaint.setColor(Color.argb(200, 200, 200, 200));
        mButtonStrokePaint.setStyle(Paint.Style.STROKE);
        mButtonStrokePaint.setStrokeWidth(3);

        mHighlightStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHighlightStrokePaint.setColor(Color.argb(255, 255, 185, 20)); // Vivid Gold/Amber
        mHighlightStrokePaint.setStyle(Paint.Style.STROKE);
        mHighlightStrokePaint.setStrokeWidth(6);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mInfoBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mInfoBadgePaint.setColor(Color.argb(220, 20, 20, 22));
        mInfoBadgePaint.setStyle(Paint.Style.FILL);

        mInfoTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mInfoTextPaint.setColor(Color.argb(255, 255, 185, 20));
        mInfoTextPaint.setTextAlign(Paint.Align.CENTER);
        mInfoTextPaint.setTextSize(26);
        mInfoTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mLayout = TouchLayoutConfig.loadLayout(context);
    }

    public void setLayoutData(TouchLayoutConfig.LayoutData layout) {
        this.mLayout = layout.copy();
        invalidate();
    }

    public TouchLayoutConfig.LayoutData getLayoutData() {
        return mLayout;
    }

    public void resetToDefaults() {
        this.mLayout = TouchLayoutConfig.getDefaultLayout();
        this.mDraggedId = null;
        this.mDraggingJoy = false;
        this.mSelectedButtonName = null;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDraggedId = null;
                mDraggingJoy = false;
                mSelectedButtonName = null;

                // 1. Check if hit buttons (reverse order so top-drawn buttons get priority)
                for (Map.Entry<String, TouchLayoutConfig.ButtonPos> entry : mLayout.buttons.entrySet()) {
                    TouchLayoutConfig.ButtonPos bp = entry.getValue();
                    float bx = bp.relX * w;
                    float by = bp.relY * h;
                    float br = bp.relRadius * h;

                    float dist = (float) Math.hypot(x - bx, y - by);
                    if (dist <= br * 1.25f) {
                        mDraggedId = entry.getKey();
                        mTouchOffsetX = x - bx;
                        mTouchOffsetY = y - by;
                        mSelectedButtonName = getButtonLabel(mDraggedId);
                        try {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        } catch (Exception ignored) {}
                        invalidate();
                        return true;
                    }
                }

                // 2. Check if hit joystick
                float jx = mLayout.joyRelX * w;
                float jy = mLayout.joyRelY * h;
                float jr = mLayout.joyRelRadius * h;
                float jDist = (float) Math.hypot(x - jx, y - jy);
                if (jDist <= jr * 1.25f) {
                    mDraggingJoy = true;
                    mTouchOffsetX = x - jx;
                    mTouchOffsetY = y - jy;
                    mSelectedButtonName = "JOYSTICK";
                    try {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    } catch (Exception ignored) {}
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mDraggedId != null) {
                    TouchLayoutConfig.ButtonPos bp = mLayout.buttons.get(mDraggedId);
                    if (bp != null) {
                        float targetX = x - mTouchOffsetX;
                        float targetY = y - mTouchOffsetY;
                        float br = bp.relRadius * h;

                        // Clamp inside screen bounds
                        targetX = Math.max(br, Math.min(w - br, targetX));
                        targetY = Math.max(br, Math.min(h - br, targetY));

                        bp.relX = targetX / w;
                        bp.relY = targetY / h;
                        invalidate();
                    }
                } else if (mDraggingJoy) {
                    float targetX = x - mTouchOffsetX;
                    float targetY = y - mTouchOffsetY;
                    float jr = mLayout.joyRelRadius * h;

                    targetX = Math.max(jr, Math.min(w - jr, targetX));
                    targetY = Math.max(jr, Math.min(h - jr, targetY));

                    mLayout.joyRelX = targetX / w;
                    mLayout.joyRelY = targetY / h;
                    invalidate();
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mDraggedId = null;
                mDraggingJoy = false;
                invalidate();
                break;
            }
        }

        return true;
    }

    private String getButtonLabel(String id) {
        if ("lmb_main".equals(id)) return "LMB (Fire)";
        if ("rmb_main".equals(id)) return "RMB (Aim)";
        if ("lmb_left".equals(id)) return "LMB (Claw)";
        if ("r".equals(id)) return "R (Reload)";
        if ("f".equals(id)) return "F (Use)";
        if ("ammo".equals(id)) return "AMMO (Next ammo type)";
        if ("gl".equals(id)) return "GL (Grenade launcher)";
        if ("jump".equals(id)) return "JUMP";
        if ("crouch".equals(id)) return "CROUCH";
        if ("shift".equals(id)) return "SHIFT";
        if ("sprint".equals(id)) return "SPRINT";
        if ("q".equals(id)) return "Q (Lean Left)";
        if ("e".equals(id)) return "E (Lean Right)";
        if ("medkit".equals(id)) return "AID KIT (Medkit)";
        if ("bandage".equals(id)) return "BAND (Bandage)";
        if ("wpn_1".equals(id)) return "Slot 1 (Knife)";
        if ("wpn_2".equals(id)) return "Slot 2 (Pistol)";
        if ("wpn_3".equals(id)) return "Slot 3 (Rifle)";
        if ("wpn_4".equals(id)) return "Slot 4 (Grenade)";
        if ("wpn_5".equals(id)) return "Slot 5 (Binoc)";
        if ("wpn_6".equals(id)) return "Slot 6 (Bolt)";
        if ("esc".equals(id)) return "ESC";
        if ("inv".equals(id)) return "INV";
        if ("pda".equals(id)) return "PDA";
        if ("torch".equals(id)) return "TORCH";
        if ("qsave".equals(id)) return "QSAVE";
        if ("qload".equals(id)) return "QLOAD";
        return id.toUpperCase(Locale.US);
    }

    private String getButtonShortLabel(String id) {
        if ("lmb_main".equals(id) || "lmb_left".equals(id)) return "LMB";
        if ("rmb_main".equals(id)) return "RMB";
        if ("r".equals(id)) return "R";
        if ("f".equals(id)) return "F";
        if ("ammo".equals(id)) return "AMMO";
        if ("gl".equals(id)) return "GL";
        if ("jump".equals(id)) return "JUMP";
        if ("crouch".equals(id)) return "CROUCH";
        if ("shift".equals(id)) return "SHIFT";
        if ("sprint".equals(id)) return "SPRINT";
        if ("q".equals(id)) return "Q";
        if ("e".equals(id)) return "E";
        if ("medkit".equals(id)) return "AID KIT";
        if ("bandage".equals(id)) return "BAND";
        if ("wpn_1".equals(id)) return "1";
        if ("wpn_2".equals(id)) return "2";
        if ("wpn_3".equals(id)) return "3";
        if ("wpn_4".equals(id)) return "4";
        if ("wpn_5".equals(id)) return "5";
        if ("wpn_6".equals(id)) return "6";
        if ("esc".equals(id)) return "ESC";
        if ("inv".equals(id)) return "INV";
        if ("pda".equals(id)) return "PDA";
        if ("torch".equals(id)) return "TORCH";
        if ("qsave".equals(id)) return "QSAVE";
        if ("qload".equals(id)) return "QLOAD";
        return id.toUpperCase(Locale.US);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || mLayout == null) return;

        // 1. Draw helper grid lines (center vertical and horizontal)
        canvas.drawLine(w * 0.5f, 0, w * 0.5f, h, mGridPaint);
        canvas.drawLine(0, h * 0.5f, w, h * 0.5f, mGridPaint);
        canvas.drawLine(w * 0.25f, 0, w * 0.25f, h, mGridPaint);
        canvas.drawLine(w * 0.75f, 0, w * 0.75f, h, mGridPaint);

        // 2. Draw Movement Joystick
        float jx = mLayout.joyRelX * w;
        float jy = mLayout.joyRelY * h;
        float jr = mLayout.joyRelRadius * h;
        canvas.drawCircle(jx, jy, jr, mDraggingJoy ? mHighlightStrokePaint : mJoyBasePaint);
        canvas.drawCircle(jx, jy, jr * 0.45f, mJoyThumbPaint);

        mTextPaint.setTextSize(jr * 0.28f);
        float joyTextY = jy - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
        canvas.drawText("MOVE", jx, joyTextY, mTextPaint);

        // 3. Draw All Buttons
        for (Map.Entry<String, TouchLayoutConfig.ButtonPos> entry : mLayout.buttons.entrySet()) {
            String id = entry.getKey();
            TouchLayoutConfig.ButtonPos bp = entry.getValue();

            float bx = bp.relX * w;
            float by = bp.relY * h;
            float br = bp.relRadius * h;
            mTempRect.set(bx - br, by - br, bx + br, by + br);

            boolean isSelected = id.equals(mDraggedId);
            float cornerRadius = br * 0.56f;

            // Set specific colors for button categories
            if ("lmb_main".equals(id) || "lmb_left".equals(id)) {
                mButtonPaint.setColor(Color.argb(180, 65, 38, 12));
                mButtonStrokePaint.setColor(Color.argb(240, 255, 160, 20));
            } else if ("rmb_main".equals(id)) {
                mButtonPaint.setColor(Color.argb(180, 12, 45, 65));
                mButtonStrokePaint.setColor(Color.argb(240, 0, 195, 225));
            } else if ("medkit".equals(id)) {
                mButtonPaint.setColor(Color.argb(190, 75, 15, 15));
                mButtonStrokePaint.setColor(Color.argb(240, 255, 65, 65));
            } else if ("bandage".equals(id)) {
                mButtonPaint.setColor(Color.argb(190, 15, 60, 55));
                mButtonStrokePaint.setColor(Color.argb(240, 45, 215, 180));
            } else if ("q".equals(id) || "e".equals(id)) {
                mButtonPaint.setColor(Color.argb(190, 48, 25, 65));
                mButtonStrokePaint.setColor(Color.argb(240, 180, 105, 245));
            } else if (id.startsWith("wpn_") || "ammo".equals(id) || "gl".equals(id)) {
                mButtonPaint.setColor(Color.argb(190, 24, 24, 28));
                mButtonStrokePaint.setColor(Color.argb(220, 240, 170, 40));
            } else {
                mButtonPaint.setColor(Color.argb(170, 30, 30, 30));
                mButtonStrokePaint.setColor(Color.argb(200, 200, 200, 200));
            }

            canvas.drawRoundRect(mTempRect, cornerRadius, cornerRadius, mButtonPaint);
            canvas.drawRoundRect(mTempRect, cornerRadius, cornerRadius, isSelected ? mHighlightStrokePaint : mButtonStrokePaint);

            String shortLabel = getButtonShortLabel(id);
            float fontSize = br * (shortLabel.length() > 4 ? 0.45f : (shortLabel.length() > 2 ? 0.60f : 0.80f));
            mTextPaint.setTextSize(fontSize);
            float textY = by - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
            canvas.drawText(shortLabel, bx, textY, mTextPaint);
        }

        // 4. Draw Selected Badge Info at top center
        if (mSelectedButtonName != null) {
            String info = "Moving: " + mSelectedButtonName;
            float textWidth = mInfoTextPaint.measureText(info);
            float badgeW = textWidth + 40;
            float badgeH = 46;
            float badgeX = (w - badgeW) / 2.0f;
            float badgeY = 16;
            RectF badgeRect = new RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH);
            canvas.drawRoundRect(badgeRect, 14, 14, mInfoBadgePaint);
            canvas.drawRoundRect(badgeRect, 14, 14, mHighlightStrokePaint);
            canvas.drawText(info, w / 2.0f, badgeY + 32, mInfoTextPaint);
        }
    }
}
