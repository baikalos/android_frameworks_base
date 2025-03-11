/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.clocks;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.service.notification.StatusBarNotification;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.tuner.TunerService;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

public class AODLastNotificationsStyle extends RelativeLayout implements TunerService.Tunable {

    private static final String TAG = "AODLastNotificationsStyle";

    private static final String CUSTOM_AOD_NOTIFICATION_ENABLED_KEY = "system:custom_aod_notification_enabled";
    private static final String CUSTOM_AOD_IMAGE_ENABLED_KEY = "system:custom_aod_image_enabled";
    private static final String CUSTOM_AOD_FS_IMAGE_ENABLED_KEY = "system:custom_aod_fs_image_enabled";


    private final Context mContext;
    private final TunerService mTunerService;

    private final StatusBarStateController mStatusBarStateController;

    private boolean mDozing;

    private TextView mAodNotificationView;
    private static boolean mAodNotificationEnabled;
    private static boolean mAodImageEnabled;
    private static boolean mAodFsImageEnabled;

    // Burn-in protection
    private static final int BURN_IN_PROTECTION_INTERVAL = 10000; // 10 seconds
    private static final int BURN_IN_PROTECTION_MAX_SHIFT = 4; // 4 pixels
    private final Handler mBurnInProtectionHandler = new Handler();
    private int mCurrentShiftX = 0;
    private int mCurrentShiftY = 0;

    private String mCurrentText = "";
    private static String sStaticText = "";
    
    private final Runnable mBurnInProtectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDozing) {
                mCurrentShiftX = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                mCurrentShiftY = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                if (mAodNotificationView != null) {
                    mAodNotificationView.setTranslationX(mCurrentShiftX);
                    mAodNotificationView.setTranslationY(mCurrentShiftY);
                }
                if( !sStaticText.equals(mCurrentText) ){
                    loadNotifications();
                }
                invalidate();
                mBurnInProtectionHandler.postDelayed(this, BURN_IN_PROTECTION_INTERVAL);
            }
        }
    };

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
        @Override
        public void onStateChanged(int newState) {}

        @Override
        public void onDozingChanged(boolean dozing) {
            if (mDozing == dozing) {
                return;
            }
            mDozing = dozing;
            updateAodNotificationView();
            if (mDozing) {
                if( !mAodNotificationEnabled ) return;
                startBurnInProtection();
            } else {
                stopBurnInProtection();
            }
        }
    };

    public AODLastNotificationsStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(this, CUSTOM_AOD_NOTIFICATION_ENABLED_KEY, CUSTOM_AOD_IMAGE_ENABLED_KEY, CUSTOM_AOD_FS_IMAGE_ENABLED_KEY);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAodNotificationView = findViewById(R.id.custom_aod_notification_view);
        Log.v(TAG, "mAodNotificationView=" + mAodNotificationView);
        if( mAodNotificationView != null ) {
            mAodNotificationView.setElegantTextHeight(true);
            //mAodNotificationView.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            mAodNotificationView.setSingleLine(false);
        }

        loadNotifications();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mStatusBarStateController.removeCallback(mStatusBarStateListener);
        mTunerService.removeTunable(this);
        mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
        if (mAodNotificationView != null) {
            mAodNotificationView.animate().cancel();
        }
    }

    private void startBurnInProtection() {
        mBurnInProtectionHandler.post(mBurnInProtectionRunnable);
    }

    private void stopBurnInProtection() {
        mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
        if (mAodNotificationView != null) {
            mAodNotificationView.setTranslationX(0);
            mAodNotificationView.setTranslationY(0);
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case CUSTOM_AOD_NOTIFICATION_ENABLED_KEY:
                mAodNotificationEnabled = TunerService.parseIntegerSwitch(
                    newValue, false); 
                break;
            case CUSTOM_AOD_IMAGE_ENABLED_KEY:
                mAodImageEnabled = TunerService.parseIntegerSwitch(newValue, false); 
                break;
            case CUSTOM_AOD_FS_IMAGE_ENABLED_KEY:
                mAodFsImageEnabled = TunerService.parseIntegerSwitch(newValue, false); 
                break;

        }
    }

    private void updateAodNotificationView() {
        if (mAodNotificationView == null || !mAodNotificationEnabled) {
            if (mAodNotificationView != null) mAodNotificationView.setVisibility(View.GONE);
            return;
        }

        TextView aodNotificationView;
        aodNotificationView = mAodNotificationView;

        if( aodNotificationView == null ) return;

        if (mDozing) {
            loadNotifications();

            aodNotificationView.setVisibility(View.VISIBLE);
            aodNotificationView.setScaleX(0f);
            aodNotificationView.setScaleY(0f);
            aodNotificationView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .withEndAction(this::startBurnInProtection)
                .start();
        } else {
            aodNotificationView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    aodNotificationView.setVisibility(View.GONE);
                    stopBurnInProtection();
                })
                .start();
        }
    }

    private void loadNotifications() {
        if (mAodNotificationView == null ) return;
        try {
            String text = sStaticText; //"BaikalOS\nHere will be your notifications...";
            if (text != null) {
                Log.v(TAG, "loadNotifications: " + text);
                mAodNotificationView.setText(text.toString());
                mCurrentText = text;
            } else {
                if( mAodNotificationView != null ) mAodNotificationView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.v(TAG, "Can't load text", e);
            if( mAodNotificationView != null ) mAodNotificationView.setVisibility(View.GONE);
        } finally {
        }
    }

    private static String lastNotification = "BaikalOS\nHere will be your notifications...";
    private static LinkedList<StatusBarNotification> _list = new LinkedList<StatusBarNotification>();

    public static void post(Collection<NotificationEntry> list) {
        if(!mAodNotificationEnabled) return;
        synchronized(_list) {
            _list.clear();
            for(NotificationEntry entry : list) {
                int ignore_flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_INSISTENT | Notification.FLAG_GROUP_SUMMARY;
                if( (entry.getSbn().getNotification().flags & ignore_flags) != 0 ) continue;
                //if( entry.getSbn().getNotification().visibility != Notification.VISIBILITY_PRIVATE) continue;
                _list.add(entry.getSbn());
            }
        }
        update();
    }

    public static void postNew(StatusBarNotification sbn) {
        if(!mAodNotificationEnabled) return;
        Log.v(TAG, "postNew(___)");
        Log.v(TAG, "postNew(sbn): " + sbn);
        if( sbn == null ) return;
        Notification n = sbn.getNotification();
        if( n == null ) return;
        Log.v(TAG, "postNew(n): " + n);
        Log.v(TAG, "postNew(---)");
    }



    //public static void post(StatusBarNotification sbn) {
    public static void update() {
        //if( sbn == null ) return;

        int maxSize = (mAodImageEnabled && !mAodFsImageEnabled) ? 4 : 6;
        int maxLen = 34;

        synchronized(_list) {
            String ntext = "";
            //_list.add(sbn);
            while( _list.size() > maxSize ) {
                _list.remove(_list.size()-1);
            }

            String separator = "";
            for(StatusBarNotification sbn : _list) {
                Log.v(TAG, "post(sbn): " + sbn);
                if( sbn == null ) continue;
                Notification n = sbn.getNotification();
                if( n == null ) continue;
                Log.v(TAG, "post(n): " + n);

                CharSequence ctitle = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence ctext = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                
                //CharSequence ticker = n.tickerText;
                if( ctitle != null && ctext != null )  {
                    String title = ctitle.toString();  
                    String text = ctext.toString();
                    if("".equals(title) && "".equals(text) ) continue;
                    title = title.length() > maxLen ? title.substring(0,maxLen) : title;
                    text = text.length() > maxLen ? text.substring(0,maxLen) : text;
                    ntext = ntext + separator + title + "\n" + text;
                    separator = "\n";
                }
            }
            sStaticText = ntext;
        }
        Log.v(TAG, "post(StatusBarNotification): " + sStaticText);
    }
}
