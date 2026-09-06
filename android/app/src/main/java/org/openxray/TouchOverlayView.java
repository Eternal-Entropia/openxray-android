package org.openxray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.List;

public class TouchOverlayView extends View {

    public static class TouchButton {
        public String label;
        public int keyCode;
        public boolean isMouseLeft;
        public boolean isMouseRight;
        public RectF bounds;
        public boolean isPressed;
        public int pointerId = -1;

        public TouchButton(String label, int keyCode, float x, float y, float radius) {
            this.label = label;
            this.keyCode = keyCode;
            this.bounds = new RectF(x - radius, y - radius, x + radius, y + radius);
        }

        public TouchButton(String label, boolean isLeft, float x, float y, float radius) {
            this.label = label;
            if (isLeft) {
                this.isMouseLeft = true;
            } else {
                this.isMouseRight = true;
            }
            this.bounds = new RectF(x - radius, y - radius, x + radius, y + radius);
        }

        public boolean contains(float x, float y) {
            return bounds.contains(x, y);
        }

        public boolean containsWithMargin(float x, float y, float margin) {
            return (x >= bounds.left - margin && x <= bounds.right + margin &&
                    y >= bounds.top - margin && y <= bounds.bottom + margin);
        }
    }

    private final Paint mBasePaint;
    private final Paint mThumbPaint;
    private final Paint mThumbDotPaint;

    private final Paint mButtonPaint;
    private final Paint mButtonStrokePaint;
    private final Paint mButtonPressedPaint;

    private final Paint mLkmPaint;
    private final Paint mLkmStrokePaint;
    private final Paint mLkmPressedPaint;

    private final Paint mPkmPaint;
    private final Paint mPkmStrokePaint;
    private final Paint mPkmPressedPaint;

    private final Paint mTextPaint;

    // Joystick state
    private float mJoyBaseX = 0;
    private float mJoyBaseY = 0;
    private float mJoyThumbX = 0;
    private float mJoyThumbY = 0;
    private float mJoyRadius = 140;
    private int mJoyPointerId = -1;
    private boolean mJoyActive = false;

    // Joystick keys currently pressed
    private boolean mKeyW = false;
    private boolean mKeyA = false;
    private boolean mKeyS = false;
    private boolean mKeyD = false;

    // Camera look / Trackpad state
    private int mLookPointerId = -1;
    private float mLastLookX = 0;
    private float mLastLookY = 0;
    private float mLookSensitivity = 1.6f;

    // Mouse button bitmask currently active
    private int mCurrentMouseButtonState = 0;

    private final List<TouchButton> mButtons = new ArrayList<>();
    private boolean mInitialized = false;

    private float mButtonScale = 1.0f;
    private float mOverlayAlpha = 0.6f;

    public TouchOverlayView(Context context) {
        super(context);

        mBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBasePaint.setStyle(Paint.Style.STROKE);
        mBasePaint.setStrokeWidth(5);

        mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mThumbPaint.setStyle(Paint.Style.FILL);

        mThumbDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mThumbDotPaint.setStyle(Paint.Style.FILL);

        mButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonPaint.setStyle(Paint.Style.FILL);

        mButtonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonStrokePaint.setStyle(Paint.Style.STROKE);
        mButtonStrokePaint.setStrokeWidth(3);

        mButtonPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonPressedPaint.setStyle(Paint.Style.FILL);

        mLkmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLkmPaint.setStyle(Paint.Style.FILL);

        mLkmStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLkmStrokePaint.setStyle(Paint.Style.STROKE);
        mLkmStrokePaint.setStrokeWidth(5);

        mLkmPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLkmPressedPaint.setStyle(Paint.Style.FILL);

        mPkmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPkmPaint.setStyle(Paint.Style.FILL);

        mPkmStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPkmStrokePaint.setStyle(Paint.Style.STROKE);
        mPkmStrokePaint.setStrokeWidth(5);

        mPkmPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPkmPressedPaint.setStyle(Paint.Style.FILL);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        updatePaints();
    }

    private void updatePaints() {
        int a = (int) (mOverlayAlpha * 255);

        mBasePaint.setColor(Color.argb((int) (a * 0.45f), 255, 255, 255));
        mThumbPaint.setColor(Color.argb((int) (a * 0.55f), 255, 255, 255));
        mThumbDotPaint.setColor(Color.argb((int) (a * 0.90f), 255, 255, 255));

        // Default buttons
        mButtonPaint.setColor(Color.argb((int) (a * 0.50f), 30, 30, 30));
        mButtonStrokePaint.setColor(Color.argb((int) (a * 0.65f), 200, 200, 200));
        mButtonPressedPaint.setColor(Color.argb((int) (a * 0.85f), 220, 130, 25));

        // LMB (Fire / Primary Action - Amber Orange)
        mLkmPaint.setColor(Color.argb((int) (a * 0.60f), 65, 38, 12));
        mLkmStrokePaint.setColor(Color.argb((int) (a * 0.95f), 255, 160, 20));
        mLkmPressedPaint.setColor(Color.argb((int) (a * 0.95f), 255, 140, 0));

        // RMB (Aim / Secondary Action - Cyan Blue)
        mPkmPaint.setColor(Color.argb((int) (a * 0.60f), 12, 45, 65));
        mPkmStrokePaint.setColor(Color.argb((int) (a * 0.95f), 0, 195, 225));
        mPkmPressedPaint.setColor(Color.argb((int) (a * 0.95f), 0, 180, 220));

        mTextPaint.setColor(Color.argb((int) (a * 0.95f), 255, 255, 255));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initLayout(w, h);
    }

    public void setLookSensitivity(float sens) {
        this.mLookSensitivity = 1.6f * sens;
    }

    public void setControlsScale(float scale) {
        this.mButtonScale = Math.max(0.6f, Math.min(2.0f, scale));
        if (getWidth() > 0 && getHeight() > 0) {
            initLayout(getWidth(), getHeight());
            invalidate();
        }
    }

    public void setOverlayAlpha(float alpha) {
        this.mOverlayAlpha = Math.max(0.1f, Math.min(1.0f, alpha));
        updatePaints();
        invalidate();
    }

    private void initLayout(int w, int h) {
        mButtons.clear();

        mJoyBaseX = w * 0.16f;
        mJoyBaseY = h * 0.72f;
        mJoyThumbX = mJoyBaseX;
        mJoyThumbY = mJoyBaseY;
        mJoyRadius = h * 0.16f * mButtonScale;

        float btnRadius = h * 0.082f * mButtonScale;
        float smallBtnRadius = h * 0.058f * mButtonScale;

        // 1. Right Side Primary Action Buttons (LMB and RMB)
        // Main LMB (Primary fire / click - bottom-right)
        mButtons.add(new TouchButton("LMB", true, w - btnRadius * 2.2f, h - btnRadius * 2.2f, btnRadius * 1.35f));
        // Main RMB (Aim / secondary click - next to LMB)
        mButtons.add(new TouchButton("RMB", false, w - btnRadius * 4.6f, h - btnRadius * 1.9f, btnRadius * 1.15f));

        // 2. Additional Left-Hand LMB (Above movement joystick for dual-thumb / claw firing & clicking)
        mButtons.add(new TouchButton("LMB", true, mJoyBaseX, mJoyBaseY - mJoyRadius - smallBtnRadius * 1.7f, smallBtnRadius * 1.30f));

        // 3. Gameplay Buttons (Right Side)
        mButtons.add(new TouchButton("R", KeyEvent.KEYCODE_R, w - btnRadius * 2.0f, h - btnRadius * 4.6f, btnRadius * 0.90f));
        mButtons.add(new TouchButton("F", KeyEvent.KEYCODE_F, w - btnRadius * 4.0f, h - btnRadius * 3.8f, btnRadius * 0.90f));
        mButtons.add(new TouchButton("JUMP", KeyEvent.KEYCODE_SPACE, w - btnRadius * 1.8f, h - btnRadius * 6.8f, btnRadius * 0.95f));
        mButtons.add(new TouchButton("CROUCH", KeyEvent.KEYCODE_CTRL_LEFT, w - btnRadius * 3.8f, h - btnRadius * 6.0f, btnRadius * 0.95f));
        mButtons.add(new TouchButton("SHIFT", KeyEvent.KEYCODE_SHIFT_LEFT, w - btnRadius * 5.8f, h - btnRadius * 6.0f, btnRadius * 0.95f));

        // Sprint (X - near joystick)
        mButtons.add(new TouchButton("SPRINT", KeyEvent.KEYCODE_X, w * 0.32f, h * 0.82f, smallBtnRadius * 1.15f));

        // 4. Top Bar System / UI Buttons
        float topY = smallBtnRadius * 1.6f;
        mButtons.add(new TouchButton("ESC", KeyEvent.KEYCODE_ESCAPE, smallBtnRadius * 1.8f, topY, smallBtnRadius));
        mButtons.add(new TouchButton("INV", KeyEvent.KEYCODE_I, w * 0.35f, topY, smallBtnRadius));
        mButtons.add(new TouchButton("PDA", KeyEvent.KEYCODE_P, w * 0.48f, topY, smallBtnRadius));
        mButtons.add(new TouchButton("TORCH", KeyEvent.KEYCODE_L, w * 0.61f, topY, smallBtnRadius));
        mButtons.add(new TouchButton("QSAVE", KeyEvent.KEYCODE_F6, w - smallBtnRadius * 4.4f, topY, smallBtnRadius));
        mButtons.add(new TouchButton("QLOAD", KeyEvent.KEYCODE_F7, w - smallBtnRadius * 1.8f, topY, smallBtnRadius));

        mInitialized = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pointerId = event.getPointerId(actionIndex);
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);

                // 1. Check Buttons first
                boolean hitButton = false;
                for (TouchButton btn : mButtons) {
                    if (btn.contains(x, y) && btn.pointerId == -1) {
                        btn.isPressed = true;
                        btn.pointerId = pointerId;
                        try {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        } catch (Exception ignored) {}
                        sendButtonEvent(btn, true);
                        hitButton = true;
                        break;
                    }
                }
                if (hitButton) {
                    invalidate();
                    break;
                }

                // 2. Check Joystick area (Left 40% of screen, bottom 62%)
                if (x < getWidth() * 0.40f && y > getHeight() * 0.38f && mJoyPointerId == -1) {
                    mJoyPointerId = pointerId;
                    mJoyActive = true;
                    updateJoystick(x, y);
                    invalidate();
                    break;
                }

                // 3. Any touch not on a button or joystick is trackpad / camera look
                if (mLookPointerId == -1) {
                    mLookPointerId = pointerId;
                    mLastLookX = x;
                    mLastLookY = y;
                    break;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pId = event.getPointerId(i);
                    float x = event.getX(i);
                    float y = event.getY(i);

                    // Update joystick
                    if (pId == mJoyPointerId) {
                        updateJoystick(x, y);
                    }

                    // Update camera look / relative mouse motion
                    if (pId == mLookPointerId) {
                        float dx = (x - mLastLookX) * mLookSensitivity;
                        float dy = (y - mLastLookY) * mLookSensitivity;
                        mLastLookX = x;
                        mLastLookY = y;
                        if (Math.abs(dx) > 0.01f || Math.abs(dy) > 0.01f) {
                            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, dx, dy, true);
                        }
                    }

                    // Update button dragging (with margin slop so holding while moving doesn't accidentally cancel)
                    for (TouchButton btn : mButtons) {
                        if (btn.pointerId == pId) {
                            if (!btn.containsWithMargin(x, y, 25.0f)) {
                                btn.isPressed = false;
                                btn.pointerId = -1;
                                sendButtonEvent(btn, false);
                            }
                        }
                    }
                }
                invalidate();
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(actionIndex);

                // Release Joystick
                if (pointerId == mJoyPointerId) {
                    mJoyPointerId = -1;
                    mJoyActive = false;
                    mJoyThumbX = mJoyBaseX;
                    mJoyThumbY = mJoyBaseY;
                    resetJoystickKeys();
                    invalidate();
                }

                // Release Look
                if (pointerId == mLookPointerId) {
                    mLookPointerId = -1;
                }

                // Release Buttons
                for (TouchButton btn : mButtons) {
                    if (btn.pointerId == pointerId) {
                        btn.isPressed = false;
                        btn.pointerId = -1;
                        sendButtonEvent(btn, false);
                        invalidate();
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mJoyPointerId != -1) {
                    mJoyPointerId = -1;
                    mJoyActive = false;
                    mJoyThumbX = mJoyBaseX;
                    mJoyThumbY = mJoyBaseY;
                    resetJoystickKeys();
                }
                mLookPointerId = -1;
                for (TouchButton btn : mButtons) {
                    if (btn.isPressed) {
                        btn.isPressed = false;
                        btn.pointerId = -1;
                        sendButtonEvent(btn, false);
                    }
                }
                invalidate();
                break;
            }
        }

        return true;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - mJoyBaseX;
        float dy = y - mJoyBaseY;
        float dist = (float) Math.hypot(dx, dy);

        if (dist > mJoyRadius) {
            dx = (dx / dist) * mJoyRadius;
            dy = (dy / dist) * mJoyRadius;
        }

        mJoyThumbX = mJoyBaseX + dx;
        mJoyThumbY = mJoyBaseY + dy;

        // Deadzone threshold
        float deadzone = mJoyRadius * 0.25f;
        boolean w = dy < -deadzone;
        boolean s = dy > deadzone;
        boolean a = dx < -deadzone;
        boolean d = dx > deadzone;

        updateKey(KeyEvent.KEYCODE_W, w, mKeyW); mKeyW = w;
        updateKey(KeyEvent.KEYCODE_S, s, mKeyS); mKeyS = s;
        updateKey(KeyEvent.KEYCODE_A, a, mKeyA); mKeyA = a;
        updateKey(KeyEvent.KEYCODE_D, d, mKeyD); mKeyD = d;
    }

    private void resetJoystickKeys() {
        if (mKeyW) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_W); mKeyW = false; }
        if (mKeyS) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_S); mKeyS = false; }
        if (mKeyA) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_A); mKeyA = false; }
        if (mKeyD) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_D); mKeyD = false; }
    }

    private void updateKey(int keyCode, boolean target, boolean current) {
        if (target != current) {
            if (target) {
                SDLActivity.onNativeKeyDown(keyCode);
            } else {
                SDLActivity.onNativeKeyUp(keyCode);
            }
        }
    }

    private void sendButtonEvent(TouchButton btn, boolean pressed) {
        if (btn.isMouseLeft) {
            boolean anyLeftPressed = false;
            for (TouchButton b : mButtons) {
                if (b.isMouseLeft && b.isPressed) {
                    anyLeftPressed = true;
                    break;
                }
            }
            if (anyLeftPressed) {
                mCurrentMouseButtonState |= MotionEvent.BUTTON_PRIMARY;
                SDLActivity.onNativeMouse(mCurrentMouseButtonState, MotionEvent.ACTION_DOWN, 0, 0, true);
            } else {
                mCurrentMouseButtonState &= ~MotionEvent.BUTTON_PRIMARY;
                SDLActivity.onNativeMouse(mCurrentMouseButtonState, MotionEvent.ACTION_UP, 0, 0, true);
            }
        } else if (btn.isMouseRight) {
            boolean anyRightPressed = false;
            for (TouchButton b : mButtons) {
                if (b.isMouseRight && b.isPressed) {
                    anyRightPressed = true;
                    break;
                }
            }
            if (anyRightPressed) {
                mCurrentMouseButtonState |= MotionEvent.BUTTON_SECONDARY;
                SDLActivity.onNativeMouse(mCurrentMouseButtonState, MotionEvent.ACTION_DOWN, 0, 0, true);
            } else {
                mCurrentMouseButtonState &= ~MotionEvent.BUTTON_SECONDARY;
                SDLActivity.onNativeMouse(mCurrentMouseButtonState, MotionEvent.ACTION_UP, 0, 0, true);
            }
        } else {
            if (pressed) {
                SDLActivity.onNativeKeyDown(btn.keyCode);
                if ("QSAVE".equals(btn.label)) {
                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F5);
                } else if ("QLOAD".equals(btn.label)) {
                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F9);
                }
            } else {
                SDLActivity.onNativeKeyUp(btn.keyCode);
                if ("QSAVE".equals(btn.label)) {
                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F5);
                } else if ("QLOAD".equals(btn.label)) {
                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F9);
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mInitialized) return;

        // 1. Draw Movement Joystick
        canvas.drawCircle(mJoyBaseX, mJoyBaseY, mJoyRadius, mBasePaint);
        canvas.drawCircle(mJoyThumbX, mJoyThumbY, mJoyRadius * 0.45f, mThumbPaint);
        canvas.drawCircle(mJoyThumbX, mJoyThumbY, mJoyRadius * 0.15f, mThumbDotPaint);

        // 2. Draw Touch Buttons
        for (TouchButton btn : mButtons) {
            float cornerRadius = btn.bounds.width() * 0.28f;

            Paint fillPaint;
            Paint strokePaint;

            if (btn.isMouseLeft) {
                fillPaint = btn.isPressed ? mLkmPressedPaint : mLkmPaint;
                strokePaint = mLkmStrokePaint;
            } else if (btn.isMouseRight) {
                fillPaint = btn.isPressed ? mPkmPressedPaint : mPkmPaint;
                strokePaint = mPkmStrokePaint;
            } else {
                fillPaint = btn.isPressed ? mButtonPressedPaint : mButtonPaint;
                strokePaint = mButtonStrokePaint;
            }

            // Fill
            canvas.drawRoundRect(btn.bounds, cornerRadius, cornerRadius, fillPaint);
            // Stroke
            canvas.drawRoundRect(btn.bounds, cornerRadius, cornerRadius, strokePaint);

            // Text Label
            float fontSize = btn.bounds.height() * (btn.label.length() > 4 ? 0.26f : (btn.label.length() > 2 ? 0.32f : 0.40f));
            mTextPaint.setTextSize(fontSize);

            float textY = btn.bounds.centerY() - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
            canvas.drawText(btn.label, btn.bounds.centerX(), textY, mTextPaint);
        }
    }
}
