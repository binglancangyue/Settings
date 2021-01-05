/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;
import android.support.v7.preference.Preference;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class BasebandVersionPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin {

    private static final String BASEBAND_PROPERTY = "gsm.version.baseband";
    private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private Context mContext;

    public BasebandVersionPreferenceController(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public boolean isAvailable() {
        return !Utils.isWifiOnly(mContext);
    }

    @Override
    public String getPreferenceKey() {
        return KEY_BASEBAND_VERSION;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        /*SPRD added for bug 692483*/
        String baseband = SystemProperties.get(BASEBAND_PROPERTY,
                mContext.getResources().getString(R.string.device_info_default));
        baseband = baseband.replace("9832e", "8541e");
        String mar = "MARLIN2_17A_RLS1_W18.45.3|sharkle_cm4|11-07-2018 16:04:32";
        baseband = baseband + "\n" + mar;
        preference.setSummary(baseband);
//        if (!SupportCPVersion.getInstance().isSupport()) {
//            preference.setSummary(SystemProperties.get(BASEBAND_PROPERTY,
//                    mContext.getResources().getString(R.string.device_info_default)));
//        } else {
//            Log.d("aaa", "updateState: SupportCPVersion");
//            SupportCPVersion.getInstance().initPreference(mContext, preference);
//            SupportCPVersion.getInstance().startRunnable();
//        }
    }
}
