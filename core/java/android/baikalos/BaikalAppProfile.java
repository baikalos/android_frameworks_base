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

package android.baikalos;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.content.Context;
import android.content.ContentResolver;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;

import android.util.Slog;
import android.util.KeyValueListParser;


public final class BaikalAppProfile implements Parcelable {

    public static final int BAIKAL_BACKGROUND_PINNED = 1 << 0;
    public static final int BAIKAL_BACKGROUND_DONOTCLOSE = 1 << 1;
    // public static final int BAIKAL_BACKGROUND_SMART = 1 << 2;
    public static final int BAIKAL_BACKGROUND_DISABLED = 1 << 3;
    public static final int BAIKAL_BACKGROUND_BOOT_DISABLED = 1 << 4;
    public static final int BAIKAL_BACKGROUND_DONOTWAKE = 1 << 5;
    public static final int BAIKAL_BACKGROUND_ALLOW_WHILE_IDLE = 1 << 6;
    public static final int BAIKAL_BACKGROUND_NET_WHILE_IDLE = 1 << 7;
    public static final int BAIKAL_BACKGROUND_HEAVY_CPU = 1 << 8;
    public static final int BAIKAL_BACKGROUND_HEAVY_MEM = 1 << 9;

    public static final int BAIKAL_SPOOF_INTEGRITY_SW_ATTEST = 1 << 0;
    public static final int BAIKAL_SPOOF_INTEGRITY_HIDE_DEBUG = 1 << 1;
    public static final int BAIKAL_SPOOF_INTEGRITY_FILTER_FS = 1 << 8;
    public static final int BAIKAL_SPOOF_INTEGRITY_FILTER_FS_ADD = 1 << 9;

    public static final int BAIKAL_SPOOF_HIDE_VPN = 1 << 16;

    // public static final int BAIKAL_OVERRIDE_ = 1 << 0;
    public static final int BAIKAL_OVERRIDE_FORCED_SCREENSHOT = 1 << 0;
    public static final int BAIKAL_OVERRIDE_FONTS = 1 << 1;
    public static final int BAIKAL_OVERRIDE_SIGNATURE = 1 << 2;
    public static final int BAIKAL_OVERRIDE_FULL_SCREEN = 1 << 3;

    // public static final int BAIKAL_AUDIO_ = 1 << 0;
    public static final int BAIKAL_AUDIO_BAFR = 1 << 1;
    public static final int BAIKAL_AUDIO_BAFS = 1 << 2;
    public static final int BAIKAL_AUDIO_FORCE_SPEAKER = 1 << 3;

    //public static final int BAIKAL_APP_ = 1 << 0;
    public static final int BAIKAL_APP_DEBUG = 1 << 0;
    public static final int BAIKAL_APP_VERBOSE = 1 << 1;
    public static final int BAIKAL_APP_OLD_LINKS = 1 << 2;
    public static final int BAIKAL_APP_PRIVELEGED_PHONE = 1 << 3;
    public static final int BAIKAL_APP_BLOCK_OVERLAYS = 1 << 4;
    public static final int BAIKAL_APP_BYPASS_CHARGING = 1 << 5;
    public static final int BAIKAL_APP_PUSH_PROVIDER = 1 << 6;
    public static final int BAIKAL_APP_DEFAULT_DIALER = 1 << 7;
    public static final int BAIKAL_APP_DEFAULT_SMS = 1 << 8;
    public static final int BAIKAL_APP_DEFAULT_CALLERID = 1 << 9;
    public static final int BAIKAL_APP_HIDE_IDLE = 1 << 10;
    public static final int BAIKAL_APP_HIDE_BATTERY_OPT = 1 << 11;
    public static final int BAIKAL_APP_HIDE_LOCATION = 1 << 12;
    public static final int BAIKAL_APP_HIDE_PHONE = 1 << 13;
    public static final int BAIKAL_APP_HIDE_SMS = 1 << 14;

    public static final int BAIKAL_APP_FAKE_SU = 1 << 15;
    public static final int BAIKAL_APP_ALLOW_HIDDEN_API = 1 << 16;
    public static final int BAIKAL_APP_ALLOW_ALL_PERMISSIONS = 1 << 17;


    public static final int BAIKAL_APPINFO_IS_SYSTEM = 1 << 0;
    public static final int BAIKAL_APPINFO_IS_IMPORTANT = 1 << 2;
    public static final int BAIKAL_APPINFO_IS_HOME = 1 << 3;

    public static final int BAIKAL_APPINFO_IS_SYSTEM_WITELISTED = 1 << 5;
    public static final int BAIKAL_APPINFO_IS_USER_WITELISTED = 1 << 6;
    public static final int BAIKAL_APPINFO_IS_USER_RESTRICTED = 1 << 7;

    public static final int BAIKAL_APPINFO_IS_GMS = 1 << 8;
    public static final int BAIKAL_APPINFO_IS_GMS_PERS = 1 << 9;
    public static final int BAIKAL_APPINFO_IS_HMS = 1 << 10;

    public static final int BAIKAL_APPINFO_IS_USER_PROFILE = 1 << 29;
    public static final int BAIKAL_APPINFO_IS_DEFAULT_PROFILE = 1 << 30;

    public static final int BAIKAL_OPCODE_LOCATION = 0;

    public static final int BAIKAL_OPCODE_BACKGROUND = 10;
    public static final int BAIKAL_OPCODE_SPOOF = 11;
    public static final int BAIKAL_OPCODE_OVERRIDE = 12;

    public static final int BAIKAL_OPCODE_BLOCK_GMS = 20;
    public static final int BAIKAL_OPCODE_BLOCK_HMS = 21;
    public static final int BAIKAL_OPCODE_BLOCK_3P = 22;
    public static final int BAIKAL_OPCODE_BLOCK_CONTACTS = 23;
    public static final int BAIKAL_OPCODE_BLOCK_CALENDAR = 24;
    public static final int BAIKAL_OPCODE_BLOCK_SMS = 25;
    public static final int BAIKAL_OPCODE_BLOCK_NOTIFICATION = 26;
    public static final int BAIKAL_OPCODE_BLOCK_MEDIA = 27;
    public static final int BAIKAL_OPCODE_BLOCK_CALLLOG = 28;

    public static final int BAIKAL_OPCODE_DEFAULT_DIALER = 40;
    public static final int BAIKAL_OPCODE_DEFAULT_SMS = 41;
    public static final int BAIKAL_OPCODE_DEFAULT_CALLERID = 42;


    public static final int BAIKAL_OPCODE_SPOOF_SIM_COUNTRY = 101;
    public static final int BAIKAL_OPCODE_SPOOF_SIM_MNC = 102;
    public static final int BAIKAL_OPCODE_SPOOF_SIM_OP = 103;
    public static final int BAIKAL_OPCODE_SPOOF_SIM_LN = 104;

    private static final String TAG = "Baikal.BaikalAppProfile";

    @SuppressLint({"MutableBareField","InternalField","AllUpper"})
    public static boolean DEBUG = false;

    @SuppressLint({"MutableBareField","InternalField","AllUpper"})
    public static boolean VERBOSE = false;

    @SuppressLint({"MutableBareField","InternalField","AllUpper"})
    public static boolean TRACE = false;

    //@SuppressLint({"MutableBareField","InternalField"})
    // public @Nullable String mPackageName;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mUid;

    // App
    @SuppressLint({"MutableBareField","InternalField"})
    public int mAppOpts;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mLocationLevel;

    // background related
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBackgroundMode;

    // background related
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBackgroundLevel;

    // presentation
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBrightness;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mKeepOn;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mRotation;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mDarkMode;

    // Privacy
    @SuppressLint({"MutableBareField","InternalField"})
    public int mCameraMode;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mMicrophoneMode;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mFileAccess;

    // Block
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockHMS;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockGMS;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlock3P;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockContacts;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockCallLog;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockCalendar;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockMedia;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockSMS;
    @SuppressLint({"MutableBareField","InternalField"})
    public int mBlockNotification;

    // Spoofer
    @SuppressLint({"MutableBareField","InternalField"})
    public int mSpoof;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mSpoofDevice;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mInstaller;

    @SuppressLint({"MutableBareField","InternalField"})
    public @Nullable String mSpoofSimCountry;

    @SuppressLint({"MutableBareField","InternalField"})
    public @Nullable String mSpoofSimMnc;

    @SuppressLint({"MutableBareField","InternalField"})
    public @Nullable String mSpoofSimOpName;

    @SuppressLint({"MutableBareField","InternalField"})
    public @Nullable String mSpoofSimLN;

    // Override
    @SuppressLint({"MutableBareField","InternalField"})
    public int mOverride;


    // Sound
    @SuppressLint({"MutableBareField","InternalField"})
    public int mAudio;

    // Performance and profiles
    @SuppressLint({"MutableBareField","InternalField"})
    public int mPerfProfile;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mThermalProfile;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mMaxFrameRate;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mMinFrameRate;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mPerformanceLevel;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mBoostControl;

    // Other

    @SuppressLint({"MutableBareField","InternalField"})
    public int mAppInfo;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIsValidated;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIsInitialized;

    @SuppressLint({"MutableBareField","InternalField"})
    public long lastActiveTime;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mDefiningUid;

    public boolean isDebug() {
        return mUid == -1 || (mAppOpts & BAIKAL_APP_DEBUG) != 0 || DEBUG;
    }

    public boolean isVerbose() {
        return mUid == -1 || (mAppOpts & BAIKAL_APP_VERBOSE) != 0 || VERBOSE;
    }

    public boolean isTrace() {
        return TRACE;
    }

    public void active(long now) {
        lastActiveTime = now;
    }

    public long getLastActive() {
        return lastActiveTime;
    }

    public boolean isPinned() {
        return (mBackgroundMode & BAIKAL_BACKGROUND_PINNED) != 0;
    }

    public boolean isProtected() {
        return (mBackgroundMode & (BAIKAL_BACKGROUND_PINNED | BAIKAL_BACKGROUND_DONOTCLOSE)) != 0;
    }

    public boolean isSystemImportant() {
        return (mAppInfo & (BAIKAL_APPINFO_IS_SYSTEM | BAIKAL_APPINFO_IS_IMPORTANT)) != 0;
    }

    public boolean isSystem() {
        return (mAppInfo & BAIKAL_APPINFO_IS_SYSTEM) != 0;
    }

    public boolean isImportant() {
        return (mAppInfo & BAIKAL_APPINFO_IS_IMPORTANT) != 0;
    }

    public boolean isRestricted() {
        return (mAppInfo & BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0;
    }

    public boolean isWhitelisted() {
        return (mAppInfo & (BAIKAL_APPINFO_IS_SYSTEM_WITELISTED | BAIKAL_APPINFO_IS_USER_WITELISTED)) != 0;
    }

    private BaikalAppProfile() {
        // mPackageName = "";
        mUid = -1;
        clear();
    }

    public BaikalAppProfile(int uid) {
        //if( packageName == null ) mPackageName = "";
        //else mPackageName = packageName;
        mUid = uid;
        clear();
    }

    public BaikalAppProfile(@Nullable BaikalAppProfile profile) {
        update(profile);
    }

    //@Nullable
    //public BaikalAppProfile setPackageName(@Nullable String packageName) {
    //    mPackageName = packageName;
    //    return this;
    //}

    @Nullable
    public BaikalAppProfile setUid(int uid) {
        mUid = uid;
        return this;
    }

    private BaikalAppProfile(@NonNull final Parcel in) {
        //mPackageName = in.readString();
        mUid = in.readInt();

        mAppOpts = in.readInt();
        mLocationLevel = in.readInt();
        mBackgroundMode = in.readInt();
        mBackgroundLevel = in.readInt();
        mBrightness = in.readInt();
        mKeepOn = in.readInt();
        mRotation = in.readInt();
        mDarkMode = in.readInt();
        mCameraMode = in.readInt();
        mMicrophoneMode = in.readInt();
        mFileAccess = in.readInt();
        mBlockHMS = in.readInt();
        mBlockGMS = in.readInt();
        mBlock3P = in.readInt();
        mBlockContacts = in.readInt();
        mBlockCallLog = in.readInt();
        mBlockCalendar = in.readInt();
        mBlockMedia = in.readInt();
        mBlockSMS = in.readInt();
        mBlockNotification = in.readInt();
        mSpoof = in.readInt();
        mSpoofDevice = in.readInt();
        mInstaller = in.readInt();
        mOverride = in.readInt();
        mAudio = in.readInt();
        mPerfProfile = in.readInt();
        mThermalProfile = in.readInt();
        mMaxFrameRate = in.readInt();
        mMinFrameRate = in.readInt();
        mPerformanceLevel = in.readInt();
        mBoostControl = in.readInt();
        mAppInfo = in.readInt();
        mSpoofSimCountry = in.readString();
        mSpoofSimMnc = in.readString();
        mSpoofSimOpName = in.readString();
        mSpoofSimLN = in.readString();
    }


    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // dest.writeString(mName);
        //dest.writeString(mPackageName);
        dest.writeInt(mUid);

        dest.writeInt(mAppOpts);
        dest.writeInt(mLocationLevel);
        dest.writeInt(mBackgroundMode);
        dest.writeInt(mBackgroundLevel);
        dest.writeInt(mBrightness);
        dest.writeInt(mKeepOn);
        dest.writeInt(mRotation);
        dest.writeInt(mDarkMode);
        dest.writeInt(mCameraMode);
        dest.writeInt(mMicrophoneMode);
        dest.writeInt(mFileAccess);
        dest.writeInt(mBlockHMS);
        dest.writeInt(mBlockGMS);
        dest.writeInt(mBlock3P);
        dest.writeInt(mBlockContacts);
        dest.writeInt(mBlockCallLog);
        dest.writeInt(mBlockCalendar);
        dest.writeInt(mBlockMedia);
        dest.writeInt(mBlockSMS);
        dest.writeInt(mBlockNotification);
        dest.writeInt(mSpoof);
        dest.writeInt(mSpoofDevice);
        dest.writeInt(mInstaller);
        dest.writeInt(mOverride);
        dest.writeInt(mAudio);
        dest.writeInt(mPerfProfile);
        dest.writeInt(mThermalProfile);
        dest.writeInt(mMaxFrameRate);
        dest.writeInt(mMinFrameRate);
        dest.writeInt(mPerformanceLevel);
        dest.writeInt(mBoostControl);
        dest.writeInt(mAppInfo);
        dest.writeString(mSpoofSimCountry);
        dest.writeString(mSpoofSimMnc);
        dest.writeString(mSpoofSimOpName);
        dest.writeString(mSpoofSimLN);

    }
    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Parcelable.Creator<BaikalAppProfile> CREATOR =
            new Parcelable.Creator<BaikalAppProfile>() {
        @Override
        public BaikalAppProfile[] newArray(int size) {
            return new BaikalAppProfile[size];
        }

        @Override
        public BaikalAppProfile createFromParcel(@NonNull Parcel in) {
            return new BaikalAppProfile(in);
        }
    };


    public void clear() {
        mAppOpts = 0;
        mLocationLevel = 0;
        mBackgroundMode = 0;
        mBackgroundLevel = 0;
        mBrightness = 0;
        mKeepOn = 0;
        mRotation = 0;
        mDarkMode = 0;
        mCameraMode = 0;
        mMicrophoneMode = 0;
        mFileAccess = 0;
        mBlockHMS = 0;
        mBlockGMS = 0;
        mBlock3P = 0;
        mBlockContacts = 0;
        mBlockCallLog = 0;
        mBlockCalendar = 0;
        mBlockMedia = 0;
        mBlockSMS = 0;
        mBlockNotification = 0;
        mSpoof = 0;
        mSpoofDevice = 0;
        mInstaller = 0;
        mOverride = 0;
        mAudio = 0;
        mPerfProfile = 0;
        mThermalProfile = 0;
        mMaxFrameRate = 0;
        mMinFrameRate = 0;
        mPerformanceLevel = 0;
        mBoostControl = 0;
        mAppInfo = 0;

        mSpoofSimCountry = "";
        mSpoofSimMnc = "";
        mSpoofSimOpName = "";
        mSpoofSimLN = "";

        mIsValidated = false;
        mIsInitialized = false;
    }


    public boolean isDefault() {

        if( mAppOpts == 0 &&
            mLocationLevel == 0 &&
            mBackgroundMode == 0 &&
            mBackgroundLevel == 0 &&
            mBrightness == 0 &&
            mKeepOn == 0 &&
            mRotation == 0 &&
            mDarkMode == 0 &&
            mCameraMode == 0 &&
            mMicrophoneMode == 0 &&
            mFileAccess == 0 &&
            mBlockHMS == 0 &&
            mBlockGMS == 0 &&
            mBlock3P == 0 &&
            mBlockContacts == 0 &&
            mBlockCallLog == 0 &&
            mBlockCalendar == 0 &&
            mBlockMedia == 0 &&
            mBlockSMS == 0 &&
            mBlockNotification == 0 &&
            mSpoof == 0 &&
            mSpoofDevice == 0 &&
            mInstaller == 0 &&
            mOverride == 0 &&
            mAudio == 0 &&
            mPerfProfile == 0 &&
            mThermalProfile == 0 &&
            mMaxFrameRate == 0 &&
            mMinFrameRate == 0 &&
            mPerformanceLevel == 0 &&
            mBoostControl == 0 &&
            "".equals(mSpoofSimCountry) &&
            "".equals(mSpoofSimMnc) &&
            "".equals(mSpoofSimOpName) &&
            "".equals(mSpoofSimLN) ) return true;

        return false;
    }

    public @Nullable BaikalAppProfile update(@Nullable BaikalAppProfile profile) {
        if( profile == null ) {
            Slog.e(TAG, "Invalid profile assignment", new Throwable());
            return null;
        }

        //this.mPackageName = profile.mPackageName;
        this.mUid = profile.mUid;

        this.mAppOpts = this.mAppOpts;
        this.mLocationLevel = profile.mLocationLevel;
        this.mBackgroundMode = profile.mBackgroundMode;
        this.mBackgroundLevel = profile.mBackgroundLevel;
        this.mBrightness = profile.mBrightness;
        this.mKeepOn = profile.mKeepOn;
        this.mRotation = profile.mRotation;
        this.mDarkMode = profile.mDarkMode;
        this.mCameraMode = profile.mCameraMode;
        this.mMicrophoneMode = profile.mMicrophoneMode;
        this.mFileAccess = profile.mFileAccess;

        this.mBlockHMS = profile.mBlockHMS;
        this.mBlockGMS = profile.mBlockGMS;
        this.mBlock3P = profile.mBlock3P;
        this.mBlockContacts = profile.mBlockContacts;
        this.mBlockCallLog = profile.mBlockCallLog;
        this.mBlockCalendar = profile.mBlockCalendar;
        this.mBlockMedia = profile.mBlockMedia;
        this.mBlockSMS = profile.mBlockSMS;
        this.mBlockNotification = profile.mBlockNotification;
        this.mSpoof = profile.mSpoof;
        this.mSpoofDevice = profile.mSpoofDevice;
        this.mInstaller = profile.mInstaller;
        this.mOverride = profile.mOverride;
        this.mAudio = profile.mAudio;
        this.mPerfProfile = profile.mPerfProfile;
        this.mThermalProfile = profile.mThermalProfile;
        this.mMaxFrameRate = profile.mMaxFrameRate;
        this.mMinFrameRate = profile.mMinFrameRate;
        this.mPerformanceLevel = profile.mPerformanceLevel;
        this.mBoostControl = profile.mBoostControl;
        this.mAppInfo = profile.mAppInfo;
        this.lastActiveTime = profile.lastActiveTime;

        this.mSpoofSimCountry = profile.mSpoofSimCountry;
        this.mSpoofSimMnc = profile.mSpoofSimMnc;
        this.mSpoofSimOpName = profile.mSpoofSimOpName;
        this.mSpoofSimLN = profile.mSpoofSimLN;

        return this;
    }

    public boolean equals(@Nullable BaikalAppProfile profile) {
        if( profile == null ) {
            return false;
        }

        //if( !this.mPackageName.equals(profile.mPackageName)) return false;
        if( this.mUid != profile.mUid ) return false;

        if( this.mAppOpts != this.mAppOpts ) return false;
        if( this.mLocationLevel != profile.mLocationLevel ) return false;
        if( this.mBackgroundMode != profile.mBackgroundMode ) return false;
        if( this.mBackgroundLevel != profile.mBackgroundLevel ) return false;
        if( this.mBrightness != profile.mBrightness ) return false;
        if( this.mKeepOn != profile.mKeepOn ) return false;
        if( this.mRotation != profile.mRotation ) return false;
        if( this.mDarkMode != profile.mDarkMode ) return false;
        if( this.mCameraMode != profile.mCameraMode ) return false;
        if( this.mMicrophoneMode != profile.mMicrophoneMode ) return false;
        if( this.mFileAccess != profile.mFileAccess ) return false;
        if( this.mBlockHMS != profile.mBlockHMS ) return false;
        if( this.mBlockGMS != profile.mBlockGMS ) return false;
        if( this.mBlock3P != profile.mBlock3P ) return false;
        if( this.mBlockContacts != profile.mBlockContacts ) return false;
        if( this.mBlockCallLog != profile.mBlockCallLog ) return false;
        if( this.mBlockCalendar != profile.mBlockCalendar ) return false;
        if( this.mBlockMedia != profile.mBlockMedia ) return false;
        if( this.mBlockSMS != profile.mBlockSMS ) return false;
        if( this.mBlockNotification != profile.mBlockNotification ) return false;
        if( this.mSpoof != profile.mSpoof ) return false;
        if( this.mSpoofDevice != profile.mSpoofDevice ) return false;
        if( this.mInstaller != profile.mInstaller ) return false;
        if( this.mOverride != profile.mOverride ) return false;
        if( this.mAudio != profile.mAudio ) return false;
        if( this.mPerfProfile != profile.mPerfProfile ) return false;
        if( this.mThermalProfile != profile.mThermalProfile ) return false;
        if( this.mMaxFrameRate != profile.mMaxFrameRate ) return false;
        if( this.mMinFrameRate != profile.mMinFrameRate ) return false;
        if( this.mPerformanceLevel != profile.mPerformanceLevel ) return false;
        if( this.mBoostControl != profile.mBoostControl ) return false;
        if( this.mAppInfo != profile.mAppInfo ) return false;

        if( this.mSpoofSimCountry.equals(profile.mSpoofSimCountry) ) return false;
        if( this.mSpoofSimMnc.equals(profile.mSpoofSimMnc) ) return false;
        if( this.mSpoofSimOpName.equals(profile.mSpoofSimOpName) ) return false;
        if( this.mSpoofSimLN.equals(profile.mSpoofSimLN) ) return false;

        return true;
    }


    public @Nullable String serialize() {
        //if( mPackageName == null || "".equals(mPackageName) ) return null;
        //String result =  "pn=" + mPackageName;
        //result += "," + "uid=" + mUid;

        String result = "uid=" + mUid;

        if( mAppOpts != 0 ) result += "," + "ao=" + mAppOpts;
        if( mOverride != 0 ) result += "," + "ovr=" + mOverride;
        if( mAudio != 0 ) result += "," + "aud=" + mAudio;
        if( mLocationLevel != 0 ) result +=  "," + "llv=" + mLocationLevel;
        if( mBackgroundMode != 0 ) result += "," + "bk=" + mBackgroundMode;
        if( mBackgroundLevel != 0 ) result += "," + "bl=" + mBackgroundLevel;
        if( mBrightness != 0 ) result += "," + "br=" + mBrightness;
        if( mKeepOn != 0 ) result +=  "," + "koi=" + mKeepOn;
        if( mRotation != 0 ) result +=  "," + "ro=" + mRotation;
        if( mDarkMode != 0 ) result += "," + "dkm=" + mDarkMode;
        if( mCameraMode != 0 ) result +=  "," + "cm=" + mCameraMode;
        if( mMicrophoneMode != 0 ) result +=  "," + "mic=" + mMicrophoneMode;
        if( mFileAccess != 0 ) result += "," + "fa=" + mFileAccess;
        if( mBlockHMS != 0 ) result +=  "," + "blhms=" + mBlockHMS;
        if( mBlockGMS != 0 ) result +=  "," + "blgms=" + mBlockGMS;
        if( mBlock3P != 0 ) result +=  "," + "bl3p=" + mBlock3P;
        if( mBlockContacts != 0 ) result +=  "," + "blcnt=" + mBlockContacts;
        if( mBlockCallLog != 0 ) result +=  "," + "blcll=" + mBlockCallLog;
        if( mBlockCalendar != 0 ) result +=  "," + "blcln=" + mBlockCalendar;
        if( mBlockMedia != 0 ) result +=  "," + "blmed=" + mBlockMedia;
        if( mBlockSMS != 0 ) result +=  "," + "blsms=" + mBlockSMS;
        if( mBlockNotification != 0 ) result +=  "," + "blnot=" + mBlockNotification;
        if( mSpoof != 0 ) result +=  "," + "spf=" + mSpoof;
        if( mSpoofDevice != 0 ) result +=  "," + "sd=" + mSpoofDevice;
        if( mInstaller != 0 ) result +=  "," + "ins=" + mInstaller;
        if( mPerformanceLevel != 0 ) result +=  "," + "pl=" + mPerformanceLevel;
        if( mBoostControl != 0 ) result +=  "," + "bcl=" + mBoostControl;
        if( mPerfProfile != 0 ) result += "," + "pp=" + mPerfProfile;
        if( mThermalProfile != 0 ) result += "," + "tp=" + mThermalProfile;
        if( mMaxFrameRate != 0 ) result +=  "," + "fr=" + mMaxFrameRate;
        if( mMinFrameRate != 0 ) result +=  "," + "mfr=" + mMinFrameRate;
        if( !"".equals(mSpoofSimCountry) ) result += "," + "spsc=" + mSpoofSimCountry;
        if( !"".equals(mSpoofSimMnc) ) result += "," + "spsm=" + mSpoofSimMnc;
        if( !"".equals(mSpoofSimOpName) ) result += "," + "spso=" + mSpoofSimOpName;
        if( !"".equals(mSpoofSimLN) ) result += "," + "spsl=" + mSpoofSimLN;

        return result;
    }

    public void deserialize(@Nullable String profileString) {

        KeyValueListParser parser = new KeyValueListParser(',');

        try {
            parser.setString(profileString);
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
            return;
        }

        //mPackageName = parser.getString("pn",null);
        //if( mPackageName == null || mPackageName.equals("") ) throw new IllegalArgumentException();


        try {
            mUid = parser.getInt("uid",-1);
            mAppOpts = parser.getInt("ao",0);
            mOverride = parser.getInt("ovr",0);
            mAudio = parser.getInt("aud",0);
            mLocationLevel = parser.getInt("llv",0);
            mBackgroundMode = parser.getInt("bk",0);
            mBackgroundLevel = parser.getInt("bl",0);
            mBrightness = parser.getInt("br",0);
            mKeepOn = parser.getInt("koi",0);
            mRotation = parser.getInt("ro",0);
            mDarkMode = parser.getInt("dkm",0);
            mCameraMode = parser.getInt("cm",0);
            mMicrophoneMode = parser.getInt("mic",0);
            mFileAccess = parser.getInt("fa",0);
            mBlockHMS = parser.getInt("blhms",0);
            mBlockGMS = parser.getInt("blgms",0);
            mBlock3P = parser.getInt("bl3p",0);
            mBlockContacts = parser.getInt("blcnt",0);
            mBlockCallLog = parser.getInt("blcll",0);
            mBlockCalendar = parser.getInt("blcln",0);
            mBlockMedia = parser.getInt("blmed",0);
            mBlockSMS = parser.getInt("blsms",0);
            mBlockNotification = parser.getInt("blnot",0);
            mSpoof = parser.getInt("spf",0);
            mSpoofDevice = parser.getInt("sd",0);
            mInstaller = parser.getInt("ins",0);
            mPerformanceLevel = parser.getInt("pl",0);
            mBoostControl = parser.getInt("bcl",0);
            mPerfProfile = parser.getInt("pp",0);
            mThermalProfile = parser.getInt("tp",0);
            mMaxFrameRate = parser.getInt("fr",0);
            mMinFrameRate = parser.getInt("mfr",0);
            mSpoofSimCountry = parser.getString("spsc","");
            mSpoofSimMnc = parser.getString("spsm","");
            mSpoofSimOpName = parser.getString("spso","");
            mSpoofSimLN = parser.getString("spsl","");
        } catch( Exception e ) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
        }
    }

    public static @Nullable BaikalAppProfile deserializeProfile(@Nullable String profileString) {
        BaikalAppProfile profile = new BaikalAppProfile();
        try {
            profile.deserialize(profileString);
            return profile;
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
            return null;
        }
    }

    public String toString() {
        String result = this.serialize();
        if( (mAppInfo & BAIKAL_APPINFO_IS_SYSTEM) != 0 ) result += ",sys=true";
        if( (mAppInfo & BAIKAL_APPINFO_IS_IMPORTANT) !=0 ) result +=",imp=true";
        return result;
    }

    /*private static BaikalContext sContext = null;
    private static Object sContextLock = new Object();

    public static @Nullable BaikalAppProfile getCurrentAppProfile() {
        if( sContext == null ) {
            synchronized(sContextLock) {
                if( sContext == null ) {
                    sContext = BaikalContext.getApplicationBaikalContext();
                    if( sContext == null ) {
                        Slog.e(TAG, "BaikalContext: not ready yet!");
                        return new BaikalAppProfile(-3);
                    }
                }
            }
        }
        if( sContext.getCurrentAppProfile() == null ) {
            Slog.e(TAG, "BaikalContext: CurrentAppProfile not ready yet!");
            return new BaikalAppProfile(-3);
        }
        return sContext.getCurrentAppProfile();
    }*/

    public static @Nullable BaikalAppProfile getCurrentAppProfile() {
       BaikalContext context = BaikalContext.getApplicationBaikalContext();
        if( context == null ) {
            Slog.e(TAG, "BaikalContext: not ready yet!", new Throwable());
            return new BaikalAppProfile(-3);
        }
        if( context.getCurrentAppProfile() == null ) {
            Slog.e(TAG, "BaikalContext: CurrentAppProfile not ready yet!", new Throwable());
            return new BaikalAppProfile(-3);
        }
        return context.getCurrentAppProfile();
    }

}
