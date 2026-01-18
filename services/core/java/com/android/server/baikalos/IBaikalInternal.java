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

package com.android.server.baikalos;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.baikalos.*;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.android.server.am.BaikalActivityManagerService;
import com.android.server.pm.BaikalPackageManagerService;
import com.android.server.power.BaikalPowerManagerService;
import com.android.server.location.BaikalLocationManager;

public interface IBaikalInternal {
    /**
     *
     */
    int getBaikalPackageOption(@Nullable String packageName, int uid, int opCode,int def);

    /**
     *
     */
    @Nullable String getBaikalPackageOptionString(@Nullable String packageName, int uid, int opCode, @Nullable String def);

    /**
     *
     */
    int getBaikalOption(int opCode,int def, int callingUid, @Nullable String callingPackage);

    /**
     *
     */
    int getBaikalOptionWithParams(int opCode,int def, int callingUid, @Nullable String callingPackage, @Nullable Bundle params);

    /**
     *
     */
    @Nullable String getBaikalOptionString(int opCode,@Nullable String def, int callingUid, @Nullable String callingPackage);

    /**
     *
     */
    @Nullable String getBaikalOptionStringWithParams(int opCode,@Nullable String def, int callingUid, @Nullable String callingPackage, @Nullable Bundle params);

    /**
     *
     */
    @Nullable BaikalAppProfile getBaikalAppProfile(@Nullable String packageName, int uid);

    /**
     *
     */
    BaikalAppProfile getBaikalAppProfileNotNull(@Nullable String packageName, int uid);

    /**
     *
     */
    int getBaikalSettingInt(int realm,@NonNull String name, int def);

    /**
     *
     */
    String getBaikalSettingString(int realm,@NonNull String name,@Nullable String def);

    void setBaikalActivityManagerService(@NonNull BaikalActivityManagerService manager);
    void setBaikalPackageManagerService(@NonNull BaikalPackageManagerService manager);
    void setBaikalPowerManagerService(@NonNull BaikalPowerManagerService manager);
    void setBaikalLocationManager(@NonNull BaikalLocationManager manager);
    void handleProfileUpdated(@NonNull BaikalAppProfile profile);

    void setDeviceIdleWhitelist(int[] sysAppids, int[] userAppids, int[] exceptIdleAppids);

    void onSystemReady();

    boolean isApplicationBackgroundRestricted(@Nullable String packageName, int uid);
    boolean isAutoAppRestrictionActive();
    boolean isImportantApp(@Nullable String packageName);
}

