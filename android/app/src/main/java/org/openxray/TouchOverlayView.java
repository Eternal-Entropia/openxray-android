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

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_LMB = 1;
    public static final int TYPE_RMB = 2;
    public static final int TYPE_MEDKIT = 3;
    public static final int TYPE_BANDAGE = 4;
    public static final int TYPE_QE = 5;
    public static final int TYPE_WEAPON = 6;

    public static class TouchButton {
        public String id;
        public String label;
        public int keyCode;
        public boolean isMouseLeft;
        public boolean isMouseRight;
        public int buttonType = TYPE_DEFAULT;
        public RectF bounds;
        public float cx;
        public float cy;
        public float radius;
        public boolean isPressed;
        public int pointerId = -1;

        public TouchButton(String id, String label, int keyCode, float x, float y, float radius, int buttonType) {
            this.id = id;
            this.label = label;
            this.keyCode = keyCode;
            this.cx = x;
            this.cy = y;
            this.radius = radius;
            this.buttonType = buttonType;
            this.bounds = new RectF(x - radius, y - radius, x + radius, y + radius);
        }

        public TouchButton(String id, String label, boolean isLeft, float x, float y, float radius) {
            this.id = id;
            this.label = label;
            if (isLeft) {
                this.isMouseLeft = true;
                this.buttonType = TYPE_LMB;
            } else {
                this.isMouseRight = true;
                this.buttonType = TYPE_RMB;
            }
            this.cx = x;
            this.cy = y;
            this.radius = radius;
            this.bounds = new RectF(x - radius, y - radius, x + radius, y + radius);
        }

        public void setCenter(float x, float y) {
            this.cx = x;
            this.cy = y;
            this.bounds.set(x - radius, y - radius, x + radius, y + radius);
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

    private final Paint mMedkitPaint;
    private final Paint mMedkitStrokePaint;
    private final Paint mMedkitPressedPaint;

    private final Paint mBandagePaint;
    private final Paint mBandageStrokePaint;
    private final Paint mBandagePressedPaint;

    private final Paint mQePaint;
    private final Paint mQeStrokePaint;
    private final Paint mQePressedPaint;

    private final Paint mWeaponPaint;
    private final Paint mWeaponStrokePaint;
    private final Paint mWeaponPressedPaint;

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

        // Default buttons
        mButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonPaint.setStyle(Paint.Style.FILL);
        mButtonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonStrokePaint.setStyle(Paint.Style.STROKE);
        mButtonStrokePaint.setStrokeWidth(3);
        mButtonPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mButtonPressedPaint.setStyle(Paint.Style.FILL);

        // LMB
        mLkmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLkmPaint.setStyle(Paint.Style.FILL);
        mLkmStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLkmStrokePaint.setStyle(Paint.Style.STROKE);
        mLkmStrokePaint.setStrokeWidth(5);
        mLkmPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLkmPressedPaint.setStyle(Paint.Style.FILL);

        // RMB
        mPkmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPkmPaint.setStyle(Paint.Style.FILL);
        mPkmStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPkmStrokePaint.setStyle(Paint.Style.STROKE);
        mPkmStrokePaint.setStrokeWidth(5);
        mPkmPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPkmPressedPaint.setStyle(Paint.Style.FILL);

        // Medkit (Red/Coral)
        mMedkitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMedkitPaint.setStyle(Paint.Style.FILL);
        mMedkitStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMedkitStrokePaint.setStyle(Paint.Style.STROKE);
        mMedkitStrokePaint.setStrokeWidth(4);
        mMedkitPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMedkitPressedPaint.setStyle(Paint.Style.FILL);

        // Bandage (Teal/Cyan)
        mBandagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBandagePaint.setStyle(Paint.Style.FILL);
        mBandageStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBandageStrokePaint.setStyle(Paint.Style.STROKE);
        mBandageStrokePaint.setStrokeWidth(4);
        mBandagePressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBandagePressedPaint.setStyle(Paint.Style.FILL);

        // Q/E (Purple/Violet)
        mQePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mQePaint.setStyle(Paint.Style.FILL);
        mQeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mQeStrokePaint.setStyle(Paint.Style.STROKE);
        mQeStrokePaint.setStrokeWidth(4);
        mQePressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mQePressedPaint.setStyle(Paint.Style.FILL);

        // Weapon (Amber/Charcoal)
        mWeaponPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWeaponPaint.setStyle(Paint.Style.FILL);
        mWeaponStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWeaponStrokePaint.setStyle(Paint.Style.STROKE);
        mWeaponStrokePaint.setStrokeWidth(3);
        mWeaponPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWeaponPressedPaint.setStyle(Paint.Style.FILL);

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

        // Medkit (Crimson Red / Salmon)
        mMedkitPaint.setColor(Color.argb((int) (a * 0.65f), 75, 15, 15));
        mMedkitStrokePaint.setColor(Color.argb((int) (a * 0.95f), 255, 65, 65));
        mMedkitPressedPaint.setColor(Color.argb((int) (a * 0.95f), 230, 30, 30));

        // Bandage (Teal / Cyan)
        mBandagePaint.setColor(Color.argb((int) (a * 0.65f), 15, 60, 55));
        mBandageStrokePaint.setColor(Color.argb((int) (a * 0.95f), 45, 215, 180));
        mBandagePressedPaint.setColor(Color.argb((int) (a * 0.95f), 20, 190, 155));

        // Q/E (Purple / Indigo)
        mQePaint.setColor(Color.argb((int) (a * 0.60f), 48, 25, 65));
        mQeStrokePaint.setColor(Color.argb((int) (a * 0.95f), 180, 105, 245));
        mQePressedPaint.setColor(Color.argb((int) (a * 0.95f), 160, 80, 230));

        // Weapons (Deep Charcoal / Amber Border)
        mWeaponPaint.setColor(Color.argb((int) (a * 0.55f), 24, 24, 28));
        mWeaponStrokePaint.setColor(Color.argb((int) (a * 0.85f), 240, 170, 40));
        mWeaponPressedPaint.setColor(Color.argb((int) (a * 0.90f), 220, 140, 20));

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

    public void reloadLayout() {
        if (getWidth() > 0 && getHeight() > 0) {
            initLayout(getWidth(), getHeight());
            invalidate();
        }
    }

    private void initLayout(int w, int h) {
        mButtons.clear();

        TouchLayoutConfig.LayoutData layout = TouchLayoutConfig.loadLayout(getContext());

        mJoyBaseX = layout.joyRelX * w;
        mJoyBaseY = layout.joyRelY * h;
        mJoyThumbX = mJoyBaseX;
        mJoyThumbY = mJoyBaseY;
        mJoyRadius = h * layout.joyRelRadius * mButtonScale;

        // 1. Right Side Primary Action Buttons (LMB and RMB)
        addButtonFromConfig(layout, "lmb_main", "LMB", true);
        addButtonFromConfig(layout, "rmb_main", "RMB", false);

        // 2. Additional Left-Hand LMB (Above movement joystick for dual-thumb / claw firing & clicking)
        addButtonFromConfig(layout, "lmb_left", "LMB", true);

        // 3. Gameplay Buttons (Right Side)
        addButtonFromConfig(layout, "r", "R", KeyEvent.KEYCODE_R, TYPE_DEFAULT);
        addButtonFromConfig(layout, "f", "F", KeyEvent.KEYCODE_F, TYPE_DEFAULT);
        addButtonFromConfig(layout, "jump", "JUMP", KeyEvent.KEYCODE_SPACE, TYPE_DEFAULT);
        addButtonFromConfig(layout, "crouch", "CROUCH", KeyEvent.KEYCODE_CTRL_LEFT, TYPE_DEFAULT);
        addButtonFromConfig(layout, "shift", "SHIFT", KeyEvent.KEYCODE_SHIFT_LEFT, TYPE_DEFAULT);
        addButtonFromConfig(layout, "sprint", "SPRINT", KeyEvent.KEYCODE_X, TYPE_DEFAULT);

        // 4. Q and E (Camera Turn / Lean)
        addButtonFromConfig(layout, "q", "Q", KeyEvent.KEYCODE_Q, TYPE_QE);
        addButtonFromConfig(layout, "e", "E", KeyEvent.KEYCODE_E, TYPE_QE);

        // 5. Quick Medical Items (Medkit & Bandage)
        addButtonFromConfig(layout, "medkit", "AID KIT", KeyEvent.KEYCODE_LEFT_BRACKET, TYPE_MEDKIT);
        addButtonFromConfig(layout, "bandage", "BAND", KeyEvent.KEYCODE_RIGHT_BRACKET, TYPE_BANDAGE);

        // 6. Weapon Selection 1-6 (Bottom Center)
        addButtonFromConfig(layout, "wpn_1", "1", KeyEvent.KEYCODE_1, TYPE_WEAPON);
        addButtonFromConfig(layout, "wpn_2", "2", KeyEvent.KEYCODE_2, TYPE_WEAPON);
        addButtonFromConfig(layout, "wpn_3", "3", KeyEvent.KEYCODE_3, TYPE_WEAPON);
        addButtonFromConfig(layout, "wpn_4", "4", KeyEvent.KEYCODE_4, TYPE_WEAPON);
        addButtonFromConfig(layout, "wpn_5", "5", KeyEvent.KEYCODE_5, TYPE_WEAPON);
        addButtonFromConfig(layout, "wpn_6", "6", KeyEvent.KEYCODE_6, TYPE_WEAPON);

        // 7. Top Bar System / UI Buttons
        addButtonFromConfig(layout, "esc", "ESC", KeyEvent.KEYCODE_ESCAPE, TYPE_DEFAULT);
        addButtonFromConfig(layout, "inv", "INV", KeyEvent.KEYCODE_I, TYPE_DEFAULT);
        addButtonFromConfig(layout, "pda", "PDA", KeyEvent.KEYCODE_P, TYPE_DEFAULT);
        addButtonFromConfig(layout, "torch", "TORCH", KeyEvent.KEYCODE_L, TYPE_DEFAULT);
        addButtonFromConfig(layout, "qsave", "QSAVE", KeyEvent.KEYCODE_F6, TYPE_DEFAULT);
        addButtonFromConfig(layout, "qload", "QLOAD", KeyEvent.KEYCODE_F7, TYPE_DEFAULT);

        mInitialized = true;
    }

    private void addButtonFromConfig(TouchLayoutConfig.LayoutData layout, String id, String label, boolean isLeft) {
        TouchLayoutConfig.ButtonPos pos = layout.buttons.get(id);
        if (pos == null) return;
        float x = pos.relX * getWidth();
        float y = pos.relY * getHeight();
        float r = pos.relRadius * getHeight() * mButtonScale;
        mButtons.add(new TouchButton(id, label, isLeft, x, y, r));
    }

    private void addButtonFromConfig(TouchLayoutConfig.LayoutData layout, String id, String label, int keyCode, int type) {
        TouchLayoutConfig.ButtonPos pos = layout.buttons.get(id);
        if (pos == null) return;
        float x = pos.relX * getWidth();
        float y = pos.relY * getHeight();
        float r = pos.relRadius * getHeight() * mButtonScale;
        mButtons.add(new TouchButton(id, label, keyCode, x, y, r, type));
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

                // 2. Check Joystick area (Near joystick base)
                float joyDist = (float) Math.hypot(x - mJoyBaseX, y - mJoyBaseY);
                if ((joyDist <= mJoyRadius * 1.6f || (x < getWidth() * 0.35f && y > getHeight() * 0.45f)) && mJoyPointerId == -1) {
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
                            if (!btn.containsWithMargin(x, y, 28.0f)) {
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
                if ("qsave".equals(btn.id)) {
                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F5);
                } else if ("qload".equals(btn.id)) {
                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F9);
                }
            } else {
                SDLActivity.onNativeKeyUp(btn.keyCode);
                if ("qsave".equals(btn.id)) {
                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F5);
                } else if ("qload".equals(btn.id)) {
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

            switch (btn.buttonType) {
                case TYPE_LMB:
                    fillPaint = btn.isPressed ? mLkmPressedPaint : mLkmPaint;
                    strokePaint = mLkmStrokePaint;
                    break;
                case TYPE_RMB:
                    fillPaint = btn.isPressed ? mPkmPressedPaint : mPkmPaint;
                    strokePaint = mPkmStrokePaint;
                    break;
                case TYPE_MEDKIT:
                    fillPaint = btn.isPressed ? mMedkitPressedPaint : mMedkitPaint;
                    strokePaint = mMedkitStrokePaint;
                    break;
                case TYPE_BANDAGE:
                    fillPaint = btn.isPressed ? mBandagePressedPaint : mBandagePaint;
                    strokePaint = mBandageStrokePaint;
                    break;
                case TYPE_QE:
                    fillPaint = btn.isPressed ? mQePressedPaint : mQePaint;
                    strokePaint = mQeStrokePaint;
                    break;
                case TYPE_WEAPON:
                    fillPaint = btn.isPressed ? mWeaponPressedPaint : mWeaponPaint;
                    strokePaint = mWeaponStrokePaint;
                    break;
                default:
                    fillPaint = btn.isPressed ? mButtonPressedPaint : mButtonPaint;
                    strokePaint = mButtonStrokePaint;
                    break;
            }

            // Fill
            canvas.drawRoundRect(btn.bounds, cornerRadius, cornerRadius, fillPaint);
            // Stroke
            canvas.drawRoundRect(btn.bounds, cornerRadius, cornerRadius, strokePaint);

            // Text Label
            float fontSize = btn.bounds.height() * (btn.label.length() > 6 ? 0.22f : (btn.label.length() > 4 ? 0.26f : (btn.label.length() > 2 ? 0.32f : 0.40f)));
            mTextPaint.setTextSize(fontSize);

            float textY = btn.bounds.centerY() - ((mTextPaint.descent() + mTextPaint.ascent()) / 2);
            canvas.drawText(btn.label, btn.bounds.centerX(), textY, mTextPaint);
        }
    }
}
