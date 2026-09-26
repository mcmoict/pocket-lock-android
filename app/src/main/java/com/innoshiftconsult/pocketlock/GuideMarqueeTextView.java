package com.innoshiftconsult.pocketlock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

public class GuideMarqueeTextView extends TextView {
    private static final String END_MARKER = "\u200B";
    private static final long MARQUEE_SCROLL_DURATION_MS = 2200L;
    private final int touchSlop;
    private boolean watchingEndMarker;
    private boolean endMarkerReported;
    private boolean manualScrollEnabled;
    private boolean dragging;
    private float lastTouchX;
    private Runnable endMarkerListener;
    private ValueAnimator scrollAnimator;

    public GuideMarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setGuideText(String text) {
        setText(text + END_MARKER);
        endMarkerReported = false;
        manualScrollEnabled = false;
        scrollTo(0, 0);
    }

    public void startWatchingEndMarker(Runnable listener) {
        endMarkerListener = listener;
        watchingEndMarker = true;
        endMarkerReported = false;
        manualScrollEnabled = false;
        stopScrollAnimation();
        post(this::startSinglePassScroll);
        invalidate();
    }

    public void stopWatchingEndMarker() {
        watchingEndMarker = false;
        endMarkerListener = null;
        stopScrollAnimation();
    }

    private float getViewportWidth() {
        // Use compound padding, not plain padding, so the start icon drawable is accounted for.
        return getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
    }

    private float getEndMarkerPosition() {
        if (getLayout() == null || getText() == null || getText().length() == 0) {
            return 0f;
        }
        int markerIndex = getText().length() - 1;
        return getLayout().getPrimaryHorizontal(markerIndex);
    }

    private int getMaxScrollX() {
        if (getLayout() == null || getText() == null) {
            return 0;
        }
        float requiredScroll = getEndMarkerPosition() - getViewportWidth();
        return (int) Math.ceil(Math.max(0f, requiredScroll));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!manualScrollEnabled) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = lastTouchX - event.getX();
                if (!dragging && Math.abs(dx) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    int maxScrollX = getMaxScrollX();
                    int newScrollX = (int) Math.max(0, Math.min(maxScrollX, getScrollX() + dx));
                    scrollTo(newScrollX, 0);
                    lastTouchX = event.getX();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    performClick();
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void startSinglePassScroll() {
        if (!watchingEndMarker || getLayout() == null || getText() == null || getText().length() == 0) {
            return;
        }

        int maxScrollX = getMaxScrollX();
        if (maxScrollX <= 0) {
            reportEndMarker();
            return;
        }

        scrollTo(0, 0);
        scrollAnimator = ValueAnimator.ofInt(0, maxScrollX);
        long duration = Math.max(1200L, Math.min(MARQUEE_SCROLL_DURATION_MS, maxScrollX * 10L));
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new LinearInterpolator());
        scrollAnimator.addUpdateListener(animation -> {
            int currentScroll = (Integer) animation.getAnimatedValue();
            scrollTo(currentScroll, 0);
        });
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (watchingEndMarker) {
                    reportEndMarker();
                }
            }
        });
        scrollAnimator.start();
    }

    private void stopScrollAnimation() {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
    }

    private void reportEndMarker() {
        if (endMarkerReported) {
            return;
        }
        endMarkerReported = true;
        manualScrollEnabled = true;
        if (endMarkerListener != null) {
            post(endMarkerListener);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!watchingEndMarker || endMarkerReported || getLayout() == null
                || getText() == null || getText().length() == 0) {
            return;
        }

        // The end marker is a zero-width object appended to the text; once it has
        // scrolled into (or past) the visible viewport, the text has fully flowed by.
        float requiredScroll = getEndMarkerPosition() - getViewportWidth();
        if (requiredScroll <= 0f) {
            reportEndMarker();
            return;
        }

        if (getScrollX() > 0 && getScrollX() >= requiredScroll - 1.0f) {
            reportEndMarker();
        }
    }
}
