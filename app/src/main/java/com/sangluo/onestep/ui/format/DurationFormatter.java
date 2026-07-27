package com.sangluo.onestep.ui.format;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure text formatting used by timer, stopwatch, and recording components. */
public final class DurationFormatter {
    private static final Pattern RECORDING_DURATION_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,2}:\\d{2}(?::\\d{2})?)(?!\\d)");

    private DurationFormatter() {
    }

    public static String ensureMilliseconds(String displayedTime) {
        String value = displayedTime == null ? "" : displayedTime.trim();
        if (value.isEmpty() || value.contains(".") || value.contains(",")) {
            return value;
        }
        return value + ".000";
    }

    public static String findRecordingDuration(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                Matcher matcher = RECORDING_DURATION_PATTERN.matcher(value);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return "0:00";
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    public static String formatDurationWithMilliseconds(long millis) {
        long duration = Math.max(0L, millis);
        long totalSeconds = duration / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        long milliseconds = duration % 1000L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d.%03d",
                    hours, minutes, seconds, milliseconds);
        }
        return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, milliseconds);
    }
}
