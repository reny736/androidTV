package cn.tabletcast;

import android.content.Context;
import android.content.res.ColorStateList;
import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Native glass-inspired material. No video frame readback or full-frame blur in the render loop. */
final class GlassUi {
    private GlassUi() { }
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static void round(View view, int radius, int color) {
        GradientDrawable background = new GradientDrawable(); background.setColor(color);
        background.setCornerRadius(dp(view.getContext(), radius)); view.setBackground(background); view.setClipToOutline(true);
    }

    /** Translucent body, directional rim lighting and a narrow inner reflection. */
    static final class Surface extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path reflectionClip = new Path();
        final float radius, hairline;
        final boolean dark, accent;
        int alpha = 255;
        Surface(Context c, int radius, boolean dark, boolean accent) {
            this.radius = dp(c, radius); hairline = Math.max(1, c.getResources().getDisplayMetrics().density);
            this.dark = dark; this.accent = accent;
        }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds(); RectF r = new RectF(b.left + hairline, b.top + hairline, b.right - hairline, b.bottom - hairline);
            int[] colors = accent ? new int[]{0xfffafcf7, 0xffeaf0e7, 0xfff7faf2}
                    : dark ? new int[]{0xb9515856, 0xbf2b3330, 0xc4434b44}
                    : new int[]{0x644f5854, 0x264b5550, 0x6958604e};
            paint.setAlpha(alpha); paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, colors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, radius, radius, paint);
            if (!accent) {
                canvas.save(); reflectionClip.reset(); reflectionClip.addRoundRect(r, radius, radius, Path.Direction.CW);
                canvas.clipPath(reflectionClip);
                paint.setShader(new RadialGradient(r.right, r.top, Math.max(1, r.width() * .75f),
                        new int[]{0x1fe9eef0, 0x0ae8edec, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(r, paint);
                paint.setShader(new LinearGradient(r.left, r.bottom - r.height() * .4f, r.right, r.bottom,
                        new int[]{0x18d2d1a3, 0x132a7694, 0x13d0b2b5}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, radius, radius, paint); canvas.restore();
            }
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    accent ? new int[]{0xdfffffff, 0x70bcc6bc, 0xb0ffffff}
                            : new int[]{0xcceef0e7, 0x22758179, 0x8bdfedce}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(hairline);
            canvas.drawRoundRect(r, radius, radius, paint);
            r.inset(hairline * 1.4f, hairline * 1.4f);
            paint.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x25ffffff, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            paint.setStrokeWidth(hairline * .55f); canvas.drawRoundRect(r, Math.max(0, radius - hairline), Math.max(0, radius - hairline), paint);
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setAlpha(255);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
    }

    static final class Backdrop extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final boolean stage;
        Backdrop(Context c, boolean stage) { super(c); this.stage = stage; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void glow(Canvas c, float x, float y, float radius, int color) {
            paint.setShader(new RadialGradient(x, y, radius, color, color & 0x00ffffff, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            canvas.drawColor(stage ? 0xff1e2628 : 0xff262e2b);
            glow(canvas, w * .12f, h * .1f, Math.max(w, h) * .65f, stage ? 0x6f7d8587 : 0x625b6264);
            glow(canvas, w * .8f, h * .95f, Math.max(w, h) * .8f, stage ? 0x65495125 : 0x84585f28);
            glow(canvas, w * .87f, h * .22f, Math.max(w, h) * .4f, 0x52413d56);
            glow(canvas, w * .33f, h * .59f, Math.max(w, h) * .42f, 0x64304c5c);
            paint.setShader(null);
            if (stage) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(getContext(), 1)); paint.setColor(0x0dffffff);
                canvas.drawOval(-w * .35f, h * .45f, w * 1.26f, h * 1.6f, paint);
                canvas.drawOval(w * .32f, -h * .7f, w * 1.55f, h * .4f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    static final class Button extends LinearLayout {
        final Icon icon;
        final TextView label;
        final boolean accent;
        boolean dark;
        Button(Context c, String glyph, String title, boolean accent) {
            super(c); this.accent = accent; setOrientation(HORIZONTAL); setGravity(Gravity.CENTER);
            setMinimumHeight(dp(c, 48)); setFocusable(true); setClickable(true);
            setPadding(dp(c, 8), 0, dp(c, 8), 0);
            icon = new Icon(c, glyph, MainActivity.INK);
            if (!glyph.isEmpty()) addView(icon, new LayoutParams(dp(c, title.isEmpty() ? 24 : 19), dp(c, 24)));
            label = new TextView(c); label.setText(title); label.setTextSize(14); label.setTextColor(MainActivity.INK);
            label.setSingleLine(); label.setGravity(Gravity.CENTER); label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (!title.isEmpty()) { LayoutParams p = new LayoutParams(-2, -2); p.leftMargin = glyph.isEmpty() ? 0 : dp(c, 7); addView(label, p); }
            setContentDescription(title); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            refreshSurface();
        }
        void refreshSurface() {
            GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(dp(getContext(), 28));
            setBackground(new RippleDrawable(ColorStateList.valueOf(accent ? 0x20334736 : 0x30ffffff), new Surface(getContext(), 28, dark, accent), mask));
            icon.color = accent ? 0xff202920 : Color.WHITE; icon.invalidate(); label.setTextColor(icon.color);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (isEnabled() && ValueAnimator.areAnimatorsEnabled()) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                    animate().scaleX(.97f).scaleY(.97f).setDuration(120).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                    animate().scaleX(1).scaleY(1).setDuration(280).setInterpolator(new android.view.animation.OvershootInterpolator(.65f)).start();
            }
            return super.onTouchEvent(event);
        }
        void setIcon(String glyph) { if (!glyph.equals(icon.glyph)) { icon.glyph = glyph; icon.invalidate(); } }
        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled); setAlpha(enabled ? 1 : .4f);
        }
        @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
    }

    /** Consistent 24-unit line icons, rendered directly without fonts or remote assets. */
    static final class Icon extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        String glyph;
        int color;
        Icon(Context c, String glyph, int color) { super(c); this.glyph = glyph; this.color = color; }
        void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, p); }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            c.save(); c.translate((getWidth() - s * 24) / 2, (getHeight() - s * 24) / 2); c.scale(s, s);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch (glyph) {
                case "play":
                    p.setStyle(Paint.Style.FILL); path.moveTo(8, 4); path.lineTo(20, 12); path.lineTo(8, 20); path.close(); c.drawPath(path, p); break;
                case "pause":
                    p.setStyle(Paint.Style.FILL); c.drawRoundRect(6, 4, 10, 20, 1, 1, p); c.drawRoundRect(14, 4, 18, 20, 1, 1, p); break;
                case "stop": c.drawRoundRect(5, 5, 19, 19, 3, 3, p); break;
                case "cast":
                    path.moveTo(3, 8); path.lineTo(3, 4); path.lineTo(21, 4); path.lineTo(21, 18); path.lineTo(14, 18); c.drawPath(path, p);
                    c.drawArc(-6, 11, 12, 29, 270, 90, false, p); c.drawArc(-2, 15, 8, 25, 270, 90, false, p);
                    p.setStyle(Paint.Style.FILL); c.drawCircle(3, 20, 1.4f, p); break;
                case "edit":
                    path.moveTo(5, 15); path.lineTo(15, 5); path.lineTo(19, 9); path.lineTo(9, 19); path.lineTo(4, 20); path.close(); c.drawPath(path, p);
                    line(c, 13, 7, 17, 11); break;
                case "expand": case "collapse":
                    for (int i = 0; i < 4; i++) {
                        c.save(); c.rotate(i * 90, 12, 12);
                        if (glyph.equals("expand")) { line(c, 4, 10, 4, 4); line(c, 4, 4, 10, 4); }
                        else { line(c, 4, 9, 9, 9); line(c, 9, 9, 9, 4); }
                        c.restore();
                    } break;
                case "volume":
                    path.moveTo(3, 9); path.lineTo(7, 9); path.lineTo(12, 5); path.lineTo(12, 19); path.lineTo(7, 15); path.lineTo(3, 15); path.close(); c.drawPath(path, p);
                    c.drawArc(10, 7, 20, 17, -55, 110, false, p); c.drawArc(7, 3, 25, 21, -48, 96, false, p); break;
                case "refresh": case "back15": case "next15":
                    boolean next = glyph.equals("next15");
                    if (next) c.scale(-1, 1, 12, 12);
                    c.drawArc(3, 4, 21, 22, -150, 300, false, p);
                    line(c, 4, 4, 4, 9); line(c, 4, 4, 9, 4);
                    if (next) c.scale(-1, 1, 12, 12);
                    if (!glyph.equals("refresh")) {
                        p.setStyle(Paint.Style.FILL); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); p.setTextSize(8); p.setTextAlign(Paint.Align.CENTER); c.drawText("15", 12, 16, p);
                    } break;
                default: break;
            }
            c.restore();
        }
    }
}
