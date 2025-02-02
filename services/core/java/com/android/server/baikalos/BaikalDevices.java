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

public class BaikalDevices extends ContentObserver {

    private static final String TAG = "Baikal.Devices";

    private ContentResolver mResolver;
    private Handler mHandler;
    private Context mContext;

    private TelephonyManager mTelephonyManager;

    private HashSet<String> mConnectedBluetoothDevices = new HashSet<String>();
    private HashSet<String> mConnectedWiFiNetworks = new HashSet<String>();

    private boolean mIsInCall;

    private static Object staticLock = new Object();
    private static BaikalDevices mInstance;

    private BaikalTrust mBaikalTrust;
    private BaikalSpecialDevices mBaikalSpecialDevices;

    void setBaikalTrust(BaikalTrust baikalTrust) {
        mBaikalTrust = baikalTrust;
    }

    void setBaikalSpecialDevices(BaikalSpecialDevices specialDevices) {
        mBaikalSpecialDevices = specialDevices;
    }

    public static BaikalDevices getInstance(Handler handler, Context context) {
        synchronized(staticLock) {
            if( mInstance == null ) {
                mInstance = new BaikalDevices(handler, context);
            }
            return mInstance;
        }
    }

    private BaikalDevices(Handler handler, Context context) {
        super(handler);
	    mHandler = handler;
        mContext = context;
        mResolver = context.getContentResolver();

        IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        btFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        mContext.registerReceiver(mBluetoothReceiver, btFilter);

        IntentFilter btAdapterFilter = new IntentFilter();
        btAdapterFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        btAdapterFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        btAdapterFilter.addAction(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothAdapterReceiver, btAdapterFilter);

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, 0xFFFFFFF);
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

    public boolean isInCall() {
        return mIsInCall;
    }

    public boolean isDeviceConnected(String sAddress) {
        synchronized(staticLock) {
            if( mConnectedBluetoothDevices.contains(sAddress)) return true;
        }
        return false;
    }

    public void updateBluetoothDeviceState(int state) {
        switch(state) { 
            case BluetoothAdapter.STATE_ON:
                Slog.d(TAG, "Bluetooth ready");
                break;
            default:
                Slog.d(TAG, "Bluetooth not ready. Disable device trust");
                synchronized(staticLock) {
                    mConnectedBluetoothDevices.clear();
                }
        }
        if( mBaikalTrust != null ) {
            mBaikalTrust.updateTrust();
        }

        if( mBaikalSpecialDevices != null ) {
            mBaikalSpecialDevices.updateDevices();
        }
    }


    public void updateBluetoothDeviceState(int state,String device_address) {
        Slog.d(TAG, "Bluetooth device state changed:" + device_address + ", state=" + state);
        synchronized(staticLock) {
            updateBluetoothDeviceStateLocked(state,device_address);
        }
        if( mBaikalTrust != null ) { 
            mBaikalTrust.updateTrust();
        } else {
            Slog.d(TAG, "BaikalTrust service not ready!");
        }
        if( mBaikalSpecialDevices != null ) {
            mBaikalSpecialDevices.updateDevices();
        } else {
            Slog.d(TAG, "BaikalSpecialDevices service not ready!");
        }
    }


    public void updateBluetoothDeviceStateLocked(int state,String device_address) {
        if( state == 0 ) {
            if( !mConnectedBluetoothDevices.contains(device_address) ) {
                mConnectedBluetoothDevices.add(device_address);
            }
        } else if( state == 1 || state == 2 )  {
            if( mConnectedBluetoothDevices.contains(device_address) ) {
                mConnectedBluetoothDevices.remove(device_address);
            }
        }
    }

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Callback invoked when device call state changes.
         * @param state call state
         * @param incomingNumber incoming call phone number. If application does not have
         * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE} permission, an empty
         * string will be passed as an argument.
         *
         * @see TelephonyManager#CALL_STATE_IDLE
         * @see TelephonyManager#CALL_STATE_RINGING
         * @see TelephonyManager#CALL_STATE_OFFHOOK
         */
        @Override
        public void onCallStateChanged(int callState, String incomingNumber) {
            Slog.i(TAG,"PhoneStateListener: onCallStateChanged(" + callState + "," + incomingNumber + ")");
            boolean state = callState != 0;
            if( mIsInCall != state ) {
                mIsInCall = state;
                if( mBaikalTrust != null ) mBaikalTrust.updateTrustService();
                if( mBaikalSpecialDevices != null ) mBaikalSpecialDevices.updateService();
            }

        }

        /**
         * Callback invoked when precise device call state changes.
         *
         * @hide
         */
        @Override
        public void onPreciseCallStateChanged(PreciseCallState callState) {
            Slog.i(TAG,"PhoneStateListener: onPreciseCallStateChanged(" + callState + ")");
            boolean state =  callState.getRingingCallState() > 0 ||
                         callState.getForegroundCallState() > 0 ||
                         callState.getBackgroundCallState() > 0;
            /*if( mIsInCall != state ) {
                mIsInCall = state;
                if( mBaikalTrust != null ) mBaikalTrust.updateTrustService();
                if( mBaikalSpecialDevices != null ) mBaikalSpecialDevices.updateService();
            }*/
       }
    };
}
