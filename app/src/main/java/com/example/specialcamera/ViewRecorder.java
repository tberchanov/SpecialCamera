package com.example.specialcamera;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;

import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;

public class ViewRecorder {

    private static final String TAG = "ViewRecorder";

    private final int fps;

    public ViewRecorder(int fps) {
        this.fps = fps;
    }

    public void record(View view) {
        File recordingFile = new File(view.getContext().getFilesDir(), "view_recording.mp4");

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(recordingFile.getAbsolutePath(), 256, 256)) {
            Log.d(TAG, "Starting recording");

            //            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
            recorder.setFormat("mp4");
            recorder.setFrameRate(30);
            //            recorder.setPixelFormat(avutil.PIX_FMT_YUV420P10);
            recorder.setVideoBitrate(1200);
            recorder.startUnsafe();

            long startTime = System.currentTimeMillis();
            // record for 5 sec for test
            while (System.currentTimeMillis() - startTime < 5000) {
                view.setDrawingCacheEnabled(true);
                Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
                view.setDrawingCacheEnabled(false);

                try (AndroidFrameConverter converter = new AndroidFrameConverter()) {
                    recorder.record(converter.convert(bitmap));
                } catch (Exception e) {
                    Log.e(TAG, "Record frame failure", e);
                }

                // <20fps
                Thread.sleep(50);
            }
            recorder.stop();
            Log.d(TAG, "Recording complete");
        } catch (Exception e) {
            Log.e(TAG, "Recording failure", e);
        }
    }
}
