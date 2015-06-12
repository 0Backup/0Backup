package com.Odroid.ObackupCore;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class OBackupCoreExclude extends OBackupCoreBaseActivity {
  SharedPreferences _sharedPreferences = null;

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.exclude);
    _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    ((ListView) findViewById(R.id.exclude_list)).setAdapter(excludeAdapter);
  }

  protected void onResume() {
    super.onResume();
    excludeAdapter.notifyDataSetChanged();
  }

  public void exclude_done(View v) {
    finish();
  }

  public void exclude_browse(View v) {
    Intent intent = new Intent(this, OBackupCoreExcludeBrowser.class);
    startActivity(intent);
  }

  private BaseAdapter excludeAdapter = new BaseAdapter() {

    public int getCount() {
      return OBackupExcludeList.count(_sharedPreferences);
    }

    public String getItem(int position) {
      return OBackupExcludeList.getItem(_sharedPreferences, position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = inflater.inflate(R.layout.exclude_listitem, parent, false);
        convertView.findViewById(R.id.exclude_listitem_name).setSelected(true);
      }
      convertView.findViewById(R.id.exclude_listitem_include).setOnClickListener(
          new IncludeOnClickListener(getItem(position), this));
      ((TextView) convertView.findViewById(R.id.exclude_listitem_name)).setText(getItem(position));
      ((TextView) convertView.findViewById(R.id.exclude_listitem_name)).setSelected(true);
      return convertView;
    }
  };

  private class IncludeOnClickListener implements OnClickListener {
    private String item = null;
    private BaseAdapter adapter = null;

    public IncludeOnClickListener(String item, BaseAdapter adapter) {
      this.item = item;
      this.adapter = adapter;
    }

    public void onClick(View v) {
      OBackupExcludeList.removeItem(_sharedPreferences, item);
      adapter.notifyDataSetChanged();
    }
  };

}
