/*
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.baikalos.keybox;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.hardware.security.keymint.Algorithm;
import android.hardware.security.keymint.EcCurve;
import android.hardware.security.keymint.KeyParameter;
import android.hardware.security.keymint.Tag;
import android.os.Binder;
import android.os.Build;
import android.os.SystemProperties;
import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyDescriptor;
import android.util.Log;

//import androidx.annotation.Nullable;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1Integer;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.internal.org.bouncycastle.asn1.DERNull;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.asn1.DERSet;
import com.android.internal.org.bouncycastle.asn1.DERTaggedObject;
import com.android.internal.org.bouncycastle.asn1.x500.X500Name;
import com.android.internal.org.bouncycastle.asn1.x509.Extension;
import com.android.internal.org.bouncycastle.asn1.x509.KeyUsage;
import com.android.internal.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import com.android.internal.org.bouncycastle.asn1.x509.Time;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.android.internal.org.bouncycastle.operator.ContentSigner;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

/**
 * @hide
 */
public final class KeyboxChainGenerator {

    private static final String TAG = "KeyboxChainGenerator";
    //private static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int ATTESTATION_APPLICATION_ID_PACKAGE_INFOS_INDEX = 0;
    private static final int ATTESTATION_APPLICATION_ID_SIGNATURE_DIGESTS_INDEX = 1;
    private static final int ATTESTATION_PACKAGE_INFO_PACKAGE_NAME_INDEX = 0;
    private static final int ATTESTATION_PACKAGE_INFO_VERSION_INDEX = 1;

    public static List<Certificate> generateCertChain(int uid, KeyDescriptor descriptor, KeyGenParameters params) {
        dlog("Requested KeyPair with alias: " + descriptor.alias);
        int size = params.keySize;
        KeyPair kp;
        try {
            if (Objects.equals(params.algorithm, Algorithm.EC)) {
                dlog("Generating EC keypair of size " + size);
                kp = buildECKeyPair(params);
            } else if (Objects.equals(params.algorithm, Algorithm.RSA)) {
                dlog("Generating RSA keypair of size " + size);
                kp = buildRSAKeyPair(params);
            } else {
                dlog("Unsupported algorithm");
                return null;
            }

            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    KeyboxUtils.getCertificateHolder(
                            Objects.equals(params.algorithm, Algorithm.EC)
                                    ? KeyProperties.KEY_ALGORITHM_EC
                                    : KeyProperties.KEY_ALGORITHM_RSA
                    ).getSubject(),
                    params.certificateSerial,
                    new Time(params.certificateNotBefore),
                    new Time(params.certificateNotAfter),
                    params.certificateSubject,
                    SubjectPublicKeyInfo.getInstance(
                            ASN1Sequence.getInstance(kp.getPublic().getEncoded())
                    )
            );

            KeyUsage keyUsage = new KeyUsage(KeyUsage.keyCertSign);
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage);
            certBuilder.addExtension(createExtension(params, uid));

            ContentSigner contentSigner;
            if (Objects.equals(params.algorithm, Algorithm.EC)) {
                contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(KeyboxUtils.getPrivateKey(KeyProperties.KEY_ALGORITHM_EC));
            } else {
                contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(KeyboxUtils.getPrivateKey(KeyProperties.KEY_ALGORITHM_RSA));
            }
            X509CertificateHolder certHolder = certBuilder.build(contentSigner);
            Certificate leaf = KeyboxUtils.getCertificateFromHolder(certHolder);
            List<Certificate> chain = KeyboxUtils.getCertificateChain(leaf.getPublicKey().getAlgorithm());
            chain.add(0, leaf);
            dlog("Successfully generated X500 Cert for alias: " + descriptor.alias);
            return chain;
        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
        }
        return null;
    }

    private static ASN1Encodable[] fromIntList(List<Integer> list) {
        ASN1Encodable[] result = new ASN1Encodable[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = new ASN1Integer(list.get(i));
        }
        return result;
    }

    static byte[] verifiedBoot = null;
    static byte[] verifiedBootHash = null;

    private static Extension createExtension(KeyGenParameters params, int uid) {
        try {

            if( verifiedBoot == null || verifiedBootHash == null ) {
                SecureRandom random = new SecureRandom();
                String bootid = SystemProperties.get("ro.boot.vbmeta.digest","");

                if( "".equals(bootid) ) {
                    Log.e(TAG, "Missing vbmeta digest. Incompatible kernel");
                    return null;
                }

                try {
                    if( "".equals(bootid) ) bootid = byteArrayToString(sha256(Build.ID));
                    verifiedBootHash = hexStringToByteArray(bootid);
                    verifiedBoot = sha256(verifiedBootHash);
                    //verifiedBootHash = verifiedBoot; //sha256(verifiedBoot);
                } catch(Exception e){
                    //verifiedBoot = null;
                    //verifiedBootHash = null;
                }
    
                if( verifiedBootHash == null ) {
                    verifiedBootHash = new byte[32];
                    random.nextBytes(verifiedBootHash);
                    Log.w(TAG, "invalid verified boot hash, using random");
                } 
    
                if( verifiedBoot == null ) {
                    verifiedBoot = new byte[32];
                    random.nextBytes(verifiedBoot);
                    Log.w(TAG, "invalid verified boot key, using random");
                } 

            }

            final boolean spoofDevice = SystemProperties.getBoolean("persist.baikal.spf.att.device",false);
            final boolean spoofV4 = false; // SystemProperties.getBoolean("persist.baikal.spf.att.v4",false);
            //final boolean spoofMintVersion = SystemProperties.getBoolean("persist.baikal.spf.att.v4",false);


            ASN1Encodable[] rootOfTrustEncodables = {new DEROctetString(verifiedBoot), ASN1Boolean.TRUE,
                    new ASN1Enumerated(0), new DEROctetString(verifiedBootHash)};

            ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEncodables);

            var Apurpose = new DERSet(fromIntList(params.purpose));
            var Aalgorithm = new ASN1Integer(params.algorithm);
            var AkeySize = new ASN1Integer(params.keySize);
            var Adigest = new DERSet(fromIntList(params.digest));
            var AecCurve = new ASN1Integer(params.ecCurve);
            var AnoAuthRequired = DERNull.INSTANCE;

            // To be loaded
            var AosVersion = new ASN1Integer(getOsVersion());
            var AosPatchLevel = new ASN1Integer(getPatchLevel());

            var AapplicationID = createApplicationId(uid);
            var AbootPatchlevel = new ASN1Integer(getPatchLevelLong());
            var AvendorPatchLevel = new ASN1Integer(getPatchLevelLong());

            var AcreationDateTime = new ASN1Integer(System.currentTimeMillis());
            var Aorigin = new ASN1Integer(0);

            var purpose = new DERTaggedObject(true, 1, Apurpose);
            var algorithm = new DERTaggedObject(true, 2, Aalgorithm);
            var keySize = new DERTaggedObject(true, 3, AkeySize);
            var digest = new DERTaggedObject(true, 5, Adigest);
            var ecCurve = new DERTaggedObject(true, 10, AecCurve);
            var noAuthRequired = new DERTaggedObject(true, 503, AnoAuthRequired);
            var creationDateTime = new DERTaggedObject(true, 701, AcreationDateTime);
            var origin = new DERTaggedObject(true, 702, Aorigin);
            var rootOfTrust = new DERTaggedObject(true, 704, rootOfTrustSeq);
            var osVersion = new DERTaggedObject(true, 705, AosVersion);
            var osPatchLevel = new DERTaggedObject(true, 706, AosPatchLevel);
            var applicationID = new DERTaggedObject(true, 709, AapplicationID);
            var vendorPatchLevel = new DERTaggedObject(true, 718, AvendorPatchLevel);
            var bootPatchLevel = new DERTaggedObject(true, 719, AbootPatchlevel);

            //var AmoduleHash = new DEROctetString(ModuleInfoHelper.getModuleHash());
            //var moduleHash = new DERTaggedObject(true, 724 , AmoduleHash);

            //ASN1Encodable[] teeEnforcedEncodables;

            var arrayList = new ArrayList<>(Arrays.asList(purpose, algorithm, keySize, digest, ecCurve,
                        noAuthRequired, origin, rootOfTrust, osVersion, osPatchLevel, vendorPatchLevel,
                        bootPatchLevel/*, moduleHash*/));


            /*if( spoofV4 ) {
                arrayList.add(moduleHash);
            }*/

            /*
            teeEnforcedEncodables = new ASN1Encodable[]{purpose, algorithm, keySize, digest, ecCurve,
                        noAuthRequired, origin, rootOfTrust, osVersion, osPatchLevel, vendorPatchLevel,
                        bootPatchLevel, moduleHash, brand, device, product, manufacturer, model};
            */


            /*byte[] attestationChallenge = KeyboxImitationHooks.getAttestationChallenge();
            if( attestationChallenge != null ) {

            var AattestationChallenge = new DEROctetString(attestationChallenge);
            if( AattestationChallenge != null ) {
                var attestationChallengeObject = new DERTaggedObject(true, 708 , AattestationChallenge);
                arrayList.add(attestationChallengeObject);
            }*/

            byte[] paramBrand = params.brand;
            byte[] paramDevice = params.device;
            byte[] paramProduct = params.product;
            byte[] paramManufacturer = params.manufacturer;
            byte[] paramModel = params.model;
            //byte[] paramImei1 = params.imei1;
            //byte[] paramMeid = params.meid;
            //byte[] paramSerial = params.serial;


            if( spoofDevice ) {

                paramBrand = Build.BRAND.getBytes(StandardCharsets.UTF_8);
                paramDevice = Build.DEVICE.getBytes(StandardCharsets.UTF_8);
                paramProduct = Build.PRODUCT.getBytes(StandardCharsets.UTF_8);
                paramManufacturer = Build.MANUFACTURER.getBytes(StandardCharsets.UTF_8);
                paramModel = Build.MODEL.getBytes(StandardCharsets.UTF_8);

                var Abrand = new DEROctetString(paramBrand);
                var Adevice = new DEROctetString(paramDevice);
                var Aproduct = new DEROctetString(paramProduct);
                var Amanufacturer = new DEROctetString(paramManufacturer);
                var Amodel = new DEROctetString(paramModel);
                var brand = new DERTaggedObject(true, 710, Abrand);
                var device = new DERTaggedObject(true, 711, Adevice);
                var product = new DERTaggedObject(true, 712, Aproduct);
                var manufacturer = new DERTaggedObject(true, 716, Amanufacturer);
                var model = new DERTaggedObject(true, 717, Amodel);

                arrayList.addAll(List.of(brand, device, product, manufacturer, model));
                //arrayList.addAll(ModuleInfoHelper.getTelephonyInfos());

                Log.w(TAG, "spoof device identification params"); // + paramBrand + "," + paramDevice + "," + paramProduct + "," + paramManufacturer + "," + paramModel);

            } else if ( paramBrand != null )  {
                Log.w(TAG, "Copy device identification params");

                addDerObject(arrayList,710,paramBrand);
                addDerObject(arrayList,711,paramDevice);
                addDerObject(arrayList,712,paramProduct);
                addDerObject(arrayList,716,paramManufacturer);
                addDerObject(arrayList,717,paramModel);

                /*addDerObject(arrayList,713,paramSerial);
                addDerObject(arrayList,714,paramImei1);
                addDerObject(arrayList,715,paramMeid);*/
            }

            
             /*else {
                teeEnforcedEncodables = new ASN1Encodable[]{purpose, algorithm, keySize, digest, ecCurve,
                        noAuthRequired, origin, rootOfTrust, osVersion, osPatchLevel, vendorPatchLevel,
                        bootPatchLevel, moduleHash};
            }*/

            arrayList.sort(Comparator.comparingInt(ASN1TaggedObject::getTagNo));

            ASN1Encodable[] softwareEnforced = {applicationID, creationDateTime};

            //ASN1OctetString keyDescriptionOctetStr = getAsn1OctetString(teeEnforcedEncodables, softwareEnforced, params);
            ASN1OctetString keyDescriptionOctetStr = getAsn1OctetString(arrayList.toArray(new ASN1Encodable[]{}), softwareEnforced, params);

            return new Extension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"), false, keyDescriptionOctetStr);
        } catch (Throwable t) {
            Log.e(TAG, Log.getStackTraceString(t));
        }
        return null;
    }


    private static void addDerObject(ArrayList<DERTaggedObject> list, int tag, byte[] object) {
        if( object != null ) {
            var string = new DEROctetString(object);
            var der = new DERTaggedObject(true, tag, string);
            list.add(der);
        }
    }

    private static int getOsVersion() {
        String release = Build.VERSION.RELEASE;
        int major = 0, minor = 0, patch = 0;

        String[] parts = release.split("\\.");
        if (parts.length > 0) major = Integer.parseInt(parts[0]);
        if (parts.length > 1) minor = Integer.parseInt(parts[1]);
        if (parts.length > 2) patch = Integer.parseInt(parts[2]);

        return major * 10000 + minor * 100 + patch;
    }

    private static int getPatchLevel() {
        return convertPatchLevel(Build.VERSION.SECURITY_PATCH, false);
    }

    private static int getPatchLevelLong() {
        return convertPatchLevel(Build.VERSION.SECURITY_PATCH, true);
    }

    private static int convertPatchLevel(String patchLevel, boolean longFormat) {
        try {
            String[] parts = patchLevel.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            if (longFormat) {
                int day = Integer.parseInt(parts[2]);
                return year * 10000 + month * 100 + day;
            } else {
                return year * 100 + month;
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid patch level: " + patchLevel, e);
            return 202505;
        }
    }

    private static ASN1OctetString getAsn1OctetString(ASN1Encodable[] teeEnforcedEncodables, ASN1Encodable[] softwareEnforcedEncodables, KeyGenParameters params) throws IOException {
        ASN1Integer attestationVersion = new ASN1Integer(4);
        ASN1Enumerated attestationSecurityLevel = new ASN1Enumerated(1);
        ASN1Integer keymasterVersion = new ASN1Integer(41);
        ASN1Enumerated keymasterSecurityLevel = new ASN1Enumerated(1);
        ASN1OctetString attestationChallenge = new DEROctetString(params.attestationChallenge);
        ASN1OctetString uniqueId = new DEROctetString("".getBytes());
        ASN1Encodable softwareEnforced = new DERSequence(softwareEnforcedEncodables);
        ASN1Sequence teeEnforced = new DERSequence(teeEnforcedEncodables);

        ASN1Encodable[] keyDescriptionEncodables = {attestationVersion, attestationSecurityLevel, keymasterVersion,
                keymasterSecurityLevel, attestationChallenge, uniqueId, softwareEnforced, teeEnforced};

        ASN1Sequence keyDescriptionHackSeq = new DERSequence(keyDescriptionEncodables);

        return new DEROctetString(keyDescriptionHackSeq);
    }

    private static DEROctetString createApplicationId(int uid) throws Throwable {
        Context context = ActivityThread.currentApplication();
        if (context == null) {
            throw new IllegalStateException("createApplicationId: context not available from ActivityThread!");
        }

        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            throw new IllegalStateException("createApplicationId: PackageManager not found!");
        }

        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null || packages.length == 0) {
            throw new IllegalStateException("No packages found for UID: " + uid);
        }

        int size = packages.length;
        ASN1Encodable[] packageInfoAA = new ASN1Encodable[size];
        Set<Digest> signatures = new HashSet<>();
        MessageDigest dg = MessageDigest.getInstance("SHA-256");

        for (int i = 0; i < size; i++) {
            String name = packages[i];
            PackageInfo info = pm.getPackageInfo(name, PackageManager.GET_SIGNATURES);
            ASN1Encodable[] arr = new ASN1Encodable[2];
            arr[ATTESTATION_PACKAGE_INFO_PACKAGE_NAME_INDEX] =
                    new DEROctetString(name.getBytes(StandardCharsets.UTF_8));
            arr[ATTESTATION_PACKAGE_INFO_VERSION_INDEX] =
                    new ASN1Integer(info.getLongVersionCode());
            packageInfoAA[i] = new DERSequence(arr);

            for (Signature s : info.signatures) {
                signatures.add(new Digest(dg.digest(s.toByteArray())));
            }
        }

        ASN1Encodable[] signaturesAA = new ASN1Encodable[signatures.size()];
        int i = 0;
        for (Digest d : signatures) {
            signaturesAA[i++] = new DEROctetString(d.digest);
        }

        ASN1Encodable[] applicationIdAA = new ASN1Encodable[2];
        applicationIdAA[ATTESTATION_APPLICATION_ID_PACKAGE_INFOS_INDEX] =
                new DERSet(packageInfoAA);
        applicationIdAA[ATTESTATION_APPLICATION_ID_SIGNATURE_DIGESTS_INDEX] =
                new DERSet(signaturesAA);

        return new DEROctetString(new DERSequence(applicationIdAA).getEncoded());
    }

    public static class Digest {
        private final byte[] digest;

        public Digest(byte[] digest) {
            this.digest = digest != null ? digest.clone() : null;
        }

        public byte[] digest() {
            return digest != null ? digest.clone() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Digest)) return false;
            Digest d = (Digest) o;
            return Arrays.equals(this.digest, d.digest);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(digest);
        }

        @Override
        public String toString() {
            return "Digest[digest=" + Arrays.toString(digest) + "]";
        }
    }

    private static KeyPair buildECKeyPair(KeyGenParameters params) throws Exception {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
        ECGenParameterSpec spec = new ECGenParameterSpec(params.ecCurveName);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(spec);
        return kpg.generateKeyPair();
    }

    private static KeyPair buildRSAKeyPair(KeyGenParameters params) throws Exception {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
        RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(
                params.keySize, params.rsaPublicExponent);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(spec);
        return kpg.generateKeyPair();
    }

    private static byte[] sha256(byte [] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            return null;
        }
    }
    private static byte[] sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String byteArrayToString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
        
    public static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i + 1), 16));
        }
    
        return data;
    }

    private static void dlog(String msg) {
        if (SystemProperties.getBoolean("persist.baikal.kb_debug", false)) {
            Log.d(TAG, msg);
        }
    }

    private static void dlogbytearray(String prefix, byte [] array) {
        dlog(prefix + new String(array, StandardCharsets.UTF_8));
    }

    public static class KeyGenParameters {
        public int keySize;
        public int algorithm;
        public BigInteger certificateSerial;
        public Date certificateNotBefore;
        public Date certificateNotAfter;
        public X500Name certificateSubject;

        public BigInteger rsaPublicExponent;
        public int ecCurve;
        public String ecCurveName;

        public List<Integer> purpose = new ArrayList<>();
        public List<Integer> digest = new ArrayList<>();

        public byte[] attestationChallenge;
        public byte[] brand;
        public byte[] device;
        public byte[] product;
        public byte[] manufacturer;
        public byte[] model;
        //public byte[] imei1;
        //public byte[] imei2;
        //public byte[] meid;
        //public byte[] serial;


        //public byte[] uniqueId;

        public int securityLevel;

        public KeyGenParameters(KeyParameter[] params) {
            for (var kp : params) {
                var p = kp.value;
                switch (kp.tag) {
                    case Tag.KEY_SIZE:
                        dlog("KeyParameter: Tag.KEY_SIZE");
                        keySize = p.getInteger();
                        break;
                    case Tag.ALGORITHM:
                        dlog("KeyParameter: Tag.ALGORITHM");
                        algorithm = p.getAlgorithm();
                        break;
                    case Tag.CERTIFICATE_SERIAL:
                        certificateSerial = new BigInteger(p.getBlob());
                        dlog("KeyParameter: Tag.CERTIFICATE_SERIAL:" + certificateSerial);
                        break;
                    case Tag.CERTIFICATE_NOT_BEFORE:
                        certificateNotBefore = new Date(p.getDateTime());
                        dlog("KeyParameter: Tag.CERTIFICATE_NOT_BEFORE:" + certificateNotBefore);
                        break;
                    case Tag.CERTIFICATE_NOT_AFTER:
                        certificateNotAfter = new Date(p.getDateTime());
                        dlog("KeyParameter: Tag.CERTIFICATE_NOT_AFTER:" + certificateNotAfter);
                        break;
                    case Tag.CERTIFICATE_SUBJECT:
                        dlog("KeyParameter: Tag.CERTIFICATE_SUBJECT");
                        certificateSubject = new X500Name(new X500Principal(p.getBlob()).getName());
                        break;
                    case Tag.RSA_PUBLIC_EXPONENT:
                        dlog("KeyParameter: Tag.RSA_PUBLIC_EXPONENT");
                        rsaPublicExponent = new BigInteger(p.getBlob());
                        break;
                    case Tag.EC_CURVE:
                        dlog("KeyParameter: Tag.EC_CURVE");
                        ecCurve = p.getEcCurve();
                        ecCurveName = getEcCurveName(ecCurve);
                        break;
                    case Tag.PURPOSE:
                        dlog("KeyParameter: Tag.PURPOSE");
                        purpose.add(p.getKeyPurpose());
                        break;
                    case Tag.DIGEST:
                        dlog("KeyParameter: Tag.DIGEST");
                        digest.add(p.getDigest());
                        break;
                    case Tag.ATTESTATION_CHALLENGE:
                        dlog("KeyParameter: Tag.ATTESTATION_CHALLENGE");
                        attestationChallenge = p.getBlob();
                        break;
                    case Tag.ATTESTATION_ID_BRAND:
                        brand = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_BRAND:", brand);
                        break;
                    case Tag.ATTESTATION_ID_DEVICE:
                        device = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_DEVICE:", device);
                        break;
                    case Tag.ATTESTATION_ID_PRODUCT:
                        product = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_PRODUCT:", product);
                        break;
                    case Tag.ATTESTATION_ID_MANUFACTURER:
                        manufacturer = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_MANUFACTURER:", manufacturer);
                        break;
                    case Tag.ATTESTATION_ID_MODEL:
                        model = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_MODEL:", model);
                        break;
                    case Tag.HARDWARE_TYPE:
                        securityLevel = p.getSecurityLevel();
                        break;
/*                    case Tag.INCLUDE_UNIQUE_ID:
                        uniqueId = p.getBlob();

                    case Tag.ATTESTATION_ID_IMEI:
                        imei1 = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_IMEI:", imei1);
                        break;
                    //case Tag.ATTESTATION_ID_SECOND_IMEI:
                    //    imei2 = p.getBlob();
                    //    break;
                    case Tag.ATTESTATION_ID_MEID:
                        meid = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_MEID:", meid);
                        break;

                    case Tag.ATTESTATION_ID_SERIAL:
                        serial = p.getBlob();
                        dlogbytearray("KeyParameter: Tag.ATTESTATION_ID_SERIAL:", meid);
                        break;

                    case Tag.NO_AUTH_REQUIRED:
                        dlog("KeyParameter: Tag.NO_AUTH_REQUIRED");
                        break;*/

                    default:
                        Log.e(TAG, "KeyParameter: unknown tag:" + kp.tag);
                        break;
                }
            }
        }

        private static String getEcCurveName(int curve) {
            String res;
            switch (curve) {
                case EcCurve.CURVE_25519:
                    res = "CURVE_25519";
                    break;
                case EcCurve.P_224:
                    res = "secp224r1";
                    break;
                case EcCurve.P_256:
                    res = "secp256r1";
                    break;
                case EcCurve.P_384:
                    res = "secp384r1";
                    break;
                case EcCurve.P_521:
                    res = "secp521r1";
                    break;
                default:
                    throw new IllegalArgumentException("unknown curve");
            }
            return res;
        }
    }
}
