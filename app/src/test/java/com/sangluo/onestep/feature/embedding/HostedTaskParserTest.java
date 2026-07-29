package com.sangluo.onestep.feature.embedding;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    }

    @Test
    public void rejectsOtherDisplaysAndPackages() {
        assertEquals(-1, HostedTaskParser.findHostedTaskId(
                STACK_LIST, 0, "com.example.player"));
        assertEquals(-1, HostedTaskParser.findHostedTaskId(STACK_LIST, 12, "com.missing"));
    }

    @Test
    public void parsesOnlyDigitsImmediatelyAfterMarker() {
        assertEquals(123, HostedTaskParser.parseIntAfter("taskId=123 visible=true", "taskId="));
        assertEquals(-1, HostedTaskParser.parseIntAfter("taskId=none", "taskId="));
    }
}
