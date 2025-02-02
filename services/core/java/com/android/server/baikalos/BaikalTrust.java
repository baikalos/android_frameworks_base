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

public class BaikalTrust extends ContentObserver {

    private static final String TAG = "Baikal.Trust";

    private ContentResolver mResolver;
    private Handler mHandler;
    private Context mContext;

    private TelephonyManager mTelephonyManager;
    private TrustManagerService mTm;
    private BaikalDevices mDevices;

    private boolean mTrustEnabled;
    private boolean mTrustAlways;
    private boolean mTrustInCall;
    private boolean mTrustedBTDevice;
    private boolean mTrustedWiFiDevice;

    private String mTrustedBluetoothDevices = "";
    private String mTrustedBluetoothLEDevices = "";
    private String mTrustedWiFiNetworks = "";

    private HashSet<String> mTrusedBluetoothDevicesSet = new HashSet<String>();
    private HashSet<String> mTrusedBluetoothLEDevicesSet = new HashSet<String>();
    private HashSet<String> mTrusedWiFiNetworksSet = new HashSet<String>();

    private boolean mIsInCall;

    public boolean isTrustEnabled() {
        return mTrustEnabled;
    }

    public boolean isTrustAlways() {
        return mTrustAlways;
    }

    public boolean isTrustInCall() {
        return mTrustInCall;
    }

    private String getTrustedBluetoothDevices() {
        return mTrustedBluetoothDevices;
    }

    private String getTrustedBluetoothLEDevices() {
        return mTrustedBluetoothLEDevices;
    }

    private String getTrustedWiFiNetworks() {
        return mTrustedWiFiNetworks;
    }

    public BaikalTrust(TrustManagerService tm, Handler handler, Context context) {
        super(handler);
        mDevices = BaikalDevices.getInstance(handler,context);
        mDevices.setBaikalTrust(this);
	    mHandler = handler;
        mContext = context;
        mResolver = context.getContentResolver();
        mTm = tm;

        try {
                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_ENABLED),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_ALWAYS),
                    false, this);

                mResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BAIKALOS_TRUST_INCALL),
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
            mTrusedBluetoothDevicesSet = setBlockedList(mTrustedBluetoothDevices);
            mTrusedBluetoothLEDevicesSet = setBlockedList(mTrustedBluetoothLEDevices);
            mTrusedWiFiNetworksSet = setBlockedList(mTrustedWiFiNetworks);
            updateTrustedDevicesInternal();
        }
        updateTrustServiceInternal();
    }

    public void loadConstants(Context context) {
        synchronized (this) {
            try {
                boolean trustEnabled = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_ENABLED,0) == 1;

                boolean trustAlways = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_ALWAYS,0) == 1;

                boolean trustInCall = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_INCALL,0) == 1;

                String trustedBluetoothDevices = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_BT_DEV);

                String trustedBluetoothLEDevices = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_BTLE_DEV);

                String trustedWiFiNetworks = Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.BAIKALOS_TRUST_WIFI_DEV);

                boolean trustChanged =  trustInCall != mTrustInCall ||
                                        trustEnabled != mTrustEnabled ||
                                        trustAlways != mTrustAlways ||
                                        !mTrustedBluetoothDevices.equals(trustedBluetoothDevices) ||
                                        !mTrustedBluetoothLEDevices.equals(trustedBluetoothLEDevices) ||
                                        !mTrustedWiFiNetworks.equals(trustedWiFiNetworks);

                if( trustChanged ) {
                    mTrustEnabled = trustEnabled;
                    mTrustAlways = trustAlways;
                    mTrustInCall = trustInCall;
                    mTrustedBluetoothDevices = trustedBluetoothDevices == null ? "" : trustedBluetoothDevices;
                    mTrustedBluetoothLEDevices = trustedBluetoothLEDevices == null ? "" : trustedBluetoothLEDevices;
                    mTrustedWiFiNetworks = trustedWiFiNetworks == null ? "" : trustedWiFiNetworks;
                }

            } catch (Exception e) {
                Slog.e(TAG, "Bad BaikalTrust settings ", e);
            }
        }
    }

    void updateTrust() { 
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateTrustInternal();
            }
        });
    }

    private void updateTrustInternal() {
        synchronized(this) {
            updateTrustedDevicesInternal();
        }
        updateTrustServiceInternal();
    }
    
    void updateTrustService() { 
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateTrustServiceInternal();
            }
        });
    }

    private void updateTrustServiceInternal() {
        mIsInCall = mDevices.isInCall();
        mTm.requestUpdateTrustAll();
    }

    void updateTrustedDevices() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateTrustedDevicesInternal();
            }
        });
    }

    private boolean updateTrustedDevicesInternal() {
        boolean trustedBTDevice = false;
        synchronized(this) {
            Iterator<String> nextItem = mTrusedBluetoothDevicesSet.iterator();

            while (nextItem.hasNext()) {
                String sAddress = nextItem.next();
                Slog.d(TAG, "Check bluetooth device connected " + sAddress);
                
                if(mDevices.isDeviceConnected(sAddress) ) {
                    Slog.d(TAG, "Trusted bluetooth device connected " + sAddress);
                    trustedBTDevice = true;
                } 
            }

            if( !trustedBTDevice ) {
                nextItem = mTrusedBluetoothLEDevicesSet.iterator();

                while (nextItem.hasNext()) {
                    String sAddress = nextItem.next();
                    Slog.d(TAG, "Check bluetooth LE device connected " + sAddress);
                    if(mDevices.isDeviceConnected(sAddress) ) {
                        Slog.d(TAG, "Trusted bluetooth LE device connected " + sAddress);
                        trustedBTDevice = true;
                    } 
                }
            }

        }
        if( trustedBTDevice != mTrustedBTDevice ) {
            if( !trustedBTDevice ) Slog.d(TAG, "No Trusted bluetooth devices connected ");
            mTrustedBTDevice = trustedBTDevice;
            return true;
        }
        return false;
    }

    public boolean isTrustable() {
        if( !mTrustEnabled ) {
            Slog.d(TAG, "isTrustable: disabled");
            return false;
        }        
        return true;
    }

    public boolean isTrusted() {
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

    public boolean isKeepUnlocked() {
        boolean keep = (mIsInCall && mTrustInCall) || isTrusted();
        Slog.i(TAG, "isKeepUnlocked: " + keep);
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
