package com.sangluo.onestep.feature.embedding;

/** Decides whether OneStep may intercept HOME and recents on the physical display. */
public final class DefaultHomeRoutingPolicy {
    private DefaultHomeRoutingPolicy() {
    }

    public static boolean shouldInterceptSystemHome(String oneStepPackage,
                                                    String defaultHomePackage) {
        return oneStepPackage != null && oneStepPackage.equals(defaultHomePackage);
    }
}
