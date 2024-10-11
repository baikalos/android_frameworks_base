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

package com.android.internal.baikalos;

import static android.os.Process.myUid;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;


import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.Application;
import android.audio.policy.configuration.V7_0.AudioUsage;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioSystem;
import android.os.Binder;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemProperties;
import android.text.FontConfig;
import android.util.Log;

import android.provider.Settings;
import android.baikalos.AppProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dalvik.system.PathClassLoader;

import android.graphics.Shader.TileMode;

public class BaikalSpoofer { 

    private enum OverrideSharedPrefsId {
        OVERRIDE_NONE,
        OVERRIDE_COM_ANDROID_CAMERA,
        OVERRIDE_COM_GOOGLE_GMS
    };

    private enum OverrideSystemPropertiesId {
        OVERRIDE_NONE,
        OVERRIDE_COM_GOOGLE_GMS_UNSTABLE
    };

    private static final String TAG = "BaikalSpoofer";


    private static final boolean FORCE_AD_ENABLE_SYSTEM = true;
    private static final boolean FORCE_AD_ENABLE_DEFAULT = true;

    public static String MANUFACTURER = "Google";
    public static String MODEL = "Pixel 6";
    public static String FINGERPRINT = "google/oriole_beta/oriole:15/AP41.240823.009/12329489:user/release-keys";
    public static String BRAND = "google";
    public static String PRODUCT = "oriole_beta";
    public static String DEVICE = "oriole";
    public static String RELEASE = "15";
    public static String ID = "AP41.240823.009";
    public static String INCREMENTAL = "12329489";
    public static String SECURITY_PATCH = "2024-09-05";
    public static int FIRST_API_LEVEL = 31;


    private static OverrideSharedPrefsId sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_NONE;
    private static OverrideSystemPropertiesId sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_NONE;

    static volatile boolean sIsGmsUnstable = false;
    static volatile boolean sIsFinsky = false;
    static volatile boolean sIsExcluded = false;
    static volatile boolean sPreventHwKeyAttestation = false;
    static volatile boolean sHideDevMode = false;
    static volatile boolean sAutoRevokeDisabled = false;
    static volatile boolean sEnableGmsSpoof = false;
    static volatile boolean sDisableCertificateSpoof = false;
    static volatile boolean sApplicationFilterDisabled = false;

    private static String sPackageName = null;
    private static String sProcessName = null;
    private static Context sContext = null;
    private static PackageManager sPackageManager = null;
    private static ActivityManager sActivityManager = null;

    private static int sDefaultBackgroundBlurRadius = -1;
    private static int sDefaultBlurModeInt = -1;
    private static AudioManager sAudioManager = null;
    private static AudioDeviceInfo sBuiltinPlaybackDevice;
    private static AudioDeviceInfo sBuiltinRecordingDevice;

    private static boolean sOverrideAudioUsage = false;
    private static int sBaikalSpooferActive = 0;

    private static AppVolumeDB sAppVolumeDB;

    private static HashMap<String, AppProfile> sCachedProfiles;
    //private static HashMap<Integer, AppProfile> sCachedProfileUids;

        //CLAMP   (0),
        /**
         * Repeat the shader's image horizontally and vertically.
         */
        //REPEAT  (1),
        /**
         * Repeat the shader's image horizontally and vertically, alternating
         * mirror images so that adjacent images always seam.
         */
        //MIRROR(2),
        /**
         * Render the shader's image pixels only within its original bounds. If the shader
         * draws outside of its original bounds, transparent black is drawn instead.
         */
        //DECAL(3);


    private static final String[] packagesToKeep = {
            "com.google.android.apps.motionsense.bridge",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.apps.pixelmigrate",
            "com.google.android.apps.recorder",
            "com.google.android.apps.restore",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.tycho",
            "com.google.android.apps.wearables.maestro.companion",
            "com.google.android.apps.youtube.kids",
            "com.google.android.apps.youtube.music",
            "com.google.android.as",
            "com.google.android.dialer",
            "com.google.android.euicc",
            "com.google.android.setupwizard",
            "com.google.android.youtube",
            "com.google.ar.core",
            "com.google.intelligence.sense",
            "com.google.oslo"
    };

    private static AppProfile spoofedProfile = null;

    public static SpoofDeviceInfo[] Devices = new SpoofDeviceInfo[] {
        new SpoofDeviceInfo("karna","M2007J20CI","Xiaomi","Poco X3 India", "xiaomi", "POCO/karna_eea/karna:11/RKQ1.200826.002/V12.0.6.4.RJGEUXM:user/release-keys"), // 1
        new SpoofDeviceInfo("surya","M2007J20CG","Xiaomi","Poco X3 NFC Global", "xiaomi", "POCO/surya_eea/surya:11/RKQ1.200826.002/V12.0.6.4.RJGEUXM:user/release-keys"), // 2
        new SpoofDeviceInfo("blueline","Pixel 3","Google","Pixel 3", "google" , "google/blueline/blueline:11/RQ3A.211001.001/7641976:user/release-keys" ), // 3
        new SpoofDeviceInfo("crosshatch","Pixel 3 XL","Google","Pixel 3 XL", "google", "google/crosshatch/crosshatch:11/RQ3A.211001.001/7641976:user/release-keys"), // 4
        new SpoofDeviceInfo("flame","Pixel 4","Google","Pixel 4", "google", "google/flame/flame:11/RQ3A.211001.001/7641976:user/release-keys" ), // 5
        new SpoofDeviceInfo("coral","Pixel 4 XL","Google","Pixel 4 XL", "google", "google/coral/coral:11/RQ3A.211001.001/7641976:user/release-keys" ), // 6
        new SpoofDeviceInfo("sunfish","Pixel 4a","Google","Pixel 4a", "google", "google/sunfish/sunfish:11/RQ3A.211001.001/7641976:user/release-keys" ), // 7
        new SpoofDeviceInfo("redfin","Pixel 5","Google","Pixel 5", "google", "google/redfin/redfin:12/SP1A.211105.003/7757856:user/release-keys" ), // 8
        new SpoofDeviceInfo("mdarcy","SHIELD Android TV","NVIDIA","Nvidia Shield TV 2019 Pro", "NVIDIA", "NVIDIA/mdarcy/mdarcy:9/PPR1.180610.011/4079208_2740.7538:user/release-keys" ), // 9
        new SpoofDeviceInfo("OnePlus8T","KB2005","OnePlus","OnePlus 8T", "OnePlus", "OnePlus/OnePlus8T/OnePlus8T:11/RP1A.201005.001/2110091917:user/release-keys" ), // 10
        new SpoofDeviceInfo("OnePlus8Pro","IN2023","OnePlus","OnePlus 8 Pro", "OnePlus", "OnePlus/OnePlus8Pro/OnePlus8Pro:11/RP1A.201005.001/2110091917:user/release-keys"  ), // 11
        new SpoofDeviceInfo("WW_I005D", "ASUS_I005_1","asus", "Asus ROG Phone 5", "asus", "asus/WW_I005D/ASUS_I005_1:11/RKQ1.201022.002/18.0840.2103.26-0:user/release-keys" ), // 12
        new SpoofDeviceInfo("XQ-AU52", "XQ-AU52","Sony", "Sony Xperia 10 II Dual", "Sony", "Sony/XQ-AU52_EEA/XQ-AU52:10/59.0.A.6.24/059000A006002402956232951:user/release-keys" ), // 13
        new SpoofDeviceInfo("XQ-AS72", "XQ-AS72","Sony", "Sony Xperia 2 5G (Asia)", "Sony" , null), // 14
        new SpoofDeviceInfo("z3s", "SM-G988B","Samsung", "Samsung S21", "samsung", "samsung/z3sxxx/z3s:10/QP1A.190711.020/G988BXXU1ATCT:user/release-keys"), // 15
        new SpoofDeviceInfo("cmi", "Mi 10 Pro","Xiaomi", "Xiaomi Mi 10 Pro", "xiaomi", "Xiaomi/cmi/cmi:11/RKQ1.200710.002/V12.1.2.0.RJACNXM:user/release-keys"), // 16
        new SpoofDeviceInfo("raven","Pixel 6 Pro","Google","Pixel 6 Pro", "google", "google/raven/raven:12/SD1A.210817.036/7805805:user/release-keys" ), // 17
        new SpoofDeviceInfo("dipper", "MI 8","Xiaomi", "Xiaomi MI 8", "xiaomi", "Xiaomi/dipper/dipper:10/QKQ1.190828.002/V11.0.3.0.QEAMIXM:user/release-keys"), // 18
        new SpoofDeviceInfo("vayu", "M2102J20SG","Xiaomi", "Poco X3 Pro", "xiaomi", "POCO/vayu_global/vayu:11/RKQ1.200826.002/V12.0.4.0.RJUMIXM:user/release-keys"), // 19
        new SpoofDeviceInfo("agate", "21081111RG","Xiaomi", "Xiaomi Mi 11T", "xiaomi", null), // 20
        new SpoofDeviceInfo("vayu", "R11 Plus","Oppo", "Oppo R11 Plus", "oppo", null), // 21
        new SpoofDeviceInfo("marlin","Pixel XL","Google","Pixel XL", "google" , "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys" ), // 22
        new SpoofDeviceInfo("star", "M2102K1G","Xiaomi", "Xiaomi Mi 11", "xiaomi", null), // 23 
        new SpoofDeviceInfo("cheetah", "Pixel 7 Pro","Google", "Pixel 7 Pro", "google", "google/cheetah/cheetah:13/TQ2A.230505.002/9891397:user/release-keys"), // 24
        new SpoofDeviceInfo("PDX-206", "SO-52A","Sony", "Sony Xperia 5", "Sony" , null), // 25
        new SpoofDeviceInfo("ZS600KL", "ASUS_Z01QD","asus", "Asus ROG 1", "asus" , null), // 26
        new SpoofDeviceInfo("obiwan", "ASUS_I003D","asus", "Asus ROG 3", "asus" , null), // 27
        new SpoofDeviceInfo("OnePlus9R","LE2101","OnePlus","OnePlus 9R", "OnePlus", null), // 28
        new SpoofDeviceInfo("munch","22021211RG","Xiaomi","POCO F4", "POCO", "POCO/munch_global/munch:13/RKQ1.211001.001/V14.0.1.0.TLMMIXM:user/release-keys"), // 29
        new SpoofDeviceInfo("cezanne","M2006J10C","Xiaomi","Redmi K30 Ultra","xiaomi", null), // 30
        new SpoofDeviceInfo("tangorpro","Pixel Tablet","Google","Pixel Tablet","google", "google/tangorpro/tangorpro:13/TQ3A.230901.001.B1/10750577:user/release-keys"), // 31
        new SpoofDeviceInfo("felix","Pixel Fold","Google","Pixel Fold","google", "google/felix/felix:13/TQ3C.230901.001.B1/10750989:user/release-keys"), // 32
        new SpoofDeviceInfo("husky","Pixel 8 Pro","Google","Pixel 8 Pro","google", "google/husky_beta/husky:15/AP31.240517.022/11948202:user/release-keys"), // 33
    };

    public static void maybeSpoofProperties(Application app, Context context) {
        sBaikalSpooferActive++;
        maybeSpoofDevice(app, context);
    }

    public static int maybeSpoofFeature(String packageName, String name, int version) {

        if (PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY.equals(name) || 
            PackageManager.FEATURE_STRONGBOX_KEYSTORE.equals(name) ||
            "android.software.device_id_attestation".equals(name) ) {
            return 0;
        }

        if (packageName != null &&
                packageName.contains("com.google.android.apps.as") ) {
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
            if( name.contains("PIXEL_2022_EXPERIENCE") || 
                name.contains("PIXEL_2022_MIDYEAR_EXPERIENCE") ) {
                return 0;
            }
            return -1;
        }

        if (packageName != null &&
                packageName.contains("com.google.android.apps.photos") ) {

            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
            if( name.contains("PIXEL_2021_EXPERIENCE") || 
                name.contains("PIXEL_2022_EXPERIENCE") || 
                name.contains("PIXEL_2023_EXPERIENCE") || 
                name.contains("PIXEL_2024_EXPERIENCE") ) {
                return 0;
            }
            if( "com.google.photos.trust_debug_certs".equals(name) ) return 1;
            if( "com.google.android.apps.photos.NEXUS_PRELOAD".equals(name) ) return 1;
            if( "com.google.android.apps.photos.nexus_preload".equals(name) ) return 1;
            if( "com.google.android.apps.photos.PIXEL_PRELOAD".equals(name) ) return 1;
            if( "com.google.android.apps.photos.PIXEL_2016_PRELOAD".equals(name) ) return 1;
            if( "com.google.android.feature.PIXEL_EXPERIENCE".equals(name) ) return 1;
            if( "com.google.android.feature.GOOGLE_BUILD".equals(name) ) return 1;
            if( "com.google.android.feature.GOOGLE_EXPERIENCE".equals(name) ) return 1;

            if( name != null ) {
                if( name.startsWith("com.google.android.apps.photos.PIXEL") ) return 0;
                if( name.startsWith("com.google.android.feature.PIXEL") ) return 0;
            }
            return -1;
        }
        return -1;
    }

    public static void setVersionField(String key, String value) {
        /*
         * This would be much prettier if we just removed "final" from the Build fields,
         * but that requires changing the API.
         *
         * While this an awful hack, it's technically safe because the fields are
         * populated at runtime.
         */
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Version." + key, e);
        }
    }

    public static void setVersionField(String key, int value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Version." + key, e);
        }
    }


    public static void setBuildField(String key, Object value) {
        try {
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    public static void setProcessField(String key, String value) {
        try {
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Process." + key + "=" + value);
            Field field = Process.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Process." + key, e);
        }
    }

    public static String SystemPropertiesGetNotNullOrEmpty(String key, String def) {
        String value = SystemProperties.get(key, "");
        if( value == null || "".equals(value) ) return def;
        return value;
    }

    public static int SystemPropertiesGetNotNullOrEmptyInt(String key, int def) {
        String value = SystemProperties.get(key, "");
        if( value == null || "".equals(value) ) return def;
        try {
            return Integer.valueOf(value);
        } catch(Exception e) {
            Log.e(TAG, "Failed to parse int value for " + key, e);
            return def;
        }
    }

    private static void maybeSpoofBuild(String packageName, String processName, Context context) {

        if( sProcessName == null )
            sProcessName = processName;
        if( sPackageName == null )
            sPackageName = packageName;

        //boolean needsWASpoof = List.of("pixelmigrate", "restore", "snapchat", "instrumentation").stream().anyMatch(packageName::contains);

        if ("com.google.android.gms".equals(packageName) ) {
            setBuildField("TIME", System.currentTimeMillis());
            if( processName != null ) {
                sIsGmsUnstable = List.of("unstable", "instrumentation").stream().anyMatch(processName.toLowerCase()::contains);
            }
        }

        
        if(  sIsGmsUnstable ) {

            if( !sEnableGmsSpoof ) {
                Log.e(TAG, "Spoof Device for GMS SN disabled: " + Application.getProcessName());
                sIsExcluded = true;
                return;
            }


            sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_COM_GOOGLE_GMS_UNSTABLE;
            Log.e(TAG, "Spoof Device for GMS SN check: " + Application.getProcessName());

            /*setBuildField("BRAND", "LeEco");
            setBuildField("PRODUCT", "LeMax2_WW");
            setBuildField("MODEL", "Le X820");
        	setBuildField("MANUFACTURER", "LeEco");
            setBuildField("DEVICE", "le_x2");
            setBuildField("FINGERPRINT", "LeEco/LeMax2_WW/le_x2:6.0.1/FKXOSOP5801910311S/letv10310125:user/release-keys");
            setBuildField("TYPE", "user");
            setBuildField("TAGS", "release-keys");
            setVersionField("DEVICE_INITIAL_SDK_INT", 23);
            setVersionField("SECURITY_PATCH", "2016-10-01");*/

        	/*
            setBuildField("MANUFACTURER", "Google");
            setBuildField("MODEL", "Pixel");
            setBuildField("FINGERPRINT", "google/sailfish/sailfish:8.1.0/OPM1.171019.011/4448085:user/release-keys");
            setBuildField("BRAND", "google");
            setBuildField("PRODUCT", "sailfish");
            setBuildField("DEVICE", "sailfish");
            setVersionField("RELEASE", "8.1.0");
            setBuildField("ID", "OPM1.171019.011");
            setVersionField("INCREMENTAL", "4448085");
            setBuildField("TYPE", "user");
            setBuildField("TAGS", "release-keys");
            setVersionField("SECURITY_PATCH", GMS_SECURITY_PATCH);
            setVersionField("DEVICE_INITIAL_SDK_INT", 25);
            */


            MANUFACTURER = SystemPropertiesGetNotNullOrEmpty("persist.spoof.manufacturer", MANUFACTURER);
            MODEL = SystemPropertiesGetNotNullOrEmpty("persist.spoof.model", MODEL);
            FINGERPRINT = SystemPropertiesGetNotNullOrEmpty("persist.spoof.fingerprint", FINGERPRINT);
            BRAND = SystemPropertiesGetNotNullOrEmpty("persist.spoof.brand", BRAND);
            PRODUCT = SystemPropertiesGetNotNullOrEmpty("persist.spoof.product", PRODUCT);
            DEVICE = SystemPropertiesGetNotNullOrEmpty("persist.spoof.device", DEVICE);
            RELEASE = SystemPropertiesGetNotNullOrEmpty("persist.spoof.release", RELEASE);
            ID = SystemPropertiesGetNotNullOrEmpty("persist.spoof.id", ID);
            INCREMENTAL = SystemPropertiesGetNotNullOrEmpty("persist.spoof.incremental", INCREMENTAL);
            SECURITY_PATCH = SystemPropertiesGetNotNullOrEmpty("persist.spoof.security_patch", SECURITY_PATCH);
            FIRST_API_LEVEL = SystemPropertiesGetNotNullOrEmptyInt("persist.spoof.firs_api_level", FIRST_API_LEVEL);

            setBuildField("MANUFACTURER", MANUFACTURER);
            setBuildField("MODEL", MODEL);
            setBuildField("FINGERPRINT", FINGERPRINT);
            setBuildField("BRAND", BRAND);
            setBuildField("PRODUCT", PRODUCT);
            setBuildField("DEVICE", DEVICE);
            setBuildField("ID", ID);
            setVersionField("INCREMENTAL", INCREMENTAL);
            setVersionField("RELEASE", RELEASE);
            setVersionField("SECURITY_PATCH", SECURITY_PATCH);
            setVersionField("DEVICE_INITIAL_SDK_INT", FIRST_API_LEVEL);

            setBuildField("TYPE", "user");
            setBuildField("TAGS", "release-keys");

        } else if( "com.android.vending".equals(packageName) ) {
            sIsFinsky = true;
        }

        if (Arrays.asList(packagesToKeep).contains(packageName) ||
                packageName.startsWith("com.google.android.GoogleCamera")) {
            sIsExcluded = true;
            return;
        }

        if( sIsFinsky && !sEnableGmsSpoof ) {
            Log.e(TAG, "Spoof Device for GMS SN disabled: " + Application.getProcessName());
            sIsExcluded = true;
            return;
        }

    }


    private static void maybeSpoofDevice(Application app, Context context) {

        sApplicationFilterDisabled = true;

        String packageName = app.getPackageName();
        String processName = app.getProcessName();

        if( myUid() != 1000 ) {
            BaikalDebugInternal.getInstance(context).updateConstants();
        }

        sContext = context;
        sPackageManager = sContext.getPackageManager();
        sActivityManager = sContext.getSystemService(ActivityManager.class);

        if( packageName == null || "".equals(packageName)) {
            if( context.getPackageName() != null && !"".equals(context.getPackageName()) ){
                packageName = context.getPackageName();
                Log.e(TAG, "Empty application package name. Using context name=" + packageName, new Throwable());
            } else {
                Log.e(TAG, "Empty package name", new Throwable());
                android.baikalos.AppProfile.setCurrentAppProfile(new AppProfile("unknown", myUid()), "invalid", myUid());
            }
            //return;
        }

        if( packageName == null || "".equals(packageName)) {
            Log.e(TAG, "Empty package name", new Throwable());
            return;
        }

        try {
            sEnableGmsSpoof = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.BAIKALOS_DISABLE_GMS_SPOOF,0) != 1;

            boolean isCertificateSpooferAvailable = context.getResources().
                getBoolean(com.android.internal.R.bool.config_certificateSpooferAvailable);
                
            if( isCertificateSpooferAvailable ) {
                sDisableCertificateSpoof = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BAIKALOS_DISABLE_CERTIFICATE_SPOOF,0) == 1;
            } else {
                sDisableCertificateSpoof = true;
            }
        } catch(Exception er) {
            Log.e(TAG, "Failed to read gms spoof status for:" + packageName, er);
        };

        if( "android".equals(packageName) ) {
            sEnableGmsSpoof = true;
        }


        try {
            sAppVolumeDB = new AppVolumeDB(context);
        } catch(Exception er) {
            Log.e(TAG, "Failed to load volume db for:" + packageName, er);
        };

        maybeSpoofBuild(packageName, processName,  context);

        setOverrideSharedPrefs(packageName);

        int device_id = -1;

        try {
            Log.i(TAG, "Loading settings for :" + packageName);

            sDefaultBackgroundBlurRadius = -1; /*Settings.System.getInt(context.getContentResolver(),
                Settings.System.BAIKALOS_BACKGROUND_BLUR_RADIUS, -1);*/

            sDefaultBlurModeInt = -1; /*Settings.System.getInt(context.getContentResolver(),
                Settings.System.BAIKALOS_BACKGROUND_BLUR_TYPE, -1);*/
            
            if( "android".equals(packageName) ) {
                sAutoRevokeDisabled = true;
            } else {
                try {
                    sAutoRevokeDisabled = Settings.Global.getInt(context.getContentResolver(),
                            Settings.Global.BAIKALOS_DISABLE_AUTOREVOKE,0) == 1;
                } catch(Exception er) {
                    Log.e(TAG, "Failed to read auto revoke status for:" + packageName, er);
                };
            }

            AppProfile profile = null;

            if( "android".equals(packageName) ) {
                profile = new AppProfile(packageName, myUid());
                profile.getBackgroundMode(false);
                sCachedProfiles = new HashMap<String, AppProfile>();
                //sCachedProfileUids = new HashMap<Integer, AppProfile>();
                profile.mAllowWhileIdle = true;
                profile.mSystemWhitelisted = true;
                profile.mBackgroundMode = -1;
                profile.mStaminaEnforced = true;
                profile.mStamina = true;
            } else {
                try {
                    sCachedProfiles = AppProfileSettings.loadCachedProfiles(context);
                    //sCachedProfileUids = AppProfileSettings.updateProfileUids(sCachedProfiles, context);
                } catch(Exception el) {
                    Log.e(TAG, "Failed to load app info for:" + packageName + ":" + el.getMessage());
                    sCachedProfiles = new HashMap<String, AppProfile>();
                    //sCachedProfileUids = new HashMap<Integer, AppProfile>();
                }

                if( sCachedProfiles.containsKey(packageName) ) {
                    profile = sCachedProfiles.get(packageName);
                } else {
                    profile = new AppProfile(packageName, myUid());
                }
                profile.getBackgroundMode(false);
                
                // profile = AppProfileSettings.loadSingleProfile(packageName, myUid(), context);
                if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Loaded profile :" + profile.toString());
            }

            if( profile == null ) {
                if( packageName == null ) packageName = "android";
                profile = new AppProfile(packageName, myUid());
                if( "android".equals(packageName) ) {
                    //profile.mSystemWhitelisted = true;
                    //profile.mDoNotClose = true;
                    //profile.mBackground = -2;
                }
            }

            //if( !sCachedProfileUids.containsKey(myUid()) ) {
            //    sCachedProfileUids.put(myUid(), profile);
            //}

            android.baikalos.AppProfile.setCurrentAppProfile(profile, packageName, myUid());
           
            device_id = profile.mSpoofDevice - 1;

            if( profile.mSonification != 0 ) {
                sOverrideAudioUsage = true;
            }
               
            if( profile.mPreventHwKeyAttestation ) {
                sPreventHwKeyAttestation = true;
                if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding hardware attestation for :" + packageName + " to " + profile.mPreventHwKeyAttestation);
            } 
            if( profile.mHideDevMode ) {
                sHideDevMode = true;
                if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding developer mode for :" + packageName + " to " + profile.mHideDevMode);
            } 

            setBuildField("TYPE", "user");
            setBuildField("TAGS", "release-keys");


            sApplicationFilterDisabled = false;


        } catch(Exception fl) {
            Log.e(TAG, "Failed to load profile for :" + packageName + ", sBaikalSpooferActive=" + sBaikalSpooferActive, fl);
        }

        try {
            if( device_id < 0 ) { 
                sBaikalSpooferActive--;
                return;
            }

            if( device_id >=  BaikalSpoofer.Devices.length ) {
                Log.e(TAG, "Spoof Device : invalid device id: " + device_id);
                sBaikalSpooferActive--;
                return;
            }

            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device Profile :" + packageName + ", device_id=" + device_id);

            SpoofDeviceInfo device = BaikalSpoofer.Devices[device_id];

            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device BRAND: " + device.deviceBrand);
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device MANUFACTURER: " + device.deviceManufacturer);
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device MODEL: " + device.deviceModel);
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device DEVICE: " + device.deviceName);
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device PRODUCT: " + device.deviceName);
            if( AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device FINGERPRINT: " + device.deviceFp);

            if( device.deviceBrand != null &&  !"".equals(device.deviceBrand) ) setBuildField("BRAND", device.deviceBrand);
            if( device.deviceManufacturer != null &&  !"".equals(device.deviceManufacturer) ) setBuildField("MANUFACTURER", device.deviceManufacturer);
            if( device.deviceModel != null &&  !"".equals(device.deviceModel) ) setBuildField("MODEL", device.deviceModel);
            if( device.deviceName != null &&  !"".equals(device.deviceName) ) setBuildField("DEVICE", device.deviceName);
            if( device.deviceName != null &&  !"".equals(device.deviceName) ) setBuildField("PRODUCT", device.deviceName);
            if( device.deviceFp != null && !"".equals(device.deviceFp) ) setBuildField("FINGERPRINT", device.deviceFp);

        } catch(Exception e) {
            Log.e(TAG, "Failed to spoof Device :" + packageName, e);
        }
        sBaikalSpooferActive--;
    }

    public static boolean isHideDevMode() {
        return sHideDevMode;
    }
    
    public static boolean isPreventHwKeyAttestation() {
        return sPreventHwKeyAttestation;
    }

    public static boolean isCurrentProcessGmsUnstable() {
        return sIsGmsUnstable;
    }

    public static String getPackageName() {
        return sProcessName;
    }

    public static String getProcessName() {
        return sPackageName;
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().toLowerCase().contains("droidguard"));
    }

    public static void onEngineGetCertificateChain() {

        if(sPreventHwKeyAttestation) {
            Log.i(TAG, "HW Attestation disabled for " + AppProfile.packageName() +  "/" + AppProfile.uid());
            throw new UnsupportedOperationException();
        } 

        if( sEnableGmsSpoof && sDisableCertificateSpoof ) {

            if (sIsExcluded) return;

            Log.i(TAG, "Certificate spoofing disabled for " + AppProfile.packageName() +  "/" + AppProfile.uid());

            if (isCallerSafetyNet()) {
                throw new UnsupportedOperationException();
            }
            if( sIsFinsky ) {
                throw new UnsupportedOperationException();
            }
        }

    
        // Check stack for SafetyNet
        // if (isCallerSafetyNet()) {
        //    throw new UnsupportedOperationException();
        //}

        // Check stack for PlayIntegrity
        // if (isCallerSafetyNet()) {
        //    throw new UnsupportedOperationException();
        //}
    }


    private static void setOverrideSharedPrefs(String packageName) {
        sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_NONE;
        if( "com.android.camera".equals(packageName) ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_ANDROID_CAMERA;
        if( sEnableGmsSpoof && "com.google.android.gms".equals(packageName) ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_GOOGLE_GMS;

    }

    public static String overrideStringSharedPreference(String key, String value) {
        String result = value;
        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getString " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Set<String> overrideSetStringSharedPreference(String key, Set<String> value) {
        Set<String> result = value;
        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getSet<String> " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Integer overrideIntegerSharedPreference(String key, Integer value) {
        Integer result = value;
        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getInteger " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Long overrideLongSharedPreference(String key, Long value) {
        Long result = value;
        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getLong " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Float overrideFloatSharedPreference(String key, Float value) {
        Float result = value;
        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getFloat " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static boolean overrideBooleanSharedPreference(String key, boolean value) {

        boolean result = value;

        if( "prefer_attest_key".equals(key) ) result = false;

        switch(sOverrideSharedPrefsId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_ANDROID_CAMERA:
                if( key.equals("pref_camera_first_use_hint_shown_key") ) result = false;
                break;
            case OVERRIDE_COM_GOOGLE_GMS:
                //if( key.equals("enabled") ) result = true;
                break;
        }

        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getBoolean " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static String overrideSetSystemProperty(@NonNull String key, @Nullable String val) {
        //if( AppProfile.isDebug() ) Log.d(TAG, "Tryset " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " val " + val);
        return null;
    }

    public static String overrideStringSystemProperty(@NonNull String key, @Nullable String rval) {
        //if( AppProfile.isDebug() ) Log.d(TAG, "Tryget " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " rval " + rval);
        if( getFilteredDevModeKey(key) ) return "";
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
                return overrideGmsUnstableString(key,rval);
        }
        return rval;
    }

    public static String overrideStringSystemProperty(@NonNull String key, @Nullable String def, @Nullable String rval) {
        //if( AppProfile.isDebug() ) Log.d(TAG, "Tryget " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " def " + def + " rval " + rval);
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
                return overrideGmsUnstableString(key,rval);
        }
        return rval;
    }

    public static int overrideIntSystemProperty(@NonNull String key, int def, int rval) {
        //if( AppProfile.isDebug() ) Log.d(TAG, "Tryget " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " def " + def + " rval " + rval);
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
                return overrideGmsUnstableInt(key,rval);
        }
        return rval;
    }

    public static long overrideLongSystemProperty(@NonNull String key, long def, long rval) {
        //if( AppProfile.isDebug() ) Log.d(TAG, "Tryget " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " def " + def + " rval " + rval);
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
                return overrideGmsUnstableLong(key,rval);
        }
        return rval;
    }

    public static Boolean overrideBooleanSystemProperty(@NonNull String key, Boolean def, Boolean rval) {
        //if( AppProfile.isDebug() ) Log.d(TAG, "Tryget " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " def " + def + " rval " + rval);
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
                return overrideGmsUnstableBoolean(key,rval);
        }
        return rval;
    }

    private static boolean getFilteredDevModeKey(String key) {
        if( AppProfile.getCurrentAppProfile().mHideDevMode ) {
           if( "init.svc.adbd".equals(key) ||
                "sys.usb.state".equals(key) ||
                "sys.usb.config".equals(key) ) {
                //Log.d(TAG, "Tryget " + AppProfile.packageName() + "/" + AppProfile.uid() + " system property " + key + " def " + def + " rval " + rval);
                return true;
            }
        }
        return false;
    }

    private static String overrideGmsUnstableString(String key, String def) {
        if( key != null ) {
            if( key.endsWith(".first_api_level") ) return String.valueOf(FIRST_API_LEVEL);
            if( key.endsWith(".security_patch") ) return SECURITY_PATCH;
            if( key.endsWith(".build.id") ) return ID;
        }
        return def;
    }

    private static int overrideGmsUnstableInt(String key, int def) {
        if( key != null ) {
            if( key.endsWith(".first_api_level") ) return FIRST_API_LEVEL;
        }
        return def;
    }

    private static long overrideGmsUnstableLong(String key, long def) {
        if( key != null ) {
            if( key.endsWith(".first_api_level") ) return FIRST_API_LEVEL;
        }
        return def;
    }

    private static Boolean overrideGmsUnstableBoolean(String key, Boolean def) {
        return def;
    }

    public static int getDefaultBackgroundBlurRadius() {
        return sDefaultBackgroundBlurRadius;
    }

    public static boolean isAutoRevokeDisabled() {
        return sAutoRevokeDisabled;
    }

        //CLAMP   (0),
        /**
         * Repeat the shader's image horizontally and vertically.
         */
        //REPEAT  (1),
        /**
         * Repeat the shader's image horizontally and vertically, alternating
         * mirror images so that adjacent images always seam.
         */
        //MIRROR(2),
        /**
         * Render the shader's image pixels only within its original bounds. If the shader
         * draws outside of its original bounds, transparent black is drawn instead.
         */
        //DECAL(3);

    public static TileMode getDefaultBlurTileMode(TileMode mode) {
        switch(sDefaultBlurModeInt) {
            case 1:
                Log.e(TAG, "Background blur mode : CLAMP");
                return TileMode.CLAMP;
            case 2:
                Log.e(TAG, "Background blur mode : REPEAT");
                return TileMode.REPEAT;
            case 3:
                Log.e(TAG, "Background blur mode : MIRROR");
                return TileMode.MIRROR;
            case 4:
                Log.e(TAG, "Background blur mode : DECAL");
                return TileMode.DECAL;
        }
        Log.e(TAG, "Background bluer mode : default:" + mode);
        return mode;
    }

    public static String overrideCameraId(String cameraId, int scenario) {
        String id = SystemProperties.get("persist.baikal.cameraid." + cameraId, "");

        if( AppProfile.isDebug() ) Log.i(TAG, "overrideCameraId: " + cameraId + " -> " + id);
        if( scenario == 0 ) return cameraId; 
        if( id != null &&  !"".equals(id) && !"-1".equals(id) ) return id;
        return cameraId;
    }

    public static int getNotificationSonification() {
        return SystemProperties.getInt("persist.baikal.sonif_a2dp", 0);
    }

    public static int overrideAudioFlags(int flags_) {
        int flags = flags_;
        /*if( (FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return flags;
        if( AppProfile.getCurrentAppProfile().mSonification == 1 ) {
            //if( !SystemProperties.getBoolean("persist.baikal.force_audio_flag_disable",false) ) {
                flags = flags_ | AudioAttributes.FLAG_AUDIBILITY_ENFORCED;
                //Log.i(TAG,"Forced Sonification. myUid()=" + myUid() + ", flags=|FLAG_AUDIBILITY_ENFORCED");
            //}
        }   
        //Log.i(TAG,"overrideAudioFlags: myUid()=" + myUid() + ", flags=" + flags);*/
        return flags;
    }

    public static int overrideAudioUsage(int usage_) {
        int usage = usage_;
        /*if( (FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return usage;
        try {
            if( AppProfile.getCurrentAppProfile().mSonification >= 1 ) {

                int audio_usage_1 = 0; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.audio_usage_1",13);
                int audio_usage_2 = 4; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.audio_usage_2",4);

                usage = AppProfile.getCurrentAppProfile().mSonification == 2 ? 
                    audio_usage_2 : audio_usage_1;
                Log.i(TAG,"Forced Sonification. myUid()=" + myUid() + ", Usage_old=" + AudioAttributes.usageToString(usage_) + ", usage=" + AudioAttributes.usageToString(usage));
                //return usage;
            }
            //Log.i(TAG,"overrideAudioUsage: myUid()=" + myUid() + ", Usage=" + AudioAttributes.usageToString(usage));
        } catch(Exception e) {}*/
        return usage;
    }

    public static int overrideAudioContentType(int contentType_) {
        int contentType = contentType_;
        /*if( (FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return contentType;
        try {
            if( AppProfile.getCurrentAppProfile().mSonification >= 1 ) {

                int content_type_1 = 0; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.content_type_1",4);
                int content_type_2 = 0; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.content_type_2",0);

                contentType = AppProfile.getCurrentAppProfile().mSonification == 2 ? 
                    content_type_2 : content_type_1;
                Log.i(TAG,"Forced Sonification. myUid()=" + myUid() + ", ContentType_old=" + AudioAttributes.contentTypeToString(contentType_) + ", ContentType=" + AudioAttributes.contentTypeToString(contentType));
                //return contentType;
            }
            //Log.i(TAG, "overrideAudioContentType:  myUid()=" + myUid() + ", ContentType=" + AudioAttributes.contentTypeToString(contentType));
        } catch(Exception e) {}*/
        return contentType;
    }

    public static int overrideAudioStreamType(int streamType_) {
        int streamType = streamType_;
        /*try {
            if( AppProfile.getCurrentAppProfile().mSonification >= 1 ) {

                int stream_type_1 = SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.stream_type_1",7);
                int stream_type_2 = SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.stream_type_2",7);

                streamType = AppProfile.getCurrentAppProfile().mSonification == 2 ? 
                    stream_type_2 : stream_type_1;
                //Log.i(TAG,"Forced Sonification. myUid()=" + myUid() + ", StreamType_old=" + streamTypeToString(streamType_) + ", StreamType=" + streamTypeToString(streamType));
                //return contentType;
            }
            //Log.i(TAG, "overrideAudioStreamType: myUid()=" + myUid() + ", StreamType=" + streamTypeToString(streamType));
        } catch(Exception e) {}*/
        return streamType;
    }

    private static String streamTypeToString(int streamType) {
        if( streamType >=0 && streamType < 12 ) return AudioSystem.STREAM_NAMES[streamType];
        return "STREAM_INVALID";
    }

    public static AudioDeviceInfo overridePreferredDevice(AudioRouting self, AudioDeviceInfo originalDeviceInfo, boolean record) {
        if( AppProfile.getCurrentAppProfile().mSonification != 0 ) {
            if( !(FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return originalDeviceInfo;
            if( sBuiltinPlaybackDevice == null || sBuiltinRecordingDevice == null ) setBuiltinDevices();
            if( !record ) {
                if( AppProfile.isDebug() ) Log.i(TAG,"overridePrefferedDevice playback :" + originalDeviceInfo + "->" + sBuiltinPlaybackDevice, new Throwable());
                return sBuiltinPlaybackDevice;
            } else {
                if( AppProfile.isDebug() ) Log.i(TAG,"overridePrefferedDevice record :" + originalDeviceInfo + "->" + sBuiltinRecordingDevice, new Throwable());
                return sBuiltinRecordingDevice;
            }
        }
        return originalDeviceInfo;
    }

    public static AudioDeviceInfo updatePreferredDevice(AudioRouting self, AudioDeviceInfo originalDeviceInfo, boolean record) {
        if( AppProfile.getCurrentAppProfile().mSonification != 0 ) {
            if( !(FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return originalDeviceInfo;
            if( sBuiltinPlaybackDevice == null || sBuiltinRecordingDevice == null ) setBuiltinDevices();
            if( !record ) {
                if( sBuiltinPlaybackDevice != null && (originalDeviceInfo == null || originalDeviceInfo.getId() != sBuiltinPlaybackDevice.getId()) ) self.setPreferredDevice(sBuiltinPlaybackDevice);
                if( AppProfile.isDebug() ) Log.i(TAG,"updatePreferredDevice playback:" + originalDeviceInfo + "->" + sBuiltinPlaybackDevice, new Throwable());
                return sBuiltinPlaybackDevice;
            } else {
                if( sBuiltinRecordingDevice != null && (originalDeviceInfo == null || originalDeviceInfo.getId() != sBuiltinRecordingDevice.getId()) ) self.setPreferredDevice(sBuiltinRecordingDevice);
                if( AppProfile.isDebug() ) Log.i(TAG,"updatePreferredDevice record:" + originalDeviceInfo + "->" + sBuiltinRecordingDevice, new Throwable());
                return sBuiltinRecordingDevice;
            }
        }
        return originalDeviceInfo;
    }

    public static AudioAttributes overrideAudioAttributes(AudioAttributes attributes, String logTag) {
        return attributes;
    }

    public static boolean isBaikalSpoofer() {
        return sBaikalSpooferActive > 0;
    }

    private static void setBuiltinDevices() {

        if( sAudioManager == null ) {
            sAudioManager = (AudioManager) sContext.getSystemService(Context.AUDIO_SERVICE);
        }

        if( sAudioManager == null && (AppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE) ) Log.i(TAG,"overridePrefferedDevice sAudioManager = null", new Throwable());

        if( sAudioManager != null && sBuiltinPlaybackDevice == null ) {

            AudioDeviceInfo speakerDevice = null;
            AudioDeviceInfo[] deviceList = sAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : deviceList) {
                if( AppProfile.isDebug() ) Log.i(TAG,"overridePrefferedDevice device:" + device.getType());
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
                    sBuiltinPlaybackDevice = device;
                    break;
                }
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    speakerDevice = device;
                }
            }
            if( sBuiltinPlaybackDevice == null ) sBuiltinPlaybackDevice = speakerDevice;
        }

        if( sAudioManager != null && sBuiltinRecordingDevice == null ) {
            AudioDeviceInfo[] deviceList = sAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo device : deviceList) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    sBuiltinRecordingDevice = device;
                    break;
                }
            }
        }
    }

    public static void newNameNotFoundExceptionTrace(String name) {
        if( "com.huawei.hwid".equals(name) /*|| "com.google.android.gms".equals(name)*/ ) {
            Log.i(TAG,"Lookup for " + name + " failed at:" /*, new Throwable()*/);
        }
    }


    public static boolean disableApplicationFilter() {
        return sApplicationFilterDisabled;
    }

    public static PackageInfo getPackageInfoAsUserCached(
            PackageInfo info, String packageName, long flags, int userId) {

        if( shouldFilterApplication(packageName,userId) ) return null;
        return info;
    }

    public static boolean shouldFilterApplication(String packageName, int userId) {
        int callingUid = Binder.getCallingUid();
        return shouldFilterApplication(packageName,userId,callingUid);
    }


    public static boolean shouldFilterApplication(String packageName, int userId, int callingUid) {
        return shouldFilterApplication(packageName,userId,callingUid,true);
    }

    public static boolean shouldFilterApplication(String packageName, int userId, int callingUid, boolean isSystem) {

        boolean hide3P = false;
        boolean hideGMS = false;
        boolean hideHMS = false;


        if( sApplicationFilterDisabled ) return false;

        if( callingUid == 0 ) callingUid = myUid();
        if( callingUid < 10000 ) return false;

        if( packageName != null ) {

            AppProfile profile = null; 
            if( callingUid != 1000 && myUid() == callingUid ) {
                profile = AppProfile.getCurrentAppProfile();
            } else {
                if( AppProfileSettings.isLoaded() ) {
                    profile = AppProfileSettings.getInstance().getProfile(callingUid);
                } 
            }

            if( profile != null ) {
                hideGMS = profile.mHideGMS;
                hide3P = profile.mHide3P;
                hideHMS = profile.mHideHMS;
            }

            if( profile == null ) hideGMS = sActivityManager.getBaikalPackageOption(null,callingUid,AppProfile.OPCODE_HIDE_GMS,0) != 0;

            if( hideGMS && packageName.startsWith("com.google.android.gms") ) { 
                if( packageName == null || sPackageName == null || packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideGMS packageName=" + packageName + " !!! disable by self call !!!! GMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideGMS(2) packageName=" + packageName + " GMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid() + ", callingUid=" + callingUid);
                return true;
            }


            if( profile == null ) hide3P = sActivityManager.getBaikalPackageOption(packageName,-1,AppProfile.OPCODE_HIDE_3P,0) != 0;

            if( hide3P && !isSystem ) {
                if( packageName == null || sPackageName == null || packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P packageName=" + packageName + " !!! disable by self call !!!! 3P for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P packageName=" + packageName + " proc=" + sProcessName + ", myPkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                return true;
            }

            if( profile == null ) hideHMS = sActivityManager.getBaikalPackageOption(packageName,-1,AppProfile.OPCODE_HIDE_HMS,0) != 0;

            if( hideHMS && (packageName.startsWith("com.huawei.hwid") || packageName.startsWith("com.huawei.hms")) ) { 
                if( packageName == null || sPackageName == null || packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideHMS packageName=" + packageName + " !!! disable by self call !!!! HMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideHMS(2) packageName=" + packageName + " HMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid() + ", callingUid=" + callingUid);
                return true;
            }


        }
        return false;
    }
}
