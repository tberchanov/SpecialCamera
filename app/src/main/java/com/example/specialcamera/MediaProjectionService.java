package com.example.specialcamera;


import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.io.File;
import java.io.IOException;

public class MediaProjectionService extends Service {

    private final String TAG = "MediaProjectionService";

    private static final String KEY_PROJECTION_RESULT_CODE = "PROJECTION_RESULT_CODE";
    private static final String KEY_PROJECTION_DATA = "PROJECTION_DATA";
    private static final String KEY_VIDEO_FRAME_WIDTH = "VIDEO_FRAME_WIDTH";
    private static final String KEY_VIDEO_FRAME_HEIGHT = "VIDEO_FRAME_HEIGHT";

    private static final String INTERNAL_RECORDING_FILE = "recording.mp4";

    /*
    * `videoFrameWidth` and `videoFrameHeight` can be null. In this case display metrics will be used.
    * */
    public static Intent newIntent(
            @NonNull Context context, int projectionResultCode,
            @NonNull Intent projectionData,
            @Nullable Integer videoFrameWidth, @Nullable Integer videoFrameHeight
    ) {
        return new Intent(context, MediaProjectionService.class)
                .putExtra(KEY_VIDEO_FRAME_HEIGHT, videoFrameHeight)
                .putExtra(KEY_VIDEO_FRAME_WIDTH, videoFrameWidth)
                .putExtra(KEY_PROJECTION_DATA, projectionData)
                .putExtra(KEY_PROJECTION_RESULT_CODE, projectionResultCode);
    }

    @Nullable
    private MediaRecorder mediaRecorder;

    @Nullable
    private MediaProjection mediaProjection;

    @Nullable
    private VirtualDisplay virtualDisplay;

    private final MediaProjectionBinder binder = new MediaProjectionBinder();

    int videoFrameHeight;
    int videoFrameWidth;
    int densityDpi;
    int projectionResultCode;
    Intent projectionData;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (mediaRecorder == null) {
            initVideoSize(intent);
        }

        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground();

        if (mediaRecorder == null) {
            initVideoSize(intent);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void initVideoSize(@NonNull Intent intent) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        videoFrameHeight = intent.getIntExtra(KEY_VIDEO_FRAME_HEIGHT, metrics.heightPixels);
        videoFrameWidth = intent.getIntExtra(KEY_VIDEO_FRAME_WIDTH, metrics.widthPixels);
        densityDpi = metrics.densityDpi;

        if (videoFrameWidth % 2 != 0 || videoFrameHeight % 2 != 0) {
            throw new IllegalArgumentException("videoFrameHeight and videoFrameWidth must be even. Current values: " +
                    videoFrameHeight + "; " + videoFrameWidth);
        }

        projectionResultCode = intent.getIntExtra(KEY_PROJECTION_RESULT_CODE, -1);
        projectionData = intent.getParcelableExtra(KEY_PROJECTION_DATA);
    }

    private void prepareForRecording() {
        mediaRecorder = new MediaRecorder();

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameHeight = videoFrameHeight;
        profile.videoFrameWidth = videoFrameWidth;
        mediaRecorder.setProfile(profile);

        File outputFile = new File(getFilesDir(), INTERNAL_RECORDING_FILE);
        try {
            if (outputFile.exists()) {
                Log.d(TAG, "Internal output file deleted: " + outputFile.delete());
            }
            Log.d(TAG, "Internal output file created: " + outputFile.createNewFile());
        } catch (IOException e) {
            Log.e(TAG, "Internal output file creation error", e);
            throw new RuntimeException(e);
        }
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare mediaRecorder error", e);
            throw new RuntimeException(e);
        }

        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(
                projectionResultCode, projectionData
        );
        Log.d(TAG, "Projection created successfully: " + mediaProjection);
        virtualDisplay = mediaProjection.createVirtualDisplay("ProjectionVirtualDisplay",
                videoFrameWidth, videoFrameHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);

        mediaRecorder.start();
    }

    public void startRecording() {
        prepareForRecording();
    }

    public void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    private void startForeground() {
        try {
            String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? createNotificationChannel(TAG, TAG)
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    : "";

            Notification notification =
                    new NotificationCompat.Builder(this, channelId)
                            .setContentTitle("Media projection service")
                            // Create the notification to display while the service
                            // is running
                            .build();
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            }
            ServiceCompat.startForeground(
                    this,
                    100,
                    notification,
                    type
            );
        } catch (Exception e) {
            Log.e(TAG, "starting foreground error", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }

    public class MediaProjectionBinder extends Binder {
        MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }
}
