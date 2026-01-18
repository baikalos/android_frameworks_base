package com.android.server.baikalos;

import static android.app.usage.UsageStatsManager.REASON_MAIN_DEFAULT;
import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_SYSTEM;
import static android.app.usage.UsageStatsManager.REASON_MAIN_FORCED_BY_USER;
import static android.app.usage.UsageStatsManager.REASON_MAIN_MASK;
import static android.app.usage.UsageStatsManager.REASON_MAIN_USAGE;
import static android.app.usage.UsageStatsManager.REASON_SUB_DEFAULT_UNDEFINED;
import static android.app.usage.UsageStatsManager.REASON_SUB_FORCED_SYSTEM_FLAG_UNDEFINED;
import static android.app.usage.UsageStatsManager.REASON_SUB_FORCED_USER_FLAG_INTERACTION;
import static android.app.usage.UsageStatsManager.REASON_SUB_MASK;
import static android.app.usage.UsageStatsManager.REASON_SUB_USAGE_SYSTEM_UPDATE;
import static android.app.usage.UsageStatsManager.REASON_SUB_USAGE_USER_INTERACTION;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_EXEMPTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_NEVER;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;

import android.baikalos.BaikalAppProfile;
import android.baikalos.IBaikalAppProfileService;
import android.content.Context;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Slog;
import com.android.internal.baikalos.BaikalConstants;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.DumpUtils;

import com.android.server.baikalos.IBaikalAppProfileInternal;
import com.android.server.SystemService;
import com.android.server.LocalServices;
import com.android.server.usage.AppStandbyInternal;

import java.util.concurrent.CompletableFuture;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * System service for per-app profiles.
 * Stores profiles in memory and synchronously on disk.
 */
public class BaikalAppProfileHelper {

    private static final String TAG = "BaikalAppProfileHelper";

    private final Context mContext;
    private final BaikalAppProfileService mService;
    private final File mProfilesDir;
    private final Map<Integer, BaikalAppProfile> mProfiles = new HashMap<>();
    private int mSystemuiUid = 0;


    public BaikalAppProfileHelper(Context context, BaikalAppProfileService service) {
        mContext = context;
        mService = service;
        mProfilesDir = new File(Environment.getDataSystemDirectory(), "baikal_profiles");
        if (!mProfilesDir.exists()) mProfilesDir.mkdirs();
    }

    public void onSystemReadyInternal() {
        mSystemuiUid = UserHandle.getAppId(BaikalConstants.getUidByPackage(mContext, "com.android.systemui"));
    }

    public void start() {
        // Get the local instance of the standby controller
        loadProfilesFromDisk();
    }

    public void reload() {
        loadProfilesFromDisk();
    }

    public void deleteAllProfiles() {
        mProfiles.clear();
        deleteAllProfileFiles();
        //updateFilterFsUids();
    }

    public BaikalAppProfile getProfile(int uid) {
        if( uid <= 10000 ) return null;
        return mProfiles.get(uid);
    }

    public void updateProfile(BaikalAppProfile profile) {
        if( profile.isDebug() ) {
            Slog.d(TAG, "updateProfile:" + BaikalAppProfile.dumpProfile(profile), new Throwable());
        } else {
            Slog.d(TAG, "updateProfile:" + BaikalAppProfile.dumpProfile(profile));
        }
        if (profile.mUid <= 10000 ) {
            Slog.d(TAG, "updateProfile: UID=" + profile.mUid + ", ignore");
            return;
        }
        if (profile.isDefault()) {
            mProfiles.remove(profile.mUid);
        } else {
            mProfiles.put(profile.mUid, profile);
        }
        // updateAppStandbyBucket(profile);
    }

    public void saveProfile(BaikalAppProfile profile) {
        Slog.d(TAG, "saveProfile:" + BaikalAppProfile.dumpProfile(profile));
        if (profile.mUid <= 10000 ) {
            Slog.d(TAG, "updateProfile: UID=" + profile.mUid + ", ignore");
            return;
        }

        if( profile.mUid == mSystemuiUid ) {
            sanitizeImportantProfile(profile);
        }

        if (profile.isDefault()) {
            mProfiles.remove(profile.mUid);
            File file = new File(mProfilesDir, String.valueOf(profile.mUid));
            if (file.exists()) file.delete();
        } else {
            profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_PROFILE;
            mProfiles.put(profile.mUid, profile);
            writeProfileToDisk(profile);
        }
        // updateAppStandbyBucket(profile);
    }

    public void removeProfile(int uid) {
        mProfiles.remove(uid);
        File file = new File(mProfilesDir, String.valueOf(uid));
        if (file.exists()) file.delete();
    }

    public void commit() {
        Slog.d(TAG, "commit");
        File[] files = mProfilesDir.listFiles();
        Set<Integer> uidsToKeep = mProfiles.keySet();
        if (files != null) {
            for (File file : files) {
                try {
                    int uid = Integer.parseInt(file.getName());
                    if (!uidsToKeep.contains(uid)) file.delete();
                } catch (NumberFormatException ignored) {}
            }
        }

        for (BaikalAppProfile profile : mProfiles.values()) {
            writeProfileToDisk(profile);
        }
    }

    void sanitizeImportantProfile(BaikalAppProfile profile) {
        profile.mAppInfo &= ~BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED;
        profile.mBackgroundMode &= ~BaikalAppProfile.BAIKAL_BACKGROUND_BOOT_DISABLED;
    }

    // ===================== Dump =====================

    public void dump(PrintWriter pw) {
        pw.println("BaikalAppProfileService saved profiles:");
        for (BaikalAppProfile profile : mProfiles.values()) {
            pw.println(BaikalAppProfile.dumpProfile(profile));
        }
    }

    private void loadProfilesFromDisk() {
        mProfiles.clear();
        File[] files = mProfilesDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            try {
                BufferedReader reader = new BufferedReader(new java.io.FileReader(file));
                String line = reader.readLine();
                reader.close();
                BaikalAppProfile profile = BaikalAppProfile.deserializeProfile(line);
                if (profile != null) {
                    if (profile.isDefault() || profile.mUid <= 10000) {
                        mProfiles.remove(profile.mUid);
                        if (file.exists()) file.delete();
                    } else {
                        profile.mAppInfo |= BaikalAppProfile.BAIKAL_APPINFO_IS_USER_PROFILE;
                        mProfiles.put(profile.mUid, profile);
                    }
                }
                updateAppStandbyBucket(profile);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to read profile " + file.getName(), e);
            }
        }
    }

    private void writeProfileToDisk(BaikalAppProfile profile) {
        File file = new File(mProfilesDir, String.valueOf(profile.mUid));
        try (FileOutputStream fos = new FileOutputStream(file)) {
            String serialized = profile.serialize();
            if (serialized != null) {
                fos.write(serialized.getBytes());
                fos.flush();
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to save profile " + profile.mUid, e);
        }
    }

    private void deleteAllProfileFiles() {
        File[] files = mProfilesDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            f.delete();
        }
    }

    private void enforceSystemUid() {
        int callingUid = Binder.getCallingUid();
        if (callingUid != android.os.Process.SYSTEM_UID) {
            throw new SecurityException("Only system UID can write profiles");
        }
    }

    private void triggerAppStandbyUpdateAsync(AppStandbyInternal appStandby, BaikalAppProfile profile, int bucket, int reason) {
        if (profile == null /*|| profile.mPackageName == null*/ ) return;

        String [] pkgs = mService.getPackagesFromUidInternal(profile.mUid);
        for(String pkg : pkgs) {

            final String pkgName = pkg;
            final int userId = UserHandle.getUserId(profile.mUid);
            final long now = SystemClock.elapsedRealtime();
            final int targetBucket = bucket;
            final int targetReason = reason;

            CompletableFuture.runAsync(() -> {
                try {
                    appStandby.setAppStandbyBucketInternal(
                        pkgName, 
                        userId, 
                        now, 
                        targetBucket, 
                        targetReason
                    );
                } catch (Exception e) {
                    Slog.e(TAG, "Error updating standby bucket for " + pkgName, e);
                }
            });
        }
    }

    public void updateAppStandbyBucket(BaikalAppProfile profile) {
        if( UserHandle.getAppId(profile.mUid) < 10000 ) return;
        AppStandbyInternal appStandby = LocalServices.getService(AppStandbyInternal.class);
        if (appStandby != null) {
            int bucket = STANDBY_BUCKET_ACTIVE;
            int reason = REASON_MAIN_DEFAULT;
            if( profile.mBackgroundLevel <= 1 ) {
                bucket = STANDBY_BUCKET_ACTIVE;
                reason = REASON_MAIN_DEFAULT;
            } else if( profile.mBackgroundLevel > 45 ) {
                bucket = 50;
                reason = REASON_MAIN_FORCED_BY_SYSTEM;
            } else {
                bucket = profile.mBackgroundLevel;
                reason = REASON_MAIN_FORCED_BY_SYSTEM;
            }

            /*
            appStandby.setAppStandbyBucketInternal(
                profile.mPackageName, 
                UserHandle.getUserId(profile.mUid), 
                SystemClock.elapsedRealtime(),
                bucket, 
                reason);*/

            triggerAppStandbyUpdateAsync(appStandby, profile, bucket, reason); 
        }
    }
}

