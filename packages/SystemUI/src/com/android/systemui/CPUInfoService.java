/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.systemui;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.util.Log;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.StringBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CPUInfoService extends Service {
    private View mView;
    private Thread mCurCPUThread;
    private final static String TAG = "CPUInfoService";
    private PowerManager mPowerManager;
    private int mNumCpus = 1;
    private String[] mCurrFreq=null;
    private String[] mCurrGov=null;

    private static final String NUM_OF_CPUS_PATH = "/sys/devices/system/cpu/present";
    private int CPU_TEMP_DIVIDER = 1;
    private int SYS_TEMP_DIVIDER = 1;
    private int BAT_TEMP_DIVIDER = 1;

    private String CPU_TEMP_SENSOR = "";

    private String CPU_TEMP_SENSOR_PREFIX = "";
    private String CPU_TEMP_SENSOR_SUFFIX = "";
    private String CPU_TEMP_SENSOR_NUMBER = "";
    private String[] CPU_TEMP_NUMBERS = null;
    private int CPU_TEMP_NUMBER = 0;


    private String SYS_TEMP_SENSOR = "";
    private String BAT_TEMP_SENSOR = "";

    private String GPU_FREQ_SENSOR = "";

    private DreamService mDreamService;

    private boolean mCpuTempAvail;
    private boolean mSysTempAvail;
    private boolean mBatTempAvail;
    private boolean mPowerProfileAvail = false;
    private boolean mThermalProfileAvail = false;
    private boolean mBoostActive = false;

    private static boolean mIsolationSupported = true;

    double mAccumulator = -10000.0;    
    double mAlpha = 0.2; //0.04;

    private class CPUView extends View {
        private Paint mOnlinePaint;
        private Paint mOfflinePaint;
        private Paint mIsolatedPaint;
        private Paint mBoostPaint;

        private float mAscent;
        private int mFH;
        private int mMaxWidth;

        private int mNeededWidth;
        private int mNeededHeight;
        private int mNumberOfRows;

        private String mCpuTemp;
        private String mSysTemp;
        private String mBatTemp;
       
        private String mThermalProfile;
        private String mPowerProfile;

        private String mGpuFreq;
        private String mBatCur;
        private String mBatAvg;

        private boolean mDataAvail;

        private Handler mCurCPUHandler = new Handler() {
            public void handleMessage(Message msg) {
                if(msg.obj==null){
                    return;
                }
                if(msg.what==1){
                    String msgData = (String) msg.obj;
                    try {
                        //Log.d(TAG, "msgData " + msgData);
                        String[] parts=msgData.split(";");
                        if( parts.length < 9 ) return;
                        mCpuTemp=parts[0];
                        mSysTemp=parts[1];
                        mBatTemp=parts[2];
                        mPowerProfile=parts[3];
                        mThermalProfile=parts[4];
                        mGpuFreq=parts[5];
                        mBatCur=parts[6];
                        mBatAvg=parts[7];


                        String[] cpuParts=parts[8].split("\\|");
                        for(int i=0; i<cpuParts.length; i++){
                            String cpuInfo=cpuParts[i];
                            String cpuInfoParts[]=cpuInfo.split(":");
                            if(cpuInfoParts.length==2){
                                mCurrFreq[i]=cpuInfoParts[0];
                                mCurrGov[i]=cpuInfoParts[1];
                            } else {
                                mCurrFreq[i]="0";
                                mCurrGov[i]="";
                            }
                        }
                        mDataAvail = true;
                        updateDisplay();
                    } catch(ArrayIndexOutOfBoundsException e) {
                        Log.e(TAG, "illegal data " + msgData);
                    }
                }
            }
        };

        CPUView(Context c) {
            super(c);
            float density = c.getResources().getDisplayMetrics().density;
            int paddingPx = Math.round(5 * density);
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            setBackgroundColor(Color.argb(0x60, 0, 0, 0));

            final int textSize = Math.round(12 * density);

            Typeface typeface = Typeface.create("monospace", Typeface.NORMAL);

            mOnlinePaint = new Paint();
            mOnlinePaint.setTypeface(typeface);
            mOnlinePaint.setAntiAlias(true);
            mOnlinePaint.setTextSize(textSize);
            mOnlinePaint.setColor(Color.WHITE);

            mOfflinePaint = new Paint();
            mOfflinePaint.setTypeface(typeface);
            mOfflinePaint.setAntiAlias(true);
            mOfflinePaint.setTextSize(textSize);
            mOfflinePaint.setColor(Color.RED);

            mIsolatedPaint = new Paint();
            mIsolatedPaint.setTypeface(typeface);
            mIsolatedPaint.setAntiAlias(true);
            mIsolatedPaint.setTextSize(textSize);
            mIsolatedPaint.setColor(Color.BLUE);

            mBoostPaint = new Paint();
            mBoostPaint.setTypeface(typeface);
            mBoostPaint.setAntiAlias(true);
            mBoostPaint.setTextSize(textSize);
            mBoostPaint.setColor(Color.RED);

            mAscent = mOnlinePaint.ascent();
            float descent = mOnlinePaint.descent();
            mFH = (int)(descent - mAscent + .5f);

            final String maxWidthStr="cX: 0000 MHz";
            mMaxWidth = (int)mOnlinePaint.measureText(maxWidthStr);

            updateDisplay();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mCurCPUHandler.removeMessages(1);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(mNeededWidth, widthMeasureSpec),
                    resolveSize(mNeededHeight, heightMeasureSpec));
        }

        private String getCPUInfoString(int i) {
            String freq=mCurrFreq[i];
            String gov=mCurrGov[i];
            return "c" + i + ": " + String.format("%8s", toMHz(freq,gov));
        }

        private String getCpuTemp(String cpuTemp) {
            if (!"-".equals(cpuTemp) && CPU_TEMP_DIVIDER > 1) {
                return String.format("%s",
                        Integer.parseInt(cpuTemp) / CPU_TEMP_DIVIDER);
            } else {
                return cpuTemp;
            }
        }

        private String getSysTemp(String sysTemp) {
            if (!"-".equals(sysTemp) && SYS_TEMP_DIVIDER > 1) {
                return String.format("%s",
                        Integer.parseInt(sysTemp) / SYS_TEMP_DIVIDER);
            } else {
                return sysTemp;
            }
        }

        private String getBatTemp(String batTemp) {
            if (!"-".equals(batTemp) && BAT_TEMP_DIVIDER > 1) {
                return String.format("%s",
                        Integer.parseInt(batTemp) / BAT_TEMP_DIVIDER);
            } else {
                return batTemp;
            }
        }


        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!mDataAvail) {
                return;
            }

            final int W = mNeededWidth;
            final int RIGHT = getWidth()-1;

            int x = RIGHT - mPaddingRight;
            int top = mPaddingTop + 2;
            int bottom = mPaddingTop + mFH - 2;

            int y = mPaddingTop - (int)mAscent;


            mNumberOfRows = 0;
            if( mCpuTemp != null && !mCpuTemp.equals("-")) {
                canvas.drawText("CPU " + getCpuTemp(mCpuTemp) + "°C",
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }
            if(mSysTemp != null && !mSysTemp.equals("-")) {
                canvas.drawText("SYS " + getSysTemp(mSysTemp) + "°C",
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }
            if(mBatTemp != null && !mBatTemp.equals("-")) {
                canvas.drawText("BAT " + getBatTemp(mBatTemp) + "°C",
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }

            if(mPowerProfile != null && !mPowerProfile.equals("-")) {
                if( readOneProperty("sys.baikal.boost_act", "0").equals("1") ) {
                    canvas.drawText("PWR " + mPowerProfile,
                            RIGHT-mPaddingRight-mMaxWidth, y-1, mBoostPaint);
                    y += mFH;
                    mNumberOfRows++;
                } else {
                    canvas.drawText("PWR " + mPowerProfile,
                            RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                    y += mFH;
                    mNumberOfRows++;
                }
            }

            if(mThermalProfile != null && !mThermalProfile.equals("-")) {
                canvas.drawText("TRM " + mThermalProfile,
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }

            
            if(mGpuFreq != null && !mGpuFreq.equals("-")) {
                canvas.drawText("GPU:" + mGpuFreq + " MHz",
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }

            if(mBatCur !=null && !mBatCur.equals("-")) {
                canvas.drawText("cur:" + mBatCur + " mA",
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }

            if(mBatAvg !=null && !mBatCur.equals("-")) {
                canvas.drawText("avg:" + mBatAvg + " mA",
                        RIGHT-mPaddingRight-mMaxWidth, y-1, mOnlinePaint);
                y += mFH;
                mNumberOfRows++;
            }

            if( mCurrFreq != null ) {
            for(int i=0; i<mCurrFreq.length; i++){
                String s=getCPUInfoString(i);
                String freq=mCurrFreq[i];
                if(!freq.equals("-")) {
                    if(freq.equals("0") || freq.equals("off") ){
                        canvas.drawText(s, RIGHT-mPaddingRight-mMaxWidth,
                            y-1, mOfflinePaint);

                    } else if ( freq.equals("iso") ) {
                        canvas.drawText(s, RIGHT-mPaddingRight-mMaxWidth,
                            y-1, mIsolatedPaint);
                    } else {
                        canvas.drawText(s, RIGHT-mPaddingRight-mMaxWidth,
                            y-1, mOnlinePaint);
                    }
                    y += mFH;
                    mNumberOfRows++;
                }
            }
            }
        }

        void updateDisplay() {
            if (!mDataAvail) {
                return;
            }

            //int NW = mNumCpus + 8;
            int NW = mNumberOfRows;


            int neededWidth = mPaddingLeft + mPaddingRight + mMaxWidth;
            int neededHeight = mPaddingTop + mPaddingBottom + (mFH*NW);
            if (neededWidth != mNeededWidth || neededHeight != mNeededHeight) {
                mNeededWidth = neededWidth;
                mNeededHeight = neededHeight;
                requestLayout();
            } else {
                invalidate();
            }
        }

        private String toMHz(String mhzString, String load ) {
            try {
                if( mhzString.equals("iso") ) return "isolated";
                if( mhzString.equals("off") ) return "offline";
                if( "-".equals(mhzString) ) return "invalid";
                //return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" MHz").toString();
                if( load != null && !"null".equals(load) && !"-".equals(load) ) {
                    return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" ").append(String.format("%3s",load)).toString();
                }
                return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" MHz").toString();
            } catch( Exception ex ) {
                Log.e(TAG, "Exception in toMHz: " + mhzString + ", " + load, ex);
            }
            return "invalid";
        }

        public Handler getHandler(){
            return mCurCPUHandler;
        }
    }

    protected class CurCPUThread extends Thread {
        private boolean mInterrupt = false;
        private Handler mHandler;

        private static final String CURRENT_CPU = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
        private static final String CPU_ROOT = "/sys/devices/system/cpu/cpu";
        private static final String CPU_CUR_TAIL = "/cpufreq/scaling_cur_freq";
        private static final String CPU_GOV_TAIL = "/sched_cur_load";
        private static final String CPU_ISO_TAIL = "/isolate";
        private static final String CPU_OFF_TAIL = "/online";
        private static final String BATT_CURRENT = "/sys/class/power_supply/battery/current_now";

        public CurCPUThread(Handler handler, int numCpus){
            mHandler=handler;
            mNumCpus = numCpus;
        }

        public void interrupt() {
            mInterrupt = true;
        }

        @Override
        public void run() {
            try {
                while (!mInterrupt) {
                    sleep(500);
                    StringBuffer sb=new StringBuffer();
                    String sCpuTemp = null;

                    int cpuTemp = -1000;
                    if( CPU_TEMP_NUMBER > 0 ) {
                        for(int i=0;i<CPU_TEMP_NUMBER;i++) {
                            String temp = CPUInfoService.readOneLine(CPU_TEMP_SENSOR_PREFIX +  CPU_TEMP_NUMBERS[i] +  CPU_TEMP_SENSOR_SUFFIX);
                            if( temp != null ) {
                                int coreTemp = Integer.parseInt(temp);
                                if( coreTemp > cpuTemp ) cpuTemp = coreTemp;
                            }
                        }
                        sCpuTemp = Integer.toString(cpuTemp);
                        sb.append(sCpuTemp == null ? "-" : sCpuTemp);
                        sb.append(";");
                    } else {
                        sCpuTemp = CPUInfoService.readOneLine(CPU_TEMP_SENSOR);
                        sb.append(sCpuTemp == null ? "-" : sCpuTemp);
                        sb.append(";");
                    }

                    String sSysTemp = CPUInfoService.readOneLine(SYS_TEMP_SENSOR);
                    sb.append(sSysTemp == null ? "-" : sSysTemp);
                    sb.append(";");

                    String sBatTemp = CPUInfoService.readOneLine(BAT_TEMP_SENSOR);
                    sb.append(sBatTemp == null ? "-" : sBatTemp);
                    sb.append(";");

                    String sPowerProfile = CPUInfoService.readOneProperty("baikal.eng.perf.cur_profile","-");
                    sb.append(sPowerProfile == null ? "-" : sPowerProfile);
                    sb.append(";");

                    String sThermalProfile = CPUInfoService.readOneProperty("baikal.eng.therm.cur_profile","-");
                    sb.append(sThermalProfile == null ? "-" : sThermalProfile);
                    sb.append(";");

                    String sGpuFreq = CPUInfoService.readOneLine(GPU_FREQ_SENSOR);
                    sb.append(sGpuFreq == null ? "-" : sGpuFreq);
                    sb.append(";");

                    String sBatCur = CPUInfoService.readOneLine(BATT_CURRENT);
                    String sBatAvg = null;
                    if( sBatCur != null ) {
                        try {
                            int iBatCur = Integer.parseInt(sBatCur);
                            iBatCur*=-1;
                            iBatCur = iBatCur/1000;
                            sBatCur = Integer.toString(iBatCur);
                            if( mAccumulator < -9999.0 ) mAccumulator = iBatCur;
                            mAccumulator =  (mAlpha * iBatCur) + (1.0 - mAlpha) * mAccumulator;
                            sBatAvg = String.valueOf((int) Math.round(mAccumulator));

                        } catch(Exception bce) {
                            sBatCur = null;
                        }
                    }
                    sb.append(sBatCur == null ? "-" : sBatCur);
                    sb.append(";");
                    sb.append(sBatAvg == null ? "-" : sBatAvg);
                    sb.append(";");

                    
                    
                    

                    for(int i=0; i<mNumCpus; i++){
                        final String freqFile=CPU_ROOT+i+CPU_CUR_TAIL;
                        String currFreq = CPUInfoService.readOneLine(freqFile);
                        final String govFile=CPU_ROOT+i+CPU_GOV_TAIL;
                        String currGov = CPUInfoService.readOneLine(govFile);

                        final String isoFile=CPU_ROOT+i+CPU_ISO_TAIL;
                        String currIso = "-";
                        if( mIsolationSupported ) currIso = CPUInfoService.readOneLine(isoFile);
                        if( currIso == null && i == 0 ) {
                            mIsolationSupported = false;
                        }

                        final String offFile=CPU_ROOT+i+CPU_OFF_TAIL;
                        String currOff = CPUInfoService.readOneLine(offFile);

                        if( currIso != null && currIso.equals("1") ){
                            currFreq="iso";
                            currGov="isolated";
                        } else if( currOff != null && currOff.equals("0") ){
                            currFreq="off";
                            currGov="offline";
                        } else if(currFreq==null){
                            currFreq="0";
                            currGov="offline";
                        }

                        if( currGov == null ) currGov = "-";
                        sb.append(currFreq+":"+currGov+"|");
                    }
                    sb.deleteCharAt(sb.length()-1);
                    mHandler.sendMessage(mHandler.obtainMessage(1, sb.toString()));
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mNumCpus = getNumOfCpus();
        mCurrFreq = new String[mNumCpus];
        mCurrGov = new String[mNumCpus];

        CPU_TEMP_DIVIDER = getResources().getInteger(R.integer.config_cpuTempDivider);
        SYS_TEMP_DIVIDER = getResources().getInteger(R.integer.config_sysTempDivider);
        BAT_TEMP_DIVIDER = getResources().getInteger(R.integer.config_batTempDivider);

        CPU_TEMP_SENSOR = getResources().getString(R.string.config_cpuTempSensor);
        CPU_TEMP_SENSOR_PREFIX = getResources().getString(R.string.config_cpuTempSensorPrefix);
        CPU_TEMP_SENSOR_SUFFIX = getResources().getString(R.string.config_cpuTempSensorSuffix);
        CPU_TEMP_SENSOR_NUMBER = getResources().getString(R.string.config_cpuTempSensorNumber);
        CPU_TEMP_NUMBER = 0;


        SYS_TEMP_SENSOR = getResources().getString(R.string.config_sysTempSensor);
        BAT_TEMP_SENSOR = getResources().getString(R.string.config_batTempSensor);

        GPU_FREQ_SENSOR = getResources().getString(R.string.config_gpuFreqSensor);

        Log.e(TAG, "CPU_TEMP_SENSOR_PREFIX " + CPU_TEMP_SENSOR_PREFIX);
        Log.e(TAG, "CPU_TEMP_SENSOR_SUFFIX " + CPU_TEMP_SENSOR_SUFFIX);
        Log.e(TAG, "CPU_TEMP_SENSOR_NUMBER " + CPU_TEMP_SENSOR_NUMBER);

        Log.e(TAG, "CPU_TEMP_SENSOR " + CPU_TEMP_SENSOR);
        Log.e(TAG, "BAT_TEMP_SENSOR " + BAT_TEMP_SENSOR);
        Log.e(TAG, "SYS_TEMP_SENSOR " + SYS_TEMP_SENSOR);

        Log.e(TAG, "GPU_FREQ_SENSOR " + GPU_FREQ_SENSOR);

        if( !CPU_TEMP_SENSOR_PREFIX.equals("") ) {
            Log.e(TAG, "mCpuTemp using multi core sensor");
            TextUtils.StringSplitter mSplitter = new TextUtils.SimpleStringSplitter(',');
            mSplitter.setString(CPU_TEMP_SENSOR_NUMBER);
            int i=0;
            for(String profileString:mSplitter) {
                i++;
            }
            if( i > 0 )  {
                CPU_TEMP_NUMBERS = new String[i];
                mSplitter = new TextUtils.SimpleStringSplitter(',');
                mSplitter.setString(CPU_TEMP_SENSOR_NUMBER);
                i=0;
                for(String profileString:mSplitter) {
                    CPU_TEMP_NUMBERS[i++] = profileString;
                }
                CPU_TEMP_NUMBER = i;
                mCpuTempAvail = true;
            }
            Log.e(TAG, "mCpuTemp using " + CPU_TEMP_NUMBER + " cores");
        } else {
            Log.e(TAG, "mCpuTemp using single core sensor");
            mCpuTempAvail = readOneLine(CPU_TEMP_SENSOR) != null;
        }

        mSysTempAvail = readOneLine(SYS_TEMP_SENSOR) != null;
        mBatTempAvail = readOneLine(BAT_TEMP_SENSOR) != null;

        Log.e(TAG, "CPU_TEMP_NUMBER " + CPU_TEMP_NUMBER);
        Log.e(TAG, "mCpuTempAvail " + mCpuTempAvail);
        Log.e(TAG, "mSysTempAvail " + mSysTempAvail);
        Log.e(TAG, "mBatTempAvail " + mBatTempAvail);

        mView = new CPUView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        params.setTitle("CPU Info");

        startThread();

        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        DreamService mDreamService = (DreamService)getSystemService(DreamService.DREAM_SERVICE);

        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int top = wm.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.statusBars()).top;
        params.y = top;

        wm.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopThread();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(mView);
        mView = null;
        unregisterReceiver(mScreenStateReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static String readOneLine(String fname) {
        BufferedReader br;
        String line = null;
        try {
            br = new BufferedReader(new FileReader(fname), 512);
            try {
                line = br.readLine();
            } finally {
                br.close();
            }
        } catch (Exception e) {
            //Log.e(TAG, "Can't read: " + fname, e);
            return null;
        }
        return line;
    }

    private static String readOneProperty(String kname,String def) {
        try {
            return SystemProperties.get(kname,def);
        } catch (Exception e) {
            return def;
        }
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                //if (!isDozeMode()) {
                    if( mCurCPUThread == null ) startThread();
                    if( mView!= null ) mView.setVisibility(View.VISIBLE);
                //} else {
                    // Add stopThread for Doze after debug
                //}
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (!isDozeMode()) {
                    mView.setVisibility(View.GONE);
                    stopThread();
                }
            }
        }
    };

    private static int getNumOfCpus() {
        int numOfCpu = 1;
        String numOfCpus = readOneLine(NUM_OF_CPUS_PATH);
        String[] cpuCount = numOfCpus.split("-");
        if (cpuCount.length > 1) {
            try {
                int cpuStart = Integer.parseInt(cpuCount[0]);
                int cpuEnd = Integer.parseInt(cpuCount[1]);

                numOfCpu = cpuEnd - cpuStart + 1;

                if (numOfCpu < 0)
                    numOfCpu = 1;
            } catch (NumberFormatException ex) {
                numOfCpu = 1;
            }
        }
        return numOfCpu;
    }

    private boolean isDozeMode() {
        if (mDreamService != null && mDreamService.isDozing()) {
            return true;
        }
        return false;
    }

    
    private void startThread() {
        mCurCPUThread = new CurCPUThread(mView.getHandler(), mNumCpus);
        mCurCPUThread.start();
    }

    private void stopThread() {
        if (mCurCPUThread != null && mCurCPUThread.isAlive()) {
            mCurCPUThread.interrupt();
            try {
                mCurCPUThread.join();
            } catch (InterruptedException e) {
            }
        }
        mCurCPUThread = null;
    }
}
