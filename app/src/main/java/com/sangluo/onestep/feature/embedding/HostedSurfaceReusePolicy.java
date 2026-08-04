package com.sangluo.onestep.feature.embedding;

/** Keeps a proven hosted surface visible while its task identity is revalidated. */
public final class HostedSurfaceReusePolicy {
    private HostedSurfaceReusePolicy() {
    }

    public static boolean shouldKeepVisibleDuringValidation(
            boolean matchingLaunchRequest, boolean revealPending, boolean surfaceVisible) {
        return matchingLaunchRequest && !revealPending && surfaceVisible;
    }

    public static boolean shouldBeginReveal(
            boolean reusingHostedApp, boolean taskResolved, boolean revealPending,
            boolean keepingVisibleDuringValidation) {
        if (!reusingHostedApp) {
            return true;
        }
        return !taskResolved && !revealPending && !keepingVisibleDuringValidation;
    }

    public static boolean shouldValidateReusedTask(boolean imageShareLaunchGuardActive) {
        return !imageShareLaunchGuardActive;
    }
}
