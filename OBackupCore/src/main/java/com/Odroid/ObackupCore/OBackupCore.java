package com.Odroid.ObackupCore;

import java.util.Date;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class OBackupCore extends OBackupCoreBaseActivity {

  protected OBackupCoreServiceInterface _serviceInterface = null;
  private Handler _refreshHandler;
  private Runnable _refreshRunnable = new Runnable() {
    public void run() {
      _refreshStatus();
    }
  };
  private static final long REFRESH_BUSY = 500;
  private static final long REFRESH_IDLE = 3000;

  protected OBackupCoreServiceInterface getServiceInterface() {
    return this._serviceInterface;
  }

  protected void setServiceInterface(OBackupCoreServiceInterface s) {
    this._serviceInterface = s;
  }

  protected ServiceConnection _connection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      setServiceInterface(OBackupCoreServiceInterface.Stub.asInterface((IBinder) service));
      _refreshHandler.removeCallbacks(_refreshRunnable);
      _refreshHandler.post(_refreshRunnable);
      android.util.Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onServiceConnected() " + service.toString());
    }

    public void onServiceDisconnected(ComponentName className) {
      android.util.Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onServiceDisconnected()");
      setServiceInterface(null);
    }
  };

  protected void doBindService() {
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: doBindService() {");
    boolean bound = bindService(new Intent(this, OBackupCoreService.class), _connection, Context.BIND_AUTO_CREATE);
    if (!bound) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.noservice).setCancelable(false)
          .setPositiveButton(R.string.noservice_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              OBackupCore.this.finish();
            }
          });
      AlertDialog alert = builder.create();
      alert.show();
    }
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: } doBindService()");
  }

  protected void doUnbindService() {
    unbindService(_connection);
  }

  @Override
  protected void onDestroy() {
    Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onDestroy");
    _refreshHandler.removeCallbacks(_refreshRunnable);
    doUnbindService();
    super.onDestroy();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onCreate");
    super.onCreate(savedInstanceState);
    _refreshHandler = new Handler();
    doBindService();
    setContentView(getLayoutMain());
    OBackupExcludeList.convertExcludeList(PreferenceManager.getDefaultSharedPreferences(this));
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (getServiceInterface() != null) {
      _refreshHandler.removeCallbacks(_refreshRunnable);
      _refreshHandler.postDelayed(_refreshRunnable, REFRESH_BUSY);
    }
  }

  // @Override
  // protected void onPause() {
  // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onPause");
  // super.onPause();
  // }
  // @Override
  // protected void onStart() {
  // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onStart");
  // super.onStart();
  // }
  // @Override
  // protected void onStop() {
  // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: onStop");
  // super.onStop();
  // }

  protected int getLayoutMain() {
    return R.layout.main;
  }

  private void _refreshStatus() {
    // SharedPreferences sp =
    // PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences resSharedPreferences = getSharedPreferences(OBackupCoreConstants.RES_SHPRF, MODE_PRIVATE);
    String messages = resSharedPreferences.getString(OBackupCoreConstants.RES_MESSAGES, "");
    boolean doing;
    try {
      OBackupCoreServiceInterface service = getServiceInterface();
      if (service == null) {
        doing = false;
        Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: service null on 134");
        Toast.makeText(getApplicationContext(), getText(R.string.nullservice), Toast.LENGTH_SHORT).show();
      } else {
        doing = service.service_getDoingBackup();
      }
    } catch (RemoteException e) {
      doing = false;
      Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: " + Log.getStackTraceString(e));
      Toast.makeText(getApplicationContext(), getText(R.string.noservice), Toast.LENGTH_SHORT).show();
    }
    if (doing) {
      String file = "";
      try {
        file = getServiceInterface().service_getCurrentFile();
      } catch (RemoteException e) {
        Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: " + Log.getStackTraceString(e));
        Toast.makeText(getApplicationContext(), getText(R.string.noservice), Toast.LENGTH_SHORT).show();
      }
      ((TextView) findViewById(R.id.backup_progressfile)).setText(file);
      ((LinearLayout) findViewById(R.id.backup_progress)).setVisibility(View.VISIBLE);
    } else {
      ((TextView) findViewById(R.id.backup_progressfile)).setText("");
      ((LinearLayout) findViewById(R.id.backup_progress)).setVisibility(View.GONE);
    }
    long lastBackup = resSharedPreferences.getLong(OBackupCoreConstants.RES_LAST_BACKUP, 0);
    if (lastBackup == 0) {
      messages += getText(R.string.status_never).toString() + "\n";
    } else {
      Date d = new Date(lastBackup);
      java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
      java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
      messages += getText(R.string.status_last) + " " + dateFormat.format(d) + " " + timeFormat.format(d) + "\n";
    }
    // Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: Refreshing: "+messages.replace('\n', '*'));
    ((TextView) findViewById(R.id.backup_status)).setText(messages);
    if (doing) {
      _refreshHandler.removeCallbacks(_refreshRunnable);
      _refreshHandler.postDelayed(_refreshRunnable, REFRESH_BUSY);
    } else {
      _refreshHandler.removeCallbacks(_refreshRunnable);
      _refreshHandler.postDelayed(_refreshRunnable, REFRESH_IDLE);
    }
  }

  public void backup_now(View v) {
    _backup_now_check(true, true, true);
  }

  private void _backup_now_check(boolean checkExclude, boolean checkWifi, boolean checkPlugged) {
    if (checkExclude) {
      _backup_now_check_exclude();
    } else if (checkWifi) {
      _backup_now_check_wifi();
    } else if (checkPlugged) {
      _backup_now_check_plugged();
    } else {
      // _thread = new OBackupThread();
      // _thread.start();
      _startBackupService();
      _refreshHandler.postDelayed(_refreshRunnable, REFRESH_BUSY);
    }
  }

  private void _backup_now_check_exclude() {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    if (sp.getString(OBackupCoreConstants.EXCLUDE, "").equals("")) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.backup_noexclude).setCancelable(false)
          .setPositiveButton(R.string.backup_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              _backup_now_check(false, true, true);
            }
          }).setNegativeButton(R.string.backup_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });
      AlertDialog alert = builder.create();
      alert.show();
    } else {
      _backup_now_check(false, true, true);
    }
  }

  private void _backup_now_check_wifi() {
    if (!_isWifiOn()) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.backup_nowifi).setCancelable(false)
          .setPositiveButton(R.string.backup_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              _backup_now_check(false, false, true);
            }
          }).setNegativeButton(R.string.backup_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });
      AlertDialog alert = builder.create();
      alert.show();
    } else {
      _backup_now_check(false, false, true);
    }
  }

  private void _backup_now_check_plugged() {
    boolean plugged = false;
    try {
      plugged = getServiceInterface().service_getIsPlugged();
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    if (!plugged) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.backup_unplugged).setCancelable(false)
          .setPositiveButton(R.string.backup_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              _backup_now_check(false, false, false);
            }
          }).setNegativeButton(R.string.backup_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });
      AlertDialog alert = builder.create();
      alert.show();
    } else {
      _backup_now_check(false, false, false);
    }
  }

  private boolean _isWifiOn() {
    WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    if (wm == null) return false;
    WifiInfo wi = wm.getConnectionInfo();
    if (wi == null) return false;
    String connectedSSID = wi.getSSID();
    if (connectedSSID == null) return false;
    if (!wm.isWifiEnabled()) return false;
    return true;
  }

  public void backup_stop(View v) {
    Log.d(OBackupCoreConstants.LOGCAT, "Basepath: " + getPrefBasePath());
    try {
      getServiceInterface().service_doStopBackup();
      Toast.makeText(getApplicationContext(), getText(R.string.backup_stopping), Toast.LENGTH_SHORT).show();
    } catch (RemoteException e) {
      Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: " + Log.getStackTraceString(e));
      Toast.makeText(getApplicationContext(), getText(R.string.noservice), Toast.LENGTH_SHORT).show();
    }

    /*
     * boolean plugged = false; try { plugged =
     * getServiceInterface().service_getIsPlugged(); } catch (RemoteException e)
     * { e.printStackTrace(); } if (plugged) {
     * Toast.makeText(getApplicationContext(), "Plugged",
     * Toast.LENGTH_SHORT).show(); } else {
     * Toast.makeText(getApplicationContext(), "Unplugged",
     * Toast.LENGTH_SHORT).show(); }
     */
  }

  public void backup_server(View v) {
    Intent intent = new Intent(this, OBackupCoreServer.class);
    startActivity(intent);
  }

  public void backup_exclude(View v) {
    Intent intent = new Intent(this, OBackupCoreExclude.class);
    startActivity(intent);
  }

  public void backup_settings(View v) {
    Intent preferencesIntent = new Intent(getApplicationContext(), OBackupCorePreferences.class);
    startActivity(preferencesIntent);
  }

  protected void _startBackupService() {
    startService(new Intent(this, OBackupCoreService.class));
  }

}
