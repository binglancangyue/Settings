package com.android.settings.applications;

import android.app.Application;
import android.content.Context;

public class SettingsApp extends Application {
    private static SettingsApp mInstance;

    /**
     * 获取context
     *
     * @return
     */
    public static Context getInstance() {
        if (mInstance == null) {
            mInstance = new SettingsApp();
        }
        return mInstance;
    }
}
