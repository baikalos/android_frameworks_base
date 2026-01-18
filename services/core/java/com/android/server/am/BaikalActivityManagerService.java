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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.baikalos.*;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.baikalos.*;
import com.android.server.baikalos.*;



public final class BaikalActivityManagerService {

    static final String TAG = "BaikalActivityManagerService";

    private final Context mContext;
    private final IBaikalInternal mBaikal;
    private final ActivityManagerService mAm;
    final BaikalService mBaikalService;

    BaikalActivityManagerService(ActivityManagerService service, Context context) {
        mContext = context;
        mAm = service;
        mBaikal = BaikalService.getService();
        if( mBaikal == null )  {
            Log.w(TAG,"Baikal service not ready yet, postpone");
        } else {
            Log.w(TAG,"Baikal service ready");
            mBaikal.setBaikalActivityManagerService(this);
        }
        mBaikalService = BaikalService.getInstance();

        IBaikalService baikalContextService = mContext.getBaikalContext().getBaikalService();
        if( baikalContextService == null ) {
            mContext.getBaikalContext().updateContext();
            if( baikalContextService == null ) {
                Log.w(TAG,"Baikal context not ready yet, postpone");
            } else {
                Log.w(TAG,"Baikal context ready");
            }
        } else {
            Log.w(TAG,"Baikal context ready");
        }
        
    }

    BaikalService getBaikalService() {
        return mBaikalService;
    }

    void onSystemReady() {
        mBaikal.onSystemReady();
    }

    public int amFindRealUid(int uid) {
        if( !UserHandle.isIsolated(uid) ) return uid;
        ProcessRecord pr = mAm.getProcessRecord(uid);
        if( pr != null ) return pr.info.uid;
        return -1;
    }

    boolean overrideIsBackgroundRestricted(ProcessRecord app, boolean state) {
        return state;
    }

    BaikalAppProfile createProfileForProcessRecord(int _uid,ApplicationInfo info,boolean isolated,boolean isSdkSandbox,int _definingUid,String _processName) {
        int uid = isolated ? _definingUid : _uid;
        BaikalAppProfile profile = mBaikal.getBaikalAppProfileNotNull(/*info.packageName,*/uid);
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_DEFAULT_PROFILE) != 0 ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.d(TAG,"Loaded default baikal profile:" + profile.serialize());
            //profile.mPackageName = _processName;
        } else {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.d(TAG,"Loaded baikal profile:" + profile.serialize());
        }
        if( isolated || isSdkSandbox ) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.d(TAG,"Cloned baikal profile for isolated or sandboxed app:" + profile.serialize());
            return new BaikalAppProfile(profile);
        }
        return profile;
    }


    void sendTopAppChanged(int uid, String packageName) {
        // Notify BaikalOS core about top app change
        if( packageName == null ) packageName = "unknown";
        /*if( uid != -1 )*/ BaikalActions.sendTopAppChanged(uid,packageName);
    }

    boolean isBackgroundRestrictedNoCheck(final int uid, final String packageName) {
        return mBaikalService.isBackgroundRestrictedNoCheck(uid, packageName); 
    }

    public boolean isUidActive(int uid) {
        return mAm.isUidActiveLOSP(uid);
    }

    void handleBackgroundRestrictionChanged(int uid, String pkgName, boolean restricted) {
        mBaikalService.handleBackgroundRestrictionChanged(uid,pkgName,restricted);
    }

    public boolean isRunAnyInBackgroundDisabled(int uid, String packageName) {
        final int mode = mAm.getAppOpsManager().checkOpNoThrow(AppOpsManager.OP_RUN_ANY_IN_BACKGROUND,
                uid, packageName);
        return mode != AppOpsManager.MODE_ALLOWED;
    }

    public boolean isStartOnBootDisabled(int uid/*, String packageName*/) {
        BaikalAppProfile profile = mBaikal.getBaikalAppProfileNotNull(/*packageName,*/uid);
        if( (profile.mAppInfo & BaikalAppProfile.BAIKAL_APPINFO_IS_USER_RESTRICTED) != 0 ) return true;
        if( (profile.mBackgroundMode & BaikalAppProfile.BAIKAL_BACKGROUND_BOOT_DISABLED) != 0 ) return true;
        return false;
    }
    
    boolean stopIfKilled(ServiceRecord r) {
        return mBaikalService.isApplicationBackgroundRestrictedInternal(r.appInfo.packageName,r.appInfo.uid);
    }

    public void updateKillBgRestrictedCachedIdleSettleTime(long killBgRestrictedAndCachedIdleSettleTimeMs) {
        final long currentSettleTime = mAm.mConstants.mKillBgRestrictedAndCachedIdleSettleTimeMs;
        mAm.mConstants.mKillBgRestrictedAndCachedIdleSettleTimeMs = killBgRestrictedAndCachedIdleSettleTimeMs;
        if (mAm.mConstants.mKillBgRestrictedAndCachedIdleSettleTimeMs < currentSettleTime) {
            // Don't remove existing messages in case other IDLE_UIDS_MSG initiators use lower
            // delays, but send a new message if the settle time has decreased.
            mAm.mHandler.sendEmptyMessageDelayed(
                    ActivityManagerService.IDLE_UIDS_MSG,
                    mAm.mConstants.mKillBgRestrictedAndCachedIdleSettleTimeMs);
        }
    }

    void incBackgroundStartCount(int targetUid, int callingUid, int callingPid) {

        int callerState = getProcessState(callingUid, callingPid);
        if (callerState < 1 || callerState > 2) {
            if( BaikalConstants.BAIKAL_DEBUG_APP_PROFILE ) Log.d(TAG,"Background start:" + targetUid + " from " + callingUid + "/" + callingPid + " with state " + callerState);
            mBaikalService.incBackgroundStartCount(targetUid);
        }
    }

    int getProcessState(int uid, int pid) {

        if( pid < 0 ) {
            return PROCESS_STATE_CACHED_EMPTY; 
        }
        ProcessRecord cr = mAm.mPidsSelfLocked.get(pid);
        if (cr != null) {
            return cr.getCurProcState();
        }
        // If no process record, it's definitely not a TOP foreground app.
        return PROCESS_STATE_CACHED_EMPTY; 
    }

    void addIsolatedUid(int isolatedUid, int uid) {
        mBaikalService.addIsolatedUid(isolatedUid,uid);
    }

    void removeIsolatedUid(int isolatedUid, int uid) {
        mBaikalService.removeIsolatedUid(isolatedUid,uid);
    }

    void onPackageRemoved(String packageName, int uid, boolean allUsers) {
        // TODO: SDV: Handle package removed
    }
}

