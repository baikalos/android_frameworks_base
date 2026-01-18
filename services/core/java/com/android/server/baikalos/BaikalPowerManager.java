/*
 * Copyright (C) 2025 BaikalOS
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

import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_NONE; // 0
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_LOW; // 1
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_MODERATE; // 2
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_AGGRESSIVE; // 3
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_EXTREME; // 4
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_STAMINA; // 5
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_BATTERY_SAVER; // 6
import static com.android.internal.baikalos.BaikalPowerSaverPolicyConfig.POWERSAVER_POLICY_MAX; // 7

import android.baikalos.*;
import com.android.internal.baikalos.*;

import android.hardware.power.Mode;
import android.hardware.power.Boost;
import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.BatterySaverPolicyConfig;
import android.os.SystemProperties;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.SystemClock;

import android.util.Slog;

import android.database.ContentObserver;
import android.provider.Settings;

import android.util.ArraySet;
import android.util.KeyValueListParser;
import android.util.Pair;

import com.android.server.power.BaikalPowerManagerService;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.android.internal.R;

public class BaikalPowerManager { 

    private static final String TAG = "BaikalPowerManager";

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
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_POWER_LEVEL_ON_CHARGER),
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
            updateConstants();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized(BaikalPowerManager.this) {
                updateConstantsLocked();
            }
            updateConstants();
        }
    }

    final BaikalService mService;
    final Context mContext;
    final Handler mHandler;
    final Looper mLooper;

    private ManagerContentObserver mObserver;
    private ContentResolver mResolver;

    boolean mSystemReady;

    BaikalPowerArbiter mBaikalPowerArbiter;
    BaikalResourceMapper mPowerModeMapper;

    private int mPowerLevelOn = 0;
    private int mPowerLevelStandby = 0;
    private int mPowerLevelIdle = 0;
    private int mPowerLevelOnCharger = 0;

    private boolean mUnrestrictedNetwork;


    static BaikalPowerSaverSettings mPowerSaverSettings;

    static PowerManager mPowerManager;

    static BatterySaverPolicyConfig [] mLevels;

    static BaikalPowerSaverPolicyConfig [] mPolicies;

    static BaikalPowerSaverPolicyConfig [] mDefaultPolicies;

    //ActivityManagerConstants mAmConstants;

    private int mWakefulness;
    private boolean mScreenOn = true;
    private boolean mIsPowered;
    private boolean mDeviceIdle;
    private boolean mStamina;
    private boolean mForcedExtremeMode;

    private int mCurrentPowerSaverLevel = -1;
    static private BaikalPowerSaverPolicyConfig sCurrentPolicy;
    static private BatterySaverPolicyConfig sCurrentLevel;


    private String mPolicyString = "";

    BaikalPowerManager(BaikalService parent, Context context, Handler handler, Looper looper) {
        mService = parent;

        mContext = context;
        mLooper = looper;
        mHandler = new Handler(mLooper);
        mPowerSaverSettings = new BaikalPowerSaverSettings(mHandler,mContext);
        mBaikalPowerArbiter = new BaikalPowerArbiter();
        mPowerModeMapper = new BaikalResourceMapper(context,R.array.config_powermode_keys,R.array.config_powermode_values);

        mLevels = new BatterySaverPolicyConfig[POWERSAVER_POLICY_MAX];
        mPolicies = new BaikalPowerSaverPolicyConfig[POWERSAVER_POLICY_MAX];
        mDefaultPolicies = new BaikalPowerSaverPolicyConfig[POWERSAVER_POLICY_MAX];

            mDefaultPolicies[POWERSAVER_POLICY_NONE] = (new BaikalPowerSaverPolicyConfig("default",POWERSAVER_POLICY_NONE))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(true)
                .setEnableKeyValueBackup(true)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(true)
                .setEnableInteractionBoost(true)
                .setEnableRenderingBoost(true)
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
                .setLessRestrictiveBackgroundPolicy(true)
                .setDisableBackgroundByDefault(false)
                .setKillInBackground(false)
                .setkillBgRestrictedCachedIdleSettleTime(600);

            sCurrentPolicy = mDefaultPolicies[POWERSAVER_POLICY_NONE];

            mDefaultPolicies[POWERSAVER_POLICY_LOW] = (new BaikalPowerSaverPolicyConfig("low",POWERSAVER_POLICY_LOW))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(true)
                .setEnableInteractionBoost(true)
                .setEnableRenderingBoost(true)
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
                .setLessRestrictiveBackgroundPolicy(false)
                .setDisableBackgroundByDefault(false)
                .setKillInBackground(false)
                .setkillBgRestrictedCachedIdleSettleTime(600);


            mDefaultPolicies[POWERSAVER_POLICY_MODERATE] = (new BaikalPowerSaverPolicyConfig("moderate",POWERSAVER_POLICY_MODERATE))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(true)
                .setEnableInteractionBoost(true)
                .setEnableRenderingBoost(false)
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
                .setLessRestrictiveBackgroundPolicy(false)
                .setDisableBackgroundByDefault(false)
                .setKillInBackground(true)
                .setkillBgRestrictedCachedIdleSettleTime(300);

            mDefaultPolicies[POWERSAVER_POLICY_AGGRESSIVE] = (new BaikalPowerSaverPolicyConfig("aggressive",POWERSAVER_POLICY_AGGRESSIVE))
                .setAdjustBrightnessFactor(100)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(true)
                .setEnableAod(true)
                .setEnableLaunchBoost(false)
                .setEnableInteractionBoost(false)
                .setEnableRenderingBoost(false)
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
                .setLessRestrictiveBackgroundPolicy(false)
                .setDisableBackgroundByDefault(true)
                .setKillInBackground(true)
                .setkillBgRestrictedCachedIdleSettleTime(30);

            mDefaultPolicies[POWERSAVER_POLICY_EXTREME] = (new BaikalPowerSaverPolicyConfig("extreme",POWERSAVER_POLICY_EXTREME))
                .setAdjustBrightnessFactor(50)
                .setAdvertiseIsEnabled(false)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(false)
                .setEnableAod(false)
                .setEnableLaunchBoost(false)
                .setEnableInteractionBoost(false)
                .setEnableRenderingBoost(false)
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
                .setLessRestrictiveBackgroundPolicy(false)
                .setDisableBackgroundByDefault(true)
                .setKillInBackground(true)
                .setkillBgRestrictedCachedIdleSettleTime(15);

            mDefaultPolicies[POWERSAVER_POLICY_BATTERY_SAVER] = (new BaikalPowerSaverPolicyConfig("batterysaver",POWERSAVER_POLICY_BATTERY_SAVER))
                .setAdjustBrightnessFactor(50)
                .setAdvertiseIsEnabled(true)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(false)
                .setEnableAod(false)
                .setEnableLaunchBoost(false)
                .setEnableInteractionBoost(false)
                .setEnableRenderingBoost(false)
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
                .setLessRestrictiveBackgroundPolicy(false)
                .setDisableBackgroundByDefault(true)
                .setKillInBackground(true)
                .setkillBgRestrictedCachedIdleSettleTime(30);

            mDefaultPolicies[POWERSAVER_POLICY_STAMINA] = (new BaikalPowerSaverPolicyConfig("stamina",POWERSAVER_POLICY_STAMINA))
                .setAdjustBrightnessFactor(50)
                .setAdvertiseIsEnabled(true)
                .setEnableFullBackup(false)
                .setEnableKeyValueBackup(false)
                .setEnableAnimation(false)
                .setEnableAod(false)
                .setEnableLaunchBoost(false)
                .setEnableInteractionBoost(false)
                .setEnableRenderingBoost(false)
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
                .setLessRestrictiveBackgroundPolicy(false)
                .setDisableBackgroundByDefault(true)
                .setKillInBackground(true)
                .setkillBgRestrictedCachedIdleSettleTime(15);

            mLevels[POWERSAVER_POLICY_NONE] = mDefaultPolicies[POWERSAVER_POLICY_NONE].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_LOW] = mDefaultPolicies[POWERSAVER_POLICY_LOW].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_MODERATE] = mDefaultPolicies[POWERSAVER_POLICY_MODERATE].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_AGGRESSIVE] = mDefaultPolicies[POWERSAVER_POLICY_AGGRESSIVE].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_EXTREME] = mDefaultPolicies[POWERSAVER_POLICY_EXTREME].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_STAMINA] = mDefaultPolicies[POWERSAVER_POLICY_STAMINA].getBatterySaverPolicyConfig();
            mLevels[POWERSAVER_POLICY_BATTERY_SAVER] = mDefaultPolicies[POWERSAVER_POLICY_BATTERY_SAVER].getBatterySaverPolicyConfig();


    }

    public void onSystemReady() {
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mResolver = mContext.getContentResolver();
        mPowerSaverSettings.registerObserver(true);
        mBaikalPowerArbiter.systemReady();
        mObserver = new ManagerContentObserver(mHandler);
        mSystemReady = true;
    }

    public void onTopAppChanged(String packageName, int uid) {
    }

    public void setCharging(boolean charging) {
        if( charging != mIsPowered ) {
           mIsPowered = charging;
           updatePowerSaveLevel(true);
        }
    }

    public void setWakefulness(int wakefulness) {
        if( wakefulness != mWakefulness ) {
           mWakefulness = wakefulness; 
           updatePowerSaveLevel(true);
        }
    }

    public void setDeviceIdleMode(boolean mode) {
        if( mode != mDeviceIdle ) {
           mDeviceIdle = mode;
           updatePowerSaveLevel(true);  
        }
    }

    public void setScreenMode(boolean mode) {
        if( mode != mScreenOn ) {
           mScreenOn = mode;
           updatePowerSaveLevel(true);
        }
    }

    public void setPhoneCallState(boolean mode) {
    }

    public void setCarMode(boolean mode) {
    }

    public void updateSettings() {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                //synchronized(this) {
                    updatePowerSaveLevel(false); 
                //}
            }
        });
    }

    protected void updateConstants() {
    }


    protected void updateConstantsLocked() {
        
        boolean changed = true;

        boolean unrestrictedNetwork = Settings.Global.getInt(mResolver,
                    Settings.Global.BAIKALOS_UNRESTRICTED_NET,0) == 1;
        if( unrestrictedNetwork != mUnrestrictedNetwork ) {
            mUnrestrictedNetwork = unrestrictedNetwork;
            changed = true;
        }

        int powerLevelOn = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_ON, POWERSAVER_POLICY_NONE);
        int powerLevelStandby = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_STANDBY, POWERSAVER_POLICY_NONE);
        int powerLevelIdle = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_IDLE, POWERSAVER_POLICY_NONE);
        int powerLevelOnCharger = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_ON_CHARGER, POWERSAVER_POLICY_NONE);

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

        if( powerLevelOnCharger != mPowerLevelOnCharger) { 
            mPowerLevelOnCharger = powerLevelOnCharger; 
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
        BaikalPowerSaverPolicyConfig policy = null;
        HashMap<Integer, BaikalPowerSaverPolicyConfig> map = mPowerSaverSettings.getPoliciesById();
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

    private void updatePolicyLocked(int type, BaikalPowerSaverPolicyConfig policy) {
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

    private BaikalPowerSaverPolicyConfig initDefaultPolicyLocked(int type) {
        return initPolicyLocked(type,true,null);
    }

    private BaikalPowerSaverPolicyConfig initPolicyLocked(int type, boolean def, BaikalPowerSaverPolicyConfig config) {
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

        if( !mSystemReady ) return;

        if( BaikalConstants.BAIKAL_DEBUG_POWER ) {
            Slog.i(TAG,"mPowerLevelOn=" + mPowerLevelOn);
            Slog.i(TAG,"mPowerLevelStandby=" + mPowerLevelStandby);
            Slog.i(TAG,"mPowerLevelIdle=" + mPowerLevelIdle);
            Slog.i(TAG,"mForcedExtremeMode=" + mForcedExtremeMode);
            Slog.i(TAG,"mPowerLevelOnCharger=" + mPowerLevelOnCharger);
            Slog.i(TAG,"mStamina=" + mStamina);
        }
        
        int powerSaverLevel = 0;

        if( mIsPowered ) {
            powerSaverLevel = setEffectiveMode(powerSaverLevel,mPowerLevelOnCharger);
        } else if( mStamina ) {
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

        //BaikalAppProfile.setPowerMode(powerSaverLevel);

        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_CURRENT, powerSaverLevel);

        if( powerSaverLevel != mCurrentPowerSaverLevel || force ) {

            mCurrentPowerSaverLevel = powerSaverLevel;
            if( powerSaverLevel >= POWERSAVER_POLICY_NONE && powerSaverLevel < POWERSAVER_POLICY_MAX ) {
                if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
                synchronized(this) {
                    sCurrentPolicy = mPolicies[powerSaverLevel];
                    sCurrentLevel = mLevels[powerSaverLevel];
                }
                //BaikalAppProfile.setDefaultBackgroundMode(mCurrentPolicy.disableBackgroundByDefault ? 2:0);
                //mAmConstants.updateKillBgRestrictedCachedIdleSettleTime(mPolicies[powerSaverLevel].killBgRestrictedCachedIdleSettleTime * 1000);
                mService.mBaikalActivityManagerService.updateKillBgRestrictedCachedIdleSettleTime(sCurrentPolicy.killBgRestrictedCachedIdleSettleTime * 1000);
                activateCurrentPolicy();
                mPowerManager.setAdaptivePowerSavePolicy(sCurrentLevel);
            } else {
                Slog.wtf(TAG,"INVALID mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
            }
            mPowerManager.setAdaptivePowerSaveEnabled(true);
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
        }
    }

    private void activateCurrentPolicy() {
        BaikalPowerSaverPolicyConfig.setCurrentPowerSaverPolicyConfig(sCurrentPolicy);
    }

    public boolean getUnrestrictedNetwork() {
        return mUnrestrictedNetwork;
    }

    public int getCurrentPowerSaverLevel() {
        return mCurrentPowerSaverLevel;
    }

    boolean isAutoAppRestrictionActiveInternal() {
        return getCurrentPolicy().autoLimitBackground;
    }

    public static BaikalPowerSaverPolicyConfig getCurrentPolicy() {
        if( sCurrentPolicy == null ) {
            Slog.i(TAG,"mCurrentPolicy=null");
            sCurrentPolicy = new BaikalPowerSaverPolicyConfig("<unknown>",0);
        }
        return sCurrentPolicy;
    }
                    
    Object mPowerBoostConfigLock = new Object();
    boolean mPowerBoostConfigInititalized = false;
    boolean[] mPowerBoostConfig = new boolean[64];
    public int onSetPowerBoostInternal(BaikalPowerManagerService service, int boost, int durationMs) {
        if( mSystemReady && !mPowerBoostConfigInititalized ) {
            synchronized(mPowerBoostConfigLock) {
                if( !mPowerBoostConfigInititalized ) mPowerBoostConfigInititalized = initPowerBoostConfigLocked(service);
            }
        }

        if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.d(TAG,"setPowerBoostInternal:" + getPowerBoostName(boost) + ", " + durationMs + ", " + Binder.getCallingUid());
        if( boost < 0 || boost > 63 )  return 0;
        if( !mPowerBoostConfig[boost] ) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG,"onSetPowerBoostInternal: unsupported boost " + getPowerBoostName(boost));
            return 0;
        }
        if( mBaikalPowerArbiter == null ) return -1;
        return -1;
    }

    Object mPowerModeConfigLock = new Object();
    boolean mPowerModeConfigInititalized = false;
    boolean[] mPowerModeConfig = new boolean[64];
    public int onSetPowerModeInternal(BaikalPowerManagerService service, int mode, boolean enabled, int uid) {
        if( mSystemReady && !mPowerModeConfigInititalized ) {
            synchronized(mPowerModeConfigLock) {
                if( !mPowerModeConfigInititalized ) mPowerModeConfigInititalized = initPowerModeConfigLocked(service);
            }
        }
        if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.d(TAG,"onSetPowerModeInternal:" + getPowerModeName(mode) + ", " + enabled + ", " + uid);
        mode = overridePowerModeInternal(mode,uid);
        if( mode < 0 || mode > 63 )  return 0;
        if( !mPowerModeConfig[mode] ) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG,"onSetPowerModeInternal: unsupported mode " + getPowerModeName(mode) + (enabled ? " requested" : " cancelled"));
            return 0;
        }
        if( mBaikalPowerArbiter == null ) return -1;
        return mBaikalPowerArbiter.onSetPowerModeInternal(service,mode,enabled,uid);
    }

    public int setPowerModeInternal(int mode, boolean enabled, int uid) {
        if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.d(TAG,"setPowerModeInternal:" + getPowerModeName(mode) + ", " + enabled + ", " + uid);
        mode = overridePowerModeInternal(mode, uid);
        if( mode < 0 || mode > 63 )  return -1;
        if( !mPowerModeConfig[mode] ) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.w(TAG,"setPowerModeInternal: unsupported mode " + getPowerModeName(mode) + (enabled ? " requested" : " cancelled"));
            return 0;
        }
        if( mBaikalPowerArbiter == null ) return -1;
        return mBaikalPowerArbiter.setPowerModeInternal(mode,enabled,uid);
    }

    boolean initPowerBoostConfigLocked(BaikalPowerManagerService service) {
        try {
            for(int i=0;i<64;i++) {
                if( service.getPowerManagerService().setPowerBoostInternalFromBaikalWrapper(i, 0) ) {
                    mPowerBoostConfig[i] = true;
                    Slog.i(TAG,"initPowerBoostConfigLocked: boost " + i + " enabled");
                } else {
                    //Slog.i(TAG,"initPowerBoostConfigLocked: boost " + i + " disabled");
                }
            }
        } catch(Exception e) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"initPowerBoostConfigLocked: not ready yet");
            return false;
        }
        return true;
    }

    boolean initPowerModeConfigLocked(BaikalPowerManagerService service) {
        try {
            mPowerModeConfig[0] = true;
            for(int i=1;i<64;i++) {
                if( service.getPowerManagerService().setPowerModeInternalFromBaikalWrapper(i, true) ) {
                    mPowerModeConfig[i] = true;
                    service.getPowerManagerService().setPowerModeInternalFromBaikalWrapper(i, false);
                    Slog.i(TAG,"initPowerModeConfigLocked: mode " + getPowerModeName(i) + " enabled");
                } else {
                    service.getPowerManagerService().setPowerModeInternalFromBaikalWrapper(i, false);
                    //Slog.i(TAG,"initPowerModeConfigLocked: mode " + i + " disabled");
                }
            }
        } catch(Exception e) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG,"initPowerModeConfigLocked: not ready yet");
            return false;
        }
        return true;
    }

    private int overridePowerModeInternal(int mode, int uid) {
        int override = mPowerModeMapper.get(mode,-1);
        if( BaikalConstants.BAIKAL_DEBUG_POWER && override != -1 ) Slog.d(TAG,"overrideSetPowerModeInternal: override " + getPowerModeName(mode) + "  to " + getPowerModeName(override));
        return  override != -1 ? override : mode;
    }

    public String getBaikalPowerModeNameInternal(int mode) {
        if( mode < 1 || mode > 63 ) return null;
        if( !mPowerModeConfig[mode] ) return null;
        return getPowerModeDisplayName(mode);
    }


    public void dump(PrintWriter pw) {
        mBaikalPowerArbiter.dump(pw);
    }

    public static String getPowerModeName(int mode) {
        return switch (mode) {
            case Mode.DOUBLE_TAP_TO_WAKE -> "DOUBLE_TAP_TO_WAKE" + "(" + mode + ")";
            case Mode.LOW_POWER -> "LOW_POWER" + "(" + mode + ")";
            case Mode.SUSTAINED_PERFORMANCE -> "SUSTAINED_PERFORMANCE" + "(" + mode + ")";
            case Mode.FIXED_PERFORMANCE -> "FIXED_PERFORMANCE" + "(" + mode + ")";
            case Mode.VR -> "VR" + "(" + mode + ")";
            case Mode.LAUNCH -> "LAUNCH" + "(" + mode + ")";
            case Mode.EXPENSIVE_RENDERING -> "EXPENSIVE_RENDERING" + "(" + mode + ")";
            case Mode.INTERACTIVE -> "INTERACTIVE" + "(" + mode + ")";
            case Mode.DEVICE_IDLE -> "DEVICE_IDLE" + "(" + mode + ")";
            case Mode.DISPLAY_INACTIVE -> "DISPLAY_INACTIVE" + "(" + mode + ")";
            case Mode.AUDIO_STREAMING_LOW_LATENCY -> "AUDIO_LOW_LATENCY" + "(" + mode + ")";
            case Mode.CAMERA_STREAMING_SECURE -> "CAMERA_SECURE" + "(" + mode + ")";
            case Mode.CAMERA_STREAMING_LOW -> "CAMERA_LOW" + "(" + mode + ")";
            case Mode.CAMERA_STREAMING_MID -> "CAMERA_MID" + "(" + mode + ")";
            case Mode.CAMERA_STREAMING_HIGH -> "CAMERA_HIGH" + "(" + mode + ")";
            case Mode.GAME -> "GAME" + "(" + mode + ")";
            case Mode.GAME_LOADING -> "GAME_LOADING" + "(" + mode + ")";
            case 17 -> "DISPLAY_CHANGE" + "(" + mode + ")";
            default -> "UNKNOWN_" + mode;
        };
    }

    public static String getPowerModeDisplayName(int mode) {
        return switch (mode) {
            case Mode.DOUBLE_TAP_TO_WAKE -> "DOUBLE_TAP_TO_WAKE" + "(" + mode + ")";
            case Mode.LOW_POWER -> "Low";
            case Mode.SUSTAINED_PERFORMANCE -> "Sustained";
            case Mode.FIXED_PERFORMANCE -> "Fixed";
            case Mode.VR -> "VR";
            case Mode.LAUNCH -> "Launch";
            case Mode.EXPENSIVE_RENDERING -> "Expensive Rendering";
            case Mode.INTERACTIVE -> "Balanced";
            case Mode.DEVICE_IDLE -> "Device Idle";
            case Mode.DISPLAY_INACTIVE -> "Display Inactive";
            case Mode.AUDIO_STREAMING_LOW_LATENCY -> "Low latency audio";
            case Mode.CAMERA_STREAMING_SECURE -> "Camera";
            case Mode.CAMERA_STREAMING_LOW -> "Camera Low";
            case Mode.CAMERA_STREAMING_MID -> "Camera Mid";
            case Mode.CAMERA_STREAMING_HIGH -> "Camera High";
            case Mode.GAME -> "Game";
            case Mode.GAME_LOADING -> "Game Loading";
            case 17 -> "Display change";
            default -> "Device mode " + mode;
        };
    }

    public static String getPowerBoostName(int mode) {
        return switch (mode) {
            case Boost.INTERACTION -> "INTERACTION" + "(" + mode + ")";
            case Boost.DISPLAY_UPDATE_IMMINENT -> "DISPLAY_UPDATE_IMMINENT" + "(" + mode + ")";
            case Boost.ML_ACC -> "ML_ACC" + "(" + mode + ")";
            case Boost.AUDIO_LAUNCH -> "AUDIO_LAUNCH" + "(" + mode + ")";
            case Boost.CAMERA_LAUNCH -> "CAMERA_LAUNCH" + "(" + mode + ")";
            case Boost.CAMERA_SHOT -> "CAMERA_SHOT" + "(" + mode + ")";
            default -> "UNKNOWN_" + mode;
        };
    }
}
