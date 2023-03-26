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


import static com.android.server.am.ProcessList.SCHED_GROUP_BACKGROUND;
import static com.android.server.am.ProcessList.SCHED_GROUP_RESTRICTED;
import static com.android.server.am.ProcessList.SCHED_GROUP_DEFAULT;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP_BOUND;

import static android.os.Process.THREAD_GROUP_DEFAULT;
import static android.os.Process.THREAD_GROUP_BACKGROUND;
import static android.os.Process.THREAD_GROUP_TOP_APP;
import static android.os.Process.THREAD_GROUP_RESTRICTED;
import static android.os.Process.THREAD_GROUP_AUDIO_APP;
import static android.os.Process.THREAD_GROUP_AUDIO_SYS;
import static android.os.Process.THREAD_GROUP_RT_APP;

import static android.os.PowerManagerInternal.MODE_LOW_POWER;
import static android.os.PowerManagerInternal.MODE_SUSTAINED_PERFORMANCE;
import static android.os.PowerManagerInternal.MODE_FIXED_PERFORMANCE;
import static android.os.PowerManagerInternal.MODE_VR;
import static android.os.PowerManagerInternal.MODE_LAUNCH;
import static android.os.PowerManagerInternal.MODE_EXPENSIVE_RENDERING;
import static android.os.PowerManagerInternal.MODE_INTERACTIVE;
import static android.os.PowerManagerInternal.MODE_DEVICE_IDLE;
import static android.os.PowerManagerInternal.MODE_DISPLAY_INACTIVE;

import android.util.Slog;

import android.content.Context;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.SystemClock;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.res.Resources;
import android.net.Uri;

import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.CellLocation;
import android.telephony.CellInfo;
import android.telephony.SignalStrength;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.VoLteServiceState;

import android.os.AsyncTask;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;

import android.database.ContentObserver;

import android.provider.Settings;

import android.util.SparseArray;

import com.android.internal.view.RotationPolicy;
import android.view.WindowManagerGlobal;
import android.view.IWindowManager;
import android.view.Display;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import android.baikalos.AppProfile;
import com.android.internal.baikalos.Actions;
import com.android.internal.baikalos.AppProfileSettings;
import com.android.internal.baikalos.BaikalConstants;

import com.android.server.LocalServices;

import java.util.ArrayList;
import java.util.List;

public class BaikalDebugManager { 

    private static final String TAG = "Baikal.Debug";

    final Context mContext;
    final Handler mHandler;
    final Looper mLooper;

    private DebugManagerContentObserver mObserver;
    private ContentResolver mResolver;

    private boolean mDebug = false;
    private long mDebugMask = 0;
    private String mDebugMaskString = "0";

    static BaikalDebugManager mInstance;

    public static BaikalDebugManager getInstance() {
        return mInstance;
    }

    public static BaikalDebugManager getInstance(Looper looper, Context context) {
        if( mInstance == null ) {
            mInstance = new BaikalDebugManager(looper,context);
        }
        return mInstance;
    }

    final class DebugManagerContentObserver extends ContentObserver {

        DebugManagerContentObserver(Handler handler) {
            super(handler);

            try {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEBUG),
                    false, this);
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEBUG_MASK),
                    false, this);
            } catch( Exception e ) {
            }
        
            synchronized(this) {
                updateConstantsLocked();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalDebugManager.this) {
                updateConstantsLocked();
            }
        }
    }

    private BaikalDebugManager(Looper looper, Context context) {
        mContext = context;
        mLooper = looper;
        mHandler = new Handler(mLooper);
    }

    public void initialize() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"initialize()");                
        synchronized(this) {

            mInstance = this;
            mResolver = mContext.getContentResolver();
            mObserver = new DebugManagerContentObserver(mHandler);
        }
    }

    protected void updateConstantsLocked() {

        boolean changed = false;

        boolean debug = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEBUG, 0) != 0;

        String debugMaskString = Settings.Global.getString(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEBUG_MASK);
        Slog.i(TAG,"enabled=" + debug + ", DebugMask=" + debugMaskString);

        if( debugMaskString != null && !"".equals(debugMaskString) &&
            (debug != mDebug || !debugMaskString.equals(mDebugMaskString)) ) {
            mDebug = debug;
            mDebugMaskString = debugMaskString;
            updateDebug();
        }
    }

    private void updateDebug() {

        BaikalConstants.BAIKAL_DEBUG_TEMPLATE = false;
        BaikalConstants.BAIKAL_DEBUG_SENSORS = false;
        BaikalConstants.BAIKAL_DEBUG_TORCH = false;
        BaikalConstants.BAIKAL_DEBUG_TELEPHONY = false;
        BaikalConstants.BAIKAL_DEBUG_TELEPHONY_RAW = false;
        BaikalConstants.BAIKAL_DEBUG_BLUETOOTH = false;
        BaikalConstants.BAIKAL_DEBUG_ACTIONS = false;
        BaikalConstants.BAIKAL_DEBUG_APP_PROFILE = false;
        BaikalConstants.BAIKAL_DEBUG_DEV_PROFILE = false;
        BaikalConstants.BAIKAL_DEBUG_SERVICES = false;
        BaikalConstants.BAIKAL_DEBUG_ACTIVITY = false;
        BaikalConstants.BAIKAL_DEBUG_ALARM = false;
        BaikalConstants.BAIKAL_DEBUG_BROADCAST = false;
        BaikalConstants.BAIKAL_DEBUG_RAW = false;
        BaikalConstants.BAIKAL_DEBUG_OOM = false;
        BaikalConstants.BAIKAL_DEBUG_OOM_RAW = false;
        BaikalConstants.BAIKAL_DEBUG_LOCATION = false;
        BaikalConstants.BAIKAL_DEBUG_FREEZER = false;
        BaikalConstants.BAIKAL_DEBUG_POWERHAL = false;
        BaikalConstants.BAIKAL_DEBUG_POWER = false;
        BaikalConstants.BAIKAL_DEBUG_JOBS = false;

        int debugMask = 0;

        if( mDebug ) { 
            try {
                debugMask = Integer.parseInt(mDebugMaskString,16);
                Slog.i(TAG, "debugMask=" + debugMask);
            } catch(Exception e) {
                Slog.e(TAG, "Invalid debug mask:" + mDebugMaskString, e);
            }
        }

        mDebugMask = debugMask;
        //if( (debugMask&BaikalConstants.DEBUG_MASK_ALL) != 0 ) debugMask =0xFFFFFF; 
        if( (debugMask&BaikalConstants.DEBUG_MASK_TEMPLATE) !=0 ) BaikalConstants.BAIKAL_DEBUG_TEMPLATE = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_SENSORS) !=0 ) BaikalConstants.BAIKAL_DEBUG_SENSORS = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_TORCH) !=0 ) BaikalConstants.BAIKAL_DEBUG_TORCH = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_TELEPHONY) !=0 ) BaikalConstants.BAIKAL_DEBUG_TELEPHONY = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_TELEPHONY_RAW) !=0 ) BaikalConstants.BAIKAL_DEBUG_TELEPHONY_RAW = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_BLUETOOTH) !=0 ) BaikalConstants.BAIKAL_DEBUG_BLUETOOTH = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_ACTIONS) !=0 ) BaikalConstants.BAIKAL_DEBUG_ACTIONS = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_APP_PROFILE) !=0 ) BaikalConstants.BAIKAL_DEBUG_APP_PROFILE = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_DEV_PROFILE) !=0 ) BaikalConstants.BAIKAL_DEBUG_DEV_PROFILE = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_SERVICES) !=0 ) BaikalConstants.BAIKAL_DEBUG_SERVICES = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_ACTIVITY) !=0 ) BaikalConstants.BAIKAL_DEBUG_ACTIVITY = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_ALARM) !=0 ) BaikalConstants.BAIKAL_DEBUG_ALARM = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_BROADCAST) !=0 ) BaikalConstants.BAIKAL_DEBUG_BROADCAST = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_RAW) !=0 ) BaikalConstants.BAIKAL_DEBUG_RAW = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_OOM) !=0 ) BaikalConstants.BAIKAL_DEBUG_OOM = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_OOM_RAW) !=0 ) BaikalConstants.BAIKAL_DEBUG_OOM_RAW = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_LOCATION) !=0 ) BaikalConstants.BAIKAL_DEBUG_LOCATION = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_FREEZER) !=0 ) BaikalConstants.BAIKAL_DEBUG_FREEZER = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_POWERHAL) !=0 ) BaikalConstants.BAIKAL_DEBUG_POWERHAL = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_POWER) !=0 ) BaikalConstants.BAIKAL_DEBUG_POWER = true;
        if( (debugMask&BaikalConstants.DEBUG_MASK_JOBS) !=0 ) BaikalConstants.BAIKAL_DEBUG_JOBS = true;
    }
}
