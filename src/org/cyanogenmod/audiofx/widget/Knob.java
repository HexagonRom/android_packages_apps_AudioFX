/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Copyright (c) 2014, The CyanogenMod Project. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *       * Redistributions in binary form must reproduce the above
 *         copyright notice, this list of conditions and the following
 *         disclaimer in the documentation and/or other materials provided
 *         with the distribution.
 *       * Neither the name of The Linux Foundation nor the names of its
 *         contributors may be used to endorse or promote products derived
 *         from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.cyanogenmod.audiofx.widget;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.Math;

import org.cyanogenmod.audiofx.R;

public class Knob extends FrameLayout {
    private static final String TAG = Knob.class.getSimpleName();

    private static final int STROKE_WIDTH = 35;
    private static final float TEXT_SIZE = 0.20f;
    private static final float TEXT_PADDING = 0.31f;
    private static final float LABEL_PADDING = 0.02f;
    private static final float LABEL_SIZE = 0.08f;
    private static final float LABEL_WIDTH = 0.45f;
    private static final float INDICATOR_RADIUS = 0.38f;
    private ValueAnimator mAnimator;

    public interface OnKnobChangeListener {
        void onValueChanged(Knob knob, int value, boolean fromUser);

        boolean onSwitchChanged(Knob knob, boolean on);

        void onAnimationFinished(boolean endValue);
    }

    private OnKnobChangeListener mOnKnobChangeListener = null;

    private float mOriginalProgress = 0.0f;
    private float mProgress = 0.0f;
    private int mMax = 100;
    private boolean mOn = false;
    private boolean mEnabled = false;

    private int mHighlightColor;
    private int mLowlightColor;
    private int mDisabledColor;

    private final Paint mPaint;

    private final TextView mLabelTV;
    private final TextView mProgressTV;

    private final ImageView mKnobOn;

    private float mLastX;
    private float mLastY;
    private boolean mMoved;

    private int mWidth = 0;
    private int mIndicatorWidth = 0;

    private RectF mRectF;

    public Knob(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Knob, 0, 0);

        String label;
        int foreground;
        try {
            label = a.getString(R.styleable.Knob_label);
            foreground = a.getResourceId(R.styleable.Knob_foreground, R.drawable.knob);
        } finally {
            a.recycle();
        }

        LayoutInflater li = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        li.inflate(R.layout.knob, this, true);

        Resources res = getResources();
        mHighlightColor = res.getColor(R.color.highlight);
        mLowlightColor = res.getColor(R.color.lowlight);
        mDisabledColor = res.getColor(R.color.disabled_knob);

        ImageView fg = (ImageView) findViewById(R.id.knob_foreground);
        fg.setImageResource(R.drawable.knob);

        mLabelTV = (TextView) findViewById(R.id.knob_label);
        mLabelTV.setText(label);
        mProgressTV = (TextView) findViewById(R.id.knob_value);

        mKnobOn = (ImageView) findViewById(R.id.knob_toggle_on);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(mHighlightColor);
        mPaint.setStrokeWidth(65);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setShadowLayer(2, 1, -2, getResources().getColor(R.color.black));

        setWillNotDraw(false);
    }

    public Knob(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Knob(Context context) {
        this(context, null);
    }

    public void setOnKnobChangeListener(OnKnobChangeListener l) {
        mOnKnobChangeListener = l;
    }

    public void setValue(int value) {
        if (mMax != 0) {
            mOriginalProgress = ((float) value) / mMax;
            if (mOriginalProgress > 100) {
                mOriginalProgress = 100;
            } else if (mOriginalProgress < 0) {
                mOriginalProgress = 0;
            }

            setProgress(mOriginalProgress);
        }
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    public void updateProgressText(boolean showText, float progress) {
        if (showText) {
            mProgressTV.setText((int) (progress * 100) + "%");
        } else {
            mProgressTV.setText("--%");
        }
    }

    public void setProgress(float progress, boolean fromUser) {
        if (progress > 1.0f) {
            progress = 1.0f;
        } else if (progress < 0.0f) {
            progress = 0.0f;
        }

        mProgress = progress;

        updateProgressText(mEnabled && mOn, progress);

        invalidate();

        if (mOnKnobChangeListener != null) {
            mOnKnobChangeListener.onValueChanged(this, (int) (progress * mMax), fromUser);
        }
    }

    public void setMax(int max) {
        mMax = max;
    }

    public float getProgress() {
        return mProgress;
    }

    private void drawIndicator() {
        float r = mWidth * INDICATOR_RADIUS;
//        ImageView view = mEnabled ? mKnobOn : mKnobOff;
        mKnobOn.setTranslationX((float) Math.sin(mProgress * 2 * Math.PI) * r - mIndicatorWidth / 2);
        mKnobOn.setTranslationY((float) -Math.cos(mProgress * 2 * Math.PI) * r - mIndicatorWidth / 2);
    }

    @Override
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;

        mLabelTV.setTextColor(mEnabled ? mHighlightColor : mDisabledColor);
        mProgressTV.setTextColor(mEnabled ? mHighlightColor : mDisabledColor);
        mPaint.setColor(mEnabled ? mHighlightColor : mDisabledColor);

//        if (enabled) {
//            mOn = true;
//        }
        if (enabled) {
            setOn(mOn, false);
        }
//        updateProgressText(mEnabled && mOn, mOriginalProgress);
//        } else {
//        }
//        invalidate();
    }

    public void setOn(final boolean on, boolean animate) {
        mOn = on;

        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (mOriginalProgress > 1) {
            mOriginalProgress = 1;
        }
        if (animate) {
            if (on) {
                mAnimator = ValueAnimator.ofFloat(mProgress, mOriginalProgress);
            } else {
                mAnimator = ValueAnimator.ofFloat(mProgress, 0f);
            }
            mAnimator.setDuration(500);
            mAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mAnimator = null;
                    updateProgressText(mOn && mEnabled, mOriginalProgress);
                    if (mOnKnobChangeListener != null) {
                        mOnKnobChangeListener.onAnimationFinished(on);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float progress = (Float) animation.getAnimatedValue();
                    if (progress < 0) {
                        progress = 0;
                    } else if (progress > 1) {
                        progress = 1;
                    }
                    mProgress = progress;
                    if (mOnKnobChangeListener != null) {
                        mOnKnobChangeListener.onValueChanged(Knob.this, (int) (progress * mMax), true);
                    }
                    updateProgressText(true, mProgress);
                    invalidate();
                }
            });
            mAnimator.start();
        } else {
            updateProgressText(mEnabled && mOn, mOriginalProgress);

            // make progress correct value
            mProgress = mOn ? mOriginalProgress : 0f;

            invalidate();

            if (mOnKnobChangeListener != null) {
                mOnKnobChangeListener.onAnimationFinished(on);
            }
        }

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawIndicator();
        if (mEnabled) {
            canvas.drawArc(mRectF, -90, mProgress * 360, false, mPaint);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        int size = w > h ? h : w;
        mWidth = size;
        mIndicatorWidth = mKnobOn.getWidth();

        int diff;
        if (w > h) {
            diff = (w - h) / 2;
            mRectF = new RectF(STROKE_WIDTH + diff, STROKE_WIDTH,
                    w - STROKE_WIDTH - diff, h - STROKE_WIDTH);
        } else {
            diff = (h - w) / 2;
            mRectF = new RectF(STROKE_WIDTH, STROKE_WIDTH + diff,
                    w - STROKE_WIDTH, h - STROKE_WIDTH - diff);
        }

        mProgressTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * TEXT_SIZE);
        mProgressTV.setPadding(0, (int) (size * TEXT_PADDING), 0, 0);
        mProgressTV.setVisibility(View.VISIBLE);
        mLabelTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * LABEL_SIZE);
        mLabelTV.setPadding(0, (int) (size * LABEL_PADDING), 0, 0);
        mLabelTV.setLayoutParams(new LinearLayout.LayoutParams((int) (w * LABEL_WIDTH),
                LayoutParams.WRAP_CONTENT));
        mLabelTV.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mOn) {
                    mLastX = event.getX();
                    mLastY = event.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mOn) {
                    float x = event.getX();
                    float y = event.getY();
                    float center = mWidth / 2;
                    if (mMoved || (x - center) * (x - center) + (y - center) * (y - center)
                            > center * center / 4) {
                        float delta = getDelta(x, y);
                        mOriginalProgress = mProgress + delta / 360;
                        if (mOriginalProgress < 0) {
                            mOriginalProgress = 0;
                        } else if (mOriginalProgress > 100) {
                            mOriginalProgress = 100;
                        }
                        setProgress(mOriginalProgress, true);
                        mMoved = true;
                    }
                    mLastX = x;
                    mLastY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!mMoved) {
                    if (mOnKnobChangeListener == null
                            || mOnKnobChangeListener.onSwitchChanged(this, !mOn)) {
                        if (mEnabled) {
                            setOn(!mOn, true);
                            invalidate();
                        }
                    }
                }
                mMoved = false;
                break;
            default:
                break;
        }
        return true;
    }

    private float getDelta(float x, float y) {
        float angle = angle(x, y);
        float oldAngle = angle(mLastX, mLastY);
        float delta = angle - oldAngle;
        if (delta >= 180.0f) {
            delta = -oldAngle;
        } else if (delta <= -180.0f) {
            delta = 360 - oldAngle;
        }
        return delta;
    }

    private float angle(float x, float y) {
        float center = mWidth / 2.0f;
        x -= center;
        y -= center;

        if (x == 0.0f) {
            if (y > 0.0f) {
                return 180.0f;
            } else {
                return 0.0f;
            }
        }

        float angle = (float) (Math.atan(y / x) / Math.PI * 180.0);
        if (x > 0.0f) {
            angle += 90;
        } else {
            angle += 270;
        }
        return angle;
    }
}
