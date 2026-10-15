package com.drive.axleweightcalc;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * High-performance animated visualizer for trailer tandem sliding.
 * Features:
 *  1. Smooth truck/trailer translation & frame shifting.
 *  2. Animated road parallax dashes moving under the wheels.
 *  3. Pulsing directional vector arrows indicating the exact slide direction.
 *  4. 2-second pause at the destination before repeating.
 */
public class TandemMotionView extends FrameLayout {

    private ImageView trailerImageView;
    private MotionOverlayView overlayView;
    private TextView badgeView;

    private ValueAnimator motionAnimator;
    private float animationProgress = 0f; // 0.0 to 1.0
    private int currentDirection = 0; // +1 = forward, -1 = backward, 0 = static

    public TandemMotionView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public TandemMotionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TandemMotionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Trailer Image Layer
        trailerImageView = new ImageView(context);
        trailerImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        trailerImageView.setAdjustViewBounds(true);
        trailerImageView.setImageResource(R.drawable.trailer_m);
        LayoutParams imgParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        imgParams.gravity = Gravity.CENTER;
        addView(trailerImageView, imgParams);

        // 2. Motion & Parallax Overlay Layer (Road dashes + Directional arrows)
        overlayView = new MotionOverlayView(context);
        LayoutParams overlayParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(overlayView, overlayParams);

        // 3. Direction Pill Badge
        badgeView = new TextView(context);
        badgeView.setTextSize(12);
        badgeView.setTypeface(null, android.graphics.Typeface.BOLD);
        badgeView.setTextColor(Color.WHITE);
        badgeView.setPadding(dp(10), dp(4), dp(10), dp(4));
        badgeView.setVisibility(GONE);
        LayoutParams badgeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        badgeParams.topMargin = dp(4);
        addView(badgeView, badgeParams);
    }

    /**
     * Updates the movement direction and starts the parallax motion animation.
     *
     * @param deltaHoles > 0 for Forward (+), < 0 for Backward (-), 0 for No Move
     */
    public void setMotionDirection(int deltaHoles) {
        if (deltaHoles > 0) {
            currentDirection = 1; // (+) -> SLIDE TANDEMS BACK (move rearwards / right)
            badgeView.setVisibility(VISIBLE);
            badgeView.setText("► SLIDE TANDEMS BACK ►");
            badgeView.setBackground(createBadgeDrawable(Color.parseColor("#E65100")));
            startAnimationLoop();
        } else if (deltaHoles < 0) {
            currentDirection = -1; // (-) -> SLIDE TANDEMS FORWARD (move forwards / left)
            badgeView.setVisibility(VISIBLE);
            badgeView.setText("◄ SLIDE TANDEMS FORWARD ◄");
            badgeView.setBackground(createBadgeDrawable(Color.parseColor("#1565C0")));
            startAnimationLoop();
        } else {
            currentDirection = 0;
            badgeView.setVisibility(GONE);
            stopAnimation();
            trailerImageView.setImageResource(R.drawable.trailer_m);
            trailerImageView.setTranslationX(0);
            overlayView.invalidate();
        }
    }

    private void startAnimationLoop() {
        stopAnimation();

        // 2400ms smooth motion + 2000ms pause = 4400ms total cycle
        motionAnimator = ValueAnimator.ofFloat(0f, 1f);
        motionAnimator.setDuration(4400);
        motionAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        motionAnimator.setRepeatCount(ValueAnimator.INFINITE);

        motionAnimator.addUpdateListener(animation -> {
            float totalFraction = animation.getAnimatedFraction(); // 0 to 1 over 4400ms
            
            // Motion phase: first 54.5% of cycle (~2400ms)
            // Pause phase: remaining 45.5% of cycle (~2000ms)
            float moveProgress;
            if (totalFraction < 0.545f) {
                moveProgress = totalFraction / 0.545f; // 0.0 to 1.0 smoothly during motion
            } else {
                moveProgress = 1.0f; // Completely stops at destination for 2 seconds!
            }

            animationProgress = moveProgress;

            // Shift trailer image frame evenly across movement
            updateTrailerVisual(moveProgress);

            overlayView.setProgress(moveProgress, totalFraction, currentDirection);
        });

        motionAnimator.start();
    }

    private void updateTrailerVisual(float progress) {
        if (currentDirection > 0) {
            // (+): trailer_f -> trailer_m -> trailer_r
            if (progress < 0.333f) {
                trailerImageView.setImageResource(R.drawable.trailer_f);
            } else if (progress < 0.666f) {
                trailerImageView.setImageResource(R.drawable.trailer_m);
            } else {
                trailerImageView.setImageResource(R.drawable.trailer_r);
            }
            float shift = (0.5f - progress) * dp(6);
            trailerImageView.setTranslationX(shift);
        } else if (currentDirection < 0) {
            // (-): trailer_r -> trailer_m -> trailer_f
            if (progress < 0.333f) {
                trailerImageView.setImageResource(R.drawable.trailer_r);
            } else if (progress < 0.666f) {
                trailerImageView.setImageResource(R.drawable.trailer_m);
            } else {
                trailerImageView.setImageResource(R.drawable.trailer_f);
            }
            float shift = (progress - 0.5f) * dp(6);
            trailerImageView.setTranslationX(shift);
        }
    }

    public void stopAnimation() {
        if (motionAnimator != null) {
            motionAnimator.cancel();
            motionAnimator = null;
        }
        if (trailerImageView != null) {
            trailerImageView.setTranslationX(0);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private android.graphics.drawable.Drawable createBadgeDrawable(int color) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(12));
        return gd;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /**
     * Custom Canvas overlay drawing moving road stripes and animated direction arrows.
     */
    private static class MotionOverlayView extends View {
        private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrowPath = new Path();

        private float moveProgress = 0f;
        private float cycleProgress = 0f;
        private int direction = 0;

        public MotionOverlayView(Context context) {
            super(context);

            roadPaint.setColor(Color.parseColor("#546E7A"));
            roadPaint.setStrokeWidth(dp(2));
            roadPaint.setStyle(Paint.Style.STROKE);

            dashPaint.setColor(Color.parseColor("#FFE082"));
            dashPaint.setStrokeWidth(dp(2.5f));
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeCap(Paint.Cap.ROUND);

            arrowPaint.setStyle(Paint.Style.FILL);
            arrowPaint.setAntiAlias(true);

            arrowGlowPaint.setStyle(Paint.Style.STROKE);
            arrowGlowPaint.setStrokeWidth(dp(1.5f));
            arrowGlowPaint.setAntiAlias(true);
        }

        public void setProgress(float moveProgress, float cycleProgress, int direction) {
            this.moveProgress = moveProgress;
            this.cycleProgress = cycleProgress;
            this.direction = direction;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (direction == 0) return;

            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float roadY = h - dp(8);

            // 1. Draw Road Base Line
            canvas.drawLine(dp(16), roadY, w - dp(16), roadY, roadPaint);

            // 2. Draw Moving Road Dash Stripes (Inverted per user request)
            float dashSpacing = dp(42);
            float roadOffset = ((direction > 0 ? 1 : -1) * moveProgress * dp(42)) % dashSpacing;

            for (float x = dp(16) + roadOffset - dashSpacing; x < w - dp(16) + dashSpacing; x += dashSpacing) {
                if (x >= dp(16) && (x + dp(18)) <= (w - dp(16))) {
                    canvas.drawLine(x, roadY, x + dp(18), roadY, dashPaint);
                }
            }

            // 3. Draw Flowing Directional Chevrons
            // Inverted per user request:
            // (+) Back -> points LEFT (pointingRight = false)
            // (-) Forward -> points RIGHT (pointingRight = true)
            boolean pointingRight = (direction < 0);
            int arrowColor = direction > 0 ? Color.parseColor("#FB8C00") : Color.parseColor("#1E88E5");
            arrowPaint.setColor(arrowColor);
            arrowGlowPaint.setColor(Color.WHITE);

            // Gentle fade in during movement, soft pulse when stopped at destination
            int alpha;
            if (cycleProgress < 0.545f) {
                alpha = (int) (180 * (cycleProgress / 0.545f));
            } else {
                float pulse = (float) Math.sin((cycleProgress - 0.545f) * Math.PI * 3);
                alpha = (int) (140 + 40 * pulse);
            }
            alpha = Math.max(0, Math.min(220, alpha));
            arrowPaint.setAlpha(alpha);
            arrowGlowPaint.setAlpha((int) (alpha * 0.7f));

            float arrowY = h / 2f + dp(10);
            float arrowSpacing = dp(38);
            // Flow in the direction of the chevrons
            float baseShift = (direction > 0 ? -1 : 1) * moveProgress * dp(16);

            // Draw 3 flowing chevrons
            for (int i = -1; i <= 1; i++) {
                float arrowX = (w / 2f) + (i * arrowSpacing) + baseShift;
                drawChevron(canvas, arrowX, arrowY, dp(12), dp(16), pointingRight);
            }
        }

        private void drawChevron(Canvas canvas, float cx, float cy, float width, float height, boolean pointingRight) {
            arrowPath.reset();
            float halfW = width / 2f;
            float halfH = height / 2f;
            float thickness = dp(4);

            if (pointingRight) {
                arrowPath.moveTo(cx - halfW, cy - halfH);
                arrowPath.lineTo(cx + halfW, cy);
                arrowPath.lineTo(cx - halfW, cy + halfH);
                arrowPath.lineTo(cx - halfW + thickness, cy + halfH);
                arrowPath.lineTo(cx + halfW + thickness, cy);
                arrowPath.lineTo(cx - halfW + thickness, cy - halfH);
            } else {
                arrowPath.moveTo(cx + halfW, cy - halfH);
                arrowPath.lineTo(cx - halfW, cy);
                arrowPath.lineTo(cx + halfW, cy + halfH);
                arrowPath.lineTo(cx + halfW - thickness, cy + halfH);
                arrowPath.lineTo(cx - halfW - thickness, cy);
                arrowPath.lineTo(cx + halfW - thickness, cy - halfH);
            }
            arrowPath.close();

            canvas.drawPath(arrowPath, arrowPaint);
            canvas.drawPath(arrowPath, arrowGlowPaint);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
