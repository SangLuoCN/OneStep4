package com.sangluo.onestep.system.root;

/** Recognizes failures caused by a restarted Android system process. */
public final class SystemServiceFailurePolicy {
    private static final String DEAD_OBJECT = "DeadObjectException";
    private static final String DEAD_SYSTEM = "DeadSystemException";
    private static final String DEAD_SYSTEM_RUNTIME = "DeadSystemRuntimeException";

    private SystemServiceFailurePolicy() {
    }

    public static boolean isStaleSystemService(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (isStaleSystemServiceName(current.getClass().getSimpleName())) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    public static boolean isStaleSystemServiceDescription(String description) {
        return description != null
                && (description.contains(DEAD_OBJECT)
                || description.contains(DEAD_SYSTEM)
                || description.contains(DEAD_SYSTEM_RUNTIME));
    }

    public static String describeCauseChain(Throwable throwable) {
        StringBuilder description = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (description.length() > 0) {
                description.append(" caused by ");
            }
            description.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isEmpty()) {
                description.append(": ").append(message);
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return description.length() == 0 ? "unknown failure" : description.toString();
    }

    private static boolean isStaleSystemServiceName(String name) {
        return DEAD_OBJECT.equals(name)
                || DEAD_SYSTEM.equals(name)
                || DEAD_SYSTEM_RUNTIME.equals(name);
    }
}
