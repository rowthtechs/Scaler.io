package com.mmp.android.mpmetrics;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.mmp.android.util.Base64Coder;
import com.mmp.android.util.HttpService;
import com.mmp.android.util.MmpLog;
import com.mmp.android.util.RemoteService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

/**
 * Manage communication of events with the internal database and the Mmp servers.
 *
 * <p>This class straddles the thread boundary between user threads and
 * a logical Mmp thread.
 */
/* package */ class ResultMessagesAnalatic {

    /**
     * Do not call directly. You should call AnalyticsMessages.getInstance()
     */
    /* package */ ResultMessagesAnalatic(final Context context) {
        mContext = context;
        mConfig = getConfig(context);
        mWorker = createWorker();
        getPoster().checkIsMmpBlocked();
    }

    protected Worker createWorker() {
        return new Worker();
    }

    /**
     * Use this to get an instance of AnalyticsMessages instead of creating one directly
     * for yourself.
     *
     * @param messageContext should be the Main Activity of the application
     *     associated with these messages.
     */
    public static ResultMessagesAnalatic getInstance(final Context messageContext) {
        synchronized (sInstances) {
            final Context appContext = messageContext.getApplicationContext();
            ResultMessagesAnalatic ret;
            if (! sInstances.containsKey(appContext)) {
                ret = new ResultMessagesAnalatic(appContext);
                sInstances.put(appContext, ret);
            } else {
                ret = sInstances.get(appContext);
            }
            return ret;
        }
    }

    public void eventsMessage(final EventDescription eventDescription) {
        final Message m = Message.obtain();
        m.what = ENQUEUE_EVENTS;
        m.obj = eventDescription;
        mWorker.runMessage(m);
    }

    // Must be thread safe.
    public void peopleMessage(final PeopleDescription peopleDescription) {
        final Message m = Message.obtain();
        m.what = ENQUEUE_PEOPLE;
        m.obj = peopleDescription;

        mWorker.runMessage(m);
    }

    // Must be thread safe.
    public void groupMessage(final GroupDescription groupDescription) {
        final Message m = Message.obtain();
        m.what = ENQUEUE_GROUP;
        m.obj = groupDescription;

        mWorker.runMessage(m);
    }

    // Must be thread safe.
    public void pushAnonymousPeopleMessage(final PushAnonymousPeopleDescription pushAnonymousPeopleDescription) {
        final Message m = Message.obtain();
        m.what = PUSH_ANONYMOUS_PEOPLE_RECORDS;
        m.obj = pushAnonymousPeopleDescription;

        mWorker.runMessage(m);
    }

    // Must be thread safe.
    public void clearAnonymousUpdatesMessage(final MmpDescription clearAnonymousUpdatesDescription) {
        final Message m = Message.obtain();
        m.what = CLEAR_ANONYMOUS_UPDATES;
        m.obj = clearAnonymousUpdatesDescription;

        mWorker.runMessage(m);
    }

    public void postToServer(final FlushDescription flushDescription) {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;
        m.obj = flushDescription.getToken();
        m.arg1 = flushDescription.shouldCheckDecide() ? 1 : 0;

        mWorker.runMessage(m);
    }

    public void installDecideCheck(final SelectMessages check) {
        final Message m = Message.obtain();
        m.what = INSTALL_DECIDE_CHECK;
        m.obj = check;

        mWorker.runMessage(m);
    }

    public void emptyTrackingQueues(final MmpDescription mmpDescription) {
        final Message m = Message.obtain();
        m.what = EMPTY_QUEUES;
        m.obj = mmpDescription;

        mWorker.runMessage(m);
    }

    public void updateEventProperties(final UpdateEventsPropertiesDescription updateEventsProperties) {
        final Message m = Message.obtain();
        m.what = REWRITE_EVENT_PROPERTIES;
        m.obj = updateEventsProperties;

        mWorker.runMessage(m);
    }

    public void hardKill() {
        final Message m = Message.obtain();
        m.what = KILL_WORKER;

        mWorker.runMessage(m);
    }

    /////////////////////////////////////////////////////////
    // For testing, to allow for Mocking.

    /* package */ boolean isDead() {
        return mWorker.isDead();
    }

    protected MmpDbAdapter makeDbAdapter(Context context) {
        return MmpDbAdapter.getInstance(context);
    }

    protected MmpConfig getConfig(Context context) {
        return MmpConfig.getInstance(context);
    }

    protected RemoteService getPoster() {
        return new HttpService();
    }

    ////////////////////////////////////////////////////

    static class EventDescription extends MmpMessageDescription {
        public EventDescription(String eventName,
                                JSONObject properties,
                                String token) {
            this(eventName, properties, token, false, new JSONObject());
        }

        public EventDescription(String eventName,
                                JSONObject properties,
                                String token,
                                boolean isAutomatic,
                                JSONObject sessionMetada) {
            super(token, properties);
            mEventName = eventName;
            mIsAutomatic = isAutomatic;
            mSessionMetadata = sessionMetada;
        }

        public String getEventName() {
            return mEventName;
        }

        public JSONObject getProperties() {
            return getMessage();
        }

        public JSONObject getSessionMetadata() {
            return mSessionMetadata;
        }

        public boolean isAutomatic() {
            return mIsAutomatic;
        }

        private final String mEventName;
        private final JSONObject mSessionMetadata;
        private final boolean mIsAutomatic;
    }

    static class PeopleDescription extends MmpMessageDescription {
        public PeopleDescription(JSONObject message, String token) {
            super(token, message);
        }

        @Override
        public String toString() {
            return getMessage().toString();
        }

        public boolean isAnonymous() {
            return !getMessage().has("distinct_id");
        }
    }

    static class GroupDescription extends MmpMessageDescription {
        public GroupDescription(JSONObject message, String token) {
            super(token, message);
        }

        @Override
        public String toString() {
            return getMessage().toString();
        }
    }

    static class PushAnonymousPeopleDescription extends MmpDescription {
        public PushAnonymousPeopleDescription(String distinctId, String token) {
            super(token);
            this.mDistinctId = distinctId;
        }

        @Override
        public String toString() {
            return this.mDistinctId;
        }

        public String getDistinctId() {
            return this.mDistinctId;
        }

        private final String mDistinctId;
    }

    static class FlushDescription extends MmpDescription {
        public FlushDescription(String token) {
            this(token, true);
        }

        protected FlushDescription(String token, boolean checkDecide) {
            super(token);
            this.checkDecide = checkDecide;
        }

        public boolean shouldCheckDecide() {
            return checkDecide;
        }

        private final boolean checkDecide;
    }

    static class MmpMessageDescription extends MmpDescription {
        public MmpMessageDescription(String token, JSONObject message) {
            super(token);
            if (message != null && message.length() > 0) {
                Iterator<String> it = message.keys();
                while (it.hasNext()) {
                    String jsonKey = it.next();
                    try {
                        message.get(jsonKey).toString();
                    } catch (AssertionError e) {
                        // see https://github.com/mmp/mmp-android/issues/567
                        message.remove(jsonKey);
                        MmpLog.e(LOGTAG, "Removing people profile property from update (see https://github.com/mmp/mmp-android/issues/567)", e);
                    } catch (JSONException e) {}
                }
            }
            this.mMessage = message;
        }

        public JSONObject getMessage() {
            return mMessage;
        }

        private final JSONObject mMessage;
    }


    static class UpdateEventsPropertiesDescription extends MmpDescription {
        private Map<String, String> mProps;

        public UpdateEventsPropertiesDescription(String token, Map<String, String> props) {
            super(token);
            mProps = props;
        }

        public Map<String, String> getProperties() {
            return mProps;
        }
    }

    static class MmpDescription {
        public MmpDescription(String token) {
            this.mToken = token;
        }

        public String getToken() {
            return mToken;
        }

        private final String mToken;
    }

    // Sends a message if and only if we are running with Mmp Message log enabled.
    // Will be called from the Mmp thread.
    private void logAboutMessageToMmp(String message) {
        MmpLog.v(LOGTAG, message + " (Thread " + Thread.currentThread().getId() + ")");
    }

    private void logAboutMessageToMmp(String message, Throwable e) {
        MmpLog.v(LOGTAG, message + " (Thread " + Thread.currentThread().getId() + ")", e);
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    // XXX: Worker class is unnecessary, should be just a subclass of HandlerThread
    class Worker {
        public Worker() {
            mHandler = restartWorkerThread();
        }

        public boolean isDead() {
            synchronized(mHandlerLock) {
                return mHandler == null;
            }
        }

        public void runMessage(Message msg) {
            synchronized(mHandlerLock) {
                if (mHandler == null) {
                    // We died under suspicious circumstances. Don't try to send any more events.
                    logAboutMessageToMmp("Dead mmp worker dropping a message: " + msg.what);
                } else {
                    mHandler.sendMessage(msg);
                }
            }
        }

        // NOTE that the returned worker will run FOREVER, unless you send a hard kill
        // (which you really shouldn't)
        protected Handler restartWorkerThread() {
            final HandlerThread thread = new HandlerThread("com.mmp.android.AnalyticsWorker", Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            final Handler ret = new AnalyticsMessageHandler(thread.getLooper());
            return ret;
        }

        class AnalyticsMessageHandler extends Handler {
            public AnalyticsMessageHandler(Looper looper) {
                super(looper);
                mDbAdapter = null;
                mSystemInformation = SystemInformation.getInstance(mContext);
                mDecideChecker = createDecideChecker();
                mFlushInterval = mConfig.getFlushInterval();
            }

            protected DecideChecker createDecideChecker() {
                return new DecideChecker(mContext, mConfig);
            }

            @Override
            public void handleMessage(Message msg) {
                if (mDbAdapter == null) {
                    mDbAdapter = makeDbAdapter(mContext);
                    mDbAdapter.cleanupEvents(System.currentTimeMillis() - mConfig.getDataExpiration(), MmpDbAdapter.Table.EVENTS);
                    mDbAdapter.cleanupEvents(System.currentTimeMillis() - mConfig.getDataExpiration(), MmpDbAdapter.Table.PEOPLE);
                }

                try {
                    int returnCode = MmpDbAdapter.DB_UNDEFINED_CODE;
                    String token = null;

                    if (msg.what == ENQUEUE_PEOPLE) {
                        final PeopleDescription message = (PeopleDescription) msg.obj;
                        final MmpDbAdapter.Table peopleTable = message.isAnonymous() ? MmpDbAdapter.Table.ANONYMOUS_PEOPLE : MmpDbAdapter.Table.PEOPLE;

                        logAboutMessageToMmp("Queuing people record for sending later");
                        logAboutMessageToMmp("    " + message.toString());
                        token = message.getToken();
                        int numRowsTable = mDbAdapter.addJSON(message.getMessage(), token, peopleTable, false);
                        returnCode = message.isAnonymous() ? 0 : numRowsTable;
                    } else if (msg.what == ENQUEUE_GROUP) {
                        final GroupDescription message = (GroupDescription) msg.obj;

                        logAboutMessageToMmp("Queuing group record for sending later");
                        logAboutMessageToMmp("    " + message.toString());
                        token = message.getToken();
                        returnCode = mDbAdapter.addJSON(message.getMessage(), token, MmpDbAdapter.Table.GROUPS, false);
                    } else if (msg.what == ENQUEUE_EVENTS) {
                        final EventDescription eventDescription = (EventDescription) msg.obj;
                        try {
                            final JSONObject message = prepareEventObject(eventDescription);
                            logAboutMessageToMmp("Queuing event for sending later");
                            logAboutMessageToMmp("    " + message.toString());
                            token = eventDescription.getToken();

                            SelectMessages decide = mDecideChecker.getDecideMessages(token);
                            if (decide != null && eventDescription.isAutomatic() && !decide.shouldTrackAutomaticEvent()) {
                                return;
                            }
                            returnCode = mDbAdapter.addJSON(message, token, MmpDbAdapter.Table.EVENTS, eventDescription.isAutomatic());
                        } catch (final JSONException e) {
                            MmpLog.e(LOGTAG, "Exception tracking event " + eventDescription.getEventName(), e);
                        }
                    } else if (msg.what == PUSH_ANONYMOUS_PEOPLE_RECORDS) {
                        final PushAnonymousPeopleDescription pushAnonymousPeopleDescription = (PushAnonymousPeopleDescription) msg.obj;
                        final String distinctId = pushAnonymousPeopleDescription.getDistinctId();
                        token = pushAnonymousPeopleDescription.getToken();
                        returnCode = mDbAdapter.pushAnonymousUpdatesToPeopleDb(token, distinctId);
                    } else if (msg.what == CLEAR_ANONYMOUS_UPDATES) {
                        final MmpDescription mmpDescription = (MmpDescription) msg.obj;
                        token = mmpDescription.getToken();
                        mDbAdapter.cleanupAllEvents(MmpDbAdapter.Table.ANONYMOUS_PEOPLE, token);
                    } else if (msg.what == REWRITE_EVENT_PROPERTIES) {
                        final UpdateEventsPropertiesDescription description = (UpdateEventsPropertiesDescription) msg.obj;
                        int updatedEvents = mDbAdapter.rewriteEventDataWithProperties(description.getProperties(), description.getToken());
                        MmpLog.d(LOGTAG, updatedEvents + " stored events were updated with new properties.");
                    } else if (msg.what == FLUSH_QUEUE) {
                        logAboutMessageToMmp("Flushing queue due to scheduled or forced flush");
                        updateFlushFrequency();
                        token = (String) msg.obj;
                        boolean shouldCheckDecide = msg.arg1 == 1 ? true : false;
                        sendAllData(mDbAdapter, token);
                        if (shouldCheckDecide && SystemClock.elapsedRealtime() >= mDecideRetryAfter) {
                            try {
                                mDecideChecker.runDecideCheck(token, getPoster());
                            } catch (RemoteService.ServiceUnavailableException e) {
                                mDecideRetryAfter = SystemClock.elapsedRealtime() + e.getRetryAfter() * 1000;
                            }
                        }
                    } else if (msg.what == INSTALL_DECIDE_CHECK) {
                        logAboutMessageToMmp("Installing a check for in-app notifications");
                        final SelectMessages check = (SelectMessages) msg.obj;
                        mDecideChecker.addDecideCheck(check);
                        if (SystemClock.elapsedRealtime() >= mDecideRetryAfter) {
                            try {
                                mDecideChecker.runDecideCheck(check.getToken(), getPoster());
                            } catch (RemoteService.ServiceUnavailableException e) {
                                mDecideRetryAfter = SystemClock.elapsedRealtime() + e.getRetryAfter() * 1000;
                            }
                        }
                    } else if (msg.what == EMPTY_QUEUES) {
                        final MmpDescription message = (MmpDescription) msg.obj;
                        token = message.getToken();
                        mDbAdapter.cleanupAllEvents(MmpDbAdapter.Table.EVENTS, token);
                        mDbAdapter.cleanupAllEvents(MmpDbAdapter.Table.PEOPLE, token);
                        mDbAdapter.cleanupAllEvents(MmpDbAdapter.Table.GROUPS, token);
                        mDbAdapter.cleanupAllEvents(MmpDbAdapter.Table.ANONYMOUS_PEOPLE, token);
                    } else if (msg.what == KILL_WORKER) {
                        MmpLog.w(LOGTAG, "Worker received a hard kill. Dumping all events and force-killing. Thread id " + Thread.currentThread().getId());
                        synchronized(mHandlerLock) {
                            mDbAdapter.deleteDB();
                            mHandler = null;
                            Looper.myLooper().quit();
                        }
                    } else {
                        MmpLog.e(LOGTAG, "Unexpected message received by Mmp worker: " + msg);
                    }

                    ///////////////////////////
                    if ((returnCode >= mConfig.getBulkUploadLimit() || returnCode == MmpDbAdapter.DB_OUT_OF_MEMORY_ERROR) && mFailedRetries <= 0 && token != null) {
                        logAboutMessageToMmp("Flushing queue due to bulk upload limit (" + returnCode + ") for project " + token);
                        updateFlushFrequency();
                        sendAllData(mDbAdapter, token);
                        if (SystemClock.elapsedRealtime() >= mDecideRetryAfter) {
                            try {
                                mDecideChecker.runDecideCheck(token, getPoster());
                            } catch (RemoteService.ServiceUnavailableException e) {
                                mDecideRetryAfter = SystemClock.elapsedRealtime() + e.getRetryAfter() * 1000;
                            }
                        }
                    } else if (returnCode > 0 && !hasMessages(FLUSH_QUEUE, token)) {
                        // The !hasMessages(FLUSH_QUEUE, token) check is a courtesy for the common case
                        // of delayed flushes already enqueued from inside of this thread.
                        // Callers outside of this thread can still send
                        // a flush right here, so we may end up with two flushes
                        // in our queue, but we're OK with that.

                        logAboutMessageToMmp("Queue depth " + returnCode + " - Adding flush in " + mFlushInterval);
                        if (mFlushInterval >= 0) {
                            final Message flushMessage = Message.obtain();
                            flushMessage.what = FLUSH_QUEUE;
                            flushMessage.obj = token;
                            flushMessage.arg1 = 1;
                            sendMessageDelayed(flushMessage, mFlushInterval);
                        }
                    }
                } catch (final RuntimeException e) {
                    MmpLog.e(LOGTAG, "Worker threw an unhandled exception", e);
                    synchronized (mHandlerLock) {
                        mHandler = null;
                        try {
                            Looper.myLooper().quit();
                            MmpLog.e(LOGTAG, "Mmp will not process any more analytics messages", e);
                        } catch (final Exception tooLate) {
                            MmpLog.e(LOGTAG, "Could not halt looper", tooLate);
                        }
                    }
                }
            }// handleMessage

            protected long getTrackEngageRetryAfter() {
                return mTrackEngageRetryAfter;
            }

            private void sendAllData(MmpDbAdapter dbAdapter, String token) {
                final RemoteService poster = getPoster();
                if (!poster.isOnline(mContext, mConfig.getOfflineMode())) {
                    logAboutMessageToMmp("Not flushing data to Mmp because the device is not connected to the internet.");
                    return;
                }

                sendData(dbAdapter, token, MmpDbAdapter.Table.EVENTS, mConfig.getEventsEndpoint());
                sendData(dbAdapter, token, MmpDbAdapter.Table.PEOPLE, mConfig.getPeopleEndpoint());
                sendData(dbAdapter, token, MmpDbAdapter.Table.GROUPS, mConfig.getGroupsEndpoint());
            }

            private void sendData(MmpDbAdapter dbAdapter, String token, MmpDbAdapter.Table table, String url) {
                final RemoteService poster = getPoster();
                SelectMessages selectMessages = mDecideChecker.getDecideMessages(token);
                boolean includeAutomaticEvents = true;
                if (selectMessages == null || selectMessages.isAutomaticEventsEnabled() == null) {
                    includeAutomaticEvents = false;
                }
                String[] eventsData = dbAdapter.generateDataString(table, token, includeAutomaticEvents);
                Integer queueCount = 0;
                if (eventsData != null) {
                    queueCount = Integer.valueOf(eventsData[2]);
                }

                while (eventsData != null && queueCount > 0) {
                    final String lastId = eventsData[0];
                    final String rawMessage = eventsData[1];

                    final String encodedData = Base64Coder.encodeString(rawMessage);
                    final Map<String, Object> params = new HashMap<String, Object>();
                    params.put("data", encodedData);
                    if (MmpConfig.DEBUG) {
                        params.put("verbose", "1");
                    }
                    System.out.println(rawMessage);
                    System.out.println(encodedData);

                    boolean deleteEvents = true;
                    byte[] response;
                    try {
                        final SSLSocketFactory socketFactory = mConfig.getSSLSocketFactory();
                        response = poster.performRequest(url, params, socketFactory);
                        if (null == response) {
                            deleteEvents = false;
                            logAboutMessageToMmp("Response was null, unexpected failure posting to " + url + ".");
                        } else {
                            deleteEvents = true; // Delete events on any successful post, regardless of 1 or 0 response
                            String parsedResponse;
                            try {
                                parsedResponse = new String(response, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                throw new RuntimeException("UTF not supported on this platform?", e);
                            }
                            if (mFailedRetries > 0) {
                                mFailedRetries = 0;
                                removeMessages(FLUSH_QUEUE, token);
                            }

                            logAboutMessageToMmp("Successfully posted to " + url + ": \n" + rawMessage);
                            logAboutMessageToMmp("Response was " + parsedResponse);
                        }
                    } catch (final OutOfMemoryError e) {
                        MmpLog.e(LOGTAG, "Out of memory when posting to " + url + ".", e);
                    } catch (final MalformedURLException e) {
                        MmpLog.e(LOGTAG, "Cannot interpret " + url + " as a URL.", e);
                    } catch (final RemoteService.ServiceUnavailableException e) {
                        logAboutMessageToMmp("Cannot post message to " + url + ".", e);
                        deleteEvents = false;
                        mTrackEngageRetryAfter = e.getRetryAfter() * 1000;
                    } catch (final SocketTimeoutException e) {
                        logAboutMessageToMmp("Cannot post message to " + url + ".", e);
                        deleteEvents = false;
                    } catch (final IOException e) {
                        logAboutMessageToMmp("Cannot post message to " + url + ".", e);
                        deleteEvents = false;
                    }

                    if (deleteEvents) {
                        logAboutMessageToMmp("Not retrying this batch of events, deleting them from DB.");
                        dbAdapter.cleanupEvents(lastId, table, token, includeAutomaticEvents);
                    } else {
                        removeMessages(FLUSH_QUEUE, token);
                        mTrackEngageRetryAfter = Math.max((long)Math.pow(2, mFailedRetries) * 60000, mTrackEngageRetryAfter);
                        mTrackEngageRetryAfter = Math.min(mTrackEngageRetryAfter, 10 * 60 * 1000); // limit 10 min
                        final Message flushMessage = Message.obtain();
                        flushMessage.what = FLUSH_QUEUE;
                        flushMessage.obj = token;
                        sendMessageDelayed(flushMessage, mTrackEngageRetryAfter);
                        mFailedRetries++;
                        logAboutMessageToMmp("Retrying this batch of events in " + mTrackEngageRetryAfter + " ms");
                        break;
                    }

                    eventsData = dbAdapter.generateDataString(table, token, includeAutomaticEvents);
                    if (eventsData != null) {
                        queueCount = Integer.valueOf(eventsData[2]);
                    }
                }
            }

            private JSONObject getDefaultEventProperties()
                    throws JSONException {
                final JSONObject ret = new JSONObject();

                Referrer r = Referrer.getInstance();
                ret.put("mp_lib", "android");
                ret.put("lib_version", MmpConfig.VERSION);

                // For querying together with data from other libraries
                ret.put("os", "Android");
                ret.put("os_version", Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);

                ret.put("manufacturer", Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
                ret.put("brand", Build.BRAND == null ? "UNKNOWN" : Build.BRAND);
                ret.put("model", Build.MODEL == null ? "UNKNOWN" : Build.MODEL);
                ret.put("package" , mContext.getPackageName());
                ret.put("medium" , r.getUtm_medium());
                ret.put("campaign" , r.getUtm_campaign() );
                ret.put("source" , r.getUtm_source());
                ret.put("click_id" , r.getClick_id());
                ret.put("inbound_link" , r.getReffer());
                System.out.println(ret);
                try {
                    try {
                        final int servicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
                        switch (servicesAvailable) {
                            case ConnectionResult.SUCCESS:
                                ret.put("google_play_services", "available");
                                break;
                            case ConnectionResult.SERVICE_MISSING:
                                ret.put("google_play_services", "missing");
                                break;
                            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                                ret.put("google_play_services", "out of date");
                                break;
                            case ConnectionResult.SERVICE_DISABLED:
                                ret.put("google_play_services", "disabled");
                                break;
                            case ConnectionResult.SERVICE_INVALID:
                                ret.put("google_play_services", "invalid");
                                break;
                        }
                    } catch (RuntimeException e) {
                        // Turns out even checking for the service will cause explosions
                        // unless we've set up meta-data
                        ret.put("google_play_services", "not configured");
                    }

                } catch (NoClassDefFoundError e) {
                    ret.put("google_play_services", "not included");
                }

                final DisplayMetrics displayMetrics = mSystemInformation.getDisplayMetrics();
                ret.put("screen_dpi", displayMetrics.densityDpi);
                ret.put("screen_height", displayMetrics.heightPixels);
                ret.put("screen_width", displayMetrics.widthPixels);

                final String applicationVersionName = mSystemInformation.getAppVersionName();
                if (null != applicationVersionName) {
                    ret.put("app_version", applicationVersionName);
                    ret.put("app_version_string", applicationVersionName);
                }

                 final Integer applicationVersionCode = mSystemInformation.getAppVersionCode();
                 if (null != applicationVersionCode) {
                    final String applicationVersion = String.valueOf(applicationVersionCode);
                    ret.put("app_release", applicationVersion);
                    ret.put("app_build_number", applicationVersion);
                }

                final Boolean hasNFC = mSystemInformation.hasNFC();
                if (null != hasNFC)
                    ret.put("has_nfc", hasNFC.booleanValue());

                final Boolean hasTelephony = mSystemInformation.hasTelephony();
                if (null != hasTelephony)
                    ret.put("has_telephone", hasTelephony.booleanValue());

                final String carrier = mSystemInformation.getCurrentNetworkOperator();
                if (null != carrier && !carrier.trim().isEmpty())
                    ret.put("carrier", carrier);

                final Boolean isWifi = mSystemInformation.isWifiConnected();
                if (null != isWifi)
                    ret.put("wifi", isWifi.booleanValue());

                final String radio = mSystemInformation.getPhoneRadioType(mContext);
                if (null != radio)
                    ret.put("radio", radio);

                final Boolean isBluetoothEnabled = mSystemInformation.isBluetoothEnabled();
                if (isBluetoothEnabled != null)
                    ret.put("bluetooth_enabled", isBluetoothEnabled);

                final String bluetoothVersion = mSystemInformation.getBluetoothVersion();
                if (bluetoothVersion != null)
                    ret.put("bluetooth_version", bluetoothVersion);

                return ret;
            }

            private JSONObject prepareEventObject(EventDescription eventDescription) throws JSONException {
                final JSONObject eventObj = new JSONObject();
                final JSONObject eventProperties = eventDescription.getProperties();
                final JSONObject sendProperties = getDefaultEventProperties();
                sendProperties.put("token", eventDescription.getToken());
                if (eventProperties != null) {
                    for (final Iterator<?> iter = eventProperties.keys(); iter.hasNext();) {
                        final String key = (String) iter.next();
                        sendProperties.put(key, eventProperties.get(key));
                    }
                }
                eventObj.put("event", eventDescription.getEventName());
                eventObj.put("properties", sendProperties);
                eventObj.put("mp_metadata", eventDescription.getSessionMetadata());
                return eventObj;
            }

            private MmpDbAdapter mDbAdapter;
            private final DecideChecker mDecideChecker;
            private final long mFlushInterval;
            private long mDecideRetryAfter;
            private long mTrackEngageRetryAfter;
            private int mFailedRetries;
        }// AnalyticsMessageHandler

        private void updateFlushFrequency() {
            final long now = System.currentTimeMillis();
            final long newFlushCount = mFlushCount + 1;

            if (mLastFlushTime > 0) {
                final long flushInterval = now - mLastFlushTime;
                final long totalFlushTime = flushInterval + (mAveFlushFrequency * mFlushCount);
                mAveFlushFrequency = totalFlushTime / newFlushCount;

                final long seconds = mAveFlushFrequency / 1000;
                logAboutMessageToMmp("Average send frequency approximately " + seconds + " seconds.");
            }

            mLastFlushTime = now;
            mFlushCount = newFlushCount;
        }

        private final Object mHandlerLock = new Object();
        private Handler mHandler;
        private long mFlushCount = 0;
        private long mAveFlushFrequency = 0;
        private long mLastFlushTime = -1;
        private SystemInformation mSystemInformation;
    }

    public long getTrackEngageRetryAfter() {
        return ((Worker.AnalyticsMessageHandler) mWorker.mHandler).getTrackEngageRetryAfter();
    }
    /////////////////////////////////////////////////////////

    // Used across thread boundaries
    private final Worker mWorker;
    protected final Context mContext;
    protected final MmpConfig mConfig;

    // Messages for our thread
    private static final int ENQUEUE_PEOPLE = 0; // push given JSON message to people DB
    private static final int ENQUEUE_EVENTS = 1; // push given JSON message to events DB
    private static final int FLUSH_QUEUE = 2; // submit events, people, and groups data
    private static final int ENQUEUE_GROUP = 3; // push given JSON message to groups DB
    private static final int PUSH_ANONYMOUS_PEOPLE_RECORDS = 4; // push anonymous people DB updates to people DB
    private static final int KILL_WORKER = 5; // Hard-kill the worker thread, discarding all events on the event queue. This is for testing, or disasters.
    private static final int EMPTY_QUEUES = 6; // Remove any local (and pending to be flushed) events or people/group updates from the db
    private static final int CLEAR_ANONYMOUS_UPDATES = 7; // Remove anonymous people updates from DB
    private static final int REWRITE_EVENT_PROPERTIES = 8; // Update or add properties to existing queued events
    private static final int INSTALL_DECIDE_CHECK = 12; // Run this DecideCheck at intervals until it isDestroyed()

    private static final String LOGTAG = "MmpAPI.Messages";

    private static final Map<Context, ResultMessagesAnalatic> sInstances = new HashMap<Context, ResultMessagesAnalatic>();

}
