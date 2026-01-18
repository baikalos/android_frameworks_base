/*
 * SPDX-FileCopyrightText: 2024 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.util.crdroid;

import android.app.ActivityThread;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager class for handling keybox providers.
 * @hide
 */
public final class KeyProviderManager {
    private static final String TAG = "KeyProviderManager";

    private static final String AOSP_KEYBOX = "PD9renkgaXJlZnZiYT0iMS4wIj8+CjxOYXFlYnZxTmdncmZnbmd2YmE+CiAgICA8QWh6b3JlQnNYcmxvYmtyZj4xPC9BaHpvcmVCc1hybG9ia3JmPgogICAgPFhybG9iayBRcml2cHJWUT0iZmoiPgogICAgICAgIDxYcmwgbnl0YmV2Z3V6PSJycHFmbiI+CiAgICAgICAgICAgIDxDZXZpbmdyWHJsIHNiZXpuZz0iY3J6Ij4KICAgICAgICAgICAgICAgIC0tLS0tT1JUVkEgUlAgQ0VWSU5HUiBYUkwtLS0tLQogICAgICAgICAgICAgICAgWlVwUE5EUlJWUFV0dXhaZFNFelJKcDgyQnlROFNaYW5lc3gxOUZzUDM5cHJHSjI4RGhJUmJOYlRQUGRURlo0OQogICAgICAgICAgICAgICAgTmpSVWJIRFFEdE5SNjU1NStSV3dKbm1ZWGNTWnZMb1pwWDJETWNCUGRLWnpSLzZmbC90dVcwanVxV3FYWGk2eQogICAgICAgICAgICAgICAgaEgxL01nR3RNRU96QW9rR2c2UHdjYVNMQ2dmK1JuNERTTj09CiAgICAgICAgICAgICAgICAtLS0tLVJBUSBSUCBDRVZJTkdSIFhSTC0tLS0tCiAgICAgICAgICAgIDwvQ2V2aW5nclhybD4KICAgICAgICAgICAgPFByZWd2c3ZwbmdyUHVudmE+CiAgICAgICAgICAgICAgICA8QWh6b3JlQnNQcmVndnN2cG5ncmY+MjwvQWh6b3JlQnNQcmVndnN2cG5ncmY+CiAgICAgICAgICAgICAgICA8UHJlZ3ZzdnBuZ3Igc2Jlem5nPSJjcnoiPgogICAgICAgICAgICAgICAgICAgIC0tLS0tT1JUVkEgUFJFR1ZTVlBOR1ItLS0tLQogICAgICAgICAgICAgICAgICAgIFpWVlByUVBQTnU2dE5qVk9OdFZQUk5SalB0TFZYYk1WbXcwUk5qVmp0TXRrUG1OV090QUlPTkxHTnlJR1pFWmoKICAgICAgICAgICAgICAgICAgICBSRExRSUREVlFOY1FMSmtjTXo5bG96eXVaRUxqU05MUUlERFVRTjFBbzNJaHFUU2Nvdk9KbkpJM1pFSGpSakxRCiAgICAgICAgICAgICAgICAgICAgSUREWFFOa1VvMjlhb1RIZlZSeWhMbDRrUlFOQk90QUlPTmZaTzBTaE1VV2luSkRrWm1Oa090QUlPTlpaWHhTaAogICAgICAgICAgICAgICAgICAgIE1VV2luSkR0RjJJNXAzRWlwekh0SDI5enFVcXVwekh0REtFME1LQTBMS0VjbzI0dEh6OWlxUU5yU2owa0F3TmsKICAgICAgICAgICAgICAgICAgICBaR1JqWlFEMlpReW5TajBsQXdOa1pRdGpaUUQyWlF5blpWVFZaRGZqUERMUUlERFRSaldJSG1SR1pPUlROMUhSCiAgICAgICAgICAgICAgICAgICAgUE5qWEQyU2ZuSk1pcHo1Y0xHUklaT1pUTjFIUlB0alpFMjlpTTJreVlQT1dvelpoWkVOalF0TFFJRERZUU5xTwogICAgICAgICAgICAgICAgICAgIG96RWxvMnl4WkdmakJETFFJRERRUVFXT296RWxvMnl4VlJneXJLQTBvM1d5VlNBaU1hRTNMS1d5VlJTMHFUSW0KICAgICAgICAgICAgICAgICAgICBxVFMwbko5aFZSeWhxVElsb0pJeG5KUzBNR09NWk9aVE9sZFRGWjQ5TnRSVFBQZFRGWjQ5TmpSVU4wVk5PQmhyCiAgICAgICAgICAgICAgICAgICAgcnN1UEwxemZsbGRFR1Z6VG1VUGd4VG5HdGR5bVd1QytlWmk0VkZxWlZLRktGdmUrY295QXMyb0g0VEhETXdKOAogICAgICAgICAgICAgICAgICAgIEg3cnRiNk1rSlE3b0N1VGhST0Z3TXdPeFpPMFROMUhxUXRESk9PRC8vWG1KVGVSNmFiUnRoQUh5VVpJeWhrNkUKICAgICAgICAgICAgICAgICAgICBkR05zT3RBSVVGWlJUUU5KdE9HVmVyeTNHUktRYjg4QVN1UXhySFo2Vkliam1tTkZPdEFJVUVaT05zOFJQUU5UCiAgICAgICAgICAgICAgICAgICAgTkRVL050Uk5aTjRUTjFIcVFqUk8vakRSTmpWUHVRTlhPdHRkdXh3QkNERFFOdEFWTlFPU052T1l2Y2c3N2JYOAogICAgICAgICAgICAgICAgICAgIGpRQlVldi9Odk12MDNwQkFkbHBkRU05Y1Fac1F4Z0RDd3RWdU5CN25OSTIyOVFZYzFWRDdMeGxIT0I4NnNabDkKICAgICAgICAgICAgICAgICAgICBLaWZ2aCtzK2hLcC9KRy83CiAgICAgICAgICAgICAgICAgICAgLS0tLS1SQVEgUFJFR1ZTVlBOR1ItLS0tLQogICAgICAgICAgICAgICAgPC9QcmVndnN2cG5ncj4KICAgICAgICAgICAgICAgIDxQcmVndnN2cG5nciBzYmV6bmc9ImNyeiI+CiAgICAgICAgICAgICAgICAgICAgLS0tLS1PUlRWQSBQUkVHVlNWUE5HUi0tLS0tCiAgICAgICAgICAgICAgICAgICAgWlZWUHZtUFBOd1h0TmpWT050VldOWFZTYWdSQkQxZ0taTmJUUFBkVEZaNDlPTlpQWlZUTFpEZmpQRExRSUREVAogICAgICAgICAgICAgICAgICAgIFJqV0lIbVJHWk9SVE4xSFJQTmpYRDJTZm5KTWlwejVjTEdSSlpPRFROMUhST2pqQUdKOTFvYUV1bko0dEl6eXkKICAgICAgICAgICAgICAgICAgICBxbVJJWk9aVE4xSFJQdGpaRTI5aU0ya3lZUE9Xb3paaFpFTmpRdExRSUREWVFOcU9vekVsbzJ5eFpHWmpaRExRCiAgICAgICAgICAgICAgICAgICAgSUREUVFQY09vekVsbzJ5eFZSZ3lyS0EwbzNXeVZTQWlNYUUzTEtXeVZSUzBxVEltcVRTMG5KOWhWU1dpbzNEagogICAgICAgICAgICAgICAgICAgIFV1cEFaR0xqWkdSa1pRTjBabUhqSnVwQVptTGpaR04yWlFOMFptSGpKd1BPelFSWVpOeFROMUhST3VaUElJWmsKICAgICAgICAgICAgICAgICAgICBSbU5FT3RBSU9OdFpQeEF1b1R5em8zV2huSlJrU3dOSE90QUlPTnBaUUgxaXFKNTBMSnloVlNNY01LcGtTR05HCiAgICAgICAgICAgICAgICAgICAgT3RBSU9OYlpRUnFpbzJxZk1GanRGSjV3WXdSRFpONFROMUhSUGpqVURKNXhwejljTVFSbVpRUlROMUhSTmpqZAogICAgICAgICAgICAgICAgICAgIERKNXhwejljTVBPWU1LeW1xVDlsTUZPR28yTTBxMlNsTUZPT3FVRXlwM0V1cVR5aW92T0ZvMjkwWlN4alJqTFUKICAgICAgICAgICAgICAgICAgICBYYk1WbXcwUE5ETFZYYk1WbXcwUU5EcFFEdE5SN3kxcmsrVU4yMjBRY2E3emd1aWZHSmNxbnp0aFEvOS9GRDU5CiAgICAgICAgICAgICAgICAgICAgcWs5UlZ6Mjlmbi82U2ZpVWVwSTMweW5wZGVyallJRE9LRzVRWGxkQjEwN2ZGVUlPY1hBd1pUUmpVRExRSUUwQgogICAgICAgICAgICAgICAgICAgIE9PTFJTWnZnNktxWkVwQndtajBKUkJFNURtYnVKd1FDWk84VE4xSHFWakRMWk9uTlNadmc2S3FaRXBCd21qMEoKICAgICAgICAgICAgICAgICAgICBSQkU1RG1idUp3UUNaTjhUTjFIcVJqUk8vakRTWk5aT05zOGpRdExRSUUwQ05EVS9PTkRRTnRYUlpOYlRQUGRUCiAgICAgICAgICAgICAgICAgICAgRlo0OU9OWlBOMHBOWlJEUFZRSHViKytZQVJMcmFBSXQ4azFMdkZPZDNYQXlEc0xBYWY2WFRMa3pGVE83TnZPQQogICAgICAgICAgICAgICAgICAgIFAvQUUyR084c0lpbkFHRHFkUnBvTDZKU01HbGdHbEZhNTAyaURLM2tpaj09CiAgICAgICAgICAgICAgICAgICAgLS0tLS1SQVEgUFJFR1ZTVlBOR1ItLS0tLQogICAgICAgICAgICAgICAgPC9QcmVndnN2cG5ncj4KICAgICAgICAgICAgPC9QcmVndnN2cG5nclB1bnZhPgogICAgICAgIDwvWHJsPgogICAgICAgIDxYcmwgbnl0YmV2Z3V6PSJlZm4iPgogICAgICAgICAgICA8Q2V2aW5nclhybCBzYmV6bmc9ImNyeiI+CiAgICAgICAgICAgICAgICAtLS0tLU9SVFZBIEVGTiBDRVZJTkdSIFhSTC0tLS0tCiAgICAgICAgICAgICAgICBaVlZQS0RWT05OWE90RFFOdGxDcElidG9oUU50bnNKanVKVVQ3ZTUvT3JZMWRSVlJ2ZTZZRTc1Mi9kN2xLQ1hvCiAgICAgICAgICAgICAgICBYaWJsTk9ESk5IWE12blNzbThuT0tlQXdKUWppMGlWWTVXdGx0OTJPRmtvSzRMSU9yaElYaVB5ZEJ6MjFqTkRWCiAgICAgICAgICAgICAgICBCMndTSWZValZtekVNT3pUR0lQM0dIUGhseHVacW1JZnZJYlpXMWQvZVJ6cUtLMHdMaVhwS3RZYnBEVlFORE5PCiAgICAgICAgICAgICAgICBOYlRPTlk2VFBqaE1kTlh6K2tjTUQ0Yzdna0hUSmp6d29wb2NsZmtlODhOZkFBc0thY0dUTFREYjJWazdzMkkzCiAgICAgICAgICAgICAgICBqcDNkTU5xWGliNWx1ZzhzUE9VcHlsdHpQVHdyeXFaaC9XbjIwVkcvV2tjc0xBNzhrakNhYjQ1aFhvZG5DUy9QCiAgICAgICAgICAgICAgICBqYk8yZ2R2SmVrMDAxNHRibWNpcWZzQUNhV0RSRGpyT1hMNHRSa01sSjcyOHpHY09OeFJONHBvTVcyRWZQRW9mCiAgICAgICAgICAgICAgICBBYldnSkh6UXFOanU4b08wa1hUeXpUc1RuS3lwdXFDcEV4a294YzZIaTdBQlFwa0RTWVJDUm1EbmcvM0k5dERICiAgICAgICAgICAgICAgICAwZFp6bGdEcGtEV09OQWNWSk1xNEtBSXdRN1E5d1NXSCtMNUd3dXZMQmQ2cm4zNWRKYWdxQVFxSWhGVEJpSE5sCiAgICAgICAgICAgICAgICBRRnQ0c0t2c3FpYnV2OGpndjJ2eTl4VENoK2x5UzVkbWU3MFBEU1ErL1FXeHlJeXVvZ01HR3VJU1BHWHF4NkNMCiAgICAgICAgICAgICAgICBSQWl5aW96UFhGbTN2OXY2MjROdGViMUs5WXBxT0d1aS9jNnFmYVVYQVVyd0ZNYW9xaXd5N0JhTjFXMFBET0ozCiAgICAgICAgICAgICAgICBHQ1c4bWkrWWYyaWpHTTJRRWVQblkzUUY5UkJvUWxuZnN0QzM2cVUzc0hoRUs5WG9YUENqQmZncUh0UXR1Sy9sCiAgICAgICAgICAgICAgICBkTkNjQ2g2SjF2QXA2SUVQaVBSUEREUERjMEtudktQbG1KRkpMUVdQWFpLNFhTby8xeko2emJLVjF0OG92KzVrCiAgICAgICAgICAgICAgICBzZjBmcGhldFVuMlRoYU1IMVo5U2VvS2s4ZVpxYTRSdm02S2tjSXBDemwweQogICAgICAgICAgICAgICAgLS0tLS1SQVEgRUZOIENFVklOR1IgWFJMLS0tLS0KICAgICAgICAgICAgPC9DZXZpbmdyWHJsPgogICAgICAgICAgICA8UHJlZ3ZzdnBuZ3JQdW52YT4KICAgICAgICAgICAgICAgIDxBaHpvcmVCc1ByZWd2c3ZwbmdyZj4yPC9BaHpvcmVCc1ByZWd2c3ZwbmdyZj4KICAgICAgICAgICAgICAgIDxQcmVndnN2cG5nciBzYmV6bmc9ImNyeiI+CiAgICAgICAgICAgICAgICAgICAgLS0tLS1PUlRWQSBQUkVHVlNWUE5HUi0tLS0tCiAgICAgICAgICAgICAgICAgICAgWlZWUGd3UFBOdSt0TmpWT050VlBSTk5qUURMV1hiTVZ1aXBBTkRSWU9ETmpMbVJZWk54VE4xSFJPdVpQSUlaawogICAgICAgICAgICAgICAgICAgIFJtTkVPdEFJT050WlB4QXVvVHl6bzNXaG5KUmtTd05IT3RBSU9OcFpRSDFpcUo1MExKeWhWU01jTUtwa1NHTkcKICAgICAgICAgICAgICAgICAgICBPdEFJT05iWlFScWlvMnFmTUZqdEZKNXdZd1JEWk40VE4xSFJQampVREo1eHB6OWNNUU5yU2owa0F3TmtaUURrCiAgICAgICAgICAgICAgICAgICAgWndEakFHQW5TajBtQUdSbFptTmtad0RqQUdBblpVTGtQbU5XT3RBSU9OTEdOeUlHWkVaalJETFFJRERWUU5jUQogICAgICAgICAgICAgICAgICAgIExKa2NNejlsb3p5dVpFSGpSakxRSUREWFFOa1VvMjlhb1RIZlZSeWhMbDRrUlFOQk90QUlPTmZaTzBTaE1VV2kKICAgICAgICAgICAgICAgICAgICBuSkRrWEdOYU90QUlPTlpaVlJTaE1VV2luSkR0SDI5enFVcXVwekh0REtFME1LQTBMS0VjbzI0dEYySTVaVlRzCiAgICAgICAgICAgICAgICAgICAgWk4wVFBGZFRGVm8zUURST05ESE5ONFRBTlFQT3ZEWE90RFFOdGxDcElidG9oUU50bnNKanVKVVQ3ZTUvT3JZMQogICAgICAgICAgICAgICAgICAgIGRSVlJ2ZTZZRTc1Mi9kN2xLQ1hvWGlibE5PREpOSFhNdm5Tc204bk9LZUF3SlFqaTBpVlk1V3RsdDkyT0Zrb0sKICAgICAgICAgICAgICAgICAgICA0TElPcmhJWGlQeWRCejIxak5EVkIyd1NJZlVqVm16RU1PelRHSVAzR0hQaGx4dVpxbUlmdkliWlcxZC9lUnpxCiAgICAgICAgICAgICAgICAgICAgS0swd0xpWHBLdFlicERWUU5ETk9iMkxqTVFOcU90QUlVRDRSU3RESDFOakRUL3dBTDdhM0JJWDFRdUFwY2dyTQogICAgICAgICAgICAgICAgICAgIHg0TGpVakxRSUUwd09PdGpTYk5IWHNla2Vaa0EweGxKRFBxMWdlUWNaaEhVL3Y0alJ0TFFJRTBHTkRVL09OdGoKICAgICAgICAgICAgICAgICAgICBPdFJPL2pWT05RTkJPdEFJVUQ4T05zOFJPTlpQTmJEalFETFdYYk1WdWlwQU5EUllPRE5RdExSTmF2MVZLNGthCiAgICAgICAgICAgICAgICAgICAgWjlqbnVuMk0xMU53NnVHZkQ3UXVhcmVQVjBMcnBlSE0zVE52NVhJYlpKallJcEd6YVhWZ2FtY0N4MmZrdmtNNAogICAgICAgICAgICAgICAgICAgIFN0MlZsOXpZbVZQcXVDUVBXK0FlQkNVOTBycEtwd1NNQUsySjg4SS9kNTJDeXpSekc3WCt0b2ZBRkREdnZmNnMKICAgICAgICAgICAgICAgICAgICA5L0lQWXZJUit2UlVSeWRRZ0lKZ1RWWTRET0ZvYVBPd09VOD0KICAgICAgICAgICAgICAgICAgICAtLS0tLVJBUSBQUkVHVlNWUE5HUi0tLS0tCiAgICAgICAgICAgICAgICA8L1ByZWd2c3ZwbmdyPgogICAgICAgICAgICAgICAgPFByZWd2c3ZwbmdyIHNiZXpuZz0iY3J6Ij4KICAgICAgICAgICAgICAgICAgICAtLS0tLU9SVFZBIFBSRUdWU1ZQTkdSLS0tLS0KICAgICAgICAgICAgICAgICAgICBaVlZQY21QUE51UHROalZPTnRWV05DK0gycTJzTzh0WlpOMFRQRmRURlZvM1FEUk9QakhOWlRaa1BtTldPdEFJCiAgICAgICAgICAgICAgICAgICAgT05MR055SUdaRVpqUkRMUUlERFZRTmNRTEprY016OWxvenl1WkVMalNOTFFJRERVUU4xQW8zSWhxVFNjb3ZPSgogICAgICAgICAgICAgICAgICAgIG5KSTNaRUhqUmpMUUlERFhRTmtVbzI5YW9USGZWUnloTGw0a1JRTkJPdEFJT05mWk8wU2hNVVdpbkpEalV1cEEKICAgICAgICAgICAgICAgICAgICBaR0xqWkdOMFpHVm1aR040SnVwQVptSGtad1pqWkdWbVpHTjRKd093WkRmalBETFFJRERUUmpXSUhtUkdaT1JUCiAgICAgICAgICAgICAgICAgICAgTjFIUlBOalhEMlNmbkpNaXB6NWNMR1JKWk9EVE4xSFJPampBR0o5MW9hRXVuSjR0SXp5eXFtUklaT1pUTjFIUgogICAgICAgICAgICAgICAgICAgIFB0alpFMjlpTTJreVlQT1dvelpoWkVOalF0TFFJRERZUU5xT296RWxvMnl4WlZUc1pOMFRQRmRURlZvM1FEUk8KICAgICAgICAgICAgICAgICAgICBOREhOTjRUQU5RUE92RFhPdERQdm42M2VvdjVSTHIvSVFiWXpnNUdFcUZac3E1Z3d4SkMvOTZlL1AzV1VHZk5mCiAgICAgICAgICAgICAgICAgICAgRCtqbXNBcmY3SE4rd1B2dE1nSzN1amZteTk0QmhSNEdEWGhpY0ZyL3lKenRacWZUSHpLNEVTeUtMc1A3OHVxWQogICAgICAgICAgICAgICAgICAgIGcwVE5NWk5iUWI5RnE0N28weHIyRXJ4TWxCellqOWlQeEcvSzExUVJVR0l6K0lzeHk1TFlQbm1CeHdKU3pqVlEKICAgICAgICAgICAgICAgICAgICBORE5PYjJaakxHTnFPdEFJVUQ0UlN0REhYc2VrZVprQTB4bEpEUHExZ2VRY1poSFUvdjRqVWpMUUlFMHdPT3RqCiAgICAgICAgICAgICAgICAgICAgU2JOSFhzZWtlWmtBMHhsSkRQcTFnZVFjWmhIVS92NGpRakxRSUUwR05EVS9PTkhqTmpSTy9tTkJPdEFJVUQ4TwogICAgICAgICAgICAgICAgICAgIE5zOFJPTlpQTmJEalFETFdYYk1WdWlwQU5EUllPRE5RdExSTkczWW1BeXpBUWZUNXFTZmtKc29qd0ZJV1pXNncKICAgICAgICAgICAgICAgICAgICBVT2pjMHhIZ1ZZeUFLMkYwNlZRVXJVZHBCcTZiZi9KL1kzT3NFa09wa3JvZUdEbk1McVhoenRzLzkzbDRkK2hwCiAgICAgICAgICAgICAgICAgICAgUWxEVUtlUy9oYXlrL0gxb2FnOEhkczdzN0ttTnZTMzQzTWd4WnlvSUFNZXZSL3pDbWZTODNCK3hkZVdJajRCYwogICAgICAgICAgICAgICAgICAgIFlpZ3A5elkxVzFWS2l6Wj0KICAgICAgICAgICAgICAgICAgICAtLS0tLVJBUSBQUkVHVlNWUE5HUi0tLS0tCiAgICAgICAgICAgICAgICA8L1ByZWd2c3ZwbmdyPgogICAgICAgICAgICA8L1ByZWd2c3ZwbmdyUHVudmE+CiAgICAgICAgPC9Ycmw+CiAgICA8L1hybG9iaz4KPC9OYXFlYnZxTmdncmZnbmd2YmE+Cg==";

    private KeyProviderManager() {}

    public static IKeyboxProvider getProvider() {
        return new DefaultKeyboxProvider();
    }

    public static boolean isKeyboxAvailable() {
        return getProvider().hasKeybox();
    }

    private static class DefaultKeyboxProvider implements IKeyboxProvider {
        private final Map<String, String> keyboxData = new HashMap<>();

        private DefaultKeyboxProvider() {
            Context context = getApplicationContext();
            if (context == null) {
                Log.e(TAG, "Failed to get application context");
                return;
            }

            loadFromXmlSetting(context);
        }

        private boolean loadFromXmlSetting(Context ctx) {
            try {


                String xml = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.KEYBOX_DATA);

                if (xml == null || xml.trim().isEmpty()) {
                    boolean autoUpdate = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BAIKALOS_GMS_SPOOFER_UPDATE, 0) != 0;
                    if( autoUpdate ) {
                        xml = Settings.Global.getString(ctx.getContentResolver(), "baikal_kb_data");
                        if (xml == null || xml.trim().isEmpty()) {
                            Log.e(TAG,"Auto keybox data not found");
                        } else {
                            Log.d(TAG, "Auto keybox found");
                        }
                    }
                } else {
                    Log.d(TAG, "Custom keybox found");
                }

                if (xml == null || xml.trim().isEmpty()) {
                    xml = decodeBase64Rot13(AOSP_KEYBOX);
                    Log.d(TAG, "Using AOSP keybox");
                }


                if (xml == null || xml.trim().isEmpty()) {
                    Log.d(TAG, "No keybox provided");
                    return false;
                }

                XmlPullParser p = Xml.newPullParser();
                p.setInput(new StringReader(xml));

                String currentAlg = null;
                int certCount = 0;
                boolean numberOfKeyboxesChecked = false;

                for (int ev = p.next(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                    if (ev == XmlPullParser.START_TAG) {
                        String tag = p.getName();
                        switch (tag) {
                            case "NumberOfKeyboxes":
                                p.next();
                                numberOfKeyboxesChecked = true;
                                try {
                                    int count = Integer.parseInt(p.getText().trim());
                                    if (count != 1) {
                                        Log.w(TAG, "Invalid NumberOfKeyboxes: " + count);
                                        return false;
                                    }
                                } catch (NumberFormatException e) {
                                    Log.w(TAG, "Failed to parse NumberOfKeyboxes", e);
                                    return false;
                                }
                                break;

                            case "Key":
                                currentAlg = p.getAttributeValue(null, "algorithm");
                                if ("ecdsa".equalsIgnoreCase(currentAlg)) currentAlg = "EC";
                                else if ("rsa".equalsIgnoreCase(currentAlg)) currentAlg = "RSA";
                                else currentAlg = null;
                                certCount = 0;
                                break;

                            case "PrivateKey": {
                                String format = p.getAttributeValue(null, "format");
                                if (!"pem".equalsIgnoreCase(format)) {
                                    Log.w(TAG, "Unsupported PrivateKey format: " + format);
                                    return false;
                                }
                                p.next();
                                if (currentAlg != null) {
                                    keyboxData.put(currentAlg + ".PRIV", p.getText().trim());
                                }
                                break;
                            }

                            case "Certificate": {
                                String format = p.getAttributeValue(null, "format");
                                if (!"pem".equalsIgnoreCase(format)) {
                                    Log.w(TAG, "Unsupported Certificate format: " + format);
                                    return false;
                                }
                                if (currentAlg != null) {
                                    p.next();
                                    certCount++;
                                    keyboxData.put(currentAlg + ".CERT_" + certCount, p.getText().trim());
                                }
                                break;
                            }
                        }
                    }
                }

                if (!numberOfKeyboxesChecked) {
                    Log.w(TAG, "Missing <NumberOfKeyboxes> in keybox XML");
                    return false;
                }

                if (!hasKeybox()) {
                    Log.w(TAG, "Failed to load keybox from XML setting");
                    return false;
                }

                Log.i(TAG, "Loaded keybox from XML setting");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "XML keybox load failed", e);
                return false;
            }
        }

        private static Context getApplicationContext() {
            try {
                return ActivityThread.currentApplication().getApplicationContext();
            } catch (Exception e) {
                Log.e(TAG, "Error getting application context", e);
                return null;
            }
        }

        @Override
        public boolean hasKeybox() {
            if (!keyboxData.containsKey("EC.PRIV") || !keyboxData.containsKey("RSA.PRIV")) {
                return false;
            }
            if (!keyboxData.containsKey("EC.CERT_1") || !keyboxData.containsKey("RSA.CERT_1")) {
                return false;
            }
            return true;
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
            List<String> dataList = new ArrayList<>();
            for (String key : keyboxData.keySet()) {
                if (key.startsWith(prefix + ".CERT_")) {
                    dataList.add(keyboxData.get(key));
                }
            }
            String[] chain = dataList.toArray(String[]::new);
            Arrays.sort(chain);
            return chain;
        }
    }

    public static String decodeBase64Rot13(String input) {
        byte[] decodedBytes = Base64.decode(input, Base64.DEFAULT);
        String decoded = new String(decodedBytes);

        StringBuilder result = new StringBuilder();
        for (char c : decoded.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                result.append((char) ((c - 'a' + 13) % 26 + 'a'));
            } else if (c >= 'A' && c <= 'Z') {
                result.append((char) ((c - 'A' + 13) % 26 + 'A'));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
