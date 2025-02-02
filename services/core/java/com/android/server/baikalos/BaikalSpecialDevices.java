/*
 * Copyright (C) 2023 BaikalOS
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


import android.app.ActivityThread;
import android.app.PendingIntent;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;

import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.UserHandle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.KeyValueListParser;
import android.util.Slog;
import android.view.IWindowManager;

import com.android.server.trust.TrustManagerService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class BaikalSpecialDevices extends ContentObserver {

    private static final String TAG = "Baikal.SpecialDevices";

    private ContentResolver mResolver;
    private Handler mHandler;
    private Context mContext;

    private BaikalDevices mDevices;

    private boolean mActivate;
    private boolean mActivateInCall;
    private boolean mBTDevice;
    private boolean mWiFiDevice;
    private boolean mIsInCall;
    private long mLastInCall;
    private boolean mDeviceKeep;
    private boolean mActiveKeep;
    private boolean mActiveTotalKeep;

    private String mBluetoothDevices = "";
    private String mBluetoothLEDevices = "";
    private String mWiFiNetworks = "";

    private HashSet<String> mBluetoothDevicesSet = new HashSet<String>();
    private HashSet<String> mBluetoothLEDevicesSet = new HashSet<String>();
    private HashSet<String> mWiFiNetworksSet = new HashSet<String>();

    public boolean isInCall() {
        long now = SystemClock.uptimeMillis();
        if( mIsInCall ) { 
            mLastInCall = now;
            return true;
        }
        if( (now - mLastInCall) < 5000 ) return true;
        return false;
    }

    public BaikalSpecialDevices(Handler handler, Context context) {
        super(handler);
        mDevices = BaikalDevices.getInstance(handler,context);
        mDevices.setBaikalSpecialDevices(this);
	    mHandler = handler;
        mContext = context;
        mResolver = context.getContentResolver();

        try {

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SD_INCALL),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SD_KEEP),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SD_BT_DEV),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SD_BTLE_DEV),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.BAIKALOS_SD_WIFI_DEV),
                    false, this);

        } catch( Exception e ) {
        }

        loadConstants(mContext);
        updateConstants();
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        loadConstants(mContext);
        updateConstants();
    }

    private void updateConstants() {
        synchronized(this) {
            mBluetoothDevicesSet = setBlockedList(mBluetoothDevices);
            mBluetoothLEDevicesSet = setBlockedList(mBluetoothLEDevices);
            mWiFiNetworksSet = setBlockedList(mWiFiNetworks);
        }
        updateDevices();
    }

    public void loadConstants(Context context) {
        synchronized (this) {
            try {

                boolean activate = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.BAIKALOS_SD_KEEP,0) == 1;

                boolean activateInCall = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.BAIKALOS_SD_INCALL,0) == 1;

                String bluetoothDevices = Settings.Global.getString(context.getContentResolver(),
                        Settings.Global.BAIKALOS_SD_BT_DEV);

                String bluetoothLEDevices = Settings.Global.getString(context.getContentResolver(),
                        Settings.Global.BAIKALOS_SD_BTLE_DEV);

                String wiFiNetworks = Settings.Global.getString(context.getContentResolver(),
                        Settings.Global.BAIKALOS_SD_WIFI_DEV);

                boolean changed =   activate != mActivate ||
                                    activateInCall != mActivateInCall ||
                                    !mBluetoothDevices.equals(bluetoothDevices) ||
                                    !mBluetoothLEDevices.equals(bluetoothLEDevices) ||
                                    !mWiFiNetworks.equals(wiFiNetworks);

                if( changed ) {
                    mActivate = activate;
                    mActivateInCall = activateInCall;
                    mBluetoothDevices = bluetoothDevices == null ? "" : bluetoothDevices;
                    mBluetoothLEDevices = bluetoothLEDevices == null ? "" : bluetoothLEDevices;
                    mWiFiNetworks = wiFiNetworks == null ? "" : wiFiNetworks;
                }

            } catch (Exception e) {
                Slog.e(TAG, "Bad BaikalSpecialDevices settings ", e);
            }
        }
    }

    void updateDevices() { 
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateDevicesInternal();
            }
        });
    }

    void updateService() {
        mIsInCall = mDevices.isInCall();
        Slog.d(TAG, "mIsInCall=" + mIsInCall);
    }

    private boolean updateDevicesInternal() {
        boolean BTDevice = false;
        synchronized(this) {
            Iterator<String> nextItem = mBluetoothDevicesSet.iterator();

            while (nextItem.hasNext()) {
                String sAddress = nextItem.next();
                Slog.d(TAG, "Check Bluetooth device " + sAddress);
                
                if(mDevices.isDeviceConnected(sAddress) ) {
                    Slog.d(TAG, "Bluetooth device connected " + sAddress);
                    BTDevice = true;
                } 
            }

            if( !BTDevice ) {
                nextItem = mBluetoothLEDevicesSet.iterator();

                while (nextItem.hasNext()) {
                    String sAddress = nextItem.next();

                    Slog.d(TAG, "Check Bluetooth LE device " + sAddress);
                    if(mDevices.isDeviceConnected(sAddress) ) {
                        Slog.d(TAG, "Bluetooth LE device connected " + sAddress);
                        BTDevice = true;
                    } 
                }
            }

        }
        if( BTDevice != mBTDevice ) {
            mBTDevice = BTDevice;
            return true;
        }
        return false;
    }

    public boolean isDeviceActive() {
        boolean keep = mBTDevice || mWiFiDevice;
        if( keep != mDeviceKeep ) { Slog.i(TAG, "isDeviceActive: " + keep); mDeviceKeep = keep; }
        return keep;
    }

    public boolean isActive() {
        boolean keep = (mActivate || (isInCall() && mActivateInCall)) && isDeviceActive();
        if( keep != mActiveKeep ) { Slog.i(TAG, "isActive: " + keep); mActiveKeep = keep; }
        return keep;
    }

    public boolean isActiveTotal() {
        boolean keep = (isInCall() && mActivateInCall) || isDeviceActive();
        if( keep != mActiveTotalKeep ) { Slog.i(TAG, "isActiveTotal: " + keep); mActiveTotalKeep = keep; }
        return keep;
    }

    private HashSet<String> setBlockedList(String tagsString) {
        HashSet<String> mBlockedList = new HashSet<String>();
        if (tagsString != null && tagsString.length() != 0) {
            String[] parts = tagsString.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                mBlockedList.add(parts[i]);
            }
        }
        return mBlockedList;
    }

}
