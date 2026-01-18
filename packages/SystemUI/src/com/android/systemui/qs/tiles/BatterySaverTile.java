/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.UserSettingObserver;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.util.settings.GlobalSettings;
import com.android.systemui.util.settings.SettingObserver;

import javax.inject.Inject;

public class BatterySaverTile extends QSTileImpl<BooleanState> implements
        BatteryController.BatteryStateChangeCallback {

    public static final String TILE_SPEC = "battery";
    static final boolean DEBUG = true;
    static final String TAG = "BatterySaverTile";

    private final BatteryController mBatteryController;
    @VisibleForTesting
    protected SettingObserver mSetting;

    private GlobalSettings mGlobalSettings;

    private int mLevel;
    private boolean mPowerSave;
    private boolean mCharging;
    private boolean mPluggedIn;

    @Inject
    public BatterySaverTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BatteryController batteryController,
            GlobalSettings globalSettings
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mGlobalSettings = globalSettings;
        mBatteryController = batteryController;
        mBatteryController.observe(getLifecycle(), this);

    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    protected void handleInitialize() {
        mSetting = new SettingObserver(mGlobalSettings, mHandler, Global.BAIKALOS_EXTREME_IDLE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                // mHandler is the background handler so calling this is OK
                if (DEBUG) Log.d(TAG, "handleValueChanged: value=" + value );
                refreshState(null);
            }
        };
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mSetting.setListening(false);
    }

    //@Override
    //protected void handleUserSwitch(int newUserId) {
    //    mSetting.setUserId(newUserId);
    //}

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_BATTERY_TILE;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        mSetting.setListening(listening);
        if (!listening) {
            // If we stopped listening, it means that the tile is not visible. In that case, we
            // don't need to save the view anymore
            mBatteryController.clearLastPowerSaverStartExpandable();
        }
    }

    @Nullable
    @Override
    public Intent getLongClickIntent() {
        return null; //return new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (DEBUG) Log.d(TAG, "handleClick: value=" + mState.value );
        mSetting.setValue(mState.value ? 0 : 1);
        refreshState();

        //mBatteryController.setPowerSaveMode(!mPowerSave, expandable);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.battery_detail_switch_title);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mSetting == null) {
            if (DEBUG) Log.d(TAG, "handleSetListening: mSetting=null!");
            return;
        }
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        if (DEBUG) Log.d(TAG, "handleSetListening: value=" + value);
        mPowerSave = value != 0;
        state.state = mPowerSave ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.icon = maybeLoadResourceIcon(mPowerSave
                ? R.drawable.qs_battery_saver_icon_on : R.drawable.qs_battery_saver_icon_off);
        state.label = mContext.getString(R.string.battery_detail_switch_title);
        state.secondaryLabel = "";
        state.contentDescription = state.label;
        state.value = mPowerSave;
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    //@Override
    //public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
    //    mLevel = level;
    //    mPluggedIn = pluggedIn;
    //    mCharging = charging;
    //    refreshState(level);
    //}

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        if (DEBUG) Log.d(TAG, "onPowerSaveChanged: isPowerSave=" + isPowerSave);
        mPowerSave = isPowerSave;
        refreshState(null);
    }
}
