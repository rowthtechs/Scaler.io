package com.mmp.android.mpmetrics;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.URLUtil;

import com.mmp.android.util.MmpLog;
import com.mmp.android.mpmetrics.MmpNotificationData.PushTapActionType;

import org.json.JSONException;
import org.json.JSONObject;

public class MmpNotificationRouteActivity extends Activity {

    protected final String LOGTAG = "MmpAPI.MmpNotificationRouteActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent routeIntent = getIntent();

        if (null == routeIntent || null == routeIntent.getExtras()) {
            MmpLog.d(LOGTAG, "Notification route activity given null intent or null extras.");
            return;
        }

        Bundle extras = routeIntent.getExtras();

        trackTapAction(routeIntent);

        final Intent notificationIntent = handleRouteIntent(routeIntent);

        if (!extras.getBoolean("mp_is_sticky")) {
            MmpFCMMessagingService fcmMessagingService = new MmpFCMMessagingService();
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
            fcmMessagingService.cancelNotification(extras, notificationManager);
        }
        finish();
        startActivity(notificationIntent);
    }

    protected Intent handleRouteIntent(Intent routeIntent) {
        CharSequence actionTypeChars = routeIntent.getExtras().getCharSequence("mp_tap_action_type");
        PushTapActionType target;
        if (null == actionTypeChars) {
            MmpLog.d(LOGTAG, "Notification action click logged with no action type");
            target = PushTapActionType.HOMESCREEN;
        } else {
            target = PushTapActionType.fromString(actionTypeChars.toString());
        }

        CharSequence uri = routeIntent.getExtras().getCharSequence("mp_tap_action_uri");

        final Intent defaultIntent = this.getPackageManager().getLaunchIntentForPackage(this.getPackageName());

        switch (target) {
            case HOMESCREEN:
                return defaultIntent;
            case URL_IN_BROWSER:
                if (URLUtil.isValidUrl(uri.toString())) {
                    return new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
                            .addCategory(Intent.CATEGORY_BROWSABLE);
                } else {
                    MmpLog.d(LOGTAG, "Wanted to open url in browser but url is invalid: " + uri.toString() + ". Starting default intent");
                    return defaultIntent;
                }
            case DEEP_LINK:
                return new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));
            default:
                return defaultIntent;
        }
    }

    protected void trackTapAction(Intent routeIntent) {
        final String tapTarget = routeIntent.getStringExtra("mp_tap_target");
        final String tapActionType = routeIntent.getStringExtra("mp_tap_action_type");
        final String tapActionUri = routeIntent.getStringExtra("mp_tap_action_uri");
        final Boolean sticky = routeIntent.getBooleanExtra("mp_is_sticky", false);
        final String buttonId;
        final String buttonLabel;
        if (tapTarget != null && tapTarget.equals(MmpPushNotification.TAP_TARGET_BUTTON)) {
            buttonId = routeIntent.getStringExtra("mp_button_id");
            buttonLabel = routeIntent.getStringExtra("mp_button_label");
        } else {
            buttonId = null;
            buttonLabel = null;
        }

        JSONObject additionalProperties = new JSONObject();
        try {
            additionalProperties.putOpt("$tap_target", tapTarget);
            additionalProperties.putOpt("$tap_action_type", tapActionType);
            additionalProperties.putOpt("$tap_action_uri", tapActionUri);
            additionalProperties.putOpt("$is_sticky", sticky);
            additionalProperties.putOpt("$button_id", buttonId);
            additionalProperties.putOpt("$button_label", buttonLabel);
        } catch (JSONException e) {
            MmpLog.e(LOGTAG, "Error adding tracking JSON properties.", e);
        }
        MmpAPI.trackPushNotificationEventFromIntent(
                getApplicationContext(),
                routeIntent,
                "$push_notification_tap",
                additionalProperties
        );
    }
}
