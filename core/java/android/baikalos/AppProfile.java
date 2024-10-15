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
import android.os.Process;
import android.os.SystemClock;

import android.util.Slog;
import android.util.KeyValueListParser;

public class AppProfile {

    public static final int OPCODE_LOCATION = 0;
    public static final int OPCODE_HIDE_HMS = 1;
    public static final int OPCODE_HIDE_GMS = 2;
    public static final int OPCODE_HIDE_3P = 3;
    public static final int OPCODE_BLOCK_CONTACTS = 4;
    public static final int OPCODE_BLOCK_CALLLOG = 5;
    public static final int OPCODE_BLOCK_CALENDAR = 6;
    public static final int OPCODE_BLOCK_MEDIA = 7;

    private static final String TAG = "Baikal.AppProfile";

    @SuppressLint({"MutableBareField","InternalField","AllUpper"})
    public static boolean DEBUG = false;

    @SuppressLint({"MutableBareField","InternalField","AllUpper"})
    public static boolean VERBOSE = false;

    @SuppressLint({"MutableBareField","InternalField","AllUpper"})
    public static boolean TRACE = false;

    @SuppressLint({"MutableBareField","InternalField"})
    public @Nullable String mPackageName;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mUid;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mBrightness;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mPerfProfile;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mThermalProfile;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mMaxFrameRate;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mMinFrameRate;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mReader;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mPinned;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDoNotClose;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mBackgroundMode;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mBackgroundModeConfig;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDisableWakeup;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDisableJobs;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDisableFreezer;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mStamina;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mStaminaEnforced;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mRequireGms;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBootDisabled;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIgnoreAudioFocus;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mRotation;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mAudioMode;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mSpoofDevice;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mKeepOn;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mPreventHwKeyAttestation;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHideDevMode;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mCamera;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mPerformanceLevel;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mMicrophone;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mFreezerMode;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mSystemWhitelisted;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mAllowIdleNetwork;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mFileAccess;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mForcedScreenshot;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mOverrideFonts;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mFullScreen;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBAFRecv;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBAFSend;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mSonification;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mForceOnSpeaker;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBypassCharging;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDebug;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHeavyMemory;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHeavyCPU;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBlockOverlays;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHideHMS;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHideGMS;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHide3P;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mAllowWhileIdle;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mHideIdle;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mInstaller;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mScaleFactor;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mOldLinks;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mLocationLevel;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mPriviledgedPhoneState;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBlockContacts;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBlockCalllog;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBlockCalendar;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBlockMedia;

    // internal
    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mSystemApp;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mImportantApp;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIsGmsPersistent;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIsGmsUnstable;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIsGms;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean isInvalidated;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mIsInitialized;


    private int mCurAdj = 900;
    private long mLastTopTime = 0;

    private static boolean sDebug;
    private static @Nullable String sPackageName = "unknown";
    private static int sUid = -1;
    private static int sPowerMode;
    private static boolean sStaminaActive;
    private static boolean sScreenOn = true;
    private static int sHomeUid = -1;
    private static int mDefaultBackgroundMode = 0;

    private static boolean sAutoLimit = false;

    private static int sTopUid = -1;
    private static @Nullable String  sTopPackageName = "unknown";
    private static AppProfile sTopAppProfile = new AppProfile("top",-1);

    public static void setDefaultBackgroundMode(int mode) {
        mDefaultBackgroundMode = mode;
    }

    public static int getDefaultBackgroundMode() {
        return mDefaultBackgroundMode;
    }

    public static void updateHomeProcess(int uid) {
        if( uid == 1000 ) sHomeUid = -1;
        else sHomeUid = uid;
        if( DEBUG ) Slog.d(TAG, "Home app set to :" + sHomeUid);
    }

    public static void setScreenMode(boolean on) {
        sScreenOn = on;
        if( DEBUG ) Slog.d(TAG, "Screen mode set to :" + on);
    }

    public static boolean isScreenOn() {
        return sScreenOn;
    }

    public static void setPowerMode(int mode) {
        sPowerMode = mode;
        if( DEBUG ) Slog.d(TAG, "PowerMode mode set to :" + mode);
    }

    public static int getPowerMode() {
        return sPowerMode;
    }

    public static void setStaminaActive(boolean enable) {
        sStaminaActive = enable;
        if( DEBUG ) Slog.d(TAG, "StaminaActive mode set to :" + enable);
    }

    public static boolean isDebug() {
        return sDebug;
    }

    public static boolean setAutoLimit(boolean limit) {
        if( limit != sAutoLimit ) {
            sAutoLimit = limit;
            if( DEBUG ) Slog.d(TAG, "AutoLimit mode set to :" + sAutoLimit);
            return true;
        }
        return false;
    }

    public static int uid() {
        return sUid;
    }

    public static @Nullable String packageName() {
        return sPackageName;
    }

    private static AppProfile sCurrentAppProfile = new AppProfile("current",-1);

    public static @Nullable AppProfile getCurrentAppProfile() {
        return sCurrentAppProfile;
    }

    public static void setCurrentAppProfile(@Nullable AppProfile profile,@Nullable String packageName, int uid) {
        sPackageName = packageName;
        sUid = uid;
        sCurrentAppProfile = profile;
        sDebug = profile.mDebug;
        if( profile.mUid <= 0 ) profile.mUid = uid;
        profile.mPackageName = packageName;
        if( DEBUG ) Slog.d(TAG, "CurrentAppProfile set to :" + sPackageName + "/" + sUid);
    }

    public static @Nullable AppProfile getTopAppProfile() {
        return sTopAppProfile;
    }

    public static void setTopAppProfile(@Nullable AppProfile profile,@Nullable String packageName, int uid) {
        sTopPackageName = packageName;
        sTopUid = uid;
        sTopAppProfile = profile;
        if( DEBUG ) Slog.d(TAG, "CurrentAppProfile set to :" + sPackageName + "/" + sUid);
    }

    //private static AppProfile sDefaultProfile = new AppProfile("default");

    /*public static @Nullable AppProfile getDefaultProfile() {
        return sDefaultProfile;
    }*/

    private AppProfile() {
        mPackageName = "";
        mUid = -1;
        clear();
    }

    public AppProfile(@Nullable String packageName, int uid) {

        if( packageName == null ) mPackageName = "";
        else mPackageName = packageName;

        mUid = uid;
        clear();
    }

    public AppProfile(@Nullable AppProfile profile) {
        update(profile);
    }


    public void clear() {

        mPerfProfile = 0;
        mThermalProfile = 0;
        mMaxFrameRate = 0;
        mMinFrameRate = 0;
        mRotation = 0;
        mAudioMode = 0;
        mSpoofDevice = 0;
        mCamera = 0;
        mPerformanceLevel = 0;
        mMicrophone = 0;
        mFreezerMode = 0;
        mSystemWhitelisted = false;
        mAllowIdleNetwork = false;
        mFileAccess = 0;
        mOverrideFonts = false;
        mDebug = false;
        mHeavyMemory = false;
        mHeavyCPU = false;
        mBlockOverlays = false;
        mHideHMS = false;
        mHideGMS = false;
        mHide3P = false;
        mSystemApp = false;
        mImportantApp = false;
        mAllowWhileIdle = false;
        mHideIdle = false;
        mInstaller = 0;
        mScaleFactor = 0;
        mLocationLevel = 0;
        mOldLinks = false;
        mPriviledgedPhoneState = false;

        mBrightness = 0;
        mReader = 0;
        mPinned = false;
        mDoNotClose = false;
        mStamina = false;
        mRequireGms = false;
        mBootDisabled = false;
        mBackgroundMode = 0;
        mBackgroundModeConfig = 0;
        mIgnoreAudioFocus = false;
        mKeepOn = false;
        mPreventHwKeyAttestation = false;
        mHideDevMode = false;
        mDisableWakeup = false;
        mDisableJobs = false;
        mDisableFreezer = false;
        mForcedScreenshot = false;
        mFullScreen = false;
        mBAFRecv = false;
        mBAFSend = false;
        mSonification = 0;
        mBypassCharging = false;
        
        mBlockContacts = false;
        mBlockCalllog = false;
        mBlockCalendar = false;
        mBlockMedia = false;

        mSystemApp = false;
        mImportantApp = false;
        mAllowWhileIdle = false;
        mIsInitialized = false;
        mSystemWhitelisted = false;

        mIsGms = false;
        mIsGmsPersistent = false;
        mIsGmsUnstable = false;

        isInvalidated = true;
    }

    public void setCurAdj(int adj) {
        mCurAdj = adj;
    }

    public int getCurAdj() {
        return mCurAdj;
    }


    public int getBackgroundMode() {
        return getBackgroundMode(false);
    }

    public int getBackgroundMode(boolean disableOnPowerSaver) {
        if( !DEBUG ) return getBackgroundModeInternal(disableOnPowerSaver);
        int result = getBackgroundModeInternal(disableOnPowerSaver);
        if( TRACE && result != 0 ) {
            if( mDebug ) {
                Slog.d(TAG, "getBackgroundMode: " + mPackageName + "/" + mUid + " result=" + result, new Throwable());
            } else {
                Slog.d(TAG, "getBackgroundMode: " + mPackageName + "/" + mUid + " result=" + result);
            }
        }
        return result;
    }

    public int getBackgroundModeInternal(boolean disableOnPowerSaver) {

        if( (isInvalidated ||
            !mIsInitialized) && mUid >=0 && mUid < 10000 ) {
            mImportantApp = true;
            // mAllowWhileIdle = true;
            mStaminaEnforced = true;
            mIsInitialized = true;
            isInvalidated = false;
            if( mUid == 1000 && mPackageName != null && 
                mPackageName.equals("android") ) {
                mSystemWhitelisted = true;
            }
        }

        if( (isInvalidated ||
            !mIsInitialized) &&
            mPackageName != null &&
            (  mPackageName.startsWith("com.google.android.gms") 
            || mPackageName.startsWith("com.huawei.hms") 
            || mPackageName.startsWith("com.huawei.hwid")
            && ! ( mPackageName.contains("auto_generated_rro_") ) ) ) {

            if( mPackageName.startsWith("com.google.android.gms") ) mIsGms = true;
        
            if( (mIsGms && mIsGmsPersistent) || !mIsGms) {
                mSystemWhitelisted = true;
                mStaminaEnforced = true;
                mImportantApp = true;
            } else {
                mSystemWhitelisted = false;
                mStaminaEnforced = false;
                mImportantApp = false;
            }
            // mAllowWhileIdle = true;
            mPriviledgedPhoneState = true;
            mAllowIdleNetwork = true;
            mIsInitialized = true;
            isInvalidated = false;
        }

        if( (isInvalidated ||
            !mIsInitialized) &&
            mPackageName != null &&
            mPackageName.equals("com.android.systemui") ) {
            mImportantApp = true;
            // mAllowWhileIdle = true;
            mPriviledgedPhoneState = true;
            mIsInitialized = true;
            isInvalidated = false;
        }

        //if( TRACE && mDebug ) Slog.d(TAG, "getBackgroundMode: " + mPackageName + "/" + mUid, new Throwable());

        if( mIsGms && !mIsGmsPersistent /*&& !mIsGmsUnstable*/ ) {
            int rc = mBackgroundMode; // mSystemWhitelisted ? -1 : mBackgroundMode >=0 ? ;
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(-2) non important gms: " + rc);
            return rc;
        }

        if( mSystemWhitelisted ) {
            if( mBackgroundMode >= 0 ) { 
                if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(1) mSystemWhitelisted:-1");
                return -1;
            }
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(2) mSystemWhitelisted:" + mBackgroundMode);
            return mBackgroundMode;
        }
        
        if( sTopUid == mUid ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(0) mTopUid:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mSystemApp ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(3) mSystemApp:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mBackgroundMode < 0 ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(4) mWhitelisted:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( sHomeUid == mUid ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(5) mHomeUid:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mImportantApp ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(6) mImportantApp:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mAllowWhileIdle ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(7) mAllowWhileIdle:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mStamina || mStaminaEnforced ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(8) mStamina:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mBackgroundMode > 0 )  {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(9) mBackgroundMode:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( mCurAdj <= 100 )  {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(9.1) mCurAdj:" + mBackgroundMode);
            return mBackgroundMode;
        }

        /*if( mBackgroundMode > 0 && !sScreenOn )  {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(10) mBackgroundMode:2");
            return 2;
        }*/

        // make it a bit more complex
        /*if( sStaminaActive && mBackgroundMode >= 0 && !mStamina ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(11) stamina:2");
            return 2;
        }*/

        /* if( !sAutoLimit || disableOnPowerSaver ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(12.1) no limit:" + mBackgroundMode);
            return mBackgroundMode;
        }

        if( sPowerMode >= 4 && mBackgroundMode >= 0 ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(12.2) extreme:2");
            return 2;
        }
        
        if( sPowerMode >= 3 && mBackgroundMode >= 0 ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(12.3) aggressive:2");
            return 2;
        }

        if( sPowerMode >= 2 && mBackgroundMode >= 0 ) {
            if( !sScreenOn ) {
                if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(13) moderate/screenoff:2");
                return 2;
            }
        } */

        if( mBackgroundModeConfig > 99 ) {
            if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(14) overriden default:" + 0);
            return 0;
        }

        if( mBackgroundMode >= 0 && mDefaultBackgroundMode > mBackgroundMode ) {
            final long now = SystemClock.uptimeMillis();
            final long timeout = now - 30 * 1000;
            if( mLastTopTime < timeout ) {
                if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(14) overriden auto:" + mDefaultBackgroundMode);
                return mDefaultBackgroundMode;
            }
        }

        if( VERBOSE || mDebug ) Slog.d(TAG, "" + mPackageName + "/" + mUid + ".getBackgroundMode(15) default:" + mBackgroundMode);
        return mBackgroundMode;
    }

    public boolean getStamina() {
        //if( VERBOSE || mDebug ) Slog.d(TAG, "AppProfile getStamina " + mPackageName + "/" + mUid + ":" + (mStamina || mImportantApp || mSystemWhitelisted));
        return mStamina || mStaminaEnforced || mImportantApp || mSystemWhitelisted;
    }

    public boolean isHeavy() {
        //if( VERBOSE || mDebug ) Slog.d(TAG, "AppProfile isHeavy " + mPackageName + "/" + mUid + ":" + (mHeavyCPU | mHeavyMemory));
        return mHeavyCPU | mHeavyMemory;
    }

    public boolean isHome() {
        return sHomeUid == mUid;
    }

    public boolean isDefault() {
        if( mBrightness == 0 &&
            mReader == 0 &&
            !mPinned &&
            !mDoNotClose &&
            !mStamina &&
            !mRequireGms &&
            !mBootDisabled &&
            mMaxFrameRate == 0 &&
            mMinFrameRate == 0 &&
            mBackgroundMode == 0 &&
            mBackgroundModeConfig == 0 &&
            !mIgnoreAudioFocus &&
            mRotation == 0 &&
            mAudioMode == 0 &&
            mSpoofDevice == 0 &&
            mCamera == 0 &&
            !mKeepOn &&
            !mPreventHwKeyAttestation &&
            !mHideDevMode &&
            mPerformanceLevel == 0 &&
            mMicrophone == 0 &&
            mFreezerMode == 0 &&
            !mDisableWakeup &&
            !mDisableJobs &&
            !mDisableFreezer &&
            !mAllowIdleNetwork &&
            mFileAccess == 0 &&
            !mForcedScreenshot &&
            !mOverrideFonts &&
            !mFullScreen &&
            !mBAFRecv &&
            !mBAFSend &&
            mSonification == 0 &&
            !mBypassCharging &&
            !mDebug &&
            !mHeavyMemory &&
            !mHeavyCPU &&
            !mBlockOverlays &&
            !mHideHMS &&
            !mHideGMS &&
            !mHide3P &&
            !mAllowWhileIdle &&
            !mHideIdle &&
            !mOldLinks &&
            mInstaller == 0 &&
            mScaleFactor == 0 &&
            mPerfProfile == 0 &&
            mLocationLevel == 0 &&
            !mPriviledgedPhoneState &&
            !mBlockContacts &&
            !mBlockCalllog &&
            !mBlockCalendar &&
            !mBlockMedia &&
            mThermalProfile == 0 ) return true;
        return false;
    }

    public @Nullable AppProfile update(@Nullable AppProfile profile) {
        if( profile == null ) {
            Slog.e(TAG, "Invalid profile assignment", new Throwable());
            return null;
        }

        this.mPackageName = profile.mPackageName;
        this.mUid = profile.mUid;
        this.mBrightness = profile.mBrightness;
        this.mReader = profile.mReader;
        this.mPinned = profile.mPinned;
        this.mDoNotClose = profile.mDoNotClose;
        this.mStamina = profile.mStamina;
        this.mStaminaEnforced = profile.mStaminaEnforced;
        this.mRequireGms = profile.mRequireGms;
        this.mBootDisabled = profile.mBootDisabled;
        this.mMaxFrameRate = profile.mMaxFrameRate;
        this.mMinFrameRate = profile.mMinFrameRate;
        this.mBackgroundMode = profile.mBackgroundMode;
        this.mBackgroundModeConfig = profile.mBackgroundModeConfig;
        this.mIgnoreAudioFocus = profile.mIgnoreAudioFocus;
        this.mRotation = profile.mRotation;
        this.mAudioMode = profile.mAudioMode;
        this.mSpoofDevice = profile.mSpoofDevice;
        this.mCamera = profile.mCamera;
        this.mKeepOn = profile.mKeepOn;
        this.mPreventHwKeyAttestation = profile.mPreventHwKeyAttestation;
        this.mHideDevMode = profile.mHideDevMode;
        this.mPerformanceLevel = profile.mPerformanceLevel;
        this.mMicrophone = profile.mMicrophone;
        this.mFreezerMode = profile.mFreezerMode;
        this.mPerfProfile = profile.mPerfProfile;
        this.mThermalProfile = profile.mThermalProfile;
        this.mDisableWakeup = profile.mDisableWakeup;
        this.mDisableJobs = profile.mDisableJobs;
        this.mDisableFreezer = profile.mDisableFreezer;
        this.mAllowIdleNetwork = profile.mAllowIdleNetwork;
        this.mFileAccess = profile.mFileAccess;
        this.mForcedScreenshot = profile.mForcedScreenshot;
        this.mOverrideFonts = profile.mOverrideFonts;
        this.mFullScreen = profile.mFullScreen;
        this.mBAFRecv = profile.mBAFRecv;
        this.mBAFSend = profile.mBAFSend;
        this.mSonification = profile.mSonification;
        this.mBypassCharging = profile.mBypassCharging;
        this.mSystemWhitelisted = profile.mSystemWhitelisted;
        this.mDebug = profile.mDebug;
        this.mHeavyMemory = profile.mHeavyMemory;
        this.mHeavyCPU = profile.mHeavyCPU;
        this.mSystemWhitelisted = profile.mSystemWhitelisted;
        this.mBlockOverlays = profile.mBlockOverlays;
        this.mHideHMS = profile.mHideHMS;
        this.mHideGMS = profile.mHideGMS;
        this.mHide3P = profile.mHide3P;
        this.mSystemApp = profile.mSystemApp;
        this.mImportantApp = profile.mImportantApp;
        this.mAllowWhileIdle = profile.mAllowWhileIdle;
        this.mHideIdle = profile.mHideIdle;
        this.mInstaller = profile.mInstaller;
        this.mScaleFactor = profile.mScaleFactor;
        this.mOldLinks = profile.mOldLinks;
        this.mLocationLevel = profile.mLocationLevel;
        this.mPriviledgedPhoneState = profile.mPriviledgedPhoneState;
        this.mBlockContacts = profile.mBlockContacts;
        this.mBlockCalllog = profile.mBlockCalllog;
        this.mBlockCalendar = profile.mBlockCalendar;
        this.mBlockMedia = profile.mBlockMedia;
        
        this.mIsGms = profile.mIsGms;
        this.mIsGmsPersistent = profile.mIsGmsPersistent;
        this.mIsGmsUnstable = profile.mIsGmsUnstable;
        if( TRACE || mDebug ) Slog.d(TAG, "AppProfile updated :" + profile.serialize());
        return this;
    }


    public @Nullable String serialize() {
        if( mPackageName == null || "".equals(mPackageName) ) return null;
        String result =  "pn=" + mPackageName;
        result += "," + "uid=" + mUid;
        if( mBrightness != 0 ) result += "," + "br=" + mBrightness;
        if( mPerfProfile != 0 ) result += "," + "pp=" + mPerfProfile;
        if( mThermalProfile != 0 ) result += "," + "tp=" + mThermalProfile;
        if( mReader != 0 ) result +=  "," + "rm=" + mReader;
        if( mPinned ) result +=  "," + "pd=" + mPinned;
        if( mDoNotClose ) result +=  "," + "dnc=" + mDoNotClose;
        if( mMaxFrameRate != 0 ) result +=  "," + "fr=" + mMaxFrameRate;
        if( mMinFrameRate != 0 ) result +=  "," + "mfr=" + mMinFrameRate;
        if( mStamina ) result +=  "," + "sta=" + mStamina;
        if( mBackgroundModeConfig != 0 ) result +=  "," + "bk=" + mBackgroundModeConfig;
        if( mRequireGms ) result +=  "," + "gms=" + mRequireGms;
        if( mBootDisabled ) result +=  "," + "bt=" + mBootDisabled;
        if( mIgnoreAudioFocus ) result +=  "," + "af=" + mIgnoreAudioFocus;
        if( mRotation != 0 ) result +=  "," + "ro=" + mRotation;
        if( mAudioMode != 0 ) result +=  "," + "am=" + mAudioMode;
        if( mSpoofDevice != 0 ) result +=  "," + "sd=" + mSpoofDevice;
        if( mKeepOn ) result +=  "," + "ko=" + mKeepOn;
        if( mPreventHwKeyAttestation ) result +=  "," + "pka=" + mPreventHwKeyAttestation;
        if( mCamera != 0 ) result +=  "," + "cm=" + mCamera;
        if( mPerformanceLevel != 0 ) result +=  "," + "pl=" + mPerformanceLevel;
        if( mMicrophone != 0 ) result +=  "," + "mic=" + mMicrophone;
        if( mHideDevMode ) result +=  "," + "hdm=" + mHideDevMode;
        if( mFreezerMode != 0 ) result +=  "," + "frz=" + mFreezerMode;
        if( mDisableWakeup ) result +=  "," + "dw=" + mDisableWakeup;
        if( mAllowIdleNetwork ) result += "," + "in=" + mAllowIdleNetwork;
        if( mFileAccess != 0 ) result += "," + "fa=" + mFileAccess;
        if( mForcedScreenshot ) result += "," + "fsc=" + mForcedScreenshot;
        if( mDisableJobs ) result +=  "," + "dj=" + mDisableJobs;
        if( mOverrideFonts ) result +=  "," + "of=" + mOverrideFonts;
        if( mFullScreen ) result +=  "," + "fs=" + mFullScreen;
        if( mBAFRecv ) result +=  "," + "bafr=" + mBAFRecv;
        if( mBAFSend ) result +=  "," + "bafs=" + mBAFSend;
        if( mSonification != 0 ) result +=  "," + "sonf=" + mSonification;
        if( mBypassCharging ) result +=  "," + "bpc=" + mBypassCharging;
        if( mDebug ) result +=  "," + "dbg=" + mDebug;
        if( mDisableFreezer ) result +=  "," + "dfr=" + mDisableFreezer;
        if( mHeavyMemory ) result +=  "," + "hm=" + mHeavyMemory;
        if( mHeavyCPU ) result +=  "," + "hc=" + mHeavyCPU;
        if( mBlockOverlays ) result +=  "," + "bo=" + mBlockOverlays;
        if( mHideHMS ) result +=  "," + "hhms=" + mHideHMS;
        if( mHideGMS ) result +=  "," + "hgms=" + mHideGMS;
        if( mHide3P ) result +=  "," + "h3p=" + mHide3P;
        if( mAllowWhileIdle ) result +=  "," + "aidl=" + mAllowWhileIdle;
        if( mHideIdle ) result +=  "," + "hidl=" + mHideIdle;
        if( mInstaller != 0 ) result +=  "," + "ins=" + mInstaller;
        if( mScaleFactor != 0 ) result +=  "," + "dsf=" + mScaleFactor;
        if( mOldLinks ) result +=  "," + "olnk=" + mOldLinks;
        if( mLocationLevel != 0 ) result +=  "," + "llv=" + mLocationLevel;
        if( mPriviledgedPhoneState ) result +=  "," + "pvps=" + mPriviledgedPhoneState;
        if( mBlockContacts ) result +=  "," + "blcn=" + mBlockContacts;
        if( mBlockCalllog ) result +=  "," + "blcl=" + mBlockCalllog;
        if( mBlockCalendar ) result +=  "," + "blcd=" + mBlockCalendar;
        if( mBlockMedia ) result +=  "," + "blmd=" + mBlockMedia;

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

        mPackageName = parser.getString("pn",null);
        if( mPackageName == null || mPackageName.equals("") ) throw new IllegalArgumentException();


        try {
            mUid = parser.getInt("uid",-1);
            mBrightness = parser.getInt("br",0);
            mPerfProfile = parser.getInt("pp",0);
            mThermalProfile = parser.getInt("tp",0);
            mPinned = parser.getBoolean("pd",false);
            mStamina = parser.getBoolean("sta",false);
            mMaxFrameRate = parser.getInt("fr",0);
            mBackgroundModeConfig = parser.getInt("bk",0);
            mRequireGms = parser.getBoolean("gms",false);
            mBootDisabled = parser.getBoolean("bt",false);
            mIgnoreAudioFocus = parser.getBoolean("af",false);
            mRotation = parser.getInt("ro",0);
            mAudioMode = parser.getInt("am",0);
            mSpoofDevice = parser.getInt("sd",0);
            mKeepOn = parser.getBoolean("ko",false);
            mPreventHwKeyAttestation = parser.getBoolean("pka",false);
            mCamera = parser.getInt("cm",0);
            mPerformanceLevel = parser.getInt("pl",0);
            mMicrophone = parser.getInt("mic",0);
            mHideDevMode = parser.getBoolean("hdm",false);
            mFreezerMode = parser.getInt("frz",0);
            mDisableWakeup = parser.getBoolean("dw",false);
            mAllowIdleNetwork = parser.getBoolean("in",false);
            mFileAccess = parser.getInt("fa",0);
            mMinFrameRate = parser.getInt("mfr",0);
            mForcedScreenshot = parser.getBoolean("fsc",false);
            mDisableJobs = parser.getBoolean("dj",false);
            mOverrideFonts = parser.getBoolean("of",false);
            mFullScreen = parser.getBoolean("fs",false);
            mBAFRecv = parser.getBoolean("bafr",false);
            mBAFSend = parser.getBoolean("bafs",false);
            mBypassCharging = parser.getBoolean("bpc",false);
            mSonification = parser.getInt("sonf",0);
            mDebug = parser.getBoolean("dbg",false);
            mDisableFreezer = parser.getBoolean("dfr",false);
            mHeavyMemory = parser.getBoolean("hm",false);
            mHeavyCPU = parser.getBoolean("hc",false);
            mDoNotClose = parser.getBoolean("dnc",false);
            mReader = parser.getInt("rm",0);
            mBlockOverlays = parser.getBoolean("bo",false);
            mHideHMS = parser.getBoolean("hhms",false);
            mHideGMS = parser.getBoolean("hgms",false);
            mHide3P = parser.getBoolean("h3p",false);
            mAllowWhileIdle = parser.getBoolean("aidl",false);
            mHideIdle = parser.getBoolean("hidl",false);
            mInstaller = parser.getInt("ins",0);
            mScaleFactor = parser.getInt("dsf",0);
            mOldLinks = parser.getBoolean("olnk",false);
            mLocationLevel = parser.getInt("llv",0);
            mPriviledgedPhoneState = parser.getBoolean("pvps",false);

            mBlockContacts = parser.getBoolean("blcn",false);
            mBlockCalllog = parser.getBoolean("blcl",false);
            mBlockCalendar = parser.getBoolean("blcd",false);
            mBlockMedia = parser.getBoolean("blmd",false);

            if( mBackgroundModeConfig > 99 ) {
                mBackgroundMode = mBackgroundModeConfig - 100;
            } else {
                mBackgroundMode = mBackgroundModeConfig;
            }
        } catch( Exception e ) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
        }
    }

    public void setLastTopTimeNow() {
        mLastTopTime = SystemClock.uptimeMillis();
    }

    public void setLastTopTime(long time) {
        mLastTopTime = time;
    }

    public long getLastTopTime(long time) {
        return mLastTopTime;
    }

    public static @Nullable AppProfile deserializeProfile(@Nullable String profileString) {
        AppProfile profile = new AppProfile();
        try {
            profile.deserialize(profileString);
            return profile;
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
            return null;
        }
    }

    /*public static @Nullable AppProfile get(@Nullable AppProfile profile) {
        if( profile != null ) return profile;
        return sDefaultProfile;
    }*/

    public String toString() {
        String result = this.serialize();
        if( mSystemApp ) result += ",sys=true";
        if( mImportantApp ) result +=",imp=true";
        return result;
    }


    public static int myUid() {
        return sUid;
    }

    public static @Nullable String myPackageName() {
        return sPackageName;
    }

}
