/*
 * Copyright (C) 2024 The LeafOS Project
 * Copyright (C) 2024 crDroid Android Project
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.android.server.baikalos;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Environment;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;


import com.android.server.SystemService;
import com.android.internal.util.crdroid.Utils;

import com.android.internal.baikalos.BaikalSpoofer;
import com.android.internal.baikalos.BaikalConstants;

import org.json.JSONException;
import org.json.JSONObject;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class BaikalAttestationService {

    private static final String TAG = BaikalAttestationService.class.getSimpleName();

    //private static final String API = "https://raw.githubusercontent.com/baikalos/android_vendor_certification/refs/heads/16.0/gms_certified_props.json";
    private static final String DATA_FILE = "gms_certified_props.json";
    private static final String CERT_FILE = "att_certicate.bin";
    private static final long INITIAL_DELAY = 0; // Start immediately on boot
    private static final long INTERVAL = 8; // Interval in hours
    private static final boolean DEBUG = true; //Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private File mDataFile;
    //private File mCertFile;
    private ScheduledExecutorService mScheduler;
    private ConnectivityManager mConnectivityManager;
    private FetchGmsCertifiedProps mFetchRunnable;

    private boolean mPendingUpdate;
    private boolean mEnabled;
    private boolean isInitialized;
    private int mRetry;

    public BaikalAttestationService(Context context) {
        mContext = context;
    }

    public void onSystemReady() {
        if (Utils.isPackageInstalled(mContext, "com.google.android.gms") ) {
            synchronized(this) {
            if( isInitialized ) return;
            Log.i(TAG, "Scheduling the service");

            mDataFile = new File(Environment.getDataSystemDirectory(), DATA_FILE);
            //mCertFile = new File(Environment.getDataSystemDirectory(), CERT_FILE);
            mFetchRunnable = new FetchGmsCertifiedProps();
            mScheduler = Executors.newSingleThreadScheduledExecutor();
            mConnectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            registerNetworkCallback();
            isInitialized = true;
            }
        }
    }

    public void scheduleIfNeeded(boolean enabled) {
        if (Utils.isPackageInstalled(mContext, "com.google.android.gms") ) {
            if( !isInitialized ) onSystemReady(); 
            mEnabled = enabled;
            if( mEnabled && mScheduler != null ) {
                mScheduler.scheduleAtFixedRate(
                    mFetchRunnable, INITIAL_DELAY, INTERVAL, TimeUnit.HOURS);
            }
            if( !mEnabled ) {
                deleteFile(mDataFile);
                Settings.Global.putString(mContext.getContentResolver(), "baikal_kb_data", "");
            }
        }
    }    

    private String readFromFile(File file) {
        StringBuilder content = new StringBuilder();

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from spoofer file", e);
            }
        }
        return content.toString();
    }

    private void writeToFile(File file, String data) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(data);
            // Set -rw-r--r-- (644) permission to make it readable by others.
            file.setReadable(true, false);
            LocalDateTime now = LocalDateTime.now();
            String formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Settings.Global.putString(mContext.getContentResolver(), "baikalos_gms_spoofer_updated", formatted);
        } catch (IOException e) {
            Log.e(TAG, "Error writing to spoofer file", e);
        }

    }

    private void deleteFile(File file) {
        if(file.exists()) {
            try {
                file.delete();
                Settings.Global.putString(mContext.getContentResolver(), "baikalos_gms_spoofer_updated", "");
            } catch(Exception e) {
                Log.e(TAG, "Can't delete spoofer file", e);
            }
        }
    }
        

    private String fetchProps() {
        try {

            URL url = new URI(BaikalSpoofer.getDevString(BaikalSpoofer.DEV_CONST.SPOOFER_JSON_URL)).toURL();
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            try {
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);

                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    mRetry = 0;
                    return response.toString();
                }
            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error making an API request", e);
            return null;
        }
    }

    private void dlog(String message) {
        if (DEBUG) Log.d(TAG, message);
    }

    private boolean isInternetConnected() {
        Network network = mConnectivityManager.getActiveNetwork();
        if (network != null) {
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return false;
    }

    private void registerNetworkCallback() {
        mConnectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                if( !mEnabled ) return;
                Log.i(TAG, "Internet is available, resuming update:" + mPendingUpdate);
                mRetry = 0;
                if (mPendingUpdate) {
                    mScheduler.schedule(mFetchRunnable, 10, TimeUnit.SECONDS);
                }
            }
        });
    }

    private class FetchGmsCertifiedProps implements Runnable {
        @Override
        public void run() {
            try {
                if( !mEnabled ) { 
                    mPendingUpdate = false;
                    return;
                }

                dlog("FetchGmsCertifiedProps started");

                if (!isInternetConnected()) {
                    Log.e(TAG, "Internet is unavailable, deferring update");
                    mPendingUpdate = true;
                    return;
                }

                String savedProps = readFromFile(mDataFile);
                String props = fetchProps();
                if( props == null ) {
                    if( mRetry < 10 ) {
                        mScheduler.schedule(mFetchRunnable, 15, TimeUnit.SECONDS);
                        mRetry++;
                        return;
                    }
                }

                if (props != null && !savedProps.equals(props)) {
                    dlog("Found new props");
                    writeToFile(mDataFile, props);
                    dlog("FetchGmsCertifiedProps completed");
                    PiItem item = new PiItem(props);
                    update(item);
                    ActivityManager mAm = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                    mAm.killBackgroundProcesses("com.google.android.gms");
                } else {
                    dlog("No change in props");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in FetchGmsCertifiedProps", e);
                if( mRetry < 10 ) {
                    mScheduler.schedule(mFetchRunnable, 15, TimeUnit.SECONDS);
                    mRetry++;
                }
                return;
            }
            mPendingUpdate = false;
            mRetry = 0;
        }

        private void update(PiItem item) {
            SystemProperties.set("persist.spf.def.manufacturer",item.MANUFACTURER);
            SystemProperties.set("persist.spf.def.model",item.MODEL);
            SystemProperties.set("persist.spf.def.fingerprint", item.FINGERPRINT);
            SystemProperties.set("persist.spf.def.brand", item.BRAND);
            SystemProperties.set("persist.spf.def.product", item.PRODUCT);
            SystemProperties.set("persist.spf.def.device", item.DEVICE);
            SystemProperties.set("persist.spf.def.id", item.ID);
            SystemProperties.set("persist.spf.def.release", item.RELEASE);
            SystemProperties.set("persist.spf.def.incremental", item.INCREMENTAL);
            SystemProperties.set("persist.spf.def.security_patch", item.SECURITY_PATCH);
            SystemProperties.set("persist.spf.def.first_api_level", item.DEVICE_INITIAL_SDK_INT);
            SystemProperties.set("persist.spf.def.sdk_int", item.SDK_INT);
            //Settings.Global.putString(mContext.getContentResolver(), "baikal_kb_data", item.ATT_CERT);
        }
    }

    class PiItem {
        public String MANUFACTURER;
        public String MODEL;
        public String FINGERPRINT;
        public String PRODUCT;
        public String DEVICE;
        public String BRAND;
        public String ID;
        public String INCREMENTAL;
        public String RELEASE;
        public String SECURITY_PATCH;
        public String DEVICE_INITIAL_SDK_INT;
        public String SDK_INT;
        public String ATT_CERT;

        public PiItem() {
            SDK_INT = "33";
        }

        public PiItem(String json) {
            update(json);
        }

        public boolean update(String json) {
            try {
                JSONObject parsedProps = new JSONObject(json);
                Iterator<String> keys = parsedProps.keys();

                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = parsedProps.getString(key);
                    Log.e(TAG, "update:" + key + ":" + value);
                    switch(key) {
                        case "MANUFACTURER":
                            MANUFACTURER = value;
                            break;
                        case "MODEL":
                            MODEL = value;
                            break;
                        case "FINGERPRINT":
                            FINGERPRINT = value;
                            break;
                        case "PRODUCT":
                            PRODUCT = value;
                            break;
                        case "DEVICE":
                            DEVICE = value;
                            break;
                        case "BRAND":
                            BRAND = value;
                            break;
                        case "ID":
                            ID = value;
                            break;
                        case "VERSION.INCREMENTAL":
                        case "INCREMENTAL":
                            INCREMENTAL = value;
                            break;
                        case "VERSION.RELEASE":
                        case "RELEASE":
                            RELEASE = value;
                            break;
                        case "VERSION.SECURITY_PATCH":
                        case "SECURITY_PATCH":
                            SECURITY_PATCH = value;
                            break;
                        case "VERSION.DEVICE_INITIAL_SDK_INT":
                        case "DEVICE_INITIAL_SDK_INT":
                            DEVICE_INITIAL_SDK_INT = value;
                            break;
                        case "SDK_INT":
                            SDK_INT = value;
                            break;
                        case "ATT_CERT":
                            //ATT_CERT = value;
                            break;
                    }
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "update:",e);
                return false;
            }
        }
    }
}
