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
 * limitations under the License.
 */

package com.android.settings.notification;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.text.TextWatcher;
import android.content.Context;
import android.text.Editable;

import com.android.settings.R;

public abstract class ZenRuleNameDialog {
    private static final String TAG = "ZenRuleNameDialog";
    private static final boolean DEBUG = ZenModeSettings.DEBUG;

    private final AlertDialog mDialog;
    public final EditText mEditText;
    private final CharSequence mOriginalRuleName;
    private final boolean mIsNew;
    private static final int TEXT_MAX_LENGTH = 128;

    public ZenRuleNameDialog(Context context, CharSequence ruleName) {
        mIsNew = ruleName == null;
        mOriginalRuleName = ruleName;
        final View v = LayoutInflater.from(context).inflate(R.layout.zen_rule_name, null, false);
        mEditText = (EditText) v.findViewById(R.id.rule_name);
        if (!mIsNew) {
            mEditText.setText(ruleName);
            mEditText.setSelection(ruleName.length());
        }
        mEditText.setSelectAllOnFocus(true);
        /* SPRD: Modify for bug712490: add toast for input up to max @{ */
        mEditText.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void afterTextChanged(Editable s) {
            if (s.length() >= TEXT_MAX_LENGTH) {
                Toast.makeText(context, context.getResources().
                        getString(R.string.rules_name_too_long), Toast.LENGTH_SHORT).show();
            }
         }
         });
    /* @} */
        mDialog = new AlertDialog.Builder(context)
                .setTitle(mIsNew ? R.string.zen_mode_add_rule : R.string.zen_mode_rule_name)
                .setView(v)
                .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String newName = trimmedText();
                        if (TextUtils.isEmpty(newName)) {
                            return;
                        }
                        if (!mIsNew && mOriginalRuleName != null
                                && mOriginalRuleName.equals(newName)) {
                            return;  // no change to an existing rule, just dismiss
                        }
                        onOk(newName);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    abstract public void onOk(String ruleName);

    public void show() {
        mDialog.show();
    }

    /* SPRD: 751571 After the screen is rotated, the automatic rule dialog disappears @{ */
    public boolean isShowing() {
        return mDialog.isShowing();
    }
    /* @} */

    private String trimmedText() {
        return mEditText.getText() == null ? null : mEditText.getText().toString().trim();
    }
}
