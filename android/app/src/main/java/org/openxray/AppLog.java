package org.openxray;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class AppLog {

    private static final String TAG = "OpenXRay";
    private static final String APP_LOG_NAME = "openxray_app.log";
    private static final String LOGCAT_LOG_NAME = "openxray_logcat.log";

    private static final Object sLock = new Object();
    private static BufferedWriter sAppLogWriter = null;
    private static File sCurrentLogDir = null;
    private static boolean sInitialized = false;

    private static Process sLogcatProcess = null;
    private static Thread sLogcatThread = null;
    private static volatile boolean sLogcatRunning = false;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    /**
     * Initializes the logging system.
     * Rotates existing logs to .bkp and starts the app log and logcat monitor.
     */
    public static void init(Context context, String customPath) {
        synchronized (sLock) {
            File logDir = resolveLogDirectory(context, customPath);
            if (logDir == null) {
                Log.e(TAG, "Failed to resolve any writable log directory");
                return;
            }

            // If already initialized for this same directory, do nothing
            if (sInitialized && sCurrentLogDir != null && sCurrentLogDir.equals(logDir)) {
                return;
            }

            sCurrentLogDir = logDir;

            try {
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }

                File appLogFile = new File(logDir, APP_LOG_NAME);
                if (appLogFile.exists() && appLogFile.length() > 0) {
                    File bkp = new File(logDir, APP_LOG_NAME + ".bkp");
                    if (bkp.exists()) {
                        bkp.delete();
                    }
                    appLogFile.renameTo(bkp);
                }

                if (sAppLogWriter != null) {
                    try {
                        sAppLogWriter.flush();
                        sAppLogWriter.close();
                    } catch (Exception ignored) {}
                }

                sAppLogWriter = new BufferedWriter(new FileWriter(appLogFile, false));
                sInitialized = true;

                installUncaughtExceptionHandler();
                writeSystemHeader(context, logDir);
                startLogcatCapture(logDir);

            } catch (Exception e) {
                Log.e(TAG, "Error initializing AppLog: " + e.getMessage(), e);
            }
        }
    }

    private static File resolveLogDirectory(Context context, String customPath) {
        if (customPath != null && !customPath.trim().isEmpty()) {
            File dir = new File(customPath.trim());
            if (dir.exists() || dir.mkdirs()) {
                return dir;
            }
        }

        File defaultDir = new File(Environment.getExternalStorageDirectory(), "OpenXRay");
        if (defaultDir.exists() || defaultDir.mkdirs()) {
            return defaultDir;
        }

        // Fallback to app external files dir if root storage not yet writable
        if (context != null) {
            File extFiles = context.getExternalFilesDir(null);
            if (extFiles != null && (extFiles.exists() || extFiles.mkdirs())) {
                return extFiles;
            }
            return context.getFilesDir();
        }

        return null;
    }

    private static void installUncaughtExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            e("CRASH", "FATAL UNCAUGHT EXCEPTION in thread [" + thread.getName() + "]: " + throwable.getMessage(), throwable);
            flush();
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private static void writeSystemHeader(Context context, File logDir) {
        StringBuilder sb = new StringBuilder();
        String sep = "================================================================================";
        String line = "--------------------------------------------------------------------------------";

        sb.append(sep).append("\n");
        sb.append("OpenXRay Application Diagnostics Log\n");
        sb.append("Generated at: ").append(DATE_FORMAT.format(new Date())).append("\n");

        if (context != null) {
            try {
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                sb.append("Application: ").append(context.getPackageName())
                  .append(" (v").append(pInfo.versionName)
                  .append(", code: ").append(pInfo.versionCode).append(")\n");
            } catch (Exception ignored) {}
        }

        sb.append(line).append("\n");
        sb.append("[DEVICE HARDWARE & OS]\n");
        sb.append("Manufacturer:   ").append(Build.MANUFACTURER).append("\n");
        sb.append("Brand:          ").append(Build.BRAND).append("\n");
        sb.append("Model:          ").append(Build.MODEL).append("\n");
        sb.append("Device:         ").append(Build.DEVICE).append("\n");
        sb.append("Product:        ").append(Build.PRODUCT).append("\n");
        sb.append("Hardware:       ").append(Build.HARDWARE).append("\n");
        sb.append("Board:          ").append(Build.BOARD).append("\n");
        sb.append("Android OS:     ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Supported ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
        sb.append("CPU Cores:      ").append(Runtime.getRuntime().availableProcessors()).append("\n");

        if (context != null) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(memInfo);
                    sb.append("Total RAM:      ").append(memInfo.totalMem / (1024 * 1024)).append(" MB\n");
                    sb.append("Available RAM:  ").append(memInfo.availMem / (1024 * 1024)).append(" MB\n");
                    sb.append("Low Memory:     ").append(memInfo.lowMemory).append("\n");

                    ConfigurationInfo configInfo = am.getDeviceConfigurationInfo();
                    if (configInfo != null) {
                        sb.append("OpenGL ES Ver:  ").append(configInfo.getGlEsVersion()).append("\n");
                    }
                }
            } catch (Exception ignored) {}
        }

        sb.append(line).append("\n");
        sb.append("[STORAGE & PERMISSIONS]\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasAllFiles = Environment.isExternalStorageManager();
            sb.append("Storage Access Mode:    All Files Access (Android 11+)\n");
            sb.append("Storage Permission:     ").append(hasAllFiles ? "GRANTED (Full Access)" : "DENIED (Action Required)").append("\n");
        } else if (context != null) {
            boolean readStorage = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            boolean writeStorage = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            sb.append("READ_EXTERNAL_STORAGE:  ").append(readStorage ? "GRANTED" : "DENIED").append("\n");
            sb.append("WRITE_EXTERNAL_STORAGE: ").append(writeStorage ? "GRANTED" : "DENIED").append("\n");
        }
        sb.append("Log Directory:          ").append(logDir.getAbsolutePath()).append("\n");
        sb.append("Log Directory Writable: ").append(logDir.canWrite()).append("\n");
        sb.append("Free Storage Space:     ").append(logDir.getFreeSpace() / (1024 * 1024)).append(" MB\n");

        sb.append(line).append("\n");
        sb.append("[GAME DIRECTORY AUDIT]\n");
        File fsgame = new File(logDir, "fsgame.ltx");
        sb.append("fsgame.ltx:             ").append(fsgame.exists() ? "FOUND (" + fsgame.length() + " bytes)" : "MISSING").append("\n");

        File gamedata = new File(logDir, "gamedata");
        sb.append("gamedata/ folder:       ").append(gamedata.exists() ? "FOUND" : "NOT FOUND").append("\n");

        File appdata = new File(logDir, "_appdata_");
        sb.append("_appdata_/ folder:      ").append(appdata.exists() ? "FOUND" : "NOT FOUND").append("\n");

        File[] dbFiles = logDir.listFiles((dir1, name) -> name.toLowerCase(Locale.US).startsWith("gamedata.db"));
        if (dbFiles != null && dbFiles.length > 0) {
            sb.append("Database Archives (").append(dbFiles.length).append(" found):\n");
            for (File db : dbFiles) {
                sb.append("  - ").append(db.getName()).append(" (").append(db.length() / (1024 * 1024)).append(" MB)\n");
            }
        } else {
            sb.append("Database Archives:      NONE FOUND in ").append(logDir.getAbsolutePath()).append("\n");
        }

        sb.append(sep).append("\n\n");

        writeRaw(sb.toString());
    }

    private static void startLogcatCapture(File logDir) {
        if (sLogcatRunning) {
            return;
        }

        File logcatFile = new File(logDir, LOGCAT_LOG_NAME);
        if (logcatFile.exists() && logcatFile.length() > 0) {
            File bkp = new File(logDir, LOGCAT_LOG_NAME + ".bkp");
            if (bkp.exists()) {
                bkp.delete();
            }
            logcatFile.renameTo(bkp);
        }

        sLogcatRunning = true;
        sLogcatThread = new Thread(() -> {
            BufferedWriter writer = null;
            BufferedReader reader = null;
            try {
                writer = new BufferedWriter(new FileWriter(logcatFile, false));
                writer.write("=== OpenXRay Real-Time Logcat Capture Started: " + DATE_FORMAT.format(new Date()) + " ===\n\n");
                writer.flush();

                int myPid = android.os.Process.myPid();
                ProcessBuilder pb;
                try {
                    // Filter by PID to capture all app & native output
                    pb = new ProcessBuilder("logcat", "-v", "threadtime", "--pid=" + myPid);
                    sLogcatProcess = pb.start();
                } catch (Exception e) {
                    // Fallback to tag-based if --pid fails
                    pb = new ProcessBuilder("logcat", "-v", "threadtime");
                    sLogcatProcess = pb.start();
                }

                reader = new BufferedReader(new InputStreamReader(sLogcatProcess.getInputStream()));
                String line;
                while (sLogcatRunning && (line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            } catch (Exception e) {
                Log.w(TAG, "Logcat capture thread exited: " + e.getMessage());
            } finally {
                if (writer != null) {
                    try {
                        writer.flush();
                        writer.close();
                    } catch (Exception ignored) {}
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {}
                }
            }
        }, "LogcatCaptureThread");
        sLogcatThread.setDaemon(true);
        sLogcatThread.start();
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        writeFormatted("INFO", tag, message, null);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        writeFormatted("WARN", tag, message, null);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        writeFormatted("ERROR", tag, message, null);
    }

    public static void e(String tag, String message, Throwable t) {
        Log.e(tag, message, t);
        writeFormatted("ERROR", tag, message, t);
    }

    private static void writeFormatted(String level, String tag, String message, Throwable t) {
        String timestamp = DATE_FORMAT.format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp).append("] [").append(level).append("] [").append(tag).append("] ")
          .append(message).append("\n");

        if (t != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            sb.append(sw.toString()).append("\n");
        }

        writeRaw(sb.toString());
    }

    private static void writeRaw(String text) {
        synchronized (sLock) {
            if (sAppLogWriter != null) {
                try {
                    sAppLogWriter.write(text);
                    sAppLogWriter.flush();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void flush() {
        synchronized (sLock) {
            if (sAppLogWriter != null) {
                try {
                    sAppLogWriter.flush();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void close() {
        sLogcatRunning = false;
        if (sLogcatProcess != null) {
            try {
                sLogcatProcess.destroy();
            } catch (Exception ignored) {}
        }
        flush();
        synchronized (sLock) {
            if (sAppLogWriter != null) {
                try {
                    sAppLogWriter.close();
                } catch (Exception ignored) {}
                sAppLogWriter = null;
            }
            sInitialized = false;
        }
    }

    public static File getAppLogFile() {
        if (sCurrentLogDir != null) {
            return new File(sCurrentLogDir, APP_LOG_NAME);
        }
        return null;
    }

    public static File getLogcatFile() {
        if (sCurrentLogDir != null) {
            return new File(sCurrentLogDir, LOGCAT_LOG_NAME);
        }
        return null;
    }
}
