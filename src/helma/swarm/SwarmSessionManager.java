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
import java.util.*;

import helma.framework.core.SessionManager;
import helma.framework.core.Application;
import helma.framework.core.Session;
import helma.framework.core.RequestEvaluator;
import helma.scripting.ScriptingEngine;
import helma.objectmodel.db.NodeHandle;
import helma.objectmodel.INode;
import helma.objectmodel.TransientNode;

public class SwarmSessionManager extends SessionManager implements MessageListener, Runnable {

    // SessionIdList operation constants
    static final int TOUCH = 0;
    static final int DISCARD = 1;

    PullPushAdapter adapter;
    Address address;
    Log log;
    volatile Thread runner;
    HashSet touched = new HashSet(), discarded = new HashSet();

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
            adapter = ChannelUtils.getAdapter(app);
            Channel channel = (Channel) adapter.getTransport();
            // enable state exchange
            channel.setOpt(Channel.GET_STATE_EVENTS, Boolean.TRUE);
            channel.setOpt(Channel.AUTO_GETSTATE, Boolean.TRUE);
            address = channel.getLocalAddress();
            // register us as main message listeners so we can exchange state
            adapter.setListener(this);
            if (!channel.getState(null, 5000)) {
                log.debug("Couldn't get session state. First instance in swarm?");
            }
            // start broadcaster thread
            runner = new Thread(this);
            runner.setDaemon(true);
            runner.start();
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
        if (adapter != null) {
            adapter.setListener(null);
        }
        ChannelUtils.stopAdapter(app);

        if (runner != null) {
            Thread t = runner;
            runner = null;
            t.interrupt();
        }
    }


    public void discardSession(Session session) {
        super.discardSession(session);
        if (((SwarmSession) session).isDistributed()) {
            discarded.add(session.getSessionId());
        }
    }

    public void touchSession(SwarmSession session) {
        if (session.isDistributed()) {
            touched.add(session.getSessionId());
        }
    }

    ///////////////////////////////////////////////////////////////
    // Runnable

    public void run() {
        while(runner == Thread.currentThread()) {
            try {
                Thread.sleep(1000l);
            } catch (InterruptedException x) {
                log.info("SwarmSession: broadcast thread interrupted, exiting");
                return;
            }
            broadcastIds(TOUCH, touched);
            broadcastIds(DISCARD, discarded);
        }
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
        if (object instanceof byte[]) {
            try {
                SwarmSession session = (SwarmSession) bytesToObject((byte[]) object);
                session.setApp(app);
                session.sessionMgr = this;
                sessions.put(session.getSessionId(), session);
                log.debug("Transfered session: " + session);
            } catch (Exception x) {
                log.error("Error in session deserialization", x);
            }
        } else if (object instanceof SessionUpdate) {
            try {
                SessionUpdate update = (SessionUpdate) object;
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
                log.debug("Transfered session update: " + session);
            } catch (Exception x) {
                log.error("Error in session deserialization", x);
            }
        } else if (object instanceof SessionIdList) {
            SessionIdList idlist = (SessionIdList) object;
            Object[] ids = idlist.ids;
            if (idlist.operation == DISCARD) {
                // TODO: implement staged session dump
                for (int i = 0; i < ids.length; i++) {
                    sessions.remove(ids[i]);
                    log.debug("Discarded session: " + ids[i]);
                }
            } else if (idlist.operation == TOUCH) {
                for (int i = 0; i < ids.length; i++) {
                    Object session = sessions.get(ids[i]);
                    if (session instanceof SwarmSession) {
                        ((SwarmSession) session).replicatedTouch();
                        log.debug("Touched session: " + ids[i]);
                    }
                }
            }
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
            if (log.isDebugEnabled()) {
                log.debug("Returned session table: " + map);
            }
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
                if (log.isDebugEnabled()) {
                    log.debug("Received session map: " + map);
                }
            } catch (Exception x) {
                log.error ("Error in setState()", x);
            }
        }
    }

    void broadcastSession(SwarmSession session, RequestEvaluator reval) {
        log.debug("Broadcasting changed session: " + session);
        try {
            if (session.isDistributed()) {
                SessionUpdate update = new SessionUpdate(session, reval);
                adapter.send(new Message(null, address, update));
            } else {
                session.setDistributed(true);
                byte[] bytes = objectToBytes(session, reval);
                adapter.send(new Message(null, address, (Serializable) bytes));
            }
        } catch (Exception x) {
            log.error("Error in session replication", x);
        }
    }

    void broadcastIds(int operation, Set idSet) {
        if (!idSet.isEmpty()) {
            Object[] ids = idSet.toArray();
            for (int i=0; i<ids.length; i++)
                idSet.remove(ids[i]);
            Serializable idlist = new SessionIdList(operation, ids);
            try {
                adapter.send(new Message(null, address, idlist));
            } catch (Exception x) {
                log.error("Error broadcasting session list", x);
            }
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

    static class SessionIdList implements Serializable {
        int operation;
        Object[] ids;

        SessionIdList(int operation, Object[] ids) {
            this.operation = operation;
            this.ids = ids;
        }
    }

    static class SessionUpdate implements Serializable {
        String sessionId;
        String message;
        NodeHandle userHandle;
        byte[] cacheNode = null;

        SessionUpdate(SwarmSession session, RequestEvaluator reval)
                throws IOException{
            this.sessionId = session.getSessionId();
            this.message = session.getMessage();
            this.userHandle = session.getUserHandle();
            INode cacheNode = session.getCacheNode();
            // only transfer cache node if it has changed
            if (cacheNode.lastModified() != session.previousLastMod) {
                this.cacheNode = objectToBytes(session.getCacheNode(), reval);
            }
        }
    }
}

