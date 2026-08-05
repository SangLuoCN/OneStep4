package com.sangluo.onestep.system.input;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.sangluo.onestep.MotionEventCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/** Sends hosted navigation events directly to MIUI Home's native gesture state machine. */
public final class MiuiGestureBridgeClient implements Closeable {
    public static final String SOCKET_NAME = "onestep_miui_gesture_bridge";

    private static final String TAG = "OneStep40";
    private static final int RESPONSE_TIMEOUT_MS = 1000;
    private static final long FAILURE_LOG_THROTTLE_MS = 2000L;

    private LocalSocket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private long lastFailureLogUptime;

    public synchronized boolean sendMotion(MotionEvent event) {
        if (event == null) {
            return false;
        }
        String command;
        try {
            command = "motion " + MotionEventCodec.encode(event);
        } catch (RuntimeException e) {
            logFailure("encode failed", e);
            return false;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (!ensureConnected()) {
                    continue;
                }
                writer.write(command);
                writer.write('\n');
                writer.flush();
                String response = reader.readLine();
                if (TextUtils.equals("ok", response)) {
                    return true;
                }
                if (TextUtils.equals("unavailable", response)) {
                    return false;
                }
                throw new IOException("unexpected response " + response);
            } catch (IOException | RuntimeException e) {
                logFailure("dispatch failed", e);
                closeConnection();
            }
        }
        return false;
    }

    private boolean ensureConnected() {
        if (socket != null && reader != null && writer != null) {
            return true;
        }
        LocalSocket newSocket = new LocalSocket();
        try {
            newSocket.connect(new LocalSocketAddress(
                    SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            newSocket.setSoTimeout(RESPONSE_TIMEOUT_MS);
            socket = newSocket;
            reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream()));
            return true;
        } catch (IOException | RuntimeException e) {
            closeQuietly(newSocket);
            logFailure("connect failed", e);
            return false;
        }
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }

    private void closeConnection() {
        closeQuietly(reader);
        closeQuietly(writer);
        closeQuietly(socket);
        reader = null;
        writer = null;
        socket = null;
    }

    private void logFailure(String stage, Exception error) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFailureLogUptime < FAILURE_LOG_THROTTLE_MS) {
            return;
        }
        lastFailureLogUptime = now;
        Log.w(TAG, "MIUI gesture bridge " + stage + ": "
                + error.getClass().getSimpleName()
                + (TextUtils.isEmpty(error.getMessage()) ? "" : " " + error.getMessage()));
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
