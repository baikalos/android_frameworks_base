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

public class BaikalBoostManager { 

    private static final String TAG = "Baikal.Boost";

    final Context mContext;
    final Handler mHandler;
    final Looper mLooper;

    private ManagerContentObserver mObserver;
    private ContentResolver mResolver;

    private int mSilver = -1;
    private int mGold = -1;
    private int mPlatinum = -1;

    static BaikalBoostManager mInstance;

    public static BaikalBoostManager getInstance() {
        return mInstance;
    }

    public static BaikalBoostManager getInstance(Looper looper, Context context) {
        if( mInstance == null ) {
            mInstance = new BaikalBoostManager(looper,context);
        }
        return mInstance;
    }

    final class ManagerContentObserver extends ContentObserver {

        ManagerContentObserver(Handler handler) {
            super(handler);

            try {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BOOST_OVERRIDE_SILVER),
                    false, this);
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BOOST_OVERRIDE_GOLD),
                    false, this);
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BOOST_OVERRIDE_PLATINUM),
                    false, this);
            } catch( Exception e ) {
            }
        
            synchronized(this) {
                updateConstantsLocked();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalBoostManager.this) {
                updateConstantsLocked();
            }
        }
    }

    private BaikalBoostManager(Looper looper, Context context) {
        mContext = context;
        mLooper = looper;
        mHandler = new Handler(mLooper);
    }

    public void initialize() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"initialize()");                
        synchronized(this) {

            mInstance = this;
            mResolver = mContext.getContentResolver();
            mObserver = new ManagerContentObserver(mHandler);
        }
    }

    protected void updateConstantsLocked() {

        int silver = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BOOST_OVERRIDE_SILVER, -1);
        int gold = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BOOST_OVERRIDE_GOLD, -1);
        int platinum = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BOOST_OVERRIDE_PLATINUM, -1);

        if( silver != mSilver) { 
            mSilver = silver; 
            UpdateBoost("silver",silver);
        }

        if( gold != mGold) { 
            mGold = gold; 
            UpdateBoost("gold",gold);
        }

        if( platinum != mPlatinum) { 
            mPlatinum = platinum; 
            UpdateBoost("platinum",platinum);
        }

    }

    private void UpdateBoost(String boost, int value) {
        String key = "baikalos.ovr.boost." + boost;
        String text = Integer.toString(value);
        Slog.e(TAG, "UpdateBoost: key=" + key + ", value=" + value);
        SystemProperties.set(key, text);
    }
}
