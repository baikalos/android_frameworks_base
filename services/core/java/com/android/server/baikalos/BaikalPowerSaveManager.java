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

import com.android.server.LocalServices;

import java.util.ArrayList;
import java.util.List;

public class BaikalPowerSaveManager { 

    private static final String TAG = "Baikal.PowerSave";

    final Context mContext;
    final Handler mHandler;
    final Looper mLooper;

    private ManagerContentObserver mObserver;
    private ContentResolver mResolver;

    private int mPowerLevelOn = -1;
    private int mPowerLevelStandby = -1;
    private int mPowerLevelIdle = -1;

    static BaikalPowerSaveManager mInstance;
    static PowerManager mPowerManager;

    static BatterySaverPolicyConfig mLevelLow;
    static BatterySaverPolicyConfig mLevelModerate;
    static BatterySaverPolicyConfig mLevelArgessive;
    static BatterySaverPolicyConfig mLevelExtreme;

    private boolean mScreenOn;
    private boolean mIsPowered;
    private boolean mDeviceIdle;

    private int mCurrentPowerSaverLevel = -1;

    public static BaikalPowerSaveManager getInstance() {
        return mInstance;
    }

    public static BaikalPowerSaveManager getInstance(Looper looper, Context context) {
        if( mInstance == null ) {
            mInstance = new BaikalPowerSaveManager(looper,context);
        }
        return mInstance;
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

    private BaikalPowerSaveManager(Looper looper, Context context) {

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
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

            mLevelLow = new BatterySaverPolicyConfig.Builder()
                .setAdjustBrightnessFactor(1.0F)
                .setAdvertiseIsEnabled(false)
                .setDeferFullBackup(true)
                .setDeferKeyValueBackup(false)
                .setDisableAnimation(false)
                .setDisableAod(false)
                .setDisableLaunchBoost(false)
                .setDisableOptionalSensors(false)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED)
                .setDisableVibration(false)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(false)
                .setEnableNightMode(false)
                .setEnableQuickDoze(false)
                .setForceAllAppsStandby(false)
                .setForceBackgroundCheck(true)
                .setLocationMode(PowerManager.LOCATION_MODE_NO_CHANGE)
                .build();

            mLevelModerate = new BatterySaverPolicyConfig.Builder()
                .setAdjustBrightnessFactor(1.0F)
                .setAdvertiseIsEnabled(false)
                .setDeferFullBackup(true)
                .setDeferKeyValueBackup(false)
                .setDisableAnimation(false)
                .setDisableAod(false)
                .setDisableLaunchBoost(false)
                .setDisableOptionalSensors(false)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY)
                .setDisableVibration(false)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(false)
                .setEnableNightMode(false)
                .setEnableQuickDoze(false)
                .setForceAllAppsStandby(true)
                .setForceBackgroundCheck(true)
                .setLocationMode(PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF)
                .build();

            mLevelArgessive = new BatterySaverPolicyConfig.Builder()
                .setAdjustBrightnessFactor(1.0F)
                .setAdvertiseIsEnabled(false)
                .setDeferFullBackup(true)
                .setDeferKeyValueBackup(true)
                .setDisableAnimation(false)
                .setDisableAod(false)
                .setDisableLaunchBoost(false)
                .setDisableOptionalSensors(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED)
                .setDisableVibration(false)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(true)
                .setEnableNightMode(false)
                .setEnableQuickDoze(true)
                .setForceAllAppsStandby(true)                    
                .setForceBackgroundCheck(true)
                .setLocationMode(PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF)
                .build();

            mLevelExtreme = new BatterySaverPolicyConfig.Builder()
                .setAdjustBrightnessFactor(1.0F)
                .setAdvertiseIsEnabled(false)
                .setDeferFullBackup(true)
                .setDeferKeyValueBackup(true)
                .setDisableAnimation(true)
                .setDisableAod(true)
                .setDisableLaunchBoost(true)
                .setDisableOptionalSensors(true)
                .setSoundTriggerMode(PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED)
                .setDisableVibration(true)
                .setEnableAdjustBrightness(true)
                .setEnableDataSaver(false)
                .setEnableFirewall(true)
                .setEnableNightMode(false)
                .setEnableQuickDoze(true)
                .setForceAllAppsStandby(true)
                .setForceBackgroundCheck(true)
                .setLocationMode(PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF)
                .build();

        }
    }


    public void setScreenMode(boolean on) {
        if( on != mScreenOn ) {
           mScreenOn = on;
           updatePowerSaveLevel();  
        }
    }

    public void setIsPowered(boolean powered) {
        if( powered != mIsPowered ) {
           mIsPowered = powered;
           updatePowerSaveLevel();  
        }
    }

    public void setDeviceIdle(boolean idle) {
        if( idle != mDeviceIdle ) {
           mDeviceIdle = idle;
           updatePowerSaveLevel();  
        }
    }

    protected void updateConstantsLocked() {
        
        boolean changed = true;

        //mPowerManager.setAdaptivePowerSaveEnabled(false);

        int powerLevelOn = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_ON, 0);
        int powerLevelStandby = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_STANDBY, 0);
        int powerLevelIdle = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.BAIKALOS_POWER_LEVEL_IDLE, 0);

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

        if( changed ) updatePowerSaveLevel();

    }

    private void updatePowerSaveLevel() {

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) {
            Slog.i(TAG,"mPowerLevelOn=" + mPowerLevelOn);
            Slog.i(TAG,"mPowerLevelStandby=" + mPowerLevelStandby);
            Slog.i(TAG,"mPowerLevelIdle=" + mPowerLevelIdle);
        }
        
        int powerSaverLevel = 0;

        if( mScreenOn ) {
            powerSaverLevel = mPowerLevelOn;
        } else if( mDeviceIdle ) {
            powerSaverLevel = mPowerLevelIdle;
        } else {
            powerSaverLevel = mPowerLevelStandby;
        }


        if( powerSaverLevel != mCurrentPowerSaverLevel ) {

            mPowerManager.setAdaptivePowerSaveEnabled(false);
            if( powerSaverLevel > 0 ) {
                BatterySaverPolicyConfig policy = mLevelLow;
                switch(powerSaverLevel) {
                    case 2:
                        policy = mLevelModerate;
                        break;
                    case 3:
                        policy = mLevelArgessive;
                        break;
                    case 4:
                        policy = mLevelExtreme;
                        break;
                    case 1:
                    default:
                        policy = mLevelLow;
                        break;
                }
                mPowerManager.setAdaptivePowerSaveEnabled(true);
                mPowerManager.setAdaptivePowerSavePolicy(policy);
            }
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"mCurrentPowerSaverLevel=" + mCurrentPowerSaverLevel);
            mCurrentPowerSaverLevel = powerSaverLevel;
        }
    }

    public int getCurrentPowerSaverLevel() {
        return mCurrentPowerSaverLevel;
    }
}
