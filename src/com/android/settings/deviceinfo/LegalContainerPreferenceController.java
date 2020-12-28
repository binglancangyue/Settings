package com.android.settings.deviceinfo;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

/**
 * @author Altair
 * @date :2019.12.13 上午 10:31
 * @description:
 */
public class LegalContainerPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin {
    private static final String KEY_LEGAL_CONTAINER = "legal_container";

    public LegalContainerPreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_LEGAL_CONTAINER;
    }

}
