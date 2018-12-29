package com.nutomic.syncthingandroid.util;

// import android.util.Log;

public final class Luhn {

    private static final String TAG = "Luhn";

    /**
     * An alphabet is a string of N characters, representing the digits of a given
     * base N.
     */
    private static final String LUHN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     *
     * generate returns a check digit for the string s, which should be composed
     * of characters from the Alphabet a.
     * Doesn't follow the actual Luhn algorithm
     * see https://forum.syncthing.net/t/v0-9-0-new-node-id-format/478/6 for more.
     */
    public String generate (byte[] s) {
        int factor = 1;
        int sum = 0;
        int n = LUHN_ALPHABET.length();

        for (int i = 0; i < s.length; i++) {
            int codepoint = LUHN_ALPHABET.indexOf(s[i]);
            // Log.v(TAG, "generate: codepoint = " + codepoint);
            if (codepoint == -1) {
                // Error "Digit %q not valid in alphabet %q", s[i], a
                return null;
            }
            int addend = factor * codepoint;
            factor = (factor == 2 ? 1 : 2);
            addend = (addend / n) + (addend % n);
            sum += addend;
        }
        int remainder = sum % n;
        int checkCodepoint = (n - remainder) % n;
        // Log.v(TAG, "generate: checkCodepoint = " + checkCodepoint);
        return LUHN_ALPHABET.substring(checkCodepoint, checkCodepoint+1);
    }

}
