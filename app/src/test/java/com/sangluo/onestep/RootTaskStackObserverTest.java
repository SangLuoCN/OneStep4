package com.sangluo.onestep;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RootTaskStackObserverTest {
    @Test
    public void detectsLegacyTaskIdPayload() throws Exception {
        Method method = ListenerShapes.class.getDeclaredMethod("legacy", int.class);

        assertFalse(RootTaskStackObserver.usesTaskInfoPayload(method));
    }

    @Test
    public void detectsTaskInfoPayload() throws Exception {
        Method method = ListenerShapes.class.getDeclaredMethod("current", Object.class);

        assertTrue(RootTaskStackObserver.usesTaskInfoPayload(method));
    }

    @Test
    public void missingCallbackHasNoTaskInfoPayload() {
        assertFalse(RootTaskStackObserver.usesTaskInfoPayload(null));
    }

    private static final class ListenerShapes {
        @SuppressWarnings("unused")
        void legacy(int taskId) {
        }

        @SuppressWarnings("unused")
        void current(Object taskInfo) {
        }
    }
}
