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


    public BaikalAppProfileHelper(Context context, BaikalAppProfileService service) {
        mContext = context;
        mService = service;
        mProfilesDir = new File(Environment.getDataSystemDirectory(), "baikal_profiles");
        if (!mProfilesDir.exists()) mProfilesDir.mkdirs();
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
        return mProfiles.get(uid);
    }

    public void updateProfile(BaikalAppProfile profile) {
        if( profile.isDebug() ) {
            Slog.d(TAG, "updateProfile:" + dumpProfile(profile), new Throwable());
        } else {
            Slog.d(TAG, "updateProfile:" + dumpProfile(profile));
        }
        if (profile.isDefault()) {
            mProfiles.remove(profile.mUid);
        } else {
            mProfiles.put(profile.mUid, profile);
        }
        // updateAppStandbyBucket(profile);
    }

    public void saveProfile(BaikalAppProfile profile) {
        Slog.d(TAG, "saveProfile:" + dumpProfile(profile));
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


    // ===================== Dump =====================

    public void dump(PrintWriter pw) {
        pw.println("BaikalAppProfileService saved profiles:");
        for (BaikalAppProfile profile : mProfiles.values()) {
            pw.println(dumpProfile(profile));
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
                    if (profile.isDefault()) {
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
                bucket = 45;
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

    public static String dumpProfile(BaikalAppProfile profile) {
        String result = /*profile.mPackageName + "/" +*/ String.valueOf(profile.mUid);
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_DEFAULT_PROFILE) != 0 ) result += ",DEF";
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_PROFILE) != 0 ) result += ",USER";
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM) != 0 ) result += ",SYS";
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_SYSTEM_WITELISTED) != 0 ) result += ",SWL";
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_WITELISTED) != 0 ) result += ",UWL";
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0 ) result += ",RES";
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_IMPORTANT) != 0 ) result += ",IMP";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_PINNED) != 0 ) result += ",PINNED";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_DONOTCLOSE) != 0 ) result += ",DNC";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_DISABLED) != 0 ) result += ",DIS";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_BOOT_DISABLED) != 0 ) result += ",BOOT_DIS";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_DONOTWAKE) != 0 ) result += ",DNW";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_ALLOW_WHILE_IDLE) != 0 ) result += ",ALLOW_IDLE";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_NET_WHILE_IDLE) != 0 ) result += ",NET_IDLE";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_HEAVY_CPU) != 0 ) result += ",HEAVY_CPU";
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_HEAVY_MEM) != 0 ) result += ",HEAVY_MEM";
        if( (profile.mSpoof & BaikalAppProfile.BAIKAL_SPOOF_INTEGRITY_SW_ATTEST) != 0 ) result += ",SWATT";
        if( (profile.mSpoof & BaikalAppProfile.BAIKAL_SPOOF_INTEGRITY_HIDE_DEBUG) != 0 ) result += ",HIDE_DEBUG";
        if( (profile.mSpoof & BaikalAppProfile.BAIKAL_SPOOF_INTEGRITY_FILTER_FS) != 0 ) result += ",F_FS";
        if( (profile.mSpoof & BaikalAppProfile.BAIKAL_SPOOF_INTEGRITY_FILTER_FS_ADD) != 0 ) result += ",F_FS_ADD";
        if( (profile.mOverride & BaikalAppProfile.BAIKAL_OVERRIDE_FORCED_SCREENSHOT) != 0 ) result += ",F_SS";
        if( (profile.mOverride & BaikalAppProfile.BAIKAL_OVERRIDE_FONTS) != 0 ) result += ",FONTS";
        if( (profile.mOverride & BaikalAppProfile.BAIKAL_OVERRIDE_SIGNATURE) != 0 ) result += ",SIGN";
        if( (profile.mOverride & BaikalAppProfile.BAIKAL_OVERRIDE_FULL_SCREEN) != 0 ) result += ",FS";
        if( (profile.mAudio & BaikalAppProfile.BAIKAL_AUDIO_BAFR) != 0 ) result += ",BAFR";
        if( (profile.mAudio & BaikalAppProfile.BAIKAL_AUDIO_BAFS) != 0 ) result += ",BAFS";
        if( (profile.mAudio & BaikalAppProfile.BAIKAL_AUDIO_FORCE_SPEAKER) != 0 ) result += ",SPK";

        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEBUG) != 0 ) result += ",DEBUG";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_VERBOSE) != 0 ) result += ",VERBOSE";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_OLD_LINKS) != 0 ) result += ",O_LINKS";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_PRIVELEGED_PHONE) != 0 ) result += ",PRIV";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_BLOCK_OVERLAYS) != 0 ) result += ",BOVL";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_BYPASS_CHARGING) != 0 ) result += ",BPC";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_PUSH_PROVIDER) != 0 ) result += ",PUSH";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEFAULT_DIALER) != 0 ) result += ",DD";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEFAULT_SMS) != 0 ) result += ",DS";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_DEFAULT_CALLERID) != 0 ) result += ",DC";
        if( (profile.mAppOpts & BaikalAppProfile.BAIKAL_APP_HIDE_IDLE) != 0 ) result += ",HIDE_IDLE";
        if( profile.mLocationLevel != 0 ) result += ",loc=" + String.valueOf(profile.mLocationLevel);
        if( profile.mBackgroundLevel != 0 ) result += ",bl=" + String.valueOf(profile.mBackgroundLevel);
        if( profile.mBrightness != 0 ) result += ",brt=" + String.valueOf(profile.mBrightness);
        if( profile.mKeepOn != 0 ) result += ",ko=" + String.valueOf(profile.mKeepOn);
        if( profile.mRotation != 0 ) result += ",rot=" + String.valueOf(profile.mRotation);
        if( profile.mDarkMode != 0 ) result += ",drk=" + String.valueOf(profile.mDarkMode);
        if( profile.mCameraMode != 0 ) result += ",cam=" + String.valueOf(profile.mCameraMode);
        if( profile.mMicrophoneMode != 0 ) result += ",mic=" + String.valueOf(profile.mMicrophoneMode);
        if( profile.mFileAccess != 0 ) result += ",fa=" + String.valueOf(profile.mFileAccess);
        if( profile.mBlockHMS != 0 ) result += ",hms=" + String.valueOf(profile.mBlockHMS);
        if( profile.mBlockGMS != 0 ) result += ",gms=" + String.valueOf(profile.mBlockGMS);
        if( profile.mBlock3P != 0 ) result += ",3p=" + String.valueOf(profile.mBlock3P);
        if( profile.mBlockContacts != 0 ) result += ",bcnt=" + String.valueOf(profile.mBlockContacts);
        if( profile.mBlockCallLog != 0 ) result += ",bcll=" + String.valueOf(profile.mBlockCallLog);
        if( profile.mBlockCalendar != 0 ) result += ",bcnd=" + String.valueOf(profile.mBlockCalendar);
        if( profile.mBlockMedia != 0 ) result += ",bmed=" + String.valueOf(profile.mBlockMedia);
        if( profile.mBlockSMS != 0 ) result += ",bsms=" + String.valueOf(profile.mBlockSMS);
        if( profile.mBlockNotification != 0 ) result += ",bnot=" + String.valueOf(profile.mBlockNotification);
        if( profile.mSpoofDevice != 0 ) result += ",spf=" + String.valueOf(profile.mSpoofDevice);
        if( profile.mInstaller != 0 ) result += ",inst=" + String.valueOf(profile.mInstaller);
        if( profile.mPerfProfile != 0 ) result += ",perf=" + String.valueOf(profile.mPerfProfile);
        if( profile.mThermalProfile != 0 ) result += ",therm=" + String.valueOf(profile.mThermalProfile);
        if( profile.mMinFrameRate != 0 ) result += ",minfs=" + String.valueOf(profile.mMinFrameRate);
        if( profile.mMaxFrameRate != 0 ) result += ",maxfs=" + String.valueOf(profile.mMaxFrameRate);
        if( profile.mPerformanceLevel != 0 ) result += ",plev=" + String.valueOf(profile.mPerformanceLevel);
        if( profile.mBoostControl != 0 ) result += ",bst=" + String.valueOf(profile.mBoostControl);

        if( !"".equals(profile.mSpoofSimCountry) ) result += ",simc=" + profile.mSpoofSimCountry;
        if( !"".equals(profile.mSpoofSimMnc) ) result += ",simm=" + profile.mSpoofSimMnc;
        if( !"".equals(profile.mSpoofSimOpName) ) result += ",simo=" + profile.mSpoofSimOpName;
        if( !"".equals(profile.mSpoofSimLN) ) result += ",siml=" + profile.mSpoofSimLN;
        long wasActive = profile.getLastActive() > 0 ? SystemClock.elapsedRealtime() - profile.getLastActive() : 0;
        result += ",act=" + wasActive;
        return result;
    }

}

