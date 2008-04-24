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

import helma.framework.core.Session;
import helma.framework.core.Application;
import helma.framework.core.RequestEvaluator;
import helma.objectmodel.db.NodeHandle;
import helma.objectmodel.INode;

public class SwarmSession extends Session {

    static final long serialVersionUID = -277633455908843661L;

    // distributed flag - true for replicated sessions
    boolean distributed = false;

    // swarm session manager reference
    transient SwarmSessionManager sessionMgr;
    // transient helper fields to track changes
    transient long previousLastMod;
    transient NodeHandle previousUserHandle;
    transient String previousMessage;
    transient StringBuffer previousDebugBuffer;

    public SwarmSession(String sessionId, Application app, SwarmSessionManager mgr) {
        super(sessionId, app);
        sessionMgr = mgr;
    }

    public void touch() {
        super.touch();
        sessionMgr.touchSession(this);
        previousLastMod = getCacheNode().lastModified();
        previousUserHandle = userHandle;
        previousMessage = message;
        previousDebugBuffer = debugBuffer;
    }

    void replicatedTouch() {
        super.touch();
    }

    /**
     * Called after a request has been handled.
     * @param reval the request evaluator that handled the request
     */
    public void commit(RequestEvaluator reval) {
        if (wasModifiedInRequest()) {
            sessionMgr.broadcastSession(this, reval);
        }
    }

    /**
     * Override setApp to also set the transient sessionMgr field
     * @param app the Application
     */
    public void setApp(Application app) {
        sessionMgr = (SwarmSessionManager) app.getSessionManager();
        super.setApp(app);
    }

    protected boolean isDistributed() {
        return distributed;
    }

    protected void setDistributed(boolean distributed) {
        this.distributed = distributed;
    }

    protected boolean wasModified() {
        // true if either session state or cachenode have been modified in the past
        INode cache = getCacheNode();
        return lastModified != onSince ||
               cache.created() != cache.lastModified();
    }

    protected boolean wasModifiedInRequest() {
        // true if session was modified since we last called touch() on it
        INode cache = getCacheNode();
        return cache.lastModified() != previousLastMod ||
               userHandle != previousUserHandle ||
               message != previousMessage ||
               debugBuffer != previousDebugBuffer;
    }

}

