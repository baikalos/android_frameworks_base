package com.android.server.baikalos;

import android.content.Context;
import android.content.res.Resources;
import android.os.FileUtils;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.SparseIntArray;


import com.android.internal.R;

/**
 * A flexible mapper that loads int-to-int associations from AOSP resource arrays.
 */
public class BaikalThermalManager {
    private static final String TAG = "BaikalThermalManager";
    
    final Context mContext;
    
    int mType = 0; // 0 - kernel fs, 1 - system property

    String mKernelFSPath;
    String mPropertyName;

    String mCurrentThermalProfile = null;

    public BaikalThermalManager(Context context) {
        mContext = context;
    }

    /**
     * Loads or reloads the mapping from the provided resource IDs.
     * * @param context System context
     * @param keysResId Resource ID for keys (e.g., com.android.internal.R.array.config_keys)
     * @param valuesResId Resource ID for values (e.g., com.android.internal.R.array.config_values)
     * @return true if loaded successfully, false otherwise.
     */
    public boolean initialize() {
        if (mContext == null) {
            Slog.e(TAG, "Context is null, cannot load resources.");
            return false;
        }

        Resources res = mContext.getResources();
        try {
            mType = res.getInteger(R.integer.config_baikal_thermal_type);
            if( mType == 0 ) {
                mKernelFSPath = res.getString(R.string.config_baikal_thermal_kernelfs);
            } else if( mType == 1 ) {
                mPropertyName = res.getString(R.string.config_baikal_thermal_property);
            } else {
                Slog.e(TAG, "BaikalOS Thermal Manager not configured properly");
            }
            Slog.i(TAG, "Thermal type " + mType + " on " + ((mType == 0) ? mKernelFSPath:mPropertyName) + " configured");
            return true;

        } catch (Exception e) {
            Slog.e(TAG, "Resources not found during load", e);
            return false;
        }
    }
    
    public String updateThermalProfile(String profile) {
        if( mType == 0 )  return updateKernelFsProfile(profile);
        else if( mType == 1 ) return updateSystemPropertyProfile(profile);
        else return null;
    }

    private String updateKernelFsProfile(String profile) {
        try {
            if( mKernelFSPath != null && !"".equals(mKernelFSPath) &&
                profile != null && !"".equals(profile) ) {
                if( !profile.equals(mCurrentThermalProfile) ) {
                    FileUtils.stringToFile(mKernelFSPath, profile);
                    mCurrentThermalProfile = profile;
                }
                return profile;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to write to " + mKernelFSPath, e);
        }
        return null;
    }

    private String updateSystemPropertyProfile(String profile) {
        try {
            if( !profile.equals(mCurrentThermalProfile) ) {
                SystemProperties.set(mPropertyName, profile);
                mCurrentThermalProfile = profile;
            }
            return profile;
        } catch (Exception e) {
            Slog.e(TAG, "Failed to set property " + mPropertyName, e);
        }
        return null;
    }
}
