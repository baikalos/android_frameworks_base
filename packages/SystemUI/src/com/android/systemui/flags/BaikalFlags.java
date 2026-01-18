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

package com.android.systemui.flags;

import android.content.Context;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.system.Os;
import android.system.StructUtsname;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class BaikalFlags { 

    private static final String TAG = "BaikalFlags";

    private static final String DEV_ALL_OVERRIDE_PROPERTY = "persist.baikal.dev_all_override";
    private static final String DEV_VIS_OVERRIDE_PROPERTY = "persist.baikal.dev_vis_override";
    private static final String DEV_EN_OVERRIDE_PROPERTY = "persist.baikal.dev_en_override";
    private static final String DEV_KERN_OVERRIDE_PROPERTY = "persist.baikal.dev_kern_override";

    private Map<String, BaikalFlag> mFlags = new HashMap<>();
    private static boolean isKernelCompatible = true;
    private static boolean isAllOverride = SystemProperties.getBoolean(DEV_ALL_OVERRIDE_PROPERTY,false);
    private static boolean isVisOverride = SystemProperties.getBoolean(DEV_VIS_OVERRIDE_PROPERTY,false);
    private static boolean isEnOverride = SystemProperties.getBoolean(DEV_EN_OVERRIDE_PROPERTY,false);
    private static boolean isKernOverride = SystemProperties.getBoolean(DEV_KERN_OVERRIDE_PROPERTY,false);

    private static BaikalFlags mInstance;

    public class BaikalFlag {
        public String mKey;
        public String mName;
        public String mProperty;
        public boolean mVisible;
        public boolean mEnabled;
        public boolean mKernel;

        public BaikalFlag(String key, String name, String property, boolean visible, boolean enabled, boolean kernel) {
            mKey = key;
            mName = name;
            mProperty = property;
            mVisible = visible; 
            mEnabled = enabled;
            mKernel = kernel;
        }

        public boolean getVisibility(boolean visible) {
            if( isAllOverride || isVisOverride ) return true;
            if( !"".equals(mProperty) ) {
                int prop = SystemProperties.getInt(mProperty,0);
                if( prop == -1 ) return false;
                if( prop == 1 ) return true;
            }
            if( mKernel && !isKernelCompatible ) return false; 
            return mVisible & visible;
        }

        public boolean getEnabled(boolean enabled) {
            if( isAllOverride || isEnOverride ) return true;
            if( !"".equals(mProperty) ) {
                int prop = SystemProperties.getInt(mProperty,0);
                if( prop == -1 ) return false;
                if( prop == 1 ) return true;
            }
            return mEnabled & enabled;
        }
    }

    private BaikalFlags() {
        mInstance = this;
    }

    public static BaikalFlags Instance() {
        if( mInstance == null ) {
            mInstance = new BaikalFlags();
            mInstance.init();
        }
        return mInstance;
    }

    public void add(BaikalFlag flag) {
        mFlags.put(flag.mKey,flag);
    }

    public BaikalFlag get(String key) {
        BaikalFlag flag = mFlags.get(key);
        if( flag != null ) return flag;
        flag = new BaikalFlag(key,key,"",false,false,true);
        Log.w(TAG, "Flag " + key + " not found!");
        return flag;
    }

    public void init() {
        add(new BaikalFlag("boost_tile","boost_tile","",true,true,false));
    }
}

