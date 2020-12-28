package com.android.settings.display;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.support.v7.preference.Preference;

import com.android.settings.SleepSettings;
import com.android.settings.core.PreferenceControllerMixin;

import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;

import com.android.settingslib.core.AbstractPreferenceController;

public class SleepPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {
    private static final String SLEEP = "sleep_settings";
    public static final String ACTION_GO_TO_SLEEP = "com.android.systemui.action_go_to_sleep";
    private Context mContext;

    public SleepPreferenceController(Context context) {
        super(context);
        this.mContext = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        int switch_on = 0;
//        if (value) {
//            Intent intent = new Intent(ACTION_GO_TO_SLEEP);
//            mContext.sendBroadcast(intent);
////            mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
//            switch_on = 1;
//        }
//        Settings.System.putInt(mContext.getContentResolver(), "sys.mistaketouch.switch", switch_on);
        return true;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
//        if (TextUtils.equals(preference.getKey(), SLEEP)) {
//            Intent intent = new Intent(ACTION_GO_TO_SLEEP);
//            mContext.sendBroadcast(intent);
//            return true;
//        }
        Log.d("TAG", "handlePreferenceTreeClick: ");
        Intent intent = new Intent(ACTION_GO_TO_SLEEP);
        mContext.sendBroadcast(intent);
        return true;
    }


    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return SLEEP;
    }

    @Override
    public void updateState(Preference preference) {
//        ((SwitchPreference) preference).setChecked(isChecked());
    }

    private boolean isChecked() {
        int switch_on = Settings.System.getInt(mContext.getContentResolver(), "sys.mistaketouch.switch", 0);
        boolean value = false;
        if (switch_on == 1) {
            value = true;
        }
        return value;
    }

}
