package com.Odroid.ObackupCore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;

import android.content.Context;
import android.os.Environment;
import android.preference.DialogPreference;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class OBackupCoreBasepath extends DialogPreference {
  @SuppressWarnings("unused")
  private static final String androidns = "http://schemas.android.com/apk/res/android";

  private String currentPath = "/";
  private String defaultPath = "/";

  private Context context;
  private View dialogView = null;

  public OBackupCoreBasepath(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    defaultPath = Environment.getExternalStorageDirectory().getPath();
    currentPath = defaultPath;
    // defaultPath = attrs.getAttributeValue(androidns, "defaultValue");
  }

  @Override
  protected View onCreateDialogView() {
    LayoutInflater inflater = (LayoutInflater) context
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    dialogView = inflater.inflate(R.layout.basepath, null, false);
    ((TextView) dialogView.findViewById(R.id.basepath_current))
        .setText(currentPath);
    ((TextView) dialogView.findViewById(R.id.basepath_current))
        .setSelected(true);
    adapter.setPath(new File(currentPath));
    ((ListView) dialogView.findViewById(R.id.basepath_list))
        .setAdapter(adapter);
    return dialogView;
  }

  @Override
  protected void onSetInitialValue(boolean restore, Object defaultValue) {
    super.onSetInitialValue(restore, defaultValue);
    if ((restore) && (shouldPersist())) {
      currentPath = getPersistedString(defaultPath);
    } else {
      currentPath = defaultPath;
    }
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    if (positiveResult == true) {
      super.onDialogClosed(positiveResult);
      if (shouldPersist()) {
        persistString(currentPath);
      }
    }
  }

  private void setCurrentPath(String path) {
    currentPath = path;
    if (dialogView != null) {
      ((TextView) dialogView.findViewById(R.id.basepath_current))
          .setText(currentPath);
    }
  }

  private BasepathAdapter adapter = new BasepathAdapter();

  private class BasepathAdapter extends BaseAdapter {
    File pathF = new File("/");
    ArrayList<String> dirs = new ArrayList<String>();

    public void setPath(File pathF) {
      if (!(pathF.exists() && pathF.isDirectory())) {
        pathF = new File("/");
      }
      setCurrentPath(pathF.getPath());
      this.pathF = pathF;
      dirs.clear();
      if (!pathF.getPath().equals("/")) {
        dirs.add("..");
      }
      File[] files = pathF.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            String item = file.getName();
            if (!item.endsWith("/")) {
              item += "/";
            }
            dirs.add(item);
          }
        }
      }
      notifyDataSetChanged();
    }

    public int getCount() {
      return dirs.size();
    }

    public String getItem(int position) {
      return dirs.get(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater) context
          .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.basepath_listitem, null, false);
        ((TextView) convertView.findViewById(R.id.basepath_listitem_name))
            .setSelected(true);
      }
      File dirF = new File(pathF, dirs.get(position));
      if (dirs.get(position).endsWith("..")) {
        dirF = pathF.getParentFile();
      }
      ((TextView) convertView.findViewById(R.id.basepath_listitem_name))
          .setText(dirs.get(position));
      convertView.setOnClickListener(new DirOnClickListener(dirF));
      return convertView;
    }
  }

  private class DirOnClickListener implements OnClickListener {
    private File dirF = null;

    public DirOnClickListener(File dirF) {
      this.dirF = dirF;
    }

    public void onClick(View v) {
      adapter.setPath(dirF);
    }
  }
}
