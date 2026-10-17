package com.innoshiftconsult.pocketlock;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ZoomableImageView extends ImageView {
    private static final float MAX_ZOOM = 5f;
    private final Matrix imageMatrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private float zoom = 1f;
    private float lastTouchX;
    private float lastTouchY;

    public ZoomableImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float nextZoom = Math.max(1f, Math.min(MAX_ZOOM, zoom * detector.getScaleFactor()));
                float scaleChange = nextZoom / zoom;
                imageMatrix.postScale(scaleChange, scaleChange, detector.getFocusX(), detector.getFocusY());
                zoom = nextZoom;
                setImageMatrix(imageMatrix);
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetImageMatrix();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && zoom > 1f) {
                    float x = event.getX();
                    float y = event.getY();
                    imageMatrix.postTranslate(x - lastTouchX, y - lastTouchY);
                    setImageMatrix(imageMatrix);
                    lastTouchX = x;
                    lastTouchY = y;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                lastTouchX = event.getX(0);
                lastTouchY = event.getY(0);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    private void resetImageMatrix() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float fit = Math.min(
                (float) getWidth() / drawable.getIntrinsicWidth(),
                (float) getHeight() / drawable.getIntrinsicHeight());
        float offsetX = (getWidth() - drawable.getIntrinsicWidth() * fit) / 2f;
        float offsetY = (getHeight() - drawable.getIntrinsicHeight() * fit) / 2f;
        imageMatrix.reset();
        imageMatrix.setScale(fit, fit);
        imageMatrix.postTranslate(offsetX, offsetY);
        zoom = 1f;
        setImageMatrix(imageMatrix);
    }
}
