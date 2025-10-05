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
import android.util.Pair;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BaikalAppProfileBase extends ContentObserver {

    private static final String TAG = "BaikalSettingsBase";

    boolean mIsReady;

    Context mContext;
    ContentResolver mResolver;
    PackageManager mPackageManager;
    AppOpsManager mAppOpsManager;

    BaikalPowerWhitelistBackend mBackend;

    protected String mLoadedProfileString = "invalid";

    private final TextUtils.StringSplitter mSplitter = new TextUtils.SimpleStringSplitter('|');

    HashMap<String, BaikalAppProfile> _profilesByPackageName = new HashMap<String,BaikalAppProfile> ();
    HashMap<Integer, BaikalAppProfile> _profilesByUid = new HashMap<Integer,BaikalAppProfile> ();

    static HashSet<Integer> _mAppsForDebug = new HashSet<Integer>();

    static HashSet<String> _allAppsByPackageName = new HashSet<String>(); 
    static HashSet<String> _systemAppsByPackageName = new HashSet<String>(); 
    static HashSet<String> _systemImportantByPackageName = new HashSet<String>(); 
    static HashSet<String> _systemWhitelistedByPackageName = new HashSet<String>(); 

    BaikalAppProfile mNotFoundProfile;

    boolean isChanged = false;

    static boolean sIsLoaded;

    public interface IBaikalAppProfileSettingsNotifier {
        void onAppProfileSettingsChanged();
    }

    IBaikalAppProfileSettingsNotifier mNotifier = null;

    BaikalAppProfileBase(Handler handler,Context context) {
        super(handler);

        /*mAndroidProfile = new AppProfile("android");
        mAndroidProfile.mBackgroundMode = -2;
        mAndroidProfile.mSystemApp = true;
        mAndroidProfile.mImportantApp = true;
        mAndroidProfile.mSystemWhitelisted = true;

        mSystemProfile = new AppProfile("system");
        mSystemProfile.mBackgroundMode = -2;
        mSystemProfile.mSystemApp = true;
        mSystemProfile.mImportantApp = true;
        mSystemProfile.mSystemWhitelisted = true;*/
        
        mNotFoundProfile = new BaikalAppProfile("NotFound",-1);
        /*mNotFoundProfile.mBootDisabled = true;
        mNotFoundProfile.mBackgroundModeConfig = 2;
        mNotFoundProfile.mBackgroundMode = 2;
        mNotFoundProfile.mDisableWakeup = true;
        mNotFoundProfile.mDisableJobs = true;*/
        
        mContext = context;
        mIsReady = true;
    }

    public void registerObserver(boolean systemBoot) {
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mPackageManager = mContext.getPackageManager();
        mResolver = mContext.getContentResolver();
        mBackend = BaikalPowerWhitelistBackend.getInstance(mContext);
        mBackend.refreshList();
        updateAllAppsLocked();
        updateSystemWhitelistedAppsLocked();
        updateImportantAppsLocked();
    }


    public boolean isSystemApp(String packageName) {
        synchronized(this) {
            return isSystemAppLocked(packageName);
        }
    }


    boolean isSysWhitelistedLocked(String packageName) {
        if( _systemWhitelistedByPackageName.contains(packageName) ) return true;     
        return false;
    }

    boolean isSystemAppLocked(String packageName) {
        if( _systemAppsByPackageName.contains(packageName) ) return true;     
        return false;
    }

    boolean isImportantAppLocked(String packageName) {
        if( _systemImportantByPackageName.contains(packageName) ) return true;     
        return false;
    }

    boolean isWhitelistedLocked(String packageName) {
        if( mBackend != null && mBackend.isWhitelisted(packageName) ) return true;     
        return false;
    }

    HashMap<String, BaikalAppProfile> getProfilesByPackageName() { 
        return _profilesByPackageName;
    }

    HashMap<Integer, BaikalAppProfile> getProfilesByUid() { 
        return _profilesByUid;
    }

    void updateSystemWhitelistedAppsLocked() {
        _systemWhitelistedByPackageName.clear();

        Slog.e(TAG, "Updating system whitelisted app list");

        ArraySet<String> sysWhitelistedAppInfo = mBackend.getSystemWhitelistedApps();
        for (String packageName : sysWhitelistedAppInfo) {
            if( !_systemWhitelistedByPackageName.contains(packageName) ) {
                Slog.e(TAG, "Added system whitelisted app " + packageName);
                _systemWhitelistedByPackageName.add(packageName);
            }
        }
    }

    void updateImportantAppsLocked() {
        _systemImportantByPackageName.clear();

        Slog.e(TAG, "Updating system important app list");

        ArraySet<String> sysWhitelistedAppInfo = mBackend.getDefaultActiveApps();
        for (String packageName : sysWhitelistedAppInfo) {
            if( !_systemImportantByPackageName.contains(packageName) ) {
                Slog.e(TAG, "Added system important app " + packageName);
                _systemImportantByPackageName.add(packageName);
            }
        }
    }

    void updateAllAppsLocked() {

        _systemAppsByPackageName.clear();
        _allAppsByPackageName.clear();

        Slog.e(TAG, "Updating app list");

        List<PackageInfo> installedAppInfo = mPackageManager.getInstalledPackages(
                PackageManager.MATCH_DISABLED_COMPONENTS | 
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS |
                PackageManager.MATCH_APEX |
                PackageManager.MATCH_ALL |
                PackageManager.MATCH_UNINSTALLED_PACKAGES |
                PackageManager.MATCH_INSTANT |
                PackageManager.MATCH_ANY_USER
                );
        for (PackageInfo info : installedAppInfo) {

            boolean isSystem = (info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.e(TAG, "Found app package:" + info.packageName + ", flags=" + Integer.toHexString(info.applicationInfo.flags));

            if( isSystem /*|| info.packageName.startsWith("com.android.")*/ ) {
                if( !_systemAppsByPackageName.contains(info.packageName) ) {
                    Slog.e(TAG, "Added system app " + info.packageName);
                    _systemAppsByPackageName.add(info.packageName);
                }
            }

            if( !_allAppsByPackageName.contains(info.packageName) ) {
                _allAppsByPackageName.add(info.packageName);
            }
        } 
    }


    private static boolean selfUpdate;
    public void loadProfiles() {
        mBackend.refreshList();

        synchronized(this) {
            String appProfiles = Settings.Global.getString(mResolver,
                    Settings.Global.BAIKALOS_APP_PROFILES);

            loadProfilesLocked(appProfiles);
        }
    }

    void loadProfiles(String appSettingsString) {

        mBackend.refreshList();

        synchronized(this) {
            loadProfilesLocked(appSettingsString);
        }
    }
    
    void loadProfilesLocked(String appSettingsString) {

        updateImportantAppsLocked();

        Slog.e(TAG, "Loading BaikalAppProfiles selfUpdate:" + selfUpdate);
        if( selfUpdate ) return;
        selfUpdate = true;

        HashSet<Integer> newAppsForDebug = new HashSet<Integer>();

        HashMap<String,BaikalAppProfile> newProfilesByPackageName = new HashMap<String,BaikalAppProfile> ();
        //newProfilesByPackageName.put(mSystemProfile.mPackageName,mSystemProfile);
        //newProfilesByPackageName.put(mAndroidProfile.mPackageName,mAndroidProfile);

        HashMap<Integer,BaikalAppProfile> newProfilesByUid = new HashMap<Integer,BaikalAppProfile> ();

        HashMap<String,BaikalAppProfile> oldProfiles = _profilesByPackageName;

        try {
            String appProfiles = Settings.Global.getString(mResolver,
                    Settings.Global.BAIKALOS_APP_PROFILES);

            if( appProfiles == null ) {
                Slog.e(TAG, "Empty profiles settings");
                appProfiles = "";
            }

            try {
                mSplitter.setString(appProfiles);

                for(String profileString:mSplitter) {
                
                    BaikalAppProfile profile = BaikalAppProfile.deserializeProfile(profileString); 
                    if( profile != null  ) {

                        int uid = getAppUidLocked(profile.mPackageName);
                        if( uid == -1 ) continue;

                        profile.mUid = uid;
 
                        if( profile.mDebug && !newAppsForDebug.contains(uid)  ) {
                            newAppsForDebug.add(uid);
                        }

                        if( oldProfiles.containsKey(profile.mPackageName) ) {
                            BaikalAppProfile old_profile = oldProfiles.get(profile.mPackageName);
                            old_profile.update(profile);
                            profile = old_profile;
                        } 

                        if( !newProfilesByPackageName.containsKey(profile.mPackageName)  ) {
                            newProfilesByPackageName.put(profile.mPackageName, profile);
                        }
    
                        if( !newProfilesByUid.containsKey(profile.mUid)  ) {
                            newProfilesByUid.put(profile.mUid, profile);
                        } else {
                            BaikalAppProfile old_uid_profile = newProfilesByUid.get(profile.mUid);
                            if( old_uid_profile.mPackageName == null && !old_uid_profile.mPackageName.equals(profile.mPackageName) ) {
                                Slog.e(TAG, "Package " + profile.mPackageName +  " with uid " + profile.mUid + " already loaded with package name=" + old_uid_profile.mPackageName);
                            }
                            BaikalAppProfile replace = merge(old_uid_profile,profile);
                            if( replace != null ) {
                                newProfilesByUid.remove(profile.mUid);
                                newProfilesByUid.put(replace.mUid, replace);
                                profile = replace;
                            }
                        }
    
                        if(!profile.mStaminaEnforced && isStaminaWl(uid,profile.mPackageName)) {
                            Slog.e(TAG, "Enforce stamina(0) on " + profile.mPackageName);
                            profile.mStaminaEnforced = true;
                        }
                    }
                }

                mLoadedProfileString = appProfiles;

            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Bad profiles settings", e);
                selfUpdate = false;
                //return ;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Bad BaikalService settings", e);
            selfUpdate = false;
            //return;
        }

        try {
            for(String pkgName : _systemAppsByPackageName) {
                int uid = getAppUidLocked(pkgName);
                if( uid == -1 ) continue;

                BaikalAppProfile profile = findOrAddDefaultProfile(pkgName, newProfilesByPackageName, newProfilesByUid, oldProfiles);
                if( !profile.mSystemApp ) {
                    Slog.e(TAG, "Enforce mSystemApp on " + pkgName);
                    profile.mSystemApp = true;
                }
                if(!profile.mStaminaEnforced && isStaminaWl(uid,pkgName)) {
                    Slog.e(TAG, "Enforce stamina(1) on " + pkgName);
                    profile.mStaminaEnforced = true;
                }
            }

            for(String pkgName : _systemWhitelistedByPackageName) {
                int uid = getAppUidLocked(pkgName);
                if( uid == -1 ) continue;
                BaikalAppProfile profile = findOrAddDefaultProfile(pkgName, newProfilesByPackageName, newProfilesByUid, oldProfiles);
                if( !profile.mSystemWhitelisted ) {
                    Slog.e(TAG, "Enforce mSystemWhitelisted on " + pkgName);
                    profile.mSystemWhitelisted = true;
                }
                if(!profile.mStaminaEnforced && isStaminaWl(uid,pkgName)) {
                    Slog.e(TAG, "Enforce stamina(2) on " + pkgName);
                    profile.mStaminaEnforced = true;
                }
            }

            for(String pkgName : _systemImportantByPackageName) {
                int uid = getAppUidLocked(pkgName);
                if( uid == -1 ) continue;
                BaikalAppProfile profile = findOrAddDefaultProfile(pkgName, newProfilesByPackageName, newProfilesByUid, oldProfiles);
                if( !profile.mImportantApp ) {
                    Slog.e(TAG, "Enforce mImportantApp on " + pkgName);
                    profile.mImportantApp = true;
                }
                if(!profile.mStaminaEnforced && isStaminaWl(uid,pkgName)) {
                    Slog.e(TAG, "Enforce stamina(3) on " + pkgName);
                    profile.mStaminaEnforced = true;
                }
            }

            for(String pkgName : _allAppsByPackageName) {
                int uid = getAppUidLocked(pkgName);
                if( uid == -1 ) continue;
                BaikalAppProfile profile = findOrAddDefaultProfile(pkgName, newProfilesByPackageName, newProfilesByUid, oldProfiles);
            }

            BaikalAppProfile android = oldProfiles.containsKey("android") ? oldProfiles.get("android") : new BaikalAppProfile("android",1000);
            android.mSystemWhitelisted = true;
            android.mStaminaEnforced = true;
            //android.mImportantApp = true;
            android.mSystemApp = true;
            android.mBackgroundMode = -1;
            newProfilesByPackageName.put("android",android);
            newProfilesByUid.put(1000,android);

            BaikalAppProfile empty = oldProfiles.containsKey("empty") ? oldProfiles.get("empty") : new BaikalAppProfile("empty",-1);
            empty.mSystemWhitelisted = false;
            empty.mStaminaEnforced = false;
            empty.mImportantApp = false;
            empty.mSystemApp = false;
            empty.mBackgroundMode = 0;
            newProfilesByPackageName.put("empty",empty);
            newProfilesByUid.put(-1,empty);

            for(Map.Entry<String, BaikalAppProfile> entry : oldProfiles.entrySet()) {
                entry.getValue().isInvalidated = true;
            }

            for(Map.Entry<String, BaikalAppProfile> entry : newProfilesByPackageName.entrySet()) {
                updateSystemSettingsLocked(entry.getValue());
            }

        } catch (Exception e) {
            Slog.e(TAG, "Bad BaikalService settings", e);
            selfUpdate = false;
            //return;
        }

        oldProfiles.clear();

        _profilesByPackageName = newProfilesByPackageName;
        _profilesByUid = newProfilesByUid;
        _mAppsForDebug = newAppsForDebug;

        Slog.e(TAG, "Loaded " + _profilesByPackageName.size() + " BaikalAppProfiles");

        updateSystemProperties();

        selfUpdate = true;
        saveLocked();

        sIsLoaded = true;
        selfUpdate = false;
    }

    private void updateSystemProperties() {
        String prop = "999999";
        for(Map.Entry<String, BaikalAppProfile> entry : _profilesByPackageName.entrySet()) {
            BaikalAppProfile profile = entry.getValue();
                
            if( profile.mFilterFS && UserHandle.getAppId(profile.mUid) >= 10000 ) {
                prop += "," + profile.mUid;
            }
        }
        SystemProperties.set("persist.baikal.filter_uids", prop);

        prop = "999999";
        for(Map.Entry<String, BaikalAppProfile> entry : _profilesByPackageName.entrySet()) {
            BaikalAppProfile profile = entry.getValue();
                
            if( profile.mFilterFSadd && UserHandle.getAppId(profile.mUid) >= 10000 ) {
                prop += "," + profile.mUid;
            }
        }
        SystemProperties.set("persist.baikal.filter_uids_add", prop);

    }

    private BaikalAppProfile merge(BaikalAppProfile existing, BaikalAppProfile from) {    
        BaikalAppProfile to = new BaikalAppProfile(existing);
        if( isSysWhitelistedLocked(from.mPackageName) ) to.mSystemWhitelisted = true;
        if( to.mBackgroundMode > from.mBackgroundMode ) to.mBackgroundMode = from.mBackgroundMode;
        if( from.mSystemApp ) to.mSystemApp = true;
        if( from.mStaminaEnforced ) to.mStaminaEnforced = true;
        if( from.mImportantApp ) to.mImportantApp = true;
        if( from.mAllowWhileIdle ) to.mAllowWhileIdle = true;
        return to;
    }

    private BaikalAppProfile findOrAddDefaultProfile(String pkgName, 
                                HashMap<String, BaikalAppProfile> newProfiles, 
                                HashMap<Integer, BaikalAppProfile> newProfilesByUid,
                                HashMap<String, BaikalAppProfile> oldProfiles ) {

        //Slog.e(TAG, "Add system or important app:" + pkgName);

        BaikalAppProfile profile = null;

        //if( newProfiles.containsKey(pkgName)  ) {
        //    profile = newProfiles.get(pkgName);
        //}

        if(/* profile == null &&*/ oldProfiles.containsKey(pkgName) && !newProfiles.containsKey(pkgName) ) {
            Slog.e(TAG, "Found removed profile for:" + pkgName);
            BaikalAppProfile old_profile = oldProfiles.get(pkgName);
            old_profile.clear();
            profile = old_profile;
            //newProfiles.put(profile.mPackageName, profile);
        } 

        if( newProfiles.containsKey(pkgName)  ) {
            if( profile == null ) {
                profile = newProfiles.get(pkgName);
            } else {
                profile.update(newProfiles.get(pkgName));
                newProfiles.put(profile.mPackageName, profile);
                newProfilesByUid.put(profile.mUid, profile);
            }
        }

        if( profile == null ) {
            int uid = pkgName.equals("android") ? 1000 : getAppUidLocked(pkgName);
            profile = new BaikalAppProfile(pkgName, uid);
        }
               
        if( !newProfiles.containsKey(profile.mPackageName)  ) {
            newProfiles.put(profile.mPackageName, profile);
        }

        if( !newProfilesByUid.containsKey(profile.mUid)  ) {
            newProfilesByUid.put(profile.mUid, profile);
        } else {
            BaikalAppProfile found_profile = newProfilesByUid.get(profile.mUid);
            if( found_profile.mPackageName == null && !found_profile.mPackageName.equals(profile.mPackageName) ) {
                Slog.e(TAG, "Package " + profile.mPackageName +  " with uid " + profile.mUid + " already loaded with package name=" + found_profile.mPackageName);
            }
        }
        return profile;
    }

    void saveLocked() {

        Slog.e(TAG, "saveLocked()");

        String val = "";

        String appProfiles = Settings.Global.getString(mResolver,
            Settings.Global.BAIKALOS_APP_PROFILES);

        if( appProfiles == null ) {
            appProfiles = "";
        }

        for(Map.Entry<String, BaikalAppProfile> entry : _profilesByPackageName.entrySet()) {
            if( entry.getValue().isDefault() ) { 
                Slog.i(TAG, "Skip saving default profile for packageName=" + entry.getValue().mPackageName);
                continue;
            }
            int uid = getAppUidLocked(entry.getValue().mPackageName);
            if( uid == -1 ) { 
                Slog.i(TAG, "Skip saving profile for packageName=" + entry.getValue().mPackageName + ". Seems app deleted");
                continue;
            }

            //if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Save profile for packageName=" + entry.getValue().mPackageName);
            String entryString = entry.getValue().serialize();
            if( entryString != null ) val += entryString + "|";
        } 

        if( !val.equals(appProfiles) ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) { 
                Slog.i(TAG, "Write new profile data string :" + val);
                Slog.i(TAG, "Old profile data string :" + appProfiles);
            }
            Settings.Global.putString(mResolver,
                Settings.Global.BAIKALOS_APP_PROFILES,val);

            mLoadedProfileString = val;
        } else {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Skip writing new profile data string :" + val);
        }
    }

    public BaikalAppProfile updateSystemSettings(BaikalAppProfile profile) {
        synchronized(this) {
            return updateSystemSettingsLocked(profile);
        }
    }

    BaikalAppProfile updateSystemSettingsLocked(BaikalAppProfile profile) {

        boolean changed = false;

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateSystemSettingsLocked packageName=" + profile.mPackageName);

        int uid = profile.mUid > 0 ? profile.mUid : getAppUidLocked(profile.mPackageName);
        if( uid == -1 )  {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Can't get uid for " + profile.mPackageName);
            return null;
        }

        boolean isSystemWhitelisted = mBackend.isSysWhitelisted(profile.mPackageName);
        boolean isWhitelisted = mBackend.isWhitelisted(profile.mPackageName);

        //boolean isDefaultDialer = profile.mPackageName.equals(BaikalSettings.getDefaultDialer()) ? true : false;
        //boolean isDefaultSMS = profile.mPackageName.equals(BaikalSettings.getDefaultSMS()) ? true : false;
        //boolean isDefaultCallScreening = profile.mPackageName.equals(BaikalSettings.getDefaultCallScreening()) ? true : false;

        if( isSystemWhitelisted ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "System Whitelisted " + profile.mPackageName);
        }

        if( isWhitelisted ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Whitelisted " + profile.mPackageName);
        }

        if( isSystemWhitelisted ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "System Whitelisted " + profile.mPackageName);
            if( profile.mBackgroundMode > 0 ) { 
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "System Whitelisted for profile.mBackground > 0. Fix it " + profile.mPackageName);
                profile.mBackgroundMode = 0;
            }
            profile.mSystemWhitelisted = true;
        }


        boolean runInBackground = getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_IN_BACKGROUND,
                    uid, profile.mPackageName) == AppOpsManager.MODE_ALLOWED;

        boolean runAnyInBackground = getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,
                    uid, profile.mPackageName) == AppOpsManager.MODE_ALLOWED;

        if( !runAnyInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
        if( !runInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateSystemSettingsLocked packageName=" + profile.mPackageName 
            + ", uid=" + uid
            + ", runInBackground=" + runInBackground
            + ", runAnyInBackground=" + runAnyInBackground
            + ", profile.mBackground=" + profile.mBackgroundMode
            + ", profile.mSystemWhitelisted=" + profile.mSystemWhitelisted
            );

        switch(profile.mBackgroundMode) {
            case -2:
                if( !isSystemWhitelisted && !isWhitelisted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Add to whitelist packageName=" + profile.mPackageName + ", uid=" + uid);
                    mBackend.addApp(profile.mPackageName);
                }
                //if( !runAnyInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                //if( !runInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
            case -1:
                if( !isSystemWhitelisted && !isWhitelisted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Add to whitelist packageName=" + profile.mPackageName + ", uid=" + uid);
                    mBackend.addApp(profile.mPackageName);
                }
                //if( !runAnyInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                //if( !runInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
            break;

            case 0:
                /*if( !runAnyInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Drop OP_RUN_ANY_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }
                if( !runInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Drop OP_RUN_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }*/
                if( isWhitelisted ) mBackend.removeApp(profile.mPackageName);
            break;

            case 1:
                /*if( runAnyInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Drop OP_RUN_ANY_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_IGNORED); 
                }
                if( !runInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set OP_RUN_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }*/
                if( isWhitelisted ) mBackend.removeApp(profile.mPackageName);
            break;

            case 2:
                /*if( runAnyInBackground  ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set OP_RUN_ANY_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_IGNORED); 
                }
                if( !runInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set OP_RUN_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }*/
                if( isWhitelisted ) mBackend.removeApp(profile.mPackageName);
            break;
    
        }
        return profile;
    }

    BaikalAppProfile getProfileWithNullLocked(String packageName) {
        if( packageName != null ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) {
                Slog.d(TAG,"getProfileLocked(" + packageName + ")"/*, new Throwable()*/);
            }
            BaikalAppProfile profile = _profilesByPackageName.get(packageName);
            if( profile != null ) return profile;
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) {
            Slog.d(TAG,"getProfileLocked(" + packageName + ") : profile not found"/*,new Throwable()*/);
        } else {
            Slog.d(TAG,"getProfileLocked(" + packageName + ") : profile not found");
        }
        return null;
    }

    BaikalAppProfile getProfileLocked(String packageName) {
        if( packageName != null ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) {
                Slog.d(TAG,"getProfileLocked(" + packageName + ")"/*, new Throwable()*/);
            }
            BaikalAppProfile profile = _profilesByPackageName.get(packageName);
            if( profile != null ) return profile;
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) {
            Slog.d(TAG,"getProfileLocked(" + packageName + ") : profile not found"/*,new Throwable()*/);
        } else {
            Slog.d(TAG,"getProfileLocked(" + packageName + ") : profile not found");
        }
        return "android".equals(packageName) ? null : _profilesByUid.get(-1);
    }

    BaikalAppProfile getProfileLocked(int uid) {

        int appId = UserHandle.getAppId(uid);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) {
            Slog.d(TAG,"getProfileLocked(" + appId + "," + uid + ")"/*, new Throwable()*/);
        }
        BaikalAppProfile profile = _profilesByUid.get(appId);
        if( profile != null ) return profile;
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && BaikalConstants.BAIKAL_DEBUG_RAW ) {
            Slog.d(TAG,"getProfileLocked(" + appId + "," + uid + ") : profile not found"/*,new Throwable()*/);
        } else {
            Slog.d(TAG,"getProfileLocked(" + appId + "," + uid + ") : profile not found");
        }
        
        return appId < 10000 ? null : _profilesByUid.get(-1);
    }

    int getAppUidLocked(String packageName) {
        return getAppUid(packageName, mContext);
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

    public BaikalAppProfile getBaikalProfileWithNull(String packageName) {
        //synchronized(this) {
            return getProfileWithNullLocked(packageName);
        //}
    }

    public BaikalAppProfile getBaikalProfile(String packageName) {
        //synchronized(this) {
            return getProfileLocked(packageName);
        //}
    }

    public BaikalAppProfile getBaikalProfile(int uid) {
        //synchronized(this) {
            return getProfileLocked(uid);
        //}
    }


    public static boolean isSystemApp(String packageName, Context context) {

        final PackageManager pm = context.getPackageManager();

        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS | 
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS |
                PackageManager.MATCH_APEX |
                PackageManager.MATCH_ALL |
                PackageManager.MATCH_UNINSTALLED_PACKAGES |
                PackageManager.MATCH_INSTANT |
                PackageManager.MATCH_ANY_USER );
            if( ai != null ) {
                return  (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            }
        } catch(Exception e) {
            Slog.i(TAG,"Package " + packageName + " not found on this device", e);
        }
        return false;
    }



    public static int getAppUid(String packageName, Context context) {
	    int uid = -1;

        final PackageManager pm = context.getPackageManager();

        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS | 
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS |
                PackageManager.MATCH_APEX |
                PackageManager.MATCH_ALL |
                PackageManager.MATCH_UNINSTALLED_PACKAGES |
                PackageManager.MATCH_INSTANT |
                PackageManager.MATCH_ANY_USER );
            if( ai != null ) {
                if( ai.uid == 0 ) {
                    Slog.i(TAG,"Package " + packageName + " has invalid uid=0!!");
                    return -1;
                }
                return UserHandle.getAppId(ai.uid);
            }
        } catch(Exception e) {
            Slog.i(TAG,"Package " + packageName + " not found on this device", e);
        }
        return uid;
    }

    public static HashMap<Integer, BaikalAppProfile> updateProfileUids(HashMap<String, BaikalAppProfile> profilesByPackageName, Context context) {
        HashMap<Integer, BaikalAppProfile> profilesByUid = new HashMap<Integer, BaikalAppProfile> ();

        for(Map.Entry<String, BaikalAppProfile> entry : profilesByPackageName.entrySet()) {
            if( entry.getValue().isDefault() ) { 
                continue;
            }
            if( entry.getValue().mUid > 0 ) {
                profilesByUid.put(entry.getValue().mUid,entry.getValue());
            }
        }

        return profilesByUid;
    }

    public static HashMap<String, BaikalAppProfile> loadCachedProfiles(Context context) {

        HashMap<String, BaikalAppProfile> profilesByPackageName = new HashMap<String, BaikalAppProfile> ();

        try {
            String appProfiles = Settings.Global.getString(context.getContentResolver(),
                    Settings.Global.BAIKALOS_APP_PROFILES);

            if( appProfiles == null ) {
                Slog.e(TAG, "Empty profiles settings");
                return null;
            }

            TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter('|');

            try {
                splitter.setString(appProfiles);
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Bad profiles settings", e);
                return null;
            }

            for(String profileString:splitter) {
                BaikalAppProfile profile = BaikalAppProfile.deserializeProfile(profileString);
                if( profile != null  ) {
                    profilesByPackageName.put(profile.mPackageName, updateProfileFromSystemApplist(profile,context));
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Bad BaikalService settings", e);
        } 
        return profilesByPackageName;
    }

    public static BaikalAppProfile loadSingleProfile(String packageName, int uid, Context context) {

        Slog.e(TAG, "loadSingleProfile:" + packageName);

        if( packageName == null ) {
            return null;
        }

        try {
            String appProfiles = Settings.Global.getString(context.getContentResolver(),
                    Settings.Global.BAIKALOS_APP_PROFILES);

            if( appProfiles == null ) {
                Slog.e(TAG, "Empty profiles settings");
                return null;
            }

            TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter('|');

            try {
                splitter.setString(appProfiles);
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Bad profiles settings", e);
                return null;
            }

            for(String profileString:splitter) {
                BaikalAppProfile profile = BaikalAppProfile.deserializeProfile(profileString);
                if( profile != null  ) {
                    if( profile.mPackageName.equals(packageName) ) 
                        return updateProfileFromSystemApplist(profile,context);
                    
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Bad BaikalService settings", e);
        } 

        BaikalAppProfile default_profile = new BaikalAppProfile(packageName, uid);
        
        return updateProfileFromSystemApplist(default_profile,context); 
    }

    static BaikalAppProfile updateProfileFromSystemApplist(BaikalAppProfile profile, Context context) {
        return profile;
    }

    public static void resetAll(ContentResolver resolver) {
        Settings.Global.putString(resolver,
            Settings.Global.BAIKALOS_APP_PROFILES,"");

    }

    public static void saveBackup(ContentResolver resolver) {
               
        String appProfiles = Settings.Global.getString(resolver,
                Settings.Global.BAIKALOS_APP_PROFILES);

        if( appProfiles == null ) appProfiles = "";

        Settings.Global.putString(resolver,
            Settings.Global.BAIKALOS_APP_PROFILES_BACKUP,appProfiles);
        
    }

    public static void restoreBackup(ContentResolver resolver) {
        
        String appProfiles = Settings.Global.getString(resolver,
                Settings.Global.BAIKALOS_APP_PROFILES_BACKUP);

        if( appProfiles == null ) appProfiles = "";

        Settings.Global.putString(resolver,
            Settings.Global.BAIKALOS_APP_PROFILES,appProfiles);
        
    }

    static void setZygoteSettings(String propPrefix, String packageName, String value) {
        try {
            SystemProperties.set(propPrefix + packageName,value);
        } catch(Exception e) {
            Slog.e(TAG, "BaikalService: Can't set Zygote settings:" + packageName, e);
        }
    }

    void setBackgroundMode(int op, int uid, String packageName, int mode) {
        if( BaikalConstants.BAIKAL_DEBUG_RAW && BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) {
            Slog.i(TAG, "Set AppOp " + op + " for packageName=" + packageName + ", uid=" + uid + " to " + mode, new Throwable());
        } else {
            BaikalConstants.Logi(BaikalConstants.BAIKAL_DEBUG_APP_PROFILE, uid, TAG, "Set AppOp " + op + " for packageName=" + packageName + ", uid=" + uid + " to " + mode);
        }
        if( uid != -1 ) {
            getAppOpsManager().setMode(op, uid, packageName, mode);
        }
    }

    AppOpsManager getAppOpsManager() {
        
        if (mAppOpsManager == null) {
            mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        }
        return mAppOpsManager;
    }

    public static boolean isDebugUid(int uid) {
        return _mAppsForDebug.contains(UserHandle.getAppId(uid));
    }

    public boolean isStaminaWl(int uid, String packageName) {
        //if( UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID ) return true;
        if( packageName == null ) return false;
        if( packageName.contains("auto_generated_rro_") ) return false;
        if( packageName.startsWith("com.android.service.ims") ) return true;
        if( packageName.startsWith("com.android.launcher3") ) return true;
        if( packageName.startsWith("com.android.systemui.plugin") ) return true;
        if( packageName.equals("com.android.systemui") ) return true;
        if( packageName.startsWith("com.android.nfc") ) return true;
        if( packageName.startsWith("com.android.providers") ) return true;
        if( packageName.startsWith("com.android.inputmethod") ) return true;
        if( packageName.startsWith("com.qualcomm.qti.telephonyservice") ) return true;
        if( packageName.startsWith("com.android.phone") ) return true;
        if( packageName.startsWith("com.android.server.telecom") ) return true;
        if( packageName.startsWith("com.android.dialer") ) return true;
        if( packageName.startsWith("com.google.android.dialer") ) return true;
        if( packageName.startsWith("com.google.android.gsf") ) return true;
        if( packageName.startsWith("com.google.android.gms") ) return true;
        if( packageName.startsWith("com.google.android.contacts") ) return true;
        if( packageName.startsWith("com.google.android.calendar") ) return true;
        if( packageName.startsWith("com.google.android.ims") ) return true;
        if( packageName.startsWith("com.google.android.ext.shared") ) return true;
        return false;
    }

}
