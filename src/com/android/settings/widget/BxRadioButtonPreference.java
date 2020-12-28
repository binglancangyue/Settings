package com.android.settings.widget;

import android.content.Context;
import android.support.v4.content.res.TypedArrayUtils;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.TwoTargetPreference;
import com.android.settingslib.RestrictedPreference;
import com.android.settings.R;

public class BxRadioButtonPreference extends RestrictedPreference implements View.OnClickListener {
    private RadioButton rbLow;
    private RadioButton rbMiddle;
    private RadioButton rbHigh;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        clearCheck();
        if (id == R.id.rb_low) {
            rbLow.setChecked(true);
        }
        if (id == R.id.rb_middle) {
            rbMiddle.setChecked(true);
        }
        if (id == R.id.rb_hight) {
            rbHigh.setChecked(true);
        }
    }

    public interface OnClickListener {
        void onRadioButtonClicked(RadioButtonPreference emiter);
    }

    public BxRadioButtonPreference(Context context, AttributeSet attrs,
                                   int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_widget_radiobuttons);
    }

    public BxRadioButtonPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_widget_radiobuttons);
    }

    public BxRadioButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_widget_radiobuttons);
    }

    public BxRadioButtonPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_widget_radiobuttons);
    }

    private void clearCheck() {
        rbLow.setChecked(false);
        rbMiddle.setChecked(false);
        rbHigh.setChecked(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
//        super.onBindViewHolder(holder);
        rbLow = (RadioButton) holder.findViewById(R.id.rb_low);
        rbMiddle = (RadioButton) holder.findViewById(R.id.rb_middle);
        rbHigh = (RadioButton) holder.findViewById(R.id.rb_hight);
        rbLow.setOnClickListener(this);
        rbMiddle.setOnClickListener(this);
        rbHigh.setOnClickListener(this);
    }

}
