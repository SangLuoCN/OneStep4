package com.sangluo.onestep.feature.drag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageShareTargetPolicyTest {
    @Test
    public void qqUsesOnlyFriendShareActivity() {
        assertEquals("com.tencent.mobileqq.activity.JumpActivity",
                ImageShareTargetPolicy.requiredActivity("com.tencent.mobileqq"));
    }

    @Test
    public void wechatUsesOnlyFriendShareActivity() {
        assertEquals("com.tencent.mm.ui.tools.ShareImgUI",
                ImageShareTargetPolicy.requiredActivity("com.tencent.mm"));
    }

    @Test
    public void otherPackagesUseTheirFirstSystemShareTarget() {
        assertNull(ImageShareTargetPolicy.requiredActivity("com.example.share"));
    }

    @Test
    public void qqWaitsForFriendPickerBeforePromotion() {
        assertFalse(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.tencent.mobileqq.activity.JumpActivity"));
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.tencent.mobileqq.activity.ForwardRecentActivity"));
    }

    @Test
    public void qqDirectShareRouteCanPromoteItsOwnUi() {
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.qqfav.FavoriteIpcDelegate",
                "cooperation.qqfav.widget.QfavJumpActivity"));
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.tencent.mobileqq.activity.qfileJumpActivity",
                "com.tencent.mobileqq.activity.qfileJumpActivity"));
    }

    @Test
    public void dragShareTargetsUseExplicitSystemShareActivities() {
        assertEquals("com.tencent.mm.ui.tools.ShareToTimeLineUI",
                ImageDragShareTarget.WECHAT_TIMELINE.activityName());
        assertEquals("com.tencent.mm.ui.tools.AddFavoriteUI",
                ImageDragShareTarget.WECHAT_FAVORITE.activityName());
        assertEquals("cooperation.qqfav.widget.QfavJumpActivity",
                ImageDragShareTarget.QQ_FAVORITE.activityName());
        assertEquals("com.tencent.mobileqq.activity.qfileJumpActivity",
                ImageDragShareTarget.QQ_COMPUTER.activityName());
        assertEquals("com.android.bluetooth.opp.BluetoothOppLauncherActivity",
                ImageDragShareTarget.BLUETOOTH.activityName());
    }
}
