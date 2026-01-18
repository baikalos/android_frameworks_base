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


import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextParams;
import android.media.AudioSystem;
import android.os.Bundle;
import static android.os.Process.myUid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;
import android.os.UserHandle;
import android.util.Log;


@SuppressLint("EndsWithImpl")
public final class BaikalContext { 

    private static final String TAG = "BaikalContext";


    private static BaikalContext sApplicationBaikalContext;
    private static Context sApplicationContext;

    private final String mBasePackageName;
    private final String mOpPackageName;
    private final @NonNull Context mContext;
    private final @NonNull ContextParams mParams;
    private final @NonNull ContentResolver mContentResolver;
    private final int mFlags;
    private @NonNull IBaikalService mBaikalService;
    private @NonNull IBaikalAppProfileService mBaikalAppProfileService;
    private BaikalAppProfile mBaikalProfile;


    @SuppressLint("ContextFirst")
    public BaikalContext(@NonNull Context context,  
            @NonNull ContentResolver contentResolver, int flags,
            @Nullable String basePackageName, @Nullable String opPackageName,
            @NonNull ContextParams params) {

        sApplicationBaikalContext = this;
        sApplicationContext = context;
        mContext = context;
        mBasePackageName = basePackageName;
        mOpPackageName = opPackageName;
        mParams = params;
        mFlags = flags;
        mContentResolver = contentResolver;

        mBaikalService = IBaikalService.Stub.asInterface(ServiceManager.getService("baikal_service"));
        mBaikalAppProfileService = IBaikalAppProfileService.Stub.asInterface(ServiceManager.getService("baikal_app_profile"));

        if( UserHandle.getAppId(android.os.Process.myUid()) == android.os.Process.SYSTEM_UID ) {
            mBaikalProfile = new BaikalAppProfile(android.os.Process.SYSTEM_UID);
            //Log.d(TAG,"BaikalAppProfile system app loaded:" + mBaikalProfile.serialize());
        } else {
            if( mBaikalAppProfileService == null ) {
                Log.d(TAG,"BaikalAppProfileService not ready");
                mBaikalProfile = null;
            } else {
                try {
                    mBaikalProfile = mBaikalAppProfileService.getProfileNotNull(android.os.Process.myUid());
                    //Log.d(TAG,"BaikalAppProfile loaded:" + mBaikalProfile.serialize());
                    float appVolume = getAppVolume(basePackageName);
                    if( appVolume >= 0.0F ) {
                        AudioSystem.setAppVolume(basePackageName,appVolume);
                    }
                } catch(RemoteException re) {
                    Log.d(TAG,"BaikalAppProfile not loaded:", re);
                    //throw re.rethrowFromSystemServer();
                    mBaikalProfile = null;
                } catch(Exception e) {
                    Log.d(TAG,"BaikalAppProfile not loaded:", e);
                    // throw e.rethrowFromSystemServer();
                    mBaikalProfile = null;
                }
            }
        }
    }
    
    public static BaikalContext getApplicationBaikalContext() {
        return sApplicationBaikalContext;
    }

    public static Context getApplicationContext() {
        return sApplicationContext;
    }

    public static String getBasePackageName() {
        return sApplicationBaikalContext !=null ? sApplicationBaikalContext.mBasePackageName : "";
    }

    public IBaikalService getBaikalService() {
        return mBaikalService;
    }

    public IBaikalAppProfileService getBaikalAppProfileService() {
        return mBaikalAppProfileService;
    }

    public void updateContext() {
        if( mBaikalService == null ) 
            mBaikalService = IBaikalService.Stub.asInterface(ServiceManager.getService("baikal_service"));
        if( mBaikalAppProfileService == null )
            mBaikalAppProfileService = IBaikalAppProfileService.Stub.asInterface(ServiceManager.getService("baikal_app_profile"));
    }

    /**
     * 
     */
    @Nullable
    public BaikalAppProfile getCurrentAppProfile() {
        if( mBaikalProfile != null ) return mBaikalProfile;


        if( mBaikalService == null || mBaikalAppProfileService == null ) {
            synchronized(this) {
                updateContext();
            }
            if( mBaikalService == null || mBaikalAppProfileService == null ) {
                Log.e(TAG,"Baikal Services not ready! Something terribly wrong!", new Throwable());
                return new BaikalAppProfile(android.os.Process.myUid());
            }
        }


        if( mBaikalProfile == null ) {
            BaikalAppProfile baikalProfile = null;

            try {
                baikalProfile = mBaikalAppProfileService.getProfileNotNull(android.os.Process.myUid());
            } catch(Exception e) {
                Log.e(TAG,"Baikal not ready getProfileNotNull exception:", e);
            }

            synchronized(this) {
                if( mBaikalProfile == null ) {
                    if(baikalProfile == null ) {
                        Log.e(TAG,"Baikal Profile not ready! Something terribly wrong!", new Throwable());
                        return new BaikalAppProfile(android.os.Process.myUid());
                    }
                    mBaikalProfile = baikalProfile;
                }
            }
        }
        return mBaikalProfile;
    }

    /**
     * 
     */
    @Nullable
    public int getBaikalPackageOption(@Nullable String packageName, int uid, int opCode,int def) {
        try {
            return mBaikalService.getBaikalPackageOption(packageName,uid,opCode,def);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    @Nullable
    public String getBaikalPackageOptionString(@Nullable String packageName, int uid, int opCode, @Nullable String def) {
        try {
            return mBaikalService.getBaikalPackageOptionString(packageName,uid,opCode,def);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    @Nullable
    public int getBaikalOption(int opCode,int def, int callingUid, @Nullable String callingPackage) {
        try {
            return mBaikalService.getBaikalOption(opCode,def,callingUid,callingPackage);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    @Nullable
    public int getBaikalOptionWithParams(int opCode,int def, int callingUid, @Nullable String callingPackage, @Nullable Bundle params) {
        try {
            return mBaikalService.getBaikalOptionWithParams(opCode,def,callingUid,callingPackage,params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    @Nullable
    public String getBaikalOptionString(int opCode,@Nullable String def, int callingUid, @Nullable String callingPackage) {
        try {
            return mBaikalService.getBaikalOptionString(opCode,def,callingUid,callingPackage);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    @Nullable
    public String getBaikalOptionStringWithParams(int opCode,@Nullable String def, int callingUid, @Nullable String callingPackage, @Nullable Bundle params) {
        try {
            return mBaikalService.getBaikalOptionStringWithParams(opCode,def,callingUid,callingPackage,params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getBaikalSettingInt(int realm, String name, int def) {
        try {
            return mBaikalService.getBaikalSettingInt(realm, name, def);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Nullable
    public String getBaikalSettingString(int realm, String name, String def) {
        try {
            return mBaikalService.getBaikalSettingString(realm, name, def);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    @Nullable
    public BaikalAppProfile getBaikalAppProfile(int uid) {
        try {
            return mBaikalService.getBaikalAppProfile(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    public BaikalAppProfile getBaikalAppProfileNotNull(int uid) {
        try {
            return mBaikalService.getBaikalAppProfileNotNull(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the profile for the calling UID.
     * Reading is allowed for all apps.
     */
    @Nullable
    public BaikalAppProfile getProfile(int uid) {
        try {
            return mBaikalAppProfileService.getProfile(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set/update a profile.
     * Only SYSTEM_UID is allowed to write.
     */
    @Nullable
    public void saveProfile(@NonNull BaikalAppProfile profile) {
        try {
            mBaikalAppProfileService.saveProfile(profile);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Commit all profiles in memory to disk.
     * Only SYSTEM_UID is allowed.
     */
    @Nullable
    public void commit() {
        try {
            mBaikalAppProfileService.commit();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Update runtime-only fields in memory: mAppInfo, mIsInitialized, mIsValidated.
     * Only SYSTEM_UID is allowed.
     */
    @Nullable
    public void updateProfile(@NonNull BaikalAppProfile profile) {
        try {
            mBaikalAppProfileService.updateProfile(profile);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Nullable
    public String getTopAppPackageName() {
        try {
            return mBaikalService.getTopAppPackageName();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getTopAppUid() {
        try {
            return mBaikalService.getTopAppUid();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setAppVolume(String packageName, float volume) {
        try {
            mBaikalService.setAppVolume(packageName,volume);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Nullable
    public float getAppVolume(String packageName) {
        try {
            return mBaikalService.getAppVolume(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public static boolean isCompatChangeEnabled(long changeId) {
        if( sApplicationBaikalContext == null ) {
            Log.e(TAG,"BaikalOS change id: Context not ready! Something is terribly wrong!", new Throwable());
            return false;
        }
        long baikalChangeId = changeId & ~0x00BA000000000000L;
        if( baikalChangeId == 0 ) return false;
        BaikalAppProfile profile = sApplicationBaikalContext.getCurrentAppProfile();
        if( profile == null ) {
            Log.e(TAG,"BaikalOS change id: Profile not ready! Something is terribly wrong!", new Throwable());
            return false;
        }
        if( baikalChangeId ==  1L ) {
            if( (profile.mSpoof & BaikalAppProfile.BAIKAL_SPOOF_HIDE_VPN) != 0 ) {
                Log.d(TAG,"BaikalOS change id: Hide VPN (" + android.os.Process.myUid() + ")");
                return true;
            }
        }
        return false;
    }
}
