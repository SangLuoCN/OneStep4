package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class HostedBackDispatchPolicyTest {
    @Test
    public void everyVirtualDisplayUsesSystemNavigationForBack() {
        assertTrue(HostedBackDispatchPolicy.shouldTrySystemNavigation(
                1, KeyEvent.KEYCODE_BACK));
        assertTrue(HostedBackDispatchPolicy.shouldTrySystemNavigation(
                24, KeyEvent.KEYCODE_BACK));
        assertFalse(HostedBackDispatchPolicy.shouldTrySystemNavigation(
                0, KeyEvent.KEYCODE_BACK));
        assertFalse(HostedBackDispatchPolicy.shouldTrySystemNavigation(
                24, KeyEvent.KEYCODE_HOME));
    }

    @Test
    public void hostedAppAlwaysReceivesBackBeforeExitCheck() {
        assertTrue(HostedBackDispatchPolicy.shouldDispatchBeforeExitCheck(
                true, false, true));
        assertFalse(HostedBackDispatchPolicy.shouldDispatchBeforeExitCheck(
                false, false, true));
        assertFalse(HostedBackDispatchPolicy.shouldDispatchBeforeExitCheck(
                true, true, true));
        assertFalse(HostedBackDispatchPolicy.shouldDispatchBeforeExitCheck(
                true, false, false));
    }
}
