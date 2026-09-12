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
        createGameDirectories();
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
            String[] gamedataAssets = getAssets().list("gamedata");
            if (gamedataAssets != null && gamedataAssets.length > 0) {
                AppLog.i("FileSystem", "Extracting assets to: " + new File(dir, "gamedata").getAbsolutePath());
                extractAssetFolder("gamedata", new File(dir, "gamedata"));
            } else {
                AppLog.i("FileSystem", "No gamedata in APK assets, using external gamedata.");
            }
            File targetFsgame = new File(dir, "fsgame.ltx");
            if (!targetFsgame.exists()) {
                try {
                    extractAssetFolder("fsgame.ltx", targetFsgame);
                } catch (Exception ignored) {}
            }
            AppLog.i("FileSystem", "Asset check finished.");
        } catch (Exception e) {
            AppLog.e("FileSystem", "Error creating game directories / extracting assets: " + e.getMessage(), e);
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
                createGameDirectories();
            } else {
                AppLog.w("Permissions", "Storage permission DENIED by user.");
                Toast.makeText(this, "Storage permission is required to load S.T.A.L.K.E.R. game files", Toast.LENGTH_LONG).show();
            }
        }
    }
}
