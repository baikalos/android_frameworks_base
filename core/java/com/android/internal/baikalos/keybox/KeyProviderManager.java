/*
 * SPDX-FileCopyrightText: 2024 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.baikalos.keybox;

import android.app.ActivityThread;
import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.text.TextUtils;

import com.android.internal.R;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manager class for handling keybox providers.
 * @hide
 */
public final class KeyProviderManager {
    private static final String TAG = "KeyboxProviderManager";
    private static final IKeyboxProvider PROVIDER = new DefaultKeyboxProvider();

    public static IKeyboxProvider getProvider() {
        return PROVIDER;
    }

    public static boolean isKeyboxAvailable() {
        return PROVIDER.hasKeybox();
    }

    private static void dlog(String msg) {
        if (SystemProperties.getBoolean("persist.sys.keybox_debug", false)) {
            Log.d(TAG, msg);
        }
    }

    private static class DefaultKeyboxProvider implements IKeyboxProvider {
        private final Map<String, String> keyboxData = new HashMap<>();

        private DefaultKeyboxProvider() {
            try {
                Context context = ActivityThread.currentApplication().getApplicationContext();
                
                if (context == null) return;

                String json = Settings.System.getString(context.getContentResolver(), "custom_keybox_data");

                if (TextUtils.isEmpty(json)) {
                    dlog("No keybox data in Settings.System");
                    //json = "{\"EC.PRIV\": \"MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgntUEup/NdXx9iKtuwHKx177YQxIvhaPkEQpAOPjHxxChRANCAARZAoMkSNgXn6MRY0jM3t/7FEOBOVWXXEgVXO21wK394TOG3aVq4Ti6LGVTJG3O2nEZbXSCWOIcA+dyxQ3hFZHB\",\"EC.CERT_1\": \"MIICJDCCAaugAwIBAgIKAZZYMGAohlAYITAKBggqhkjOPQQDAjApMRkwFwYDVQQFExA1NDRjMTRlMTJkYzgyMGYzMQwwCgYDVQQMDANURUUwHhcNMTgwNDE4MjIzOTM0WhcNMjgwNDE1MjIzOTM0WjApMRkwFwYDVQQFExA0Nzc3YzQwZDJhMWQyNjVmMQwwCgYDVQQMDANURUUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARZAoMkSNgXn6MRY0jM3t/7FEOBOVWXXEgVXO21wK394TOG3aVq4Ti6LGVTJG3O2nEZbXSCWOIcA+dyxQ3hFZHBo4G6MIG3MB0GA1UdDgQWBBSKhqG8P0H4JGpd7rJUc9aXWMn4RDAfBgNVHSMEGDAWgBTSfnB7oefBbLt1RqEuwdiFUGy1bzAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDBUBgNVHR8ETTBLMEmgR6BFhkNodHRwczovL2FuZHJvaWQuZ29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3JsLzAxOTY1ODMwNjAyODg2NTAxODIxMAoGCCqGSM49BAMCA2cAMGQCMGpnxr5EMyG9NdyEDnfbug1sLoWwceLZjRHPFTHfAioaW9/VTdZiv7Y0LeTi71EJlgIwF+/lhjz64sYvRQ7dcHHtkwuJ1sq15NGIsArG9azYUxqKNQ6ZxukLWWLbPiQaKY6A\",\"EC.CERT_2\": \"MIID0TCCAbmgAwIBAgIKA4gmZ2BliZaFkzANBgkqhkiG9w0BAQsFADAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTE4MDQxODIyMjE0MVoXDTI4MDQxNTIyMjE0MVowKTEZMBcGA1UEBRMQNTQ0YzE0ZTEyZGM4MjBmMzEMMAoGA1UEDAwDVEVFMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE/WvRRlHZDCrhcd0319b9SehNankl3p/k79NX1ZmZHD50yZ7lCb0lENs0FGn4SL+l+WrKSKTbCfUO3wGFhFg1QTiM62IRGBhM1/tqC8fP50R9gqQ3d6ajJEX9bPLp7AE8o4G2MIGzMB0GA1UdDgQWBBTSfnB7oefBbLt1RqEuwdiFUGy1bzAfBgNVHSMEGDAWgBQ2YeEAfIgFCVGLRGxH/xpMyepPEjAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDBQBgNVHR8ESTBHMEWgQ6BBhj9odHRwczovL2FuZHJvaWQuZ29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3JsL0U4RkExOTYzMTREMkZBMTgwDQYJKoZIhvcNAQELBQADggIBAJnYLaTbhN2Ly91kxLj/vljTcqlCV9cgVYteJLJPWLNdWFZu167qppnmFDKn/VDV24v3XbJb7jqzMBrE1V+DN72lFfREHYDyJM8FITF0CbIuOTwTSLKRV26Ogm8esFrCjPoWC6OLTsHLSGfv5KQO1M0KpjqkOywBxgCCHCbI+BotefY2Kr/JTJIIEOq5ra0QcuAZTQULMNSdYzMQZawnGFH4XeINKiScoISch939//APkBbNXHmdpM7u5t2B3dnCQekXtX3qMbSAGhV6OSoM4bRY23tiLToYzXQyysE8sqDVfmaLWXduh+xsodvc0EDsE8fS4MIS5VXWVBelS+9j0AjAMCNp24G8bMG2EdM7xxK+UciAUwgLwFFxVDFpCzdPyhKIdpTPK6UVOInS9qklTXpwIvGDIwsowAS005s+D5rGo1qZD0mwGXZRKtqF64nTqpmOXWg7baRufUjMerroggY4XgZ14vtLOcsiQevjL7nNH4G8SekWL4uIRH7AxBKaIqPR69XlEFwZdgn7e0ED7t8MLJ/f1/todJEaQOHk2uysbpr0SIZdtWqS4w7IQRpGo4RaiKl7rYHVvXiuBPqt2S8MeNyPi225Yq/gWZSLtPcg2Dl45aFzqw72egCt5b20ijNGnUIUQP7snMHKL+4JoGl86LC7xtbpdLKvcJO9Vv3u\",\"EC.CERT_3\": \"MIIFYDCCA0igAwIBAgIJAOj6GWMU0voYMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTYwNTI2MTYyODUyWhcNMjYwNTI0MTYyODUyWjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAaOBpjCBozAdBgNVHQ4EFgQUNmHhAHyIBQlRi0RsR/8aTMnqTxIwHwYDVR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAYYwQAYDVR0fBDkwNzA1oDOgMYYvaHR0cHM6Ly9hbmRyb2lkLmdvb2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC8wDQYJKoZIhvcNAQELBQADggIBACDIw41L3KlXG0aMiS//cqrG+EShHUGo8HNsw30W1kJtjn6UBwRM6jnmiwfBPb8VA91chb2vssAtX2zbTvqBJ9+LBPGCdw/E53Rbf86qhxKaiAHOjpvAy5Y3m00mqC0w/Zwvju1twb4vhLaJ5NkUJYsUS7rmJKHHBnETLi8GFqiEsqTWpG/6ibYCv7rYDBJDcR9W62BW9jfIoBQcxUCUJouMPH25lLNcDc1ssqvC2v7iUgI9LeoM1sNovqPmQUiG9rHli1vXxzCyaMTjwftkJLkf6724DFhuKug2jITV0QkXvaJWF4nUaHOTNA4uJU9WDvZLI1j83A+/xnAJUucIv/zGJ1AMH2boHqF8CY16LpsYgBt6tKxxWH00XcyDCdW2KlBCeqbQPcsFmWyWugxdcekhYsAWyoSf818NUsZdBWBaR/OukXrNLfkQ79IyZohZbvabO/X+MVT3rriAoKc8oE2Uws6DF+60PV7/WIPjNvXySdqspImSN78mflxDqwLqRBYkA3I75qppLGG9rp7UCdRjxMl8ZDBld+7yvHVgt1cVzJx9xnyGCC23UaicMDSXYrB4I4WHXPGjxhZuCuPBLTdOLU8YRvMYdEvYebWHMpvwGCF6bAx3JBpIeOQ1wDB5y0USicV3YgYGmi+NZfhA4URSh77Yd6uuJOJENRaNVTzk\"}";
                    json = "{\"EC.PRIV\": \"MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQguGc8DZjeMeVhTHmFWNLBoTn1bY/VOdXQRGrK+cbJppuhRANCAAS8VQ6n7Ocl4YLE+XzoQp5nMmE2jp9tqov01ri5OXkpj58gOWefmG+XP3kIFJ09t87ZjIu5JQdaMalP0d4qVcJa\",  \"EC.CERT_1\": \"MIIB9DCCAXmgAwIBAgIQdgHZmlo1k9XFMNXX260ovzAKBggqhkjOPQQDAjA5MQwwCgYDVQQMDANURUUxKTAnBgNVBAUTIDcwOTEyZGY0NjEwNGZhYWU5NDc4NjRlNTgwNGYxZjhkMB4XDTIwMDkyODIwMjc1M1oXDTMwMDkyNjIwMjc1M1owOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyA5MTg0NjRkZjdkNzUxNmE3OWQxOTFhMjE5Nzk0MDVhMDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABLxVDqfs5yXhgsT5fOhCnmcyYTaOn22qi/TWuLk5eSmPnyA5Z5+Yb5c/eQgUnT23ztmMi7klB1oxqU/R3ipVwlqjYzBhMB0GA1UdDgQWBBToarPDW7uz3jhVlvqQaGnTAJ/fsjAfBgNVHSMEGDAWgBQSxACLx3agtQtKLkjHgFMYZu9QSTAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDAKBggqhkjOPQQDAgNpADBmAjEA+tpcWtuwzCZ0do3uGEPrFzNU4HNEU1arBJyL5OasgIAh7ZmNGCUwmSw3v6DVUkhJAjEAnfqtDsqjE2G0C8iSlt+DkdD7/j8/nCvcBQqjQJPG2+ZOKgYx+n59zLM/DOlrELG3\",  \"EC.CERT_2\": \"MIIDkzCCAXugAwIBAgIQNTAX5z3CBac6nD3hQiMDcDANBgkqhkiG9w0BAQsFADAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTIwMDkyODIwMjUwMloXDTMwMDkyNjIwMjUwMlowOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyA3MDkxMmRmNDYxMDRmYWFlOTQ3ODY0ZTU4MDRmMWY4ZDB2MBAGByqGSM49AgEGBSuBBAAiA2IABA/7xZFlFtTjdy2B3p7E+FsrBjyhBSqY4a9FywawXMJRSja3HAK36ruzJjWlEkD+D0vqHI2joY39FHmWoZWwm2cq9gOleFGYOSCpMr4ib7xtq/6nefvKTP5rutxudF97t6NjMGEwHQYDVR0OBBYEFBLEAIvHdqC1C0ouSMeAUxhm71BJMB8GA1UdIwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQAaMONDQxJz3PRn9gHQW5KP+TIoBPJZyGa1QFuEBcMDTtIxBxEh5Pj3ivPBc76PrdYu5U47Ve5YYCPsTpUTj7dOxbzGSZjfjvHFfNwy24g1Lah2iAdQRVErhWKBlpnQhBnnRrrNmTTmzhl8NvSExqAPP746dqwm1kQ7YesC5yoEAHpxamhlZpIKAjSxSZeHWace2qV00M8qWd/7lIpqttJjFFrhCjzR0dtroIIpC5EtmqIWdLeg6yZjJkX+Cjv4F8mRfBtwuNuxFsfALQ3D5l8WKw3iwPebmCy1kEby8Eoq88FxzXQp/XgAaljlrKXyuxptrc1noRuob4g42Oh6wetueYRSCtO6Bkym0UMnld/kG77aeiHOMVVb86wrhNuAGir1vgDGOBsclITVyuu9ka0YVQjjDm3phTpdO8JV16gbei2Phn+FfRV1MSDsZo/wu0i2KVzgs27bfJocMHXv+GzvwfefYgMJ/rYqBg27lpsWzmFEPv2cyhA5PwwbG8ceswa3RZE/2eS9o7STkz93jr/KsKLcMBY6cX2Cq4CBJByKFJtVANOVj+neFNxc2sQgeTT33yYNKbe4b5bm7Ki1FbrhFVckpzUGDnKsgL+AxvALWOoryDGwNbJiW8PRiD3HHByiMvSEQ7e7BSc2KjbsaWbCfYZAMZJEhEscP1l8lcUVuA==\",  \"EC.CERT_3\": \"MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAzNzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1UdIwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnuXKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83Uh6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cnoL/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2okQBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vAD32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAImMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoWFua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09ojm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUBZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCHex0SdDrx+tWUDqG8At2JHA==\",  \"RSA.PRIV\": \"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\",  \"RSA.CERT_1\": \"MIIE3zCCAsegAwIBAgIQeYLYsTNMOirnr9i0PCPfQjANBgkqhkiG9w0BAQsFADA5MQwwCgYDVQQMDANURUUxKTAnBgNVBAUTIDcwOTEyZGY0NjEwNGZhYWU5NDc4NjRlNTgwNGYxZjhkMB4XDTIwMDkyODIwMjc1M1oXDTMwMDkyNjIwMjc1M1owOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyA5MTg0NjRkZjdkNzUxNmE3OWQxOTFhMjE5Nzk0MDVhMDCCAaIwDQYJKoZIhvcNAQEBBQADggGPADCCAYoCggGBAMcTodGT3GohteMpbHnDQJ3ikAH93d2Wl9jB2LAOajnzS+uljcxZR1acKuyF/Xngbh0GS8W4x4qWxaVeQRlprcB4bVxis+AElzirKNHwDHI0FUmD4Ec+xfWA5O7OOOIDCE8DNP2Jc6XOKB/ifmf+NRCVVh5jMx9Uz0RjNrZinSi+KipDrVnmgryKDsClPkzClPjbY3xNi+ALF5LJisJr2AEWy0fCkxxbVOKXJchE9JUCg1NP9U9cgxdzFeAuso3UxE5CliqgWZxU/tulkJ6JKlcnLOMsFl/YMcnVrCqA0r7QNuvInov8xumkUMJrbNrWK9j7TT6QwO4TZp6htA2r4QCXQ2AWfaPAMXQSoaladChXbWMqDe8znA3ci5IALHmAp1dGWytIQlp68iMy196srcJwHkYKqpmk7JxKJXxKfRyb6ArBgkhJ1O6C2DE4RNmkoGpE/3zFQyLSu0uGgH2ajforHzoHpbtrQ8rmwX0m/eEk9E3ElIxkGGAW42GQbzzbfQIDAQABo2MwYTAdBgNVHQ4EFgQUFPp3HUemVCxktM0qQNWJItFFre8wHwYDVR0jBBgwFoAUslbj5wSIn69LBT/K1QAakgbLDnQwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAgQwDQYJKoZIhvcNAQELBQADggIBAD8CGjhi1sPFezn/7+5ZBz7M/62JYyEbRS0Mx06zzHUNVoyxVFYbkDWwd5+VlIZ7pNx/lA07cO2DwfmL+wRwsuqtmCp2WpM4EkB+bWR7yyKUDErlyE+DtTeXZDmUlP7E+b7ts2Fp8PI3A0w5SByngn9Xf3EmJrXf3TmtYQFurgJ16L6BFAEd29fhIC3PXvwpbPeRTQ/TF6p0U011M0pAb3K6ZVzrqrrzj1TptrL59Flxo2CS5SbvFQK3vianJc7/Div+fMVgTRzQZIyOqMHKr0LPnLkWjoWXHy5weFjs8EmRa/xgwCTzhpjUF2GXRSphKQELQ+qYENFK+8YmSxOkIecH1lLpcMkwe8fYV2Q8WXLneuCH57sF224w2y4LmJbCTwND3QbY5ayTrRgNx2x6J0/MNtucigTWW91daUh4owr2IYcEcBA28DfrET7DU9HAxcPtjj37If71P9sLaAWTgNO1hWc968XPEFQsPhDAh6aVjo4/sBChs5zz91lmlvhBPaazB15i0AtRlEJlqa7Zw7ZPtXLcQHQmbDJS0VA0jbKYRXEj902GtHyJNVRQgGVPFW2EGbf3fPHgb2YEJwftPpNh6sYnXuhfXxLSw8x+N8zsOkXA/vkehVOXX1hx+wriJqv9E/fyQVy3xWkmxOWzrmMIpsSWtP9Z2TpXtOGOGS0d\",  \"RSA.CERT_2\": \"MIIFQjCCAyqgAwIBAgIRAIedW9yH79+GM2LaZKGXyj8wDQYJKoZIhvcNAQELBQAwGzEZMBcGA1UEBRMQZjkyMDA5ZTg1M2I2YjA0NTAeFw0yMDA5MjgyMDI0NDVaFw0zMDA5MjYyMDI0NDVaMDkxDDAKBgNVBAwMA1RFRTEpMCcGA1UEBRMgNzA5MTJkZjQ2MTA0ZmFhZTk0Nzg2NGU1ODA0ZjFmOGQwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDRtUsBMLH/aMOMZsSktzoEcJk40XZ6eYfkVHYwU+Yo3xQN6mVM8CzV0m41v6NNANT3MBXnLyTEcCJGUeiNd4DEOOm/SNKlzHVNZLLHtK9tgVYk6j5psJ6RaVuRKWF9a4KWiPGNvBKQ7zr8c9ITSZHFi7OzB+bS02XOCHqiSjND4HEZbyEpRn27L7mb/i8ZscrSANM/nVllUMXlJLAh7NVjtYH3gNfGFXqJ/Wp0lWyOTaWOVwoz7bO7iFCPFmPrREvpUvVkU28CvYdv+56i/dn0bSbOdhjm8wceK6fqShxxdyA5Tt/qriLw7ydn062HRHwQhQVzE+9fl/iNrBDTTdAdUta407YUG5JZ5KLI/HZNNsSGmZpyU9B/6fIiHIqKQAOLkTzCnlhlkVK6KQ102mKUuB432hQnzZcRR4b0qOpizTci75YeAQjGWWIYv4Hxe0OPkBqIroV2+ydAbhsLKVLMlAO2WS/sVmbJ4n8qBDZq3OZezckx0/mCzwcEKlgtaYNBQxPz3zilhUwzMH2Dg8h0YWsqTzmUvZhO9tdvgasNypWX+J/U3+iCSiCePlSuzOF+I5cnQ0KAsBSVj5IiebAHJjXktH+pw+s6adpOW7t3zugDNLqLbrwt1LC33PtCyWJFfVKaYrcmHWkfFOH910ZztE0bkv5eiCuOyMvdcimRgwIDAQABo2MwYTAdBgNVHQ4EFgQUslbj5wSIn69LBT/K1QAakgbLDnQwHwYDVR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAgQwDQYJKoZIhvcNAQELBQADggIBAD8NygWaOGAiZe8kdb3cgdUyxv+NYHOhxbj2L5TRGJavDBm6FWfalcXN9tDqWeuFIsbGTXurUBaKkWwkR9u8OyaJGwGRVp0o8dT4ceDTfrlDrZSxulJYBxEWtnbOfcQzz0yfAIng34u2P1d9bPI6lJhBvIb6Dc0aSy94UCDXCCuf+O/CJOk1oyieg+4HCkG2EAMfxR0zSwSfvLu35e5ketB/4DI/H634c+aRoV+yAM06jSSINj13kBYwFOnOA+p/wP6hb3gb2rUKt4saJm7gI8ZlA0zsyK11kn8SLEUGLL06QDNrmFi18F209XgOknP4ZoP+2UuJ4+jVTmssVvqg/Nbv0QbBKztqOr0vSMLGFh4LIXdU3Q+gkWxOHjYvU7aSoYEZw7ID4k4p5VeZVq6UBzwyadiZm/ADQJp4EXQ/QSNCh+BGS90g+RJb45dMM8AKD0A59sLcKBFqhADJZw8ib1dx/lrk9d3v9eiiVsm5swidPsxDWi3wtk3mC38pfGJIotFcekOLU9MZNrQQYvmuzrmKe1ElTUAiQwGjIG9gLJbwzhIQInxkGenrlf0Lir6yGrmFWzICxw1/rF50nl5zirmCbkqw/LavgJdCGlVEweWG9R5mwwIh/cNfW4gFfnoywQbbUwMnEAJKyGzGQlIXUDQ95v/6hHBERmLYtjdE4oT9\",  \"RSA.CERT_3\": \"MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAzNzU4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1UdIwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQBOMaBc8oumXb2voc7XCWnuXKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOyXAmeE6SRo83Uh6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cnoL/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2okQBUIYSY6bjEL4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vAD32KdNQ+c3N+vl2OTsUVMC1GiWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAImMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1UtzmLoBIuUFsVXJMTz+Jucth+IqoWFua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1grw3ZLl4CiOe/A91oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguBw09ojm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUBZutL8VuFkERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCHex0SdDrx+tWUDqG8At2JHA==\"}";
                    //return;
                }

                JSONObject keyboxJson = new JSONObject(json);
                Iterator<String> keys = keyboxJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    keyboxData.put(key, keyboxJson.getString(key));
                }

                if (!hasKeybox()) {
                    dlog("Incomplete keybox data loaded");
                    logMissingKeys();
                } else {
                    logLoadedKeys();
                }

            } catch (Exception e) {
                dlog("Error retrieving keybox from settings: " + e.getMessage());
            }
        }

        private void logLoadedKeys() {
            dlog("Successfully loaded keybox data:");
            for (String key : Arrays.asList(
                    "EC.PRIV", "EC.CERT_1", "EC.CERT_2", "EC.CERT_3",
                    "RSA.PRIV", "RSA.CERT_1", "RSA.CERT_2", "RSA.CERT_3")) {
                String value = keyboxData.get(key);
                if (value != null) {
                    dlog(key + ": " + value);
                }
            }
        }

        private void logMissingKeys() {
            for (String key : Arrays.asList(
                    "EC.PRIV", "EC.CERT_1", "EC.CERT_2", "EC.CERT_3",
                    "RSA.PRIV", "RSA.CERT_1", "RSA.CERT_2", "RSA.CERT_3")) {
                if (!keyboxData.containsKey(key)) {
                    dlog("Missing key: " + key);
                }
            }
        }

        @Override
        public boolean hasKeybox() {
            return Arrays.asList("EC.PRIV", "EC.CERT_1", "EC.CERT_2" /*, "EC.CERT_3" ,
                    "RSA.PRIV", "RSA.CERT_1", "RSA.CERT_2" , "RSA.CERT_3" */)
                    .stream()
                    .allMatch(keyboxData::containsKey);
        }

        @Override
        public String getEcPrivateKey() {
            return keyboxData.get("EC.PRIV");
        }

        @Override
        public String getRsaPrivateKey() {
            return keyboxData.get("RSA.PRIV");
        }

        @Override
        public String[] getEcCertificateChain() {
            return getCertificateChain("EC");
        }

        @Override
        public String[] getRsaCertificateChain() {
            return getCertificateChain("RSA");
        }

        private String[] getCertificateChain(String prefix) {
            if( !keyboxData.containsKey(prefix + ".CERT_3") ) {
                return new String[]{
                    keyboxData.get(prefix + ".CERT_1"),
                    keyboxData.get(prefix + ".CERT_2")
                };
            }
            return new String[]{
                    keyboxData.get(prefix + ".CERT_1"),
                    keyboxData.get(prefix + ".CERT_2"),
                    keyboxData.get(prefix + ".CERT_3")
            };
        }
    }
}
