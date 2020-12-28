package com.android.settings;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.applications.SettingsApp;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.settingslib.core.lifecycle.Lifecycle;

import java.lang.ref.WeakReference;

public class SleepSettings extends Fragment implements View.OnClickListener {
    private static final String TAG = "SleepSettings";
    private RelativeLayout rlWakeUp;
    private RelativeLayout rlPark;
    private LinearLayout llParkTime;
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myHandle = new MyHandle(this);
        SettingsApp.getInstance().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SELECT_PARK_TIME),
                false, mContentObserver);
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
        rbLow = view.findViewById(R.id.rb_low);
        rbMiddle = view.findViewById(R.id.rb_middle);
        rbHigh = view.findViewById(R.id.rb_hight);
        tvRemotePark = view.findViewById(R.id.tv_park_monitor);
        tvWakeup = view.findViewById(R.id.tv_remote_wake_up);
        tvSelectTime = view.findViewById(R.id.tv_settings_park_time);
        switchWakeup = view.findViewById(R.id.switch_wake_up);
        switchPark = view.findViewById(R.id.switch_part);
        rlWakeUp.setOnClickListener(this);
        rlPark.setOnClickListener(this);
        llParkTime.setOnClickListener(this);
        rbLow.setOnClickListener(this);
        rbMiddle.setOnClickListener(this);
        rbHigh.setOnClickListener(this);
        type = getSelectTime();
        updateSummary();
        setCheckedLevel();
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
                Intent intent = new Intent(ACTION_SHOW_TIME_DIALOG);
                SettingsApp.getInstance().sendBroadcast(intent);
                break;
        }

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
        int level = Settings.Global.getInt(SettingsApp.getInstance().getContentResolver(),
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
            type = getSelectTime();
            myHandle.sendEmptyMessage(1);
        }
    };

    private int getSelectTime() {
        return Settings.Global.getInt(SettingsApp.getInstance().getContentResolver(),
                SELECT_PARK_TIME, 0);
    }

    private void setGSensorLevel(int level) {
        Settings.Global.putInt(SettingsApp.getInstance().getContentResolver(),
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
        tvRemotePark.setText(textId);
    }

    /**
     * update update radio button state
     *
     * @param type    wakeup or park switch
     * @param checked is checked
     */
    private void updateRBState() {
        int id;
        id = R.string.settings_opened;
        if (rbType == 0) {
            tvWakeup.setText(id);
            switchWakeup.setChecked(checked);
        } else {
            tvRemotePark.setText(id);
            switchPark.setChecked(checked);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mContentObserver != null) {
            SettingsApp.getInstance().getContentResolver().unregisterContentObserver(mContentObserver);
        }
        if (myHandle != null) {
            myHandle.removeCallbacksAndMessages(null);
            myHandle = null;
        }
    }

    /*    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.sleep_settings;
    }

    @Override
    protected List<AbstractPreferenceController> getPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getLifecycle());
    }

    @Override
    public int getMetricsCategory() {
        return 0;
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new SleepPreferenceController(context));
        controllers.add(new SleepPreferenceController(context));
        controllers.add(new SleepPreferenceController(context));
        return controllers;
    }*/
}
