package com.nutomic.syncthingandroid.service;

import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class to send notifications via ntfy.sh
 */
public class NtfyNotifier {

    private static final String TAG = "NtfyNotifier";
    private static final String NTFY_BASE_URL = "https://ntfy.sh/";
    private static final int TIMEOUT_MS = 5000;
    
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Sends a notification to a device via ntfy.sh
     * 
     * @param deviceId The device ID to send the notification to (used as topic)
     * @param title The notification title
     * @param body The notification body
     */
    public static void sendNotification(String deviceId, String title, String body) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(NTFY_BASE_URL + deviceId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Title", title);
                connection.setRequestProperty("Content-Type", "text/plain");
                
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bodyBytes.length);
                
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bodyBytes);
                    os.flush();
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Notification sent successfully to device: " + deviceId);
                } else {
                    Log.w(TAG, "Failed to send notification to device: " + deviceId + 
                            ", response code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending notification to device: " + deviceId, e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}
