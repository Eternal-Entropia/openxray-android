package org.openxray;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ControlLayoutEditorActivity extends AppCompatActivity {

    private ControlLayoutEditorView mEditorView;
    private Button mBtnReset;
    private Button mBtnCancel;
    private Button mBtnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_editor);

        hideSystemUI();

        mEditorView = findViewById(R.id.editor_canvas);
        mBtnReset = findViewById(R.id.btn_editor_reset);
        mBtnCancel = findViewById(R.id.btn_editor_cancel);
        mBtnSave = findViewById(R.id.btn_editor_save);

        mBtnReset.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Reset to Defaults?")
                .setMessage("All on-screen buttons and joystick will be restored to their original positions.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    mEditorView.resetToDefaults();
                    Toast.makeText(this, "Layout reset to defaults", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        mBtnCancel.setOnClickListener(v -> finish());

        mBtnSave.setOnClickListener(v -> {
            TouchLayoutConfig.saveLayout(this, mEditorView.getLayoutData());
            Toast.makeText(this, "Control button layout saved!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.Window window = getWindow();
            if (window != null) {
                window.setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }
}
