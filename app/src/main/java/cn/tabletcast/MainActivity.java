package cn.tabletcast;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import android.net.*;
import android.content.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.ui.PlayerView;
import java.util.*;

public class MainActivity extends Activity {
    final Handler main = new Handler(Looper.getMainLooper());
    ExoPlayer player;
    PlayerView video;
    FrameLayout root;
    LinearLayout waiting;
    TextView status, network, name;
    DlnaServer receiver;
    String deviceName, currentUri="", metadata="", lastError="";
    ConnectivityManager connectivity;
    ConnectivityManager.NetworkCallback networkCallback;
    boolean destroyed;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        deviceName=getPreferences(0).getString("name","小米平板 7");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(248,247,244));
        getWindow().setNavigationBarColor(Color.rgb(248,247,244));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        player=new ExoPlayer.Builder(this).setLoadControl(new DefaultLoadControl.Builder()
            .setBufferDurationsMs(20000,90000,1500,3000).setTargetBufferBytes(96*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        root=new FrameLayout(this); root.setBackgroundColor(Color.rgb(248,247,244));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        video=new PlayerView(this); video.setPlayer(player); video.setControllerShowTimeoutMs(3000);
        video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        waiting=new LinearLayout(this); waiting.setOrientation(LinearLayout.VERTICAL); waiting.setGravity(Gravity.CENTER);
        waiting.setPadding(dp(28),dp(32),dp(28),dp(32)); waiting.setBackgroundColor(Color.rgb(248,247,244));
        ImageView icon=new ImageView(this); icon.setImageResource(cn.tabletcast.R.drawable.ic_cast);
        waiting.addView(icon,new LinearLayout.LayoutParams(dp(72),dp(72)));
        TextView title=text("等待投屏",32,0xff242c29); add(title,24);
        name=text(deviceName+"  ›",19,0xff347567); name.setGravity(Gravity.CENTER); name.setMinHeight(dp(48)); add(name,8);
        name.setContentDescription("修改投屏设备名称"); name.setOnClickListener(v->rename());
        status=text("打开手机视频 App，选择此设备",16,0xff56615b); add(status,18);
        network=text("正在连接局域网…",14,0xff56615b); add(network,10);
        TextView hint=text("手机与平板请连接同一 Wi-Fi",14,0xff56615b); add(hint,28);
        Button retry=new Button(this); retry.setText("重新等待投屏"); retry.setAllCaps(false); add(retry,18); retry.setOnClickListener(v->restartReceiver());
        TextView end=text("结束投屏",16,Color.WHITE); end.setGravity(Gravity.CENTER); end.setPadding(dp(16),0,dp(16),0); end.setBackgroundColor(0xb3242c29);
        FrameLayout.LayoutParams endParams=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.END); endParams.setMargins(dp(16),dp(40),dp(16),0);
        root.addView(end,endParams); end.setVisibility(View.GONE); end.setOnClickListener(v->stopPlayback());
        video.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility->end.setVisibility(waiting.getVisibility()==View.GONE?visibility:View.GONE));
        root.addView(waiting,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state) { if(receiver!=null) receiver.changed(); }
            @Override public void onIsPlayingChanged(boolean playing) { if(receiver!=null) receiver.changed(); }
            @Override public void onPlayerError(PlaybackException error) {
                lastError=error.getErrorCodeName(); android.util.Log.w("TabletCast","Playback failed: "+lastError);
                String hint=error.errorCode==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS?"视频地址暂时不可用\n请在手机重新投屏，或尝试其他视频":"视频暂时无法播放\n请检查网络，或尝试其他清晰度";
                showWaiting(hint); if(receiver!=null)receiver.changed();
            }
        });
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        networkCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){ main.postDelayed(()->{if(!destroyed)restartReceiver();},800); }
            @Override public void onLost(Network n){ main.postDelayed(()->{if(!destroyed)restartReceiver();},800); }
        };
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(),networkCallback);
        restartReceiver();
    }
    TextView text(String value,int size,int color){ TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t; }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void add(View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.topMargin=dp(top);waiting.addView(v,p);}
    void rename(){EditText input=new EditText(this);input.setSingleLine();input.setText(deviceName);new AlertDialog.Builder(this).setTitle("设备名称").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String s=input.getText().toString().trim();if(!s.isEmpty()){deviceName=s.substring(0,Math.min(40,s.length()));getPreferences(0).edit().putString("name",deviceName).apply();name.setText(getString(R.string.device_name,deviceName));restartReceiver();}}).show();}
    void restartReceiver(){
        if(receiver!=null)receiver.close(); receiver=null;
        String ip=DlnaServer.localAddress(this);
        if(ip==null){network.setText(R.string.no_network);status.setText(R.string.connect_network);return;}
        network.setText(getString(R.string.network,ip));
        try{receiver=new DlnaServer(this,ip);receiver.startReceiver();status.setText(R.string.ready);}
        catch(Exception e){if(receiver!=null)receiver.close();receiver=null;status.setText("接收服务启动失败，请点击重试");}
    }
    void setMedia(String uri,String meta){
        Uri parsed=Uri.parse(uri);String scheme=parsed.getScheme();
        if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme))throw new IllegalArgumentException("Unsupported URI");
        currentUri=uri;metadata=meta;lastError="";
        player.setMediaItem(MediaItem.fromUri(uri));player.prepare();showVideo();
    }
    void showVideo(){root.setBackgroundColor(Color.BLACK);waiting.setVisibility(View.GONE);video.setVisibility(View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(5894);video.hideController();}
    void showWaiting(String message){root.setBackgroundColor(Color.rgb(248,247,244));waiting.setVisibility(View.VISIBLE);video.setVisibility(View.GONE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);status.setText(message);}
    void stopPlayback(){player.stop();showWaiting("已结束播放，可以再次投屏");if(receiver!=null)receiver.changed();}
    String transportState(){if(!lastError.isEmpty())return "STOPPED";if(player.getPlaybackState()==Player.STATE_BUFFERING)return "TRANSITIONING";if(player.getPlaybackState()==Player.STATE_ENDED||player.getPlaybackState()==Player.STATE_IDLE)return "STOPPED";return player.getPlayWhenReady()?"PLAYING":"PAUSED_PLAYBACK";}
    long duration(){long d=player.getDuration();return d==C.TIME_UNSET?0:Math.max(0,d);}
    @Override public void onBackPressed(){if(waiting.getVisibility()==View.GONE)stopPlayback();else super.onBackPressed();}
    @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);if(networkCallback!=null)connectivity.unregisterNetworkCallback(networkCallback);if(receiver!=null)receiver.close();video.setPlayer(null);player.release();super.onDestroy();}
}
