package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.mindrot.jbcrypt.BCrypt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

/**
 * Provides direct access to the config.xml file in the file system.
 * This class should only be used if the syncthing API is not available (usually during startup).
 */
public class ConfigXml {

    private static final String TAG = "ConfigXml";

    private Boolean ENABLE_VERBOSE_LOG = false;

    public class OpenConfigException extends RuntimeException {
    }

    /**
     * Compares devices by name, uses the device ID as fallback if the name is empty
     */
    private final static Comparator<Device> DEVICES_COMPARATOR = (lhs, rhs) -> {
        String lhsName = lhs.name != null && !lhs.name.isEmpty() ? lhs.name : lhs.deviceID;
        String rhsName = rhs.name != null && !rhs.name.isEmpty() ? rhs.name : rhs.deviceID;
        return lhsName.compareTo(rhsName);
    };

    /**
     * Compares folders by labels, uses the folder ID as fallback if the label is empty
     */
    private final static Comparator<Folder> FOLDERS_COMPARATOR = (lhs, rhs) -> {
        String lhsLabel = lhs.label != null && !lhs.label.isEmpty() ? lhs.label : lhs.id;
        String rhsLabel = rhs.label != null && !rhs.label.isEmpty() ? rhs.label : rhs.id;
        return lhsLabel.compareTo(rhsLabel);
    };

    public interface OnResultListener1<T> {
        void onResult(T t);
    }

    private static final int FOLDER_ID_APPENDIX_LENGTH = 4;

    private final Context mContext;

    private final File mConfigFile;

    private Document mConfig;

    public ConfigXml(Context context) {
        mContext = context;
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(context);
        mConfigFile = Constants.getConfigFile(mContext);
    }

    public void loadConfig() throws OpenConfigException {
        parseConfig();
        updateIfNeeded();
    }

    /**
     * This should run within an AsyncTask as it can cause a full CPU load
     * for more than 30 seconds on older phone hardware.
     */
    public void generateConfig() throws OpenConfigException, SyncthingRunnable.ExecutableNotFoundException {
        // Create new secret keys and config.
        Log.i(TAG, "(Re)Generating keys and config.");
        new SyncthingRunnable(mContext, SyncthingRunnable.Command.generate).run(true);
        parseConfig();
        Boolean changed = false;

        // Set local device name.
        Log.i(TAG, "Starting syncthing to retrieve local device id.");
        String localDeviceID = getLocalDeviceIDandStoreToPref();
        if (!TextUtils.isEmpty(localDeviceID)) {
            changed = changeLocalDeviceName(localDeviceID) || changed;
        }

        // Set default folder to the "camera" folder: path and name
        changed = changeDefaultFolder() || changed;

        // Save changes if we made any.
        if (changed) {
            saveChanges();
        }
    }

    private String getLocalDeviceIDfromPref() {
        String localDeviceID = PreferenceManager.getDefaultSharedPreferences(mContext).getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        if (TextUtils.isEmpty(localDeviceID)) {
            Log.d(TAG, "getLocalDeviceIDfromPref: Local device ID unavailable, trying to retrieve it from syncthing ...");
            try {
                localDeviceID = getLocalDeviceIDandStoreToPref();
            } catch (SyncthingRunnable.ExecutableNotFoundException e) {
                Log.e(TAG, "getLocalDeviceIDfromPref: Failed to execute syncthing core");
            }
            if (TextUtils.isEmpty(localDeviceID)) {
                Log.e(TAG, "getLocalDeviceIDfromPref: Local device ID unavailable");
            }
        }
        return localDeviceID;
    }

    private String getLocalDeviceIDandStoreToPref() throws SyncthingRunnable.ExecutableNotFoundException {
        String logOutput = new SyncthingRunnable(mContext, SyncthingRunnable.Command.deviceid).run(true);
        String localDeviceID = logOutput.replace("\n", "");

        // Verify that local device ID is correctly formatted.
        Device localDevice = new Device();
        localDevice.deviceID = localDeviceID;
        if (!localDevice.checkDeviceID()) {
            Log.w(TAG, "getLocalDeviceIDandStoreToPref: Syncthing core returned a bad formatted device ID \"" + localDeviceID + "\"");
            return "";
        }

        // Store local device ID to pref. This saves us expensive calls to the syncthing binary if we need it later.
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
            .putString(Constants.PREF_LOCAL_DEVICE_ID, localDeviceID)
            .apply();
        Log.v(TAG, "getLocalDeviceIDandStoreToPref: Cached local device ID \"" + localDeviceID + "\"");
        return localDeviceID;
    }

    private void parseConfig() {
        if (!mConfigFile.canRead() && !Util.fixAppDataPermissions(mContext)) {
            Log.w(TAG, "Failed to open config file '" + mConfigFile + "'");
            throw new OpenConfigException();
        }
        try {
            FileInputStream inputStream = new FileInputStream(mConfigFile);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            InputSource inputSource = new InputSource(inputStreamReader);
            inputSource.setEncoding("UTF-8");
            DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbfactory.newDocumentBuilder();
            if (ENABLE_VERBOSE_LOG) {
                Log.v(TAG, "Parsing config file '" + mConfigFile + "'");
            }
            mConfig = db.parse(inputSource);
            inputStream.close();
            if (ENABLE_VERBOSE_LOG) {
                Log.v(TAG, "Successfully parsed config file");
            }
        } catch (SAXException | ParserConfigurationException | IOException e) {
            Log.w(TAG, "Failed to parse config file '" + mConfigFile + "'", e);
            throw new OpenConfigException();
        }
    }

    public URL getWebGuiUrl() {
        String urlProtocol = Constants.osSupportsTLS12() ? "https" : "http";
        try {
            return new URL(urlProtocol + "://" + getGuiElement().getElementsByTagName("address").item(0).getTextContent());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse web interface URL", e);
        }
    }

    public Integer getWebGuiBindPort() {
        try {
            Gui gui = new Gui();
            gui.address = getGuiElement().getElementsByTagName("address").item(0).getTextContent();
            return Integer.parseInt(gui.getBindPort());
        } catch (Exception e) {
            Log.w(TAG, "getWebGuiBindPort: Failed with exception: ", e);
            return Constants.DEFAULT_WEBGUI_TCP_PORT;
        }
    }

    public String getApiKey() {
        return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
    }

    public String getUserName() {
        return getGuiElement().getElementsByTagName("user").item(0).getTextContent();
    }

    /**
     * Updates the config file.
     * Sets ignorePerms flag to true on every folder, force enables TLS, sets the
     * username/password, and disables weak hash checking.
     */
    @SuppressWarnings("SdCardPath")
    private void updateIfNeeded() {
        boolean changed = false;

        /* Perform one-time migration tasks on syncthing's config file when coming from an older config version. */
        changed = migrateSyncthingOptions() || changed;

        /* Get refs to important config objects */
        NodeList folders = mConfig.getDocumentElement().getElementsByTagName("folder");

        /* Section - folders */
        for (int i = 0; i < folders.getLength(); i++) {
            Element r = (Element) folders.item(i);
            // Set ignorePerms attribute.
            if (!r.hasAttribute("ignorePerms") ||
                    !Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
                Log.i(TAG, "Set 'ignorePerms' on folder " + r.getAttribute("id"));
                r.setAttribute("ignorePerms", Boolean.toString(true));
                changed = true;
            }

            // Set 'hashers' (see https://github.com/syncthing/syncthing-android/issues/384) on the
            // given folder.
            changed = setConfigElement(r, "hashers", "1") || changed;
        }

        /* Section - GUI */
        Element gui = getGuiElement();
        if (gui == null) {
            throw new OpenConfigException();
        }

        // Platform-specific: Force REST API and Web UI access to use TLS 1.2 or not.
        Boolean forceHttps = Constants.osSupportsTLS12();
        if (!gui.hasAttribute("tls") ||
                Boolean.parseBoolean(gui.getAttribute("tls")) != forceHttps) {
            gui.setAttribute("tls", Boolean.toString(forceHttps));
            changed = true;
        }

        // Set user to "syncthing"
        changed = setConfigElement(gui, "user", "syncthing") || changed;

        // Set password to the API key
        Node password = gui.getElementsByTagName("password").item(0);
        if (password == null) {
            password = mConfig.createElement("password");
            gui.appendChild(password);
        }
        String apikey = getApiKey();
        String pw = password.getTextContent();
        boolean passwordOk;
        try {
            passwordOk = !TextUtils.isEmpty(pw) && BCrypt.checkpw(apikey, pw);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Malformed password", e);
            passwordOk = false;
        }
        if (!passwordOk) {
            Log.i(TAG, "Updating password");
            password.setTextContent(BCrypt.hashpw(apikey, BCrypt.gensalt(4)));
            changed = true;
        }

        /* Section - options */
        // Disable weak hash benchmark for faster startup.
        // https://github.com/syncthing/syncthing/issues/4348
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);
        if (options == null) {
            throw new OpenConfigException();
        }
        changed = setConfigElement(options, "weakHashSelectionMethod", "never") || changed;

        /* Dismiss "fsWatcherNotification" according to https://github.com/syncthing/syncthing-android/pull/1051 */
        NodeList childNodes = options.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("unackedNotificationID")) {
                switch (getContentOrDefault(node, "")) {
                    case "crAutoEnabled":
                    case "crAutoDisabled":
                    case "fsWatcherNotification":
                        Log.i(TAG, "Remove found unackedNotificationID '" + node + "'.");
                        options.removeChild(node);
                        changed = true;
                        break;
                }
            }
        }

        // Disable "startBrowser" because it applies to desktop environments and cannot start a mobile browser app.
        Options defaultOptions = new Options();
        changed = setConfigElement(options, "startBrowser", Boolean.toString(defaultOptions.startBrowser)) || changed;

        // Save changes if we made any.
        if (changed) {
            saveChanges();
        }
    }

    /**
     * Updates syncthing options to a version specific target setting in the config file.
     * Used for one-time config migration from a lower syncthing version to the current version.
     * Enables filesystem watcher.
     * Returns if changes to the config have been made.
     */
    private boolean migrateSyncthingOptions() {
        Folder defaultFolder = new Folder();

        /* Read existing config version */
        int iConfigVersion = Integer.parseInt(mConfig.getDocumentElement().getAttribute("version"));
        int iOldConfigVersion = iConfigVersion;
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, "Found existing config version " + Integer.toString(iConfigVersion));
        }

        /* Check if we have to do manual migration from version X to Y */
        if (iConfigVersion == 27) {
            /* fsWatcher transition - https://github.com/syncthing/syncthing/issues/4882 */
            Log.i(TAG, "Migrating config version " + Integer.toString(iConfigVersion) + " to 28 ...");

            /* Enable fsWatcher for all folders */
            NodeList folders = mConfig.getDocumentElement().getElementsByTagName("folder");
            for (int i = 0; i < folders.getLength(); i++) {
                Element r = (Element) folders.item(i);

                // Enable "fsWatcherEnabled" attribute and set default delay.
                Log.i(TAG, "Set 'fsWatcherEnabled', 'fsWatcherDelayS' on folder " + r.getAttribute("id"));
                r.setAttribute("fsWatcherEnabled", Boolean.toString(defaultFolder.fsWatcherEnabled));
                r.setAttribute("fsWatcherDelayS", Integer.toString(defaultFolder.fsWatcherDelayS));
            }

            /**
             * Set config version to 28 after manual config migration
             * This prevents "unackedNotificationID" getting populated
             * with the fsWatcher GUI notification.
             */
            iConfigVersion = 28;
        }

        if (iConfigVersion != iOldConfigVersion) {
            mConfig.getDocumentElement().setAttribute("version", Integer.toString(iConfigVersion));
            Log.i(TAG, "New config version is " + Integer.toString(iConfigVersion));
            return true;
        } else {
            return false;
        }
    }

    private Boolean getAttributeOrDefault(final Element element, String attribute, Boolean defaultValue) {
        return element.hasAttribute(attribute) ? Boolean.parseBoolean(element.getAttribute(attribute)) : defaultValue;
    }

    private Integer getAttributeOrDefault(final Element element, String attribute, Integer defaultValue) {
        try {
            return element.hasAttribute(attribute) ? Integer.parseInt(element.getAttribute(attribute)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getAttributeOrDefault(final Element element, String attribute, String defaultValue) {
        return element.hasAttribute(attribute) ? element.getAttribute(attribute) : defaultValue;
    }

    private Boolean getContentOrDefault(final Node node, Boolean defaultValue) {
        return (node == null) ? defaultValue : Boolean.parseBoolean(node.getTextContent());
    }

    private Integer getContentOrDefault(final Node node, Integer defaultValue) {
        try {
            return (node == null) ? defaultValue : Integer.parseInt(node.getTextContent());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Float getContentOrDefault(final Node node, Float defaultValue) {
        try {
            return (node == null) ? defaultValue : Float.parseFloat(node.getTextContent());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getContentOrDefault(final Node node, String defaultValue) {
         return (node == null) ? defaultValue : node.getTextContent();
    }

    public List<Folder> getFolders() {
        String localDeviceID = getLocalDeviceIDfromPref();
        List<Folder> folders = new ArrayList<>();
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            Folder folder = new Folder();
            folder.id = getAttributeOrDefault(r, "id", "");
            folder.label = getAttributeOrDefault(r, "label", folder.label);
            folder.path = getAttributeOrDefault(r, "path", "");
            folder.type = getAttributeOrDefault(r, "type", Constants.FOLDER_TYPE_SEND_RECEIVE);
            folder.autoNormalize = getAttributeOrDefault(r, "autoNormalize", folder.autoNormalize);
            folder.fsWatcherDelayS =getAttributeOrDefault(r, "fsWatcherDelayS", folder.fsWatcherDelayS);
            folder.fsWatcherEnabled = getAttributeOrDefault(r, "fsWatcherEnabled", folder.fsWatcherEnabled);
            folder.ignorePerms = getAttributeOrDefault(r, "ignorePerms", folder.ignorePerms);
            folder.rescanIntervalS = getAttributeOrDefault(r, "rescanIntervalS", folder.rescanIntervalS);

            folder.copiers = getContentOrDefault(r.getElementsByTagName("copiers").item(0), folder.copiers);
            folder.hashers = getContentOrDefault(r.getElementsByTagName("hashers").item(0), folder.hashers);
            folder.order = getContentOrDefault(r.getElementsByTagName("order").item(0), folder.order);
            folder.paused = getContentOrDefault(r.getElementsByTagName("paused").item(0), folder.paused);
            folder.ignoreDelete = getContentOrDefault(r.getElementsByTagName("ignoreDelete").item(0), folder.ignoreDelete);
            folder.copyOwnershipFromParent = getContentOrDefault(r.getElementsByTagName("copyOwnershipFromParent").item(0), folder.copyOwnershipFromParent);
            folder.modTimeWindowS = getContentOrDefault(r.getElementsByTagName("modTimeWindowS").item(0), folder.modTimeWindowS);

            // Devices
            /*
            <device id="[DEVICE_ID]" introducedBy=""/>
            */
            NodeList nodeDevices = r.getElementsByTagName("device");
            for (int j = 0; j < nodeDevices.getLength(); j++) {
                Element elementDevice = (Element) nodeDevices.item(j);
                Device device = new Device();
                device.deviceID = getAttributeOrDefault(elementDevice, "id", "");

                // Exclude self.
                if (!TextUtils.isEmpty(device.deviceID) && !device.deviceID.equals(localDeviceID)) {
                    device.introducedBy = getAttributeOrDefault(elementDevice, "introducedBy", device.introducedBy);
                    // Log.v(TAG, "getFolders: deviceID=" + device.deviceID + ", introducedBy=" + device.introducedBy);
                    folder.addDevice(device);
                }
            }

            // MinDiskFree
            /*
            <minDiskFree unit="MB">5</minDiskFree>
            */
            folder.minDiskFree = new Folder.MinDiskFree();
            Element elementMinDiskFree = (Element) r.getElementsByTagName("minDiskFree").item(0);
            if (elementMinDiskFree != null) {
                folder.minDiskFree.unit = getAttributeOrDefault(elementMinDiskFree, "unit", folder.minDiskFree.unit);
                folder.minDiskFree.value = getContentOrDefault(elementMinDiskFree, folder.minDiskFree.value);
            }
            // Log.v(TAG, "folder.minDiskFree.unit=" + folder.minDiskFree.unit + ", folder.minDiskFree.value=" + folder.minDiskFree.value);

            // Versioning
            /*
            <versioning></versioning>
            <versioning type="trashcan">
                <param key="cleanoutDays" val="90"></param>
            </versioning>
            */
            folder.versioning = new Folder.Versioning();
            Element elementVersioning = (Element) r.getElementsByTagName("versioning").item(0);
            if (elementVersioning != null) {
                folder.versioning.type = getAttributeOrDefault(elementVersioning, "type", "");
                NodeList nodeVersioningParam = elementVersioning.getElementsByTagName("param");
                for (int j = 0; j < nodeVersioningParam.getLength(); j++) {
                    Element elementVersioningParam = (Element) nodeVersioningParam.item(j);
                    folder.versioning.params.put(
                            getAttributeOrDefault(elementVersioningParam, "key", ""),
                            getAttributeOrDefault(elementVersioningParam, "val", "")
                    );
                    /*
                    Log.v(TAG, "folder.versioning.type=" + folder.versioning.type +
                            ", key=" + getAttributeOrDefault(elementVersioningParam, "key", "") +
                            ", val=" + getAttributeOrDefault(elementVersioningParam, "val", "")
                    );
                    */
                }
            }

            // For testing purposes only.
            // Log.v(TAG, "folder.label=" + folder.label + "/" +"folder.type=" + folder.type + "/" + "folder.paused=" + folder.paused);
            folders.add(folder);
        }
        Collections.sort(folders, FOLDERS_COMPARATOR);
        return folders;
    }

    public void addFolder(final Folder folder) {
        Log.v(TAG, "addFolder: folder.id=" + folder.id);
        Node nodeConfig = mConfig.getDocumentElement();
        Node nodeFolder = mConfig.createElement("folder");
        nodeConfig.appendChild(nodeFolder);
        Element elementFolder = (Element) nodeFolder;
        elementFolder.setAttribute("id", folder.id);
        updateFolder(folder);
    }

    public void updateFolder(final Folder folder) {
        String localDeviceID = getLocalDeviceIDfromPref();
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            if (folder.id.equals(getAttributeOrDefault(r, "id", ""))) {
                // Found folder node to update.
                r.setAttribute("label", folder.label);
                r.setAttribute("path", folder.path);
                r.setAttribute("type", folder.type);
                r.setAttribute("autoNormalize", Boolean.toString(folder.autoNormalize));
                r.setAttribute("fsWatcherDelayS", Integer.toString(folder.fsWatcherDelayS));
                r.setAttribute("fsWatcherEnabled", Boolean.toString(folder.fsWatcherEnabled));
                r.setAttribute("ignorePerms", Boolean.toString(folder.ignorePerms));
                r.setAttribute("rescanIntervalS", Integer.toString(folder.rescanIntervalS));

                setConfigElement(r, "copiers", Integer.toString(folder.copiers));
                setConfigElement(r, "hashers", Integer.toString(folder.hashers));
                setConfigElement(r, "order", folder.order);
                setConfigElement(r, "paused", Boolean.toString(folder.paused));
                setConfigElement(r, "ignoreDelete", Boolean.toString(folder.ignoreDelete));
                setConfigElement(r, "copyOwnershipFromParent", Boolean.toString(folder.copyOwnershipFromParent));
                setConfigElement(r, "modTimeWindowS", Integer.toString(folder.modTimeWindowS));

                // Update devices that share this folder.
                // Pass 1: Remove all devices below that folder in XML except the local device.
                NodeList nodeDevices = r.getElementsByTagName("device");
                for (int j = nodeDevices.getLength() - 1; j >= 0; j--) {
                    Element elementDevice = (Element) nodeDevices.item(j);
                    if (!getAttributeOrDefault(elementDevice, "id", "").equals(localDeviceID)) {
                        Log.v(TAG, "updateFolder: nodeDevices: Removing deviceID=" + getAttributeOrDefault(elementDevice, "id", ""));
                        removeChildElementFromTextNode(r, elementDevice);
                    }
                }

                // Pass 2: Add devices below that folder from the POJO model.
                final List<Device> devices = folder.getDevices();
                for (Device device : devices) {
                    Log.v(TAG, "updateFolder: nodeDevices: Adding deviceID=" + device.deviceID);
                    Node nodeDevice = mConfig.createElement("device");
                    r.appendChild(nodeDevice);
                    Element elementDevice = (Element) nodeDevice;
                    elementDevice.setAttribute("id", device.deviceID);
                    elementDevice.setAttribute("introducedBy", device.introducedBy);
                }

                // minDiskFree
                if (folder.minDiskFree != null) {
                    // Pass 1: Remove all minDiskFree nodes from XML (usually one)
                    Element elementMinDiskFree = (Element) r.getElementsByTagName("minDiskFree").item(0);
                    if (elementMinDiskFree != null) {
                        Log.v(TAG, "updateFolder: nodeMinDiskFree: Removing minDiskFree node");
                        removeChildElementFromTextNode(r, elementMinDiskFree);
                    }

                    // Pass 2: Add minDiskFree node from the POJO model to XML.
                    Node nodeMinDiskFree = mConfig.createElement("minDiskFree");
                    r.appendChild(nodeMinDiskFree);
                    elementMinDiskFree = (Element) nodeMinDiskFree;
                    elementMinDiskFree.setAttribute("unit", folder.minDiskFree.unit);
                    setConfigElement(r, "minDiskFree", Float.toString(folder.minDiskFree.value));
                }

                // Versioning
                // Pass 1: Remove all versioning nodes from XML (usually one)
                /*
                NodeList nlVersioning = r.getElementsByTagName("versioning");
                for (int j = nlVersioning.getLength() - 1; j >= 0; j--) {
                    Log.v(TAG, "updateFolder: nodeVersioning: Removing versioning node");
                    removeChildElementFromTextNode(r, (Element) nlVersioning.item(j));
                }
                */
                Element elementVersioning = (Element) r.getElementsByTagName("versioning").item(0);
                if (elementVersioning != null) {
                    Log.v(TAG, "updateFolder: nodeVersioning: Removing versioning node");
                    removeChildElementFromTextNode(r, elementVersioning);
                }

                // Pass 2: Add versioning node from the POJO model to XML.
                Node nodeVersioning = mConfig.createElement("versioning");
                r.appendChild(nodeVersioning);
                elementVersioning = (Element) nodeVersioning;
                if (!TextUtils.isEmpty(folder.versioning.type)) {
                    elementVersioning.setAttribute("type", folder.versioning.type);
                    for (Map.Entry<String, String> param : folder.versioning.params.entrySet()) {
                        Log.v(TAG, "updateFolder: nodeVersioning: Adding param key=" + param.getKey() + ", val=" + param.getValue());
                        Node nodeParam = mConfig.createElement("param");
                        elementVersioning.appendChild(nodeParam);
                        Element elementParam = (Element) nodeParam;
                        elementParam.setAttribute("key", param.getKey());
                        elementParam.setAttribute("val", param.getValue());
                    }
                }

                break;
            }
        }
    }

    public void removeFolder(String folderId) {
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = nodeFolders.getLength() - 1; i >= 0; i--) {
            Element r = (Element) nodeFolders.item(i);
            if (folderId.equals(getAttributeOrDefault(r, "id", ""))) {
                // Found folder node to remove.
                Log.v(TAG, "removeFolder: Removing folder node, folderId=" + folderId);
                removeChildElementFromTextNode((Element) r.getParentNode(), r);
                break;
            }
        }
    }

    public void setFolderPause(String folderId, Boolean paused) {
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            if (getAttributeOrDefault(r, "id", "").equals(folderId))
            {
                setConfigElement(r, "paused", Boolean.toString(paused));
                break;
            }
        }
    }

    /**
     * Gets ignore list for given folder.
     */
    public void getFolderIgnoreList(Folder folder, OnResultListener1<FolderIgnoreList> listener) {
        FolderIgnoreList folderIgnoreList = new FolderIgnoreList();
        File file;
        FileInputStream fileInputStream = null;
        try {
            file = new File(folder.path, Constants.FILENAME_STIGNORE);
            if (file.exists()) {
                fileInputStream = new FileInputStream(file);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, "UTF-8");
                byte[] data = new byte[(int) file.length()];
                fileInputStream.read(data);
                folderIgnoreList.ignore = new String(data, "UTF-8").split("\n");
            } else {
                // File not found.
                Log.w(TAG, "getFolderIgnoreList: File missing " + file);
                /**
                 * Don't fail as the file might be expectedly missing when users didn't
                 * set ignores in the past storyline of that folder.
                 */
            }
        } catch (IOException e) {
            Log.e(TAG, "getFolderIgnoreList: Failed to read '" + folder.path + "/" + Constants.FILENAME_STIGNORE + "' #1", e);
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "getFolderIgnoreList: Failed to read '" + folder.path + "/" + Constants.FILENAME_STIGNORE + "' #2", e);
            }
        }
        listener.onResult(folderIgnoreList);
    }

    /**
     * Stores ignore list for given folder.
     */
    public void postFolderIgnoreList(Folder folder, String[] ignore) {
        File file;
        FileOutputStream fileOutputStream = null;
        try {
            file = new File(folder.path, Constants.FILENAME_STIGNORE);
            if (!file.exists()) {
                file.createNewFile();
            }
            fileOutputStream = new FileOutputStream(file);
            // Log.v(TAG, "postFolderIgnoreList: Writing " + Constants.FILENAME_STIGNORE + " content=" + TextUtils.join("\n", ignore));
            fileOutputStream.write(TextUtils.join("\n", ignore).getBytes("UTF-8"));
            fileOutputStream.flush();
        } catch (IOException e) {
            /**
             * This will happen on external storage folders which exist outside the
             * "/Android/data/[package_name]/files" folder on Android 5+.
             */
            Log.w(TAG, "postFolderIgnoreList: Failed to write '" + folder.path + "/" + Constants.FILENAME_STIGNORE + "' #1", e);
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "postFolderIgnoreList: Failed to write '" + folder.path + "/" + Constants.FILENAME_STIGNORE + "' #2", e);
            }
        }
    }

    public List<Device> getDevices(Boolean includeLocal) {
        String localDeviceID = getLocalDeviceIDfromPref();
        List<Device> devices = new ArrayList<>();

        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                Device device = new Device();
                device.compression = getAttributeOrDefault(r, "compression", device.compression);
                device.deviceID = getAttributeOrDefault(r, "id", "");
                device.introducedBy = getAttributeOrDefault(r, "introducedBy", device.introducedBy);
                device.introducer =  getAttributeOrDefault(r, "introducer", device.introducer);
                device.name = getAttributeOrDefault(r, "name", device.name);
                device.paused = getContentOrDefault(r.getElementsByTagName("paused").item(0), device.paused);

                // Addresses
                /*
                <device ...>
                    <address>dynamic</address>
                    <address>tcp4://192.168.1.67:2222</address>
                </device>
                */
                device.addresses = new ArrayList<>();
                NodeList nodeAddresses = r.getElementsByTagName("address");
                for (int j = 0; j < nodeAddresses.getLength(); j++) {
                    String address = getContentOrDefault(nodeAddresses.item(j), "");
                    device.addresses.add(address);
                    // Log.v(TAG, "getDevices: address=" + address);
                }

                // For testing purposes only.
                // Log.v(TAG, "getDevices: device.name=" + device.name + "/" +"device.id=" + device.deviceID + "/" + "device.paused=" + device.paused);

                // Exclude self if requested.
                Boolean isLocalDevice = !TextUtils.isEmpty(device.deviceID) && device.deviceID.equals(localDeviceID);
                if (includeLocal || !isLocalDevice) {
                    devices.add(device);
                }
            }
        }
        Collections.sort(devices, DEVICES_COMPARATOR);
        return devices;
    }

    public void addDevice(final Device device) {
        Log.v(TAG, "addDevice: deviceID=" + device.deviceID);
        Node nodeConfig = mConfig.getDocumentElement();
        Node nodeDevice = mConfig.createElement("device");
        nodeConfig.appendChild(nodeDevice);
        Element elementDevice = (Element) nodeDevice;
        elementDevice.setAttribute("id", device.deviceID);
        updateDevice(device);
    }

    public void updateDevice(final Device device) {
        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (device.deviceID.equals(getAttributeOrDefault(r, "id", ""))) {
                    // Found device to update.
                    r.setAttribute("compression", device.compression);
                    r.setAttribute("introducedBy", device.introducedBy);
                    r.setAttribute("introducer", Boolean.toString(device.introducer));
                    r.setAttribute("name", device.name);

                    setConfigElement(r, "paused", Boolean.toString(device.paused));

                    // Addresses
                    // Pass 1: Remove all addresses in XML.
                    NodeList nodeAddresses = r.getElementsByTagName("address");
                    for (int j = nodeAddresses.getLength() - 1; j >= 0; j--) {
                        Element elementAddress = (Element) nodeAddresses.item(j);
                        Log.v(TAG, "updateDevice: nodeAddresses: Removing address=" + getContentOrDefault(elementAddress, ""));
                        removeChildElementFromTextNode(r, elementAddress);
                    }

                    // Pass 2: Add addresses from the POJO model.
                    if (device.addresses != null) {
                        for (String address : device.addresses) {
                            Log.v(TAG, "updateDevice: nodeAddresses: Adding address=" + address);
                            Node nodeAddress = mConfig.createElement("address");
                            r.appendChild(nodeAddress);
                            Element elementAddress = (Element) nodeAddress;
                            elementAddress.setTextContent(address);
                        }
                    }

                    break;
                }
            }
        }
    }

    public void removeDevice(String deviceID) {
        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (deviceID.equals(getAttributeOrDefault(r, "id", ""))) {
                    // Found device to remove.
                    Log.v(TAG, "removeDevice: Removing device node, deviceID=" + deviceID);
                    removeChildElementFromTextNode((Element) r.getParentNode(), r);
                    break;
                }
            }
        }
    }

    public Gui getGui() {
        Element elementGui = (Element) mConfig.getDocumentElement().getElementsByTagName("gui").item(0);
        Gui gui = new Gui();
        if (elementGui == null) {
            Log.e(TAG, "getGui: elementGui == null. Returning defaults.");
            return gui;
        }

        gui.debugging = getAttributeOrDefault(elementGui, "debugging", gui.debugging);
        gui.enabled = getAttributeOrDefault(elementGui, "enabled", gui.enabled);
        gui.useTLS = getAttributeOrDefault(elementGui, "tls", gui.useTLS);

        gui.address = getContentOrDefault(elementGui.getElementsByTagName("address").item(0), gui.address);
        gui.user = getContentOrDefault(elementGui.getElementsByTagName("user").item(0), gui.user);
        gui.password = getContentOrDefault(elementGui.getElementsByTagName("password").item(0), "");
        gui.apiKey = getContentOrDefault(elementGui.getElementsByTagName("apikey").item(0), "");
        gui.theme = getContentOrDefault(elementGui.getElementsByTagName("theme").item(0), gui.theme);
        gui.insecureAdminAccess = getContentOrDefault(elementGui.getElementsByTagName("insecureAdminAccess").item(0), gui.insecureAdminAccess);
        gui.insecureAllowFrameLoading = getContentOrDefault(elementGui.getElementsByTagName("insecureAllowFrameLoading").item(0), gui.insecureAllowFrameLoading);
        gui.insecureSkipHostCheck = getContentOrDefault(elementGui.getElementsByTagName("insecureSkipHostCheck").item(0), gui.insecureSkipHostCheck);
        return gui;
    }

    public void updateGui(final Gui gui) {
        Element elementGui = (Element) mConfig.getDocumentElement().getElementsByTagName("gui").item(0);
        if (elementGui == null) {
            Log.e(TAG, "updateGui: elementGui == null");
            return;
        }

        elementGui.setAttribute("debugging", Boolean.toString(gui.debugging));
        elementGui.setAttribute("enabled", Boolean.toString(gui.enabled));
        elementGui.setAttribute("tls", Boolean.toString(gui.useTLS));

        setConfigElement(elementGui, "address", gui.address);
        setConfigElement(elementGui, "user", gui.user);
        setConfigElement(elementGui, "password", gui.password);
        setConfigElement(elementGui, "apikey", gui.apiKey);
        setConfigElement(elementGui, "theme", gui.theme);
        setConfigElement(elementGui, "insecureAdminAccess", Boolean.toString(gui.insecureAdminAccess));
        setConfigElement(elementGui, "insecureAllowFrameLoading", Boolean.toString(gui.insecureAllowFrameLoading));
        setConfigElement(elementGui, "insecureSkipHostCheck", Boolean.toString(gui.insecureSkipHostCheck));
    }

    public Options getOptions() {
        Element elementOptions = (Element) mConfig.getDocumentElement().getElementsByTagName("options").item(0);
        Options options = new Options();
        if (elementOptions == null) {
            Log.e(TAG, "getOptions: elementOptions == null. Returning defaults.");
            return options;
        }
        // options.listenAddresses
        // options.globalAnnounceServers
        options.globalAnnounceEnabled = getContentOrDefault(elementOptions.getElementsByTagName("globalAnnounceEnabled").item(0), options.globalAnnounceEnabled);
        options.localAnnounceEnabled = getContentOrDefault(elementOptions.getElementsByTagName("localAnnounceEnabled").item(0), options.localAnnounceEnabled);
        options.localAnnouncePort = getContentOrDefault(elementOptions.getElementsByTagName("localAnnouncePort").item(0), options.localAnnouncePort);
        options.localAnnounceMCAddr = getContentOrDefault(elementOptions.getElementsByTagName("localAnnounceMCAddr").item(0), "");
        options.maxSendKbps = getContentOrDefault(elementOptions.getElementsByTagName("maxSendKbps").item(0), options.maxSendKbps);
        options.maxRecvKbps = getContentOrDefault(elementOptions.getElementsByTagName("maxRecvKbps").item(0), options.maxRecvKbps);
        options.reconnectionIntervalS = getContentOrDefault(elementOptions.getElementsByTagName("reconnectionIntervalS").item(0), options.reconnectionIntervalS);
        options.relaysEnabled = getContentOrDefault(elementOptions.getElementsByTagName("relaysEnabled").item(0), options.relaysEnabled);
        options.relayReconnectIntervalM = getContentOrDefault(elementOptions.getElementsByTagName("relayReconnectIntervalM").item(0), options.relayReconnectIntervalM);
        options.startBrowser = getContentOrDefault(elementOptions.getElementsByTagName("startBrowser").item(0), options.startBrowser);
        options.natEnabled = getContentOrDefault(elementOptions.getElementsByTagName("natEnabled").item(0), options.natEnabled);
        options.natLeaseMinutes = getContentOrDefault(elementOptions.getElementsByTagName("natLeaseMinutes").item(0), options.natLeaseMinutes);
        options.natRenewalMinutes = getContentOrDefault(elementOptions.getElementsByTagName("natRenewalMinutes").item(0), options.natRenewalMinutes);
        options.natTimeoutSeconds = getContentOrDefault(elementOptions.getElementsByTagName("natTimeoutSeconds").item(0), options.natTimeoutSeconds);
        options.urAccepted = getContentOrDefault(elementOptions.getElementsByTagName("urAccepted").item(0), options.urAccepted);
        options.urUniqueId = getContentOrDefault(elementOptions.getElementsByTagName("urUniqueId").item(0), "");
        options.urURL = getContentOrDefault(elementOptions.getElementsByTagName("urURL").item(0), options.urURL);
        options.urPostInsecurely = getContentOrDefault(elementOptions.getElementsByTagName("urPostInsecurely").item(0), options.urPostInsecurely);
        options.urInitialDelayS = getContentOrDefault(elementOptions.getElementsByTagName("urInitialDelayS").item(0), options.urInitialDelayS);
        options.restartOnWakeup = getContentOrDefault(elementOptions.getElementsByTagName("restartOnWakeup").item(0), options.restartOnWakeup);
        options.autoUpgradeIntervalH = getContentOrDefault(elementOptions.getElementsByTagName("autoUpgradeIntervalH").item(0), options.autoUpgradeIntervalH);
        options.upgradeToPreReleases = getContentOrDefault(elementOptions.getElementsByTagName("upgradeToPreReleases").item(0), options.upgradeToPreReleases);
        options.keepTemporariesH = getContentOrDefault(elementOptions.getElementsByTagName("keepTemporariesH").item(0), options.keepTemporariesH);
        options.cacheIgnoredFiles = getContentOrDefault(elementOptions.getElementsByTagName("cacheIgnoredFiles").item(0), options.cacheIgnoredFiles);
        options.progressUpdateIntervalS = getContentOrDefault(elementOptions.getElementsByTagName("progressUpdateIntervalS").item(0), options.progressUpdateIntervalS);
        options.limitBandwidthInLan = getContentOrDefault(elementOptions.getElementsByTagName("limitBandwidthInLan").item(0), options.limitBandwidthInLan);
        options.releasesURL = getContentOrDefault(elementOptions.getElementsByTagName("releasesURL").item(0), options.releasesURL);
        // alwaysLocalNets
        options.overwriteRemoteDeviceNamesOnConnect = getContentOrDefault(elementOptions.getElementsByTagName("overwriteRemoteDeviceNamesOnConnect").item(0), options.overwriteRemoteDeviceNamesOnConnect);
        options.tempIndexMinBlocks = getContentOrDefault(elementOptions.getElementsByTagName("tempIndexMinBlocks").item(0), options.tempIndexMinBlocks);
        options.defaultFolderPath = getContentOrDefault(elementOptions.getElementsByTagName("defaultFolderPath").item(0), "");
        options.setLowPriority = getContentOrDefault(elementOptions.getElementsByTagName("setLowPriority").item(0), options.setLowPriority);
        // minHomeDiskFree
        options.maxConcurrentScans = getContentOrDefault(elementOptions.getElementsByTagName("maxConcurrentScans").item(0), options.maxConcurrentScans);
        options.unackedNotificationID = getContentOrDefault(elementOptions.getElementsByTagName("unackedNotificationID").item(0), options.unackedNotificationID);
        options.crashReportingURL = getContentOrDefault(elementOptions.getElementsByTagName("crashReportingURL").item(0), options.crashReportingURL);
        options.crashReportingEnabled =getContentOrDefault(elementOptions.getElementsByTagName("crashReportingEnabled").item(0), options.crashReportingEnabled);
        options.stunKeepaliveStartS = getContentOrDefault(elementOptions.getElementsByTagName("stunKeepaliveStartS").item(0), options.stunKeepaliveStartS);
        options.stunKeepaliveMinS = getContentOrDefault(elementOptions.getElementsByTagName("stunKeepaliveMinS").item(0), options.stunKeepaliveMinS);
        options.stunServer = getContentOrDefault(elementOptions.getElementsByTagName("stunServer").item(0), options.stunServer);
        return options;
    }

    public void setDevicePause(String deviceId, Boolean paused) {
        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (getAttributeOrDefault(r, "id", "").equals(deviceId))
                {
                    setConfigElement(r, "paused", Boolean.toString(paused));
                    break;
                }
            }
        }
    }

    /**
     * If an indented child element is removed, whitespace and line break will be left by
     * Element.removeChild().
     * See https://stackoverflow.com/questions/14255064/removechild-how-to-remove-indent-too
     */
    private void removeChildElementFromTextNode(Element parentElement, Element childElement) {
        Node prev = childElement.getPreviousSibling();
        if (prev != null &&
                prev.getNodeType() == Node.TEXT_NODE &&
                prev.getNodeValue().trim().length() == 0) {
            parentElement.removeChild(prev);
        }
        parentElement.removeChild(childElement);
    }

    private boolean setConfigElement(Element parent, String tagName, String textContent) {
        Node element = parent.getElementsByTagName(tagName).item(0);
        if (element == null) {
            element = mConfig.createElement(tagName);
            parent.appendChild(element);
        }
        if (!textContent.equals(element.getTextContent())) {
            element.setTextContent(textContent);
            return true;
        }
        return false;
    }

    private Element getGuiElement() {
        return (Element) mConfig.getDocumentElement().getElementsByTagName("gui").item(0);
    }

    /**
     * Set device model name as device name for Syncthing.
     * We need to iterate through XML nodes manually, as mConfig.getDocumentElement() will also
     * return nested elements inside folder element. We have to check that we only rename the
     * device corresponding to the local device ID.
     * Returns if changes to the config have been made.
     */
    private boolean changeLocalDeviceName(String localDeviceID) {
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                if (((Element) node).getAttribute("id").equals(localDeviceID)) {
                    Log.i(TAG, "changeLocalDeviceName: Rename device ID " + localDeviceID + " to " + Build.MODEL);
                    ((Element) node).setAttribute("name", Build.MODEL);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Change default folder id to camera and path to camera folder path.
     * Returns if changes to the config have been made.
     */
    private boolean changeDefaultFolder() {
        Folder defaultFolder = new Folder();
        Element folder = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("folder").item(0);
        String deviceModel = Build.MODEL
                .replace(" ", "_")
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]", "");
        String defaultFolderId = deviceModel + "_" + generateRandomString(FOLDER_ID_APPENDIX_LENGTH);
        folder.setAttribute("label", mContext.getString(R.string.default_folder_label));
        folder.setAttribute("id", mContext.getString(R.string.default_folder_id, defaultFolderId));
        folder.setAttribute("path", Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        folder.setAttribute("type", Constants.FOLDER_TYPE_SEND_ONLY);
        folder.setAttribute("fsWatcherEnabled", Boolean.toString(defaultFolder.fsWatcherEnabled));
        folder.setAttribute("fsWatcherDelayS", Integer.toString(defaultFolder.fsWatcherDelayS));
        return true;
    }

    /**
     * Generates a random String with a given length
     */
    private String generateRandomString(int length) {
        char[] chars = "abcdefghjkmnpqrstuvwxyz123456789".toCharArray();
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            sb.append(chars[random.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    /**
     * Writes updated mConfig back to file.
     */
    public void saveChanges() {
        if (!mConfigFile.canWrite() && !Util.fixAppDataPermissions(mContext)) {
            Log.w(TAG, "Failed to save updated config. Cannot change the owner of the config file.");
            return;
        }

        Log.i(TAG, "Saving config file");
        File mConfigTempFile = Constants.getConfigTempFile(mContext);
        try {
            // Write XML header.
            FileOutputStream fileOutputStream = new FileOutputStream(mConfigTempFile);
            fileOutputStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes("UTF-8"));

            // Prepare Object-to-XML transform.
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            // Output XML body.
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            StreamResult streamResult = new StreamResult(new OutputStreamWriter(byteArrayOutputStream, "UTF-8"));
            transformer.transform(new DOMSource(mConfig), streamResult);
            byte[] outputBytes = byteArrayOutputStream.toByteArray();
            fileOutputStream.write(outputBytes);
            fileOutputStream.close();
        } catch (TransformerException e) {
            Log.w(TAG, "Failed to transform object to xml and save temporary config file", e);
            return;
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Failed to save temporary config file, FileNotFoundException", e);
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Failed to save temporary config file, UnsupportedEncodingException", e);
        } catch (IOException e) {
            Log.w(TAG, "Failed to save temporary config file, IOException", e);
        }
        try {
            mConfigTempFile.renameTo(mConfigFile);
        } catch (Exception e) {
            Log.w(TAG, "Failed to rename temporary config file to original file");
        }
    }
}
