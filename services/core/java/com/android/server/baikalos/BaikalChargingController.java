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


import static android.os.PowerManagerInternal.WAKEFULNESS_ASLEEP;
import static android.os.PowerManagerInternal.WAKEFULNESS_AWAKE;
import static android.os.PowerManagerInternal.WAKEFULNESS_DREAMING;
import static android.os.PowerManagerInternal.WAKEFULNESS_DOZING;

import android.util.Slog;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Binder;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
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
import android.content.pm.ApplicationInfo;
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

import android.database.ContentObserver;

import android.provider.Settings;

import android.util.SparseArray;

import android.baikalos.BaikalAppProfile;

import com.android.internal.annotations.GuardedBy;

import com.android.internal.baikalos.BaikalActions;
//import com.android.internal.baikalos.BaikalAppProfileSettings;
import com.android.internal.baikalos.BaikalConstants;
import com.android.internal.baikalos.BaikalPowerSaverPolicyConfig;
import com.android.internal.baikalos.BaikalSpoofer;



import com.android.server.location.provider.LocationProviderManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BaikalChargingController { 

    private static final String TAG = "BaikalChargingController";

    private final Object mLock = new Object();

    final Context mContext;
    final InternalHandler mHandler;
    final Looper mLooper;

    /*private static Boolean mBypassChargingAvailable;
    private static String mPowerInputSuspendSysfsNode;
    private static String mPowerInputSuspendValue;
    private static String mPowerInputResumeValue;
    private static String mPowerInputLimitValue;*/

    private InternalContentObserver mObserver;
    private ContentResolver mResolver;

    private BaikalService mService;
    private BaikalAppProfileService mAppProfileService;

    private int mWakefulness = WAKEFULNESS_AWAKE;
    private boolean mIsPowered = true;
    private boolean mScreenOn = true;
    private boolean mCarModeEnabled = false;

    boolean mBypassChargingTopApp;
    boolean mBypassChargingForced;
    boolean mBypassChargingScreenOn;

    boolean mSmartBypassChargingEnabled;
    boolean mLimitedChargingForced;
    boolean mLimitedChargingScreenOn;

    boolean mAodOnCharger;

    int mCurrentMode = 0;

    static BaikalChargingController sInstance;

    public static BaikalChargingController getInstance() {
        return sInstance;
    }

    public static BaikalChargingController getInstance(BaikalService service, BaikalAppProfileService appProfileService, Looper looper, Context context) {
        if( sInstance == null ) {
            synchronized (BaikalChargingController.class) {
                if (sInstance == null) {
                    sInstance = new BaikalChargingController(service,appProfileService,looper,context);
                }
            }
        }
        return sInstance;
    }

    final class InternalHandler extends Handler {
        InternalHandler(Looper looper) {
            super(looper);
        }
    }

    final class InternalContentObserver extends ContentObserver {

        InternalContentObserver(Handler handler) {
            super(handler);

            Slog.i(TAG,"InternalContentObserver");                

            synchronized(this) {
                updateConstantsLocked();
            }

            try {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_PROFILE_MANAGER_REFRESH),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_FORCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_TOP_APP),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_SCREEN_ON),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_AOD_ON_CHARGER),
                    false, this);

            } catch( Exception e ) {
                Slog.i(TAG,"InternalContentObserver: exception!", e);
            }
       
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalChargingController.this) {
                updateConstantsLocked();
            }
        }
    }

    private BaikalChargingController(BaikalService service, BaikalAppProfileService appProfileService, Looper looper, Context context) {
        mContext = context;
        mLooper = looper;
        mService = service;
        mAppProfileService = appProfileService;
        mHandler = new InternalHandler(mLooper);

        final Resources resources = mContext.getResources();

        /*mBypassChargingAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_bypassChargingAvailable);
        mPowerInputSuspendSysfsNode = resources.getString(
                com.android.internal.R.string.config_bypassChargingSysfsNode);
        mPowerInputSuspendValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingSuspendValue);
        mPowerInputResumeValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingResumeValue);
        mPowerInputLimitValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingLimitValue);*/
        Slog.i(TAG,"created");
    }

    public void onSystemReady() {
        /*if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) */ Slog.i(TAG,"onSystemReady()");                

        mResolver = mContext.getContentResolver();

        synchronized(this) {
            mObserver = new InternalContentObserver(mHandler);
        }
        Slog.i(TAG,"initialized");
    }

    @GuardedBy("mLock")
    protected void updateConstantsLocked() {
        
        boolean changed = false;

        /*if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE )*/ Slog.i(TAG,"BaikalOS Charging Controller Settings update");

        boolean bypassForced = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0) != 0;
        if( mBypassChargingForced != bypassForced ) {
            mBypassChargingForced = bypassForced;
            changed = true;
        }

        boolean bypassChargingScreenOn = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BPCHARGE_SCREEN_ON, 0) != 0;
        if( mBypassChargingScreenOn != bypassChargingScreenOn ) {
            mBypassChargingScreenOn = bypassChargingScreenOn;
            changed = true;
        }

        boolean bypassChargingTopApp = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BPCHARGE_TOP_APP, 0) != 0;
        if( mBypassChargingTopApp != bypassChargingTopApp ) {
            mBypassChargingTopApp = bypassChargingTopApp;
            changed = true;
        }

        boolean aodOnCharger = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_AOD_ON_CHARGER, 0) != 0;
        if( mAodOnCharger != aodOnCharger ) {
            mAodOnCharger = aodOnCharger;
            changed = true;
        }

        if( changed ) {
            updateChargingMode();
        }
    }

    private void updateChargingMode() {
        int newMode = (mBypassChargingForced || mBypassChargingTopApp) ? 1 : 0;
        if( newMode != mCurrentMode ) {
            mCurrentMode = newMode;
            BaikalActions.sendChargingModeChanged(newMode);
        }
    }

    public void setCharging(boolean charging) {
        if( charging != mIsPowered ) {
            mIsPowered = charging;
            updateChargingMode();
        }
    }

    public void setWakefulness(int wakefulness) {
        if( wakefulness != mWakefulness ) {
            mWakefulness = wakefulness; 
            updateChargingMode();
        }
    }

    public void setScreenMode(boolean mode) {
        if( mode != mScreenOn ) {
            mScreenOn = mode;
            updateChargingMode();
        }
    }

}
