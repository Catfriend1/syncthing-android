package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.FileUtils.ExternalStorageDirType;

import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

public class PhotoShootActivity extends AppCompatActivity {

    private static String TAG = "PhotoShootActivity";

    private static final int REQUEST_CAMERA = 140;
    private static final int REQUEST_WRITE_STORAGE = 142;
    private static final int REQUEST_CAPTURE_IMAGE = 150;

    private Button mBtnGo;
    private Button mBtnGrantCameraPerm;
    private Button mBtnGrantStoragePerm;

    private Uri lastPhotoURI = null;

    @Inject
    SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        // Check if required camera hardware is present.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(
                    PhotoShootActivity.this,
                    getString(R.string.photo_shoot_intro_no_camera), Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        // Check if user granted permissions before and consented to use this feature.
        Boolean prefEnableSyncthingCamera = mPreferences.getBoolean(Constants.PREF_ENABLE_SYNCTHING_CAMERA, false);
        Boolean haveRequiredPermissions = haveStoragePermission() && haveCameraPermission();
        Log.v(TAG, "prefEnableSyncthingCamera=" + Boolean.toString(prefEnableSyncthingCamera) + ", haveRequiredPermissions=" + Boolean.toString(haveRequiredPermissions));
        if (haveRequiredPermissions && prefEnableSyncthingCamera) {
            // Take a shortcut and offer to take a picture instantly.
            Log.v(TAG, "User completed intro and consented before. Warp to take a picture.");
            openCameraIntent();
            return;
        }

        // Make notification bar transparent (API level 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // Show photo shoot intro UI to request required permissions.
        setContentView(R.layout.activity_photo_shoot_intro);

        mBtnGrantStoragePerm = (Button) findViewById(R.id.btnGrantStoragePerm);
        if (mBtnGrantStoragePerm != null) {
            mBtnGrantStoragePerm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!haveStoragePermission()) {
                        requestStoragePermission();
                    }
                }
            });
        }

        mBtnGrantCameraPerm = (Button) findViewById(R.id.btnGrantCameraPerm);
        if (mBtnGrantCameraPerm != null) {
            mBtnGrantCameraPerm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!haveCameraPermission()) {
                        requestCameraPermission();
                    }
                }
            });
        }

        Button mBtnBack = (Button) findViewById(R.id.btn_back);
        mBtnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mBtnGo = (Button) findViewById(R.id.btn_go);
        mBtnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Re-check permissions.
                Boolean haveRequiredPermissions = haveStoragePermission() && haveCameraPermission();
                if (!haveRequiredPermissions) {
                    Toast.makeText(
                            PhotoShootActivity.this,
                            getString(R.string.photo_shoot_intro_missing_permissions), Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                // Store user consent.
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putBoolean(Constants.PREF_ENABLE_SYNCTHING_CAMERA, true);
                editor.apply();

                // Warp to take a picture.
                Log.v(TAG, "User completed intro and consented.");
                openCameraIntent();
            }
        });

        updateButtons();
    }

    private void openCameraIntent() {
        Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (pictureIntent.resolveActivity(getPackageManager()) == null) {
            Log.e(TAG, "This system does not support the ACTION_IMAGE_CAPTURE intent.");
            Toast.makeText(
                    PhotoShootActivity.this,
                    "This system does not support the ACTION_IMAGE_CAPTURE intent.", Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        //Create a file to store the image
        File photoFile = null;
        try {
            photoFile = createImageFile();
            if (photoFile == null) {
                Log.e(TAG, "openCameraIntent: photoFile == null");
                return;
            }
        } catch (IOException ex) {
            Log.e(TAG, "Error occurred while creating the temp image file");
            return;
        }

        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                pictureIntent.setClipData(ClipData.newRawUri("", photoURI));
                pictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            Log.d(TAG, "Launching take picture intent ...");
            lastPhotoURI = photoURI;
            startActivityForResult(pictureIntent, REQUEST_CAPTURE_IMAGE);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp =
             new SimpleDateFormat("yyyyMMdd_HHmmss",
                          Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File storageDir = FileUtils.getExternalFilesDir(PhotoShootActivity.this, ExternalStorageDirType.INT_MEDIA, Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            Log.e(TAG, "createImageFile: storageDir == null");
            return null;
        }
        storageDir.mkdirs();
        File image = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
        );
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                                  Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "User took a picture.");
                lastPhotoURI = null;
            } else if(resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "User cancelled to take a picture.");
                if (lastPhotoURI != null) {
                    Log.v(TAG, "Deleting temporary file [" + lastPhotoURI.getPath() + "]");
                    try {
                        final ContentResolver contentResolver = getContentResolver();
                        contentResolver.delete(lastPhotoURI, null, null);
                    } catch (Exception e) {
                        Log.e(TAG, "Delete temporary file FAILED", e);
                    }
                    lastPhotoURI = null;
                }
            }
            finish();
        }
    }

    /**
     * Permission check and request functions
     */
    private boolean haveCameraPermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA);
    }

    private boolean haveStoragePermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied CAMERA permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted CAMERA permission.");
                    updateButtons();
                }
                break;
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied WRITE_EXTERNAL_STORAGE permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted WRITE_EXTERNAL_STORAGE permission.");
                    updateButtons();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void updateButtons() {
        mBtnGrantCameraPerm.setVisibility(haveCameraPermission() ? View.GONE : View.VISIBLE);
        mBtnGrantStoragePerm.setVisibility(haveStoragePermission() ? View.GONE : View.VISIBLE);
    }
}
