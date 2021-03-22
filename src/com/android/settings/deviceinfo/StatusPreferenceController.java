package com.android.settings.deviceinfo;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

/**
 * @author Altair
 * @date :2019.12.13 上午 11:43
 * @description:
 */
public class StatusPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin {
    private static final String KEY_STATUS = "status_info";

    public StatusPreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_STATUS;
    }

}
