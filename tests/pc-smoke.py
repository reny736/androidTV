"""Android emulator smoke test using a self-generated local video, no third-party media."""
import sys, pathlib, subprocess, time, re, threading, http.server, functools, urllib.request, xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.tools/python'))
ADB=ROOT/'.tools/android-sdk/platform-tools/adb.exe'
OUT=ROOT/'dist/pc-check'
OUT.mkdir(parents=True,exist_ok=True)
MEDIA=ROOT/'.tools/test-media'
MEDIA.mkdir(parents=True,exist_ok=True)

def adb(*args):
    return subprocess.check_output([str(ADB),'-s','emulator-5554',*args],text=True,encoding='utf-8',errors='replace')

def screenshot(name):
    adb('shell','screencap','-p','/sdcard/'+name+'.png')
    adb('pull','/sdcard/'+name+'.png',str(OUT/(name+'.png')))

def generate_video():
    import av
    from PIL import Image,ImageDraw,ImageFont
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',30)
    small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',20)
    with av.open(str(MEDIA/'demo.mp4'),'w') as output:
        stream=output.add_stream('libx264',rate=24)
        stream.width=640;stream.height=360;stream.pix_fmt='yuv420p'
        stream.options={'crf':'24','preset':'ultrafast'}
        for i in range(24*30):
            im=Image.new('RGB',(640,360),'#182b27');draw=ImageDraw.Draw(im)
            draw.text((36,35),'TabletCast / playback check',font=font,fill='#f7f8f6')
            draw.text((36,85),'Local test video - 640 x 360 / 24 fps',font=small,fill='#b8d0c8')
            draw.text((36,285),f'{i/24:05.2f} s',font=font,fill='#ffffff')
            x=40+(i%240)*560//240
            draw.ellipse((x-12,190,x+12,214),fill='#71cbb1')
            frame=av.VideoFrame.from_image(im)
            for packet in stream.encode(frame):output.mux(packet)
        for packet in stream.encode():output.mux(packet)

generate_video()
events=[]
class MediaHandler(http.server.SimpleHTTPRequestHandler):
    def do_NOTIFY(self):
        events.append(self.rfile.read(int(self.headers.get('Content-Length','0'))))
        self.send_response(200);self.send_header('Content-Length','0');self.end_headers()
server=http.server.ThreadingHTTPServer(('127.0.0.1',18888),functools.partial(MediaHandler,directory=str(MEDIA)))
threading.Thread(target=server.serve_forever,daemon=True).start()
adb('logcat','-c')
adb('shell','am','force-stop','cn.tabletcast')
adb('shell','am','start','-n','cn.tabletcast/.MainActivity')
adb('shell','settings','put','secure','immersive_mode_confirmations','confirmed')
time.sleep(4)
port=None
for i in range(40):
    logs=adb('logcat','-d','-s','TabletCast:I','AndroidRuntime:E')
    matches=re.findall(r'Receiver listening http://[^:]+:(\d+)/device.xml',logs)
    if matches:port=matches[-1];break
    time.sleep(2)
assert port, 'Receiver did not start: '+logs
adb('forward','tcp:18889','tcp:'+port)
base='http://127.0.0.1:18889'
device=ET.fromstring(urllib.request.urlopen(base+'/device.xml').read())
assert device.find('.//{*}friendlyName').text=='小米平板 7'
for name in ['AVTransport','RenderingControl','ConnectionManager']:
    ET.fromstring(urllib.request.urlopen(base+'/'+name+'.xml').read())
adb('reverse','tcp:18888','tcp:18888')
subscribe=urllib.request.Request(base+'/event/AVTransport',method='SUBSCRIBE',headers={'NT':'upnp:event','CALLBACK':'<http://127.0.0.1:18888/events>','TIMEOUT':'Second-300'})
with urllib.request.urlopen(subscribe,timeout=10) as response:sid=response.headers['SID']
for i in range(20):
    if events:break
    time.sleep(.2)
assert events,'Initial GENA event missing'
ET.fromstring(events[0])
screenshot('01-waiting')
adb('shell','settings','put','system','accelerometer_rotation','0')
adb('shell','settings','put','system','user_rotation','1')
time.sleep(2)
screenshot('04-portrait')
adb('shell','settings','put','system','user_rotation','0')
time.sleep(2)
adb('shell','input','tap','800','500')
time.sleep(1)
screenshot('05-device-name')
adb('shell','uiautomator','dump','/sdcard/controls.xml')
controls=ET.fromstring(adb('shell','cat','/sdcard/controls.xml'))
cancel=next((node for node in controls.iter('node') if node.get('text')=='取消'),None)
assert cancel is not None,'Device rename dialog did not open'
coords=list(map(int,re.findall(r'\d+',cancel.get('bounds'))))
adb('shell','input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2))

def action(name,values=None,service='AVTransport'):
    from xml.sax.saxutils import escape
    values=values or {}
    if service!='ConnectionManager':values={'InstanceID':'0',**values}
    type='urn:schemas-upnp-org:service:'+service+':1'
    args=''.join('<'+k+'>'+escape(str(v))+'</'+k+'>' for k,v in values.items())
    body=f'<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:{name} xmlns:u="{type}">{args}</u:{name}></s:Body></s:Envelope>'
    req=urllib.request.Request(base+'/control/'+service,data=body.encode(),headers={'Content-Type':'text/xml; charset=utf-8','SOAPAction':'"'+type+'#'+name+'"'})
    return ET.fromstring(urllib.request.urlopen(req,timeout=12).read())

def value(doc,key):return doc.find('.//'+key).text

action('SetAVTransportURI',{'CurrentURI':'http://10.0.2.2:18888/demo.mp4','CurrentURIMetaData':''})
action('Play',{'Speed':'1'})
for i in range(30):
    state=value(action('GetTransportInfo'),'CurrentTransportState')
    if state=='PLAYING':break
    time.sleep(2)
assert state=='PLAYING',state
time.sleep(3)
screenshot('02-playing')
action('Pause')
assert value(action('GetTransportInfo'),'CurrentTransportState')=='PAUSED_PLAYBACK'
action('Seek',{'Unit':'REL_TIME','Target':'00:00:12'})
time.sleep(2)
assert value(action('GetPositionInfo'),'RelTime') in ['00:00:12','00:00:13']
action('SetVolume',{'Channel':'Master','DesiredVolume':'65'},'RenderingControl')
assert value(action('GetVolume',{'Channel':'Master'},'RenderingControl'),'CurrentVolume')=='65'
action('Stop')
assert value(action('GetTransportInfo'),'CurrentTransportState')=='STOPPED'
action('SetAVTransportURI',{'CurrentURI':'http://10.0.2.2:18888/missing.mp4','CurrentURIMetaData':''})
action('Play',{'Speed':'1'})
time.sleep(8)
screenshot('03-error')
logs=adb('logcat','-d','-s','AndroidRuntime:E')
assert 'FATAL EXCEPTION' not in logs,logs
(OUT/'runtime-log.txt').write_text(logs,encoding='utf-8')
adb('shell','am','force-stop','cn.tabletcast')
adb('shell','am','start','-n','cn.tabletcast/.MainActivity')
(OUT/'result.txt').write_text('PASS: Android 16 emulator launch, device XML, 3 service descriptions, GENA subscription and initial event, HTTP video playback, pause, seek, volume, stop, error screen; no fatal exception.\nNot covered: real-phone SSDP discovery, Migu/Bilibili compatibility, Xiaomi hardware decoding, high bitrate performance.\n',encoding='utf-8')
print('PASS: local playback and DLNA control smoke checks')
adb('forward','--remove','tcp:18889')
adb('reverse','--remove','tcp:18888')
server.shutdown()
