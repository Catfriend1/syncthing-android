package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;
import java.util.Locale;
// import android.util.Log;

import com.google.common.io.BaseEncoding;

import com.nutomic.syncthingandroid.util.Luhn;

import java.lang.System;
import java.util.Arrays;
import java.util.List;

public class Device {
    public String deviceID;
    public String name = "";
    public List<String> addresses;
    public String compression = "metadata";
    public String certName;
    public String introducedBy = "";
    public boolean introducer = false;
    public boolean paused = false;
    public List<PendingFolder> pendingFolders;
    public List<IgnoredFolder> ignoredFolders;

    /**
     * Relevant fields for Folder.List<Device> "shared-with-device" model,
     * handled by {@link ConfigRouter#updateFolder and ConfigXml#updateFolder}
     *  deviceID
     *  introducedBy
     * Example Tag
     *  <folder ...>
     *      <device id="[DEVICE_SHARING_THAT_FOLDER_WITH_US]" introducedBy="[INTRODUCER_DEVICE_THAT_TOLD_US_ABOUT_THE_FOLDER_OR_EMPTY_STRING]"></device>
     *  </folder>
     */

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    public String getDisplayName() {
        return (TextUtils.isEmpty(name))
                ? (TextUtils.isEmpty(deviceID) ? "" : deviceID.substring(0, 7))
                : name;
    }

    /**
     * Returns if a syncthing device ID is correctly formatted.
     */
    public Boolean checkDeviceID() {
        /**
         * See https://github.com/syncthing/syncthing/blob/master/lib/protocol/deviceid.go
         * how syncthing validates device IDs.
         * Old dirty way to check was: return deviceID.matches("^([A-Z0-9]{7}-){7}[A-Z0-9]{7}$");
         */
        String deviceID = new String(this.deviceID);

        // Trim "="
        deviceID = deviceID.replaceAll("=", "");

        // Convert to upper case.
        deviceID = deviceID.toUpperCase(Locale.ROOT);

        // untypeoify
        deviceID = deviceID.replaceAll("1", "I");
        deviceID = deviceID.replaceAll("0", "O");
        deviceID = deviceID.replaceAll("8", "B");

        // unchunkify
        deviceID = deviceID.replaceAll("-", "");
        deviceID = deviceID.replaceAll(" ", "");

        // Check length.
        switch(deviceID.length()) {
            case 0:
                // Log.w(TAG, "checkDeviceID: Empty device ID.");
                return false;
            case 56:
                // unluhnify(deviceID)
                byte bytesIn[] = deviceID.getBytes();
                byte res[] = new byte[52];
                for (int i = 0; i < 4; i++) {
                    byte[] p = Arrays.copyOfRange(bytesIn, i*(13+1), (i+1)*(13+1)-1);
                    System.arraycopy(p, 0, res, i*13, 13);

                    // Generate check digit.
                    Luhn luhn = new Luhn();
                    String checkRune = luhn.generate(p);
                    // Log.v(TAG, "checkDeviceID: luhn.generate(" + new String(p) + ") returned (" + checkRune + ")");
                    if (checkRune == null) {
                        // Log.w(TAG, "checkDeviceID: deviceID=(" + deviceID + "): invalid character");
                        return false;
                    }
                    if (!deviceID.substring((i+1)*14-1, (i+1)*14-1+1).equals(checkRune)) {
                        // Log.w(TAG, "checkDeviceID: deviceID=(" + deviceID + "): check digit incorrect");
                        return false;
                    }
                }
                deviceID = new String(res);
                // Log.v(TAG, "isDeviceIdValid: unluhnify(deviceID)=" + deviceID);
                // Fall-Through
            case 52:
                try {
                    BaseEncoding.base32().decode(deviceID + "====");
                    return true;
                } catch (IllegalArgumentException e) {
                    // Log.w(TAG, "checkDeviceID: deviceID=(" + deviceID + "): invalid character, base32 decode failed");
                    return false;
                }
            default:
                // Log.w(TAG, "checkDeviceID: Incorrect length (" + deviceID + ")");
                return false;
        }
    }
}
