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

package com.android.settings.deviceinfo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.util.Log;

import android.widget.Toast;
import com.android.settings.R;

import java.util.Objects;
import android.app.PackageManagerEx;
import static com.android.settings.deviceinfo.StorageSettings.TAG;

public class StorageWizardMigrateConfirm extends StorageWizardBase {
    private MigrateEstimateTask mEstimate;
    /* SPRD: add for storage manage @{ */
    private Thread mTask;
    /* @} */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.storage_wizard_generic);

        // When called with just disk, find the first private volume
        if (mVolume == null) {
            mVolume = findFirstVolume(VolumeInfo.TYPE_PRIVATE);
        }

        /* SPRD: modify for storage manage @{
         * @orig final VolumeInfo sourceVol = getPackageManager().getPrimaryStorageCurrentVolume();
         */
        final VolumeInfo sourceVol;
        if (mVolume != null && mVolume.type == VolumeInfo.TYPE_PRIVATE) {
            final VolumeInfo primaryEmulatedVol = ((PackageManagerEx)getPackageManager()).getPrimaryEmulatedStorageCurrentVolume();
            if (!Objects.equals(mVolume,primaryEmulatedVol)) {
                sourceVol = primaryEmulatedVol;
            } else {
                sourceVol = getPackageManager().getPrimaryStorageCurrentVolume();
            }
        } else {
            sourceVol = getPackageManager().getPrimaryStorageCurrentVolume();
        }
        /* @} */
        if (sourceVol == null || mVolume == null) {
            Log.d(TAG, "Missing either source or target volume");
            finish();
            return;
        }

        final String sourceDescrip = mStorage.getBestVolumeDescription(sourceVol);
        final String targetDescrip = mStorage.getBestVolumeDescription(mVolume);

        setIllustrationType(ILLUSTRATION_INTERNAL);
        setHeaderText(R.string.storage_wizard_migrate_confirm_title, targetDescrip);
        setBodyText(R.string.memory_calculating_size);
        setSecondaryBodyText(R.string.storage_wizard_migrate_details, targetDescrip);

        mEstimate = new MigrateEstimateTask(this) {
            @Override
            public void onPostExecute(String size, String time) {
                setBodyText(R.string.storage_wizard_migrate_confirm_body, time, size,
                        sourceDescrip);
            }
        };

        mEstimate.copyFrom(getIntent());
        mEstimate.execute();

        getNextButton().setText(R.string.storage_wizard_migrate_confirm_next);
    }

    @Override
    public void onNavigateNext() {
        /* SPRD: modify for emulated storage @{ */
        if (mTask != null) {
            Log.w(TAG, "Task thread already exist.");
            return;
        }
        Toast toast = Toast.makeText(StorageWizardMigrateConfirm.this, getString(R.string.another_migration_already_in_progress),
                            Toast.LENGTH_LONG);
        mTask = new Thread() {
            @Override
            public void run() {

                final int moveId;
                // We only expect exceptions from MountService#setPrimaryStorageUuid
                try {
                    moveId = ((PackageManagerEx)getPackageManager()).movePrimaryEmulatedStorage(mVolume);
                }  catch (IllegalArgumentException e) {
                    Log.w(TAG, "set primary or migrate data get an exception", e);
                    StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);

                    if (Objects.equals(mVolume.getFsUuid(), sm.getPrimaryStorageVolume().getUuid())) {
                        final Intent intent = new Intent(StorageWizardMigrateConfirm.this, StorageWizardReady.class);
                        intent.putExtra(DiskInfo.EXTRA_DISK_ID,
                                getIntent().getStringExtra(DiskInfo.EXTRA_DISK_ID));
                        startActivity(intent);
                    }
                    finishAffinity();
                    return;
                } catch (IllegalStateException e) {
                    Log.w(TAG, "set primary or migrate data get an exception", e);
                    toast.show();
                    finishAffinity();

            return;
        }

                final Intent intent = new Intent(StorageWizardMigrateConfirm.this,
                        StorageWizardMigrateProgress.class);
                intent.putExtra(VolumeInfo.EXTRA_VOLUME_ID, mVolume.getId());
                intent.putExtra(PackageManager.EXTRA_MOVE_ID, moveId);
                startActivity(intent);
                finishAffinity();
                mTask = null;
            }
        };
        mTask.start();
        /* @} */
    }
}
