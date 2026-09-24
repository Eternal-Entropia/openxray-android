package org.openxray;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.util.DisplayMetrics;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ClipData;
import android.content.ClipboardManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

public class LauncherActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "openxray_launcher_prefs";
    private static final int PERMISSION_REQUEST_CODE = 2001;
    private static final int BROWSE_FOLDER_REQUEST_CODE = 2002;

    private EditText mEditGamePath;
    private Button mBtnBrowsePath;
    private Button mBtnCheckPath;
    private Button mBtnViewLogs;
    private TextView mTextPathStatus;

    private RadioGroup mRadioGroupGameMode;
    private RadioButton mRadioSoc;
    private RadioButton mRadioCs;
    private RadioButton mRadioCop;

    private CheckBox mCheckNoIntro;
    private CheckBox mCheckNoSound;
    private CheckBox mCheckNoShadows;
    private CheckBox mCheckDLights;
    private CheckBox mCheckNoSun;
    private CheckBox mCheckFastDxt;
    private Spinner mSpinnerGraphicsPreset;
    private java.util.List<GraphicsPresetItem> mGraphicsPresetList = new java.util.ArrayList<>();
    private ArrayAdapter<GraphicsPresetItem> mGraphicsPresetAdapter;
    private Spinner mSpinnerResolution;
    private java.util.List<ResolutionItem> mResolutionList = new java.util.ArrayList<>();
    private ArrayAdapter<ResolutionItem> mResolutionAdapter;
    private Spinner mSpinnerRenderBackend;
    private TextView mTextRenderBackendStatus;
    private java.util.List<RenderBackendItem> mRenderBackendList = new java.util.ArrayList<>();
    private ArrayAdapter<RenderBackendItem> mRenderBackendAdapter;
    private EditText mEditCustomArgs;

    private TextView mLabelLookSens;
    private SeekBar mSeekLookSens;

    private TextView mLabelTouchOpacity;
    private SeekBar mSeekTouchOpacity;

    private TextView mLabelTouchScale;
    private SeekBar mSeekTouchScale;

    private Button mBtnEditTouchLayout;

    private RadioButton mRadioDiffNovice;
    private RadioButton mRadioDiffStalker;
    private RadioButton mRadioDiffVeteran;
    private RadioButton mRadioDiffMaster;

    private Button mBtnStartNewGame;
    private Button mBtnStartGame;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedPath = mPrefs.getString("game_path", null);
        AppLog.init(this, savedPath);
        AppLog.i("Launcher", "LauncherActivity onCreate started");

        initViews();
        loadPreferences();
        checkAndRequestPermissions();
        checkGamePathStatus();
        AppLog.i("Launcher", "LauncherActivity onCreate finished");
    }

    private void initViews() {
        mEditGamePath = findViewById(R.id.edit_game_path);
        mBtnBrowsePath = findViewById(R.id.btn_browse_path);
        mBtnCheckPath = findViewById(R.id.btn_check_path);
        mBtnViewLogs = findViewById(R.id.btn_view_logs);
        mTextPathStatus = findViewById(R.id.text_path_status);

        mBtnBrowsePath.setOnClickListener(v -> openFolderPicker());
        mBtnCheckPath.setOnClickListener(v -> checkGamePathStatus());
        if (mBtnViewLogs != null) {
            mBtnViewLogs.setOnClickListener(v -> showLogsDialog());
        }

        mRadioGroupGameMode = findViewById(R.id.radiogroup_gamemode);
        mRadioSoc = findViewById(R.id.radio_soc);
        mRadioCs = findViewById(R.id.radio_cs);
        mRadioCop = findViewById(R.id.radio_cop);
        if (mRadioGroupGameMode != null) {
            // Each game lives in its own subfolder (soc/cs/cop) — re-check files on switch.
            mRadioGroupGameMode.setOnCheckedChangeListener((group, checkedId) -> checkGamePathStatus());
        }

        mCheckNoIntro = findViewById(R.id.check_nointro);
        mCheckNoSound = findViewById(R.id.check_nosound);
        mCheckNoShadows = findViewById(R.id.check_noshadows);
        mCheckDLights = findViewById(R.id.check_dlights);
        mCheckNoSun = findViewById(R.id.check_nosun);
        mCheckFastDxt = findViewById(R.id.check_fastdxt);
        mSpinnerGraphicsPreset = findViewById(R.id.spinner_graphics_preset);
        setupGraphicsPresetSpinner();
        mSpinnerResolution = findViewById(R.id.spinner_resolution);
        setupResolutionSpinner();
        mSpinnerRenderBackend = findViewById(R.id.spinner_render_backend);
        mTextRenderBackendStatus = findViewById(R.id.text_render_backend_status);
        setupRenderBackendSpinner();
        mEditCustomArgs = findViewById(R.id.edit_custom_args);

        mLabelLookSens = findViewById(R.id.label_look_sens);
        mSeekLookSens = findViewById(R.id.seek_look_sens);

        mLabelTouchOpacity = findViewById(R.id.label_touch_opacity);
        mSeekTouchOpacity = findViewById(R.id.seek_touch_opacity);

        mLabelTouchScale = findViewById(R.id.label_touch_scale);
        mSeekTouchScale = findViewById(R.id.seek_touch_scale);

        mBtnEditTouchLayout = findViewById(R.id.btn_edit_touch_layout);

        if (mBtnEditTouchLayout != null) {
            mBtnEditTouchLayout.setOnClickListener(v -> {
                Intent intent = new Intent(this, ControlLayoutEditorActivity.class);
                startActivity(intent);
            });
        }

        mBtnStartGame = findViewById(R.id.btn_start_game);

        mBtnCheckPath.setOnClickListener(v -> checkGamePathStatus());

        mEditGamePath.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                checkGamePathStatus();
            }
        });

        // Seekbar listeners
        mSeekLookSens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = Math.max(5, progress) / 10.0f;
                mLabelLookSens.setText(String.format(Locale.US, "Camera sensitivity: %.1fx", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mSeekTouchOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(20, progress);
                mLabelTouchOpacity.setText(String.format(Locale.US, "Button opacity: %d%%", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mSeekTouchScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(70, progress);
                mLabelTouchScale.setText(String.format(Locale.US, "Button size: %d%%", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mRadioDiffNovice = findViewById(R.id.radio_diff_novice);
        mRadioDiffStalker = findViewById(R.id.radio_diff_stalker);
        mRadioDiffVeteran = findViewById(R.id.radio_diff_veteran);
        mRadioDiffMaster = findViewById(R.id.radio_diff_master);

        mBtnStartNewGame = findViewById(R.id.btn_start_new_game);
        mBtnStartGame = findViewById(R.id.btn_start_game);

        mBtnStartNewGame.setOnClickListener(v -> launchGame(true));
        mBtnStartGame.setOnClickListener(v -> launchGame(false));
    }

    private void loadPreferences() {
        String defaultPath = new File(Environment.getExternalStorageDirectory(), "OpenXRay").getAbsolutePath();
        mEditGamePath.setText(mPrefs.getString("game_path", defaultPath));

        String mode = mPrefs.getString("game_mode", "soc");
        if ("cs".equals(mode)) mRadioCs.setChecked(true);
        else if ("cop".equals(mode)) mRadioCop.setChecked(true);
        else mRadioSoc.setChecked(true);

        mCheckNoIntro.setChecked(mPrefs.getBoolean("nointro", false));
        mCheckNoSound.setChecked(mPrefs.getBoolean("nosound", false));
        mCheckNoShadows.setChecked(mPrefs.getBoolean("noshadows", false));
        mCheckDLights.setChecked(mPrefs.getBoolean("dlights", true));
        if (mCheckNoSun != null) {
            mCheckNoSun.setChecked(mPrefs.getBoolean("nosun", false));
        }
        if (mCheckFastDxt != null) {
            mCheckFastDxt.setChecked(mPrefs.getBoolean("fastdxt", true));
        }

        String savedPreset = mPrefs.getString("graphics_preset", "none");
        int presetIndex = 0;
        for (int i = 0; i < mGraphicsPresetList.size(); i++) {
            if (mGraphicsPresetList.get(i).id.equals(savedPreset)) {
                presetIndex = i;
                break;
            }
        }
        if (mSpinnerGraphicsPreset != null) {
            mSpinnerGraphicsPreset.setSelection(presetIndex);
        }

        if (!mPrefs.getBoolean("custom_args_v2_initialized", false)) {
            String customArgs = mPrefs.getString("custom_args", "");
            if (customArgs.isEmpty()) {
                customArgs = "-xclsx";
            } else if (!customArgs.contains("-xclsx")) {
                customArgs = (customArgs + " -xclsx").trim();
            }
            mEditCustomArgs.setText(customArgs);
            mPrefs.edit().putBoolean("custom_args_v2_initialized", true).apply();
        } else {
            mEditCustomArgs.setText(mPrefs.getString("custom_args", "-xclsx"));
        }

        int lookSens = mPrefs.getInt("look_sens", 10);
        mSeekLookSens.setProgress(lookSens);
        mLabelLookSens.setText(String.format(Locale.US, "Camera sensitivity: %.1fx", lookSens / 10.0f));

        int opacity = mPrefs.getInt("touch_opacity", 60);
        mSeekTouchOpacity.setProgress(opacity);
        mLabelTouchOpacity.setText(String.format(Locale.US, "Button opacity: %d%%", opacity));

        int scale = mPrefs.getInt("touch_scale", 100);
        mSeekTouchScale.setProgress(scale);
        mLabelTouchScale.setText(String.format(Locale.US, "Button size: %d%%", scale));

        int savedW = mPrefs.getInt("res_width", 0);
        int savedH = mPrefs.getInt("res_height", 0);
        int selectedIndex = 0;
        for (int i = 0; i < mResolutionList.size(); i++) {
            ResolutionItem item = mResolutionList.get(i);
            if (item.width == savedW && item.height == savedH) {
                selectedIndex = i;
                break;
            }
        }
        if (mSpinnerResolution != null) {
            mSpinnerResolution.setSelection(selectedIndex);
        }

        String savedBackend = mPrefs.getString("render_backend", "native");
        int backendIndex = 0;
        for (int i = 0; i < mRenderBackendList.size(); i++) {
            RenderBackendItem item = mRenderBackendList.get(i);
            if (item.id.equals(savedBackend)) {
                backendIndex = i;
                break;
            }
        }
        if (mSpinnerRenderBackend != null) {
            mSpinnerRenderBackend.setSelection(backendIndex);
            updateRenderBackendStatus();
        }

        String diff = mPrefs.getString("difficulty", "gd_novice");
        if ("gd_stalker".equals(diff)) mRadioDiffStalker.setChecked(true);
        else if ("gd_veteran".equals(diff)) mRadioDiffVeteran.setChecked(true);
        else if ("gd_master".equals(diff)) mRadioDiffMaster.setChecked(true);
        else mRadioDiffNovice.setChecked(true);
    }

    private void savePreferences() {
        String mode = "soc";
        if (mRadioCs.isChecked()) mode = "cs";
        else if (mRadioCop.isChecked()) mode = "cop";

        String diff = "gd_novice";
        if (mRadioDiffStalker.isChecked()) diff = "gd_stalker";
        else if (mRadioDiffVeteran.isChecked()) diff = "gd_veteran";
        else if (mRadioDiffMaster.isChecked()) diff = "gd_master";

        mPrefs.edit()
            .putString("game_path", mEditGamePath.getText().toString().trim())
            .putString("game_mode", mode)
            .putString("difficulty", diff)
            .putBoolean("nointro", mCheckNoIntro.isChecked())
            .putBoolean("nosound", mCheckNoSound.isChecked())
            .putBoolean("noshadows", mCheckNoShadows.isChecked())
            .putBoolean("dlights", mCheckDLights.isChecked())
            .putBoolean("nosun", mCheckNoSun != null && mCheckNoSun.isChecked())
            .putBoolean("fastdxt", mCheckFastDxt != null && mCheckFastDxt.isChecked())
            .putString("custom_args", mEditCustomArgs.getText().toString().trim())
            .putInt("look_sens", Math.max(5, mSeekLookSens.getProgress()))
            .putInt("touch_opacity", Math.max(20, mSeekTouchOpacity.getProgress()))
            .putInt("touch_scale", Math.max(70, mSeekTouchScale.getProgress()))
            .apply();

        if (mSpinnerResolution != null && mSpinnerResolution.getSelectedItem() instanceof ResolutionItem) {
            ResolutionItem item = (ResolutionItem) mSpinnerResolution.getSelectedItem();
            mPrefs.edit()
                .putInt("res_width", item.width)
                .putInt("res_height", item.height)
                .apply();
        }

        if (mSpinnerRenderBackend != null && mSpinnerRenderBackend.getSelectedItem() instanceof RenderBackendItem) {
            RenderBackendItem item = (RenderBackendItem) mSpinnerRenderBackend.getSelectedItem();
            mPrefs.edit()
                .putString("render_backend", item.id)
                .apply();
        }

        if (mSpinnerGraphicsPreset != null && mSpinnerGraphicsPreset.getSelectedItem() instanceof GraphicsPresetItem) {
            GraphicsPresetItem item = (GraphicsPresetItem) mSpinnerGraphicsPreset.getSelectedItem();
            mPrefs.edit()
                .putString("graphics_preset", item.id)
                .apply();
        }
    }

    private String selectedGameMode() {
        if (mRadioCs != null && mRadioCs.isChecked()) return "cs";
        if (mRadioCop != null && mRadioCop.isChecked()) return "cop";
        return "soc";
    }

    private void checkGamePathStatus() {
        String pathStr = mEditGamePath.getText().toString().trim();
        if (pathStr.isEmpty()) {
            mTextPathStatus.setText("Path not specified");
            mTextPathStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red));
            return;
        }

        // Each game has its own subfolder: <path>/soc, <path>/cs, <path>/cop.
        // SoC archives are gamedata.db* in the mode root; retail CoP/CS
        // archives live in subfolders (levels/, resources/, ...).
        String mode = selectedGameMode();
        File dir = new File(pathStr, mode);

        File[] dbFiles = AppLog.collectDatabaseArchives(dir);
        if (dbFiles != null && dbFiles.length > 0) {
            mTextPathStatus.setText("✓ Found " + dbFiles.length + " game archives for " + mode + " in " + dir.getAbsolutePath());
            mTextPathStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green));
        } else {
            mTextPathStatus.setText("✗ No game archives in " + dir.getAbsolutePath() + " — copy the " + mode + " archives there");
            mTextPathStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red));
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void launchGame(boolean isNewGame) {
        savePreferences();

        String basePath = mEditGamePath.getText().toString().trim();

        String gameMode = "soc";
        if (mRadioCs.isChecked()) {
            gameMode = "cs";
        } else if (mRadioCop.isChecked()) {
            gameMode = "cop";
        }

        // Each game lives in its own subfolder: <base>/soc, <base>/cs, <base>/cop.
        // Saves, configs, logs and gamedata.db of different games never mix.
        String pathStr = basePath.isEmpty() ? basePath : new File(basePath, gameMode).getAbsolutePath();
        File dir = new File(pathStr);
        if (!pathStr.isEmpty() && !dir.exists()) {
            try {
                dir.mkdirs();
            } catch (Exception ignored) {}
        }

        // Build args string
        StringBuilder argsBuilder = new StringBuilder();

        if ("cs".equals(gameMode)) {
            argsBuilder.append("-cs ");
        } else if ("cop".equals(gameMode)) {
            argsBuilder.append("-cop ");
        } else {
            argsBuilder.append("-soc ");
        }

        // OpenGL (-rgl) is always enabled
        argsBuilder.append("-rgl ");
        if (mCheckNoIntro.isChecked()) {
            argsBuilder.append("-nointro ");
        }
        if (mCheckNoSound.isChecked()) {
            argsBuilder.append("-nosound ");
        }
        if (mCheckNoShadows.isChecked()) {
            argsBuilder.append("-noshadows ");
        }
        if (!mCheckDLights.isChecked()) {
            argsBuilder.append("-nodlights -sunstatic ");
        }

        String customArgs = mEditCustomArgs.getText().toString().trim();
        if (!customArgs.isEmpty()) {
            argsBuilder.append(customArgs).append(" ");
        }

        if (!pathStr.isEmpty()) {
            argsBuilder.append("-game_path \"").append(pathStr).append("\" ");
        }

        String diff = null;
        if (isNewGame) {
            diff = "gd_novice";
            if (mRadioDiffStalker.isChecked()) diff = "gd_stalker";
            else if (mRadioDiffVeteran.isChecked()) diff = "gd_veteran";
            else if (mRadioDiffMaster.isChecked()) diff = "gd_master";

            argsBuilder.append("-difficulty ").append(diff).append(" ");
            argsBuilder.append("-start server(all/single/alife/new) client(localhost) ");
        }

        ResolutionItem selectedRes = (mSpinnerResolution != null && mSpinnerResolution.getSelectedItem() instanceof ResolutionItem)
            ? (ResolutionItem) mSpinnerResolution.getSelectedItem()
            : null;
        int resW = (selectedRes != null) ? selectedRes.width : 0;
        int resH = (selectedRes != null) ? selectedRes.height : 0;

        boolean noSun = mCheckNoSun != null && mCheckNoSun.isChecked();
        String graphicsPreset = "balanced";
        if (mSpinnerGraphicsPreset != null && mSpinnerGraphicsPreset.getSelectedItem() instanceof GraphicsPresetItem) {
            graphicsPreset = ((GraphicsPresetItem) mSpinnerGraphicsPreset.getSelectedItem()).id;
        }

        updateUserLtxSettingsIfChanged(pathStr, gameMode, diff, mCheckNoShadows.isChecked(), mCheckDLights.isChecked(), noSun, mCheckFastDxt != null && mCheckFastDxt.isChecked(), graphicsPreset, resW, resH);

        float sens = Math.max(5, mSeekLookSens.getProgress()) / 10.0f;
        float opacity = Math.max(20, mSeekTouchOpacity.getProgress()) / 100.0f;
        float scale = Math.max(70, mSeekTouchScale.getProgress()) / 100.0f;

        String renderBackend = "native";
        if (mSpinnerRenderBackend != null && mSpinnerRenderBackend.getSelectedItem() instanceof RenderBackendItem) {
            renderBackend = ((RenderBackendItem) mSpinnerRenderBackend.getSelectedItem()).id;
        }

        String finalArgs = argsBuilder.toString().trim();
        AppLog.i("Launcher", "Starting OpenXRayActivity: isNewGame=" + isNewGame + ", renderBackend=" + renderBackend + ", args: " + finalArgs);

        Intent gameIntent = new Intent(this, OpenXRayActivity.class);
        gameIntent.putExtra("extra_args", finalArgs);
        gameIntent.putExtra("extra_game_path", pathStr);
        gameIntent.putExtra("extra_game_mode", gameMode);
        gameIntent.putExtra("extra_res_width", resW);
        gameIntent.putExtra("extra_res_height", resH);
        gameIntent.putExtra("extra_render_backend", renderBackend);
        gameIntent.putExtra("extra_look_sensitivity", sens);
        gameIntent.putExtra("extra_touch_opacity", opacity);
        gameIntent.putExtra("extra_touch_scale", scale);

        startActivity(gameIntent);
    }

    private void showLogsDialog() {
        String basePath = mEditGamePath.getText().toString().trim();
        String pathStr = (basePath != null && !basePath.isEmpty())
            ? new File(basePath, selectedGameMode()).getAbsolutePath()
            : basePath;
        File logDir = (pathStr != null && !pathStr.isEmpty())
            ? new File(pathStr)
            : new File(Environment.getExternalStorageDirectory(), "OpenXRay");

        File appLogFile = new File(logDir, "openxray_app.log");
        File logcatFile = new File(logDir, "openxray_logcat.log");
        File appLogBkpFile = new File(logDir, "openxray_app.log.bkp");
        File logcatBkpFile = new File(logDir, "openxray_logcat.log.bkp");

        StringBuilder content = new StringBuilder();
        content.append("Log directory: ").append(logDir.getAbsolutePath()).append("\n\n");

        if (appLogFile.exists()) {
            content.append("=== [openxray_app.log (Current Session)] ===\n");
            content.append(readLastLines(appLogFile, 150)).append("\n\n");
        } else {
            content.append("openxray_app.log: not found\n\n");
        }

        if (appLogBkpFile.exists() && appLogBkpFile.length() > 0) {
            content.append("=== [openxray_app.log.bkp (Previous Session / Crash)] ===\n");
            content.append(readLastLines(appLogBkpFile, 150)).append("\n\n");
        }

        if (logcatFile.exists()) {
            content.append("=== [openxray_logcat.log] (last 100 lines) ===\n");
            content.append(readLastLines(logcatFile, 100)).append("\n");
        }

        if (logcatBkpFile.exists() && logcatBkpFile.length() > 0) {
            content.append("\n=== [openxray_logcat.log.bkp (Previous Session Logcat)] (last 100 lines) ===\n");
            content.append(readLastLines(logcatBkpFile, 100)).append("\n");
        }

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(content.toString());
        tv.setTextSize(11);
        tv.setPadding(30, 20, 30, 20);
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        sv.addView(tv);

        new AlertDialog.Builder(this)
            .setTitle("OpenXRay Logs")
            .setView(sv)
            .setPositiveButton("Copy to Clipboard", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("OpenXRay Logs", content.toString());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private String readLastLines(File file, int maxLines) {
        if (!file.exists()) return "";
        java.util.LinkedList<String> lines = new java.util.LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines) {
                    lines.removeFirst();
                }
            }
        } catch (Exception e) {
            return "Error reading log: " + e.getMessage();
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    // Engine _preset tokens (see qpreset_token in xrRender_console.cpp).
    // "none" means the launcher does not manage graphics at all (set in game).
    private static String presetTokenForId(String presetId) {
        if ("none".equals(presetId)) return null;
        if ("minimum".equals(presetId)) return "Minimum";
        if ("low".equals(presetId)) return "Low";
        if ("high".equals(presetId)) return "High";
        if ("extreme".equals(presetId)) return "Extreme";
        if ("potato".equals(presetId)) return "Minimum";
        return "Default";
    }

    // Extra cheap settings applied on top of Minimum for the Potato preset.
    // All names verified against xrRender_console.cpp console commands.
    private static final String[][] POTATO_EXTRA_SETTINGS = {
        {"r2_ssao_mode", "disabled"},
        {"r2_ssao", "off"},
        {"r2_sun_shafts", "st_opt_off"},
        {"r2_sun_quality", "st_opt_low"},
        {"r2_smap_size", "1024"},
        {"r2_dof_enable", "off"},
        {"r3_msaa", "st_opt_off"},
        {"r2_volumetric_lights", "off"},
        {"r3_volumetric_smoke", "off"},
        {"r3_dynamic_wet_surfaces", "off"},
        {"r3_water_refl", "st_opt_off"},
        {"r2_steep_parallax", "off"},
        {"r2_detail_bump", "off"},
        {"r2_soft_water", "off"},
        {"r2_soft_particles", "off"},
        {"r__tf_aniso", "1"},
        {"r__detail_density", "0.3"},
        {"r2_aa", "off"},
    };

    // Graphics keys fully managed by the launcher: old values are dropped from
    // user.ltx and re-appended after the _preset line, so switching presets
    // never leaves stale overrides behind.
    private static final String[] MANAGED_GRAPHICS_KEYS = {
        "_preset",
        "r2_sun", "r2_sun_details", "r__actor_shadow",
        "r2_volumetric_lights", "r2_slight_fade",
        "r2_ssao_mode", "r2_ssao", "r2_sun_shafts", "r2_sun_quality",
        "r2_smap_size", "r2_dof_enable", "r3_msaa",
        "r3_volumetric_smoke", "r3_dynamic_wet_surfaces", "r3_water_refl",
        "r2_steep_parallax", "r2_detail_bump", "r2_soft_water",
        "r2_soft_particles", "r__tf_aniso", "r__detail_density", "r2_aa",
        // Extra aspects covered per preset below.
        "r2_tf_mipbias", "r__geometry_lod", "r2_ssa_lod_a", "r2_ssa_lod_b",
        "r2_sun_far", "rs_skeleton_update", "r__fast_dxt",
    };

    // Full-aspect values per graphics preset. Key names verified against
    // xrRender_console.cpp console commands and their min/max ranges.
    // Texture quality: r__tf_aniso (filtering) + r2_tf_mipbias (detail).
    // Draw distance: r2_ssa_lod_a/b (object cull) + r2_sun_far.
    // Model quality: r__geometry_lod + rs_skeleton_update (lower = smoother).
    private static java.util.Map<String, String[][]> presetAspectSettings() {
        java.util.Map<String, String[][]> m = new java.util.LinkedHashMap<>();
        String[][] potato = {
            {"r__tf_aniso", "1"}, {"r2_tf_mipbias", "1.0"},
            {"r__geometry_lod", "0.5"}, {"rs_skeleton_update", "32"},
            {"r2_ssa_lod_a", "32"}, {"r2_ssa_lod_b", "32"},
            {"r__detail_density", "0.3"},
            {"r2_smap_size", "1024"}, {"r2_sun_far", "60"},
        };
        String[][] minimum = {
            {"r__tf_aniso", "2"}, {"r2_tf_mipbias", "0.5"},
            {"r__geometry_lod", "0.6"}, {"rs_skeleton_update", "24"},
            {"r2_ssa_lod_a", "32"}, {"r2_ssa_lod_b", "32"},
            {"r__detail_density", "0.3"},
            {"r2_smap_size", "1024"}, {"r2_sun_far", "70"},
        };
        String[][] low = {
            {"r__tf_aniso", "4"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "0.75"}, {"rs_skeleton_update", "16"},
            {"r2_ssa_lod_a", "48"}, {"r2_ssa_lod_b", "40"},
            {"r__detail_density", "0.45"},
            {"r2_smap_size", "1536"}, {"r2_sun_far", "80"},
        };
        String[][] balanced = {
            {"r__tf_aniso", "4"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "0.9"}, {"rs_skeleton_update", "10"},
            {"r2_ssa_lod_a", "64"}, {"r2_ssa_lod_b", "48"},
            {"r__detail_density", "0.6"},
            {"r2_smap_size", "2048"}, {"r2_sun_far", "100"},
        };
        String[][] high = {
            {"r__tf_aniso", "8"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "1.0"}, {"rs_skeleton_update", "8"},
            {"r2_ssa_lod_a", "64"}, {"r2_ssa_lod_b", "56"},
            {"r__detail_density", "0.75"},
            {"r2_smap_size", "2048"}, {"r2_sun_far", "120"},
        };
        String[][] extreme = {
            {"r__tf_aniso", "16"}, {"r2_tf_mipbias", "0"},
            {"r__geometry_lod", "1.25"}, {"rs_skeleton_update", "4"},
            {"r2_ssa_lod_a", "96"}, {"r2_ssa_lod_b", "64"},
            {"r__detail_density", "0.9"},
            {"r2_smap_size", "3072"}, {"r2_sun_far", "150"},
        };
        m.put("potato", potato);
        m.put("minimum", minimum);
        m.put("low", low);
        m.put("balanced", balanced);
        m.put("high", high);
        m.put("extreme", extreme);
        return m;
    }

    private static boolean isManagedGraphicsKey(String trimmedLine) {
        for (String key : MANAGED_GRAPHICS_KEYS) {
            if (trimmedLine.equals(key)
                    || trimmedLine.startsWith(key + " ")
                    || trimmedLine.startsWith(key + "\t")) {
                return true;
            }
        }
        return false;
    }

    // The launcher rewrites graphics keys in user.ltx ONLY when the
    // launcher-side selection changed since the last launch (or on first
    // run). Otherwise settings tweaked inside the game are left alone —
    // previously every launch wiped them back to the spinner values.
    private void updateUserLtxSettingsIfChanged(String modePathStr, String gameMode, String diff,
            boolean noShadows, boolean dLights, boolean noSun, boolean fastDxt,
            String graphicsPreset, int resW, int resH) {
        if ("none".equals(graphicsPreset)) {
            AppLog.i("Launcher", "Graphics preset is 'none', leaving user.ltx alone (set in game)");
            return;
        }
        String sig = graphicsPreset + "|" + resW + "x" + resH + "|"
                + noShadows + "|" + dLights + "|" + noSun + "|" + fastDxt + "|" + diff;
        String key = "applied_gfx_signature_" + gameMode;
        String prev = mPrefs.getString(key, null);
        File probe = new File(new File(modePathStr, "_appdata_"), "user.ltx");
        if (sig.equals(prev) && probe.exists()) {
            AppLog.i("Launcher", "Graphics selection unchanged, keeping in-game user.ltx");
            return;
        }
        updateUserLtxSettings(modePathStr, diff, noShadows, dLights, noSun, fastDxt, graphicsPreset, resW, resH);
        mPrefs.edit().putString(key, sig).apply();
    }

    private void updateUserLtxSettings(String pathStr, String diff, boolean noShadows, boolean dLights, boolean noSun, boolean fastDxt, String graphicsPreset, int resW, int resH) {
        if (pathStr == null || pathStr.isEmpty()) return;
        try {
            File appDataDir = new File(pathStr, "_appdata_");
            if (!appDataDir.exists()) {
                appDataDir.mkdirs();
            }

            File[] candidates = new File[] {
                new File(appDataDir, "user.ltx"),
                new File(pathStr, "appdata/user.ltx"),
                new File(pathStr, "user.ltx")
            };

            File targetFile = candidates[0];
            for (File f : candidates) {
                if (f.exists()) {
                    targetFile = f;
                    break;
                }
            }

            java.util.Map<String, String> settings = new java.util.LinkedHashMap<>();
            // _preset must come first: it loads rspec_*.ltx, lines below override it.
            settings.put("_preset", presetTokenForId(graphicsPreset));
            // Full-aspect values for this preset (texture quality, draw
            // distance, model quality...). Explicit sun/light checkbox
            // choices below still win over these.
            String[][] aspects = presetAspectSettings().get(graphicsPreset);
            if (aspects != null) {
                for (String[] kv : aspects) {
                    settings.put(kv[0], kv[1]);
                }
            }
            if (diff != null && !diff.isEmpty()) {
                settings.put("g_game_difficulty", diff);
            }
            // Fast CPU DXT decode (bcdec); managed so the checkbox state
            // always matches the engine, like the other graphics keys.
            settings.put("r__fast_dxt", fastDxt ? "1" : "0");
            if (resW > 0 && resH > 0) {
                settings.put("vid_mode", resW + "x" + resH);
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                int screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
                int screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
                if (screenW > 0 && screenH > 0) {
                    settings.put("vid_mode", screenW + "x" + screenH);
                }
            }
            boolean sunOff = noSun || noShadows || !dLights;
            if (sunOff) {
                settings.put("r2_sun", "off");
            } else {
                settings.put("r2_sun", "on");
            }
            if (noShadows) {
                settings.put("r2_sun_details", "off");
                settings.put("r__actor_shadow", "off");
            } else {
                settings.put("r2_sun_details", "on");
                settings.put("r__actor_shadow", "on");
            }
            if (!dLights) {
                settings.put("r2_volumetric_lights", "off");
                settings.put("r2_slight_fade", "0.05");
            } else {
                settings.put("r2_slight_fade", "0.5");
            }
            if ("potato".equals(graphicsPreset)) {
                for (String[] kv : POTATO_EXTRA_SETTINGS) {
                    // Don't stomp explicit sun/light choices made above.
                    if (!settings.containsKey(kv[0])) {
                        settings.put(kv[0], kv[1]);
                    }
                }
            }

            java.util.List<String> lines = new java.util.ArrayList<>();
            java.util.Set<String> updatedKeys = new java.util.HashSet<>();

            if (targetFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(targetFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (isManagedGraphicsKey(trimmed)) {
                            continue;
                        }
                        boolean replaced = false;
                        for (java.util.Map.Entry<String, String> entry : settings.entrySet()) {
                            if (trimmed.startsWith(entry.getKey() + " ")) {
                                lines.add(entry.getKey() + " " + entry.getValue());
                                updatedKeys.add(entry.getKey());
                                replaced = true;
                                break;
                            }
                        }
                        if (!replaced) {
                            lines.add(line);
                        }
                    }
                }
            } else {
                lines.add("renderer renderer_rgl");
            }

            File[] saveDirs = new File[] {
                new File(appDataDir, "savedgames"),
                new File(pathStr, "savedgames"),
                new File(pathStr, "appdata/savedgames")
            };
            String latestSaveName = null;
            long latestSaveTime = 0;
            for (File sDir : saveDirs) {
                if (sDir.exists() && sDir.isDirectory()) {
                    File[] files = sDir.listFiles();
                    if (files != null) {
                        for (File sf : files) {
                            String fn = sf.getName();
                            if ((fn.endsWith(".sav") || fn.endsWith(".scop")) && sf.lastModified() > latestSaveTime) {
                                latestSaveTime = sf.lastModified();
                                int dot = fn.lastIndexOf('.');
                                latestSaveName = (dot > 0) ? fn.substring(0, dot) : fn;
                            }
                        }
                    }
                }
            }
            if (latestSaveName != null && !latestSaveName.isEmpty()) {
                settings.put("load_last_save", latestSaveName);
            }

            for (java.util.Map.Entry<String, String> entry : settings.entrySet()) {
                if (!updatedKeys.contains(entry.getKey())) {
                    lines.add(entry.getKey() + " " + entry.getValue());
                }
            }

            boolean hasQuickSave = false;
            boolean hasQuickLoad = false;
            boolean hasQuit = false;
            boolean hasMedkit = false;
            boolean hasBandage = false;
            boolean hasLookoutL = false;
            boolean hasLookoutR = false;
            boolean hasWpn1 = false, hasWpn2 = false, hasWpn3 = false, hasWpn4 = false, hasWpn5 = false, hasWpn6 = false;

            for (String l : lines) {
                String tl = l.trim();
                if (tl.startsWith("bind quick_save ") || tl.startsWith("bind_sec quick_save ")) hasQuickSave = true;
                if (tl.startsWith("bind quick_load ") || tl.startsWith("bind_sec quick_load ")) hasQuickLoad = true;
                if (tl.startsWith("bind quit ") || tl.startsWith("bind_sec quit ")) hasQuit = true;
                if (tl.startsWith("bind use_medkit ") || tl.startsWith("bind_sec use_medkit ")) hasMedkit = true;
                if (tl.startsWith("bind use_bandage ") || tl.startsWith("bind_sec use_bandage ")) hasBandage = true;
                if (tl.startsWith("bind llookout ") || tl.startsWith("bind_sec llookout ")) hasLookoutL = true;
                if (tl.startsWith("bind rlookout ") || tl.startsWith("bind_sec rlookout ")) hasLookoutR = true;
                if (tl.startsWith("bind wpn_1 ") || tl.startsWith("bind_sec wpn_1 ")) hasWpn1 = true;
                if (tl.startsWith("bind wpn_2 ") || tl.startsWith("bind_sec wpn_2 ")) hasWpn2 = true;
                if (tl.startsWith("bind wpn_3 ") || tl.startsWith("bind_sec wpn_3 ")) hasWpn3 = true;
                if (tl.startsWith("bind wpn_4 ") || tl.startsWith("bind_sec wpn_4 ")) hasWpn4 = true;
                if (tl.startsWith("bind wpn_5 ") || tl.startsWith("bind_sec wpn_5 ")) hasWpn5 = true;
                if (tl.startsWith("bind wpn_6 ") || tl.startsWith("bind_sec wpn_6 ")) hasWpn6 = true;
            }
            if (!hasQuickSave) {
                lines.add("bind quick_save kF6");
                lines.add("bind_sec quick_save kF5");
            }
            if (!hasQuickLoad) {
                lines.add("bind quick_load kF7");
                lines.add("bind_sec quick_load kF9");
            }
            if (!hasQuit) {
                lines.add("bind quit kESCAPE");
            }
            if (!hasMedkit) {
                lines.add("bind use_medkit kLBRACKET");
            }
            if (!hasBandage) {
                lines.add("bind use_bandage kRBRACKET");
            }
            if (!hasLookoutL) {
                lines.add("bind llookout kQ");
            }
            if (!hasLookoutR) {
                lines.add("bind rlookout kE");
            }
            if (!hasWpn1) lines.add("bind wpn_1 k1");
            if (!hasWpn2) lines.add("bind wpn_2 k2");
            if (!hasWpn3) lines.add("bind wpn_3 k3");
            if (!hasWpn4) lines.add("bind wpn_4 k4");
            if (!hasWpn5) lines.add("bind wpn_5 k5");
            if (!hasWpn6) lines.add("bind wpn_6 k6");

            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(targetFile))) {
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            }
        } catch (Exception ignored) {}
    }

    private void openFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, BROWSE_FOLDER_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open folder picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BROWSE_FOLDER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                String path = getPathFromTreeUri(treeUri);
                if (path != null && !path.isEmpty()) {
                    mEditGamePath.setText(path);
                    checkGamePathStatus();
                }
            }
        }
    }

    private String getPathFromTreeUri(Uri treeUri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (docId != null) {
                String[] split = docId.split(":");
                if (split.length > 0) {
                    String type = split[0];
                    String relativePath = split.length > 1 ? split[1] : "";
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + (relativePath.isEmpty() ? "" : "/" + relativePath);
                    } else {
                        return "/storage/" + type + (relativePath.isEmpty() ? "" : "/" + relativePath);
                    }
                }
            }
        } catch (Exception ignored) {}
        return treeUri.getPath();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            checkGamePathStatus();
        }
    }

    public static class ResolutionItem {
        public final String title;
        public final int width;
        public final int height;

        public ResolutionItem(String title, int width, int height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void setupResolutionSpinner() {
        mResolutionList.clear();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
        int screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (screenW <= 0) screenW = 1920;
        if (screenH <= 0) screenH = 1080;
        float aspect = (float) screenW / (float) screenH;

        // 1. Native resolution
        mResolutionList.add(new ResolutionItem("Native: " + screenW + "x" + screenH + " (Screen native)", 0, 0));

        // 2. Aspect-ratio matched resolutions (preserving full-width screen without stretching)
        if (Math.abs(aspect - (16.0f / 9.0f)) > 0.05f) {
            int w1080 = Math.round(1080 * aspect);
            int w720 = Math.round(720 * aspect);
            int w540 = Math.round(540 * aspect);
            mResolutionList.add(new ResolutionItem("1080p Screen (" + w1080 + "x1080)", w1080, 1080));
            mResolutionList.add(new ResolutionItem("720p Screen (" + w720 + "x720)", w720, 720));
            mResolutionList.add(new ResolutionItem("540p Screen (" + w540 + "x540)", w540, 540));
        }

        // 3. Standard fixed resolutions
        mResolutionList.add(new ResolutionItem("Full HD (1920x1080, 16:9)", 1920, 1080));
        mResolutionList.add(new ResolutionItem("720p (1280x720, 16:9)", 1280, 720));
        mResolutionList.add(new ResolutionItem("qHD (960x540, 16:9)", 960, 540));
        mResolutionList.add(new ResolutionItem("SVGA (800x600, 4:3)", 800, 600));
        mResolutionList.add(new ResolutionItem("VGA (640x480, 4:3)", 640, 480));

        mResolutionAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_resolution_spinner,
            mResolutionList
        );
        mResolutionAdapter.setDropDownViewResource(R.layout.item_resolution_dropdown);
        mSpinnerResolution.setAdapter(mResolutionAdapter);
    }

    public static class GraphicsPresetItem {
        public final String title;
        public final String id;

        public GraphicsPresetItem(String title, String id) {
            this.title = title;
            this.id = id;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void setupGraphicsPresetSpinner() {
        mGraphicsPresetList.clear();
        mGraphicsPresetList.add(new GraphicsPresetItem("None (set in game)", "none"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Potato (max speed)", "potato"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Minimum", "minimum"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Low", "low"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Balanced", "balanced"));
        mGraphicsPresetList.add(new GraphicsPresetItem("High", "high"));
        mGraphicsPresetList.add(new GraphicsPresetItem("Extreme", "extreme"));

        mGraphicsPresetAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_resolution_spinner,
            mGraphicsPresetList
        );
        mGraphicsPresetAdapter.setDropDownViewResource(R.layout.item_resolution_dropdown);
        if (mSpinnerGraphicsPreset != null) {
            mSpinnerGraphicsPreset.setAdapter(mGraphicsPresetAdapter);
        }
    }

    public static class RenderBackendItem {
        public final String title;
        public final String id;

        public RenderBackendItem(String title, String id) {
            this.title = title;
            this.id = id;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private void setupRenderBackendSpinner() {
        mRenderBackendList.clear();
        mRenderBackendList.add(new RenderBackendItem("Native OpenGL ES", "native"));
        mRenderBackendList.add(new RenderBackendItem("OpenGL ES -> Vulkan (ANGLE)", "angle_vulkan"));
        mRenderBackendList.add(new RenderBackendItem("OpenGL ES -> OpenGL ES (ANGLE)", "angle_gles"));

        mRenderBackendAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_resolution_spinner,
            mRenderBackendList
        );
        mRenderBackendAdapter.setDropDownViewResource(R.layout.item_resolution_dropdown);
        mSpinnerRenderBackend.setAdapter(mRenderBackendAdapter);

        mSpinnerRenderBackend.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateRenderBackendStatus();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private boolean isAngleAvailable() {
        try {
            String appLibDir = getApplicationInfo().nativeLibraryDir;
            if (appLibDir != null && new File(appLibDir, "libEGL_angle.so").exists()) {
                return true;
            }
        } catch (Exception ignored) {}
        String[] systemDirs = {
            "/apex/com.android.angle/lib64",
            "/system/lib64/egl",
            "/vendor/lib64/egl"
        };
        for (String dir : systemDirs) {
            if (new File(dir, "libEGL_angle.so").exists()) {
                return true;
            }
        }
        return false;
    }

    private void updateRenderBackendStatus() {
        if (mTextRenderBackendStatus == null || mSpinnerRenderBackend == null) return;
        Object selected = mSpinnerRenderBackend.getSelectedItem();
        if (selected instanceof RenderBackendItem) {
            RenderBackendItem item = (RenderBackendItem) selected;
            if ("angle_vulkan".equals(item.id) || "angle_gles".equals(item.id)) {
                mTextRenderBackendStatus.setVisibility(View.VISIBLE);
                if (isAngleAvailable()) {
                    mTextRenderBackendStatus.setText("✓ ANGLE libraries detected in APK");
                    mTextRenderBackendStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                } else {
                    mTextRenderBackendStatus.setText("⚠ ANGLE libraries not found in APK. Run scripts/build_angle.bat to build them (will fallback to Native).");
                    mTextRenderBackendStatus.setTextColor(ContextCompat.getColor(this, R.color.amber_primary));
                }
            } else {
                mTextRenderBackendStatus.setVisibility(View.GONE);
            }
        }
    }
}
