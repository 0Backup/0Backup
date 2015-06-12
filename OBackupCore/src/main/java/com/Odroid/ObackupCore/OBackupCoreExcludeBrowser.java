package com.Odroid.ObackupCore;

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class OBackupCoreExcludeBrowser extends OBackupCoreBaseActivity {
  SharedPreferences _sharedPreferences = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.exclude_browser);
    _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    ((ListView) findViewById(R.id.excludebrowser_list))
        .setAdapter(browserExcludeAdapter);
    setPath(getPrefBasePath());
  }

  public void excludebrowser_done(View v) {
    finish();
  }

  private void setPath(String path) {
    ((TextView) findViewById(R.id.excludebrowser_dir)).setText(path);
    browserExcludeAdapter.setPath(new File(path));
  }

  private BrowserExcludeAdapter browserExcludeAdapter = new BrowserExcludeAdapter();

  private class BrowserExcludeAdapter extends BaseAdapter {
    private File pathF = new File("/");
    private ArrayList<String> listdir = new ArrayList<String>();

    public int getCount() {
      return listdir.size();
    }

    public String getItem(int position) {
      return listdir.get(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = inflater.inflate(R.layout.exclude_browser_listitem,
            parent, false);
        convertView.findViewById(R.id.excludebrowser_listitem_name)
            .setSelected(true);
      }
      String item = getItem(position);
      convertView.findViewById(R.id.excludebrowser_listitem_name)
          .setOnClickListener(new ItemOnClickListener(item, this));
      ((TextView) convertView.findViewById(R.id.excludebrowser_listitem_name))
          .setText(item);
      ((TextView) convertView.findViewById(R.id.excludebrowser_listitem_name))
          .setSelected(true);
      if (item.equals("..")) {
        convertView.findViewById(R.id.excludebrowser_listitem_exclude)
            .setVisibility(View.INVISIBLE);
        convertView.findViewById(R.id.excludebrowser_listitem_include)
            .setVisibility(View.INVISIBLE);
      } else {
        convertView.findViewById(R.id.excludebrowser_listitem_exclude)
            .setVisibility(View.VISIBLE);
        convertView.findViewById(R.id.excludebrowser_listitem_include)
            .setVisibility(View.VISIBLE);
        String itemPath = getPathFString() + item;
        if (OBackupExcludeList.contains(_sharedPreferences, itemPath)) {
          convertView.findViewById(R.id.excludebrowser_listitem_exclude)
              .setEnabled(false);
          convertView.findViewById(R.id.excludebrowser_listitem_include)
              .setEnabled(true);
          convertView.findViewById(R.id.excludebrowser_listitem_include)
              .setOnClickListener(new IncludeOnClickListener(item, this));
        } else {
          convertView.findViewById(R.id.excludebrowser_listitem_include)
              .setEnabled(false);
          convertView.findViewById(R.id.excludebrowser_listitem_exclude)
              .setEnabled(true);
          convertView.findViewById(R.id.excludebrowser_listitem_exclude)
              .setOnClickListener(new ExcludeOnClickListener(item, this));
        }
      }
      return convertView;
    }

    public void setPath(File pathF) {
      this.pathF = pathF;
      File[] files = pathF.listFiles();
      if (files == null) {
        files = new File[0];
      }
      listdir.clear();
      if (!pathF.getPath().equals(getPrefBasePath())) {
        listdir.add("..");
      }
      for (File fileF : files) {
        if (fileF.isDirectory()) {
          listdir.add(fileF.getName());
        }
      }
      notifyDataSetChanged();
    }

    public File getPathF() {
      return pathF;
    }

    public String getPathFString() {
      String pathFString = pathF.getPath();
      if (!pathFString.endsWith("/")) {
        pathFString += "/";
      }
      return pathFString;
    }
  };

  private class ItemOnClickListener implements OnClickListener {
    private String item = "";
    private BrowserExcludeAdapter adapter = null;

    public ItemOnClickListener(String item, BrowserExcludeAdapter adapter) {
      this.item = item;
      if (!this.item.endsWith("/")) {
        this.item += "/";
      }
      this.adapter = adapter;
    }

    public void onClick(View v) {
      String current = adapter.getPathFString();
      if (item.startsWith("..")) {
        OBackupCoreExcludeBrowser.this.setPath(adapter.getPathF().getParent());
      } else {
        String newPath = current + item;
        OBackupCoreExcludeBrowser.this.setPath(newPath);
      }
    }
  }

  private class ExcludeOnClickListener implements OnClickListener {
    private String item = null;
    private BrowserExcludeAdapter adapter = null;

    public ExcludeOnClickListener(String item, BrowserExcludeAdapter adapter) {
      this.item = item;
      this.adapter = adapter;
    }

    public void onClick(View v) {
      String current = adapter.getPathFString();
      String excludePath = current + item;
      Log.d(OBackupCoreConstants.LOGCAT, "current:" + current + " excludePath:"
          + excludePath);
      OBackupExcludeList.addItem(_sharedPreferences, excludePath);
      adapter.notifyDataSetChanged();
    }
  };

  private class IncludeOnClickListener implements OnClickListener {
    private String item = null;
    private BrowserExcludeAdapter adapter = null;

    public IncludeOnClickListener(String item, BrowserExcludeAdapter adapter) {
      this.item = item;
      this.adapter = adapter;
    }

    public void onClick(View v) {
      String parent = adapter.getPathFString();
      String excludePath = parent + item;
      OBackupExcludeList.removeItem(_sharedPreferences, excludePath);
      adapter.notifyDataSetChanged();
    }
  };
}
