/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile$
 * $Author$
 * $Revision$
 * $Date$
 */
package helma.swarm;

import org.jgroups.JChannel;
import org.jgroups.Channel;
import org.jgroups.ChannelException;
import org.jgroups.blocks.PullPushAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import helma.framework.core.Application;
import helma.framework.repository.Repository;
import helma.framework.repository.Resource;
import helma.framework.repository.FileResource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.io.File;

public class ChannelUtils {

    // weak hashmap for pull-push-adapters
    static WeakHashMap adapters = new WeakHashMap();

    // Ids for multiplexing PullPushAdapter.
    // SwarmSessionManager acts as main listener so it can use state exchange. 
    final static Integer CACHE = new Integer(1);
    final static Integer IDGEN = new Integer(2);

    static PullPushAdapter getAdapter(Application app)
            throws ChannelException {
        PullPushAdapter adapter = (PullPushAdapter) adapters.get(app);

        if (adapter == null) {
            SwarmConfig config = new SwarmConfig(app);
            Channel channel = new JChannel(config.getJGroupsProps());
            String groupName = app.getProperty("swarm.name", app.getName());
            channel.connect(groupName + "_swarm");
            adapter = new PullPushAdapter(channel);
            adapter.start();
            adapters.put(app, adapter);
        }

        return adapter;
    }

    static void stopAdapter(Application app) {
        PullPushAdapter adapter = (PullPushAdapter) adapters.remove(app);
        if (adapter != null) {
            Channel channel = (Channel) adapter.getTransport();
            if (channel.isConnected())
                channel.disconnect();
            if (channel.isOpen())
                channel.close();
            adapter.stop();
        }
    }
}

class SwarmConfig {

    String jGroupsProps =
            "UDP(mcast_addr=224.0.0.132;mcast_port=22024;ip_ttl=32;" +
                "bind_port=48848;port_range=1000;" +
                "mcast_send_buf_size=150000;mcast_recv_buf_size=80000):" +
            "PING(timeout=2000;num_initial_members=3):" +
            "MERGE2(min_interval=5000;max_interval=10000):" +
            "FD_SOCK:" +
            "VERIFY_SUSPECT(timeout=1500):" +
            "pbcast.NAKACK(gc_lag=50;retransmit_timeout=300,600,1200,2400,4800):" +
            "UNICAST(timeout=5000):" +
            "pbcast.STABLE(desired_avg_gossip=20000):" +
            "FRAG(frag_size=8096;down_thread=false;up_thread=false):" +
            "pbcast.GMS(join_timeout=5000;join_retry_timeout=2000;shun=false;print_local_addr=true):" +
            "pbcast.STATE_TRANSFER";


    public SwarmConfig (Application app) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        Resource res = null;

        String conf = app.getProperty("swarm.config");

        if (conf != null) {
            res = new FileResource(new File(conf));
        } else {
            Iterator reps = app.getRepositories();
            while (reps.hasNext()) {
                Repository rep = (Repository) reps.next();
                res = rep.getResource("helmaswarm.conf");
                if (res != null)
                    break;
            }
        }

        if (res == null || !res.exists()) {
            app.logEvent("Resource \"" + conf + "\" not found, using defaults");
            return;
        }

        app.logEvent("HelmaSwarm: Reading config from " + res);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(res.getInputStream());
            Element root = document.getDocumentElement();
            NodeList nodes = root.getElementsByTagName("jgroups-stack");

            if (nodes.getLength() == 0) {
                app.logEvent("No JGroups stack found in helmaswarm.conf, using defaults");
            } else {
                NodeList jgroups = null;

                String stackName = app.getProperty("swarm.jgroups.stack", "udp");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element elem = (Element) nodes.item(i);
                    if (stackName.equalsIgnoreCase(elem.getAttribute("name"))) {
                        jgroups = elem.getChildNodes();
                        break;
                    }
                }
                if (jgroups == null) {
                    app.logEvent("JGroups stack \"" + stackName +
                            "\" not found in helmaswarm.conf, using first element");
                    jgroups = nodes.item(0).getChildNodes();
                }

                StringBuffer buffer = new StringBuffer();
                for (int i = 0; i < jgroups.getLength(); i++) {
                    Node node = jgroups.item(i);
                    if (!"#text".equals(node.getNodeName())) {
                        continue;
                    }
                    String str = node.toString();
                    for (int j = 0; j < str.length(); j++) {
                        char c = str.charAt(j);
                        if (!Character.isWhitespace(c)) {
                            buffer.append(c);
                        }
                    }
                }
                if (buffer.length() > 0) {
                    jGroupsProps = buffer.toString();
                }
            }
        } catch (Exception e) {
            app.logError("HelmaSwarm: Error reading config from " + res, e);
        }
    }

    public String getJGroupsProps() {
        return jGroupsProps;
    }

}
