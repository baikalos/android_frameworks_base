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


package com.android.server;


import android.util.Slog;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;


import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;


import android.os.Binder;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.UserHandle;
import android.os.Process;
import android.os.SystemClock;

import com.android.server.SystemService;


import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

public class BaikalSystemService extends SystemService {

    private static final String TAG = "BaikalSystemService";

    private static final boolean DEBUG = true;

    private boolean mSystemReady = false;

    private final Context mContext;

    final MyHandler mHandler;
    final MyHandlerThread mHandlerThread;

    public BaikalSystemService(Context context) {
        super(context);
        mContext = context;
        Slog.i(TAG,"BaikalSystemService()");

        mHandlerThread = new MyHandlerThread();
        mHandlerThread.start();
        mHandler = new MyHandler(mHandlerThread.getLooper());
    }

    @Override
    public void onStart() {
        Slog.i(TAG,"onStart()");
    }

    @Override
    public void onBootPhase(int phase) {
        Slog.i(TAG,"onBootPhase(" + phase + ")");

        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            synchronized(this) {

                /*
                int uid = getPackageUidLocked("com.google.android.gms");

                Runtime.setGmsUid(uid);
		        BaikalUtils.setGmsUid(uid);

                uid = getPackageUidLocked("com.android.vending");
                Runtime.setGpsUid(uid);

                //uid = getPackageUidLocked("com.dolby.daxservice");
                //BaikalUtils.setDolbyUid(uid);

                uid = getPackageUidLocked("com.android.systemui");
                BaikalUtils.setSystemUiUid(uid);
                */

	        }

            //mBaikalAppManager = new BaikalAppManager(this, mContext, mHandler);

    	} else if( phase == PHASE_BOOT_COMPLETED) {
            Slog.i(TAG,"onBootPhase(PHASE_BOOT_COMPLETED)");
        }
    }

    final class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
            }
        }
    }

    private class MyHandlerThread extends HandlerThread {

        Handler handler;

        public MyHandlerThread() {
            super("baikal.handler", android.os.Process.THREAD_PRIORITY_FOREGROUND);
        }
    }

    public static int getTemporaryAppWhitelistDuration(int uid, String packageName, String activity) {
        if( activity != null ) {
            if( activity.startsWith("com.huawei.android.push.intent.RECEIVE") ) { 
                Slog.i(TAG,"getTemporaryAppWhitelistDuration: Huawei Push");
                return 10000;
            } else if( activity.startsWith("com.google.android.c2dm.intent.RECEIVE") ) {
                Slog.i(TAG,"getTemporaryAppWhitelistDuration: Google Push");
                return 10000;
            }
        }
        return 0;
    }

}
