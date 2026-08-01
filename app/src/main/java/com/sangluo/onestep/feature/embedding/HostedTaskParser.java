package com.sangluo.onestep.feature.embedding;

/** Parses `am stack list` output to locate an app task hosted on a display. */
public final class HostedTaskParser {
    private HostedTaskParser() {
    }

    public static int findHostedTaskId(String stackList, int targetDisplayId,
                                       String packageName) {
        if (stackList == null || packageName == null || packageName.isEmpty()) {
            return -1;
        }
        int rootDisplayId = -1;
        int fallbackTaskId = -1;
        String[] lines = stackList.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isDisplayContainerLine(line)) {
                rootDisplayId = parseIntAfter(line, "displayId=");
                continue;
            }
            if (rootDisplayId == targetDisplayId
                    && line.startsWith("taskId=")
                    && line.contains(packageName + "/")) {
                int taskId = parseIntAfter(line, "taskId=");
                if (line.contains("visible=true")) {
                    return taskId;
                }
                if (fallbackTaskId <= 0) {
                    fallbackTaskId = taskId;
                }
            }
        }
        return fallbackTaskId;
    }

    public static int findVisibleHostedTaskId(String stackList, int targetDisplayId,
                                              String packageName) {
        if (stackList == null || packageName == null || packageName.isEmpty()) {
            return -1;
        }
        int rootDisplayId = -1;
        String[] lines = stackList.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isDisplayContainerLine(line)) {
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

    public static int findHostedTaskIdForComponent(String stackList, int targetDisplayId,
                                                   String packageName, String className) {
        if (stackList == null || packageName == null || packageName.isEmpty()
                || className == null || className.isEmpty()) {
            return -1;
        }
        String fullComponent = packageName + "/" + className;
        String shortComponent = className.startsWith(packageName + ".")
                ? packageName + "/." + className.substring(packageName.length() + 1)
                : fullComponent;
        int rootDisplayId = -1;
        int fallbackTaskId = -1;
        String[] lines = stackList.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isDisplayContainerLine(line)) {
                rootDisplayId = parseIntAfter(line, "displayId=");
                continue;
            }
            if (rootDisplayId != targetDisplayId || !line.startsWith("taskId=")
                    || (!line.contains(fullComponent) && !line.contains(shortComponent))) {
                continue;
            }
            int taskId = parseIntAfter(line, "taskId=");
            if (line.contains("visible=true")) {
                return taskId;
            }
            if (fallbackTaskId <= 0) {
                fallbackTaskId = taskId;
            }
        }
        return fallbackTaskId;
    }

    public static int findVisibleHostedTaskIdForComponent(
            String stackList, int targetDisplayId, String packageName, String className) {
        if (stackList == null || packageName == null || packageName.isEmpty()
                || className == null || className.isEmpty()) {
            return -1;
        }
        String fullComponent = packageName + "/" + className;
        String shortComponent = className.startsWith(packageName + ".")
                ? packageName + "/." + className.substring(packageName.length() + 1)
                : fullComponent;
        int rootDisplayId = -1;
        String[] lines = stackList.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isDisplayContainerLine(line)) {
                rootDisplayId = parseIntAfter(line, "displayId=");
                continue;
            }
            if (rootDisplayId == targetDisplayId && line.startsWith("taskId=")
                    && line.contains("visible=true")
                    && (line.contains(fullComponent) || line.contains(shortComponent))) {
                return parseIntAfter(line, "taskId=");
            }
        }
        return -1;
    }

    public static boolean containsTaskOnDisplay(String stackList, int targetDisplayId,
                                                int targetTaskId) {
        if (stackList == null || targetTaskId <= 0) {
            return false;
        }
        int rootDisplayId = -1;
        String[] lines = stackList.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (isDisplayContainerLine(line)) {
                rootDisplayId = parseIntAfter(line, "displayId=");
                continue;
            }
            if (rootDisplayId == targetDisplayId
                    && line.startsWith("taskId=")
                    && parseIntAfter(line, "taskId=") == targetTaskId
                    && hasDisplayableTopActivity(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDisplayableTopActivity(String taskLine) {
        int marker = taskLine.indexOf("topActivity=");
        if (marker < 0) {
            // Older Android releases print the component directly on the task line.
            return taskLine.contains("/");
        }
        String value = taskLine.substring(marker + "topActivity=".length()).trim();
        return !value.isEmpty()
                && !value.startsWith("null")
                && !value.startsWith("unknown");
    }

    private static boolean isDisplayContainerLine(String line) {
        return line.startsWith("RootTask id=") || line.startsWith("Stack id=");
    }

    public static int findTaskActivityCount(String activitiesDump, int targetTaskId) {
        if (activitiesDump == null || targetTaskId <= 0) {
            return -1;
        }
        String[] lines = activitiesDump.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!line.contains("Task{")
                    || parseIntAfter(line, "#") != targetTaskId) {
                continue;
            }
            int size = parseIntAfter(line, "sz=");
            if (size >= 0) {
                return size;
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
