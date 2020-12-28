/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.sprd.settings.wifi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.settings.R;

import java.nio.charset.Charset;

import android.os.SystemProperties;
import java.util.ArrayList;
//add for 5g beg
import android.provider.Settings;
import android.widget.SpinnerAdapter;
//add for 5g end

/**
 * Dialog to configure the SSID and security settings
 * for Access Point operation
 */
public class WifiApDialogFor5GChannel extends AlertDialog implements View.OnClickListener,
        TextWatcher, AdapterView.OnItemSelectedListener {

    static final int BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;

    private final DialogInterface.OnClickListener mListener;

    public static final int OPEN_INDEX = 0;
    //NOTE: Add security type of WPA PSK to SoftAP BEG-->
    //public static final int WPA2_INDEX = 1;
    public static final int WPA_INDEX = 1;
    //<-- Add security type of WPA PSK to SoftAP BEG Feature END
    public static final int WPA2_INDEX = 2;


    //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
    public static final int CHANNEL_DEFAULT = 11;
    public static final int DEFAULT_LIMIT = SystemProperties.getInt("ro.wifi.softap.maxstanum",8);
    //<-- Add for SoftAp Advance Feature EN

    private View mView;
    private TextView mSsid;
    private int mSecurityTypeIndex = OPEN_INDEX;
    private EditText mPassword;
    private int mBandIndex = OPEN_INDEX;

    //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
    private Spinner mSecurity;
    private Spinner mChannelPref;
    private Spinner mConnectedUserPref;
    private TextView mChannelLabel;
    private int mChannel = CHANNEL_DEFAULT;
    private int mConnectedUser = DEFAULT_LIMIT;
    private boolean hiddenSSID = false;
    //<-- Add for SoftAp Advance Feature END

    WifiConfiguration mWifiConfig;
    WifiManager mWifiManager;
    private Context mContext;
    //add for 5g beg
    private Spinner mChannelPreffor5G;
    private TextView mChannelLabelfor5G;
    private String mHostapdSupportChannels = null;
    private boolean mHideChannel = false;
    private int m5GChannels[] = null;
    private int m5GChannelsCount = 0;
    private String m5GChannelsString[] = null;
    private static final int MAX_CHANNEL_FOR_2G = 14;
    private static final int mSoftAPChoose5GEnabled = 1;
    //add for 5g end

    private static final String TAG = "WifiApDialogFor5GChannel";

    public WifiApDialogFor5GChannel(Context context, DialogInterface.OnClickListener listener,
            WifiConfiguration wifiConfig) {
        super(context);
        mListener = listener;
        mWifiConfig = wifiConfig;
        mContext =  context;
        if (wifiConfig != null) {
            mSecurityTypeIndex = getSecurityTypeIndex(wifiConfig);
            Log.d(TAG, "WifiApDialog; band = " + mWifiConfig.apBand + "; channel = " + mWifiConfig.apChannel);
        } else {
            Log.e(TAG, "wifiConfig == null");
        }
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public static int getSecurityTypeIndex(WifiConfiguration wifiConfig) {
        if (wifiConfig.allowedKeyManagement.get(KeyMgmt.WPA2_PSK)) {
            return WPA2_INDEX;
        //NOTE: Add security type of WPA PSK to SoftAP BEG-->
        } else if (wifiConfig.allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            return WPA_INDEX;
        //<-- Add security type of WPA PSK to SoftAP BEG Feature END

        }
        return OPEN_INDEX;
    }

    public WifiConfiguration getConfig() {

        WifiConfiguration config = new WifiConfiguration();

        /**
         * TODO: SSID in WifiConfiguration for soft ap
         * is being stored as a raw string without quotes.
         * This is not the case on the client side. We need to
         * make things consistent and clean it up
         */
        config.SSID = mSsid.getText().toString();
        config.apChannel = mChannel;
        config.softApMaxNumSta = mConnectedUser;

        config.apBand = mBandIndex;
        config.hiddenSSID = hiddenSSID;

        switch (mSecurityTypeIndex) {
            case OPEN_INDEX:
                config.allowedKeyManagement.set(KeyMgmt.NONE);
                return config;

            case WPA2_INDEX:
                config.allowedKeyManagement.set(KeyMgmt.WPA2_PSK);
                config.allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
                if (mPassword.length() != 0) {
                    String password = mPassword.getText().toString();
                    config.preSharedKey = password;
                }
                return config;
            //NOTE: Add security type of WPA PSK to SoftAP BEG-->
            case WPA_INDEX:
                config.allowedKeyManagement.set(KeyMgmt.WPA_PSK);
                config.allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
                if (mPassword.length() != 0) {
                    String password = mPassword.getText().toString();
                    config.preSharedKey = password;
                }
                return config;
            //<-- Add security type of WPA PSK to SoftAP BEG Feature END

        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean mInit = true;
        mView = getLayoutInflater().inflate(R.layout.wifi_ap_dialog_5G_channel, null);

        //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
        //Spinner mSecurity = ((Spinner) mView.findViewById(R.id.security));
        //final Spinner mChannel = (Spinner) mView.findViewById(R.id.choose_channel);
        mSecurity = ((Spinner) mView.findViewById(R.id.security));
        final Spinner mChannelBand = (Spinner) mView.findViewById(R.id.choose_channel);
        mChannelLabel = (TextView) mView.findViewById(R.id.wifi_channel_label);
        mChannelPref = ((Spinner) mView.findViewById(R.id.channel));
        mConnectedUserPref = ((Spinner) mView.findViewById(R.id.limit_user));
        if (SystemProperties.get("ro.softap.whitelist", "true").equals("false")) {
            mView.findViewById(R.id.limit_user_label).setVisibility(View.GONE);
            mConnectedUserPref.setVisibility(View.GONE);
        }
        //<-- Add for SoftAp Advance Feature END
        //add for 5g beg
        mChannelLabelfor5G = (TextView) mView.findViewById(R.id.wifi_channel_label_for_5G);
        mChannelPreffor5G = ((Spinner) mView.findViewById(R.id.channel_for_5G));
        mHostapdSupportChannels = Settings.Global.getString(
            mContext.getContentResolver(), Settings.Global.SOFTAP_SUPPORT_CHANNELS);
        Log.d(TAG, "mHostapdSupportChannels " + mHostapdSupportChannels);
        if (mHostapdSupportChannels != null && mHostapdSupportChannels.contains(",")) {
            m5GChannelsString = mHostapdSupportChannels.split(",");
            m5GChannels = stringArrayToIntArray(m5GChannelsString);
        } else {
            m5GChannels = null;
        }
        //add for 5g end

        mHideChannel = SystemProperties.get("ro.softaplte.coexist","true").equals("true");


        setView(mView);
        setInverseBackgroundForced(true);

        Context context = getContext();

        setTitle(R.string.wifi_tether_configure_ap_text);
        mView.findViewById(R.id.type).setVisibility(View.VISIBLE);
        mSsid = (TextView) mView.findViewById(R.id.ssid);
        mPassword = (EditText) mView.findViewById(R.id.password);

        ArrayAdapter <CharSequence> channelAdapter;
        String countryCode = mWifiManager.getCountryCode();
        if (!mWifiManager.isDualBandSupported()) {
            //If no country code, 5GHz AP is forbidden
            Log.i(TAG,(!mWifiManager.isDualBandSupported() ? "Device do not support 5GHz " :"") 
                    + (countryCode == null ? " NO country code" :"") +  " forbid 5GHz");
            channelAdapter = ArrayAdapter.createFromResource(mContext,
                    R.array.wifi_ap_band_config_2G_only, android.R.layout.simple_spinner_item);
            mWifiConfig.apBand = 0;
            mBandIndex = 0;
            if (mWifiConfig.apChannel > MAX_CHANNEL_FOR_2G) {
                mWifiConfig.apChannel = CHANNEL_DEFAULT;
                mChannelPref.setSelection(CHANNEL_DEFAULT-1);
            } else {
                mChannelPref.setSelection(mWifiConfig.apChannel-1);
            }
        } else {
            channelAdapter = ArrayAdapter.createFromResource(mContext,
                    R.array.wifi_ap_band_config_full, android.R.layout.simple_spinner_item);
            ArrayList channelList_5G = new ArrayList<CharSequence>();
            channelList_5G.add("Auto");
            m5GChannelsCount++;
            if (mHostapdSupportChannels != null && m5GChannels != null) {
                for (int i = 0 ; i < m5GChannels.length; i++) {
                    channelList_5G.add(m5GChannels[i]);
                    m5GChannelsCount++;
                }
            }
            ArrayAdapter adapter_for_5G = new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_item,channelList_5G);
            adapter_for_5G.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mChannelPreffor5G.setAdapter(adapter_for_5G);
        }
        if (mHideChannel) {
            mChannelLabel.setVisibility(View.GONE);
            mChannelPref.setVisibility(View.GONE);
        }
        //add for 5g end

    channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
        if (DEFAULT_LIMIT  != 8) {
            ArrayList<CharSequence>  arraySoftApMaxNum = new ArrayList<CharSequence>();
            for (int i = 1; i<= DEFAULT_LIMIT; i++) {
                arraySoftApMaxNum.add(i+"");
            }
            ArrayAdapter<CharSequence> mSoftApMaxNumStaAdapter = new ArrayAdapter<CharSequence>(context,android.R.layout.simple_list_item_1,arraySoftApMaxNum) ;
            mConnectedUserPref.setAdapter(mSoftApMaxNumStaAdapter);
        }
        //<-- Add for SoftAp Advance Feature END

        setButton(BUTTON_SUBMIT, context.getString(R.string.wifi_save), mListener);
        setButton(DialogInterface.BUTTON_NEGATIVE,
        context.getString(R.string.wifi_cancel), mListener);

        if (mWifiConfig != null) {
            mSsid.setText(mWifiConfig.SSID);
            //add for 5g beg
            mBandIndex = mWifiConfig.apBand;
            mChannel = mWifiConfig.apChannel;
            //add for 5g end

            hiddenSSID = mWifiConfig.hiddenSSID;

            mSecurity.setSelection(mSecurityTypeIndex);
            //add for 5g beg
            Log.d(TAG, "mBandIndex = " + mBandIndex +  "; mWifiConfig.apChannel = " + mWifiConfig.apChannel);
            if (mBandIndex == 0) {
                if (mWifiConfig.apChannel > MAX_CHANNEL_FOR_2G) {
                    mChannelPref.setSelection(CHANNEL_DEFAULT-1);
                } else {
                    mChannelPref.setSelection(mWifiConfig.apChannel-1);
                }
            } else {
                if (mWifiConfig.apChannel > MAX_CHANNEL_FOR_2G && m5GChannelsCount > 1) {
                    mChannelPreffor5G.setSelection(get5GChannelAdapterNum(mWifiConfig.apChannel) + 1);
                } else if (mWifiConfig.apChannel == 0) {
                    mChannelPreffor5G.setSelection(0);
                }
            }

            //add for 5g end
            //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
            //Correct the max num, beacuse default value in WifiConfiguration is 8.
            //But this value may be changed by ro.ro.wifi.softap.maxstanum
            if (mWifiConfig.softApMaxNumSta > DEFAULT_LIMIT) {
                mWifiConfig.softApMaxNumSta = DEFAULT_LIMIT;
            }
            mChannelPref.setSelection(mWifiConfig.apChannel-1);
            mConnectedUserPref.setSelection(mWifiConfig.softApMaxNumSta-1);
            //<-- Add for SoftAp Advance Feature END
            if (mSecurityTypeIndex == WPA2_INDEX || mSecurityTypeIndex == WPA_INDEX) {
                mPassword.setText(mWifiConfig.preSharedKey);
            }
              //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
            } else {
               mChannelPref.setSelection(CHANNEL_DEFAULT-1);
               mConnectedUserPref.setSelection(DEFAULT_LIMIT-1);
            }
            //add for 5g beg
            if (mBandIndex == 1) {
               mChannelPref.setVisibility(View.GONE);
               mChannelLabel.setVisibility(View.GONE);
               mChannelPreffor5G.setVisibility(View.VISIBLE);
               mChannelLabelfor5G.setVisibility(View.VISIBLE);
            } else {
               mChannelPref.setVisibility(View.VISIBLE);
               mChannelLabel.setVisibility(View.VISIBLE);
               mChannelPreffor5G.setVisibility(View.GONE);
               mChannelLabelfor5G.setVisibility(View.GONE);
            }
            if (mHideChannel) {
                mChannel = 0;
                mChannelPref.setVisibility(View.GONE);
                mChannelLabel.setVisibility(View.GONE);
            }
            //add for 5g end
            //mChannel.setAdapter(channelAdapter);
            //mChannel.setOnItemSelectedListener(
            mChannelBand.setAdapter(channelAdapter);
            mChannelBand.setOnItemSelectedListener(
            //<-- Add for SoftAp Advance Feature END
                new AdapterView.OnItemSelectedListener() {
                    boolean mInit = true;
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                                               long id) {
                        if (!mInit) {
                            mBandIndex = position;
                            Log.i(TAG, " Select band " + position);
                        } else {
                            mInit = false;
                            //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
                            //mChannel.setSelection(mBandIndex);
                            mChannelBand.setSelection(mBandIndex);
                            //<-- Add for SoftAp Advance Feature END
                        }
                        //add for 5g beg
                        if (mBandIndex == 1) {
                            mChannelPref.setVisibility(View.GONE);
                            mChannelLabel.setVisibility(View.GONE);
                            mChannelPreffor5G.setVisibility(View.VISIBLE);
                            mChannelLabelfor5G.setVisibility(View.VISIBLE);
                        } else {
                            mChannelPref.setVisibility(View.VISIBLE);
                            mChannelLabel.setVisibility(View.VISIBLE);
                            mChannelPreffor5G.setVisibility(View.GONE);
                            mChannelLabelfor5G.setVisibility(View.GONE);
                        }
                        if (mHideChannel) {
                            mChannel = 0;
                            mChannelPref.setVisibility(View.GONE);
                            mChannelLabel.setVisibility(View.GONE);
                        }
                        //add for 5g end

                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                }
        );

        mSsid.addTextChangedListener(this);
        mPassword.addTextChangedListener(this);
        ((CheckBox) mView.findViewById(R.id.show_password)).setOnClickListener(this);
        mSecurity.setOnItemSelectedListener(this);
        //add for 5g beg
        mChannelPreffor5G.setOnItemSelectedListener(this);
        //add for 5g end
        //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
        mChannelPref.setOnItemSelectedListener(this);
        mConnectedUserPref.setOnItemSelectedListener(this);
        //<-- Add for SoftAp Advance Feature END

        super.onCreate(savedInstanceState);

        showSecurityFields();
        validate();
    }

    //add for 5g beg
    private int[] stringArrayToIntArray(String[] array) {
        try {
             int[] intArray = new int[array.length];
             for (int i = 0; i < array.length; i++) {
                 intArray[i] = Integer.parseInt(array[i]);
             }
             return intArray;
        } catch (Exception e) {
            return null;
        }
    }

    private int get5GChannelAdapterNum (int channel) {
        if (m5GChannels != null) {
            for (int i = 0; i < m5GChannels.length; i++) {
                if (m5GChannels[i] == channel) {
                    return i;
                }
            }
        }
        return 0;
    }
    //add for 5g end

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mPassword.setInputType(
                InputType.TYPE_CLASS_TEXT |
                (((CheckBox) mView.findViewById(R.id.show_password)).isChecked() ?
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                InputType.TYPE_TEXT_VARIATION_PASSWORD));
    }

    private void validate() {
        String mSsidString = mSsid.getText().toString();
        if ((mSsid != null && mSsid.length() == 0)
                || ((mSecurityTypeIndex == WPA2_INDEX || mSecurityTypeIndex == WPA_INDEX) && mPassword.length() < 8)
                || (mSsid != null &&
                Charset.forName("UTF-8").encode(mSsidString).limit() > 32)) {
            getButton(BUTTON_SUBMIT).setEnabled(false);
        } else {
            getButton(BUTTON_SUBMIT).setEnabled(true);
        }
    }

    public void onClick(View view) {
        mPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | (((CheckBox) view).isChecked() ?
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                InputType.TYPE_TEXT_VARIATION_PASSWORD));
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void afterTextChanged(Editable editable) {
        validate();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
        //mSecurityTypeIndex = position;
        //showSecurityFields();
        if (parent == mSecurity) {
            mSecurityTypeIndex = position;
            showSecurityFields();
         } else if (parent == mChannelPref) {
            mChannel = position+1;
        } else if (parent == mConnectedUserPref) {
             mConnectedUser = position+1;
        } else if (parent == mChannelPreffor5G) {
          if (position == 0) {
                mChannel = 0;
            } else if (position > 0) {
                if (m5GChannels != null) {
                    mChannel = m5GChannels[position-1];
                }
            }
            Log.d(TAG, "5G channel item select position " + position + "; mChannel = " + mChannel);
        }
        //<-- Add for SoftAp Advance Feature END
        validate();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void showSecurityFields() {
        if (mSecurityTypeIndex == OPEN_INDEX) {
            mView.findViewById(R.id.fields).setVisibility(View.GONE);
            return;
        }
        mView.findViewById(R.id.fields).setVisibility(View.VISIBLE);
    }
}
