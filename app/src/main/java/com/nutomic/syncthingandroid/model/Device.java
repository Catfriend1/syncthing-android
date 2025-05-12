package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;
import android.util.Log;

import com.google.common.io.BaseEncoding;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import com.nutomic.syncthingandroid.util.Luhn;

import java.lang.System;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Device {
    public String deviceID;
    public String name = "";
    public List<String> addresses;
    public List<String> allowedNetworks;
    public String compression = "metadata";
    public String certName;
    public String introducedBy = "";
    public boolean introducer = false;
    public boolean paused = false;
    public List<IgnoredFolder> ignoredFolders;
    public boolean autoAcceptFolders = false;
    public Integer maxRecvKbps = 0;
    public Integer maxSendKbps = 0;

    // Since v1.12.0
    // See https://github.com/syncthing/syncthing/pull/7055
    public boolean untrusted = false;

    // Since v1.25.0
    public Integer numConnections = 0;

    private static final String TAG = "Device";

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
                byte[] bytesIn = deviceID.getBytes();
                byte[] res = new byte[52];
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
                    byte[] decodedStr = BaseEncoding.base32().decode(deviceID + "====");
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

    /**
     * Returns if device.addresses elements are correctly formatted.
     * See https://docs.syncthing.net/users/config.html#device-element for what is correct.
     * It can be improved in the future because it doesn't catch all mistakes a user can do.
     * It catches the most common mistakes.
     */
    public Boolean checkDeviceAddresses() {
        if (!testCheckDeviceAddress()) {
            Log.e(TAG, "checkDeviceAddresses: testCheckDeviceAddress unit test failed");
            return false;
        }
        if (this.addresses == null) {
            return false;
        }
        for (String address : this.addresses) {
            // Log.v(TAG, "address=(" + address + ")");
            if (!checkDeviceAddress(address)) {
                return false;
            }
        }
        return true;
    }

    private Boolean checkDeviceAddress(String address) {
        if (address.equals("dynamic")) {
            return true;
        }

        if (!address.matches("^tcp([46])?://.*$") &&
                !address.matches("^relay://.*$") &&
                !address.matches("^quic([46])?://.*$")) {
            // Log.v(TAG, "Invalid protocol.");
            return false;
        }

        // Separate protocol from address and port.
        String[] addressSplit = address.split("://");
        if (addressSplit.length == 1) {
            // There's only the protocol given, nothing more.
            // Log.v(TAG, "There's only the protocol given, nothing more.");
            return false;
        }
        else if (addressSplit.length == 2) {
            // Log.v(TAG, "strProtocol=" + addressSplit[0]);
            if (addressSplit[0].matches("^tcp.*$")) {
                return checkDeviceAddressTcp(addressSplit[1]);
            } else if (addressSplit[0].matches("^relay.*$")) {
                return checkDeviceAddressRelay(addressSplit[1]);
            } else if (addressSplit[0].matches("^quic.*$")) {
                return checkDeviceAddressTcp(addressSplit[1]);
            }
        }

        // Protocol is given more than one time. Will match "tcp://tcp://"
        return false;
    }

    private Boolean checkDeviceAddressTcp(String address) {
        // Check if the address ends with ":" or "]:"
        if (address.endsWith(":") ||
                address.endsWith("]:")) {
            // The address ends with ":". Will match "tcp://myserver:"
            // Log.v(TAG, "address ends with \":\" or \"]:\". Will match \"tcp://myserver:\".");
            return false;
        }

        // Check if there's a "hostname:port" number given in the part after "://".
        String[] hostnamePortSplit = address.split(":");
        if (hostnamePortSplit.length > 1) {
            // Check if the hostname or IP address given before the port is empty.
            if (TextUtils.isEmpty(hostnamePortSplit[0])) {
                // Empty hostname or IP address before the port. Will match "tcp://:4000"
                // Log.v(TAG, "Empty hostname or IP address before the port.");
                return false;
            }

            // Check if there's a port number given in the last part.
            String potentialPort = hostnamePortSplit[hostnamePortSplit.length-1].split("/")[0];
            if (!potentialPort.endsWith("]")) {
                // It's not the end of an IPv6 address and likely a port number.
                // Log.v(TAG, "... potentialPort=(" + potentialPort + ")");
                Integer port = 0;
                try {
                    port = Integer.parseInt(potentialPort);
                } catch (Exception e) {
                }
                if (port < 1 || port > 65535) {
                    // Invalid port number.
                    // Log.v(TAG, "Invalid port number.");
                    return false;
                }
            }
        }

        return true;
    }

    private Boolean checkDeviceAddressRelay(String address) {
        // Check if the address ends with ":" or "]:"
        if (address.endsWith(":") ||
                address.endsWith("]:")) {
            // The address ends with ":". Will match "relay://myserver:"
            // Log.v(TAG, "address ends with \":\" or \"]:\". Will match \"relay://myserver:\".");
            return false;
        }

        // Check if there's a "hostname:port" number given in the part after "://".
        String[] hostnamePortSplit = address.split(":");
        if (hostnamePortSplit.length > 1) {
            // Check if the hostname or IP address given before the port is empty.
            if (TextUtils.isEmpty(hostnamePortSplit[0])) {
                // Empty hostname or IP address before the port. Will match "relay://:4000"
                // Log.v(TAG, "Empty hostname or IP address before the port.");
                return false;
            }

            // Check if there's a port number given in the last part.
            String potentialPort = hostnamePortSplit[1].split("/")[0];
            if (!potentialPort.endsWith("]")) {
                // It's not the end of an IPv6 address and likely a port number.
                // Log.v(TAG, "... potentialPort=(" + potentialPort + ")");
                Integer port = 0;
                try {
                    port = Integer.parseInt(potentialPort);
                } catch (Exception e) {
                }
                if (port < 1 || port > 65535) {
                    // Invalid port number.
                    // Log.v(TAG, "Invalid port number.");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    private <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    private Boolean testCheckDeviceAddress() {
        Boolean failSuccess = true;

        // Positive Syntax
        failSuccess = failSuccess && checkDeviceAddress("tcp://127.0.0.1:4000");
        failSuccess = failSuccess && checkDeviceAddress("tcp4://127.0.0.1:4000");
        failSuccess = failSuccess && checkDeviceAddress("tcp6://127.0.0.1:4000");
        failSuccess = failSuccess && checkDeviceAddress("tcp4://127.0.0.1");
        failSuccess = failSuccess && checkDeviceAddress("tcp://[2001:db8::23:42]");
        failSuccess = failSuccess && checkDeviceAddress("tcp://[2001:db8::23:42]:12345");
        failSuccess = failSuccess && checkDeviceAddress("tcp://myserver");
        failSuccess = failSuccess && checkDeviceAddress("tcp://myserver:12345");
        failSuccess = failSuccess && checkDeviceAddress("relay://stlocal:22067/?id=ID-REDACTED&pingInterval=30s&networkTimeout=2m0s&sessionLimitBps=0&globalLimitBps=0&statusAddr=:22070&providedBy=REDACTED");
        failSuccess = failSuccess && checkDeviceAddress("relay://stlocal:22067");
        failSuccess = failSuccess && checkDeviceAddress("quic://127.0.0.1");
        failSuccess = failSuccess && checkDeviceAddress("quic://127.0.0.1:24000");
        failSuccess = failSuccess && checkDeviceAddress("quic4://127.0.0.1:24000");
        failSuccess = failSuccess && checkDeviceAddress("quic6://127.0.0.1:24000");

        // Negative Syntax
        failSuccess = failSuccess && !checkDeviceAddress("tcp://myserver:");
        failSuccess = failSuccess && !checkDeviceAddress("tcp8://127.0.0.1");
        failSuccess = failSuccess && !checkDeviceAddress("udp4://127.0.0.1");
        return failSuccess;
    }


}
