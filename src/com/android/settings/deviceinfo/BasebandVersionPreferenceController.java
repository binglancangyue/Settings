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
import android.content.Intent;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.util.Log;

import com.android.settings.Customer;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class BasebandVersionPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin {
    private int mDevHitCountdown = 8;
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
         if (!SupportCPVersion.getInstance().isSupport()) {
             preference.setSummary(SystemProperties.get(BASEBAND_PROPERTY,
                mContext.getResources().getString(R.string.device_info_default)));
         }else{
             SupportCPVersion.getInstance().initPreference( mContext, preference);
             SupportCPVersion.getInstance().startRunnable();
         }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), KEY_BASEBAND_VERSION)) {
            return false;
        }
        if (!Customer.IS_SUPPORT_BACK_DOOR) {
            return false;
        }
        if (mDevHitCountdown > 0) {
            mDevHitCountdown--;
        } else if (mDevHitCountdown == 0) {
            mDevHitCountdown = 8;
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            mContext.startActivity(intent);
        }
        return true;
    }

}
