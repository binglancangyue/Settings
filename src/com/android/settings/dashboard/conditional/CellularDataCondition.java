/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.android.settings.dashboard.conditional;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.Settings;

import com.android.settings.datausage.DisableDataUsage;
import com.android.settings.Utils;

public class CellularDataCondition extends Condition {

    private final Receiver mReceiver;

    private static final IntentFilter DATA_CONNECTION_FILTER =
        new IntentFilter(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);

    public CellularDataCondition(ConditionManager manager) {
        super(manager);
        mReceiver = new Receiver();
    }

    @Override
    public void refreshState() {
        ConnectivityManager connectivity = mManager.getContext().getSystemService(
                ConnectivityManager.class);
        TelephonyManager telephony = mManager.getContext().getSystemService(TelephonyManager.class);
        if (!connectivity.isNetworkSupported(ConnectivityManager.TYPE_MOBILE)
                || telephony.getSimState() != TelephonyManager.SIM_STATE_READY) {
            setActive(false);
            return;
        }
        setActive(!telephony.isDataEnabled());
    }

    @Override
    protected BroadcastReceiver getReceiver() {
        return mReceiver;
    }

    @Override
    protected IntentFilter getIntentFilter() {
        return DATA_CONNECTION_FILTER;
    }

    @Override
    public Icon getIcon() {
        return Icon.createWithResource(mManager.getContext(), R.drawable.ic_cellular_off);
    }

    @Override
    public CharSequence getTitle() {
        return mManager.getContext().getString(R.string.condition_cellular_title);
    }

    @Override
    public CharSequence getSummary() {
        return mManager.getContext().getString(R.string.condition_cellular_summary);
    }

    @Override
    public CharSequence[] getActions() {
        return new CharSequence[] { mManager.getContext().getString(R.string.condition_turn_on) };
    }

    @Override
    public void onPrimaryClick() {
        /* SPRD bug for 849374 Make the Click invalid when the version is Cmcc @{*/
        if (!Utils.isBandwidthControlEnabled() ||!DisableDataUsage.getInstance().isCmcc()) {
            mManager.getContext().startActivity(new Intent(mManager.getContext(),
                    Settings.DataUsageSummaryActivity.class));
        }/* @} */
    }

    @Override
    public void onActionClick(int index) {
        /* SPRD: add for bug718882 @{ */
        if (isAirplaneModeOn()) {
            Toast.makeText(mManager.getContext(), R.string.toggle_data_error_airplane,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        /* @} */
        if (index == 0) {
            TelephonyManager telephony = mManager.getContext().getSystemService(
                    TelephonyManager.class);
            telephony.setDataEnabled(true);
            setActive(false);
        } else {
            throw new IllegalArgumentException("Unexpected index " + index);
        }
    }

    @Override
    public int getMetricsConstant() {
        return MetricsEvent.SETTINGS_CONDITION_CELLULAR_DATA;
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED.equals(
                    intent.getAction())) {
                CellularDataCondition condition = ConditionManager.get(context).getCondition(
                        CellularDataCondition.class);
                if (condition != null) {
                    condition.refreshState();
                }
            }
        }
    }

    /* SPRD: add for bug718882 @{ */
    private boolean isAirplaneModeOn() {
        return android.provider.Settings.Global.getInt(mManager.getContext().getContentResolver(),
                android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
    /* @} */
}
