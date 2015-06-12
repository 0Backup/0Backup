package com.Odroid.ObackupGold;

import java.io.Serializable;

import android.content.Intent;
import android.view.View;

import com.Odroid.ObackupCore.OBackupCore;
import com.Odroid.ObackupCore.OBackupCoreService;
import com.Odroid.ObackupCore.R;

public class OBackupGold extends OBackupCore {

  @Override
  protected void onResume() {
    super.onResume();
    ((View) findViewById(R.id.backup_scheduling)).setEnabled(true);
  }

  protected int getLayoutMain() {
    return R.layout.main;
  }

  public void backup_scheduling(View v) {
    Intent intent = new Intent(this, OBackupGoldScheduling.class);
    startActivity(intent);
  }

  protected void _startBackupService() {
    Intent i = new Intent(this, OBackupCoreService.class);
    i.putExtra(OBackupGoldConstants.BACKCLASS, (Serializable) OBackupGold.class);
    startService(i);
  }
}