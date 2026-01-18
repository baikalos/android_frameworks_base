package com.android.server.baikalos;

import android.baikalos.BaikalAppProfile;
import androidx.annotation.Nullable;

/**
 * Local (internal) interface exposed via LocalServices.
 * Method names are simple (no "Internal" suffix) - they will delegate
 * inside the service to private methods with the "Internal" suffix.
 *
 * Thread-safety must be provided by the service implementation.
 */
public interface IBaikalAppProfileInternal {
    @Nullable
    BaikalAppProfile getProfile(int uid);

    @Nullable
    BaikalAppProfile getProfileByPackageName(String packageName);

    void updateProfile(BaikalAppProfile profile);

    void saveProfile(BaikalAppProfile profile);

    void commit();

    Object getBaikalAppProfileServiceInstance();
    void setBaikalService(BaikalService service);

    int overrideStandbyBucket(String packageName, int bucket, int mode);

    void onSystemReady();
}
