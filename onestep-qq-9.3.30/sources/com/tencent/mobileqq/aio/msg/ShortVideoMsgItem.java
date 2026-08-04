package com.tencent.mobileqq.aio.msg;

import android.content.Context;
import android.text.TextUtils;
import com.tencent.mobileqq.R;
import com.tencent.mobileqq.aio.msglist.holder.component.video.VideoViewModel;
import com.tencent.mobileqq.aio.msglist.holder.component.video.u;
import com.tencent.mobileqq.aio.utils.AIOMsgItemExtKt;
import com.tencent.mobileqq.app.HardCodeUtil;
import com.tencent.mobileqq.app.ThreadManagerV2;
import com.tencent.mobileqq.qfix.redirect.IPatchRedirector;
import com.tencent.mobileqq.qfix.redirect.PatchRedirectCenter;
import com.tencent.mobileqq.utils.FileUtils;
import com.tencent.qphone.base.util.QLog;
import com.tencent.qqnt.aio.adapter.api.IGuildTroopApi;
import com.tencent.qqnt.compress.api.IVideoCompressApi;
import com.tencent.qqnt.freesia_wrapper.FreesiaWrapperImpl;
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback;
import com.tencent.qqnt.kernel.nativeinterface.IVideoPlayUrlCallback;
import com.tencent.qqnt.kernel.nativeinterface.MsgElement;
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord;
import com.tencent.qqnt.kernel.nativeinterface.RMReqExParams;
import com.tencent.qqnt.kernel.nativeinterface.RichMediaElementGetReq;
import com.tencent.qqnt.kernel.nativeinterface.VideoCodecFormatType;
import com.tencent.qqnt.kernel.nativeinterface.VideoElement;
import com.tencent.qqnt.kernel.nativeinterface.VideoPlayUrlInfo;
import com.tencent.qqnt.kernel.nativeinterface.VideoPlayUrlResult;
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.collections.MapsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function5;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import mqq.app.AppRuntime;
import mqq.app.MobileQQ;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: P */
/* JADX INFO: loaded from: classes6.dex */
@SourceDebugExtension({"SMAP\nShortVideoMsgItem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ShortVideoMsgItem.kt\ncom/tencent/mobileqq/aio/msg/ShortVideoMsgItem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,478:1\n1855#2,2:479\n1855#2,2:481\n1855#2,2:483\n*S KotlinDebug\n*F\n+ 1 ShortVideoMsgItem.kt\ncom/tencent/mobileqq/aio/msg/ShortVideoMsgItem\n*L\n431#1:479,2\n437#1:481,2\n443#1:483,2\n*E\n"})
public class ShortVideoMsgItem extends RichMediaMsgItem {
    static IPatchRedirector $redirector_;

    /* JADX INFO: renamed from: h1, reason: collision with root package name */
    @NotNull
    private final Lazy f200965h1;

    /* JADX INFO: renamed from: i1, reason: collision with root package name */
    @Nullable
    private String f200966i1;

    /* JADX INFO: renamed from: j1, reason: collision with root package name */
    @Nullable
    private String f200967j1;

    /* JADX INFO: renamed from: k1, reason: collision with root package name */
    @Nullable
    private String f200968k1;

    /* JADX INFO: renamed from: l1, reason: collision with root package name */
    private int f200969l1;

    /* JADX INFO: renamed from: m1, reason: collision with root package name */
    @Nullable
    private VideoPlayUrlResult f200970m1;

    /* JADX INFO: renamed from: n1, reason: collision with root package name */
    private int f200971n1;

    /* JADX INFO: renamed from: o1, reason: collision with root package name */
    @NotNull
    private VideoViewModel f200972o1;

    /* JADX INFO: compiled from: P */
    public static final class a {
        static IPatchRedirector $redirector_;

        public a(DefaultConstructorMarker defaultConstructorMarker) {
            IPatchRedirector iPatchRedirector = $redirector_;
            if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 1)) {
                iPatchRedirector.redirect((short) 1, (Object) this);
            }
            IPatchRedirector iPatchRedirector2 = $redirector_;
            if (iPatchRedirector2 == null || !iPatchRedirector2.hasPatch((short) 2)) {
                return;
            }
            iPatchRedirector2.redirect((short) 2, (Object) this, (Object) defaultConstructorMarker);
        }
    }

    static {
        IPatchRedirector redirector = PatchRedirectCenter.getRedirector(77435);
        $redirector_ = redirector;
        if (redirector == null || !redirector.hasPatch((short) 47)) {
            new a(null);
        } else {
            redirector.redirect((short) 47);
        }
    }

    ShortVideoMsgItem(MsgRecord msgRecord, boolean z15) {
        super(msgRecord);
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 1)) {
            iPatchRedirector.redirect((short) 1, this, msgRecord, Boolean.valueOf(z15));
            return;
        }
        this.f200965h1 = LazyKt.lazy(new Function0<com.tencent.mobileqq.aio.msglist.holder.component.video.a>() { // from class: com.tencent.mobileqq.aio.msg.ShortVideoMsgItem$msgExtInfo$2
            static IPatchRedirector $redirector_;

            {
                super(0);
                IPatchRedirector iPatchRedirector2 = $redirector_;
                if (iPatchRedirector2 == null || !iPatchRedirector2.hasPatch((short) 1)) {
                    return;
                }
                iPatchRedirector2.redirect((short) 1, (Object) this, (Object) this.this$0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            @NotNull
            public final com.tencent.mobileqq.aio.msglist.holder.component.video.a invoke() {
                IPatchRedirector iPatchRedirector2 = $redirector_;
                if (iPatchRedirector2 != null && iPatchRedirector2.hasPatch((short) 2)) {
                    return (com.tencent.mobileqq.aio.msglist.holder.component.video.a) iPatchRedirector2.redirect((short) 2, (Object) this);
                }
                ShortVideoMsgItem shortVideoMsgItem = this.this$0;
                IPatchRedirector iPatchRedirector3 = ShortVideoMsgItem.$redirector_;
                shortVideoMsgItem.getClass();
                boolean zIsSwitchOn = FreesiaWrapperImpl.f405090a.b().isSwitchOn("106123", true);
                QLog.i("ShortVideoMsgItem", 2, "[getMsgExtInfo] isSwitchOn:" + zIsSwitchOn);
                if (!zIsSwitchOn) {
                    return com.tencent.mobileqq.aio.msglist.holder.component.video.a.f204109d.a(shortVideoMsgItem.Q2().extBufForUI);
                }
                MsgElement firstTypeElement = shortVideoMsgItem.getFirstTypeElement(5);
                if (firstTypeElement == null) {
                    QLog.w("ShortVideoMsgItem", 1, "init msgExtInfo. cannot find video element.");
                }
                return com.tencent.mobileqq.aio.msglist.holder.component.video.a.f204109d.a(firstTypeElement != null ? firstTypeElement.extBufForUI : null);
            }
        });
        this.f200971n1 = msgRecord.sendStatus;
        this.f200972o1 = VideoViewModel.Normal;
    }

    public static void J2(ShortVideoMsgItem this$0, Function5 cb5, int i15, String str, VideoPlayUrlResult videoPlayUrlResult) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(cb5, "$cb");
        if (i15 == 0) {
            Intrinsics.checkNotNull(videoPlayUrlResult);
            this$0.getClass();
            ArrayList<VideoPlayUrlInfo> domainUrl = videoPlayUrlResult.domainUrl;
            Intrinsics.checkNotNullExpressionValue(domainUrl, "domainUrl");
            if (!domainUrl.isEmpty()) {
                String str2 = videoPlayUrlResult.domainUrl.get(0).url;
                QLog.i("ShortVideoMsgItem", 1, "[getVideoPlayUrl] success, err=" + i15 + ", " + str + ", videoDownloadResponseCodecFormat=" + videoPlayUrlResult.videoCodecFormat + ", url=" + str2);
                this$0.f200968k1 = str2;
                u.a aVar = com.tencent.mobileqq.aio.msglist.holder.component.video.u.f204149e;
                this$0.f200969l1 = aVar.a(videoPlayUrlResult.videoCodecFormat);
                this$0.f200970m1 = videoPlayUrlResult;
                Integer numValueOf = Integer.valueOf(i15);
                Intrinsics.checkNotNull(str);
                cb5.invoke(numValueOf, str, this$0.f200968k1, this$0.f200970m1, Integer.valueOf(aVar.a(videoPlayUrlResult.videoCodecFormat)));
                return;
            }
        }
        QLog.i("ShortVideoMsgItem", 1, "[getVideoPlayUrl] failed, err=" + i15 + "," + str);
        Integer numValueOf2 = Integer.valueOf(i15);
        Intrinsics.checkNotNull(str);
        cb5.invoke(numValueOf2, str, null, null, Integer.valueOf(com.tencent.mobileqq.aio.msglist.holder.component.video.u.f204149e.a(videoPlayUrlResult.videoCodecFormat)));
    }

    @Nullable
    public final String K2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 30)) {
            return (String) iPatchRedirector.redirect((short) 30, (Object) this);
        }
        String strI3 = i3();
        if (strI3 != null) {
            return strI3;
        }
        String strT2 = T2();
        if (new File(strT2).exists()) {
            this.f200967j1 = strT2;
            return strT2;
        }
        String strF = isSelf() ? R2().c().f() : "";
        if (TextUtils.isEmpty(strF) || !new File(strF).exists()) {
            return null;
        }
        this.f200967j1 = strF;
        return null;
    }

    @Nullable
    public final String L2() throws IOException {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 32)) {
            return (String) iPatchRedirector.redirect((short) 32, (Object) this);
        }
        String strJ3 = j3();
        if (strJ3 != null) {
            return strJ3;
        }
        String strY2 = Y2();
        File file = new File(strY2);
        if (file.exists()) {
            this.f200966i1 = strY2;
            return strY2;
        }
        String parent = file.getParent();
        if (parent == null) {
            return null;
        }
        FileUtils.createDirectory(parent);
        return null;
    }

    public final void M2(final boolean z15) {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 43)) {
            ThreadManagerV2.excute(new Runnable() { // from class: com.tencent.mobileqq.aio.msg.ah
                @Override // java.lang.Runnable
                public final void run() {
                    ShortVideoMsgItem this$0 = this.f201045d;
                    boolean z16 = z15;
                    Intrinsics.checkNotNullParameter(this$0, "this$0");
                    MsgElement firstTypeElement = this$0.getFirstTypeElement(5);
                    if (firstTypeElement == null) {
                        QLog.w("ShortVideoMsgItem", 1, "[downloadThumb] no video element found, msgId=" + this$0.getMsgId());
                        return;
                    }
                    if (this$0.d3()) {
                        QLog.i("ShortVideoMsgItem", 1, "resource is expired, msgId=" + this$0.getMsgId() + ", elemId=" + firstTypeElement.elementId);
                    }
                    RichMediaElementGetReq richMediaElementGetReq = new RichMediaElementGetReq();
                    richMediaElementGetReq.msgId = this$0.A0();
                    richMediaElementGetReq.peerUid = this$0.B0();
                    richMediaElementGetReq.chatType = this$0.z0();
                    richMediaElementGetReq.elementId = firstTypeElement.elementId;
                    richMediaElementGetReq.downloadType = 2;
                    richMediaElementGetReq.thumbSize = 0;
                    richMediaElementGetReq.downSourceType = 1;
                    richMediaElementGetReq.triggerType = !z16 ? 1 : 0;
                    com.tencent.qqnt.kernel.api.ac acVarE = com.tencent.qqnt.msg.f.e();
                    if (acVarE != null) {
                        acVarE.getRichMediaElement(richMediaElementGetReq);
                    }
                    QLog.i("ShortVideoMsgItem", 1, "[downloadThumb] start downloading, msgId=" + richMediaElementGetReq.msgId + ", elemId={" + firstTypeElement.elementId + "}");
                }
            }, 16, null, false);
        } else {
            iPatchRedirector.redirect((short) 43, (Object) this, z15);
        }
    }

    public final int N2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 5)) ? this.f200969l1 : ((Integer) iPatchRedirector.redirect((short) 5, (Object) this)).intValue();
    }

    @Nullable
    public final VideoPlayUrlResult O2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 7)) ? this.f200970m1 : (VideoPlayUrlResult) iPatchRedirector.redirect((short) 7, (Object) this);
    }

    @Nullable
    public final String P2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 3)) ? this.f200968k1 : (String) iPatchRedirector.redirect((short) 3, (Object) this);
    }

    @NotNull
    public final MsgElement Q2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 23)) {
            return (MsgElement) iPatchRedirector.redirect((short) 23, (Object) this);
        }
        MsgElement firstTypeElement = getFirstTypeElement(5);
        Intrinsics.checkNotNull(firstTypeElement);
        return firstTypeElement;
    }

    @NotNull
    public final com.tencent.mobileqq.aio.msglist.holder.component.video.a R2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 2)) ? (com.tencent.mobileqq.aio.msglist.holder.component.video.a) this.f200965h1.getValue() : (com.tencent.mobileqq.aio.msglist.holder.component.video.a) iPatchRedirector.redirect((short) 2, (Object) this);
    }

    @NotNull
    public final String S2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 28)) ? com.tencent.mobileqq.aio.msglist.holder.component.video.b.f204113a.c(getMsgRecord()) : (String) iPatchRedirector.redirect((short) 28, (Object) this);
    }

    @NotNull
    public final String T2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 27)) {
            return (String) iPatchRedirector.redirect((short) 27, (Object) this);
        }
        IGuildTroopApi iGuildTroopApi = (IGuildTroopApi) com.tencent.qqnt.aio.adapter.a.f396099c.a(IGuildTroopApi.class);
        String channelId = getMsgRecord().channelId;
        Intrinsics.checkNotNullExpressionValue(channelId, "channelId");
        return String.valueOf(!iGuildTroopApi.isGuildTroop2(channelId) ? com.tencent.mobileqq.aio.msglist.holder.component.video.b.f204113a.d(getMsgRecord()) : com.tencent.mobileqq.aio.utils.an.k(U2()));
    }

    @NotNull
    public final VideoElement U2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 24)) {
            return (VideoElement) iPatchRedirector.redirect((short) 24, (Object) this);
        }
        VideoElement videoElement = Q2().videoElement;
        Intrinsics.checkNotNullExpressionValue(videoElement, "videoElement");
        return videoElement;
    }

    public final long V2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 25)) ? ((long) U2().fileTime) * ((long) 1000) : ((Long) iPatchRedirector.redirect((short) 25, (Object) this)).longValue();
    }

    @NotNull
    public final String W2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 35)) ? com.tencent.mobileqq.aio.msglist.holder.component.video.b.f204113a.f(getMsgRecord()) : (String) iPatchRedirector.redirect((short) 35, (Object) this);
    }

    @NotNull
    public final String X2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 34)) ? com.tencent.mobileqq.aio.msglist.holder.component.video.b.f204113a.g(getMsgRecord()) : (String) iPatchRedirector.redirect((short) 34, (Object) this);
    }

    @NotNull
    public final String Y2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 33)) {
            return (String) iPatchRedirector.redirect((short) 33, (Object) this);
        }
        IGuildTroopApi iGuildTroopApi = (IGuildTroopApi) com.tencent.qqnt.aio.adapter.a.f396099c.a(IGuildTroopApi.class);
        String channelId = getMsgRecord().channelId;
        Intrinsics.checkNotNullExpressionValue(channelId, "channelId");
        return String.valueOf(!iGuildTroopApi.isGuildTroop2(channelId) ? com.tencent.mobileqq.aio.msglist.holder.component.video.b.f204113a.h(getMsgRecord()) : com.tencent.mobileqq.aio.utils.an.h(U2()));
    }

    @NotNull
    public final String Z2() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 36)) {
            return (String) iPatchRedirector.redirect((short) 36, (Object) this);
        }
        return com.tencent.mobileqq.aio.msglist.holder.component.video.b.f204113a.i(getMsgRecord()) + "_" + R2().a().b();
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem
    public int a0() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 9)) ? this.f200971n1 : ((Integer) iPatchRedirector.redirect((short) 9, (Object) this)).intValue();
    }

    @NotNull
    public final VideoViewModel a3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 12)) ? this.f200972o1 : (VideoViewModel) iPatchRedirector.redirect((short) 12, (Object) this);
    }

    public final boolean b3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 38)) {
            return ((Boolean) iPatchRedirector.redirect((short) 38, (Object) this)).booleanValue();
        }
        if (!R2().c().i()) {
            QLog.w("ShortVideoMsgItem", 1, "[initVideoElementBySendInfo] invalid send info, msgId=" + getMsgId());
            return false;
        }
        com.tencent.mobileqq.aio.msglist.holder.component.video.z zVarC = R2().c();
        VideoElement videoElementU2 = U2();
        videoElementU2.filePath = zVarC.c();
        videoElementU2.thumbPath = MapsKt.hashMapOf(new Pair(0, zVarC.f()));
        videoElementU2.thumbHeight = zVarC.d();
        videoElementU2.thumbWidth = zVarC.h();
        videoElementU2.thumbMd5 = zVarC.e();
        videoElementU2.thumbSize = (int) zVarC.g();
        this.f200967j1 = zVarC.f();
        this.f200966i1 = zVarC.c();
        return true;
    }

    public final boolean c3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 37)) {
            return ((Boolean) iPatchRedirector.redirect((short) 37, (Object) this)).booleanValue();
        }
        VideoElement videoElementU2 = U2();
        return (videoElementU2.fileSize == 0 || TextUtils.isEmpty(videoElementU2.fileName) || TextUtils.isEmpty(videoElementU2.videoMd5)) ? false : true;
    }

    public final boolean d3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 26)) {
            return ((Boolean) iPatchRedirector.redirect((short) 26, (Object) this)).booleanValue();
        }
        Integer num = U2().invalidState;
        return num == null || num.intValue() != 0;
    }

    public final boolean e3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 16)) ? a0() == 0 : ((Boolean) iPatchRedirector.redirect((short) 16, (Object) this)).booleanValue();
    }

    public final boolean f3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 15)) ? a0() == 2 || a0() == 3 : ((Boolean) iPatchRedirector.redirect((short) 15, (Object) this)).booleanValue();
    }

    public final boolean g3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 14)) ? a0() == 1 : ((Boolean) iPatchRedirector.redirect((short) 14, (Object) this)).booleanValue();
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem, com.tencent.aio.data.msglist.a
    @Nullable
    public Object getChangePayload(@NotNull com.tencent.aio.data.msglist.a target) {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 46)) {
            return iPatchRedirector.redirect((short) 46, (Object) this, (Object) target);
        }
        Intrinsics.checkNotNullParameter(target, "target");
        if (target instanceof AIOMsgItem) {
            AIOMsgItem aIOMsgItem = (AIOMsgItem) target;
            if (!com.tencent.qqnt.aio.msg.d.o(this, aIOMsgItem)) {
                QLog.i("ShortVideoMsgItem", 1, "[getChangePayload] fileTransNotifyInfo=" + aIOMsgItem.getFileTransNotifyInfo());
                return null;
            }
        }
        return super.getChangePayload(target);
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem, com.tencent.aio.data.msglist.a
    public int getViewType() {
        IPatchRedirector iPatchRedirector = $redirector_;
        return (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 41)) ? getMsgRecord().msgType == 7 ? isSelf() ? 11 : 10 : super.getViewType() : ((Integer) iPatchRedirector.redirect((short) 41, (Object) this)).intValue();
    }

    public final boolean h3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 17)) {
            return ((Boolean) iPatchRedirector.redirect((short) 17, (Object) this)).booleanValue();
        }
        if (isSelf()) {
            return R2().c().i();
        }
        return false;
    }

    @Nullable
    public final String i3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 29)) {
            return (String) iPatchRedirector.redirect((short) 29, (Object) this);
        }
        String str = this.f200967j1;
        if (str == null) {
            return null;
        }
        if (!Intrinsics.areEqual(str, T2())) {
            if (!Intrinsics.areEqual(this.f200967j1, isSelf() ? R2().c().f() : "")) {
                return null;
            }
        }
        return this.f200967j1;
    }

    @Nullable
    public final String j3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 31)) {
            return (String) iPatchRedirector.redirect((short) 31, (Object) this);
        }
        String str = this.f200966i1;
        if (str == null || !Intrinsics.areEqual(str, Y2())) {
            return null;
        }
        return this.f200966i1;
    }

    public final void k3(final boolean z15, final int i15, @NotNull final Function5<? super Integer, ? super String, ? super String, ? super VideoPlayUrlResult, ? super Integer, Unit> cb5) {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 45)) {
            iPatchRedirector.redirect((short) 45, this, Boolean.valueOf(z15), Integer.valueOf(i15), cb5);
            return;
        }
        Intrinsics.checkNotNullParameter(cb5, "cb");
        if (TextUtils.isEmpty(this.f200968k1)) {
            ThreadManagerV2.excute(new Runnable() { // from class: com.tencent.mobileqq.aio.msg.ai
                @Override // java.lang.Runnable
                public final void run() {
                    final ShortVideoMsgItem this$0 = this.f201047d;
                    boolean z16 = z15;
                    int i16 = i15;
                    final Function5 cb6 = cb5;
                    Intrinsics.checkNotNullParameter(this$0, "this$0");
                    Intrinsics.checkNotNullParameter(cb6, "$cb");
                    u.a aVar = com.tencent.mobileqq.aio.msglist.holder.component.video.u.f204149e;
                    IVideoCompressApi iVideoCompressApi = (IVideoCompressApi) com.tencent.qqnt.aio.adapter.a.f396099c.a(IVideoCompressApi.class);
                    AppRuntime appRuntimePeekAppRuntime = MobileQQ.sMobileQQ.peekAppRuntime();
                    Intrinsics.checkNotNullExpressionValue(appRuntimePeekAppRuntime, "peekAppRuntime(...)");
                    VideoCodecFormatType videoCodecFormatTypeB = aVar.b(Integer.valueOf(iVideoCompressApi.getVideoDownloadRequestCodecFormat(appRuntimePeekAppRuntime, this$0.U2().busiType, this$0.U2().thumbWidth, this$0.U2().thumbHeight, this$0.U2().thumbWidth, this$0.U2().thumbHeight, this$0.R2().a())));
                    QLog.d("ShortVideoMsgItem", 1, "[requestVideoPlayUrl] targetVideoCodecFormat:" + videoCodecFormatTypeB, ", formatInfo=" + this$0.R2().a() + ", md5=" + this$0.U2().videoMd5 + ", fileUuid=" + this$0.U2().fileUuid);
                    RMReqExParams rMReqExParams = new RMReqExParams(i16, !z16 ? 1 : 0);
                    com.tencent.qqnt.kernel.api.ai aiVarG = com.tencent.qqnt.msg.f.g();
                    if (aiVarG != null) {
                        aiVarG.getVideoPlayUrlV2(new Contact(this$0.z0(), this$0.B0(), ""), this$0.A0(), this$0.Q2().elementId, videoCodecFormatTypeB, rMReqExParams, new IVideoPlayUrlCallback() { // from class: com.tencent.mobileqq.aio.msg.ak
                            @Override // com.tencent.qqnt.kernel.nativeinterface.IVideoPlayUrlCallback
                            public final void onResult(int i17, String str, VideoPlayUrlResult videoPlayUrlResult) {
                                ShortVideoMsgItem.J2(this$0, cb6, i17, str, videoPlayUrlResult);
                            }
                        });
                    }
                }
            }, 16, null, false);
        } else {
            cb5.invoke(0, "", this.f200968k1, this.f200970m1, Integer.valueOf(this.f200969l1));
        }
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem
    @NotNull
    public AIOMsgItem l0(@NotNull MsgRecord targetMsgRecord) {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 21)) {
            return (AIOMsgItem) iPatchRedirector.redirect((short) 21, (Object) this, (Object) targetMsgRecord);
        }
        Intrinsics.checkNotNullParameter(targetMsgRecord, "targetMsgRecord");
        return new ShortVideoMsgItem(targetMsgRecord, true);
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem
    public void l1(@Nullable Context context) throws IOException {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 19)) {
            iPatchRedirector.redirect((short) 19, (Object) this, (Object) context);
            return;
        }
        super.l1(context);
        R2();
        K2();
        L2();
    }

    public final void l3() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 18)) {
            iPatchRedirector.redirect((short) 18, (Object) this);
            return;
        }
        MsgElement msgElementQ2 = Q2();
        com.tencent.qqnt.kernel.api.ac acVarE = com.tencent.qqnt.msg.f.e();
        if (acVarE != null) {
            acVarE.updateElementExtBufForUI(new Contact(z0(), B0(), ""), A0(), msgElementQ2.elementId, R2().d(), new IOperateCallback() { // from class: com.tencent.mobileqq.aio.msg.aj
                @Override // com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
                public final void onResult(int i15, String str) {
                    ShortVideoMsgItem this$0 = this.f201051d;
                    Intrinsics.checkNotNullParameter(this$0, "this$0");
                    if (i15 != 0) {
                        QLog.w("ShortVideoMsgItem", 1, "[serializeExtInfo] fail, err=" + i15 + ", " + str + ", " + this$0.R2().a());
                    }
                }
            });
        }
    }

    public final void m3(@NotNull VideoViewModel model) {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 11)) {
            iPatchRedirector.redirect((short) 11, (Object) this, (Object) model);
            return;
        }
        Intrinsics.checkNotNullParameter(model, "model");
        QLog.i("ShortVideoMsgItem", 1, "[setVideoViewModel] model=" + model);
        this.f200972o1 = model;
    }

    public final void n3(int i15) {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 10)) {
            iPatchRedirector.redirect((short) 10, (Object) this, i15);
            return;
        }
        QLog.i("ShortVideoMsgItem", 1, "[updateMsgSendStatus] msgId=" + getMsgId() + " status=" + this.f200971n1 + "->" + i15);
        this.f200971n1 = i15;
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem
    public void q(@NotNull AIOMsgItem newMsgItem) {
        VideoPlayUrlResult videoPlayUrlResult;
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 22)) {
            iPatchRedirector.redirect((short) 22, (Object) this, (Object) newMsgItem);
            return;
        }
        Intrinsics.checkNotNullParameter(newMsgItem, "newMsgItem");
        super.q(newMsgItem);
        ShortVideoMsgItem shortVideoMsgItem = (ShortVideoMsgItem) newMsgItem;
        shortVideoMsgItem.f200968k1 = this.f200968k1;
        VideoPlayUrlResult videoPlayUrlResult2 = this.f200970m1;
        if (videoPlayUrlResult2 != null) {
            ArrayList arrayList = new ArrayList();
            ArrayList<VideoPlayUrlInfo> arrayList2 = videoPlayUrlResult2.v4IpUrl;
            if (arrayList2 != null) {
                Intrinsics.checkNotNull(arrayList2);
                for (VideoPlayUrlInfo videoPlayUrlInfo : arrayList2) {
                    arrayList.add(new VideoPlayUrlInfo(videoPlayUrlInfo.url, videoPlayUrlInfo.isHttps, videoPlayUrlInfo.httpsDomain));
                }
            }
            ArrayList arrayList3 = new ArrayList();
            ArrayList<VideoPlayUrlInfo> arrayList4 = videoPlayUrlResult2.v6IpUrl;
            if (arrayList4 != null) {
                Intrinsics.checkNotNull(arrayList4);
                for (VideoPlayUrlInfo videoPlayUrlInfo2 : arrayList4) {
                    arrayList3.add(new VideoPlayUrlInfo(videoPlayUrlInfo2.url, videoPlayUrlInfo2.isHttps, videoPlayUrlInfo2.httpsDomain));
                }
            }
            ArrayList arrayList5 = new ArrayList();
            ArrayList<VideoPlayUrlInfo> arrayList6 = videoPlayUrlResult2.domainUrl;
            if (arrayList6 != null) {
                Intrinsics.checkNotNull(arrayList6);
                for (VideoPlayUrlInfo videoPlayUrlInfo3 : arrayList6) {
                    arrayList5.add(new VideoPlayUrlInfo(videoPlayUrlInfo3.url, videoPlayUrlInfo3.isHttps, videoPlayUrlInfo3.httpsDomain));
                }
            }
            videoPlayUrlResult = new VideoPlayUrlResult(arrayList, arrayList3, arrayList5, videoPlayUrlResult2.videoCodecFormat);
        } else {
            videoPlayUrlResult = null;
        }
        shortVideoMsgItem.f200970m1 = videoPlayUrlResult;
        shortVideoMsgItem.f200967j1 = this.f200967j1;
        shortVideoMsgItem.f200966i1 = this.f200966i1;
    }

    @Override // com.tencent.mobileqq.aio.msg.AIOMsgItem
    @NotNull
    public String w1() {
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector != null && iPatchRedirector.hasPatch((short) 20)) {
            return (String) iPatchRedirector.redirect((short) 20, (Object) this);
        }
        if (isSelf()) {
            return HardCodeUtil.qqStr(R.string.w_7) + HardCodeUtil.qqStr(R.string.w_9) + HardCodeUtil.qqStr(R.string.w_a);
        }
        if (AIOMsgItemExtKt.m(this)) {
            return getMsgRecord().anonymousExtInfo.anonymousNick + HardCodeUtil.qqStr(R.string.w_8) + HardCodeUtil.qqStr(R.string.w_a);
        }
        CharSequence charSequenceF = F();
        return ((Object) charSequenceF) + HardCodeUtil.qqStr(R.string.w_8) + HardCodeUtil.qqStr(R.string.w_a);
    }

    /* JADX WARN: 'this' call moved to the top of the method (can break code semantics) */
    public ShortVideoMsgItem(@NotNull MsgRecord msgRecord) {
        this(msgRecord, false);
        Intrinsics.checkNotNullParameter(msgRecord, "msgRecord");
        IPatchRedirector iPatchRedirector = $redirector_;
        if (iPatchRedirector == null || !iPatchRedirector.hasPatch((short) 13)) {
            return;
        }
        iPatchRedirector.redirect((short) 13, (Object) this, (Object) msgRecord);
    }
}
