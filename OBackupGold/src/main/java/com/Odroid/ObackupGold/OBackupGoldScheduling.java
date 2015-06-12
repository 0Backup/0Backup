package com.Odroid.ObackupGold;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;

import com.Odroid.ObackupCore.OBackupCoreBaseActivity;

public class OBackupGoldScheduling extends OBackupCoreBaseActivity {
  SharedPreferences _sharedPreferences = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.scheduled);

    _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    ((CompoundButton) findViewById(R.id.scheduled_enable))
        .setChecked(_sharedPreferences.getBoolean(
            OBackupGoldConstants.SCHEDULED, false));
    ((CompoundButton) findViewById(R.id.scheduled_enable))
        .setOnCheckedChangeListener(_enabledOnCheckedChangeListener);

    ((EditText) findViewById(R.id.scheduled_days)).setText(""
        + _sharedPreferences.getInt(OBackupGoldConstants.DAYS, 7));
    ((EditText) findViewById(R.id.scheduled_days))
        .addTextChangedListener(_daysTextWatcher);

    ((EditText) findViewById(R.id.scheduled_ssid)).setText(_sharedPreferences
        .getString(OBackupGoldConstants.SSID, ""));
    ((EditText) findViewById(R.id.scheduled_ssid))
        .addTextChangedListener(_ssidTextWatcher);

    ((CompoundButton) findViewById(R.id.scheduled_wifion))
        .setChecked(_sharedPreferences.getBoolean(OBackupGoldConstants.WIFION,
            true));
    ((CompoundButton) findViewById(R.id.scheduled_wifion))
        .setOnCheckedChangeListener(_wifionOnCheckedChangeListener);

    ((CompoundButton) findViewById(R.id.scheduled_onlywifi))
        .setChecked(_sharedPreferences.getBoolean(
            OBackupGoldConstants.ONLYWIFI, true));
    ((CompoundButton) findViewById(R.id.scheduled_onlywifi))
        .setOnCheckedChangeListener(_onlywifiOnCheckedChangeListener);

    ((TimePicker) findViewById(R.id.scheduled_time)).setIs24HourView(true);
    ((TimePicker) findViewById(R.id.scheduled_time))
        .setCurrentHour(_sharedPreferences.getInt(
            OBackupGoldConstants.TIMEHOUR, 0));
    ((TimePicker) findViewById(R.id.scheduled_time))
        .setCurrentMinute(_sharedPreferences.getInt(
            OBackupGoldConstants.TIMEMINUTE, 0));
    ((TimePicker) findViewById(R.id.scheduled_time))
        .setOnTimeChangedListener(_timeOnTimeChangedListener);

    setVisibilities();
  }

  CompoundButton.OnCheckedChangeListener _enabledOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
    public void onCheckedChanged(CompoundButton button, boolean value) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      e.putBoolean(OBackupGoldConstants.SCHEDULED, value);
      e.commit();
      setVisibilities();
      setAlarm(value);
    }
  };

  CompoundButton.OnCheckedChangeListener _wifionOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
    public void onCheckedChanged(CompoundButton button, boolean value) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      e.putBoolean(OBackupGoldConstants.WIFION, value);
      e.commit();
    }
  };

  CompoundButton.OnCheckedChangeListener _onlywifiOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
    public void onCheckedChanged(CompoundButton button, boolean value) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      e.putBoolean(OBackupGoldConstants.ONLYWIFI, value);
      e.commit();
      setVisibilities();
    }
  };

  TimePicker.OnTimeChangedListener _timeOnTimeChangedListener = new TimePicker.OnTimeChangedListener() {
    public void onTimeChanged(TimePicker view, int hour, int minute) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      e.putInt(OBackupGoldConstants.TIMEHOUR, hour);
      e.putInt(OBackupGoldConstants.TIMEMINUTE, minute);
      e.commit();
      setAlarm(true);
    }
  };

  private void setVisibilities() {
    if (((CompoundButton) findViewById(R.id.scheduled_enable)).isChecked()) {
      ((View) findViewById(R.id.scheduled_enabledChildren))
          .setVisibility(View.VISIBLE);
      EditText days = ((EditText) findViewById(R.id.scheduled_days));
      if (days.getText().toString().equals("")) {
        days.setText("7");
      }
      if (((CompoundButton) findViewById(R.id.scheduled_onlywifi)).isChecked()) {
        ((View) findViewById(R.id.scheduled_onlywifiChildren))
            .setVisibility(View.VISIBLE);
      } else {
        ((View) findViewById(R.id.scheduled_onlywifiChildren))
            .setVisibility(View.GONE);
      }
    } else {
      ((View) findViewById(R.id.scheduled_enabledChildren))
          .setVisibility(View.GONE);
    }
  }

  private void setAlarm(boolean enabled) {
    OBackupGoldAlarmReceiver.setAlarm(enabled, this);
  }

  public void scheduled_current(View v) {
    WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    if (wm == null)
      return;
    if (!wm.isWifiEnabled())
      return;
    WifiInfo wi = wm.getConnectionInfo();
    if (wi == null)
      return;
    String ssid = wi.getSSID();
    if (ssid == null)
      return;
    ((EditText) findViewById(R.id.scheduled_ssid)).setText(ssid);
  }

  public void scheduled_done(View v) {
    finish();
  }

  // Auto-saves the EditText to the preference whenever changed
  private TextWatcher _ssidTextWatcher = new TextWatcher() {
    public void afterTextChanged(Editable arg0) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      e.putString(OBackupGoldConstants.SSID,
          ((TextView) findViewById(R.id.scheduled_ssid)).getText().toString());
      e.commit();
    }

    public void beforeTextChanged(CharSequence s, int start, int count,
        int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  };

  // Auto-saves the EditText to the preference whenever changed
  private TextWatcher _daysTextWatcher = new TextWatcher() {
    public void afterTextChanged(Editable arg0) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      String daysS = ((TextView) findViewById(R.id.scheduled_days)).getText()
          .toString();
      int daysI = 7;
      try {
        daysI = Integer.parseInt(daysS);
      } catch (NumberFormatException ex) {
        daysI = 7;
      }
      e.putInt(OBackupGoldConstants.DAYS, daysI);
      e.commit();
    }

    public void beforeTextChanged(CharSequence s, int start, int count,
        int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  };

}
