package com.sangluo.onestep.feature.embedding;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** Persists the generation used to reject stale asynchronous embedded-app starts. */
public final class EmbeddedStartEpochStore {
    private static final String TAG = "OneStep40";
    private static final String STATE_DIRECTORY = "root-bridge/state";
    private static final String EPOCH_FILE = "embedded-start-epoch.txt";

    private final File stateFile;

    public EmbeddedStartEpochStore(Context context) {
        this(new File(context.getFilesDir(), STATE_DIRECTORY));
    }

    EmbeddedStartEpochStore(File stateDirectory) {
        stateFile = new File(stateDirectory, EPOCH_FILE);
    }

    public int beginSession() {
        int epoch = Math.max(1, read() + 1);
        persist(epoch);
        return epoch;
    }

    public void persist(int epoch) {
        File parentDirectory = stateFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.exists()
                && !parentDirectory.mkdirs()) {
            Log.w(TAG, "Create embedded bridge state dir failed");
            return;
        }
        File tempFile = new File(parentDirectory, stateFile.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(Integer.toString(epoch));
            writer.write('\n');
        } catch (IOException e) {
            Log.w(TAG, "Write embedded start epoch failed: " + e.getClass().getSimpleName());
            return;
        }
        if (stateFile.exists() && !stateFile.delete()) {
            Log.w(TAG, "Delete old embedded start epoch failed");
        }
        if (!tempFile.renameTo(stateFile)) {
            Log.w(TAG, "Persist embedded start epoch failed: rename");
        }
    }

    public String getFilePath() {
        return stateFile.getAbsolutePath();
    }

    private int read() {
        if (!stateFile.exists()) {
            return 0;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(stateFile))) {
            String value = reader.readLine();
            return value == null || value.trim().isEmpty() ? 0 : Integer.parseInt(value.trim());
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "Read embedded start epoch failed: " + e.getClass().getSimpleName());
            return 0;
        }
    }
}
