/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.settings.datausage;

import static android.net.ConnectivityManager.TYPE_ETHERNET;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerEx;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.RelativeSizeSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.SummaryPreference;
import com.android.settings.Utils;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.NetworkPolicyEditor;
import com.android.settingslib.WirelessUtils;
import com.android.settingslib.net.DataUsageController;

import android.telephony.PhoneStateListener;

import java.lang.ref.WeakReference;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings preference fragment that displays data usage summary.
 *
 * This class in deprecated use {@link DataPlanUsageSummary}.
 */
@Deprecated
public class DataUsageSummary extends DataUsageBase implements Indexable, DataUsageEditController {

    private static final String TAG = "DataUsageSummary";
    static final boolean LOGD = false;

    public static final String KEY_RESTRICT_BACKGROUND = "restrict_background";

    private static final String KEY_STATUS_HEADER = "status_header";
    private static final String KEY_LIMIT_SUMMARY = "limit_summary";

    // Mobile data keys
    public static final String KEY_MOBILE_USAGE_TITLE = "mobile_category";
    public static final String KEY_MOBILE_DATA_USAGE_TOGGLE = "data_usage_enable";
    public static final String KEY_MOBILE_DATA_USAGE = "cellular_data_usage";
    public static final String KEY_MOBILE_BILLING_CYCLE = "billing_preference";

    // Wifi keys
    public static final String KEY_WIFI_USAGE_TITLE = "wifi_category";
    public static final String KEY_WIFI_DATA_USAGE = "wifi_data_usage";
    public static final String KEY_NETWORK_RESTRICTIONS = "network_restrictions";


    private static final String KEY_WIFI_RATE = "wifi_Rate";
    private static final String KEY_DATA_RATE = "data_Rate";
    private static final String KEY_DATA_ENABLE = "data_usage_enable";
    // SPRD: add for bug724515
    private int mSubscriptionSize;

    private DataUsageController mDataUsageController;
    private DataUsageInfoController mDataInfoController;
    private SummaryPreference mSummaryPreference;
    private Preference mLimitPreference;
    private NetworkTemplate mDefaultTemplate;
    private int mDataUsageTemplate;
    private NetworkRestrictionsPreference mNetworkRestrictionPreference;
    private WifiManager mWifiManager;
    private NetworkPolicyEditor mPolicyEditor;
    private CellDataPreference[] mCellDataPreferences;
    private BillingCyclePreference[] mBillingCyclePreferences;
    private PhoneStateListener[] mPhoneStateListener;
    private UserManager mUserManager;

    @Override
    protected int getHelpResource() {
        return R.string.help_url_data_usage;
    }
    /*SPRD added for bug 692482 @{ */
    private NetworkRatePreference mWifiNetworkRatePreference;
    private NetworkRatePreference[] mDataNetworkRatePreference;
    /* @}*/
    /* SPRD:Bug 788821 mobile data switch status is not same with status bar @{ */
    private boolean mIsAirPlanModeOn = false;
    private List<SubscriptionInfo> mSubscriptionInfos;
    /* @}*/
    private TelephonyManagerEx mTelephonyManagerEx;
    private boolean mSupportSubsidyLock = false;

    private boolean shouldEnableMobileData() {
        if(mSupportSubsidyLock) {
            return mTelephonyManagerEx.isPrimaryCardSwitchAllowed();
        }
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getContext();
        mTelephonyManagerEx = TelephonyManagerEx.from(getContext());
        mSupportSubsidyLock = Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_subsidyLock);
        NetworkPolicyManager policyManager = NetworkPolicyManager.from(context);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mPolicyEditor = new NetworkPolicyEditor(policyManager);

        boolean hasMobileData = DataUsageUtils.hasMobileData(context);
        // SPRD:Bug 788821 mobile data switch status is not same with status bar
        mIsAirPlanModeOn = WirelessUtils.isAirplaneModeOn(context);

        mUserManager = UserManager.get(context);

        mDataUsageController = new DataUsageController(context);
        mDataInfoController = new DataUsageInfoController();
        addPreferencesFromResource(R.xml.data_usage);

        int defaultSubId = DataUsageUtils.getDefaultSubscriptionId(context);
        if (defaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            hasMobileData = false;
        }
        mDefaultTemplate = DataUsageUtils.getDefaultTemplate(context, defaultSubId);
        mSummaryPreference = (SummaryPreference) findPreference(KEY_STATUS_HEADER);

        if (!hasMobileData || !isAdmin()) {
            removePreference(KEY_RESTRICT_BACKGROUND);
        }
        if (hasMobileData) {
            mLimitPreference = findPreference(KEY_LIMIT_SUMMARY);
            /* SPRD:Bug 788821 mobile data switch status is not same with status bar @{ */
            mSubscriptionInfos =
                    services.mSubscriptionManager.getActiveSubscriptionInfoList();
            if (mSubscriptionInfos == null) {
                mSubscriptionInfos = Collections.emptyList();
            }
            mSubscriptionSize = mSubscriptionInfos.size();
            if (mSubscriptionInfos.size() == 0) {
                addMobileSection(defaultSubId,0);
            }
            //SPRD modified for bug 692482
            if (mSubscriptionInfos.size() > 0) {
                mDataNetworkRatePreference = new NetworkRatePreference[mSubscriptionInfos.size()];
                mCellDataPreferences = new CellDataPreference[mSubscriptionInfos.size()];
                mBillingCyclePreferences = new BillingCyclePreference[mSubscriptionInfos.size()];
                mPhoneStateListener = new PhoneStateListener[mSubscriptionInfos.size()];
            }
            for (int i = 0; i < mSubscriptionInfos.size(); i++) {
                SubscriptionInfo subInfo = mSubscriptionInfos.get(i);
                if (mSubscriptionInfos.size() > 1) {
                    addMobileSection(subInfo.getSubscriptionId(), subInfo, i);
                } else {
                    addMobileSection(subInfo.getSubscriptionId(), i);
                }
                //SPRD 714846 if call state not idle can not swtich data card
                mPhoneStateListener[i] = new PhoneStateListener(mSubscriptionInfos.get(i).getSubscriptionId()) {
                    @Override
                    public void onCallStateChanged(int state, String incomingNumber) {
                        Log.d(TAG, "onCallStateChanged state = "+state);
                        int phoneId = SubscriptionManager.getPhoneId(this.mSubId);
                        /* SPRD: Bug 782722 switch default data when in call，
                         * call disconnected automatically @{ */
                        int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
                        Log.d(TAG, "onCallStateChanged mSubId = " + mSubId + " defaultDataSubId = "
                             + defaultDataSubId + " phoneId = " + phoneId);

                        if (phoneId > -1) {
                            int otherPhone = (phoneId == 0? 1:0);
                            for (int i = 0 ; i < mCellDataPreferences.length ; i++) {
                                if (mCellDataPreferences[i] != null && mUserManager != null
                                        && mCellDataPreferences[i].mSubId != defaultDataSubId) {
                                    /* SPRD: modify by BUG 776159 @{ */
                                    mCellDataPreferences[i].setEnabled(
                                        mUserManager.isAdminUser()//SPRD 724458  if not admin, disable the CellDataPreference
                                        && (services.mTelephonyManager.getCallState(this.mSubId)
                                                == TelephonyManager.CALL_STATE_IDLE)
                                        && services.mTelephonyManager. getCallStateForSlot(otherPhone) == TelephonyManager.CALL_STATE_IDLE
                                        && isSimDataEnabled(phoneId));
                                    mBillingCyclePreferences[i].updateEnabled();
                                    /* @} */
                                }
                            }
                        }
                        /* @} */
                    }
                };
                /* @}*/
                services.mTelephonyManager.listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_CALL_STATE);
            }
            mSummaryPreference.setSelectable(true);
        } else {
            mSubscriptionSize = 0;
            removePreference(KEY_LIMIT_SUMMARY);
            mSummaryPreference.setSelectable(false);
        }
        boolean hasWifiRadio = DataUsageUtils.hasWifiRadio(context);
        if (hasWifiRadio) {
            addWifiSection();
        }
        if (hasEthernet(context)) {
            addEthernetSection();
        }
        mDataUsageTemplate = hasMobileData ? R.string.cell_data_template
                : hasWifiRadio ? R.string.wifi_data_template
                : R.string.ethernet_data_template;

        setHasOptionsMenu(true);
        /* SPRD: add for bug724515 @{ */
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        // SPRD:Bug 788821 mobile data switch status is not same with status bar
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);
        /* @} */
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (UserManager.get(getContext()).isAdminUser()) {
            inflater.inflate(R.menu.data_usage, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.data_usage_menu_cellular_networks: {
                /* SPRD 709749, when click the cellular network and split screen, happens crash @{ */
                if (getContext() == null) {
                    return false;
                }
                /* @} */
                final Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("com.android.phone",
                        "com.android.phone.MobileNetworkSettings"));
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == findPreference(KEY_STATUS_HEADER)) {
            BillingCycleSettings.BytesEditorFragment.show(this, false);
            return false;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void addMobileSection(int subId, int index) {
        addMobileSection(subId, null, index);
    }

    private void addMobileSection(int subId, SubscriptionInfo subInfo, int index) {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_cellular);
        /*SPRD added for bug 692482, show data network rate  @{ */
        if (mDataNetworkRatePreference != null){
            mDataNetworkRatePreference[index] = (NetworkRatePreference)category.findPreference(KEY_DATA_RATE);
            mDataNetworkRatePreference[index].setSubId(subId);
            mDataNetworkRatePreference[index].setNetworkType(NetworkRatePreference.TAB_MOBILE);
            mCellDataPreferences[index] = (CellDataPreference)category.findPreference(KEY_DATA_ENABLE);
            mBillingCyclePreferences[index] = (BillingCyclePreference)category.findPreference(KEY_MOBILE_BILLING_CYCLE);
        }
        /* @}*/

        category.setTemplate(getNetworkTemplate(subId), subId, services);
        category.pushTemplates(services);
        if (subInfo != null && !TextUtils.isEmpty(subInfo.getDisplayName())) {
            Preference title  = category.findPreference(KEY_MOBILE_USAGE_TITLE);
            title.setTitle(subInfo.getDisplayName());
        }
    }

    private void addWifiSection() {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_wifi);
        /*SPRD added for bug 692482, show wifi network rate  @{ */
        mWifiNetworkRatePreference = (NetworkRatePreference)category.findPreference(KEY_WIFI_RATE);
        mWifiNetworkRatePreference.setNetworkType(NetworkRatePreference.TAB_WIFI);
        /* @} */
        category.setTemplate(NetworkTemplate.buildTemplateWifiWildcard(), 0, services);
        mNetworkRestrictionPreference =
            (NetworkRestrictionsPreference) category.findPreference(KEY_NETWORK_RESTRICTIONS);
    }

    private void addEthernetSection() {
        TemplatePreferenceCategory category = (TemplatePreferenceCategory)
                inflatePreferences(R.xml.data_usage_ethernet);
        category.setTemplate(NetworkTemplate.buildTemplateEthernet(), 0, services);
    }

    private Preference inflatePreferences(int resId) {
        PreferenceScreen rootPreferences = getPreferenceManager().inflateFromResource(
                getPrefContext(), resId, null);
        Preference pref = rootPreferences.getPreference(0);
        rootPreferences.removeAll();

        PreferenceScreen screen = getPreferenceScreen();
        pref.setOrder(screen.getPreferenceCount());
        screen.addPreference(pref);

        return pref;
    }

    private NetworkTemplate getNetworkTemplate(int subscriptionId) {
        NetworkTemplate mobileAll = NetworkTemplate.buildTemplateMobileAll(
                services.mTelephonyManager.getSubscriberId(subscriptionId));
        return NetworkTemplate.normalize(mobileAll,
                services.mTelephonyManager.getMergedSubscriberIds());
    }
    /*SPRD added for bug 692482  @{ */
    @Override
    public void onPause() {
        super.onPause();
       if (mWifiNetworkRatePreference != null) {
            mWifiNetworkRatePreference.clean();
          }
       for (int i = 0; mDataNetworkRatePreference != null && i < mDataNetworkRatePreference.length; i++) {
          if (mDataNetworkRatePreference[i] != null) {
                mDataNetworkRatePreference[i].clean();
            }
       }
    }/* @}*/
    @Override
    public void onResume() {
        super.onResume();
        updateState();
        /*SPRD added for bug 692482  @{ */
        Log.i(TAG,"onResume");
        if (mWifiNetworkRatePreference != null) {
            mWifiNetworkRatePreference.resume();
            mWifiNetworkRatePreference.networkRateInit();
         }
        for (int i = 0; mDataNetworkRatePreference != null && i < mDataNetworkRatePreference.length; i++) {
             if (mDataNetworkRatePreference[i] != null) {
                 mDataNetworkRatePreference[i].resume();
                 mDataNetworkRatePreference[i].networkRateInit();
                }
        }/* @} */
        // SPRD:Bug 788821 mobile data switch status is not same with status bar
        updateMobileDataEnableState();
    }
    private static CharSequence formatTitle(Context context, String template, long usageLevel) {
        final float LARGER_SIZE = 1.25f * 1.25f;  // (1/0.8)^2
        final float SMALLER_SIZE = 1.0f / LARGER_SIZE;  // 0.8^2
        final int FLAGS = Spannable.SPAN_INCLUSIVE_INCLUSIVE;

        final Formatter.BytesResult usedResult = Formatter.formatBytes(context.getResources(),
                usageLevel, Formatter.FLAG_SHORTER);
        final SpannableString enlargedValue = new SpannableString(usedResult.value);
        enlargedValue.setSpan(new RelativeSizeSpan(LARGER_SIZE), 0, enlargedValue.length(), FLAGS);

        final SpannableString amountTemplate = new SpannableString(
                context.getString(com.android.internal.R.string.fileSizeSuffix)
                .replace("%1$s", "^1").replace("%2$s", "^2"));
        final CharSequence formattedUsage = TextUtils.expandTemplate(amountTemplate,
                enlargedValue, usedResult.units);

        final SpannableString fullTemplate = new SpannableString(template);
        fullTemplate.setSpan(new RelativeSizeSpan(SMALLER_SIZE), 0, fullTemplate.length(), FLAGS);
        return TextUtils.expandTemplate(fullTemplate,
                BidiFormatter.getInstance().unicodeWrap(formattedUsage));
    }

    private void updateState() {
        DataUsageController.DataUsageInfo info = mDataUsageController.getDataUsageInfo(
                mDefaultTemplate);
        Context context = getContext();
        /* SPRD Bug:750859 click the data warning and split screen, happens crash */
        if (context == null) {
            return;
        }
        mDataInfoController.updateDataLimit(info,
                services.mPolicyEditor.getPolicy(mDefaultTemplate));

        if (mSummaryPreference != null) {
            mSummaryPreference.setTitle(
                    formatTitle(context, getString(mDataUsageTemplate), info.usageLevel));
            long limit = mDataInfoController.getSummaryLimit(info);
            mSummaryPreference.setSummary(info.period);

            if (limit <= 0) {
                mSummaryPreference.setChartEnabled(false);
            } else {
                mSummaryPreference.setChartEnabled(true);
                mSummaryPreference.setLabels(Formatter.formatFileSize(context, 0),
                        Formatter.formatFileSize(context, limit));
                mSummaryPreference.setRatios(info.usageLevel / (float) limit, 0,
                        (limit - info.usageLevel) / (float) limit);
            }
        }
        if (mLimitPreference != null && (info.warningLevel > 0 || info.limitLevel > 0)) {
            String warning = Formatter.formatFileSize(context, info.warningLevel);
            String limit = Formatter.formatFileSize(context, info.limitLevel);
            mLimitPreference.setSummary(getString(info.limitLevel <= 0 ? R.string.cell_warning_only
                    : R.string.cell_warning_and_limit, warning, limit));
        } else if (mLimitPreference != null) {
            mLimitPreference.setSummary(null);
        }

        updateNetworkRestrictionSummary(mNetworkRestrictionPreference);

        PreferenceScreen screen = getPreferenceScreen();
        for (int i = 1; i < screen.getPreferenceCount(); i++) {
            ((TemplatePreferenceCategory) screen.getPreference(i)).pushTemplates(services);
        }
    }

    /* SPRD:Bug 788821 mobile data switch status is not same with status bar @{ */
    private boolean isSimDataEnabled(int slotId) {
        boolean isSimReady = SubscriptionManager.getSimStateForSlotIndex(slotId)
                == TelephonyManager.SIM_STATE_READY;
        Log.i(TAG, "isSimDataEnabled slotId = " + slotId + " isSimReady = " + isSimReady
                + " mIsAirPlanModeOn = " + mIsAirPlanModeOn);
        return isSimReady && !mIsAirPlanModeOn;
    }

    private void updateMobileDataEnableState () {
        if (mCellDataPreferences != null && mSubscriptionInfos != null) {
            /* SPRD: Bug 837888 @{ */
            int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            for (int i = 0; i < mCellDataPreferences.length; i++) {
                // UNISOC: modify for bug 926974
                if (i < mSubscriptionInfos.size() && null != mCellDataPreferences[i]) {
                    SubscriptionInfo subscriptionInfo = mSubscriptionInfos.get(i);
                    boolean isEnabled = isSimDataEnabled(subscriptionInfo.getSimSlotIndex());
                    int subId = subscriptionInfo.getSubscriptionId();
                    //added for bug 875251, fresh the billcycle status
                    mBillingCyclePreferences[i].updateEnabled();
                    if (subId != defaultDataSubId && mUserManager != null) {
                        mCellDataPreferences[i].setEnabled(isEnabled && mUserManager.isAdminUser()
                                && shouldEnableMobileData());
                        continue;
                    }
                    /* @}*/
                    mCellDataPreferences[i].setEnabled(isEnabled);
                }
            }
        }
    }
    /* @}*/

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DATA_USAGE_SUMMARY;
    }

    @Override
    public NetworkPolicyEditor getNetworkPolicyEditor() {
        return services.mPolicyEditor;
    }

    @Override
    public NetworkTemplate getNetworkTemplate() {
        return mDefaultTemplate;
    }

    @Override
    public void updateDataUsage() {
        updateState();
    }

    @VisibleForTesting
    void updateNetworkRestrictionSummary(NetworkRestrictionsPreference preference) {
        if (preference == null) {
            return;
        }
        mPolicyEditor.read();
        int count = 0;
        for (WifiConfiguration config : mWifiManager.getConfiguredNetworks()) {
            if (WifiConfiguration.isMetered(config, null)) {
                count++;
            }
        }
        preference.setSummary(getResources().getQuantityString(
            R.plurals.network_restrictions_summary, count, count));
    }

    private static class SummaryProvider
            implements SummaryLoader.SummaryProvider {

        private final Activity mActivity;
        private final SummaryLoader mSummaryLoader;
        private final DataUsageController mDataController;

        public SummaryProvider(Activity activity, SummaryLoader summaryLoader) {
            mActivity = activity;
            mSummaryLoader = summaryLoader;
            mDataController = new DataUsageController(activity);
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                DataUsageController.DataUsageInfo info = mDataController.getDataUsageInfo();
                String used;
                if (info == null) {
                    used = Formatter.formatFileSize(mActivity, 0);
                } else if (info.limitLevel <= 0) {
                    used = Formatter.formatFileSize(mActivity, info.usageLevel);
                } else {
                    used = Utils.formatPercentage(info.usageLevel, info.limitLevel);
                }
                mSummaryLoader.setSummary(this,
                        mActivity.getString(R.string.data_usage_summary_format, used));
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
        = SummaryProvider::new;

    /**
     * For search
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {

            @Override
            public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                    boolean enabled) {
                List<SearchIndexableResource> resources = new ArrayList<>();
                SearchIndexableResource resource = new SearchIndexableResource(context);
                resource.xmlResId = R.xml.data_usage;
                resources.add(resource);

                resource = new SearchIndexableResource(context);
                resource.xmlResId = R.xml.data_usage_cellular;
                resources.add(resource);

                resource = new SearchIndexableResource(context);
                resource.xmlResId = R.xml.data_usage_wifi;
                resources.add(resource);

                return resources;
            }

            @Override
            public List<String> getNonIndexableKeys(Context context) {
                List<String> keys = super.getNonIndexableKeys(context);

                if (!DataUsageUtils.hasMobileData(context)) {
                    keys.add(KEY_MOBILE_USAGE_TITLE);
                    keys.add(KEY_MOBILE_DATA_USAGE_TOGGLE);
                    keys.add(KEY_MOBILE_DATA_USAGE);
                    keys.add(KEY_MOBILE_BILLING_CYCLE);
                }

                if (!DataUsageUtils.hasWifiRadio(context)) {
                    keys.add(KEY_WIFI_DATA_USAGE);
                    keys.add(KEY_NETWORK_RESTRICTIONS);
                }

                // This title is named Wifi, and will confuse users.
                keys.add(KEY_WIFI_USAGE_TITLE);

                return keys;
            }
        };

        /* SPRD: add for bug724515 @{ */
        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.d(TAG, "onDestroy");
            final Context context = getContext();
            if (context != null) {
                context.unregisterReceiver(mReceiver);
            }

        }

        private BroadcastReceiver mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                /* SPRD:Bug 788821 mobile data switch status is not same with status bar @{ */
                if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                    List<SubscriptionInfo> subscriptions =
                            services.mSubscriptionManager.getActiveSubscriptionInfoList();
                    int size = (subscriptions != null) ? subscriptions.size() : 0;
                    Log.d(TAG, "action = " + action + " size = " + size
                            + ",  mSubscriptionSize = " + mSubscriptionSize);
                    /* SPRD: modified for Bug 712313 @{ */
                    if (size != mSubscriptionSize) {
                        Log.d(TAG, "BroadcastReceiver finish");
                        finish();
                    }
                    /* @}*/
                } else if ((Intent.ACTION_AIRPLANE_MODE_CHANGED).equals(action)) {
                    mIsAirPlanModeOn = WirelessUtils.isAirplaneModeOn(context);
                    updateMobileDataEnableState();
                }
                /* @}*/
            }
        };
        /* @} */
}
