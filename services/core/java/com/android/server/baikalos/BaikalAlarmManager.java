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

import android.baikalos.AppProfile;
import com.android.internal.baikalos.Actions;
import com.android.internal.baikalos.AppProfileSettings;


import android.app.job.IJobScheduler;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobProtoEnums;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobSnapshot;
import android.app.job.JobWorkItem;

import android.app.ActivityManager;

import android.util.Slog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;


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
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;

import android.app.AlarmManager;


import com.android.server.DeviceIdleInternal;
import com.android.server.LocalServices;

import com.android.internal.baikalos.BaikalConstants;

import com.android.internal.util.ArrayUtils;

import java.util.Arrays;

public class BaikalAlarmManager {

    private static final String TAG = "BaikalAlarmManager";

    private static final boolean DEBUG = false;

    private static Object mLock = new Object();

    private boolean mSystemReady = false;
    private boolean mDisableWakeupByDefault = false;
    private Context mContext;
    private AppProfileSettings mAppSettings;

    static BaikalAlarmManager mInstance;

    final BaikalAlarmManagerHandler mHandler;

    public static BaikalAlarmManager getInstance() {
        return mInstance;
    }

    public static BaikalAlarmManager getInstance(Looper looper, Context context) {
        if( mInstance == null ) {
            mInstance = new BaikalAlarmManager(looper,context);
        }
        return mInstance;
    }

    final class BaikalAlarmManagerHandler extends Handler {
        BaikalAlarmManagerHandler(Looper looper) {
            super(looper);
        }

        @Override public void handleMessage(Message msg) {
            onMessage(msg);
        }
    }

    private BaikalAlarmManager(Looper looper, Context context) {
        mContext = context;
        mHandler = new BaikalAlarmManagerHandler(looper);
    }

    private boolean onMessage(Message msg) {
    	/*switch(msg.what) {
    	}*/
    	return false;
    }

    public void initialize() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"initialize()");                
        synchronized(mLock) {
            mInstance = this;
            mAppSettings = AppProfileSettings.getInstance(); 
        }
    }

    public boolean isAppWakeupAllowed(int uid) {
        if( mAppSettings == null ) { 
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Not initialized yet");
            return true;
        }

        if( uid < Process.FIRST_APPLICATION_UID ) return true;

        String packageName = BaikalConstants.getPackageByUid(mContext, uid);

        if( packageName == null ) { 
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Package not found for uid=" + uid);
            return true;
        }

        AppProfile profile = mAppSettings.getProfile(packageName);
        if( profile != null ) {
            if( profile.mDisableWakeup ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Wakeup alarm disabled for " + packageName);
                return false;
            }
            if( profile.getBackground() < 0 ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Wakeup alarm enabled for " + packageName);
                return true;
            }
            if( profile.getBackground() > 0 ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Wakeup alarm restricted for " + packageName);
                return false;
            }
        }

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Wakeup alarm set to " + !mDisableWakeupByDefault + " for " + packageName);
        return !mDisableWakeupByDefault;
    }

    public void setDisableWakeupByDefault(boolean disable) {
        mDisableWakeupByDefault = disable;
    }
}
