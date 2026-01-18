/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.power;

import static android.os.PowerManager.BRIGHTNESS_OFF_FLOAT;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUPS_TURNED_OFF;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUP_REMOVED;
import static android.os.PowerManager.WAKE_REASON_DISPLAY_GROUP_ADDED;
import static android.os.PowerManager.WAKE_REASON_DISPLAY_GROUP_TURNED_ON;
import static android.os.PowerManagerInternal.MODE_DEVICE_IDLE;
import static android.os.PowerManagerInternal.MODE_DISPLAY_INACTIVE;
import static android.os.PowerManagerInternal.WAKEFULNESS_ASLEEP;
import static android.os.PowerManagerInternal.WAKEFULNESS_AWAKE;
import static android.os.PowerManagerInternal.WAKEFULNESS_DOZING;
import static android.os.PowerManagerInternal.WAKEFULNESS_DREAMING;
import static android.os.PowerManagerInternal.isInteractive;
import static android.os.PowerManagerInternal.wakefulnessToString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.baikalos.*;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.GoToSleepReason;
import android.os.PowerManager.ServiceType;
import android.os.PowerManager.WakeReason;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.baikalos.*;
import com.android.server.baikalos.*;

import com.android.server.baikalos.*;

public final class BaikalPowerManagerService {

    static final String TAG = "BaikalPowerManagerService";

    private final Context mContext;
    private final IBaikalInternal mBaikal;
    private final PowerManagerService mPower;
    private BaikalService mBaikalService;
    private BaikalSpecialDevices mBaikalDevices;

    BaikalPowerManagerService(PowerManagerService service, Context context) {
        mContext = context;
        mPower = service;
        mBaikal = BaikalService.getService();
        if( mBaikal == null )  {
            Log.w(TAG,"Baikal service not ready yet, postpone");
        } else {
            Log.w(TAG,"Baikal service ready");
            mBaikal.setBaikalPowerManagerService(this);
        }

        mBaikalService = BaikalService.getInstance();
    }

    public PowerManagerService getPowerManagerService() {
        return mPower;
    }

    void onChargingChanged(boolean mIsPowered,int mPlugType,int mBatteryLevel,boolean isOverheat) {
        if( PowerManagerService.DEBUG ) Log.w(TAG,"onChargingChanged:" + mIsPowered + "," + mPlugType + "," + mBatteryLevel + "," + isOverheat);
        BaikalActions.sendChargerModeChanged(mIsPowered);
        
    }

    void onWakefulnessChangeStarted(int newWakefulness) {
        if( PowerManagerService.DEBUG ) Log.w(TAG,"onWakefulnessChangeStarted:" + newWakefulness);
    }

    void onWakefulnessChangeCompleted(int newWakefulness) {
        if( PowerManagerService.DEBUG ) Log.w(TAG,"onWakefulnessChangeCompleted:" + newWakefulness);
        BaikalActions.sendWakefulnessChanged(newWakefulness);
    }

    void onScreenModeChanged(boolean on) {
        if( PowerManagerService.DEBUG ) Log.w(TAG,"onScreenModeChanged:" + on);
        BaikalActions.sendScreenModeChanged(on);
    }

    void userActivityNoUpdateLocked(final PowerGroup powerGroup, long eventTime,
        @PowerManager.UserActivityEvent int event, int flags, int uid) {
        if( PowerManagerService.DEBUG_SPEW ) Log.w(TAG,"userActivityNoUpdateLocked:");
    }

    boolean setPowerBoostInternal(int boost, int durationMs) {
        //if( PowerManagerService.DEBUG_SPEW ) Log.w(TAG,"setPowerBoostInternal:" + boost + ", " + durationMs);
        int result = mBaikalService.onSetPowerBoostInternal(this, boost, durationMs);
        if( result != -1 ) return result != 0;
        return mPower.setPowerBoostInternalFromBaikalWrapper(boost, durationMs);
    }

    int setPowerModeInternal(int mode, boolean enabled, int uid) {
        //if( PowerManagerService.DEBUG ) Log.w(TAG,"setPowerModeInternal:" + mode + ", " + enabled);
        int result = mBaikalService.onSetPowerModeInternal(this, mode, enabled, uid);
        if( result != -1 ) return result;
        if( mPower.setPowerModeInternalFromBaikalWrapper(mode, enabled) ) return 1;
        return 0;
    }

    public boolean isKeepOn() {
        if( !(mPower.getGlobalWakefulnessLocked() == WAKEFULNESS_AWAKE) ) return false;
        if( mBaikalDevices == null ) {
            try {
                mBaikalDevices = BaikalSpecialDevices.getInstance();
            } catch(Exception e) {
                Log.w(TAG,"isKeepOn: mBaikalDevices = null",e);
            }
        }
        if( mBaikalDevices == null ) {
            Log.w(TAG,"isKeepOn: mBaikalDevices = null");
            return false;
        }
        if( mBaikalDevices.isActive() ) return true;
        switch( mBaikalService.getTopAppProfileInternal().mKeepOn ) {
            case 1:
                return true;
            case 2:
                if( mPower.mIsPowered ) return true;
                break;
            case 3:
                if( mBaikalDevices.isDeviceActive() ) return true;
                break;
            case 4:
                if( mBaikalDevices.isDeviceActive() && mPower.mIsPowered ) return true;
                break;
        
        }
        return false;
    }
}
