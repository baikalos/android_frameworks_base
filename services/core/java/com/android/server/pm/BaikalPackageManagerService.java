/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.pm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.baikalos.*;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.baikalos.*;
import com.android.server.baikalos.*;
import com.android.server.pm.pkg.PackageStateInternal;
import com.android.server.pm.snapshot.PackageDataSnapshot;

public final class BaikalPackageManagerService {

    static final String TAG = "BaikalPackageManagerService";

    private final Context mContext;
    private final IBaikalInternal mBaikal;
    private final PackageManagerService mPm;
    static BaikalPackageManagerService sInstance = null;
    
    static BaikalPackageManagerService getInstance() {
        return sInstance;
    }

    BaikalPackageManagerService(PackageManagerService service, Context context) {
        sInstance = this;
        mContext = context;
        mPm = service;
        mBaikal = BaikalService.getService();
        if( mBaikal == null )  {
            Log.w(TAG,"Baikal service not ready yet, postpone");
        } else {
            Log.w(TAG,"Baikal service ready");
            mBaikal.setBaikalPackageManagerService(this);
        }
    }

    public BaikalAppProfile pmGetAppProfile(int uid) {
        Computer snapshot = mPm.snapshotComputer();
        String [] packages = snapshot.getPackagesForUid(UserHandle.getAppId(uid));
        if( packages != null && packages.length > 0 ) {
            return new BaikalAppProfile(packages[0],uid);
        }
        return null;
    }

    public boolean isSystemPackage(String packageName) {
        Computer snapshot = mPm.snapshotComputer();
        PackageStateInternal state = snapshot.getPackageStateInternal(packageName, Process.SYSTEM_UID);
        if (state == null) {
            // isolated / shared / no app
            return false;
        }

        boolean system = state.isSystem() || state.isUpdatedSystemApp() || state.isApex() || state.isPrivileged();

        if (system) {
            return true;
        } 
        return false;
    }

    BaikalAppProfile getBaikalAppPofileNotNull(String packageName, int uid) {
        return mBaikal.getBaikalAppProfileNotNull(packageName,uid);
    }

    boolean isAllowSigOverride(int appid) {
        BaikalAppProfile profile = mBaikal.getBaikalAppProfileNotNull(null,appid);
        return (profile.mOverride & BaikalAppProfile.BAIKAL_OVERRIDE_SIGNATURE) != 0;
    }

    boolean isApplicationBackgroundRestricted(ApplicationInfo info) {
        if(info == null) return false;
        if(UserHandle.getAppId(info.uid) < 10000) return false;
        if(mBaikal.isApplicationBackgroundRestricted(info.packageName,info.uid)) return true;
        return false;
    }

    boolean shouldFilterApplication(PackageDataSnapshot snapshot, int callingUid, @Nullable Object callingSetting, PackageStateInternal targetPkgSetting, int userId) {
        final int targetAppId = targetPkgSetting.getAppId();
        final int callingAppId = UserHandle.getAppId(callingUid);
        if( callingAppId == targetAppId ) return false;
        if( callingAppId < Process.FIRST_APPLICATION_UID || targetAppId < Process.FIRST_APPLICATION_UID ) return false;
        boolean blockGms = mBaikal.getBaikalPackageOption(null,callingUid,BaikalAppProfile.BAIKAL_OPCODE_BLOCK_GMS,0) != 0;
        if( blockGms ) {
            final String targetPackage = targetPkgSetting.getPackageName();
            if( targetPackage.startsWith("com.google.android.gms") || targetPackage.startsWith("com.android.vending") ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideGMS(1) packageName=" + targetPackage + " filter GMS " + ", myUid=" + Process.myUid() + ", callingUid=" + callingUid);
                return true;
            }
        }
        boolean blockHms = mBaikal.getBaikalPackageOption(null,callingUid,BaikalAppProfile.BAIKAL_OPCODE_BLOCK_HMS,0) != 0;
        if( blockHms ) {
            final String targetPackage = targetPkgSetting.getPackageName();
            if( targetPackage.startsWith("com.huawei.hwid") || targetPackage.startsWith("com.huawei.hms") ) {
                if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"HideHMS(1) packageName=" + targetPackage + " filter HMS " + ", myUid=" + Process.myUid() + ", callingUid=" + callingUid);
                return true;
            }
        }

        boolean block3p = mBaikal.getBaikalPackageOption(null,callingUid,BaikalAppProfile.BAIKAL_OPCODE_BLOCK_3P,0) != 0;
        if( block3p ) {
            final String targetPackage = targetPkgSetting.getPackageName();
            if( isSystemPackage(targetPackage) ) return false;
            if( mBaikal.isImportantApp(targetPackage) ) return false;
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.i(TAG,"Hide3P(1) packageName=" + targetPackage + " filter 3P " + ", myUid=" + Process.myUid() + ", callingUid=" + callingUid);
            return true;
        }

        return false;
    }
}
