/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.network;

import android.content.Context;
import android.os.Looper;
import android.os.UserManager;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.settings.Customer;
import com.android.settings.Utils;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;

import com.android.internal.telephony.TeleUtils;

import java.util.List;

import static android.os.UserHandle.myUserId;
import static android.os.UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS;

public class MobileNetworkPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnResume, OnPause {

    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";

    private final boolean mIsSecondaryUser;
    private final TelephonyManager mTelephonyManager;
    private final UserManager mUserManager;
    private Preference mPreference;
    @VisibleForTesting
    PhoneStateListener mPhoneStateListener;
    private SubscriptionManager mSubscriptionManager;
    private SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener;

    public MobileNetworkPreferenceController(Context context) {
        super(context);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mIsSecondaryUser = !mUserManager.isAdminUser();
        mSubscriptionManager = SubscriptionManager.from(mContext);
        if(Looper.getMainLooper() == Looper.myLooper()){
            mOnSubscriptionsChangeListener
                    = new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    updateSubscriptions();
                }
            };
        }
    }

    @Override
    public boolean isAvailable() {
        // by lym start
        if (Customer.IS_ONLY_SHOW_WIFI) {
            return false;
        } else {
            return !isUserRestricted() && !Utils.isWifiOnly(mContext);
        }
//        return false;
        //end
    }

    public boolean isUserRestricted() {
        final RestrictedLockUtilsWrapper wrapper = new RestrictedLockUtilsWrapper();
        return mIsSecondaryUser ||
                wrapper.hasBaseUserRestriction(
                        mContext,
                        DISALLOW_CONFIG_MOBILE_NETWORKS,
                        myUserId());
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (isAvailable()) {
            mPreference = screen.findPreference(getPreferenceKey());
            /* SPRD: modify by BUG 791390 @{ */
            if (mPreference != null && mPreference.getIcon() != null) {
                mPreference.getIcon().setAutoMirrored(false);
            }
            /* @} */
        }
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MOBILE_NETWORK_SETTINGS;
    }

    @Override
    public void onResume() {
        updateSubscriptions();
        if (isAvailable()) {
            if (mPhoneStateListener == null) {
                mPhoneStateListener = new PhoneStateListener() {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        if (mPreference != null) {
                            // SPRD: [bug787914] Show network operator when service state in STATE_EMERGENCY_ONLY.
                            if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                                // SPRD: [bug717465] Translate operator name into current language.
                                String netOperatorName = mTelephonyManager.getNetworkOperatorName();
                                mPreference.setSummary(TeleUtils.updateOperator(netOperatorName, "operator"));
                            }
                        }
                    }
                };
            }
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
        if(mOnSubscriptionsChangeListener != null){
            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }

    @Override
    public void onPause() {
        if (mPhoneStateListener != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        if(mOnSubscriptionsChangeListener != null){
            mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }


    private void updateSubscriptions(){
        if(mUserManager != null && mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)){
            return;
        }
        if(mPreference == null){
            return;
        }
        List<SubscriptionInfo> sil = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        if(sil != null && sil.size() > 0){
            mPreference.setEnabled(true);
        }else{
            mPreference.setEnabled(false);
        }
    }
}
