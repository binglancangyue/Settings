package com.android.settings.applications;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import com.android.settings.R;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * @author Altair
 * @date :2019.12.25 上午 11:04
 * @description:
 */
public class ResetSDCardHelper implements DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener {
    private AlertDialog mResetDialog;
    private Context mContext;
    private static final String EXTRA_RESET_DIALOG = "resetDialog";

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mResetDialog != dialog) {
            return;
        }
        start();
    }

    public void onSaveInstanceState(Bundle outState) {
        if (mResetDialog != null) {
            outState.putBoolean(EXTRA_RESET_DIALOG, true);
        }
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_RESET_DIALOG)) {
            buildResetDialog();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mResetDialog == dialog) {
            mResetDialog = null;
        }
    }

    public ResetSDCardHelper(Context context) {
        mContext = context;
    }

    public void buildResetDialog() {
        if (mResetDialog == null) {
            mResetDialog = new AlertDialog.Builder(mContext)
                    .setTitle(R.string.reset_sdcard_preferences_title)
                    .setMessage(R.string.reset_sdcard_preferences_desc)
                    .setPositiveButton(R.string.reset_sdcard_preferences_button, this)
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener(this)
                    .show();
        }
    }

    private void start() {
        Parcelable sv = getStoragePath(true);
        ComponentName formatter = new ComponentName("android", "com.android.internal.os.storage" +
                ".ExternalStorageFormatter");
        Intent intent = new Intent("com.android.internal.os.storage.FORMAT_ONLY");
        intent.setComponent(formatter);
        intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, sv);
        mContext.startService(intent);
        Log.d("aa", "start: ");
    }

    public Parcelable getStoragePath(boolean isRemoveAble) {
        StorageManager mStorageManager =
                (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (isRemoveAble == removable) {
                    return (Parcelable) storageVolumeElement;

                }
            }
        } catch (ClassNotFoundException
                | InvocationTargetException
                | NoSuchMethodException
                | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
