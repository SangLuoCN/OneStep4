package com.sangluo.onestep.data.settings;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class TopAppListPolicyTest {
    @Test
    public void defaultsToAllAppsSelectedInDesktopOrder() {
        TopAppListPolicy.State state = TopAppListPolicy.reconcile(
                Arrays.asList("a", "b", "c"), false,
                Collections.emptyList(), Collections.emptySet());

        assertEquals(Arrays.asList("a", "b", "c"), state.orderedKeys);
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), state.selectedKeys);
    }

    @Test
    public void preservesSavedSelectionAndOrder() {
        TopAppListPolicy.State state = TopAppListPolicy.reconcile(
                Arrays.asList("a", "b", "c"), true,
                Arrays.asList("c", "a", "b"),
                new HashSet<>(Arrays.asList("c", "b")));

        assertEquals(Arrays.asList("c", "a", "b"), state.orderedKeys);
        assertEquals(new HashSet<>(Arrays.asList("c", "b")), state.selectedKeys);
    }

    @Test
    public void appendsNewAppsAsSelectedAndDropsUninstalledApps() {
        TopAppListPolicy.State state = TopAppListPolicy.reconcile(
                Arrays.asList("a", "c", "new"), true,
                Arrays.asList("c", "removed", "a"),
                new HashSet<>(Arrays.asList("removed", "a")));

        assertEquals(Arrays.asList("c", "a", "new"), state.orderedKeys);
        assertEquals(new HashSet<>(Arrays.asList("a", "new")), state.selectedKeys);
    }

    @Test
    public void allowsSavingAnEmptySelection() {
        TopAppListPolicy.State state = TopAppListPolicy.reconcile(
                Arrays.asList("a", "b"), true,
                Arrays.asList("b", "a"), Collections.emptySet());

        assertEquals(Arrays.asList("b", "a"), state.orderedKeys);
        assertEquals(Collections.emptySet(), state.selectedKeys);
    }

    @Test
    public void movesVisibleAppsFirstAndPreservesBothGroupOrders() {
        assertEquals(Arrays.asList("b", "d", "a", "c", "e"),
                TopAppListPolicy.prioritizeVisible(
                        Arrays.asList("a", "b", "c", "d", "e"),
                        new HashSet<>(Arrays.asList("b", "d"))));
    }

    @Test
    public void restoresConfiguredOrderWhenNoAppsAreVisible() {
        assertEquals(Arrays.asList("c", "a", "b"),
                TopAppListPolicy.prioritizeVisible(
                        Arrays.asList("c", "a", "b"), Collections.emptySet()));
    }
}
