package com.example.specialcamera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CrossView extends View {

    private static final int LINE_WIDTH = 5;

    private final Rect verticalRect = new Rect();
    private final Rect horizontalRect = new Rect();

    private final Paint paint = new Paint();

    {
        paint.setColor(Color.RED);
    }

    public CrossView(Context context) {
        super(context);
    }

    public CrossView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CrossView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CrossView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(verticalRect, paint);
        canvas.drawRect(horizontalRect, paint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        verticalRect.bottom = getMeasuredHeight();
        verticalRect.left = getMeasuredHeight() / 2 - LINE_WIDTH / 2;
        verticalRect.right = getMeasuredHeight() / 2 + LINE_WIDTH / 2;

        horizontalRect.right = getMeasuredHeight();
        horizontalRect.top = getMeasuredHeight() / 2 - LINE_WIDTH / 2;
        horizontalRect.bottom = getMeasuredHeight() / 2 + LINE_WIDTH / 2;
    }
}
