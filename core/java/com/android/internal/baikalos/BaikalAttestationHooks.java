/*
 * Copyright (C) 2024 crDroid Android Project
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

package com.android.internal.baikalos;

import android.os.SystemProperties;
import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyEntryResponse;
import android.util.Log;

import android.content.Context;
import android.content.ContentResolver;

import android.provider.Settings;

import java.security.cert.Certificate;

/**
 * @hide
 */
public final class BaikalAttestationHooks {

    private static final String TAG = BaikalAttestationHooks.class.getSimpleName();
    private static final boolean DEBUG = true;


    public static KeyEntryResponse onGetKeyEntry(KeyEntryResponse response) {
        return response;
    }
}
