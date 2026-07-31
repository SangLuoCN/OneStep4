package com.sangluo.onestep.data.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reconciles the saved top-app selection with currently launchable desktop apps. */
public final class TopAppListPolicy {
    private TopAppListPolicy() {
    }

    public static State reconcile(List<String> availableKeys, boolean configured,
                                  List<String> savedOrder, Set<String> savedSelection) {
        List<String> available = uniqueNonEmpty(availableKeys);
        if (!configured) {
            return new State(available, new LinkedHashSet<>(available));
        }

        Set<String> availableSet = new HashSet<>(available);
        List<String> order = new ArrayList<>(available.size());
        Set<String> knownKeys = new HashSet<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                if (availableSet.contains(key) && knownKeys.add(key)) {
                    order.add(key);
                }
            }
        }

        Set<String> selection = new LinkedHashSet<>();
        if (savedSelection != null) {
            for (String key : order) {
                if (savedSelection.contains(key)) {
                    selection.add(key);
                }
            }
        }
        for (String key : available) {
            if (knownKeys.add(key)) {
                order.add(key);
                selection.add(key);
            }
        }
        return new State(order, selection);
    }

    private static List<String> uniqueNonEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isEmpty() && seen.add(value)) {
                result.add(value);
            }
        }
        return result;
    }

    public static final class State {
        public final List<String> orderedKeys;
        public final Set<String> selectedKeys;

        State(List<String> orderedKeys, Set<String> selectedKeys) {
            this.orderedKeys = Collections.unmodifiableList(new ArrayList<>(orderedKeys));
            this.selectedKeys = Collections.unmodifiableSet(new LinkedHashSet<>(selectedKeys));
        }
    }
}
