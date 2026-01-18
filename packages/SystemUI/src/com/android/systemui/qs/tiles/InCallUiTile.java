/*
 * Copyright (C) 2017-2018 Benzo Rom
 *           (C) 2017-2025 crDroidAndroid Project
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
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.flags.BaikalFlags;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.util.settings.GlobalSettings;
import com.android.systemui.util.settings.SettingObserver;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;


import javax.inject.Inject;

public class InCallUiTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "incallui";
    static final boolean DEBUG = true;
    static final String TAG = "InCallUiTile";


    @Nullable
    private Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_sim_card);

    private static final Intent SWITCH_INTENT = new Intent("com.android.internal.baikalos.Actions.ACTION_SWITCH_INCALLUI");


    @Inject
    public InCallUiTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            GlobalSettings globalSettings) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        if (DEBUG) Log.d(TAG, ".ctor");
    }

    @Override
    public boolean isAvailable() {
        if (DEBUG) Log.d(TAG, "isAvailable");
        return true; //BaikalFlags.Instance().get("boost_tile").getVisibility(true);
    }


    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        if (DEBUG) Log.d(TAG, "newTileState:" + state);
        return state;
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        // TODO:
        if (DEBUG) Log.d(TAG, "handleClick:" + expandable);
        mContext.sendBroadcastAsUser(SWITCH_INTENT, UserHandle.ALL);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (DEBUG) Log.d(TAG, "handleUpdateState:" + state + "," + arg);
        state.state = Tile.STATE_INACTIVE;
        state.icon = mIcon;
        state.label = mContext.getString(R.string.quick_settings_incallui_label);
        state.secondaryLabel = "";
        state.contentDescription = state.label;
        state.value = false;
        state.expandedAccessibilityClassName = Switch.class.getName();

    }

    @Override
    public CharSequence getTileLabel() {
        if (DEBUG) Log.d(TAG, "getTileLabel");
        return mContext.getString(R.string.quick_settings_incallui_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (DEBUG) Log.d(TAG, "handleSetListening");
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        if (DEBUG) Log.d(TAG, "handleDestroy");
    }
}
