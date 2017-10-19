package com.nutomic.syncthingandroid.util;

import android.content.Context;

import com.nutomic.syncthingandroid.R;

/**
 * Device compression attribute helper. This unifies operations between string values as expected by
 * Syncthing with string values as displayed to the user and int ordinals as expected by the dialog
 * click interface.
 */
public enum Compression {
    NONE(0),
    METADATA(1),
    ALWAYS(2);

    private final int index;

    Compression(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public String getValue(Context context) {
        return context.getResources().getStringArray(R.array.compress_values)[index];
    }

    public String getTitle(Context context) {
        return context.getResources().getStringArray(R.array.compress_entries)[index];
    }

    public static Compression fromIndex(int index) {
        switch (index) {
            case 0:
                return NONE;
            case 2:
                return ALWAYS;
            default:
                return METADATA;
        }
    }

    public static Compression fromValue(Context context, String value) {
        int index = 0;
        String[] values = context.getResources().getStringArray(R.array.compress_values);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                index = i;
            }
        }

        return fromIndex(index);
    }
}
