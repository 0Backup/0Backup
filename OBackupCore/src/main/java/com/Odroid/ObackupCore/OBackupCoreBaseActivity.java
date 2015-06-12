package com.Odroid.ObackupCore;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;

public class OBackupCoreBaseActivity extends Activity {
  private static final int MENU_ABOUT = 1;
  private static final int MENU_WEB = 2;
  private static final int MENU_MARKET = 3;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(0, MENU_ABOUT, 0, R.string.menu_about).setIcon(R.drawable.menuicon_about);
    menu.add(0, MENU_WEB, 0, R.string.menu_web).setIcon(R.drawable.menuicon_web);
    menu.add(0, MENU_MARKET, 0, R.string.menu_market).setIcon(R.drawable.menuicon_market);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case MENU_ABOUT:
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle(R.string.about_title).setMessage(R.string.about_text).setCancelable(false)
          .setIcon(R.drawable.icon).setNeutralButton(R.string.about_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          }).show();
    break;
    case MENU_WEB:
      _web();
    break;
    case MENU_MARKET:
      _market();
    break;
    }
    return false;
  }

  private void _web() {
    String url = getString(R.string.web);
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse(url));
    startActivity(i);
  }

  private void _market() {
    String url = getString(R.string.market);
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse(url));
    try {
      startActivity(i);
    } catch (ActivityNotFoundException e) {
      _web();
    }
  }

  public String getPrefBasePath() {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    File f = Environment.getExternalStorageDirectory();
    String basePath = sharedPreferences.getString(OBackupCoreConstants.BASEPATH, f.getPath());
    return basePath;
  }
}
