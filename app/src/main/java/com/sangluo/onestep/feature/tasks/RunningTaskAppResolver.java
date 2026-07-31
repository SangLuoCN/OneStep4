package com.sangluo.onestep.feature.tasks;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves launcher instances represented by system task-manager entries. */
public final class RunningTaskAppResolver {
    private RunningTaskAppResolver() {
    }

    public static Set<String> resolve(List<AppIdentity> apps, List<TaskIdentity> tasks) {
        if (apps == null || apps.isEmpty() || tasks == null || tasks.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (TaskIdentity task : tasks) {
            if (task == null) {
                continue;
            }
            if (addExactMatches(result, apps, task.userId, task.launchComponent)
                    || addExactMatches(result, apps, task.userId, task.originalComponent)
                    || addExactMatches(result, apps, task.userId, task.baseComponent)
                    || addExactMatches(result, apps, task.userId, task.topComponent)) {
                continue;
            }
            String packageName = firstComponentPackage(
                    task.launchComponent, task.originalComponent,
                    task.baseComponent, task.topComponent);
            if (packageName.isEmpty()) {
                continue;
            }
            for (AppIdentity app : apps) {
                if (app != null && app.userId == task.userId
                        && packageName.equals(app.packageName)) {
                    result.add(app.instanceKey);
                }
            }
        }
        return result;
    }

    private static boolean addExactMatches(Set<String> result, List<AppIdentity> apps,
                                           int userId, String component) {
        if (component == null || component.isEmpty()) {
            return false;
        }
        boolean matched = false;
        for (AppIdentity app : apps) {
            if (app != null && app.userId == userId
                    && component.equals(app.component)) {
                result.add(app.instanceKey);
                matched = true;
            }
        }
        return matched;
    }

    private static String firstComponentPackage(String... components) {
        for (String component : components) {
            if (component == null) {
                continue;
            }
            int separator = component.indexOf('/');
            if (separator > 0) {
                return component.substring(0, separator);
            }
        }
        return "";
    }

    public static final class AppIdentity {
        public final String instanceKey;
        public final String component;
        public final String packageName;
        public final int userId;

        public AppIdentity(String instanceKey, String component,
                           String packageName, int userId) {
            this.instanceKey = valueOrEmpty(instanceKey);
            this.component = valueOrEmpty(component);
            this.packageName = valueOrEmpty(packageName);
            this.userId = userId;
        }
    }

    public static final class TaskIdentity {
        public final int userId;
        public final String launchComponent;
        public final String originalComponent;
        public final String baseComponent;
        public final String topComponent;

        public TaskIdentity(int userId, String launchComponent,
                            String originalComponent, String baseComponent,
                            String topComponent) {
            this.userId = userId;
            this.launchComponent = valueOrEmpty(launchComponent);
            this.originalComponent = valueOrEmpty(originalComponent);
            this.baseComponent = valueOrEmpty(baseComponent);
            this.topComponent = valueOrEmpty(topComponent);
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
