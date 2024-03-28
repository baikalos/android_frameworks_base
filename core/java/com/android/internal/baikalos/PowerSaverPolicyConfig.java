/*
 * Copyright (C) 2019 BaikalOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.baikalos;

import android.os.BatterySaverPolicyConfig;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.os.Process;

import android.util.Slog;
import android.util.KeyValueListParser;

    public class PowerSaverPolicyConfig {


    public static final int POWERSAVER_POLICY_NONE = 0;
    public static final int POWERSAVER_POLICY_LOW = 1;
    public static final int POWERSAVER_POLICY_MODERATE = 2;
    public static final int POWERSAVER_POLICY_AGGRESSIVE = 3;
    public static final int POWERSAVER_POLICY_EXTREME = 4;
    public static final int POWERSAVER_POLICY_BATTERY_SAVER = 5;
    public static final int POWERSAVER_POLICY_STAMINA = 6;
    public static final int POWERSAVER_POLICY_MAX = 7;

    
        private static final String TAG = "Baikal.PowerSave";

        public String policyName;
        public int policyNumber;
        public int adjustBrightnessFactor;
        public boolean advertiseIsEnabled;
        public boolean enableFullBackup;
        public boolean enableKeyValueBackup;
        public boolean enableAnimation;
        public boolean enableAod;
        public boolean enableLaunchBoost;
        public boolean enableOptionalSensors;
        public boolean enableVibration;
        public boolean enableAdjustBrightness;
        public boolean enableDataSaver;
        public boolean enableFirewall;
        public boolean enableNightMode;
        public boolean enableQuickDoze;
        public boolean forceAllAppsStandby;
        public boolean forceBackgroundCheck;
        public int soundTriggerMode;
        public int locationMode;
        public int killBgRestrictedCachedIdleSettleTime;
        public boolean killInBackground;
        public boolean autoLimitBackground;

        public PowerSaverPolicyConfig(String name, int number) {
            policyName = name;
            policyNumber = number;
            adjustBrightnessFactor = 100;
            advertiseIsEnabled = false;
            enableFullBackup = true;
            enableKeyValueBackup = true;
            enableAnimation = true;
            enableAod = true;
            enableLaunchBoost = true;
            enableOptionalSensors = true;
            enableVibration = true;
            enableAdjustBrightness = true;
            enableDataSaver = false;
            enableFirewall = false;
            enableNightMode = false;
            enableQuickDoze = false;
            forceAllAppsStandby = false;
            forceBackgroundCheck = false;
            soundTriggerMode = 0;
            locationMode = 0;
            killBgRestrictedCachedIdleSettleTime = 600;
            killInBackground = false;
        }

        public static PowerSaverPolicyConfig deserialize(String policyString) {
            KeyValueListParser parser = new KeyValueListParser(',');

            try {
                parser.setString(policyString);
            } catch (Exception e) {
                Slog.e(TAG, "Bad policy settings :" + policyString, e);
                return null;
            }

            String name = parser.getString("pnm",null);
            if( name == null || name.equals("") ) {
                Slog.e(TAG, "Bad policy settings :" + policyString);
                return null;
            }

            try {
                int number = parser.getInt("pnr",0);

                PowerSaverPolicyConfig policy = new PowerSaverPolicyConfig(name,number);

                policy.adjustBrightnessFactor = parser.getInt("abf",100);
                policy.advertiseIsEnabled = parser.getBoolean("av",false);
                policy.enableFullBackup = parser.getBoolean("efb",true);
                policy.enableKeyValueBackup = parser.getBoolean("ekvb",true);
                policy.enableAnimation = parser.getBoolean("ean",true);
                policy.enableAod = parser.getBoolean("eaod",true);
                policy.enableLaunchBoost = parser.getBoolean("elb",true);
                policy.enableOptionalSensors = parser.getBoolean("eos",true);
                policy.enableVibration = parser.getBoolean("evb",true);
                policy.enableAdjustBrightness = parser.getBoolean("eab",true);
                policy.enableDataSaver = parser.getBoolean("eds",false);
                policy.enableFirewall = parser.getBoolean("efw",false);
                policy.enableNightMode = parser.getBoolean("enm",false);
                policy.enableQuickDoze = parser.getBoolean("eqd",false);
                policy.forceAllAppsStandby = parser.getBoolean("fas",false);
                policy.forceBackgroundCheck = parser.getBoolean("fbc",false);
                policy.soundTriggerMode = parser.getInt("stm",0);
                policy.locationMode = parser.getInt("loc",0);
                policy.killBgRestrictedCachedIdleSettleTime = parser.getInt("kbt",600);
                policy.killInBackground = parser.getBoolean("kill",false);

                return policy;

            } catch( Exception e ) {
                Slog.e(TAG, "Bad policy settings :" + policyString, e);
            }
            return null;
        }

        public String serialize() {
            String seralized = "";
            seralized += "pnm=" + policyName;
            seralized += "," + "pnr=" +  policyNumber;
            if( adjustBrightnessFactor != 100 ) seralized += "," + "abf=" +  adjustBrightnessFactor;
            if( advertiseIsEnabled ) seralized += "," + "av=" +  advertiseIsEnabled;
            if( !enableFullBackup ) seralized += "," + "efb=" +  enableFullBackup;
            if( !enableKeyValueBackup ) seralized += "," + "ekvb=" +  enableKeyValueBackup;
            if( !enableAnimation ) seralized += "," + "ean=" +  enableAnimation;
            if( !enableAod ) seralized += "," + "eaod=" +  enableAod;
            if( !enableLaunchBoost ) seralized += "," + "elb=" +  enableLaunchBoost;
            if( !enableOptionalSensors ) seralized += "," + "eos=" +  enableOptionalSensors;
            if( !enableVibration ) seralized += "," + "evb=" +  enableVibration;
            if( !enableAdjustBrightness ) seralized += "," + "eab=" +  enableAdjustBrightness;
            if( enableDataSaver ) seralized += "," + "eds=" +  enableDataSaver;
            if( enableFirewall ) seralized += "," + "efw=" +  enableFirewall;
            if( enableNightMode ) seralized += "," + "enm=" +  enableNightMode;
            if( enableQuickDoze ) seralized += "," + "eqd=" +  enableQuickDoze;
            if( forceAllAppsStandby ) seralized += "," + "fas=" +  forceAllAppsStandby;
            if( forceBackgroundCheck ) seralized += "," + "fbc=" +  forceBackgroundCheck;
            if( soundTriggerMode != 0 ) seralized += "," + "stm=" +  soundTriggerMode;
            if( locationMode != 0 ) seralized += "," + "loc=" +  locationMode;
            if( killBgRestrictedCachedIdleSettleTime != 600 ) seralized += "," + "kbt=" +  killBgRestrictedCachedIdleSettleTime;
            if( killInBackground ) seralized += "," + "kill=" +  killInBackground;
            return seralized;
        }

        public PowerSaverPolicyConfig setPolicyName(String policyName_) {
            policyName = policyName_;
            return this;
        }

        public PowerSaverPolicyConfig setPolicyNumber(int policyNumber_) {
            policyNumber = policyNumber_;
            return this;
        }

        public PowerSaverPolicyConfig setAdjustBrightnessFactor(int  adjustBrightnessFactor_) {
            adjustBrightnessFactor = adjustBrightnessFactor_;
            return this;
        }

        public PowerSaverPolicyConfig setAdvertiseIsEnabled(boolean enable) {
            advertiseIsEnabled = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableFullBackup(boolean enable) {
            enableFullBackup = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableKeyValueBackup(boolean enable) {
            enableKeyValueBackup = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableAnimation(boolean enable) {
            enableAnimation = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableAod(boolean enable) {
            enableAod = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableLaunchBoost(boolean enable) {
            enableLaunchBoost = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableOptionalSensors(boolean enable) {
            enableOptionalSensors = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableVibration(boolean enable) {
            enableVibration = enable;
            return this;
        }


        public PowerSaverPolicyConfig setEnableAdjustBrightness(boolean enable) {
            enableAdjustBrightness = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableDataSaver(boolean enable) {
            enableDataSaver = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableFirewall(boolean enable) {
            enableFirewall = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableNightMode(boolean enable) {
            enableNightMode = enable;
            return this;
        }

        public PowerSaverPolicyConfig setEnableQuickDoze(boolean enable) {
            enableQuickDoze = enable;
            return this;
        }

        public PowerSaverPolicyConfig setForceAllAppsStandby(boolean enable) {
            forceAllAppsStandby = enable;
            return this;
        }

        public PowerSaverPolicyConfig setForceBackgroundCheck(boolean enable) {
            forceBackgroundCheck = enable;
            return this;
        }


        public PowerSaverPolicyConfig setLocationMode(int mode) {
            locationMode = mode;
            return this;
        }

        public PowerSaverPolicyConfig setSoundTriggerMode(int mode) {
            soundTriggerMode = mode;
            return this;
        }

        public PowerSaverPolicyConfig setkillBgRestrictedCachedIdleSettleTime(int timeout) {
            killBgRestrictedCachedIdleSettleTime = timeout;
            return this;
        }

        public PowerSaverPolicyConfig setKillInBackground(boolean enable) {
            killInBackground = enable;
            return this;
        }

        public BatterySaverPolicyConfig getBatterySaverPolicyConfig () {
                return new BatterySaverPolicyConfig.Builder()
                .setAdjustBrightnessFactor( ((float)adjustBrightnessFactor)/100F )
                .setAdvertiseIsEnabled(advertiseIsEnabled)
                .setDeferFullBackup(!enableFullBackup)
                .setDeferKeyValueBackup(!enableKeyValueBackup)
                .setDisableAnimation(!enableAnimation)
                .setDisableAod(!enableAod)
                .setDisableLaunchBoost(!enableLaunchBoost)
                .setDisableOptionalSensors(!enableOptionalSensors)
                .setDisableVibration(!enableVibration)
                .setEnableAdjustBrightness(enableAdjustBrightness)
                .setEnableDataSaver(enableDataSaver)
                .setEnableFirewall(enableFirewall)
                .setEnableNightMode(enableNightMode)
                .setEnableQuickDoze(enableQuickDoze)
                .setForceAllAppsStandby(forceAllAppsStandby)
                .setForceBackgroundCheck(forceBackgroundCheck)
                .setLocationMode(locationMode)
                .setSoundTriggerMode(soundTriggerMode)
                .build();
        }
    }

