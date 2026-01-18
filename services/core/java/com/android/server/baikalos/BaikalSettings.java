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

//import static com.android.server.am.ProcessList.SCHED_GROUP_BACKGROUND;
//import static com.android.server.am.ProcessList.SCHED_GROUP_RESTRICTED;
//import static com.android.server.am.ProcessList.SCHED_GROUP_DEFAULT;
//import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP;
//import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP_BOUND;

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

import com.android.internal.view.RotationPolicy;
import android.view.WindowManagerGlobal;
import android.view.IWindowManager;
import android.view.Display;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import android.baikalos.BaikalAppProfile;

import com.android.internal.annotations.GuardedBy;

import com.android.internal.baikalos.BaikalActions;
//import com.android.internal.baikalos.BaikalAppProfileSettings;
import com.android.internal.baikalos.BaikalConstants;
//import com.android.internal.baikalos.BaikalAppVolumeDB;
import com.android.internal.baikalos.BaikalPowerSaverPolicyConfig;
import com.android.internal.baikalos.BaikalSpoofer;

import com.android.server.audio.AudioService;
import com.android.server.LocalServices;
//import com.android.server.am.ActivityManagerConstants;


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

public class BaikalSettings implements UiModeManager.OnProjectionStateChangedListener { 

    private static final String TAG = "BaikalSettings";

    private final Object mLock = new Object();

    final Context mContext;
    final BaikalSettingsHandler mHandler;
    final Looper mLooper;

    /*private static Boolean mBypassChargingAvailable;
    private static String mPowerInputSuspendSysfsNode;
    private static String mPowerInputSuspendValue;
    private static String mPowerInputResumeValue;
    private static String mPowerInputLimitValue;*/

    private BaikalSettingsContentObserver mObserver;
    private ContentResolver mResolver;

    private static final int MESSAGE_APP_PROFILE_UPDATE = BaikalConstants.MESSAGE_APP_PROFILE + 100;

    private BaikalService mService;
    private BaikalAppProfileService mAppProfileService;
    TelephonyManager mTelephonyManager;


    //private IPowerManager mPowerManager;

    private boolean mAutoUpdate = false;
    private boolean mOverrideSpoof = false;
    private boolean mOnCharger = false;
    private boolean mDeviceIdleMode = false;
    private boolean mScreenMode = true;
    private int mWakefulness = WAKEFULNESS_AWAKE;
    private boolean mCarModeEnabled = false;

    private boolean mAodOnCharger = false;
    private boolean mSystemPriority = true;

    int mActiveMinFrameRate = -1;
    int mActiveMaxFrameRate = -1;
                                   
    float mDefaultMinFps = 0.0f;
    float mDefaultMaxFps = 0.0f;

    float mSystemDefaultMinFps = 0.0f;
    float mSystemDefaultMaxFps = Float.POSITIVE_INFINITY;

    int mDefaultPerformanceProfile;
    int mDefaultThermalProfile;

    int mDefaultScreenOffPerformanceProfile;
    int mDefaultScreenOffThermalProfile;

    int mDefaultIdlePerformanceProfile;
    int mDefaultIdleThermalProfile;

    boolean mSmartBypassChargingEnabled;
    boolean mBypassChargingForced;
    boolean mLimitedChargingForced;
    boolean mBypassChargingScreenOn;
    boolean mLimitedChargingScreenOn;

    boolean mStaminaEnabled;

    boolean mPerfAvailable = false;
    boolean mThermAvailable = false;

    boolean mForcedExtremeMode = false;
    boolean mAggressiveIdleMode = false;
    boolean mKillInBackground = false;
    boolean mAllowDowngrade = false;
    boolean mAllowSigOverride = false;

    boolean mInteractionBoost = true;
    boolean mDisplayBoost = false;
    boolean mRenderingBoost = true;

    int mInteractionBoostValue = 0;
    int mDisplayBoostValue = 0;
    int mRenderingBoostValue = 0;

    boolean mBlockIfBusy = false;

    int mBrightnessCurve = 0;

    boolean mPhoneCall = false;
    boolean mAudioPlaying = false;

    boolean mForcedUpdate = false;

    boolean mBlockHMS = false;
    boolean mBlockGMS = false;
    boolean mBlock3P = false;

    boolean mBlockContacts = false;
    boolean mBlockCallLog = false;
    boolean mBlockCalendar = false;
    boolean mBlockMedia = false;

    boolean mBlockSMS = false;
    boolean mBlockNotification = false;

    int mGmsUid = -1;
    int mAaUid = -1;
    int mSystemuiUid = -1;

    static BaikalSettings sInstance;

    public static BaikalSettings getInstance() {
        return sInstance;
    }

    public static BaikalSettings getInstance(BaikalService service, BaikalAppProfileService appProfileService, Looper looper, Context context) {
        if( sInstance == null ) {
            synchronized (BaikalSettings.class) {
                if (sInstance == null) {
                    sInstance = new BaikalSettings(service,appProfileService,looper,context);
                }
            }
        }
        return sInstance;
    }

    final class BaikalSettingsHandler extends Handler {
        BaikalSettingsHandler(Looper looper) {
            super(looper);
        }

        @Override public void handleMessage(Message msg) {
            onMessage(msg);
        }
    }

    final class BaikalSettingsContentObserver extends ContentObserver {

        BaikalSettingsContentObserver(Handler handler) {
            super(handler);

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
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_LIMITED_CHARGE_FORCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_PERFORMANCE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_THERMAL),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_DEFAULT_SCREENOFF_PERFORMANCE),
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

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_GMS_OVERRIDE_PROPS),
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

            } catch( Exception e ) {
            }
       
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalSettings.this) {
                updateConstantsLocked();
            }
        }
    }

    private BaikalSettings(BaikalService service, BaikalAppProfileService appProfileService, Looper looper, Context context) {
        mContext = context;
        mLooper = looper;
        mService = service;
        mAppProfileService = appProfileService;
        mHandler = new BaikalSettingsHandler(mLooper);

        final Resources resources = mContext.getResources();

    }

    public boolean onMessage(Message msg) {
    	switch(msg.what) {
    	    case MESSAGE_APP_PROFILE_UPDATE:
                //if( BaikalConstants.BAIKAL_DEBUG_POWERHAL ) Slog.i(TAG, "MESSAGE_APP_PROFILE_UPDATE cancel all boost requests");
    		    return true;
    	}
    	return false;
    }

    public void onSystemReady() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"onSystemReady()");                
        synchronized(this) {

            //mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
            //mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);

            IntentFilter topAppFilter = new IntentFilter();
            topAppFilter.addAction(BaikalActions.ACTION_TOP_APP_CHANGED);
            mContext.registerReceiver(mTopAppReceiver, topAppFilter, Context.RECEIVER_NOT_EXPORTED);

            IntentFilter idleFilter = new IntentFilter();
            idleFilter.addAction(BaikalActions.ACTION_IDLE_MODE_CHANGED);
            mContext.registerReceiver(mIdleReceiver, idleFilter, Context.RECEIVER_NOT_EXPORTED);

            IntentFilter chargerFilter = new IntentFilter();
            chargerFilter.addAction(BaikalActions.ACTION_CHARGER_MODE_CHANGED);
            mContext.registerReceiver(mChargerReceiver, chargerFilter, Context.RECEIVER_NOT_EXPORTED);

            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(BaikalActions.ACTION_SCREEN_MODE_CHANGED);
            mContext.registerReceiver(mScreenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);

            //IntentFilter profileFilter = new IntentFilter();
            //profileFilter.addAction(BaikalActions.ACTION_SET_PROFILE);
            //mContext.registerReceiver(mProfileReceiver, profileFilter, Context.RECEIVER_NOT_EXPORTED);

            IntentFilter wakefulnessFilter = new IntentFilter();
            wakefulnessFilter.addAction(BaikalActions.ACTION_WAKEFULNESS_CHANGED);
            mContext.registerReceiver(mWakefulnessReceiver, wakefulnessFilter, Context.RECEIVER_NOT_EXPORTED);

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

            // Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_EXTREME_IDLE, 0);
            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0);
            Settings.Global.putInt(mResolver,Settings.Global.BAIKALOS_LIMITED_CHARGE_FORCE, 0);

            // TODO: fill other wellknown UIDS, move to static context
            mGmsUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.google.android.gms"));
            mAaUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.google.android.projection.gearhead"));
            mSystemuiUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.android.systemui"));

            mObserver = new BaikalSettingsContentObserver(mHandler);

            mContext.getSystemService(UiModeManager.class).addOnProjectionStateChangedListener(
                    UiModeManager.PROJECTION_TYPE_AUTOMOTIVE, mContext.getMainExecutor(), this);

            mCarModeEnabled = getSystemCarModeOrProjectionState();
        }
    }

    @GuardedBy("mLock")
    protected void updateConstantsLocked() {
        
        boolean changed = false;

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings update");

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

        int defaultScreenOffPerformanceProfile = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEFAULT_SCREENOFF_PERFORMANCE, -1);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading screenoff performance profile=" + defaultScreenOffPerformanceProfile);
        if( mDefaultScreenOffPerformanceProfile != defaultScreenOffPerformanceProfile ) {
            mDefaultScreenOffPerformanceProfile = defaultScreenOffPerformanceProfile;
            changed = true;
        }

        int defaultScreenOffThermalProfile = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_DEFAULT_SCREENOFF_THERMAL, -1);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Settings loading screenoff thermal profile=" + defaultScreenOffThermalProfile);
        if( mDefaultScreenOffThermalProfile != defaultScreenOffThermalProfile ) {
            mDefaultScreenOffThermalProfile = defaultScreenOffThermalProfile;
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
            changed = true;
        }

        boolean autoUpdate = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_GMS_SPOOFER_UPDATE, 0) != 0;
        if( mService.mAttestation != null && autoUpdate != mAutoUpdate ) {
            mAutoUpdate = autoUpdate;
            mService.mAttestation.scheduleIfNeeded(autoUpdate);
        }

        boolean overrideSpoof = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_GMS_OVERRIDE_PROPS, 0) != 0;
        if( overrideSpoof != mOverrideSpoof ) {
            mOverrideSpoof = overrideSpoof;
        }

        mBlockIfBusy = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_IF_BUSY, 0) == 1; 

        mBlockHMS = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_HIDE_HMS, 0) == 1; 
        mBlockGMS = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_HIDE_GMS, 0) == 1; 
        mBlock3P = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_HIDE_3P, 0) == 1; 

        mBlockContacts = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_CONTACTS, 0) == 1; 
        mBlockCallLog = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_CALLLOG, 0) == 1; 
        mBlockCalendar = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_CALENDAR, 0) == 1; 
        mBlockMedia = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_MEDIA, 0) == 1; 
        mBlockSMS = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_SMS, 0) == 1; 
        mBlockNotification = Settings.Global.getInt(mResolver, Settings.Global.BAIKALOS_BLOCK_NOTIFICATION, 0) == 1; 

        if( changed ) {
            //activateCurrentProfileLocked(mForcedUpdate,false);
            //mForcedUpdate = false;
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    mService.updateSettings();
                }
            });
        }
    }

    protected void setDeviceIdleModeLocked(boolean mode) {
        if( mDeviceIdleMode != mode ) {
            mDeviceIdleMode = mode;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after device idle changed mode=" + mDeviceIdleMode);
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    mService.updateDeviceIdleMode(mode);
                }
            });
        }
    }

    protected void onCallStateChangedLocked(int state, String incomingNumber) {
    }


    private boolean isCallStateActive(int callState) {
        if( callState > 0 && callState != 7 ) return true;
        return false;
    }

    protected void onPreciseCallStateChangedLocked(PreciseCallState callState) {

        boolean state = isCallStateActive(callState.getRingingCallState()) ||
                        isCallStateActive(callState.getForegroundCallState()) ||
                        isCallStateActive(callState.getBackgroundCallState());

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"PreciseCallState: " + callState.toString());

        if( mPhoneCall != state ) {
            mPhoneCall = state;

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after phone mode changed mode=" + mPhoneCall);
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    mService.updatePhoneCallState(mPhoneCall);
                }
            });

        }
    }

    protected void setTopAppLocked(String packageName, int uid) {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                mService.updateTopApp(packageName,uid);
            }
        });
    }

    protected void setCarModeLocked(boolean mode) {
        if( mCarModeEnabled != mode ) {
            mCarModeEnabled = mode;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after car mode changed mode=" + mScreenMode);
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    mService.updateCarMode(mCarModeEnabled);
                }
            });
        }
    }

    protected void setScreenModeLocked(boolean mode) {
        if( mScreenMode != mode ) {
            mScreenMode = mode;
            BaikalConstants.setAodOnChargerEnabled(isAodOnChargerEnabled());
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after screen mode changed mode=" + mScreenMode);
            mHandler.post( new Runnable() {
                @Override
                public void run() {
                    mService.updateScreenMode(mode);
                }
            });

        }
    }

    protected void setWakefulnessLocked(int wakefulness) {
        if( mWakefulness != wakefulness ) {
            mWakefulness = wakefulness;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after wakefulness changed mode=" + mWakefulness);
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    mService.updateWakefulness(wakefulness);
                }
            });
        }
    }

    protected void setChargerModeLocked(boolean mode) {
        if( mOnCharger != mode ) {
            mOnCharger = mode;
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    mService.updateCharging(mode);
                }
            });
        }
    }


    private final BroadcastReceiver mTopAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalSettings.this) {
                String action = intent.getAction();
                String packageName = (String)intent.getExtra(BaikalActions.EXTRA_PACKAGENAME);
                int uid = (int)intent.getExtra(BaikalActions.EXTRA_UID);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"topAppChanged pkg=" + packageName + ", uid=" + uid);
                setTopAppLocked(packageName,uid);
            }
        }
    };

    private final BroadcastReceiver mIdleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalSettings.this) {
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
            synchronized (BaikalSettings.this) {
                String action = intent.getAction();
                boolean mode = (boolean)intent.getExtra(BaikalActions.EXTRA_BOOL_MODE);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"chargerChanged mode=" + mode);
                setChargerModeLocked(mode);
            }
        }
    };


    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (BaikalSettings.this) {
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
            synchronized (BaikalSettings.this) {
                String action = intent.getAction();
                int wakefulness = (int)intent.getExtra(BaikalActions.EXTRA_INT_WAKEFULNESS);
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"wakefulness mode=" + wakefulness);
                setWakefulnessLocked(wakefulness);
            }
        }
    };


    private void SystemPropertiesSet(String key, String value) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "SystemProperties.set("+key+","+value+")");
        try {
            SystemProperties.set(key,value);
        }
        catch( Exception e ) {
            Slog.e(TAG, "SystemPropertiesSet: unable to set property "+key+" to "+value);
        }
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
            synchronized (BaikalSettings.this) {
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
            synchronized (BaikalSettings.this) {
                onPreciseCallStateChangedLocked(callState);
            }
        }

    };

    private int getTriStateInt(int app, boolean global, boolean system, int def) {
        switch(app) {
            case 1: return 0;
            case 2: return 1;
            default: return global ? (system ? def : 1) : def;
        }
    }

    public String getPackageOptionString(String packageName, int uid, int opCode, String def) {
        String result = def;

        try {
            BaikalAppProfile profile = mAppProfileService.getProfileNotNullInternal(uid);

            if( profile == null )  {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) Slog.w(TAG, "getPackageString:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", profile=null" + ", result=" + def);
                return def;
            }

            switch(opCode) {
                case BaikalAppProfile.BAIKAL_OPCODE_SPOOF_SIM_COUNTRY:
                    result = "".equals(profile.mSpoofSimCountry) ? def : profile.mSpoofSimCountry;
                    break;
                case BaikalAppProfile.BAIKAL_OPCODE_SPOOF_SIM_MNC:
                    result = "".equals(profile.mSpoofSimMnc) ? def : profile.mSpoofSimMnc;
                    break;
                case BaikalAppProfile.BAIKAL_OPCODE_SPOOF_SIM_OP:
                    result = "".equals(profile.mSpoofSimOpName) ? def : profile.mSpoofSimOpName;
                    break;
                case BaikalAppProfile.BAIKAL_OPCODE_SPOOF_SIM_LN:
                    result = "".equals(profile.mSpoofSimLN) ? def : profile.mSpoofSimLN;
                    break;


                default:
                    Slog.w(TAG, "getPackageString: invalid opcode:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", result=" + result);
            }
        } catch(Exception ex) {
            Slog.w(TAG, "getPackageStringFromActivityManager: exception", ex);
        }

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) Slog.w(TAG, "getPackageStringFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", result=" + result);
        return result;

    }

    public int getPackageOption(String packageName, int uid, int opCode, int def) {

        int result = def;
        //int uid = UserHandle.getAppId(_uid);

        // if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def);
        try {
            BaikalAppProfile profile = null;

            if( packageName != null ) profile = mAppProfileService.getProfileByPackageNameInternal(packageName);
            if( profile == null && uid > 0 ) profile = mAppProfileService.getProfileInternal(uid);

            if( profile == null )  {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) Slog.w(TAG, "getPackageOption:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", profile=null" + ", result=" + def);
                return def;
            }

            switch(opCode) {
    
                case BaikalAppProfile.BAIKAL_OPCODE_LOCATION:
                    result = profile.mLocationLevel;
                    break;
                
                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_HMS:
                    result = getTriStateInt(profile.mBlockHMS, mBlockHMS, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_GMS:
                    result = getTriStateInt(profile.mBlockHMS, mBlockGMS, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_3P:
                    result = getTriStateInt(profile.mBlock3P, mBlock3P, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_CONTACTS:
                    result = getTriStateInt(profile.mBlockContacts, mBlockContacts, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_CALLLOG:
                    result = getTriStateInt(profile.mBlockCallLog, mBlockCallLog, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_CALENDAR:
                    result = getTriStateInt(profile.mBlockCalendar, mBlockCalendar, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_MEDIA:
                    result = getTriStateInt(profile.mBlockMedia, mBlockMedia, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_SMS:
                    result = getTriStateInt(profile.mBlockSMS, mBlockSMS, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_BLOCK_NOTIFICATION:
                    result = getTriStateInt(profile.mBlockNotification, mBlockNotification, (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0,def);
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_DEFAULT_DIALER:
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def);
                    // SDV" TODO: FIXME!!!
                    result = /*profile.mPackageName.equals(packageName) && */ profile.mUid == uid && 
                        (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEFAULT_DIALER) != 0 ? 1 : def;
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_DEFAULT_SMS:
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def);
                    // SDV" TODO: FIXME!!!
                    result = /*profile.mPackageName.equals(packageName) && */ profile.mUid == uid && 
                        (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEFAULT_SMS) != 0 ? 1 : def;
                    break;

                case BaikalAppProfile.BAIKAL_OPCODE_DEFAULT_CALLERID:
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def);
                    // SDV" TODO: FIXME!!!
                    result = /*profile.mPackageName.equals(packageName) && */ profile.mUid == uid && 
                        (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEFAULT_CALLERID) != 0 ? 1 : def;
                    break;

                default:
                    Slog.w(TAG, "getPackageOption: invalid opcode:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", result=" + result);
            }
        } catch(Exception ex) {
            Slog.w(TAG, "getPackageOption: exception", ex);
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) Slog.w(TAG, "getPackageOptionFromActivityManager:" + packageName + "/" + uid + ", opCode=" + opCode + ", def=" + def + ", result=" + result);
        return result;
    }


    public int getBaikalOptionWithParams(int opCode,int def, int callingUid, String callingPackage, Bundle params) {
        switch(opCode) {
            case 0:
                if( !mBlockIfBusy ) return def;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.w(TAG, "getBaikalOptionWithParams filterIncomingCall: " + params.getString("callerDisplayName") + ", " + params.getString("contactDisplayName"));
                AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                int mode = audioManager.getMode();
                if (mode == AudioManager.MODE_IN_COMMUNICATION) {
                    Slog.w(TAG, "getBaikalOptionWithParams VoicePlaybackActive: block call " + params.getString("callerDisplayName") + ", " + params.getString("contactDisplayName"));
                    return 1;
                }
                return 0;
        }
        return def;
    }

    public String getBaikalOptionStringWithParams(int opCode,String def, int callingUid, String callingPackage, Bundle params) {
        return "";
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
        return mAodOnCharger && mOnCharger && !mCarModeEnabled;
    }

    public boolean isGmsUid(int uid) {
        return UserHandle.getAppId(uid) == mGmsUid;
    }

    public boolean isAaUid(int uid) {
        return UserHandle.getAppId(uid) == mAaUid;
    }

    public boolean isSystemUiUid(int uid) {
        return UserHandle.getAppId(uid) == mSystemuiUid;
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
            mHandler.post( new Runnable() {
                @Override
                public void run() { 
                    synchronized (mLock) {
                        if (projectingPackages.isEmpty()) {
                            setCarModeLocked(false);
                        } else {
                            setCarModeLocked(true);
                        }
                    }
                }
            });
        } finally {
            
        }
    }
}
