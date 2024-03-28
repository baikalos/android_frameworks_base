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

package com.android.internal.baikalos;

import android.util.Slog;

import android.text.TextUtils;

import android.os.BatterySaverPolicyConfig;
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


public class PowerSaverSettings extends ContentObserver {

    private static final String TAG = "PowerSaverSettings";

    boolean mIsReady;

    Context mContext;
    ContentResolver mResolver;
    PackageManager mPackageManager;
    AppOpsManager mAppOpsManager;

    private final TextUtils.StringSplitter mSplitter = new TextUtils.SimpleStringSplitter('|');

    HashMap<Integer, PowerSaverPolicyConfig> _policiesById = new HashMap<Integer,PowerSaverPolicyConfig> ();
    //HashMap<String, PowerSaverPolicyConfig> _policiesByName = new HashMap<String,PowerSaverPolicyConfig> ();

    boolean isChanged = false;

    static boolean sIsLoaded;

    public PowerSaverSettings(Handler handler,Context context) {
        super(handler);

        mContext = context;
        mIsReady = true;
    }

    public void registerObserver(boolean systemBoot) {
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mPackageManager = mContext.getPackageManager();
        mResolver = mContext.getContentResolver();
    }


    public HashMap<Integer, PowerSaverPolicyConfig> getPoliciesById() { 
        return _policiesById;
    }


    /*public HashMap<String, PowerSaverPolicyConfig> getPoliciesByName() { 
        return _policiesByName;
    }*/

    public void updatePolicy(PowerSaverPolicyConfig policy) {
        _policiesById.put(policy.policyNumber,policy);
        //_policiesByName.put(policy.policyName,policy);
    }

    private static boolean selfUpdate;
    public void loadPolicies() {
        synchronized(this) {
            String policies = Settings.Global.getString(mResolver,
                    Settings.Global.BAIKALOS_POWERSAVER_POLICY);

            loadPoliciesLocked(policies);
        }
    }

    public void loadPolicies(String policiesString) {
        synchronized(this) {
            loadPoliciesLocked(policiesString);
        }
    }
    
    void loadPoliciesLocked(String policiesString) {

        Slog.e(TAG, "Loading Policies - selfUpdate:" + selfUpdate);
        if( selfUpdate ) return;
        selfUpdate = true;

        HashMap<String,PowerSaverPolicyConfig> newPoliciesByName = new HashMap<String,PowerSaverPolicyConfig> ();
        HashMap<Integer,PowerSaverPolicyConfig> newPoliciesById = new HashMap<Integer,PowerSaverPolicyConfig> ();

        try {
            if( policiesString == null ) {
                Slog.e(TAG, "Empty policiesString settings");
                policiesString = "";
            }

            try {
                mSplitter.setString(policiesString);

                for(String policyString:mSplitter) {
                
                    PowerSaverPolicyConfig policy = PowerSaverPolicyConfig.deserialize(policyString); 
                    if( policy != null  ) {
                        newPoliciesByName.put(policy.policyName, policy);
                        newPoliciesById.put(policy.policyNumber, policy);
                    } else {
                        Slog.e(TAG, "Invalid powersaver policy:" + policyString);
                    }
                }
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Bad policy settings", e);
                selfUpdate = false;
                //return ;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Bad policy settings", e);
            selfUpdate = false;
            //return;
        }


        _policiesById = newPoliciesById;
        //_policiesByName = newPoliciesByName;

        Slog.e(TAG, "Loaded " + newPoliciesById.size() + " Policies");

        sIsLoaded = true;
        selfUpdate = false;
    }

    void saveLocked() {

        Slog.e(TAG, "saveLocked()");

        String val = "";

        String policies = Settings.Global.getString(mResolver,
            Settings.Global.BAIKALOS_POWERSAVER_POLICY);

        if( policies == null ) {
            policies = "";
        }

        for(Map.Entry<Integer, PowerSaverPolicyConfig> entry : _policiesById.entrySet()) {
            if( BaikalConstants.BAIKAL_DEBUG_POWER ) Slog.i(TAG, "Save policy name=" + entry.getValue().policyName);
            String entryString = entry.getValue().serialize();
            if( entryString != null ) val += entryString + "|";
        } 

        if( !val.equals(policies) ) {
            Slog.i(TAG, "Write new policies data string :" + val);
            Slog.i(TAG, "Old policies data string :" + policies);
            Settings.Global.putString(mResolver,
                Settings.Global.BAIKALOS_POWERSAVER_POLICY,val);
        } else {
            Slog.i(TAG, "Skip writing new policies data string :" + val);
        }
    }

    public void save() {
        isChanged = true;
        Slog.e(TAG, "save()");
    }

    public void commit() {
        synchronized(this) {
            Slog.e(TAG, "commit(isChanged=" + isChanged + ")");
            if( isChanged ) saveLocked();
            isChanged = false;
        }
    }

    public static void resetAll(ContentResolver resolver) {
        Settings.Global.putString(resolver,
            Settings.Global.BAIKALOS_POWERSAVER_POLICY,"");

    }

    public static void saveBackup(ContentResolver resolver) {
               
        String policies = Settings.Global.getString(resolver,
                Settings.Global.BAIKALOS_POWERSAVER_POLICY);

        if( policies == null ) policies = "";

        Settings.Global.putString(resolver,
            Settings.Global.BAIKALOS_POWERSAVER_POLICY_BACKUP,policies);
        
    }

    public static void restoreBackup(ContentResolver resolver) {
        
        String policies = Settings.Global.getString(resolver,
                Settings.Global.BAIKALOS_POWERSAVER_POLICY_BACKUP);

        if( policies == null ) policies = "";

        Settings.Global.putString(resolver,
            Settings.Global.BAIKALOS_POWERSAVER_POLICY,policies);
        
    }

    AppOpsManager getAppOpsManager() {
        if (mAppOpsManager == null) {
            mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        }
        return mAppOpsManager;
    }

    public static boolean isLoaded() {
        return sIsLoaded;
    }
}
