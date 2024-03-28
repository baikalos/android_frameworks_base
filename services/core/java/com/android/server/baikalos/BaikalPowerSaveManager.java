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

import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_NONE;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_LOW;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_MODERATE;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_AGGRESSIVE;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_EXTREME;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_STAMINA;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_BATTERY_SAVER;
import static com.android.internal.baikalos.PowerSaverPolicyConfig.POWERSAVER_POLICY_MAX;

import android.util.Slog;

import android.content.Context;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.BatterySaverPolicyConfig;
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
import android.os.PowerManagerInternal;
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

import com.android.internal.baikalos.PowerSaverSettings;
import com.android.internal.baikalos.PowerSaverPolicyConfig;

import com.android.server.LocalServices;
import com.android.server.am.ActivityManagerConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

public class BaikalPowerSaveManager { 

    private static final String TAG = "Baikal.PowerSave";

    final Context mContext;
    final Handler mHandler;
    final Looper mLooper;

    private ManagerContentObserver mObserver;
    private ContentResolver mResolver;

    private int mPowerLevelOn = 0;
    private int mPowerLevelStandby = 0;
    private int mPowerLevelIdle = 0;

    private boolean mUnrestrictedNetwork;

    static BaikalPowerSaveManager mInstance;
    static PowerSaverSettings mPowerSaverSettings;
    static PowerManager mPowerManager;

    static BatterySaverPolicyConfig [] mLevels;

    static PowerSaverPolicyConfig [] mPolicies;

    static PowerSaverPolicyConfig [] mDefaultPolicies;

    ActivityManagerConstants mAmConstants;

    private boolean mScreenOn = true;
    private boolean mIsPowered;
    private boolean mDeviceIdle;
    private boolean mStamina;
    private boolean mForcedExtremeMode;

    private int mCurrentPowerSaverLevel = -1;
    static private PowerSaverPolicyConfig mCurrentPolicy;


    private String mPolicyString = "";

    public static BaikalPowerSaveManager getInstance() {
        return mInstance;
    }

    public static BaikalPowerSaveManager getInstance(Looper looper, Context context, ActivityManagerConstants amConstants) {
        if( mInstance == null ) {
            mInstance = new BaikalPowerSaveManager(looper,context,amConstants);
        }
        return mInstance;
    }

    public static PowerSaverPolicyConfig getCurrentPolicy() {
        if( mCurrentPolicy == null ) return new PowerSaverPolicyConfig("<unlnown>",0);
        return mCurrentPolicy;
    }

    final class ManagerContentObserver extends ContentObserver {

        ManagerContentObserver(Handler handler) {
            super(handler);

            try {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_POWER_LEVEL_ON),
                    false, this);
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_POWER_LEVEL_STANDBY),
                    false, this);
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_POWER_LEVEL_IDLE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_UNRESTRICTED_NET),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_EXTREME_IDLE),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_POWERSAVER_POLICY),
                    false, this);

            } catch( Exception e ) {
            }
        
            synchronized(this) {
                updateConstantsLocked();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalPowerSaveManager.this) {
                updateConstantsLocked();
            }
        }
    }

    private BaikalPowerSaveManager(Looper looper, Context context, ActivityManagerConstants amConstants) {

        mLevels = new BatterySaverPolicyConfig[POWERSAVER_POLICY_MAX];
        mPolicies = new PowerSaverPolicyConfig[POWERSAVER_POLICY_MAX];
        mDefaultPolicies = new PowerSaverPolicyConfig[POWERSAVER_POLICY_MAX];

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        mContext = context;
        mLooper = looper;
        mHandler = new Handler(mLooper);
        mAmConstants = amConstants;
        mPowerSaverSettings = new PowerSaverSettings(mHandler,mContext);
    }

    public void initialize() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"initialize()");                
        synchronized(this) {

            mInstance = this;

            mDefaultPolicies[POWERSAVER_POLICY_NONE] = (new PowerSaverPolicyConfig("default",POWERSAVER_POLICY_NONE))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(true)
                .setEnableKeyValueBackup(true)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(true)
                .setEnableOptionalSensors(true)
                .setEnableVibration(true)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(false)
                .setEnableNightMode(false)
                .setEnableQuickDoze(false)
                .setForceAllAppsStandby(false)
                .setForceBackgroundCheck(false)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED)
                .setLocationMode(PowerManager.LOCATION_MODE_NO_CHANGE)
                .setkillBgRestrictedCachedIdleSettleTime(600);

            mCurrentPolicy = mDefaultPolicies[POWERSAVER_POLICY_NONE];

            mDefaultPolicies[POWERSAVER_POLICY_LOW] = (new PowerSaverPolicyConfig("low",POWERSAVER_POLICY_LOW))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(true)
                .setEnableOptionalSensors(true)
                .setEnableVibration(true)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(false)
                .setEnableNightMode(false)
                .setEnableQuickDoze(false)
                .setForceAllAppsStandby(false)
                .setForceBackgroundCheck(false)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED)
                .setLocationMode(PowerManager.LOCATION_MODE_NO_CHANGE)
                .setkillBgRestrictedCachedIdleSettleTime(600);


            mDefaultPolicies[POWERSAVER_POLICY_MODERATE] = (new PowerSaverPolicyConfig("moderate",POWERSAVER_POLICY_MODERATE))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(true)
                .setEnableOptionalSensors(false)
                .setEnableVibration(true)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(false)
                .setEnableNightMode(false)
                .setEnableQuickDoze(false)
                .setForceAllAppsStandby(false)
                .setForceBackgroundCheck(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY)
                .setLocationMode(PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF)
                .setkillBgRestrictedCachedIdleSettleTime(300);

            mDefaultPolicies[POWERSAVER_POLICY_AGGRESSIVE] = (new PowerSaverPolicyConfig("aggressive",POWERSAVER_POLICY_AGGRESSIVE))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(false)
                .setEnableOptionalSensors(false)
                .setEnableVibration(true)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(false)
                .setEnableNightMode(false)
                .setEnableQuickDoze(true)
                .setForceAllAppsStandby(true)
                .setForceBackgroundCheck(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED)
                .setLocationMode(PowerManager.LOCATION_MODE_FOREGROUND_ONLY)
                .setkillBgRestrictedCachedIdleSettleTime(60);

            mDefaultPolicies[POWERSAVER_POLICY_EXTREME] = (new PowerSaverPolicyConfig("extreme",POWERSAVER_POLICY_EXTREME))
                .setAdjustBrightnessFactor(50)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(false)
                .setEnableAod(false)
                .setEnableLaunchBoost(false)
                .setEnableOptionalSensors(false)
                .setEnableVibration(false)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(true)
                .setEnableNightMode(false)
                .setEnableQuickDoze(true)
                .setForceAllAppsStandby(true)
                .setForceBackgroundCheck(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED)
                .setLocationMode(PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF)
                .setkillBgRestrictedCachedIdleSettleTime(30);

            mDefaultPolicies[POWERSAVER_POLICY_BATTERY_SAVER] = (new PowerSaverPolicyConfig("batterysaver",POWERSAVER_POLICY_BATTERY_SAVER))
                .setAdjustBrightnessFactor(50)
                .setAdvertiseIsEnabled(true)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(false)
                .setEnableAod(false)
                .setEnableLaunchBoost(false)
                .setEnableOptionalSensors(false)
                .setEnableVibration(false)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(true)
                .setEnableNightMode(false)
                .setEnableQuickDoze(true)
                .setForceAllAppsStandby(true)
                .setForceBackgroundCheck(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED)
                .setLocationMode(PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF)
                .setkillBgRestrictedCachedIdleSettleTime(30);

            mDefaultPolicies[POWERSAVER_POLICY_STAMINA] = (new PowerSaverPolicyConfig("stamina",POWERSAVER_POLICY_STAMINA))
                .setAdjustBrightnessFactor(50)
                .setAdvertiseIsEnabled(true)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(false)
                .setEnableAod(false)
                .setEnableLaunchBoost(false)
                .setEnableOptionalSensors(false)
                .setEnableVibration(false)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(true)
                .setEnableNightMode(false)
                .setEnableQuickDoze(true)
                .setForceAllAppsStandby(true)
                .setForceBackgroundCheck(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED)
                .setLocationMode(PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF)
                .setkillBgRestrictedCachedIdleSettleTime(15);

            mLevels[POWERSAVER_POLICY_NONE] = mDefaultPolicies[POWERSAVER_POLICY_NONE].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_LOW] = mDefaultPolicies[POWERSAVER_POLICY_LOW].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_MODERATE] = mDefaultPolicies[POWERSAVER_POLICY_MODERATE].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_AGGRESSIVE] = mDefaultPolicies[POWERSAVER_POLICY_AGGRESSIVE].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_EXTREME] = mDefaultPolicies[POWERSAVER_POLICY_EXTREME].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_STAMINA] = mDefaultPolicies[POWERSAVER_POLICY_STAMINA].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_BATTERY_SAVER] = mDefaultPolicies[POWERSAVER_POLICY_BATTERY_SAVER].getBatterySaverPolicyConfig();

            mResolver = mContext.getContentResolver();
            mPowerSaverSettings.registerObserver(true);
            mObserver = new ManagerContentObserver(mHandler);
        }
    }

    public void setScreenMode(boolean on) {
        if( on != mScreenOn ) {
           mScreenOn = on;
           updatePowerSaveLevel(true);
        }
    }

    public void setIsPowered(boolean powered) {
        if( powered != mIsPowered ) {
           mIsPowered = powered;
           updatePowerSaveLevel(true);
        }
    }

    public void setDeviceIdle(boolean idle) {
        if( idle != mDeviceIdle ) {
           mDeviceIdle = idle;
           updatePowerSaveLevel(true);  
        }
    }

    public void setStamina(boolean enable) {
        if( enable != mStamina ) {
           mStamina = enable;
           updatePowerSaveLevel(true);  
        }
    }

    protected void updateConstantsLocked() {
        
        boolean changed = true;

        boolean unrestrictedNetwork = Settings.Global.getInt(mResolver,
                    Settings.Global.BAIKALOS_UNRESTRICTED_NET,0) == 1;
        if( unrestrictedNetwork != mUnrestrictedNetwork ) {
            mUnrestrictedNetwork = unrestrictedNetwork;
        }

        int powerLevelOn = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_ON, POWERSAVER_POLICY_NONE);
        int powerLevelStandby = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_STANDBY, POWERSAVER_POLICY_NONE);
        int powerLevelIdle = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_IDLE, POWERSAVER_POLICY_NONE);

        boolean forcedExtremeMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_EXTREME_IDLE, 0) != 0;

        String policyConfig = Settings.Global.getString(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWERSAVER_POLICY);
        if( mPolicyString == null ||  mPolicyString.equals("") || !mPolicyString.equals(policyConfig) ) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"Policy changed. Updating");
            mPowerSaverSettings.loadPolicies(policyConfig);
            updatePowerSaverPoliciesLocked(policyConfig);
            changed = true;
        } else {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"Policy unchanged. Ignore");
        }

        if( mForcedExtremeMode != forcedExtremeMode ) {
            mForcedExtremeMode = forcedExtremeMode;
            changed = true;
        }

        if( powerLevelOn != mPowerLevelOn) { 
            mPowerLevelOn = powerLevelOn; 
            changed = true;
        }

        if( powerLevelStandby != mPowerLevelStandby) { 
            mPowerLevelStandby = powerLevelStandby; 
            changed = true;
        }

        if( powerLevelIdle != mPowerLevelIdle) { 
            mPowerLevelIdle = powerLevelIdle; 
            changed = true;
        }

        if( changed ) updatePowerSaveLevel(true);

    }

    private void updatePowerSaverPoliciesLocked(String policyString) {
        boolean changed = false;
        if( policyString == null || policyString.equals("") ) {
            initDefaultPoliciesLocked();
            changed = true;
        } else {
            if( !policyString.equals(mPolicyString) ) {
                for(int i=POWERSAVER_POLICY_NONE; i < POWERSAVER_POLICY_MAX; i++) {
                    changed |= loadOrDefaultLocked(i);
                }
                mPolicyString = policyString; 
            }
        }

        if( changed ) {
            mPowerSaverSettings.save();
            mPowerSaverSettings.commit();
        }
    }

    private boolean loadOrDefaultLocked(int type) {
        PowerSaverPolicyConfig policy = null;
        HashMap<Integer,PowerSaverPolicyConfig> map = mPowerSaverSettings.getPoliciesById();
        policy = mPowerSaverSettings.getPoliciesById().get(type);
        if( policy == null ) {
            policy = initDefaultPolicyLocked(type);
            if( policy != null ) {
                updatePolicyLocked(type, policy);
                return true;
            }
            return false;
        }
        initPolicyLocked(type,false,policy);
        return false;
    }

    private void updatePolicyLocked(int type, PowerSaverPolicyConfig policy) {
        if( policy != null ) {
            mPowerSaverSettings.getPoliciesById().put(type, policy);
            //mPowerSaverSettings.getPoliciesByName().put(policy.policyName, policy);
        }
    } 

    private void initDefaultPoliciesLocked() {
        for(int i=POWERSAVER_POLICY_NONE; i < POWERSAVER_POLICY_MAX; i++) {
            updatePolicyLocked(i, initDefaultPolicyLocked(i));
        }
    }

    private PowerSaverPolicyConfig initDefaultPolicyLocked(int type) {
        return initPolicyLocked(type,true,null);
    }


    private PowerSaverPolicyConfig initPolicyLocked(int type, boolean def, PowerSaverPolicyConfig config) {
        if( type < POWERSAVER_POLICY_NONE || type >= POWERSAVER_POLICY_MAX ) return null;

        mPolicies[type] = !def ? config : mDefaultPolicies[type];
        mLevels[type] = mPolicies[type].getBatterySaverPolicyConfig();
        if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"initPolicyLocked (" + def +"):" + mPolicies[type].serialize());
        return mPolicies[type];
    }


    private int setEffectiveMode(int current, int mode) {
        if( mode > current ) return mode;
        return current;
    }

    private void updatePowerSaveLevel(boolean force) {

        if( BaikalConstants.BAIKAL_DEBUG_POWER ) {
            Slog.i(TAG,"mPowerLevelOn=" + mPowerLevelOn);
            Slog.i(TAG,"mPowerLevelStandby=" + mPowerLevelStandby);
            Slog.i(TAG,"mPowerLevelIdle=" + mPowerLevelIdle);
            Slog.i(TAG,"mForcedExtremeMode=" + mForcedExtremeMode);
            Slog.i(TAG,"mStamina=" + mStamina);
        }
        
        int powerSaverLevel = 0;


        /*if( mIsPowered ) {
            powerSaverLevel = POWERSAVER_POLICY_NONE;
        } else*/ if( mStamina ) {
            powerSaverLevel = setEffectiveMode(powerSaverLevel,POWERSAVER_POLICY_STAMINA);
        } else if( mForcedExtremeMode ) {
            powerSaverLevel = setEffectiveMode(powerSaverLevel,POWERSAVER_POLICY_BATTERY_SAVER);
        } else if( mScreenOn ) {
            powerSaverLevel = setEffectiveMode(powerSaverLevel,mPowerLevelOn);
        } else if( mDeviceIdle ) {
            powerSaverLevel = setEffectiveMode(powerSaverLevel,mPowerLevelIdle);
        } else {
            powerSaverLevel = setEffectiveMode(powerSaverLevel,mPowerLevelStandby);
        }

        AppProfile.setPowerMode(powerSaverLevel);

        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_CURRENT, powerSaverLevel);

        if( powerSaverLevel != mCurrentPowerSaverLevel || force ) {

            if( powerSaverLevel >= POWERSAVER_POLICY_NONE && powerSaverLevel < POWERSAVER_POLICY_MAX ) {
                if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
                mCurrentPolicy = mPolicies[powerSaverLevel];
                activateCurrentPolicy();
                mPowerManager.setAdaptivePowerSavePolicy(mLevels[powerSaverLevel]);
                mPowerManager.setAdaptivePowerSaveEnabled(true);
                mAmConstants.updateKillBgRestrictedCachedIdleSettleTime(mPolicies[powerSaverLevel].killBgRestrictedCachedIdleSettleTime * 1000);
            } else {
                Slog.wtf(TAG,"INVALID mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
            }

            mCurrentPowerSaverLevel = powerSaverLevel;
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
        }
    }

    private void activateCurrentPolicy() {

    }

    public boolean getUnrestrictedNetwork() {
        return mUnrestrictedNetwork;
    }

    public int getCurrentPowerSaverLevel() {
        return mCurrentPowerSaverLevel;
    }
}
