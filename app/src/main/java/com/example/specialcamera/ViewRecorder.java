package com.example.specialcamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.TextureView;
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

            // ==
//            TextureView textureView = view.findViewById(R.id.lens_texture_view);
//            Bitmap lensBitmap = textureView.getBitmap();

            TextureView previewTextureView = view.findViewById(R.id.preview_texture_view);
//            Bitmap previewBitmap = previewTextureView.getBitmap();

//            Canvas canvas = new Canvas();
//            canvas.drawBitmap(previewBitmap, 200, 200, null);
//            canvas.setBitmap(previewBitmap);
            // ==

            int x = 10;
            int y = 10;
            int r = 40;

            Paint mPaint = new Paint();
            mPaint.setColor(0xFF0000);



//            Bitmap bitmapCopy = Bitmap.createBitmap(previewBitmap);
//            Canvas mCanvas = new Canvas(bitmapCopy);
//            mCanvas.drawCircle(x,y,r,mPaint);
//            BitmapDrawable bitmapDrawable = new BitmapDrawable(view.getResources(), bitmapCopy);

            // ==

            long startTime = System.currentTimeMillis();
            // record for 10 sec for test
            while (System.currentTimeMillis() - startTime < 10_000) {
                Bitmap previewBitmap = previewTextureView.getBitmap();
                Canvas mCanvas = new Canvas(previewBitmap);
                mCanvas.drawCircle(x,y,r,mPaint);
                mCanvas.save();

//                view.setDrawingCacheEnabled(true);
//                Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
//                view.setDrawingCacheEnabled(false);

                try (AndroidFrameConverter converter = new AndroidFrameConverter()) {
                    recorder.record(converter.convert(previewBitmap));
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
