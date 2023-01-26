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
//import static android.provider.Settings.Secure.GMS_ENABLED;

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
import android.provider.Settings;

import com.android.server.ServiceThread;
import com.android.server.SystemService;

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
    };

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

        boolean enabledGms = entries[0].mEnabled;
        boolean enabledHms = entries[1].mEnabled;
        boolean enabledFdroid = entries[2].mEnabled;
        boolean enabledAurora = entries[3].mEnabled;

        if( !enabledGms &&
                Arrays.stream(GMS_PACKAGES).anyMatch(packageName::equals) ) return true;

        if( !enabledHms &&
                Arrays.stream(HMS_PACKAGES).anyMatch(packageName::equals) ) return true;

        if( !enabledFdroid &&
                Arrays.stream(FDROID_PACKAGES).anyMatch(packageName::equals) ) return true;

        if( !enabledAurora &&
                Arrays.stream(AURORA_PACKAGES).anyMatch(packageName::equals) ) return true;
        
        return false;
    }

    public static ParceledListSlice<PackageInfo> recreatePackageList(
                            int userId, ParceledListSlice<PackageInfo> list) {

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) return list;

        boolean enabledGms = entries[0].mEnabled;
        boolean enabledHms = entries[1].mEnabled;
        boolean enabledFdroid = entries[2].mEnabled;
        boolean enabledAurora = entries[3].mEnabled;


        List<PackageInfo> oldList = list.getList();
        ArrayList<PackageInfo> newList = new ArrayList<>();
        for (PackageInfo info : oldList) {

            if (!enabledGms && info.packageName != null &&
                    Arrays.stream(GMS_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            if (!enabledHms && info.packageName != null &&
                    Arrays.stream(HMS_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            if (!enabledFdroid && info.packageName != null &&
                    Arrays.stream(FDROID_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            if (!enabledAurora && info.packageName != null &&
                    Arrays.stream(AURORA_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            newList.add(info);
        }

        return new ParceledListSlice<>(newList);
    }

    public static List<ApplicationInfo> recreateApplicationList(
                            int userId, List<ApplicationInfo> list) {

        BaikalAppManagerEntry[] entries = sCachedSettings.get(userId);
        if( entries == null ) return list;

        boolean enabledGms = entries[0].mEnabled;
        boolean enabledHms = entries[1].mEnabled;
        boolean enabledFdroid = entries[2].mEnabled;
        boolean enabledAurora = entries[3].mEnabled;


        ArrayList<ApplicationInfo> newList = new ArrayList<>();
        for (ApplicationInfo info : list) {

            if (!enabledGms && info.packageName != null &&
                    Arrays.stream(GMS_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            if (!enabledHms && info.packageName != null &&
                    Arrays.stream(HMS_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            if (!enabledFdroid && info.packageName != null &&
                    Arrays.stream(FDROID_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            if (!enabledAurora && info.packageName != null &&
                    Arrays.stream(AURORA_PACKAGES).anyMatch(info.packageName::equals))
                continue;

            newList.add(info);
        }

        return newList;
    }

    private void updateStateForUser(int userId) {

        boolean enabledGms = Settings.Secure.getIntForUser(mResolver, Settings.Secure.GMS_ENABLED, 0, userId) == 1;
        boolean enabledHms = Settings.Secure.getIntForUser(mResolver, Settings.Secure.HMS_ENABLED, 0, userId) == 1;
        boolean enabledFdroid = Settings.Secure.getIntForUser(mResolver, Settings.Secure.FDROID_ENABLED, 0, userId) == 1;
        boolean enabledAurora = Settings.Secure.getIntForUser(mResolver, Settings.Secure.AURORA_ENABLED, 0, userId) == 1;

        BaikalAppManagerEntry[] managedApps = new BaikalAppManagerEntry[] {
            new BaikalAppManagerEntry("gms", Settings.Secure.GMS_ENABLED, GMS_PACKAGES, enabledGms),
            new BaikalAppManagerEntry("hms", Settings.Secure.HMS_ENABLED, HMS_PACKAGES, enabledHms),
            new BaikalAppManagerEntry("fdroid", Settings.Secure.FDROID_ENABLED, FDROID_PACKAGES, enabledFdroid),
            new BaikalAppManagerEntry("aurora", Settings.Secure.AURORA_ENABLED, AURORA_PACKAGES, enabledAurora),
        };

        sCachedSettings.put(userId, managedApps);

        updatePackagesStateForUser(GMS_PACKAGES, enabledGms, userId);
        updatePackagesStateForUser(HMS_PACKAGES, enabledHms, userId);
        updatePackagesStateForUser(FDROID_PACKAGES, enabledFdroid, userId);
        updatePackagesStateForUser(AURORA_PACKAGES, enabledAurora, userId);

    }

    private void updatePackagesStateForUser(String[] packages, boolean enabled, int userId) {
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

        SettingsObserver observer = new SettingsObserver(mHandler, userId);

        mResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.GMS_ENABLED), false, observer, userId);
        mResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.HMS_ENABLED), false, observer, userId);
        mResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.FDROID_ENABLED), false, observer, userId);
        mResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.AURORA_ENABLED), false, observer, userId);

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

    public class BaikalAppManagerEntry {
        public final String mName;
        public final String mSettingsUri;
        public final String [] mPackages;
        public boolean mEnabled;

        public BaikalAppManagerEntry(String Name, String SettingsUri, String [] Packages, boolean Enabled) {
            mName = Name;
            mPackages = Packages;
            mEnabled = Enabled;
            mSettingsUri = SettingsUri;
        }
    }

}