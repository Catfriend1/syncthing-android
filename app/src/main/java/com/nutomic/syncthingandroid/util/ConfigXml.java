package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.model.IgnoredFolder;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.model.SharedWithDevice;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.util.FileUtils.ExternalStorageDirType;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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

        // Change default folder section.
        Element elementDefaults = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("defaults").item(0);
        if (elementDefaults != null) {
            Element elementDefaultFolder = (Element) elementDefaults
                    .getElementsByTagName("folder").item(0);
            if (elementDefaultFolder != null) {
                Element elementVersioning = (Element) elementDefaultFolder.getElementsByTagName("versioning").item(0);
                if (elementVersioning != null) {
                    elementVersioning.setAttribute("type", "trashcan");
                    Node nodeParam = mConfig.createElement("param");
                    elementVersioning.appendChild(nodeParam);
                    Element elementParam = (Element) nodeParam;
                    elementParam.setAttribute("key", "cleanoutDays");
                    elementParam.setAttribute("val", "14");
                    changed = true;
                }
            }
        }

        /* Section - GUI */
        Element gui = getGuiElement();
        if (gui == null) {
            throw new OpenConfigException();
        }

        // Set user to "syncthing"
        changed = setConfigElement(gui, "user", "syncthing") || changed;

        // Initialiaze password to the API key
        changed = setConfigElement(gui, "password",  BCrypt.hashpw(getApiKey(), BCrypt.gensalt(4))) || changed;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(Constants.PREF_WEBUI_PASSWORD, getApiKey())
                .apply();

        //  Allow debug and release to run in parallel for testing purposes.
        if (Constants.isDebuggable(mContext)) {
            // Set alternative gui listen port.
            changed = setConfigElement(gui, "address", "127.0.0.1:8385") || changed;

            // Set alternative data listen port.
            Element elementOptions = (Element) mConfig.getDocumentElement().getElementsByTagName("options").item(0);
            if (elementOptions != null) {
                changed = setConfigElement(elementOptions, "listenAddress", new String[]{
                                "tcp://:22001",
                                "dynamic+https://relays.syncthing.net/endpoint"
                        }
                ) || changed;
            }
        }

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
        Log.d(TAG, "getLocalDeviceIDandStoreToPref: Cached local device ID \"" + localDeviceID + "\"");
        return localDeviceID;
    }

    private void parseConfig() {
        if (!mConfigFile.canRead() && !Util.fixAppDataPermissions(mContext)) {
            Log.w(TAG, "Failed to open config file '" + mConfigFile + "'");
            throw new OpenConfigException();
        }
        try {
            FileInputStream inputStream = new FileInputStream(mConfigFile);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            InputSource inputSource = new InputSource(inputStreamReader);
            inputSource.setEncoding("UTF-8");
            DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbfactory.newDocumentBuilder();
            // LogV("Parsing config file '" + mConfigFile + "'");
            mConfig = db.parse(inputSource);
            inputStream.close();
            // LogV("Successfully parsed config file");
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

    public String getWebUIUsername() {
        Node userNode = getGuiElement().getElementsByTagName("user").item(0);
        if (userNode != null) {
            String username = userNode.getTextContent();
            return username != null ? username : "";
        }
        return "";
    }

    public String getWebUIPassword() {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getString(Constants.PREF_WEBUI_PASSWORD, "");
    }

    /**
     * Updates the config file.
     * Sets ignorePerms flag to true on every folder, force enables TLS, sets the
     * username/password.
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

            // Set 'hashers' on the given folder.
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

        /* Section - options */
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);
        if (options == null) {
            throw new OpenConfigException();
        }

        /* Dismiss "fsWatcherNotification" according to https://github.com/syncthing/syncthing-android/pull/1051 */
        NodeList childNodes = options.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("unackedNotificationID")) {
                String notificationType = getContentOrDefault(node, "");
                switch (notificationType) {
                    case "authenticationUserAndPassword":
                    case "crAutoEnabled":
                    case "crAutoDisabled":
                    case "fsWatcherNotification":
                        Log.i(TAG, "Remove found unackedNotificationID '" + notificationType + "'.");
                        options.removeChild(node);
                        changed = true;
                        break;
                }
            }
        }

        // Disable "startBrowser" because it applies to desktop environments and cannot start a mobile browser app.
        Options defaultOptions = new Options();
        changed = setConfigElement(options, "startBrowser", defaultOptions.startBrowser) || changed;

        /**
         * Disable Syncthing's NAT feature because it causes kernel oops on some buggy kernels.
         */
        if (Constants.osHasKernelBugIssue505()) {
            Boolean natEnabledChanged = setConfigElement(options, "natEnabled", false);
            if (natEnabledChanged) {
                Log.d(TAG, "Disabling NAT option because a buggy kernel was detected.");
                changed = true;
            }
        }

        // Add the "Syncthing Camera" folder if the user consented to use the feature.
        Boolean prefEnableSyncthingCamera =
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getBoolean(Constants.PREF_ENABLE_SYNCTHING_CAMERA, false);
        if (prefEnableSyncthingCamera) {
            changed = addSyncthingCameraFolder() || changed;
        }

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
        int iConfigVersion = getAttributeOrDefault(mConfig.getDocumentElement(), "version", 0);
        int iOldConfigVersion = iConfigVersion;

        /* Check if we have to do manual migration from version X to Y */
        if (iConfigVersion == 27) {
            /* fsWatcher transition */
            Log.i(TAG, "Migrating config version " + Integer.toString(iConfigVersion) + " to 28 ...");

            /* Enable fsWatcher for all folders */
            NodeList folders = mConfig.getDocumentElement().getElementsByTagName("folder");
            for (int i = 0; i < folders.getLength(); i++) {
                Element r = (Element) folders.item(i);

                // Enable "fsWatcherEnabled" attribute and set default delay.
                Log.i(TAG, "Set 'fsWatcherEnabled', 'fsWatcherDelayS' on folder " + r.getAttribute("id"));
                r.setAttribute("fsWatcherEnabled", Boolean.toString(defaultFolder.fsWatcherEnabled));
                r.setAttribute("fsWatcherDelayS", Float.toString(defaultFolder.fsWatcherDelayS));
            }

            /**
             * Set config version to 28 after manual config migration
             * This prevents "unackedNotificationID" getting populated
             * with the fsWatcher GUI notification.
             */
            iConfigVersion = 28;
        }

        if (iConfigVersion == iOldConfigVersion) {
            return false;
        }
        mConfig.getDocumentElement().setAttribute("version", Integer.toString(iConfigVersion));
        Log.i(TAG, "Old config version was " + Integer.toString(iOldConfigVersion) +
                ", new config version is " + Integer.toString(iConfigVersion));
        return true;
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

    private Float getAttributeOrDefault(final Element element, String attribute, Float defaultValue) {
        try {
            return element.hasAttribute(attribute) ? Float.parseFloat(element.getAttribute(attribute)) : defaultValue;
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

        // Prevent enumerating "<folder>" tags below "<default>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (!node.getNodeName().equals("folder")) {
                continue;
            }
            Element r = (Element) node;
            Folder folder = new Folder();
            folder.id = getAttributeOrDefault(r, "id", "");
            folder.label = getAttributeOrDefault(r, "label", folder.label);
            
            folder.path = getAttributeOrDefault(r, "path", "");
            if (folder.path.startsWith("~/")) {
                folder.path = folder.path.replaceFirst("^~", FileUtils.getSyncthingTildeAbsolutePath());
            }
            
            folder.type = getAttributeOrDefault(r, "type", Constants.FOLDER_TYPE_SEND_RECEIVE);
            folder.autoNormalize = getAttributeOrDefault(r, "autoNormalize", folder.autoNormalize);
            folder.fsWatcherDelayS = getAttributeOrDefault(r, "fsWatcherDelayS", folder.fsWatcherDelayS);
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
            folder.blockPullOrder = getContentOrDefault(r.getElementsByTagName("blockPullOrder").item(0), folder.blockPullOrder);
            folder.disableFsync = getContentOrDefault(r.getElementsByTagName("disableFsync").item(0), folder.disableFsync);
            folder.maxConcurrentWrites = getContentOrDefault(r.getElementsByTagName("maxConcurrentWrites").item(0), folder.maxConcurrentWrites);
            folder.maxConflicts = getContentOrDefault(r.getElementsByTagName("maxConflicts").item(0), folder.maxConflicts);
            folder.copyRangeMethod = getContentOrDefault(r.getElementsByTagName("copyRangeMethod").item(0), folder.copyRangeMethod);
            folder.caseSensitiveFS = getContentOrDefault(r.getElementsByTagName("caseSensitiveFS").item(0), folder.caseSensitiveFS);
            folder.syncOwnership = getContentOrDefault(r.getElementsByTagName("syncOwnership").item(0), folder.syncOwnership);
            folder.sendOwnership = getContentOrDefault(r.getElementsByTagName("sendOwnership").item(0), folder.sendOwnership);
            folder.syncXattrs = getContentOrDefault(r.getElementsByTagName("syncXattrs").item(0), folder.syncXattrs);
            folder.sendXattrs = getContentOrDefault(r.getElementsByTagName("sendXattrs").item(0), folder.sendXattrs);
            folder.filesystemType = getContentOrDefault(r.getElementsByTagName("filesystemType").item(0), folder.filesystemType);

            // Devices
            /*
            <device id="[DEVICE_ID]" introducedBy=""/>
            */
            NodeList nodeDevices = r.getElementsByTagName("device");
            for (int j = 0; j < nodeDevices.getLength(); j++) {
                Element elementDevice = (Element) nodeDevices.item(j);
                SharedWithDevice device = new SharedWithDevice();
                device.deviceID = getAttributeOrDefault(elementDevice, "id", "");

                // Exclude self.
                if (!TextUtils.isEmpty(device.deviceID) && !device.deviceID.equals(localDeviceID)) {
                    device.introducedBy = getAttributeOrDefault(elementDevice, "introducedBy", device.introducedBy);
                    // LogV("getFolders: deviceID=" + device.deviceID + ", introducedBy=" + device.introducedBy);
                    device.encryptionPassword = getContentOrDefault(elementDevice.getElementsByTagName("encryptionPassword").item(0), device.encryptionPassword);
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
            // LogV("folder.minDiskFree.unit=" + folder.minDiskFree.unit + ", folder.minDiskFree.value=" + folder.minDiskFree.value);

            // Versioning
            /*
            <versioning></versioning>
            <versioning type="trashcan">
                <param key="cleanoutDays" val="90"></param>
                <cleanupIntervalS>3600</cleanupIntervalS>
                <fsPath></fsPath>
                <fsType>basic</fsType>
            </versioning>
            */
            folder.versioning = new Folder.Versioning();
            Element elementVersioning = (Element) r.getElementsByTagName("versioning").item(0);
            if (elementVersioning != null) {
                folder.versioning.type = getAttributeOrDefault(elementVersioning, "type", "");
                folder.versioning.cleanupIntervalS = getContentOrDefault(elementVersioning.getElementsByTagName("cleanupIntervalS").item(0), 3600);
                folder.versioning.fsPath = getContentOrDefault(elementVersioning.getElementsByTagName("fsPath").item(0), "");
                folder.versioning.fsType = getContentOrDefault(elementVersioning.getElementsByTagName("fsType").item(0), "basic");
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
            // LogV("folder.label=" + folder.label + "/" +"folder.type=" + folder.type + "/" + "folder.paused=" + folder.paused);
            folders.add(folder);
        }
        Collections.sort(folders, FOLDERS_COMPARATOR);
        return folders;
    }

    public void addFolder(final Folder folder) {
        Log.d(TAG, "addFolder: folder.id=" + folder.id);
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
                r.setAttribute("fsWatcherDelayS", Float.toString(folder.fsWatcherDelayS));
                r.setAttribute("fsWatcherEnabled", Boolean.toString(folder.fsWatcherEnabled));
                r.setAttribute("ignorePerms", Boolean.toString(folder.ignorePerms));
                r.setAttribute("rescanIntervalS", Integer.toString(folder.rescanIntervalS));

                setConfigElement(r, "copiers", Integer.toString(folder.copiers));
                setConfigElement(r, "hashers", Integer.toString(folder.hashers));
                setConfigElement(r, "order", folder.order);
                setConfigElement(r, "paused", folder.paused);
                setConfigElement(r, "ignoreDelete", folder.ignoreDelete);
                setConfigElement(r, "copyOwnershipFromParent", folder.copyOwnershipFromParent);
                setConfigElement(r, "modTimeWindowS", Integer.toString(folder.modTimeWindowS));
                setConfigElement(r, "blockPullOrder", folder.blockPullOrder);
                setConfigElement(r, "disableFsync", folder.disableFsync);
                setConfigElement(r, "maxConcurrentWrites", Integer.toString(folder.maxConcurrentWrites));
                setConfigElement(r, "maxConflicts", Integer.toString(folder.maxConflicts));
                setConfigElement(r, "copyRangeMethod", folder.copyRangeMethod);
                setConfigElement(r, "caseSensitiveFS", folder.caseSensitiveFS);
                setConfigElement(r, "syncOwnership", folder.syncOwnership);
                setConfigElement(r, "sendOwnership", folder.sendOwnership);
                setConfigElement(r, "syncXattrs", folder.syncXattrs);
                setConfigElement(r, "sendXattrs", folder.sendXattrs);
                setConfigElement(r, "filesystemType", folder.filesystemType);

                // Update devices that share this folder.
                // Pass 1: Remove all devices below that folder in XML except the local device.
                NodeList nodeDevices = r.getElementsByTagName("device");
                for (int j = nodeDevices.getLength() - 1; j >= 0; j--) {
                    Element elementDevice = (Element) nodeDevices.item(j);
                    if (!getAttributeOrDefault(elementDevice, "id", "").equals(localDeviceID)) {
                        Log.d(TAG, "updateFolder: nodeDevices: Removing deviceID=" + getAttributeOrDefault(elementDevice, "id", ""));
                        removeChildElementFromTextNode(r, elementDevice);
                    }
                }

                // Pass 2: Add devices below that folder from the POJO model.
                final List<SharedWithDevice> devices = folder.getSharedWithDevices();
                for (SharedWithDevice device : devices) {
                    Log.d(TAG, "updateFolder: nodeDevices: Adding deviceID=" + device.deviceID);
                    Node nodeDevice = mConfig.createElement("device");
                    r.appendChild(nodeDevice);
                    Element elementDevice = (Element) nodeDevice;
                    elementDevice.setAttribute("id", device.deviceID);
                    elementDevice.setAttribute("introducedBy", device.introducedBy);
                    setConfigElement(elementDevice, "encryptionPassword", device.encryptionPassword);
                }

                // minDiskFree
                if (folder.minDiskFree != null) {
                    // Pass 1: Remove all minDiskFree nodes from XML (usually one)
                    Element elementMinDiskFree = (Element) r.getElementsByTagName("minDiskFree").item(0);
                    if (elementMinDiskFree != null) {
                        Log.d(TAG, "updateFolder: nodeMinDiskFree: Removing minDiskFree node");
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
                Element elementVersioning = (Element) r.getElementsByTagName("versioning").item(0);
                if (elementVersioning != null) {
                    Log.d(TAG, "updateFolder: nodeVersioning: Removing versioning node");
                    removeChildElementFromTextNode(r, elementVersioning);
                }

                // Pass 2: Add versioning node from the POJO model to XML.
                Node nodeVersioning = mConfig.createElement("versioning");
                r.appendChild(nodeVersioning);
                elementVersioning = (Element) nodeVersioning;
                if (!TextUtils.isEmpty(folder.versioning.type)) {
                    elementVersioning.setAttribute("type", folder.versioning.type);
                    setConfigElement(elementVersioning, "cleanupIntervalS", Integer.toString(folder.versioning.cleanupIntervalS));
                    setConfigElement(elementVersioning, "fsPath", folder.versioning.fsPath);
                    setConfigElement(elementVersioning, "fsType", folder.versioning.fsType);
                    for (Map.Entry<String, String> param : folder.versioning.params.entrySet()) {
                        Log.d(TAG, "updateFolder: nodeVersioning: Adding param key=" + param.getKey() + ", val=" + param.getValue());
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
                Log.d(TAG, "removeFolder: Removing folder node, folderId=" + folderId);
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
                setConfigElement(r, "paused", paused);
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
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                byte[] data = new byte[(int) file.length()];
                fileInputStream.read(data);
                folderIgnoreList.ignore = new String(data, StandardCharsets.UTF_8).split("\n");
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
            // LogV("postFolderIgnoreList: Writing " + Constants.FILENAME_STIGNORE + " content=" + TextUtils.join("\n", ignore));
            fileOutputStream.write(TextUtils.join("\n", ignore).getBytes(StandardCharsets.UTF_8));
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

        // Prevent enumerating "<device>" tags below "<defaults>", "<folder>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (!node.getNodeName().equals("device")) {
                continue;
            }
            Element r = (Element) node;
            Device device = new Device();
            device.compression = getAttributeOrDefault(r, "compression", device.compression);
            device.deviceID = getAttributeOrDefault(r, "id", "");
            device.introducedBy = getAttributeOrDefault(r, "introducedBy", device.introducedBy);
            device.introducer =  getAttributeOrDefault(r, "introducer", device.introducer);
            device.name = getAttributeOrDefault(r, "name", device.name);
            device.autoAcceptFolders = getContentOrDefault(r.getElementsByTagName("autoAcceptFolders").item(0), device.autoAcceptFolders);
            device.maxRecvKbps = getContentOrDefault(r.getElementsByTagName("maxRecvKbps").item(0), device.maxRecvKbps);
            device.maxSendKbps = getContentOrDefault(r.getElementsByTagName("maxSendKbps").item(0), device.maxSendKbps);
            device.paused = getContentOrDefault(r.getElementsByTagName("paused").item(0), device.paused);
            device.untrusted = getContentOrDefault(r.getElementsByTagName("untrusted").item(0), device.untrusted);
            device.numConnections = getContentOrDefault(r.getElementsByTagName("numConnections").item(0), device.numConnections);

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
                // LogV("getDevices: address=" + address);
            }

            // Allowed Networks
            /*
            <device ...>
                <allowedNetwork>192.168.0.0/24</allowedNetwork>
                <allowedNetwork>192.168.1.0/24</allowedNetwork>
            </device>
            */
            device.allowedNetworks = new ArrayList<>();
            NodeList nodeAllowedNetworks = r.getElementsByTagName("allowedNetwork");
            for (int j = 0; j < nodeAllowedNetworks.getLength(); j++) {
                String allowedNetwork = getContentOrDefault(nodeAllowedNetworks.item(j), "");
                device.allowedNetworks.add(allowedNetwork);
                // LogV("getDevices: allowedNetwork=" + allowedNetwork);
            }

            // ignoredFolders
            device.ignoredFolders = new ArrayList<>();
            NodeList nodeIgnoredFolders = r.getElementsByTagName("ignoredFolder");
            for (int j = 0; j < nodeIgnoredFolders.getLength(); j++) {
                Element elementIgnoredFolder = (Element) nodeIgnoredFolders.item(j);
                IgnoredFolder ignoredFolder = new IgnoredFolder();
                ignoredFolder.id = getAttributeOrDefault(elementIgnoredFolder, "id", ignoredFolder.id);
                ignoredFolder.label = getAttributeOrDefault(elementIgnoredFolder, "label", ignoredFolder.label);
                ignoredFolder.time = getAttributeOrDefault(elementIgnoredFolder, "time", ignoredFolder.time);

                // LogV("getDevices: ignoredFolder=[id=" + ignoredFolder.id + ", label=" + ignoredFolder.label + ", time=" + ignoredFolder.time + "]");
                device.ignoredFolders.add(ignoredFolder);
            }

            // For testing purposes only.
            // LogV("getDevices: device.name=" + device.name + "/" +"device.id=" + device.deviceID + "/" + "device.paused=" + device.paused);

            // Exclude self if requested.
            Boolean isLocalDevice = !TextUtils.isEmpty(device.deviceID) && device.deviceID.equals(localDeviceID);
            if (includeLocal || !isLocalDevice) {
                devices.add(device);
            }
        }
        Collections.sort(devices, DEVICES_COMPARATOR);
        return devices;
    }

    /**
     * Adds or updates a device identified by its device ID.
     */
    public void updateDevice(final Device device) {
        NodeList childNodes;
        boolean deviceExists = false;

        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (device.deviceID.equals(getAttributeOrDefault(r, "id", ""))) {
                    deviceExists = true;
                    break;
                }
            }
        }

        // If the device does not exist in config, add it.
        if (!deviceExists) {
            Log.d(TAG, "updateDevice: [addDevice] Adding deviceID='" + device.deviceID + "' to config ...");
            Node nodeConfig = mConfig.getDocumentElement();
            Node nodeDevice = mConfig.createElement("device");
            nodeConfig.appendChild(nodeDevice);
            Element elementDevice = (Element) nodeDevice;
            elementDevice.setAttribute("id", device.deviceID);
        }

        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        childNodes = mConfig.getDocumentElement().getChildNodes();
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

                    setConfigElement(r, "autoAcceptFolders", device.autoAcceptFolders);
                    setConfigElement(r, "paused", device.paused);
                    setConfigElement(r, "untrusted", device.untrusted);
                    setConfigElement(r, "numConnections", Integer.toString(device.numConnections));

                    // Addresses
                    // Pass 1: Remove all addresses in XML.
                    NodeList nodeAddresses = r.getElementsByTagName("address");
                    for (int j = nodeAddresses.getLength() - 1; j >= 0; j--) {
                        Element elementAddress = (Element) nodeAddresses.item(j);
                        Log.d(TAG, "updateDevice: nodeAddresses: Removing address=" + getContentOrDefault(elementAddress, ""));
                        removeChildElementFromTextNode(r, elementAddress);
                    }

                    // Pass 2: Add addresses from the POJO model.
                    if (device.addresses != null) {
                        for (String address : device.addresses) {
                            Log.d(TAG, "updateDevice: nodeAddresses: Adding address=" + address);
                            Node nodeAddress = mConfig.createElement("address");
                            r.appendChild(nodeAddress);
                            Element elementAddress = (Element) nodeAddress;
                            elementAddress.setTextContent(address);
                        }
                    }

                    // Allowed Networks
                    // Pass 1: Remove all allowed networks in XML.
                    NodeList nodeAllowedNetworks = r.getElementsByTagName("allowedNetwork");
                    for (int j = nodeAllowedNetworks.getLength() - 1; j >= 0; j--) {
                        Element elementAllowedNetwork = (Element) nodeAllowedNetworks.item(j);
                        Log.d(TAG, "updateDevice: nodeAllowedNetworks: Removing allowedNetwork=" + getContentOrDefault(elementAllowedNetwork, ""));
                        removeChildElementFromTextNode(r, elementAllowedNetwork);
                    }

                    // Pass 2: Add allowed networks from the POJO model.
                    if (device.allowedNetworks != null) {
                        for (String allowedNetwork : device.allowedNetworks) {
                            Log.d(TAG, "updateDevice: nodeAllowedNetworks: Adding allowedNetwork=" + allowedNetwork);
                            Node nodeAllowedNetwork = mConfig.createElement("allowedNetwork");
                            r.appendChild(nodeAllowedNetwork);
                            Element elementAllowedNetwork = (Element) nodeAllowedNetwork;
                            elementAllowedNetwork.setTextContent(allowedNetwork);
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
                    Log.d(TAG, "removeDevice: Removing device node, deviceID=" + deviceID);
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

        elementGui.setAttribute("enabled", Boolean.toString(gui.enabled));
        elementGui.setAttribute("tls", Boolean.toString(gui.useTLS));

        setConfigElement(elementGui, "address", gui.address);
        setConfigElement(elementGui, "user", gui.user);
        setConfigElement(elementGui, "password", gui.password);
        setConfigElement(elementGui, "apikey", gui.apiKey);
        setConfigElement(elementGui, "theme", gui.theme);
        setConfigElement(elementGui, "insecureAdminAccess", gui.insecureAdminAccess);
        setConfigElement(elementGui, "insecureAllowFrameLoading", gui.insecureAllowFrameLoading);
        setConfigElement(elementGui, "insecureSkipHostCheck", gui.insecureSkipHostCheck);
    }

    public Options getOptions() {
        Element elementOptions = (Element) mConfig.getDocumentElement().getElementsByTagName("options").item(0);
        Options options = new Options();
        if (elementOptions == null) {
            Log.e(TAG, "getOptions: elementOptions == null. Returning defaults.");
            return options;
        }

        // options.listenAddresses
        NodeList listenAddressNodes = elementOptions.getElementsByTagName("listenAddress");
        List<String> listenAddressesList = new ArrayList<>();
        for (int i = 0; i < listenAddressNodes.getLength(); i++) {
            Node addressNode = listenAddressNodes.item(i);
            String addressText = addressNode.getTextContent().trim();
            if (!addressText.isEmpty()) {
                listenAddressesList.add(addressText);
            }
        }
        options.listenAddresses = listenAddressesList.toArray(new String[0]);

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
        options.setLowPriority = getContentOrDefault(elementOptions.getElementsByTagName("setLowPriority").item(0), options.setLowPriority);
        // minHomeDiskFree
        options.maxFolderConcurrency = getContentOrDefault(elementOptions.getElementsByTagName("maxFolderConcurrency").item(0), options.maxFolderConcurrency);
        options.unackedNotificationID = getContentOrDefault(elementOptions.getElementsByTagName("unackedNotificationID").item(0), options.unackedNotificationID);
        options.crURL = getContentOrDefault(elementOptions.getElementsByTagName("crashReportingURL").item(0), options.crURL);
        options.crashReportingEnabled =getContentOrDefault(elementOptions.getElementsByTagName("crashReportingEnabled").item(0), options.crashReportingEnabled);
        options.stunKeepaliveStartS = getContentOrDefault(elementOptions.getElementsByTagName("stunKeepaliveStartS").item(0), options.stunKeepaliveStartS);
        options.stunKeepaliveMinS = getContentOrDefault(elementOptions.getElementsByTagName("stunKeepaliveMinS").item(0), options.stunKeepaliveMinS);
        options.stunServer = getContentOrDefault(elementOptions.getElementsByTagName("stunServer").item(0), options.stunServer);
        options.maxConcurrentIncomingRequestKiB = getContentOrDefault(elementOptions.getElementsByTagName("maxConcurrentIncomingRequestKiB").item(0), options.maxConcurrentIncomingRequestKiB);
        options.announceLanAddresses = getContentOrDefault(elementOptions.getElementsByTagName("announceLANAddresses").item(0), options.announceLanAddresses);
        options.sendFullIndexOnUpgrade = getContentOrDefault(elementOptions.getElementsByTagName("sendFullIndexOnUpgrade").item(0), options.sendFullIndexOnUpgrade);
        options.featureFlag = getContentOrDefault(elementOptions.getElementsByTagName("featureFlag").item(0), options.featureFlag);
        options.connectionLimitEnough = getContentOrDefault(elementOptions.getElementsByTagName("connectionLimitEnough").item(0), options.connectionLimitEnough);
        options.connectionLimitMax = getContentOrDefault(elementOptions.getElementsByTagName("connectionLimitMax").item(0), options.connectionLimitMax);
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
                    setConfigElement(r, "paused", paused);
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

    private boolean setConfigElement(Element parent, String tagName, Boolean newValue) {
        return setConfigElement(parent, tagName, Boolean.toString(newValue));
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

    private boolean setConfigElement(Element parent, String tagName, String[] textArray) {
        NodeList existingNodes = parent.getElementsByTagName(tagName);
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < existingNodes.getLength(); i++) {
            Node node = existingNodes.item(i);
            if (node.getParentNode() == parent) {
                toRemove.add(node);
            }
        }
        for (Node node : toRemove) {
            parent.removeChild(node);
        }
        for (String text : textArray) {
            Element newElement = mConfig.createElement(tagName);
            newElement.setTextContent(text);
            parent.appendChild(newElement);
        }
        return (!toRemove.isEmpty() || textArray.length > 0);
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
     * Adds a new folder pointing to the app-specific "Syncthing Camera"
     * directory if it hasn't been added to the config yet.
     * Returns if changes to the config have been made.
     */
    private boolean addSyncthingCameraFolder() {
        // LogV("addSyncthingCameraFolder: Examining config if folder already exists ...");
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        Boolean folderAlreadyPresentInConfig = false;
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            String folderId = getAttributeOrDefault(r, "id", "");
            if (!TextUtils.isEmpty(folderId) && folderId.equals(Constants.syncthingCameraFolderId)) {
                folderAlreadyPresentInConfig = true;
                break;
            }
        }
        if (folderAlreadyPresentInConfig) {
            // LogV("addSyncthingCameraFolder: Folder [" + Constants.syncthingCameraFolderId + "] already present in config.");
            return false;
        }

        // Get app specific directory, e.g. "/storage/emulated/0/Android/media/[PACKAGE_NAME]/Pictures".
        File storageDir = FileUtils.getExternalFilesDir(mContext, ExternalStorageDirType.INT_MEDIA, Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            Log.e(TAG, "addSyncthingCameraFolder: storageDir == null");
            return false;
        }

        // Prepare folder element.
        Folder folder = new Folder();
        folder.minDiskFree = new Folder.MinDiskFree();
        folder.id = Constants.syncthingCameraFolderId;
        folder.label = mContext.getString(R.string.default_syncthing_camera_folder_label);
        folder.path = storageDir.getAbsolutePath();

        // Add versioning.
        folder.versioning = new Folder.Versioning();
        folder.versioning.type = "trashcan";
        folder.versioning.params.put("cleanoutDays", Integer.toString(14));
        folder.versioning.cleanupIntervalS = 3600;
        folder.versioning.fsPath = "";
        folder.versioning.fsType = "basic";

        // Add folder to config.
        LogV("addSyncthingCameraFolder: Adding folder to config [" + folder.path + "]");
        addFolder(folder);
        return true;
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
            fileOutputStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8));

            // Prepare Object-to-XML transform.
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            // Output XML body.
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            StreamResult streamResult = new StreamResult(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8));
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

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
