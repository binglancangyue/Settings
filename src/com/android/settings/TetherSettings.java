/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserManager;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.ListPreference;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.datausage.DataSaverBackend;
import com.android.settings.wifi.WifiApDialog;
import com.android.settings.wifi.WifiApEnabler;
import com.android.settings.wifi.tether.WifiTetherPreferenceController;
import com.android.settings.wifi.tether.WifiTetherSettings;
import com.android.settingslib.TetherUtil;
import com.sprd.settings.SprdChooseOS;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static android.net.ConnectivityManager.TETHERING_BLUETOOTH;
import static android.net.ConnectivityManager.TETHERING_USB;
import static android.net.ConnectivityManager.TETHERING_WIFI;

import android.os.SystemProperties;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends RestrictedSettingsFragment
        implements DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener,
        DataSaverBackend.Listener {

    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private static final String ENABLE_BLUETOOTH_TETHERING = "enable_bluetooth_tethering";
    private static final String DATA_SAVER_FOOTER = "disabled_on_data_saver";
    /* SPRD:Add for 692657 androido porting :pc net share @{ */
    private static final String USB_PC_SHARE_SETTINGS = "usb_pc_share_settings";
    private static final boolean SUPPORT_USB_REVERSE_TETHER = SystemProperties.getBoolean("persist.sys.usb-pc.tethering",true);
    private static final int PROVISION_REQUEST_USB_PC_TETHER = 1;
    public static final int TETHERING_INVALID   = -1;
    /* Bug692657 end @} */

    private static final int DIALOG_AP_SETTINGS = 1;

    private static final String TAG = "TetheringSettings";

    private SwitchPreference mUsbTether;

    private WifiApEnabler mWifiApEnabler;
    private SwitchPreference mEnableWifiAp;

    private SwitchPreference mBluetoothTether;

    private ListPreference mHotspotKeepOn;
    private String[] mSummaries;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;

    private String[] mWifiRegexs;

    private String[] mBluetoothRegexs;
    private AtomicReference<BluetoothPan> mBluetoothPan = new AtomicReference<BluetoothPan>();

    private Handler mHandler = new Handler();
    private OnStartTetheringCallback mStartTetheringCallback;

    private static final String WIFI_AP_SSID_AND_SECURITY = "wifi_ap_ssid_and_security";
    private static final int CONFIG_SUBTEXT = R.string.wifi_tether_configure_subtext;

    //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
    private static final String HOTSPOT_SETTINGS = "hotspot_settings";
    //<-- Add for SoftAp Advance Feature END
    public static final int WIFI_SOFT_AP_SLEEP_POLICY_NEVER = 0;
    private static final String HOTSPOT_KEEP_WIFI_HOTSPOT_ON = "soft_ap_sleep_policy";
    public static final String WIFI_SOFT_AP_SLEEP_POLICY = "wifi_soft_ap_sleep_policy_key";
    private String[] mSecurityType;
    private Preference mCreateNetwork;

    private WifiApDialog mDialog;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConfig = null;
    private ConnectivityManager mCm;

    private WifiTetherPreferenceController mWifiTetherPreferenceController;

    private boolean mRestartWifiApAfterConfigChange;

    private boolean mUsbConnected;
    private boolean mBluetoothEnableForTether;
    private boolean mUnavailable;

    private DataSaverBackend mDataSaverBackend;
    private boolean mDataSaverEnabled;
    private Preference mDataSaverFooter;

    /* SPRD:Add for 692657 androido porting :pc net share @{ */
    private SwitchPreference mUsbPcShare;
    private int mTetherChoice = TETHERING_INVALID;
    /* Bug692657 end @}*/

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.TETHER;
    }

    public TetherSettings() {
        super(UserManager.DISALLOW_CONFIG_TETHERING);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mWifiTetherPreferenceController =
                new WifiTetherPreferenceController(context, getLifecycle());
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.tether_prefs);
        mFooterPreferenceMixin.createFooterPreference()
            .setTitle(R.string.tethering_footer_info);

        mDataSaverBackend = new DataSaverBackend(getContext());
        mDataSaverEnabled = mDataSaverBackend.isDataSaverEnabled();
        mDataSaverFooter = findPreference(DATA_SAVER_FOOTER);
        mHotspotKeepOn = (ListPreference) findPreference(HOTSPOT_KEEP_WIFI_HOTSPOT_ON);
        mSummaries = getResources().getStringArray(R.array.soft_ap_sleep_policy_entries);

        setIfOnlyAvailableForAdmins(true);
        if (isUiRestricted()) {
            mUnavailable = true;
            getPreferenceScreen().removeAll();
            return;
        }

        final Activity activity = getActivity();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
         // SPRD: Modify for Bug#875145. Get PAN proxy just when Bluetooth is ON.
        if (adapter != null &&adapter.getState() == BluetoothAdapter.STATE_ON) {
            adapter.getProfileProxy(activity.getApplicationContext(), mProfileServiceListener,
                    BluetoothProfile.PAN);
        }

        mEnableWifiAp =
                (SwitchPreference) findPreference(ENABLE_WIFI_AP);

        Preference wifiApSettings = findPreference(WIFI_AP_SSID_AND_SECURITY);
        mUsbTether = (SwitchPreference) findPreference(USB_TETHER_SETTINGS);
        // SPRD:Add for bug692657 androido porting pc net share
        mUsbPcShare = (SwitchPreference) findPreference(USB_PC_SHARE_SETTINGS);

        mBluetoothTether = (SwitchPreference) findPreference(ENABLE_BLUETOOTH_TETHERING);

        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        mUsbRegexs = mCm.getTetherableUsbRegexs();
        mWifiRegexs = mCm.getTetherableWifiRegexs();
        mBluetoothRegexs = mCm.getTetherableBluetoothRegexs();

        mDataSaverBackend.addListener(this);
        mHotspotKeepOn.setOnPreferenceChangeListener(this);
        int value = Settings.System.getInt(getActivity().getContentResolver(),WIFI_SOFT_AP_SLEEP_POLICY,
                    WIFI_SOFT_AP_SLEEP_POLICY_NEVER);
        String stringValue = String.valueOf(value);
        mHotspotKeepOn.setValue(stringValue);
        mHotspotKeepOn.setSummary(mSummaries[value]);
        final boolean usbAvailable = mUsbRegexs.length != 0;
        final boolean wifiAvailable = mWifiRegexs.length != 0;
        final boolean bluetoothAvailable = mBluetoothRegexs.length != 0;

        if (!usbAvailable || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(mUsbTether);
            /* SPRD:Add for bug692657 androido porting pc net share */
            getPreferenceScreen().removePreference(mUsbPcShare);
        }

        if (!SUPPORT_USB_REVERSE_TETHER && mUsbPcShare != null){
            getPreferenceScreen().removePreference(mUsbPcShare);
        }
        /* Bug692657 end @ } */

        mWifiTetherPreferenceController.displayPreference(getPreferenceScreen());
        if (WifiTetherSettings.isTetherSettingPageEnabled()) {
            removePreference(ENABLE_WIFI_AP);
            removePreference(WIFI_AP_SSID_AND_SECURITY);
            getPreferenceScreen().removePreference(findPreference(HOTSPOT_SETTINGS));
        } else {
            if (wifiAvailable && !Utils.isMonkeyRunning()) {
                mWifiApEnabler = new WifiApEnabler(activity, mDataSaverBackend, mEnableWifiAp);
                //NOTE: Bug #692685 Add For SoftAp advance Feature BEG-->
                //initWifiTethering();
                if (SystemProperties.getInt("ro.hotspot.enabled", 1) == 0) {
                    initWifiTethering();
                    getPreferenceScreen().removePreference(findPreference(HOTSPOT_SETTINGS));
                } else {
                    getPreferenceScreen().removePreference(wifiApSettings);
                    getPreferenceScreen().removePreference(mHotspotKeepOn);
                }
                //<-- Add for SoftAp Advance Feature END
            } else {
                getPreferenceScreen().removePreference(mEnableWifiAp);
                getPreferenceScreen().removePreference(wifiApSettings);
                getPreferenceScreen().removePreference(findPreference(HOTSPOT_SETTINGS));
            }
        }

        if (!bluetoothAvailable) {
            getPreferenceScreen().removePreference(mBluetoothTether);
        } else {
            BluetoothPan pan = mBluetoothPan.get();
            if (pan != null && pan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
            } else {
                mBluetoothTether.setChecked(false);
            }
        }
        // Set initial state based on Data Saver mode.
        onDataSaverChanged(mDataSaverBackend.isDataSaverEnabled());
    }

    @Override
    public void onDestroy() {
        mDataSaverBackend.remListener(this);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothProfile profile = mBluetoothPan.getAndSet(null);
        if (profile != null && adapter != null) {
            adapter.closeProfileProxy(BluetoothProfile.PAN, profile);
        }

        super.onDestroy();
    }

    @Override
    public void onDataSaverChanged(boolean isDataSaving) {
        mDataSaverEnabled = isDataSaving;
        mEnableWifiAp.setEnabled(!mDataSaverEnabled);
        /* SPRD:Add for bug692657 androido porting pc net share @{ */
        mUsbTether.setEnabled(!mDataSaverEnabled && mUsbConnected && !mUsbPcShare.isChecked());
        if (mDataSaverEnabled) {
            mTetherChoice = TETHERING_INVALID;
            updateState();
        }
        /* Bug692657 end @} */
        mBluetoothTether.setEnabled(!mDataSaverEnabled);
        mDataSaverFooter.setVisible(mDataSaverEnabled);
        Log.d(TAG, mDataSaverEnabled ? "Can't tether or use portable hotspots while Data Saver is on" : "DataSaver is off");
    }

    @Override
    public void onWhitelistStatusChanged(int uid, boolean isWhitelisted) {
    }

    @Override
    public void onBlacklistStatusChanged(int uid, boolean isBlacklisted)  {
    }

    private void initWifiTethering() {
        final Activity activity = getActivity();
        mWifiConfig = mWifiManager.getWifiApConfiguration();
        mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);

        mCreateNetwork = findPreference(WIFI_AP_SSID_AND_SECURITY);

        mRestartWifiApAfterConfigChange = false;

        if (mWifiConfig == null) {
            final String s = activity.getString(
                    com.android.internal.R.string.wifi_tether_configure_ssid_default);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    s, mSecurityType[WifiApDialog.OPEN_INDEX]));
        } else {
            int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    mWifiConfig.SSID,
                    mSecurityType[index]));
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_AP_SETTINGS) {
            final Activity activity = getActivity();
            mDialog = new WifiApDialog(activity, this, mWifiConfig);
            return mDialog;
        }

        return null;
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (dialogId == DIALOG_AP_SETTINGS) {
            return MetricsEvent.DIALOG_AP_SETTINGS;
        }
        return 0;
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateState(available.toArray(new String[available.size()]),
                        active.toArray(new String[active.size()]),
                        errored.toArray(new String[errored.size()]));
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange) {
                    mRestartWifiApAfterConfigChange = false;
                    Log.d(TAG, "Restarting WifiAp due to prior config change.");
                    startTethering(TETHERING_WIFI);
                }
            } else if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, 0);
                /* SPRD:Add for bug692657 androido porting pc net share @{ */
                if (state == WifiManager.WIFI_AP_STATE_DISABLED && mTetherChoice == TETHERING_WIFI) {
                    mTetherChoice = TETHERING_INVALID;
                }
                /* Bug692657 end @} */
                if (state == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange) {
                    mRestartWifiApAfterConfigChange = false;
                    Log.d(TAG, "Restarting WifiAp due to prior config change.");
                    startTethering(TETHERING_WIFI);
                }
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                boolean rndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_RNDIS, false);
                /* SPRD:Add for bug692657 androido porting pc net share @{ */
                if (mUsbConnected == false || (mUsbConnected && !rndisEnabled) && mTetherChoice == TETHERING_USB) {
                    mTetherChoice = TETHERING_INVALID;
                }
                /* Bug692657 end @} */
                updateState();
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                  // SPRD: Modify for Bug#875145. Get PAN proxy just when Bluetooth is ON. START->
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON && mBluetoothPan.get() == null) {
                    Log.d(TAG, "recevie ACTION_STATE_CHANGED, getProfieProxy PAN");
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter != null)  {
                        adapter.getProfileProxy(content.getApplicationContext(), mProfileServiceListener,
                                  BluetoothProfile.PAN);
                    }
                }
                // <- END
                if (mBluetoothEnableForTether) {
                    switch (state) {
                        case BluetoothAdapter.STATE_ON:
                            startTethering(TETHERING_BLUETOOTH);
                            mBluetoothEnableForTether = false;
                            break;

                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.ERROR:
                            mBluetoothEnableForTether = false;
                            break;

                        default:
                            // ignore transition states
                    }
                }
                /* SPRD:Add for bug692657 androido porting pc net share @{ */
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_OFF &&
                        mTetherChoice == TETHERING_BLUETOOTH) {
                    mTetherChoice = TETHERING_INVALID;
                }
                /* Bug692657 end @} */
                updateState();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mUnavailable) {
            if (!isUiRestrictedByOnlyAdmin()) {
                getEmptyTextView().setText(R.string.tethering_settings_not_available);
            }
            getPreferenceScreen().removeAll();
            return;
        }

        final Activity activity = getActivity();

        mStartTetheringCallback = new OnStartTetheringCallback(this);

        mTetherChangeReceiver = new TetherChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        Intent intent = activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        if (intent != null) mTetherChangeReceiver.onReceive(activity, intent);
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(this);
            mWifiApEnabler.resume();
        }

        updateState();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mUnavailable) {
            return;
        }
        getActivity().unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        mStartTetheringCallback = null;
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(null);
            mWifiApEnabler.pause();
        }
    }

    private void updateState() {
        String[] available = mCm.getTetherableIfaces();
        String[] tethered = mCm.getTetheredIfaces();
        String[] errored = mCm.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }
    /* SPRD:Add for bug692657 androido porting pc net share @} @{ */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (requestCode == PROVISION_REQUEST_USB_PC_TETHER) {
            if (resultCode == Activity.RESULT_OK) {
                setInternetShare(true, data.getStringExtra("usb_rndis_ip_address"));
            }
        }
    }
    /* Bug692657 end @} */

    private void updateState(String[] available, String[] tethered,
            String[] errored) {
        updateUsbState(available, tethered, errored);
        updateBluetoothState();
    }


    private void updateUsbState(String[] available, String[] tethered,
            String[] errored) {
        boolean usbAvailable = mUsbConnected;
        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        for (String s : available) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        usbError = mCm.getLastTetherError(s);
                    }
                }
            }
        }
        boolean usbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbTethered = true;
            }
        }
        boolean usbErrored = false;
        for (String s: errored) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbErrored = true;
            }
        }
        Log.d(TAG, "updateUsbState: " + "usbTethered:" + usbTethered + " usbAvailable: " + usbAvailable + " mCm.getPCNetTether(): " + mCm.getPCNetTether());

        /* SPRD:Add for bug692657 androido porting pc net share @} */
        if (mCm.getPCNetTether()) {
            mUsbTether.setEnabled(false);
            mUsbPcShare.setEnabled(true);
            mUsbPcShare.setChecked(true);
        } else if (usbTethered) {
            mUsbTether.setEnabled(!mDataSaverEnabled);
            mUsbTether.setChecked(true);
            mUsbPcShare.setEnabled(false);
            mUsbPcShare.setChecked(false);
        } else if (usbAvailable) {
            mUsbTether.setEnabled(!mDataSaverEnabled);
            mUsbTether.setChecked(false);
            mUsbPcShare.setEnabled(mTetherChoice!=TETHERING_USB);
            mUsbPcShare.setChecked(false);
        } else {
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            mUsbPcShare.setEnabled(false);
            mUsbPcShare.setChecked(false);
            /* Bug692657 end @} */
        }
    }

    private void updateBluetoothState() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        int btState = adapter.getState();
        if (btState == BluetoothAdapter.STATE_TURNING_OFF) {
            mBluetoothTether.setEnabled(false);
        } else if (btState == BluetoothAdapter.STATE_TURNING_ON) {
            mBluetoothTether.setEnabled(false);
        } else {
            BluetoothPan bluetoothPan = mBluetoothPan.get();
            mBluetoothTether.setSummary(
                                        R.string.bluetooth_tethering_subtext);

	    if (btState == BluetoothAdapter.STATE_ON && bluetoothPan != null
                    && bluetoothPan.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
                mBluetoothTether.setEnabled(!mDataSaverEnabled);
            } else {
                mBluetoothTether.setEnabled(!mDataSaverEnabled);
                mBluetoothTether.setChecked(false);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String key = preference.getKey();
        if (HOTSPOT_KEEP_WIFI_HOTSPOT_ON.equals(key)) {
            try {
                int newValue = Integer.parseInt((String) value);
                Settings.System.putInt(getActivity().getContentResolver(),WIFI_SOFT_AP_SLEEP_POLICY,newValue);
                mHotspotKeepOn.setValue((String) value);
                mHotspotKeepOn.setSummary(mSummaries[newValue]);
            } catch (IllegalArgumentException e) {
            }
            return false;
        }
        boolean enable = (Boolean) value;
        if (enable) {
            startTethering(TETHERING_WIFI);
        } else {
            mCm.stopTethering(TETHERING_WIFI);
            // SPRD:Add for bug692657 androido porting pc net share
            mTetherChoice = TETHERING_INVALID;
        }
        return false;
    }

    public static boolean isProvisioningNeededButUnavailable(Context context) {
        return (TetherUtil.isProvisioningNeeded(context)
                && !isIntentAvailable(context));
    }

    private static boolean isIntentAvailable(Context context) {
        String[] provisionApp = context.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
        if (provisionApp.length < 2) {
            return false;
        }
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(provisionApp[0], provisionApp[1]);

        return (packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).size() > 0);
    }

    private void startTethering(int choice) {
        /* SPRD:Add for bug692657 androido porting pc net share @} */
        if (choice == TETHERING_WIFI || choice == TETHERING_BLUETOOTH) {
            mTetherChoice =choice;
        }
        /* Bug692657 end @} */
        if (choice == TETHERING_BLUETOOTH) {
            // Turn on Bluetooth first.
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                mBluetoothEnableForTether = true;
                adapter.enable();
                mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
                mBluetoothTether.setEnabled(false);
                return;
            }
        }

        mCm.startTethering(choice, true, mStartTetheringCallback, mHandler);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mUsbTether) {
            if (mUsbTether.isChecked()) {
                mUsbPcShare.setEnabled(false);
                mTetherChoice = TETHERING_USB;
                startTethering(TETHERING_USB);
            } else {
                mTetherChoice = TETHERING_INVALID;
                mCm.stopTethering(TETHERING_USB);
            }
        } else if (preference == mBluetoothTether) {
            if (mBluetoothTether.isChecked()) {
                startTethering(TETHERING_BLUETOOTH);
            } else {
                mCm.stopTethering(TETHERING_BLUETOOTH);
                // No ACTION_TETHER_STATE_CHANGED is fired or bluetooth unless a device is
                // connected. Need to update state manually.
                updateState();
            }
           /* SPRD:Add for bug692657 androido porting pc net share @} */
        } else if (preference == mUsbPcShare) {
            if (!mUsbPcShare.isChecked()) {
                setInternetShare(false, null);
            } else {
                mUsbPcShare.setEnabled(false);
                Intent chooseOSIntent = new Intent(getActivity(),SprdChooseOS.class);
                startActivityForResult(chooseOSIntent, PROVISION_REQUEST_USB_PC_TETHER);
            }
            /* Bug692657 end@} */
        } else if (preference == mCreateNetwork) {
            showDialog(DIALOG_AP_SETTINGS);
        }
        return super.onPreferenceTreeClick(preference);
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            mWifiConfig = mDialog.getConfig();
            if (mWifiConfig != null) {
                /**
                 * if soft AP is stopped, bring up
                 * else restart with new config
                 * TODO: update config on a running access point when framework support is added
                 */
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
                    Log.d("TetheringSettings",
                            "Wifi AP config changed while enabled, stop and restart");
                    mRestartWifiApAfterConfigChange = true;
                    mCm.stopTethering(TETHERING_WIFI);
                }
                mWifiManager.setWifiApConfiguration(mWifiConfig);
                int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
                mCreateNetwork.setSummary(String.format(getActivity().getString(CONFIG_SUBTEXT),
                        mWifiConfig.SSID,
                        mSecurityType[index]));
            }
        }
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_tether;
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothPan.set((BluetoothPan) proxy);
        }
        public void onServiceDisconnected(int profile) {
            mBluetoothPan.set(null);
        }
    };

    private static final class OnStartTetheringCallback extends
            ConnectivityManager.OnStartTetheringCallback {
        final WeakReference<TetherSettings> mTetherSettings;

        OnStartTetheringCallback(TetherSettings settings) {
            mTetherSettings = new WeakReference<TetherSettings>(settings);
        }

        @Override
        public void onTetheringStarted() {
            update();
        }

        @Override
        public void onTetheringFailed() {
            /* SPRD:Add for bug692657 androido porting pc net share @} */
            TetherSettings settings = mTetherSettings.get();
            if(TETHERING_USB == settings.mTetherChoice){
                settings.mTetherChoice = TETHERING_INVALID;
            }
            /* Bug692657 end @} */
            update();
        }

        private void update() {
            TetherSettings settings = mTetherSettings.get();
            if (settings != null) {
                settings.updateState();
            }
        }
    }

    /* SPRD:Add for bug692657 androido porting pc net share @{ */
    private void setInternetShare(boolean isShared, String ip) {
        int result = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        Log.d(TAG, "setInternetShare(" + isShared +"," + " " + ip + ")");
        if(isShared) {
            mWifiManager.setWifiEnabled(false);
            result = mCm.enableTetherPCInternet(ip);
        } else {
            result = mCm.disableTetherPCInternet();
        }
    }
    /* Bug692657 end @} */
}
