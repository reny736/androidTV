package cn.tabletcast;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

/** Full-height player fills the left 75%; glass controls occupy the remaining 25%. */
final class CastLayout extends ViewGroup {
    private final View backdrop;
    private View stage, sidebar;
    private boolean fullscreen;
    private int split, stageWidth, stageHeight;
    CastLayout(Context context) {
        super(context); setBackgroundColor(Color.WHITE);
        backdrop = new GlassUi.Backdrop(context, false); addView(backdrop);
    }
    void setContent(View stage, View sidebar) {
        this.stage = stage; this.sidebar = sidebar; addView(stage); addView(sidebar);
    }
    void setFullscreen(boolean value) {
        fullscreen = value; sidebar.setVisibility(value ? GONE : VISIBLE);
        backdrop.setVisibility(value ? GONE : VISIBLE); requestLayout();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        int availableWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int availableHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom());
        split = StageGeometry.playerWidth(availableWidth, false);
        stageWidth = StageGeometry.playerWidth(availableWidth, fullscreen);
        stageHeight = availableHeight;
        stage.measure(MeasureSpec.makeMeasureSpec(stageWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(stageHeight, MeasureSpec.EXACTLY));
        int sideWidth = availableWidth - split;
        sidebar.measure(MeasureSpec.makeMeasureSpec(sideWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY));
        backdrop.measure(MeasureSpec.makeMeasureSpec(sideWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = getPaddingLeft(), y = getPaddingTop();
        int width = getWidth() - x - getPaddingRight(), height = getHeight() - y - getPaddingBottom();
        int sx = x, sy = y;
        stage.layout(sx, sy, sx + stageWidth, sy + stageHeight);
        sidebar.layout(x + split, y, x + width, y + height); backdrop.layout(x + split, y, x + width, y + height);
    }
}
