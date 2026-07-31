package com.sangluo.onestep.data.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.sangluo.onestep.R;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Loads the icon produced by the current OEM theme instead of the raw APK resource. */
final class SystemThemedIconLoader {
    private static final String TAG = "OneStepIcons";
    private static final int HYPER_OS_ICON_SIZE_DP = 72;

    private final Context context;
    private final PackageManager packageManager;
    private Boolean miuiIconCustomizerAvailable;
    private boolean miuiMethodsResolved;
    private Method miuiCustomizedIconMethod;
    private Method miuiStyleGeneratorMethod;
    private Method miuiClearCacheMethod;

    SystemThemedIconLoader(Context context) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
    }

    Drawable loadIcon(ResolveInfo resolveInfo) {
        Drawable systemIcon = resolveInfo.loadIcon(packageManager);
        Drawable miuiIcon = loadMiuiCustomizedIcon(resolveInfo, systemIcon);
        return miuiIcon == null ? systemIcon : miuiIcon;
    }

    Drawable addCloneBadge(Drawable icon) {
        Drawable badge = AppCompatResources.getDrawable(
                context, R.drawable.ic_clone_app_badge);
        if (icon == null || badge == null) {
            return icon;
        }
        return new CloneBadgedDrawable(icon.mutate(), badge.mutate());
    }

    void invalidateThemeCaches(List<ResolveInfo> resolveInfos) {
        clearMiuiIconCustomizerCache();
        clearOplusActivityIconCaches(resolveInfos);
    }

    private Drawable loadMiuiCustomizedIcon(ResolveInfo resolveInfo, Drawable systemIcon) {
        if (!hasMiuiIconCustomizer() || resolveInfo.activityInfo == null) {
            return null;
        }

        resolveMiuiMethods();
        if (miuiCustomizedIconMethod != null) {
            try {
                Object result = miuiCustomizedIconMethod.invoke(null,
                        resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
                if (result instanceof Drawable) {
                    return (Drawable) result;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Fall through to the current HyperOS style generator.
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !(systemIcon instanceof AdaptiveIconDrawable)
                || miuiStyleGeneratorMethod == null) {
            return null;
        }
        try {
            Drawable flattenedIcon = flattenAdaptiveIcon(systemIcon);
            Object result = miuiStyleGeneratorMethod.invoke(null, flattenedIcon);
            return result instanceof Drawable ? (Drawable) result : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private synchronized void resolveMiuiMethods() {
        if (miuiMethodsResolved) {
            return;
        }
        miuiMethodsResolved = true;
        try {
            Class<?> customizer = Class.forName("miui.content.res.IconCustomizer");
            miuiCustomizedIconMethod = findAccessibleMethod(customizer,
                    "getCustomizedIconDrawable", String.class, String.class);
            miuiStyleGeneratorMethod = findAccessibleMethod(customizer,
                    "generateIconStyleDrawable", Drawable.class);
            if (miuiStyleGeneratorMethod == null) {
                miuiStyleGeneratorMethod = findAccessibleMethod(customizer,
                        "generateIconDrawable", Drawable.class);
            }
            miuiClearCacheMethod = findAccessibleMethod(customizer, "clearCache");
        } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            // hasMiuiIconCustomizer() already supplies the standard Android fallback.
        }
    }

    private Method findAccessibleMethod(
            Class<?> target, String name, Class<?>... parameterTypes) {
        try {
            Method method = target.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private Drawable flattenAdaptiveIcon(Drawable source) {
        int size = Math.max(1, Math.round(HYPER_OS_ICON_SIZE_DP
                * context.getResources().getDisplayMetrics().density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect oldBounds = new Rect(source.getBounds());
        source.setBounds(0, 0, size, size);
        source.draw(canvas);
        source.setBounds(oldBounds);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private boolean hasMiuiIconCustomizer() {
        if (miuiIconCustomizerAvailable != null) {
            return miuiIconCustomizerAvailable;
        }
        try {
            Class.forName("miui.content.res.IconCustomizer");
            miuiIconCustomizerAvailable = true;
        } catch (ClassNotFoundException | LinkageError e) {
            miuiIconCustomizerAvailable = false;
        }
        return miuiIconCustomizerAvailable;
    }

    private void clearMiuiIconCustomizerCache() {
        if (!hasMiuiIconCustomizer()) {
            return;
        }
        resolveMiuiMethods();
        if (miuiClearCacheMethod == null) {
            return;
        }
        try {
            miuiClearCacheMethod.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.d(TAG, "HyperOS icon cache is managed by the system framework", e);
        }
    }

    private void clearOplusActivityIconCaches(List<ResolveInfo> resolveInfos) {
        if (resolveInfos.isEmpty()) {
            return;
        }
        try {
            Class<?> extensionClass = Class.forName("android.app.UxIconPackageManagerExt");
            Constructor<?> constructor = extensionClass.getDeclaredConstructor(
                    PackageManager.class, Context.class);
            constructor.setAccessible(true);
            Object extension = constructor.newInstance(packageManager, context);
            Method clearIcon = extensionClass.getDeclaredMethod(
                    "clearCachedIconForActivity", ComponentName.class);
            clearIcon.setAccessible(true);
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (resolveInfo.activityInfo != null) {
                    clearIcon.invoke(extension, new ComponentName(
                            resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Not a ColorOS/OxygenOS framework.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.d(TAG, "ColorOS icon cache is managed by the system framework", e);
        }
    }

    /** Matches Launcher3's bottom-end badge placement and 44.4% badge scale. */
    private static final class CloneBadgedDrawable extends Drawable {
        private static final float BADGE_SIZE_RATIO = 0.444f;

        private final Drawable icon;
        private final Drawable badge;

        CloneBadgedDrawable(Drawable icon, Drawable badge) {
            this.icon = icon;
            this.badge = badge;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            icon.setBounds(bounds);
            int badgeSize = Math.max(1, Math.round(
                    Math.min(bounds.width(), bounds.height()) * BADGE_SIZE_RATIO));
            badge.setBounds(bounds.right - badgeSize, bounds.bottom - badgeSize,
                    bounds.right, bounds.bottom);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            icon.draw(canvas);
            badge.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            icon.setAlpha(alpha);
            badge.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            icon.setColorFilter(colorFilter);
            badge.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return icon.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return icon.getIntrinsicHeight();
        }

        @Override
        public boolean isStateful() {
            return icon.isStateful() || badge.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean changed = icon.setState(state);
            changed |= badge.setState(state);
            return changed;
        }
    }
}
