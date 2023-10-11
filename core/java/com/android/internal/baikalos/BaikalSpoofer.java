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

import android.app.ActivityThread;
import android.app.Application;
import android.audio.policy.configuration.V7_0.AudioUsage;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemProperties;
import android.text.FontConfig;
import android.util.Log;

import android.provider.Settings;
import android.baikalos.AppProfile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


import android.graphics.Shader.TileMode;

public class BaikalSpoofer { 

    private enum OverrideSharedPrefsId {
        OVERRIDE_NONE,
        OVERRIDE_COM_ANDROID_CAMERA
    };

    private static final String TAG = "BaikalSpoofer";

    private static OverrideSharedPrefsId sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_NONE;

    private static boolean sIsGmsUnstable = false;
    private static boolean sIsFinsky = false;
    private static boolean sPreventHwKeyAttestation = false;
    private static boolean sHideDevMode = false;
    private static boolean sAutoRevokeDisabled = false;

    private static String sPackageName = null;
    private static String sProcessName = null;
    private static Context sContext = null;

    private static int sDefaultBackgroundBlurRadius = -1;
    private static int sDefaultBlurModeInt = -1;
    private static AudioManager sAudioManager = null;
    private static AudioDeviceInfo sBuiltinPlaybackDevice;
    private static AudioDeviceInfo sBuiltinRecordingDevice;

    private static boolean sOverrideAudioUsage = false;
    private static int sBaikalSpooferActive = 0;


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
    };

    public static void maybeSpoofProperties(Application app, Context context) {
        sBaikalSpooferActive++;
        maybeSpoofDevice(app.getPackageName(), context);
        maybeSpoofBuild(app.getPackageName(), app.getProcessName(), context);
    }

    public static int maybeSpoofFeature(String packageName, String name, int version) {
        if (packageName != null &&
                packageName.contains("com.google.android.apps.as") ) {
            if( AppProfile.isDebug() ) Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
            if( name.contains("PIXEL_2022_EXPERIENCE") || 
                name.contains("PIXEL_2022_MIDYEAR_EXPERIENCE") ) {
                return 0;
            }
            return -1;
        }

        if (packageName != null &&
                packageName.contains("com.google.android.apps.photos") ) {

            if( AppProfile.isDebug() ) Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
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
        /*
         * This would be much prettier if we just removed "final" from the Build fields,
         * but that requires changing the API.
         *
         * While this an awful hack, it's technically safe because the fields are
         * populated at runtime.
         */
        try {

            if( AppProfile.isDebug() ) Log.i(TAG, "Build.VERSION." + key + "=" + value);

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


    public static void setBuildField(String key, String value) {
        /*
         * This would be much prettier if we just removed "final" from the Build fields,
         * but that requires changing the API.
         *
         * While this an awful hack, it's technically safe because the fields are
         * populated at runtime.
         */
        try {

            if( AppProfile.isDebug() ) Log.i(TAG, "Build." + key + "=" + value);

            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    public static void setProcessField(String key, String value) {
        /*
         * This would be much prettier if we just removed "final" from the Build fields,
         * but that requires changing the API.
         *
         * While this an awful hack, it's technically safe because the fields are
         * populated at runtime.
         */
        try {

            if( AppProfile.isDebug() ) Log.i(TAG, "Process." + key + "=" + value);

            // Unlock
            Field field = Process.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Process." + key, e);
        }
    }

    private static void maybeSpoofBuild(String packageName, String processName, Context context) {

        sProcessName = processName;
        sPackageName = packageName;

        //boolean needsWASpoof = List.of("pixelmigrate", "restore", "snapchat", "instrumentation").stream().anyMatch(packageName::contains);

        if ("com.google.android.gms".equals(packageName) ) {
            if( processName != null ) {
                sIsGmsUnstable = List.of("unstable", "instrumentation").stream().anyMatch(processName.toLowerCase()::contains);
            }
        }

        
        if(  sIsGmsUnstable ) {
            Log.e(TAG, "Spoof Device for GMS SN check: " + Application.getProcessName());

            setBuildField("BRAND", "google");
            setBuildField("PRODUCT", "marlin");
            setBuildField("MODEL", "Pixel XL");
        	setBuildField("MANUFACTURER", "Google");
            setBuildField("DEVICE", "marlin");
            setBuildField("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
            setBuildField("ID", "NJH47F");
            setBuildField("TYPE", "user");
            setBuildField("TAGS", "release-keys");
            setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
            setVersionField("SECURITY_PATCH", "2017-08-05");

        } else if( "com.android.vending".equals(packageName) ) {
            sIsFinsky = true;
        }
    }


    private static void maybeSpoofDevice(String packageName, Context context) {

        sContext = context;

        if( packageName == null ) return;

        setOverrideSharedPrefs(packageName);

        int device_id = -1;

        try {

            if( AppProfile.isDebug() ) Log.i(TAG, "Loadins settings for :" + packageName + ", sBaikalSpooferActive=" + sBaikalSpooferActive );

            sDefaultBackgroundBlurRadius = -1; /*Settings.System.getInt(context.getContentResolver(),
                Settings.System.BAIKALOS_BACKGROUND_BLUR_RADIUS, -1);*/

            sDefaultBlurModeInt = -1; /*Settings.System.getInt(context.getContentResolver(),
                Settings.System.BAIKALOS_BACKGROUND_BLUR_TYPE, -1);*/
            
            try {
                sAutoRevokeDisabled = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.BAIKALOS_DISABLE_AUTOREVOKE,0) == 1;
            } catch(Exception er) {
                Log.e(TAG, "Failed to read auto revoke status for:" + packageName);
            };

            AppProfile profile = null;
            
            try {
                profile = AppProfileSettings.loadSingleProfile(packageName, context);
                if( AppProfile.isDebug() ) Log.i(TAG, "Loaded profile :" + profile.toString());
            } catch(Exception el) {
                Log.e(TAG, "Failed to load profile for:" + packageName + ":" + el.getMessage());
                profile = new AppProfile(packageName);
                if( "android".equals(packageName) ) {
                    profile.mSystemWhitelisted = true;
                    profile.mDoNotClose = true;
                    profile.mUid = myUid();
                    profile.mBackground = -2;
                }
            }

            android.baikalos.AppProfile.setCurrentAppProfile(profile, myUid());
           
            device_id = profile.mSpoofDevice - 1;

            if( profile.mSonification != 0 ) {
                sOverrideAudioUsage = true;
            }
               
            if( profile.mPreventHwKeyAttestation ) {
                sPreventHwKeyAttestation = true;
                if( AppProfile.isDebug() ) Log.i(TAG, "Overriding hardware attestation for :" + packageName + " to " + profile.mPreventHwKeyAttestation);
            } 
            if( profile.mHideDevMode ) {
                sHideDevMode = true;
                if( AppProfile.isDebug() ) Log.i(TAG, "Overriding developer mode for :" + packageName + " to " + profile.mHideDevMode);
            } 

            setBuildField("TYPE", "user");
            setBuildField("TAGS", "release-keys");

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

            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device Profile :" + packageName + ", device_id=" + device_id);

            SpoofDeviceInfo device = BaikalSpoofer.Devices[device_id];

            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device BRAND: " + device.deviceBrand);
            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device MANUFACTURER: " + device.deviceManufacturer);
            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device MODEL: " + device.deviceModel);
            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device DEVICE: " + device.deviceName);
            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device PRODUCT: " + device.deviceName);
            if( AppProfile.isDebug() ) Log.i(TAG, "Spoof Device FINGERPRINT: " + device.deviceFp);

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
        // Check stack for SafetyNet
        if (isCallerSafetyNet()) {
            throw new UnsupportedOperationException();
        }

        // Check stack for PlayIntegrity
        if (sIsFinsky) {
            throw new UnsupportedOperationException();
        }

        if(sPreventHwKeyAttestation) {
            throw new UnsupportedOperationException();
        } 
    }

    private static void setOverrideSharedPrefs(String packageName) {
        sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_NONE;
        if( "com.android.camera".equals(packageName) ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_ANDROID_CAMERA;
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

        switch(sOverrideSharedPrefsId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_ANDROID_CAMERA:
                if( key.equals("pref_camera_first_use_hint_shown_key") ) result = false;
                break;
        }

        if( AppProfile.isDebug() ) Log.i(TAG, "package=" + AppProfile.packageName() +  "/" + AppProfile.uid() + ": getBoolean " + key + " -> "  + value + " -> " + result);
        return result;
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

    public static int overrideAudioFlags(int flags_) {
        int flags = flags_;
        /*if( AppProfile.getCurrentAppProfile().mSonification == 1 ) {
            flags = flags_ | AudioAttributes.FLAG_AUDIBILITY_ENFORCED;
            Log.i(TAG,"Forced Sonification. flags=|FLAG_AUDIBILITY_ENFORCED");
        }*/
        return flags;
    }

    public static int overrideAudioUsage(int usage_) {
        int usage = usage_;
        /*if( AppProfile.getCurrentAppProfile().mSonification >= 1 ) {
            usage = AppProfile.getCurrentAppProfile().mSonification == 2 ? 
                6 : 0;
            Log.i(TAG,"Forced Sonification. Usage_old=" + AudioAttributes.usageToString(usage_) + ", usage=" + AudioAttributes.usageToString(usage));
            return usage;
        }*/
        return usage;
    }



    public static AudioDeviceInfo overridePrefferedDevice(AudioRouting self, AudioDeviceInfo originalDeviceInfo, boolean record) {
        if( AppProfile.getCurrentAppProfile().mSonification != 0 ) {
            setBuiltinDevices();
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

    public static boolean updatePreferredDevice(AudioRouting self, AudioDeviceInfo originalDeviceInfo, boolean record) {
        if( AppProfile.getCurrentAppProfile().mSonification != 0 ) {
            setBuiltinDevices();
            if( !record ) {
                if( sBuiltinPlaybackDevice != null ) self.setPreferredDevice(sBuiltinPlaybackDevice);
                if( AppProfile.isDebug() ) Log.i(TAG,"updatePreferredDevice playback:" + originalDeviceInfo + "->" + sBuiltinPlaybackDevice, new Throwable());
            } else {
                if( sBuiltinRecordingDevice != null ) self.setPreferredDevice(sBuiltinRecordingDevice);
                if( AppProfile.isDebug() ) Log.i(TAG,"updatePreferredDevice record:" + originalDeviceInfo + "->" + sBuiltinRecordingDevice, new Throwable());
            }
            return true;
        }
        return false;
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

            AudioDeviceInfo[] deviceList = sAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : deviceList) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    sBuiltinPlaybackDevice = device;
                    break;
                }
            }
            deviceList = sAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo device : deviceList) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    sBuiltinRecordingDevice = device;
                    break;
                }
            }
        }
    }

}
