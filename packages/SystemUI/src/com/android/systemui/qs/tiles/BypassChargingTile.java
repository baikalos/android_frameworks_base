/*
 * Copyright (C) 2018 The OmniROM Project
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

import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.View;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.R;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.BluetoothController;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import javax.inject.Inject;

import com.android.internal.baikalos.BaikalConstants;

public class BypassChargingTile extends QSTileImpl<BooleanState> {
    private boolean mBPCEnabled;
    private boolean mListening;
    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_battery_saver_charging);
    private boolean isAvailable = false;


    @Inject
    public BypassChargingTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        if( !BaikalConstants.isKernelCompatible() ) return;
        isAvailable = true;

        mBPCEnabled = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0) == 1;
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public void handleClick(@Nullable View view) {
        mBPCEnabled = !mBPCEnabled;
        Settings.Global.putInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_BPCHARGE_FORCE,mBPCEnabled ? 1 : 0);
        refreshState();
    }


    @Override
    public Intent getLongClickIntent() {
	    return null;
    }


    @Override
    public CharSequence getTileLabel() {
        return "Bypass Charging"; //mContext.getString(R.string.quick_settings_stamina_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        mBPCEnabled = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.BAIKALOS_BPCHARGE_FORCE, 0) == 1;
        state.value = mBPCEnabled;
        state.slash.isSlashed = !state.value;
        state.icon = mIcon;
        if( mBPCEnabled )
            state.label = "Bypass Charging Enabled";  //mContext.getString(R.string.quick_settings_stamina_label);
        else
            state.label = "Bypass Charging Disabled";  //mContext.getString(R.string.quick_settings_stamina_label);

        if ( !mBPCEnabled ) {
            state.state = Tile.STATE_INACTIVE;
        } else {
            state.state = Tile.STATE_ACTIVE;
        }
    }

    @Override
    public int getMetricsCategory() {
        return 0;
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void handleSetListening(boolean listening) {
        if (mObserver == null) {
            return;
        }
        if (mListening != listening) {
            mListening = listening;
            if (listening) {
                mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_BPCHARGE_FORCE), false, mObserver);
            } else {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
            }
        }
    }
}
