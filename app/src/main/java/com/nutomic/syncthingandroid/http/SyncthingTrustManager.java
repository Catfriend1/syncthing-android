package com.nutomic.syncthingandroid.http;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/*
 * TrustManager checking against the local Syncthing instance's https public key.
 *
 * Based on http://stackoverflow.com/questions/16719959#16759793
 */
class SyncthingTrustManager implements X509TrustManager {

    private static final String TAG = "SyncthingTrustManager";

    private final File mHttpsCertPath;

    SyncthingTrustManager(File httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    @Override
    @SuppressLint("TrustAllX509TrustManager")
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
    }

    /**
     * Verifies certs against public key of the local syncthing instance
     */
    @Override
    public void checkServerTrusted(X509Certificate[] certs,
                                   String authType) throws CertificateException {
        InputStream is = null;
        try {
            is = new FileInputStream(mHttpsCertPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate) cf.generateCertificate(is);
            for (X509Certificate cert : certs) {
                cert.verify(ca.getPublicKey());
            }
        } catch (FileNotFoundException | NoSuchAlgorithmException | InvalidKeyException |
                NoSuchProviderException | SignatureException e) {
            throw new CertificateException("Untrusted Certificate!", e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }
    }
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
