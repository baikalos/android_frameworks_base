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

package com.android.server.baikalos;

import static android.os.Process.myUid;

import android.os.Binder;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import android.baikalos.AppProfile;

public class BaikalSpooferService { 

    private static final String TAG = "BaikalSpooferService";
    private static com.android.server.pm.Settings mSettings;
    private static com.android.server.pm.InstallSource mInstallerNone;
    private static com.android.server.pm.InstallSource mInstallerGooglePlay;
    private static com.android.server.pm.InstallSource mInstallerAppGallery;
    private static com.android.server.pm.InstallSource mInstallerRuStore;
    private static com.android.server.pm.InstallSource mInstallerAOSP;
    private static com.android.server.pm.InstallSource mInstallerGoogle;

    private static boolean mInitilized;

    public static void updatePackageSettings(com.android.server.pm.Settings settings) {
        mSettings = settings;
        mInstallerNone = com.android.server.pm.InstallSource.EMPTY;
    }

    public static com.android.server.pm.InstallSource overrideInstallSource(com.android.server.pm.PackageSetting packageSettings) {

        AppProfile profile = AppProfileManager.getProfile(packageSettings.getName(), packageSettings.getAppId());
        if( profile != null ) {
            switch(profile.mInstaller) {
                case 1:
                    Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - None");
                    return mInstallerNone;
                case 2:
                    if( mInstallerGooglePlay == null ) mInstallerGooglePlay = getInstallSource("com.android.vending",2);
                    Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - Google Play");
                    return mInstallerGooglePlay;
                case 3:
                    if( mInstallerAppGallery == null ) mInstallerAppGallery = getInstallSource("com.huawei.appmarket",2);
                    Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - App Gallery");
                    return mInstallerAppGallery;
                case 4:
                    if( mInstallerRuStore == null ) mInstallerRuStore = getInstallSource("ru.vk.store",2);
                    Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - Rustore");
                    return mInstallerRuStore;
                case 5:
                    if( mInstallerAOSP == null ) mInstallerAOSP = getInstallSource("com.android.packageinstaller",3);
                    Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - AOSP PI");
                    return mInstallerAOSP;
                case 6:
                    if( mInstallerGoogle == null ) mInstallerGoogle = getInstallSource("com.google.android.packageinstaller",3);
                    Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - Google PI");
                    return mInstallerGoogle;
                default:
                    //Log.w(TAG, "overrideInstallSource:" + packageSettings.getName() + "/" + packageSettings.getAppId() + " - Default");
                    return null;
            }
        }
        return null;
    }

    private static com.android.server.pm.InstallSource getInstallSource(String packageName, int packageSource) {
        try {
            final com.android.server.pm.PackageSetting ips = mSettings.getPackageLPr(packageName);
            if( ips == null ) {
                return com.android.server.pm.InstallSource.baikalClone(packageName, packageName, null, packageSource);
            } else {
                return com.android.server.pm.InstallSource.baikalClone(packageName, packageName, ips.getSignatures(), packageSource);
            }
        } catch(Exception e) {
            Log.w(TAG, "overrideInstallSource: can't find installer " + packageName, e);
        }
        return null;
    }
}
