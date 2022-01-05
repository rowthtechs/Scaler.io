package com.mmp.android.mpmetrics;

import com.mmp.android.util.MmpLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;

import static com.mmp.android.mpmetrics.CheckConfigration.LOG_MESSAGE_TAG;

/* package */ class SessionMetadata {
    private long mEventsCounter, mPeopleCounter, mSessionStartEpoch ;
    private String mSessionID;
    private SecureRandom mRandom;

    /* package */ SessionMetadata() {
        initSession();
        mRandom = new SecureRandom();
    }

    protected void initSession() {
        mEventsCounter = 0L;
        mPeopleCounter = 0L;
        mSessionID = Long.toHexString(new SecureRandom().nextLong());
        mSessionStartEpoch = System.currentTimeMillis() / 1000;

    }

    public JSONObject getMetadataForEvent() {
        return getNewMetadata(true);
    }

    public JSONObject getMetadataForPeople() {
        return getNewMetadata(false);
    }

    private JSONObject getNewMetadata(boolean isEvent) {
        JSONObject metadataJson = new JSONObject();
        try {
            metadataJson.put("mp_event_id", Long.toHexString(mRandom.nextLong()));
            metadataJson.put("mp_session_id", mSessionID);
            metadataJson.put("mp_session_seq_id", isEvent ? mEventsCounter : mPeopleCounter);
            metadataJson.put("mp_session_start_sec", mSessionStartEpoch);
            if (isEvent) {
                mEventsCounter++;
            } else {
                mPeopleCounter++;
            }
        } catch (JSONException e) {
            MmpLog.e(LOG_MESSAGE_TAG, "Cannot create session metadata JSON object", e);
        }

        return metadataJson;
    }

}
