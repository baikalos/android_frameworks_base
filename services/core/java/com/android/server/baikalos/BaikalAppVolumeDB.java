/*
 * Copyright (C) 2019 BaikalOS
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

import android.util.Slog;

import android.text.TextUtils;

import android.media.AudioSystem;

import android.os.UserHandle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemProperties;

import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;

import android.app.AppOpsManager;
import android.baikalos.BaikalAppProfile;

import android.net.Uri;

import android.database.ContentObserver;

import android.provider.Settings;

import android.util.ArraySet;
import android.util.KeyValueListParser;
import android.util.Pair;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.android.internal.baikalos.BaikalConstants;

public class BaikalAppVolumeDB {

    private static final String TAG = "AppVolumeDB";

    Context mContext;
    ContentResolver mResolver;
    PackageManager mPackageManager;
    boolean isLoaded = false;
    String loadedVolumes;

    private final TextUtils.StringSplitter mSplitter = new TextUtils.SimpleStringSplitter('|');

    HashMap<String, Float> _volumeByPackageName = new HashMap<String,Float> ();

    BaikalAppVolumeDB(Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
    }

    public void onSystemReady() {
        synchronized(this) {
            String appVolumes = Settings.Global.getString(mResolver,Settings.Global.BAIKALOS_APP_VOLUMES);
            loadVolumesLocked(appVolumes);
        }
    }

    void loadVolumesLocked(String appVolumesString) {

        Slog.e(TAG, "Loading appVolumes");

        HashMap<String,Float> newVolumesByPackageName = new HashMap<String,Float> ();

        try {
            if( appVolumesString == null ) {
                Slog.e(TAG, "Empty appVolumes settings");
                appVolumesString = "";
            }

            if( appVolumesString.equals(loadedVolumes) ) return;

            try {
                mSplitter.setString(appVolumesString);

                for(String volumeString:mSplitter) {

                    KeyValueListParser parser = new KeyValueListParser(',');

                    try {
                        parser.setString(volumeString);
                    } catch (IllegalArgumentException e) {
                        Slog.e(TAG, "Bad appVolumes settings :" + volumeString, e);
                        continue;
                    }

                    String packageName = parser.getString("pn",null);
                    if( packageName == null || packageName.equals("") ) {
                        Slog.e(TAG, "Bad appVolumes settings :" + volumeString);
                        continue;
                    }
                    Float volume  = parser.getFloat("vol",-10.0F);

                    //Slog.e(TAG, "Init appVolume " + packageName + " " + volume);
                    if( volume >= 0.0F ) newVolumesByPackageName.put(packageName,volume);
                    //if( apply ) AudioSystem.setAppVolume(packageName,volume);
                }
            } catch (Exception e) {
                Slog.e(TAG, "Bad appVolumes settings", e);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Bad appVolumes settings", e);
        }

        _volumeByPackageName = newVolumesByPackageName;
        loadedVolumes = appVolumesString;
    }


    void save() {
        synchronized(this) {
            saveLocked();
        }
    }

    void saveLocked() {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "saveLocked()");

        String val = "";

        String appVolumes = Settings.Global.getString(mResolver,Settings.Global.BAIKALOS_APP_VOLUMES);

        if( appVolumes == null ) {
            appVolumes = "";
        }

        for(Map.Entry<String, Float> entry : _volumeByPackageName.entrySet()) {
            if( entry.getValue() < 0.0 ) { 
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Skip saving default profile for packageName=" + entry.getKey());
                continue;
            }

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Save profile for packageName=" + entry.getKey());
            String entryString = "pn=" + entry.getKey().toString() + "," + "vol=" + entry.getValue().toString();
            if( entryString != null ) val += entryString + "|";
        } 

        if( !val.equals(appVolumes) ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Write new volumes data string :" + val);
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Old volumes data string :" + appVolumes);
            Settings.Global.putString(mResolver, Settings.Global.BAIKALOS_APP_VOLUMES, val);
        } else {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Skip writing new volumes data string :" + val);
        }
    }

    public void setAppVolume(String packageName, Float volume) {
        _volumeByPackageName.put(packageName,volume);
        saveLocked();
    }

    public Float getAppVolume(String packageName) {
        if( _volumeByPackageName == null ) return null;
        Float volume = _volumeByPackageName.get(packageName);
        if( volume == null ) return null;
        return volume;
    }
}
