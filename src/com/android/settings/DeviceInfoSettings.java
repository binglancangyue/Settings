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
import android.app.Fragment;
import android.content.Context;
import android.util.Log;
import android.content.Intent;
import android.provider.SearchIndexableResource;
import android.content.res.Resources;
import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.deviceinfo.AdditionalSystemUpdatePreferenceController;
import com.android.settings.deviceinfo.RsAdditionalSystemUpdatePreferenceController;
import com.android.settings.deviceinfo.OtaUpdate;
import com.android.settings.deviceinfo.SprdAdditionalSystemUpdatePreferenceController;
import com.android.settings.deviceinfo.BasebandVersionPreferenceController;
import com.android.settings.deviceinfo.BuildNumberPreferenceController;
import com.android.settings.deviceinfo.DeviceModelPreferenceController;
import com.android.settings.deviceinfo.FccEquipmentIdPreferenceController;
import com.android.settings.deviceinfo.FeedbackPreferenceController;
import com.android.settings.deviceinfo.FirmwareVersionPreferenceController;
import com.android.settings.deviceinfo.KernelVersionPreferenceController;
import com.android.settings.deviceinfo.ManualPreferenceController;
import com.android.settings.deviceinfo.RegulatoryInfoPreferenceController;
import com.android.settings.deviceinfo.SELinuxStatusPreferenceController;
import com.android.settings.deviceinfo.SafetyInfoPreferenceController;
import com.android.settings.deviceinfo.SecurityPatchPreferenceController;
import com.android.settings.deviceinfo.FotaUpdatePreferenceController;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;

import com.android.settings.search.SearchIndexableRaw;
import com.sprd.settings.sprdramdisplay.SprdRamDisplayPreferenceController;
import com.android.settings.deviceinfo.LegalContainerPreferenceController;
import com.android.settings.deviceinfo.RecoverySystemUpdatePreferenceController;
import com.android.settings.deviceinfo.StatusPreferenceController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeviceInfoSettings extends DashboardFragment implements Indexable {

    private static final String LOG_TAG = "DeviceInfoSettings";

    private static final String KEY_LEGAL_CONTAINER = "legal_container";

    private static String KEY_RECOVERY_SYSTEM_UPDATE = "RecoverySystemUpdate";
    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DEVICEINFO;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_uri_about;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final BuildNumberPreferenceController buildNumberPreferenceController =
                getPreferenceController(BuildNumberPreferenceController.class);
        if (buildNumberPreferenceController.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.device_info_settings;
    }

    @Override
    protected List<AbstractPreferenceController> getPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getActivity(), this /* fragment */,
                getLifecycle());
    }

    private static class SummaryProvider implements SummaryLoader.SummaryProvider {

        private final SummaryLoader mSummaryLoader;

        public SummaryProvider(SummaryLoader summaryLoader) {
            mSummaryLoader = summaryLoader;
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                mSummaryLoader.setSummary(this, DeviceModelPreferenceController.getDeviceModel());
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                SummaryLoader summaryLoader) {
            return new SummaryProvider(summaryLoader);
        }
    };

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Activity activity, Fragment fragment, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(
                new BuildNumberPreferenceController(context, activity, fragment, lifecycle));
        controllers.add(new AdditionalSystemUpdatePreferenceController(context));
		controllers.add(new RsAdditionalSystemUpdatePreferenceController(context));
        /*SPRD added for bug 692496 local software update controller*/
        controllers.add(new SprdAdditionalSystemUpdatePreferenceController(context));
		controllers.add(new FotaUpdatePreferenceController(context));
        controllers.add(new ManualPreferenceController(context));
        controllers.add(new FeedbackPreferenceController(fragment, context));
        controllers.add(new KernelVersionPreferenceController(context));
        controllers.add(new BasebandVersionPreferenceController(context));
        controllers.add(new FirmwareVersionPreferenceController(context, lifecycle));
        controllers.add(new RegulatoryInfoPreferenceController(context));
        controllers.add(new DeviceModelPreferenceController(context, fragment));
        controllers.add(new SecurityPatchPreferenceController(context));
        controllers.add(new FccEquipmentIdPreferenceController(context));
        controllers.add(new SELinuxStatusPreferenceController(context));
        controllers.add(new SafetyInfoPreferenceController(context));
        /*SPRD added for bug 748539 adding phone ram */
        controllers.add(new SprdRamDisplayPreferenceController(context));
        //add by lym start
        controllers.add(new LegalContainerPreferenceController(context));
        controllers.add(new StatusPreferenceController(context));
//        controllers.add(new RecoverySystemUpdatePreferenceController(context));
        //end
        return controllers;
    }

    /**
     * add by lym
     * @param savedInstanceState
     * @param rootKey
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        getPreferenceScreen().removePreference(findPreference(KEY_RECOVERY_SYSTEM_UPDATE));
    }

    /**
     * For Search.
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.device_info_settings;
                    return Arrays.asList(sir);
                }
                //SPRD: added for bug 843855, config the locale system update to search Index
                @Override
                public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                    final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();

                    final Resources res = context.getResources();
                    boolean isOtaUpdate = OtaUpdate.getInstance(context).isSupport();
                    Log.d(LOG_TAG, "isOtaUpdate ="+isOtaUpdate);
                    if(isOtaUpdate){
                        // Add OTA update
                        SearchIndexableRaw data = new SearchIndexableRaw(context);
                        data.key = KEY_RECOVERY_SYSTEM_UPDATE;
                        data.title = res.getString(R.string.recovery_update_title);
                        Log.d(LOG_TAG, "title ="+data.title);
                        data.screenTitle = res.getString(R.string.about_settings);
                        result.add(data);
                    }

                    return result;
                }

                @Override
                public List<AbstractPreferenceController> getPreferenceControllers(Context context) {
                    return buildPreferenceControllers(context, null /*activity */,
                            null /* fragment */, null /* lifecycle */);
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    keys.add(KEY_LEGAL_CONTAINER);
                    return keys;
                }
            };
}
