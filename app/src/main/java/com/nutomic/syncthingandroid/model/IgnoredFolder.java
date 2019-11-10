package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

public class IgnoredFolder {
    public String id = "";
    public String label = "";
    public String time = "";

    /**
     * Returns the folder label, or the first characters of the ID if the label is empty.
     */
    public String getDisplayLabel() {
        return (TextUtils.isEmpty(label))
                ? id.substring(0, 7)
                : label;
    }
}
