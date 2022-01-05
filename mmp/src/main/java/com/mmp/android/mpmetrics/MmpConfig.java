package com.mmp.android.mpmetrics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

import com.mmp.android.util.MmpConstants;
import com.mmp.android.util.MmpLog;
import com.mmp.android.util.OfflineMode;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;


/**
 * Stores global configuration options for the Mmp library. You can enable and disable configuration
 * options using &lt;meta-data&gt; tags inside of the &lt;application&gt; tag in your AndroidManifest.xml.
 * All settings are optional, and default to reasonable recommended values. Most users will not have to
 * set any options.
 *
 * Mmp understands the following options:
 *
 * <dl>
 *     <dt>com.mmp.android.MPConfig.EnableDebugLogging</dt>
 *     <dd>A boolean value. If true, emit more detailed log messages. Defaults to false</dd>
 *
 *     <dt>com.mmp.android.MPConfig.BulkUploadLimit</dt>
 *     <dd>An integer count of messages, the maximum number of messages to queue before an upload attempt. This value should be less than 50.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.FlushInterval</dt>
 *     <dd>An integer number of milliseconds, the maximum time to wait before an upload if the bulk upload limit isn't reached.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.FlushOnBackground</dt>
 *     <dd>A boolean value. If false, the library will not flush the event and people queues when the app goes into the background. Defaults to true.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DebugFlushInterval</dt>
 *     <dd>An integer number of milliseconds, the maximum time to wait before an upload if the bulk upload limit isn't reached in debug mode.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DataExpiration</dt>
 *     <dd>An integer number of milliseconds, the maximum age of records to send to Mmp. Corresponds to Mmp's server-side limit on record age.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.MinimumDatabaseLimit</dt>
 *     <dd>An integer number of bytes. Mmp attempts to limit the size of its persistent data
 *          queue based on the storage capacity of the device, but will always allow queing below this limit. Higher values
 *          will take up more storage even when user storage is very full.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.ResourcePackageName</dt>
 *     <dd>A string java package name. Defaults to the package name of the Application. Users should set if the package name of their R class is different from the application package name due to application id settings.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableGestureBindingUI</dt>
 *     <dd>A boolean value. If true, do not allow connecting to the codeless event binding or A/B testing editor using an accelerometer gesture. Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableEmulatorBindingUI</dt>
 *     <dd>A boolean value. If true, do not attempt to connect to the codeless event binding or A/B testing editor when running in the Android emulator. Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableAppOpenEvent</dt>
 *     <dd>A boolean value. If true, do not send an "$app_open" event when the MmpAPI object is created for the first time. Defaults to true - the $app_open event will not be sent by default.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableExceptionHandler</dt>
 *     <dd>A boolean value. If true, do not automatically capture app crashes. "App Crashed" events won't show up on Mmp. Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.AutoShowMmpUpdates</dt>
 *     <dd>A boolean value. If true, automatically show notifications and A/B test variants. Defaults to true.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.EventsEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send events to this endpoint rather than to the default Mmp endpoint.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.PeopleEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send people updates to this endpoint rather than to the default Mmp endpoint.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.GroupsEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to send group updates to this endpoint rather than to the default Mmp endpoint.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DecideEndpoint</dt>
 *     <dd>A string URL. If present, the library will attempt to get notification, codeless event tracking, and A/B test variant information from this url rather than the default Mmp endpoint.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.EditorUrl</dt>
 *     <dd>A string URL. If present, the library will attempt to connect to this endpoint when in interactive editing mode, rather than to the default Mmp editor url.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.IgnoreInvisibleViewsVisualEditor</dt>
 *     <dd>A boolean value. If true, invisible views won't be shown on Mmp Visual Editor (AB Test and codeless events) . Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableViewCrawler</dt>
 *     <dd>A boolean value. If true, AB tests, tweaks and codeless events will be disabled. Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableDecideChecker</dt>
 *     <dd>A boolean value. If true, the library will not query our decide endpoint and won't retrieve in-app notifications, codeless events, Ab Tests or tweaks. Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.NotificationDefaults</dt>
 *     <dd>An integer number. This value is used to create a notification before API 26 (https://developer.android.com/reference/android/app/Notification.Builder.html#setDefaults(int)). Defaults to 0.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.MinimumSessionDuration</dt>
 *     <dd>An integer number. The minimum session duration (ms) that is tracked in automatic events. Defaults to 10000 (10 seconds).</dd>
 *
 *     <dt>com.mmp.android.MPConfig.SessionTimeoutDuration</dt>
 *     <dd>An integer number. The maximum session duration (ms) that is tracked in automatic events. Defaults to Integer.MAX_VALUE (no maximum session duration).</dd>
 *
 *     <dt>com.mmp.android.MPConfig.TestMode</dt>
 *     <dd>A boolean value. If true, in-app notifications won't be marked as seen. Defaults to false.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.ImageCacheMaxMemoryFactor</dt>
 *     <dd>An integer value. The LRU cache size that Mmp uses to store images is calculated by the available memory divided by this factor. Defaults to 10.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.DisableViewCrawlerForProjects</dt>
 *     <dd>A resource array list (e.g. @array/my_project_list). AB tests, tweaks and codeless events will be disabled for the projects from that list. Defaults to null.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.UseIpAddressForGeolocation</dt>
 *     <dd>A boolean value. If true, Mmp will automatically determine city, region and country data using the IP address of the client.Defaults to true.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.NotificationChannelId</dt>
 *     <dd>An string value. If present, the library will use this id when creating a notification channel. Applicable only for Android 26 and above.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.NotificationChannelName</dt>
 *     <dd>An string value. If present, the library will use this user-visible name for our notification channel. Applicable only for Android 26 and above. Defaults to the application name.</dd>
 *
 *     <dt>com.mmp.android.MPConfig.NotificationChannelImportance</dt>
 *     <dd>An integer number. Importance of the notification channel (see https://developer.android.com/reference/android/app/NotificationManager.html). Defaults to 3 (IMPORTANCE_DEFAULT). Applicable only for Android 26 and above.</dd>
 * </dl>
 *
 */
public class MmpConfig {

    public static final String VERSION = "1";

    public static boolean DEBUG = false;

    /**
     * Minimum API level for support of rich UI features, like In-App notifications and dynamic event binding.
     * Devices running OS versions below this level will still support tracking and push notification features.
     */
    public static final int UI_FEATURES_MIN_API = 16;

    // Name for persistent storage of app referral SharedPreferences
    /* package */ static final String REFERRER_PREFS_NAME = "com.mmp.android.mpmetrics.ReferralInfo";

    // Max size of the number of notifications we will hold in memory. Since they may contain images,
    // we don't want to suck up all of the memory on the device.
    /* package */ static final int MAX_NOTIFICATION_CACHE_COUNT = 2;
    /* package */ static final int MAX_EVENT_TRIGGERED_NOTIFICATION_CACHE_COUNT = 2;

    // Instances are safe to store, since they're immutable and always the same.
    public static MmpConfig getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (null == sInstance) {
                final Context appContext = context.getApplicationContext();
                sInstance = readConfig(appContext);
            }
        }

        return sInstance;
    }

    /**
     * The MmpAPI will use the system default SSL socket settings under ordinary circumstances.
     * That means it will ignore settings you associated with the default SSLSocketFactory in the
     * schema registry or in underlying HTTP libraries. If you'd prefer for Mmp to use your
     * own SSL settings, you'll need to call setSSLSocketFactory early in your code, like this
     *
     * {@code
     * <pre>
     *     MPConfig.getInstance(context).setSSLSocketFactory(someCustomizedSocketFactory);
     * </pre>
     * }
     *
     * Your settings will be globally available to all Mmp instances, and will be used for
     * all SSL connections in the library. The call is thread safe, but should be done before
     * your first call to MmpAPI.getInstance to insure that the library never uses it's
     * default.
     *
     * The given socket factory may be used from multiple threads, which is safe for the system
     * SSLSocketFactory class, but if you pass a subclass you should ensure that it is thread-safe
     * before passing it to Mmp.
     *
     * @param factory an SSLSocketFactory that
     */
    public synchronized void setSSLSocketFactory(SSLSocketFactory factory) {
        mSSLSocketFactory = factory;
    }

    /**
     * {@link OfflineMode} allows Mmp to be in-sync with client offline internal logic.
     * If you want to integrate your own logic with Mmp you'll need to call
     * {@link #setOfflineMode(OfflineMode)} early in your code, like this
     *
     * {@code
     * <pre>
     *     MPConfig.getInstance(context).setOfflineMode(OfflineModeImplementation);
     * </pre>
     * }
     *
     * Your settings will be globally available to all Mmp instances, and will be used across
     * all the library. The call is thread safe, but should be done before
     * your first call to MmpAPI.getInstance to insure that the library never uses it's
     * default.
     *
     * The given {@link OfflineMode} may be used from multiple threads, you should ensure that
     * your implementation is thread-safe before passing it to Mmp.
     *
     * @param offlineMode client offline implementation to use on Mmp
     */
    public synchronized void setOfflineMode(OfflineMode offlineMode) {
        mOfflineMode = offlineMode;
    }

    /* package */ MmpConfig(Bundle metaData, Context context) {

        // By default, we use a clean, FACTORY default SSLSocket. In general this is the right
        // thing to do, and some other third party libraries change the
        SSLSocketFactory foundSSLFactory;
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            foundSSLFactory = sslContext.getSocketFactory();
        } catch (final GeneralSecurityException e) {
            MmpLog.i("MmpAPI.Conf", "System has no SSL support. Built-in events editor will not be available", e);
            foundSSLFactory = null;
        }
        mSSLSocketFactory = foundSSLFactory;

        DEBUG = metaData.getBoolean("com.mmp.android.MPConfig.EnableDebugLogging", false);
        if (DEBUG) {
            MmpLog.setLevel(MmpLog.VERBOSE);
        }

        if (metaData.containsKey("com.mmp.android.MPConfig.DebugFlushInterval")) {
            MmpLog.w(LOG_MESSAGE_TAG, "We do not support com.mmp.android.MPConfig.DebugFlushInterval anymore. There will only be one flush interval. Please, update your AndroidManifest.xml.");
        }

        mBulkUploadLimit = metaData.getInt("com.mmp.android.MPConfig.BulkUploadLimit", 40); // 40 records default
        mFlushInterval = metaData.getInt("com.mmp.android.MPConfig.FlushInterval", 60 * 1000); // one minute default
        mFlushOnBackground = metaData.getBoolean("com.mmp.android.MPConfig.FlushOnBackground", true);
        mMinimumDatabaseLimit = metaData.getInt("com.mmp.android.MPConfig.MinimumDatabaseLimit", 20 * 1024 * 1024); // 20 Mb
        mResourcePackageName = metaData.getString("com.mmp.android.MPConfig.ResourcePackageName"); // default is null
        mDisableGestureBindingUI = metaData.getBoolean("com.mmp.android.MPConfig.DisableGestureBindingUI", false);
        mDisableEmulatorBindingUI = metaData.getBoolean("com.mmp.android.MPConfig.DisableEmulatorBindingUI", false);
        mDisableAppOpenEvent = metaData.getBoolean("com.mmp.android.MPConfig.DisableAppOpenEvent", true);
        mDisableViewCrawler = metaData.getBoolean("com.mmp.android.MPConfig.DisableViewCrawler", false);
        mDisableDecideChecker = metaData.getBoolean("com.mmp.android.MPConfig.DisableDecideChecker", false);
        mDisableExceptionHandler = metaData.getBoolean("com.mmp.android.MPConfig.DisableExceptionHandler", false);
        mImageCacheMaxMemoryFactor = metaData.getInt("com.mmp.android.MPConfig.ImageCacheMaxMemoryFactor", 10);
        mIgnoreInvisibleViewsEditor = metaData.getBoolean("com.mmp.android.MPConfig.IgnoreInvisibleViewsVisualEditor", false);
        mAutoShowMmpUpdates = metaData.getBoolean("com.mmp.android.MPConfig.AutoShowMmpUpdates", true);
        mNotificationDefaults = metaData.getInt("com.mmp.android.MPConfig.NotificationDefaults", 0);
        mMinSessionDuration = metaData.getInt("com.mmp.android.MPConfig.MinimumSessionDuration", 10 * 1000); // 10 seconds
        mSessionTimeoutDuration = metaData.getInt("com.mmp.android.MPConfig.SessionTimeoutDuration", Integer.MAX_VALUE); // no timeout by default
        mUseIpAddressForGeolocation = metaData.getBoolean("com.mmp.android.MPConfig.UseIpAddressForGeolocation", true);
        mTestMode = metaData.getBoolean("com.mmp.android.MPConfig.TestMode", false);
        mNotificationChannelImportance = metaData.getInt("com.mmp.android.MPConfig.NotificationChannelImportance", 3); // NotificationManger.IMPORTANCE_DEFAULT

        Object dataExpirationMetaData = metaData.get("com.mmp.android.MPConfig.DataExpiration");
        long dataExpirationLong = 1000 * 60 * 60 * 24 * 5; // 5 days default
        if (dataExpirationMetaData != null) {
            try {
                if (dataExpirationMetaData instanceof Integer) {
                    dataExpirationLong = (long) (int) dataExpirationMetaData;
                } else if (dataExpirationMetaData instanceof Float) {
                    dataExpirationLong = (long) (float) dataExpirationMetaData;
                } else {
                    throw new NumberFormatException(dataExpirationMetaData.toString() + " is not a number.");
                }
            } catch (Exception e) {
                MmpLog.e(LOG_MESSAGE_TAG,"Error parsing com.mmp.android.MPConfig.DataExpiration meta-data value", e);
            }
        }
        mDataExpiration = dataExpirationLong;

        String notificationChannelId = metaData.getString("com.mmp.android.MPConfig.NotificationChannelId");
        if (notificationChannelId == null) {
            notificationChannelId = "mp";
        }
        mNotificationChannelId = notificationChannelId;

        String notificationChannelName = metaData.getString("com.mmp.android.MPConfig.NotificationChannelName");
        if (notificationChannelName == null) {
            notificationChannelName = SystemInformation.getInstance(context).getAppName();
        }
        mNotificationChannelName = notificationChannelName;

        String eventsEndpoint = metaData.getString("com.mmp.android.MPConfig.EventsEndpoint");
        if (eventsEndpoint != null) {
            setEventsEndpoint(eventsEndpoint);
        } else {
            setEventsEndpointWithBaseURL(MmpConstants.URL.MMP_API);
        }

        String peopleEndpoint = metaData.getString("com.mmp.android.MPConfig.PeopleEndpoint");
        if (peopleEndpoint != null) {
            setPeopleEndpoint(peopleEndpoint);
        } else {
            setPeopleEndpointWithBaseURL(MmpConstants.URL.MMP_API);
        }

        String groupsEndpoint = metaData.getString("com.mmp.android.MPConfig.GroupsEndpoint");
        if (groupsEndpoint != null) {
            setGroupsEndpoint(groupsEndpoint);
        } else {
            setGroupsEndpointWithBaseURL(MmpConstants.URL.MMP_API);
        }

        String decideEndpoint = metaData.getString("com.mmp.android.MPConfig.DecideEndpoint");
        if (decideEndpoint != null) {
            setDecideEndpoint(decideEndpoint);
        } else {
            setDecideEndpointWithBaseURL(MmpConstants.URL.MMP_API);
        }

        String editorUrl = metaData.getString("com.mmp.android.MPConfig.EditorUrl");
        if (null == editorUrl) {
            editorUrl = MmpConstants.URL.SWITCHBOARD;
        }
        mEditorUrl = editorUrl;

        int resourceId = metaData.getInt("com.mmp.android.MPConfig.DisableViewCrawlerForProjects", -1);
        if (resourceId != -1) {
            mDisableViewCrawlerForProjects = context.getResources().getStringArray(resourceId);
        } else {
            mDisableViewCrawlerForProjects = new String[0];
        }

        MmpLog.v(LOG_MESSAGE_TAG, toString());
    }

    // Max size of queue before we require a flush. Must be below the limit the service will accept.
    public int getBulkUploadLimit() {
        return mBulkUploadLimit;
    }

    // Target max milliseconds between flushes. This is advisory.
    public int getFlushInterval() {
        return mFlushInterval;
    }

    // Whether the SDK should flush() queues when the app goes into the background or not.
    public boolean getFlushOnBackground() {
        return mFlushOnBackground;
    }

    // Throw away records that are older than this in milliseconds. Should be below the server side age limit for events.
    public long getDataExpiration() {
        return mDataExpiration;
    }

    public int getMinimumDatabaseLimit() { return mMinimumDatabaseLimit; }

    public boolean getDisableGestureBindingUI() {
        return mDisableGestureBindingUI;
    }

    public boolean getDisableEmulatorBindingUI() {
        return mDisableEmulatorBindingUI;
    }

    public boolean getDisableAppOpenEvent() {
        return mDisableAppOpenEvent;
    }

    public boolean getDisableViewCrawler() {
        return mDisableViewCrawler;
    }

    public String[] getDisableViewCrawlerForProjects() { return mDisableViewCrawlerForProjects; }

    public boolean getTestMode() {
        return mTestMode;
    }

    // Preferred URL for tracking events
    public String getEventsEndpoint() {
        return mEventsEndpoint;
    }

    // In parity with iOS SDK
    public void setServerURL(String serverURL) {
        setEventsEndpointWithBaseURL(serverURL);
        setPeopleEndpointWithBaseURL(serverURL);
        setGroupsEndpointWithBaseURL(serverURL);
        setDecideEndpointWithBaseURL(serverURL);
    }

    private String getEndPointWithIpTrackingParam(String endPoint, boolean ifUseIpAddressForGeolocation) {
        if (endPoint.contains("?ip=")) {
            return endPoint.substring(0, endPoint.indexOf("?ip=")) + "?ip=" + (ifUseIpAddressForGeolocation ? "1" : "0");
        } else {
            return endPoint + "?ip=" + (ifUseIpAddressForGeolocation ? "1" : "0");
        }
    }

    private void setEventsEndpointWithBaseURL(String baseURL) {
        setEventsEndpoint(getEndPointWithIpTrackingParam(baseURL + MmpConstants.URL.EVENT, getUseIpAddressForGeolocation()));
    }

    private void setEventsEndpoint(String eventsEndpoint) {
        mEventsEndpoint = eventsEndpoint;
    }

    // Preferred URL for tracking people
    public String getPeopleEndpoint() {
        return mPeopleEndpoint;
    }

    private void setPeopleEndpointWithBaseURL(String baseURL) {
        setPeopleEndpoint(getEndPointWithIpTrackingParam(baseURL + MmpConstants.URL.PEOPLE, getUseIpAddressForGeolocation()));
    }

    private void setPeopleEndpoint(String peopleEndpoint) {
        mPeopleEndpoint = peopleEndpoint;
    }

    // Preferred URL for tracking groups
    public String getGroupsEndpoint() {
        return mGroupsEndpoint;
    }

    private void setGroupsEndpointWithBaseURL(String baseURL) {
        setGroupsEndpoint(baseURL + MmpConstants.URL.GROUPS);
    }

    private void setGroupsEndpoint(String groupsEndpoint) {
        mGroupsEndpoint = groupsEndpoint;
    }

    // Preferred URL for pulling decide data
    public String getDecideEndpoint() {
        return mDecideEndpoint;
    }

    private void setDecideEndpointWithBaseURL(String baseURL) {
        setDecideEndpoint(baseURL + MmpConstants.URL.DECIDE);
    }

    private void setDecideEndpoint(String decideEndpoint) {
        mDecideEndpoint = decideEndpoint;
    }

    // Check for and show eligible in app notifications on Activity changes
    public boolean getAutoShowMmpUpdates() {
        return mAutoShowMmpUpdates;
    }

    // Preferred URL for connecting to the editor websocket
    public String getEditorUrl() {
        return mEditorUrl;
    }

    public boolean getDisableDecideChecker() {
        return mDisableDecideChecker;
    }

    public boolean getIgnoreInvisibleViewsEditor() {
        return mIgnoreInvisibleViewsEditor;
    }

    public int getNotificationDefaults() {
        return mNotificationDefaults;
    }

    public int getMinimumSessionDuration() {
        return mMinSessionDuration;
    }

    public int getSessionTimeoutDuration() {
        return mSessionTimeoutDuration;
    }

    public boolean getDisableExceptionHandler() {
        return mDisableExceptionHandler;
    }

    public String getNotificationChannelId() {
        return mNotificationChannelId;
    }

    public String getNotificationChannelName() {
        return mNotificationChannelName;
    }

    public int getNotificationChannelImportance() {
        return mNotificationChannelImportance;
    }

    private boolean getUseIpAddressForGeolocation() {
        return mUseIpAddressForGeolocation;
    }

    public void setUseIpAddressForGeolocation(boolean useIpAddressForGeolocation) {
        mUseIpAddressForGeolocation = useIpAddressForGeolocation;
        setEventsEndpoint(getEndPointWithIpTrackingParam(getEventsEndpoint(), useIpAddressForGeolocation));
        setPeopleEndpoint(getEndPointWithIpTrackingParam(getPeopleEndpoint(), useIpAddressForGeolocation));
    }

    public void setEnableLogging(boolean enableLogging) {
        DEBUG = enableLogging;
        MmpLog.setLevel(DEBUG ? MmpLog.VERBOSE : MmpLog.NONE);
    }

    // Pre-configured package name for resources, if they differ from the application package name
    //
    // mContext.getPackageName() actually returns the "application id", which
    // usually (but not always) the same as package of the generated R class.
    //
    //  See: http://tools.android.com/tech-docs/new-build-system/applicationid-vs-packagename
    //
    // As far as I can tell, the original package name is lost in the build
    // process in these cases, and must be specified by the developer using
    // MPConfig meta-data.
    public String getResourcePackageName() {
        return mResourcePackageName;
    }

    // This method is thread safe, and assumes that SSLSocketFactory is also thread safe
    // (At this writing, all HttpsURLConnections in the framework share a single factory,
    // so this is pretty safe even if the docs are ambiguous)
    public synchronized SSLSocketFactory getSSLSocketFactory() {
        return mSSLSocketFactory;
    }

    // This method is thread safe, and assumes that OfflineMode is also thread safe
    public synchronized OfflineMode getOfflineMode() {
        return mOfflineMode;
    }

    // ImageStore LRU Cache size will be availableMaxMemory() / mImageCacheMaxMemoryFactor
    public int getImageCacheMaxMemoryFactor() {
        return mImageCacheMaxMemoryFactor;
    }

    ///////////////////////////////////////////////

    // Package access for testing only- do not call directly in library code
    /* package */ static MmpConfig readConfig(Context appContext) {
        final String packageName = appContext.getPackageName();
        try {
            final ApplicationInfo appInfo = appContext.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle configBundle = appInfo.metaData;
            if (null == configBundle) {
                configBundle = new Bundle();
            }
            return new MmpConfig(configBundle, appContext);
        } catch (final NameNotFoundException e) {
            throw new RuntimeException("Can't configure Mmp with package name " + packageName, e);
        }
    }

    @Override
    public String toString() {
        return "Mmp (" + VERSION + ") configured with:\n" +
                "    AutoShowMmpUpdates " + getAutoShowMmpUpdates() + "\n" +
                "    BulkUploadLimit " + getBulkUploadLimit() + "\n" +
                "    FlushInterval " + getFlushInterval() + "\n" +
                "    DataExpiration " + getDataExpiration() + "\n" +
                "    MinimumDatabaseLimit " + getMinimumDatabaseLimit() + "\n" +
                "    DisableAppOpenEvent " + getDisableAppOpenEvent() + "\n" +
                "    DisableViewCrawler " + getDisableViewCrawler() + "\n" +
                "    DisableGestureBindingUI " + getDisableGestureBindingUI() + "\n" +
                "    DisableEmulatorBindingUI " + getDisableEmulatorBindingUI() + "\n" +
                "    EnableDebugLogging " + DEBUG + "\n" +
                "    TestMode " + getTestMode() + "\n" +
                "    EventsEndpoint " + getEventsEndpoint() + "\n" +
                "    PeopleEndpoint " + getPeopleEndpoint() + "\n" +
                "    DecideEndpoint " + getDecideEndpoint() + "\n" +
                "    EditorUrl " + getEditorUrl() + "\n" +
                "    ImageCacheMaxMemoryFactor " + getImageCacheMaxMemoryFactor() + "\n" +
                "    DisableDecideChecker " + getDisableDecideChecker() + "\n" +
                "    IgnoreInvisibleViewsEditor " + getIgnoreInvisibleViewsEditor() + "\n" +
                "    NotificationDefaults " + getNotificationDefaults() + "\n" +
                "    MinimumSessionDuration: " + getMinimumSessionDuration() + "\n" +
                "    SessionTimeoutDuration: " + getSessionTimeoutDuration() + "\n" +
                "    DisableExceptionHandler: " + getDisableExceptionHandler() + "\n" +
                "    NotificationChannelId: " + getNotificationChannelId() + "\n" +
                "    NotificationChannelName: " + getNotificationChannelName() + "\n" +
                "    NotificationChannelImportance: " + getNotificationChannelImportance() + "\n" +
                "    FlushOnBackground: " + getFlushOnBackground() + "\n" +
                "    UseIpAddressForGeolocation: " + getUseIpAddressForGeolocation();
    }

    private final int mBulkUploadLimit;
    private final int mFlushInterval;
    private final boolean mFlushOnBackground;
    private final long mDataExpiration;
    private final int mMinimumDatabaseLimit;
    private final boolean mTestMode;
    private final boolean mDisableGestureBindingUI;
    private final boolean mDisableEmulatorBindingUI;
    private final boolean mDisableAppOpenEvent;
    private final boolean mDisableViewCrawler;
    private final boolean mDisableExceptionHandler;
    private final String[] mDisableViewCrawlerForProjects;
    private String mEventsEndpoint;
    private String mPeopleEndpoint;
    private String mGroupsEndpoint;
    private String mDecideEndpoint;
    private final boolean mAutoShowMmpUpdates;
    private final String mEditorUrl;
    private final String mResourcePackageName;
    private final boolean mDisableDecideChecker;
    private final int mImageCacheMaxMemoryFactor;
    private final boolean mIgnoreInvisibleViewsEditor;
    private final int mNotificationDefaults;
    private final int mMinSessionDuration;
    private final int mSessionTimeoutDuration;
    private boolean mUseIpAddressForGeolocation;
    private final int mNotificationChannelImportance;
    private final String mNotificationChannelId;
    private final String mNotificationChannelName;

    // Mutable, with synchronized accessor and mutator
    private SSLSocketFactory mSSLSocketFactory;
    private OfflineMode mOfflineMode;

    private static MmpConfig sInstance;
    private static final Object sInstanceLock = new Object();
    private static final String LOG_MESSAGE_TAG = "MmpAPI.Conf";
}
