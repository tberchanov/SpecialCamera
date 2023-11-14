package com.example.specialcamera;

import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.util.Arrays;
import java.util.function.IntPredicate;

public class MainActivity extends FragmentActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE
            );
        } else {
            if (savedInstanceState == null) {
                showCameraFragment();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Arrays.stream(grantResults).allMatch(value -> value == PackageManager.PERMISSION_GRANTED)) {
                showCameraFragment();
            }
        }
    }

    private void showCameraFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fragment_container, CameraFragment.newInstance())
                .commit();
    }
}
