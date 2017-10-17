package com.nutomic.syncthingandroid.model;

public class Model {
    public long globalBytes;
    public long globalDeleted;
    public long globalFiles;
    public long localBytes;
    public long localDeleted;
    public long localFiles;
    public long inSyncBytes;
    public long inSyncFiles;
    public long needBytes;
    public long needFiles;
    public long needDeletes;
    public long needDirectories;
    public long needSymlinks;
    public String state;
    public String invalid;
}
