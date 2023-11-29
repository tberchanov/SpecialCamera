package com.example.specialcamera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CameraHelper {

    private final String TAG = "CameraHelper";

    private final String mCameraID;
    @Nullable
    private CameraDevice mCameraDevice = null;
    private final Context context;
    private CameraCaptureSession mCaptureSession;
    private final CameraManager mCameraManager;
    private final TextureView previewView;
    private final TextureView lensView;

    private final CameraCharacteristics characteristics;
    private final float maxZoom;
    private float currentZoom;

    private CaptureRequest.Builder captureRequestBuilder;

    private final Runnable onSurfaceTextureAvailable;

    private final Range<Integer> sensitivityRange;

    private final Range<Long> exposureRange;

    @Nullable
    private SurfaceTexture previewSurfaceTexture;

    @Nullable
    private SurfaceTexture lensSurfaceTexture;

    // 0..100
    @Nullable
    private Integer isoLevel;

    // 0..100
    @Nullable
    private Float exposure = 0f;

    @NonNull
    private final Runnable onPreviewFrameUpdate;

    private final CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            Log.i(TAG, "Open camera  with id:" + mCameraDevice.getId());

            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraDevice.close();

            Log.i(TAG, "disconnect camera  with id:" + mCameraDevice.getId());
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.i(TAG, "error! camera id:" + camera.getId() + " error:" + error);
        }
    };

    public CameraHelper(
            @NonNull Context context, @NonNull String cameraID,
            @NonNull TextureView previewView, @NonNull TextureView lensView,
            @NonNull Runnable onSurfaceTextureAvailable,
            @NonNull Runnable onPreviewFrameUpdate
    ) {
        this.onPreviewFrameUpdate = onPreviewFrameUpdate;
        this.context = context;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mCameraID = cameraID;

        try {
            characteristics = mCameraManager.getCameraCharacteristics(cameraID);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) * 10;
        sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);

        this.previewView = previewView;
        this.lensView = lensView;
        this.onSurfaceTextureAvailable = onSurfaceTextureAvailable;
    }

    private void createCameraPreviewSession() {
        retrieveSurfaceTexture(
                previewView,
                (surfaceTexture -> {
                    previewSurfaceTexture = surfaceTexture;

                    if (lensSurfaceTexture != null) {
                        startCameraPreviewCapturing(previewSurfaceTexture, lensSurfaceTexture);
                    }
                }),
                () -> previewSurfaceTexture = null,
                onPreviewFrameUpdate
        );

        retrieveSurfaceTexture(
                lensView,
                (surfaceTexture -> {
                    lensSurfaceTexture = surfaceTexture;

                    if (previewSurfaceTexture != null) {
                        startCameraPreviewCapturing(previewSurfaceTexture, lensSurfaceTexture);
                    }
                }),
                () -> lensSurfaceTexture = null,
                () -> {
                }
        );
    }

    private void retrieveSurfaceTexture(@NonNull TextureView textureView, @NonNull SurfaceTextureConsumer onResult,
                                        @NonNull Runnable onDestroy, @NonNull Runnable onUpdate) {
        if (textureView.isAvailable()) {
            onResult.consume(textureView.getSurfaceTexture());
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    onResult.consume(surface);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    onDestroy.run();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                    onUpdate.run();
                }
            });
        }
    }

    private void startCameraPreviewCapturing(
            @NonNull SurfaceTexture previewSurfaceTexture, @NonNull SurfaceTexture lensSurfaceTexture
    ) {
        onSurfaceTextureAvailable.run();

        // TODO width and height should be configurable based on the CameraDevice characteristics
        previewSurfaceTexture.setDefaultBufferSize(1920, 1080);

        Surface surface = new Surface(previewSurfaceTexture);

//        Canvas canvas =surface.lockCanvas(null);
//        Paint paint = new Paint();
//        paint.setColor(0xFF0000);
//        canvas.drawCircle(50, 50, 50, paint);
//        surface.unlockCanvasAndPost(canvas);

        Surface lensSurface = new Surface(lensSurfaceTexture);

        try {
            captureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, null);

            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(lensSurface);

            // TODO possibly second preview for zoom miniature can be used here, in the list
            mCameraDevice.createCaptureSession(Arrays.asList(surface, lensSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mCaptureSession = session;
                            try {
                                mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "createCaptureSession", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "onConfigureFailed");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCameraPreviewSession", e);
        }
    }

    public boolean isOpen() {
        if (mCameraDevice == null) {
            return false;
        } else {
            return true;
        }
    }

    public void openCamera() {
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                mCameraManager.openCamera(mCameraID, mCameraCallback, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "openCamera", e);
            }
        } else {
            Log.e(TAG, "Cannot openCamera. No permissions.");
        }
    }

    public void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    public float getZoom() {
        return currentZoom;
    }

    public void setZoom(float zoomLevel) {
        Rect zoomRect = getZoomRect(zoomLevel);
        if (zoomRect != null) {
            try {
                //you can try to add the synchronized object here
                captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
                mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);

                currentZoom = zoomLevel;
            } catch (Exception e) {
                Log.e(TAG, "setZoom: ", e);
            }
        }
    }

    @Nullable
    private Rect getZoomRect(float zoomLevel) {
        try {
            Rect activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if ((zoomLevel <= maxZoom) && (zoomLevel > 1)) {
                int minW = (int) (activeRect.width() / maxZoom);
                int minH = (int) (activeRect.height() / maxZoom);
                int difW = activeRect.width() - minW;
                int difH = activeRect.height() - minH;
                int cropW = difW / 100 * (int) zoomLevel;
                int cropH = difH / 100 * (int) zoomLevel;
                cropW -= cropW & 3;
                cropH -= cropH & 3;
                return new Rect(cropW, cropH, activeRect.width() - cropW, activeRect.height() - cropH);
            } else if (zoomLevel == 0) {
                return new Rect(0, 0, activeRect.width(), activeRect.height());
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "getZoomRect", e);
            return null;
        }
    }

    @Nullable
    public Integer getIsoLevel() {
        return isoLevel;
    }

    public void setAutoControl() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        isoLevel = null;
        exposure = null;

        try {
            mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "setISO: ", e);
        }
    }

    public void setISO(int isoValue) {
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

        int maxISO = sensitivityRange.getUpper();
        int minISO = sensitivityRange.getLower();
        int realIso = ((isoValue * (maxISO - minISO)) / 100 + minISO);
        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, realIso);

        isoLevel = isoValue;

        try {
            mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "setISO: ", e);
        }
    }

    @Nullable
    public Float getExposure() {
        return exposure;
    }

    public void setExposure(float exposure) {
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

        long maxISO = exposureRange.getUpper();
        long minISO = exposureRange.getLower();
        float realIso = ((exposure * (maxISO - minISO)) / 100 + minISO);
        captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) realIso);

        this.exposure = exposure;

        try {
            mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "setISO: ", e);
        }
    }

    public void setFPS(Range<Integer> fpsRange) {
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        try {
            mCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "setISO: ", e);
        }
    }
}

interface SurfaceTextureConsumer {
    void consume(SurfaceTexture surfaceTexture);
}