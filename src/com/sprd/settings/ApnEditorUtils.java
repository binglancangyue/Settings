package com.sprd.settings;

import android.app.AddonManager;
import android.content.Context;
import static android.content.Context.TELEPHONY_SERVICE;
import android.database.Cursor;
import android.util.Log;
import android.telephony.CarrierConfigManagerEx;
import android.os.PersistableBundle;

import com.android.settings.R;

/**
 * Base class to customize various APN requirement
 */
public class ApnEditorUtils {
    public final static String APN_EDITABLE = "editable";
    public final static String KEY_IS_EDITABLE = "IS_EDITABLE";

    private static ApnEditorUtils mInstance;
    private static final String LOG_TAG = "ApnEditorUtils";
    private static final String ZERO = "0";
    private static Context mAddonContext;

    private static final int NUMERIC = 11;
    // SPRD: Bug 832161
    private static final int EDITABLE = 25;

    public ApnEditorUtils() {
    }

    public static ApnEditorUtils getInstance(Context context) {
        if (mInstance != null) {
            return mInstance;
        }
        mAddonContext = context;
        AddonManager addonManager = new AddonManager(context);
        mInstance = (ApnEditorUtils) addonManager.getAddon(
                R.string.feature_apn_editor_plugin, ApnEditorUtils.class);
        Log.d(LOG_TAG, "mInstance = " + mInstance);
        return mInstance;
    }

    /* Public interfaces to be override in sub classes */
    public boolean allowEmptyApn() {
        boolean isAllowEmpty = mAddonContext.getResources()
                .getBoolean(R.bool.config_allow_empty_apn);
        Log.d(LOG_TAG, "allowEmptyApn(): isAllowEmpty = " + isAllowEmpty);
        return isAllowEmpty;
    }

    /* SPRD: Bug 630976 get whether this APN of this operator is editable @{ */
    boolean isApnNotEditable(String numeric) {
        boolean isMatched = false;
        String operatorList = "";
        CarrierConfigManagerEx mConfigManagerEx =
                (CarrierConfigManagerEx)mAddonContext.getSystemService("carrier_config_ex");
        PersistableBundle persistableBundle = null;
        if (mConfigManagerEx != null) {
            persistableBundle = mConfigManagerEx.getConfigForDefaultPhone();
            if (persistableBundle != null) {
                operatorList = persistableBundle
                        .getString(CarrierConfigManagerEx.KEY_OPERATOR_APN_NOT_EDITABLE,"");
            } else {
                Log.d(LOG_TAG, "persistableBundle is null ");
            }
        } else {
            Log.d(LOG_TAG, "mConfigManagerEx is null");
        }
        Log.d(LOG_TAG, "operatorList = " + operatorList);
        if (!operatorList.isEmpty()) {
            String[] strings = operatorList.split(",");
            for(String s : strings) {
                if (!s.isEmpty() && s.equals(numeric)) {
                    isMatched = true;
                    break;
                }
            }
        }
        return isMatched;
    }
    /* @} */

    /**
     * Get the EDITABLE value from the cursor.
     *
     * @param c the cursor of the APN
     * @return true if this APN is editable
     */
    public boolean getEditable(Cursor c) {
        /* SPRD: Bug 630976 get whether this APN of this operator is editable @{ */
        String numeric = "";
        if (c != null) {
            numeric = c.getString(NUMERIC);
        }
        Log.d(LOG_TAG, "numeric = " + numeric);
        boolean apnNotEditable = isApnNotEditable(numeric);
        if (apnNotEditable && ZERO.equals(c.getString(EDITABLE))) {
            return false;
        } else {
            return true;
        }
    }
    /* @} */

    /**
     * Is APN editable by user
     */
    public boolean isApnEditable() {
        boolean isApnEditable = mAddonContext.getResources()
                .getBoolean(R.bool.config_is_apn_editable);
        Log.d(LOG_TAG, "isApnEditable(): isApnEditable = " + isApnEditable);
        return isApnEditable;
    }

    /* SPRD: Bug Bug 757343 add for operator which APN could not show XCAP  @{
     * ApnEditor CUCC apn will show default,supl instead of default,xcap,supl
    public boolean allowDisplayXCAP() {
        return true;
    }
    */

    public boolean isApnAllowDisplayXCAP(Cursor c) {
        String operatorList = "";
        String numeric = "";
        if (c != null) {
            numeric = c.getString(NUMERIC);
        }
        Log.d(LOG_TAG, "isApnAllowDisplayXCAP numeric = " + numeric);
        CarrierConfigManagerEx configManagerEx = CarrierConfigManagerEx.from(mAddonContext);
        PersistableBundle persistableBundle = null;
        if (configManagerEx != null) {
            persistableBundle = configManagerEx.getConfigForDefaultPhone();
            if (persistableBundle != null) {
                operatorList = persistableBundle
                        .getString(CarrierConfigManagerEx.KEY_OPERATOR_APN_NOT_SHOW_XCAP, "");
            } else {
                Log.d(LOG_TAG, "persistableBundle is null ");
            }
        } else {
            Log.d(LOG_TAG, "configManagerEx is null");
        }
        Log.d(LOG_TAG, "isApnAllowDisplayXCAP operatorList = " + operatorList);
        if (!operatorList.isEmpty()) {
            String[] strings = operatorList.split(",");
            for (String s : strings) {
                if (!s.isEmpty() && s.equals(numeric)) {
                    return false;
                }
            }
        }
        return true;
    }
    /* @}*/
}
