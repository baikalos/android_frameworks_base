/*
 * Copyright (C) 2022 Project Kaleidoscope
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.baikalos;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;

import com.android.server.ServiceThread;
import com.android.server.SystemService;

import android.util.Slog;

import java.lang.Boolean;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class BaikalAppManagerService extends SystemService {

    public static final String[] GMS_PACKAGES =
    {
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gms.policy_sidecar_aps",
        "com.google.android.gsf",
        "com.google.android.markup",
        "com.google.android.projection.gearhead",
        "com.google.android.syncadapters.calendar",
        "com.google.android.syncadapters.contacts",
        "com.google.android.syncadapters.contacts",
        "com.google.android.gm.exchange",
        "com.google.android.apps.customization.pixel",
        "com.google.android.apps.restore",
        "com.google.android.apps.wellbeing",
        "com.google.android.soundpicker",
        "com.google.android.settings.intelligence",
        "com.google.android.setupwizard",
        "com.google.android.partnersetup",
        "com.google.android.feedback",
        "com.google.android.tts",
        "com.google.android.marvin.talkback",
        "com.google.android.googlequicksearchbox"
    };

    public static final String[] HMS_PACKAGES = 
    {
        "com.huawei.hwid",
        "com.huawei.appmarket"
    };

    public static final String[] FDROID_PACKAGES = 
    {
        "org.fdroid.fdroid.privileged",
        "org.fdroid.fdroid"
    };

    public static final String[] AURORA_PACKAGES = 
    {
        "com.aurora.store",
        "com.aurora.services"
    };

    public static final String[] DOLBY_PACKAGES = 
    {
        "com.dolby.daxservice",
        "com.motorola.dolby.dolbyui"
    };

    public static final String[] JDSP_PACKAGES = 
    {
        "james.dsp"
    };

    public static final String[] AFX_PACKAGES = 
    {
        "org.lineageos.audiofx"
    };



    private BaikalAppManagerEntry[] initAppList() {
        BaikalAppManagerEntry[] managedApps = new BaikalAppManagerEntry[] {
            new BaikalAppManagerEntry("gms", "gms_enabled", "persist.baikal.srv.gms", GMS_PACKAGES, false, true),
            new BaikalAppManagerEntry("hms", "hms_enabled", "persist.baikal.srv.hms", HMS_PACKAGES, false, false),
            //new BaikalAppManagerEntry("fdroid", "fdroid_enabled", "persist.baikal.srv.fdroid", FDROID_PACKAGES, false, false),
            //new BaikalAppManagerEntry("aurora", "aurora_enabled", "persist.baikal.srv.aurora", AURORA_PACKAGES, false, false),
            new BaikalAppManagerEntry("dolby", "dolby_enabled", "persist.baikal.srv.dolby", DOLBY_PACKAGES, false, true),
            new BaikalAppManagerEntry("jdsp", "jdsp_enabled", "persist.baikal.srv.jdsp", JDSP_PACKAGES, false, false),
            new BaikalAppManagerEntry("afx", "afx_enabled", "persist.baikal.srv.afx", AFX_PACKAGES, false, false),
        };
        return managedApps;
    }

    private static final String TAG = "BaikalAppManagerService";

    private static HashMap<Integer, BaikalAppManagerEntry[]> sCachedSettings = new HashMap<>();

    private final Context mContext;
    private final IPackageManager mPM;
    private final IUserManager mUM;
    private final ContentResolver mResolver;
    private final String mOpPackageName;

    private ServiceThread mWorker;
    private Handler mHandler;
    private HashMap<Integer, SettingsObserver> mObservers;

    public static boolean shouldHide(int userId, String packageName) {
        if (packageName == null)
            return false;

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) return false;

        for(BaikalAppManagerEntry entry : entries) {
            if(Arrays.stream(entry.mPackages).anyMatch(packageName::equals)) {
                return !entry.mEnabled;
            }
        }
       
        return false;
    }

    public static ParceledListSlice<PackageInfo> recreatePackageList(
                            int userId, ParceledListSlice<PackageInfo> list) {

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) return list;

        List<PackageInfo> oldList = list.getList();
        ArrayList<PackageInfo> newList = new ArrayList<>();
        for (PackageInfo info : oldList) {

            if( info.packageName == null ) {
                newList.add(info);
                continue;
            }

            boolean skip = false;
            for(BaikalAppManagerEntry entry : entries) {
                if(
                    Arrays.stream(entry.mPackages).anyMatch(info.packageName::equals)) {
                    skip = !entry.mEnabled;
                    break;
                }
            }

            if( skip ) continue;
            newList.add(info);
        }

        return new ParceledListSlice<>(newList);
    }

    public static List<ApplicationInfo> recreateApplicationList(
                            int userId, List<ApplicationInfo> list) {

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) return list;

        ArrayList<ApplicationInfo> newList = new ArrayList<>();
        for (ApplicationInfo info : list) {

            boolean skip = false;
            for(BaikalAppManagerEntry entry : entries) {
                if( !entry.mEnabled && info.packageName != null &&
                    Arrays.stream(entry.mPackages).anyMatch(info.packageName::equals)) {
                    skip = true;
                    break;
                }
            }

            if( skip ) continue;
            newList.add(info);
        }

        return newList;
    }

    private void updateStateForUser(int userId) {

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) return;

        for(BaikalAppManagerEntry entry : entries) {
            boolean enabled = Settings.Secure.getIntForUser(mResolver, entry.mSettingsUri, entry.mEnabledByDefault ? 1 : 0, userId) == 1;
            entry.mEnabled = enabled;
            Slog.e(TAG, "updateStateForUser: app=" + entry.mName + ", enabled=" + enabled);
            updatePackagesStateForUser(entry.mPackages, enabled, userId);
            setSystemPropertyBoolean(entry.mSystemProperty,enabled);
        }
    }

    private void updatePackagesStateForUser(String[] packages, boolean enabled, int userId) {
        Slog.e(TAG, "updatePackagesStateForUser: packages=" + packages + ", enabled=" + enabled);
        try {
            for (String packageName : packages) {
                try {
                    if (enabled) {
                        mPM.setApplicationEnabledSetting(packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                                0, userId, mOpPackageName);
                    } else {
                        mPM.setApplicationEnabledSetting(packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                0, userId, mOpPackageName);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
    }

    private void initForUser(int userId) {
        if (userId < 0)
            return;

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) { 

            BaikalAppManagerEntry[] managedApps = initAppList();

            sCachedSettings.put(userId, managedApps);
            entries = managedApps;
        }

        SettingsObserver observer = new SettingsObserver(mHandler, userId);

        for(BaikalAppManagerEntry entry : entries) {
            mResolver.registerContentObserver(
                Settings.Secure.getUriFor(entry.mSettingsUri), false, observer, userId);
        }

        mObservers.put(userId, observer);

        updateStateForUser(userId);
    }

    private void deInitForUser(int userId) {
        if (userId < 0)
            return;

        SettingsObserver observer = mObservers.get(userId);
        if (observer == null)
            return;

        mResolver.unregisterContentObserver(observer);
        mObservers.remove(userId);
        sCachedSettings.remove(userId);
    }

    private void init() {
        try {
            for (UserInfo user : mUM.getUsers(false, false, false)) {
                initForUser(user.id);
            }
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiver(new UserReceiver(), filter,
                android.Manifest.permission.MANAGE_USERS, mHandler);
    }

    @Override
    public void onStart() {
        mWorker = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());

        init();
    }

    public BaikalAppManagerService(Context context) {
        super(context);
        mContext = context;
        mResolver = context.getContentResolver();
        mPM = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mUM = IUserManager.Stub.asInterface(ServiceManager.getService(Context.USER_SERVICE));
        mOpPackageName = context.getOpPackageName();
        mObservers = new HashMap<>();
        
    }

    private final class UserReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);

            if (Intent.ACTION_USER_ADDED.equals(intent.getAction()))
                initForUser(userId);
            else
                deInitForUser(userId);
        }
    }

    private final class SettingsObserver extends ContentObserver {
        private int mUserId;

        public SettingsObserver(Handler handler, int userId) {
            super(handler);
            mUserId = userId;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateStateForUser(mUserId);
        }
    }

    private boolean getSystemPropertyBoolean(String key) {
        if( SystemProperties.get(key,"0").equals("1") || SystemProperties.get(key,"0").equals("true") ) return true;
	    return false;
    }

    private void setSystemPropertyBoolean(String key, boolean value) {
        String text = value?"1":"0";
        Slog.e(TAG, "setSystemPropertyBoolean: key=" + key + ", value=" + value);
        SystemProperties.set(key, text);
    }

    public class BaikalAppManagerEntry {
        public final String mName;
        public final String mSettingsUri;
        public final String mSystemProperty;
        public final String [] mPackages;
        public final boolean mEnabledByDefault;
        public boolean mEnabled;

        public BaikalAppManagerEntry(String Name, String SettingsUri, String SystemProperty, String [] Packages, boolean Enabled, boolean EnabledByDefault) {
            mName = Name;
            mPackages = Packages;
            mEnabled = Enabled;
            mSettingsUri = SettingsUri;
            mSystemProperty = SystemProperty;
            mEnabledByDefault = EnabledByDefault;
        }
    }
}
