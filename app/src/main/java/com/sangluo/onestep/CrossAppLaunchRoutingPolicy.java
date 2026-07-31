package com.sangluo.onestep;

import android.content.Intent;

/** Keeps common activity-result contracts attached to their original caller task. */
final class CrossAppLaunchRoutingPolicy {
    private CrossAppLaunchRoutingPolicy() {
    }

    static boolean shouldPreserveCallerTask(String action, int flags) {
        if ((flags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) {
            return true;
        }
        if (action == null) {
            return false;
        }
        switch (action) {
            case Intent.ACTION_GET_CONTENT:
            case Intent.ACTION_OPEN_DOCUMENT:
            case Intent.ACTION_CREATE_DOCUMENT:
            case Intent.ACTION_OPEN_DOCUMENT_TREE:
            case Intent.ACTION_PICK:
            case Intent.ACTION_PICK_ACTIVITY:
            case "android.provider.action.PICK_IMAGES":
            case "android.provider.action.PICK_IMAGES_MAX":
            case "android.media.action.IMAGE_CAPTURE":
            case "android.media.action.IMAGE_CAPTURE_SECURE":
            case "android.media.action.VIDEO_CAPTURE":
            case "android.provider.MediaStore.RECORD_SOUND":
            case "android.intent.action.RINGTONE_PICKER":
            case "android.speech.action.RECOGNIZE_SPEECH":
                return true;
            default:
                return false;
        }
    }
}
