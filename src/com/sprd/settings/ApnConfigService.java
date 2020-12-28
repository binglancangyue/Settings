package com.sprd.settings;

import java.util.List;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import android.text.TextUtils;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;

import com.android.settings.R;
import com.android.internal.telephony.TelephonyIntents;

public class ApnConfigService extends Service {

    private static final String TAG = "ApnConfigService";
    private static final boolean DBG = true;
    private static final String POP_FILE = "data_pop_show";
    private static final String FIRST_REBOOT = "first_reboot";

    private boolean mIsPopUpShowing = false;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private Context mContext;
    private static final String PACK_NAME = "com.android.phone";
    private static final String CLASS_NAME = "com.android.phone.MobileNetworkSettings";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // the service started and receive the broadcast of set primary card complete
            // we should show apn config popup
            log("OnReceive: " + intent.getAction());
            if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED
                    .equals(intent.getAction())) {
                if (!mIsPopUpShowing) {
                    mIsPopUpShowing = true;
                    showApnConfigPopUp();
                }
            }
        }
    };

    public ApnConfigService() {
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        log("onCreate");
        mContext = this;
        mTelephonyManager = TelephonyManager.from(this);
        mSubscriptionManager = SubscriptionManager.from(this);
        final IntentFilter intentFilter = new IntentFilter(
                TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        registerReceiver(mReceiver, intentFilter);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        log("onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void showDataSwitchPopUp() {
        log("show DATA pop up");
        AlertDialog.Builder dataServiceDialog = new AlertDialog.Builder(this);
        dataServiceDialog
                .setCancelable(false)
                .setTitle(R.string.data_popup_title)
                .setMessage(R.string.data_popup_summary)
                .setPositiveButton(R.string.data_popup_enable,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mTelephonyManager.setDataEnabled(
                                        SubscriptionManager.getDefaultDataSubscriptionId(), true);
                            }
                        })
                .setNegativeButton(R.string.data_popup_disable,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mTelephonyManager.setDataEnabled(
                                        SubscriptionManager.getDefaultDataSubscriptionId(), false);
                            }
                        });
        AlertDialog dataDialog = dataServiceDialog.create();
        dataDialog.setCanceledOnTouchOutside(false);
        dataDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dataDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                SharedPreferences sp = mContext.getSharedPreferences(
                        POP_FILE, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean(FIRST_REBOOT, false);
                editor.apply();
                mIsPopUpShowing = false;
                stopSelf();
            }
        });
        dataDialog.show();
    }

    private void showApnConfigPopUp() {
        Log.d(TAG, "show APN pop up");
        AlertDialog.Builder apnConfigDialog = new AlertDialog.Builder(this);
        apnConfigDialog.setCancelable(false)
                .setTitle(R.string.apn_popup_title)
                .setMessage(R.string.apn_popup_summary)
                .setPositiveButton(R.string.apn_popup_change,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setClassName(PACK_NAME, CLASS_NAME);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.apn_popup_close,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        AlertDialog apnDialog = apnConfigDialog.create();
        apnDialog.setCanceledOnTouchOutside(false);
        apnDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        apnDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                SharedPreferences sp = mContext.getSharedPreferences(POP_FILE,
                        Context.MODE_PRIVATE);
                // whether the first boot or restore factory settings
                boolean needShowData = sp.getBoolean(FIRST_REBOOT, true);
                log("Need show data: " + needShowData);
                if (needShowData) {
                    showDataSwitchPopUp();
                } else {
                    mIsPopUpShowing = false;
                    stopSelf();
                }
            }
        });
        apnDialog.show();
    }
    /* @} */

    private void log(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
