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

import org.jgroups.*;
import org.jgroups.blocks.PullPushAdapter;
import org.apache.commons.logging.Log;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Hashtable;

import helma.framework.core.SessionManager;
import helma.framework.core.Application;
import helma.framework.core.Session;
import helma.framework.core.RequestEvaluator;
import helma.scripting.ScriptingEngine;
import helma.objectmodel.db.NodeHandle;
import helma.objectmodel.INode;
import helma.objectmodel.TransientNode;

public class SwarmSessionManager extends SessionManager
        implements MessageListener, MembershipListener {

    PullPushAdapter adapter;
    Address address;
    Log log;

    ////////////////////////////////////////////////////////
    // SessionManager functionality

    public void init(Application app) {
        this.app = app;
        String logName = new StringBuffer("helma.")
                                  .append(app.getName())
                                  .append(".swarm")
                                  .toString();
        log = app.getLogger(logName);
        try {
            Channel channel = ChannelUtils.createChannel(app);
            channel.connect("swarm_session");
            address = channel.getLocalAddress();
            adapter = new PullPushAdapter(channel, this, this);
            channel.setOpt(Channel.GET_STATE_EVENTS, Boolean.TRUE);
            channel.setOpt(Channel.AUTO_GETSTATE, Boolean.TRUE);
            if (!channel.getState(null, 0)) {
                log.debug("Couldn't get session state. First instance in swarm?");
            }
        } catch (Exception e) {
            log.error("HelmaSwarm: Error starting/joining channel", e);
            e.printStackTrace();
        }
    }


    public Session createSession(String sessionId) {
        Session session = getSession(sessionId);

        if (session == null) {
            session = new SwarmSession(sessionId, app, this);
            sessions.put(sessionId, session);
        }

        return session;
    }

    public void shutdown() {
        adapter.stop();
    }


    public void discardSession(Session session) {
        super.discardSession(session);
        broadcast(new DiscardSessionEvent(session.getSessionId()));
    }

    public void touchSession(Session session) {
        broadcast(new TouchSessionEvent(session.getSessionId()));
    }

    ///////////////////////////////////////////////////////////////
    // JGroups/ MessageListener functionality

    public void receive(Message msg) {
        if (address.equals(msg.getSrc())) {
            log.debug("Discarding own message: "+address);
            return;
        }

        Object object = msg.getObject();
        log.debug("Received object: " + object);
        if (object instanceof UpdateSessionEvent) {
            try {
                UpdateSessionEvent update = (UpdateSessionEvent) object;
                Session session = getSession(update.sessionId);
                if (session == null) {
                    session = createSession(update.sessionId);
                }
                session.setMessage(update.message);
                session.setUserHandle(update.userHandle);
                if (update.cacheNode != null) {
                    Object cacheNode = bytesToObject(update.cacheNode);
                    session.setCacheNode((TransientNode) cacheNode);
                }
                log.debug("Transfered session: " + session);
            } catch (Exception x) {
                log.error("Error in session deserialization", x);
            }
        } else if (object instanceof UpdateList) {
            UpdateList list = (UpdateList) object;

            for (int i = 0; i < list.touched.length; i++) {
               // TODO: implement bulk session update
            }
            for (int i = 0; i < list.discarded.length; i++) {
                // TODO: implement bulk session update
            }
        } else if (object instanceof DiscardSessionEvent) {
            // TODO: implement staged session dump
            sessions.remove(object.toString());
            log.debug("Discarded session: " + object);
        } else if (object instanceof TouchSessionEvent) {
            Session session = getSession(object.toString());
            if (session instanceof SwarmSession) {
                ((SwarmSession) session).replicatedTouch();
            }
            log.debug("Touched session: " + object);
        }
    }


    public byte[] getState() {
        Map map = getSessions();
        RequestEvaluator reval = app.getEvaluator();
        try {
            return objectToBytes(map, reval);
        } catch (IOException x) {
            log.error("Error in getState()", x);
            throw new RuntimeException("Error in getState(): "+x);
        } finally {
            log.debug("Returned session table: " + map);
            app.releaseEvaluator(reval);
        }
    }

    public void setState(byte[] bytes) {
        if (bytes != null) {
            try {
            Hashtable map = (Hashtable) bytesToObject(bytes);
            Iterator it = map.values().iterator();
            while (it.hasNext()) {
                SwarmSession session = (SwarmSession) it.next();
                session.setApp(app);
                session.sessionMgr = SwarmSessionManager.this;
            }
            sessions = map;
            log.debug("Received session: " + map);
            } catch (Exception x) {
                log.error ("Error in setState()", x);
            }
        }
    }

    void broadcastSession(SwarmSession session, RequestEvaluator reval) {
        log.debug("Broadcasting changed session: " + session);
        try {
            UpdateSessionEvent update = new UpdateSessionEvent(session, reval);
            adapter.send(new Message(null, address, update));
        } catch (Exception x) {
            log.error("Error in session replication", x);
        }
    }

    void broadcast(Serializable object) {
        try {
            adapter.send(new Message(null, address, object));
        } catch (Exception x) {
            log.error("Error broadcasting session event", x);
        }
    }

    static byte[] objectToBytes(Object obj, RequestEvaluator reval)
            throws IOException {
        ScriptingEngine engine = reval.getScriptingEngine();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        engine.serialize(obj, out);
        return out.toByteArray();
    }

    Object bytesToObject(byte[] bytes)
            throws IOException, ClassNotFoundException {
        RequestEvaluator reval = app.getEvaluator();
        try {
            ScriptingEngine engine = reval.getScriptingEngine();
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            return engine.deserialize(in);
        } finally {
            app.releaseEvaluator(reval);
        }
    }


    public void viewAccepted(View view) {
        // log.debug("SESSION MGR: GOT VIEW_ACCEPTED()");
    }

    public void suspect(Address address) {
        // log.debug("SESSION MGR: GOT SUSPECT()");
    }

    public void  block() {
        // log.debug("SESSION MGR: GOT BLOCK()");
    }


    static class UpdateList implements Serializable {
        Address address;
        Object[] touched;
        Object[] discarded;

        public UpdateList(Address address, List touched, List discarded) {
            this.address = address;
            this.touched = touched.toArray();
            this.discarded = discarded.toArray();
        }
    }

    static class TouchSessionEvent implements Serializable {
        String sessionId;

        TouchSessionEvent(String id) {
            this.sessionId = id;
        }

        public String toString() {
            return sessionId;
        }
    }

    static class DiscardSessionEvent implements Serializable {
        String sessionId;

        DiscardSessionEvent(String id) {
            this.sessionId = id;
        }

        public String toString() {
            return sessionId;
        }
    }

    static class UpdateSessionEvent implements Serializable {
        String sessionId;
        String message;
        NodeHandle userHandle;
        byte[] cacheNode = null;

        UpdateSessionEvent(SwarmSession session, RequestEvaluator reval)
                throws IOException{
            this.sessionId = session.getSessionId();
            this.message = session.getMessage();
            this.userHandle = session.getUserHandle();
            INode cacheNode = session.getCacheNode();
            // only transfer cache node if it has changed
            if (cacheNode.lastModified() != session.previousLastMod) {
                this.cacheNode = objectToBytes(session, reval);
            }
        }

    }
}

