package com.android.server.baikalos;

import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.baikalos.BaikalAppProfile;
import android.baikalos.IBaikalAppProfileService;
import android.content.Context;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;


import com.android.internal.annotations.GuardedBy;
import com.android.internal.baikalos.*;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.DumpUtils;

import com.android.server.baikalos.IBaikalAppProfileInternal;
import com.android.server.DeviceIdleInternal;
import com.android.server.SystemService;
import com.android.server.LocalServices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * System service for per-app profiles.
 * Stores profiles in memory and synchronously on disk.
 */
public class BaikalAppProfileService extends SystemService {

    private static final String TAG = "BaikalAppProfileService";

    private final Context mContext;
    private final Map<Integer, BaikalAppProfile> mProfiles = new HashMap<>();
    private final Map<String, BaikalAppProfile> mProfilesByPackage = new HashMap<>();
    private final BaikalAppProfileHelper mProfileHelper;
    private BaikalService mBaikalService;
    private DeviceIdleInternal mDeviceIdleController;
    private BaikalDefaultAppManager mBaikalDefaultAppManager;
    
    private final IBaikalAppProfileInternal mLocalService = new IBaikalAppProfileInternal() {
        @Override
        public BaikalAppProfile getProfile(int uid) {
            return getProfileInternal(uid);
        }

        @Override
        public void updateProfile(BaikalAppProfile profile) {
            updateProfileInternal(profile);
        }

        @Override
        public void saveProfile(BaikalAppProfile profile) {
            saveProfileInternal(profile);
        }

        @Override
        public void commit() {
            commitInternal();
        }

        @Override
        public void setBaikalService(BaikalService service) {
            mBaikalService = service;
        }

        @Override
        public Object getBaikalAppProfileServiceInstance() {   
            return BaikalAppProfileService.this;
        }

        @Override
        public int overrideStandbyBucket(String packageName, int bucket, int mode) {
            return overrideStandbyBucketInternal(packageName, bucket, mode);
        }

        @Override
        public void onSystemReady() {
            onSystemReadyInternal();
        }
    };

    private final IBaikalAppProfileService.Stub mBinder = new IBaikalAppProfileService.Stub() {
        @Override
        public BaikalAppProfile getProfile(int uid) throws RemoteException {
            return getProfileInternal(uid);
        }

        @Override
        public BaikalAppProfile getProfileNotNull(int uid) throws RemoteException {
            return getProfileNotNullInternal(uid);
        }

        @Override
        public void updateProfile(BaikalAppProfile profile) throws RemoteException {
            enforceSystemUid();
            updateProfileInternal(profile);
        }

        @Override
        public void saveProfile(BaikalAppProfile profile) throws RemoteException {
            enforceSystemUid();
            saveProfileInternal(profile);
        }

        @Override
        public void commit() throws RemoteException {
            enforceSystemUid();
            commitInternal();
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!DumpUtils.checkDumpAndUsageStatsPermission(mContext, TAG, pw)) {
                return;
            }

            if (args == null || args.length == 0) {
                dumpHelp(pw);
                return;
            }

            final String cmd = args[0];

            synchronized (mProfiles) {
            
                switch (cmd) {
                    case "list":
                        mProfileHelper.dump(pw);
                        return;

                    case "list-all":
                        dumpAll(pw);
                        return;

                    case "clear-all":
                        mProfileHelper.deleteAllProfiles();
                        pw.println("All profiles cleared.");
                        return;

                    case "clear":
                        if (args.length < 2) {
                            pw.println("Usage: clear <uid>");
                            return;
                        }
                        try {
                            int uid = Integer.parseInt(args[1]);
                            mProfileHelper.removeProfile(uid);
                            pw.println("Profile removed: uid=" + uid);
                        } catch (NumberFormatException e) {
                            pw.println("Invalid UID");
                        }
                        return;
        
                    case "add":
                        if (args.length < 4) {
                            pw.println("Usage: add <uid> <package> <hexAppOpts>");
                            return;
                        }
                        try {
                            int uid = Integer.parseInt(args[1]);
                            String pkg = args[2];
                            int appOpts = Integer.parseUnsignedInt(args[3], 16);
            
                            BaikalAppProfile p = new BaikalAppProfile(pkg,uid);
                            p.mPackageName = pkg;
                            p.mAppOpts = appOpts;
        
                            mProfileHelper.updateProfile(p);
                            mProfileHelper.saveProfile(p);
        
                            pw.println("Added profile:");
                            pw.println("  uid=" + uid);
                            pw.println("  package=" + pkg);
                            pw.println("  appOpts=0x" + Integer.toHexString(appOpts));
                        } catch (NumberFormatException e) {
                            pw.println("Invalid number");
                        }
                        return;
        
                    case "help":
                    default:
                        dumpHelp(pw);
                        return;
                }
            }
        }
    };

    public BaikalAppProfileService(Context context) {
        super(context);
        mContext = context;
        mProfileHelper = new BaikalAppProfileHelper(context,this);
        LocalServices.addService(IBaikalAppProfileInternal.class, mLocalService);
    }

    @Override
    public void onStart() {
        mProfileHelper.start();
        publishBinderService("baikal_app_profile", mBinder, true);
    }

    public void onSystemReadyInternal() {
        if( mDeviceIdleController == null ) {
            mDeviceIdleController = LocalServices.getService(DeviceIdleInternal.class);
        }
        if( mBaikalDefaultAppManager == null ) {
            mBaikalDefaultAppManager = getBaikalDefaultAppManager();
        }
    }

    public DeviceIdleInternal getDeviceIdleController() {
        if( mDeviceIdleController == null ) {
            mDeviceIdleController = LocalServices.getService(DeviceIdleInternal.class);
        }
        return mDeviceIdleController;
    }

    BaikalDefaultAppManager getBaikalDefaultAppManager() {
        if( mBaikalDefaultAppManager == null ) {
            try {
                mBaikalDefaultAppManager = new BaikalDefaultAppManager(mContext);
            } catch(Exception ex) {
            }
        }
        return mBaikalDefaultAppManager;
    }

    private void enforceSystemUid() {
        int callingUid = Binder.getCallingUid();
        if ( UserHandle.getAppId(callingUid) != android.os.Process.SYSTEM_UID) {
            throw new SecurityException("Only system UID can write profiles");
        }
    }

    // ===================== Internal Methods =====================
    BaikalAppProfile getProfileInternal(int uid) {
        try {
            BaikalAppProfile profile = null;
            if( mBaikalService == null ) {
                return null;
            }
            if( mBaikalService.mBaikalActivityManagerService == null ) {
                return null;
            }
            uid = mBaikalService.mBaikalActivityManagerService.amFindRealUid(uid);
            synchronized (mProfiles) {
                profile = getProfileLocked(uid);
            }
            if( profile != null ) return profile;

            synchronized (mProfiles) {
                profile = getUserProfileLocked(uid);
            }
            if( profile == null ) {
                profile = tryFindProfileInternal(uid);
            }
            if( profile != null ) {
                int appId = UserHandle.getAppId(uid);
                if( isSystemPackageInternal(profile.mPackageName) ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM;
                }
                if( Arrays.binarySearch(mBaikalService.mDeviceIdleSystemWhitelist, appId) >=0 ||
                    Arrays.binarySearch(mBaikalService.mDeviceIdleSystemExceptIdleWhitelist, appId) >=0 ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM_WITELISTED;
                } else if( Arrays.binarySearch(mBaikalService.mDeviceIdleUserWhitelist, appId) >= 0  ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_WITELISTED;
                }
                if( !UserHandle.isIsolated(uid) ) updateProfileInternal(profile);
                return profile;
            }
            Slog.w(TAG, "Can't find profile for uid: " + uid);
        } catch(Exception e) {
            Slog.w(TAG, "Can't get profile for uid: " + uid, e);
        }
        return null;
    }

    void updateDeviceIdleWhitelistInternal() {
        updateDeviceIdleWhitelistLocked();
    }

    BaikalAppProfile getProfileNotNullInternal(int uid) {
        BaikalAppProfile profile = getProfileInternal(uid);
        if( profile != null ) return profile;
        return getDefaultProfileInternal(uid);
    }


    BaikalAppProfile tryFindProfileInternal(int uid) {
        /*if (UserHandle.isIsolated(uid)) {
            ProcessRecord r = mBaikalService.getProcessRecord(pid);
        }*/
        BaikalAppProfile profile = null;
        //profile = tryFindRunningProcessInternal(uid);
        //if( profile != null ) return profile;
        profile = tryFindInstalledPackageInternal(uid);
        if( profile != null ) updateProfileFromSystemLocked(profile);
        return profile;
    }

    BaikalAppProfile tryFindInstalledPackageInternal(int uid) {
        return mBaikalService.mBaikalPackageManagerService.pmGetAppProfile(uid);
    }

    boolean isSystemPackageInternal(String packageName) {
        return mBaikalService.mBaikalPackageManagerService.isSystemPackage(packageName);
    }


    BaikalAppProfile getDefaultProfileInternal(int uid) {
        BaikalAppProfile profile = new BaikalAppProfile("app.unknown",uid);
        profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_DEFAULT_PROFILE;
        return profile;
    }

    void updateProfileInternal(BaikalAppProfile profile) {
        boolean changed = false;
        synchronized (mProfiles) {
            changed = updateProfileLocked(profile);
        }
        if( changed ) {
            mBaikalService.handleProfileUpdatedInternal(profile);
        }
    }

    void saveProfileInternal(BaikalAppProfile profile) {
        synchronized (mProfiles) {
            saveProfileLocked(profile);
        }
        mBaikalService.handleProfileUpdatedInternal(profile);
    }

    void removeProfileInternal(int uid) {
        synchronized (mProfiles) {
            removeProfileLocked(uid);
        }
        //mBaikalService.handleProfileUpdated(profile);
    }

    void commitInternal() {
        synchronized (mProfiles) {
            commitLocked();
        }
    }

    void updateDeviceIdleWhitelistLocked() {
        synchronized (mProfiles) {
            for (BaikalAppProfile profile : mProfiles.values()) {
                int appId = UserHandle.getAppId(profile.mUid);
                if( Arrays.binarySearch(mBaikalService.mDeviceIdleSystemWhitelist, appId) >=0 ||
                    Arrays.binarySearch(mBaikalService.mDeviceIdleSystemExceptIdleWhitelist, appId) >=0 ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM_WITELISTED;
                    profile.mAppInfo &= ~BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
                } else if( Arrays.binarySearch(mBaikalService.mDeviceIdleUserWhitelist, appId) >= 0 ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_WITELISTED;
                    profile.mAppInfo &= ~BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
                }
            }
        }
    }


    private BaikalAppProfile getProfileLocked(int uid) {
        BaikalAppProfile profile = mProfiles.get(uid);
        return profile; //mProfileHelper.getProfile(uid);
    }

    private BaikalAppProfile getUserProfileLocked(int uid) {
        BaikalAppProfile profile = mProfileHelper.getProfile(uid);
        if( profile != null ) {
            profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_PROFILE;
            updateProfileFromSystemLocked(profile);
            mProfiles.put(uid,profile);
            mProfilesByPackage.put(profile.mPackageName,profile);
        }
        return profile;
    }


    private boolean updateProfileLocked(BaikalAppProfile profile) {
        boolean changed = false;
        BaikalAppProfile old = mProfiles.get(profile.mUid);
        if( old == null ) {
            updateProfileFromSystemLocked(profile);
            mProfiles.put(profile.mUid,profile);
            mProfilesByPackage.put(profile.mPackageName,profile);
            changed = true;
        } else {
            if( old != profile ) {  
                updateProfileFromSystemLocked(profile);
                old.update(profile);
                changed = true;
            }
        }
        if( UserHandle.getAppId(profile.mUid) >= 10000 ) {
            mProfileHelper.updateProfile(profile);
        }
        return changed;
    }

    private void saveProfileLocked(BaikalAppProfile profile) {
        if(UserHandle.getAppId(profile.mUid) >= 10000){
            updateProfileFromSystemLocked(profile);
            mProfiles.put(profile.mUid,profile);
            mProfilesByPackage.put(profile.mPackageName,profile);
            mProfileHelper.saveProfile(profile);
        }
    }

    private void removeProfileLocked(int uid) {
        BaikalAppProfile old = mProfiles.get(uid);
        mProfiles.remove(uid);
        if( old != null ) mProfilesByPackage.remove(old.mPackageName);
        mProfileHelper.removeProfile(uid);
    }

    private void commitLocked() {
        mProfileHelper.commit();
    }

    public int overrideStandbyBucketInternal(String packageName, int bucket, int mode) {

        BaikalAppProfile profile = null;

        synchronized (mProfiles) {
            profile = mProfilesByPackage.get(packageName);
        }

        if( profile == null || profile.mBackgroundLevel == 1 /*|| profile.mBackgroundMode != 0*/ ) return bucket;

        if( profile.isImportant() ) return 5;
        if( profile.isWhitelisted() ) return 5;
        if( profile.isRestricted() ) return 45;

        if( profile.mBackgroundLevel == 0 ) {
            if( mBaikalService.isAutoAppRestrictionActiveInternal() ) return 45;
            return bucket;
        }

        switch(mode) {
            default:
            case 0:
                return profile.mBackgroundLevel;
            case 1:
                if( profile.mBackgroundLevel < bucket ) return profile.mBackgroundLevel;
                break;
            case 2:
                if( profile.mBackgroundLevel > bucket ) return profile.mBackgroundLevel;
                break;
        }
        return bucket;
    }


    public void handleBackgroundRestrictionChanged(int uid, String pkgName, boolean restricted) {
        BaikalAppProfile profile = getProfileNotNullInternal(uid);
        if( restricted ) {
            if( UserHandle.getAppId(profile.mUid) >= 10000 ) {
                if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) == 0 ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
                    Slog.w(TAG, "User restricted app: " + BaikalAppProfileHelper.dumpProfile(profile));
                }
            }
        } else {
            if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0 ) {
                profile.mAppInfo &= ~BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
                Slog.w(TAG, "User not restricted app: " + BaikalAppProfileHelper.dumpProfile(profile));
            }
        }
        updateProfileInternal(profile);
    }

    public boolean isRunAnyInBackgroundDisabled(int uid, String packageName) {
        return mBaikalService.isRunAnyInBackgroundDisabled(uid,packageName);
    }

    public boolean isImportantApp(String packageName) {
        if( mBaikalDefaultAppManager == null ) {
            mBaikalDefaultAppManager = getBaikalDefaultAppManager();
        }
        if( mBaikalDefaultAppManager == null ) return false;
        return mBaikalDefaultAppManager.isDefaultPackage(packageName);
    }

    void updateCacheForUser(int userId) {
        synchronized (mProfiles) {
            updateCacheForUserLocked();
        }
    }
    void updateCacheForUserLocked() {
        // TODO: Rescan
    }

    void updateProfileFromSystemLocked(BaikalAppProfile profile) {
        updateProfileFromRunAnyInBackground(profile);
        updateProfileFromDefaultApps(profile);
    }

    void updateProfileFromDefaultApps(BaikalAppProfile profile) {
        if( mBaikalDefaultAppManager == null ) return;
        if( mBaikalDefaultAppManager.isDefaultPackage(profile.mPackageName) ) {
            if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_IMPORTANT) == 0 ) {
                profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_IMPORTANT;
                Slog.w(TAG, "User default app: " + BaikalAppProfileHelper.dumpProfile(profile));
            } 
        } else {
            if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_IMPORTANT) != 0 ) {
                profile.mAppInfo &= ~BaikalAppProfile.BAIKAL_APPINFO_IS_IMPORTANT;
                Slog.w(TAG, "User not default app: " + BaikalAppProfileHelper.dumpProfile(profile));
            }
        }
    }

    void updateProfileFromRunAnyInBackground(BaikalAppProfile profile) {
        if(isRunAnyInBackgroundDisabled(profile.mUid,profile.mPackageName) ) {
            if( UserHandle.getAppId(profile.mUid) >= 10000 ) {
                if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) == 0 ) {
                    profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
                    Slog.w(TAG, "User restricted app: " + BaikalAppProfileHelper.dumpProfile(profile));
                }
            } 
        } else {
            if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0 ) {
                profile.mAppInfo &= ~BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
                Slog.w(TAG, "User not restricted app: " + BaikalAppProfileHelper.dumpProfile(profile));
            }
        }
    }

    private void dumpAll(PrintWriter pw) {
        synchronized (mProfiles) {
            dumpLocked(pw);
        }
    }
    
    // ===================== Dump =====================
    private void dumpLocked(PrintWriter pw) {
        pw.println("BaikalAppProfileService dump:");
        for (BaikalAppProfile profile : mProfiles.values()) {
            //pw.println("UID: " + profile.mUid + " Profile: " + profile.toString() + " mAppInfo=0x" + Integer.toHexString(profile.mAppInfo));
            //pw.println("UID: " + profile.mUid + " pkg: " + profile.mPackageName + " mAppInfo=0x" + Integer.toHexString(profile.mAppInfo));
            pw.println(BaikalAppProfileHelper.dumpProfile(profile));
        }
    }

    private void dumpHelp(PrintWriter pw) {
        pw.println("BaikalAppProfileService commands:");
        pw.println("  list                      - Show all profiles");
        pw.println("  clear-all                 - Delete all profiles");
        pw.println("  clear <uid>               - Delete specific profile");
        pw.println("  add <uid> <pkg> <hex>     - Create/update a profile");
        pw.println("  help                      - Show this help");
    }

    class BaikalDefaultAppManager {

        private static final String TAG = "BaikalDefaultAppManager";

        // List of roles we want to track
        private static final String[] TRACKED_ROLES = {
            RoleManager.ROLE_DIALER,
            RoleManager.ROLE_SMS,
            RoleManager.ROLE_BROWSER,
            RoleManager.ROLE_HOME
        };

        private static BaikalDefaultAppManager sInstance;

        private final RoleManager mRoleManager;
        private final Context mContext;

        @GuardedBy("mLock")
        private final Set<String> mDefaultPackages = new ArraySet<>();
        private final Object mLock = new Object();

        public BaikalDefaultAppManager(Context context) {
            sInstance = this;
            mContext = context;
            mRoleManager = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
        
            // Initial build of the cache
            // Assuming system user or current user at boot
            updateCacheForUser(mContext.getUserId());

            // Register listener for changes
            // Use main executor or a specific handler thread from system_server
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(
                mContext.getMainExecutor(),
                mRoleListener,
                UserHandle.ALL
            );
        }

        /**
         * Listener that triggers when a user changes a default application.
         */
        private final OnRoleHoldersChangedListener mRoleListener = (roleName, user) -> {
            Slog.i(TAG, "Role changed: " + roleName + " for user " + user.getIdentifier());
            // We update the entire cache for simplicity, 
            // or you could optimize to update only the changed role.
            updateCacheForUser(user.getIdentifier());
        };

        /**
         * Rebuilds the set of default packages from scratch.
         */
        private void updateCacheForUser(int userId) {
            synchronized (mLock) {
                mDefaultPackages.clear();
                UserHandle userHandle = UserHandle.of(userId);
            
                for (String role : TRACKED_ROLES) {
                    List<String> holders = mRoleManager.getRoleHoldersAsUser(role, userHandle);
                    if (!holders.isEmpty()) {
                        mDefaultPackages.addAll(holders);
                    }
                }
                Slog.d(TAG, "Cache updated. Current default packages: " + mDefaultPackages);
            }
            BaikalAppProfileService.this.updateCacheForUser(userId);
        }

        /**
         * The fastest way to check if a package is a default app.
         * Call this from any part of system_server.
         */
        public boolean isDefaultPackage(String packageName) {
            if (packageName == null) return false;
        
            synchronized (mLock) {
                return mDefaultPackages.contains(packageName);
            }
        }
    }
}

