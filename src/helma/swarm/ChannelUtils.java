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
import helma.framework.core.Application;

public class ChannelUtils {

    public static Channel createChannel(Application app, int port)
            throws ChannelException {
        StringBuffer b = new StringBuffer(groupPropsPrefix);
        b.append("mcast_addr=");
        b.append(app.getProperty("helmaswarm.multicast_ip", mcast_ip));
        b.append(";mcast_port=");
        b.append(app.getProperty("helmaswarm.multicast_port", Integer.toString(port)));
        b.append(";ip_ttl=");
        b.append(app.getProperty("helmaswarm.ip_ttl", ip_ttl));
        b.append(";bind_port=");
        b.append(app.getProperty("helmaswarm.bind_port", bind_port));
        b.append(";port_range=");
        b.append(app.getProperty("helmaswarm.port_range", port_range));
        String bind_addr = app.getProperty("helmaswarm.bind_addr");
        if (bind_addr != null) {
            b.append(";bind_addr=");
            b.append(bind_addr);
        }
        b.append(";");
        b.append(groupPropsSuffix);
        return new JChannel(b.toString());
    }


    // jgroups properties. copied from swarmcache
    final static String groupPropsPrefix = "UDP(";
    // plus something like this created from app.properties:
    // mcast_addr=231.12.21.132;mcast_port=45566;ip_ttl=32;
    final static String groupPropsSuffix =
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

    final static String mcast_ip = "224.0.0.132";
    final static String mcast_port = "22024";
    final static String ip_ttl = "32";
    final static String bind_port = "48848";
    final static String port_range = "1000";
}
