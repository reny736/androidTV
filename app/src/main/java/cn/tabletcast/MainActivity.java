package cn.tabletcast;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.*;
import android.os.*;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.ui.PlayerView;

/** Landscape receiver with persistent controls and an optional full-screen stage. */
public class MainActivity extends Activity {
    static final int INK = 0xfff4f7f2, MUTED = 0xffc4cfca, ACCENT = 0xfff0f5ec;
    final Handler main = new Handler(Looper.getMainLooper());
    ExoPlayer player;
    android.media.AudioManager audioManager;
    boolean adjustingVolume;
    PlayerView video;
    CastLayout layout;
    FrameLayout stage;
    LinearLayout empty, overlay;
    View ambient;
    TextView name, network, status, hint, emptyTitle, emptyHint, position, format, overlayTime;
    GlassUi.Button play, backward, forward, stop, overlayPlay, overlayBack, overlayNext;
    SeekBar timeline, volume, overlayTimeline;
    DlnaServer receiver;
    String deviceName, currentUri = "", metadata = "", lastError = "";
    ConnectivityManager connectivity;
    ConnectivityManager.NetworkCallback networkCallback;
    boolean destroyed, mediaVisible, isFullscreen, seeking;
    final Runnable hideOverlay = () -> { if (isFullscreen && mediaVisible) overlay.setVisibility(View.GONE); };
    final Runnable refresh = new Runnable() {
        @Override public void run() { if (!destroyed) { updateControls(); main.postDelayed(this, 500); } }
    };
    final Runnable reconnect = () -> { if (!destroyed) restartReceiver(); };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // Migrate the old default, retaining explicitly customized names.
        deviceName = DeviceNames.forV2(getPreferences(0).getString("name", "AndroidTV"));
        getPreferences(0).edit().putString("name", deviceName).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        player = new ExoPlayer.Builder(this).setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(20000, 90000, 1500, 3000).setTargetBufferBytes(96 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        buildInterface();
        player.addListener(new Player.Listener() {
            @Override public void onEvents(Player p, Player.Events events) {
                updateControls(); if (receiver != null) receiver.changed();
            }
            @Override public void onPlayerError(PlaybackException error) {
                lastError = error.getErrorCodeName();
                android.util.Log.w("TabletCast", "Playback failed: " + lastError);
                showWaiting(error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                        ? "视频地址暂时不可用，请在手机重新投屏。"
                        : "视频暂时无法播放，请检查网络或更换清晰度。");
                if (receiver != null) receiver.changed();
            }
        });
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            void changed() { main.removeCallbacks(reconnect); main.postDelayed(reconnect, 800); }
            @Override public void onAvailable(Network n) { changed(); }
            @Override public void onLost(Network n) { changed(); }
        };
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), networkCallback);
        restartReceiver(); main.post(refresh);
    }

    void buildInterface() {
        layout = new CastLayout(this);
        stage = new FrameLayout(this); stage.setContentDescription("播放器窗口");
        GlassUi.round(stage, 0, 0xff1e2628);
        video = (PlayerView) getLayoutInflater().inflate(R.layout.video_surface, stage, false);
        video.setPlayer(player); video.setUseController(false);
        video.setVisibility(View.INVISIBLE);
        video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        stage.addView(video, new FrameLayout.LayoutParams(-1, -1));
        ambient = new GlassUi.Backdrop(this, true);
        stage.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        empty = column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        GlassUi.Icon cast = new GlassUi.Icon(this, "cast", ACCENT);
        LinearLayout castGlass = column(); castGlass.setGravity(Gravity.CENTER);
        castGlass.setBackground(new GlassUi.Surface(this, 28, false, false));
        castGlass.addView(cast, new LinearLayout.LayoutParams(dp(44), dp(44)));
        empty.addView(castGlass, new LinearLayout.LayoutParams(dp(88), dp(88)));
        emptyTitle = text("等待投屏", 30, INK); emptyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        add(empty, emptyTitle, 22);
        emptyHint = text("从手机发起，让精彩在这里播放", 15, MUTED); add(empty, emptyHint, 10);
        stage.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        stage.setOnClickListener(v -> { if (isFullscreen) revealOverlay(); });
        video.setOnClickListener(v -> { if (isFullscreen) revealOverlay(); });

        ScrollView sidebar = new ScrollView(this); sidebar.setContentDescription("投屏功能区");
        sidebar.setFillViewport(true); sidebar.setVerticalScrollBarEnabled(false);
        LinearLayout panel = column(); panel.setPadding(dp(22), dp(22), dp(22), dp(20));
        sidebar.addView(panel, new ScrollView.LayoutParams(-1, -1));
        LinearLayout heading = row(); heading.addView(new GlassUi.Icon(this, "cast", ACCENT), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView title = text("投屏控制", 22, INK); title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2); titleParams.leftMargin = dp(10);
        heading.addView(title, titleParams); panel.addView(heading);
        status = text("正在连接…", 13, MUTED); status.setGravity(Gravity.START); addWide(panel, status, 9);

        LinearLayout device = glassPanel(); TextView label = text("设备名称", 12, MUTED); label.setGravity(Gravity.START); device.addView(label);
        LinearLayout nameRow = row(); name = text(deviceName, 23, INK);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END);
        name.setAutoSizeTextTypeUniformWithConfiguration(13, 23, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        name.setTypeface(null, android.graphics.Typeface.BOLD); nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        GlassUi.Button edit = button("", "修改", false, this::rename); edit.setContentDescription("修改设备名称");
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(68), dp(48)); editParams.leftMargin = dp(6);
        nameRow.addView(edit, editParams); addWide(device, nameRow, 4);
        network = text("正在连接局域网…", 12, MUTED); network.setGravity(Gravity.START); addWide(device, network, 6);
        addWide(panel, device, 18);
        addWide(panel, button("expand", "全屏播放", true, () -> setFullscreen(true)), 16, 50);

        LinearLayout playback = glassPanel(), playRow = row();
        backward = button("back15", "", false, () -> skip(-15000));
        play = button("play", "", true, this::togglePlayback);
        forward = button("next15", "", false, () -> skip(15000));
        backward.setContentDescription("后退15秒"); forward.setContentDescription("前进15秒"); play.setContentDescription("播放");
        addWeighted(playRow, backward, 52, 0); addWeighted(playRow, play, 52, 8); addWeighted(playRow, forward, 52, 8);
        playback.addView(playRow); position = text("00:00  /  00:00", 12, MUTED); addWide(playback, position, 13);
        timeline = seekBar("播放进度", 1000, true); addWide(playback, timeline, 0, 48);
        LinearLayout soundRow = row(); soundRow.addView(new GlassUi.Icon(this, "volume", MUTED), new LinearLayout.LayoutParams(dp(21), dp(21)));
        soundRow.setClipChildren(false); soundRow.setClipToPadding(false); playback.setClipChildren(false);
        volume = seekBar("音量", 100, false); volume.setProgress(mediaVolume());
        LinearLayout.LayoutParams soundParams = new LinearLayout.LayoutParams(0, dp(48), 1); soundParams.leftMargin = dp(7);
        soundRow.addView(volume, soundParams); playback.addView(soundRow);
        stop = button("stop", "结束播放", false, this::stopPlayback); addWide(playback, stop, 4, 48); addWide(panel, playback, 14);
        hint = text("手机与平板连接同一 Wi-Fi，\n在视频 App 中选择此设备。", 13, MUTED);
        hint.setGravity(Gravity.START); hint.setLineSpacing(dp(3), 1); addWide(panel, hint, 18);
        addWide(panel, button("refresh", "重新发现设备", false, this::restartReceiver), 12, 48);
        format = text("原始画质 · 不转码", 11, MUTED); format.setGravity(Gravity.START); addWide(panel, format, 12);
        buildOverlay(); layout.setContent(stage, sidebar); setContentView(layout);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.displayCutout());
                layout.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } return insets;
        }); immersive();
    }

    void buildOverlay() {
        overlay = column(); overlay.setPadding(dp(18), dp(8), dp(18), dp(8));
        overlay.setBackground(new GlassUi.Surface(this, 28, true, false));
        LinearLayout controls = row();
        overlayBack = button("back15", "", false, () -> skip(-15000));
        overlayPlay = button("play", "", false, this::togglePlayback);
        overlayNext = button("next15", "", false, () -> skip(15000));
        overlayBack.setContentDescription("全屏后退15秒"); overlayNext.setContentDescription("全屏前进15秒");
        overlayPlay.setContentDescription("全屏播放暂停");
        for (GlassUi.Button b : new GlassUi.Button[]{overlayBack, overlayPlay, overlayNext}) {
            b.dark = true; b.refreshSurface(); controls.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        overlayTime = text("00:00 / 00:00", 13, Color.WHITE); controls.addView(overlayTime, new LinearLayout.LayoutParams(0, -2, 1));
        GlassUi.Button exit = button("collapse", "退出全屏", false, () -> setFullscreen(false)); exit.dark = true; exit.refreshSurface();
        controls.addView(exit, new LinearLayout.LayoutParams(dp(126), dp(48))); overlay.addView(controls);
        overlayTimeline = seekBar("全屏播放进度", 1000, true); addWide(overlay, overlayTimeline, 0, 48);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(600), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.setMargins(dp(20), dp(12), dp(20), dp(24)); stage.addView(overlay, p); overlay.setVisibility(View.GONE);
    }

    void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else getWindow().getDecorView().setSystemUiVisibility(5894);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) immersive(); }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); immersive(); }
    TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setFontFeatureSettings("tnum"); return t;
    }
    int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    LinearLayout glassPanel() {
        LinearLayout l = column(); l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(new GlassUi.Surface(this, 24, false, false)); return l;
    }
    GlassUi.Button button(String icon, String label, boolean primary, Runnable action) {
        GlassUi.Button b = new GlassUi.Button(this, icon, label, primary);
        b.setOnClickListener(v -> { action.run(); if (isFullscreen) revealOverlay(); }); return b;
    }
    void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(top); parent.addView(view, p);
    }
    void addWide(LinearLayout parent, View view, int top) { addWide(parent, view, top, -2); }
    void addWide(LinearLayout parent, View view, int top, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height)); p.topMargin = dp(top); parent.addView(view, p);
    }
    void addWeighted(LinearLayout parent, View v, int h, int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(h), 1); p.leftMargin = dp(margin); parent.addView(v, p);
    }
    SeekBar seekBar(String description, int max, boolean progress) {
        SeekBar bar = new LiquidSlider(this, progress); bar.setMax(max); bar.setContentDescription(description);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x40ffffff)); bar.setPadding(dp(8), 0, dp(8), 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int value, boolean user) {
                if (user && !progress) { setMediaVolume(value); if (receiver != null) receiver.changed(); }
                if (user && progress && duration() > 0) {
                    if (seeking) {
                        String preview = shortTime(duration() * value / 1000) + "  /  " + shortTime(duration());
                        assign(position, preview); assign(overlayTime, preview);
                    } else player.seekTo(duration() * value / 1000); // Keyboard / accessibility action.
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { if (progress) seeking = true; else adjustingVolume = true; main.removeCallbacks(hideOverlay); }
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (progress && duration() > 0 && !((LiquidSlider) s).cancelledTouch)
                    player.seekTo(duration() * s.getProgress() / 1000);
                seeking = false; adjustingVolume = false; updateControls(); if (isFullscreen) revealOverlay();
            }
        }); return bar;
    }
    void rename() {
        EditText input = new EditText(this); input.setSingleLine(); input.setText(deviceName);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        LinearLayout wrapper = column(); wrapper.setPadding(dp(24), dp(8), dp(24), 0); wrapper.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("设备名称").setView(wrapper)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim(); if (value.isEmpty()) { input.setError("请输入设备名称"); return; }
            deviceName = value; getPreferences(0).edit().putString("name", value).apply();
            name.setText(value); restartReceiver(); dialog.dismiss();
        }));
        dialog.show();
        if (dialog.getWindow() != null) {
            android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable();
            base.setColor(0xff303a34); base.setCornerRadius(dp(28));
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{base, new GlassUi.Surface(this, 28, false, false)}));
        }
    }
    void restartReceiver() {
        if (receiver != null) receiver.close(); receiver = null;
        String ip = DlnaServer.localAddress(this);
        if (ip == null) { network.setText(R.string.no_network); status.setText("○  尚未连接网络"); hint.setText("连接 Wi-Fi 后，点击重新发现设备。"); return; }
        network.setText(getString(R.string.network, ip));
        try {
            receiver = new DlnaServer(this, ip); receiver.startReceiver();
            if (lastError.isEmpty()) hint.setText("手机与平板连接同一 Wi-Fi，\n在视频 App 中选择此设备。"); updateControls();
        } catch (Exception e) {
            if (receiver != null) receiver.close(); receiver = null;
            status.setText("○  接收服务未启动"); hint.setText("接收服务启动失败，请重新发现设备。");
        }
    }
    void setMedia(String uri, String meta) {
        String scheme = Uri.parse(uri).getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) throw new IllegalArgumentException("Unsupported URI");
        currentUri = uri; metadata = meta; lastError = "";
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); showVideo();
    }
    void showVideo() {
        GlassUi.round(stage, 0, Color.BLACK);
        mediaVisible = true; empty.setVisibility(View.GONE); ambient.setVisibility(View.GONE); video.setVisibility(View.VISIBLE);
        updateControls(); if (isFullscreen) revealOverlay();
    }
    void showWaiting(String message) {
        mediaVisible = false; setFullscreen(false); video.setVisibility(View.INVISIBLE);
        empty.setVisibility(View.VISIBLE); ambient.setVisibility(View.VISIBLE);
        emptyTitle.setText(lastError.isEmpty() ? "等待投屏" : "暂时无法播放");
        emptyHint.setText(lastError.isEmpty() ? "从手机发起，让精彩在这里播放" : "请在手机重新选择视频");
        hint.setText(message); updateControls();
    }
    void stopPlayback() { player.stop(); lastError = ""; showWaiting("已结束播放，可以从手机再次投屏。"); if (receiver != null) receiver.changed(); }
    void togglePlayback() {
        if (!mediaVisible) return;
        if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED) player.pause();
        else { if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0); player.play(); } updateControls();
    }
    void skip(long delta) {
        if (!mediaVisible || !player.isCurrentMediaItemSeekable()) return;
        player.seekTo(Math.max(0, Math.min(duration(), player.getCurrentPosition() + delta))); updateControls();
    }
    void setFullscreen(boolean enabled) {
        isFullscreen = enabled; layout.setFullscreen(enabled); GlassUi.round(stage, 0, mediaVisible ? Color.BLACK : 0xff1e2628);
        overlay.setVisibility(enabled ? View.VISIBLE : View.GONE); main.removeCallbacks(hideOverlay);
        if (enabled) revealOverlay(); immersive();
    }
    void revealOverlay() {
        overlay.setVisibility(View.VISIBLE); main.removeCallbacks(hideOverlay);
        if (mediaVisible && player.isPlaying()) main.postDelayed(hideOverlay, 3500);
    }
    void updateControls() {
        if (play == null) return;
        boolean canSeek = mediaVisible && player.isCurrentMediaItemSeekable() && duration() > 0;
        boolean playing = mediaVisible && player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED;
        play.setIcon(playing ? "pause" : "play");
        String actionLabel = playing ? "暂停" : "播放";
        if (!TextUtils.equals(play.getContentDescription(), actionLabel)) play.setContentDescription(actionLabel);
        overlayPlay.setIcon(playing ? "pause" : "play");
        play.setEnabled(mediaVisible); stop.setEnabled(mediaVisible); overlayPlay.setEnabled(mediaVisible);
        backward.setEnabled(canSeek); forward.setEnabled(canSeek); overlayBack.setEnabled(canSeek); overlayNext.setEnabled(canSeek);
        timeline.setEnabled(canSeek); overlayTimeline.setEnabled(canSeek);
        long pos = mediaVisible ? Math.max(0, player.getCurrentPosition()) : 0, length = mediaVisible ? duration() : 0;
        if (!seeking) {
            String times = shortTime(pos) + "  /  " + shortTime(length); assign(position, times); assign(overlayTime, times);
            int v = length > 0 ? (int) (pos * 1000 / length) : 0; timeline.setProgress(v); overlayTimeline.setProgress(v);
        }
        if (!adjustingVolume) volume.setProgress(mediaVolume());
        if (receiver != null) {
            String state = !lastError.isEmpty() ? "播放遇到问题" : !mediaVisible ? "已就绪 · 等待连接"
                    : player.getPlaybackState() == Player.STATE_BUFFERING ? "正在缓冲…"
                    : player.getPlaybackState() == Player.STATE_ENDED ? "播放已结束" : playing ? "正在播放" : "已暂停";
            assign(status, "●  " + state);
        }
        VideoSize size = player.getVideoSize(); assign(format, mediaVisible && size.width > 0
                ? size.width + " × " + size.height + " · 原始画质" : "原始画质 · 不转码");
    }
    int mediaVolume() {
        if (audioManager.isVolumeFixed()) return Math.round(player.getVolume() * 100);
        int max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        return max > 0 ? Math.round(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100f / max) : 0;
    }
    void setMediaVolume(int percent) {
        int value = Math.max(0, Math.min(100, percent));
        if (audioManager.isVolumeFixed()) {
            player.setVolume(value / 100f);
        } else {
            int max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(value * max / 100f), 0);
            player.setVolume(1f);
        }
    }
    void assign(TextView view, String value) { if (!TextUtils.equals(view.getText(), value)) view.setText(value); }
    static String shortTime(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }
    String transportState() {
        if (!lastError.isEmpty()) return "STOPPED";
        if (player.getPlaybackState() == Player.STATE_BUFFERING) return "TRANSITIONING";
        if (player.getPlaybackState() == Player.STATE_ENDED || player.getPlaybackState() == Player.STATE_IDLE) return "STOPPED";
        return player.getPlayWhenReady() ? "PLAYING" : "PAUSED_PLAYBACK";
    }
    long duration() { long d = player.getDuration(); return d == C.TIME_UNSET ? 0 : Math.max(0, d); }
    @Override public void onBackPressed() { if (isFullscreen) setFullscreen(false); else if (mediaVisible) stopPlayback(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null);
        if (networkCallback != null) connectivity.unregisterNetworkCallback(networkCallback);
        if (receiver != null) receiver.close(); video.setPlayer(null); player.release(); super.onDestroy();
    }
}
