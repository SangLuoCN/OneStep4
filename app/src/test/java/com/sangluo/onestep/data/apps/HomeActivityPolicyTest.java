package com.sangluo.onestep.data.apps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;

import org.junit.Test;

public class HomeActivityPolicyTest {
    @Test
    public void recognizesStandardSystemApplicationFlags() {
        assertTrue(HomeActivityPolicy.isSystemHome(ApplicationInfo.FLAG_SYSTEM,
                "/data/app/system-copy/base.apk"));
        assertTrue(HomeActivityPolicy.isSystemHome(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
                "/data/app/updated-system/base.apk"));
    }

    @Test
    public void recognizesKnownSystemPartitions() {
        assertTrue(HomeActivityPolicy.isSystemHome(0, "/product/priv-app/Launcher/base.apk"));
        assertTrue(HomeActivityPolicy.isSystemHome(0, "/system_ext/app/Launcher/base.apk"));
    }

    @Test
    public void doesNotTreatDataAppAsSystemApplication() {
        assertFalse(HomeActivityPolicy.isSystemHome(0,
                "/data/app/~~abc/com.example.launcher/base.apk"));
        assertFalse(HomeActivityPolicy.isSystemHome(0, null));
    }
}
