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
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemProperties;
import android.text.FontConfig;
import android.util.Log;

import android.baikalos.AppProfile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class BaikalSpoofer { 

    private static final String TAG = "BaikalSpoofer";

    private static boolean sIsGmsUnstable = false;
    private static boolean sIsFinsky = false;
    private static boolean sPreventHwKeyAttestation = false;
    private static boolean sHideDevMode = false;
    private static String sPackageName = null;
    private static String sProcessName = null;

    private static AppProfile spoofedProfile = null;

    public static SpoofDeviceInfo[] Devices = new SpoofDeviceInfo[] {
        new SpoofDeviceInfo("karna","M2007J20CI","Xiaomi","Poco X3 India", "xiaomi", "POCO/karna_eea/karna:11/RKQ1.200826.002/V12.0.6.4.RJGEUXM:user/release-keys"),
        new SpoofDeviceInfo("surya","M2007J20CG","Xiaomi","Poco X3 NFC Global", "xiaomi", "POCO/surya_eea/surya:11/RKQ1.200826.002/V12.0.6.4.RJGEUXM:user/release-keys"),
        new SpoofDeviceInfo("blueline","Pixel 3","Google","Pixel 3", "google" , "google/blueline/blueline:11/RQ3A.211001.001/7641976:user/release-keys" ),
        new SpoofDeviceInfo("crosshatch","Pixel 3 XL","Google","Pixel 3 XL", "google", "google/crosshatch/crosshatch:11/RQ3A.211001.001/7641976:user/release-keys"),
        new SpoofDeviceInfo("flame","Pixel 4","Google","Pixel 4", "google", "google/flame/flame:11/RQ3A.211001.001/7641976:user/release-keys" ),
        new SpoofDeviceInfo("coral","Pixel 4 XL","Google","Pixel 4 XL", "google", "google/coral/coral:11/RQ3A.211001.001/7641976:user/release-keys" ),
        new SpoofDeviceInfo("sunfish","Pixel 4a","Google","Pixel 4a", "google", "google/sunfish/sunfish:11/RQ3A.211001.001/7641976:user/release-keys" ),
        new SpoofDeviceInfo("redfin","Pixel 5","Google","Pixel 5", "google", "google/redfin/redfin:12/SP1A.211105.003/7757856:user/release-keys" ),
        new SpoofDeviceInfo("mdarcy","SHIELD Android TV","NVIDIA","Nvidia Shield TV 2019 Pro", "NVIDIA", "NVIDIA/mdarcy/mdarcy:9/PPR1.180610.011/4079208_2740.7538:user/release-keys" ),
        new SpoofDeviceInfo("OnePlus8T","KB2005","OnePlus","OnePlus 8T", "OnePlus", "OnePlus/OnePlus8T/OnePlus8T:11/RP1A.201005.001/2110091917:user/release-keys" ),
        new SpoofDeviceInfo("OnePlus8Pro","IN2023","OnePlus","OnePlus 8 Pro", "OnePlus", "OnePlus/OnePlus8Pro/OnePlus8Pro:11/RP1A.201005.001/2110091917:user/release-keys"  ),
        new SpoofDeviceInfo("WW_I005D", "ASUS_I005_1","asus", "Asus ROG Phone 5", "asus", "asus/WW_I005D/ASUS_I005_1:11/RKQ1.201022.002/18.0840.2103.26-0:user/release-keys" ),
        new SpoofDeviceInfo("XQ-AU52", "XQ-AU52","Sony", "Sony Xperia 10 II Dual", "Sony", "Sony/XQ-AU52_EEA/XQ-AU52:10/59.0.A.6.24/059000A006002402956232951:user/release-keys" ),
        new SpoofDeviceInfo("XQ-AS72", "XQ-AS72","Sony", "Sony Xperia 2 5G (Asia)", "Sony" , null),
        new SpoofDeviceInfo("z3s", "SM-G988B","Samsung", "Samsung S21", "samsung", "samsung/z3sxxx/z3s:10/QP1A.190711.020/G988BXXU1ATCT:user/release-keys"),
        new SpoofDeviceInfo("cmi", "Mi 10 Pro","Xiaomi", "Xiaomi Mi 10 Pro", "xiaomi", "Xiaomi/cmi/cmi:11/RKQ1.200710.002/V12.1.2.0.RJACNXM:user/release-keys"),
        new SpoofDeviceInfo("raven","Pixel 6 Pro","Google","Pixel 6 Pro", "google", "google/raven/raven:12/SD1A.210817.036/7805805:user/release-keys" ),
        new SpoofDeviceInfo("dipper", "MI 8","Xiaomi", "Xiaomi MI 8", "xiaomi", "Xiaomi/dipper/dipper:10/QKQ1.190828.002/V11.0.3.0.QEAMIXM:user/release-keys"),
        new SpoofDeviceInfo("vayu", "M2102J20SG","Xiaomi", "Poco X3 Pro", "xiaomi", "POCO/vayu_global/vayu:11/RKQ1.200826.002/V12.0.4.0.RJUMIXM:user/release-keys"),
        new SpoofDeviceInfo("agate", "21081111RG","Xiaomi", "Xiaomi Mi 11T", "xiaomi", null),
        new SpoofDeviceInfo("vayu", "R11 Plus","Oppo", "Oppo R11 Plus", "oppo", null),
        new SpoofDeviceInfo("marlin","Pixel XL","Google","Pixel XL", "google" , "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys" ),
        new SpoofDeviceInfo("star", "M2102K1G","Xiaomi", "Xiaomi Mi 11", "xiaomi", null),

    };

    public static void maybeSpoofProperties(Application app, Context context) {
        maybeSpoofBuild(app.getPackageName(), app.getProcessName(), context);
        maybeSpoofDevice(app.getPackageName(), context);
    }

    public static int maybeSpoofFeature(String packageName, String name, int version) {
        if (packageName != null &&
                packageName.contains("com.google.android.apps.photos") ) {

            Log.i(TAG, "App " + packageName + " is requested " + name + " feature with " + version + " version");
            if( name.contains("PIXEL_2021_EXPERIENCE") || name.contains("PIXEL_2022_EXPERIENCE") ) {
                return 0;
            }
            if( "com.google.photos.trust_debug_certs".equals(name) ) return 1;
            if( "com.google.android.apps.photos.NEXUS_PRELOAD".equals(name) ) return 1;
            if( "com.google.android.feature.PIXEL_EXPERIENCE".equals(name) ) return 1;
            if( "com.google.android.apps.photos.PIXEL_PRELOAD".equals(name) ) return 1;
            if( "com.google.android.apps.photos.PIXEL_2016_PRELOAD".equals(name) ) return 1;
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
        
        if( "com.google.android.gms.unstable".equals(processName) &&
            "com.google.android.gms".equals(packageName) ) {

            sIsGmsUnstable = true;

            String stockFp = SystemProperties.get("ro.build.stock_fingerprint", null);
            String stockSecurityPatch = SystemProperties.get("ro.build.stock_sec_patch", null);

            setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.S);

            Log.e(TAG, "Spoof Device GMS FINGERPRINT: [" + stockFp + "], " + Application.getProcessName());
            if( stockFp != null && !stockFp.isEmpty() )
                setBuildField("FINGERPRINT", stockFp);

            Log.e(TAG, "Spoof Device GMS SECURITY_PATCH: [" + stockSecurityPatch + "]");
            if( stockSecurityPatch != null && !stockSecurityPatch.isEmpty() )
                setVersionField("SECURITY_PATCH", stockSecurityPatch);
            return;                    
        } else if( "com.android.vending".equals(packageName) ) {
            sIsFinsky = true;
        }
    }


    private static void maybeSpoofDevice(String packageName, Context context) {

        if( packageName == null ) return;

        int device_id = -1;

        try {
            AppProfile profile = AppProfileSettings.loadSingleProfile(packageName, context.getContentResolver());

            spoofedProfile = profile;

            if( profile != null ) {
                Log.e(TAG, "Loaded profile :" + profile.toString());
                device_id = profile.mSpoofDevice - 1;

                android.baikalos.AppProfile.setCurrentAppProfile(profile);
                
               
                if( profile.mOverrideFonts ) {
                //    FontConfig.setBaikalOverride(true);
                }

                final int uid = myUid();

                
                if( profile.mPreventHwKeyAttestation ) {
                    sPreventHwKeyAttestation = true;
                    Log.e(TAG, "Overriding hardware attestation for :" + packageName + " to " + profile.mPreventHwKeyAttestation);
                } 
                if( profile.mHideDevMode ) {
                    sHideDevMode = true;
                    Log.e(TAG, "Overriding developer mode for :" + packageName + " to " + profile.mHideDevMode);
                } 

                //setProcessField("APP_PROFILE", profile);

                //device_id = profile.mSpoofDevice - 1;
            } else {
                AppProfile newProfile = new AppProfile(packageName);
                //setProcessField("APP_PROFILE", profile);
                android.baikalos.AppProfile.setCurrentAppProfile(newProfile);

            }

        } catch(Exception fl) {
            Log.e(TAG, "Failed to load profile for :" + packageName, fl);
        }

        try {
            //String val = SystemProperties.get("b.spf." + packageName,"0");

            //int device_id = Integer.parseInt(val) - 1;
            if( device_id < 0 ) return;

            if( device_id >=  BaikalSpoofer.Devices.length ) {
                Log.e(TAG, "Spoof Device : invalid device id: " + device_id);
                return;
            }

            Log.e(TAG, "Spoof Device Profile :" + packageName);
            Log.e(TAG, "Spoof Device :" + device_id);

            SpoofDeviceInfo device = BaikalSpoofer.Devices[device_id];

            Log.e(TAG, "Spoof Device BRAND: " + device.deviceBrand);
            Log.e(TAG, "Spoof Device MANUFACTURER: " + device.deviceManufacturer);
            Log.e(TAG, "Spoof Device MODEL: " + device.deviceModel);
            Log.e(TAG, "Spoof Device DEVICE: " + device.deviceName);
            Log.e(TAG, "Spoof Device PRODUCT: " + device.deviceName);
            Log.e(TAG, "Spoof Device FINGERPRINT: " + device.deviceFp);

            setBuildField("BRAND", device.deviceBrand);
            setBuildField("MANUFACTURER", device.deviceManufacturer);
            setBuildField("MODEL", device.deviceModel);
            setBuildField("DEVICE", device.deviceName);
            setBuildField("PRODUCT", device.deviceName);

            if( device.deviceFp != null ) setBuildField("FINGERPRINT", device.deviceFp);
        } catch(Exception e) {
            Log.e(TAG, "Failed to spoof Device :" + packageName, e);
        }
        
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
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet
        if (isCurrentProcessGmsUnstable() && isCallerSafetyNet()) {
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
}
