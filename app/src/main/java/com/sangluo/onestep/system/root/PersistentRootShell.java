package com.sangluo.onestep.system.root;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Serializes commands through one long-lived su process. */
public final class PersistentRootShell implements AutoCloseable {
    private final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
    private Process process;
    private BufferedWriter inputWriter;
    private Thread readerThread;
    private int commandSequence;

    public synchronized ShellCommandResult run(String command, int timeoutSeconds) {
        if (!ensureStarted()) {
            return new ShellCommandResult(-1, "start su failed");
        }
        drainOutputLines();

        String marker = "__ONESTEP_ROOT_DONE_" + SystemClock.uptimeMillis()
                + "_" + (++commandSequence) + "__";
        try {
            inputWriter.write("(\n");
            inputWriter.write(command);
            inputWriter.write('\n');
            inputWriter.write(")\n");
            inputWriter.write("__onestep_exit=$?\n");
            inputWriter.write("echo " + marker + ":$__onestep_exit\n");
            inputWriter.flush();
        } catch (IOException | RuntimeException e) {
            closeLocked();
            return new ShellCommandResult(-1, e.getClass().getSimpleName());
        }

        long deadline = SystemClock.uptimeMillis() + Math.max(1, timeoutSeconds) * 1000L;
        StringBuilder output = new StringBuilder();
        while (SystemClock.uptimeMillis() < deadline) {
            long waitMs = Math.min(250L,
                    Math.max(1L, deadline - SystemClock.uptimeMillis()));
            String line;
            try {
                line = outputLines.poll(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                closeLocked();
                return new ShellCommandResult(-1, "interrupted");
            }

            if (line == null) {
                if (!isProcessRunning(process)) {
                    closeLocked();
                    return new ShellCommandResult(-1, trimOutput(output, "shell exited"));
                }
                continue;
            }

            String markerPrefix = marker + ":";
            if (line.startsWith(markerPrefix)) {
                return new ShellCommandResult(
                        parseExitCode(line.substring(markerPrefix.length())), output.toString());
            }
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(line);
        }

        closeLocked();
        return new ShellCommandResult(-1, trimOutput(output, "timeout"));
    }

    @Override
    public synchronized void close() {
        closeLocked();
    }

    private boolean ensureStarted() {
        if (isProcessRunning(process) && inputWriter != null) {
            return true;
        }
        closeLocked();
        try {
            process = new ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start();
            inputWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            Process readerProcess = process;
            readerThread = new Thread(() -> readOutputLoop(readerProcess), "OneStepRootShell");
            readerThread.start();
            return true;
        } catch (IOException | RuntimeException e) {
            closeLocked();
            return false;
        }
    }

    private void readOutputLoop(Process readerProcess) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(readerProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.offer(line);
            }
        } catch (IOException ignored) {
        }
    }

    private void drainOutputLines() {
        while (outputLines.poll() != null) {
            // Drop stale output from a previously failed command.
        }
    }

    private boolean isProcessRunning(Process target) {
        if (target == null) {
            return false;
        }
        try {
            target.exitValue();
            return false;
        } catch (IllegalThreadStateException running) {
            return true;
        }
    }

    private int parseExitCode(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String trimOutput(StringBuilder output, String fallback) {
        return output.length() == 0 ? fallback : output.toString();
    }

    private void closeLocked() {
        if (inputWriter != null) {
            try {
                inputWriter.write("exit\n");
                inputWriter.flush();
            } catch (IOException | RuntimeException ignored) {
            }
            try {
                inputWriter.close();
            } catch (IOException ignored) {
            }
            inputWriter = null;
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
        drainOutputLines();
    }
}
