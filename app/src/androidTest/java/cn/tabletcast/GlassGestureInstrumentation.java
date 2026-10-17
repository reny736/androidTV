package cn.tabletcast;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;

/** Device-level check of the real slider gesture, range semantics and unchanged touch target. */
public final class GlassGestureInstrumentation extends Instrumentation {
    MainActivity activity;
    LiquidSlider slider;
    int initialWidth, initialHeight;
    long downTime;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(intent);
            waitForIdleSync();
            runOnMainSync(() -> {
                slider = locate(activity.getWindow().getDecorView());
                require(slider != null, "Volume slider missing");
                initialWidth = slider.getWidth(); initialHeight = slider.getHeight();
                require(slider.getBackground() == null, "Slider must not draw a framework ripple circle");
                activity.setMediaVolume(40); slider.setProgress(activity.mediaVolume());
            });
            screenshot("glass-volume-idle");
            runOnMainSync(() -> { downTime = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, .4f); });
            SystemClock.sleep(350);
            runOnMainSync(() -> {
                require(Math.abs(slider.visualScale - 1.10f) < .001f, "Held scale must settle to 1.10");
                require(slider.getWidth() == initialWidth && slider.getHeight() == initialHeight, "Touch target must stay stable");
                require(slider.capsule.width() * 1.10f < slider.getWidth(), "Expanded capsule must retain rounded ends");
            });
            screenshot("glass-volume-held");
            runOnMainSync(() -> {
                touch(MotionEvent.ACTION_MOVE, .75f);
                require(slider.getProgress() >= 70 && slider.getProgress() <= 80, "Drag must update native volume range");
                int max = activity.audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                require(Math.abs(activity.mediaVolume() - slider.getProgress()) <= Math.ceil(50f / max), "Actual media volume must follow drag");
                activity.updateControls();
                require(slider.getProgress() >= 70 && slider.getProgress() <= 80, "Refresh must not reset held volume");
                touch(MotionEvent.ACTION_UP, .75f);
            });
            SystemClock.sleep(500);
            runOnMainSync(() -> {
                require(Math.abs(slider.visualScale - 1) < .001f, "Released scale must return to 1");
                AccessibilityNodeInfo info = slider.createAccessibilityNodeInfo();
                require(info.getRangeInfo() != null, "Accessibility range must be available");
                require(info.getRangeInfo().getMax() == 100, "Accessibility range must be 0..100");
                downTime = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, .6f);
            });
            SystemClock.sleep(80);
            runOnMainSync(() -> touch(MotionEvent.ACTION_CANCEL, .6f));
            SystemClock.sleep(500);
            runOnMainSync(() -> {
                require(Math.abs(slider.visualScale - 1) < .001f, "Cancelled drag must reset spring");
                require(activity.layout.getWidth() > activity.layout.getHeight(), "UI must stay landscape");
                activity.setMediaVolume(0);
                require(activity.mediaVolume() == 0, "Zero must mute media output");
                activity.setMediaVolume(100);
                require(activity.mediaVolume() == 100, "Maximum must restore media output");
                activity.setMediaVolume(40); slider.setProgress(activity.mediaVolume());
            });
            screenshot("glass-volume-released");
            checkTimeline();
            result.putString("result", "PASS");
            result.putString("checked", "volume + windowed/fullscreen timeline: 1.10 held scale, 1.00 release/cancel; drag updates real gain/seek; cancel does not seek; preview time; fixed hit area; native range accessibility; landscape");
            result.putInt("touch_width", initialWidth); result.putInt("touch_height", initialHeight);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL"); result.putString("error", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    LiquidSlider locate(View view) {
        if (view instanceof LiquidSlider && "音量".contentEquals(view.getContentDescription())) return (LiquidSlider) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LiquidSlider found = locate(group.getChildAt(i)); if (found != null) return found;
            }
        } return null;
    }
    void checkTimeline() throws Exception {
        File demo = new File(getTargetContext().getExternalFilesDir(null), "gesture-demo.mp4");
        require(demo.exists(), "Push gesture-demo.mp4 to the target external files directory first");
        runOnMainSync(() -> {
            activity.player.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(demo)));
            activity.player.prepare(); activity.player.pause(); activity.showVideo();
        });
        boolean[] ready = {false};
        for (int i = 0; i < 40 && !ready[0]; i++) {
            SystemClock.sleep(100);
            runOnMainSync(() -> ready[0] = activity.duration() > 0 && activity.player.isCurrentMediaItemSeekable()
                    && activity.player.getPlaybackState() == androidx.media3.common.Player.STATE_READY);
        }
        require(ready[0], "Local test video is not seekable");
        runOnMainSync(() -> { activity.player.seekTo(3000); activity.updateControls(); slider = (LiquidSlider) activity.timeline; });
        SystemClock.sleep(200); screenshot("glass-progress-idle");
        runOnMainSync(() -> { downTime = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, .1f); });
        SystemClock.sleep(350);
        runOnMainSync(() -> {
            require(Math.abs(slider.visualScale - 1.10f) < .001f, "Timeline held scale must be 1.10");
            touch(MotionEvent.ACTION_MOVE, .65f);
            require(activity.seeking, "Timeline drag must enter preview mode");
            require(activity.player.getCurrentPosition() < 5000, "Dragging must preview before committing seek");
            require(activity.position.getText().toString().startsWith("00:19"), "Timeline must preview requested time");
        });
        screenshot("glass-progress-held");
        runOnMainSync(() -> touch(MotionEvent.ACTION_UP, .65f));
        SystemClock.sleep(500);
        runOnMainSync(() -> {
            require(Math.abs(slider.visualScale - 1) < .001f, "Timeline release must return scale to 1");
            require(Math.abs(activity.player.getCurrentPosition() - 19500) < 1000, "Release must seek to requested time");
            AccessibilityNodeInfo info = slider.createAccessibilityNodeInfo();
            require(info.getRangeInfo() != null && info.getRangeInfo().getMax() == 1000, "Timeline accessibility range missing");
            downTime = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, .65f); touch(MotionEvent.ACTION_MOVE, .3f);
            touch(MotionEvent.ACTION_CANCEL, .3f);
        });
        SystemClock.sleep(500);
        runOnMainSync(() -> {
            require(Math.abs(activity.player.getCurrentPosition() - 19500) < 1000, "Cancelled scrub must not seek");
            require(Math.abs(slider.visualScale - 1) < .001f && !activity.seeking, "Cancel must leave preview and reset spring");
            activity.setFullscreen(true);
        });
        SystemClock.sleep(300);
        runOnMainSync(() -> { slider = (LiquidSlider) activity.overlayTimeline; downTime = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, .65f); });
        SystemClock.sleep(350);
        runOnMainSync(() -> {
            require(Math.abs(slider.visualScale - 1.10f) < .001f, "Fullscreen timeline held scale must be 1.10");
            require(slider.capsule.width() * 1.10f < slider.getWidth(), "Fullscreen expanded capsule must not be clipped");
            touch(MotionEvent.ACTION_MOVE, .45f);
        });
        screenshot("glass-fullscreen-progress-held");
        runOnMainSync(() -> touch(MotionEvent.ACTION_UP, .45f));
        SystemClock.sleep(500);
        runOnMainSync(() -> {
            require(Math.abs(activity.player.getCurrentPosition() - 13500) < 1000, "Fullscreen timeline must seek");
            require(Math.abs(slider.visualScale - 1) < .001f, "Fullscreen timeline spring must settle");
            activity.stopPlayback();
            require(!activity.timeline.isEnabled() && !activity.overlayTimeline.isEnabled(), "Stopped timeline must be disabled");
        });
    }
    void touch(int action, float fraction) {
        float width = slider.getWidth() - slider.getPaddingLeft() - slider.getPaddingRight();
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                slider.getPaddingLeft() + width * fraction, slider.getHeight() / 2f, 0);
        try { slider.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    void screenshot(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(150);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        require(bitmap != null, "Screenshot unavailable");
        File file = new File(getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
