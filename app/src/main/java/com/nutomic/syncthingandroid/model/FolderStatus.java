package com.nutomic.syncthingandroid.model;

public class FolderStatus {
    public long globalBytes;
    public long globalDeleted;
    public long globalDirectories;
    public long globalFiles;
    public long globalSymlinks;
    public boolean ignorePatterns;
    public String invalid;
    public long localBytes;
    public long localDeleted;
    public long localDirectories;
    public long localSymlinks;
    public long localFiles;
    public long inSyncBytes;
    public long inSyncFiles;
    public long needBytes;
    public long needDeletes;
    public long needDirectories;
    public long needFiles;
    public long needSymlinks;
    public long pullErrors;
    public long receiveOnlyChangedBytes;
    public long receiveOnlyChangedDeletes;
    public long receiveOnlyChangedDirectories;
    public long receiveOnlyChangedFiles;
    public long receiveOnlyChangedSymlinks;
    public long receiveOnlyTotalItems;
    public long sequence;
    public String state;
    public String stateChanged;
    public long version;
    public String error;
    public String watchError;
}
