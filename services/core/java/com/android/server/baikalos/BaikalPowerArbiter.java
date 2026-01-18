package com.android.server.baikalos;

import android.hardware.power.Mode;
import android.os.Binder;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.util.Slog;
import android.util.SparseBooleanArray;
import com.android.server.LocalServices;

import com.android.server.power.BaikalPowerManagerService;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * High-performance Power Mode Arbiter for Android 16.
 * Features: Selective HAL syncing, state caching, and BaikalService integration.
 */
public class BaikalPowerArbiter {
    private static final String TAG = "PowerArbiter";
    private final Object mLock = new Object();
    
    // Registry: Mode -> Set of [Source:UID] tokens
    private final Map<Integer, HashSet<String>> mActiveRequests = new HashMap<>();
    
    // Cache of actual HAL states to prevent redundant JNI calls
    private final SparseBooleanArray mHalStates = new SparseBooleanArray();
    
    private boolean mStrictLowPowerMode = true;
    private boolean mLastOverrideState = false;

    private static final Set<Integer> OVERRIDE_MODES = Set.of(
        Mode.LOW_POWER,
        Mode.DEVICE_IDLE
    );

    //private PowerManagerInternal mPowerInternal;
    BaikalPowerManagerService mBaikalPowerManagerService;

    public void systemReady() {
        //mPowerInternal = LocalServices.getService(PowerManagerInternal.class);
    }

    /**
     * Set global policy for Low Power priority.
     */
    public void setStrictLowPowerMode(boolean enabled) {
        synchronized (mLock) {
            if (mStrictLowPowerMode != enabled) {
                mStrictLowPowerMode = enabled;
                syncAllLocked();
            }
        }
    }

    public int setPowerModeInternal(int mode, boolean enabled, int uid) {
        if( mBaikalPowerManagerService == null ) return -1;
        updateRequest(mode,enabled,"BAIKALOS",uid);
        return 1;
    }

    public int setPowerModeInternal(int mode, boolean enabled) {
        if( mBaikalPowerManagerService == null ) return -1;
        updateRequest(mode,enabled,"BAIKALOS",Process.myUid());
        return 1;
    }

    public int onSetPowerModeInternal(BaikalPowerManagerService service, int mode, boolean enabled, int uid) {
        mBaikalPowerManagerService = service;
        updateRequest(mode,enabled,"FRAMEWORK",uid);
        return 1;
    }

    /**
     * Main update method for any power mode change.
     */
    public void updateRequest(int mode, boolean enabled, String source, int uid) {
        // English comments for compiler compatibility.
        String token = source + ":" + uid;
        synchronized (mLock) {
            mActiveRequests.putIfAbsent(mode, new HashSet<>());
            HashSet<String> tokens = mActiveRequests.get(mode);

            boolean changed = enabled ? tokens.add(token) : tokens.remove(token);
            if (!changed) return;

            boolean currentOverride = isOverrideEffectivelyActiveLocked();
            
            if (currentOverride != mLastOverrideState) {
                // If global override state flipped, sync everything.
                syncAllLocked();
            } else {
                // Otherwise, sync only the mode that was updated.
                syncSingleModeLocked(mode, currentOverride);
            }
        }
    }

    /**
     * Syncs a single mode to HAL only if the desired state differs from the cached state.
     */
    private void syncSingleModeLocked(int mode, boolean isOverrideActive) {
        boolean hasRequests = isModeRequestedLocked(mode);
        boolean targetState;

        if (isOverrideActive) {
            // Strict Mode: performance modes are suppressed.
            targetState = OVERRIDE_MODES.contains(mode) && hasRequests;
        } else {
            targetState = hasRequests;
        }

        // Only call HAL if state actually changes
        if (mHalStates.get(mode) != targetState) {

            if (mBaikalPowerManagerService != null) {
                mBaikalPowerManagerService.getPowerManagerService().setPowerModeInternalFromBaikalWrapper(mode, targetState);
                mHalStates.put(mode, targetState);
            }
        }
    }

    /**
     * Full synchronization of all known modes.
     */
    private void syncAllLocked() {
        mLastOverrideState = isOverrideEffectivelyActiveLocked();
        Set<Integer> allModes = new HashSet<>(mActiveRequests.keySet());
        allModes.addAll(OVERRIDE_MODES);

        for (int mode : allModes) {
            syncSingleModeLocked(mode, mLastOverrideState);
        }
    }

    private boolean isOverrideEffectivelyActiveLocked() {
        return mStrictLowPowerMode && 
               OVERRIDE_MODES.stream().anyMatch(this::isModeRequestedLocked);
    }

    private boolean isModeRequestedLocked(int mode) {
        HashSet<String> tokens = mActiveRequests.get(mode);
        return tokens != null && !tokens.isEmpty();
    }

    /**
     * Handle process death to prevent "stuck" high-performance states.
     */
    public void handleProcessDied(int uid) {
        synchronized (mLock) {
            boolean changed = false;
            for (HashSet<String> tokens : mActiveRequests.values()) {
                if (tokens.removeIf(token -> token.endsWith(":" + uid))) {
                    changed = true;
                }
            }
            if (changed) syncAllLocked();
        }
    }

    /**
     * Debugging via 'adb shell dumpsys power' (or your custom service dump).
     */
    public void dump(PrintWriter pw) {
        synchronized (mLock) {
            pw.println("BAIKAL POWER ARBITER:");
            pw.println("  Strict Low Power Mode: " + mStrictLowPowerMode);
            pw.println("  Current Override State: " + mLastOverrideState);
            
            if (mActiveRequests.isEmpty()) {
                pw.println("  No active requests.");
            } else {
                pw.println("  Active Requests & HAL States:");
                for (Map.Entry<Integer, HashSet<String>> entry : mActiveRequests.entrySet()) {
                    int mode = entry.getKey();
                    HashSet<String> tokens = entry.getValue();
                    if (!tokens.isEmpty()) {
                        pw.print("    Mode " + mode + " (" + BaikalPowerManager.getPowerModeName(mode) + "): ");
                        pw.print(tokens.toString());
                        pw.println(" [HAL: " + (mHalStates.get(mode) ? "ON" : "OFF") + "]");
                    }
                }
            }
        }
    }
}
