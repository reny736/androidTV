package cn.tabletcast;

import org.junit.Test;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class ServiceDescriptionTest {
    @Test public void descriptionsExposeValidActionsAndRelatedVariables() throws Exception {
        String[] types={DlnaServer.AV,DlnaServer.RC,DlnaServer.CM};
        String[][] required={{"SetAVTransportURI","Play","Pause","Stop","Seek","GetPositionInfo","GetTransportInfo"},{"GetVolume","SetVolume","GetMute","SetMute"},{"GetProtocolInfo","GetCurrentConnectionIDs","GetCurrentConnectionInfo"}};
        for(int k=0;k<types.length;k++){
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
            Document doc=f.newDocumentBuilder().parse(new ByteArrayInputStream(ServiceDescription.xml(types[k]).getBytes(StandardCharsets.UTF_8)));
            Set<String> variables=new HashSet<>(),actions=new HashSet<>();
            NodeList states=doc.getElementsByTagName("stateVariable");
            for(int i=0;i<states.getLength();i++){Element e=(Element)states.item(i);assertTrue(variables.add(e.getElementsByTagName("name").item(0).getTextContent()));}
            NodeList related=doc.getElementsByTagName("relatedStateVariable");
            for(int i=0;i<related.getLength();i++)assertTrue(variables.contains(related.item(i).getTextContent()));
            NodeList actionNodes=doc.getElementsByTagName("action");
            for(int i=0;i<actionNodes.getLength();i++)assertTrue(actions.add(((Element)actionNodes.item(i)).getElementsByTagName("name").item(0).getTextContent()));
            for(String action:required[k])assertTrue(action,actions.contains(action));
            if(k<2)assertTrue(variables.contains("LastChange"));
        }
    }
}
