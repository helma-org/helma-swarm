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

import helma.objectmodel.db.IDGenerator;
import helma.objectmodel.db.DbMapping;
import helma.objectmodel.db.NodeManager;
import helma.framework.core.Application;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.PullPushAdapter;
import org.jgroups.*;
import org.apache.commons.logging.Log;

public class SwarmIDGenerator implements IDGenerator, MessageListener,
                                         MembershipListener, RequestHandler {

    NodeManager nmgr;
    MessageDispatcher dispatcher;
    Address address;
    volatile View view;
    Log log;

    public void init(Application app) {
        nmgr = app.getNodeManager();
        String logName = new StringBuffer("helma.")
                                  .append(app.getName())
                                  .append(".swarm")
                                  .toString();
        log = app.getLogger(logName);
        try {
            PullPushAdapter adapter = ChannelUtils.getAdapter(app);
            dispatcher = new MessageDispatcher(adapter, ChannelUtils.IDGEN, this, this, this);
            Channel channel = (Channel) adapter.getTransport();
            channel.setOpt(Channel.VIEW, Boolean.TRUE);
            address = channel.getLocalAddress();
            view = channel.getView();
            log.info("SwarmIDGenerator: Got initial view: " + view);
        } catch (Exception e) {
            log.error("SwarmIDGenerator: Error starting/joining channel", e);
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (dispatcher != null) {
            dispatcher.stop();
        }
    }

    public String generateID(DbMapping dbmap) throws Exception {
        String typeName = dbmap == null ? null : dbmap.getTypeName();
        // try three times to get id from group coordinator
        for (int i=0; i<3; i++) {
            // if we are the coordinator, create id locally
            Address coordinator = (Address) view.getMembers().firstElement();
            if (coordinator == null) {
                throw new NullPointerException("Coordinator is null in " + view);
            }
            if (address.equals(coordinator)) {
                // no view, or we are the group coordinator. use local id generator
                log.info("SwarmIDGenerator: Generating ID locally for " + dbmap);
                return nmgr.doGenerateID(dbmap);
            }
            Message msg = new Message(coordinator, address, typeName);
            Object response = dispatcher.sendMessage(msg, GroupRequest.GET_FIRST, 2000);
            log.info("SwarmIDGenerator: Received ID " + response + " for " + dbmap);
            if (response != null) {
                return (String) response;
            }
        }
        throw new RuntimeException("SwarmIDGenerator: Unable to get ID from group coordinator");
    }

    public Object handle(Message msg) {
        // only handle id generation request if we are the first node
        if (address.equals(view.getMembers().firstElement())) {
            try {
                Object obj = msg.getObject();
                DbMapping dbmap = obj == null ? null : nmgr.getDbMapping(obj.toString());
                log.info("SwarmIDGenerator: Processing ID request for " + dbmap);
                return nmgr.doGenerateID(dbmap);
            } catch (Exception x) {
                log.error("SwarmIDGenerator: Error in central ID generator", x);
            }
        }
        return null;
    }

    public void viewAccepted(View view) {
        log.info("SwarmIDGenerator: Got View: " + view);
        this.view = view;
    }

    public void suspect(Address addr) {
        log.info("SwarmIDGenerator: Got suspect: " + addr);
    }

    public void block() {
        log.info("SwarmIDGenerator: Got block");
    }

    public void receive(Message message) {
        // not used
    }

    public byte[] getState() {
        // state transfer not used
        return null;
    }

    public void setState(byte[] bytes) {
        // state transfer not used
    }

}
