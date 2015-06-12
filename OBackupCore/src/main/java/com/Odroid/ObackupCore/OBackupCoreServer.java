package com.Odroid.ObackupCore;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Vector;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class OBackupCoreServer extends OBackupCoreBaseActivity {
  protected SharedPreferences _sharedPreferences = null;
  private Handler _browserHandler = new Handler();
  private Runnable _browserRunnable = null;
  private String[] _dirs;

  private static final int LISTBG = 0xff606060;
  private static final int LISTBGFOCUS = 0xff808080;
  private static final int LISTFG = 0xffFFE877;

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.server);

    _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    ((EditText) findViewById(R.id.server_server)).setText(_sharedPreferences.getString(OBackupCoreConstants.SERVER, ""));
    ((EditText) findViewById(R.id.server_user)).setText(_sharedPreferences.getString(OBackupCoreConstants.USER, ""));
    ((EditText) findViewById(R.id.server_domain)).setText(_sharedPreferences.getString(OBackupCoreConstants.DOMAIN, ""));
    ((EditText) findViewById(R.id.server_password)).setText(_sharedPreferences.getString(OBackupCoreConstants.PASSWORD, ""));
    ((EditText) findViewById(R.id.server_server)).addTextChangedListener(new TextWatcherSaverReseter(R.id.server_server, OBackupCoreConstants.SERVER,
        R.id.server_path));
    ((EditText) findViewById(R.id.server_user)).addTextChangedListener(new TextWatcherSaverReseter(R.id.server_user, OBackupCoreConstants.USER,
        R.id.server_path));
    ((EditText) findViewById(R.id.server_domain)).addTextChangedListener(new TextWatcherSaverReseter(R.id.server_domain, OBackupCoreConstants.DOMAIN,
        R.id.server_path));
    ((EditText) findViewById(R.id.server_password)).addTextChangedListener(new TextWatcherSaverReseter(R.id.server_password, OBackupCoreConstants.PASSWORD,
        R.id.server_path));

    ((TextView) findViewById(R.id.server_path)).setText(_sharedPreferences.getString(OBackupCoreConstants.PATH, "/"));
    ((TextView) findViewById(R.id.server_path)).addTextChangedListener(new TextWatcherSaver(R.id.server_path, OBackupCoreConstants.PATH));
    _browserRunnable = new Runnable() {
      public void run() {
        _refresh();
      }
    };

  }

  private class ToastRunnable implements Runnable {
    private CharSequence _text = "";
    public ToastRunnable(CharSequence text) {
      _text = text;
    }
    public void run() {
      Toast.makeText(getApplicationContext(), _text, Toast.LENGTH_SHORT).show();
    }
  }
  
  private void _toastOnUI(CharSequence text) {
    runOnUiThread(new ToastRunnable(text));
  }

  public void server_browseServer(View v) {
    // _notify(getText(R.string.server_wait));
    ((ProgressBar) findViewById(R.id.server_progress)).setVisibility(View.VISIBLE);
    (new Thread() {
      public void run() {
        Vector<String> v = new Vector<String>();
        String server = _sharedPreferences.getString(OBackupCoreConstants.SERVER, "");
        String user = _sharedPreferences.getString(OBackupCoreConstants.USER, "");
        String domain = _sharedPreferences.getString(OBackupCoreConstants.DOMAIN, "");
        String password = _sharedPreferences.getString(OBackupCoreConstants.PASSWORD, "");
        String path = _sharedPreferences.getString(OBackupCoreConstants.PATH, "/");
        if (!path.startsWith("/")) path = "/" + path;
        if (!path.endsWith("/")) path = path + "/";
        SharedPreferences.Editor editor = _sharedPreferences.edit();
        editor.putString(OBackupCoreConstants.PATH, path);
        editor.commit();
        String smbroot = null;
        if (server.equals("")) {
          _toastOnUI(getApplicationContext().getText(R.string.server_mandatory));
          return;
        }
        if (user.equals("")) {
          smbroot = "smb://" + server;
        } else {
          smbroot = "smb://" + (domain.equals("") ? "" : domain + ";") + user + (password.equals("") ? "" : ":" + password) + "@" + server;
        }
        smbroot += path;
        String[] files = new String[0];
        try {
          // android.util.Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: " + smbroot);
          SmbFile smbFile = new SmbFile(smbroot, null, SmbFile.FILE_SHARE_DELETE | SmbFile.FILE_SHARE_READ | SmbFile.FILE_SHARE_WRITE);
          files = smbFile.list();
        } catch (MalformedURLException e) {
          Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
          _toastOnUI(getApplicationContext().getText(R.string.server_cannot) + " " + smbroot);
          files = new String[0];
        } catch (SmbException e) {
          Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
          _toastOnUI(getApplicationContext().getText(R.string.server_cannot) + " " + smbroot);
          files = new String[0];
        }
        if (!path.equals("/")) {
          v.add("../");
        }
        for (int i = 0; i < files.length; i++) {
          //Log.d(OBackupCoreConstants.LOGCAT, "0 Backup:        " + files[i]);
          // try { Thread.sleep(500); } catch (Exception e) {} // To simulate a slow network...
          if (path.equals("/") && files[i].endsWith("$")) continue;
          String itemS = smbroot + "/" + files[i];
          try {
            SmbFile listFile = new SmbFile(itemS, null, SmbFile.FILE_SHARE_DELETE | SmbFile.FILE_SHARE_READ | SmbFile.FILE_SHARE_WRITE);
            if (listFile.isDirectory()) {
              //android.util.Log.d(OBackupCoreConstants.LOGCAT, "0 Backup: +" + files[i]);
              v.add(files[i] + "/");
            } else {
              //android.util.Log.d(OBackupCoreConstants.LOGCAT, "0 Backup:  " + files[i]);
            }
          } catch (MalformedURLException e) {
            Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
          } catch (SmbException e) {
            Log.w(OBackupCoreConstants.LOGCAT, Log.getStackTraceString(e));
          }
        }
        _dirs = v.toArray(new String[0]);
        Arrays.sort(_dirs);
        _browserHandler.post(_browserRunnable);
      }
    }).start();
  }

  private void _refresh() {
    ((ProgressBar) findViewById(R.id.server_progress)).setVisibility(View.GONE);
    String path = _sharedPreferences.getString(OBackupCoreConstants.PATH, "/");
    ((TextView)findViewById(R.id.server_path)).setText(path);
    LinearLayout linear = (LinearLayout) findViewById(R.id.server_list);
    linear.removeAllViews();
    for (int i = 0; i < _dirs.length; i++) {
      TextView item = new TextView(getApplicationContext());
      item.setClickable(true);
      item.setText(_dirs[i]);
      item.setOnClickListener(_listOnClickListener);
      item.setOnFocusChangeListener(_listOnFocusChangeListener);
      item.setPadding(20, 20, 20, 20);
      item.setTextColor(LISTFG);
      item.setBackgroundColor(LISTBG);
      item.setFocusable(true);
      linear.addView(item);
    }
  }

  public void server_done(View v) {
    finish();
  }

  private View.OnFocusChangeListener _listOnFocusChangeListener = new View.OnFocusChangeListener() {
    public void onFocusChange(View v, boolean hasFocus) {
      if (hasFocus) {
        v.setBackgroundColor(LISTBGFOCUS);
      } else {
        v.setBackgroundColor(LISTBG);
      }
    }
  };

  private View.OnClickListener _listOnClickListener = new View.OnClickListener() {
    public void onClick(View v) {
      if (v instanceof TextView) {
        v.setBackgroundColor(LISTBGFOCUS);
        String dir = ((TextView) v).getText().toString();
        String base = ((TextView) findViewById(R.id.server_path)).getText().toString();
        String target = null;
        if (!base.startsWith("/")) base = "/" + base;
        if (!base.endsWith("/")) base = base + "/";
        if (dir.equals("../")) {
          target = base.replaceAll("/[^/]*/$", "/");
        } else {
          target = base + dir;
        }
        SharedPreferences.Editor e = _sharedPreferences.edit();
        e.putString(OBackupCoreConstants.PATH, target);
        e.commit();
        server_browseServer(findViewById(R.id.server_browseServer));
      }
    }
  };

  // Auto-saves the EditText to the preference whenever changed
  private class TextWatcherSaver implements TextWatcher {
    private int _editText;
    private String _preference;

    TextWatcherSaver(int editText, String preference) {
      _editText = editText;
      _preference = preference;
    }

    public void afterTextChanged(Editable arg0) {
      SharedPreferences.Editor e = _sharedPreferences.edit();
      e.putString(_preference, ((TextView) findViewById(_editText)).getText().toString());
      e.commit();
      ((LinearLayout) findViewById(R.id.server_list)).removeAllViews();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  // Auto-saves the EditText to the preference whenever changed and them resets
  // the textView
  private class TextWatcherSaverReseter extends TextWatcherSaver {
    private int _textView;

    TextWatcherSaverReseter(int editText, String preference, int textView) {
      super(editText, preference);
      _textView = textView;
    }

    public void afterTextChanged(Editable arg0) {
      super.afterTextChanged(arg0);
      ((TextView) findViewById(_textView)).setText("/");
    }
  }
}
