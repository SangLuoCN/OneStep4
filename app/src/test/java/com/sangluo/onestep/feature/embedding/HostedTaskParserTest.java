package com.sangluo.onestep.feature.embedding;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostedTaskParserTest {
    private static final String STACK_LIST =
            "RootTask id=1 displayId=0 userId=0\n"
                    + "  taskId=10 visible=true topActivity=com.android.settings/.Settings\n"
                    + "RootTask id=8 displayId=12 userId=0\n"
                    + "  taskId=41 visible=false topActivity=com.example.player/.Main\n"
                    + "  taskId=42 visible=true topActivity=com.example.player/.Main\n";

    @Test
    public void findsVisiblePackageOnTargetDisplay() {
        assertEquals(42, HostedTaskParser.findHostedTaskId(
                STACK_LIST, 12, "com.example.player"));
    }

    @Test
    public void fallsBackToNonVisiblePackageTaskOnTargetDisplay() {
        String stackList = "RootTask id=8 displayId=12 userId=0\n"
                + "  taskId=41 visible=false topActivity=com.example.player/.Main\n";

        assertEquals(41, HostedTaskParser.findHostedTaskId(
                stackList, 12, "com.example.player"));
        assertEquals(-1, HostedTaskParser.findVisibleHostedTaskId(
                stackList, 12, "com.example.player"));
    }

    @Test
    public void rejectsOtherDisplaysAndPackages() {
        assertEquals(-1, HostedTaskParser.findHostedTaskId(
                STACK_LIST, 0, "com.example.player"));
        assertEquals(-1, HostedTaskParser.findHostedTaskId(STACK_LIST, 12, "com.missing"));
    }

    @Test
    public void findsExactHomeComponentWithoutAcceptingSecondaryLauncher() {
        String stackList = "RootTask id=8 displayId=12 userId=0\n"
                + "  taskId=41 visible=true topActivity=com.example.launcher/"
                + "com.example.launcher.SecondaryDisplayLauncher\n"
                + "  taskId=42 visible=false topActivity=com.example.launcher/"
                + "com.example.launcher.QuickstepLauncher\n";

        assertEquals(42, HostedTaskParser.findHostedTaskIdForComponent(
                stackList, 12, "com.example.launcher",
                "com.example.launcher.QuickstepLauncher"));
        assertEquals(-1, HostedTaskParser.findVisibleHostedTaskIdForComponent(
                stackList, 12, "com.example.launcher",
                "com.example.launcher.QuickstepLauncher"));
    }

    @Test
    public void acceptsShortExactHomeComponentName() {
        String stackList = "RootTask id=8 displayId=12 userId=0\n"
                + "  taskId=42 visible=true topActivity=com.example.launcher/.Launcher\n";

        assertEquals(42, HostedTaskParser.findHostedTaskIdForComponent(
                stackList, 12, "com.example.launcher",
                "com.example.launcher.Launcher"));
        assertEquals(42, HostedTaskParser.findVisibleHostedTaskIdForComponent(
                stackList, 12, "com.example.launcher",
                "com.example.launcher.Launcher"));
    }

    @Test
    public void parsesAndroid10StackListFormat() {
        String stackList = "Stack id=2 bounds=[0,0][1080,2400] displayId=0 userId=0\n"
                + "  taskId=14: com.sangluo.onestep/com.sangluo.onestep.MainActivity "
                + "visible=true topActivity=ComponentInfo{com.sangluo.onestep/"
                + "com.sangluo.onestep.MainActivity}\n"
                + "Stack id=4 bounds=[0,0][1080,2400] displayId=1 userId=0\n"
                + "  taskId=16: com.google.android.apps.nexuslauncher/"
                + "com.google.android.apps.nexuslauncher.NexusLauncherActivity "
                + "visible=true topActivity=ComponentInfo{com.google.android.apps.nexuslauncher/"
                + "com.google.android.apps.nexuslauncher.NexusLauncherActivity}\n";

        assertEquals(16, HostedTaskParser.findHostedTaskId(
                stackList, 1, "com.google.android.apps.nexuslauncher"));
        assertEquals(16, HostedTaskParser.findVisibleHostedTaskIdForComponent(
                stackList, 1, "com.google.android.apps.nexuslauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity"));
        assertTrue(HostedTaskParser.containsTaskOnDisplay(stackList, 1, 16));
        assertEquals(-1, HostedTaskParser.findHostedTaskId(
                stackList, 0, "com.google.android.apps.nexuslauncher"));
    }

    @Test
    public void tracksTaskIdentityAcrossPackageChangesInsideTheTask() {
        String stackList = "RootTask id=8 displayId=12 userId=0\n"
                + "  taskId=42 visible=true topActivity=com.other/.Previous\n";

        assertTrue(HostedTaskParser.containsTaskOnDisplay(stackList, 12, 42));
        assertFalse(HostedTaskParser.containsTaskOnDisplay(stackList, 12, 41));
        assertFalse(HostedTaskParser.containsTaskOnDisplay(stackList, 0, 42));
    }

    @Test
    public void emptyTaskShellDoesNotCountAsALiveHostedActivity() {
        String stackList = "RootTask id=8 displayId=12 userId=0\n"
                + "  taskId=42 visible=false topActivity=null\n"
                + "  taskId=43 unknown visible=false\n";

        assertFalse(HostedTaskParser.containsTaskOnDisplay(stackList, 12, 42));
        assertFalse(HostedTaskParser.containsTaskOnDisplay(stackList, 12, 43));
    }

    @Test
    public void componentInfoTopActivityCountsAsLive() {
        String stackList = "RootTask id=8 displayId=12 userId=0\n"
                + "  taskId=42 visible=true "
                + "topActivity=ComponentInfo{com.other/.Previous}\n";

        assertTrue(HostedTaskParser.containsTaskOnDisplay(stackList, 12, 42));
    }

    @Test
    public void findsVisibleTopActivityOnExactDisplay() {
        String stackList = "Stack id=4 displayId=12 userId=0\n"
                + "  taskId=42 visible=true topActivity=ComponentInfo{"
                + "com.tencent.mobileqq/.activity.ForwardRecentActivity}\n"
                + "Stack id=5 displayId=13 userId=0\n"
                + "  taskId=43 visible=true topActivity=com.tencent.mobileqq/"
                + ".activity.SplashActivity\n";

        assertEquals("com.tencent.mobileqq/.activity.ForwardRecentActivity",
                HostedTaskParser.findVisibleTopActivity(
                        stackList, 12, "com.tencent.mobileqq"));
        assertEquals("com.tencent.mobileqq/.activity.SplashActivity",
                HostedTaskParser.findVisibleTopActivity(
                        stackList, 13, "com.tencent.mobileqq"));
        assertEquals("", HostedTaskParser.findVisibleTopActivity(
                stackList, 12, "com.tencent.mm"));
    }

    @Test
    public void findsActivityCountForExactTask() {
        String activities = "* Task{root #333 type=home sz=1}\n"
                + "  * Task{app #42 type=standard visible=true sz=1}\n"
                + "  * Task{other #420 type=standard visible=true sz=3}\n";

        assertEquals(1, HostedTaskParser.findTaskActivityCount(activities, 42));
        assertEquals(3, HostedTaskParser.findTaskActivityCount(activities, 420));
    }

    @Test
    public void returnsUnknownForMissingTaskSize() {
        assertEquals(-1, HostedTaskParser.findTaskActivityCount(
                "* Task{app #42 type=standard}", 42));
        assertEquals(-1, HostedTaskParser.findTaskActivityCount(
                "* Task{app #42 type=standard sz=1}", 41));
    }

    @Test
    public void parsesOnlyDigitsImmediatelyAfterMarker() {
        assertEquals(123, HostedTaskParser.parseIntAfter("taskId=123 visible=true", "taskId="));
        assertEquals(-1, HostedTaskParser.parseIntAfter("taskId=none", "taskId="));
    }
}
