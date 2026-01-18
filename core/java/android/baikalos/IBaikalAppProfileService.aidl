package android.baikalos;

import android.baikalos.BaikalAppProfile;

/**
 * Binder interface for BaikalAppProfileService.
 * All parcelable parameters are 'in' direction for AOSP 16.
 */
interface IBaikalAppProfileService {
    /** Return profile for UID (may be null). */
    BaikalAppProfile getProfile(int uid);
    BaikalAppProfile getProfileNotNull(int uid);
    BaikalAppProfile getProfileByPackageName(String packageName);

    /** Update profile in memory without persisting. Only SYSTEM_UID allowed to call. */
    void updateProfile(in BaikalAppProfile profile);

    /** Save profile immediately (memory + disk). If profile.isDefault() => delete. Only SYSTEM_UID allowed. */
    void saveProfile(in BaikalAppProfile profile);

    /** Commit all in-memory profiles to disk. Only SYSTEM_UID allowed. */
    void commit();

}
