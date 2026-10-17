package cn.tabletcast;

import android.content.Context;
import android.net.*;
import android.net.wifi.WifiManager;
import fi.iki.elonen.NanoHTTPD;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Foreground-only UPnP MediaRenderer. All player access is confined to the UI thread. */
final class DlnaServer extends NanoHTTPD {
    static final String AV="urn:schemas-upnp-org:service:AVTransport:1", RC="urn:schemas-upnp-org:service:RenderingControl:1", CM="urn:schemas-upnp-org:service:ConnectionManager:1";
    static final String PROTOCOL="http-get:*:video/mp4:*,http-get:*:video/mpeg:*,http-get:*:video/x-matroska:*,http-get:*:video/mp2t:*,http-get:*:video/*:*,http-get:*:audio/*:*,http-get:*:application/vnd.apple.mpegurl:*,http-get:*:application/x-mpegURL:*,http-get:*:application/dash+xml:*";
    final MainActivity app; final String ip,uuid; final ScheduledExecutorService worker=Executors.newScheduledThreadPool(3);
    final Map<String,Subscription> subscriptions=new ConcurrentHashMap<>();
    MulticastSocket ssdp; WifiManager.MulticastLock lock; volatile boolean running;
    static class Subscription {String callback,service;long expires;int seq;}
    DlnaServer(MainActivity app,String ip){super("0.0.0.0",0);this.app=app;this.ip=ip;String id=app.getPreferences(0).getString("uuid",null);if(id==null){id=UUID.randomUUID().toString();app.getPreferences(0).edit().putString("uuid",id).apply();}uuid="uuid:"+id;}
    static String localAddress(Context c){
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps==null||(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;LinkProperties p=cm.getLinkProperties(n);if(p!=null)for(LinkAddress a:p.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();}
        return null;
    }
    void startReceiver() throws Exception {
        start(SOCKET_READ_TIMEOUT,false);running=true;
        android.util.Log.i("TabletCast","Receiver listening "+location());
        WifiManager wifi=(WifiManager)app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);lock=wifi.createMulticastLock("tabletcast-discovery");lock.setReferenceCounted(false);lock.acquire();
        ssdp=new MulticastSocket(null);ssdp.setReuseAddress(true);ssdp.bind(new InetSocketAddress(1900));ssdp.setTimeToLive(2);ssdp.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName(ip)));
        ssdp.joinGroup(new InetSocketAddress("239.255.255.250",1900),NetworkInterface.getByInetAddress(InetAddress.getByName(ip)));
        worker.execute(this::listen);worker.scheduleWithFixedDelay(()->announce(true),0,60,TimeUnit.SECONDS);
        worker.scheduleWithFixedDelay(this::notifySubscribers,1,1,TimeUnit.SECONDS);
    }
    List<String> types(){return Arrays.asList("upnp:rootdevice",uuid,"urn:schemas-upnp-org:device:MediaRenderer:1",AV,RC,CM);}
    String usn(String type){return type.equals(uuid)?uuid:uuid+"::"+type;}
    String location(){return "http://"+ip+":"+getListeningPort()+"/device.xml";}
    void send(String message,InetAddress address,int port)throws IOException{byte[] b=message.getBytes(StandardCharsets.UTF_8);ssdp.send(new DatagramPacket(b,b.length,address,port));}
    void announce(boolean alive){if(ssdp==null||ssdp.isClosed())return;try{for(String t:types())send("NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: "+t+"\r\nNTS: ssdp:"+(alive?"alive":"byebye")+"\r\nUSN: "+usn(t)+"\r\n"+(alive?"CACHE-CONTROL: max-age=180\r\nLOCATION: "+location()+"\r\nSERVER: Android/16 UPnP/1.0 TabletCast/0.1\r\n":"")+"\r\n",InetAddress.getByName("239.255.255.250"),1900);}catch(Exception ignored){}}
    void listen(){while(running){try{DatagramPacket p=new DatagramPacket(new byte[8192],8192);ssdp.receive(p);String msg=new String(p.getData(),0,p.getLength(),StandardCharsets.UTF_8);if(!msg.startsWith("M-SEARCH ")||!msg.toLowerCase(Locale.ROOT).contains("ssdp:discover"))continue;String st="";int mx=1;for(String line:msg.split("\r?\n")){if(line.regionMatches(true,0,"ST:",0,3))st=line.substring(3).trim();if(line.regionMatches(true,0,"MX:",0,3))try{mx=Math.min(5,Math.max(1,Integer.parseInt(line.substring(3).trim())));}catch(Exception ignored){}}
            final InetAddress addr=p.getAddress();final int port=p.getPort();for(String t:types())if(st.equals("ssdp:all")||st.equals(t))worker.schedule(()->{try{send("HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=180\r\nEXT:\r\nLOCATION: "+location()+"\r\nSERVER: Android/16 UPnP/1.0 TabletCast/0.1\r\nST: "+t+"\r\nUSN: "+usn(t)+"\r\n\r\n",addr,port);}catch(Exception ignored){}},ThreadLocalRandom.current().nextInt(mx*700),TimeUnit.MILLISECONDS);
        }catch(Exception e){if(!running)return;}}}
    void close(){running=false;announce(false);if(ssdp!=null)ssdp.close();stop();worker.shutdownNow();subscriptions.clear();if(lock!=null&&lock.isHeld())lock.release();}
    static String x(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    String service(String type,String shortName){return "<service><serviceType>"+type+"</serviceType><serviceId>urn:upnp-org:serviceId:"+shortName+"</serviceId><SCPDURL>/"+shortName+".xml</SCPDURL><controlURL>/control/"+shortName+"</controlURL><eventSubURL>/event/"+shortName+"</eventSubURL></service>";}
    String device(){return "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType><friendlyName>"+x(app.deviceName)+"</friendlyName><manufacturer>TabletCast</manufacturer><modelDescription>平板 DLNA 视频接收器</modelDescription><modelName>TabletCast</modelName><modelNumber>0.1</modelNumber><UDN>"+uuid+"</UDN><dlna:X_DLNADOC xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\">DMR-1.50</dlna:X_DLNADOC><serviceList>"+service(AV,"AVTransport")+service(RC,"RenderingControl")+service(CM,"ConnectionManager")+"</serviceList></device></root>";}
    Response xml(String s){return newFixedLengthResponse(Response.Status.OK,"text/xml; charset=utf-8",s);}
    @Override public Response serve(IHTTPSession session){try{
        String path=session.getUri();String method=session.getMethod().name();
        if(path.equals("/device.xml"))return xml(device());
        if(path.endsWith(".xml")){String service=path.contains("AVTransport")?AV:path.contains("RenderingControl")?RC:CM;return xml(scpd(service));}
        if(path.startsWith("/event/"))return subscription(session);
        if(path.startsWith("/control/")&&method.equals("POST")){
            Map<String,String> body=new HashMap<>();session.parseBody(body);String raw=body.getOrDefault("postData","");if(raw.length()>262144)return fault(402,"Invalid Args");
            // Android XML factories do not all implement Xerces feature flags.
            // SOAP has no DTD; reject declarations before parsing on every platform.
            if(raw.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||raw.toUpperCase(Locale.ROOT).contains("<!ENTITY"))return fault(402,"Invalid Args");
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);
            Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            NodeList bodies=doc.getElementsByTagNameNS("*","Body");if(bodies.getLength()==0)return fault(402,"Invalid Args");Element action=null;for(Node n=bodies.item(0).getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element){action=(Element)n;break;}if(action==null)return fault(402,"Invalid Args");
            final Element actionNode=action;String type=path.endsWith("AVTransport")?AV:path.endsWith("RenderingControl")?RC:CM;
            FutureTask<String> task=new FutureTask<>(()->perform(type,actionNode));app.main.post(task);
            try{return xml("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:"+action.getLocalName()+"Response xmlns:u=\""+type+"\">"+task.get(4,TimeUnit.SECONDS)+"</u:"+action.getLocalName()+"Response></s:Body></s:Envelope>");}
            catch(ExecutionException e){Throwable cause=e.getCause();return fault(cause instanceof UnsupportedOperationException?401:402,cause instanceof UnsupportedOperationException?"Invalid Action":"Invalid Args");}
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Not found");
    }catch(Exception e){return fault(501,"Action Failed");}}
    Response fault(int code,String message){return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/xml; charset=utf-8","<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>"+code+"</errorCode><errorDescription>"+message+"</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>");}
    String arg(Element e,String key){NodeList list=e.getElementsByTagName(key);if(list.getLength()==0)list=e.getElementsByTagNameNS("*",key);return list.getLength()==0?"":list.item(0).getTextContent();}
    String tags(String... pairs){StringBuilder b=new StringBuilder();for(int i=0;i<pairs.length;i+=2)b.append('<').append(pairs[i]).append('>').append(x(pairs[i+1])).append("</").append(pairs[i]).append('>');return b.toString();}
    static String time(long ms){long s=Math.max(0,ms)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,s/60%60,s%60);}
    String perform(String type,Element e){String a=e.getLocalName();String instance=arg(e,"InstanceID");if(!instance.isEmpty()&&!instance.equals("0"))throw new IllegalArgumentException();
        switch(a){
            case "SetAVTransportURI":app.setMedia(arg(e,"CurrentURI"),arg(e,"CurrentURIMetaData"));break;
            case "Play":if(app.currentUri.isEmpty())throw new IllegalArgumentException();if(app.player.getPlaybackState()==androidx.media3.common.Player.STATE_IDLE)app.player.prepare();if(app.player.getPlaybackState()==androidx.media3.common.Player.STATE_ENDED)app.player.seekTo(0);app.showVideo();app.player.play();break;
            case "Pause":app.player.pause();break;
            case "Stop":app.stopPlayback();break;
            case "Seek":String unit=arg(e,"Unit");if(!unit.equals("REL_TIME")&&!unit.equals("ABS_TIME"))throw new IllegalArgumentException();String[] t=arg(e,"Target").split(":");if(t.length!=3)throw new IllegalArgumentException();app.player.seekTo((long)((Double.parseDouble(t[0])*3600+Double.parseDouble(t[1])*60+Double.parseDouble(t[2]))*1000));break;
            case "GetTransportInfo":return tags("CurrentTransportState",app.transportState(),"CurrentTransportStatus",app.lastError.isEmpty()?"OK":"ERROR_OCCURRED","CurrentSpeed","1");
            case "GetPositionInfo":return tags("Track",app.currentUri.isEmpty()?"0":"1","TrackDuration",time(app.duration()),"TrackMetaData",app.metadata,"TrackURI",app.currentUri,"RelTime",time(app.player.getCurrentPosition()),"AbsTime",time(app.player.getCurrentPosition()),"RelCount","2147483647","AbsCount","2147483647");
            case "GetMediaInfo":return tags("NrTracks",app.currentUri.isEmpty()?"0":"1","MediaDuration",time(app.duration()),"CurrentURI",app.currentUri,"CurrentURIMetaData",app.metadata,"NextURI","","NextURIMetaData","","PlayMedium","NETWORK","RecordMedium","NOT_IMPLEMENTED","WriteStatus","NOT_IMPLEMENTED");
            case "GetDeviceCapabilities":return tags("PlayMedia","NETWORK","RecMedia","NOT_IMPLEMENTED","RecQualityModes","NOT_IMPLEMENTED");
            case "GetTransportSettings":return tags("PlayMode","NORMAL","RecQualityMode","NOT_IMPLEMENTED");
            case "GetCurrentTransportActions":return tags("Actions",app.currentUri.isEmpty()?"":"Play,Pause,Stop,Seek");
            case "SetPlayMode":if(!arg(e,"NewPlayMode").equals("NORMAL"))throw new IllegalArgumentException();break;
            case "GetVolume":return tags("CurrentVolume",String.valueOf(volume()));
            case "SetVolume":int v=Integer.parseInt(arg(e,"DesiredVolume"));app.player.setVolume(Math.max(0,Math.min(100,v))/100f);break;
            case "GetMute":return tags("CurrentMute",app.player.getVolume()==0?"1":"0");
            case "SetMute":String mute=arg(e,"DesiredMute");if(mute.equals("1")||mute.equals("true")){if(app.player.getVolume()>0)previousVolume=app.player.getVolume();app.player.setVolume(0);}else app.player.setVolume(previousVolume);break;
            case "ListPresets":return tags("CurrentPresetNameList","FactoryDefaults");
            case "GetProtocolInfo":return tags("Source","","Sink",PROTOCOL);
            case "GetCurrentConnectionIDs":return tags("ConnectionIDs","0");
            case "GetCurrentConnectionInfo":return tags("RcsID","0","AVTransportID","0","ProtocolInfo","http-get:*:video/*:*","PeerConnectionManager","","PeerConnectionID","-1","Direction","Input","Status","OK");
            default:throw new UnsupportedOperationException();
        }changed();return "";
    }
    float previousVolume=1f;
    int volume(){return Math.round(app.player.getVolume()*100);}
    Response subscription(IHTTPSession s){String sid=s.getHeaders().get("sid");if(s.getMethod().name().equals("UNSUBSCRIBE")){subscriptions.remove(sid==null?"":sid);return newFixedLengthResponse("");}
        if(!s.getMethod().name().equals("SUBSCRIBE"))return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,"text/plain","");
        Subscription sub=sid==null?null:subscriptions.get(sid);
        if(sub==null&&sid!=null)return newFixedLengthResponse(Response.Status.PRECONDITION_FAILED,"text/plain","");
        if(sub==null){String cb=s.getHeaders().get("callback");if(cb==null||!cb.startsWith("<")||!cb.endsWith(">"))return newFixedLengthResponse(Response.Status.PRECONDITION_FAILED,"text/plain","");try{URI uri=URI.create(cb.substring(1,cb.length()-1));if(!uri.getScheme().equals("http")||!InetAddress.getByName(uri.getHost()).getHostAddress().equals(s.getRemoteIpAddress()))throw new IllegalArgumentException();}catch(Exception e){return newFixedLengthResponse(Response.Status.PRECONDITION_FAILED,"text/plain","");}sub=new Subscription();sub.callback=cb.substring(1,cb.length()-1);sub.service=s.getUri().endsWith("AVTransport")?AV:s.getUri().endsWith("RenderingControl")?RC:CM;sid="uuid:"+UUID.randomUUID();subscriptions.put(sid,sub);}
        sub.expires=System.currentTimeMillis()+1800000;Response r=newFixedLengthResponse("");r.addHeader("SID",sid);r.addHeader("TIMEOUT","Second-1800");final String id=sid;worker.schedule(()->notifyOne(id),200,TimeUnit.MILLISECONDS);return r;
    }
    void changed(){if(running)try{worker.execute(this::notifySubscribers);}catch(RejectedExecutionException ignored){}}
    void notifySubscribers(){for(String sid:subscriptions.keySet())notifyOne(sid);}
    synchronized void notifyOne(String sid){Subscription sub=subscriptions.get(sid);if(sub==null)return;if(sub.expires<System.currentTimeMillis()){subscriptions.remove(sid);return;}try{
        FutureTask<String> snapshot=new FutureTask<>(()->{
            if(sub.service.equals(CM))return "<e:property><SourceProtocolInfo/></e:property><e:property><SinkProtocolInfo>"+x(PROTOCOL)+"</SinkProtocolInfo></e:property><e:property><CurrentConnectionIDs>0</CurrentConnectionIDs></e:property>";
            String event=sub.service.equals(AV)?"<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/AVT/\"><InstanceID val=\"0\"><TransportState val=\""+app.transportState()+"\"/><TransportStatus val=\""+(app.lastError.isEmpty()?"OK":"ERROR_OCCURRED")+"\"/><CurrentTrackURI val=\""+x(app.currentUri)+"\"/><AVTransportURI val=\""+x(app.currentUri)+"\"/><CurrentTrackDuration val=\""+time(app.duration())+"\"/><CurrentMediaDuration val=\""+time(app.duration())+"\"/><CurrentTrack val=\"1\"/><NumberOfTracks val=\"1\"/><CurrentTransportActions val=\"Play,Pause,Stop,Seek\"/></InstanceID></Event>":"<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/RCS/\"><InstanceID val=\"0\"><Volume channel=\"Master\" val=\""+volume()+"\"/><Mute channel=\"Master\" val=\""+(volume()==0?1:0)+"\"/></InstanceID></Event>";
            return "<e:property><LastChange>"+x(event)+"</LastChange></e:property>";
        });app.main.post(snapshot);byte[] body=("<?xml version=\"1.0\"?><e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">"+snapshot.get(2,TimeUnit.SECONDS)+"</e:propertyset>").getBytes(StandardCharsets.UTF_8);
        URI url=URI.create(sub.callback);try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(url.getHost(),url.getPort()<0?80:url.getPort()),1000);socket.setSoTimeout(1000);String path=url.getRawPath();if(path==null||path.isEmpty())path="/";if(url.getRawQuery()!=null)path+="?"+url.getRawQuery();String header="NOTIFY "+path+" HTTP/1.1\r\nHOST: "+url.getRawAuthority()+"\r\nCONTENT-TYPE: text/xml; charset=utf-8\r\nNT: upnp:event\r\nNTS: upnp:propchange\r\nSID: "+sid+"\r\nSEQ: "+sub.seq+++"\r\nCONTENT-LENGTH: "+body.length+"\r\nCONNECTION: close\r\n\r\n";socket.getOutputStream().write(header.getBytes(StandardCharsets.UTF_8));socket.getOutputStream().write(body);socket.getOutputStream().flush();socket.getInputStream().read();}
    }catch(Exception ignored){}}

    String scpd(String type){return ServiceDescription.xml(type);}
}
