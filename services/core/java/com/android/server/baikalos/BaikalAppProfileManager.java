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


import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

import static com.android.server.am.ProcessList.SCHED_GROUP_BACKGROUND;
import static com.android.server.am.ProcessList.SCHED_GROUP_RESTRICTED;
import static com.android.server.am.ProcessList.SCHED_GROUP_DEFAULT;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP_BOUND;

import static com.android.server.pm.verify.domain.DomainVerificationCollector.RESTRICT_DOMAINS;

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

import static android.os.PowerManagerInternal.BOOST_INTERACTION;
import static android.os.PowerManagerInternal.BOOST_DISPLAY_UPDATE_IMMINENT;

import static android.os.PowerManagerInternal.WAKEFULNESS_ASLEEP;
import static android.os.PowerManagerInternal.WAKEFULNESS_AWAKE;
import static android.os.PowerManagerInternal.WAKEFULNESS_DREAMING;
import static android.os.PowerManagerInternal.WAKEFULNESS_DOZING;

import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

import static android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY;
import static android.location.LocationRequest.QUALITY_HIGH_ACCURACY;
import static android.location.LocationRequest.QUALITY_LOW_POWER;
import static android.location.LocationRequest.PASSIVE_INTERVAL;

import static com.android.server.location.LocationPermissions.PERMISSION_COARSE;
import static com.android.server.location.LocationPermissions.PERMISSION_FINE;
import static com.android.server.location.LocationPermissions.PERMISSION_NONE;


import android.util.Slog;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
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

import android.baikalos.BaikalAppProfile;

import com.android.internal.annotations.GuardedBy;

import com.android.internal.baikalos.BaikalActions;
import com.android.internal.baikalos.BaikalAppProfileSettings;
import com.android.internal.baikalos.BaikalConstants;
import com.android.internal.baikalos.BaikalAppVolumeDB;
import com.android.internal.baikalos.BaikalPowerSaverPolicyConfig;

import com.android.server.audio.AudioService;
import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerConstants;


import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationRequest;
import android.location.LastLocationRequest;
import android.location.util.identity.CallerIdentity;

import com.android.server.location.provider.LocationProviderManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BaikalAppProfileManager implements UiModeManager.OnProjectionStateChangedListener { 

    private static final String TAG = "Baikal.AppProfileManager";

    private final Object mLock = new Object();

    final Context mContext;
    final BaikalAppProfileManagerHandler mHandler;
    final Looper mLooper;

    private static Boolean mBypassChargingAvailable;
    private static String mPowerInputSuspendSysfsNode;
    private static String mPowerInputSuspendValue;
    private static String mPowerInputResumeValue;
    private static String mPowerInputLimitValue;

    private BaikalAppProfileContentObserver mObserver;
    private ContentResolver mResolver;

    private static final int MESSAGE_APP_PROFILE_UPDATE = BaikalConstants.MESSAGE_APP_PROFILE + 100;

    private BaikalAppProfileSettings mAppSettings;
    private IPowerManager mPowerManager;

    private boolean mAutoUpdate = false;
    private boolean mOnCharger = false;
    private boolean mDeviceIdleMode = false;
    private boolean mScreenMode = true;
    private int mWakefulness = WAKEFULNESS_AWAKE;
    private boolean mCarModeEnabled = false;

    private boolean mAodOnCharger = false;
    private boolean mSystemPriority = true;

    private int mTopUid=-1;
    private String mTopPackageName;

    private int mActivePerfProfile = -1;
    private int mActiveThermProfile = -1;
    private int mRequestedPowerMode = -1;

    private static Object mCurrentProfileSync = new Object();
    private static BaikalAppProfile mCurrentProfile = new BaikalAppProfile("system", -1);

    private int mActiveMinFrameRate = -1;
    private int mActiveMaxFrameRate = -1;
                                   
    private float mDefaultMinFps = 0.0f;
    private float mDefaultMaxFps = 0.0f;

    private float mSystemDefaultMinFps = 0.0f;
    private float mSystemDefaultMaxFps = 960.0f;

    private int mDefaultPerformanceProfile;
    private int mDefaultThermalProfile;

    private int mDefaultIdlePerformanceProfile;
    private int mDefaultIdleThermalProfile;

    private boolean mSmartBypassChargingEnabled;
    private boolean mBypassChargingForced;
    private boolean mLimitedChargingForced;
    private boolean mBypassChargingScreenOn;
    private boolean mLimitedChargingScreenOn;

    private boolean mStaminaEnabled;

    private boolean mPerfAvailable = false;
    private boolean mThermAvailable = false;

    private boolean mForcedExtremeMode = false;
    private boolean mAggressiveIdleMode = false;
    private boolean mKillInBackground = false;
    private boolean mAllowDowngrade = false;
    private boolean mAllowSigOverride = false;

    private boolean mInteractionBoost = true;
    private boolean mDisplayBoost = false;
    private boolean mRenderingBoost = true;

    private int mInteractionBoostValue = 0;
    private int mDisplayBoostValue = 0;
    private int mRenderingBoostValue = 0;

    private boolean mBlockIfBusy = false;

    private int mBrightnessCurve = 0;

    private boolean mPhoneCall = false;
    private boolean mAudioPlaying = false;

    private boolean mForcedUpdate = false;

    private boolean mHideHMS = false;
    private boolean mHideGMS = false;
    private boolean mHide3P = false;

    private boolean mBlockContacts = false;
    private boolean mBlockCalllog = false;
    private boolean mBlockCalendar = false;
    private boolean mBlockMedia = false;

    private boolean mBlockSms = false;
    private boolean mBlockNotification = false;

    private int mGmsUid = -1;
    private int mAaUid = -1;
    private int mSystemuiUid = -1;

    TelephonyManager mTelephonyManager;

    static BaikalAppProfileManager mInstance;
    static BaikalDebugManager mDebugManager;
    static BaikalBoostManager mBoostManager;
    static BaikalPowerSaveManager mBaikalPowerSaveManager;
    static BaikalAppVolumeDB mAppVolumeDB;
    static AudioService mAudioService = null;
    static BaikalAttestationService mAttestation;

    private PowerManagerInternal mPowerManagerInternal;
    private ActivityManagerConstants mAmConstants;

    public static void setAudioService(AudioService service) {
        mAudioService = service;
    }

    public static BaikalAppProfile getCurrentProfile() {
        return mCurrentProfile;
    }

    public static BaikalAppProfileManager getInstance() {
        return mInstance;
    }

    public static BaikalAppProfileManager getInstance(Looper looper, Context context, ActivityManagerConstants amConstants) {
        if( mInstance == null ) {
            mInstance = new BaikalAppProfileManager(looper,context,amConstants);
        }
        return mInstance;
    }

    final class BaikalAppProfileManagerHandler extends Handler {
        BaikalAppProfileManagerHandler(Looper looper) {
            super(looper);
    
        }

        @Override public void handleMessage(Message msg) {
            onMessage(msg);
        }
    }

    final class BaikalAppProfileContentObserver extends ContentObserver {

        BaikalAppProfileContentObserver(Handler handler) {
            super(handler);

            try {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_PROFILE_MANAGER_REFRESH),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_FORCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_LIMITED_CHARGE_FORCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_PERFORMANCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_THERMAL),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_IDLE_PERFORMANCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_IDLE_THERMAL),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_STAMINA_ENABLED),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_AGGRESSIVE_IDLE),
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

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BRIGHTNESS_CURVE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_ALLOW_DOWNGRADE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_ALLOW_SIG_OVERRIDE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SUPER_SAVER),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SUPER_SAVER_DRAW),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_AUTO_LIMIT),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_GMS_SPOOFER_UPDATE),
                    false, this);

                mResolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BAIKALOS_BOOST_INTERACTION), false, this);

                mResolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BAIKALOS_BOOST_DISPLAY_UPDATE_IMMINENT), false, this);

                mResolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BAIKALOS_BOOST_RENDERING), false, this);

                mResolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BAIKALOS_BLOCK_IF_BUSY), false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_HIDE_GMS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_HIDE_HMS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_HIDE_3P),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_CONTACTS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_CALENDAR),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_CALLLOG),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_MEDIA),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_SMS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_NOTIFICATION),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BLOCK_NOTIFICATION),
                    false, this);

            } catch( Exception e ) {
            }
        
            synchronized(this) {
                updateConstantsLocked();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalAppProfileManager.this) {
                updateConstantsLocked();
            }
        }
    }

    private BaikalAppProfileManager(Looper looper, Context context, ActivityManagerConstants amConstants) {
        mContext = context;
        mLooper = looper;
        mHandler = new BaikalAppProfileManagerHandler(mLooper);
        mAmConstants = amConstants;
        mAppVolumeDB = BaikalAppVolumeDB.getInstance(mContext);

        final Resources resources = mContext.getResources();


        mBypassChargingAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_bypassChargingAvailable);
        mPowerInputSuspendSysfsNode = resources.getString(
                com.android.internal.R.string.config_bypassChargingSysfsNode);
        mPowerInputSuspendValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingSuspendValue);
        mPowerInputResumeValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingResumeValue);
        mPowerInputLimitValue = resources.getString(
                com.android.internal.R.string.config_bypassChargingLimitValue);

        mAttestation = new BaikalAttestationService(context);
    }

    public boolean onMessage(Message msg) {
    	switch(msg.what) {
    	    case MESSAGE_APP_PROFILE_UPDATE:
                if( BaikalConstants.BAIKAL_DEBUG_POWERHAL ) Slog.i(TAG, "MESSAGE_APP_PROFILE_UPDATE cancel all boost requests");
    		    return true;
    	}
    	return false;
    }

    public void init_debug() {
        Slog.i(TAG,"init_debug()");                
        mDebugManager = BaikalDebugManager.getInstance(mLooper,mContext); 
        mDebugManager.initialize();
    }

    public void initialize() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"initialize()");                
        synchronized(this) {

            mInstance = this;

            mAppSettings = BaikalAppProfileSettings.getInstance(); 

            mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
            mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);

            IntentFilter topAppFilter = new IntentFilter();
            topAppFilter.addAction(BaikalActions.ACTION_TOP_APP_CHANGED);
            mContext.registerReceiver(mTopAppReceiver, topAppFilter);

            IntentFilter idleFilter = new IntentFilter();
            idleFilter.addAction(BaikalActions.ACTION_IDLE_MODE_CHANGED);
            mContext.registerReceiver(mIdleReceiver, idleFilter);

            IntentFilter chargerFilter = new IntentFilter();
            chargerFilter.addAction(BaikalActions.ACTION_CHARGER_MODE_CHANGED);
            mContext.registerReceiver(mChargerReceiver, chargerFilter);

            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(BaikalActions.ACTION_SCREEN_MODE_CHANGED);
            mContext.registerReceiver(mScreenReceiver, screenFilter);

            IntentFilter profileFilter = new IntentFilter();
            profileFilter.addAction(BaikalActions.ACTION_SET_PROFILE);
            mContext.registerReceiver(mProfileReceiver, profileFilter);

            IntentFilter wakefulnessFilter = new IntentFilter();
            wakefulnessFilter.addAction(BaikalActions.ACTION_WAKEFULNESS_CHANGED);
            mContext.registerReceiver(mWakefulnessReceiver, wakefulnessFilter);

            mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            mTelephonyManager.listen(mPhoneStateListener, 0xFFFFFFF);

            Display.Mode mode = mContext.getDisplay().getMode();
            Display.Mode[] modes = mContext.getDisplay().getSupportedModes();

            float minFps = 960.0f, maxFps = 0.0f;

            for (Display.Mode m : modes) {
                if (m.getPhysicalWidth() == mode.getPhysicalWidth() &&
                        m.getPhysicalHeight() == mode.getPhysicalHeight()) {
                    if( m.getRefreshRate() > maxFps ) maxFps = m.getRefreshRate();
                    if( m.getRefreshRate() < minFps ) minFps = m.getRefreshRate();
                }
            }

            
            mSystemDefaultMinFps = minFps;
            mSystemDefaultMaxFps = maxFps;


            mResolver = mContext.getContentResolver();

            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_ALLOW_SIG_OVERRIDE, 0);
            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_STAMINA_ENABLED, 0);
            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_EXTREME_IDLE, 0);
            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0);
            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_LIMITED_CHARGE_FORCE, 0);

            mAttestation.initialize();
            mGmsUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.google.android.gms"));
            mAaUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.google.android.projection.gearhead"));
            mSystemuiUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.android.systemui"));

            mObserver = new BaikalAppProfileContentObserver(mHandler);

            mBoostManager = BaikalBoostManager.getInstance(mLooper,mContext); 
            mBoostManager.initialize();

            mBaikalPowerSaveManager = BaikalPowerSaveManager.getInstance(mLooper,mContext,mAmConstants); 
            mBaikalPowerSaveManager.initialize();

            mAppVolumeDB.loadVolumes(true);

            mContext.getSystemService(UiModeManager.class).addOnProjectionStateChangedListener(
                    UiModeManager.PROJECTION_TYPE_AUTOMOTIVE, mContext.getMainExecutor(), this);

            mCarModeEnabled = getSystemCarModeOrProjectionState();
        }
    }

    @GuardedBy("mLock")
    protected void updateConstantsLocked() {
        
        boolean changed = false;

        boolean refresh = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_PROFILE_MANAGER_REFRESH, 0) != 0;
        if( refresh ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings forced reload request");
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_PROFILE_MANAGER_REFRESH, 0);
            mForcedUpdate = true;
            return;
        }

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings update mForcedUpdate=" + mForcedUpdate);

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

        int defaultIdlePerformanceProfile = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEFAULT_IDLE_PERFORMANCE, -1);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading idle performance profile=" + defaultIdlePerformanceProfile);
        if( mDefaultIdlePerformanceProfile != defaultIdlePerformanceProfile ) {
            mDefaultIdlePerformanceProfile = defaultIdlePerformanceProfile;
            changed = true;
        }

        int defaultIdleThermalProfile = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEFAULT_IDLE_THERMAL, -1);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading idle thermal profile=" + defaultIdleThermalProfile);
        if( mDefaultIdleThermalProfile != defaultIdleThermalProfile ) {
            mDefaultIdleThermalProfile = defaultIdleThermalProfile;
            changed = true;
        }

        boolean bypassForced = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0) != 0;
        if( mBypassChargingForced != bypassForced ) {
            mBypassChargingForced = bypassForced;
            changed = true;
        }

        boolean limitedForced = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_LIMITED_CHARGE_FORCE, 0) != 0;
        if( mLimitedChargingForced != limitedForced ) {
            mLimitedChargingForced = limitedForced;
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
            BaikalAppProfile.setStaminaActive(mStaminaEnabled);
            changed = true;
        }

        float defaultMinFps = Settings.System.getFloat(mContext.getContentResolver(), Settings.System.BAIKALOS_DEFAULT_MINFPS, mSystemDefaultMinFps);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading BAIKALOS_DEFAULT_MINFPS=" + defaultMinFps);
        if( Math.abs(mDefaultMinFps - defaultMinFps) > 0.001 ) {
            mDefaultMinFps = defaultMinFps;
            changed = true;
        }

        float defaultMaxFps = Settings.System.getFloat(mContext.getContentResolver(), Settings.System.BAIKALOS_DEFAULT_MAXFPS, mSystemDefaultMaxFps);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading BAIKALOS_DEFAULT_MAXFPS=" + defaultMaxFps);
        if( Math.abs(mDefaultMaxFps - defaultMaxFps) > 0.001 ) {
            mDefaultMaxFps = defaultMaxFps;
            changed = true;
        }

        boolean aodOnCharger = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_AOD_ON_CHARGER, 0) != 0;
        if( mAodOnCharger != aodOnCharger ) {
            mAodOnCharger = aodOnCharger;
            BaikalConstants.setAodOnChargerEnabled(isAodOnChargerEnabled());
        }

        mBrightnessCurve = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BRIGHTNESS_CURVE, 0);

        mAllowDowngrade = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_ALLOW_DOWNGRADE, 0) != 0;

        mAllowSigOverride = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_ALLOW_SIG_OVERRIDE, 0) != 0;

        boolean superSaver = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_SUPER_SAVER, 0) != 0;
        BaikalAppProfileSettings.setSuperSaver(superSaver);

        boolean superSaverForDraw = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_SUPER_SAVER_DRAW, 0) != 0;
        BaikalAppProfileSettings.setSuperSaverActiveForDraw(superSaverForDraw);

        boolean autoLimit = false; //Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_AUTO_LIMIT, 0) != 0;
        changed |= BaikalAppProfile.setAutoLimit(autoLimit);


        boolean autoUpdate = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_GMS_SPOOFER_UPDATE, 0) != 0;
        if( mAttestation != null && autoUpdate != mAutoUpdate ) {
            mAutoUpdate = autoUpdate;
            mAttestation.scheduleIfNeeded(autoUpdate);
        }

        boolean interactionBoost = Settings.Global.getInt(mResolver,Settings.Global.BAIKALOS_BOOST_INTERACTION, 1) == 1;
        if( interactionBoost != mInteractionBoost ) {
            mInteractionBoost = interactionBoost;
            changed = true;
        }

        boolean displayBoost = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BOOST_DISPLAY_UPDATE_IMMINENT, 0) == 1;
        if( displayBoost != mDisplayBoost ) {
            mDisplayBoost = displayBoost;
            changed = true;
        }

        boolean renderingBoost = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BOOST_RENDERING, 1) == 1;
        if( renderingBoost != mRenderingBoost ) {
            mRenderingBoost = renderingBoost;
            changed = true;
        }

        mBlockIfBusy = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_IF_BUSY, 0) == 1; 

        mHideHMS = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_HIDE_HMS, 0) == 1; 
        mHideGMS = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_HIDE_GMS, 0) == 1; 
        mHide3P = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_HIDE_3P, 0) == 1; 

        mBlockContacts = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_CONTACTS, 0) == 1; 
        mBlockCalllog = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_CALLLOG, 0) == 1; 
        mBlockCalendar = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_CALENDAR, 0) == 1; 
        mBlockMedia = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_MEDIA, 0) == 1; 
        mBlockSms = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_SMS, 0) == 1; 
        mBlockNotification = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_NOTIFICATION, 0) == 1; 

        if( changed || mForcedUpdate ) {
            activateCurrentProfileLocked(mForcedUpdate,false);
            mForcedUpdate = false;
        }
    }

    protected void updateBypassChargingIfNeededLocked() {
        updateBypassChargingLocked();
    }

    protected void updateStaminaIfNeededLocked() {
        if( mBaikalPowerSaveManager != null ) mBaikalPowerSaveManager.setStamina(mStaminaEnabled);
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

            if( mBaikalPowerSaveManager != null ) mBaikalPowerSaveManager.setDeviceIdle(mode);

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

    protected void setCarModeLocked(boolean mode) {
        if( mCarModeEnabled != mode ) {
            mCarModeEnabled = mode;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after car mode changed mode=" + mScreenMode);
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

    protected void setWakefulnessLocked(int wakefulness) {
        if( mWakefulness != wakefulness ) {
            mWakefulness = wakefulness;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after wakefulness changed mode=" + mWakefulness);
            mHandler.postDelayed( new Runnable() {
                @Override
                public void run() { 
                    synchronized(this) {
                        //SystemProperties.set("baikal.screen_mode", mScreenMode ? "1" : "0");
                        restoreProfileForCurrentModeLocked(true);
                    }
                }
            }, 100);

            if( mBaikalPowerSaveManager != null ) mBaikalPowerSaveManager.setScreenMode(mWakefulness == WAKEFULNESS_AWAKE);
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
        activateCurrentProfileLocked(force,false);
    }

    protected void activateIdleProfileLocked(boolean force) {

        int thermMode = mDefaultIdleThermalProfile <= 0 ?  1 : mDefaultIdleThermalProfile;
        if( force || thermMode != mActiveThermProfile ) activateThermalProfile(thermMode);

        int perfMode = mDefaultIdlePerformanceProfile <= 0 ? MODE_DEVICE_IDLE : mDefaultIdlePerformanceProfile;

        try {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", idle=" + mDeviceIdleMode + ", screen=" + mScreenMode);
            if( force || perfMode != mActivePerfProfile ) activatePowerMode(perfMode, true);
        } catch(Exception e) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate idle perfromance profile failed profile=" + perfMode, e);
        }
    }

    protected void activateBalancedProfileLocked(boolean force) {

        int thermMode = mDefaultIdleThermalProfile <= 0 ?  1 : mDefaultIdleThermalProfile;
        if( force || thermMode != mActiveThermProfile ) activateThermalProfile(thermMode);

        int perfMode = MODE_INTERACTIVE;

        try {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", idle=" + mDeviceIdleMode + ", screen=" + mScreenMode);
            if( force || perfMode != mActivePerfProfile ) activatePowerMode(perfMode, true);
        } catch(Exception e) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate balanced perfromance profile failed profile=" + perfMode, e);
        }
    }

    @GuardedBy("mLock")
    protected void activateCurrentProfileLocked(boolean force, boolean wakeup) {

        //if( !mPhoneCall && (!mScreenMode || mDeviceIdleMode || mWakefulness == WAKEFULNESS_ASLEEP || mWakefulness == WAKEFULNESS_DOZING ) )  {
        if( mCarModeEnabled || mPhoneCall) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate balanced profile for phone or car mode " + 
                                                                      "mPhoneCall=" + mPhoneCall +
                                                                      ", mCarModeEnabled=" + mCarModeEnabled +
                                                                      ", mScreenMode=" + mScreenMode +
                                                                      ", mDeviceIdleMode=" + mDeviceIdleMode +
                                                                      ", mWakefulness=" + mWakefulness);

            activateBalancedProfileLocked(force);
            updateBoostValuesIfNeededLocked();
            updateSystemPriorityLocked();
            updateBypassChargingIfNeededLocked();
            updateStaminaIfNeededLocked();
            return;
        }
        else if( !mCarModeEnabled && !mPhoneCall && /* !mScreenMode && !isAudioPlaying() &&*/ !wakeup && mWakefulness != WAKEFULNESS_AWAKE  /*&& !mScreenMode*/)  {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate idle profile " + 
                                                                      "mPhoneCall=" + mPhoneCall +
                                                                      ", mCarModeEnabled=" + mCarModeEnabled +
                                                                      ", mScreenMode=" + mScreenMode +
                                                                      ", mDeviceIdleMode=" + mDeviceIdleMode +
                                                                      ", mWakefulness=" + mWakefulness);
            activateIdleProfileLocked(force);
            updateBoostValuesIfNeededLocked();
            updateSystemPriorityLocked();
            updateBypassChargingIfNeededLocked();
            updateStaminaIfNeededLocked();
            return;
        } /* else if( mPhoneCall || mScreenMode ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate balanced profile " + 
                                                                      "mPhoneCall=" + mPhoneCall +
                                                                      ", mScreenMode=" + mScreenMode +
                                                                      ", mDeviceIdleMode=" + mDeviceIdleMode +
                                                                      ", mWakefulness=" + mWakefulness);
            //activateBalancedProfileLocked(force);
            //return;
        } */

        BaikalAppProfile profile = mCurrentProfile;

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate current profile=" + profile);

        if( profile == null ) {
            BaikalAppProfileSettings.setReaderMode(0);
            setActiveFrameRateLocked(0,0);
            BaikalActions.sendBrightnessOverrideChanged(setBrightnessOverrideLocked(0));
            setRotation(-1);
            int perfMode = mDefaultPerformanceProfile <= 0 ?  MODE_INTERACTIVE : mDefaultPerformanceProfile;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", mDefaultPerformanceProfile=" + mDefaultPerformanceProfile);
            try {
    		    if( force || perfMode != mActivePerfProfile ) activatePowerMode(perfMode, true);
            } catch(Exception e) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate perfromance profile failed profile=" + perfMode, e);
            }
            int thermMode = mDefaultThermalProfile <= 0 ?  1 : mDefaultThermalProfile;
	        if( force || thermMode != mActiveThermProfile ) activateThermalProfile(thermMode);
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BLOCK_OVERLAYS, 0);
        } else {
            BaikalAppProfileSettings.setReaderMode(profile.mReader);
            setActiveFrameRateLocked(profile.mMinFrameRate,profile.mMaxFrameRate);
            BaikalActions.sendBrightnessOverrideChanged(setBrightnessOverrideLocked(profile.mBrightness));
            setRotation(profile.mRotation-1);
            int perfMode = profile.mPerfProfile <= 0 ? (mDefaultPerformanceProfile <= 0 ?  MODE_INTERACTIVE : mDefaultPerformanceProfile) : profile.mPerfProfile;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + perfMode + ", profile.mPerfProfile=" + profile.mPerfProfile);
            try {
                if( force || perfMode != mActivePerfProfile ) activatePowerMode(perfMode, true);
            } catch(Exception e) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate perfromance profile failed profile=" + perfMode, e);
            }
            int thermMode = profile.mThermalProfile <= 0 ? (mDefaultThermalProfile <= 0 ?  1 : mDefaultThermalProfile) : profile.mThermalProfile;
	        if( force || thermMode != mActiveThermProfile ) activateThermalProfile(thermMode);
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BLOCK_OVERLAYS, profile.mBlockOverlays ? 1:0 );
        }

        updateBoostValuesIfNeededLocked();
        updateSystemPriorityLocked();
        updateBypassChargingIfNeededLocked();
        updateStaminaIfNeededLocked();
    }


    private void updateSystemPriorityLocked() {
        BaikalPowerSaverPolicyConfig policy = BaikalPowerSaveManager.getCurrentPolicy();
        if( mSystemPriority != policy.systemPriority ) {
            mSystemPriority = policy.systemPriority;
            if( mSystemPriority == false ) {
                Slog.i(TAG,"mSystemPriority=" + mSystemPriority, new Throwable());
            }
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"mSystemPriority=" + mSystemPriority);
    }

    private void updateBoostValuesIfNeededLocked() {
        boolean changed = false; 

        boolean interactionBoost = false;
        boolean displayBoost = false;
        boolean renderingBoost = false;

        if( mWakefulness == WAKEFULNESS_AWAKE ) {
            if( mDisplayBoost ) displayBoost = true;
            if( mInteractionBoost ) {
                switch(mCurrentProfile.mBoostControl) {

                    case 1:
                        interactionBoost = true;
                        renderingBoost = true;    
                        break;

                    case 2:
                        interactionBoost = true;
                        renderingBoost = false;    
                        break;

                    case 3:
                        interactionBoost = false;
                        renderingBoost = false;    
                        break;

                    case 0:
                    default:
                        BaikalPowerSaverPolicyConfig policy = BaikalPowerSaveManager.getCurrentPolicy();
                        if( policy.enableInteractionBoost ) interactionBoost = true;
                        if( policy.enableRenderingBoost ) renderingBoost = true;    
                        break;
                }
            }
        }

        int interactionBoostValue = interactionBoost ? -1001 : -1000;
        int displayBoostValue = displayBoost ? -1001 : -1000;
        int renderingBoostValue = renderingBoost ? -1003 : -1002;

        try {
            if( interactionBoostValue != mInteractionBoostValue ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Interaction boost=" + interactionBoost);
                mPowerManager.setPowerBoost(BOOST_INTERACTION,interactionBoostValue);
                mInteractionBoostValue = interactionBoostValue;
            }

            if( renderingBoostValue != mRenderingBoostValue ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Rendering boost=" + renderingBoost);
                mPowerManager.setPowerBoost(BOOST_INTERACTION,renderingBoostValue);
                mRenderingBoostValue = renderingBoostValue;
            }

            if( displayBoostValue != mDisplayBoostValue ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Display boost=" + displayBoost);
                mPowerManager.setPowerBoost(BOOST_DISPLAY_UPDATE_IMMINENT,displayBoostValue);
                mDisplayBoostValue = displayBoostValue;
            }
        } catch(Exception e) {
        }
    }

    public void wakeUp() {
        activateCurrentProfileLocked(true,true);
        
        try {
            mPowerManager.setPowerBoost(BOOST_INTERACTION,4000);
            //mPowerManager.setPowerMode(MODE_LAUNCH,true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void activatePowerMode(int mode, boolean enable) {

	    if( enable ) {
            if( mActivePerfProfile != -1 ) {
                try {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + mActivePerfProfile + ", deactivating previous");
                    //activatePowerMode(mActivePerfProfile,false);
                    mPowerManager.setPowerMode(mActivePerfProfile, false);
                    mActivePerfProfile = -1;
                } catch(Exception e) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Deactivate perfromance profile failed profile=" + mActivePerfProfile, e);
                }
                mActivePerfProfile = -1;
            }
	    } else {
	        if( mActivePerfProfile == -1 ) return;
    	}

        try {
            mPowerManager.setPowerMode(mode, enable);
            if( enable ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + mode + ", activating");
                SystemPropertiesSet("baikal.power.perf",Integer.toString(mode));
                mActivePerfProfile = mode;
            } else {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode profile=" + mode + ", deactivating");
                SystemPropertiesSet("baikal.power.perf","-1");
		        mActivePerfProfile = -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if( enable ) {
            if( mode == MODE_SUSTAINED_PERFORMANCE || mode == MODE_FIXED_PERFORMANCE ) {
                BaikalAppProfileSettings.setSuperSaverOverride(true);
            } else {
                BaikalAppProfileSettings.setSuperSaverOverride(false);
            }
        }
    }

    protected void activateThermalProfile(int profile) {
        if( profile != SystemProperties.getInt("baikal.power.thermal",-99999) ) {
            SystemPropertiesSet("baikal.power.thermal",Integer.toString(profile));
        }
    	mActiveThermProfile = profile;
    }

    protected void setTopAppLocked(int uid, String packageName) {

        if( mAppSettings == null ) return;

        if( packageName != null )  packageName = packageName.split(":")[0];

        if( isGmsUid(uid) && packageName != null && packageName.startsWith("com.google.android.gms.") ) packageName = "com.google.android.gms";

        if( uid != mTopUid || packageName != mTopPackageName ) {
            mTopUid = uid;
            mTopPackageName = packageName;

            Slog.i(TAG,"topAppChanged uid=" + uid + ", packageName=" + packageName);

            BaikalAppProfile profile = mAppSettings.getBaikalProfile(packageName);
            if( profile == null ) {
                profile = new BaikalAppProfile(mTopPackageName, uid);   
            }

            BaikalAppProfile.setTopAppProfile(profile,packageName,uid);

            mCurrentProfile = profile;
            activateCurrentProfileLocked(true,false);
        }
    }

    protected void setChargerModeLocked(boolean mode) {
        if( mOnCharger != mode ) {
            mOnCharger = mode;
            BaikalConstants.setAodOnChargerEnabled(isAodOnChargerEnabled());
            activateCurrentProfileLocked(true,false);
            if( mBaikalPowerSaveManager != null ) mBaikalPowerSaveManager.setIsPowered(mode);
        }
    }

    private final BroadcastReceiver mTopAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalAppProfileManager.this) {
                String action = intent.getAction();
                String packageName = (String)intent.getExtra(BaikalActions.EXTRA_PACKAGENAME);
                int uid = (int)intent.getExtra(BaikalActions.EXTRA_UID);
                setTopAppLocked(uid,packageName);
            }
        }
    };

    private final BroadcastReceiver mIdleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalAppProfileManager.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(BaikalActions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"idleChanged mode=" + mode);
                setDeviceIdleModeLocked(mode);
            }
        }
    };

    private final BroadcastReceiver mChargerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalAppProfileManager.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(BaikalActions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"chargerChanged mode=" + mode);
                setChargerModeLocked(mode);
            }
        }
    };


    private final BroadcastReceiver mProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalAppProfileManager.this) {
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
            synchronized (BaikalAppProfileManager.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(BaikalActions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"screenChanged mode=" + mode);
                setScreenModeLocked(mode);
            }
        }
    };

    private final BroadcastReceiver mWakefulnessReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalAppProfileManager.this) {
                String action = intent.getAction();
                int wakefulness = (int)intent.getExtra(BaikalActions.EXTRA_INT_WAKEFULNESS);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"wakefulness mode=" + wakefulness);
                setWakefulnessLocked(wakefulness);
            }
        }
    };

    private boolean setHwFrameRateLocked(int minFps, int maxFps, boolean override) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setHwFrameRateLocked minFps=" + minFps + ", maxFps=" + maxFps + ", override=" + override);

        if( minFps == 0 ) minFps = (int)mDefaultMinFps;
        if( maxFps == 0 ) maxFps = (int)mDefaultMaxFps;
        
        try {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setHwFrameRateLocked 2 minFps=" + minFps + ", maxFps=" + maxFps + ", override=" + override);

            float oldmin = 0.0F; 
            float oldmax = 960.0F; 

            try {
                oldmin = Settings.System.getFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE);
            } catch(Exception me) {}

            try {
                oldmax = Settings.System.getFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE);
            } catch(Exception me) {}

            if( maxFps > 0 && minFps > maxFps ) minFps = maxFps;

            if( minFps != 0 ) {
                //if( minFps > oldmax ) Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE,(float)minFps);
                //if( minFps != oldmin ) 
                Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE,(float)minFps);
            }
            if( maxFps != 0 ) {
                //if( maxFps < oldmin ) Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE,(float)maxFps);
                //if( maxFps != oldmax ) 
                Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE,(float)maxFps);
            }
        } catch(Exception f) {
            Slog.e(TAG,"setHwFrameRateLocked exception minFps=" + minFps + ", maxFps=" + maxFps, f);
            return false;
        }

        return true;
    }

    private void SystemPropertiesSet(String key, String value) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "SystemProperties.set("+key+","+value+")");
        try {
            SystemProperties.set(key,value);
        }
        catch( Exception e ) {
            Slog.e(TAG, "SystemPropertiesSet: unable to set property "+key+" to "+value);
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

        int autoRotationMode = 0;

        final int currentAutoRotationMode = Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION,
                    0, UserHandle.USER_CURRENT);

        if( rotation == -1 ) {
            autoRotationMode = Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION_DEFAULT,
                    0, UserHandle.USER_CURRENT);
        } else if ( rotation == 0 ) {
            autoRotationMode = 1;
        } else {
            autoRotationMode = 0;
        }

        if( currentAutoRotationMode != autoRotationMode ) { 
            Settings.System.putIntForUser(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 
                    autoRotationMode, UserHandle.USER_CURRENT);
        }

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
            synchronized (BaikalAppProfileManager.this) {
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
            synchronized (BaikalAppProfileManager.this) {
                onPreciseCallStateChangedLocked(callState);
            }
        }

    };

   
    public int updateProcSchedGroup(int curProcState, int curAdj, BaikalAppProfile profile, int processGroup, int schedGroup) {
        int r_processGroup = processGroup;

        int level = 0;

        BaikalAppProfile cur_profile = getCurrentProfile();
        /*if( schedGroup == SCHED_GROUP_TOP_APP_BOUND ) {
            if( cur_profile != null ) {
                level = cur_profile.mPerformanceLevel;
                if( BaikalConstants.BAIKAL_DEBUG_OOM ) Slog.v(TAG,"updateSchedGroupLocked: override profile from " + cur_profile.mPackageName + " level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup);
            }
        } else {*/
            if( profile != null ) level = profile.mPerformanceLevel;
        /*}*/

        if( mSystemPriority ) {
            if( BaikalConstants.BAIKAL_DEBUG_OOM_RAW ) Slog.v(TAG,"updateSchedGroupLocked: force system level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup   + " state " + curProcState + " adj " + curAdj);
            if( schedGroup != SCHED_GROUP_TOP_APP ) {
                if( curProcState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE ) {
                    processGroup = THREAD_GROUP_TOP_APP;
                    if( BaikalConstants.BAIKAL_DEBUG_OOM || BaikalConstants.BAIKAL_DEBUG_OOM_RAW ) Slog.v(TAG,"updateSchedGroupLocked: force system on top level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup   + " state " + curProcState + " adj " + curAdj);
                    return processGroup;
                }
            }
        } else {
            if( BaikalConstants.BAIKAL_DEBUG_OOM_RAW ) Slog.v(TAG,"updateSchedGroupLocked: not force system level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup   + " state " + curProcState + " adj " + curAdj);
        }

        if( cur_profile.mHeavyCPU && schedGroup != SCHED_GROUP_TOP_APP_BOUND && schedGroup != SCHED_GROUP_TOP_APP ) {
            if( processGroup != THREAD_GROUP_RESTRICTED && processGroup != THREAD_GROUP_BACKGROUND ) {
                processGroup = THREAD_GROUP_RESTRICTED;
                if( (processGroup != r_processGroup &&  BaikalConstants.BAIKAL_DEBUG_OOM) || 
                    BaikalConstants.BAIKAL_DEBUG_OOM_RAW ) Slog.d(TAG,"updateSchedGroupLocked: heavy app active, level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup);
                return processGroup;
            } 
        }

        if( level == 0 ) {
            if( BaikalConstants.BAIKAL_DEBUG_OOM_RAW ) Slog.v(TAG,"updateSchedGroupLocked: level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup  + " state " + curProcState + " adj " + curAdj);
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
                Slog.w(TAG,"updateSchedGroupLocked: Unsupported thread group "  + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup);
                break;
        }

        if( (processGroup != r_processGroup && BaikalConstants.BAIKAL_DEBUG_OOM) ||
             BaikalConstants.BAIKAL_DEBUG_OOM_RAW ) Slog.v(TAG,"updateSchedGroupLocked: level=" + level + " " + profile.mPackageName + " " + schedGroup + " " + r_processGroup + " -> " + processGroup + " state " + curProcState + " adj " + curAdj);
        return processGroup;

    }

    public void onAudioModeChanged(boolean playing) {

        final int AUDIO_LAUNCH = 3;
        final int AUDIO_STREAMING_LOW_LATENCY = 10;

        if( playing ) {
            try {
                mPowerManager.setPowerBoost(AUDIO_LAUNCH,3000);
            } catch(Exception e) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate audio boost failed", e);
            }
        }

        if( mAudioPlaying != playing ) {
            mAudioPlaying = playing;
            //activateCurrentProfileLocked(false,false);

            try {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setPowerMode audio=" + mAudioPlaying);
                mPowerManager.setPowerMode(AUDIO_STREAMING_LOW_LATENCY, mAudioPlaying);
            } catch(Exception e) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate audio profile failed", e);
            }
        }
    }

    public boolean isAudioPlaying() {
        return mAudioPlaying;
    }


    public BaikalAppProfile getBaikalAppProfile(String packageName, int uid) {
        if( mAppSettings == null ) return new BaikalAppProfile(packageName,uid);

        BaikalAppProfile profile = null;

        if( packageName == null ) {
            if( uid == -1 ) {
                Slog.d(TAG,"getBaikalAppProfile(null,-1) : profile not found",new Throwable());
                return new BaikalAppProfile(packageName,uid);
            }
            profile = mAppSettings.getBaikalProfile(uid);
        } else {
            profile = mAppSettings.getBaikalProfile(packageName);
        }

        if( profile != null ) return profile;
        if( uid == -1 ) uid = BaikalConstants.getUidByPackage(mContext, packageName);
        if( uid == -1 ) {
            uid = 99999;
        }
        return new BaikalAppProfile(packageName,uid);
    }

    public BaikalAppProfile getBaikalAppProfile(int uid) {
        BaikalAppProfile profile = mAppSettings != null ? mAppSettings.getBaikalProfile(uid) : null;
        return profile;
    }

    public boolean isBaikalAppRestricted(int uid, String packageName) {
        if( mAppSettings == null ) return false;
        if( UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID ) return false;
        if( packageName == null ) {
            packageName = BaikalConstants.getPackageByUid(mContext, uid);
            if( packageName == null ) return false;
        }
        BaikalAppProfile profile = mAppSettings.getBaikalProfile(packageName);
        if( profile == null ) return false;
        if( isStamina() && !profile.getStamina() ) {
            if( profile.getBackgroundMode() >= 0 ) {
                if( profile.mDebug || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "Background execution restricted by baikalos stamina ("
                    + profile.getBackgroundMode() + "," 
                    + profile.getStamina() + ") : " 
                    + profile.mPackageName);
                return true;
            }
        }
        if( mAwake ) {
            if( profile.getBackgroundMode() > 1 ) {
                if( profile.mDebug || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "Background execution restricted by baikalos (" 
                    + profile.getBackgroundMode() + "," 
                    + profile.getStamina() + ") : " 
                    + profile.mPackageName);
                return true;
            
            }
        } else {
            if( profile.getBackgroundMode() > 0 ) {
                if( profile.mDebug || BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "Background execution restricted by baikalos ("
                    + profile.getBackgroundMode() + "," 
                    + profile.getStamina() + ") : " 
                    + profile.mPackageName);
                return true;
            }
        }
        return false;
    }

    public boolean isBaikalBlocked(BaikalAppProfile profile, String packageName, int uid) {
        boolean result = isBaikalBlockedInternal(profile, packageName, uid);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) {
            Slog.w(TAG, "isBlocked: " + result + ", pn=" + (profile == null ? "null" : profile.toString())  + ", pkg=" + packageName + "/" + uid);
        }
        return result;
    }

    public boolean isBaikalBlockedInternal(BaikalAppProfile profile, String packageName, int uid) {
        if( mAppSettings == null ) return false;
        if( UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID ) return false;
        if( profile == null ) {
            if( packageName == null ) {
                profile = mAppSettings.getBaikalProfile(uid);
            } else {
                profile = mAppSettings.getBaikalProfile(packageName);
            }
        }
        return isBaikalBlocked(profile);
    }

    public boolean isBaikalBlocked(BaikalAppProfile profile) {
        if( profile == null ) return false;
        if( UserHandle.getAppId(profile.mUid) < Process.FIRST_APPLICATION_UID ) return false;
        if( isStamina() && !profile.getStamina() ) {
            if( profile.getBackgroundMode() > 0 ) {
                Slog.w(TAG, "Background execution disabled by baikalos stamina (" + 
                    + profile.getBackgroundMode() + "," 
                    + profile.getStamina() + ") : " 
                    + profile.mPackageName);
                return true;
            }
        }
        if( mAwake ) {
            if( profile.getBackgroundMode() > 1 ) {
                Slog.w(TAG, "Background execution disabled by baikalos ("
                    + profile.getBackgroundMode() + "," 
                    + profile.getStamina() + ") : " 
                    + profile.mPackageName);
                return true;
            
            }
        } else {
            if( profile.getBackgroundMode() > 0 ) {
                Slog.w(TAG, "Background execution limited by baikalos ("
                    + profile.getBackgroundMode() + "," 
                    + profile.getStamina() + ") : " 
                    + profile.mPackageName);
                return true;
            }
        }
        return false;
    }

    public boolean isCompatChangeEnabled(long changeId, ApplicationInfo app, boolean def) {
        String packageName = app.packageName;
        if( mAppSettings == null ) return def;
        if( packageName == null ) return def;
        BaikalAppProfile profile = mAppSettings.getBaikalProfile(packageName);
        if( profile == null ) {
            //Slog.w(TAG, "isCompatChangeEnabled pkg=" + packageName + ", chanId=" + changeId + ", def=" + def + ", result=" + def);
            return def;
        }
        boolean result = spoofCompatChangeInternal(changeId,profile,def);
        //if( profile.mDebug ) {
            //Slog.w(TAG, "isCompatChangeEnabled pkg=" + packageName + ", chanId=" + changeId + ", def=" + def + ", result=" + result);
        //}
        return result;
    }

	private boolean spoofCompatChangeInternal(long chanId, BaikalAppProfile profile, boolean def) {
        if( chanId == RESTRICT_DOMAINS ) {
            /*if( profile.mPackageName.equals("psyberia.alpinequest.free") ) {
                Slog.w(TAG, "isCompatChangeEnabled pkg=" + profile.mPackageName + ", chanId=" + chanId + ", def=" + def + ", result=" + false);
                return false;
            }*/
                //if( profile.mUnrestrict ) return true;
        }
        return def;
    }

    public int getBaikalOptionFromActivityManager(int opCode, int def, int callingUid, String callingPackage, Bundle bundle) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getBaikalOptionFromActivityManager:" + callingPackage + "/" + callingUid + ", opCode=" + opCode + ", def=" + def + ", profile=null" + ", result=" + def);                

        switch(opCode) {
            case 0:
                if( !mBlockIfBusy ) return def;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) 
                    Slog.w(TAG, "getBaikalOptionFromActivityManager filterIncomingCall: " + bundle.getString("callerDisplayName") + ", " + bundle.getString("contactDisplayName"));
                if ( mAudioService != null ) {
                    if( mAudioService.getVoicePlaybackActive() ) {
                        Slog.w(TAG, "getBaikalOptionFromActivityManager VoicePlaybackActive: block call " + bundle.getString("callerDisplayName") + ", " + bundle.getString("contactDisplayName"));
                        return 1;
                    }
                }
                return 0;
        }
        return def;
    }


    private int getTriStateInt(int app, boolean global, boolean system, int def) {
        switch(app) {
            case 1: return 0;
            case 2: return 1;
            default: return global ? (system ? def : 1) : def;
        }
    }


    public int getPackageOptionFromActivityManager(String packageName, int uid, int opCode, int def) {

        int result = def;
        //if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def);
        try {
            BaikalAppProfile profile = null;
            if( packageName != null ) profile = mAppSettings.getBaikalProfile(packageName);
            else profile = mAppSettings.getBaikalProfile(uid);
            if( profile == null )  {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", profile=null" + ", result=" + def);
                return def;
            }

            switch(opCode) {
    
                case BaikalAppProfile.OPCODE_LOCATION:
                    result = profile.mLocationLevel;
                    break;
                
                case BaikalAppProfile.OPCODE_HIDE_HMS:
                    result = getTriStateInt(profile.mHideHMS, mHideHMS, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_HIDE_GMS:
                    result = getTriStateInt(profile.mHideGMS, mHideGMS, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_HIDE_3P:
                    result = getTriStateInt(profile.mHide3P, mHide3P, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_BLOCK_CONTACTS:
                    result = getTriStateInt(profile.mBlockContacts, mBlockContacts, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_BLOCK_CALLLOG:
                    result = getTriStateInt(profile.mBlockCalllog, mBlockCalllog, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_BLOCK_CALENDAR:
                    result = getTriStateInt(profile.mBlockCalendar, mBlockCalendar, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_BLOCK_MEDIA:
                    result = getTriStateInt(profile.mBlockMedia, mBlockMedia, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_BLOCK_SMS:
                    result = getTriStateInt(profile.mBlockSms, mBlockSms, profile.mSystemApp,def);
                    break;

                case BaikalAppProfile.OPCODE_BLOCK_NOTIFICATION:
                    result = getTriStateInt(profile.mBlockNotification, mBlockNotification, profile.mSystemApp,def);
                    break;

                default:
                    Slog.w(TAG, "getPackageOptionFromActivityManager: invalid opcode:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", result=" + result);
            }
        } catch(Exception ex) {
            Slog.w(TAG, "getPackageOptionFromActivityManager: exception", ex);
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", result=" + result);
        return result;
    }

    boolean mAwake;
    public void setAwake(boolean awake) {
        mAwake = awake;
        BaikalAppProfile.setScreenMode(awake);
    }

    public boolean isAwake() {
        return mAwake;
    }

    public boolean isKillInBackground() {
        return mKillInBackground;
    }

    public int getBrightnessCurve() {
        try {
            mBrightnessCurve = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_BRIGHTNESS_CURVE, 0);
        } catch(Exception e) {
            return 0;
        }
        return mBrightnessCurve;
    }

    public boolean isExtreme() {
        if( mBaikalPowerSaveManager != null ) return mBaikalPowerSaveManager.getCurrentPowerSaverLevel() >= 3;
        return false;
    }

    public boolean isStamina() {
        return mStaminaEnabled;
    }

    public boolean isScreenActive() {
        return mScreenMode;
    }

    public boolean isTopAppUid(int uid, String callerPackage) {
        if( mTopUid != 1000 ) return mTopUid == uid;
        return mTopPackageName.equals(callerPackage);
    }

    public boolean isTopApp(String packageName) {
        return packageName == null ? false : packageName.equals(mTopPackageName);
    }

    public boolean updateBypassCharging(boolean enable) {
        mSmartBypassChargingEnabled = enable;
        return updateBypassChargingLocked();
    }
   
    public boolean updateBypassChargingLocked() {

        if( BaikalConstants.BAIKAL_DEBUG_POWER ) {
            Slog.i(TAG,"mBypassChargingAvailable=" + mBypassChargingAvailable +
                    ", mSmartBypassChargingEnabled=" + mSmartBypassChargingEnabled +
                    ", mBypassChargingForced=" + mBypassChargingForced +
                    ", mLimitedChargingForced=" + mLimitedChargingForced +
                    ", mBypassChargingScreenOn=" + mBypassChargingScreenOn +
                    ", mCurrentProfile.mBypassCharging=" + mCurrentProfile.mBypassCharging +
                    ", mLimitedChargingScreenOn=" + mLimitedChargingScreenOn +
                    ", mScreenMode=" + mScreenMode);
        }

        if( !mBypassChargingAvailable ) {
            return false;
        }

        if( !BaikalConstants.isKernelCompatible() ) {
            Slog.w(TAG, "Bypass charging disabled. Unsupported kernel!");
            return false;
        }

        boolean bypassEnabled = mSmartBypassChargingEnabled | mCurrentProfile.mBypassCharging | mBypassChargingForced | (mBypassChargingScreenOn && (mWakefulness == WAKEFULNESS_AWAKE));
        boolean limitedEnabled = !"none".equals(mPowerInputLimitValue) && (mLimitedChargingForced || (mLimitedChargingScreenOn && (mWakefulness == WAKEFULNESS_AWAKE)));

        try {
            if( !bypassEnabled && limitedEnabled ) {
                final File file = new File(mPowerInputSuspendSysfsNode);
                if (!file.exists()) {
                    if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG, "Update Limited charging failed. Kernel node not ready!");
                    return false;
                }

                String currentValue = FileUtils.readTextFile(file,0,null);
                int currentSetting = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_CHARGING_MODE,0);

                if( currentValue == null || currentSetting != 2 || !currentValue.startsWith(mPowerInputLimitValue)) {
                    if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG, "Update Limited charging " + limitedEnabled);
                    FileUtils.stringToFile(mPowerInputSuspendSysfsNode, mPowerInputLimitValue);
                    Settings.Global.putInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_CHARGING_MODE,2);
                }

            } else {

                final File file = new File(mPowerInputSuspendSysfsNode);
                if (!file.exists()) {
                    if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG, "Update Limited charging failed. Kernel node not ready!");
                    return false;
                }

                String currentValue = FileUtils.readTextFile(file,0,null);
                int currentSetting = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_CHARGING_MODE,0);

                if( currentValue == null 
                    || currentSetting != (bypassEnabled ? 1 : 0) 
                    || !currentValue.startsWith(bypassEnabled ? mPowerInputSuspendValue : mPowerInputResumeValue)) {
                    if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG, "Update Bypass charging " + bypassEnabled);
                    FileUtils.stringToFile(mPowerInputSuspendSysfsNode, bypassEnabled ? mPowerInputSuspendValue : mPowerInputResumeValue);
                    SystemPropertiesSet("baikal.charging.mode", bypassEnabled ? "1" : "0");
                    Settings.Global.putInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_CHARGING_MODE,bypassEnabled ? 1 : 0);
                }
            }
        } catch(Exception e) {
            Slog.e(TAG, "Couldn't set bypass or limited charging!", e);
        } 

        return bypassEnabled;
    }

    private static String readFileContents(String path) {
        final File file = new File(path);
        if (!file.exists()) {
            return null;
        }

        try {
            return FileUtils.readTextFile(file, 0 /* max */, null /* ellipsis */);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to read file:", e);
            return null;
        }
    }


    public boolean isAodOnChargerEnabled() {
        return mAodOnCharger & mOnCharger;
    }

    public boolean isGmsUid(int uid) {
        return UserHandle.getAppId(uid) == mGmsUid;
    }

    public boolean isAaUid(int uid) {
        return UserHandle.getAppId(uid) == mAaUid;
    }

    public boolean isSystemuiUid(int uid) {
        return UserHandle.getAppId(uid) == mSystemuiUid;
    }


    public static BaikalAppProfile getBaikalProfile(String packageName, int uid) {
        BaikalAppProfile profile = null;
        if( mInstance != null ) { 
            profile =  mInstance.getBaikalAppProfile(packageName, uid);
            if( uid == -1 ) return profile;
            return profile != null ? profile : mInstance.getBaikalAppProfile(-1);
        }
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
    
        return null; // new AppProfile(packageName,uid);
    }

    public static int getBackgroundMode(String packageName, int uid) {
        BaikalAppProfile profile = null;
        if( mInstance != null ) { 
            profile = mInstance.getBaikalAppProfile(packageName, uid);
            return profile != null ? profile.getBackgroundMode() : 0;
        }
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
    
        return 0; 
    }


    public static boolean isBaikalAppBlocked(BaikalAppProfile profile, String packageName, int uid) {
        if( mInstance != null ) return mInstance.isBaikalBlocked(profile, packageName, uid);
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
        return false;
    }

    public static boolean isBaikalAppBlocked(BaikalAppProfile profile) {
        if( mInstance != null ) return mInstance.isBaikalBlocked(profile);
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
        return false;
    }

    public static void setAudioMode(boolean playing) {
        if( mInstance != null ) { 
            mInstance.onAudioModeChanged(playing);
            return;
        }
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
    }

    public static boolean isAllowDowngrade() {
        if( mInstance != null ) return mInstance.mAllowDowngrade;
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
        return false;
    }

    public static boolean isAllowSigOverride() {
        if( mInstance != null ) return mInstance.mAllowSigOverride;
        Slog.wtf(TAG, "AppProfileManager not initialized.", new Throwable());
        return false;
    }

// ----------------- READER MODE --------------


    public static boolean isReaderModeActive() {
        return BaikalAppProfileSettings.isSuperSaverActive();
    }

// ----------------- READER MODE --------------

// Location

    public static boolean isLocationProviderEnabled(String name) {

        int uid = Binder.getCallingUid();

        int level = getLocationLevel(uid);
        switch(level) {
            case 0:
                return true;
            case 1:
                return true;
            case 5:
                if( name.equals(PASSIVE_PROVIDER) ) return false;
            case 4:
                if( name.equals(NETWORK_PROVIDER) ) return false;
            case 3:
                if( name.equals(FUSED_PROVIDER) ) return false;
            case 2:
                if( name.equals(GPS_PROVIDER) ) return false;

        }
        return true;
    }

    public static String overrideProvider(String provider, LocationRequest request, CallerIdentity identity) {

        int uid = identity.getUid(); //Binder.getCallingUid();

        if( request != null ) {
            WorkSource workSource = new WorkSource(request.getWorkSource());
            if (workSource != null && !workSource.isEmpty()) {
                WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
                if (workChain != null) {
                    uid = workChain.getAttributionUid();
                } else {
                    uid = workSource.getUid(0);
                }
                //Slog.i(TAG, "overrideProvider: Using workSource=" + workSource);
            }
        }

        int level = getLocationLevel(UserHandle.getAppId(uid));
        if( level < 2 ) return provider;
        if( level > 4 ) {
            Slog.i(TAG, "overrideProvider: from " + provider + " to NONE Using uid=" + uid);
            if( request != null ) request.setOriginalProvider(null);
            return null;
        } else if( level > 3 ) {
            Slog.i(TAG, "overrideProvider: from " + provider + " to PASSIVE Using uid=" + uid);
            if( request != null ) {
                request.setOriginalProvider(provider);
                request.setProvider(PASSIVE_PROVIDER);
            }
            return PASSIVE_PROVIDER;
        } else {
            if( GPS_PROVIDER.equals(provider) || FUSED_PROVIDER.equals(provider) ) {
                Slog.i(TAG, "overrideProvider: from " + provider + " to NETWORK Using uid=" + uid);
                if( request != null ) {
                    request.setOriginalProvider(provider);
                    request.setProvider(NETWORK_PROVIDER);
                }
                return NETWORK_PROVIDER;
            }
        }
        Slog.i(TAG, "overrideProvider: " + provider + " using uid=" + uid);
        return provider;
    }

    public static int getRequestUid(int uid, LocationRequest request) {

        WorkSource workSource = new WorkSource(request.getWorkSource());
        if (workSource != null && !workSource.isEmpty()) {
            WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
            if (workChain != null) {
                uid = workChain.getAttributionUid();
            } else {
                uid = workSource.getUid(0);
            }
            //Slog.i(TAG, "getRequestUid: Using workSource=" + workSource);
        }
        return uid;
    }


    public static LocationRequest.Builder sanitizeLocationRequest(LocationRequest.Builder source, CallerIdentity identity) {

        int uid = identity.getUid(); //Binder.getCallingUid();

        if( source == null ) return source;

        LocationRequest request = source.build();

        LocationRequest.Builder sanitized = new LocationRequest.Builder(request);

        WorkSource workSource = new WorkSource(request.getWorkSource());
        if (workSource != null && !workSource.isEmpty()) {
            WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
            if (workChain != null) {
                uid = workChain.getAttributionUid();
            } else {
                uid = workSource.getUid(0);
            }
            //Slog.i(TAG, "sanitizeLocationRequest: Using workSource=" + workSource);
        }

        int level = getLocationLevel(UserHandle.getAppId(uid));
        switch(level) {
            case 0:
                return sanitized;

            case 1: // FULL
                sanitized.setQuality(QUALITY_HIGH_ACCURACY);
                sanitized.setMinUpdateIntervalMillis(0);
                sanitized.setMinUpdateDistanceMeters(0);
                return sanitized;
            case 5: // NONE
                //sanitized.setMinUpdateIntervalMillis( 180*60*1000 );
                //sanitized.setIntervalMillis(PASSIVE_INTERVAL);
                //sanitized.setQuality(POWER_LOW);
            case 4: // PASSIVE
                sanitized.setMinUpdateIntervalMillis( 180*60*1000 );
                sanitized.setIntervalMillis(PASSIVE_INTERVAL);
                sanitized.setQuality(QUALITY_LOW_POWER);
                return sanitized;

            case 3: // COARSE CITY
                sanitized.setQuality(QUALITY_LOW_POWER);
                return sanitized;

            case 2: // COARSE BLOCK
                sanitized.setQuality(QUALITY_HIGH_ACCURACY);
                sanitized.setMinUpdateIntervalMillis(0);
                sanitized.setMinUpdateDistanceMeters(0);
                return sanitized;

        }
        return sanitized;
    }

    public static int overridePermissionLevel(int permissionLevel, LocationRequest request, CallerIdentity identity) {

        int level = getBaikalPermissionLevel(request,identity);

        switch(level) {
            case 1:
            case 2:
                return PERMISSION_FINE;
            case 3:
            case 4:
                return PERMISSION_COARSE;
            case 5:
                if( permissionLevel == PERMISSION_NONE ) return PERMISSION_NONE;
                return PERMISSION_COARSE;
        }
        return permissionLevel;
    }

    public static int getBaikalPermissionLevel(LocationRequest request, CallerIdentity identity) {
        return getBaikalPermissionLevel(0,request,identity);
    }

    public static int getBaikalPermissionLevel(int def, LocationRequest request, CallerIdentity identity) {

        int uid = identity.getUid(); //Binder.getCallingUid();

        if( request != null ) {
            WorkSource workSource = new WorkSource(request.getWorkSource());
            if (workSource != null && !workSource.isEmpty()) {
                WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
                if (workChain != null) {
                    uid = workChain.getAttributionUid();
                } else {
                    uid = workSource.getUid(0);
                }
            }
        }

        return getBaikalPermissionLevel(def, uid);
    }

    public static int getBaikalPermissionLevel(int def, int uid) {
        if( mInstance == null ) return def;

        if( mInstance.isGmsUid(uid) ) return def;

        BaikalAppProfile profile = mInstance.getBaikalAppProfile(UserHandle.getAppId(uid));
        if( profile == null ) return def;
        return profile.mLocationLevel;    
    }

    public static int getLocationLevel(int uid) {

        //Slog.i(TAG, "getLocationLevel: uid=" + uid);
        if( mInstance == null ) return 0;

        if( mInstance.isGmsUid(uid) ) return 0;

        BaikalAppProfile profile = mInstance.getBaikalAppProfile(UserHandle.getAppId(uid));
        if( profile == null ) return 0;
        return profile.mLocationLevel;    
    }

    private static WorkChain getFirstNonEmptyWorkChain(WorkSource workSource) {
        if (workSource.getWorkChains() == null) {
            return null;
        }

        for (WorkChain workChain: workSource.getWorkChains()) {
            if (workChain.getSize() > 0) {
                return workChain;
            }
        }

        return null;
    }

    public static boolean checkComponentPermission(String permission, int pid, int uid,
            int owningUid, boolean exported) {

        if( mInstance == null ) return false;

        if( mInstance.mContext != null ) {
            String packageName = BaikalConstants.getPackageByUid(mInstance.mContext, UserHandle.getAppId(uid));
            return checkPermission(packageName, permission, 0);
        }
        return false;
    }


    public static boolean checkPermission(String pkgName, String permName, int userId) {

        if( mInstance == null ) return false;

        if (pkgName == null || permName == null) {
            return false;
        }

        if( permName.startsWith("com.huawei") ) {
            if( pkgName.startsWith("com.huawei") ) {
                Slog.i(TAG, "checkPermission: huawei " + permName + " granted for " + pkgName);
                return true;
            }
        }

        if( permName.equals(READ_PRIVILEGED_PHONE_STATE) ) {
            if( pkgName.equals("android") || pkgName.equals("vendor.qti.iwlan") ) {
                //Slog.d(TAG, "checkPermission: READ_PRIVILEGED_PHONE_STATE granted for " + pkgName);
                return true;
            }
            BaikalAppProfile profile = mInstance.getBaikalAppProfile(pkgName,-1);
            if( profile != null ) {
                if( profile.mPriviledgedPhoneState ) {
                    Slog.d(TAG, "checkPermission: READ_PRIVILEGED_PHONE_STATE granted for " + pkgName);
                    return true;
                }
            } 
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "checkPermission: READ_PRIVILEGED_PHONE_STATE denied for " + pkgName);
        }
        return false;
    }

    public static boolean checkUidPermission(int uid, String permName) {
        if( mInstance == null ) return false;

        if( mInstance.mContext != null ) {
            String packageName = BaikalConstants.getPackageByUid(mInstance.mContext, UserHandle.getAppId(uid));
            return checkPermission(packageName, permName, 0);
        }
        return false;
    }

    public void setCarModeEnabled(int priority, String packageName) {
        setCarModeLocked(true);
    }
    public void setCarModeDisabled(int priority, String packageName) {
        setCarModeLocked(false);
    }



    public static void notifyCarModeEnabled(int priority, String packageName) {
        Slog.d(TAG, "notifyCarModeEnabled: prio=" + priority + ", pkg=" + packageName);
        if( mInstance != null ) mInstance.setCarModeEnabled(priority,packageName);
    }

    public static void notifyCarModeDisabled(int priority, String packageName) {
        Slog.d(TAG, "notifyCarModeDisabled: prio=" + priority + ", pkg=" + packageName);
        if( mInstance != null ) mInstance.setCarModeDisabled(priority,packageName);
    }

    private boolean getSystemCarModeOrProjectionState() {
        UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);

        if (uiModeManager != null) {
            return (uiModeManager.getActiveProjectionTypes()
                            & UiModeManager.PROJECTION_TYPE_AUTOMOTIVE) != 0;
        }

        Slog.w(TAG, "Got null UiModeManager, returning false.");
        return false;
    }


    @Override
    public void onProjectionStateChanged(int activeProjectionTypes,
            @NonNull Set<String> projectingPackages) {

        Slog.i(TAG, "onProjectionStateChanged: type=" + activeProjectionTypes + ", state=" + projectingPackages.isEmpty());
        try {
            synchronized (mLock) {
                if (projectingPackages.isEmpty()) {
                    setCarModeLocked(false);
                } else {
                    setCarModeLocked(true);
                }
            }
        } finally {
            
        }

    }
}
