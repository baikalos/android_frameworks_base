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
import android.baikalos.AppProfile;

import android.net.Uri;

import android.database.ContentObserver;

import android.provider.Settings;

import android.util.ArraySet;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class AppProfileSettings extends ContentObserver {

    private static final String TAG = "BaikalPreferences";

    private static AppProfileSettings sInstance;

    private final Context mContext;
    private ContentResolver mResolver;

    private final TextUtils.StringSplitter mSplitter = new TextUtils.SimpleStringSplitter('|');

    private HashMap<String, AppProfile> _profilesByPackageName = new HashMap<String,AppProfile> ();

    private static HashMap<String, AppProfile> _staticProfilesByPackageName = null; 

    private PowerWhitelistBackend mBackend;

    private PackageManager mPackageManager;
    private AppOpsManager mAppOpsManager;

    private boolean mAutorevokeDisabled;

    private static AppProfile sSystemProfile;

    private static boolean mAwaitSystemBoot;

    static String [] config_performanceValues = {
        "default",
        "balance",
        "limited",
        "video",
        "performance",
        "gaming",
        "gaming2",
        "battery",
        "reader",
        "screen_off",
        "idle",

    };

    public static HashMap<String, AppProfile> internalGetStaticProfilesByPackageName() {
        return _staticProfilesByPackageName;
    }

    private static HashMap<String, Integer> _performanceToId = new HashMap<String, Integer>();

    public static String perfProfileNameFromId(int id) {
        if( id < 0 || id > config_performanceValues.length ) return "default";
        return config_performanceValues[id];
    }

    public static int perfProfileIdFromName(String name) {
        return _performanceToId.get(name);
    }

    public interface IAppProfileSettingsNotifier {
        void onAppProfileSettingsChanged();
    }

    private IAppProfileSettingsNotifier mNotifier = null;

    private static boolean _updating = false;
    private AppProfileSettings(Handler handler,Context context) {
        super(handler);
        mContext = context;

        for( int i=0; i < config_performanceValues.length;i++ ) {
            _performanceToId.put( config_performanceValues[i], i );    
        }  

        if( sSystemProfile == null ) {
            sSystemProfile = new AppProfile("android");
            sSystemProfile.mSystemWhitelisted = true;
        }
    }

    public void registerObserver(boolean systemBoot) {
        mResolver = mContext.getContentResolver();
        mPackageManager = mContext.getPackageManager();
        mBackend = PowerWhitelistBackend.getInstance(mContext);

        try {
            mResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BAIKALOS_APP_PROFILES),
                false, this);

            mResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BAIKALOS_DISABLE_AUTOREVOKE),
                false, this);
        } catch( Exception e ) {
        }
        
        synchronized(this) {
            mBackend.refreshList();
            updateConstantsLocked();
            if( systemBoot ) {
                mAwaitSystemBoot = true; 
                updateProfilesOnBootLocked();
            }
        }
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Preferences changed. Reloading");
        synchronized(this) {
            mBackend.refreshList();
            updateConstantsLocked();
        }
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Preferences changed. Reloading - done");
    }

    private AppProfile updateProfileFromSystemSettingsLocked(AppProfile profile) {
        int uid = getAppUidLocked(profile.mPackageName);
        if( uid == -1 )  {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Can't get uid for " + profile.mPackageName);
            return new AppProfile(profile.mPackageName);
        }
        boolean isSystemWhitelisted = mBackend.isSysWhitelisted(profile.mPackageName);
        if( isSystemWhitelisted ) {
            if( profile.mBackground >= -1 ) profile.mBackground = 0;
            profile.mSystemWhitelisted = true;
            return profile;
        }

        boolean isWhitelisted = mBackend.isWhitelisted(profile.mPackageName);
        if( isWhitelisted ) {
            if( profile.mBackground >= 0 )  profile.mBackground = -1;
            return profile;
        }

        boolean runInBackground = getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_IN_BACKGROUND,
                    uid, profile.mPackageName) == AppOpsManager.MODE_ALLOWED;        

        boolean runAnyInBackground = getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,
                    uid, profile.mPackageName) == AppOpsManager.MODE_ALLOWED;

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateProfileFromSystemSettingsLocked packageName=" + profile.mPackageName 
            + ", uid=" + uid
            + ", runInBackground=" + runInBackground
            + ", runAnyInBackground=" + runAnyInBackground
            + ", profile.mBackground=" + profile.mBackground
            );


        if( runInBackground && runAnyInBackground ) {
            if( profile.mBackground > 0 ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateProfileFromSystemSettingsLocked fix background 0 packageName=" + profile.mPackageName);
                profile.mBackground = 0;
            }
            return profile;
        }

        if( runInBackground && !runAnyInBackground ) {
            if( profile.mBackground != 1 ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateProfileFromSystemSettingsLocked fix background 1 packageName=" + profile.mPackageName);
                profile.mBackground = 1;
            }
            return profile;
        }
        
        if( !runInBackground ) {
            if( profile.mBackground != 2 ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateProfileFromSystemSettingsLocked fix background 2 packageName=" + profile.mPackageName);
                profile.mBackground = 2;
            }
            return profile;
        }
        return profile;
    }

    private AppProfile updateSystemSettingsLocked(AppProfile profile) {

        boolean changed = false;

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateSystemSettingsLocked packageName=" + profile.mPackageName);

        int uid = getAppUidLocked(profile.mPackageName);
        if( uid == -1 )  {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Can't get uid for " + profile.mPackageName);
            return null;
        }

        boolean isSystemWhitelisted = mBackend.isSysWhitelisted(profile.mPackageName);
        boolean isWhitelisted = mBackend.isWhitelisted(profile.mPackageName);

        //boolean isDefaultDialer = profile.mPackageName.equals(BaikalSettings.getDefaultDialer()) ? true : false;
        //boolean isDefaultSMS = profile.mPackageName.equals(BaikalSettings.getDefaultSMS()) ? true : false;
        //boolean isDefaultCallScreening = profile.mPackageName.equals(BaikalSettings.getDefaultCallScreening()) ? true : false;

        if( isWhitelisted ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Whitelisted " + profile.mPackageName);
        }

        if( isSystemWhitelisted ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "System Whitelisted " + profile.mPackageName);
        }

        if( isSystemWhitelisted ) {
            if( profile.mBackground > 0 ) { 
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "System Whitelisted for profile.mBackground > 0. Fix it " + profile.mPackageName);
                profile.mBackground = 0;
            }
            profile.mSystemWhitelisted = true;
            return profile;
        }


        boolean runInBackground = getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_IN_BACKGROUND,
                    uid, profile.mPackageName) == AppOpsManager.MODE_ALLOWED;        

        boolean runAnyInBackground = getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,
                    uid, profile.mPackageName) == AppOpsManager.MODE_ALLOWED;

        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateSystemSettingsLocked packageName=" + profile.mPackageName 
            + ", uid=" + uid
            + ", runInBackground=" + runInBackground
            + ", runAnyInBackground=" + runAnyInBackground
            + ", profile.mBackground=" + profile.mBackground
            );

        switch(profile.mBackground) {
            case -2:
                if( !isSystemWhitelisted && !isWhitelisted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Add to whitelist packageName=" + profile.mPackageName + ", uid=" + uid);
                    mBackend.addApp(profile.mPackageName);
                }
                if( !runAnyInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                if( !runInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
            case -1:
                if( !isSystemWhitelisted && !isWhitelisted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Add to whitelist packageName=" + profile.mPackageName + ", uid=" + uid);
                    mBackend.addApp(profile.mPackageName);
                }
                if( !runAnyInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                if( !runInBackground ) setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
            break;

            case 0:
                if( !runAnyInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Drop OP_RUN_ANY_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }
                if( !runInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Drop OP_RUN_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }
                if( isWhitelisted ) mBackend.removeApp(profile.mPackageName);
            break;

            case 1:
                if( runAnyInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Drop OP_RUN_ANY_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_IGNORED); 
                }
                if( !runInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set OP_RUN_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_ALLOWED); 
                }
                if( isWhitelisted ) mBackend.removeApp(profile.mPackageName);
            break;

            case 2:
                if( runAnyInBackground  ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set OP_RUN_ANY_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_IGNORED); 
                }
                if( runInBackground ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set OP_RUN_IN_BACKGROUND packageName=" + profile.mPackageName + ", uid=" + uid);
                    setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, profile.mPackageName,AppOpsManager.MODE_IGNORED); 
                }
                if( isWhitelisted ) mBackend.removeApp(profile.mPackageName);
            break;
    
        }
        return profile;
    }

    public static AppProfile loadSingleProfile(String packageName, ContentResolver resolver) {

        Slog.e(TAG, "loadSingleProfile:" + packageName);

        if( "android".equals(packageName) || "system".equals(packageName) ) return sSystemProfile;

        try {
            String appProfiles = Settings.Global.getString(resolver,
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
                AppProfile profile = AppProfile.deserializeProfile(profileString);
                if( profile != null  ) {
                    if( profile.mPackageName.equals(packageName) ) return profile;
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Bad BaikalService settings", e);
            return null;
        } 
        return null;
    }


    private void updateProfilesOnBootLocked() {
        //updateProfilesFromInstalledPackagesLocked();
        updateSystemFromInstalledPackagesLocked();
    }

    private void updateConstantsLocked() {

        Slog.e(TAG, "Loading AppProfiles");
        mBackend.refreshList();

        try {

            mAutorevokeDisabled = Settings.Global.getInt(mResolver,
                    Settings.Global.BAIKALOS_DISABLE_AUTOREVOKE,0) == 1;

            String appProfiles = Settings.Global.getString(mResolver,
                    Settings.Global.BAIKALOS_APP_PROFILES);

            if( appProfiles == null ) {
                Slog.e(TAG, "Empty profiles settings");
                appProfiles = "";
            }

            try {
                mSplitter.setString(appProfiles);
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Bad profiles settings", e);
                return ;
            }

            HashMap<String,AppProfile> _oldProfiles = _profilesByPackageName;

            _profilesByPackageName = new HashMap<String,AppProfile> ();
            _profilesByPackageName.put(sSystemProfile.mPackageName,sSystemProfile);

            for(String profileString:mSplitter) {
                
                AppProfile profile = AppProfile.deserializeProfile(profileString); 
                if( profile != null  ) {

                    int uid = getAppUidLocked(profile.mPackageName);
                    if( uid == -1 ) continue;

                    profile.mUid = uid;

                    if( profile == null ) {
                        continue;
                    }

                    if( _oldProfiles.containsKey(profile.mPackageName) ) {
                        AppProfile old_profile = _oldProfiles.get(profile.mPackageName);
                        old_profile.update(profile);
                        profile = old_profile;
                    } 

                    if( !_profilesByPackageName.containsKey(profile.mPackageName)  ) {
                        _profilesByPackageName.put(profile.mPackageName, profile);
                    }
                }
            }

            for(Map.Entry<String, AppProfile> entry : _oldProfiles.entrySet()) {
                entry.getValue().isInvalidated = true;
            }
            _oldProfiles.clear();


        } catch (Exception e) {
            Slog.e(TAG, "Bad BaikalService settings", e);
            return;
        }

        _staticProfilesByPackageName =  _profilesByPackageName;
        Slog.e(TAG, "Loaded " + _profilesByPackageName.size() + " AppProfiles");
    }

    private void updateSystemFromInstalledPackagesLocked() {
        boolean changed = false;
        List<PackageInfo> installedAppInfo = mPackageManager.getInstalledPackages(/*PackageManager.GET_PERMISSIONS*/0);
        for (PackageInfo info : installedAppInfo) {
            AppProfile profile = null;
            if( !_profilesByPackageName.containsKey(info.packageName) ) {
                profile = new AppProfile(info.packageName);
            } else {
                profile = _profilesByPackageName.get(info.packageName);
            }

            profile = updateSystemSettingsLocked(profile);
            if( !profile.isDefault() && !_profilesByPackageName.containsKey(info.packageName) ) {
                _profilesByPackageName.put(info.packageName,profile);
            }
        }
    }

    private void updateProfilesFromInstalledPackagesLocked() {
        boolean changed = false;
        List<PackageInfo> installedAppInfo = mPackageManager.getInstalledPackages(/*PackageManager.GET_PERMISSIONS*/0);
        for (PackageInfo info : installedAppInfo) {
            AppProfile profile = null ; 
            if( !_profilesByPackageName.containsKey(info.packageName) ) {
                profile = new AppProfile(info.packageName);
            } else {
                profile = _profilesByPackageName.get(info.packageName);
            }

            profile = updateProfileFromSystemSettingsLocked(profile);
            if( !profile.isDefault() && !_profilesByPackageName.containsKey(info.packageName) ) {
                _profilesByPackageName.put(info.packageName,profile);
            }
        }
    }

    public void updateSystemFromInstalledPackages() {
        synchronized(this) {
            updateSystemFromInstalledPackagesLocked();
        }
    }

    public void updateProfilesFromInstalledPackages() {
        synchronized(this) {
            updateProfilesFromInstalledPackagesLocked();
        }
    }

    public AppProfile updateSystemSettings(AppProfile profile) {
        synchronized(this) {
            return updateSystemSettingsLocked(profile);
        }
    }

    public AppProfile updateProfileFromSystemSettings(AppProfile profile) {
        synchronized(this) {
            return updateProfileFromSystemSettingsLocked(profile);
        }
    }

    public static void setZygoteSettings(String propPrefix, String packageName, String value) {
        try {
            SystemProperties.set(propPrefix + packageName,value);
        } catch(Exception e) {
            Slog.e(TAG, "BaikalService: Can't set Zygote settings:" + packageName, e);
        }
    }
    
    private void setBackgroundMode(int op, int uid, String packageName, int mode) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Set AppOp " + op + " for packageName=" + packageName + ", uid=" + uid + " to " + mode);
        if( uid != -1 ) {
            getAppOpsManager().setMode(op, uid, packageName, mode);
        }
    }
 

    public void saveLocked() {

        Slog.e(TAG, "saveLocked()");

        String val = "";

        String appProfiles = Settings.Global.getString(mResolver,
            Settings.Global.BAIKALOS_APP_PROFILES);

        if( appProfiles == null ) {
            appProfiles = "";
        }

        //synchronized(mBackend) {
        mBackend.refreshList();
        //}

        for(Map.Entry<String, AppProfile> entry : _profilesByPackageName.entrySet()) {
            if( entry.getValue().isDefault() ) { 
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Skip saving default profile for packageName=" + entry.getValue().mPackageName);
                continue;
            }
            int uid = getAppUidLocked(entry.getValue().mPackageName);
            if( uid == -1 ) { 
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Skip saving profile for packageName=" + entry.getValue().mPackageName + ". Seems app deleted");
                continue;
            }

            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "Save profile for packageName=" + entry.getValue().mPackageName);
            String entryString = entry.getValue().serialize();
            if( entryString != null ) val += entryString + "|";
        } 

        if( appProfiles != null && !appProfiles.equals(val) ) {
            Settings.Global.putString(mResolver,
                Settings.Global.BAIKALOS_APP_PROFILES,val);
        }
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


    public AppProfile getProfileLocked(String packageName) {
        if( packageName != null ) {
            if( "android".equals(packageName) || "system".equals(packageName) ) return sSystemProfile;
	        return _profilesByPackageName.get(packageName);
        }
        return null;
    }

    public void updateProfileLocked(AppProfile profile) {
        if( "android".equals(profile.mPackageName) || "system".equals(profile.mPackageName) ) return;
        AppProfile newProfile = profile;
        if( !_profilesByPackageName.containsKey(profile.mPackageName) ) {
            _profilesByPackageName.put(profile.mPackageName, profile);
            newProfile = profile;
        } else {
            AppProfile oldProfile = _profilesByPackageName.get(profile.mPackageName);
            oldProfile.update(profile);
            newProfile = oldProfile;
        }
    }

    private int getAppUidLocked(String packageName) {
	    int uid = -1;

        final PackageManager pm = mContext.getPackageManager();

        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName,
                    PackageManager.MATCH_ALL);
            if( ai != null ) {
                return ai.uid;
            }
        } catch(Exception e) {
            Slog.i(TAG,"Package " + packageName + " not found on this device");
        }
        return uid;
    }


    private boolean is_changed = false;
    public void save() {
        is_changed = true;
        Slog.e(TAG, "save()");
    }

    public void commit() {
        synchronized(this) {
            Slog.e(TAG, "commit()");
            saveLocked();
        }
        is_changed = false;
    }

    public AppProfile getProfile(String packageName) {
        synchronized(this) {
            return getProfileLocked(packageName);
        }
    }

    public void updateProfile(AppProfile profile) {
        synchronized(this) {
            updateProfileLocked(profile);
        }
    }

    public static AppProfile getProfileStatic(String packageName) {
        if( "android".equals(packageName) || "system".equals(packageName) ) return sSystemProfile;
        if( packageName == null ) return null;
        if( _staticProfilesByPackageName == null ) return null;
	    return _staticProfilesByPackageName.get(packageName);
    }

    AppOpsManager getAppOpsManager() {
        if (mAppOpsManager == null) {
            mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        }
        return mAppOpsManager;
    }

    public boolean isAutoRevokeDisabled() {
        return mAutorevokeDisabled;
    }

    public static AppProfileSettings getInstance() {
        return sInstance;
    }

    public static AppProfileSettings getInstance(Handler handler, Context context) {
        if (sInstance == null) {
            sInstance = new AppProfileSettings(handler,context);
        }
        return sInstance;
    }
}
