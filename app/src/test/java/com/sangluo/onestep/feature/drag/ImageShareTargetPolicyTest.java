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
        assertFalse(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/cooperation.qqfav.widget.QfavJumpActivity",
                "cooperation.qqfav.widget.QfavJumpActivity"));
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.qqfav.FavoriteIpcDelegate",
                "cooperation.qqfav.widget.QfavJumpActivity"));
        assertFalse(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.tencent.mobileqq.activity.qfileJumpActivity",
                "com.tencent.mobileqq.activity.qfileJumpActivity"));
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq/com.tencent.mobileqq.activity.ChatActivity",
                "com.tencent.mobileqq.activity.qfileJumpActivity"));
    }

    @Test
    public void wechatDirectShareWaitsPastLauncherUi() {
        assertFalse(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mm",
                "com.tencent.mm/com.tencent.mm.ui.LauncherUI",
                "com.tencent.mm.ui.tools.ShareToTimeLineUI"));
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mm",
                "com.tencent.mm/com.tencent.mm.plugin.sns.ui.SnsUploadUI",
                "com.tencent.mm.ui.tools.ShareToTimeLineUI"));
        assertTrue(ImageShareTargetPolicy.isShareUiReady(
                "com.tencent.mm",
                "com.tencent.mm/com.tencent.mm.ui.tools.AddFavoriteUI",
                "com.tencent.mm.ui.tools.AddFavoriteUI"));
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

    @Test
    public void appShareTargetsInitializeBeforeColdStartShare() {
        assertTrue(ImageDragShareTarget.WECHAT_TIMELINE.initializesAppBeforeColdStartShare());
        assertTrue(ImageDragShareTarget.WECHAT_FAVORITE.initializesAppBeforeColdStartShare());
        assertTrue(ImageDragShareTarget.QQ_FAVORITE.initializesAppBeforeColdStartShare());
        assertTrue(ImageDragShareTarget.QQ_COMPUTER.initializesAppBeforeColdStartShare());
        assertFalse(ImageDragShareTarget.BLUETOOTH.initializesAppBeforeColdStartShare());
    }
}
