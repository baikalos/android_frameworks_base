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

import android.util.Slog;
import android.util.KeyValueListParser;

public class AppProfile {

    private static final String TAG = "Baikal.AppProfile";

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
    public boolean mReader;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mPinned;

    @SuppressLint({"MutableBareField","InternalField"})
    public int mBackground;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDisableWakeup;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mDisableJobs;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mStamina;

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
    public boolean mSonification;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean mBypassCharging;

    @SuppressLint({"MutableBareField","InternalField"})
    public boolean isInvalidated;

    private static AppProfile mCurrentAppProfile = new AppProfile("current");

    public static @Nullable AppProfile getCurrentAppProfile() {
        return mCurrentAppProfile;
    }

    public static void setCurrentAppProfile(@Nullable AppProfile profile) {
        mCurrentAppProfile = profile;
    }

    private static AppProfile mDefaultProfile = new AppProfile("default");

    public static @Nullable AppProfile getDefaultProfile() {
        return mDefaultProfile;
    }

    public AppProfile() {
        mPerfProfile = 0;
        mThermalProfile = 0;
        mPackageName = "";
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
    }

    public AppProfile(@Nullable String packageName) {

        if( packageName == null ) mPackageName = "";
        else mPackageName = packageName;

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
    }

    public AppProfile(@Nullable AppProfile profile) {
        if( profile == null ) return;
        this.mPackageName = profile.mPackageName;
        this.mBrightness = profile.mBrightness;
        this.mReader = profile.mReader;
        this.mPinned = profile.mPinned;
        this.mStamina = profile.mStamina;
        this.mRequireGms = profile.mRequireGms;
        this.mBootDisabled = profile.mBootDisabled;
        this.mMaxFrameRate = profile.mMaxFrameRate;
        this.mMinFrameRate = profile.mMinFrameRate;
        this.mBackground = profile.mBackground;
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
    }

    public int getBackground() {
        if( mSystemWhitelisted && mBackground >=0 ) return -1;
        return mBackground;
    }

    public boolean isDefault() {
        if( mBrightness == 0 &&
            !mReader &&
            !mPinned &&
            !mStamina &&
            !mRequireGms &&
            !mBootDisabled &&
            mMaxFrameRate == 0 &&
            mMinFrameRate == 0 &&
            mBackground == 0 &&
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
            !mAllowIdleNetwork &&
            mFileAccess == 0 &&
            !mForcedScreenshot &&
            !mOverrideFonts &&
            !mFullScreen &&
            !mBAFRecv &&
            !mBAFSend &&
            !mSonification &&
            !mBypassCharging &&
            mPerfProfile == 0 &&
            mThermalProfile == 0 ) return true;
        return false;
    }

    public void update(@Nullable AppProfile profile) {
        this.mPackageName = profile.mPackageName;
        this.mBrightness = profile.mBrightness;
        this.mReader = profile.mReader;
        this.mPinned = profile.mPinned;
        this.mStamina = profile.mStamina;
        this.mRequireGms = profile.mRequireGms;
        this.mBootDisabled = profile.mBootDisabled;
        this.mMaxFrameRate = profile.mMaxFrameRate;
        this.mMinFrameRate = profile.mMinFrameRate;
        this.mBackground = profile.mBackground;
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
    }


    public @Nullable String serialize() {
        if( mPackageName == null || "".equals(mPackageName) ) return null;
        String result =  "pn=" + mPackageName;
        if( mBrightness != 0 ) result += "," + "br=" + mBrightness;
        if( mPerfProfile != 0 ) result += "," + "pp=" + mPerfProfile;
        if( mThermalProfile != 0 ) result += "," + "tp=" + mThermalProfile;
        if( mReader ) result +=  "," + "rm=" + mReader;
        if( mPinned ) result +=  "," + "pd=" + mPinned;
        if( mMaxFrameRate != 0 ) result +=  "," + "fr=" + mMaxFrameRate;
        if( mMinFrameRate != 0 ) result +=  "," + "mfr=" + mMinFrameRate;
        if( mStamina ) result +=  "," + "as=" + mStamina;
        if( mBackground != 0 ) result +=  "," + "bk=" + mBackground;
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
        if( mSonification ) result +=  "," + "sonf=" + mSonification;
        if( mBypassCharging ) result +=  "," + "bpc=" + mBypassCharging;
        return result;
    }

    public void deserialize(@Nullable String profileString) {

        KeyValueListParser parser = new KeyValueListParser(',');

        try {
            parser.setString(profileString);
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
            //throw new IllegalArgumentException();
            return;
        }

        mPackageName = parser.getString("pn",null);
        if( mPackageName == null || mPackageName.equals("") ) throw new IllegalArgumentException();
        try {
            mBrightness = parser.getInt("br",0);
            mPerfProfile = parser.getInt("pp",0);
            mThermalProfile = parser.getInt("tp",0);
            mReader = parser.getBoolean("rm",false);
            mPinned = parser.getBoolean("pd",false);
            mStamina = parser.getBoolean("as",false);
            mMaxFrameRate = parser.getInt("fr",0);
            mBackground = parser.getInt("bk",0);
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
            mSonification = parser.getBoolean("sonf",false);
            mBypassCharging = parser.getBoolean("bpc",false);
        } catch( Exception e ) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
        }
    }


    public static @Nullable AppProfile deserializeProfile(@Nullable String profileString) {
        AppProfile profile = new AppProfile();
        try {
            profile.deserialize(profileString);
            return profile;
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Bad profile settings :" + profileString, e);
            //throw new IllegalArgumentException();
            return null;
        }
    }

    public static @Nullable AppProfile get(@Nullable AppProfile profile) {
        if( profile != null ) return profile;
        return mDefaultProfile;
    }

    public String toString() {
        return this.serialize();
    }

}
