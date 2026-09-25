package org.openxray;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.Arrays;

public class OpenXRayActivity extends SDLActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private TouchOverlayView mTouchOverlay;

    private final Object mInitLock = new Object();
    private boolean mGameDataReady = false;
    private boolean mInitStarted = false;
    private View mLoadingOverlay;

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "c++_shared",
            "SDL2",
            "openal",
            "xrCore",
            "xrAPI",
            "xrEngine",
            "xrRender_GL",
            "xrGame",
            "main"
        };
    }

    @Override
    public void loadLibraries() {
        Intent intent = getIntent();
        String backend = (intent != null && intent.hasExtra("extra_render_backend"))
            ? intent.getStringExtra("extra_render_backend")
            : "native";

        if ("angle_vulkan".equalsIgnoreCase(backend) || "angle_gles".equalsIgnoreCase(backend)) {
            AppLog.i("Libraries", "Preloading ANGLE native libraries for " + backend + "...");
            try {
                org.libsdl.app.SDL.loadLibrary("EGL_angle", this);
                AppLog.i("Libraries", "  -> Preloaded libEGL_angle.so successfully");
            } catch (Throwable t) {
                AppLog.w("Libraries", "  -> Note: libEGL_angle.so preload: " + t.getMessage());
            }
            try {
                org.libsdl.app.SDL.loadLibrary("GLESv2_angle", this);
                AppLog.i("Libraries", "  -> Preloaded libGLESv2_angle.so successfully");
            } catch (Throwable t) {
                AppLog.w("Libraries", "  -> Note: libGLESv2_angle.so preload: " + t.getMessage());
            }
        }

        String[] libs = getLibraries();
        AppLog.i("Libraries", "Starting native library loading (" + libs.length + " libraries)...");

        for (int i = 0; i < libs.length; i++) {
            String lib = libs[i];
            AppLog.i("Libraries", "[" + (i + 1) + "/" + libs.length + "] Loading library: " + lib);
            try {
                org.libsdl.app.SDL.loadLibrary(lib, this);
                AppLog.i("Libraries", "  -> SUCCESS: " + lib);
            } catch (UnsatisfiedLinkError e) {
                AppLog.e("Libraries", "  -> FATAL: UnsatisfiedLinkError loading " + lib + ": " + e.getMessage(), e);
                AppLog.flush();
                throw e;
            } catch (Throwable t) {
                AppLog.e("Libraries", "  -> FATAL: Error loading " + lib + ": " + t.getMessage(), t);
                AppLog.flush();
                throw new RuntimeException("Failed to load native library: " + lib, t);
            }
        }
        AppLog.i("Libraries", "All native libraries loaded successfully.");
    }

    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("extra_args")) {
            String argsStr = intent.getStringExtra("extra_args");
            if (argsStr != null && !argsStr.trim().isEmpty()) {
                java.util.List<String> list = new java.util.ArrayList<>();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("([^\"\\s]+|\"[^\"]*\")").matcher(argsStr.trim());
                while (m.find()) {
                    String match = m.group(1);
                    if (match.startsWith("\"") && match.endsWith("\"") && match.length() >= 2) {
                        match = match.substring(1, match.length() - 1);
                    }
                    list.add(match);
                }
                if (!list.isEmpty()) {
                    return list.toArray(new String[0]);
                }
            }
        }
        return new String[] {
            "-soc",
            "-rgl",
            "-force_flushlog"
        };
    }

    @Override
    protected org.libsdl.app.SDLSurface createSDLSurface(android.content.Context context) {
        org.libsdl.app.SDLSurface surface = super.createSDLSurface(context);
        Intent intent = getIntent();
        int targetW = (intent != null) ? intent.getIntExtra("extra_res_width", 0) : 0;
        int targetH = (intent != null) ? intent.getIntExtra("extra_res_height", 0) : 0;
        if (targetW > 0 && targetH > 0) {
            AppLog.i("Video", "Setting fixed surface resolution from launcher: " + targetW + "x" + targetH);
            surface.setFixedResolution(targetW, targetH);
        }
        return surface;
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        String path = (intent != null && intent.hasExtra("extra_game_path")) 
            ? intent.getStringExtra("extra_game_path") 
            : null;
        AppLog.init(this, path);
        AppLog.i("Activity", "OpenXRayActivity onCreate started");

        String[] args = getArguments();
        AppLog.i("Activity", "Launch arguments: " + Arrays.toString(args));

        String renderBackend = (intent != null && intent.hasExtra("extra_render_backend"))
            ? intent.getStringExtra("extra_render_backend")
            : "native";
        AppLog.i("Activity", "Selected render backend: " + renderBackend);
        configureRenderBackend(renderBackend);

        super.onCreate(savedInstanceState);

        hideSystemUI();
        checkAndRequestPermissions();
        startGameDirectoriesAsync();
        setupTouchControls();

        AppLog.i("Activity", "OpenXRayActivity onCreate finished successfully");
    }

    private String findNativeLib(String libName) {
        try {
            String appLibDir = getApplicationInfo().nativeLibraryDir;
            if (appLibDir != null) {
                File f = new File(appLibDir, libName);
                if (f.exists()) {
                    AppLog.i("RenderBackend", "Found " + libName + " in app nativeLibraryDir: " + f.getAbsolutePath());
                    return f.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}

        String[] systemDirs = {
            "/apex/com.android.angle/lib64",
            "/system/lib64/egl",
            "/vendor/lib64/egl"
        };
        for (String dir : systemDirs) {
            File f = new File(dir, libName);
            if (f.exists()) {
                AppLog.i("RenderBackend", "Found " + libName + " in system path: " + f.getAbsolutePath());
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    private void configureRenderBackend(String backend) {
        if ("angle_vulkan".equalsIgnoreCase(backend) || "angle_gles".equalsIgnoreCase(backend)) {
            // NOTE: valid ANGLE_DEFAULT_PLATFORM values are vulkan/gl/d3d11/metal/null.
            // On Android, "gl" selects the native OpenGL ES backend (EGL_PLATFORM_ANGLE_TYPE_OPENGLES_ANGLE).
            String platform = "angle_vulkan".equalsIgnoreCase(backend) ? "vulkan" : "gl";
            AppLog.i("RenderBackend", ">>> Configuring ANGLE (" + platform + " backend) <<<");

            String eglPath = findNativeLib("libEGL_angle.so");
            String glesPath = findNativeLib("libGLESv2_angle.so");

            if (eglPath != null && glesPath != null) {
                try {
                    android.system.Os.setenv("SDL_VIDEO_EGL_DRIVER", eglPath, true);
                    android.system.Os.setenv("SDL_VIDEO_GL_DRIVER", glesPath, true);
                    android.system.Os.setenv("ANGLE_DEFAULT_PLATFORM", platform, true);
                    AppLog.i("RenderBackend", "Environment configured for ANGLE: EGL=" + eglPath + ", GLES=" + glesPath + ", PLATFORM=" + platform);
                } catch (Exception e) {
                    AppLog.e("RenderBackend", "Failed to set environment variables for ANGLE: " + e.getMessage(), e);
                }
            } else {
                AppLog.e("RenderBackend", "ANGLE libraries (libEGL_angle.so / libGLESv2_angle.so) not found in APK or system! Falling back to Native OpenGLES.");
                runOnUiThread(() -> Toast.makeText(this, "ANGLE libraries not found in APK. Using Native OpenGLES.", Toast.LENGTH_LONG).show());
                try {
                    android.system.Os.unsetenv("SDL_VIDEO_EGL_DRIVER");
                    android.system.Os.unsetenv("SDL_VIDEO_GL_DRIVER");
                    android.system.Os.unsetenv("ANGLE_DEFAULT_PLATFORM");
                } catch (Exception ignored) {}
            }
        } else {
            AppLog.i("RenderBackend", ">>> Using Native OpenGLES backend (system driver) <<<");
            try {
                android.system.Os.unsetenv("SDL_VIDEO_EGL_DRIVER");
                android.system.Os.unsetenv("SDL_VIDEO_GL_DRIVER");
                android.system.Os.unsetenv("ANGLE_DEFAULT_PLATFORM");
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        AppLog.i("Activity", "OpenXRayActivity onResume");
        super.onResume();
        hideSystemUI();
    }

    public void hideSystemUI() {
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
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        setWindowStyle(true);
    }

    @Override
    protected void onPause() {
        AppLog.i("Activity", "OpenXRayActivity onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        AppLog.i("Activity", "OpenXRayActivity onDestroy");
        super.onDestroy();
        AppLog.close();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AppLog.w("Permissions", "MANAGE_EXTERNAL_STORAGE not granted, opening settings...");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            } else {
                AppLog.i("Permissions", "MANAGE_EXTERNAL_STORAGE is granted.");
            }
        } else {
            // Android 7.0 (API 24) to Android 10
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                AppLog.w("Permissions", "Requesting READ/WRITE_EXTERNAL_STORAGE permissions...");
                ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
            } else {
                AppLog.i("Permissions", "WRITE_EXTERNAL_STORAGE is granted.");
            }
        }
    }

    @Override
    protected boolean isNativeStartReady() {
        synchronized (mInitLock) {
            return mGameDataReady;
        }
    }

    // Runs the first-run setup (asset extraction etc.) off the main thread so
    // the UI stays responsive. On OEM ROMs with aggressive watchdog/ANR
    // monitoring (e.g. MIUI) blocking the main thread for several seconds in
    // onCreate() was reported as a hang and the app got killed.
    private void startGameDirectoriesAsync() {
        synchronized (mInitLock) {
            if (mInitStarted) {
                return;
            }
            mInitStarted = true;
        }
        showLoadingOverlay();
        AppLog.i("Activity", "Preparing game data on background thread...");
        new Thread(() -> {
            try {
                createGameDirectories();
            } finally {
                synchronized (mInitLock) {
                    mGameDataReady = true;
                }
                runOnUiThread(this::completeGameInit);
            }
        }, "GameDataInitThread").start();
    }

    private void completeGameInit() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        hideLoadingOverlay();
        AppLog.i("Activity", "Game data ready, starting engine.");
        SDLActivity.handleNativeState();
    }

    private void showLoadingOverlay() {
        if (mLoadingOverlay != null || mLayout == null) {
            return;
        }
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xFF000000);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);

        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        content.addView(spinner, spinnerLp);

        TextView text = new TextView(this);
        text.setText("Preparing game data...");
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(16);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        content.addView(text, textLp);

        overlay.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mLayout.addView(overlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
        mLoadingOverlay = overlay;
    }

    private void hideLoadingOverlay() {
        if (mLoadingOverlay != null) {
            ViewGroup parent = (ViewGroup) mLoadingOverlay.getParent();
            if (parent != null) {
                parent.removeView(mLoadingOverlay);
            }
            mLoadingOverlay = null;
        }
    }

    private void createGameDirectories() {
        try {
            Intent intent = getIntent();
            String path = (intent != null && intent.hasExtra("extra_game_path")) 
                ? intent.getStringExtra("extra_game_path") 
                : null;
            File dir = (path != null && !path.trim().isEmpty())
                ? new File(path.trim())
                : new File(Environment.getExternalStorageDirectory(), "OpenXRay");

            AppLog.i("FileSystem", "Target game directory: " + dir.getAbsolutePath());
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                AppLog.i("FileSystem", "Created target directory: " + created);
            }
            // Game mode selected in launcher: soc / cs / cop.
            String gameMode = "soc";
            try {
                Intent modeIntent = getIntent();
                if (modeIntent != null && modeIntent.hasExtra("extra_game_mode")) {
                    String m = modeIntent.getStringExtra("extra_game_mode");
                    if ("cs".equals(m) || "cop".equals(m) || "soc".equals(m)) {
                        gameMode = m;
                    }
                }
            } catch (Exception ignored) {}
            AppLog.i("FileSystem", "Game mode for res overlay: " + gameMode);

            // res/ is split per game: res/soc/* for Shadow of Chernobyl,
            // res/cop/* for Call of Pripyat (Clear Sky reuses it until it
            // gets its own overlay).
            String resBase = "cop";
            if ("soc".equals(gameMode)) {
                resBase = "soc";
            }
            AppLog.i("FileSystem", "Res source folder: " + resBase);

            String[] gamedataAssets = null;
            try {
                gamedataAssets = getAssets().list(resBase + "/gamedata");
            } catch (Exception e) {
                AppLog.w("FileSystem", "Res folder missing in APK: " + resBase);
            }
            if (gamedataAssets != null && gamedataAssets.length > 0) {
                AppLog.i("FileSystem", "Extracting assets to: " + new File(dir, "gamedata").getAbsolutePath());
                extractAssetFolder(resBase + "/gamedata", new File(dir, "gamedata"));
                // Engine fallback reads game_mode from openxray.ltx — keep it
                // in sync with the selected mode (flags -soc/-cs/-cop win anyway).
                // SoC keeps configs in gamedata/config, CS/CoP in gamedata/configs.
                patchGameMode(dir, gameMode);
            } else {
                AppLog.i("FileSystem", "No gamedata in APK assets, using external gamedata.");
            }
            File targetFsgame = new File(dir, "fsgame.ltx");
            installFsgameConfig(resBase, targetFsgame);
            AppLog.i("FileSystem", "Asset check finished.");
        } catch (Exception e) {
            AppLog.e("FileSystem", "Error creating game directories / extracting assets: " + e.getMessage(), e);
        }
    }

    // The engine mounts game archives (.db) only for the directories listed in
    // fsgame.ltx. A stale or retail fsgame.ltx has no $arch_dir* entries, so the
    // engine ends up with "0 archives" and exits with "Cannot find file
    // system.ltx" while every log still looks healthy. Always install the
    // bundled config, keeping one backup of a previously used file.
    private void installFsgameConfig(String resBase, File targetFsgame) {
        try {
            byte[] bundled;
            try (java.io.InputStream in = getAssets().open(resBase + "/fsgame.ltx")) {
                bundled = readAll(in);
            }
            if (bundled == null || bundled.length == 0) {
                AppLog.w("FileSystem", "Bundled fsgame.ltx missing for " + resBase + ", keeping the existing one.");
                return;
            }

            if (targetFsgame.exists()) {
                byte[] current = readFile(targetFsgame);
                if (Arrays.equals(bundled, current)) {
                    AppLog.i("FileSystem", "fsgame.ltx is up to date (" + bundled.length + " bytes).");
                    return;
                }
                File backup = new File(targetFsgame.getParentFile(), "fsgame.ltx.user.bak");
                if (!backup.exists()) {
                    try (java.io.OutputStream out = new java.io.FileOutputStream(backup)) {
                        out.write(current);
                    }
                    AppLog.i("FileSystem", "Previous fsgame.ltx (" + (current == null ? 0 : current.length)
                            + " bytes) backed up as fsgame.ltx.user.bak");
                }
            }

            try (java.io.OutputStream out = new java.io.FileOutputStream(targetFsgame)) {
                out.write(bundled);
            }
            String text = new String(bundled, java.nio.charset.StandardCharsets.UTF_8);
            if (!text.contains("$arch_dir$")) {
                AppLog.w("FileSystem", "Bundled fsgame.ltx has no $arch_dir$ entry, game archives may not load.");
            }
            AppLog.i("FileSystem", "Installed bundled fsgame.ltx (" + bundled.length + " bytes).");
        } catch (Exception e) {
            AppLog.e("FileSystem", "Could not install fsgame.ltx: " + e.getMessage(), e);
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        if (in == null) {
            return null;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static byte[] readFile(File file) {
        if (!file.exists()) {
            return null;
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    private void extractAssetFolder(String assetPath, File targetDir) {
        try {
            String[] files = getAssets().list(assetPath);
            if (files == null || files.length == 0) {
                if (!targetDir.getParentFile().exists()) {
                    targetDir.getParentFile().mkdirs();
                }
                boolean shouldExtract = !targetDir.exists() || targetDir.length() == 0 
                        || targetDir.getName().endsWith(".script")
                        || targetDir.getName().endsWith(".vs")
                        || targetDir.getName().endsWith(".ps")
                        || targetDir.getName().endsWith(".s")
                        || targetDir.getName().endsWith(".h");
                if (shouldExtract) {
                    try (java.io.InputStream in = getAssets().open(assetPath);
                         java.io.OutputStream out = new java.io.FileOutputStream(targetDir)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            } else {
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                for (String file : files) {
                    extractAssetFolder(assetPath + "/" + file, new File(targetDir, file));
                }
            }
        } catch (Exception e) {
            AppLog.e("FileSystem", "extractAssetFolder error for " + assetPath + ": " + e.getMessage(), e);
        }
    }

    // Keeps [compatibility] game_mode in the extracted openxray.ltx matched
    // with the launcher-selected game (soc/cs/cop).
    private void patchGameMode(File gameDir, String gameMode) {
        try {
            // SoC: gamedata/config, Clear Sky / Call of Pripyat: gamedata/configs.
            String configName = "soc".equals(gameMode) ? "config" : "configs";
            File configDir = new File(new File(gameDir, "gamedata"), configName);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            File cfg = new File(configDir, "openxray.ltx");
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (cfg.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cfg))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }
            boolean inCompat = false;
            boolean compatFound = false;
            boolean modeSet = false;
            int compatHeaderIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    inCompat = "[compatibility]".equalsIgnoreCase(trimmed);
                    if (inCompat) {
                        compatFound = true;
                        compatHeaderIndex = i;
                    }
                } else if (inCompat && trimmed.startsWith("game_mode")) {
                    lines.set(i, "game_mode = " + gameMode);
                    modeSet = true;
                }
            }
            if (!compatFound) {
                lines.add("[compatibility]");
                lines.add("game_mode = " + gameMode);
            } else if (!modeSet && compatHeaderIndex >= 0) {
                lines.add(compatHeaderIndex + 1, "game_mode = " + gameMode);
            }
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(cfg))) {
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            }
            AppLog.i("FileSystem", "Patched openxray.ltx game_mode=" + gameMode);
        } catch (Exception e) {
            AppLog.e("FileSystem", "patchGameMode failed: " + e.getMessage(), e);
        }
    }

    private void setupTouchControls() {
        if (mLayout == null) {
            AppLog.w("TouchControls", "mLayout is null, cannot setup touch overlay");
            return;
        }

        mTouchOverlay = new TouchOverlayView(this);

        Intent intent = getIntent();
        if (intent != null) {
            float opacity = intent.getFloatExtra("extra_touch_opacity", 0.6f);
            float scale = intent.getFloatExtra("extra_touch_scale", 1.0f);
            float sens = intent.getFloatExtra("extra_look_sensitivity", 1.0f);

            AppLog.i("TouchControls", "Touch controls params: opacity=" + opacity + ", scale=" + scale + ", sens=" + sens);
            // Кнопка детектора артефактов — только CoP и Clear Sky.
            String mode = intent.getStringExtra("extra_game_mode");
            boolean detector = "cop".equals(mode) || "cs".equals(mode);
            AppLog.i("TouchControls", "Detector button enabled: " + detector + " (mode=" + mode + ")");
            mTouchOverlay.setDetectorEnabled(detector);
            mTouchOverlay.setOverlayAlpha(opacity);
            mTouchOverlay.setControlsScale(scale);
            mTouchOverlay.setLookSensitivity(sens);
        }

        android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        mLayout.addView(mTouchOverlay, params);
        AppLog.i("TouchControls", "Touch overlay added to layout.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AppLog.i("Permissions", "Storage permission granted by user.");
                startGameDirectoriesAsync();
            } else {
                AppLog.w("Permissions", "Storage permission DENIED by user.");
                Toast.makeText(this, "Storage permission is required to load S.T.A.L.K.E.R. game files", Toast.LENGTH_LONG).show();
            }
        }
    }
}
