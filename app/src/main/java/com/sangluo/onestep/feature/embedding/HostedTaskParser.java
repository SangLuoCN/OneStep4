package com.sangluo.onestep.feature.embedding;

/** Parses `am stack list` output to locate the visible task hosted on a display. */
public final class HostedTaskParser {
    private HostedTaskParser() {
    }

    public static int findHostedTaskId(String stackList, int targetDisplayId,
                                       String packageName) {
        if (stackList == null || packageName == null || packageName.isEmpty()) {
            return -1;
        }
        int rootDisplayId = -1;
        String[] lines = stackList.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("RootTask id=")) {
                rootDisplayId = parseIntAfter(line, "displayId=");
                continue;
            }
            if (rootDisplayId == targetDisplayId
                    && line.startsWith("taskId=")
                    && line.contains("visible=true")
                    && line.contains(packageName + "/")) {
                return parseIntAfter(line, "taskId=");
            }
        }
        return -1;
    }

    static int parseIntAfter(String text, String marker) {
        int start = text.indexOf(marker);
        if (start < 0) {
            return -1;
        }
        start += marker.length();
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
