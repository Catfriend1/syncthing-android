package com.nutomic.syncthingandroid.model;

public class FolderStatus {
    public String error = "";
    public long errors = 0;
    public long globalBytes = 0;
    public long globalDeleted = 0;
    public long globalDirectories = 0;
    public long globalFiles = 0;
    public long globalSymlinks = 0;
    public long globalTotalItems = 0;
    public boolean ignorePatterns = false;
    public long inSyncBytes = 0;
    public long inSyncFiles = 0;
    public String invalid = "";
    public long localBytes = 0;
    public long localDeleted = 0;
    public long localDirectories = 0;
    public long localFiles = 0;
    public long localSymlinks = 0;
    public long localTotalItems = 0;
    public long needBytes = 0;
    public long needDeletes = 0;
    public long needDirectories = 0;
    public long needFiles = 0;
    public long needSymlinks = 0;
    public long needTotalItems = 0;
    public long pullErrors = 0;
    public long receiveOnlyChangedBytes = 0;
    public long receiveOnlyChangedDeletes = 0;
    public long receiveOnlyChangedDirectories = 0;
    public long receiveOnlyChangedFiles = 0;
    public long receiveOnlyChangedSymlinks = 0;
    public long receiveOnlyTotalItems = 0;
    public long sequence = 0;
    public String state = "idle";
    public String stateChanged = "";                        // "2019-11-12T20:59:04.9882373Z"
    public long version = 0;
    public String watchError = "";
}
