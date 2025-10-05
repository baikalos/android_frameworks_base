/*
 * SPDX-FileCopyrightText: 2024 Paranoid Android
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.baikalos.keybox;

import android.hardware.security.keymint.Algorithm;
import android.hardware.security.keymint.KeyParameter;
import android.hardware.security.keymint.KeyParameterValue;
import android.hardware.security.keymint.Tag;
import android.os.Binder;
import android.os.SystemProperties;
import android.system.keystore2.Authorization;
import android.system.keystore2.IKeystoreSecurityLevel;
import android.system.keystore2.KeyDescriptor;
import android.system.keystore2.KeyEntryResponse;
import android.system.keystore2.KeyMetadata;
import android.util.Log;

import com.android.internal.baikalos.keybox.KeyboxChainGenerator.KeyGenParameters;

import com.android.internal.baikalos.BaikalSpoofer;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @hide
 */
public class KeyboxImitationHooks {

    private static final String TAG = "KeyboxImitationHooks";
    //private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static boolean mFailed = false;
    private static boolean mIsAttestation = false;
    private static byte[] mAttestationChallenge = null;
    private static boolean hasAttestKeyDescriptor = false;
    private static Integer keyAlgo;

    public static KeyEntryResponse onGetKeyEntry(KeyDescriptor descriptor) {

        if( BaikalSpoofer.disableCertificateSpoof() ) {
            dlog("Key entry spoofer disabled");
            return null;
        }

        if (!KeyProviderManager.isKeyboxAvailable()) {
            dlog("Keybox not available");
            return null;
        }

        if (mFailed) {
            dlog("Key entry spoofer failed");
            return null;
        }

        if (!mIsAttestation) {
            dlog("Key entry spoofer not an attestation");
            return null;
        }

        if (hasAttestKeyDescriptor) {
            dlog("Key entry spoofer no attestkey descriptor");
            return null;
        }

        if (keyAlgo == null || (keyAlgo != Algorithm.EC && keyAlgo != Algorithm.RSA)) {
            dlog("Key entry spoofer invalid parameter");
            return null;
        }

        int uid = Binder.getCallingUid();
        KeyEntryResponse spoofed = KeyboxUtils.retrieve(uid, descriptor.alias);
        if (spoofed != null) {
            dlog("Key entry spoofed, Algorithm: " + keyAlgo);
            return spoofed;
        }

        dlog("Key entry not spoofed, Algorithm: " + keyAlgo);
        return null;
    }

    public static boolean deleteKey(KeyDescriptor descriptor) {
        int uid = Binder.getCallingUid();
        return KeyboxUtils.remove(uid, descriptor.alias);
    }

    public static KeyMetadata generateKey(IKeystoreSecurityLevel level, KeyDescriptor descriptor, Collection<KeyParameter> args) {
        if( BaikalSpoofer.disableCertificateSpoof() ) {
            dlog("generateKey spoofer disabled");
            return null;
        }

        if (!KeyProviderManager.isKeyboxAvailable()) {
            dlog("generateKey spoofer disabled");
            return null;
        }

        KeyGenParameters params = new KeyGenParameters(args.toArray(new KeyParameter[args.size()]));
        if (params.algorithm != Algorithm.EC && params.algorithm != Algorithm.RSA) {
            Log.w(TAG, "Unsupported algorithm: " + params.algorithm);
            return null;
        }

        int uid = Binder.getCallingUid();
        try {
            List<Certificate> chain = KeyboxChainGenerator.generateCertChain(uid, descriptor, params);
            if (chain == null || chain.isEmpty()) {
                dlog("generateKey chain is empty");
                return null;
            }
            KeyEntryResponse response = buildResponse(level, chain, params, descriptor);
            if (response == null) {
                dlog("generateKey response is empty");
                return null;
            }
            KeyboxUtils.append(uid, descriptor.alias, response);
            mFailed = false;
            putAlgo(params.algorithm);
            return response.metadata;
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate key", e);
            return null;
        }
    }

    private static KeyEntryResponse buildResponse(
            IKeystoreSecurityLevel level,
            List<Certificate> chain,
            KeyGenParameters params,
            KeyDescriptor descriptor
    ) {
        try {
            KeyEntryResponse response = new KeyEntryResponse();
            KeyMetadata metadata = new KeyMetadata();
            metadata.keySecurityLevel = params.securityLevel;

            KeyboxUtils.putCertificateChain(metadata, chain.toArray(new Certificate[chain.size()]));

            KeyDescriptor d = new KeyDescriptor();
            d.domain = descriptor.domain;
            d.nspace = descriptor.nspace;
            metadata.key = d;

            List<Authorization> authorizations = new ArrayList<>();
            Authorization a;

            for (Integer i : params.purpose) {
                a = new Authorization();
                a.keyParameter = new KeyParameter();
                a.keyParameter.tag = Tag.PURPOSE;
                a.keyParameter.value = KeyParameterValue.keyPurpose(i);
                a.securityLevel = params.securityLevel;
                authorizations.add(a);
            }

            for (Integer i : params.digest) {
                a = new Authorization();
                a.keyParameter = new KeyParameter();
                a.keyParameter.tag = Tag.DIGEST;
                a.keyParameter.value = KeyParameterValue.digest(i);
                a.securityLevel = params.securityLevel;
                authorizations.add(a);
            }

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.ALGORITHM;
            a.keyParameter.value = KeyParameterValue.algorithm(params.algorithm);
            a.securityLevel = params.securityLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.KEY_SIZE;
            a.keyParameter.value = KeyParameterValue.integer(params.keySize);
            a.securityLevel = params.securityLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.EC_CURVE;
            a.keyParameter.value = KeyParameterValue.ecCurve(params.ecCurve);
            a.securityLevel = params.securityLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.NO_AUTH_REQUIRED;
            a.keyParameter.value = KeyParameterValue.boolValue(true); // TODO: copy
            a.securityLevel = params.securityLevel;
            authorizations.add(a);

            // TODO: ORIGIN, OS_VERSION, OS_PATCHLEVEL, VENDOR_PATCHLEVEL, BOOT_PATCHLEVEL,
            // CREATION_DATETIME, USER_ID

            metadata.authorizations = authorizations.toArray(new Authorization[0]);
            response.metadata = metadata;
            response.iSecurityLevel = level;
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build key entry response", e);
            return null;
        }
    }

    public static void setFailFlag(boolean flag) {
        mFailed = flag;
    }

    public static void putAlgo(int algo) {
        keyAlgo = algo;
    }

    public static void setAttestationFlag(boolean flag) {
        mIsAttestation = flag;
    }

    public static void setAttestKeyFlag(boolean flag) {
        hasAttestKeyDescriptor = flag;
    }

    public static void setAttestationChallenge(byte[] challenge) {
        mAttestationChallenge = challenge;
    }

    public static byte[] getAttestationChallenge() {
        return mAttestationChallenge;
    }

    private static void dlog(String msg) {
        if (SystemProperties.getBoolean("persist.baikal.kb_debug", false)) {
            Log.d(TAG, msg);
        }
    }
}
