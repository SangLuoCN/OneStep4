package com.sangluo.onestep.feature.tasks;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunningTaskAppResolverTest {
    @Test
    public void resolvesExactLauncherComponent() {
        Set<String> result = RunningTaskAppResolver.resolve(
                Collections.singletonList(app("player", "com.example/.Player", 0)),
                Collections.singletonList(task(0, "com.example/.Player", "", "", "")));

        assertEquals(Collections.singleton("player"), result);
    }

    @Test
    public void exactComponentDoesNotMarkOtherLauncherEntryInSamePackage() {
        Set<String> result = RunningTaskAppResolver.resolve(
                Arrays.asList(
                        app("main", "com.example/.Main", 0),
                        app("scanner", "com.example/.Scanner", 0)),
                Collections.singletonList(task(
                        0, "com.example/.Scanner", "", "com.example/.Internal", "")));

        assertEquals(Collections.singleton("scanner"), result);
    }

    @Test
    public void fallsBackToPackageForInternalTaskComponent() {
        Set<String> result = RunningTaskAppResolver.resolve(
                Collections.singletonList(app("main", "com.example/.Main", 0)),
                Collections.singletonList(task(
                        0, "com.example/.Internal", "", "", "")));

        assertTrue(result.contains("main"));
    }

    @Test
    public void keepsClonedAppTasksSeparatedByUser() {
        Set<String> result = RunningTaskAppResolver.resolve(
                Arrays.asList(
                        app("owner", "com.example/.Main", 0),
                        app("clone", "com.example/.Main", 999)),
                Collections.singletonList(task(
                        999, "com.example/.Main", "", "", "")));

        assertTrue(result.contains("clone"));
        assertFalse(result.contains("owner"));
    }

    @Test
    public void taskWithoutMatchingApplicationDoesNotCreateStatus() {
        Set<String> result = RunningTaskAppResolver.resolve(
                Collections.singletonList(app("main", "com.example/.Main", 0)),
                Collections.singletonList(task(
                        0, "com.other/.Main", "", "", "")));

        assertTrue(result.isEmpty());
    }

    private static RunningTaskAppResolver.AppIdentity app(
            String instanceKey, String component, int userId) {
        return new RunningTaskAppResolver.AppIdentity(
                instanceKey, component, component.substring(0, component.indexOf('/')), userId);
    }

    private static RunningTaskAppResolver.TaskIdentity task(
            int userId, String launchComponent, String originalComponent,
            String baseComponent, String topComponent) {
        return new RunningTaskAppResolver.TaskIdentity(
                userId, launchComponent, originalComponent, baseComponent, topComponent);
    }
}
