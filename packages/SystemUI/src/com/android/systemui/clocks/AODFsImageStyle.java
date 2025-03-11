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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.tuner.TunerService;

public class AODFsImageStyle extends FrameLayout implements TunerService.Tunable {

    private static final String TAG = "AODFsImageStyle";

    private static final String CUSTOM_AOD_IMAGE_URI_KEY = "system:custom_aod_image_uri";
    private static final String CUSTOM_AOD_IMAGE_ENABLED_KEY = "system:custom_aod_image_enabled";
    private static final String CUSTOM_AOD_FS_IMAGE_ENABLED_KEY = "system:custom_aod_fs_image_enabled";

    private final Context mContext;
    private final TunerService mTunerService;

    private final StatusBarStateController mStatusBarStateController;

    private boolean mDozing;

    private ImageView mAodImageView;
    private String mImagePath;
    private String mCurrImagePath;
    private boolean mAodImageEnabled;
    private boolean mAodFsImageEnabled;
    private boolean mImageLoaded = false;

    // Burn-in protection
    private static final int BURN_IN_PROTECTION_INTERVAL = 10000; // 10 seconds
    private static final int BURN_IN_PROTECTION_MAX_SHIFT = 4; // 4 pixels
    private final Handler mBurnInProtectionHandler = new Handler();
    private int mCurrentShiftX = 0;
    private int mCurrentShiftY = 0;
    
    private final Runnable mBurnInProtectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDozing) {
                mCurrentShiftX = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                mCurrentShiftY = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                if (mAodImageView != null) {
                    mAodImageView.setTranslationX(mCurrentShiftX);
                    mAodImageView.setTranslationY(mCurrentShiftY);
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
            updateAodImageView();
            if (mDozing) {
                if( !mAodFsImageEnabled ) return;
                startBurnInProtection();
            } else {
                stopBurnInProtection();
            }
        }
    };

    public AODFsImageStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(this, CUSTOM_AOD_IMAGE_URI_KEY, CUSTOM_AOD_IMAGE_ENABLED_KEY, CUSTOM_AOD_FS_IMAGE_ENABLED_KEY);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
    }

    @Override
    protected void onFinishInflate() {
        try {
            super.onFinishInflate();
            //if( !mAodFsImageEnabled ) return;
            mAodImageView = findViewById(R.id.custom_aod_fs_image_view);
            Log.v(TAG, "mAodImageView=" + mAodImageView);
            loadAodImage();
        } catch(Exception e) {
            Log.v(TAG, "onFinishInflate exception:", e);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow();
            mStatusBarStateController.removeCallback(mStatusBarStateListener);
            mTunerService.removeTunable(this);
            mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
            if (mAodImageView != null) {
                mAodImageView.animate().cancel();
                mAodImageView.setImageBitmap(null);
            }
        } catch( Exception e ) {
            Log.v(TAG, "onDetachedFromWindow:", e);
        }
    }

    private void startBurnInProtection() {
        if( !mAodFsImageEnabled ) {
            mBurnInProtectionHandler.post(mBurnInProtectionRunnable);
        }
    }

    private void stopBurnInProtection() {
        mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
        if (mAodImageView != null) {
            mAodImageView.setTranslationX(0);
            mAodImageView.setTranslationY(0);
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case CUSTOM_AOD_IMAGE_URI_KEY:
                mImagePath = newValue;
                if (mImagePath != null && !mImagePath.isEmpty() 
                    && !mImagePath.equals(mCurrImagePath)) {
                    mCurrImagePath = mImagePath;
                    mImageLoaded = false;
                    loadAodImage();
                }
                break;
            case CUSTOM_AOD_IMAGE_ENABLED_KEY:
                mAodImageEnabled = TunerService.parseIntegerSwitch(
                    newValue, false); 
                loadAodImage();
                break;
            case CUSTOM_AOD_FS_IMAGE_ENABLED_KEY:
                mAodFsImageEnabled = TunerService.parseIntegerSwitch(
                    newValue, false); 
                loadAodImage();
                break;
        }
    }

    private void updateAodImageView() {
        if (mAodImageView == null || !mAodImageEnabled || !mAodFsImageEnabled ) {
            if (mAodImageView != null) mAodImageView.setVisibility(View.GONE);
            return;
        }
        loadAodImage();

        ImageView aodImageView;
        aodImageView = mAodImageView;

        if( aodImageView == null ) return;

        if (mDozing) {
            aodImageView.setVisibility(View.VISIBLE);
            aodImageView.setScaleX(0f);
            aodImageView.setScaleY(0f);
            aodImageView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .withEndAction(this::startBurnInProtection)
                .start();
        } else {
            aodImageView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    aodImageView.setVisibility(View.GONE);
                    stopBurnInProtection();
                })
                .start();
        }
    }

    private void loadAodImage() {
        if (mAodImageView == null || mCurrImagePath == null || mCurrImagePath.isEmpty() || !mAodFsImageEnabled || mImageLoaded) return;
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(mCurrImagePath);
            if (bitmap != null) {
                if( mAodFsImageEnabled ) {
                    mAodImageView.setImageBitmap(bitmap);
                    mAodImageView.setTranslationZ(-10);
                    mImageLoaded = true;
                    Log.v(TAG, "Image loaded:" + mCurrImagePath);
                } else {
                    mImageLoaded = false;
                    Log.e(TAG, "Image not loaded. Disabled");
                    mAodImageView.setVisibility(View.GONE);
                }
            } else {
                Log.e(TAG, "Image not loaded. Not found or can't decode");
                mImageLoaded = false;
                mAodImageView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.v(TAG, "Can't load image " + mCurrImagePath, e);
            mImageLoaded = false;
            mAodImageView.setVisibility(View.GONE);
        } finally {
            if (bitmap != null) {
                //bitmap.recycle();
            }
        }
    }
}
