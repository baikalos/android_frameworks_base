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

public class AppProfileManager { 

    private static final String TAG = "Baikal.AppProfile";

    final Context mContext;
    final AppProfileManagerHandler mHandler;
    final Looper mLooper;

    private static String mPowerInputSuspendSysfsNode;
    private static String mPowerInputSuspendValue;
    private static String mPowerInputResumeValue;
    private static String mPowerInputLimitValue;

    private AppProfileContentObserver mObserver;
    private ContentResolver mResolver;

    private static final int MESSAGE_APP_PROFILE_UPDATE = BaikalConstants.MESSAGE_APP_PROFILE + 100;

    private AppProfileSettings mAppSettings;
    private IPowerManager mPowerManager;

    private boolean mOnCharger = false;
    private boolean mDeviceIdleMode = false;
    private boolean mScreenMode = true;

    private boolean mAodOnCharger = false;

    private int mTopUid=-1;
    private String mTopPackageName;

    private int mActivePerfProfile = -1;
    private int mActiveThermProfile = -1;
    private int mRequestedPowerMode = -1;

    private static Object mCurrentProfileSync = new Object();
    private static AppProfile mCurrentProfile = new AppProfile("system");

    private int mActiveMinFrameRate = -1;
    private int mActiveMaxFrameRate = -1;
                                   
    private int mDefaultMinFps = -1;
    private int mDefaultMaxFps = -1;

    private int mDefaultPerformanceProfile;
    private int mDefaultThermalProfile;

    private boolean mSmartBypassChargingEnabled;
    private boolean mBypassChargingForced;
    private boolean mBypassChargingScreenOn;
    private boolean mLimitedChargingScreenOn;

    private boolean mStaminaEnabled;

    private boolean mPerfAvailable = false;
    private boolean mThermAvailable = false;

    private boolean mAggressiveMode = false;
    private boolean mExtremeMode = false;
    private boolean mAggressiveIdleMode = false;
    private boolean mKillInBackground = false;

    private boolean mPhoneCall = false;

    private int mGmsUid = -1;

    TelephonyManager mTelephonyManager;

    static AppProfileManager mInstance;
    static BaikalDebugManager mDebugManager;
    static BaikalBoostManager mBoostManager;

    private PowerManagerInternal mPowerManagerInternal;

    public static AppProfile getCurrentProfile() {
        return mCurrentProfile;
    }

    public static AppProfileManager getInstance() {
        return mInstance;
    }

    public static AppProfileManager getInstance(Looper looper, Context context) {
        if( mInstance == null ) {
            mInstance = new AppProfileManager(looper,context);
        }
        return mInstance;
    }

    final class AppProfileManagerHandler extends Handler {
        AppProfileManagerHandler(Looper looper) {
            super(looper);
    
        }

        @Override public void handleMessage(Message msg) {
            onMessage(msg);
        }
    }

    final class AppProfileContentObserver extends ContentObserver {

        AppProfileContentObserver(Handler handler) {
            super(handler);

            try {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_FORCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_PERFORMANCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_THERMAL),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_STAMINA_ENABLED),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_AGGRESSIVE_IDLE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_EXTREME_IDLE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_AGGRESSIVE_DEVICE_IDLE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_KILL_IN_BACKGROUND),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.BAIKALOS_DEFAULT_MINFPS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.BAIKALOS_DEFAULT_MAXFPS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_SCREEN_ON),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_LIMITED_CHARGE_SCREEN_ON),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_AOD_ON_CHARGER),
                    false, this);

            } catch( Exception e ) {
            }
        
            synchronized(this) {
                updateConstantsLocked();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(AppProfileManager.this) {
                updateConstantsLocked();
            }
        }
    }

    private AppProfileManager(Looper looper, Context context) {
        mContext = context;
        mLooper = looper;
        mHandler = new AppProfileManagerHandler(mLooper);

        final Resources resources = mContext.getResources();


        mPowerInputSuspendSysfsNode = resources.getString(
                com.android.internal.R.string.config_bypassChargingSysfsNode);
        mPowerInputSuspendValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingSuspendValue);
        mPowerInputResumeValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingResumeValue);
        mPowerInputLimitValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingLimitValue);
    }

    public boolean onMessage(Message msg) {
    	switch(msg.what) {
    	    case MESSAGE_APP_PROFILE_UPDATE:
                if( BaikalConstants.BAIKAL_DEBUG_POWERHAL ) Slog.i(TAG, "MESSAGE_APP_PROFILE_UPDATE cancel all boost requests");
    		    return true;
    	}
    	return false;
    }

    public void initialize() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"initialize()");                
        synchronized(this) {

            mInstance = this;
            mAppSettings = AppProfileSettings.getInstance(); 

            mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
            mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);

            IntentFilter topAppFilter = new IntentFilter();
            topAppFilter.addAction(Actions.ACTION_TOP_APP_CHANGED);
            mContext.registerReceiver(mTopAppReceiver, topAppFilter);

            IntentFilter idleFilter = new IntentFilter();
            idleFilter.addAction(Actions.ACTION_IDLE_MODE_CHANGED);
            mContext.registerReceiver(mIdleReceiver, idleFilter);

            IntentFilter chargerFilter = new IntentFilter();
            chargerFilter.addAction(Actions.ACTION_CHARGER_MODE_CHANGED);
            mContext.registerReceiver(mChargerReceiver, chargerFilter);

            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(Actions.ACTION_SCREEN_MODE_CHANGED);
            mContext.registerReceiver(mScreenReceiver, screenFilter);

            IntentFilter profileFilter = new IntentFilter();
            profileFilter.addAction(Actions.ACTION_SET_PROFILE);
            mContext.registerReceiver(mProfileReceiver, profileFilter);

            mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            mTelephonyManager.listen(mPhoneStateListener, 0xFFFFFFF);

            mResolver = mContext.getContentResolver();
            mObserver = new AppProfileContentObserver(mHandler);

            mGmsUid = BaikalConstants.getUidByPackage(mContext, "com.google.android.gms");
            mDebugManager = BaikalDebugManager.getInstance(mLooper,mContext); 
            mDebugManager.initialize();

            mBoostManager = BaikalBoostManager.getInstance(mLooper,mContext); 
            mBoostManager.initialize();

        }
    }

    protected void updateConstantsLocked() {

        boolean changed = false;

        mAggressiveMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_AGGRESSIVE_IDLE, 0) != 0;
        mExtremeMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_EXTREME_IDLE, 0) != 0;
        mAggressiveIdleMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_AGGRESSIVE_DEVICE_IDLE, 0) != 0;
        mKillInBackground = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_KILL_IN_BACKGROUND, 0) != 0;

        int defaultPerformanceProfile = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEFAULT_PERFORMANCE, -1);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading performance profile=" + defaultPerformanceProfile);

        if( mDefaultPerformanceProfile != defaultPerformanceProfile ) {
            mDefaultPerformanceProfile = defaultPerformanceProfile;
            changed = true;
        }

        int defaultThermalProfile = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEFAULT_THERMAL, -1);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading thermal profile=" + defaultThermalProfile);
        if( mDefaultThermalProfile != defaultThermalProfile ) {
            mDefaultThermalProfile = defaultThermalProfile;
            changed = true;
        }

        boolean forced = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0) != 0;
        if( mBypassChargingForced != forced ) {
            mBypassChargingForced = forced;
            changed = true;
        }

        boolean bypassChargingScreenOn = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BPCHARGE_SCREEN_ON, 0) != 0;
        if( mBypassChargingScreenOn != bypassChargingScreenOn ) {
            mBypassChargingScreenOn = bypassChargingScreenOn;
            changed = true;
        }

        boolean limitedChargingScreenOn = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_LIMITED_CHARGE_SCREEN_ON, 0) != 0;
        if( mLimitedChargingScreenOn != limitedChargingScreenOn ) {
            mLimitedChargingScreenOn = limitedChargingScreenOn;
            changed = true;
        }

        boolean staminaEnabled = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_STAMINA_ENABLED, 0) != 0;
        if( mStaminaEnabled != staminaEnabled ) {
            mStaminaEnabled = staminaEnabled;
            changed = true;
        }

        int defaultMinFps = (int) Settings.System.getFloat(mContext.getContentResolver(), Settings.System.BAIKALOS_DEFAULT_MINFPS, 0);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading BAIKALOS_DEFAULT_MINFPS=" + defaultMinFps);
        if( mDefaultMinFps != defaultMinFps ) {
            mDefaultMinFps = defaultMinFps;
            changed = true;
        }

        int defaultMaxFps = (int) Settings.System.getFloat(mContext.getContentResolver(), Settings.System.BAIKALOS_DEFAULT_MAXFPS, 0);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading BAIKALOS_DEFAULT_MAXFPS=" + defaultMaxFps);
        if( mDefaultMaxFps != defaultMaxFps ) {
            mDefaultMaxFps = defaultMaxFps;
            changed = true;
        }

        boolean aodOnCharger = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_AOD_ON_CHARGER, 0) != 0;
        if( mAodOnCharger != aodOnCharger ) {
            mAodOnCharger = aodOnCharger;
            BaikalConstants.setAodOnChargerEnabled(isAodOnChargerEnabled());
        }

        if( changed ) {
            activateCurrentProfileLocked(false);
        }
    }

    protected void updateBypassChargingIfNeededLocked() {
        updateBypassChargingLocked();
    }

    protected void updateStaminaIfNeededLocked() {
    }

    protected void setActiveFrameRateLocked(int minFps, int maxFps) {

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setActiveFrameRateLocked : min=" + minFps + ", max=" + maxFps);

        if( setHwFrameRateLocked(minFps, maxFps, false) ) {
            mActiveMinFrameRate = minFps;
            mActiveMaxFrameRate = maxFps;
        }
    }

    protected void setDeviceIdleModeLocked(boolean mode) {
        if( mDeviceIdleMode != mode ) {
            mDeviceIdleMode = mode;
            //if( mDeviceIdleMode ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after device idle changed mode=" + mDeviceIdleMode);
                mHandler.postDelayed( new Runnable() {
                    @Override
                    public void run() { 
                        synchronized(this) {
                            restoreProfileForCurrentModeLocked(true);
                        }
                    }
                }, 100);
            //}
        }
    }

    protected void onCallStateChangedLocked(int state, String incomingNumber) {
    }

    protected void onPreciseCallStateChangedLocked(PreciseCallState callState) {

        boolean state =  callState.getRingingCallState() > 0 ||
                         callState.getForegroundCallState() > 0 ||
                         callState.getBackgroundCallState() > 0;

        if( mPhoneCall != state ) {
            mPhoneCall = state;

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after phone mode changed mode=" + mPhoneCall);
            mHandler.postDelayed( new Runnable() {
                @Override
                public void run() { 
                    synchronized(this) {
                        //SystemProperties.set("baikal.screen_mode", mScreenMode ? "1" : "0");
                        restoreProfileForCurrentModeLocked(true);
                    }
                }
            }, 100);

        }
    }


    protected void setScreenModeLocked(boolean mode) {
        if( mScreenMode != mode ) {
            mScreenMode = mode;
            BaikalConstants.setAodOnChargerEnabled(isAodOnChargerEnabled());
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after screen mode changed mode=" + mScreenMode);
            mHandler.postDelayed( new Runnable() {
                @Override
                public void run() { 
                    synchronized(this) {
                        //SystemProperties.set("baikal.screen_mode", mScreenMode ? "1" : "0");
                        restoreProfileForCurrentModeLocked(true);
                    }
                }
            }, 100);
        }
    }

    protected void setProfileExternalLocked(String profile) {
        if( profile == null || profile.equals("") ) {
            synchronized(this) {
                restoreProfileForCurrentModeLocked(true);
            }
        } else {
        }   
    }

    protected void restoreProfileForCurrentModeLocked(boolean force) {
        activateCurrentProfileLocked(force);
    }

    protected void activateIdleProfileLocked() {

        int thermMode = mDefaultThermalProfile <= 0 ?  1 : mDefaultThermalProfile;
        activateThermalProfile(thermMode);

        if( mActivePerfProfile != -1 ) {
            try {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + mActivePerfProfile + ", deactivating");
                activatePowerMode(mActivePerfProfile,false);
                mActivePerfProfile = -1;
            } catch(Exception e) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Deactivate current perfromance profile failed profile=" + mActivePerfProfile, e);
            }
            mActivePerfProfile = -1;
        }

        int perfMode = MODE_DEVICE_IDLE;
        if( mDeviceIdleMode ) {
            perfMode = MODE_DEVICE_IDLE;
        }
        try {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", idle=" + mDeviceIdleMode + ", screen=" + mScreenMode);
            activatePowerMode(perfMode, true);
        } catch(Exception e) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate idle perfromance profile failed profile=" + perfMode, e);
        }
    }

    protected void activateCurrentProfileLocked(boolean force) {

            if( !mPhoneCall && (!mScreenMode || mDeviceIdleMode) )  {
                activateIdleProfileLocked();
                return;
            }

            AppProfile profile = mCurrentProfile;

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate current profile=" + profile);

            if( mActivePerfProfile != -1 ) {
                try {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + mActivePerfProfile + ", deactivating");
                    activatePowerMode(mActivePerfProfile,false);
                    mActivePerfProfile = -1;
                } catch(Exception e) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Deactivate perfromance profile failed profile=" + mActivePerfProfile, e);
                }
                mActivePerfProfile = -1;
            }

            if( profile == null ) {
                setActiveFrameRateLocked(0,0);
                Actions.sendBrightnessOverrideChanged(setBrightnessOverrideLocked(0));
                setRotation(-1);
                int perfMode = mDefaultPerformanceProfile <= 0 ?  MODE_INTERACTIVE : mDefaultPerformanceProfile;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", mDefaultPerformanceProfile=" + mDefaultPerformanceProfile);
                try {
                    activatePowerMode(perfMode, true);
                } catch(Exception e) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate perfromance profile failed profile=" + perfMode, e);
                }
                //if( mDefaultThermalProfile > 0 )
                int thermMode = mDefaultThermalProfile <= 0 ?  1 : mDefaultThermalProfile;
                activateThermalProfile(thermMode);
            } else {
                setActiveFrameRateLocked(profile.mMinFrameRate,profile.mMaxFrameRate);
                Actions.sendBrightnessOverrideChanged(setBrightnessOverrideLocked(profile.mBrightness));
                setRotation(profile.mRotation-1);
                int perfMode = profile.mPerfProfile <= 0 ? (mDefaultPerformanceProfile <= 0 ?  MODE_INTERACTIVE : mDefaultPerformanceProfile) : profile.mPerfProfile;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", profile.mPerfProfile=" + profile.mPerfProfile);
                try {
                    activatePowerMode(perfMode, true);
                } catch(Exception e) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate perfromance profile failed profile=" + perfMode, e);
                }
                int thermMode = profile.mThermalProfile <= 0 ? (mDefaultThermalProfile <= 0 ?  1 : mDefaultThermalProfile) : profile.mThermalProfile;
                activateThermalProfile(thermMode);   
            }

            updateBypassChargingIfNeededLocked();
            updateStaminaIfNeededLocked();

    }

    protected void activatePowerMode(int mode, boolean enable) {
        if( enable ) {
            mRequestedPowerMode = mode;
            /*if( mPhoneCall ) {
                //mode = MODE_INTERACTIVE;
            } else if( mDeviceIdleMode ) {
                mode = MODE_DEVICE_IDLE;
            } else if( !mScreenMode ) {
                mode = MODE_LOW_POWER;
            }*/
        }
        try {
            mPowerManager.setPowerMode(mode, enable);
            if( enable ) {
                SystemPropertiesSet("baikal.power.perf",Integer.toString(mode));
                mActivePerfProfile = mode;
            } else {
                SystemPropertiesSet("baikal.power.perf","0");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void activateThermalProfile(int profile) {
        /*if( mDeviceIdleMode ) {
            profile = mDefaultThermalProfile;
        } else if( !mScreenMode ) {
            profile = mDefaultThermalProfile;
        }*/
        SystemPropertiesSet("baikal.power.thermal",Integer.toString(profile));
    }

    protected void setTopAppLocked(int uid, String packageName) {

        if( packageName != null )  packageName = packageName.split(":")[0];

        if( isGmsUid(uid) && packageName != null && packageName.startsWith("com.google.android.gms.") ) packageName = "com.google.android.gms";

        if( uid != mTopUid || packageName != mTopPackageName ) {
            mTopUid = uid;
            mTopPackageName = packageName;

            Slog.i(TAG,"topAppChanged uid=" + uid + ", packageName=" + packageName);

            AppProfile profile = mAppSettings.getProfile(packageName);
            if( profile == null ) {
                profile = new AppProfile(mTopPackageName);   
            }

            mCurrentProfile = profile;
            activateCurrentProfileLocked(true);
        }
    }

    protected void setChargerModeLocked(boolean mode) {
        if( mOnCharger != mode ) {
            mOnCharger = mode;
            BaikalConstants.setAodOnChargerEnabled(isAodOnChargerEnabled());
            activateCurrentProfileLocked(true);
        }
    }

    private final BroadcastReceiver mTopAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (AppProfileManager.this) {
                String action = intent.getAction();
                String packageName = (String)intent.getExtra(Actions.EXTRA_PACKAGENAME);
                int uid = (int)intent.getExtra(Actions.EXTRA_UID);
                setTopAppLocked(uid,packageName);
            }
        }
    };

    private final BroadcastReceiver mIdleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (AppProfileManager.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(Actions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"idleChanged mode=" + mode);
                setDeviceIdleModeLocked(mode);
            }
        }
    };

    private final BroadcastReceiver mChargerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (AppProfileManager.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(Actions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"chargerChanged mode=" + mode);
                setChargerModeLocked(mode);
            }
        }
    };


    private final BroadcastReceiver mProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (AppProfileManager.this) {
                String action = intent.getAction();
                String profile = (String)intent.getExtra("profile");
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setProfile profile=" + profile);
                setProfileExternalLocked(profile);
            }
        }
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (AppProfileManager.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(Actions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"screenChanged mode=" + mode);
                setScreenModeLocked(mode);
            }
        }
    };

    private boolean setHwFrameRateLocked(int minFps, int maxFps, boolean override) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setHwFrameRateLocked minFps=" + minFps + ", maxFps=" + maxFps + ", override=" + override);

        if( minFps == 0 ) minFps = mDefaultMinFps;
        if( maxFps == 0 ) maxFps = mDefaultMaxFps;
        
        try {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setHwFrameRateLocked 2 minFps=" + minFps + ", maxFps=" + maxFps + ", override=" + override);
            if( minFps != 0 ) Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE,(float)minFps);
            if( maxFps != 0 ) Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE,(float)maxFps);
        } catch(Exception f) {
            Slog.e(TAG,"setHwFrameRateLocked exception minFps=" + minFps + ", maxFps=" + maxFps, f);
            return false;
        }

        return true;
    }

    private void SystemPropertiesSet(String key, String value) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "SystemProperties.set("+key+","+value+")");
        }
        try {
            SystemProperties.set(key,value);
        }
        catch( Exception e ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.e(TAG, "SystemPropertiesSet: unable to set property "+key+" to "+value);
        }
    }

    private int setBrightnessOverrideLocked(int brightness) {
        int mBrightnessOverride = -1;
        switch( brightness ) {
            case 0:
                mBrightnessOverride = -1;
                break;
            case 10:
                mBrightnessOverride = -2;
                break;
            case 12:
                mBrightnessOverride = -3;
                break;

            case 13:
                mBrightnessOverride = -4;
                break;
            case 14:
                mBrightnessOverride = -5;
                break;

            case 15:
                mBrightnessOverride = -6;
                break;

            case 11:
                mBrightnessOverride = PowerManager.BRIGHTNESS_ON;
                break;
            case 1:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 2)/100; // 3
                break;
            case 2:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 3)/100; // 4
                break;
            case 3:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 4)/100; // 6
                break;
            case 4:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 6)/100; // 8
                break;
            case 5:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 8)/100; // 10
                break;
            case 6:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 15)/100; // 20
                break;
            case 7:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 30)/100; // 35
                break;
            case 8:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 60)/100; // 60
                break;
            case 9:
                mBrightnessOverride = (PowerManager.BRIGHTNESS_ON * 80)/100; // 100
                break;
            default:
                mBrightnessOverride = -1;
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"mBrightnessOverride=" + mBrightnessOverride);
        return mBrightnessOverride;
    }


    private void setRotation(int rotation) {
        setRotationLock(rotation);
    }

    private void setRotationLock(final int rotation) {
        Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.BAIKALOS_DEFAULT_ROTATION,rotation);
    }

    public boolean isPhoneCall() {
        return mPhoneCall;
    }

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Callback invoked when device call state changes.
         * @param state call state
         * @param incomingNumber incoming call phone number. If application does not have
         * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE} permission, an empty
         * string will be passed as an argument.
         *
         * @see TelephonyManager#CALL_STATE_IDLE
         * @see TelephonyManager#CALL_STATE_RINGING
         * @see TelephonyManager#CALL_STATE_OFFHOOK
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"PhoneStateListener: onCallStateChanged(" + state + "," + incomingNumber + ")");
            synchronized (AppProfileManager.this) {
                onCallStateChangedLocked(state,incomingNumber);
            }

        // default implementation empty
        }

        /**
         * Callback invoked when precise device call state changes.
         *
         * @hide
         */
        @Override
        public void onPreciseCallStateChanged(PreciseCallState callState) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"PhoneStateListener: onPreciseCallStateChanged(" + callState + ")");
            synchronized (AppProfileManager.this) {
                onPreciseCallStateChangedLocked(callState);
            }
        }

    };

   
    public int updateProcSchedGroup(AppProfile profile, int processGroup, int schedGroup) {
        int r_processGroup = processGroup;

        int level = 0;

        if( schedGroup == SCHED_GROUP_TOP_APP_BOUND ) {
            AppProfile cur_profile = getCurrentProfile();
            if( cur_profile != null ) {
                level = cur_profile.mPerformanceLevel;
            }
        } else {
            if( profile != null ) level = profile.mPerformanceLevel;
        }

        if( level == 0 ) {
            return processGroup;
        }

        switch( processGroup ) {
            case THREAD_GROUP_DEFAULT:
            case 1: // THREAD_GROUP_FOREGROUND:
                switch(level) {
                    case 1:
                        processGroup = THREAD_GROUP_TOP_APP; 
                        break;
                    case 2:
                        processGroup = THREAD_GROUP_DEFAULT;
                        break;
                    case 3:
                        processGroup = THREAD_GROUP_RESTRICTED;
                        break;
                    case 4:
                        processGroup = THREAD_GROUP_BACKGROUND;
                        break;
                }
                break;
            case THREAD_GROUP_BACKGROUND:
                if( level == 1 ) processGroup = THREAD_GROUP_TOP_APP;
                //else return schedGroup;
                break;
            case THREAD_GROUP_TOP_APP:
                switch(level) {
                    case 1:
                        processGroup = THREAD_GROUP_TOP_APP;
                        break;
                    case 2:
                        processGroup = THREAD_GROUP_DEFAULT;
                        break;
                    case 3:
                        processGroup = THREAD_GROUP_RESTRICTED;
                        break;
                    case 4:
                        processGroup = THREAD_GROUP_BACKGROUND;
                        break;
                }
                break;
            case THREAD_GROUP_RESTRICTED:
                switch(level) {
                    case 1:
                        processGroup = THREAD_GROUP_TOP_APP;
                        break;
                    case 2:
                        processGroup = THREAD_GROUP_RESTRICTED;
                        break;
                    case 3:
                        processGroup = THREAD_GROUP_RESTRICTED;
                        break;
                    case 4:
                        processGroup = THREAD_GROUP_BACKGROUND;
                        break;
                }
                break;

            case THREAD_GROUP_AUDIO_APP:
            case THREAD_GROUP_AUDIO_SYS:
            case THREAD_GROUP_RT_APP:
            default:
                Slog.i(TAG,"updateSchedGroupLocked: Unsupported thread group "  + profile.mPackageName + " " + r_processGroup + " -> " + processGroup);
                break;
        }

        if( processGroup != r_processGroup && BaikalConstants.BAIKAL_DEBUG_OOM ) Slog.i(TAG,"updateSchedGroupLocked: level=" + level + " " + profile.mPackageName + " " + r_processGroup + " -> " + processGroup);
        return processGroup;

    }

    public AppProfile getAppProfile(String packageName) {
        AppProfile profile = mAppSettings != null ? mAppSettings.getProfile(packageName) : null;
        return profile != null ? profile : new AppProfile(packageName);
    }

    public boolean isBlocked(AppProfile profile, String packageName, int uid) {

        if( !mAggressiveMode ) return false;
        if( mAppSettings == null ) return false;
        if( uid < Process.FIRST_APPLICATION_UID ) return false;
        if( profile == null ) {
            if( packageName == null ) {
                packageName = BaikalConstants.getPackageByUid(mContext, uid);
                if( packageName == null ) return true;
            }
            profile = mAppSettings.getProfile(packageName);
        }
        return isBlocked(profile);
    }

    public boolean isBlocked(AppProfile profile) {
        if( profile == null ) return false;
        if( profile.mUid < Process.FIRST_APPLICATION_UID ) return false;
        if( isStamina() && !profile.mStamina ) {
            if( profile.getBackground() < 0 ) {
                return false;
            }
            Slog.w(TAG, "Background execution disabled by baikalos stamina:" + profile.mPackageName);
            return true;
        }
        if( mAwake ) {
            int mode = mExtremeMode ? 0 : 1;
            if( profile.getBackground() > mode ) {
                Slog.w(TAG, "Background execution disabled by baikalos:" + profile.mPackageName);
                return true;
            
            }
        } else {
            if( profile.getBackground() > 0 ) {
                Slog.w(TAG, "Background execution limited by baikalos: " + profile.mPackageName);
                return true;
            }
        }
        return false;
    }

    boolean mAwake;
    public void setAwake(boolean awake) {
        mAwake = awake;
    }

    public boolean isAwake() {
        return mAwake;
    }

    public boolean isKillInBackground() {
        return mKillInBackground;
    }

    public boolean isExtreme() {
        return mExtremeMode;
    }

    public boolean isAggressive() {
        return mAggressiveMode;
    }

    public boolean isStamina() {
        return mStaminaEnabled;
    }

    public boolean isScreenActive() {
        return mScreenMode;
    }

    public boolean isTopAppUid(int uid) {
        return mTopUid == uid;
    }

    public boolean isTopApp(String packageName) {
        return packageName == null ? false : packageName.equals(mTopPackageName);
    }

    public boolean updateBypassCharging(boolean enable) {
        mSmartBypassChargingEnabled = enable;
        return updateBypassChargingLocked();
    }
   
    public boolean updateBypassChargingLocked() {
        if( !BaikalConstants.isKernelCompatible() ) {
            Slog.w(TAG, "Bypass charging disabled. Unsupported kernel!");
            return false;
        }

        boolean bypassEnabled = mSmartBypassChargingEnabled | mCurrentProfile.mBypassCharging | mBypassChargingForced | (mBypassChargingScreenOn && mScreenMode);
        boolean limitedEnabled = !"none".equals(mPowerInputLimitValue) && mLimitedChargingScreenOn && mScreenMode;

        try {
            if( !bypassEnabled && limitedEnabled ) {
                Slog.w(TAG, "Update Limited charging " + limitedEnabled);
                FileUtils.stringToFile(mPowerInputSuspendSysfsNode, mPowerInputLimitValue);
            } else {
                Slog.w(TAG, "Update Bypass/Limited charging " + bypassEnabled);
                FileUtils.stringToFile(mPowerInputSuspendSysfsNode, bypassEnabled ? mPowerInputSuspendValue : mPowerInputResumeValue);
            }
        } catch(Exception e) {
            Slog.w(TAG, "Can't enable bypass charging!", e);
        } 

        return bypassEnabled;
    }

    public boolean isAodOnChargerEnabled() {
        Slog.w(TAG, "isAodOnChargerEnabled=" + (mAodOnCharger & mOnCharger));
        return mAodOnCharger & mOnCharger;
    }

    public boolean isGmsUid(int uid) {
        return uid == mGmsUid;
    }


    public static AppProfile getProfile(String packageName) {
        AppProfile profile = null;
        if( mInstance != null ) profile =  mInstance.getAppProfile(packageName);
        return profile != null ? profile : new AppProfile(packageName);
    }

    public static boolean isAppBlocked(AppProfile profile, String packageName, int uid) {
        if( mInstance != null ) return mInstance.isBlocked(profile, packageName, uid);
        return false;
    }

    public static boolean isAppBlocked(AppProfile profile) {
        if( mInstance != null ) return mInstance.isBlocked(profile);
        return false;
    }
}
