/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.Phone;
import com.android.settings.widget.SwitchBar;
import android.provider.SettingsEx;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.CarrierConfigManagerEx;

/**
 * "Wi-Fi Calling settings" screen.  This preference screen lets you
 * enable/disable Wi-Fi Calling and change Wi-Fi Calling mode.
 */
public class WifiCallingSettings extends SettingsPreferenceFragment
        implements SwitchBar.OnSwitchChangeListener,
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "WifiCallingSettings";

    //String keys for preference lookup
    private static final String BUTTON_WFC_MODE = "wifi_calling_mode";
    private static final String BUTTON_WFC_ROAMING_MODE = "wifi_calling_roaming_mode";
    private static final String PREFERENCE_EMERGENCY_ADDRESS = "emergency_address_key";

    private static final int REQUEST_CHECK_WFC_EMERGENCY_ADDRESS = 1;

    public static final String EXTRA_LAUNCH_CARRIER_APP = "EXTRA_LAUNCH_CARRIER_APP";

    public static final int LAUCH_APP_ACTIVATE = 0;
    public static final int LAUCH_APP_UPDATE = 1;

    //UI objects
    private SwitchBar mSwitchBar;
    private Switch mSwitch;
    private ListPreference mButtonWfcMode;
    private ListPreference mButtonWfcRoamingMode;
    private Preference mUpdateAddress;
    private TextView mEmptyView;

    private boolean mValidListener = false;
    private boolean mEditableWfcMode = true;
    private boolean mEditableWfcRoamingMode = true;
    private boolean mShowWfcOnNotification = false;
    // SPRD: add for bug750594
    private boolean mRemoveWfcPref = false;
    private boolean mShowWfcPref = true;

    private ContentObserver mWfcEnableObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            /* SPRD: add for bug732845 @{ */
            Log.d(TAG,"[mWfcEnableObserver] getActivity: " + getActivity());
            if (getActivity() == null) {
                return;
            }
            /* @} */
            boolean enabled = ImsManager.isWfcEnabledByUser(getActivity())
                    && ImsManager.isNonTtyOrTtyOnVolteEnabled(getActivity());
            Log.d(TAG,"[mWfcEnableObserver][wfcEnabled]: " + enabled);
            mSwitchBar.setChecked(enabled);
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /*
         * Enable/disable controls when in/out of a call and depending on
         * TTY mode and TTY support over VoLTE.
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            final SettingsActivity activity = (SettingsActivity) getActivity();
            /* SPRD: add for bug717903 @{ */
            if (activity == null) {
                return;
            }
            /* @} */
            boolean isNonTtyOrTtyOnVolteEnabled = ImsManager
                    .isNonTtyOrTtyOnVolteEnabled(activity);
            final SwitchBar switchBar = activity.getSwitchBar();
            boolean isWfcEnabled = switchBar.getSwitch().isChecked()
                    && isNonTtyOrTtyOnVolteEnabled;

            switchBar.setEnabled((state == TelephonyManager.CALL_STATE_IDLE)
                    && isNonTtyOrTtyOnVolteEnabled);

            boolean isWfcModeEditable = true;
            boolean isWfcRoamingModeEditable = false;
            final CarrierConfigManager configManager = (CarrierConfigManager)
                    activity.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            if (configManager != null) {
                PersistableBundle b = configManager.getConfig();
                if (b != null) {
                    isWfcModeEditable = b.getBoolean(
                            CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL);
                    isWfcRoamingModeEditable = b.getBoolean(
                            CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL);
                }
            }

            Preference pref = getPreferenceScreen().findPreference(BUTTON_WFC_MODE);
            if (pref != null) {
                pref.setEnabled(isWfcEnabled && isWfcModeEditable
                        && (state == TelephonyManager.CALL_STATE_IDLE));
            }
            Preference pref_roam = getPreferenceScreen().findPreference(BUTTON_WFC_ROAMING_MODE);
            if (pref_roam != null) {
                pref_roam.setEnabled(isWfcEnabled && isWfcRoamingModeEditable
                        && (state == TelephonyManager.CALL_STATE_IDLE));
            }
        }
    };

    private final OnPreferenceClickListener mUpdateAddressListener =
            new OnPreferenceClickListener() {
                /*
                 * Launch carrier emergency address managemnent activity
                 */
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final Context context = getActivity();
                    Intent carrierAppIntent = getCarrierActivityIntent(context);
                    if (carrierAppIntent != null) {
                        carrierAppIntent.putExtra(EXTRA_LAUNCH_CARRIER_APP, LAUCH_APP_UPDATE);
                        startActivity(carrierAppIntent);
                    }
                    return true;
                }
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final SettingsActivity activity = (SettingsActivity) getActivity();

        mSwitchBar = activity.getSwitchBar();
        mSwitch = mSwitchBar.getSwitch();
        mSwitchBar.show();

        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        setEmptyView(mEmptyView);
        String emptyViewText = activity.getString(R.string.wifi_calling_off_explanation)
                + activity.getString(R.string.wifi_calling_off_explanation_2);
        mEmptyView.setText(emptyViewText);
        /* UNISOC: bug 921623 @{ */
        if (ImsManager.isWfcEnabledByPlatform(getActivity())) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            mSwitchBar.addOnSwitchChangeListener(this);

            mValidListener = true;
        }
        /* @} */
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        /* UNISOC: bug 921623 @{ */
        if (mValidListener) {
            mValidListener = false;

            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

            mSwitchBar.removeOnSwitchChangeListener(this);
        }
        /* @} */
        mSwitchBar.hide();
    }

    private void showAlert(Intent intent) {
        Context context = getActivity();

        CharSequence title = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_TITLE);
        CharSequence message = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_MESSAGE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
                .setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private IntentFilter mIntentFilter;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ImsManager.ACTION_IMS_REGISTRATION_ERROR)) {
                // If this fragment is active then we are immediately
                // showing alert on screen. There is no need to add
                // notification in this case.
                //
                // In order to communicate to ImsPhone that it should
                // not show notification, we are changing result code here.
                setResultCode(Activity.RESULT_CANCELED);

                // UX requirement is to disable WFC in case of "permanent" registration failures.
                mSwitch.setChecked(false);

                showAlert(intent);
            }
        }
    };

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.WIFI_CALLING;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context mContext = getActivity();

        addPreferencesFromResource(R.xml.wifi_calling_settings);

        mButtonWfcMode = (ListPreference) findPreference(BUTTON_WFC_MODE);

        mButtonWfcRoamingMode = (ListPreference) findPreference(BUTTON_WFC_ROAMING_MODE);
        mButtonWfcRoamingMode.setOnPreferenceChangeListener(this);

        mUpdateAddress = (Preference) findPreference(PREFERENCE_EMERGENCY_ADDRESS);
        mUpdateAddress.setOnPreferenceClickListener(mUpdateAddressListener);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ImsManager.ACTION_IMS_REGISTRATION_ERROR);

        CarrierConfigManager configManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        boolean isWifiOnlySupported = true;
        boolean isLteOnlySupported = true;
        if (configManager != null) {
            PersistableBundle b = configManager.getConfig();
            if (b != null) {
                mEditableWfcMode = b.getBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL);
                mEditableWfcRoamingMode = b.getBoolean(
                        CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL);
                isWifiOnlySupported = b.getBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL);
                isLteOnlySupported = ImsManager.hasLteOnlyForWfcPreferred(getActivity());
            }
        }
        /* SPRD: add for bug843265 @{ */
        CarrierConfigManagerEx carrierConfig = CarrierConfigManagerEx.from(mContext);
        if (carrierConfig != null){
            if (carrierConfig.getConfigForDefaultPhone() != null){
                mShowWfcPref = carrierConfig.getConfigForDefaultPhone()
                   .getBoolean(CarrierConfigManagerEx.KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE);
            }
        }
        /*  @} */
        //Normal case is WIFI only not supported and LTE only not supported
        if (isWifiOnlySupported && isLteOnlySupported) { //both support WIFI only and LTE only
            mButtonWfcMode.setEntries(R.array.wifi_calling_mode_choices_with_wifionly_with_lteonly);
            mButtonWfcMode.setEntryValues(R.array.wifi_calling_mode_values_with_wifionly_with_lteonly);
            mButtonWfcRoamingMode.setEntries(
                    R.array.wifi_calling_mode_choices_v2_with_wifionly_with_lteonly);
            mButtonWfcRoamingMode.setEntryValues(
                    R.array.wifi_calling_mode_values_with_wifionly_with_lteonly);
        } else if (!isWifiOnlySupported && !isLteOnlySupported) {//support WIFI only but no LTE only
            mButtonWfcMode.setEntries(R.array.wifi_calling_mode_choices_without_wifi_only);
            mButtonWfcMode.setEntryValues(R.array.wifi_calling_mode_values_without_wifi_only);
            mButtonWfcRoamingMode.setEntries(
                    R.array.wifi_calling_mode_choices_v2_without_wifi_only);
            mButtonWfcRoamingMode.setEntryValues(
                    R.array.wifi_calling_mode_values_without_wifi_only);
        }
        /* SPRD: mofify for bug750594 and 843265@{ */
        mRemoveWfcPref = getActivity().getResources().getBoolean(
                R.bool.remove_wifi_calling_preference_option);
        if (mButtonWfcMode != null) {
            mButtonWfcMode.setOnPreferenceChangeListener(this);
            if (mRemoveWfcPref || !mShowWfcPref) {
                getPreferenceScreen().removePreference(mButtonWfcMode);
            }
        }
        /* @} */
        Log.d(TAG,"mShowWfcPref: " + mShowWfcPref + "mRemoveWfcPref" + mRemoveWfcPref);
        mShowWfcOnNotification = true;
        /* SPRD: Add for wifi-call show toast demand in bug 691804. @{ */
        mShowWfcOnNotification = getActivity().getResources().getBoolean(R.bool.show_wfc_on_notification);
        Log.d(TAG,"[mShowWfcOnNotification]: " + mShowWfcOnNotification);
        /* @} */
    }

    @Override
    public void onResume() {
        super.onResume();

        final Context context = getActivity();

        // NOTE: Buttons will be enabled/disabled in mPhoneStateListener
        boolean wfcEnabled = ImsManager.isWfcEnabledByUser(context)
                && ImsManager.isNonTtyOrTtyOnVolteEnabled(context);
        mSwitch.setChecked(wfcEnabled);
        int wfcMode = ImsManager.getWfcMode(context, false);
        int wfcRoamingMode = ImsManager.getWfcMode(context, true);
        mButtonWfcMode.setValue(Integer.toString(wfcMode));
        mButtonWfcRoamingMode.setValue(Integer.toString(wfcRoamingMode));
        updateButtonWfcMode(context, wfcEnabled, wfcMode, wfcRoamingMode);

        context.registerReceiver(mIntentReceiver, mIntentFilter);
        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WFC_IMS_ENABLED), true,
                mWfcEnableObserver);
        Intent intent = getActivity().getIntent();
        if (intent.getBooleanExtra(Phone.EXTRA_KEY_ALERT_SHOW, false)) {
            showAlert(intent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG,"[onPause]");
        final Context context = getActivity();

        context.unregisterReceiver(mIntentReceiver);
        context.getContentResolver().unregisterContentObserver(mWfcEnableObserver);
    }

    /**
     * Listens to the state change of the switch.
     */
    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        Log.d(TAG,"[isSwitchChecked]: " + isChecked);
        final Context context = getActivity();

        Log.d(TAG, "onSwitchChanged(" + isChecked + ")");
        boolean enabled = ImsManager.isWfcEnabledByUser(context) && ImsManager.isNonTtyOrTtyOnVolteEnabled(context);
        Log.d(TAG,"[onSwitchChanged][enabled]: " + enabled);

        if (!isChecked) {
            updateWfcMode(context, false);
            return;
        }
        // Call address management activity before turning on WFC
        Intent carrierAppIntent = getCarrierActivityIntent(context);
        if (carrierAppIntent != null) {
            carrierAppIntent.putExtra(EXTRA_LAUNCH_CARRIER_APP, LAUCH_APP_ACTIVATE);
            startActivityForResult(carrierAppIntent, REQUEST_CHECK_WFC_EMERGENCY_ADDRESS);
        } else {
            if (!mShowWfcOnNotification && isChecked) {
                int subId = SubscriptionManager.getDefaultDataSubscriptionId();
                Log.d(TAG,"[onSwitchChanged][subId]: " + subId);
                Log.d(TAG,"[onSwitchChanged][isEnhanced4g]: " + ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).isEnhanced4gLteModeSettingEnabledByUserForSlot());
                if (ImsManager.isVolteEnabledByPlatform(context)
                        && !ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).isEnhanced4gLteModeSettingEnabledByUserForSlot()) {
                    //SPRD: modify by bug811592
                    ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).setEnhanced4gLteModeSettingForSlot(true);
                    showToast(context, R.string.vowifi_service_volte_open_synchronously, Toast.LENGTH_LONG);
                }
            }
            if (mShowWfcOnNotification && !enabled) {
                showVowifiRegisterToast(context);
            } else {
                updateWfcMode(context, true);
            }
        }

    }

    /*SPRD: Add for wifi-call show toast demand in bug 691804*/
    public void showVowifiRegisterToast(Context context) {
        int enabled = Settings.Global.getInt(context.getContentResolver(), SettingsEx.GlobalEx.ENHANCED_VOWIFI_TOAST_SHOW_ENABLED,0);
        Log.d(TAG, "[showVowifiRegisterToast]enabled: " + enabled);
        if (enabled == 1) {
            Log.d(TAG,"showVoWifiNotification nomore ");
            updateWfcMode(context, true);
            int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (ImsManager.isVolteEnabledByPlatform(context)
                    && !ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).isEnhanced4gLteModeSettingEnabledByUserForSlot()) {
                //SPRD: modify by bug811592
                ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).setEnhanced4gLteModeSettingForSlot(true);
                showToast(context, R.string.vowifi_service_volte_open_synchronously, Toast.LENGTH_LONG);
            }
            return ;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.xml.vowifi_register_dialog, null);

        builder.setView(view);
        builder.setTitle(context.getString(R.string.vowifi_connected_title));
        builder.setMessage(context.getString(R.string.vowifi_connected_message));
        CheckBox cb = (CheckBox) view.findViewById(R.id.nomore);

        builder.setPositiveButton(context.getString(R.string.vowifi_connected_continue), new android.content.DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (cb.isChecked()) {
                    Settings.Global.putInt(context.getContentResolver(), SettingsEx.GlobalEx.ENHANCED_VOWIFI_TOAST_SHOW_ENABLED, 1);
                }
                Log.d(TAG,"Vowifi service Continue, cb.isChecked = " + cb.isChecked());
                updateWfcMode(context, true);
                int subId = SubscriptionManager.getDefaultDataSubscriptionId();
                if (ImsManager.isVolteEnabledByPlatform(context)
                        && !ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).isEnhanced4gLteModeSettingEnabledByUserForSlot()) {
                    //SPRD: modify by bug811592
                    ImsManager.getInstance(context,SubscriptionManager.getPhoneId(subId)).setEnhanced4gLteModeSettingForSlot(true);
                    showToast(context, R.string.vowifi_service_volte_open_synchronously, Toast.LENGTH_LONG);
                }
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
            }
        });
        builder.setNegativeButton(context.getString(R.string.vowifi_connected_disable), new android.content.DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (cb.isChecked()) {
                    Settings.Global.putInt(context.getContentResolver(), SettingsEx.GlobalEx.ENHANCED_VOWIFI_TOAST_SHOW_ENABLED, 1);
                }
                Log.d(TAG,"Vowifi service disable, cb.isChecked = " + cb.isChecked());
                mSwitch.setChecked(false);
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
            }
        });
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        dialog.show();
    }

    /*
     * Get the Intent to launch carrier emergency address management activity.
     * Return null when no activity found.
     */
    private static Intent getCarrierActivityIntent(Context context) {
        // Retrive component name from carrirt config
        CarrierConfigManager configManager = context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) return null;

        PersistableBundle bundle = configManager.getConfig();
        if (bundle == null) return null;

        String carrierApp = bundle.getString(
                CarrierConfigManager.KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING);
        if (TextUtils.isEmpty(carrierApp)) return null;

        ComponentName componentName = ComponentName.unflattenFromString(carrierApp);
        if (componentName == null) return null;

        // Build and return intent
        Intent intent = new Intent();
        intent.setComponent(componentName);
        return intent;
    }

    /*
     * Turn on/off WFC mode with ImsManager and update UI accordingly
     */
    private void updateWfcMode(Context context, boolean wfcEnabled) {
        Log.i(TAG, "updateWfcMode(" + wfcEnabled + ")");
        ImsManager.setWfcSetting(context, wfcEnabled);
        int wfcMode = ImsManager.getWfcMode(context, false);
        int wfcRoamingMode = ImsManager.getWfcMode(context, true);
        updateButtonWfcMode(context, wfcEnabled, wfcMode, wfcRoamingMode);
        if (wfcEnabled) {
            mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), wfcMode);
        } else {
            mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), -1);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        final Context context = getActivity();

        if (requestCode == REQUEST_CHECK_WFC_EMERGENCY_ADDRESS) {
            Log.d(TAG, "WFC emergency address activity result = " + resultCode);

            if (resultCode == Activity.RESULT_OK) {
                updateWfcMode(context, true);
            }
        }
    }

    private void updateButtonWfcMode(Context context, boolean wfcEnabled,
                                     int wfcMode, int wfcRoamingMode) {
        mButtonWfcMode.setSummary(getWfcModeSummary(context, wfcMode));
        mButtonWfcMode.setEnabled(wfcEnabled && mEditableWfcMode);
        // mButtonWfcRoamingMode.setSummary is not needed; summary is just selected value.
        mButtonWfcRoamingMode.setEnabled(wfcEnabled && mEditableWfcRoamingMode);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        boolean updateAddressEnabled = (getCarrierActivityIntent(context) != null);
        if (wfcEnabled) {
            // SPRD: add for bug750594
            if (!mRemoveWfcPref && mEditableWfcMode && mShowWfcPref) {
                preferenceScreen.addPreference(mButtonWfcMode);
            } else {
                // Don't show WFC (home) preference if it's not editable.
                preferenceScreen.removePreference(mButtonWfcMode);
            }
            if (mEditableWfcRoamingMode) {
                preferenceScreen.addPreference(mButtonWfcRoamingMode);
            } else {
                // Don't show WFC roaming preference if it's not editable.
                preferenceScreen.removePreference(mButtonWfcRoamingMode);
            }
            if (updateAddressEnabled) {
                preferenceScreen.addPreference(mUpdateAddress);
            } else {
                preferenceScreen.removePreference(mUpdateAddress);
            }
        } else {
            preferenceScreen.removePreference(mButtonWfcMode);
            preferenceScreen.removePreference(mButtonWfcRoamingMode);
            preferenceScreen.removePreference(mUpdateAddress);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getActivity();
        /* SPRD: add for bug712231 @{ */
        if (context == null) {
            return false;
        }
        /* @} */
        if (preference == mButtonWfcMode) {
            mButtonWfcMode.setValue((String) newValue);
            int buttonMode = Integer.valueOf((String) newValue);
            int currentWfcMode = ImsManager.getWfcMode(context, false);
            if (buttonMode != currentWfcMode) {
                ImsManager.setWfcMode(context, buttonMode, false);
                mButtonWfcMode.setSummary(getWfcModeSummary(context, buttonMode));
                mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), buttonMode);
            }
            if (!mEditableWfcRoamingMode) {
                int currentWfcRoamingMode = ImsManager.getWfcMode(context, true);
                if (buttonMode != currentWfcRoamingMode) {
                    ImsManager.setWfcMode(context, buttonMode, true);
                    // mButtonWfcRoamingMode.setSummary is not needed; summary is selected value
                }
            }
        } else if (preference == mButtonWfcRoamingMode) {
            mButtonWfcRoamingMode.setValue((String) newValue);
            int buttonMode = Integer.valueOf((String) newValue);
            int currentMode = ImsManager.getWfcMode(context, true);
            if (buttonMode != currentMode) {
                ImsManager.setWfcMode(context, buttonMode, true);
                // mButtonWfcRoamingMode.setSummary is not needed; summary is just selected value.
                mMetricsFeatureProvider.action(getActivity(), getMetricsCategory(), buttonMode);
            }
        }
        return true;
    }

    public static int getWfcModeSummary(Context context, int wfcMode) {
        int resId = com.android.internal.R.string.wifi_calling_off_summary;
        if (ImsManager.isWfcEnabledByUser(context)) {
            /* SPRD: add for bug854291 @{ */
            boolean mShowWifiCallingSummaryOn = false;
            CarrierConfigManagerEx carrierConfig = CarrierConfigManagerEx.from(context);
            if (carrierConfig != null){
                if (carrierConfig.getConfigForDefaultPhone() != null){
                    mShowWifiCallingSummaryOn = carrierConfig.getConfigForDefaultPhone()
                       .getBoolean(CarrierConfigManagerEx.KEY_SUPPORT_SHOW_WIFI_CALLING_PREFERENCE);
                }
            }
            Log.d(TAG,"mShowWifiCallingSummaryOn " +  mShowWifiCallingSummaryOn);
            if (mShowWifiCallingSummaryOn) {
                switch (wfcMode) {
                    case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                        resId = com.android.internal.R.string.wfc_mode_wifi_only_summary;
                        break;
                    case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                        resId = com.android.internal.R.string.wfc_mode_cellular_preferred_summary;
                        break;
                    case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                        resId = com.android.internal.R.string.wfc_mode_wifi_preferred_summary;
                        break;
                    case ImsConfig.WfcModeFeatureValueConstants.LTE_ONLY:
                        resId = com.android.internal.R.string.wfc_mode_lte_only_summary;
                        break;
                    default:
                        Log.e(TAG, "Unexpected WFC mode value: " + wfcMode);
                }
            } else {
                resId = com.android.internal.R.string.wifi_calling_on_vodaphone_summary;
            }
        }
        /* @} */
        return resId;
    }


    private static void showToast(Context context, int text, int duration) {
        Toast mToast = Toast.makeText(context, text, duration);
        mToast.getWindowParams().type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
        mToast.getWindowParams().flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        mToast.show();
    }
}
