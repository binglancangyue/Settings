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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends SettingsPreferenceFragment
        implements EditPinPreference.OnPinEnteredListener {
    private static final String TAG = "IccLockSettings";
    private static final boolean DBG = true;

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;

    // Keys in xml file
    private static final String PIN_DIALOG = "sim_pin";
    private static final String PIN_TOGGLE = "sim_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";

    // SPRD: see bug #718648
    private static final String CURRENT_TAB = "currentTab";

    // Save and restore inputted PIN code when configuration changed
    // (ex. portrait<-->landscape) during change PIN code
    private static final String OLD_PINCODE = "oldPinCode";
    private static final String NEW_PINCODE = "newPinCode";

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;

    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;

    private TabHost mTabHost;
    private TabWidget mTabWidget;
    private ListView mListView;

    private Phone mPhone;

    private EditPinPreference mPinDialog;
    private SwitchPreference mPinToggle;

    private Resources mRes;

    /* SPRD: modify by BUG 718648 @{ */
    private int mCurrentTab = 0;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    /* @} */

    // For async handler to identify request type
    private static final int MSG_ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int MSG_CHANGE_ICC_PIN_COMPLETE = 101;
    private static final int MSG_SIM_STATE_CHANGED = 102;

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MSG_ENABLE_ICC_PIN_COMPLETE:
                    iccLockChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_CHANGE_ICC_PIN_COMPLETE:
                    iccPinChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_SIM_STATE_CHANGED:
                    updatePreferences();
                    // SPRD: modify by BUG 712648
                    updateTabName();
                    break;
            }

            return;
        }
    };

    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
            }
        }
    };

    // For top-level settings screen to query
    static boolean isIccLockEnabled() {
        return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
    }

    static String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled()
                ? res.getString(R.string.sim_lock_on)
                : res.getString(R.string.sim_lock_off);
        return summary;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }

        addPreferencesFromResource(R.xml.sim_lock_settings);

        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (SwitchPreference) findPreference(PIN_TOGGLE);
        /* UNISOC: modify for bug911959 @{ */
        if (savedInstanceState != null) {
            // SPRD: see bug #718648
            mCurrentTab = savedInstanceState.getInt(CURRENT_TAB);
            if (savedInstanceState.containsKey(DIALOG_STATE)) {
                mDialogState = savedInstanceState.getInt(DIALOG_STATE);
                mPin = savedInstanceState.getString(DIALOG_PIN);
                mError = savedInstanceState.getString(DIALOG_ERROR);
                mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);
                // Restore inputted PIN code
                switch (mDialogState) {
                    case ICC_NEW_MODE:
                        mOldPin = savedInstanceState.getString(OLD_PINCODE);
                        break;
                    case ICC_REENTER_MODE:
                        mOldPin = savedInstanceState.getString(OLD_PINCODE);
                        mNewPin = savedInstanceState.getString(NEW_PINCODE);
                        break;
                    case ICC_LOCK_MODE:
                    case ICC_OLD_MODE:
                    default:
                        break;
                }
            }
        }
        /* @} */
        mPinDialog.setOnPinEnteredListener(this);

        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);

        mRes = getResources();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        /* SPRD: modify by BUG 718648 @{ */
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        final int numSims = mTelephonyManager.getSimCount();
        /* @} */
        if (numSims > 1) {
            View view = inflater.inflate(R.layout.icc_lock_tabs, container, false);
            final ViewGroup prefs_container = (ViewGroup) view.findViewById(R.id.prefs_container);
            Utils.prepareCustomPreferencesList(container, view, prefs_container, false);
            View prefs = super.onCreateView(inflater, prefs_container, savedInstanceState);
            prefs_container.addView(prefs);

            mTabHost = (TabHost) view.findViewById(android.R.id.tabhost);
            mTabWidget = (TabWidget) view.findViewById(android.R.id.tabs);
            mListView = (ListView) view.findViewById(android.R.id.list);

            mTabHost.setup();
            mTabHost.clearAllTabs();

            /* SPRD: modify by BUG 718648 @{ */
            // if this ui was re-constructed by event orientation change or others,
            // we should set previous tab as current tab. See bug #496369.
            mSubscriptionManager = SubscriptionManager.from(getContext());
            for (int i = 0; i < numSims; ++i) {
                final SubscriptionInfo subInfo = mSubscriptionManager
                        .getActiveSubscriptionInfoForSimSlotIndex(i);
                mTabHost.addTab(buildTabSpec(String.valueOf(i),
                        String.valueOf(subInfo == null
                            ? getContext().getString(R.string.sim_editor_title, i + 1)
                            : subInfo.getDisplayName())));
            }
            final SubscriptionInfo sir = mSubscriptionManager
                    .getActiveSubscriptionInfoForSimSlotIndex(mCurrentTab);

            mPhone = (sir == null) ? null
                : PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir.getSubscriptionId()));

            if (mCurrentTab > 0) {
                mTabHost.setCurrentTab(mCurrentTab);
            }
            /* @} */
            // UNISOC: modify for bug911959
            mTabHost.setOnTabChangedListener(mTabListener);
            return view;
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updatePreferences();
    }

    private void updatePreferences() {
        /* SPRD: modify by BUG 718648 @{ */
        if (Utils.isMonkeyRunning() || getContext() == null) {
            return;
        }

        /* SPRD: add for bug874314@{ */
        final SubscriptionInfo subInfo = SubscriptionManager.from(getContext())
                .getActiveSubscriptionInfoForSimSlotIndex(mCurrentTab);
        mPhone = (subInfo == null) ? null
                : PhoneFactory.getPhone(
                        SubscriptionManager.getPhoneId(subInfo.getSubscriptionId()));
        Log.d(TAG, "mCurrentTab : " + mCurrentTab + " mPhone is " + mPhone);
        /* @} */
        boolean pinNotAvailable = (mPhone == null
                || !mPhone.getIccCard().hasIccCard()
                || (mTelephonyManager.getSimState(mCurrentTab)
                        != TelephonyManager.SIM_STATE_READY));

        mPinDialog.setEnabled(!pinNotAvailable);
        mPinToggle.setEnabled(!pinNotAvailable);
        /* SPRD: modify by Bug878465 @{ */
        if (pinNotAvailable) {
            mPinToggle.setChecked(false);
        }
        /* @} */
        if (mPinDialog.isDialogOpen() && pinNotAvailable) {
            mPinDialog.getDialog().dismiss();
        }
        /* @} */
        if (mPhone != null) {
            mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.ICC_LOCK;
    }

    @Override
    public void onResume() {
        super.onResume();

        // ACTION_SIM_STATE_CHANGED is sticky, so we'll receive current state after this call,
        // which will call updatePreferences().
        final IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        getContext().registerReceiver(mSimStateReceiver, filter);
        // UNISOC: add for bug903728
        SubscriptionManager.from(
                getContext()).addOnSubscriptionsChangedListener(mSubscriptionListener);

        if (mDialogState != OFF_MODE) {
            showPinDialog();
        } else {
            // Prep for standard click on "Change PIN"
            resetDialogState();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(mSimStateReceiver);
        // UNISOC: add for bug903728
        SubscriptionManager.from(getContext()).removeOnSubscriptionsChangedListener(
                mSubscriptionListener);
    }

    /* UNISOC: add for bug903728 @{ */
    private OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    Log.d(TAG, "onSubscriptionsChanged updateTabName");
                    updateTabName();
                }
            };
    /* @} */

    @Override
    protected int getHelpResource() {
        return R.string.help_url_icc_lock;
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        /* SPRD: modify by BUG 718648 @{ */
        if (mTabHost != null) {
            out.putInt(CURRENT_TAB, mTabHost.getCurrentTab());
        }
        /* @} */
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);

            // Save inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    break;

                case ICC_REENTER_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    out.putString(NEW_PINCODE, mNewPin);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        } else {
            super.onSaveInstanceState(out);
        }
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        setDialogValues();

        mPinDialog.showPinDialog();
    }

    /* SPRD: Bug847291,Pin code input and modify the interface does not have times tips@{*/
    private int getRemainTimes() {
        int remainTimes = -1;
        Context context = getContext();
        if (context != null) {
            SubscriptionInfo sir = SubscriptionManager.from(context)
                    .getActiveSubscriptionInfoForSimSlotIndex(mCurrentTab);
            if (sir != null) {
                 /* SPRD: modify by BUG 862309 @{ */
                try {
                        remainTimes = Integer.valueOf(TelephonyManager
                            .getTelephonyProperty(sir.getSimSlotIndex(),
                            "gsm.sim.pin.remaintimes", "0"));
                } catch (Exception e) {
                    remainTimes = 3;
                    Log.d(TAG, "catch exception in getRemainTimes");
                }
                /*@}*/
            }
        }
        return remainTimes;
    }
    /*@}*/

    private void setDialogValues() {
        if (mPinDialog != null) {
            mPinDialog.setText(mPin);
            String message = "";
            switch (mDialogState) {
                case ICC_LOCK_MODE:
                    message = mRes.getString(R.string.sim_enter_pin);
                    mPinDialog.setDialogTitle(mToState
                        ? mRes.getString(R.string.sim_enable_sim_lock)
                        : mRes.getString(R.string.sim_disable_sim_lock));
                    break;
                case ICC_OLD_MODE:
                    message = mRes.getString(R.string.sim_enter_old);
                    mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                    break;
                case ICC_NEW_MODE:
                    message = mRes.getString(R.string.sim_enter_new);
                    mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                    break;
                case ICC_REENTER_MODE:
                    message = mRes.getString(R.string.sim_reenter_new);
                    mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                    break;
            }
            //SPRD: Bug847291,Pin code input and modify the interface does not have times tips
            int remainTimes = getRemainTimes();
            // SPRD: modify for bug866233
            if (remainTimes >= 0
                    && mDialogState != ICC_NEW_MODE && mDialogState != ICC_REENTER_MODE) {
                message += mRes.getString((com.android.settings.R.string.attempts_remaining_times), remainTimes);
            }
            if (mError != null) {
                message = mError + "\n" + message;
                mError = null;
            }
            mPinDialog.setDialogMessage(message);
        }
    }

    @Override
    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            resetDialogState();
            return;
        }

        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.sim_bad_pin);
            // UNISOC: modify for bug894012
            mPin = null;
            showPinDialog();
            return;
        }
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                tryChangeIccLockState();
                break;
            case ICC_OLD_MODE:
                mOldPin = mPin;
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
                break;
            case ICC_NEW_MODE:
                mNewPin = mPin;
                mDialogState = ICC_REENTER_MODE;
                mPin = null;
                showPinDialog();
                break;
            case ICC_REENTER_MODE:
                if (!mPin.equals(mNewPin)) {
                    mError = mRes.getString(R.string.sim_pins_dont_match);
                    mDialogState = ICC_NEW_MODE;
                    mPin = null;
                    showPinDialog();
                } else {
                    mError = null;
                    tryChangePin();
                }
                break;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog
            mPinToggle.setChecked(!mToState);
            if (mDialogState != OFF_MODE) {
                Log.d(TAG, "Wait for change sim pin done.");
                return true;
            }
            mDialogState = ICC_LOCK_MODE;
            /* SPRD: modify by BUG 862802 @{ */
            mPin = null;
            Log.d(TAG, "Set mPin as null");
            /*@}*/
            showPinDialog();
        } else if (preference == mPinDialog) {
            if (mDialogState != OFF_MODE) {
                Log.d(TAG, "Wait for enable/disable pin lock done.");
                return false;
            }
            mDialogState = ICC_OLD_MODE;
            return false;
        }
        return true;
    }

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        /* SPRD: modify for BUG 720391 @{ */
        if (mPhone != null) {
            Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE);
            mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);
            // Disable the setting till the response is received.
            mPinToggle.setEnabled(false);
        } else {
            resetDialogState();
        }
        /* @} */
    }

    private void iccLockChanged(boolean success, int attemptsRemaining) {
        if (success) {
            mPinToggle.setChecked(mToState);
            /* SPRD: modify by BUG 739649 @{ */
            if (attemptsRemaining < 0 && getContext() != null) {
                if (mToState) {
                    Toast.makeText(getContext(), mRes.getString(R.string.icc_pin_enabled,
                            mCurrentTab + 1), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), mRes.getString(R.string.icc_pin_disabled,
                            mCurrentTab + 1), Toast.LENGTH_LONG).show();
                }
            }
            /* @} */
        } else {
            /* SPRD: modify by BUG 717879 @{ */
            if (getContext() != null) {
                Toast.makeText(getContext(), getPinPasswordErrorMessage(attemptsRemaining),
                        Toast.LENGTH_LONG).show();
            }
        }
        /* @} */
        /* SPRD: add for bug861782@{ */
        Log.d(TAG, "iccLockChanged attemptsRemaining : " + attemptsRemaining);
        if (attemptsRemaining != 0) {
            mPinToggle.setEnabled(true);
        }
        /* @} */
        resetDialogState();
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (!success) {
            Toast.makeText(getContext(), getPinPasswordErrorMessage(attemptsRemaining),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(getContext(), mRes.getString(R.string.sim_change_succeeded),
                    Toast.LENGTH_SHORT)
                    .show();

        }
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, MSG_CHANGE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = mRes
                    .getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = mRes.getString(R.string.pin_failed);
        }
        if (DBG) Log.d(TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            Log.d(TAG, "onTabChanged " + mCurrentTab + " > " + tabId);
            /* SPRD: modify by BUG 718648 @{ */
            try {
                mCurrentTab = Integer.parseInt(tabId);
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
            }
            final SubscriptionInfo sir = SubscriptionManager.from(getActivity().getBaseContext())
                    .getActiveSubscriptionInfoForSimSlotIndex(mCurrentTab);
            /* @} */
            mPhone = (sir == null) ? null
                : PhoneFactory.getPhone(SubscriptionManager.getPhoneId(sir.getSubscriptionId()));

            /* UNISOC: add for bug900785 @{ */
            if ((mPinDialog != null) && (mPinDialog.getDialog() != null)
                    && mPinDialog.getDialog().isShowing()) {
                Log.d(TAG, "onTabChanged dismiss old dialog");
                mPinDialog.getDialog().dismiss();
            }
            /* @} */
            // The User has changed tab; update the body.
            resetDialogState();
            updatePreferences();
        }
    };

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);
    }

    /* SPRD: modify by BUG 718648 @{ */
    private void updateTabName() {
        /* SPRD: modify by BUG 723536 @{ */
        if (getContext() == null) {
            return;
        }
        /* @} */
        int numSims = mTelephonyManager.getSimCount();
        for (int i = 0; i < numSims; ++i) {
            final SubscriptionInfo subInfo = mSubscriptionManager
                    .getActiveSubscriptionInfoForSimSlotIndex(i);
            TextView tabTitle = (TextView) mTabHost.getTabWidget().getChildTabViewAt(i)
                    .findViewById(android.R.id.title);
            if (tabTitle != null) {
                tabTitle.setText(
                    String.valueOf(subInfo == null
                    ? getContext().getString(R.string.sim_editor_title, i + 1)
                    : subInfo.getDisplayName()));
                /* SPRD: modify by BUG 728271 @{ */
                tabTitle.setSingleLine();
                tabTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            }
            /* @} */
        }
    }
    /* @} */
}
