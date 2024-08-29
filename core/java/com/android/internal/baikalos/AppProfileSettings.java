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
import android.baikalos.AppProfile;

import android.net.Uri;

import android.database.ContentObserver;

import android.provider.Settings;

import android.util.ArraySet;
import android.util.Pair;
import android.util.SparseIntArray;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AppProfileSettings extends AppProfileBase {

    private static final String TAG = "BaikalSettings";

    private static AppProfileSettings sInstance;
    private static boolean sScreenMode = true;
    private static boolean sSuperSaverAvailable = false;

    private boolean mAutorevokeDisabled;

    private AppProfileSettings(Handler handler,Context context) {
        super(handler,context);

        final Resources resources = mContext.getResources();

        sSuperSaverAvailable = resources.getBoolean(
                com.android.internal.R.bool.config_superSaverAvailable);

    }

    public void registerObserver(boolean systemBoot) {
        if( !mIsReady ) return;
        super.registerObserver(systemBoot);

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
            if( systemBoot ) { 
                updateProfilesOnBootLocked();
            }
            updateConstantsLocked();
        }
        loadProfiles();
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if( !mIsReady ) return;
        Slog.i(TAG, "Preferences changed (selfChange=" + selfChange + ", uri=" + uri + "). Reloading");

        String appProfileString = "";

        synchronized(this) {
            Slog.i(TAG, "Preferences changed (selfChange=" + selfChange + ", uri=" + uri + "). Reloading locked");
            updateConstantsLocked();
            Slog.i(TAG, "Preferences changed. Reloading - done locked");

            appProfileString = Settings.Global.getString(mResolver,
                    Settings.Global.BAIKALOS_APP_PROFILES);

            if( mLoadedProfileString != null && mLoadedProfileString.equals(appProfileString) ) {
                Slog.i(TAG,"updateConstantsLocked: Not changed. Ignore");
                return;
            }
        }
        mBackend.refreshList();
        loadProfiles(appProfileString);

        Slog.i(TAG, "Preferences changed. Reloading - done");
    }

    private void updateProfilesOnBootLocked() {
    }

    private static boolean selfUpdate;
    private void updateConstantsLocked() {
        if( !mIsReady ) return;
        mAutorevokeDisabled = Settings.Global.getInt(mResolver,
            Settings.Global.BAIKALOS_DISABLE_AUTOREVOKE,0) == 1;

    }


    public static void updateRulesForWhitelistedAppIdsBaikal(final SparseIntArray uidRules, int userId, int chain) {
    
        
        AppProfileSettings _settings = AppProfileSettings.getInstance();
        if( _settings == null ) {
            Slog.e(TAG,"updateRulesForWhitelistedAppIdsBaikal: Not ready yet");
            return;
        }
        
        PackageManager packageManager = _settings.mContext.getPackageManager();

        if( packageManager == null ) {
            Slog.e(TAG,"updateRulesForWhitelistedAppIdsBaikal: Not ready!");
            return;
        }

        final HashMap<String, AppProfile> profilesByPackageName =  _settings._profilesByPackageName;
        final HashMap<Integer, AppProfile> profilesByUid =  _settings._profilesByUid;

        final int minlevel = chain != 2 ? 0 : 1;

        List<PackageInfo> installedAppInfo = packageManager.getInstalledPackages(0);
        for (PackageInfo info : installedAppInfo) {
            AppProfile profile = null ; 
            if( profilesByPackageName.containsKey(info.packageName) ) {
                profile = profilesByPackageName.get(info.packageName);
            } else if( profilesByUid.containsKey(info.applicationInfo.uid) ) {
                profile = profilesByUid.get(info.applicationInfo.uid);
            } else {
                profile = new AppProfile(info.packageName, info.applicationInfo.uid);
            }

            if( profile.mImportantApp ||
                profile.mAllowIdleNetwork || 
                profile.mStamina || 
                profile.getBackgroundMode(false) < minlevel ) {

                    final int uid = UserHandle.getUid(userId, UserHandle.getAppId(info.applicationInfo.uid));
                    if( BaikalConstants.BAIKAL_DEBUG_NETWORK ) Slog.d(TAG,"updateRulesForWhitelistedAppIdsBaikal:" + info.packageName + "/" + uid + " " + chainToString(chain));
                    uidRules.put(uid, 1);
            }
        }
    }

    static String sChainNames[] = {
        "FIREWALL_CHAIN_NONE",
        "FIREWALL_CHAIN_DOZABLE",
        "FIREWALL_CHAIN_STANDBY",
        "FIREWALL_CHAIN_POWERSAVE",
        "FIREWALL_CHAIN_RESTRICTED",
        "FIREWALL_CHAIN_LOW_POWER_STANDBY",
        "FIREWALL_CHAIN_LOCKDOWN_VPN",
        "FIREWALL_CHAIN_OEM_DENY_1",
        "FIREWALL_CHAIN_OEM_DENY_2",
        "FIREWALL_CHAIN_OEM_DENY_3" };

    private static String chainToString(int chain) {
        if( chain >= 0  && chain < 10 ) return sChainNames[chain];
        return " FIREWALL_CHAIN_INVALID(" + chain + ")";
    }

    public static void updateBackgroundRestrictedUidPackagesLocked(Set<Pair<Integer, String>> backgroundRestrictedUidPackages, boolean forceAllAppsStandby) {

        AppProfileSettings _settings = AppProfileSettings.getInstance();
        if( _settings == null ) {
            Slog.i(TAG,"updateBackgroundRestrictedUidPackagesLocked: Not ready yet");
            return;
        }
        
        final HashMap<String, AppProfile> profilesByPackageName =  _settings._profilesByPackageName;
        final HashMap<Integer, AppProfile> profilesByUid =  _settings._profilesByUid;
        
        Slog.i(TAG,"updateBackgroundRestrictedUidPackagesLocked:" + forceAllAppsStandby);

        PackageManager packageManager = _settings.mContext.getPackageManager();

        if( packageManager == null ) {
            Slog.i(TAG,"updateBackgroundRestrictedUidPackagesLocked: Not ready!");
            return;
        }

        Slog.i(TAG,"updateBackgroundRestrictedUidPackagesLocked: profilesByPackageName=" + profilesByPackageName.size());

        List<PackageInfo> installedAppInfo = packageManager.getInstalledPackages(0);
        for (PackageInfo info : installedAppInfo) {
            AppProfile profile = null ; 
            if( profilesByPackageName.containsKey(info.packageName) ) {
                profile = profilesByPackageName.get(info.packageName);
                if( profile == null ) {
                    Slog.w(TAG, "updateBackgroundRestrictedUidPackagesLocked: WTF profile(package)=null packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    continue;
                }
            } else if( profilesByUid.containsKey(info.applicationInfo.uid) ) {
                profile = profilesByUid.get(info.applicationInfo.uid);
                if( profile == null ) {
                    Slog.w(TAG, "updateBackgroundRestrictedUidPackagesLocked: WTF profile(uid)=null packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    continue;
                }
            } else {
                profile = new AppProfile(info.packageName, info.applicationInfo.uid);
            }


            boolean isSystem = (info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if( profile.mPackageName == null ) {
                Slog.w(TAG, "updateBackgroundRestrictedUidPackagesLocked: WTF profile packageName=null packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                continue;
            }

            Pair<Integer,String> pair = Pair.create(info.applicationInfo.uid, info.packageName);
            final int uid = info.applicationInfo.uid;

            boolean restricted = backgroundRestrictedUidPackages.contains(pair);
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE && restricted) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: system restricted app packageName=" + profile.mPackageName + "/" + info.applicationInfo.uid);

            boolean runInBackground = _settings.getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_IN_BACKGROUND,
                    uid, info.packageName) == AppOpsManager.MODE_ALLOWED;        

            boolean runAnyInBackground = _settings.getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,
                    uid, info.packageName) == AppOpsManager.MODE_ALLOWED;

            //if( !runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_ALLOWED); 
            //if( !runInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_ALLOWED); 

            int backgroundMode = profile.getBackgroundMode(forceAllAppsStandby);

            if( backgroundMode < 1 && ( /*isSystem ||*/ profile.mAllowWhileIdle || profile.isHome() || profile.mImportantApp ) ) {
                if( restricted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: unrestrict system or allowed packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    backgroundRestrictedUidPackages.remove(pair);
                }
                continue;
            }

            if( backgroundMode >= 2 ) {
                if( runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_IGNORED); 
                if( restricted ) continue;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: Restrict 2 packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                backgroundRestrictedUidPackages.add(pair);
            } else if( backgroundMode == 1 && !sScreenMode ) {
                if( runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_IGNORED); 
                if( restricted ) continue;
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: Restrict 1 packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                backgroundRestrictedUidPackages.add(pair);
            } else if( backgroundMode == 1 && sScreenMode ) {
                if( !runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_ALLOWED); 
                if( restricted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: Unrestrict 1 packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    backgroundRestrictedUidPackages.remove(pair);
                }
            } else if( backgroundMode == 0 )  {
                if( !runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_ALLOWED); 
                if( restricted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: Unrestrict 0 packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    backgroundRestrictedUidPackages.remove(pair);
                }
            } else if( backgroundMode < 0 )  {
                if( !runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_ALLOWED); 
                if( restricted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: Unrestrict whitelisted packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    backgroundRestrictedUidPackages.remove(pair);
                }
            } else {
                if( !runAnyInBackground ) _settings.setBackgroundMode(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,uid, info.packageName,AppOpsManager.MODE_ALLOWED); 
                if( restricted ) {
                    if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.i(TAG, "updateBackgroundRestrictedUidPackagesLocked: WTF? Unrestrict whitelisted packageName=" + info.packageName + "/" + info.applicationInfo.uid);
                    backgroundRestrictedUidPackages.remove(pair);
                }
            }
        } 
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
    
    private static boolean sSuperSaver;
    public static boolean isSuperSaver() {
        return sSuperSaver;
    }

    public static boolean setSuperSaver(boolean enable) {
        if( enable != sSuperSaver ) {
            sSuperSaver = enable;
            return true;
        }
        return false;
    }

    private static int sReaderMode;
    public static int getReaderMode() {
        return sReaderMode;
    }

    public static boolean setReaderMode(int enable) {
        if( enable != sReaderMode ) {
            sReaderMode = enable;
            return true;
        }
        return false;
    }

    public static boolean isSuperSaverActive() {
        return sSuperSaverAvailable && (sReaderMode != -1) && ( (sSuperSaver || sReaderMode == 1)  && !sSuperSaverOverride && !sCameraActive );
    }


    private static boolean sSuperSaverForDraw;

    public static boolean setSuperSaverActiveForDraw(boolean enable) {
        if( enable != sSuperSaverForDraw ) {
            sSuperSaverForDraw = enable;
            return true;
        }
        return false;
    }

    public static boolean isSuperSaverActiveForDraw() {
        return !sSuperSaverForDraw && isSuperSaverActive();
    }

    private static boolean sSuperSaverOverride;
    public static boolean setSuperSaverOverride(boolean enable) {
        if( enable != sSuperSaverOverride ) {
            sSuperSaverOverride = enable;
            return true;
        }
        return false;
    }

    public static boolean isSuperSaverOverride() {
        return sSuperSaverOverride;
    }

    private static boolean sCameraActive;
    public static boolean isCameraActive() {
        return sCameraActive;
    }

    public static boolean setCameraActive(boolean active) {
        Slog.i(TAG, "setCameraActive: active=" + active);
        if( active != sCameraActive ) {
            sCameraActive = active;
            return true;
        }
        return false;
    }

    public static void setScreenMode(boolean on) {
        sScreenMode = on;
    }


}
