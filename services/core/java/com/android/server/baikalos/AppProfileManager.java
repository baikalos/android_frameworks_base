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

import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

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

    private static final int MESSAGE_APP_PROFILE_UPDATE = BaikalConstants.MESSAGE_APP_PROFILE + 100;

    private AppProfileSettings mAppSettings;
    private IPowerManager mPowerManager;

    private boolean mOnCharger=false;
    private boolean mDeviceIdleMode = false;
    private boolean mScreenMode = true;

    private int mTopUid=-1;
    private String mTopPackageName;

    private boolean mIdleProfileActive;
    private int mActivePerfProfile = -1;
    private int mActiveThermProfile = -1;

    private String mScreenOffPerfProfile = "screen_off";
    private String mScreenOffThermProfile = "screen_off";

    private String mIdlePerfProfile = "idle";
    private String mIdleThermProfile = "idle";

    private static Object mCurrentProfileSync = new Object();
    private static AppProfile mCurrentProfile = new AppProfile("system");

    private int mActiveMinFrameRate=-1;
    private int mActiveMaxFrameRate=-1;

    private int mDefaultMinFps = 60;
    private int mDefaultMaxFps = 60;

    private boolean mVariableFps = false;

    private boolean mPerfAvailable = false;
    private boolean mThermAvailable = false;

    private boolean mPhoneCall = false;

    TelephonyManager mTelephonyManager;

    static AppProfileManager mInstance;

    private PowerManagerInternal mPowerManagerInternal;

    //private SparseArray<Integer> _performanceProfiles = new SparseArray<Integer> ();

    public static AppProfile getCurrentProfile() {
        //synchronized(mCurrentProfileSync) {
        return mCurrentProfile;
        //}
    }

    public static void refreshProfile() {
        if( mInstance != null ) {
            mInstance.restoreProfileForCurrentMode(true);
        }
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


    private AppProfileManager(Looper looper, Context context) {
        mContext = context;
        mHandler = new AppProfileManagerHandler(looper);
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

            mVariableFps = false; // SystemProperties.get("sys.baikal.var_fps", "0").equals("1");

            mPerfAvailable = false; // SystemProperties.get("baikal.eng.perf", "0").equals("1");
            mThermAvailable = false; // SystemProperties.get("baikal.eng.therm", "0").equals("1");

            
    
        }
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
            //Runtime.setIdleMode(mode);
            //BaikalSettings.setIdleMode(mode);
            //if( !mScreenMode ) {
            //    setIdlePerformanceMode(mode);
            //} 
            //restoreProfileForCurrentMode(true);
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
        }
    }


    protected void setScreenModeLocked(boolean mode) {
        if( mScreenMode != mode ) {
            mScreenMode = mode;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Restore profile after screen mode changed mode=" + mScreenMode);
            mHandler.postDelayed( new Runnable() {
                @Override
                public void run() { 
                    //SystemProperties.set("baikal.screen_mode", mScreenMode ? "1" : "0");
                    restoreProfileForCurrentMode(true);
                }
            }, 100);
        }
    }

    protected void setProfileExternalLocked(String profile) {
        if( profile == null || profile.equals("") ) {
            restoreProfileForCurrentMode(true);
        } else {
        }   
    }

    protected void restoreProfileForCurrentMode(boolean force) {
        if( mScreenMode ) {
            activateCurrentProfileLocked(force);
        } 
    }

    protected void activateCurrentProfileLocked(boolean force) {

            if( !mScreenMode ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Can't activate profile screen mode is " + mScreenMode);
                return;
            }

            AppProfile profile = mCurrentProfile;

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate current profile=" + profile);

            if( mActivePerfProfile != -1 ) {
                try {
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
                try {
                    activatePowerMode(MODE_INTERACTIVE, true);
                    mActivePerfProfile = MODE_INTERACTIVE;
                } catch(Exception e) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate perfromance profile failed profile=" + mActivePerfProfile, e);
                }
            } else {
                setActiveFrameRateLocked(profile.mMinFrameRate,profile.mMaxFrameRate);
                Actions.sendBrightnessOverrideChanged(setBrightnessOverrideLocked(profile.mBrightness));
                setRotation(profile.mRotation-1);
                try {
                    int perfMode = profile.mPerfProfile == 0 ? MODE_INTERACTIVE : profile.mPerfProfile;
                    activatePowerMode(perfMode, true);
                    mActivePerfProfile = perfMode;
                } catch(Exception e) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"Activate perfromance profile failed profile=" + mActivePerfProfile, e);
                }
                
            }
    }

    protected void activatePowerMode(int mode, boolean enable) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    mPowerManager.setPowerMode(mode, enable);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    protected void setTopAppLocked(int uid, String packageName) {

        if( packageName != null )  packageName = packageName.split(":")[0];

        //if( Runtime.isGmsUid(uid) && packageName != null && packageName.startsWith("com.google.android.gms.") ) packageName = "com.google.android.gms";

        if( uid != mTopUid || packageName != mTopPackageName ) {
            mTopUid = uid;
            mTopPackageName = packageName;

            /*if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE )*/ Slog.i(TAG,"topAppChanged uid=" + uid + ", packageName=" + packageName);

            //BaikalSettings.setTopApp(mTopUid, mTopPackageName);

            AppProfile profile = mAppSettings.getProfile(/*uid,*/packageName);
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
        if( !mVariableFps ) return true;
        if( mIdleProfileActive && !override ) return false;

        //if( minFps < 30 ) retrun setHwFrameRateLockedOld(minFps, override); 
        if( minFps == 0 ) minFps = mDefaultMinFps;
        if( maxFps == 0 ) maxFps = mDefaultMaxFps;

        try {
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE,minFps);
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE,maxFps);
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


    public boolean isAppBlocked(AppProfile profile, String packageName, int uid) {
        if( mAppSettings == null ) return false;
        if( profile == null ) {
            if( packageName == null ) {
                packageName = BaikalConstants.getPackageByUid(mContext, uid);
                if( packageName == null ) return true;
            }
            profile = mAppSettings.getProfile(packageName);
        }
        return isAppBlocked(profile);
    }

    public boolean isAppBlocked(AppProfile profile) {
        if( profile == null ) return false;
        if( mAwake ) {
            if( profile.getBackground() > 1 ) {
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
}
