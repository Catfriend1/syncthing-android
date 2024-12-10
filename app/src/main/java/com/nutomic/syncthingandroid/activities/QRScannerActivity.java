package com.nutomic.syncthingandroid.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.nutomic.syncthingandroid.R;

import java.util.List;

public class QRScannerActivity extends ThemedAppCompatActivity implements BarcodeCallback {

    static final String QR_RESULT_ARG = "QR_CODE";
    static Intent intent(Context context) {
        return new Intent(context, QRScannerActivity.class);
    }

    private final int RC_HANDLE_CAMERA_PERM = 888;

    private DecoratedBarcodeView barcodeView;
    private TextView cancleButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_qr_scanner);

        this.barcodeView = findViewById(R.id.bar_code_scanner_view);

        findViewById(R.id.cancel_button).setOnClickListener(view -> {
            pauseScanner();
            finish();
        });

        checkPermissionAndStartScanner();
    }

    @Override
    public void onResume() {
        checkPermissionAndStartScanner();
        super.onResume();
    }

    @Override
    public void onStop() {
        pauseScanner();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == RC_HANDLE_CAMERA_PERM) {
            if (grantResults.length !=0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                finish();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void barcodeResult(BarcodeResult result) {
        String code = result.getText();
        Intent intent = new Intent();
        intent.putExtra(QR_RESULT_ARG, code);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {
        // Unused
    }

    private void checkPermissionAndStartScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.CAMERA};
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
        } else {
            startScanner();
        }
    }

    private void startScanner() {
        this.barcodeView.resume();
        this.barcodeView.decodeSingle(this);
    }

    private void pauseScanner() {
        this.barcodeView.pause();
    }
}
