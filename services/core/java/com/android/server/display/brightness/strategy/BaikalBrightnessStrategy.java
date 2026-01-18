/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.display.brightness.strategy;


import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.DisplayManagerInternal.DisplayPowerRequest;
import android.os.PowerManager;
import android.util.Slog;

import com.android.internal.baikalos.*;

import com.android.server.baikalos.*;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.brightness.BrightnessReason;
import com.android.server.display.brightness.BrightnessUtils;
import com.android.server.display.brightness.StrategyExecutionRequest;
import com.android.server.display.brightness.StrategySelectionNotifyRequest;

import java.io.PrintWriter;

/**
 * Manages the brightness of the display when the system brightness is overridden
 */
public class BaikalBrightnessStrategy implements DisplayBrightnessStrategy {

    private DisplayBrightnessStrategy mBaseDisplayBrightnessStrategy;
    private float mWindowManagerBrightnessOverride = PowerManager.BRIGHTNESS_INVALID_FLOAT;
    private boolean mIsActive = false;
    private int mOverrideType;
    private float mOverrideBrightness;

    final BaikalService mBaikalService;
    private static BaikalBrightnessStrategy sInstance;

    private BaikalBrightnessStrategy(DisplayBrightnessStrategy baseDisplayBrightnessStrategy) {
        mBaseDisplayBrightnessStrategy = baseDisplayBrightnessStrategy;
        mBaikalService = BaikalService.getInstance();
        mBaikalService.registerBaikalBrightnessStrategy(this);
    }

    public static BaikalBrightnessStrategy getInstance(DisplayBrightnessStrategy baseDisplayBrightnessStrategy) {
        if( sInstance == null ) {
            sInstance = new BaikalBrightnessStrategy(baseDisplayBrightnessStrategy);
        } else {
            sInstance.mBaseDisplayBrightnessStrategy = baseDisplayBrightnessStrategy;
        }
        return sInstance;
    }


    @Override
    public DisplayBrightnessState updateBrightness(
            StrategyExecutionRequest strategyExecutionRequest) {

        if( !mIsActive) return mBaseDisplayBrightnessStrategy.updateBrightness(strategyExecutionRequest);
        switch(mOverrideType) {
            case 1:
                return new DisplayBrightnessState.Builder()
                .setBrightness(mOverrideBrightness)
                .setBrightnessReason(BrightnessReason.REASON_OVERRIDE)
                .setDisplayBrightnessStrategyName("BaikalBrightnessStrategy")
                .build();
                //break;
            case 2:
                DisplayBrightnessState state = mBaseDisplayBrightnessStrategy.updateBrightness(strategyExecutionRequest);
                return DisplayBrightnessState.Builder.from(state)
                .setBrightness(state.getBrightness() * mOverrideBrightness)
                .setBrightnessReason(BrightnessReason.REASON_OVERRIDE)
                .setDisplayBrightnessStrategyName("BaikalBrightnessStrategy")
                .build();
            case 3:
                return mBaseDisplayBrightnessStrategy.updateBrightness(strategyExecutionRequest);
                //break;
            case 0:
            default:
                return mBaseDisplayBrightnessStrategy.updateBrightness(strategyExecutionRequest);
        }
    }

    @Override
    public String getName() {
        if( !mIsActive) return mBaseDisplayBrightnessStrategy.getName();
        return "BaikalBrightnessStrategy";
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("BaikalBrightnessStrategy:");
        writer.println("  mIsActive=" + mIsActive);
    }

    @Override
    public void strategySelectionPostProcessor(
            StrategySelectionNotifyRequest strategySelectionNotifyRequest) {
        mBaseDisplayBrightnessStrategy.strategySelectionPostProcessor(strategySelectionNotifyRequest);
    }

    @Override
    public int getReason() {
        if( !mIsActive) return mBaseDisplayBrightnessStrategy.getReason();
        return BrightnessReason.REASON_OVERRIDE;
    }

    public void overrideBrightness(boolean isActive, int type, float bightness) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i("BaikalBrightness","overrideBrightness: a=" + isActive + ", t=" + type + ", b=" + bightness);
        mIsActive = isActive;
        mOverrideType = type;
        mOverrideBrightness = bightness;
    }
}
