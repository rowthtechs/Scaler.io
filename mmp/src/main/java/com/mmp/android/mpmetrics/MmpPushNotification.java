package com.mmp.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import com.mmp.android.util.ImageStore;
import com.mmp.android.util.MmpLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MmpPushNotification {
    public final static String PUSH_TAP_ACTION = "com.mmp.push_notification_tap";
    public final static String PUSH_DISMISS_ACTION = "com.mmp.push_notification_dismissed";

    private final String LOGTAG = "MmpAPI.MmpPushNotification";

    protected final static String TAP_TARGET_BUTTON = "button";
    protected final static String TAP_TARGET_NOTIFICATION = "notification";

    private final static String VISIBILITY_PRIVATE = "VISIBILITY_PRIVATE";
    private final static String VISIBILITY_PUBLIC = "VISIBILITY_PUBLIC";
    private final static String VISIBILITY_SECRET = "VISIBILITY_SECRET";

    protected final int ROUTING_REQUEST_CODE;

    private static final String DATETIME_NO_TZ = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DATETIME_WITH_TZ = "yyyy-MM-dd'T'HH:mm:ssz";
    private static final String DATETIME_ZULU_TZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private Context mContext;
    private ResourceIds mDrawableIds;
    private Notification.Builder mBuilder;
    private long mNow;
    private MmpNotificationData mData;
    private int notificationId;
    private boolean hasOnTapError = false;

    @SuppressWarnings("deprecation")
    public MmpPushNotification(Context context) {
        this(context, new Notification.Builder(context), System.currentTimeMillis());
    }

    public MmpPushNotification(Context context, Notification.Builder builder, long now) {
        this.mContext = context;
        this.mBuilder = builder;
        this.mDrawableIds = getResourceIds(context);
        this.mNow = now;
        this.ROUTING_REQUEST_CODE = (int) now;
        this.notificationId = (int) now;
    }

    @SuppressWarnings("deprecation")
    /* package */ Notification createNotification(Intent inboundIntent) {
        parseIntent(inboundIntent);

        if (this.mData == null) {
            return null;
        }

        if (this.mData.isSilent()) {
            MmpLog.d(LOGTAG, "Notification will not be shown because \'mp_silent = true\'");
            return null;
        }

        if (this.mData.getMessage() == null) {
            MmpLog.d(LOGTAG, "Notification will not be shown because 'mp_message' was null");
            return null;
        }

        if (this.mData.getMessage().equals("")) {
            MmpLog.d(LOGTAG, "Notification will not be shown because 'mp_message' was empty");
            return null;
        }

        buildNotificationFromData();

        Notification n;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            n = mBuilder.build();
        } else {
            n = mBuilder.getNotification();
        }
        if (!mData.isSticky()) {
            n.flags |= Notification.FLAG_AUTO_CANCEL;
        }

        return n;
    }

    /* package */ void parseIntent(Intent inboundIntent) {
        List<MmpNotificationData.MmpNotificationButtonData> buttons = new ArrayList<>();
        final String message = inboundIntent.getStringExtra("mp_message");
        final String iconName = inboundIntent.getStringExtra("mp_icnm");
        final String largeIconName = inboundIntent.getStringExtra("mp_icnm_l");
        final String whiteIconName = inboundIntent.getStringExtra("mp_icnm_w");
        final String expandableImageURL = inboundIntent.getStringExtra("mp_img");
        final String uriString = inboundIntent.getStringExtra("mp_cta");
        final String onTapStr = inboundIntent.getStringExtra("mp_ontap");
        CharSequence notificationTitle = inboundIntent.getStringExtra("mp_title");
        CharSequence notificationSubText = inboundIntent.getStringExtra("mp_subtxt");
        final String colorName = inboundIntent.getStringExtra("mp_color");
        final String buttonsJsonStr = inboundIntent.getStringExtra("mp_buttons");
        final String campaignId = inboundIntent.getStringExtra("mp_campaign_id");
        final String messageId = inboundIntent.getStringExtra("mp_message_id");
        final String extraLogData = inboundIntent.getStringExtra("mp");
        final String badgeCountStr = inboundIntent.getStringExtra("mp_bdgcnt");
        final String channelId = inboundIntent.getStringExtra("mp_channel_id");
        final String notificationTag = inboundIntent.getStringExtra("mp_tag");
        final String groupKey = inboundIntent.getStringExtra("mp_groupkey");
        final String ticker = inboundIntent.getStringExtra("mp_ticker");
        final String stickyString = inboundIntent.getStringExtra("mp_sticky");
        final String timeString = inboundIntent.getStringExtra("mp_time");
        final String visibilityStr = inboundIntent.getStringExtra("mp_visibility");
        final String silent = inboundIntent.getStringExtra("mp_silent");

        mData = new MmpNotificationData();
        mData.setMessage(message);
        mData.setLargeIconName(largeIconName);
        mData.setExpandableImageUrl(expandableImageURL);
        mData.setTag(notificationTag);
        mData.setGroupKey(groupKey);
        mData.setTicker(ticker);
        mData.setTimeString(timeString);
        mData.setCampaignId(campaignId);
        mData.setMessageId(messageId);
        mData.setButtons(buildButtons(buttonsJsonStr));
        mData.setExtraLogData(extraLogData);

        int badgeCount = MmpNotificationData.NOT_SET;
        if (null != badgeCountStr) {
            try {
                badgeCount = Integer.parseInt(badgeCountStr);
                if (badgeCount < 0) {
                    badgeCount = 0;
                }
            } catch (NumberFormatException e) {
                badgeCount = 0;
            }
        }
        mData.setBadgeCount(badgeCount);

        int visibility = Notification.VISIBILITY_PRIVATE;
        if (null != visibilityStr) {
            switch (visibilityStr) {
                case MmpPushNotification.VISIBILITY_SECRET:
                    visibility = Notification.VISIBILITY_SECRET;
                    break;
                case MmpPushNotification.VISIBILITY_PUBLIC:
                    visibility = Notification.VISIBILITY_PUBLIC;
                    break;
                case MmpPushNotification.VISIBILITY_PRIVATE:
                default:
                    visibility = Notification.VISIBILITY_PRIVATE;
            }
        }
        mData.setVisibility(visibility);

        if (channelId != null) {
            mData.setChannelId(channelId);
        }

        int color = MmpNotificationData.NOT_SET;
        if (colorName != null) {
            try {
                color = Color.parseColor(colorName);
            } catch (IllegalArgumentException e) {}
        }
        mData.setColor(color);

        if (notificationSubText != null && notificationSubText.length() == 0) {
            notificationSubText = null;
        }
        mData.setSubText(notificationSubText);

        boolean isSilent = silent != null && silent.equals("true");
        mData.setSilent(isSilent);

        boolean sticky = stickyString != null && stickyString.equals("true");
        mData.setSticky(sticky);

        int notificationIcon = MmpNotificationData.NOT_SET;
        if (iconName != null) {
            if (mDrawableIds.knownIdName(iconName)) {
                notificationIcon = mDrawableIds.idFromName(iconName);
            }
        }
        if (notificationIcon == MmpNotificationData.NOT_SET) {
            notificationIcon = getDefaultIcon();
        }
        mData.setIcon(notificationIcon);

        int whiteNotificationIcon = MmpNotificationData.NOT_SET;
        if (whiteIconName != null) {
            if (mDrawableIds.knownIdName(whiteIconName)) {
                whiteNotificationIcon = mDrawableIds.idFromName(whiteIconName);
            }
        }
        mData.setWhiteIcon(whiteNotificationIcon);

        if (notificationTitle == null || notificationTitle.length() == 0) {
            notificationTitle = getDefaultTitle();
        }
        mData.setTitle(notificationTitle);

        MmpNotificationData.PushTapAction onTap = buildOnTap(onTapStr);
        if (null == onTap) {
            onTap = buildOnTapFromURI(uriString);
        }
        if (null == onTap) {
            onTap = getDefaultOnTap();
        }
        mData.setOnTap(onTap);

        trackCampaignReceived();
    }

    protected void buildNotificationFromData() {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                mContext,
                ROUTING_REQUEST_CODE,
                getRoutingIntent(mData.getOnTap()),
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        final PendingIntent deleteIntent = PendingIntent.getBroadcast(
                mContext,
                0,
                getDeleteIntent(),
                0
        );

        mBuilder.
                setContentTitle(mData.getTitle()).
                setContentText(mData.getMessage()).
                setTicker(null == mData.getTicker() ? mData.getMessage() : mData.getTicker()).
                setContentIntent(contentIntent).
                setDeleteIntent(deleteIntent);

        maybeSetNotificationBarIcon();
        maybeSetLargeIcon();
        maybeSetExpandableNotification();
        maybeSetCustomIconColor();
        maybeAddActionButtons();
        maybeSetChannel();
        maybeSetNotificationBadge();
        maybeSetTime();
        maybeSetVisibility();
        maybeSetSubText();
    }

    protected void maybeSetSubText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && mData.getSubText() != null) {
            mBuilder.setSubText(mData.getSubText());
        }
    }

    protected void maybeSetNotificationBarIcon() {
        // For Android 5.0+ (Lollipop), any non-transparent pixels are turned white, so users generally specify
        // icons for these devices and regular full-color icons for older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mData.getWhiteIcon() != MmpNotificationData.NOT_SET) {
            mBuilder.setSmallIcon(mData.getWhiteIcon());
        } else {
            mBuilder.setSmallIcon(mData.getIcon());
        }
    }

    protected void maybeSetLargeIcon() {
        if (mData.getLargeIconName() != null) {
            if (mDrawableIds.knownIdName(mData.getLargeIconName())) {
                mBuilder.setLargeIcon(getBitmapFromResourceId(mDrawableIds.idFromName(mData.getLargeIconName())));
            } else if (mData.getLargeIconName().startsWith("http")) {
                Bitmap imageBitmap = getBitmapFromUrl(mData.getLargeIconName());
                if (imageBitmap != null) {
                    mBuilder.setLargeIcon(imageBitmap);
                }
            } else {
                MmpLog.d(LOGTAG, "large icon data was sent but did match a resource name or a valid url: " + mData.getLargeIconName());
            }
        }
    }

    protected void maybeSetExpandableNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (null != mData.getExpandableImageUrl() && mData.getExpandableImageUrl().startsWith("http")) {
                try {
                    Bitmap imageBitmap = getBitmapFromUrl(mData.getExpandableImageUrl());
                    if (imageBitmap == null) {
                        setBigTextStyle(mData.getMessage());
                    } else {
                        setBigPictureStyle(imageBitmap);
                    }
                } catch (Exception e) {
                    setBigTextStyle(mData.getMessage());
                }
            } else {
                setBigTextStyle(mData.getMessage());
            }
        }
    }

    protected void setBigTextStyle(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mBuilder.setStyle(new Notification.BigTextStyle().bigText(message));
        }
    }

    protected void setBigPictureStyle(Bitmap imageBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mBuilder.setStyle(new Notification.BigPictureStyle().bigPicture(imageBitmap));
        }
    }

    protected void maybeSetCustomIconColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            for (int i = 0; i < mData.getButtons().size(); i++) {
                MmpNotificationData.MmpNotificationButtonData btn = mData.getButtons().get(i);
                mBuilder.addAction(this.createAction(btn.getLabel(), btn.getOnTap(), btn.getId(), i + 1));
            }
        }
    }

    protected void maybeAddActionButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            for (int i = 0; i < mData.getButtons().size(); i++) {
                MmpNotificationData.MmpNotificationButtonData btn = mData.getButtons().get(i);
                mBuilder.addAction(this.createAction(btn.getLabel(), btn.getOnTap(), btn.getId(), i + 1));
            }
        }
    }

    protected List<MmpNotificationData.MmpNotificationButtonData> buildButtons(String buttonsJsonStr) {
        List<MmpNotificationData.MmpNotificationButtonData> buttons = new ArrayList<>();
        if (null != buttonsJsonStr) {
            try {
                JSONArray buttonsArr = new JSONArray(buttonsJsonStr);
                for (int i = 0; i < buttonsArr.length(); i++) {
                    JSONObject buttonObj = buttonsArr.getJSONObject(i);

                    // handle button label
                    final String btnLabel = buttonObj.getString("lbl");

                    // handle button action
                    final MmpNotificationData.PushTapAction pushAction = buildOnTap(buttonObj.getString("ontap"));

                    //handle button id
                    final String btnId = buttonObj.getString("id");

                    if (pushAction == null || btnLabel == null || btnId == null) {
                        MmpLog.d(LOGTAG, "Null button data received. No buttons will be rendered.");
                    } else {
                        buttons.add(new MmpNotificationData.MmpNotificationButtonData(btnLabel, pushAction, btnId));
                    }
                }
            } catch (JSONException e) {
                MmpLog.e(LOGTAG, "Exception parsing buttons payload", e);
            }
        }

        return buttons;
    }

    protected MmpNotificationData.PushTapAction buildOnTap(String onTapStr) {
        MmpNotificationData.PushTapAction onTap = null;
        if (null != onTapStr) {
            try {
                final JSONObject onTapJSON = new JSONObject(onTapStr);
                final String typeFromJSON = onTapJSON.getString("type");

                if (!typeFromJSON.equals(MmpNotificationData.PushTapActionType.HOMESCREEN.toString())) {
                    final String uriFromJSON = onTapJSON.getString("uri");
                    onTap = new MmpNotificationData.PushTapAction(MmpNotificationData.PushTapActionType.fromString(typeFromJSON), uriFromJSON);
                } else {
                    onTap = new MmpNotificationData.PushTapAction(MmpNotificationData.PushTapActionType.fromString(typeFromJSON));
                }

                if (onTap.getActionType().toString().equals(MmpNotificationData.PushTapActionType.ERROR.toString())) {
                    hasOnTapError = true;
                    onTap = new MmpNotificationData.PushTapAction(MmpNotificationData.PushTapActionType.HOMESCREEN);
                }
            } catch (JSONException e){
                MmpLog.d(LOGTAG, "Exception occurred while parsing ontap");
                onTap = null;
            }
        }

        return onTap;
    }

    protected MmpNotificationData.PushTapAction buildOnTapFromURI(String uriString) {
        MmpNotificationData.PushTapAction onTap = null;

        if (null != uriString) {
            onTap = new MmpNotificationData.PushTapAction(MmpNotificationData.PushTapActionType.URL_IN_BROWSER, uriString);
        }

        return onTap;
    }

    protected MmpNotificationData.PushTapAction getDefaultOnTap() {
        return new MmpNotificationData.PushTapAction(MmpNotificationData.PushTapActionType.HOMESCREEN);
    }

    @TargetApi(20)
    @SuppressWarnings("deprecation")
    protected Notification.Action createAction(CharSequence title, MmpNotificationData.PushTapAction onTap, String actionId, int index) {
        return (new Notification.Action.Builder(0, title, createActionIntent(onTap, actionId, title, index))).build();
    }

    protected PendingIntent createActionIntent(MmpNotificationData.PushTapAction onTap, String buttonId, CharSequence label, int index) {
        Intent routingIntent = getRoutingIntent(onTap, buttonId, label);
        return PendingIntent.getActivity(mContext, ROUTING_REQUEST_CODE + index, routingIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    protected Intent getRoutingIntent(MmpNotificationData.PushTapAction onTap, String buttonId, CharSequence label) {
        Bundle options = buildBundle(onTap, buttonId, label);
        return new Intent().
                setClass(mContext, MmpNotificationRouteActivity.class).
                putExtras(options).
                setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    }

    protected Intent getRoutingIntent(MmpNotificationData.PushTapAction onTap) {
        Bundle options = buildBundle(onTap);
        return new Intent().
                setAction(PUSH_TAP_ACTION).
                setClass(mContext, MmpNotificationRouteActivity.class).
                putExtras(options).
                setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    }


    protected Intent getDeleteIntent() {
        Bundle options = new Bundle();
        options.putCharSequence("mp_message_id", mData.getMessageId());
        options.putCharSequence("mp_campaign_id", mData.getCampaignId());
        options.putCharSequence("mp_canonical_notification_id", getCanonicalIdentifier());
        options.putCharSequence("mp", mData.getExtraLogData());

        return new Intent().
                setAction(PUSH_DISMISS_ACTION).
                setClass(mContext, MmpPushNotificationDismissedReceiver.class).
                putExtras(options);
    }

    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * Creates an intent to start the routing activity with a bundle describing the new intent
     * the routing activity should launch.
     *
     * Uses FLAG_ACTIVITY_NO_HISTORY so that the routing activity does not appear in the back stack
     * in Android.
     *
     * @param onTap The PushTapAction for the intent this bundle is a member of.
     *
     * @return Bundle built from onTap.
     */
    protected Bundle buildBundle(MmpNotificationData.PushTapAction onTap) {
        Bundle options = new Bundle();
        options.putCharSequence("mp_tap_target", TAP_TARGET_NOTIFICATION);
        options.putCharSequence("mp_tap_action_type", onTap.getActionType().toString());
        options.putCharSequence("mp_tap_action_uri", onTap.getUri());
        options.putCharSequence("mp_message_id", mData.getMessageId());
        options.putCharSequence("mp_campaign_id", mData.getCampaignId());
        options.putInt("mp_notification_id", notificationId);
        options.putBoolean("mp_is_sticky", mData.isSticky());
        options.putCharSequence("mp_tag", mData.getTag());
        options.putCharSequence("mp_canonical_notification_id", getCanonicalIdentifier());
        options.putCharSequence("mp", mData.getExtraLogData());

        return options;
    }

    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * Creates an intent to start the routing activity with a bundle describing the new intent
     * the routing activity should launch.
     *
     * Uses FLAG_ACTIVITY_NO_HISTORY so that the routing activity does not appear in the back stack
     * in Android.
     *
     * @param onTap The PushTapAction for the intent this bundle is a member of
     * @param buttonId The buttonId for the Notification action this bundle will be a member of
     * @param buttonLabel The label for the button that will appear in the notification which
     *                    this bundle will me a member of
     *
     * @return Bundle built from the given input params.
     */
    protected Bundle buildBundle(MmpNotificationData.PushTapAction onTap, String buttonId, CharSequence buttonLabel) {
        Bundle options = buildBundle(onTap);
        options.putCharSequence("mp_tap_target", TAP_TARGET_BUTTON);
        options.putCharSequence("mp_button_id", buttonId);
        options.putCharSequence("mp_button_label", buttonLabel);
        return options;
    }

    @SuppressWarnings("deprecation")
    protected void maybeSetChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = mData.getChannelId() == null ? MmpConfig.getInstance(mContext).getNotificationChannelId() : mData.getChannelId();
            String channelName = MmpConfig.getInstance(mContext).getNotificationChannelName();

            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);

            mBuilder.setChannelId(channelId);
        } else {
            mBuilder.setDefaults(MmpConfig.getInstance(mContext).getNotificationDefaults());
        }
    }

    protected void maybeSetNotificationBadge() {
        if (mData.getBadgeCount() > 0) {
            mBuilder.setNumber(mData.getBadgeCount());
        }
    }

    protected void maybeSetTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mBuilder.setShowWhen(true);
        }

        if (mData.getTimeString() == null) {
            mBuilder.setWhen(mNow);
        } else {
            Date dt = parseDateTime(DATETIME_WITH_TZ, mData.getTimeString());

            if (null == dt) {
                dt = parseDateTime(DATETIME_ZULU_TZ, mData.getTimeString());
            }

            if (null == dt) {
                dt = parseDateTime(DATETIME_NO_TZ, mData.getTimeString());
            }

            if (null == dt) {
                MmpLog.d(LOGTAG,"Unable to parse date string into datetime: " + mData.getTimeString());
            } else {
                mBuilder.setWhen(dt.getTime());
            }
        }
    }

    protected void maybeSetVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setVisibility(mData.getVisibility());
        }
    }

    protected ApplicationInfo getAppInfo() {
        try {
            return mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    protected CharSequence getDefaultTitle() {
        ApplicationInfo appInfo = getAppInfo();
        if (null != appInfo) {
            return mContext.getPackageManager().getApplicationLabel(appInfo);
        } else {
            return "A message for you";
        }
    }

    protected int getDefaultIcon() {
        ApplicationInfo appInfo = getAppInfo();
        if (null != appInfo) {
            return appInfo.icon;
        } else {
            return android.R.drawable.sym_def_app_icon;
        }
    }

    protected int getNotificationId(){
        return this.notificationId;
    }

    protected String getCanonicalIdentifier() {
        if (this.mData.getTag() != null) {
            return this.mData.getTag();
        } else {
            return Integer.toString(this.notificationId);
        }
    }

    protected boolean isValid() {
        return mData != null && !hasOnTapError;
    }

    protected void trackCampaignReceived() {
        final String campaignId = this.mData.getCampaignId();
        final String messageId = this.mData.getMessageId();
        final String mpPayloadStr = this.mData.getExtraLogData();
        if (campaignId != null && messageId != null) {

            MmpAPI.trackPushNotificationEvent(
                    mContext,
                    Integer.valueOf(campaignId),
                    Integer.valueOf(messageId),
                    getCanonicalIdentifier(),
                    mpPayloadStr,
                    "$push_notification_received",
                    new JSONObject()
            );

            MmpAPI instance = MmpAPI.getInstanceFromMpPayload(mContext, mpPayloadStr);
            if (instance != null && instance.isAppInForeground()) {
                JSONObject additionalProperties = new JSONObject();
                try {
                    additionalProperties.put("message_type", "push");
                } catch (JSONException e) {}
                MmpAPI.trackPushNotificationEvent(
                        mContext,
                        Integer.valueOf(campaignId),
                        Integer.valueOf(messageId),
                        getCanonicalIdentifier(),
                        mpPayloadStr,
                        "$campaign_received",
                        additionalProperties
                );
            }
        }
    }

    private Date parseDateTime(String format, String datetime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
            if (format.equals(DATETIME_ZULU_TZ)) {
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            return sdf.parse(datetime);
        } catch (ParseException e) {
            return null;
        }
    }

    /* package */ MmpNotificationData getData() {
        return mData;
    }

    /* package */ Bitmap getBitmapFromResourceId(int resourceId) {
        return BitmapFactory.decodeResource(mContext.getResources(), resourceId);
    }

    /* package */ Bitmap getBitmapFromUrl(String url) {
        ImageStore is = new ImageStore(mContext, "MmpPushNotification");
        try {
            return is.getImage(url);
        } catch (ImageStore.CantGetImageException e) {
            return null;
        }
    }

    /* package */ ResourceIds getResourceIds(Context context) {
        final MmpConfig config = MmpConfig.getInstance(context);
        String resourcePackage = config.getResourcePackageName();
        if (null == resourcePackage) {
            resourcePackage = context.getPackageName();
        }
        return new ResourceReader.Drawables(resourcePackage, context);
    }
}
