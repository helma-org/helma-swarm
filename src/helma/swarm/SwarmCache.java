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

import helma.objectmodel.ObjectCache;
import helma.objectmodel.db.DbMapping;
import helma.objectmodel.db.Node;
import helma.objectmodel.db.NodeChangeListener;
import helma.framework.core.Application;
import helma.util.CacheMap;

import org.jgroups.blocks.*;
import org.jgroups.*;
import org.apache.commons.logging.Log;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Properties;
import java.util.List;

public class SwarmCache implements ObjectCache, NodeChangeListener, MessageListener {

    CacheMap cache;

    Application app;
    PullPushAdapter adapter;
    Address address;
    Log log;

    /**
     * Initialize the cache client from the properties of our
     * {@link helma.framework.core.Application Application} instance
     *
     * @param app the app instance
     */
    public void init(Application app) {
        this.app = app;
        app.getNodeManager().addNodeChangeListener(this);
        // Configure and Initialize the cache
        cache = new CacheMap();
        // Set up the channel
        String logName = new StringBuffer("helma.")
                                  .append(app.getName())
                                  .append(".swarm")
                                  .toString();
        log = app.getLogger(logName);
        String groupName = app.getProperty("swarm.name", app.getName());
        try {
            Channel channel = ChannelUtils.createChannel(app, 22024);
            channel.connect("swarm_cache_" + groupName);
            adapter = new PullPushAdapter(channel, this);
            address = channel.getLocalAddress();
        } catch (Exception e) {
            log.error("SwarmCache: Error starting/joining channel", e);
            e.printStackTrace();
        }
    }

    /** 
     * Called to shut down the cache when the application terminates.
     */
    public void shutdown() {
        if (cache != null) {
            cache.shutdown();
        }
        if (adapter != null) {
            adapter.stop();
        }
    }

    /**
     * Called when a transaction is committed that has created, modified or 
     * deleted one or more nodes.
     */
    public void nodesChanged(List inserted, List updated,
                             List deleted, List parents) {
        if (!inserted.isEmpty() || !updated.isEmpty() || !deleted.isEmpty()) {
            HashSet keys = new HashSet();
            HashSet parentKeys = new HashSet();
            HashSet types = new HashSet();
            collectUpdates(inserted, keys, types);
            collectUpdates(updated, keys, types);
            collectUpdates(deleted, keys, types);
            collectUpdates(parents, parentKeys, null);
            if (log.isDebugEnabled()) {
                log.debug("SwarmCache: Sending invalidation for " + keys + ", " + types);
            }
            InvalidationList list = new InvalidationList(keys, parentKeys, types);
            try {
                adapter.send(new Message(null, address, list));
            } catch (Exception x) {
                log.error("SwarmCache: Error sending invalidation list", x);
            }
        }
    }

    private void collectUpdates(List list, HashSet keys, HashSet types) {
        for (int i = 0; i < list.size(); i++) {
            Node node = (Node) list.get(i);
            keys.add(node.getKey());
            DbMapping dbm = node.getDbMapping();
            if (types != null && dbm != null) {
                types.add(dbm.getTypeName());
            }
        }
    }

    /**
     * Called when the application's properties have been updated to let
     * the cache implementation update its settings.
     * @param props
     */
    public void updateProperties(Properties props) {
        cache.updateProperties(props);
    }

    /**
     * Returns true if the collection contains an element for the key.
     *
     * @param key the key that we are looking for
     */
    public boolean containsKey(Object key) {
        return cache.containsKey(key);
    }

    /**
     * Returns the number of keys in object array <code>keys</code> that
     * were not found in the Map.
     * Those keys that are contained in the Map are nulled out in the array.
     *
     * @param keys an array of key objects we are looking for
     * @see helma.objectmodel.ObjectCache#containsKey
     */
    public int containsKeys(Object[] keys) {
        return cache.containsKeys(keys);
    }

    /**
     * Gets the object associated with the specified key in the
     * hashtable.
     *
     * @param key the specified key
     * @return the element for the key or null if the key
     *         is not defined in the hash table.
     * @see helma.objectmodel.ObjectCache#put
     */
    public Object get(Object key) {
        return cache.get(key);
    }

    /**
     * Puts the specified element into the hashtable, using the specified
     * key.  The element may be retrieved by doing a get() with the same key.
     * The key and the element cannot be null.
     *
     * @param key   the specified key in the hashtable
     * @param value the specified element
     * @return the old value of the key, or null if it did not have one.
     * @throws NullPointerException If the value of the element
     *                              is equal to null.
     * @see helma.objectmodel.ObjectCache#get
     */
    public Object put(Object key, Object value) {
        return cache.put(key, value);
    }

    /**
     * Removes the element corresponding to the key. Does nothing if the
     * key is not present.
     *
     * @param key the key that needs to be removed
     * @return the value of key, or null if the key was not found.
     */
    public Object remove(Object key) {
        return cache.remove(key);
    }

    /**
     * Removes all items currently stored in the cache.
     *
     * @return true if the operation succeeded
     */
    public boolean clear() {
        return cache.clear();
    }

    /**
     * Return the number of objects currently stored in the cache.
     *
     * @return the number of cached items
     */
    public int size() {
        return cache.size();
    }

    /**
     * Return an array with all objects currently contained in the cache.
     */
    public Object[] getCachedObjects(){
        return cache.getCachedObjects();
    }

    public void receive(Message message) {
        if (address.equals(message.getSrc())) {
            log.debug("SwarmCache: Discarding own message: "+address);
            return;
        }

        Object object = message.getObject();
        if (!(object instanceof InvalidationList)) {
            return;
        }

        InvalidationList list = (InvalidationList) object;
        for (int i=0; i<list.types.length; i++) {
            log.debug("SwarmCache: marking "+list.types[i]);
            DbMapping dbm = app.getDbMapping(list.types[i]);
            long now = System.currentTimeMillis();
            if (dbm != null) {
                dbm.setLastDataChange(now);
            }
        }
        for (int i=0; i<list.keys.length; i++) {
            log.debug("SwarmCache: invalidating "+list.keys[i]);
            Node node = (Node) cache.remove(list.keys[i]);
            if (node != null) {
                node.setState(Node.INVALID);
            }
        }
        long now = System.currentTimeMillis();
        for (int i=0; i<list.parentKeys.length; i++) {
            Node node = (Node) cache.get(list.parentKeys[i]);
            if (node != null) {
                node.setLastSubnodeChange(now);
            }
        }

    }

    public byte[] getState() {
        // doesn't implement state transfer
        return null;
    }

    public void setState(byte[] bytes) {
        // doesn't implement state transfer
    }
    
    static class InvalidationList implements Serializable {
        Object[] keys;
        Object[] parentKeys;
        String[] types;
        
        public InvalidationList(HashSet keys, HashSet parentKeys, HashSet types) {
            this.keys = keys.toArray();
            this.parentKeys = parentKeys.toArray();
            this.types = (String[]) types.toArray(new String[types.size()]);
        }
    }
}
