package com.android.server.baikalos;

import android.baikalos.BaikalAppProfile;
import android.baikalos.IBaikalService;
import android.content.Context;
import android.content.ContentResolver;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.baikalos.*;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.ConcurrentUtils;

import com.android.server.LocalServices;
import com.android.server.ServiceThread;


import com.android.server.am.BaikalActivityManagerService;
import com.android.server.display.brightness.strategy.BaikalBrightnessStrategy;
import com.android.server.pm.BaikalPackageManagerService;
import com.android.server.power.BaikalPowerManagerService;
import com.android.server.location.BaikalLocationManager;


import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to manage per-app profiles. Supports Binder and LocalService.
 * All internal methods are thread-safe.
 */
public final class BaikalService extends com.android.server.SystemService {

    private static final String TAG = "BaikalService";

    private final IBaikalAppProfileInternal mBaikalAppProfileInternal;
    BaikalAppProfileService mBaikalAppProfileService;

    BaikalActivityManagerService mBaikalActivityManagerService;
    BaikalPackageManagerService mBaikalPackageManagerService;
    BaikalPowerManagerService mBaikalPowerManagerService;
    BaikalLocationManager mBaikalLocationManager;

    BaikalAppManager mBaikalAppManager;
    BaikalPowerManager mBaikalPowerManager;
    BaikalDebugManager mBaikalDebugger;
    BaikalSettings mBaikalSettings;
    BaikalChargingController mBaikalChargingController;
    BaikalAttestationService mAttestation;
    BaikalActions mActions; 
    BaikalSpecialDevices mBaikalSpecialDevices;
    BaikalAppVolumeDB mVolumeDB;

    private final Context mContext;
    private ServiceThread mWorker;
    private Handler mHandler;

    private static BaikalService mInstance;
    private boolean isReady = false;

    int[] mDeviceIdleSystemExceptIdleWhitelist = new int[0];
    int[] mDeviceIdleSystemWhitelist = new int[0];
    int[] mDeviceIdleUserWhitelist = new int[0];

    private final Object mLock = new Object(); //LockGuard.installNewLock(LockGuard.INDEX_POWER);

    private final SparseIntArray mBackgroundStartStats = new SparseIntArray();

    public static IBaikalInternal getService() {
        return LocalServices.getService(IBaikalInternal.class);
    }

    public static BaikalService getInstance() {
        return mInstance;
    }

    Handler getHandler() {
        return mHandler;
    }

    ServiceThread getWorkerThread() {
        return mWorker;
    }

    public BaikalSettings getSettings() {
        return mBaikalSettings;
    }

    public BaikalService(Context context) {
        super(context);
        mInstance = this;
        mContext = context;

        // Register LocalService internally
        LocalServices.addService(IBaikalInternal.class, getLocalService());

        mBaikalAppProfileInternal = LocalServices.getService(IBaikalAppProfileInternal.class);
        mBaikalAppProfileInternal.setBaikalService(this);

        try {
            mBaikalAppProfileService = (BaikalAppProfileService) mBaikalAppProfileInternal.getBaikalAppProfileServiceInstance();

            mWorker = new ServiceThread(TAG, android.os.Process.THREAD_PRIORITY_DEFAULT, false);
            mWorker.start();
            mHandler = new Handler(mWorker.getLooper());

            mBaikalSettings = BaikalSettings.getInstance(this, mBaikalAppProfileService, mWorker.getLooper(), mContext);
            mBaikalChargingController = BaikalChargingController.getInstance(this, mBaikalAppProfileService, mWorker.getLooper(), mContext);
            mBaikalDebugger = BaikalDebugManager.getInstance(mHandler, mContext);
            mActions = new BaikalActions(mContext, mWorker.getLooper());

            mAttestation = new BaikalAttestationService(mContext);
            mBaikalAppManager = new BaikalAppManager(this, mContext);
            mBaikalPowerManager = new BaikalPowerManager(this, context, mHandler, mWorker.getLooper());

            mVolumeDB = new BaikalAppVolumeDB(context);

            isReady = true;
        } catch(Exception e) {
            Slog.e(TAG, "ctor exception", e);
        }
    }

    public void updateTopApp(String packageName, int uid) {
        if( !isReady ) return;
        mBaikalAppManager.onTopAppChanged(packageName, uid);
        mBaikalPowerManager.onTopAppChanged(packageName, uid);
    }

    public void updateCharging(boolean charging) {
        if( !isReady ) return;
        mBaikalAppManager.setCharging(charging);
        mBaikalPowerManager.setCharging(charging);
        mBaikalChargingController.setCharging(charging);
    }

    public void updateWakefulness(int wakefulness) {
        if( !isReady ) return;
        mBaikalAppManager.setWakefulness(wakefulness);
        mBaikalPowerManager.setWakefulness(wakefulness);
        mBaikalChargingController.setWakefulness(wakefulness);
    }


    public void updateCarMode(boolean mode) {
        if( !isReady ) return;
        mBaikalAppManager.setCarMode(mode);
        mBaikalPowerManager.setCarMode(mode);
    }

    public void updatePhoneCallState(boolean state) {
        if( !isReady ) return;
        mBaikalAppManager.setPhoneCallState(state);
        mBaikalPowerManager.setPhoneCallState(state);
    }

    public void updateScreenMode(boolean mode) {
        if( !isReady ) return;
        mBaikalAppManager.setScreenMode(mode);
        mBaikalPowerManager.setScreenMode(mode);
        mBaikalChargingController.setScreenMode(mode);
    }

    public void updateDeviceIdleMode(boolean mode) {
        if( !isReady ) return;
        mBaikalAppManager.setDeviceIdleMode(mode);
        mBaikalPowerManager.setDeviceIdleMode(mode);
    }


    public void updateSettings() {
        if( !isReady ) return;
        mBaikalAppManager.updateSettings();
        mBaikalPowerManager.updateSettings();
    }

    public BaikalPowerManager getBaikalPowerManager() {
        return mBaikalPowerManager;
    }

    public BaikalAppManager getBaikalAppManager() {
        return mBaikalAppManager;
    }

    /**
     * Get the LocalService implementation.
     */
    private IBaikalInternal getLocalService() {
        return new IBaikalInternal() {

            /**
             *
             */
            @Override
            public int getBaikalPackageOption(String packageName, int uid, int opCode,int def) {
                return getBaikalPackageOptionInternal(packageName, uid, opCode, def);
            }

            /**
             *
             */
            @Override
            public String getBaikalPackageOptionString(String packageName, int uid, int opCode, String def) {
                return getBaikalPackageOptionStringInternal(packageName, uid, opCode, def);
            }

            /**
             *
             */
            @Override
            public int getBaikalOption(int opCode,int def, int callingUid, String callingPackage) {
                return getBaikalOptionInternal(opCode, def, callingUid, callingPackage);
            }

            /**
             *
             */
            @Override
            public int getBaikalOptionWithParams(int opCode,int def, int callingUid, String callingPackage, Bundle params) {
                return getBaikalOptionWithParamsInternal(opCode, def, callingUid, callingPackage, params);
            }

            /**
             *
             */
            @Override
            public String getBaikalOptionString(int opCode,String def, int callingUid, String callingPackage) {
                return getBaikalOptionStringInternal(opCode, def, callingUid, callingPackage);
            }

            /**
             *
             */
            @Override
            public String getBaikalOptionStringWithParams(int opCode,String def, int callingUid, String callingPackage, Bundle params) {
                return getBaikalOptionStringWithParamsInternal(opCode, def, callingUid, callingPackage, params);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getBaikalAppProfile(int uid) {
                return getBaikalAppProfileInternal(uid);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getBaikalAppProfileNotNull(int uid) {
                return getBaikalAppProfileNotNullInternal(uid);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getBaikalAppProfileByPackageName(String packageName) {
                return getBaikalAppProfileByPackageNameInternal(packageName);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getUserProfile(int uid) {
                return getUserProfileInternal(uid);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getUserProfile(String packageName) {
                return getUserProfileInternal(packageName);
            }

            @Override
            public int getBaikalSettingInt(int realm, String name, int def) {
                return getBaikalSettingIntInternal(realm,name,def);
            }

            @Override
            public String getBaikalSettingString(int realm, String name, String def) {
                return getBaikalSettingStringInternal(realm,name,def);
            }

            @Override
            public void setBaikalActivityManagerService(BaikalActivityManagerService manager) {
                mBaikalActivityManagerService = manager;
            }

            @Override
            public void setBaikalPackageManagerService(BaikalPackageManagerService manager) {
                mBaikalPackageManagerService = manager;
            }

            @Override
            public void setBaikalPowerManagerService(BaikalPowerManagerService manager) {
                mBaikalPowerManagerService = manager;
            }

            @Override
            public void setBaikalLocationManager(BaikalLocationManager manager) {
                mBaikalLocationManager = manager;
            }

            @Override
            public void handleProfileUpdated(BaikalAppProfile profile) {
                handleProfileUpdatedInternal(profile);
            }
            
            @Override
            public void onSystemReady() {
                onSystemReadyInternal();
            }

            @Override
            public void setDeviceIdleWhitelist(int[] sysAppids, int[] userAppids, int[] exceptIdleAppids) {
                setDeviceIdleWhitelistInternal(sysAppids,userAppids,exceptIdleAppids);
            }

            @Override
            public boolean isApplicationBackgroundRestricted(String packageName, int uid) {
                return isApplicationBackgroundRestrictedInternal(packageName,uid);
            }

            @Override
            public boolean isAutoAppRestrictionActive() {
                return isAutoAppRestrictionActiveInternal();
            }

            @Override
            public boolean isImportantApp(String packageName) {
                return isImportantAppInternal(packageName);
            }

            @Override
            public void incBackgroundStartCount(int uid) {
                // Ensure only system or authorized calls can increment this
                // final int callingUid = Binder.getCallingUid(); 
                synchronized (mBackgroundStartStats) {
                    int currentCount = mBackgroundStartStats.get(uid, 0);
                    int newCount = currentCount + 1;
                    mBackgroundStartStats.put(uid, newCount);
                }
            }

            @Override
            public int getBackgroundStartCount(int uid) {
                synchronized (mBackgroundStartStats) {
                    return mBackgroundStartStats.get(uid, 0);
                }
            }

        };
    }

    /**
     * Binder implementation.
     */
    private final IBaikalService.Stub mBinder = new IBaikalService.Stub() {
            /**
             *
             */
            @Override
            public int getBaikalPackageOption(String packageName, int uid, int opCode,int def) {
                return getBaikalPackageOptionInternal(packageName, uid, opCode, def);
            }

            /**
             *
             */
            @Override
            public String getBaikalPackageOptionString(String packageName, int uid, int opCode, String def) {
                return getBaikalPackageOptionStringInternal(packageName, uid, opCode, def);
            }

            /**
             *
             */
            @Override
            public int getBaikalOption(int opCode,int def, int callingUid, String callingPackage) {
                return getBaikalOptionInternal(opCode, def, callingUid, callingPackage);
            }

            /**
             *
             */
            @Override
            public int getBaikalOptionWithParams(int opCode,int def, int callingUid, String callingPackage, Bundle params) {
                return getBaikalOptionWithParamsInternal(opCode, def, callingUid, callingPackage, params);
            }

            /**
             *
             */
            @Override
            public String getBaikalOptionString(int opCode,String def, int callingUid, String callingPackage) {
                return getBaikalOptionStringInternal(opCode, def, callingUid, callingPackage);
            }

            /**
             *
             */
            @Override
            public String getBaikalOptionStringWithParams(int opCode,String def, int callingUid, String callingPackage, Bundle params) {
                return getBaikalOptionStringWithParamsInternal(opCode, def, callingUid, callingPackage, params);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getBaikalAppProfile(int uid) {
                return getBaikalAppProfileInternal(uid);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getBaikalAppProfileNotNull(int uid) {
                return getBaikalAppProfileNotNullInternal(uid);
            }

            /**
             *
             */
            @Override
            public BaikalAppProfile getBaikalAppProfileByPackageName(String packageName) {
                return getBaikalAppProfileByPackageNameInternal(packageName);
            }

            @Override
            public int getBaikalSettingInt(int realm, String name,int def) {
                return getBaikalSettingIntInternal(realm,name,def);
            }

            @Override
            public String getBaikalSettingString(int realm, String name, String def) {
                return getBaikalSettingStringInternal(realm,name,def);
            }


            @Override
            public String getBaikalPowerModeName(int mode) {
                return getBaikalPowerModeNameInternal(mode);
            }

            @Override
            public int getBackgroundStartCount(int uid) {
                synchronized (mBackgroundStartStats) {
                    return mBackgroundStartStats.get(uid, 0);
                }
            }

            @Override
            public String getTopAppPackageName() {
                return getTopAppPackageNameInternal();
            }

            @Override
            public int getTopAppUid() {
                return getTopAppUidInternal();
            }


            @Override
            protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
                pw.println("BaikalService dump:");
                if( mBaikalPowerManager != null ) mBaikalPowerManager.dump(pw);
            }

            @Override
            public void setAppVolume(String packageName, float volume) {
                if( mVolumeDB == null ) return;
                mVolumeDB.setAppVolume(packageName, volume);
            }

            @Override
            public float getAppVolume(String packageName) {
                if( mVolumeDB == null ) return -10.0F;
                Float value = mVolumeDB.getAppVolume(packageName);
                if( value == null ) return -10.0F;
                return value.floatValue();
            }
    };

    @Override
    public void onStart() {
        Slog.e(TAG, "Starting binderized baikal_service");
        publishBinderService("baikal_service", mBinder, true);
    }

    // --- Internal thread-safe methods ---

    public void onSystemReadyInternal() {
        if( !isReady ) return;
        try {
            mBaikalDebugger.onSystemReady();
            mAttestation.onSystemReady();
            mBaikalSettings.onSystemReady();
            mBaikalChargingController.onSystemReady();
            mBaikalPowerManager.onSystemReady();
            mBaikalAppManager.onSystemReady();
            mBaikalSpecialDevices = BaikalSpecialDevices.getInstance(mHandler, mContext);
            mVolumeDB.onSystemReady();
        } catch(Exception e) {
            Slog.e(TAG, "onSystemReadyInternal exception", e);
        }

    }

    public void handleProfileUpdatedInternal(BaikalAppProfile profile) {
    }

    public void setDeviceIdleWhitelistInternal(int[] sysAppids, int[] userAppids, int[] exceptIdleAppids) {
        synchronized (mLock) {
            mDeviceIdleSystemExceptIdleWhitelist = exceptIdleAppids;
            mDeviceIdleSystemWhitelist = sysAppids;
            mDeviceIdleUserWhitelist = userAppids;
            mBaikalAppProfileService.updateDeviceIdleWhitelistInternal();
        }
        
    }

    public String getTopAppPackageNameInternal() {
        return mBaikalAppManager == null ? "unknown" : mBaikalAppManager.getTopAppPackageName();
    }

    public int getTopAppUidInternal() {
        return mBaikalAppManager == null ? -1 : mBaikalAppManager.getTopAppUid();
    }


    public String getBaikalPowerModeNameInternal(int mode) {
        return mBaikalPowerManager.getBaikalPowerModeNameInternal(mode);
    }

    public int getBaikalPackageOptionInternal(String packageName, int uid, int opCode,int def) {
        try {
            return mBaikalSettings.getPackageOption(packageName,uid,opCode,def);
        }
        catch(Exception e) {
            Slog.e(TAG, "Can't get PackageOption " + opCode + " for " + packageName + "/" + uid, e);
        }
        return def;
    }

    public String getBaikalPackageOptionStringInternal(String packageName, int uid, int opCode, String def) {
        try {
            return mBaikalSettings.getPackageOptionString(packageName,uid,opCode,def);
        }
        catch(Exception e) {
            Slog.e(TAG, "Can't get PackageOptionString  " + opCode + " for " + packageName + "/" + uid, e);
        }
        return def;
    }

    public int getBaikalOptionInternal(int opCode,int def, int callingUid, String callingPackage) {
        return getBaikalOptionWithParamsInternal(opCode,def,callingUid,callingPackage,null);
    }

    public int getBaikalOptionWithParamsInternal(int opCode,int def, int callingUid, String callingPackage, Bundle params) {
        try {
            return mBaikalSettings.getBaikalOptionWithParams(opCode,def,callingUid,callingPackage,params);
        } catch(Exception e) {
            Slog.e(TAG, "Can't get getBaikalOptionWithParams " + opCode + " for " + callingPackage + "/" + callingUid, e);
        }
        return def;
    }

    public String getBaikalOptionStringInternal(int opCode,String def, int callingUid, String callingPackage) {
        return getBaikalOptionStringWithParamsInternal(opCode,def,callingUid,callingPackage,null);
    }

    public String getBaikalOptionStringWithParamsInternal(int opCode,String def, int callingUid, String callingPackage, Bundle params) {
        try {
            return mBaikalSettings.getBaikalOptionStringWithParams(opCode,def,callingUid,callingPackage,params);
        } catch(Exception e) {
            Slog.e(TAG, "Can't get getBaikalOptionStringWithParams " + opCode + " for " + callingPackage + "/" + callingUid, e);
        }
        return def;
    }

    public BaikalAppProfile getBaikalAppProfileInternal(int uid) {
        return mBaikalAppProfileService.getProfileInternal(uid);
    }

    public BaikalAppProfile getBaikalAppProfileNotNullInternal(int uid) {
        return mBaikalAppProfileService.getProfileNotNullInternal(uid);
    }

    public BaikalAppProfile getBaikalAppProfileByPackageNameInternal(String packageName) {
        return mBaikalAppProfileService.getProfileByPackageNameInternal(packageName);
    }

    public BaikalAppProfile getUserProfileInternal(int uid) {
        return mBaikalAppProfileService.getUserProfileInternal(uid);
    }

    public BaikalAppProfile getUserProfileInternal(String packageName) {
        return mBaikalAppProfileService.getUserProfileInternal(packageName);
    }

    public int getBaikalSettingIntInternal(int realm,String name,int def) {
        try  {
            ContentResolver resolver = mContext.getContentResolver();
            switch(realm) {
                default:
                case 0:
                    return Settings.Global.getInt(resolver,name,def);
                case 1:
                    return Settings.Secure.getInt(resolver,name,def);
                case 2:
                    return Settings.System.getInt(resolver,name,def);

            }
        } catch(Exception e) {
            Slog.e(TAG, "Can't read int " + name + " from " + realm, e);
        }
        return def;
    }

    public String getBaikalSettingStringInternal(int realm,String name, String def) {
        String result = def;
        try  {
            ContentResolver resolver = mContext.getContentResolver();
            switch(realm) {
                default:
                case 0:
                    result = Settings.Global.getString(resolver,name);
                    break;
                case 1:
                    result = Settings.Secure.getString(resolver,name);
                    break;
                case 2:
                    result = Settings.System.getString(resolver,name);
                    break;

            }
            if( result != null ) return result;
        } catch(Exception e) {
            Slog.e(TAG, "Can't read string " + name + " from " + realm, e);
        }
        return def;
    }

    public boolean isApplicationBackgroundRestrictedInternal(String packageName, int uid) {
        boolean result = false;
        if( mBaikalAppManager != null ) result = mBaikalAppManager.isApplicationBackgroundRestricted(uid,packageName);
        else Slog.e(TAG, "isApplicationBackgroundRestrictedInternal: not ready yet");
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "isApplicationBackgroundRestrictedInternal: " + packageName + "/" + uid + ":" + result);
        return result;
    }

    public boolean isAutoAppRestrictionActiveInternal() {
        if( mBaikalPowerManager != null ) return mBaikalPowerManager.isAutoAppRestrictionActiveInternal();
        Slog.e(TAG, "isAutoAppRestrictionActiveInternal: not ready yet");
        return false;
    }

    public int onSetPowerBoostInternal(BaikalPowerManagerService service, int boost, int durationMs) {
        if( mBaikalPowerManager != null ) return mBaikalPowerManager.onSetPowerBoostInternal(service, boost, durationMs);
        Slog.e(TAG, "onSetPowerBoostInternal: not ready yet");
        return -1;
    }

    public int onSetPowerModeInternal(BaikalPowerManagerService service, int mode, boolean enabled, int uid) {
        if( mBaikalPowerManager != null ) return mBaikalPowerManager.onSetPowerModeInternal(service, mode, enabled, uid);
        Slog.e(TAG, "onSetPowerModeInternal: not ready yet");
        return -1;
    }

    public boolean isBackgroundRestrictedNoCheck(final int uid, final String packageName) {
        /*boolean result = false;
        if( mBaikalPowerManager != null ) result = mBaikalAppManager.isApplicationBackgroundRestricted(uid,packageName);
        else Slog.e(TAG, "isBackgroundRestrictedNoCheck: not ready yet");
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "isBackgroundRestrictedNoCheck: " + packageName + "/" + uid + ":" + result);
        return result;*/
        return isApplicationBackgroundRestrictedInternal(packageName,uid);
    }
    
    public boolean isUidActive(int uid) {
        if( mBaikalActivityManagerService != null ) return mBaikalActivityManagerService.isUidActive(uid);
        return false;
    }

    public void handleBackgroundRestrictionChanged(int uid, String packageName, boolean restricted) {
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "handleBackgroundRestrictionChanged: " + packageName + "/" + uid + ":" + restricted);
        mBaikalAppProfileService.handleBackgroundRestrictionChanged(uid,packageName,restricted);
    }


    public boolean isRunAnyInBackgroundDisabled(int uid, String packageName) {
        boolean result = mBaikalActivityManagerService.isRunAnyInBackgroundDisabled(uid,packageName);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "isBackgroundRestrictedNoCheck: " + packageName + "/" + uid + ":" + result);
        return result;
    }

    public boolean isImportantAppInternal(String packageName) {
        boolean result = mBaikalAppProfileService.isImportantApp(packageName);
        if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Slog.d(TAG, "isImportantApp: " + packageName + ":" + result);
        return result;
    }

    public void registerBaikalBrightnessStrategy(BaikalBrightnessStrategy strategy) {
        mBaikalAppManager.registerBaikalBrightnessStrategy(strategy);
    }

    public BaikalAppProfile getTopAppProfileInternal() {
        return mBaikalAppManager.getTopAppProfileInternal();
    }

    public int getBackgroundStartCount(int uid) {
        synchronized (mBackgroundStartStats) {
            return mBackgroundStartStats.get(uid, 0);
        }
    }

    public void incBackgroundStartCount(int uid) {
        synchronized (mBackgroundStartStats) {
            int currentCount = mBackgroundStartStats.get(uid, 0);
            int newCount = currentCount + 1;
            mBackgroundStartStats.put(uid, newCount);
        }
    }

    public void addIsolatedUid(int isolatedUid, int uid) {
        if( mBaikalAppProfileService != null ) mBaikalAppProfileService.addIsolatedUid(isolatedUid,uid);
    }

    public void removeIsolatedUid(int isolatedUid, int uid) {
        if( mBaikalAppProfileService != null ) mBaikalAppProfileService.removeIsolatedUid(isolatedUid,uid);
    }
}
