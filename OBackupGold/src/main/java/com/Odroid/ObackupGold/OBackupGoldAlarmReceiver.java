package com.Odroid.ObackupGold;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.Odroid.ObackupCore.OBackupCoreConstants;
import com.Odroid.ObackupCore.OBackupCoreService;

public class OBackupGoldAlarmReceiver extends BroadcastReceiver {
  private static final int MAGIC = 42;
  private static long WAITTRIESINIT = 1000; // 1000ms*2^10 = 1024 seconds with
  private static int MAXTRIESWIFION = 10; // 2-exponential backoff
  // private static int MAXTRIESWIFION = 3; // for testing
  private static long WAITAFTERON = 30; // 30 Seconds, to ensure connection
  // private static long WAITAFTERON = 10; // 10 Seconds, testing

  private static final long SCHEDINTERVAL = 1000 * 60 * 60 * 24; // 1 day
  // private static final long SCHEDINTERVAL = 1000*60*2; // 2 minute, testing
  // private static final long SCHEDINTERVAL = 1000 * 60; // 1 minute, testing

  private static final int NOTIFICATION_ID = 1;

  private Notification _notification = null;
  private Context _context = null;

  private Handler wifiWaitHandler = null;

  @Override
  public void onReceive(Context context, Intent intent) {
    // Only Alarms set on OBackupGoldService raises this Broadcast, so no need
    // to check
    _context = context;
    wifiWaitHandler = new Handler();
    doBackupScheduled();
  }

  private void doBackupScheduled() {
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: doBackupScheduled() { ");
    SharedPreferences sharedPreferences = PreferenceManager
        .getDefaultSharedPreferences(_context);
    boolean enabled = sharedPreferences.getBoolean(
        OBackupGoldConstants.SCHEDULED, false);
    SharedPreferences resSharedPreferences = _context.getSharedPreferences(
        OBackupCoreConstants.RES_SHPRF, Context.MODE_PRIVATE);
    if (!enabled)
      return;
    long last = resSharedPreferences.getLong(
        OBackupGoldConstants.RES_LAST_BACKUP, 0);
    long now = (new Date()).getTime();
    int days = sharedPreferences.getInt(OBackupGoldConstants.DAYS, 7);
    long daysms = days * 24 * 60 * 60 * 1000;
    if ((last + daysms - 12 * 60 * 60 * 1000) > now)
      return; // 12 hour margin
    // long daysms = days * 60 * 1000;
    // Log.d(OBackupGoldConstants.LOGCAT,
    // "0 Backup: last "+last+", now "+now+", daysms "+daysms+" ==> "+(last+daysms-now-20*1000));
    // if ((last + daysms - 20 * 1000) > now) return; // 20 sec margin
    ConnectivityManager cm = (ConnectivityManager) _context
        .getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cm == null) {
      _notifyFatalError();
      android.util.Log.e(OBackupCoreConstants.LOGCAT,
          "0 Backup: doBackupScheduled() FATAL ERROR");
      return;
    }
    _niceAutowifion();
  }

  private void _niceAutowifion() {
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: autowifion() {");
    SharedPreferences sp = PreferenceManager
        .getDefaultSharedPreferences(_context);
    boolean wifion = sp.getBoolean(OBackupGoldConstants.WIFION, true);
    if (wifion) {
      _notify("Turnning WiFi on");
      WifiManager wm = (WifiManager) _context
          .getSystemService(Context.WIFI_SERVICE);
      if (wm == null) {
        android.util.Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: wm null");
      } else {
        if (!wm.isWifiEnabled()) {
          // android.util.Log.d(OBackupCoreConstants.LOGCAT,
          // "0 Backup: wm.SetWifiEnabled(true) {");
          wm.setWifiEnabled(true);
          // android.util.Log.d(OBackupCoreConstants.LOGCAT,
          // "0 Backup: } wm.SetWifiEnabled(true)");
          _autowifion_waiter(0, WAITTRIESINIT);
          return;
        }
      }
    }
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: } autowifion() = false");
    _niceAutowifionResult(false);
  }

  private void _autowifion_waiter(final int ntry, final long nextWait) {
    WifiManager wm = (WifiManager) _context
        .getSystemService(Context.WIFI_SERVICE);
    WifiInfo wi = wm.getConnectionInfo();
    if ((wi != null) & (wi.getSSID() != null)) {
      // Log.d(OBackupGoldConstants.LOGCAT, "0 Backup: autoon ssid: " +
      // wi.getSSID());
      // Wait a few seconds to let WiFi get IP
      // _notify("Getting WiFi IP");
      // wifiWaitHandler.postDelayed(new Runnable() {
      // public void run() {
      _niceAutowifionResult(true);
      // }
      // }, WAITAFTERON * 1000);
      // android.util.Log.d(OBackupCoreConstants.LOGCAT,
      // "0 Backup: } autowifion() = true");
      return;
    }
    if (ntry < MAXTRIESWIFION) {
      // Retry, with an exponential delay
      wifiWaitHandler.postDelayed(new Runnable() {
        public void run() {
          _autowifion_waiter(ntry + 1, 2 * nextWait);
        }
      }, nextWait);
      return;
    }
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: } autowifion() = true");
    _niceAutowifionResult(true);
  }

  private void _niceAutowifionResult(boolean turnedWifiOn) {
    SharedPreferences sharedPreferences = PreferenceManager
        .getDefaultSharedPreferences(_context);
    boolean onlywifi = sharedPreferences.getBoolean(
        OBackupGoldConstants.ONLYWIFI, true);
    String ssid = sharedPreferences.getString(OBackupGoldConstants.SSID, "");
    if (onlywifi) {
      if (false == _onlywifi(ssid)) {
        _autowifioff(turnedWifiOn);
        return;
      }
    }
    Intent i = new Intent(_context, OBackupCoreService.class);
    i.putExtra(OBackupGoldConstants.BACKCLASS, (Serializable) OBackupGold.class);
    i.putExtra(OBackupGoldConstants.CHECKPLUGGEDONLY, true);
    i.putExtra(OBackupGoldConstants.TURNEDWIFION, turnedWifiOn);
    _context.startService(i);
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: } doBackupScheduled()");
  }

  private void _autowifioff(boolean turnedWifiOn) {
    if (turnedWifiOn) {
      _notify("Turnning WiFi off");
      WifiManager wm = (WifiManager) _context
          .getSystemService(Context.WIFI_SERVICE);
      if (wm == null) {
        android.util.Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: wm null");
      } else {
        // android.util.Log.d(OBackupCoreConstants.LOGCAT,
        // "0 Backup: wm.SetWifiEnabled(false) {");
        wm.setWifiEnabled(false);
        // android.util.Log.d(OBackupCoreConstants.LOGCAT,
        // "0 Backup: } wm.SetWifiEnabled(false)");
      }
    }
  }

  // Returns false if must halt backup
  private boolean _onlywifi(String ssid) {
    WifiManager wm = (WifiManager) _context
        .getSystemService(Context.WIFI_SERVICE);
    if (wm == null) {
      Log.w(OBackupGoldConstants.LOGCAT, "0 Backup: WifiManager null");
      _notifyNoWifi();
      return false;
    }
    WifiInfo wi = wm.getConnectionInfo();
    if (wi == null) {
      Log.i(OBackupGoldConstants.LOGCAT, "0 Backup: WifiInfo null");
      _notifyNoWifi();
      return false;
    }
    String connectedSSID = wi.getSSID();
    if (connectedSSID == null) {
      Log.i(OBackupGoldConstants.LOGCAT, "0 Backup: SSID null");
      _notifyNoWifi();
      return false;
    }
    if (!ssid.equals("")) {
      if (!ssid.equalsIgnoreCase(connectedSSID)) {
        Log.i(OBackupGoldConstants.LOGCAT, "0 Backup: " + ssid + " != "
            + connectedSSID);
        _notifyNoSsid(ssid);
        return false;
      }
    }
    return true;
  }

  public static void setAlarm(boolean enabled, Context context) {
    Intent intent = new Intent(context, OBackupGoldAlarmReceiver.class);
    PendingIntent sender = PendingIntent.getBroadcast(context, MAGIC, intent,
        PendingIntent.FLAG_UPDATE_CURRENT);
    AlarmManager am = (AlarmManager) context
        .getSystemService(Context.ALARM_SERVICE);
    am.cancel(sender);
    Log.d(OBackupGoldConstants.LOGCAT, "0 Backup: removed alarms");
    if (enabled) {
      Calendar firstAlarm = Calendar.getInstance();
      Calendar now = Calendar.getInstance();
      SharedPreferences sharedPreferences = PreferenceManager
          .getDefaultSharedPreferences(context);
      firstAlarm.set(Calendar.HOUR_OF_DAY,
          sharedPreferences.getInt(OBackupGoldConstants.TIMEHOUR, 0));
      firstAlarm.set(Calendar.MINUTE,
          sharedPreferences.getInt(OBackupGoldConstants.TIMEMINUTE, 0));
      firstAlarm.set(Calendar.SECOND, 0);
      firstAlarm.set(Calendar.MILLISECOND, 0);
      while (firstAlarm.before(now)) {
        firstAlarm.add(Calendar.MILLISECOND, (int) SCHEDINTERVAL);
      }
      Log.d(OBackupGoldConstants.LOGCAT,
          "0 Backup: set alarm " + firstAlarm.toString() + " every "
              + SCHEDINTERVAL + " ms");
      am.setRepeating(AlarmManager.RTC_WAKEUP, firstAlarm.getTimeInMillis(),
          SCHEDINTERVAL, sender);
    }
  }

  private Notification _getNotification(boolean rebuild) {
    if ((_notification == null) || (rebuild)) {
      int icon = R.drawable.notification_icon;
      CharSequence tickerText = _context.getResources().getText(
          R.string.app_name);
      ;
      long when = System.currentTimeMillis();
      _notification = new Notification(icon, tickerText, when);
    }
    return _notification;
  }

  protected void _notify(CharSequence contentText) {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, contentText);
  }

  protected void _notify(Notification notification, CharSequence contentText) {
    CharSequence contentTitle = _context.getResources().getText(
        R.string.app_name);
    Intent notificationIntent = new Intent(_context, OBackupGold.class);
    PendingIntent contentIntent = PendingIntent.getActivity(_context, 0,
        notificationIntent, 0);
    notification.setLatestEventInfo(_context, contentTitle, contentText,
        contentIntent);
    ((NotificationManager) _context
        .getSystemService(Context.NOTIFICATION_SERVICE)).notify(
        NOTIFICATION_ID, notification);
  }

  private void _notifyFatalError() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, _context.getResources()
        .getText(R.string.notification_fatalerror));
  }

  private void _notifyNoWifi() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, _context.getResources().getText(R.string.notification_nowifi));
  }

  private void _notifyNoSsid(String ssid) {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(
        n,
        "'" + ssid + "' "
            + _context.getResources().getText(R.string.notification_nossid));
  }

  private void _notifyNoPlugged() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, _context.getResources().getText(R.string.notification_nowifi));
  }

}
