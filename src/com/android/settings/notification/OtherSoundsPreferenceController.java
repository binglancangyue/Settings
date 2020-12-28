package com.android.settings.notification;

import android.content.Context;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settings.core.PreferenceControllerMixin;

/**
 * @author Altair
 * @date :2019.12.16 下午 03:19
 * @description:
 */
public class OtherSoundsPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin {
    private static final String KEY_OTHER_SOUNDS = "other_sounds";

    public OtherSoundsPreferenceController(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_OTHER_SOUNDS;
    }
}
