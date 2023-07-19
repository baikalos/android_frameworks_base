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

import android.content.Context;
import android.os.Process;
import android.system.Os;
import android.system.StructUtsname;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Slog;

public class BaikalConstants { 

    private static final String TAG = "BaikalConstants";

    public static final int MESSAGE_MIN = 10000;

    public static final int MESSAGE_SETTINGS = MESSAGE_MIN;
    public static final int MESSAGE_ACTIONS = MESSAGE_SETTINGS +1000;
    public static final int MESSAGE_SENSORS = MESSAGE_ACTIONS + 1000;
    public static final int MESSAGE_TELEPHONY = MESSAGE_SENSORS + 1000;
    public static final int MESSAGE_TORCH = MESSAGE_TELEPHONY + 1000;
    public static final int MESSAGE_ACTION = MESSAGE_TORCH + 1000;

    public static final int MESSAGE_APP_PROFILE = MESSAGE_ACTION + 1000;
    public static final int MESSAGE_DEV_PROFILE = MESSAGE_APP_PROFILE + 1000;

    public static final int MESSAGE_MAX = MESSAGE_DEV_PROFILE + 1000;


    public static boolean BAIKAL_DEBUG = false;
    public static boolean BAIKAL_DEBUG_RAW = false;
    public static boolean BAIKAL_DEBUG_TEMPLATE = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_SENSORS = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_TORCH = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_TELEPHONY = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_TELEPHONY_RAW = BAIKAL_DEBUG_RAW | false;
    public static boolean BAIKAL_DEBUG_BLUETOOTH = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_ACTIONS = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_APP_PROFILE = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_DEV_PROFILE = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_SERVICES = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_ACTIVITY = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_ALARM = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_BROADCAST = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_OOM = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_OOM_RAW = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_LOCATION = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_FREEZER = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_POWERHAL = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_POWER = BAIKAL_DEBUG | false;
    public static boolean BAIKAL_DEBUG_JOBS = BAIKAL_DEBUG | false;


    public static final int DEBUG_MASK_ALL = 0x0001;
    public static final int DEBUG_MASK_TEMPLATE = 0x0002;
    public static final int DEBUG_MASK_SENSORS = 0x0004;
    public static final int DEBUG_MASK_TORCH = 0x0008;
    public static final int DEBUG_MASK_TELEPHONY = 0x0010;
    public static final int DEBUG_MASK_TELEPHONY_RAW = 0x0020;
    public static final int DEBUG_MASK_BLUETOOTH = 0x0040;
    public static final int DEBUG_MASK_ACTIONS = 0x0080;
    public static final int DEBUG_MASK_APP_PROFILE = 0x0100;
    public static final int DEBUG_MASK_DEV_PROFILE = 0x0200;
    public static final int DEBUG_MASK_SERVICES = 0x0400;
    public static final int DEBUG_MASK_ACTIVITY = 0x0800;
    public static final int DEBUG_MASK_ALARM = 0x1000;
    public static final int DEBUG_MASK_BROADCAST = 0x2000;
    public static final int DEBUG_MASK_OOM = 0x4000;
    public static final int DEBUG_MASK_LOCATION = 0x8000;

    public static final int DEBUG_MASK_RAW = 0x10000;
    public static final int DEBUG_MASK_OOM_RAW = 0x20000;

    public static final int DEBUG_MASK_FREEZER = 0x40000;
    public static final int DEBUG_MASK_POWERHAL = 0x80000;

    public static final int DEBUG_MASK_POWER = 0x100000;
    public static final int DEBUG_MASK_JOBS = 0x200000;

    public static void Logv(boolean type, int uid,String tag, String msg) {
        if( type ) { Slog.v(tag,msg); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.v(tag,msg); return; }
    }

    public static void Logv(boolean type, int uid, String tag, String msg, Throwable tr) {
        if( type ) { Slog.v(tag,msg,tr); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.v(tag,msg,tr); return; }
    }

    public static void Logd(boolean type, int uid,String tag, String msg) {
        if( type ) { Slog.d(tag,msg); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.d(tag,msg); return; }
    }

    public static void Logd(boolean type, int uid, String tag, String msg, Throwable tr) {
        if( type ) { Slog.d(tag,msg,tr); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.d(tag,msg,tr); return; }
    }

    public static void Logi(boolean type, int uid,String tag, String msg) {
        if( type ) { Slog.i(tag,msg); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.i(tag,msg); return; }
    }

    public static void Logi(boolean type, int uid, String tag, String msg, Throwable tr) {
        if( type ) { Slog.i(tag,msg,tr); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.i(tag,msg,tr); return; }
    }

    public static void Logw(boolean type, int uid,String tag, String msg) {
        if( type ) { Slog.w(tag,msg); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.w(tag,msg); return; }
    }

    public static void Logw(boolean type, int uid, String tag, String msg, Throwable tr) {
        if( type ) { Slog.w(tag,msg,tr); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.w(tag,msg,tr); return; }
    }

    public static void Loge(boolean type, int uid,String tag, String msg) {
        if( type ) { Slog.e(tag,msg); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.e(tag,msg); return; }
    }

    public static void Loge(boolean type, int uid, String tag, String msg, Throwable tr) {
        if( type ) { Slog.e(tag,msg,tr); return; }
        if( uid > 0 && AppProfileSettings.isDebugUid(uid) ) { Slog.e(tag,msg,tr); return; }
    }

    public static String getPackageByUid(Context context, int uid) {
        if( uid < Process.FIRST_APPLICATION_UID ) {
            //if( uid == 1000 ) return "system";           
            //else 
            return "android";
        }

        String[] pkgs = context.getPackageManager().getPackagesForUid(uid);
        if( pkgs != null && pkgs.length > 0 ) return pkgs[0];
        return null;
    }

    public static int getUidByPackage(Context context, String packageName) {
	    int uid = -1;

        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName,
                    PackageManager.MATCH_ALL);
            if( ai != null ) {
                return ai.uid;
            }
        } catch(Exception e) {
            Slog.i(TAG,"Package " + packageName + " not found on this device");
        }
        return uid;
    }

    public static int mIsKernelCompatible = -1;
    public static boolean isKernelCompatible() {
        if( mIsKernelCompatible == -1 ) initIsKernalCompatible();
        //Slog.i(TAG,"isKernelCompatible " + (mIsKernelCompatible == 1) );
        return mIsKernelCompatible == 1;
    }

    private static String kernelVersion = "";
    private static void initIsKernalCompatible() {
        if( Os.uname() == null ) { kernelVersion = ""; mIsKernelCompatible = -1; return; }
        kernelVersion = Os.uname().release;
        if( kernelVersion != null && kernelVersion.contains("baikalos") ) mIsKernelCompatible = 1;
	    else mIsKernelCompatible = 0;
    } 

    private static boolean sAodOnChargerEnabled = false;
    public static boolean isAodOnChargerEnabled() {
        return sAodOnChargerEnabled;
    }

    public static void setAodOnChargerEnabled(boolean enabled) {
        sAodOnChargerEnabled = enabled;
    }

}
