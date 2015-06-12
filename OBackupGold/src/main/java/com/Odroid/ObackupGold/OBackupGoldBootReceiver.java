package com.Odroid.ObackupGold;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class OBackupGoldBootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    // Only Boot raises this Broadcast, so no need to check
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    boolean enabled = sharedPreferences.getBoolean(OBackupGoldConstants.SCHEDULED, false);
    OBackupGoldAlarmReceiver.setAlarm(enabled, context);
  }
}
