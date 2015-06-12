package com.Odroid.ObackupCore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashSet;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class OBackupCoreService extends Service {

  private int _percent = 0;
  private boolean _stopBackup = false;
  private boolean _doingBackup = false;
  private Thread _doerThread = null;
  @SuppressWarnings("rawtypes")
  private Class _backClass = OBackupCore.class;
  PowerManager _powerManagement = null;
  PowerManager.WakeLock _wakeLock = null;
  WifiManager.WifiLock _wifiLock = null;
  private boolean _isPlugged = false;
  private boolean _checkPlugged = false;
  private boolean _turnedWifiOn = false;
  private String _currentFile = "";
  private String _backupbase = "";
  private File _backupbaseF = null;
  BatteryReceiver _batteryReceiver = null;
  private Handler _handler = null;

  // private final IBinder _binder = new OBackupCoreService.OBackupBinder();

  private static final int NOTIFICATION_ID = 1;
  private static final int NOTIFICATIONSERVICE_ID = 2;
  private static final int NOTIFICATIONUNPLUGGED_ID = 3;
  private Notification _notification = null;

  private NtlmPasswordAuthentication _auth = null;
  private int _nerrors = 0;
  private static final int MAXERRORS = 10;
  // private static final int MAXERRORS = 2; // Testing
  private static final int BUFFERSIZE = 32768;

  private OBackupCoreServiceInterface.Stub _serviceBinder = new OBackupCoreServiceInterfaceBinder();

  static {
    System.setProperty("jcifs.smb.client.responseTimeout", "120000");
    System.setProperty("jcifs.smb.client.soTimeout", "130000");
  }

  @Override
  public void onCreate() {
    android.util.Log.d(OBackupCoreConstants.LOGCAT,
        "0 Backup: Service onCreate()");
    OBackupExcludeList.convertExcludeList(PreferenceManager
        .getDefaultSharedPreferences(this));
    // This just dealys the error, but solves nothing... faking the UI
    // System.setProperty("jcifs.smb.client.responseTimeout", "300000");
    // System.setProperty("jcifs.smb.client.soTimeout", "350000");
    _setLastStatus(null);
    _registerBatteryReceiver();
  }

  @Override
  public void onDestroy() {
    android.util.Log.d(OBackupCoreConstants.LOGCAT,
        "0 Backup: Service onDestroy()");
    if (_doingBackup) {
      doStopBackup();
      _notifyDestroyed();
    }
    _unregisterBatteryReceiver();
    super.onDestroy();
  }

  private void _registerBatteryReceiver() {
    if (_batteryReceiver == null) {
      _batteryReceiver = new BatteryReceiver();
    } else {
      unregisterReceiver(_batteryReceiver);
    }
    _batteryReceiver.resetIsFresh();
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    registerReceiver(_batteryReceiver, filter);
  }

  private void _unregisterBatteryReceiver() {
    unregisterReceiver(_batteryReceiver);
  }

  // See onStart() below for pre-2.0 compatilibity
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    android.util.Log.d(OBackupCoreConstants.LOGCAT,
        "0 Backup: Service onStartCommand: startid " + startId + "; " + intent);
    onStart(intent, startId);
    return START_STICKY;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void onStart(Intent intent, int startId) {
    android.util.Log.d(OBackupCoreConstants.LOGCAT,
        "0 Backup: Service onStart: startid " + startId + "; " + intent);
    if (intent == null)
      return;
    if (_doingBackup)
      return;
    Serializable backClass = intent
        .getSerializableExtra(OBackupCoreConstants.BACKCLASS);
    if ((backClass != null) && (backClass instanceof Class)) {
      _backClass = (Class) backClass;
    } else {
      _backClass = OBackupCore.class;
    }
    _checkPlugged = intent.getBooleanExtra(
        OBackupCoreConstants.CHECKPLUGGEDONLY, false);
    _turnedWifiOn = intent.getBooleanExtra(OBackupCoreConstants.TURNEDWIFION,
        false);
    _doerThread = new DoerThread();
    // If turned wifi on, wait 30s for wifi to get IP
    if (_turnedWifiOn) {
      if (_handler == null) {
        _handler = new Handler();
      }
      _notify("Getting WiFi IP");
      _handler.postDelayed(new Runnable() {
        public void run() {
          _doerThread.start();
        }
      }, 30000);
    } else {
      _doerThread.start();
    }
    return;
  }

  @Override
  public IBinder onBind(Intent intent) {
    android.util.Log.d(OBackupCoreConstants.LOGCAT,
        "0 Backup: Service onBind()");
    return _serviceBinder;
  }

  public void doBackupNow() {
    if (_doingBackup)
      return;
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup:   Backup started");
    _doingBackup = true;
    _stopBackup = false;
    _percent = 0;
    _currentFile = "";
    _notifyStarted();
    _nerrors = 0;
    _clearMessages();

    SharedPreferences sharedPreferences = PreferenceManager
        .getDefaultSharedPreferences(this);
    SharedPreferences resSharedPreferences = getSharedPreferences(
        OBackupCoreConstants.RES_SHPRF, MODE_PRIVATE);
    String server = sharedPreferences
        .getString(OBackupCoreConstants.SERVER, "");
    String user = sharedPreferences.getString(OBackupCoreConstants.USER, "");
    String password = sharedPreferences.getString(
        OBackupCoreConstants.PASSWORD, "");
    String domain = sharedPreferences
        .getString(OBackupCoreConstants.DOMAIN, "");
    String path = sharedPreferences.getString(OBackupCoreConstants.PATH, "/");
    // String exclude =
    // sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    boolean delete = sharedPreferences.getBoolean(OBackupCoreConstants.DELETE,
        false);
    boolean abort = sharedPreferences.getBoolean(OBackupCoreConstants.ABORT,
        false);

    if (server.equals("")) {
      _notifyServer();
      _doingBackup = false;
      _setLastStatus(R.string.notification_server);
      return;
    }

    // Get SMB path root
    if (!user.equals("")) {
      _auth = new NtlmPasswordAuthentication(domain, user, password);
    }
    String smbrootS = "smb://" + server + path;
    SmbFile smbroot = null;
    try {
      smbroot = getSmbFile(smbrootS, _auth);
      // new SmbFile(smbrootS, _aauth, SmbFile.FILE_SHARE_DELETE |
      // SmbFile.FILE_SHARE_READ | SmbFile.FILE_SHARE_WRITE);
      boolean isDir = smbroot.isDirectory();
      if (!isDir) {
        _notifyNoSmb(smbrootS, " isDirectory()");
        _doingBackup = false;
        _setLastStatus(smbrootS + " " + getText(R.string.notification_nosmb)
            + " isDirectory()");
        return;
      }
    } catch (MalformedURLException e) {
      Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
      _notifyNoSmb(smbrootS, e.getMessage());
      _doingBackup = false;
      _setLastStatus(smbrootS + " " + getText(R.string.notification_nosmb)
          + " " + e.getMessage());
      return;
    } catch (SmbException e) {
      Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
      _notifyNoSmb(smbrootS, e.getMessage());
      _doingBackup = false;
      _setLastStatus(smbrootS + " " + getText(R.string.notification_nosmb)
          + " " + e.getMessage());
      return;
    } catch (RuntimeException e) {
      Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
      _notifyNoSmb(smbrootS, e.getMessage());
      _doingBackup = false;
      _setLastStatus(smbrootS + " " + getText(R.string.notification_nosmb)
          + " " + e.getMessage());
      return;
    }

    _backupbase = sharedPreferences.getString(OBackupCoreConstants.BASEPATH,
        Environment.getExternalStorageDirectory().getPath());

    String sdroot = Environment.getExternalStorageDirectory().getPath();
    if (_backupbase.startsWith(sdroot)) {
      // Check SD card root and exclude list
      boolean mounted = false;
      String state = Environment.getExternalStorageState();
      if (Environment.MEDIA_MOUNTED.equals(state)) {
        mounted = true;
      } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
        mounted = true;
      } else {
        _notifyNoSd();
        _doingBackup = false;
        _setLastStatus(R.string.notification_nosd);
        return;
      }
      if (!mounted) {
        _notifyNoSd();
        _doingBackup = false;
        _setLastStatus(R.string.notification_nosd);
        return;
      }
    }
    HashSet<String> excludeSet = OBackupExcludeList
        .getExcludeHashset(sharedPreferences);
    _percent = 0;
    _notifyPercent();
    _setLastStatus(getText(R.string.notification_doing) + " [" + _percent
        + "%]");

    try {
      _backupbaseF = new File(_backupbase);
      _syncDir(_backupbaseF, smbroot, excludeSet, delete, abort);
      _percent = 100;
      if (_nerrors == 0) {
        _notifySuccess();
        _setLastStatus(R.string.notification_success);
      } else {
        _notifySuccessWErrors();
        _setLastStatus(R.string.notification_successwerrors);
      }
      SharedPreferences.Editor e = resSharedPreferences.edit();
      e.putLong(OBackupCoreConstants.RES_LAST_BACKUP,
          System.currentTimeMillis());
      e.commit();
    } catch (SyncException e) {
      _percent = 0;
      _notifyError(e.getMessage());
      _setLastStatus(getText(R.string.notification_error) + ": "
          + e.getMessage());
    } catch (StoppedException e) {
      _percent = 0;
      _notifyStopped();
      _setLastStatus(R.string.notification_stopped);
    }
    _doingBackup = false;
    _stopBackup = false;
    _currentFile = "";
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup:   Backup ended");
  }

  // If excludeSet is null, nothing is excluded
  private void _syncDir(File sddir, SmbFile smbdir, HashSet<String> excludeSet,
      boolean delete, boolean abort) throws SyncException, StoppedException {
    if (_stopBackup)
      throw new StoppedException();
    String[] sdfilesArray = sddir.list();
    if (sdfilesArray == null) {
      sdfilesArray = new String[0];
    }

    // First, copy new or modified files and new dirs from sddir to smbdir (if
    // not in exclude)
    // long now = System.currentTimeMillis();
    // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: { syncdir " +
    // sddir.getPath() + " ==> " + smbdir.getPath() + " @ " + now);
    for (int i = 0; i < sdfilesArray.length; i++) {
      String filename = sdfilesArray[i];

      // ps.println(filename);

      Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: syncdir " + sddir.getPath()
          + " / " + filename + " ==> " + smbdir.getPath());
      if (excludeSet.contains(sddir.getPath() + "/" + filename)) {
        // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: syncdir " + filename +
        // " excluded");
        _addInfo(getResources().getString(R.string.notification_exclude)
            + sddir.getPath() + "/" + filename);
      } else {
        try {
          File sdfile = new File(sddir, filename);
          SmbFile smbfile = getSmbFile(smbdir.getPath() + "/" + filename, _auth);
          if (smbfile.exists()) {
            _sync(sdfile, smbfile, excludeSet, delete, abort);
          } else {
            _copy(sdfile, smbfile, excludeSet, abort);
          }
        } catch (MalformedURLException e) {
          _addError(sddir.getPath() + "/" + filename + ": " + e.getMessage());
          Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage()
              + "\n" + Log.getStackTraceString(e));
          if (abort)
            throw new SyncException(sddir.getPath() + "/" + filename, e);
        } catch (SmbException e) {
          _addError(sddir.getPath() + "/" + filename + ": " + e.getMessage());
          Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage()
              + "\n" + Log.getStackTraceString(e));
          if (abort)
            throw new SyncException(sddir.getPath() + "/" + filename, e);
        }
      }
      if (_backupbaseF.equals(sddir)) {
        // ==> this is the root folder
        _percent = (i + 1) * 100 / sdfilesArray.length;
        // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup:   " + getPercent() +
        // " %");
        // if ((_wifiLock != null) && _wifiLock.isHeld()) {
        // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: Wifi lock is held");
        // }
        _notifyPercent();
        _setLastStatus(getText(R.string.notification_doing) + " [" + _percent
            + "%]");
      }
    }
    // long now2 = System.currentTimeMillis();
    // long elapsed = now2-now;
    // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup:   syncdir " +
    // sddir.getPath() + " ==> " + smbdir.getPath() + " @ " + now2 +
    // " -> "+elapsed);

    // Second, delete files not in sddir from smbdir (if configured)
    if (delete) {
      try {
        if (!smbdir.getPath().endsWith("/")) {
          smbdir = getSmbFile(smbdir.getPath() + "/", _auth);
        }
        String[] smbfilesArray = smbdir.list();
        if (smbfilesArray == null) {
          smbfilesArray = new String[0];
        }
        for (String filename : smbfilesArray) {
          File sdfile = new File(sddir, filename);
          if (!sdfile.exists()) {
            try {
              SmbFile smbfile = getSmbFile(smbdir.getPath() + "/" + filename,
                  _auth);
              if (smbfile.isDirectory() && (!smbfile.getPath().endsWith("/"))) {
                smbfile = getSmbFile(smbdir.getPath() + "/" + filename + "/",
                    _auth);
              }
              smbfile.delete();
              Log.d(OBackupCoreConstants.LOGCAT,
                  "0 Backup: deleted " + smbfile.getPath());
            } catch (MalformedURLException e) {
              _addError(smbdir.getPath() + ": " + e.getMessage());
              Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage()
                  + "\n" + Log.getStackTraceString(e));
              if (abort)
                throw new SyncException(smbdir.getPath() + "/" + filename, e);
            } catch (SmbException e) {
              _addError(smbdir.getPath() + ": " + e.getMessage());
              Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage()
                  + "\n" + Log.getStackTraceString(e));
              if (abort)
                throw new SyncException(smbdir.getPath() + "/" + filename, e);
            }
          }
        }
      } catch (SmbException e) {
        _addError(smbdir.getPath() + ": " + e.getMessage());
        Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
            + Log.getStackTraceString(e));
        if (abort)
          throw new SyncException(smbdir.getPath(), e);
      } catch (MalformedURLException e) {
        _addError(smbdir.getPath() + ": " + e.getMessage());
        Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
            + Log.getStackTraceString(e));
        if (abort)
          throw new SyncException(smbdir.getPath(), e);
      }
    }
    // long now3 = System.currentTimeMillis();
    // long elapsed2 = now3-now;
    // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: } syncdir " +
    // sddir.getPath() + " ==> " + smbdir.getPath() + " @ " + now3 +
    // " ---> "+elapsed2);

  }

  private void _sync(File sdfile, SmbFile smbfile, HashSet<String> excludeSet,
      boolean delete, boolean abort) throws SyncException, StoppedException {
    if (_stopBackup)
      throw new StoppedException();
    try {
      if (sdfile.isDirectory() && smbfile.isDirectory()) {
        _syncDir(sdfile, smbfile, excludeSet, delete, abort);
      } else if (sdfile.isDirectory() && !smbfile.isDirectory()) {
        if (delete) {
          smbfile.delete();
          _copyDir(sdfile, smbfile, excludeSet, abort);
        } else {
          _addError(sdfile.getPath() + ": delete()");
          if (abort)
            throw new SyncException(sdfile.getPath());
        }
      } else if (!sdfile.isDirectory() && smbfile.isDirectory()) {
        if (delete) {
          // Dirs must end with / to be deleted by jcifs
          if (!smbfile.getPath().endsWith("/")) {
            smbfile = getSmbFile(smbfile.getPath() + "/", _auth);
          }
          smbfile.delete();
          _copyFile(sdfile, smbfile, abort);
        } else {
          _addError(sdfile.getPath() + ": delete()");
          if (abort)
            throw new SyncException(sdfile.getPath());
        }
      } else if (!sdfile.isDirectory() && !smbfile.isDirectory()) {
        _syncFile(sdfile, smbfile, abort);
      }
    } catch (SmbException e) {
      _addError(sdfile.getPath() + ": " + e.getMessage());
      Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
          + Log.getStackTraceString(e));
      if (abort)
        throw new SyncException(sdfile.getPath(), e);
    } catch (MalformedURLException e) {
      _addError(smbfile.getPath() + ": " + e.getMessage());
      Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
          + Log.getStackTraceString(e));
      if (abort)
        throw new SyncException(sdfile.getPath(), e);
    }
  }

  private void _syncFile(File sdfile, SmbFile smbfile, boolean abort)
      throws SyncException, StoppedException {
    if (_stopBackup)
      throw new StoppedException();
    setCurrentFile(sdfile);
    try {
      if (sdfile.lastModified() != smbfile.getLastModified()) {
        // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: lastModified: " +
        // sdfile.getPath() + ":" + sdfile.lastModified() + " != " +
        // smbfile.getLastModified());
        _copyFile(sdfile, smbfile, abort);
        return;
      }
      if (sdfile.length() != smbfile.length()) {
        // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: size: " +
        // sdfile.length() + " != " + smbfile.length());
        _copyFile(sdfile, smbfile, abort);
        return;
      }
      // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: " + sdfile.getPath() +
      // " is up to date");
    } catch (SmbException e) {
      _addError(sdfile.getPath() + ": " + e.getMessage());
      Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
          + Log.getStackTraceString(e));
      if (abort)
        throw new SyncException(sdfile.getPath(), e);
    }
  }

  private void _copy(File sdfile, SmbFile smbfile, HashSet<String> excludeSet,
      boolean abort) throws SyncException, StoppedException {
    if (_stopBackup)
      throw new StoppedException();
    if (sdfile.isDirectory()) {
      _copyDir(sdfile, smbfile, excludeSet, abort);
    } else {
      _copyFile(sdfile, smbfile, abort);
    }
  }

  byte[] buffer = new byte[BUFFERSIZE];

  private void _copyFile(File sdfile, SmbFile smbfile, boolean abort)
      throws SyncException, StoppedException {
    if (_stopBackup)
      throw new StoppedException();
    setCurrentFile(sdfile);
    // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: copying " +
    // sdfile.getPath() + " to " + smbfile.getPath());
    FileInputStream is = null;
    OutputStream os = null;
    BufferedInputStream bis = null;
    BufferedOutputStream bos = null;
    try {
      // long now = (new Date()).getTime();
      int readed = 0;
      is = new FileInputStream(sdfile);
      os = smbfile.getOutputStream();
      bis = new BufferedInputStream(is);
      bos = new BufferedOutputStream(os);
      while ((readed = bis.read(buffer)) != -1) {
        bos.write(buffer, 0, readed);
      }
      // long later = (new Date()).getTime();
      // long Bps = sdfile.length() * 1000 / (later - now);
      // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: Copied "+
      // sdfile.getPath() + " @ " + Bps + " B/s");
    } catch (IOException e) {
      _addError(sdfile.getPath() + ": " + e.getMessage());
      if (abort)
        throw new SyncException(sdfile.getPath(), e);
    } finally {
      try {
        if (bis != null)
          bis.close();
        if (bos != null) {
          bos.flush();
          bos.close();
        }
        smbfile.setLastModified(sdfile.lastModified());
        // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: lastmodified "+
        // sdfile.getPath() + " : " + smbfile.getLastModified());
      } catch (IOException e) {
        _addError(sdfile.getPath() + ": " + e.getMessage());
        Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
            + Log.getStackTraceString(e));
        if (abort)
          throw new SyncException(sdfile.getPath(), e);
      }
    }
  }

  private void _copyDir(File sdfile, SmbFile smbfile,
      HashSet<String> excludeSet, boolean abort) throws SyncException,
      StoppedException {
    if (_stopBackup)
      throw new StoppedException();
    try {
      if (!smbfile.exists()) {
        smbfile.mkdir();
        Log.d(OBackupCoreConstants.LOGCAT,
            "0 Backup: created " + smbfile.getPath());
      }
      String[] infiles = sdfile.list();
      if (infiles != null) {
        for (String infile : infiles) {
          File newsdfile = new File(sdfile, infile);
          SmbFile newsmbfile = getSmbFile(smbfile.getPath() + "/" + infile,
              _auth);
          if (excludeSet.contains(newsdfile.getPath())) {
            // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: syncdir " +
            // newsdfile.getPath() + " excluded");
            _addInfo(getResources().getString(R.string.notification_exclude)
                + newsdfile.getPath());
            continue;
          }
          _copy(newsdfile, newsmbfile, excludeSet, abort);
        }
      }
    } catch (SmbException e) {
      _addError(sdfile.getPath() + ": " + e.getMessage());
      Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
          + Log.getStackTraceString(e));
      if (abort)
        throw new SyncException(sdfile.getPath(), e);
    } catch (MalformedURLException e) {
      _addError(sdfile.getPath() + ": " + e.getMessage());
      Log.w(OBackupCoreConstants.LOGCAT, "0 Backup: " + e.getMessage() + "\n"
          + Log.getStackTraceString(e));
      if (abort)
        throw new SyncException(sdfile.getPath(), e);
    }
  }

  public void doStopBackup() {
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: doStopBackup() {");
    _stopBackup = true;
    _notifyStopping();
    _setLastStatus(R.string.notification_stopping);
    // android.util.Log.d(OBackupCoreConstants.LOGCAT,
    // "0 Backup: }doStopBackup()");
  }

  public int getPercent() {
    return _percent;
  }

  public boolean getDoingBackup() {
    // Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: getDoingBackup @ " +
    // this.toString() + " = " + _doingBackup);
    return _doingBackup;
  }

  public void setCurrentFile(File sdfile) {
    _currentFile = sdfile.getPath().replace(_backupbase, "");
  }

  public String getCurrentFile() {
    return _currentFile;
  }

  public boolean getIsPlugged() {
    return _isPlugged;
  }

  public Notification _getNotification(boolean rebuild) {
    if ((_notification == null) || (rebuild)) {
      int icon = R.drawable.notification_icon;
      CharSequence tickerText = getApplicationContext().getResources().getText(
          R.string.app_name);
      long when = System.currentTimeMillis();
      _notification = new Notification(icon, tickerText, when);
    }
    return _notification;
  }

  public void _addBeep(Notification n) {
    boolean quiet = PreferenceManager.getDefaultSharedPreferences(this)
        .getBoolean(OBackupCoreConstants.QUIET, false);
    if (!quiet) {
      n.defaults |= Notification.DEFAULT_SOUND;
    }
  }

  private void _notifyStarted() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
    _notify(n, getResources().getText(R.string.notification_started));
  }

  private void _notifyPercent() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
    _notify(n, getResources().getText(R.string.notification_doing) + " ("
        + getPercent() + "%).");
  }

  private void _notifyStopping() {
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    java.text.DateFormat df = android.text.format.DateFormat
        .getTimeFormat(getApplicationContext());
    _notify(n, getResources().getText(R.string.notification_stopping) + " ["
        + df.format(new Date()) + "]");
  }

  private void _notifyStopped() {
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    java.text.DateFormat df = android.text.format.DateFormat
        .getTimeFormat(getApplicationContext());
    _notify(n, getResources().getText(R.string.notification_stopped) + " ["
        + df.format(new Date()) + "]");
  }

  private void _notifyDestroyed() {
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, getResources().getText(R.string.notification_destroyed));
  }

  private void _notifyServer() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, getResources().getText(R.string.notification_server));
  }

  private void _notifyNoSmb(String smb, String exc) {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, smb + " " + getResources().getText(R.string.notification_nosmb)
        + exc);
  }

  private void _notifyNoSd() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, getResources().getText(R.string.notification_nosd));
  }

  private void _notifyNoPlugged() {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, getResources().getText(R.string.notification_noplugged));
  }

  private void _notifySuccess() {
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    java.text.DateFormat df = android.text.format.DateFormat
        .getTimeFormat(getApplicationContext());
    _notify(n, getResources().getText(R.string.notification_success) + " ["
        + df.format(new Date()) + "]");
  }

  private void _notifySuccessWErrors() {
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    java.text.DateFormat df = android.text.format.DateFormat
        .getTimeFormat(getApplicationContext());
    _notify(n, getResources().getText(R.string.notification_successwerrors)
        + " [" + df.format(new Date()) + "]");
  }

  private void _notifyError(String text) {
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    java.text.DateFormat df = android.text.format.DateFormat
        .getTimeFormat(getApplicationContext());
    _notify(
        n,
        getResources().getText(R.string.notification_error) + " ["
            + df.format(new Date()) + "]: " + text);
  }

  protected void _notify(Notification notification, CharSequence contentText) {
    CharSequence contentTitle = getResources().getText(R.string.app_name);
    Intent notificationIntent = new Intent(this, _backClass);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        notificationIntent, 0);
    notification.setLatestEventInfo(this, contentTitle, contentText,
        contentIntent);
    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(NOTIFICATION_ID, notification);
  }

  protected void _notify(CharSequence contentText) {
    Notification n = _getNotification(true);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    _notify(n, contentText);
  }

  protected void _notifyStartForeground(int resid) {
    Notification notification = _getNotification(true);
    notification.flags = Notification.FLAG_ONGOING_EVENT
        | Notification.FLAG_NO_CLEAR;
    CharSequence contentTitle = getResources().getText(R.string.app_name);
    Intent notificationIntent = new Intent(this, _backClass);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        notificationIntent, 0);
    notification.setLatestEventInfo(this, contentTitle,
        getResources().getText(resid), contentIntent);
    startForeground(NOTIFICATIONSERVICE_ID, notification);
  }

  protected void _notifyUnplugged() {
    if (_isPlugged)
      return; // Plugging. Do nothing
    if (!_doingBackup)
      return; // Doing nothing... just do nothing
    boolean pluggedOnly = PreferenceManager.getDefaultSharedPreferences(this)
        .getBoolean(OBackupCoreConstants.PLUGGED, true);
    int stringId = 0;
    @SuppressWarnings("rawtypes")
    Class backClass = null;
    if (pluggedOnly) {
      stringId = R.string.notification_unpluggedStop;
      backClass = _backClass;
      doStopBackup();
    } else {
      stringId = R.string.notification_unpluggedContinue;
      backClass = OBackupCorePreferences.class;
    }
    Notification n = _getNotification(true);
    _addBeep(n);
    n.flags = Notification.FLAG_AUTO_CANCEL;
    java.text.DateFormat df = android.text.format.DateFormat
        .getTimeFormat(getApplicationContext());
    CharSequence contentTitle = getResources().getText(R.string.app_name);
    Intent notificationIntent = new Intent(this, backClass);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        notificationIntent, 0);
    String contentText = getResources().getText(stringId) + " ["
        + df.format(new Date()) + "]";
    n.setLatestEventInfo(this, contentTitle, contentText, contentIntent);
    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(NOTIFICATIONUNPLUGGED_ID, n);
  }

  private void _clearMessages() {
    SharedPreferences resSharedPreferences = getSharedPreferences(
        OBackupCoreConstants.RES_SHPRF, MODE_PRIVATE);
    SharedPreferences.Editor e = resSharedPreferences.edit();
    e.putString(OBackupCoreConstants.RES_MESSAGES, "");
    e.commit();
  }

  private void _setLastStatus(CharSequence text) {
    SharedPreferences resSharedPreferences = getSharedPreferences(
        OBackupCoreConstants.RES_SHPRF, MODE_PRIVATE);
    String messages = resSharedPreferences.getString(
        OBackupCoreConstants.RES_MESSAGES, "");
    messages = messages.replaceAll("^> [^\n]*\n", ""); // Replace the first line
    // if it is a status line
    SharedPreferences.Editor e = resSharedPreferences.edit();
    if (text != null) {
      messages = "> " + text + "\n" + messages;
    }
    e.putString(OBackupCoreConstants.RES_MESSAGES, messages);
    e.commit();
  }

  private void _setLastStatus(int textId) {
    _setLastStatus(getText(textId));
  }

  private void _addError(String error) {
    if (_nerrors > MAXERRORS) {
      return;
    }
    if (_nerrors == MAXERRORS) {
      error = getResources().getText(R.string.notification_maxerrors)
          .toString();
    }
    _addInfo(getResources().getText(R.string.notification_errors) + error);
    _nerrors++;
  }

  private void _addInfo(String msg) {
    SharedPreferences resSharedPreferences = getSharedPreferences(
        OBackupCoreConstants.RES_SHPRF, MODE_PRIVATE);
    String messages = resSharedPreferences.getString(
        OBackupCoreConstants.RES_MESSAGES, "");
    SharedPreferences.Editor e = resSharedPreferences.edit();
    messages = messages + msg + "\n";
    e.putString(OBackupCoreConstants.RES_MESSAGES, messages);
    e.commit();
  }

  private void _keepOnStart() {
    if (_powerManagement == null) {
      _powerManagement = (PowerManager) getSystemService(Context.POWER_SERVICE);
    }
    if (_wakeLock == null) {
      _wakeLock = _powerManagement.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
          | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
          "0 Backup power lock");
    }
    _wakeLock.acquire();
    WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    if (wifiManager != null) {
      _wifiLock = wifiManager.createWifiLock("0 Backup wifi lock");
      _wifiLock.acquire();
    }
  }

  private void _keepOnStop() {
    if ((_wifiLock != null) && (_wifiLock.isHeld())) {
      _wifiLock.release();
    }
    if ((_wakeLock != null) && (_wakeLock.isHeld())) {
      _wakeLock.release();
    }
  }

  private void _autowifioff() {
    if (_turnedWifiOn) {
      _turnedWifiOn = false; // Ready for the next round
      WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
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

  private class BatteryReceiver extends BroadcastReceiver {
    private boolean _isFresh = false;

    public void resetIsFresh() {
      _isFresh = false;
    }

    public boolean isFresh() {
      return _isFresh;
    }

    public void onReceive(Context context, Intent intent) {
      int pluggedInt = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
      int statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
      int levelInt = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
      int scaleInt = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
      Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: plugged=" + pluggedInt
          + "; status=" + statusInt + "; level=" + levelInt + "/" + scaleInt);

      if ((pluggedInt != 0)
          || (statusInt == BatteryManager.BATTERY_STATUS_CHARGING)
          || (statusInt == BatteryManager.BATTERY_STATUS_FULL)) {
        _isPlugged = true;
      } else {
        _isPlugged = false;
      }
      _isFresh = true;
      _notifyUnplugged();
    }
  }

  private class DoerThread extends Thread {
    public void run() {
      if (_checkPlugged) { // Only Gold does it, actually
        _checkPlugged = false; // Ready for the next round
        // Hack: if the service has been killed, the battery receiver did not
        // receive anything, and _isPlugged is faked. So we need to force a
        // refresh of it: reregister the _batteryReceiver and wait a few of
        // seconds for it to refresh
        _registerBatteryReceiver();
        int backoffWait = 1000;
        for (int i = 0; i < 5; i++) {
          try {
            Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: sleeping "
                + backoffWait + "ms to refresh _isPlugged");
            Thread.sleep(backoffWait);
          } catch (InterruptedException e) {
            Log.d(OBackupCoreConstants.LOGCAT,
                "0 Backup: " + Log.getStackTraceString(e));
          }
          if (_batteryReceiver.isFresh()) {
            break;
          }
          backoffWait = 2 * backoffWait;
        }

        if (!_isPlugged) {
          boolean pluggedOnly = PreferenceManager.getDefaultSharedPreferences(
              OBackupCoreService.this).getBoolean(OBackupCoreConstants.PLUGGED,
              true);
          if (pluggedOnly) {
            _notifyNoPlugged();
            _autowifioff();
            stopSelf();
            return;
          }
        }
      }
      _notifyStartForeground(R.string.notification_servicerunning);
      _keepOnStart();
      doBackupNow();
      _autowifioff();
      _keepOnStop();
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
          .cancel(NOTIFICATIONSERVICE_ID);
      stopForeground(false);
      Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: Service stopSelf()");
      stopSelf();
    }
  }

  @SuppressWarnings("unused")
  private class DoerThreadFake extends Thread {
    public void run() {
      _notifyStartForeground(R.string.notification_servicerunning);
      _keepOnStart();
      Socket s;
      _doingBackup = true;
      _stopBackup = false;
      _percent = 0;
      _notifyStarted();
      _nerrors = 0;
      _clearMessages();
      byte[] buffer = new byte[1000];

      try {
        s = new Socket("192.168.0.16", 2000);
        PrintStream ps = new PrintStream(s.getOutputStream());
        InputStream is = s.getInputStream();
        for (int i = 0; i < 100000; i++) {
          if (_stopBackup)
            break;
          ps.println(System.currentTimeMillis() + ": " + i);
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          while (is.available() > 0) {
            int a = is.available();
            if (a > 1000)
              a = 1000;
            is.read(buffer, 0, a); // Clean echo
          }
          _percent = i / 1000;
        }
      } catch (UnknownHostException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      _percent = 100;
      _notifySuccess();
      _setLastStatus(R.string.notification_success);
      _doingBackup = false;
      _stopBackup = false;

      _keepOnStop();
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
          .cancel(NOTIFICATIONSERVICE_ID);
      stopForeground(false);
      Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: Service stopSelf()");
      stopSelf();
    }
  }

  // public class OBackupBinder extends Binder {
  // public OBackupCoreService getService() {
  // return OBackupCoreService.this;
  // }
  // }

  @SuppressWarnings("serial")
  private class StoppedException extends Exception {
    StoppedException() {
      super();
    }
  }

  @SuppressWarnings("serial")
  private class SyncException extends Exception {
    SyncException(String s) {
      super(s);
    }

    SyncException(String s, Throwable t) {
      super(s, t);
    }
  }

  public class OBackupCoreServiceInterfaceBinder extends
      OBackupCoreServiceInterface.Stub {
    public int service_getPercent() throws RemoteException {
      return getPercent();
    }

    public boolean service_getDoingBackup() throws RemoteException {
      return getDoingBackup();
    }

    public void service_doStopBackup() throws RemoteException {
      doStopBackup();
    }

    public boolean service_getIsPlugged() throws RemoteException {
      return getIsPlugged();
    }

    public String service_getCurrentFile() throws RemoteException {
      return getCurrentFile();
    }
  };

  // If auth is null, it means that anonymous access is needed, so it must not
  // be passed
  private SmbFile getSmbFile(String url, NtlmPasswordAuthentication auth)
      throws MalformedURLException {
    if (auth == null) {
      return new SmbFile(url);
    } else {
      return new SmbFile(url, auth);
    }
  }
}