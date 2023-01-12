/*
 * Copyright (C) 2019 BaikalOS
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

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

import android.util.Slog;

import android.content.Context;
import android.content.ContentResolver;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;


import android.database.ContentObserver;
import android.net.Uri;

import android.os.Binder;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.UserHandle;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

import com.android.server.BaikalSystemService;


import java.util.HashMap;
import java.util.Map;


public class BaikalAppManager extends ContentObserver {

    private static final String TAG = "BaikalAppManager";

    private static final boolean DEBUG = true;

    private final Context mContext;
    private final Handler mHandler;
    private final ContentResolver mResolver;
    private final BaikalSystemService mBss;

    private static int sUidGms = -1;
    private static int sUidHms = -1;
    private static int sUidGP = -1;
    private static int sUidAppGallery = -1;
    private static int sUidFDroid = -1;
    private static int sUidAurora = -1;

    private boolean mModuleGms = false;
    private boolean mModuleGmsAA = false;
    private boolean mModuleGmsAssistant = false;
    private boolean mModuleHms = false;
    private boolean mModuleFDroid = false;
    private boolean mModuleAurora = false;

    private HashMap<String, BaikalApplication> _appByName = new HashMap<String, BaikalApplication> ();
    private HashMap<String, BaikalApplicationPackage> _packageByName = new HashMap<String, BaikalApplicationPackage> ();

    public static final String [] sPackagesGoogleServices = {
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.setupwizard",
        "com.google.android.onetimeinitializer",
        "com.google.android.play.games",
        "com.google.android.apps.docs",
        "com.google.android.tts",
        "com.google.android.apps.turbo",
        "com.android.vending",
        "com.google.android.partnersetup",
        "com.google.android.ext.shared",
        "com.google.android.packageinstaller",
        "com.google.android.backuptransport"
    };

    public static final String [] sPackagesGoogleAA = {
        "com.google.android.projection.gearhead"
    };

    public static final String [] sPackagesGoogleAssistant = {
        "com.google.android.apps.googleassistant",
        "com.google.android.googlequicksearchbox",
    };

    public static final String [] sPackagesHuaweiServices = {
        "com.huawei.appmarket",
        "com.huawei.hwid"
    };

    public static final String [] sPackagesFDroid = {
        "org.fdroid.fdroid",
        "org.fdroid.fdroid.privileged"
    };

    public static final String [] sPackagesAurora = {
        "com.aurora.store"
    };

    public BaikalAppManager(BaikalSystemService service, Context context, Handler handler) {
        super(handler);

        Slog.i(TAG,"BaikalAppManager");

        mContext = context;
        mHandler = handler;
        mBss = service;
        mResolver = context.getContentResolver();

        mResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BAIKALOS_MODULE_GMS),
                    false, this);
        mResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BAIKALOS_MODULE_GMS_AA),
                    false, this);
        mResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BAIKALOS_MODULE_GMS_ASSISTANT),
                    false, this);
        mResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BAIKALOS_MODULE_HMS),
                    false, this);
        mResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BAIKALOS_MODULE_FDROID),
                    false, this);
        mResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.BAIKALOS_MODULE_AURORA),
                    false, this);

        updateConstants(true);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        updateConstants(false);
    }

    public void onSystemReady() {
        sUidGms = getPackageUidLocked("com.google.android.gms");
        sUidHms = getPackageUidLocked("com.huawei.hwid");
        sUidGP = getPackageUidLocked("com.android.vending");
        sUidAppGallery = getPackageUidLocked("com.huawei.appmarket");
        sUidFDroid = getPackageUidLocked("org.fdroid.fdroid");
        sUidAurora = getPackageUidLocked("com.aurora.store");
    }

    private void updateConstants(boolean init) {

        /*
        updateGms(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_MODULE_GMS,0) == 1, init);

        updateGmsAA(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_MODULE_GMS_AA,0) == 1, init);

        updateGmsAssistant(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_MODULE_GMS_ASSISTANT,0) == 1, init);

        updateHms(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_MODULE_HMS,0) == 1, init);

        updateFDroid(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_MODULE_FDROID,0) == 1, init);

        updateAurora(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_MODULE_AURORA,0) == 1, init);
        */

    }

    /*
    private void updateGms(boolean enable, boolean force) {
        if( force || mModuleGms != enable ) {
            mModuleGms = enable;
            updatePackages(sPackagesGoogleServices,enable);
            if( !enable ) {
                updatePackages(sPackagesGoogleAA,false);
                updatePackages(sPackagesGoogleAssistant,false);
            }
        }
    }

    private void updateGmsAA(boolean enable, boolean force) {
        if( force || mModuleGmsAA != enable ) {
            mModuleGmsAA = enable;
            updatePackages(sPackagesGoogleAA,enable);
        }
    }

    private void updateGmsAssistant(boolean enable, boolean force) {
        if( force || mModuleGmsAssistant != enable ) {
            mModuleGmsAssistant = enable;
            updatePackages(sPackagesGoogleAssistant,enable);
        }
    }

    private void updateHms(boolean enable, boolean force) {
        if( force || mModuleHms != enable ) {
            mModuleHms = enable;
            updatePackages(sPackagesHuaweiServices,enable);
        }
    }

    private void updateFDroid(boolean enable, boolean force) {
        if( force || mModuleFDroid != enable ) {
            mModuleFDroid = enable;
            updatePackages(sPackagesFDroid,enable);
        }
    }

    private void updateAurora(boolean enable, boolean force) {
        if( force || mModuleAurora != enable ) {
            mModuleAurora = enable;
            updatePackages(sPackagesAurora,enable);
        }
    }

    private void updatePackages(String [] packages, boolean enable) {
        for(String pkg:packages) {
            setPackageEnabled(pkg,enable);
        }
    }
    */

    private int getPackageUidLocked(String packageName) {

        final PackageManager pm = mContext.getPackageManager();

        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName,PackageManager.MATCH_ALL);
            if( ai != null ) {
                Slog.i(TAG,"getPackageUidLocked package=" + packageName + ", uid=" + ai.uid);
                return ai.uid;
            }
        } catch(Exception e) {
            Slog.i(TAG,"getPackageUidLocked package=" + packageName + " not found on this device.");
        }
        return -1;
    }

    private void setPackageEnabled(String packageName, boolean enabled) {
        int state = enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
        try {
            mContext.getPackageManager().setApplicationEnabledSetting(packageName,state,0);
        } catch(Exception e1) {
            Slog.e(TAG, "setPackageEnabled: exception=", e1);
        }
    }

    /*
    private void updateDolbyService() {
        if( SystemProperties.getBoolean("sys.baikal.dolby.avail",false) ) {
            Boolean isDolbyEnabled = SystemProperties.getBoolean("persist.baikal.dolby.enable",false);
            if( BaikalConstants.BAIKAL_DEBUG_ACTIVITY ) Slog.i(TAG,"updateDolbyService isDolbyEnabled=" + isDolbyEnabled);
            updateDolbyConfiguration(isDolbyEnabled);
        }
    }

    
    private void updateAudioServices() {
        Boolean isViperAvailable = SystemProperties.getBoolean("sys.baikal.viper.avail",false);
        Boolean isViperEnabled = SystemProperties.getBoolean("persist.baikal.viper.enable",false);
        Boolean isJamesDSPEnabled = SystemProperties.getBoolean("persist.baikal.jdsp.enable",false);

       if( BaikalConstants.BAIKAL_DEBUG_ACTIVITY ) Slog.i(TAG,"updateAudioServices isViperEnabled=" + isViperEnabled + ", isJamesDSPEnabled=" + isJamesDSPEnabled);

        if( isViperAvailable && isViperEnabled ) {
            setPackageEnabled("com.pittvandewitt.viperfx",true);
            setPackageEnabled("com.android.musicfx",false);
            setPackageEnabled("james.dsp",false);
        } else {
            setPackageEnabled("com.pittvandewitt.viperfx",false);
        }

        if( isJamesDSPEnabled ) {
            setPackageEnabled("com.pittvandewitt.viperfx",false);
            setPackageEnabled("com.android.musicfx",false);
            setPackageEnabled("james.dsp",true);
        } else {
            setPackageEnabled("james.dsp",false);
        }

        if( !isJamesDSPEnabled && !isViperAvailable ) {
            setPackageEnabled("com.android.musicfx",true);
        }
    }


    private void updateDolbyConfiguration(boolean enabled) {
        
        if( !enabled ) {
            setPackageEnabled("com.motorola.dolby.dolbyui",false);
            setPackageEnabled("com.dolby.daxservice",false);
        } else {
            setPackageEnabled("com.motorola.dolby.dolbyui",true);
            setPackageEnabled("com.dolby.daxservice",true);
        }
    }
    */

    private static int getUidGms() {
        return sUidGms;
    }

    private static int getUidHms() {
        return sUidHms;
    }

    private static int getUidPlayMarket() {
        return sUidGP;
    }

    private static int getUidAppGallery() {
        return sUidAppGallery;
    }


    public static int getTemporaryAppWhitelistDuration(int uid, String packageName, String activity) {
        if( activity != null ) {
            if( activity.startsWith("com.huawei.android.push.intent.RECEIVE") ) { 
                Slog.i(TAG,"getTemporaryAppWhitelistDuration: Huawei Push");
                return 10000;
            } else if( activity.startsWith("com.google.android.c2dm.intent.RECEIVE") ) {
                Slog.i(TAG,"getTemporaryAppWhitelistDuration: Google Push");
                return 10000;
            }
        }
        return 0;
    }

    public class BaikalApplication {
        public final String mId;
        public final String mName;
        public final String[] mPackages;

        public Boolean mEnabled;
        
        public BaikalApplication(String id, String name, String [] packages) {
            mId = id;
            mName = name;
            mPackages = packages;
        }
    }

    public class BaikalApplicationPackage {
        public int mUid;
        public String mPackageName;
        public Boolean mEnabled; 
    }
}
