package com.mmp.android.mpmetrics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.mmp.android.util.MmpLog;

// In order to use writeEdits, we have to suppress the linter's check for commit()/apply()
@SuppressLint("CommitPrefEdits")
/* package */ class PersistentIdentity {
    // Should ONLY be called from an OnPrefsLoadedListener (since it should NEVER be called concurrently)
    public static String getPeopleDistinctId(SharedPreferences storedPreferences) {
        return storedPreferences.getString("people_distinct_id", null);
    }

    public static void writeReferrerPrefs(Context context, String preferencesName, Map<String, String> properties) {
        synchronized (sReferrerPrefsLock) {
            final SharedPreferences referralInfo = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
            final SharedPreferences.Editor editor = referralInfo.edit();
            editor.clear();
            for (final Map.Entry<String, String> entry : properties.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            writeEdits(editor);
            sReferrerPrefsDirty = true;
        }
    }

    public PersistentIdentity(Future<SharedPreferences> referrerPreferences, Future<SharedPreferences> storedPreferences, Future<SharedPreferences> timeEventsPreferences, Future<SharedPreferences> mmpPreferences) {
        mLoadReferrerPreferences = referrerPreferences;
        mLoadStoredPreferences = storedPreferences;
        mTimeEventsPreferences = timeEventsPreferences;
        mMmpPreferences = mmpPreferences;
        mSuperPropertiesCache = null;
        mReferrerPropertiesCache = null;
        mIdentitiesLoaded = false;
        mReferrerChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                synchronized (sReferrerPrefsLock) {
                    readReferrerProperties();
                    sReferrerPrefsDirty = false;
                }
            }
        };
    }

    // Super properties
    public void addSuperPropertiesToObject(JSONObject ob) {
        synchronized (mSuperPropsLock) {
            final JSONObject superProperties = this.getSuperPropertiesCache();
            final Iterator<?> superIter = superProperties.keys();
            while (superIter.hasNext()) {
                final String key = (String) superIter.next();

                try {
                    ob.put(key, superProperties.get(key));
                } catch (JSONException e) {
                    MmpLog.e(LOGTAG, "Object read from one JSON Object cannot be written to another", e);
                }
            }
        }
    }

    public void updateSuperProperties(SuperPropertyUpdate updates) {
        synchronized (mSuperPropsLock) {
            final JSONObject oldPropCache = getSuperPropertiesCache();
            final JSONObject copy = new JSONObject();

            try {
                final Iterator<String> keys = oldPropCache.keys();
                while (keys.hasNext()) {
                    final String k = keys.next();
                    final Object v = oldPropCache.get(k);
                    copy.put(k, v);
                }
            } catch (JSONException e) {
                MmpLog.e(LOGTAG, "Can't copy from one JSONObject to another", e);
                return;
            }

            final JSONObject replacementCache = updates.update(copy);
            if (replacementCache == null) {
                MmpLog.w(LOGTAG, "An update to Mmp's super properties returned null, and will have no effect.");
                return;
            }

            mSuperPropertiesCache = replacementCache;
            storeSuperProperties();
        }
    }

    public void registerSuperProperties(JSONObject superProperties) {
        synchronized (mSuperPropsLock) {
            final JSONObject propCache = getSuperPropertiesCache();

            for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
                final String key = (String) iter.next();
                try {
                    propCache.put(key, superProperties.get(key));
                } catch (final JSONException e) {
                    MmpLog.e(LOGTAG, "Exception registering super property.", e);
                }
            }

            storeSuperProperties();
        }
    }

    public void unregisterSuperProperty(String superPropertyName) {
        synchronized (mSuperPropsLock) {
            final JSONObject propCache = getSuperPropertiesCache();
            propCache.remove(superPropertyName);

            storeSuperProperties();
        }
    }

    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        synchronized (mSuperPropsLock) {
            final JSONObject propCache = getSuperPropertiesCache();

            for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
                final String key = (String) iter.next();
                if (! propCache.has(key)) {
                    try {
                        propCache.put(key, superProperties.get(key));
                    } catch (final JSONException e) {
                        MmpLog.e(LOGTAG, "Exception registering super property.", e);
                    }
                }
            }// for

            storeSuperProperties();
        }
    }

    public void clearSuperProperties() {
        synchronized (mSuperPropsLock) {
            mSuperPropertiesCache = new JSONObject();
            storeSuperProperties();
        }
    }

    public Map<String, String> getReferrerProperties() {
        synchronized (sReferrerPrefsLock) {
            if (sReferrerPrefsDirty || null == mReferrerPropertiesCache) {
                readReferrerProperties();
                sReferrerPrefsDirty = false;
            }
        }
        return mReferrerPropertiesCache;
    }

    public void clearReferrerProperties() {
        synchronized (sReferrerPrefsLock) {
            try {
                final SharedPreferences referrerPrefs = mLoadReferrerPreferences.get();
                final SharedPreferences.Editor prefsEdit = referrerPrefs.edit();
                prefsEdit.clear();
                writeEdits(prefsEdit);
            } catch (final ExecutionException e) {
                MmpLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e.getCause());
            } catch (final InterruptedException e) {
                MmpLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e);
            }
        }
    }

    public synchronized String getAnonymousId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mAnonymousId;
    }

    public synchronized boolean getHadPersistedDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mHadPersistedDistinctId;
    }

    public synchronized String getEventsDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mEventsDistinctId;
    }

    public synchronized String getEventsUserId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        if(mEventsUserIdPresent) {
            return mEventsDistinctId;
        }
        return null;
    }

    public synchronized void setAnonymousIdIfAbsent(String anonymousId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        if (mAnonymousId != null) {
            return;
        }
        mAnonymousId = anonymousId;
        mHadPersistedDistinctId = true;
        writeIdentities();
    }

    public synchronized void setEventsDistinctId(String eventsDistinctId) {
        if(!mIdentitiesLoaded) {
            readIdentities();
        }
        mEventsDistinctId = eventsDistinctId;
        writeIdentities();
    }

    public synchronized void markEventsUserIdPresent() {
        if(!mIdentitiesLoaded) {
            readIdentities();
        }
        mEventsUserIdPresent = true;
        writeIdentities();
    }

    public synchronized String getPeopleDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mPeopleDistinctId;
    }

    public synchronized void setPeopleDistinctId(String peopleDistinctId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        mPeopleDistinctId = peopleDistinctId;
        writeIdentities();
    }

    public synchronized void clearPreferences() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.clear();
            writeEdits(prefsEdit);
            readSuperProperties();
            readIdentities();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public void clearTimeEvents() {
        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            writeEdits(editor);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public synchronized void storePushId(String registrationId) {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("push_id", registrationId);
            writeEdits(editor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't write push id to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't write push id to shared preferences", e);
        }
    }

    public synchronized void clearPushId() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.remove("push_id");
            writeEdits(editor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't write push id to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't write push id to shared preferences", e);
        }
    }

    public synchronized String getPushId() {
        String ret = null;
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            ret = prefs.getString("push_id", null);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't write push id to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't write push id to shared preferences", e);
        }
        return ret;
    }

    public Map<String, Long> getTimeEvents() {
        Map<String, Long> timeEvents = new HashMap<>();

        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();

            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                timeEvents.put(entry.getKey(), Long.valueOf(entry.getValue().toString()));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return timeEvents;
    }

    // access is synchronized outside (mEventTimings)
    public void removeTimeEvent(String timeEventName) {
        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.remove(timeEventName);
            writeEdits(editor);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    // access is synchronized outside (mEventTimings)
    public void addTimeEvent(String timeEventName, Long timeEventTimestamp) {
        try {
            final SharedPreferences prefs = mTimeEventsPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(timeEventName, timeEventTimestamp);
            writeEdits(editor);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isFirstIntegration(String token) {
        boolean firstLaunch = false;
        try {
            SharedPreferences prefs = mMmpPreferences.get();
            firstLaunch = prefs.getBoolean(token, false);
        }  catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Couldn't read internal Mmp shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Couldn't read internal Mmp from shared preferences.", e);
        }
        return firstLaunch;
    }

    public synchronized void setIsIntegrated(String token) {
        try {
            SharedPreferences.Editor mmpEditor = mMmpPreferences.get().edit();
            mmpEditor.putBoolean(token, true);
            writeEdits(mmpEditor);
        } catch (ExecutionException e) {
            MmpLog.e(LOGTAG, "Couldn't write internal Mmp shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MmpLog.e(LOGTAG, "Couldn't write internal Mmp from shared preferences.", e);
        }
    }

    public synchronized boolean isNewVersion(String versionCode) {
        if (versionCode == null) {
            return false;
        }

        Integer version = Integer.valueOf(versionCode);
        try {
            if (sPreviousVersionCode == null) {
                SharedPreferences mmpPreferences = mMmpPreferences.get();
                sPreviousVersionCode = mmpPreferences.getInt("latest_version_code", -1);
                if (sPreviousVersionCode == -1) {
                    sPreviousVersionCode = version;
                    SharedPreferences.Editor mmpPreferencesEditor = mMmpPreferences.get().edit();
                    mmpPreferencesEditor.putInt("latest_version_code", version);
                    writeEdits(mmpPreferencesEditor);
                }
            }

            if (sPreviousVersionCode.intValue() < version.intValue()) {
                SharedPreferences.Editor mmpPreferencesEditor = mMmpPreferences.get().edit();
                mmpPreferencesEditor.putInt("latest_version_code", version);
                writeEdits(mmpPreferencesEditor);
                return true;
            }
        } catch (ExecutionException e) {
            MmpLog.e(LOGTAG, "Couldn't write internal Mmp shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MmpLog.e(LOGTAG, "Couldn't write internal Mmp from shared preferences.", e);
        }

        return false;
    }

    public synchronized boolean isFirstLaunch(boolean dbExists, String token) {
        if (sIsFirstAppLaunch == null) {
            try {
                SharedPreferences mmpPreferences = mMmpPreferences.get();
                boolean hasLaunched = mmpPreferences.getBoolean("has_launched_" + token, false);
                if (hasLaunched) {
                    sIsFirstAppLaunch = false;
                } else {
                    sIsFirstAppLaunch = !dbExists;
                    if (!sIsFirstAppLaunch) {
                        setHasLaunched(token);
                    }
                }
            } catch (ExecutionException e) {
                sIsFirstAppLaunch = false;
            } catch (InterruptedException e) {
                sIsFirstAppLaunch = false;
            }
        }

        return sIsFirstAppLaunch;
    }

    public synchronized void setHasLaunched(String token) {
        try {
            SharedPreferences.Editor mmpPreferencesEditor = mMmpPreferences.get().edit();
            mmpPreferencesEditor.putBoolean("has_launched_" + token, true);
            writeEdits(mmpPreferencesEditor);
        } catch (ExecutionException e) {
            MmpLog.e(LOGTAG, "Couldn't write internal Mmp shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MmpLog.e(LOGTAG, "Couldn't write internal Mmp shared preferences.", e);
        }
    }

    public synchronized HashSet<Integer> getSeenCampaignIds() {
        HashSet<Integer> campaignIds = new HashSet<>();
        try {
            SharedPreferences mpPrefs = mLoadStoredPreferences.get();
            String seenIds = mpPrefs.getString("seen_campaign_ids", "");
            StringTokenizer stTokenizer = new StringTokenizer(seenIds, DELIMITER);
            while (stTokenizer.hasMoreTokens()) {
                campaignIds.add(Integer.valueOf(stTokenizer.nextToken()));
            }
        } catch (ExecutionException e) {
            MmpLog.e(LOGTAG, "Couldn't read Mmp shared preferences.", e.getCause());
        } catch (InterruptedException e) {
            MmpLog.e(LOGTAG, "Couldn't read Mmp shared preferences.", e);
        }
        return campaignIds;
    }

    public synchronized void saveCampaignAsSeen(Integer notificationId) {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            String campaignIds = prefs.getString("seen_campaign_ids", "");
            editor.putString("seen_campaign_ids", campaignIds + notificationId + DELIMITER);
            writeEdits(editor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't write campaign d to shared preferences", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't write campaign id to shared preferences", e);
        }
    }

    public synchronized void setOptOutTracking(boolean optOutTracking, String token) {
        mIsUserOptOut = optOutTracking;
        writeOptOutFlag(token);
    }

    public synchronized boolean getOptOutTracking(String token) {
        if (mIsUserOptOut == null) {
            readOptOutFlag(token);
        }

        return mIsUserOptOut;
    }

    //////////////////////////////////////////////////

    // Must be called from a synchronized setting
    private JSONObject getSuperPropertiesCache() {
        if (mSuperPropertiesCache == null) {
            readSuperProperties();
        }
        return mSuperPropertiesCache;
    }

    // All access should be synchronized on this
    private void readSuperProperties() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final String props = prefs.getString("super_properties", "{}");
            MmpLog.v(LOGTAG, "Loading Super Properties " + props);
            mSuperPropertiesCache = new JSONObject(props);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e);
        } catch (final JSONException e) {
            MmpLog.e(LOGTAG, "Cannot parse stored superProperties");
            storeSuperProperties();
        } finally {
            if (mSuperPropertiesCache == null) {
                mSuperPropertiesCache = new JSONObject();
            }
        }
    }

    // All access should be synchronized on this
    private void readReferrerProperties() {
        mReferrerPropertiesCache = new HashMap<String, String>();

        try {
            final SharedPreferences referrerPrefs = mLoadReferrerPreferences.get();
            referrerPrefs.unregisterOnSharedPreferenceChangeListener(mReferrerChangeListener);
            referrerPrefs.registerOnSharedPreferenceChangeListener(mReferrerChangeListener);

            final Map<String, ?> prefsMap = referrerPrefs.getAll();
            for (final Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                final String prefsName = entry.getKey();
                final Object prefsVal = entry.getValue();
                mReferrerPropertiesCache.put(prefsName, prefsVal.toString());
            }
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Cannot load referrer properties from shared preferences.", e);
        }
    }

    // All access should be synchronized on this
    private void storeSuperProperties() {
        if (mSuperPropertiesCache == null) {
            MmpLog.e(LOGTAG, "storeSuperProperties should not be called with uninitialized superPropertiesCache.");
            return;
        }

        final String props = mSuperPropertiesCache.toString();
        MmpLog.v(LOGTAG, "Storing Super Properties " + props);

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("super_properties", props);
            writeEdits(editor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Cannot store superProperties in shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Cannot store superProperties in shared preferences.", e);
        }
    }

    // All access should be synchronized on this
    private void readIdentities() {
        SharedPreferences prefs = null;
        try {
            prefs = mLoadStoredPreferences.get();
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e);
        }

        if (prefs == null) {
            return;
        }

        mEventsDistinctId = prefs.getString("events_distinct_id", null);
        mEventsUserIdPresent = prefs.getBoolean("events_user_id_present", false);
        mPeopleDistinctId = prefs.getString("people_distinct_id", null);
        mAnonymousId = prefs.getString("anonymous_id", null);
        mHadPersistedDistinctId = prefs.getBoolean("had_persisted_distinct_id", false);

        if (mEventsDistinctId == null) {
            mAnonymousId = UUID.randomUUID().toString();
            mEventsDistinctId = mAnonymousId;
            mEventsUserIdPresent = false;
            writeIdentities();
        }
        mIdentitiesLoaded = true;
    }

    private void readOptOutFlag(String token) {
        SharedPreferences prefs = null;
        try {
            prefs = mMmpPreferences.get();
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Cannot read opt out flag from sharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Cannot read opt out flag from sharedPreferences.", e);
        }

        if (prefs == null) {
            return;
        }
        mIsUserOptOut = prefs.getBoolean("opt_out_" + token, false);
    }

    private void writeOptOutFlag(String token) {
        try {
            final SharedPreferences prefs = mMmpPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putBoolean("opt_out_" + token, mIsUserOptOut);
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't write opt-out shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't write opt-out shared preferences.", e);
        }
    }

    protected void removeOptOutFlag(String token) {
        try {
            final SharedPreferences prefs = mMmpPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.clear();
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't remove opt-out shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't remove opt-out shared preferences.", e);
        }
    }

    protected boolean hasOptOutFlag(String token) {
        try {
            final SharedPreferences prefs = mMmpPreferences.get();
            return prefs.contains("opt_out_" + token);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't read opt-out shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't read opt-out shared preferences.", e);
        }
        return false;
    }
    // All access should be synchronized on this
    private void writeIdentities() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();

            prefsEditor.putString("events_distinct_id", mEventsDistinctId);
            prefsEditor.putBoolean("events_user_id_present", mEventsUserIdPresent);
            prefsEditor.putString("people_distinct_id", mPeopleDistinctId);
            prefsEditor.putString("anonymous_id", mAnonymousId);
            prefsEditor.putBoolean("had_persisted_distinct_id", mHadPersistedDistinctId);
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            MmpLog.e(LOGTAG, "Can't write distinct ids to shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            MmpLog.e(LOGTAG, "Can't write distinct ids to shared preferences.", e);
        }
    }

    private static void writeEdits(final SharedPreferences.Editor editor) {
        editor.apply();
    }

    private final Future<SharedPreferences> mLoadStoredPreferences;
    private final Future<SharedPreferences> mLoadReferrerPreferences;
    private final Future<SharedPreferences> mTimeEventsPreferences;
    private final Future<SharedPreferences> mMmpPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener mReferrerChangeListener;
    private JSONObject mSuperPropertiesCache;
    private Object mSuperPropsLock = new Object();
    private Map<String, String> mReferrerPropertiesCache;
    private boolean mIdentitiesLoaded;
    private String mEventsDistinctId;
    private boolean mEventsUserIdPresent;
    private String mPeopleDistinctId;
    private String mAnonymousId;
    private boolean mHadPersistedDistinctId;
    private Boolean mIsUserOptOut;
    private static Integer sPreviousVersionCode;
    private static Boolean sIsFirstAppLaunch;

    private static boolean sReferrerPrefsDirty = true;
    private static final Object sReferrerPrefsLock = new Object();
    private static final String DELIMITER = ",";
    private static final String LOGTAG = "MmpAPI.PIdentity";
}
