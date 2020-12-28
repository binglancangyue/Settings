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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.android.ims.ImsManager;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.PhoneConstants;
import com.android.settingslib.RestrictedLockUtils;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

/**
 * Confirm and execute a reset of the network settings to a clean "just out of the box"
 * state.  Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one, followed by a final strongly-worded "THIS WILL RESET EVERYTHING"
 * prompt.  If at any time the phone is allowed to go to sleep, is
 * locked, et cetera, then the confirmation sequence is abandoned.
 *
 * This is the confirmation screen.
 */
public class ResetNetworkConfirm extends OptionsMenuFragment {

    private View mContentView;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    /* SPRD: modify by BUG 724193 @{ */
    private final String LOG_TAG = "ResetNetworkConfirm";
    private final String RESET_STATE = "RESET_STATE";
    private boolean mIsReseting = false;
    private Thread mResetThread;
    private Button mResetBtn;
    /* @} */

    /**
     * The user has gone through the multiple confirmation, so now we go ahead
     * and reset the network settings to its factory-default state.
     */
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (Utils.isMonkeyRunning()) {
                return;
            }
            /* SPRD: modify by BUG 724193 @{ */
            if (mResetThread == null) {
                mResetThread = new Thread(mRunnable);
            }
            mResetThread.start();
            setResetBtnClickable(false);
            mIsReseting = true;
            /* @} */
        }
    };

    /**
     * Restore APN settings to default.
     */
    private void restoreDefaultApn(Context context) {
        Uri uri = Uri.parse(ApnSettings.RESTORE_CARRIERS_URI);

        if (SubscriptionManager.isUsableSubIdValue(mSubId)) {
            uri = Uri.withAppendedPath(uri, "subId/" + String.valueOf(mSubId));
        }

        ContentResolver resolver = context.getContentResolver();
        resolver.delete(uri, null, null);
    }

    /**
     * Configure the UI for the final confirmation interaction
     */
    private void establishFinalConfirmationState() {
        mContentView.findViewById(R.id.execute_reset_network)
                .setOnClickListener(mFinalClickListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final EnforcedAdmin admin = RestrictedLockUtils.checkIfRestrictionEnforced(
                getActivity(), UserManager.DISALLOW_NETWORK_RESET, UserHandle.myUserId());
        if (RestrictedLockUtils.hasBaseUserRestriction(getActivity(),
                UserManager.DISALLOW_NETWORK_RESET, UserHandle.myUserId())) {
            return inflater.inflate(R.layout.network_reset_disallowed_screen, null);
        } else if (admin != null) {
            View view = inflater.inflate(R.layout.admin_support_details_empty_view, null);
            ShowAdminSupportDetailsDialog.setAdminSupportDetails(getActivity(), view, admin, false);
            view.setVisibility(View.VISIBLE);
            return view;
        }
        mContentView = inflater.inflate(R.layout.reset_network_confirm, null);
        establishFinalConfirmationState();
        /* SPRD: modify by BUG 724193 @{ */
        mResetBtn = (Button) mContentView.findViewById(R.id.execute_reset_network);
        setResetBtnClickable(true);
        if (savedInstanceState != null) {
            mIsReseting = savedInstanceState.getBoolean(RESET_STATE);
            Log.d(LOG_TAG, "onCreateView#mIsReseting : " + mIsReseting);
            if (mIsReseting) {
                setResetBtnClickable(false);
            }
        }
        /* @} */
        return mContentView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mSubId = args.getInt(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.RESET_NETWORK_CONFIRM;
    }

    /* SPRD: modify by BUG 724193 @{ */
    private final String ACTION_RESET_COMPLETED = "action_reset_completed";
    private final ContentObserver mResetCompletedObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            int flag = Settings.System.getInt(
                    getActivity().getContentResolver(), ACTION_RESET_COMPLETED, 0);
            Log.d(LOG_TAG, "onChange flag: " + flag + "; mIsReseting: " + mIsReseting);
            if(flag == 0) {
                return;
            }
            if (getActivity() != null) {
                Toast.makeText(getActivity(), R.string.reset_network_complete_toast,
                        Toast.LENGTH_SHORT).show();
                Settings.System.putInt(
                        getActivity().getContentResolver(), ACTION_RESET_COMPLETED, 0);
            }
            mResetThread = null;
            mIsReseting = false;
            setResetBtnClickable(true);
        }
    };

    @Override
    public void onResume() {
        if (getActivity() != null) {
            getActivity().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(
                            ACTION_RESET_COMPLETED), true , mResetCompletedObserver);
        }

        int flag = Settings.System.getInt(
                getActivity().getContentResolver(), ACTION_RESET_COMPLETED, 0);
        Log.d(LOG_TAG, "register ContentObserver in onResume#flag: "
                + flag + "; mIsReseting: " + mIsReseting);
        if(mIsReseting && flag != 0) {
            Toast.makeText(getActivity(), R.string.reset_network_complete_toast,
                    Toast.LENGTH_SHORT).show();
            Settings.System.putInt(getActivity().getContentResolver(), ACTION_RESET_COMPLETED, 0);
            int flagafter = Settings.System.getInt(
                    getActivity().getContentResolver(), ACTION_RESET_COMPLETED, 0);
            mResetThread = null;
            mIsReseting = false;
            setResetBtnClickable(true);
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.d(LOG_TAG, "unregister ContentObserver in onPause");
        if (getActivity() != null) {
            getActivity().getContentResolver().unregisterContentObserver(mResetCompletedObserver);
        }
        super.onPause();
    }

    Runnable mRunnable = new Runnable() {

        @Override
        public void run() {
            Log.d(LOG_TAG, "BEGIN RESET");
            // TODO maybe show a progress dialog if this ends up taking a while
            Context context = getActivity();
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.factoryReset();
            }

            WifiManager wifiManager = (WifiManager)
                    context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.factoryReset();
            }

            TelephonyManager telephonyManager = (TelephonyManager)
                    context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                telephonyManager.factoryReset(mSubId);
            }

            NetworkPolicyManager policyManager = (NetworkPolicyManager)
                    context.getSystemService(Context.NETWORK_POLICY_SERVICE);
            if (policyManager != null) {
                String subscriberId = telephonyManager.getSubscriberId(mSubId);
                policyManager.factoryReset(subscriberId);
            }

            BluetoothManager btManager = (BluetoothManager)
                    context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager != null) {
                BluetoothAdapter btAdapter = btManager.getAdapter();
                if (btAdapter != null) {
                    btAdapter.factoryReset();
                }
            }
            ImsManager.factoryReset(context);
            restoreDefaultApn(context);
            Log.d(LOG_TAG, "END RESET");
            Settings.System.putInt(context.getContentResolver(), ACTION_RESET_COMPLETED, 1);
        }

    };

    private void setResetBtnClickable(boolean state) {
        if (!state) {
           mResetBtn.setText(R.string.reset_networks_progress);
        } else {
            mResetBtn.setText(R.string.reset_network_button_text);
        }
        mResetBtn.setEnabled(state);
        mResetBtn.setClickable(state);
        mResetBtn.setFocusable(state);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(RESET_STATE, mIsReseting);
        super.onSaveInstanceState(outState);
    }
    /* @} */
}
