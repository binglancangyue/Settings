package com.android.settings.deviceinfo;

import android.content.Context;

import com.android.settings.R;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settings.core.PreferenceControllerMixin;


/**
 * @author Altair
 * @date :2019.12.13 下午 03:04
 * @description:
 */
public class RecoverySystemUpdatePreferenceController extends
        AbstractPreferenceController implements PreferenceControllerMixin {
    private static String KEY_RECOVERY_SYSTEM_UPDATE = "RecoverySystemUpdate";

    public RecoverySystemUpdatePreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_RECOVERY_SYSTEM_UPDATE;
    }
}
