package com.Odroid.ObackupCore;
interface OBackupCoreServiceInterface {
  void service_doStopBackup();
  int service_getPercent();
  boolean service_getDoingBackup();
  boolean service_getIsPlugged();
  String service_getCurrentFile();
}
