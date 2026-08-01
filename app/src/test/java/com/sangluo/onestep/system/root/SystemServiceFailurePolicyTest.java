package com.sangluo.onestep.system.root;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SystemServiceFailurePolicyTest {
    @Test
    public void recognizesDirectAndNestedDeadSystemFailures() {
        assertTrue(SystemServiceFailurePolicy.isStaleSystemService(
                new DeadSystemException()));
        assertTrue(SystemServiceFailurePolicy.isStaleSystemService(
                new RuntimeException(new DeadObjectException())));
        assertTrue(SystemServiceFailurePolicy.isStaleSystemService(
                new RuntimeException(new DeadSystemRuntimeException())));
    }

    @Test
    public void recognizesBridgeFailureDescriptions() {
        assertTrue(SystemServiceFailurePolicy.isStaleSystemServiceDescription(
                "flags=1035:RuntimeException:android.os.DeadSystemException"));
        assertTrue(SystemServiceFailurePolicy.isStaleSystemServiceDescription(
                "DeadObjectException"));
        assertFalse(SystemServiceFailurePolicy.isStaleSystemServiceDescription(
                "SecurityException: packageName must match the calling uid"));
    }

    @Test
    public void rejectsUnrelatedFailures() {
        assertFalse(SystemServiceFailurePolicy.isStaleSystemService(
                new IllegalStateException("display unavailable")));
        assertFalse(SystemServiceFailurePolicy.isStaleSystemService(null));
    }

    @Test
    public void causeDescriptionPreservesDeadSystemMarker() {
        RuntimeException failure = new RuntimeException(
                "display service failed", new DeadSystemException());

        String description = SystemServiceFailurePolicy.describeCauseChain(failure);

        assertTrue(description.contains("RuntimeException: display service failed"));
        assertTrue(description.contains("DeadSystemException"));
        assertTrue(SystemServiceFailurePolicy.isStaleSystemServiceDescription(description));
    }

    private static final class DeadObjectException extends RuntimeException {
    }

    private static final class DeadSystemException extends RuntimeException {
    }

    private static final class DeadSystemRuntimeException extends RuntimeException {
    }
}
