/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settings.faceid;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.Log;

import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.R;


/**
 * Activity notifying user look at the faceid camera for faceid enrollment.
 */
public class FaceIdEnrollFindSensor extends FaceIdEnrollBase {
    private static final String TAG = "FaceIdEnrollFindSensor";

    private static final int CONFIRM_REQUEST = 1;
    private static final int ENROLLING = 2;
    private static final int FACEID_MAX_TEMPLATES_PER_USER = 5;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceid_enroll_find_sensor);
        setHeaderText(R.string.security_settings_faceid_enroll_add_faceid_title);
        if (mToken == null) {
            launchConfirmLock();
        }
    }

    @Override
    protected void onNextButtonClick() {
        startActivityForResult(getEnrollingIntent(), ENROLLING);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CONFIRM_REQUEST) {
            if (resultCode == RESULT_OK) {
                mToken = data.getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                Log.d(TAG, "telefk mToken = " + mToken);
                overridePendingTransition(R.anim.suw_slide_next_in, R.anim.suw_slide_next_out);
            } else {
                finish();
            }
        } else if (requestCode == ENROLLING) {
            if (resultCode == RESULT_FINISHED) {
                setResult(RESULT_FINISHED);
                finish();
            } else {
                setResult(RESULT_SKIP);
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void launchConfirmLock() {
        long challenge = -1;//getSystemService(IrisManager.class).preEnroll();
        ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(this);
        if (!helper.launchConfirmationActivity(CONFIRM_REQUEST,
                getString(R.string.security_settings_faceid_preference_title),
                null, null, challenge)) {

            // This shouldn't happen, as we should only end up at this step if a lock thingy is
            // already set.
            finish();
        }
    }
}
