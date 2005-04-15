HelmaSwarm
==========

HelmaSwarm is a clustering add-on package that allows multiple instances of
Helma to join into one virtual server. HelmaSwarm uses JGroups
<http://jgroups.org> for the behind-the-scenes communication between Helma
instances. HelmaSwarm provides three tools that connect into various parts of
Helma:

  1) SwarmCache, which acts as a replacement object cache that propagates
     notifications of changed objects to other members in the swarm.

  2) SwarmSessionManager, which is a replacement for the Helma session
     manager that replicates sessions among swarm members and propagates
     session updates and expiry.

  3) SwarmIDGenerator, which is necessary to coordinate primary key generation
     for new persistent objects when the underlying database uses SELECT max(id)
     for creating primary keys (as is usually the case with MySQL). It is not
     needed if Sequences are used for key generation (as usually the case with
     Oracle, for instance).

Asynchronous Communication and Sticky Sessions
==============================================

HelmaSwarm uses asynchronous communication for keeping consistent state among
Helma instances. This means that messages are sent without the sender waiting
for confirmation of receipt by other swarm members. While this greatly reduces
group communication overhead and complexity, it makes it possible that a client
that has altered some state on one Helma instance may still see the old state in
a subsequent request to another swarm member, if that request gets ahead of the
swarm notification of the state change.

For this reason it is advisable (although not strictly necessary) to use sticky
sessions with HelmaSwarm clusters. See doc/README-Apache.txt for information on
how to implement sticky sessions with Apache and mod_jk. Round Robin DNS should
minimize the risk of this scenario, since DNS names aren't refreshed that often,
and if they are chances should be small that DNS lookup and HTTP request gets
ahead of HelmaSwarm communication on the server LAN.

Requirements
============

This version of HelmaSwarm requires a Helma snapshot from
March 25, 2005 or later.

Building
========

HelmaSwarm is built with Apache Ant. 

  1) Edit build.properties to match your Helma installation directory.

  2) Run the following in the command line:

       ant jar

This should compile and build helmaSwarm.jar and copy it to the lib/ext 
directory of your Helma installation along with the JGroups jar file.

Configuration
=============

To enable SwarmCache, SwarmSessionManager, and SwarmIDGenerator for a Helma
application, add the respective lines to its app.properties file:

  cacheImpl = helma.swarm.SwarmCache
  sessionManagerImpl = helma.swarm.SwarmSessionManager
  idGeneratorImpl = helma.swarm.SwarmIDGenerator

HelmaSwarm uses a group name to identify and connect to a particular swarm. By
default, the application name is used as the group name. If you want use a
different group name, for instance because your swarm is made up of
applications with different names, you can set the HelmaSwarm group name with
the swarm.name entry in app.properties:

  swarm.name = mySwarmName

HelmaSwarm uses an XML configuration file from which it reads its properties.
This file is called swarm.conf and is either set by just copying it to the
application directory, or by setting the helma.conf app property:

  swarm.conf = /path/to/helmaswarm/swarm.conf

The most important setting in helma.conf is the JGroups network stack. By
default, HelmaSwarm uses a UDP multicast stack called "udp". helma.swarm also
contains a TCP stack. The JGroups stack is configured with the
following app property:

  swarm.jgroups.stack = [udp|tcp|custom]

The default UDP multicast stack uses port 22024 on multicast address
224.0.0.132. It is advisable to use a different setting if multiple swarm
instances are operated on the same local network to avoid unnecessary network
traffic.

Credits & Feedback
==================

HelmaSwarm is written by Hannes Wallnoefer (hannes at helma dot at).
This sofware was initially inspired by SwarmCache <http://swarmcache.sf.net/>,
from which it borrows part of its name.
