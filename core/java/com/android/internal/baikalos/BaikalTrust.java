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

package com.android.internal.baikalos;


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

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.UserHandle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.KeyValueListParser;
import android.util.Slog;
import android.view.IWindowManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class BaikalTrust extends ContentObserver {

    private static final String TAG = "Baikal.Trust";

    private static ContentResolver mResolver;
    private static Handler mHandler;
    private static Context mContext;
    private static Object _staticLock = new Object();

    private static boolean mTrustEnabled;
    private static boolean mTrustAlways;
    private static boolean mTrustedBTDevice;
    private static boolean mTrustedWiFiDevice;

    private static String mTrustedBluetoothDevices = "";
    private static String mTrustedBluetoothLEDevices = "";
    private static String mTrustedWiFiNetworks = "";

    private static HashSet<String> mTrusedBluetoothDevicesSet = new HashSet<String>();
    private static HashSet<String> mTrusedBluetoothLEDevicesSet = new HashSet<String>();
    private static HashSet<String> mTrusedWiFiNetworksSet = new HashSet<String>();

    private static HashSet<String> mConnectedBluetoothDevices = new HashSet<String>();
    private static HashSet<String> mConnectedWiFiNetworks = new HashSet<String>();

    public static boolean isTrustEnabled() {
        return mTrustEnabled;
    }

    public static boolean isTrustAlways() {
        return mTrustAlways;
    }

    public static String getTrustedBluetoothDevices() {
        return mTrustedBluetoothDevices;
    }

    public static String getTrustedBluetoothLEDevices() {
        return mTrustedBluetoothLEDevices;
    }

    public static String getTrustedWiFiNetworks() {
        return mTrustedWiFiNetworks;
    }

    public BaikalTrust(Handler handler, Context context) {
        super(handler);
	    mHandler = handler;
        mContext = context;
        mResolver = context.getContentResolver();

        try {
                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_ENABLED),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_ALWAYS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_BT_DEV),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_BTLE_DEV),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_WIFI_DEV),
                    false, this);

        } catch( Exception e ) {
        }

        updateConstants();
        loadConstants(mContext);

        IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        btFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        mContext.registerReceiver(mBluetoothReceiver, btFilter);

        IntentFilter btAdapterFilter = new IntentFilter();
        btAdapterFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        btAdapterFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        btAdapterFilter.addAction(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        //btAdapterFilter.addAction(BluetoothAdapter.ACTION_BLE_ACL_CONNECTED);
        //btAdapterFilter.addAction(BluetoothAdapter.ACTION_BLE_ACL_DISCONNECTED);
        mContext.registerReceiver(mBluetoothAdapterReceiver, btAdapterFilter);

    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        loadConstants(mContext);
        updateConstants();
    }

    private void updateConstants() {
        synchronized (_staticLock) {
            updateConstantsLocked();
        }
    }

    public static void loadConstants(Context context) {
        synchronized (_staticLock) {
            try {
                boolean trustEnabled = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_ENABLED,0) == 1;

                boolean trustAlways = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_ALWAYS,0) == 1;

                String trustedBluetoothDevices = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_BT_DEV);

                String trustedBluetoothLEDevices = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_BTLE_DEV);

                String trustedWiFiNetworks = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_WIFI_DEV);

                boolean trustChanged = trustEnabled != mTrustEnabled ||
                                        trustAlways != mTrustAlways ||
                                        !mTrustedBluetoothDevices.equals(trustedBluetoothDevices) ||
                                        !mTrustedBluetoothLEDevices.equals(trustedBluetoothLEDevices) ||
                                        !mTrustedWiFiNetworks.equals(trustedWiFiNetworks);

                if( trustChanged ) {
                    mTrustEnabled = trustEnabled;
                    mTrustAlways = trustAlways;
                    mTrustedBluetoothDevices = trustedBluetoothDevices == null ? "" : trustedBluetoothDevices;
                    mTrustedBluetoothLEDevices = trustedBluetoothLEDevices == null ? "" : trustedBluetoothLEDevices;
                    mTrustedWiFiNetworks = trustedWiFiNetworks == null ? "" : trustedWiFiNetworks;
                    updateTrust();
                }

            } catch (Exception e) {
                Slog.e(TAG, "Bad BaikalService settings ", e);
            }
        }
    }

    private static void updateConstantsLocked() {
        //updateFilters();
        
    }

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Slog.i(TAG,"device broadcast recevied: intent=" + intent);
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                updateBluetoothDeviceState(0,device.getAddress());
            } else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                updateBluetoothDeviceState(1,device.getAddress());
            } else if(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                updateBluetoothDeviceState(2,device.getAddress());
            }
        }
    };

    private final BroadcastReceiver mBluetoothAdapterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           	Slog.d(TAG,"adapter broadcast recevied: intent=" + intent);
            String action = intent.getAction();
            if( BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                updateBluetoothDeviceState(state);
            } 
        }
    };

    private static Object _trustDevicesLock = new Object();

    private static void updateTrust() {
        synchronized(_trustDevicesLock) {
            mTrusedBluetoothDevicesSet = setBlockedList(mTrustedBluetoothDevices);
            mTrusedBluetoothLEDevicesSet = setBlockedList(mTrustedBluetoothLEDevices);
            mTrusedWiFiNetworksSet = setBlockedList(mTrustedWiFiNetworks);
        }
        updateTrustedDevices();
    }

    public static boolean isTrusted() {
        if( !mTrustEnabled ) {
            Slog.i(TAG, "isTrusted: disabled");
            return false;
        }
        if( mTrustAlways ) {
            Slog.i(TAG, "isTrusted: always");
            return true;
        }
        if( mTrustedBTDevice ) {
            Slog.i(TAG, "isTrusted: trusted BT device");
            return true;
        }

        if( mTrustedWiFiDevice ) {
            Slog.i(TAG, "isTrusted: trustedWiFi  device");
            return true;
        }

        Slog.i(TAG, "isTrusted: not trusted");
        return false;
    }

    private static void updateTrustedDevices() {
        synchronized(_trustDevicesLock) {
            Iterator<String> nextItem = mTrusedBluetoothDevicesSet.iterator();

            while (nextItem.hasNext()) {
                String sAddress = nextItem.next();
                
                if(mConnectedBluetoothDevices.contains(sAddress) ) {
                    Slog.e(TAG, "Trusted bluetooth device connected " + sAddress);
                    mTrustedBTDevice = true;
                    return;
                } 
            }

            nextItem = mTrusedBluetoothLEDevicesSet.iterator();

            while (nextItem.hasNext()) {
                String sAddress = nextItem.next();
                if(mConnectedBluetoothDevices.contains(sAddress) ) {
                    Slog.e(TAG, "Trusted bluetooth LE device connected " + sAddress);
                    mTrustedBTDevice = true;
                    return;
                } 
            }

        }
        Slog.e(TAG, "No Trusted bluetooth devices connected ");
        mTrustedBTDevice = false;
    }

    public static void updateBluetoothDeviceState(int state) {
        switch(state) { 
            case BluetoothAdapter.STATE_ON:
                Slog.e(TAG, "Bluetooth ready");
                break;
            default:
                Slog.e(TAG, "Bluetooth not ready. Disable device trust");
                mConnectedBluetoothDevices.clear();
                updateTrustedDevices();
        }
    }


    public static void updateBluetoothDeviceState(int state,String device_address) {
        if( state == 0 ) {
            if( !mConnectedBluetoothDevices.contains(device_address) ) {
                mConnectedBluetoothDevices.add(device_address);
                updateTrustedDevices();
            }
        } else if( state == 1 || state == 2 )  {
            if( mConnectedBluetoothDevices.contains(device_address) ) {
                mConnectedBluetoothDevices.remove(device_address);
                updateTrustedDevices();
            }
        }
    }

    private static HashSet<String> setBlockedList(String tagsString) {
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
