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

package com.android.settings.notification;

import static com.android.settings.notification.SettingPref.TYPE_SYSTEM;

import android.content.Context;

import android.media.AudioManager;
import android.os.AsyncTask;
import android.provider.Settings.System;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.core.lifecycle.Lifecycle;

public class TouchSoundPreferenceController extends SettingPrefController {

    private static final String KEY_TOUCH_SOUNDS = "touch_sounds";

    public TouchSoundPreferenceController(Context context, SettingsPreferenceFragment parent,
            Lifecycle lifecycle) {
        super(context, parent, lifecycle);
        mPreference = new SettingPref(
            TYPE_SYSTEM, KEY_TOUCH_SOUNDS, System.SOUND_EFFECTS_ENABLED, DEFAULT_ON) {
            @Override
            protected boolean setSetting(final Context context, final int value) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        final AudioManager am =
                            (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                        if (value != 0) {
                            am.loadSoundEffects();
                        } else {
                            am.unloadSoundEffects();
                        }
                    }
                });
                return super.setSetting(context, value);
            }
        };
    }
}
