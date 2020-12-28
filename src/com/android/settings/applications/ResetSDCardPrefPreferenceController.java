package com.android.settings.applications;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.text.TextUtils;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnCreate;
import com.android.settingslib.core.lifecycle.events.OnSaveInstanceState;

/**
 * @author Altair
 * @date :2019.12.25 上午 11:34
 * @description:
 */
public class ResetSDCardPrefPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnCreate, OnSaveInstanceState  {
    private ResetSDCardHelper mResetSDCardHelper;

    public ResetSDCardPrefPreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        mResetSDCardHelper = new ResetSDCardHelper(context);
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }
        mResetSDCardHelper.buildResetDialog();
        return true;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return "reset_sdcard_prefs";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mResetSDCardHelper.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mResetSDCardHelper.onSaveInstanceState(outState);
    }
}
