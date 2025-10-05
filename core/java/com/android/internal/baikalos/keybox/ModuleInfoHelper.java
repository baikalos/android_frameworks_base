package com.android.internal.baikalos.keybox;

import android.content.Context;
import android.os.Build;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1Integer;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ModuleInfoHelper {

    private static final String TAG = "KeyboxModuleInfoHelper";

    private static List<Pair<String, Long>> apexInfos;
    private static byte[] moduleHash;
    private static List<DERTaggedObject> telephonyInfos;

    public static List<Pair<String, Long>> getApexInfos() {
        if (apexInfos == null) {
            apexInfos = new ArrayList<>();
            try {
                IPackageManager pm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
                List<PackageInfo> packages;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packages = pm.getInstalledPackages((long) PackageManager.MATCH_APEX, 0).getList();
                } else {
                    packages = pm.getInstalledPackages(PackageManager.MATCH_APEX, 0).getList();
                }
                for (PackageInfo pkg : packages) {
                    apexInfos.add(new Pair<>(pkg.packageName, pkg.getLongVersionCode()));
                }
                Collections.sort(apexInfos, Comparator.comparing(Pair::getFirst));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return apexInfos;
    }

    public static byte[] getModuleHash() {
        if (moduleHash == null) {
            try {
                List<ASN1Encodable> list = new ArrayList<>();
                for (Pair<String, Long> info : getApexInfos()) {
                    list.add(new DEROctetString(info.getFirst().getBytes()));
                    list.add(new ASN1Integer(info.getSecond()));
                }
                ASN1Encodable[] encodables = list.toArray(new ASN1Encodable[0]);
                DERSequence sequence = new DERSequence(encodables);
                byte[] encoded = sequence.getEncoded();

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(encoded);
                moduleHash = digest.digest();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return moduleHash;
    }

    public static List<DERTaggedObject> getTelephonyInfos() {
        if (telephonyInfos == null) {
            telephonyInfos = new ArrayList<>();
            try {
                TelephonyManager telephonyService = null;
                telephonyService =
                        (TelephonyManager) android.app.AppGlobals.getInitialApplication()
                                .getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyService == null) {
                    Log.e(TAG, "Telephony service unavailable.");
                    return telephonyInfos;
                }

                final String imei = telephonyService.getImei(0);
                final String meid = telephonyService.getMeid(0);

                if( imei != null ) {
                    telephonyInfos.add(new DERTaggedObject(true, 714,
                            new DEROctetString(imei.getBytes(StandardCharsets.UTF_8))));
                }
                if( meid != null ) {
                    telephonyInfos.add(new DERTaggedObject(true, 715,
                            new DEROctetString(meid.getBytes(StandardCharsets.UTF_8))));
                }

                /*try {
                    final String imei2 = telephonyService.getImei(1);
                    if( imei2 != null ) {
                        telephonyInfos.add(new DERTaggedObject(true, 723,
                                new DEROctetString(imei2.getBytes(StandardCharsets.UTF_8))));
                    }
                } catch(Exception ie) {
                }*/

                telephonyInfos.add(new DERTaggedObject(true, 713,
                        new DEROctetString(getSystemPropertyBytes("ro.baikalos.serialno"))));
            } catch (Exception e) {
                Log.e(TAG, "Can't get device specific prperties.");
                e.printStackTrace();
            }
        }
        return telephonyInfos;
    }

    private static byte[] getSystemPropertyBytes(String key) {
        String value = SystemProperties.get(key, null);
        return value != null ? value.getBytes() : null;
    }

    public static class Pair<F, S> {
        private final F first;
        private final S second;
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
        public F getFirst() { return first; }
        public S getSecond() { return second; }
    }
}
