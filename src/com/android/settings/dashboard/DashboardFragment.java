/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.dashboard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.support.annotation.VisibleForTesting;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Settings.DevelopmentSettingsActivity;
import com.android.settings.Settings.SimSettingsActivity;
import com.android.settings.Settings.NotificationAppListActivity;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.Indexable;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.drawer.DashboardCategory;
import com.android.settingslib.drawer.SettingsDrawerActivity;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.drawer.TileUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.database.ContentObserver;
import android.provider.Settings;
import com.android.ims.ImsManager;
import android.os.Handler;

import com.sprd.settings.SprdUsbSettingsActivity;
import com.android.settings.Utils;

/**
 * Base fragment for dashboard style UI containing a list of static and dynamic setting items.
 */
public abstract class DashboardFragment extends SettingsPreferenceFragment
        implements SettingsDrawerActivity.CategoryListener, Indexable,
        SummaryLoader.SummaryConsumer {
    private static final String TAG = "DashboardFragment";

    private final Map<Class, AbstractPreferenceController> mPreferenceControllers =
            new ArrayMap<>();
    private final Set<String> mDashboardTilePrefKeys = new ArraySet<>();

    protected ProgressiveDisclosureMixin mProgressiveDisclosureMixin;
    protected DashboardFeatureProvider mDashboardFeatureProvider;
    private DashboardTilePlaceholderPreferenceController mPlaceholderPreferenceController;
    private boolean mListeningToCategoryChange;
    private SummaryLoader mSummaryLoader;
    // SPRD:Add for bug713799 Add sprd udb new UI for usb settings
    private boolean orignal_support = SystemProperties.getBoolean("ro.usb.orignal.design", false);

    private boolean isEnableWifiDisplay = false;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mDashboardFeatureProvider =
                FeatureFactory.getFactory(context).getDashboardFeatureProvider(context);
        mProgressiveDisclosureMixin = mDashboardFeatureProvider
                .getProgressiveDisclosureMixin(context, this, getArguments());
        getLifecycle().addObserver(mProgressiveDisclosureMixin);

        isEnableWifiDisplay = context.getResources().getBoolean(com.android.internal.R.bool.config_enableWifiDisplay);
        List<AbstractPreferenceController> controllers = getPreferenceControllers(context);
        if (controllers == null) {
            controllers = new ArrayList<>();
        }
        mPlaceholderPreferenceController =
                new DashboardTilePlaceholderPreferenceController(context);
        controllers.add(mPlaceholderPreferenceController);
        for (AbstractPreferenceController controller : controllers) {
            addPreferenceController(controller);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // Set ComparisonCallback so we get better animation when list changes.
        getPreferenceManager().setPreferenceComparisonCallback(
                new PreferenceManager.SimplePreferenceComparisonCallback());
        // Upon rotation configuration change we need to update preference states before any
        // editing dialog is recreated (that would happen before onResume is called).
        updatePreferenceStates();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        return view;
    }

    @Override
    public void onCategoriesChanged() {
        final DashboardCategory category =
                mDashboardFeatureProvider.getTilesForCategory(getCategoryKey());
        if (category == null) {
            return;
        }
        refreshDashboardTiles(getLogTag());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        refreshAllPreferences(getLogTag());
    }

    @Override
    public void onStart() {
        super.onStart();
        final DashboardCategory category =
                mDashboardFeatureProvider.getTilesForCategory(getCategoryKey());
        if (category == null) {
            return;
        }
        if (mSummaryLoader != null) {
            // SummaryLoader can be null when there is no dynamic tiles.
            mSummaryLoader.setListening(true);
        }
        final Activity activity = getActivity();
        if (activity instanceof SettingsDrawerActivity) {
            mListeningToCategoryChange = true;
            ((SettingsDrawerActivity) activity).addCategoryListener(this);
        }
        /* SPRD:Add for bug713799 Add sprd udb new UI for usb settings @{*/
        Preference preference = getPreferenceScreen().findPreference("usb_mode");
        if (preference != null && "usb_mode".equalsIgnoreCase(preference.getKey())) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setClassName("com.android.settings",orignal_support ? "com.android.settings.deviceinfo.UsbModeChooserActivity" : "com.sprd.settings.SprdUsbSettingsActivity");
            preference.setIntent(intent);
        }
        /* Bug713799 end @} */
    }

    @Override
    public void notifySummaryChanged(Tile tile) {
        final String key = mDashboardFeatureProvider.getDashboardKeyForTile(tile);
        final Preference pref = mProgressiveDisclosureMixin.findPreference(
                getPreferenceScreen(), key);
        if (pref == null) {
            Log.d(getLogTag(),
                    String.format("Can't find pref by key %s, skipping update summary %s/%s",
                            key, tile.title, tile.summary));
            return;
        }
        pref.setSummary(tile.summary);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceStates();
        /* SPRD: Bug 867335 @{ */
        getActivity().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WFC_IMS_ENABLED), true,
                mWfcEnableObserver);
        /* @}*/
    }
    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG,"[onPause]");
        getActivity().getContentResolver().unregisterContentObserver(mWfcEnableObserver);
    }
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        Collection<AbstractPreferenceController> controllers = mPreferenceControllers.values();
        // If preference contains intent, log it before handling.
        mMetricsFeatureProvider.logDashboardStartIntent(
                getContext(), preference.getIntent(), getMetricsCategory());
        // Give all controllers a chance to handle click.
        for (AbstractPreferenceController controller : controllers) {
            if (controller.handlePreferenceTreeClick(preference)) {
                return true;
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSummaryLoader != null) {
            // SummaryLoader can be null when there is no dynamic tiles.
            mSummaryLoader.setListening(false);
        }
        if (mListeningToCategoryChange) {
            final Activity activity = getActivity();
            if (activity instanceof SettingsDrawerActivity) {
                ((SettingsDrawerActivity) activity).remCategoryListener(this);
            }
            mListeningToCategoryChange = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //SPRD add for Bug 809502 create repeatly HandlerThread result in fd leaked.
        if (mSummaryLoader != null) {
            mSummaryLoader.release();
        }
    }

    protected <T extends AbstractPreferenceController> T getPreferenceController(Class<T> clazz) {
        AbstractPreferenceController controller = mPreferenceControllers.get(clazz);
        return (T) controller;
    }

    protected void addPreferenceController(AbstractPreferenceController controller) {
        mPreferenceControllers.put(controller.getClass(), controller);
    }

    /**
     * Returns the CategoryKey for loading {@link DashboardCategory} for this fragment.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public String getCategoryKey() {
        return DashboardFragmentRegistry.PARENT_TO_CATEGORY_KEY_MAP.get(getClass().getName());
    }

    /**
     * Get the tag string for logging.
     */
    protected abstract String getLogTag();

    /**
     * Get the res id for static preference xml for this fragment.
     */
    protected abstract int getPreferenceScreenResId();

    /**
     * Get a list of {@link AbstractPreferenceController} for this fragment.
     */
    protected abstract List<AbstractPreferenceController> getPreferenceControllers(Context context);

    /**
     * Returns true if this tile should be displayed
     */
    protected boolean displayTile(Tile tile) {
        return true;
    }

    @VisibleForTesting
    boolean tintTileIcon(Tile tile) {
        if (tile.icon == null) {
            return false;
        }
        // First check if the tile has set the icon tintable metadata.
        final Bundle metadata = tile.metaData;
        if (metadata != null
                && metadata.containsKey(TileUtils.META_DATA_PREFERENCE_ICON_TINTABLE)) {
            return metadata.getBoolean(TileUtils.META_DATA_PREFERENCE_ICON_TINTABLE);
        }
        final String pkgName = getContext().getPackageName();
        // If this drawable is coming from outside Settings, tint it to match the color.
        return pkgName != null && tile.intent != null
                && !pkgName.equals(tile.intent.getComponent().getPackageName());
    }

    /**
     * Returns true if this tile is wifiDisplay
     */
    protected boolean isContainsWifiDisplay(String key) {
        if (key != null) {
            if (key.contains("WifiDisplaySettingsActivity")) {
                Log.d(TAG, "isContainsWifiDisplay true");
                return true;
            }
        }
        return false;
    }

    /**
     * Displays resource based tiles.
     */
    private void displayResourceTiles() {
        final int resId = getPreferenceScreenResId();
        if (resId <= 0) {
            return;
        }
        addPreferencesFromResource(resId);
        final PreferenceScreen screen = getPreferenceScreen();
        Collection<AbstractPreferenceController> controllers = mPreferenceControllers.values();
        for (AbstractPreferenceController controller : controllers) {
            controller.displayPreference(screen);
        }
    }

    /**
     * Update state of each preference managed by PreferenceController.
     */
    protected void updatePreferenceStates() {
        Collection<AbstractPreferenceController> controllers = mPreferenceControllers.values();
        final PreferenceScreen screen = getPreferenceScreen();
        for (AbstractPreferenceController controller : controllers) {
            if (!controller.isAvailable()) {
                continue;
            }
            final String key = controller.getPreferenceKey();

            final Preference preference = mProgressiveDisclosureMixin.findPreference(screen, key);
            if (preference == null) {
                Log.d(TAG, String.format("Cannot find preference with key %s in Controller %s",
                        key, controller.getClass().getSimpleName()));
                continue;
            }
            controller.updateState(preference);
        }
    }
    /* SPRD: Bug 867335 @{ */
    protected void updatewWiFicallPreference() {
        Collection<AbstractPreferenceController> controllers = mPreferenceControllers.values();
        final PreferenceScreen screen = getPreferenceScreen();
        for (AbstractPreferenceController controller : controllers) {
            if (!controller.isAvailable()) {
                continue;
            }
            String key = controller.getPreferenceKey();

            final Preference preference = mProgressiveDisclosureMixin.findPreference(screen, key);
            if (preference == null) {
                Log.d(TAG, String.format("Cannot find preference with key %s in Controller %s",
                        key, controller.getClass().getSimpleName()));
                continue;
            }
            Log.d(TAG,"updatewWiFicallPreferencekey = " + key + "preference =" + preference);
            if (key != null){
                if (key.equals("wifi_calling_settings")){
                    controller.updateState(preference);
                }
            }
        }
    }
    /* @}*/

     /**
     * Refresh all preference items, including both static prefs from xml, and dynamic items from
     * DashboardCategory.
     */
    private void refreshAllPreferences(final String TAG) {
        // First remove old preferences.
        if (getPreferenceScreen() != null) {
            // Intentionally do not cache PreferenceScreen because it will be recreated later.
            getPreferenceScreen().removeAll();
        }

        // Add resource based tiles.
        displayResourceTiles();
        mProgressiveDisclosureMixin.collapse(getPreferenceScreen());

        refreshDashboardTiles(TAG);
    }

    /**
     * Refresh preference items backed by DashboardCategory.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void refreshDashboardTiles(final String TAG) {
        final PreferenceScreen screen = getPreferenceScreen();

        final DashboardCategory category =
                mDashboardFeatureProvider.getTilesForCategory(getCategoryKey());
        if (category == null) {
            Log.d(TAG, "NO dashboard tiles for " + TAG);
            return;
        }
        List<Tile> tiles = category.tiles;
        if (tiles == null) {
            Log.d(TAG, "tile list is empty, skipping category " + category.title);
            return;
        }
        // Create a list to track which tiles are to be removed.
        final List<String> remove = new ArrayList<>(mDashboardTilePrefKeys);

        // There are dashboard tiles, so we need to install SummaryLoader.
        if (mSummaryLoader != null) {
            mSummaryLoader.release();
        }
        final Context context = getContext();
        mSummaryLoader = new SummaryLoader(getActivity(), getCategoryKey());
        mSummaryLoader.setSummaryConsumer(this);
        final TypedArray a = context.obtainStyledAttributes(new int[]{
                android.R.attr.colorControlNormal});
        final int tintColor = a.getColor(0, context.getColor(android.R.color.white));
        a.recycle();
        final boolean isOwner = (UserHandle.myUserId() == UserHandle.USER_OWNER);
        // Install dashboard tiles.
        for (Tile tile : tiles) {
            final String key = mDashboardFeatureProvider.getDashboardKeyForTile(tile);
            if (!isOwner) {
                if (key.endsWith(DevelopmentSettingsActivity.class.getSimpleName())
                    || key.endsWith(NotificationAppListActivity.class.getSimpleName())) {
                    continue;
                }
            }
            if (key.endsWith(SimSettingsActivity.class.getSimpleName()) && !isOwner) {
                continue;
            }
            if (TextUtils.isEmpty(key)) {
                Log.d(TAG, "tile does not contain a key, skipping " + tile);
                continue;
            }
            if (!displayTile(tile)) {
                continue;
            }
            Log.d(TAG, "isEnableWifiDisplay = " + isEnableWifiDisplay + "; isContainsWifiDisplay(key) = " + isContainsWifiDisplay(key));
            if (!isEnableWifiDisplay || Utils.WCN_DISABLED) {
                if (isContainsWifiDisplay(key)) {
                    continue;
                }
            }
            if (tintTileIcon(tile)) {
                tile.icon.setTint(tintColor);
            }
            if (mDashboardTilePrefKeys.contains(key)) {
                // Have the key already, will rebind.
                final Preference preference = mProgressiveDisclosureMixin.findPreference(
                        screen, key);
                mDashboardFeatureProvider.bindPreferenceToTile(getActivity(), getMetricsCategory(),
                        preference, tile, key, mPlaceholderPreferenceController.getOrder());
            } else {
                // Don't have this key, add it.
                final Preference pref = new Preference(getPrefContext());
                mDashboardFeatureProvider.bindPreferenceToTile(getActivity(), getMetricsCategory(),
                        pref, tile, key, mPlaceholderPreferenceController.getOrder());
                mProgressiveDisclosureMixin.addPreference(screen, pref);
                mDashboardTilePrefKeys.add(key);
            }
            remove.remove(key);
        }
        // Finally remove tiles that are gone.
        for (String key : remove) {
            mDashboardTilePrefKeys.remove(key);
            mProgressiveDisclosureMixin.removePreference(screen, key);
        }
        mSummaryLoader.setListening(true);
    }
    /* SPRD: Bug 867335 @{ */
    private ContentObserver mWfcEnableObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "[mWfcEnableObserver] getActivity: " + getActivity());
            if (getActivity() == null) {
                return;
            }
            boolean enabled = ImsManager.isWfcEnabledByUser(getActivity())
                    && ImsManager.isNonTtyOrTtyOnVolteEnabled(getActivity());
            Log.d(TAG, "[mWfcEnableObserver][wfcEnabled]: " + enabled);
            updatewWiFicallPreference();
        }
    };
    /* @}*/
}
