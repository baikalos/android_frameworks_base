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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioSystem;
import android.os.Binder;
import android.os.Build;
import android.os.LocaleList;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.FontConfig;
import android.util.Base64;
import android.util.Log;

import android.net.Uri;
import android.provider.Settings;
import android.baikalos.BaikalAppProfile;

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
        OVERRIDE_COM_GOOGLE_GMS,
        OVERRIDE_COM_GOOGLE_VENDING
    };

    private enum OverrideSystemPropertiesId {
        OVERRIDE_NONE,
        OVERRIDE_COM_GOOGLE_GMS_UNSTABLE,
        OVERRIDE_COM_GOOGLE_VENDING
    };

    private static final String TAG = "BaikalSpoofer";


    private static final boolean FORCE_AD_ENABLE_SYSTEM = true;
    private static final boolean FORCE_AD_ENABLE_DEFAULT = true;

    public static String DEF_MANUFACTURER;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.manufacturer","Google");
    public static String DEF_MODEL;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.model","Pixel 9");
    public static String DEF_FINGERPRINT;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.fingerprint","google/tokay_beta/tokay:16/BP31.250523.010/13667654:user/release-keys");
    public static String DEF_BRAND;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.brand","google");
    public static String DEF_PRODUCT;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.product","tokay_beta");
    public static String DEF_DEVICE;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.device","tokay");
    public static String DEF_RELEASE;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.release","16");
    public static String DEF_ID;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.id","BP31.250523.010");
    public static String DEF_INCREMENTAL;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.incremental","13667654");
    public static String DEF_SECURITY_PATCH;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.security_patch","2025-07-05");
    public static String DEF_FIRST_API_LEVEL;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.first_api_level","21");
    public static String DEF_SDK_INT;// = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.sdk_int","33");

    public static String MANUFACTURER;// = DEF_MANUFACTURER; // "Google";
    public static String MODEL;// = DEF_MODEL; // "Pixel 9";
    public static String FINGERPRINT;// = DEF_FINGERPRINT; // "google/tokay_beta/tokay:15/BP11.241025.006/12620009:user/release-keys";
    public static String BRAND;// = DEF_BRAND; // "google";
    public static String PRODUCT;// = DEF_PRODUCT; // "tokay_beta";
    public static String DEVICE;// = DEF_DEVICE; // "tokay";
    public static String RELEASE;// = DEF_RELEASE; // "15";
    public static String ID;// = DEF_ID; // "BP11.241025.006";
    public static String INCREMENTAL;// = DEF_INCREMENTAL; // "12620009";
    public static String SECURITY_PATCH;// = DEF_SECURITY_PATCH; // "2024-11-05";
    public static String FIRST_API_LEVEL;// = DEF_FIRST_API_LEVEL; // 32
    public static String SDK_INT;// = DEF_SDK_INT;

    private static OverrideSharedPrefsId sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_NONE;
    private static OverrideSystemPropertiesId sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_NONE;
    private static BaikalSpoofDeviceInfo sOverrideDevice = null;

    static volatile boolean sIsInitialized = false;


    static volatile boolean sIsGms = false;
    static volatile boolean sIsGmsServices = false;
    static volatile boolean sIsGmsUnstable = false;
    static volatile boolean sIsFinsky = false;
    static volatile boolean sIsAA = false;
    static volatile boolean sIsExcluded = false;
    static volatile boolean sPreventHwKeyAttestation = false;
    static volatile boolean sDefaultDialer = false;
    static volatile boolean sDefaultSMS = false;
    static volatile boolean sDefaultCallerID = false;
    static volatile boolean sHideDevMode = false;
    static volatile boolean sAutoRevokeDisabled = false;
    static volatile boolean sEnableGmsSpoof = false;
    static volatile boolean sEnableVendingSpoof = false;
    static volatile boolean sEnableServicesSpoof = false;
    static volatile boolean sDisableCertificateSpoof = false;
    static volatile boolean sDisableCertificateSpoofVending = false;
    static volatile boolean sDisableCertificateSpoofServices = false;
    static volatile boolean sDisableCertificateSpoofApps = false;
    static volatile boolean sDisableSignatureSpoof = false;
    static volatile boolean sDisableGMSSWASpoof = false;
    static volatile boolean sApplicationFilterDisabled = false;
    static volatile boolean sOverrideProps = false;
    static volatile boolean sSpooferSettingsLoaded = false;

    public static boolean sIsDev = SystemProperties.getBoolean("persist.baikal.is_dev", false);


    public static int sGmsUid = -1;
    public static int sFinskyUid = -1;
    public static int sChatGptUid = -1;
    public static boolean isChatGpt = false;
    public static boolean BaikalConstantsBAIKAL_DEBUG_RAW = BaikalConstants.BAIKAL_DEBUG_RAW;

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

    private static int sBaikalSpooferActive = 0;

    private static BaikalAppVolumeDB sAppVolumeDB;

    private static HashMap<String, BaikalAppProfile> sCachedProfiles;

    private static final String signatureData = "MIIFyTCCA7GgAwIBAgIVALyxxl+zDS9SL68SzOr48309eAZyMA0GCSqGSIb3DQEBCwUAMHQxCzAJ\n" +
            "BgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQw\n" +
            "EgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAg\n" +
            "Fw0yMjExMDExODExMzVaGA8yMDUyMTEwMTE4MTEzNVowdDELMAkGA1UEBhMCVVMxEzARBgNVBAgT\n" +
            "CkNhbGlmb3JuaWExFjAUBgNVBAcTDU1vdW50YWluIFZpZXcxFDASBgNVBAoTC0dvb2dsZSBJbmMu\n" +
            "MRAwDgYDVQQLEwdBbmRyb2lkMRAwDgYDVQQDEwdBbmRyb2lkMIICIjANBgkqhkiG9w0BAQEFAAOC\n" +
            "Ag8AMIICCgKCAgEAsqtalIy/nctKlrhd1UVoDffFGnDf9GLi0QQhsVoJkfF16vDDydZJOycG7/kQ\n" +
            "ziRZhFdcoMrIYZzzw0ppBjsSe1AiWMuKXwTBaEtxN99S1xsJiW4/QMI6N6kMunydWRMsbJ6aAxi1\n" +
            "lVq0bxSwr8Sg/8u9HGVivfdG8OpUM+qjuV5gey5xttNLK3BZDrAlco8RkJZryAD40flmJZrWXJmc\n" +
            "r2HhJJUnqG4Z3MSziEgW1u1JnnY3f/BFdgYsA54SgdUGdQP3aqzSjIpGK01/vjrXvifHazSANjvl\n" +
            "0AUE5i6AarMw2biEKB2ySUDp8idC5w12GpqDrhZ/QkW8yBSa87KbkMYXuRA2Gq1fYbQx3YJraw0U\n" +
            "gZ4M3fFKpt6raxxM5j0sWHlULD7dAZMERvNESVrKG3tQ7B39WAD8QLGYc45DFEGOhKv5Fv8510h5\n" +
            "sXK502IvGpI4FDwz2rbtAgJ0j+16db5wCSW5ThvNPhCheyciajc8dU1B5tJzZN/ksBpzne4Xf9gO\n" +
            "LZ9ZU0+3Z5gHVvTS/YpxBFwiFpmL7dvGxew0cXGSsG5UTBlgr7i0SX0WhY4Djjo8IfPwrvvA0QaC\n" +
            "FamdYXKqBsSHgEyXS9zgGIFPt2jWdhaS+sAa//5SXcWro0OdiKPuwEzLgj759ke1sHRnvO735dYn\n" +
            "5whVbzlGyLBh3L0CAwEAAaNQME4wDAYDVR0TBAUwAwEB/zAdBgNVHQ4EFgQUU1eXQ7NoYKjvOQlh\n" +
            "5V8jHQMoxA8wHwYDVR0jBBgwFoAUU1eXQ7NoYKjvOQlh5V8jHQMoxA8wDQYJKoZIhvcNAQELBQAD\n" +
            "ggIBAHFIazRLs3itnZKllPnboSd6sHbzeJURKehx8GJPvIC+xWlwWyFO5+GHmgc3yh/SVd3Xja/k\n" +
            "8Ud59WEYTjyJJWTw0Jygx37rHW7VGn2HDuy/x0D+els+S8HeLD1toPFMepjIXJn7nHLhtmzTPlDW\n" +
            "DrhiaYsls/k5Izf89xYnI4euuOY2+1gsweJqFGfbznqyqy8xLyzoZ6bvBJtgeY+G3i/9Be14HseS\n" +
            "Na4FvI1Oze/l2gUu1IXzN6DGWR/lxEyt+TncJfBGKbjafYrfSh3zsE4N3TU7BeOL5INirOMjre/j\n" +
            "VgB1YQG5qLVaPoz6mdn75AbBBm5a5ahApLiKqzy/hP+1rWgw8Ikb7vbUqov/bnY3IlIU6XcPJTCD\n" +
            "b9aRZQkStvYpQd82XTyxD/T0GgRLnUj5Uv6iZlikFx1KNj0YNS2T3gyvL++J9B0Y6gAkiG0EtNpl\n" +
            "z7Pomsv5pVdmHVdKMjqWw5/6zYzVmu5cXFtR384Ti1qwML1xkD6TC3VIv88rKIEjrkY2c+v1frh9\n" +
            "fRJ2OmzXmML9NgHTjEiJR2Ib2iNrMKxkuTIs9oxKZgrJtJKvdU9qJJKM5PnZuNuHhGs6A/9gt9Oc\n" +
            "cetYeQvVSqeEmQluWfcunQn9C9Vwi2BJIiVJh4IdWZf5/e2PlSSQ9CJjz2bKI17pzdxOmjQfE0JS\n" +
            "F7Xt\n";


    private static final Signature spoofedSignature = new Signature(Base64.decode(signatureData, Base64.DEFAULT));

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


    private static final String[] packagesGoogleServices = {
            "com.google.android.contactkeys",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.ims",
            "com.google.android.safetycore"
    };


    private static final String[] packagesToSpoof = {
            "com.google.android.apps.walletnfcrel",
            "com.google.android.contactkeys",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.ims",
            "com.google.android.safetycore",
            "com.android.vending",
            "com.whatsapp",
            "com.whatsapp.w4b"
    };

    private static final String[] packagesToBlockWithoutDebug = {
            "rikka.safetynetchecker",
            "gr.nikolasspyr.integritycheck",
            "com.henrikherzig.playintegritychecker"
    };


    private static BaikalAppProfile spoofedProfile = null;

    // public SpoofDeviceInfo(String name, String model, String manufacturer, String displayName, String brand, String fp)  JFR

    public static BaikalSpoofDeviceInfo[] Devices = new BaikalSpoofDeviceInfo[] {
        new BaikalSpoofDeviceInfo("karna","M2007J20CI","Xiaomi","Poco X3 India", "xiaomi", "POCO/karna_eea/karna:11/RKQ1.200826.002/V12.0.6.4.RJGEUXM:user/release-keys"), // 1
        new BaikalSpoofDeviceInfo("surya","M2007J20CG","Xiaomi","Poco X3 NFC Global", "xiaomi", "POCO/surya_eea/surya:11/RKQ1.200826.002/V12.0.6.4.RJGEUXM:user/release-keys"), // 2
        new BaikalSpoofDeviceInfo("blueline","Pixel 3","Google","Pixel 3", "google" , "google/blueline/blueline:11/RQ3A.211001.001/7641976:user/release-keys" ), // 3
        new BaikalSpoofDeviceInfo("crosshatch","Pixel 3 XL","Google","Pixel 3 XL", "google", "google/crosshatch/crosshatch:11/RQ3A.211001.001/7641976:user/release-keys"), // 4
        new BaikalSpoofDeviceInfo("flame","Pixel 4","Google","Pixel 4", "google", "google/flame/flame:11/RQ3A.211001.001/7641976:user/release-keys" ), // 5
        new BaikalSpoofDeviceInfo("coral","Pixel 4 XL","Google","Pixel 4 XL", "google", "google/coral/coral:11/RQ3A.211001.001/7641976:user/release-keys" ), // 6
        new BaikalSpoofDeviceInfo("sunfish","Pixel 4a","Google","Pixel 4a", "google", "google/sunfish/sunfish:11/RQ3A.211001.001/7641976:user/release-keys" ), // 7
        new BaikalSpoofDeviceInfo("redfin","Pixel 5","Google","Pixel 5", "google", "google/redfin/redfin:12/SP1A.211105.003/7757856:user/release-keys" ), // 8
        new BaikalSpoofDeviceInfo("mdarcy","SHIELD Android TV","NVIDIA","Nvidia Shield TV 2019 Pro", "NVIDIA", "NVIDIA/mdarcy/mdarcy:9/PPR1.180610.011/4079208_2740.7538:user/release-keys" ), // 9
        new BaikalSpoofDeviceInfo("OnePlus8T","KB2005","OnePlus","OnePlus 8T", "OnePlus", "OnePlus/OnePlus8T/OnePlus8T:11/RP1A.201005.001/2110091917:user/release-keys" ), // 10
        new BaikalSpoofDeviceInfo("OnePlus8Pro","IN2023","OnePlus","OnePlus 8 Pro", "OnePlus", "OnePlus/OnePlus8Pro/OnePlus8Pro:11/RP1A.201005.001/2110091917:user/release-keys"  ), // 11
        new BaikalSpoofDeviceInfo("WW_I005D", "ASUS_I005_1","asus", "Asus ROG Phone 5", "asus", "asus/WW_I005D/ASUS_I005_1:11/RKQ1.201022.002/18.0840.2103.26-0:user/release-keys" ), // 12
        new BaikalSpoofDeviceInfo("XQ-AU52", "XQ-AU52","Sony", "Sony Xperia 10 II Dual", "Sony", "Sony/XQ-AU52_EEA/XQ-AU52:10/59.0.A.6.24/059000A006002402956232951:user/release-keys" ), // 13
        new BaikalSpoofDeviceInfo("XQ-AS72", "XQ-AS72","Sony", "Sony Xperia 2 5G (Asia)", "Sony" , null), // 14
        new BaikalSpoofDeviceInfo("z3s", "SM-G988B","Samsung", "Samsung S21", "samsung", "samsung/z3sxxx/z3s:10/QP1A.190711.020/G988BXXU1ATCT:user/release-keys"), // 15
        new BaikalSpoofDeviceInfo("cmi", "Mi 10 Pro","Xiaomi", "Xiaomi Mi 10 Pro", "xiaomi", "Xiaomi/cmi/cmi:11/RKQ1.200710.002/V12.1.2.0.RJACNXM:user/release-keys"), // 16
        new BaikalSpoofDeviceInfo("raven","Pixel 6 Pro","Google","Pixel 6 Pro", "google", "google/raven/raven:12/SD1A.210817.036/7805805:user/release-keys" ), // 17
        new BaikalSpoofDeviceInfo("dipper", "MI 8","Xiaomi", "Xiaomi MI 8", "xiaomi", "Xiaomi/dipper/dipper:10/QKQ1.190828.002/V11.0.3.0.QEAMIXM:user/release-keys"), // 18
        new BaikalSpoofDeviceInfo("vayu", "M2102J20SG","Xiaomi", "Poco X3 Pro", "xiaomi", "POCO/vayu_global/vayu:11/RKQ1.200826.002/V12.0.4.0.RJUMIXM:user/release-keys"), // 19
        new BaikalSpoofDeviceInfo("agate", "21081111RG","Xiaomi", "Xiaomi Mi 11T", "xiaomi", null), // 20
        new BaikalSpoofDeviceInfo("vayu", "R11 Plus","Oppo", "Oppo R11 Plus", "oppo", null), // 21
        new BaikalSpoofDeviceInfo("marlin","Pixel XL","Google","Pixel XL", "google" , "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys" ), // 22
        new BaikalSpoofDeviceInfo("star", "M2102K1G","Xiaomi", "Xiaomi Mi 11", "xiaomi", null), // 23 
        new BaikalSpoofDeviceInfo("cheetah", "Pixel 7 Pro","Google", "Pixel 7 Pro", "google", "google/cheetah/cheetah:13/TQ2A.230505.002/9891397:user/release-keys"), // 24
        new BaikalSpoofDeviceInfo("PDX-206", "SO-52A","Sony", "Sony Xperia 5", "Sony" , null), // 25
        new BaikalSpoofDeviceInfo("ZS600KL", "ASUS_Z01QD","asus", "Asus ROG 1", "asus" , null), // 26
        new BaikalSpoofDeviceInfo("obiwan", "ASUS_I003D","asus", "Asus ROG 3", "asus" , null), // 27
        new BaikalSpoofDeviceInfo("OnePlus9R","LE2101","OnePlus","OnePlus 9R", "OnePlus", null), // 28
        new BaikalSpoofDeviceInfo("munch","22021211RG","Xiaomi","POCO F4", "POCO", "POCO/munch_global/munch:13/RKQ1.211001.001/V14.0.1.0.TLMMIXM:user/release-keys"), // 29
        new BaikalSpoofDeviceInfo("cezanne","M2006J10C","Xiaomi","Redmi K30 Ultra","xiaomi", null), // 30
        new BaikalSpoofDeviceInfo("tangorpro","Pixel Tablet","Google","Pixel Tablet","google", "google/tangorpro/tangorpro:13/TQ3A.230901.001.B1/10750577:user/release-keys"), // 31
        new BaikalSpoofDeviceInfo("felix","Pixel Fold","Google","Pixel Fold","google", "google/felix/felix:13/TQ3C.230901.001.B1/10750989:user/release-keys"), // 32
        new BaikalSpoofDeviceInfo("husky","Pixel 8 Pro","Google","Pixel 8 Pro","google", "google/husky_beta/husky:15/AP31.240517.022/11948202:user/release-keys"), // 33
        new BaikalSpoofDeviceInfo("qssi_64","SM-S928B","samsung","Galaxy S24 Ultra","samsung", "samsung/e3qxxx/qssi_64:15/AP3A.240905.015.A2/S928BXXU4ZXLJ:user/release-keys"), // 34
    };

    public static void maybeSpoofProperties(Application app, Context context) {
        sBaikalSpooferActive++;
        maybeSpoofDevice(app, context);
    }

    public static int maybeSpoofFeature(String packageName, String name, int version) {

        /*if (PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY.equals(name) || 
            PackageManager.FEATURE_STRONGBOX_KEYSTORE.equals(name) ||
            "android.software.device_id_attestation".equals(name) ) {
            return 0;
        }*/

        if (packageName != null &&
                packageName.contains("com.google.android.apps.as") ) {
            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
            if( name.contains("PIXEL_2022_EXPERIENCE") || 
                name.contains("PIXEL_2022_MIDYEAR_EXPERIENCE") ) {
                return 0;
            }
            return -1;
        }

        if (packageName != null &&
                packageName.contains("com.google.android.apps.photos") ) {

            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
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

    public static void setVersionFieldV(String key, String value) {
        Log.i(TAG, "setVersionField:" + key + ":" + value);
        setVersionField(key,value);
    }

    public static void setVersionField(String key, String value) {
        try {
            if( "-1".equals(value) ) return;
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Version." + key, e);
        }
    }

    public static void setVersionFieldV(String key, int value) {
        Log.i(TAG, "setVersionField:" + key + ":" + value);
        setVersionField(key,value);
    }

    public static void setVersionField(String key, int value) {
        try {
            if( -1 == value ) return;
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Version." + key, e);
        }
    }

    public static void setBuildFieldV(String key, Object value) {
        Log.i(TAG, "setBuildField:" + key + ":" + value);
        setBuildField(key,value);
    }

    public static void setBuildField(String key, Object value) {
        try {

            if( value instanceof String) {
                if( "-1".equals((String)value) ) return;
            }

            if( value instanceof Integer) {
                if( -1 == ((Integer)value)) return;
            }

            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    public static void setProcessFieldV(String key, String value) {
        Log.i(TAG, "setProcessField:" + key + ":" + value);
        setProcessField(key,value);
    }

    public static void setProcessField(String key, String value) {
        try {
            //if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Process." + key + "=" + value);
            if( "-1".equals(value) ) return;
            Field field = Process.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Process." + key, e);
        }
    }

    public static String SystemPropertiesGetDefaultOrEmpty(String key, String def) {
        if( sOverrideProps ) {
            return SystemPropertiesGetNotNullOrEmpty(key,def);
        }
        return def;
    }

    public static String SystemPropertiesGetNotNullOrEmpty(String key, String def) {
        String value = SystemProperties.get(key, "");
        Log.i(TAG, "SystemPropertiesGetNotNullOrEmpty:" + key + ", value=" + value);
        if( value == null || "".equals(value) ) return def;
        return value;
    }

    public static int SystemPropertiesGetDefaultOrEmptyInt(String key, int def) {
        if( sOverrideProps ) {
            return SystemPropertiesGetNotNullOrEmptyInt(key,def);
        }
        return def;
    }

    public static int SystemPropertiesGetNotNullOrEmptyInt(String key, int def) {
        String value = SystemProperties.get(key, "");
        Log.i(TAG, "SystemPropertiesGetNotNullOrEmptyInt:" + key + ", value=" + value);
        if( value == null || "".equals(value) ) return def;
        try {
            return Integer.valueOf(value);
        } catch(Exception e) {
            Log.e(TAG, "Failed to parse int value for " + key, e);
            return def;
        }
    }

    private static void loadDefaultSpoofValues() {
        DEF_MANUFACTURER = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.manufacturer","Google");
        DEF_MODEL = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.model","Pixel 9");
        DEF_FINGERPRINT = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.fingerprint","google/tokay_beta/tokay:16/BP31.250523.010/13667654:user/release-keys");
        DEF_BRAND = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.brand","google");
        DEF_PRODUCT = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.product","tokay_beta");
        DEF_DEVICE = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.device","tokay");
        DEF_RELEASE = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.release","16");
        DEF_ID = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.id","BP31.250523.010");
        DEF_INCREMENTAL = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.incremental","13667654");
        DEF_SECURITY_PATCH = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.security_patch","2025-07-05");
        DEF_FIRST_API_LEVEL = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.first_api_level","32");
        DEF_SDK_INT = SystemPropertiesGetNotNullOrEmpty("persist.spf.def.sdk_int","33");

        MANUFACTURER = DEF_MANUFACTURER; // "Google";
        MODEL = DEF_MODEL; // "Pixel 9";
        FINGERPRINT = DEF_FINGERPRINT; // "google/tokay_beta/tokay:15/BP11.241025.006/12620009:user/release-keys";
        BRAND = DEF_BRAND; // "google";
        PRODUCT = DEF_PRODUCT; // "tokay_beta";
        DEVICE = DEF_DEVICE; // "tokay";
        RELEASE = DEF_RELEASE; // "15";
        ID = DEF_ID; // "BP11.241025.006";
        INCREMENTAL = DEF_INCREMENTAL; // "12620009";
        SECURITY_PATCH = DEF_SECURITY_PATCH; // "2024-11-05";
        FIRST_API_LEVEL = DEF_FIRST_API_LEVEL; // 32
        SDK_INT = DEF_SDK_INT;

    }

    private static void spoofBuild() {

        int uid = myUid();

        loadDefaultSpoofValues();

        String prefix = "persist.spf";

        String value = SystemProperties.get("persist.baikal.spfdbg." + String.valueOf(uid), "");
        if( value != null && !"".equals(value) ) {
            prefix = value;
        } else {
            value = SystemProperties.get("persist.baikal.spfdbg", "");
            if( value != null && !"".equals(value) ) {
                prefix = value;
            }
        }

        MANUFACTURER = SystemPropertiesGetDefaultOrEmpty(prefix + ".manufacturer", DEF_MANUFACTURER);
        MODEL = SystemPropertiesGetDefaultOrEmpty(prefix + ".model", DEF_MODEL);
        FINGERPRINT = SystemPropertiesGetDefaultOrEmpty(prefix + ".fingerprint", DEF_FINGERPRINT);
        BRAND = SystemPropertiesGetDefaultOrEmpty(prefix + ".brand", DEF_BRAND);
        PRODUCT = SystemPropertiesGetDefaultOrEmpty(prefix + ".product", DEF_PRODUCT);
        DEVICE = SystemPropertiesGetDefaultOrEmpty(prefix + ".device", DEF_DEVICE);
        RELEASE = SystemPropertiesGetDefaultOrEmpty(prefix + ".release", DEF_RELEASE);
        ID = SystemPropertiesGetDefaultOrEmpty(prefix + ".id", DEF_ID);
        INCREMENTAL = SystemPropertiesGetDefaultOrEmpty(prefix + ".incremental", DEF_INCREMENTAL);
        SECURITY_PATCH = SystemPropertiesGetDefaultOrEmpty(prefix + ".security_patch", DEF_SECURITY_PATCH);
        FIRST_API_LEVEL = SystemPropertiesGetDefaultOrEmpty(prefix + ".first_api_level", DEF_FIRST_API_LEVEL);
        SDK_INT = SystemPropertiesGetDefaultOrEmpty(prefix + ".sdk_int", DEF_SDK_INT);
        
        value = SystemProperties.get("persist.baikal.ovrsdk." + String.valueOf(uid), "");
        if( value != null && !"".equals(value) ) {
            SDK_INT = value;
        } 

        setBuildFieldV("MANUFACTURER", MANUFACTURER);
        setBuildFieldV("MODEL", MODEL);
        setBuildFieldV("FINGERPRINT", FINGERPRINT);
        setBuildFieldV("BRAND", BRAND);
        setBuildFieldV("PRODUCT", PRODUCT);
        setBuildFieldV("DEVICE", DEVICE);
        setBuildFieldV("ID", ID);
        setVersionFieldV("INCREMENTAL", INCREMENTAL);
        setVersionFieldV("RELEASE", RELEASE );
        setVersionFieldV("SECURITY_PATCH", SECURITY_PATCH);
        setVersionFieldV("DEVICE_INITIAL_SDK_INT", Integer.parseInt(FIRST_API_LEVEL));
        setVersionFieldV("SDK", SDK_INT);
        setVersionFieldV("SDK_INT", Integer.parseInt(SDK_INT));
    }


    private static void maybeSpoofBuild(String packageName, String processName, Context context) {

        Log.e(TAG, "Spoof Device check: " + packageName + "/" + processName);

        //boolean needsWASpoof = List.of("pixelmigrate", "restore", "snapchat", "instrumentation").stream().anyMatch(packageName::contains);

        sGmsUid = getAppUid("com.google.android.gms");
        sFinskyUid = getAppUid("com.android.vending");
        sChatGptUid = getAppUid("com.openai.chatgpt");

        if ("com.google.android.gms".equals(packageName) ) {
            //setBuildField("TIME", System.currentTimeMillis());
            sIsGms = true;
            if( processName != null ) {
                sIsGmsUnstable = List.of("unstable", "instrumentation").stream().anyMatch(processName.toLowerCase()::contains);
            } 
            sIsGmsServices = !sIsGmsUnstable;
        }

        if( "com.google.android.projection.gearhead".equals(packageName) ) {
            //mAaUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.google.android.projection.gearhead"));
            sIsAA = true;
        }

        if ( !sIsGms && Arrays.asList(packagesGoogleServices).contains(packageName)) {
            sIsGmsServices = true;
        }

        if( sIsGms /*Unstable */ ) {

            if( !sEnableGmsSpoof ) {
                Log.e(TAG, "Spoof Device for GMS SN disabled: " + Application.getProcessName());
                return;
            }

            sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_COM_GOOGLE_GMS_UNSTABLE;
            Log.e(TAG, "Spoof Device for GMS SN check: " + Application.getProcessName());
            spoofBuild();
            return;
        } else /*if( sIsGmsServices ) {

            if( !sEnableServicesSpoof ) {
                Log.e(TAG, "Spoof Device for Google Services SN disabled: " + Application.getProcessName());
                //sIsExcluded = true;
                return;
            }

            sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_COM_GOOGLE_GMS_UNSTABLE;
            Log.e(TAG, "Spoof Device for Google Services SN check: " + Application.getProcessName());
            spoofBuild();
            return;
        } else*/ if( "com.android.vending".equals(packageName) ) {
            if( !sEnableVendingSpoof ) {
                Log.e(TAG, "Spoof Device for VENDING SN disabled: " + Application.getProcessName());
                //sIsExcluded = true;
                sIsFinsky = true;
                return;
            }

            //setVersionField("SECURITY_PATCH", SECURITY_PATCH);
            //setVersionField("SDK_INT", 32);
            //setVersionField("RELEASE", "12");
            sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_COM_GOOGLE_VENDING;
            Log.e(TAG, "Spoof Device for VENDING SN check: " + Application.getProcessName());
            spoofBuild();
            sIsFinsky = true;
            return;
        } else if( "com.openai.chatgpt".equals(packageName) ) {
            // spoofBuild();
            // BaikalConstantsBAIKAL_DEBUG_RAW = true;
            isChatGpt = true;
            // return;
        }




        /*if (Arrays.asList(packagesToSpoof).contains(packageName)) {
            if( sEnableVendingSpoof ) {
                sOverrideSystemPropertiesId = OverrideSystemPropertiesId.OVERRIDE_COM_GOOGLE_VENDING;
                Log.e(TAG, "Spoof Device for ListedApp SN check: " + Application.getProcessName());
                spoofBuild();
                sIsFinsky = true;
            }
            return;
        }*/

        /*if (Arrays.asList(packagesToKeep).contains(packageName) ||
                packageName.startsWith("com.google.android.GoogleCamera")) {
            sIsExcluded = true;
            return;
        }*/

        /*if( sIsFinsky && !sEnableGmsSpoof ) {
            Log.e(TAG, "Spoof Device for GMS SN disabled: " + Application.getProcessName());
            sIsExcluded = true;
            return;
        }*/
        
        Log.e(TAG, "Spoof Device check completed: " + packageName + "/" + processName);
    }

    private static void loadSpooferSettings() {
        if( sSpooferSettingsLoaded || sContext == null ) return;
        try {
            sEnableGmsSpoof = Settings.Global.getInt(sContext.getContentResolver(),
                Settings.Global.BAIKALOS_ENABLE_GMS_SPOOF,0) != 0;

            sEnableVendingSpoof = Settings.Global.getInt(sContext.getContentResolver(),
                Settings.Global.BAIKALOS_ENABLE_VENDING_SPOOF,0) != 0;

            sEnableServicesSpoof = Settings.Global.getInt(sContext.getContentResolver(),
                Settings.Global.BAIKALOS_ENABLE_SERVICES_SPOOF,0) != 0;

            sOverrideProps = Settings.Global.getInt(sContext.getContentResolver(),
                    Settings.Global.BAIKALOS_GMS_OVERRIDE_PROPS,0) != 0;

            
            sDisableGMSSWASpoof = true; //Settings.Global.getInt(sContext.getContentResolver(),
                    //Settings.Global.BAIKALOS_DISABLE_GMS_SWA_SPOOF,0) != 0;


            boolean isCertificateSpooferAvailable = sContext.getResources().
                getBoolean(com.android.internal.R.bool.config_certificateSpfrAvailable);
                
            if( isCertificateSpooferAvailable ) {
                sDisableCertificateSpoof = Settings.Global.getInt(sContext.getContentResolver(),
                    Settings.Global.BAIKALOS_ENABLE_CERTIFICATE_SPOOF,0) == 0;
                sDisableCertificateSpoofVending = Settings.Global.getInt(sContext.getContentResolver(),
                    Settings.Global.BAIKALOS_ENABLE_CERTIFICATE_SPOOF_VENDING,0) == 0;
                sDisableCertificateSpoofApps = Settings.Global.getInt(sContext.getContentResolver(),
                    Settings.Global.BAIKALOS_ENABLE_CERTIFICATE_SPOOF_APPS,0) == 0;
                sDisableCertificateSpoofServices = Settings.Global.getInt(sContext.getContentResolver(),
                    Settings.Global.BAIKALOS_ENABLE_CERTIFICATE_SPOOF_SERVICES,0) == 0;
            } else {
                sDisableCertificateSpoof = true;
                sDisableCertificateSpoofApps = true;
                sDisableCertificateSpoofVending = true;
                sDisableCertificateSpoofServices = true;
            }

            boolean isSignatureSpooferAvailable = sContext.getResources().
                getBoolean(com.android.internal.R.bool.config_signatureSpfrAvailable);

            if( isSignatureSpooferAvailable ) {
                sDisableSignatureSpoof = Settings.Global.getInt(sContext.getContentResolver(),
                    Settings.Global.BAIKALOS_ENABLE_SIGNATURE_SPOOF,0) == 0;
            } else {
                sDisableSignatureSpoof = true;
            }

            sSpooferSettingsLoaded = true;

        } catch(Exception er) {
            Log.e(TAG, "Failed to load settings for " + sPackageName + "/" + sProcessName, er);
        };
        
    }


    private static Handler mBackgroundHandler;
    private static HandlerThread mHandlerThread;

    private static void maybeSpoofDevice(Application app, Context context) {

        sApplicationFilterDisabled = true;

        String packageName = app.getPackageName();
        String processName = app.getProcessName();

        if( myUid() != 1000 ) {

            mHandlerThread = new HandlerThread("DebugBackgroundThread");
            mHandlerThread.start();

            mBackgroundHandler = new Handler(mHandlerThread.getLooper());
            BaikalDebugInternal.getInstance(mBackgroundHandler,context).updateConstants();
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
                android.baikalos.BaikalAppProfile.setCurrentAppProfile(new BaikalAppProfile("unknown", myUid()), "invalid", myUid());
            }
            //return;
        }

        if( packageName == null || "".equals(packageName)) {
            if( myUid() == 1000 ) {
                packageName = "android";
            } else {
                Log.e(TAG, "Empty package name", new Throwable());
                packageName = "unknown";
            }
        }


        int device_id = -1;
        BaikalAppProfile profile = null;

        if( myUid() == 1000 ) {

            Log.e(TAG, "Delay android settings loader until settings provider available");

            if( packageName == null ) packageName = "android";
            profile = new BaikalAppProfile(packageName, myUid());
            profile.getBackgroundMode(false);
            sCachedProfiles = new HashMap<String, BaikalAppProfile>();
                //sCachedProfileUids = new HashMap<Integer, AppProfile>();
            profile.mAllowWhileIdle = true;
            profile.mSystemWhitelisted = true;
            profile.mBackgroundMode = -1;
            profile.mStaminaEnforced = true;
            profile.mStamina = true;
            sAutoRevokeDisabled = true;

            android.baikalos.BaikalAppProfile.setCurrentAppProfile(profile, packageName, myUid());

            sIsInitialized = true;

            if( sProcessName == null )
                sProcessName = processName;
            if( sPackageName == null )
                sPackageName = packageName;

        } else {

            loadSpooferSettings();

            try {
                sAppVolumeDB = BaikalAppVolumeDB.getInstance(context);
                sAppVolumeDB.applyAppVolume(packageName);
            } catch(Exception er) {
                Log.e(TAG, "Failed to load BaikalAppVolumeDB for:" + packageName, er);
            };
        

            if( sProcessName == null )
                sProcessName = processName;
            if( sPackageName == null )
                sPackageName = packageName;

            maybeSpoofBuild(packageName, processName,  context);

            setOverrideSharedPrefs(packageName);

            try {
                Log.i(TAG, "Loading settings for :" + packageName);

                sDefaultBackgroundBlurRadius = -1; /*Settings.System.getInt(context.getContentResolver(),
                    Settings.System.BAIKALOS_BACKGROUND_BLUR_RADIUS, -1);*/

                sDefaultBlurModeInt = -1; /*Settings.System.getInt(context.getContentResolver(),
                    Settings.System.BAIKALOS_BACKGROUND_BLUR_TYPE, -1);*/
            
                try {
                    sAutoRevokeDisabled = Settings.Global.getInt(context.getContentResolver(),
                            Settings.Global.BAIKALOS_DISABLE_AUTOREVOKE,0) == 1;
                } catch(Exception er) {
                    Log.e(TAG, "Failed to read auto revoke status for:" + packageName, er);
                };

                try {
                    sCachedProfiles = BaikalAppProfileSettings.loadCachedProfiles(context);
                    //sCachedProfileUids = AppProfileSettings.updateProfileUids(sCachedProfiles, context);
                } catch(Exception el) {
                    Log.e(TAG, "Failed to load app info for:" + packageName + ":" + el.getMessage());
                    sCachedProfiles = new HashMap<String, BaikalAppProfile>();
                    //sCachedProfileUids = new HashMap<Integer, AppProfile>();
                }

                if( sCachedProfiles.containsKey(packageName) ) {
                    profile = sCachedProfiles.get(packageName);
                } else {
                    profile = new BaikalAppProfile(packageName, myUid());
                }
                profile.getBackgroundMode(false);
                
                // profile = AppProfileSettings.loadSingleProfile(packageName, myUid(), context);
                if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Loaded profile :" + profile.toString());
            

                android.baikalos.BaikalAppProfile.setCurrentAppProfile(profile, packageName, myUid());

          
                device_id = profile.mSpoofDevice - 1;

                if( profile.mPreventHwKeyAttestation ) {
                    sPreventHwKeyAttestation = true;
                    if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding hardware attestation for :" + packageName + " to " + profile.mPreventHwKeyAttestation);
                } 
                if( profile.mHideDevMode ) {
                    sHideDevMode = true;
                    if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding developer mode for :" + packageName + " to " + profile.mHideDevMode);
                } 

                if( profile.mSpoofAsDefaultDialer ) {
                    sDefaultDialer = true;
                    if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding default dialer for :" + packageName + " to " + profile.mSpoofAsDefaultDialer);
                }

                if( profile.mSpoofAsDefaultSMS ) {
                    sDefaultSMS = true;
                    if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding default SMS for :" + packageName + " to " + profile.mSpoofAsDefaultSMS);
                }

                if( profile.mSpoofAsDefaultCallerID ) {
                    sDefaultCallerID = true;
                    if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Overriding default callerid for :" + packageName + " to " + profile.mSpoofAsDefaultCallerID);
                }

                setBuildField("TYPE", "user");
                setBuildField("TAGS", "release-keys");
    

            } catch(Exception fl) {
                Log.e(TAG, "Failed to load profile for :" + packageName + ", sBaikalSpooferActive=" + sBaikalSpooferActive, fl);
            }
        }

        sApplicationFilterDisabled = false;
        sIsInitialized = true;

        Log.i(TAG, "Loading completed for :" + packageName);

        if (Arrays.asList(packagesToBlockWithoutDebug).contains(packageName)) {
            Log.e(TAG, "Package disabled by BaikalOS: " + Application.getProcessName());
            if( profile == null || !profile.mDebug ) {
                Log.e(TAG, "Execution disabled by BaikalOS: " + Application.getProcessName());
                throw new SecurityException("Execution disabled by BaikalOS");
            }
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

            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device Profile :" + packageName + ", device_id=" + device_id);

            BaikalSpoofDeviceInfo device = BaikalSpoofer.Devices[device_id];

            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device BRAND: " + device.deviceBrand);
            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device MANUFACTURER: " + device.deviceManufacturer);
            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device MODEL: " + device.deviceModel);
            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device DEVICE: " + device.deviceName);
            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device PRODUCT: " + device.deviceName);
            if( BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG, "Spoof Device FINGERPRINT: " + device.deviceFp);

            if( device.deviceBrand != null &&  !"".equals(device.deviceBrand) ) setBuildField("BRAND", device.deviceBrand);
            if( device.deviceManufacturer != null &&  !"".equals(device.deviceManufacturer) ) setBuildField("MANUFACTURER", device.deviceManufacturer);
            if( device.deviceModel != null &&  !"".equals(device.deviceModel) ) setBuildField("MODEL", device.deviceModel);
            if( device.deviceName != null &&  !"".equals(device.deviceName) ) setBuildField("DEVICE", device.deviceName);
            if( device.deviceName != null &&  !"".equals(device.deviceName) ) setBuildField("PRODUCT", device.deviceName);
            if( device.deviceFp != null && !"".equals(device.deviceFp) ) setBuildField("FINGERPRINT", device.deviceFp);

            sOverrideDevice = device;

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

    public static boolean isSpoofDefaultDialer() {
        return sDefaultDialer;
    }

    public static boolean isSpoofDefaultSMS() {
        return sDefaultSMS;
    }

    public static boolean isSpoofDefaultCallerID() {
        return sDefaultCallerID;
    }

    public static boolean isCurrentProcessGmsUnstable() {
        return sIsGmsUnstable;
    }

    public static boolean isCurrentProcessGms() {
        return sIsGms;
    }

    public static boolean isCurrentProcessAA() {
        return sIsAA;
    }

    public static String getPackageName() {
        return sPackageName;
    }

    public static String getProcessName() {
        return sProcessName;
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().toLowerCase().contains("droidguard"));
    }

    public static void onEngineGetCertificateChain() {

        loadSpooferSettings();

        if(sPreventHwKeyAttestation) {
            Log.i(TAG, "HW Attestation disabled for " + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid());
            throw new UnsupportedOperationException();
        } 

        /*if (sIsExcluded) return;

        if( !sDisableGMSSWASpoof ) {

            Log.i(TAG, "Certificate spoofing disabled for " + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid());

            if (isCallerSafetyNet()) {
                throw new UnsupportedOperationException();
            }
            if( sIsFinsky ) {
                throw new UnsupportedOperationException();
            }
        }*/
    }


    private static void setOverrideSharedPrefs(String packageName) {
        sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_NONE;
        if( "com.android.camera".equals(packageName) ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_ANDROID_CAMERA;
        if( sEnableGmsSpoof && "com.google.android.gms".equals(packageName) ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_GOOGLE_GMS;
        if( sEnableVendingSpoof && "com.android.vending".equals(packageName) ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_GOOGLE_VENDING;
        if( sEnableServicesSpoof && sIsGmsServices ) sOverrideSharedPrefsId = OverrideSharedPrefsId.OVERRIDE_COM_GOOGLE_GMS;

    }

    public static String overrideStringSharedPreference(String key, String value) {
        String result = value;

        switch(sOverrideSharedPrefsId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS:
            case OVERRIDE_COM_GOOGLE_VENDING:
                if( "last_build_fingerprint".equals(key) ) result = FINGERPRINT;
                if( "dailyhygiene-last-android-version".equals(key) ) result = FINGERPRINT;
                break;
        }

        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "package=" + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid() + ": getString " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Set<String> overrideSetStringSharedPreference(String key, Set<String> value) {
        Set<String> result = value;
        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "package=" + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid() + ": getSet<String> " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Integer overrideIntegerSharedPreference(String key, Integer value) {
        Integer result = value;
        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "package=" + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid() + ": getInteger " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Long overrideLongSharedPreference(String key, Long value) {
        Long result = value;
        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "package=" + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid() + ": getLong " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static Float overrideFloatSharedPreference(String key, Float value) {
        Float result = value;
        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "package=" + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid() + ": getFloat " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static boolean overrideBooleanSharedPreference(String key, boolean value) {

        boolean result = value;

        //if( "prefer_attest_key".equals(key) ) result = false;

        switch(sOverrideSharedPrefsId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_ANDROID_CAMERA:
                if( key.equals("pref_camera_first_use_hint_shown_key") ) result = false;
                break;
            case OVERRIDE_COM_GOOGLE_GMS:
            case OVERRIDE_COM_GOOGLE_VENDING:
                //if( key.equals("enabled") ) result = true;
                break;
        }

        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "package=" + BaikalAppProfile.packageName() +  "/" + BaikalAppProfile.uid() + ": getBoolean " + key + " -> "  + value + " -> " + result);
        return result;
    }

    public static String overrideSetSystemProperty(@NonNull String key, @Nullable String val) {
        //if( BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryset " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property " + key + " val " + val);
        return null;
    }

    public static String overrideStringSystemProperty(@NonNull String key, @Nullable String rval) {
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget string " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' rval \'" + rval + "\'");
        if( !sIsInitialized ) return rval == null ? "" : rval;
        if( getFilteredDevModeKey(key) ) return "";

        String simoverride;
        if( (simoverride = overrideSimProperties(key)) != null ) return simoverride;

        rval = overrideDeviceProperty(key,"",rval);

        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
            case OVERRIDE_COM_GOOGLE_VENDING:
                rval = overrideGmsUnstableString(key,rval);
        }
        
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget string " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' result \'" + rval + "\'");
        return rval == null ? "" : rval;
    }

    public static String overrideStringSystemProperty(@NonNull String key, @Nullable String def, @Nullable String rval) {
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget string " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' rval " + rval);
        if( !sIsInitialized ) return rval == null ? "" : rval;
        if( getFilteredDevModeKey(key) ) return def == null ? "" : def;

        String simoverride;
        if( (simoverride = overrideSimProperties(key)) != null ) return simoverride;

        rval = overrideDeviceProperty(key,def,rval);
        
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
            case OVERRIDE_COM_GOOGLE_VENDING:
                rval = overrideGmsUnstableString(key,rval);
        }
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget string " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' result \'" + rval + "\'");
        return rval == null ? "" : rval;
    }

    public static int overrideIntSystemProperty(@NonNull String key, int def, int rval) {
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget int " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' rval " + rval);
        if( !sIsInitialized ) return rval;
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
            case OVERRIDE_COM_GOOGLE_VENDING:
                rval = overrideGmsUnstableInt(key,rval);
        }
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget int " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' result \'" + rval + "\'");
        return rval;
    }

    public static long overrideLongSystemProperty(@NonNull String key, long def, long rval) {
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget long " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' rval " + rval);
        if( !sIsInitialized ) return rval;
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
            case OVERRIDE_COM_GOOGLE_VENDING:
                rval = overrideGmsUnstableLong(key,rval);
        }
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget long " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' result \'" + rval + "\'");
        return rval;
    }

    public static Boolean overrideBooleanSystemProperty(@NonNull String key, Boolean def, Boolean rval) {
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget bool " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' rval " + rval);
        if( !sIsInitialized ) return rval;
        if( getFilteredDevModeKey(key) ) return def;
        switch(sOverrideSystemPropertiesId) {
            case OVERRIDE_NONE:
                break;
            case OVERRIDE_COM_GOOGLE_GMS_UNSTABLE:
            case OVERRIDE_COM_GOOGLE_VENDING:
                rval = overrideGmsUnstableBoolean(key,rval);
        }
        if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget bool " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\' def \'" + def + "\' result \'" + rval + "\'");
        return rval;
    }

    private static String overrideDeviceProperty(@NonNull String key, @Nullable String def, @Nullable String rval) {
        if(sOverrideDevice != null) {
            if("ro.product.name".equals(key)) {
                return sOverrideDevice.deviceName;
            } else if("ro.product.device".equals(key)) {
                return sOverrideDevice.deviceName;
            } else if("ro.product.brand".equals(key)) {
                return sOverrideDevice.deviceBrand;
            } else if("ro.product.manufacturer".equals(key)) {
                return sOverrideDevice.deviceManufacturer;
            } else if("ro.product.model".equals(key)) {
                return sOverrideDevice.deviceModel;
            } else if("ro.product.system.name".equals(key)) {
                return sOverrideDevice.deviceName;
            } else if("ro.product.system.device".equals(key)) {
                return sOverrideDevice.deviceName;
            } else if("ro.product.system.brand".equals(key)) {
                return sOverrideDevice.deviceBrand;
            } else if("ro.product.system.manufacturer".equals(key)) {
                return sOverrideDevice.deviceManufacturer;
            } else if("ro.product.system.model".equals(key)) {
                return sOverrideDevice.deviceModel;
            }
        }
        return rval;
    }


    private static String overrideOneSimProperty(String property, String key, String rkey) {
        if(property == null || "".equals(property)) return null;
        if( rkey == null ) return null;
        if( rkey.equals("gsm." + key) || rkey.equals("gsm.sim." + key) ) {
            /*if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() )*/ 
            Log.d(TAG, "Tryget override SIM prop " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\'" + " value = \'" + property + "\'");
            return property;
        }
        return null;
    }


    private static String overrideSimProperties(String key) {
        String result;
        if( (result = overrideOneSimProperty(BaikalAppProfile.getCurrentAppProfile().mSpoofSimCountry, "operator.iso-country", key)) != null ) return result;
        if( (result = overrideOneSimProperty(BaikalAppProfile.getCurrentAppProfile().mSpoofSimMnc, "operator.numeric", key)) != null ) return result;
        if( (result = overrideOneSimProperty(BaikalAppProfile.getCurrentAppProfile().mSpoofSimOpName, "operator.alpha", key)) != null ) return result;
        //if( result = overrideOneSimProperty(BaikalAppProfile.getCurrentAppProfile().mSpoofSimLN, "operator.iso-country", key) != null ) return result;
        return null;
    }


    private static boolean getFilteredDevModeKey(String key) {
        if( /*BaikalAppProfile.getCurrentAppProfile().mHideDevMode && */ key != null ) {
           if(  "init.svc.adbd".equals(key) ||
                "init.svc.adb_root".equals(key) ||
                "init.svc.magiskd".equals(key) ||
                "init.svc_debug_pid.adb_root".equals(key) ||
                "init.svc_debug_pid.adbd".equals(key) ||
                "init.svc_debug_pid.magiskd".equals(key) ||
                "sys.usb.state".equals(key) ||
                "sys.usb.config".equals(key) || 
                "sys.usb.adb.disabled".equals(key) ||
                "sys.oem_unlock_allowed".equals(key) ||
                "ro.boottime.adb_root".equals(key) ||
                "ro.boottime.adbd".equals(key) ||
                "vendor.sys.usb.adb.disabled".equals(key) ||
                "persist.sys.usb.config".equals(key) ||
                "persist.adb.wifi.guid".equals(key) ||
                "persist.adb.tls_server.port".equals(key) ||
                "persist.adb.tls_server.enable".equals(key) ||
                /*"gsm.sim.operator.numeric".equals(key) ||
                "gsm.operator.numeric".equals(key) ||
                "gsm.sim.operator.alpha".equals(key) ||
                "gsm.operator.alpha".equals(key) ||
                "gsm.sim.operator.iso-country".equals(key) ||
                "gsm.operator.iso-country".equals(key) ||*/
                key.startsWith("persist.spoof") ) { 

                if( BaikalAppProfile.getCurrentAppProfile().mHideDevMode ) {
                    Log.d(TAG, "Tryget filtered dev mode " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\'");
                    return true;
                } 
                if( BaikalConstantsBAIKAL_DEBUG_RAW || BaikalAppProfile.isDebug() ) Log.d(TAG, "Tryget not filtered dev mode " + BaikalAppProfile.packageName() + "/" + BaikalAppProfile.uid() + " system property \'" + key + "\'");
            }
        }
        return false;
    }

    private static String overrideGmsUnstableString(String key, String def) {
        if( key != null ) {
            if( key.endsWith("product.device") ) return DEVICE;
            if( key.endsWith("product.manufacturer") ) return MANUFACTURER;
            if( key.endsWith("product.model") ) return MODEL;
            if( key.endsWith("product.brand") ) return BRAND;
            if( key.endsWith("product.name") ) return PRODUCT;

            if( key.endsWith("system.device") ) return DEVICE;
            if( key.endsWith("system.manufacturer") ) return MANUFACTURER;
            if( key.endsWith("system.model") ) return MODEL;
            if( key.endsWith("system.brand") ) return BRAND;
            if( key.endsWith("system.name") ) return PRODUCT;

            if( key.endsWith("system_ext.device") ) return DEVICE;
            if( key.endsWith("system_ext.manufacturer") ) return MANUFACTURER;
            if( key.endsWith("system_ext.model") ) return MODEL;
            if( key.endsWith("system_ext.brand") ) return BRAND;
            if( key.endsWith("system_ext.name") ) return PRODUCT;

            if( key.endsWith("bootimage.device") ) return DEVICE;
            if( key.endsWith("bootimage.manufacturer") ) return MANUFACTURER;
            if( key.endsWith("bootimage.model") ) return MODEL;
            if( key.endsWith("bootimage.brand") ) return BRAND;
            if( key.endsWith("bootimage.name") ) return PRODUCT;

            if( key.endsWith("vendor.device") ) return DEVICE;
            if( key.endsWith("vendor.manufacturer") ) return MANUFACTURER;
            if( key.endsWith("vendor.model") ) return MODEL;
            if( key.endsWith("vendor.brand") ) return BRAND;
            if( key.endsWith("vendor.name") ) return PRODUCT;

            if( key.endsWith("vendor_dlkm.device") ) return DEVICE;
            if( key.endsWith("vendor_dlkm.manufacturer") ) return MANUFACTURER;
            if( key.endsWith("vendor_dlkm.model") ) return MODEL;
            if( key.endsWith("vendor_dlkm.brand") ) return BRAND;
            if( key.endsWith("vendor_dlkm.name") ) return PRODUCT;

            if( key.endsWith("build.fingerprint") ) return FINGERPRINT;
            if( key.endsWith("api_level") ) return FIRST_API_LEVEL;
            if( key.endsWith(".security_patch") ) return SECURITY_PATCH;
            if( key.endsWith(".build.id") ) return ID;
            if( key.endsWith(".version.sdk") ) return SDK_INT;
        }
        return def == null ? "" : def;
    }

    private static int overrideGmsUnstableInt(String key, int def) {
        if( key != null ) {
            if( key.endsWith("api_level") ) return Integer.parseInt(FIRST_API_LEVEL);
            if( key.endsWith(".version.sdk") ) return Integer.parseInt(SDK_INT);
        }
        return def;
    }

    private static long overrideGmsUnstableLong(String key, long def) {
        if( key != null ) {
            if( key.endsWith("api_level") ) return Long.parseLong(FIRST_API_LEVEL);
            if( key.endsWith(".version.sdk") ) return Long.parseLong(SDK_INT);
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

        if( BaikalAppProfile.isDebug() ) Log.i(TAG, "overrideCameraId: " + cameraId + " -> " + id);
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
        if( BaikalAppProfile.getCurrentAppProfile().mSonification == 1 ) {
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
            if( BaikalAppProfile.getCurrentAppProfile().mSonification >= 1 ) {

                int audio_usage_1 = 0; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.audio_usage_1",13);
                int audio_usage_2 = 4; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.audio_usage_2",4);

                usage = BaikalAppProfile.getCurrentAppProfile().mSonification == 2 ? 
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
            if( BaikalAppProfile.getCurrentAppProfile().mSonification >= 1 ) {

                int content_type_1 = 0; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.content_type_1",4);
                int content_type_2 = 0; // SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.content_type_2",0);

                contentType = BaikalAppProfile.getCurrentAppProfile().mSonification == 2 ? 
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
            if( BaikalAppProfile.getCurrentAppProfile().mSonification >= 1 ) {

                int stream_type_1 = SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.stream_type_1",7);
                int stream_type_2 = SystemPropertiesGetNotNullOrEmptyInt("persist.baikal.stream_type_2",7);

                streamType = BaikalAppProfile.getCurrentAppProfile().mSonification == 2 ? 
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
        if( BaikalAppProfile.getCurrentAppProfile().mSonification != 0 ) {
            if( !(FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return originalDeviceInfo;
            if( sBuiltinPlaybackDevice == null || sBuiltinRecordingDevice == null ) setBuiltinDevices();
            if( !record ) {
                if( BaikalAppProfile.isDebug() ) Log.i(TAG,"overridePrefferedDevice playback :" + originalDeviceInfo + "->" + sBuiltinPlaybackDevice, new Throwable());
                return sBuiltinPlaybackDevice;
            } else {
                if( BaikalAppProfile.isDebug() ) Log.i(TAG,"overridePrefferedDevice record :" + originalDeviceInfo + "->" + sBuiltinRecordingDevice, new Throwable());
                return sBuiltinRecordingDevice;
            }
        }
        return originalDeviceInfo;
    }

    public static AudioDeviceInfo updatePreferredDevice(AudioRouting self, AudioDeviceInfo originalDeviceInfo, boolean record) {
        if( BaikalAppProfile.getCurrentAppProfile().mSonification != 0 ) {
            if( !(FORCE_AD_ENABLE_SYSTEM || SystemProperties.getBoolean("persist.baikal.force_ad_enable",FORCE_AD_ENABLE_DEFAULT)) ) return originalDeviceInfo;
            if( sBuiltinPlaybackDevice == null || sBuiltinRecordingDevice == null ) setBuiltinDevices();
            if( !record ) {
                if( sBuiltinPlaybackDevice != null && (originalDeviceInfo == null || originalDeviceInfo.getId() != sBuiltinPlaybackDevice.getId()) ) self.setPreferredDevice(sBuiltinPlaybackDevice);
                if( BaikalAppProfile.isDebug() ) Log.i(TAG,"updatePreferredDevice playback:" + originalDeviceInfo + "->" + sBuiltinPlaybackDevice, new Throwable());
                return sBuiltinPlaybackDevice;
            } else {
                if( sBuiltinRecordingDevice != null && (originalDeviceInfo == null || originalDeviceInfo.getId() != sBuiltinRecordingDevice.getId()) ) self.setPreferredDevice(sBuiltinRecordingDevice);
                if( BaikalAppProfile.isDebug() ) Log.i(TAG,"updatePreferredDevice record:" + originalDeviceInfo + "->" + sBuiltinRecordingDevice, new Throwable());
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

        if( sAudioManager == null && (BaikalAppProfile.isDebug() || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE) ) Log.i(TAG,"overridePrefferedDevice sAudioManager = null", new Throwable());

        if( sAudioManager != null && sBuiltinPlaybackDevice == null ) {

            AudioDeviceInfo speakerDevice = null;
            AudioDeviceInfo[] deviceList = sAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : deviceList) {
                if( BaikalAppProfile.isDebug() ) Log.i(TAG,"overridePrefferedDevice device:" + device.getType());
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

    public static boolean disableSignatureSpoof() {
        loadSpooferSettings();
        return sDisableSignatureSpoof;
    }

    public static boolean disableCertificateSpoof() {
        loadSpooferSettings();

        int callingUid = Binder.getCallingUid();

        /*if( sIsGmsUnstable && callingUid == myUid() ) {
            Log.i(TAG,"Spoof certificate for GMS Core " + sProcessName  + "/" + sPackageName + "/" + myUid() + "(" + callingUid + ") " + !sDisableCertificateSpoof);
            return sDisableCertificateSpoof;
        }*/

        if( myUid() == sGmsUid ) {
            if( callingUid == sGmsUid ) {
                Log.i(TAG,"Spoof certificate for GMS Core " + sProcessName  + "/" + sPackageName + "/" + myUid() + "(" + callingUid + ") " + !sDisableCertificateSpoof);
                return sDisableCertificateSpoof;
            }
            Log.i(TAG,"Spoof certificate thru Google Services "  + sProcessName  + "/" + sPackageName + "/" + myUid() + "(" + callingUid + ") " + !sDisableCertificateSpoofServices);
            return sDisableCertificateSpoofServices;
        }

        if( sIsFinsky ) {
            Log.i(TAG,"Spoof certificate for Vending " + sPackageName + "/" + myUid() + "(" + callingUid + ") " + !sDisableCertificateSpoofVending);
            return sDisableCertificateSpoofVending;
        }

        Log.i(TAG,"Spoof certificate for the app " + sPackageName + "/" + myUid() + "(" + callingUid + ") " + !sDisableCertificateSpoofApps);
        return sDisableCertificateSpoofApps;
    }

    public static PackageInfo spoofPackageInfo(PackageInfo packageInfo, Parcel source) {
        loadSpooferSettings();
        if( sDisableSignatureSpoof ) return packageInfo;

        if (packageInfo.packageName.equals("android")) {

            Log.i(TAG,"Spoof signature " + packageInfo.packageName);

            if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                packageInfo.signatures[0] = spoofedSignature;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (packageInfo.signingInfo != null) {
                    Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                    if (signaturesArray != null && signaturesArray.length > 0) {
                        signaturesArray[0] = spoofedSignature;
                    }
                }
            }
        }
        return packageInfo;
    }

    public static PackageInfo getPackageInfoAsUserCached(
            PackageInfo info, String packageName, long flags, int userId) {

        //int callingUid = Binder.getCallingUid();
        //if( shouldFilterApplication(packageName,userId,callingUid,true) ) return null;
        return info;
    }

    /*public static boolean shouldFilterApplication(String packageName, int userId) {
        int callingUid = Binder.getCallingUid();
        return shouldFilterApplication(packageName,userId,callingUid);
    }*/


    /*public static boolean shouldFilterApplication(String packageName, int userId, int callingUid) {
        return shouldFilterApplication(packageName,userId,callingUid,true);
    }*/



    private static String sDefaultBrowser = null;
    private static String getDefaultBrowser(int userId) {
        if( sDefaultBrowser == null ) {
            try {
                sDefaultBrowser = sPackageManager.getDefaultBrowserPackageNameAsUser(userId);
            } catch( Exception e) {
                sDefaultBrowser = "<unknown>";
                Log.i(TAG,"Can't get default borwser for user " + userId, e);
            }
        }
        return sDefaultBrowser;
    }

    public static int getAppUid(String packageName) {
        try {
            ApplicationInfo appInfo = sPackageManager.getApplicationInfo(packageName, 0);
            return appInfo.uid;
        } catch( Exception e) {
            Log.i(TAG,"Can't get uid for " + packageName /*, e*/);
            return -1;
        }
    }

    private static boolean sCachedInfo = false;
    public static boolean shouldFilterApplication(String packageName, int userId, int _callingUid, boolean isSystem) {

        boolean hide3P = false;
        boolean hideGMS = false;
        boolean hideHMS = false;

        int callingUid = _callingUid == 0 ? callingUid = myUid() : _callingUid;

        try {
        if( !sIsInitialized ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) 
                Log.i(TAG,"HidePackage !sIsInitialized packageName=" + packageName + 
                    ", proc=" + sProcessName + 
                    ", myPkg=" + sPackageName + 
                    ", myUid=" + myUid() + 
                    ", callingUid=" + callingUid +
                    ", isSystem=" + isSystem);

            return false;
        }

        if( sActivityManager == null ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) 
                Log.i(TAG,"HidePackage !sActivityManager packageName=" + packageName + 
                    ", proc=" + sProcessName + 
                    ", myPkg=" + sPackageName + 
                    ", myUid=" + myUid() + 
                    ", callingUid=" + callingUid +
                    ", isSystem=" + isSystem);

            return false;
        }

        if( sApplicationFilterDisabled ) { 
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) 
                Log.i(TAG,"HidePackage sApplicationFilterDisabled packageName=" + packageName + 
                    ", proc=" + sProcessName + 
                    ", myPkg=" + sPackageName + 
                    ", myUid=" + myUid() + 
                    ", callingUid=" + callingUid +
                    ", isSystem=" + isSystem);
            return false;
        }

        if( UserHandle.getAppId(callingUid) < 10000 ) {
            /*if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) 
                Log.i(TAG,"HidePackage (callingUid < 10000) packageName=" + packageName + 
                    ", proc=" + sProcessName + 
                    ", myPkg=" + sPackageName + 
                    ", myUid=" + myUid() + 
                    ", callingUid=" + callingUid +
                    ", isSystem=" + isSystem);*/
            return false;
        }

        //if( myUid() != 1000 && callingUid == myUid() ) isSystem = false;

        if( packageName != null ) {

            BaikalAppProfile profile = null; 

            hideGMS = sActivityManager.getBaikalPackageOption(null,callingUid,BaikalAppProfile.OPCODE_HIDE_GMS,0) != 0;

            if( hideGMS && (packageName.startsWith("com.google.android.gms") || packageName.startsWith("com.android.vending") ) ) { 
                if( packageName == null || sPackageName == null || packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideGMS packageName=" + packageName + " !!! disable by self call !!!! GMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideGMS(2) packageName=" + packageName + " GMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid() + ", callingUid=" + callingUid);
                return true;
            }

            hideHMS = sActivityManager.getBaikalPackageOption(null,callingUid,BaikalAppProfile.OPCODE_HIDE_HMS,0) != 0;

            if( hideHMS && (packageName.startsWith("com.huawei.hwid") || packageName.startsWith("com.huawei.hms")) ) { 
                if( packageName == null || sPackageName == null || packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideHMS packageName=" + packageName + " !!! disable by self call !!!! HMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideHMS(2) packageName=" + packageName + " HMS for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid() + ", callingUid=" + callingUid);
                return true;
            }

            hide3P = sActivityManager.getBaikalPackageOption(null,callingUid,BaikalAppProfile.OPCODE_HIDE_3P,0) != 0;

            if( hide3P && !isSystem ) {
                if( packageName == null || sPackageName == null || 
                    packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ||
                    packageName.startsWith(getDefaultBrowser(userId)) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P packageName=" + packageName + " !!! disable by self call !!!! 3P for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P packageName=" + packageName + " proc=" + sProcessName + ", myPkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                return true;
            } else if( hide3P ) {
                /*if( packageName == null || sPackageName == null || packageName.startsWith(sPackageName) || sPackageName.startsWith(packageName) ) { 
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P packageName=" + packageName + " !!! disable by self call !!!! 3P for proc=" + sProcessName + ", pkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return false;
                }*/
                String lPackageName = packageName == null ? null : packageName.toLowerCase();
                if( lPackageName != null && ( lPackageName.contains("lineage") || lPackageName.contains("crdroid") ) ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P (system) packageName=" + packageName + " proc=" + sProcessName + ", myPkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                    return true;
                }
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P pass (isSystem) packageName=" + packageName + " proc=" + sProcessName + ", myPkg=" + sPackageName + ", myUid=" + myUid()  + ", callingUid=" + callingUid);
                return false;
            } 

        }
        } catch(Exception ex) {
            Log.i(TAG,"shouldFilterApplication: Exception!",ex);
        }

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) 
            Log.i(TAG,"HidePackage packageName=" + packageName + 
                ", proc=" + sProcessName + 
                ", myPkg=" + sPackageName + 
                ", myUid=" + myUid() + 
                ", callingUid=" + callingUid +
                ", isSystem=" + isSystem +
                ", hide3P=" + hide3P +
                ", hideGMS=" + hideGMS +
                ", hideHMS=" + hideHMS +
                "");

        return false;
    }

    public static Context getContext() {
        return sContext;
    }

    public static String getBaikalPackageString(String packageName, int uid, int opCode, String def) {
        if( sActivityManager == null ) return def;
        return sActivityManager.getBaikalPackageString(packageName, uid, opCode, def);
    }


    public enum DEV_CONST {
        SPOOFER_JSON_URL,
        EMPTY_END
    }

    public static final String PUB_SPOOFER_JSON_URL = "https://raw.githubusercontent.com/baikalos/android_vendor_certification/refs/heads/13.0/gms_certified_props.json";
    public static final String DEV_SPOOFER_JSON_URL = "https://raw.githubusercontent.com/baikalos/android_vendor_certification/refs/heads/13.0_dev/gms_certified_props.json";

    public static String getDevString(DEV_CONST num /* enum DEV_CONST */) {
        switch(num) {
            case SPOOFER_JSON_URL:
                return sIsDev ? DEV_SPOOFER_JSON_URL : PUB_SPOOFER_JSON_URL;
        }
        return null;
    }

    public static int getDevInt(DEV_CONST num /* enum DEV_CONST */) {
        switch(num) {
            case SPOOFER_JSON_URL:
                return -1;
        }
        return -1;
    }
}
