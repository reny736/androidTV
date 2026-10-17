package cn.tabletcast;

import java.util.*;

/** Builds SCPD from one action/argument schema so every argument references a declared variable. */
final class ServiceDescription {
    final StringBuilder actions=new StringBuilder();
    final Map<String,String> variables=new LinkedHashMap<>();
    void action(String name,String... args){actions.append("<action><name>").append(name).append("</name>");if(args.length>0)actions.append("<argumentList>");for(String arg:args){String[] p=arg.split(":");String variable="A_ARG_TYPE_"+p[1];variables.put(variable,p[2]);actions.append("<argument><name>").append(p[1]).append("</name><direction>").append(p[0]).append("</direction><relatedStateVariable>").append(variable).append("</relatedStateVariable></argument>");}if(args.length>0)actions.append("</argumentList>");actions.append("</action>");}
    static final String ID="in:InstanceID:ui4", CH="in:Channel:string";
    static String xml(String type){ServiceDescription d=new ServiceDescription();
        if(type.equals(DlnaServer.AV)){
            d.action("SetAVTransportURI",ID,"in:CurrentURI:string","in:CurrentURIMetaData:string");
            d.action("Play",ID,"in:Speed:string");d.action("Pause",ID);d.action("Stop",ID);
            d.action("Seek",ID,"in:Unit:string","in:Target:string");d.action("SetPlayMode",ID,"in:NewPlayMode:string");
            d.action("GetTransportInfo",ID,"out:CurrentTransportState:string","out:CurrentTransportStatus:string","out:CurrentSpeed:string");
            d.action("GetPositionInfo",ID,"out:Track:ui4","out:TrackDuration:string","out:TrackMetaData:string","out:TrackURI:string","out:RelTime:string","out:AbsTime:string","out:RelCount:i4","out:AbsCount:i4");
            d.action("GetMediaInfo",ID,"out:NrTracks:ui4","out:MediaDuration:string","out:CurrentURI:string","out:CurrentURIMetaData:string","out:NextURI:string","out:NextURIMetaData:string","out:PlayMedium:string","out:RecordMedium:string","out:WriteStatus:string");
            d.action("GetDeviceCapabilities",ID,"out:PlayMedia:string","out:RecMedia:string","out:RecQualityModes:string");
            d.action("GetTransportSettings",ID,"out:PlayMode:string","out:RecQualityMode:string");
            d.action("GetCurrentTransportActions",ID,"out:Actions:string");
        }else if(type.equals(DlnaServer.RC)){
            d.action("ListPresets",ID,"out:CurrentPresetNameList:string");
            d.action("GetVolume",ID,CH,"out:CurrentVolume:ui2");d.action("SetVolume",ID,CH,"in:DesiredVolume:ui2");
            d.action("GetMute",ID,CH,"out:CurrentMute:boolean");d.action("SetMute",ID,CH,"in:DesiredMute:boolean");
        }else{
            d.action("GetProtocolInfo","out:Source:string","out:Sink:string");
            d.action("GetCurrentConnectionIDs","out:ConnectionIDs:string");
            d.action("GetCurrentConnectionInfo","in:ConnectionID:i4","out:RcsID:i4","out:AVTransportID:i4","out:ProtocolInfo:string","out:PeerConnectionManager:string","out:PeerConnectionID:i4","out:Direction:string","out:Status:string");
        }
        StringBuilder table=new StringBuilder();for(Map.Entry<String,String> v:d.variables.entrySet())table.append("<stateVariable sendEvents=\"no\"><name>").append(v.getKey()).append("</name><dataType>").append(v.getValue()).append("</dataType></stateVariable>");
        if(!type.equals(DlnaServer.CM))table.append("<stateVariable sendEvents=\"yes\"><name>LastChange</name><dataType>string</dataType></stateVariable>");
        else for(String name:new String[]{"SourceProtocolInfo","SinkProtocolInfo","CurrentConnectionIDs"})table.append("<stateVariable sendEvents=\"yes\"><name>").append(name).append("</name><dataType>string</dataType></stateVariable>");
        return "<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><actionList>"+d.actions+"</actionList><serviceStateTable>"+table+"</serviceStateTable></scpd>";
    }
}
