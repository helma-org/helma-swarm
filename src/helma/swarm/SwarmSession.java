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

public class SwarmSession extends Session {

    transient long previousLastMod;
    NodeHandle previousUserHandle;
    String previousMessage;
    transient SwarmSessionManager sessionMgr;

    public SwarmSession(String sessionId, Application app, SwarmSessionManager mgr) {
        super(sessionId, app);
        sessionMgr = mgr;
    }

    public void touch() {
        super.touch();
        sessionMgr.touchSession(this);
        previousLastMod = cacheNode.lastModified();
        previousUserHandle = userHandle;
        previousMessage = message;
    }

    void replicatedTouch() {
        super.touch();
    }

    public void commit(RequestEvaluator reval) {
        if (cacheNode.lastModified() != previousLastMod ||
                userHandle != previousUserHandle ||
                message != previousMessage) {
            sessionMgr.broadcastSession(this, reval);
        }
    }
}

