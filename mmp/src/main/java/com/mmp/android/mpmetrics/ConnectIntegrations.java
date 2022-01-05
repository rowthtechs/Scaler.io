package com.mmp.android.mpmetrics;

import android.content.Context;
import android.os.Handler;

import com.mmp.android.util.MmpLog;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/* package */ class ConnectIntegrations {
    private final MmpAPI mMmp;
    private Context mContext;
    private String mSavedUrbanAirshipChannelID;
    private int mUrbanAirshipRetries;

    private static final String LOGTAG = "MmpAPI.CnctInts";
    private static final int UA_MAX_RETRIES = 3;

    public ConnectIntegrations(MmpAPI mmp, Context context) {
        mMmp = mmp;
        mContext = context;
    }

    public void reset() {
        mSavedUrbanAirshipChannelID = null;
        mUrbanAirshipRetries = 0;
    }

    public void setupIntegrations(Set<String> integrations) {
        if (integrations.contains("urbanairship")) {
            setAirshipPeopleProp();
        }
        if (integrations.contains("braze")) {
            setBrazePeopleProp();
        }
    }

    @SuppressWarnings("unchecked")
    private void setAirshipPeopleProp() {
        String urbanAirshipClassName = "com.urbanairship.UAirship";
        try {
            Class urbanAirshipClass = Class.forName(urbanAirshipClassName);
            Object sharedUAirship = urbanAirshipClass.getMethod("shared").invoke(null);
            Object channel = sharedUAirship.getClass().getMethod("getChannel").invoke(sharedUAirship);
            String channelID = (String)channel.getClass().getMethod("getId").invoke(channel);
            if (channelID != null && !channelID.isEmpty()) {
                mUrbanAirshipRetries = 0;
                if (mSavedUrbanAirshipChannelID == null || !mSavedUrbanAirshipChannelID.equals(channelID)) {
                    mMmp.getPeople().set("android_urban_airship_channel_id", channelID);
                    mSavedUrbanAirshipChannelID = channelID;
                }
            } else {
                mUrbanAirshipRetries++;
                if (mUrbanAirshipRetries <= UA_MAX_RETRIES) {
                    final Handler delayedHandler = new Handler(android.os.Looper.getMainLooper());
                    delayedHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setAirshipPeopleProp();
                        }
                    }, 2000);
                }
            }
        } catch (ClassNotFoundException e) {
            MmpLog.w(LOGTAG, "Airship SDK not found but Urban Airship is integrated on Mmp", e);
        } catch (NoSuchMethodException e) {
            MmpLog.e(LOGTAG, "Airship SDK class exists but methods do not", e);
        } catch (InvocationTargetException e) {
            MmpLog.e(LOGTAG, "method invocation failed", e);
        } catch (IllegalAccessException e) {
            MmpLog.e(LOGTAG, "method invocation failed", e);
        } catch (Exception e) {
            MmpLog.e(LOGTAG, "Error setting Airship people property", e);

        }
    }

    @SuppressWarnings("unchecked")
    private void setBrazePeopleProp() {
        String urbanAirshipClassName = "com.appboy.Appboy";
        try {
            Class brazeClass = Class.forName(urbanAirshipClassName);
            Object brazeInstance = brazeClass.getMethod("getInstance", Context.class).invoke(null, mContext);
            String deviceId = (String) brazeInstance.getClass().getMethod("getDeviceId").invoke(brazeInstance);

            Object currentUser = brazeInstance.getClass().getMethod("getCurrentUser").invoke(brazeInstance);
            if (currentUser == null) {
                MmpLog.w(LOGTAG, "Make sure Braze is initialized properly before Mmp.");
                return;
            }
            String externalUserId = (String) currentUser.getClass().getMethod("getUserId").invoke(currentUser);

            if (deviceId != null && !deviceId.isEmpty()) {
                mMmp.alias(deviceId, mMmp.getDistinctId());
                mMmp.getPeople().set("braze_device_id", deviceId);
            }
            if (externalUserId != null && !externalUserId.isEmpty()) {
                mMmp.alias(externalUserId, mMmp.getDistinctId());
                mMmp.getPeople().set("braze_external_id", externalUserId);
            }
        } catch (ClassNotFoundException e) {
            MmpLog.w(LOGTAG, "Braze SDK not found but Braze is integrated on Mmp", e);
        } catch (NoSuchMethodException e) {
            MmpLog.e(LOGTAG, "Braze SDK class exists but methods do not", e);
        } catch (InvocationTargetException e) {
            MmpLog.e(LOGTAG, "method invocation failed", e);
        } catch (IllegalAccessException e) {
            MmpLog.e(LOGTAG, "method invocation failed", e);
        } catch (Exception e) {
            MmpLog.e(LOGTAG, "Error setting braze people properties", e);
        }
    }
}
