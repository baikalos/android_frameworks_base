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

package com.android.server.location;

import static android.location.LocationManager.FUSED_PROVIDER;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.location.LocationManager.PASSIVE_PROVIDER;

import static android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY;
import static android.location.LocationRequest.QUALITY_HIGH_ACCURACY;
import static android.location.LocationRequest.QUALITY_LOW_POWER;
import static android.location.LocationRequest.PASSIVE_INTERVAL;

import static com.android.server.location.LocationPermissions.PERMISSION_COARSE;
import static com.android.server.location.LocationPermissions.PERMISSION_FINE;
import static com.android.server.location.LocationPermissions.PERMISSION_NONE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.baikalos.*;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
import android.util.Slog;

import com.android.internal.baikalos.*;
import com.android.server.baikalos.*;
import com.android.server.pm.pkg.PackageStateInternal;

import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.LocationRequest;
import android.location.LastLocationRequest;
import android.location.util.identity.CallerIdentity;

import com.android.server.location.provider.LocationProviderManager;

public final class BaikalLocationManager {

    static final String TAG = "BaikalLocationManager";

    private final Context mContext;
    private final IBaikalInternal mBaikal;
    private final LocationManagerService mLm;
    private final BaikalService mService;

    BaikalLocationManager(LocationManagerService service, Context context) {
        mContext = context;
        mLm = service;
        mBaikal = BaikalService.getService();
        if( mBaikal == null )  {
            Slog.w(TAG,"Baikal service not ready yet, postpone");
        } else {
            Slog.w(TAG,"Baikal service ready");
            mBaikal.setBaikalLocationManager(this);
        }

        mService = BaikalService.getInstance();
    }

    BaikalAppProfile getBaikalAppPofileNotNull(int uid) {
        return mBaikal.getBaikalAppProfileNotNull(uid);
    }

    public boolean isLocationProviderEnabled(String name) {

        int uid = Binder.getCallingUid();

        int level = getLocationLevel(uid);
        switch(level) {
            case 0:
                return true;
            case 1:
                return true;
            case 5:
                if( name.equals(PASSIVE_PROVIDER) ) return false;
            case 4:
                if( name.equals(NETWORK_PROVIDER) ) return false;
            case 3:
                if( name.equals(FUSED_PROVIDER) ) return false;
            case 2:
                if( name.equals(GPS_PROVIDER) ) return false;

        }
        return true;
    }

    public String overrideProvider(String provider, LocationRequest request, CallerIdentity identity) {

        int uid = identity.getUid(); //Binder.getCallingUid();

        if( request != null ) {
            WorkSource workSource = new WorkSource(request.getWorkSource());
            if (workSource != null && !workSource.isEmpty()) {
                WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
                if (workChain != null) {
                    uid = workChain.getAttributionUid();
                } else {
                    uid = workSource.getUid(0);
                }
                //Slog.i(TAG, "overrideProvider: Using workSource=" + workSource);
            }
        }

        int level = getLocationLevel(uid);
        if( level < 2 ) return provider;
        if( level > 4 ) {
            Slog.i(TAG, "overrideProvider: from " + provider + " to NONE Using uid=" + uid);
            //if( request != null ) request.setOriginalProvider(null);
            return null;
        } else if( level > 3 ) {
            Slog.i(TAG, "overrideProvider: from " + provider + " to PASSIVE Using uid=" + uid);
            if( request != null ) {
                //request.setOriginalProvider(provider);
                request.setProvider(PASSIVE_PROVIDER);
            }
            return PASSIVE_PROVIDER;
        } else {
            if( GPS_PROVIDER.equals(provider) || FUSED_PROVIDER.equals(provider) ) {
                Slog.i(TAG, "overrideProvider: from " + provider + " to NETWORK Using uid=" + uid);
                if( request != null ) {
                    //request.setOriginalProvider(provider);
                    request.setProvider(NETWORK_PROVIDER);
                }
                return NETWORK_PROVIDER;
            }
        }
        Slog.i(TAG, "overrideProvider: " + provider + " using uid=" + uid);
        return provider;
    }

    public int getRequestUid(int uid, LocationRequest request) {

        WorkSource workSource = new WorkSource(request.getWorkSource());
        if (workSource != null && !workSource.isEmpty()) {
            WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
            if (workChain != null) {
                uid = workChain.getAttributionUid();
            } else {
                uid = workSource.getUid(0);
            }
            //Slog.i(TAG, "getRequestUid: Using workSource=" + workSource);
        }
        return uid;
    }


    public LocationRequest.Builder sanitizeLocationRequest(LocationRequest.Builder source, CallerIdentity identity) {

        int uid = identity.getUid(); //Binder.getCallingUid();

        if( source == null ) return source;

        LocationRequest request = source.build();

        LocationRequest.Builder sanitized = new LocationRequest.Builder(request);

        WorkSource workSource = new WorkSource(request.getWorkSource());
        if (workSource != null && !workSource.isEmpty()) {
            WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
            if (workChain != null) {
                uid = workChain.getAttributionUid();
            } else {
                uid = workSource.getUid(0);
            }
            //Slog.i(TAG, "sanitizeLocationRequest: Using workSource=" + workSource);
        }

        int level = getLocationLevel(uid);
        switch(level) {
            case 0:
                return sanitized;

            case 1: // FULL
                sanitized.setQuality(QUALITY_HIGH_ACCURACY);
                sanitized.setMinUpdateIntervalMillis(0);
                sanitized.setMinUpdateDistanceMeters(0);
                return sanitized;
            case 5: // NONE
                //sanitized.setMinUpdateIntervalMillis( 180*60*1000 );
                //sanitized.setIntervalMillis(PASSIVE_INTERVAL);
                //sanitized.setQuality(POWER_LOW);
            case 4: // PASSIVE
                sanitized.setMinUpdateIntervalMillis( 180*60*1000 );
                sanitized.setIntervalMillis(PASSIVE_INTERVAL);
                sanitized.setQuality(QUALITY_LOW_POWER);
                return sanitized;

            case 3: // COARSE CITY
                sanitized.setQuality(QUALITY_LOW_POWER);
                return sanitized;

            case 2: // COARSE BLOCK
                sanitized.setQuality(QUALITY_HIGH_ACCURACY);
                sanitized.setMinUpdateIntervalMillis(0);
                sanitized.setMinUpdateDistanceMeters(0);
                return sanitized;

        }
        return sanitized;
    }

    public int overridePermissionLevel(int permissionLevel, LocationRequest request, CallerIdentity identity) {

        int level = getBaikalPermissionLevel(request,identity);

        switch(level) {
            case 1:
            case 2:
                return PERMISSION_FINE;
            case 3:
            case 4:
                return PERMISSION_COARSE;
            case 5:
                if( permissionLevel == PERMISSION_NONE ) return PERMISSION_NONE;
                return PERMISSION_COARSE;
        }
        return permissionLevel;
    }

    public int getBaikalPermissionLevel(LocationRequest request, CallerIdentity identity) {
        return getBaikalPermissionLevel(0,request,identity);
    }

    public int getBaikalPermissionLevel(int def, LocationRequest request, CallerIdentity identity) {

        int uid = identity.getUid(); //Binder.getCallingUid();

        if( request != null ) {
            WorkSource workSource = new WorkSource(request.getWorkSource());
            if (workSource != null && !workSource.isEmpty()) {
                WorkChain workChain = getFirstNonEmptyWorkChain(workSource);
                if (workChain != null) {
                    uid = workChain.getAttributionUid();
                } else {
                    uid = workSource.getUid(0);
                }
            }
        }

        return getBaikalPermissionLevel(def, uid);
    }

    public int getBaikalPermissionLevel(int def, int uid) {
        if( mService == null ) return def;

        if( mService.getSettings().isGmsUid(uid) ) return def;

        BaikalAppProfile profile = getBaikalAppPofileNotNull(uid);
        if( profile == null ) return def;
        return profile.mLocationLevel;    
    }

    public int getLocationLevel(int uid) {

        //Slog.i(TAG, "getLocationLevel: uid=" + uid);
        if( mService == null ) return 0;

        if( mService.getSettings().isGmsUid(uid) ) return 0;

        BaikalAppProfile profile = getBaikalAppPofileNotNull(uid);
        if( profile == null ) return 0;
        return profile.mLocationLevel;    
    }

    private WorkChain getFirstNonEmptyWorkChain(WorkSource workSource) {
        if (workSource.getWorkChains() == null) {
            return null;
        }

        for (WorkChain workChain: workSource.getWorkChains()) {
            if (workChain.getSize() > 0) {
                return workChain;
            }
        }

        return null;
    }

}
