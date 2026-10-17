package cn.tabletcast;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** Thin glass capsule shared by volume and playback progress, with native range/input semantics. */
final class LiquidSlider extends SeekBar {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF capsule = new RectF();
    final Path clip = new Path();
    final GlassUi.Surface material;
    final boolean timeline;
    boolean cancelledTouch;
    float visualScale = 1;
    ValueAnimator spring;

    LiquidSlider(Context context, boolean timeline) {
        super(context); material = new GlassUi.Surface(context, 30, false, false);
        this.timeline = timeline;
        // The framework ripple draws a circular hotspot outside our custom capsule.
        setBackground(null);
        setThumb(null); setThumbOffset(0); setSplitTrack(false); setMinimumHeight(dp(48));
    }
    int dp(float n) { return GlassUi.dp(getContext(), n); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // Reserve the 10% expansion inside the stable hit area, including on the long fullscreen bar.
        int inset = Math.max(dp(8), (int) Math.ceil(width * .05f) + dp(3));
        setPadding(inset, getPaddingTop(), inset, getPaddingBottom());
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        float inset = dp(3), halfHeight = dp(timeline ? 4 : 7);
        capsule.set(getPaddingLeft() + inset, getHeight() / 2f - halfHeight,
                getWidth() - getPaddingRight() - inset, getHeight() / 2f + halfHeight);
        canvas.save(); canvas.scale(visualScale, visualScale, getWidth() / 2f, getHeight() / 2f);
        material.setBounds(Math.round(capsule.left), Math.round(capsule.top), Math.round(capsule.right), Math.round(capsule.bottom));
        material.draw(canvas);
        clip.reset(); clip.addRoundRect(capsule, halfHeight, halfHeight, Path.Direction.CW);
        canvas.save(); canvas.clipPath(clip);
        float fraction = getMax() == 0 ? 0 : getProgress() / (float) getMax();
        float end = capsule.left + capsule.width() * fraction;
        paint.setShader(new LinearGradient(0, capsule.top, 0, capsule.bottom,
                new int[]{0xfffefffb, 0xffe7eee7, 0xffc9d9d0}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(capsule.left, capsule.top, end, capsule.bottom, paint);
        paint.setShader(null); paint.setColor(0x65ffffff); paint.setStrokeWidth(dp(.7f));
        if (end > capsule.left + halfHeight)
            canvas.drawLine(capsule.left + halfHeight, capsule.top + dp(2), end - 2, capsule.top + dp(2), paint);
        canvas.restore(); canvas.restore();
    }
    void animateScale(float target) {
        if (spring != null) spring.cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) { visualScale = target; invalidate(); return; }
        float start = visualScale;
        spring = ValueAnimator.ofFloat(0, 1); spring.setDuration(target > 1 ? 280 : 420);
        spring.setInterpolator(new android.view.animation.LinearInterpolator());
        spring.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            // Damped spring: cap the held expansion at 10%, with a soft release undershoot.
            float response = t == 1 ? 1 : (float) (1 - Math.exp(-7.5 * t)
                    * (Math.cos(9 * t) + 7.5 / 9 * Math.sin(9 * t)));
            visualScale = Math.min(1.10f, start + (target - start) * response); invalidate();
        }); spring.start();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelledTouch = false;
                getParent().requestDisallowInterceptTouchEvent(true); animateScale(1.10f); break;
            case MotionEvent.ACTION_CANCEL:
                cancelledTouch = true;
                getParent().requestDisallowInterceptTouchEvent(false); animateScale(1); break;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false); animateScale(1); break;
            default: break;
        }
        return super.onTouchEvent(event);
    }
    @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); setAlpha(enabled ? 1 : .4f); }
    @Override public boolean performClick() { return super.performClick(); }
    @Override protected void onDetachedFromWindow() {
        if (spring != null) spring.cancel(); visualScale = 1; super.onDetachedFromWindow();
    }
}
