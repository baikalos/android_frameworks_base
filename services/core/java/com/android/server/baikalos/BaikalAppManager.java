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

import android.baikalos.*;
import com.android.internal.baikalos.*;

import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;

import android.hardware.power.Mode;
import android.hardware.power.Boost;

import android.os.IPowerManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;

import android.provider.Settings;


import com.android.server.display.brightness.strategy.BaikalBrightnessStrategy;
import com.android.server.ServiceThread;


public class BaikalAppManager { 

    private static final String TAG = "BaikalAppManager";

    final BaikalService mService;
    BaikalBrightnessStrategy mDisplayBrightnessStrategy;
    BaikalThermalManager mBaikalThermalManager;

    BaikalAppProfile mTopAppProfile = new BaikalAppProfile(1000);

    int mCurrentPerformanceProfile = -2;
    int mCurrentThermalProfile = -2;

    int mPrevPowerModeUid = -1;
    int mPrevPowerMode = -1;
    int mCurrPowerModeUid = -1;
    int mCurrPowerMode = -1;

    private final Context mContext;
    private ServiceThread mWorker;
    private Handler mHandler;

    String mTopAppPackageName = "system";
    int mTopAppUid = -1;
    int mCurrentBrightness = -1;

    int mWakefulness = 1;
    boolean mIdleMode = false;
    boolean mCharging;

    public String getTopAppPackageName() {
        return mTopAppPackageName;
    }

    public int getTopAppUid() {
        return mTopAppUid;
    }

    public BaikalAppProfile getTopAppProfileInternal() {
        return mTopAppProfile;
    }

    BaikalAppManager(BaikalService parent, Context context) {
        mService = parent;
        mContext = context;
        mWorker = new ServiceThread(TAG, android.os.Process.THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());
        mBaikalThermalManager = new BaikalThermalManager(mContext);
    }

    public void onSystemReady() {
        synchronized(this) {
            mBaikalThermalManager.initialize();
            updateStateLocked();
        }
    }

    public void onTopAppChanged(String packageName, int uid) {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                synchronized(this) {
                    onTopAppChangedLocked(packageName,uid); 
                }
            }
        });
    }

    public void onTopAppChangedLocked(String packageName, int uid) {
        if( packageName != null )  packageName = packageName.split(":")[0];
        if( mService.getSettings().isGmsUid(uid) && packageName != null && packageName.startsWith("com.google.android.gms.") ) packageName = "com.google.android.gms";
        if( packageName == null ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setTopApp: null!!", new Throwable());
            // packageName = "android";
            updateStateLocked();
            return;
        }

        if( uid != mTopAppUid || !packageName.equals(mTopAppPackageName) ) {
            mTopAppUid = uid;
            mTopAppPackageName = packageName;

            BaikalAppProfile profile = mService.getBaikalAppProfileNotNullInternal(uid);
            
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"setTopApp:" + mTopAppPackageName + ", " + BaikalAppProfileHelper.dumpProfile(profile));
            mTopAppProfile = profile;

            updateStateLocked();

        }
    }

    public void setCharging(boolean charging) {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                synchronized(this) {
                    onChargingChangedLocked(charging); 
                }
            }
        });
    }

    public void setWakefulness(int wakefulness) {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                synchronized(this) {
                    onWakefulnessChangedLocked(wakefulness); 
                }
            }
        });
    }

    public void setDeviceIdleMode(boolean mode) {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                synchronized(this) {
                    onIdleModeChangedLocked(mode); 
                }
            }
        });
    }

    public void updateSettings() {
        mHandler.post( new Runnable() {
            @Override
            public void run() { 
                synchronized(this) {
                    updateStateLocked(); 
                }
            }
        });
    }


    public void setScreenMode(boolean mode) {
    }

    public void setPhoneCallState(boolean mode) {
    }

    public void setCarMode(boolean mode) {
    }

    private void updateServiceLocked() {
    }

    private void updateStateLocked() {
        updateTopAppLocked();
        updatePowerModeLocked();
        updateThermalLocked();
    }

    private void onIdleModeChangedLocked(boolean mode) {
        if( mIdleMode != mode ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"onIdleModeChangedLocked:" + mode);
            mIdleMode = mode;
            updateStateLocked();
        }
    }

    private void onChargingChangedLocked(boolean charging) {
        if( charging != mCharging ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"onChargingChangedLocked:" + charging);
            mCharging = charging;
            updateStateLocked();
        }
    }

    private void onWakefulnessChangedLocked(int wakefulness) {
        if( wakefulness != mWakefulness ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"onWakefulnessChangedLocked:" + wakefulness);
            mWakefulness = wakefulness;
            updateStateLocked();
        }
    }


    private void updateTopAppLocked() {

        Settings.Global.putInt(mContext.getContentResolver(), 
            Settings.Global.BAIKALOS_BLOCK_OVERLAYS,            
            ((mTopAppProfile.mAppOpts & BaikalAppProfile.BAIKAL_APP_BLOCK_OVERLAYS) != 0) ? 1 : 0 );

        Settings.Global.putInt(mContext.getContentResolver(), 
            Settings.Global.BAIKALOS_BPCHARGE_TOP_APP,            
            ((mTopAppProfile.mAppOpts & BaikalAppProfile.BAIKAL_APP_BYPASS_CHARGING) != 0) ? 1 : 0 );

        setRotationLocked(mTopAppProfile.mRotation-1);
        updateDisplayBrightnessStrategy(mTopAppProfile);
        updateRefreshRate(mTopAppProfile.mMinFrameRate,mTopAppProfile.mMaxFrameRate);
    }

    private void updateThermalLocked() {
        if( mBaikalThermalManager != null ) {

            if( mWakefulness != 1 ) {
                int profile = mService.getSettings().mDefaultScreenOffThermalProfile;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updateThermalLocked: mDefaultScreenOffThermalProfile=" + profile);
                if( profile < 0 ) profile = 0;
                mBaikalThermalManager.updateThermalProfile(String.valueOf(profile));
                return;
            }

            int profile = mTopAppProfile != null ? mTopAppProfile.mThermalProfile : 0;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updateThermalLocked: mAppProfile=" + profile);
            if( profile == 0 ) {
                profile = mService.getSettings().mDefaultThermalProfile;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updateThermalLocked: mDefaultThermalProfile=" + profile);
            }
            if( profile < 0 ) {
                profile = 0;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updateThermalLocked: forced balanced profile=" + profile);
            }
            mBaikalThermalManager.updateThermalProfile(String.valueOf(profile));
        }
    }

    private void updatePowerModeLocked() {
        if( mWakefulness != 1 ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: mWakefulness=" + mWakefulness);
            int mode = mService.getSettings().mDefaultScreenOffPerformanceProfile;
            if( mPrevPowerMode != mode || mPrevPowerModeUid != Process.SYSTEM_UID ) {
                if( mPrevPowerMode != -1 ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: cancel prev mode:" + mPrevPowerMode + " for :" + mPrevPowerModeUid);
                    mService.mBaikalPowerManager.setPowerModeInternal(mPrevPowerMode,false,mPrevPowerModeUid);
                }
            }
            if( mode > 0 ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: set idle mode:" + mode + " for SYSTEM_UID");
                mPrevPowerMode = mode;
                mPrevPowerModeUid = Process.SYSTEM_UID;
                mService.mBaikalPowerManager.setPowerModeInternal(mode,true,mPrevPowerModeUid);
                setPerfProfileProperty(mPrevPowerMode);
            } else {
                mPrevPowerMode = -1;
                mPrevPowerModeUid = -1;
                setPerfProfileProperty(-1);
            }
            return;
        }

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: mWakefulness=" + mWakefulness + 
                " mCurrPowerMode=" + mCurrPowerMode +
                " mCurrPowerModeUid=" + mCurrPowerModeUid +
                " mPrevPowerMode=" + mPrevPowerMode +
                " mPrevPowerModeUid=" + mPrevPowerModeUid +
                " mPerfProfile=" + mTopAppProfile.mPerfProfile + 
                " mDefaultPerformanceProfile=" + mService.getSettings().mDefaultPerformanceProfile);

        int mode = mTopAppProfile.mPerfProfile;
        if( mode == 0 ) {
            mode = mService.getSettings().mDefaultPerformanceProfile;
        }

        if( mode > 0 ) {
            mCurrPowerMode = mode;
            mCurrPowerModeUid = mTopAppUid;
        } else {
            mCurrPowerMode = -1;
            mCurrPowerModeUid = -1;
        }

        if( mCurrPowerMode != mPrevPowerMode || mCurrPowerModeUid != mPrevPowerModeUid ) { 
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: requested mode change detected:" + mPrevPowerMode + "/" + mCurrPowerMode + "|" + mPrevPowerModeUid + "/" + mCurrPowerModeUid);
            if( mPrevPowerModeUid != -1 /*&& (mCurrPowerModeUid != mPrevPowerModeUid)*/ ) {
                if( mPrevPowerMode != -1 ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: cancel prev mode:" + mPrevPowerMode + " for :" + mPrevPowerModeUid);
                    mService.mBaikalPowerManager.setPowerModeInternal(mPrevPowerMode,false,mPrevPowerModeUid);
                    mPrevPowerMode = -1;
                }
                mPrevPowerModeUid = -1;
            }
        }


        if( mCurrPowerMode > 0 ) {
            if( mCurrPowerMode != mPrevPowerMode || mCurrPowerModeUid != mPrevPowerModeUid ) {
                mPrevPowerMode = mCurrPowerMode;
                mPrevPowerModeUid = mCurrPowerModeUid;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG,"updatePowerModeLocked: set new mode:" + mPrevPowerMode + " for :" + mPrevPowerModeUid);
                mService.mBaikalPowerManager.setPowerModeInternal(mPrevPowerMode,true,mPrevPowerModeUid);
                setPerfProfileProperty(mode);
            }
        } else {
            mPrevPowerMode = -1;
            mPrevPowerModeUid = -1;
            setPerfProfileProperty(-1);
        }
    }

    private void updateRefreshRate(int minFps, int maxFps) {

        BaikalSettings settings = BaikalSettings.getInstance();

        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE,
                calculateRefreshRate(minFps, settings.mDefaultMinFps, settings.mSystemDefaultMinFps ));

        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE,
                calculateRefreshRate(maxFps, settings.mDefaultMaxFps, settings.mSystemDefaultMaxFps ));

    }
    
    private float calculateRefreshRate(int rate, float def, float systemDef) {
        if( rate != 0 && rate != -1 ) return (float)rate;
        if( def > 0.1 ) return def;
        return systemDef;
    }

    private void setRotationLocked(final int rotation) {  

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

    void registerBaikalBrightnessStrategy(BaikalBrightnessStrategy strategy) {
        mDisplayBrightnessStrategy = strategy;
        if( mTopAppProfile != null ) updateDisplayBrightnessStrategy(mTopAppProfile);
    }

    void updateDisplayBrightnessStrategy(BaikalAppProfile profile) {
        boolean active;
        int type;
        float brightness;
        switch(profile.mBrightness) {
            case 13: // brightness_enable_ab
                active = true;
                type = 2;
                brightness = 1.0F;
                break;

            case 14: // brightness_disable_ab
                active = true;
                type = 3;
                brightness = 1.0F;
                break;

            case 10: // brightness_reduced
                active = true;
                type = 2;
                brightness = 0.5F;
                break;

            case 12: // brightness_quarter
                active = true;
                type = 2;
                brightness = 0.25F;
                break;

            case 11: // brightness_full
                active = true;
                type = 1;
                brightness = 1.0F;
                break;
            
            case 15: // brightness_increased
                active = true;
                type = 2;
                brightness = 1.25F;
                break;

            case 1: // (b * 10) / 100;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                active = true;
                type = 1;
                brightness = 1.0F;
                break;

            case 0: // default
            default:
                active = false;
                type = 0;
                brightness = ((float)profile.mBrightness) / 10.0F;
                break;
        }

        if( profile.mBrightness != mCurrentBrightness ) {
            mDisplayBrightnessStrategy.overrideBrightness(active,type,brightness);

            Settings.Global.putInt(mContext.getContentResolver(), 
                Settings.Global.BAIKALOS_BRIGHTNESS_MODE,            
                profile.mBrightness);

            mCurrentBrightness = profile.mBrightness;
        }
    }

    void setPerfProfileProperty(int mode) {
        try {
            SystemProperties.set("baikal.eng.perf.cur_profile",getPerfProfileName(mode));
        } catch(Exception e) {
        }
    }

    String getPerfProfileName(int mode) {
        return switch(mode) {
            case -1 -> "default";
            case Mode.LOW_POWER -> "low";
            case Mode.SUSTAINED_PERFORMANCE -> "sustain";
            case Mode.FIXED_PERFORMANCE -> "fixed";
            case Mode.VR -> "vr";
            case Mode.LAUNCH -> "launch";
            case Mode.EXPENSIVE_RENDERING -> "render";
            case Mode.INTERACTIVE -> "balanced";
            case Mode.DEVICE_IDLE -> "idle";
            case Mode.DISPLAY_INACTIVE -> "inactive";
            case Mode.AUDIO_STREAMING_LOW_LATENCY -> "audio";
            case Mode.CAMERA_STREAMING_SECURE -> "secure";
            case Mode.CAMERA_STREAMING_LOW -> "cam_low";
            case Mode.CAMERA_STREAMING_MID -> "cam_mid";
            case Mode.CAMERA_STREAMING_HIGH -> "cam_high";
            case Mode.GAME -> "game";
            case Mode.GAME_LOADING -> "loading";
            case 17 -> "display";
            default -> "unknown";
        };
    }



    public boolean isApplicationBackgroundRestricted(int uid, String packageName) {

        if( uid == mTopAppUid ) return false;
        BaikalAppProfile profile = mService.getBaikalAppProfileNotNullInternal(uid);

        if( profile.isDebug() ) return isApplicationBackgroundRestrictedDebug(profile,uid,packageName);

        long timeout = SystemClock.elapsedRealtime() - profile.getLastActive();
        if( timeout < 10 * 1000 ) return false;
        if( mService.isUidActive(uid) && profile.mBackgroundLevel < 45 ) return false;

        if( profile.isImportant() ) return false;

        if(mService.getSettings().isSystemUiUid(uid)) return false;
        
        if( (profile.mBackgroundMode & ( BaikalAppProfile.BAIKAL_BACKGROUND_DISABLED | BaikalAppProfile.BAIKAL_BACKGROUND_BOOT_DISABLED )) != 0 ) return true;
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0 ) return true;

        if(mService.getSettings().isGmsUid(uid)) return false;
        if(mService.getSettings().isAaUid(uid)) return false;

        if( profile.isProtected() ) return false;

        if( profile.isImportant() ) return false;
        if( profile.isWhitelisted() ) return false;

        if( profile.mBackgroundLevel >= 45 ) return true;
        if( profile.mBackgroundLevel >= 30 || profile.mBackgroundLevel == 0 ) {
            if( profile.isSystem() ) return false;
            var policy = mService.mBaikalPowerManager.getCurrentPolicy();
            if( policy.autoLimitBackground ) return true;
        }

        return false;
    }

    public boolean isApplicationBackgroundRestrictedDebug(BaikalAppProfile profile, int uid, String packageName) {
        
        long timeout = SystemClock.elapsedRealtime() - profile.getLastActive();
        if( timeout < 10 * 1000 ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", timeout=" + timeout);
            return false;
        }
        if( mService.isUidActive(uid) && profile.mBackgroundLevel < 45 ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", uid is active");
            return false;
        }

        if( profile.isImportant() ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is important");
            return false;
        }
        
        if( (profile.mBackgroundMode & ( BaikalAppProfile.BAIKAL_BACKGROUND_DISABLED | BaikalAppProfile.BAIKAL_BACKGROUND_BOOT_DISABLED )) != 0 ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is disabled or boot is disabled");
            return true;
        }
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0 ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is restricted");
            return true;
        }

        if(mService.getSettings().isGmsUid(uid)) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is GMS");
            return false;
        }
        if(mService.getSettings().isSystemUiUid(uid)) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is SystemUI");
            return false;
        }
        if(mService.getSettings().isAaUid(uid)) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is AA");
            return false;
        }

        if( profile.isProtected() ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is protected");
            return false;
        }

        if( profile.isWhitelisted() ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is whitelisted");
            return false;
        }

        if( profile.mBackgroundLevel >= 45 ) {
            Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app level is restricted");
            return true;
        }
        if( profile.mBackgroundLevel >= 30 || profile.mBackgroundLevel == 0 ) {
            var policy = mService.mBaikalPowerManager.getCurrentPolicy();
            if( policy.autoLimitBackground ) {
                Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is auto restricted");
                return true;    
            }
        }

        Slog.i(TAG,"isApplicationBackgroundRestrictedDebug:" + packageName + "/" + uid + ", app is not restricted");
        return false;
    }
}
