package com.android.settings;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.settingslib.core.lifecycle.Lifecycle;

import java.lang.ref.WeakReference;

public class SleepSettings extends Fragment implements View.OnClickListener {
    private static final String TAG = "SleepSettings";
    private RelativeLayout rlWakeUp;
    private RelativeLayout rlPark;
    private LinearLayout llParkTime;
    private LinearLayout llG_sensor;
    private RadioButton rbLow;
    private RadioButton rbMiddle;
    private RadioButton rbHigh;
    private MyHandle myHandle;
    private TextView tvWakeup;
    private TextView tvRemotePark;
    private TextView tvSelectTime;
    private Switch switchWakeup;
    private Switch switchPark;
    private int type;
    private int rbType;
    private boolean checked = false;
    private Context mContext;
    private AlertDialog selectTimeDialog;
    private RadioButton rb8;
    private RadioButton rb16;
    private RadioButton rb24;
    private RadioButton rb48;
    private RadioButton rbAlways;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myHandle = new MyHandle(this);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SELECT_PARK_TIME),
                false, mContentObserver);
        type = getSelectedSleepTime();
    }

    private static class MyHandle extends Handler {
        private final SleepSettings mFragment;

        public MyHandle(SleepSettings fragment) {
            WeakReference<SleepSettings> weakReference = new WeakReference<>(fragment);
            mFragment = weakReference.get();
        }

        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);
            int id = msg.what;
            if (id == 1) {
                mFragment.updateSummary();
            }
            if (id == 2) {
                mFragment.updateRBState();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings_sleep, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView(view);
    }

    private void initView(View view) {
        rlWakeUp = view.findViewById(R.id.rl_wake_up);
        rlPark = view.findViewById(R.id.rl_part);
        llParkTime = view.findViewById(R.id.ll_park_time);
        llG_sensor = view.findViewById(R.id.ll_g_sensor);
        rbLow = view.findViewById(R.id.rb_low);
        rbMiddle = view.findViewById(R.id.rb_middle);
        rbHigh = view.findViewById(R.id.rb_hight);
        tvRemotePark = view.findViewById(R.id.tv_park_monitor);
        tvWakeup = view.findViewById(R.id.tv_remote_wake_up);
        tvSelectTime = view.findViewById(R.id.tv_settings_park_time);
        switchWakeup = view.findViewById(R.id.switch_wake_up);
        switchPark = view.findViewById(R.id.switch_part);
        switchWakeup.setOnClickListener(this);
        switchPark.setOnClickListener(this);
        rlWakeUp.setOnClickListener(this);
        rlPark.setOnClickListener(this);
        llParkTime.setOnClickListener(this);
        rbLow.setOnClickListener(this);
        rbMiddle.setOnClickListener(this);
        rbHigh.setOnClickListener(this);
//        type = getSelectedSleepTime();
        updateSummary();
        setCheckedLevel();
        initRbt(0);
        initRbt(1);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.rb_low:
                clearCheck();
                rbLow.setChecked(true);
                setGSensorLevel(0);
                break;
            case R.id.rb_middle:
                clearCheck();
                rbMiddle.setChecked(true);
                setGSensorLevel(1);
                break;
            case R.id.rb_hight:
                clearCheck();
                rbHigh.setChecked(true);
                setGSensorLevel(2);
                break;
            case R.id.rl_wake_up:
            case R.id.switch_wake_up:
                if (rlWakeUp.isSelected()) {
                    rlWakeUp.setSelected(false);
                    checked = false;
                } else {
                    checked = true;
                    rlWakeUp.setSelected(true);
                }
                rbType = 0;
                myHandle.sendEmptyMessage(2);
                break;
            case R.id.rl_part:
            case R.id.switch_part:
                if (rlPark.isSelected()) {
                    rlPark.setSelected(false);
                    checked = false;
                } else {
                    rlPark.setSelected(true);
                    checked = true;
                }
                rbType = 1;
                myHandle.sendEmptyMessage(2);
                break;
            case R.id.ll_park_time:
                showSelectTimeDialog();
                break;
            case R.id.rb_time_8:
                clearAll();
                rb8.setChecked(true);
                setSelectedParkTime(0);
                break;
            case R.id.rb_time_16:
                clearAll();
                rb16.setChecked(true);
                setSelectedParkTime(1);
                break;
            case R.id.rb_time_24:
                clearAll();
                rb24.setChecked(true);
                setSelectedParkTime(2);
                break;
            case R.id.rb_time_48:
                clearAll();
                rb48.setChecked(true);
                setSelectedParkTime(3);
                break;
            case R.id.rb_time_always:
                clearAll();
                rbAlways.setChecked(true);
                setSelectedParkTime(4);
                break;

        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    private void setSelectedParkTime(int time) {
        Settings.Global.putInt(mContext.getContentResolver(), SELECT_PARK_TIME, time);
    }

    private static final String SELECT_PARK_TIME = "select_park_time";
    private static final String ACTION_SHOW_TIME_DIALOG = "com.bixin.action.show_time_dialog";
    private static final String G_SENSOR_LEVEL = "g_sensor_level";

    /**
     * clear all radio button checked state
     */
    private void clearCheck() {
        rbLow.setChecked(false);
        rbMiddle.setChecked(false);
        rbHigh.setChecked(false);
    }

    private void setCheckedLevel() {
        int level = Settings.Global.getInt(mContext.getContentResolver(),
                G_SENSOR_LEVEL, 0);
        if (level == 0) {
            rbLow.setChecked(true);
        } else if (level == 1) {
            rbMiddle.setChecked(true);
        } else {
            rbHigh.setChecked(true);
        }
    }

    private final ContentObserver mContentObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            type = getSelectedSleepTime();
            myHandle.sendEmptyMessage(1);
        }
    };

    private int getSelectedSleepTime() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                SELECT_PARK_TIME, 0);
    }

    private void setGSensorLevel(int level) {
        Settings.Global.putInt(mContext.getContentResolver(),
                G_SENSOR_LEVEL, level);
    }

    private void updateSummary() {
        int textId = 0;
        switch (type) {
            case 0:
                textId = R.string.settings_park_time_8;
                break;
            case 1:
                textId = R.string.settings_park_time_16;
                break;
            case 2:
                textId = R.string.settings_park_time_24;
                break;
            case 3:
                textId = R.string.settings_park_time_48;
                break;
            case 4:
                textId = R.string.settings_park_time_always;
                break;
        }
        tvSelectTime.setText(textId);
    }

    private void updateDialogSelected() {
        int textId = 0;
        clearAll();
        switch (type) {
            case 0:
                textId = R.string.settings_park_time_8;
                rb8.setChecked(true);
                break;
            case 1:
                textId = R.string.settings_park_time_16;
                rb16.setChecked(true);
                break;
            case 2:
                textId = R.string.settings_park_time_24;
                rb24.setChecked(true);
                break;
            case 3:
                textId = R.string.settings_park_time_48;
                rb48.setChecked(true);
                break;
            case 4:
                textId = R.string.settings_park_time_always;
                rbAlways.setChecked(true);
                break;
        }
        tvSelectTime.setText(textId);
    }

    /**
     * update update radio button state
     *
     * @param type    wakeup or park switch
     * @param checked is checked
     */
    private void updateRBState() {
        int id;
        int value = 1;
        if (checked) {
            id = R.string.settings_opened;
            value = 1;
        } else {
            id = R.string.settings_closed;
            value = 0;
        }
        if (rbType == 0) {
            tvWakeup.setText(id);
            switchWakeup.setChecked(checked);
            Settings.Global.putInt(mContext.getContentResolver(), "sleep_wake_up", value);
        } else {
            tvRemotePark.setText(id);
            switchPark.setChecked(checked);
            isShowParkItem(checked);
            Settings.Global.putInt(mContext.getContentResolver(), "sleep_remote_park", value);
        }
    }

    private void initRbt(int type) {
        if (type == 0) {
            if (Settings.Global.getInt(mContext.getContentResolver(), "sleep_wake_up", 0) == 0) {
                int id = getStringId(false);
                tvWakeup.setText(id);
                rlWakeUp.setSelected(false);
                switchWakeup.setChecked(false);
            } else {
                int id = getStringId(true);
                tvWakeup.setText(id);
                rlWakeUp.setSelected(true);
                switchWakeup.setChecked(true);
            }
        } else {
            if (Settings.Global.getInt(mContext.getContentResolver(), "sleep_remote_park", 0) == 0) {
                int id = getStringId(false);
                tvRemotePark.setText(id);
                switchPark.setChecked(false);
                rlPark.setSelected(false);
            } else {
                int id = getStringId(true);
                tvRemotePark.setText(id);
                switchPark.setChecked(true);
                rlPark.setSelected(true);
            }
        }
    }

    private int getStringId(boolean checked) {
        if (checked) {
            return R.string.settings_opened;
        } else {
            return R.string.settings_closed;
        }
    }

    private void showSelectTimeDialog() {
        if (selectTimeDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            View view = View.inflate(mContext, R.layout.dialog_select_time, null);
            rb8 = view.findViewById(R.id.rb_time_8);
            rb16 = view.findViewById(R.id.rb_time_16);
            rb24 = view.findViewById(R.id.rb_time_24);
            rb48 = view.findViewById(R.id.rb_time_48);
            rbAlways = view.findViewById(R.id.rb_time_always);
            updateDialogSelected();
            rb8.setOnClickListener(this);
            rb16.setOnClickListener(this);
            rb24.setOnClickListener(this);
            rb48.setOnClickListener(this);
            rbAlways.setOnClickListener(this);
            builder.setView(view);
            builder.setTitle(R.string.settings_park_time);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissTimeDialog();
                }
            });
            selectTimeDialog = builder.create();
        }
        if (!selectTimeDialog.isShowing()) {
            updateSummary();
            updateDialogSelected();
            selectTimeDialog.show();
        }
    }

    private void dismissTimeDialog() {
        if (selectTimeDialog != null) {
            selectTimeDialog.dismiss();
        }
    }

    private void clearAll() {
        rb8.setChecked(false);
        rb16.setChecked(false);
        rb24.setChecked(false);
        rb48.setChecked(false);
        rbAlways.setChecked(false);
    }

    private void isShowParkItem(boolean isShow) {
        if (isShow) {
            llG_sensor.setVisibility(View.VISIBLE);
            llParkTime.setVisibility(View.VISIBLE);
        } else {
            llG_sensor.setVisibility(View.GONE);
            llParkTime.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        }
        if (myHandle != null) {
            myHandle.removeCallbacksAndMessages(null);
            myHandle = null;
        }
    }
}
