/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.users;

import android.app.Dialog;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.pm.UserInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.util.Log;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.RestrictedLockUtils;

import java.util.List;

/**
 * Settings screen for configuring a specific user. It can contain user restrictions
 * and deletion controls. It is shown when you tap on the settings icon in the
 * user management (UserSettings) screen.
 *
 * Arguments to this fragment must include the userId of the user (in EXTRA_USER_ID) for whom
 * to display controls, or should contain the EXTRA_USER_GUEST = true.
 */
public class UserDetailsSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    private static final String TAG = UserDetailsSettings.class.getSimpleName();

    private static final String KEY_ENABLE_TELEPHONY = "enable_calling";
    private static final String KEY_REMOVE_USER = "remove_user";

    /** Integer extra containing the userId to manage */
    static final String EXTRA_USER_ID = "user_id";
    /** Boolean extra to indicate guest preferences */
    static final String EXTRA_USER_GUEST = "guest_user";

    private static final int DIALOG_CONFIRM_REMOVE = 1;
    private static final int DIALOG_CONFIRM_ENABLE_CALLING = 2;
    private static final int DIALOG_CONFIRM_ENABLE_CALLING_AND_SMS = 3;

    // SPRD:modify for Bug 712336 settigns crash with remove new user.
    private static final String REMOVE_USER_EVENT = "remove_user";

    private UserManager mUserManager;
    private SwitchPreference mPhonePref;
    private Preference mRemoveUserPref;

    private UserInfo mUserInfo;
    private boolean mGuestUser;
    private Bundle mDefaultGuestRestrictions;

    private int mUserId;
    private Dialog mDeleteUserDialog = null;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.USER_DETAILS;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getActivity();
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);

        addPreferencesFromResource(R.xml.user_details_settings);
        mPhonePref = (SwitchPreference) findPreference(KEY_ENABLE_TELEPHONY);
        mRemoveUserPref = findPreference(KEY_REMOVE_USER);

        mGuestUser = getArguments().getBoolean(EXTRA_USER_GUEST, false);

        if (!mGuestUser) {
            // Regular user. Get the user id from the caller.
            final int userId = getArguments().getInt(EXTRA_USER_ID, -1);
            mUserId = userId;
            if (userId == -1) {
                throw new RuntimeException("Arguments to this fragment must contain the user id");
            }
            mUserInfo = mUserManager.getUserInfo(userId);
            mPhonePref.setChecked(!mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_OUTGOING_CALLS, new UserHandle(userId)));
            mRemoveUserPref.setOnPreferenceClickListener(this);
        } else {
            // These are not for an existing user, just general Guest settings.
            removePreference(KEY_REMOVE_USER);
            // Default title is for calling and SMS. Change to calling-only here
            mPhonePref.setTitle(R.string.user_enable_calling);
            mDefaultGuestRestrictions = mUserManager.getDefaultGuestRestrictions();
            mPhonePref.setChecked(
                    !mDefaultGuestRestrictions.getBoolean(UserManager.DISALLOW_OUTGOING_CALLS));
        }
        if (RestrictedLockUtils.hasBaseUserRestriction(context,
                UserManager.DISALLOW_REMOVE_USER, UserHandle.myUserId())) {
            removePreference(KEY_REMOVE_USER);
        }
        mPhonePref.setOnPreferenceChangeListener(this);

        /* SPRD:modify for Bug 712336 settigns crash with remove new user. @{ */
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(REMOVE_USER_EVENT),
                false, mRemoveUserObserver, UserHandle.USER_OWNER);
        /* @} */
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        /* SPRD:modify for Bug 712336 settigns crash with remove new user. @{ */
        if (!mGuestUser) {
            if (null == mUserManager.getUserInfo(mUserId)) {
                finishFragment();
                return false;
            }
        }
        /* @} */
        if (preference == mRemoveUserPref) {
            if (!mUserManager.isAdminUser()) {
                throw new RuntimeException("Only admins can remove a user");
            }
            /* SPRD: modify for Bug 750376 settings crash @{ */
            if (mUserInfo == null) return false;
            /* @} */
            showDialog(DIALOG_CONFIRM_REMOVE);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        /* SPRD:modify for Bug 712336 settigns crash with remove new user. @{ */
        if (!mGuestUser) {
            if (null == mUserManager.getUserInfo(mUserId)) {
                finishFragment();
                return false;
            }
        }
        /* @} */
        if (Boolean.TRUE.equals(newValue)) {
            showDialog(mGuestUser ? DIALOG_CONFIRM_ENABLE_CALLING
                    : DIALOG_CONFIRM_ENABLE_CALLING_AND_SMS);
            return false;
        }
        enableCallsAndSms(false);
        return true;
    }

    void enableCallsAndSms(boolean enabled) {
        mPhonePref.setChecked(enabled);
        if (mGuestUser) {
            mDefaultGuestRestrictions.putBoolean(UserManager.DISALLOW_OUTGOING_CALLS, !enabled);
            // SMS is always disabled for guest
            mDefaultGuestRestrictions.putBoolean(UserManager.DISALLOW_SMS, true);
            mUserManager.setDefaultGuestRestrictions(mDefaultGuestRestrictions);

            // Update the guest's restrictions, if there is a guest
            // TODO: Maybe setDefaultGuestRestrictions() can internally just set the restrictions
            // on any existing guest rather than do it here with multiple Binder calls.
            List<UserInfo> users = mUserManager.getUsers(true);
            for (UserInfo user: users) {
                if (user.isGuest()) {
                    UserHandle userHandle = UserHandle.of(user.id);
                    for (String key : mDefaultGuestRestrictions.keySet()) {
                        mUserManager.setUserRestriction(
                                key, mDefaultGuestRestrictions.getBoolean(key), userHandle);
                    }
                }
            }
        } else {
            if (mUserInfo == null) {
                return;
            }
            UserHandle userHandle = UserHandle.of(mUserInfo.id);
            mUserManager.setUserRestriction(UserManager.DISALLOW_OUTGOING_CALLS, !enabled,
                    userHandle);
            mUserManager.setUserRestriction(UserManager.DISALLOW_SMS, !enabled, userHandle);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        Context context = getActivity();
        if (context == null) return null;
        switch (dialogId) {
            case DIALOG_CONFIRM_REMOVE:
                if (mUserInfo != null) {
                    mDeleteUserDialog = UserDialogs.createRemoveDialog(getActivity(), mUserInfo.id,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    removeUser();
                                }
                            });
                    return mDeleteUserDialog;
                }
            case DIALOG_CONFIRM_ENABLE_CALLING:
                return UserDialogs.createEnablePhoneCallsDialog(getActivity(),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                enableCallsAndSms(true);
                            }
                        });
            case DIALOG_CONFIRM_ENABLE_CALLING_AND_SMS:
                return UserDialogs.createEnablePhoneCallsAndSmsDialog(getActivity(),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                enableCallsAndSms(true);
                            }
                        });
        }
        throw new IllegalArgumentException("Unsupported dialogId " + dialogId);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        switch (dialogId) {
            case DIALOG_CONFIRM_REMOVE:
                return MetricsProto.MetricsEvent.DIALOG_USER_REMOVE;
            case DIALOG_CONFIRM_ENABLE_CALLING:
                return MetricsProto.MetricsEvent.DIALOG_USER_ENABLE_CALLING;
            case DIALOG_CONFIRM_ENABLE_CALLING_AND_SMS:
                return MetricsProto.MetricsEvent.DIALOG_USER_ENABLE_CALLING_AND_SMS;
            default:
                return 0;
        }
    }

    void removeUser() {
        dismissDeleteUserDialog();
        mUserManager.removeUser(mUserInfo.id);
         Log.d(TAG,"removeUser()");
        /* SPRD:modify for Bug 712336 settigns crash with remove new user. @{ */
        Settings.System.putInt(getContentResolver(), REMOVE_USER_EVENT, 1);
        //finishFragment();
        /* @} */
    }

    /* SPRD:modify for Bug 712336 settigns crash with remove new user. @{ */
    private final ContentObserver mRemoveUserObserver = new ContentObserver(new Handler(true)) {
        @Override
        public void onChange(boolean selfChange) {
            boolean isRemoveUser = (Settings.System.getInt(
                    getContentResolver(), REMOVE_USER_EVENT, 0) == 1);
            Log.d(TAG,"onChange isRemoveUser = " + isRemoveUser);
            if (isRemoveUser) {
                dismissDeleteUserDialog();
                finishFragment();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mRemoveUserObserver);
        Settings.System.putInt(getContentResolver(), REMOVE_USER_EVENT, 0);
    }
    /* @} */
    /*SPRD:bug 758481 settings crash when removing new user and splitting screen @{ */
    private void dismissDeleteUserDialog() {
        if (mDeleteUserDialog != null && mDeleteUserDialog.isShowing()) {
            mDeleteUserDialog.dismiss();
        }
    }
    /* @} */
}
