package com.Odroid.ObackupCore;

import java.util.Arrays;
import java.util.HashSet;

import android.content.SharedPreferences;
import android.os.Environment;

public class OBackupExcludeList {

  public static void convertExcludeList(SharedPreferences sharedPreferences) {
    // <V1.11: dir/dir/dir
    // >V1.11: /mnt/sdcard/dir:/mnt/sdcard/dir:/mnt/sdcard/dir
    String excludeString = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    if (excludeString.equals("")) { return; }
    if (excludeString.startsWith("/")) { // It is already converted
      return;
    }
    String sdbase = sharedPreferences.getString(OBackupCoreConstants.BASEPATH, Environment
        .getExternalStorageDirectory().getPath());
    String[] excludeArray = excludeString.split("/");
    for (int i = 0; i < excludeArray.length; i++) {
      android.util.Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: Excluded: " + excludeArray[i]);
      if (!excludeArray[i].startsWith("/")) {
        excludeArray[i] = sdbase + "/" + excludeArray[i];
        android.util.Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: -> Excluded: " + excludeArray[i]);
      }
    }
    excludeString = implode(excludeArray, ":");
    SharedPreferences.Editor e = sharedPreferences.edit();
    e.putString(OBackupCoreConstants.EXCLUDE, excludeString);
    e.commit();
  }

  public static HashSet<String> getExcludeHashset(SharedPreferences sharedPreferences) {
    String excludeString = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    String sdbase = sharedPreferences.getString(OBackupCoreConstants.BASEPATH, Environment
        .getExternalStorageDirectory().getPath());
    String[] excludeArray = excludeString.split(":");
    if (excludeString.equals("")) {
      excludeArray = new String[0];
    }
    for (int i = 0; i < excludeArray.length; i++) {
      android.util.Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: Excluded: " + excludeArray[i]);
      if (!excludeArray[i].startsWith("/")) {
        excludeArray[i] = sdbase + "/" + excludeArray[i];
        android.util.Log.i(OBackupCoreConstants.LOGCAT, "0 Backup: -> Excluded: " + excludeArray[i]);
      }
    }
    HashSet<String> excludeSet = new HashSet<String>(Arrays.asList(excludeArray));
    return excludeSet;
  }

  private static String implode(String[] array, String delim) {
    String res = "";
    for (int i = 0; i < array.length; i++) {
      if (i != 0) {
        res += delim;
      }
      res += array[i];
    }
    return res;
  }

  public static int count(SharedPreferences sharedPreferences) {
    String excludeString = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    if (excludeString.equals("")) { return 0; }
    String[] excludeArray = excludeString.split(":");
    return excludeArray.length;
  }

  public static String getItem(SharedPreferences sharedPreferences, int position) {
    String[] excludeArray = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "").split(":");
    Arrays.sort(excludeArray);
    return excludeArray[position];
  }

  public static void removeItem(SharedPreferences sharedPreferences, String item) {
    String excludeString = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    String[] excludeArray = excludeString.split(":");
    if (excludeString.equals("")) {
      excludeArray = new String[0];
    }
    HashSet<String> excludeSet = new HashSet<String>(Arrays.asList(excludeArray));
    excludeSet.remove(item);
    String[] newExcludeArray = excludeSet.toArray(new String[0]);
    excludeString = implode(newExcludeArray, ":");
    SharedPreferences.Editor e = sharedPreferences.edit();
    e.putString(OBackupCoreConstants.EXCLUDE, excludeString);
    e.commit();
  }

  public static void addItem(SharedPreferences sharedPreferences, String item) {
    String excludeString = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    String[] excludeArray = excludeString.split(":");
    if (excludeString.equals("")) {
      excludeArray = new String[0];
    }
    HashSet<String> excludeSet = new HashSet<String>(Arrays.asList(excludeArray));
    excludeSet.add(item);
    String[] newExcludeArray = excludeSet.toArray(new String[0]);
    excludeString = implode(newExcludeArray, ":");
    SharedPreferences.Editor e = sharedPreferences.edit();
    e.putString(OBackupCoreConstants.EXCLUDE, excludeString);
    e.commit();
  }

  public static boolean contains(SharedPreferences sharedPreferences, String item) {
    String excludeString = sharedPreferences.getString(OBackupCoreConstants.EXCLUDE, "");
    String[] excludeArray = excludeString.split(":");
    if (excludeString.equals("")) {
      excludeArray = new String[0];
    }
    HashSet<String> excludeSet = new HashSet<String>(Arrays.asList(excludeArray));
    return excludeSet.contains(item);
  }
}
