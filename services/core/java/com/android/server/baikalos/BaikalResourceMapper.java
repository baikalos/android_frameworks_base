package com.android.server.baikalos;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.util.SparseIntArray;

/**
 * A flexible mapper that loads int-to-int associations from AOSP resource arrays.
 */
public class BaikalResourceMapper {
    private static final String TAG = "BaikalResourceMapper";
    
    // Efficient storage for int -> int mapping
    private final SparseIntArray mMapping = new SparseIntArray();

    /**
     * Constructor that immediately loads the map.
     * * @param context System context
     * @param keysResId Resource ID of the integer-array containing keys
     * @param valuesResId Resource ID of the integer-array containing values
     */
    public BaikalResourceMapper(Context context, int keysResId, int valuesResId) {
        load(context, keysResId, valuesResId);
    }

    /**
     * Default constructor. Requires calling load() later.
     */
    public BaikalResourceMapper() {
    }

    /**
     * Loads or reloads the mapping from the provided resource IDs.
     * * @param context System context
     * @param keysResId Resource ID for keys (e.g., com.android.internal.R.array.config_keys)
     * @param valuesResId Resource ID for values (e.g., com.android.internal.R.array.config_values)
     * @return true if loaded successfully, false otherwise.
     */
    public boolean load(Context context, int keysResId, int valuesResId) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot load resources.");
            return false;
        }

        Resources res = context.getResources();
        try {
            int[] keys = res.getIntArray(keysResId);
            int[] values = res.getIntArray(valuesResId);

            if (keys == null || values == null) {
                Log.e(TAG, "One or both arrays are null.");
                return false;
            }

            if (keys.length != values.length) {
                Log.e(TAG, "Size mismatch: keys[" + keys.length + "] values[" + values.length + "]");
                return false;
            }

            // Clear previous data if reloading
            mMapping.clear();

            for (int i = 0; i < keys.length; i++) {
                mMapping.put(keys[i], values[i]);
            }

            Log.i(TAG, "Successfully loaded " + mMapping.size() + " mappings.");
            return true;

        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Resources not found during load", e);
            return false;
        }
    }

    /**
     * Gets the mapped value.
     * * @param key The key to look up
     * @param defaultValue Value to return if key is not found
     * @return The mapped integer value
     */
    public int get(int key, int defaultValue) {
        return mMapping.get(key, defaultValue);
    }

    /**
     * Returns the number of mapped elements.
     */
    public int size() {
        return mMapping.size();
    }

    /**
     * Clears the current mapping.
     */
    public void clear() {
        mMapping.clear();
    }
}
