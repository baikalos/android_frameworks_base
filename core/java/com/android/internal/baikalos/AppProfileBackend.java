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

package com.android.internal.baikalos;

import android.util.Slog;

import android.text.TextUtils;

import android.os.UserHandle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemProperties;

import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;

import android.app.AppOpsManager;
import android.baikalos.AppProfile;

import android.net.Uri;

import android.database.ContentObserver;

import android.provider.Settings;

import android.util.ArraySet;
import android.util.Pair;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AppProfileBackend extends AppProfileBase {

    private static final String TAG = "BaikalSettings";

    private static AppProfileBackend sInstance;

    private AppProfileBackend(Handler handler,Context context) {
        super(handler,context);
        super.registerObserver(false);
        loadProfiles();
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        Slog.i(TAG, "Preferences changed (selfChange=" + selfChange + ", uri=" + uri + "). Reloading");
    }

    public static AppProfileBackend getInstance() {
        return sInstance;
    }

    public static AppProfileBackend getInstance(Handler handler, Context context) {
        if (sInstance == null) {
            sInstance = new AppProfileBackend(handler,context);
        }
        return sInstance;
    }

    public AppProfile updateProfile(AppProfile profile) {
        synchronized(this) {
            return updateProfileLocked(profile);
        }
    }

    public AppProfile updateProfileLocked(AppProfile profile) {
        if( "android".equals(profile.mPackageName) || "system".equals(profile.mPackageName) ) return profile;
        AppProfile newProfile = profile;
        if( !_profilesByPackageName.containsKey(profile.mPackageName) ) {
            _profilesByPackageName.put(profile.mPackageName, profile);
            newProfile = profile;
        } else {
            AppProfile oldProfile = _profilesByPackageName.get(profile.mPackageName);
            oldProfile.update(profile);
            newProfile = oldProfile;
        }

        int uid = getAppUidLocked(profile.mPackageName);
        if( uid == -1 ) return newProfile;

        profile.mUid = uid;
        if( !_profilesByUid.containsKey(uid)  ) {
            _profilesByUid.put(profile.mUid, profile);
        }

        return newProfile;
    }
}
