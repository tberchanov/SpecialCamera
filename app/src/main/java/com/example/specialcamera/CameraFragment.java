package com.example.specialcamera;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.specialcamera.MediaProjectionService.MediaProjectionBinder;

public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";

    private static final int REQUEST_MEDIA_PROJECTION = 2;

    @NonNull
    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    public static final String LOG_TAG = "myLogs";
    String[] myCameras = null;
    private CameraManager mCameraManager = null;

    @Nullable
    private CameraHelper cameraHelper;

    private AutoFitTextureView previewTextureView;
    private TextureView lensTextureView;
    private TextView fpsTextView;

    private MediaProjectionManager projectionManager;

    private boolean isMediaProjectionServiceBound = false;

    @Nullable
    private MediaProjectionService mediaProjectionService;

    private boolean isScreenCapturingOngoing = false;

    private int framesQuantity = 0;

    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder ibinder) {
            MediaProjectionBinder binder = (MediaProjectionBinder) ibinder;
            mediaProjectionService = binder.getService();
            isMediaProjectionServiceBound = true;

            mediaProjectionService.startRecording();
            isScreenCapturingOngoing = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isMediaProjectionServiceBound = false;
        }
    };

    public CameraFragment() {
        super(R.layout.fragment_camera);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        logCamerasInfo();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewTextureView = view.findViewById(R.id.preview_texture_view);
        lensTextureView = view.findViewById(R.id.lens_texture_view);
        fpsTextView = view.findViewById(R.id.fps_text_view);

        // TODO width and height should be configurable based on the CameraDevice characteristics
        previewTextureView.setAspectRatio(1920, 1080);

        cameraHelper = new CameraHelper(requireContext(), myCameras[0], previewTextureView,
                lensTextureView, this::onSurfaceTextureAvailable,
                () -> framesQuantity++
        );
        cameraHelper.openCamera();

        setupListeners(view);

        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                fpsTextView.setText(Integer.toString(framesQuantity));
                framesQuantity = 0;
                view.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void setupListeners(@NonNull View view) {
        if (cameraHelper == null) return;

        view.findViewById(R.id.change_zoom_btn).setOnClickListener((v) -> {
            cameraHelper.setZoom(cameraHelper.getZoom() + 5);
        });
        view.findViewById(R.id.add_iso_btn).setOnClickListener((v) -> {
            cameraHelper.setISO(cameraHelper.getIsoLevel() == null ? 0 : (cameraHelper.getIsoLevel() + 5));
        });
        view.findViewById(R.id.auto_iso_btn).setOnClickListener((v) -> {
            cameraHelper.setAutoControl();
        });
        view.findViewById(R.id.add_exposure_btn).setOnClickListener((v) -> {
            cameraHelper.setExposure(cameraHelper.getExposure() == null ? 0 : cameraHelper.getExposure() + 0.1f);
        });
        view.findViewById(R.id.record_btn).setOnClickListener((v) -> {
            if (isScreenCapturingOngoing) {
                mediaProjectionService.stopRecording();
                isScreenCapturingOngoing = false;
            } else {
                if (isMediaProjectionServiceBound) {
                    mediaProjectionService.startRecording();
                    isScreenCapturingOngoing = true;
                } else {
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
                }
            }
        });
        view.findViewById(R.id.change_fps_btn).setOnClickListener((v) -> {
            cameraHelper.setFPS(new Range<>(0, 15));
        });
    }

    private void onSurfaceTextureAvailable() {
        adjustTextureViewToRotation(previewTextureView);
        adjustTextureViewToRotation(lensTextureView);

        int lensWidth = lensTextureView.getWidth();
        int lensHeight = lensTextureView.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(5, 5, lensWidth / 2f, lensHeight / 2f);
        if (needRotateTexture()) {
            matrix.postRotate(-90, lensWidth / 2f, lensHeight / 2f);
        }
        lensTextureView.setTransform(matrix);
    }

    private void adjustTextureViewToRotation(TextureView textureView) {
        Matrix matrix = new Matrix();
        int rotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, textureView.getWidth(), textureView.getHeight());
        RectF previewRectF = new RectF(0, 0, textureView.getHeight(), textureView.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        if (needRotateTexture()) {
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) textureView.getWidth() / textureView.getWidth(), (float) textureView.getHeight() / textureView.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private boolean needRotateTexture() {
        int rotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
    }

    @Override
    public void onDestroyView() {
        if (cameraHelper != null) {
            cameraHelper.closeCamera();
            cameraHelper = null;
        }
        super.onDestroyView();
    }

    private void logCamerasInfo() {
        mCameraManager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            // Получение списка камер с устройства

            myCameras = mCameraManager.getCameraIdList();

            // выводим информацию по камере
            for (String cameraID : mCameraManager.getCameraIdList()) {
                Log.i(LOG_TAG, "cameraID: " + cameraID);
                int id = Integer.parseInt(cameraID);

                // Получениe характеристик камеры
                CameraCharacteristics cc = mCameraManager.getCameraCharacteristics(cameraID);
                // Получения списка выходного формата, который поддерживает камера
                StreamConfigurationMap configurationMap =
                        cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                //  Определение какая камера куда смотрит
                int Faceing = cc.get(CameraCharacteristics.LENS_FACING);

                if (Faceing == CameraCharacteristics.LENS_FACING_FRONT) {
                    Log.i(LOG_TAG, "Camera with ID: " + cameraID + "  is FRONT CAMERA  ");
                }

                if (Faceing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.i(LOG_TAG, "Camera with: ID " + cameraID + " is BACK CAMERA  ");
                }


                // Получения списка разрешений которые поддерживаются для формата jpeg
                Size[] sizesJPEG = configurationMap.getOutputSizes(ImageFormat.JPEG);

                if (sizesJPEG != null) {
                    for (Size item : sizesJPEG) {
                        Log.i(LOG_TAG, "w:" + item.getWidth() + " h:" + item.getHeight());
                    }
                } else {
                    Log.i(LOG_TAG, "camera don`t support JPEG");
                }
            }
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled media projection");
                Toast.makeText(getActivity(), "User denied screen sharing permission", Toast.LENGTH_LONG).show();
                return;
            }
            if (data == null) {
                Log.i(TAG, "Projection data is null!");
                Toast.makeText(getActivity(), "Projection data is null!", Toast.LENGTH_LONG).show();
                return;
            }
            Log.i(TAG, "Starting screen capture");

            Context context = requireContext();

            if (!isMediaProjectionServiceBound) {
                Intent mediaProjectionIntent = MediaProjectionService.newIntent(context, resultCode, data, 1080, 1920);
                ActivityCompat.startForegroundService(context, mediaProjectionIntent);
                context.bindService(mediaProjectionIntent, connection, Context.BIND_AUTO_CREATE);
            }
        }
    }
}
