package com.sangluo.onestep.feature.logging;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Records the current app session and exports it to the public Download directory. */
public final class SessionLogRecorder {
    private static final String TAG = "OneStep40";
    private static final String LOG_DIRECTORY_NAME = "session_logs";

    private static final List<String> LOGCAT_PREFIX_ARGUMENTS = Arrays.asList(
            "-b", "main",
            "-b", "system",
            "-b", "crash",
            "-v", "threadtime"
    );
    private static final List<String> LOGCAT_FILTER_ARGUMENTS = Arrays.asList(
            "OneStep40:V",
            "AndroidRuntime:E",
            "ActivityTaskManager:I",
            "ActivityManager:I",
            "ActivityTaskSupervisor:I",
            "TaskOrganizer:I",
            "WindowManager:I",
            "WindowManagerShell:I",
            "DisplayManager:I",
            "DisplayManagerService:I",
            "VirtualDisplayAdapter:I",
            "InputDispatcher:I",
            "InputMethodManager:I",
            "InputMethodManagerService:I",
            "*:W"
    );

    private final Context context;
    private final Object stateLock = new Object();
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OneStep-LogExport");
        thread.setDaemon(true);
        return thread;
    });

    private File sessionFile;
    private Process captureProcess;
    private Thread captureOutputThread;
    private long sessionStartedAtMillis;
    private boolean started;
    private volatile boolean closed;
    private boolean exportInProgress;

    public SessionLogRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        synchronized (stateLock) {
            if (started || closed) {
                return;
            }
            sessionStartedAtMillis = System.currentTimeMillis();
            File logDirectory = new File(context.getCacheDir(), LOG_DIRECTORY_NAME);
            if (!logDirectory.exists() && !logDirectory.mkdirs()) {
                Log.w(TAG, "Unable to create session log directory: " + logDirectory);
            }
            sessionFile = new File(logDirectory,
                    "current-session-" + sessionStartedAtMillis + ".txt");
            if (sessionFile.exists() && !sessionFile.delete()) {
                Log.w(TAG, "Unable to clear previous session log: " + sessionFile);
            }
            appendMarkerLocked(buildSessionHeader());
            startCaptureLocked(sessionStartedAtMillis);
            started = true;
        }
    }

    public boolean export(ExportCallback callback) {
        long exportRequestedAtMillis;
        synchronized (stateLock) {
            if (!started || closed || sessionFile == null) {
                return false;
            }
            if (exportInProgress) {
                return false;
            }
            exportInProgress = true;
            exportRequestedAtMillis = System.currentTimeMillis();
            if (captureProcess != null) {
                captureProcess.destroy();
            }
        }

        try {
            exportExecutor.execute(() -> {
                ExportResult result;
                try {
                    synchronized (stateLock) {
                        stopCaptureLocked();
                        appendMarkerLocked("\nexported_at="
                                + formatReadableTimestamp(exportRequestedAtMillis) + "\n");
                        result = exportToDownloadsLocked(exportRequestedAtMillis);
                        if (!closed) {
                            startCaptureLocked(System.currentTimeMillis());
                        }
                        exportInProgress = false;
                    }
                } catch (RuntimeException error) {
                    Log.e(TAG, "Unexpected session log export failure", error);
                    synchronized (stateLock) {
                        exportInProgress = false;
                    }
                    result = ExportResult.failure(error.getMessage());
                }
                if (callback != null) {
                    callback.onComplete(result);
                }
            });
        } catch (RuntimeException error) {
            synchronized (stateLock) {
                exportInProgress = false;
            }
            Log.e(TAG, "Unable to schedule session log export", error);
            return false;
        }
        return true;
    }

    public void close() {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            closed = true;
            stopCaptureLocked();
            appendMarkerLocked("\nsession_ended_at="
                    + formatReadableTimestamp(System.currentTimeMillis()) + "\n");
        }
        exportExecutor.shutdownNow();
    }

    private void startCaptureLocked(long captureFromMillis) {
        if (sessionFile == null || closed) {
            return;
        }
        List<String> logcatArguments = createLogcatArguments(captureFromMillis);
        ProcessBuilder processBuilder;
        if (context.checkSelfPermission(Manifest.permission.READ_LOGS)
                == PackageManager.PERMISSION_GRANTED) {
            List<String> command = new ArrayList<>();
            command.add("/system/bin/logcat");
            command.addAll(logcatArguments);
            processBuilder = new ProcessBuilder(command);
        } else {
            processBuilder = new ProcessBuilder("su", "-c",
                    buildRootLogcatCommand(logcatArguments));
        }
        processBuilder.redirectErrorStream(true);
        try {
            captureProcess = processBuilder.start();
            startOutputPumpLocked(captureProcess);
            Log.i(TAG, "Session log capture started; fullReadLogs="
                    + (context.checkSelfPermission(Manifest.permission.READ_LOGS)
                    == PackageManager.PERMISSION_GRANTED));
        } catch (IOException | SecurityException primaryError) {
            Log.w(TAG, "Primary session log capture failed, using app-visible logcat", primaryError);
            List<String> fallbackCommand = new ArrayList<>();
            fallbackCommand.add("/system/bin/logcat");
            fallbackCommand.addAll(logcatArguments);
            ProcessBuilder fallbackBuilder = new ProcessBuilder(fallbackCommand);
            fallbackBuilder.redirectErrorStream(true);
            try {
                captureProcess = fallbackBuilder.start();
                startOutputPumpLocked(captureProcess);
            } catch (IOException | SecurityException fallbackError) {
                captureProcess = null;
                appendMarkerLocked("log_capture_error=" + fallbackError + "\n");
                Log.e(TAG, "Unable to start session log capture", fallbackError);
            }
        }
    }

    private List<String> createLogcatArguments(long captureFromMillis) {
        List<String> arguments = new ArrayList<>(LOGCAT_PREFIX_ARGUMENTS);
        arguments.add("-T");
        arguments.add(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date(captureFromMillis)));
        arguments.addAll(LOGCAT_FILTER_ARGUMENTS);
        return arguments;
    }

    private String buildRootLogcatCommand(List<String> logcatArguments) {
        StringBuilder command = new StringBuilder("exec /system/bin/logcat");
        for (String argument : logcatArguments) {
            command.append(' ').append(shellQuote(argument));
        }
        return command.toString();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void startOutputPumpLocked(Process process) {
        File outputFile = sessionFile;
        Thread outputThread = new Thread(() -> {
            try (InputStream inputStream = process.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(outputFile, true)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    outputStream.flush();
                }
            } catch (IOException error) {
                if (!closed) {
                    Log.w(TAG, "Session log output pump stopped unexpectedly", error);
                }
            }
        }, "OneStep-LogCapture");
        outputThread.setDaemon(true);
        captureOutputThread = outputThread;
        outputThread.start();
    }

    private void stopCaptureLocked() {
        Process process = captureProcess;
        Thread outputThread = captureOutputThread;
        captureProcess = null;
        captureOutputThread = null;
        if (process == null) {
            return;
        }
        process.destroy();
        long deadlineNanos = System.nanoTime() + 750_000_000L;
        while (System.nanoTime() < deadlineNanos) {
            try {
                process.exitValue();
                break;
            } catch (IllegalThreadStateException stillRunning) {
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (outputThread != null) {
            try {
                outputThread.join(750L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ExportResult exportToDownloadsLocked(long exportedAtMillis) {
        String fileName = createExportFileName(exportedAtMillis);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return exportWithMediaStore(fileName);
        }
        return exportToLegacyDownloads(fileName);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private ExportResult exportWithMediaStore(String fileName) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri outputUri = null;
        try {
            outputUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (outputUri == null) {
                return ExportResult.failure("无法创建下载文件");
            }
            try (OutputStream outputStream = resolver.openOutputStream(outputUri, "w")) {
                if (outputStream == null) {
                    throw new IOException("ContentResolver returned a null output stream");
                }
                copySessionLog(outputStream);
            }
            ContentValues completedValues = new ContentValues();
            completedValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(outputUri, completedValues, null, null);
            return ExportResult.success(fileName);
        } catch (Exception error) {
            if (outputUri != null) {
                try {
                    resolver.delete(outputUri, null, null);
                } catch (RuntimeException cleanupError) {
                    Log.w(TAG, "Unable to remove incomplete session log export", cleanupError);
                }
            }
            Log.e(TAG, "Failed to export session log with MediaStore", error);
            return ExportResult.failure(error.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private ExportResult exportToLegacyDownloads(String fileName) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return ExportResult.failure("未获得存储权限");
        }
        File downloadDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDirectory.exists() && !downloadDirectory.mkdirs()) {
            return ExportResult.failure("无法创建 Download 目录");
        }
        File destination = new File(downloadDirectory, fileName);
        try (OutputStream outputStream = new FileOutputStream(destination)) {
            copySessionLog(outputStream);
            return ExportResult.success(fileName);
        } catch (Exception error) {
            Log.e(TAG, "Failed to export session log to legacy Downloads", error);
            return ExportResult.failure(error.getMessage());
        }
    }

    private void copySessionLog(OutputStream outputStream) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(sessionFile)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private String buildSessionHeader() {
        return "OneStep4 session log\n"
                + "session_started_at=" + formatReadableTimestamp(sessionStartedAtMillis) + "\n"
                + "app_version=" + readAppVersion() + "\n"
                + "device=" + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "android=" + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "----------------------------------------\n";
    }

    private String readAppVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
            return packageInfo.versionName + " (" + versionCode + ")";
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }

    private void appendMarkerLocked(String text) {
        if (sessionFile == null) {
            return;
        }
        try (FileOutputStream outputStream = new FileOutputStream(sessionFile, true)) {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException error) {
            Log.e(TAG, "Unable to append session log marker", error);
        }
    }

    public static String createExportFileName(long timestampMillis) {
        return "OneStep4-log-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
                .format(new Date(timestampMillis))
                + ".txt";
    }

    private static String formatReadableTimestamp(long timestampMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
                .format(new Date(timestampMillis));
    }

    public interface ExportCallback {
        void onComplete(ExportResult result);
    }

    public static final class ExportResult {
        private final boolean successful;
        private final String fileName;
        private final String errorMessage;

        private ExportResult(boolean successful, String fileName, String errorMessage) {
            this.successful = successful;
            this.fileName = fileName;
            this.errorMessage = errorMessage;
        }

        public static ExportResult success(String fileName) {
            return new ExportResult(true, fileName, null);
        }

        public static ExportResult failure(String errorMessage) {
            String message = errorMessage == null || errorMessage.trim().isEmpty()
                    ? "unknown error"
                    : errorMessage;
            return new ExportResult(false, null, message);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getFileName() {
            return fileName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
