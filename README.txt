HelmaSwarm
==========

HelmaSwarm is a distributed ObjectCache implementation for Helma clusters 
based on JGroups. 

Requirements
============

HelmaSwarm requires a Helma snapshot from October 26, 2004 or later.

Known bugs and limitions
========================

HelmaSwarm currently does not propagate changes in collections with 
cachemode=aggressive set. 

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

To enable the distributed HelmaSwarm cache for one of your applications,
add the following property to its app.properties file:

  cacheimpl = helma.objectmodel.swarm.HelmaSwarmCache

By default, HelmaSwarm will use port 22023 on multicast address 224.0.0.132.
To use a different port or address or change the default ttl of 32, use the 
following properties:

  helmaswarm.multicast_ip = 224.0.0.132
  helmaswarm.multicast_port = 22023
  helmaswarm.multicast_ttl = 32

Credits & Feedback
==================

HelmaSwarm is heavily inspired by SwarmCache <http://swarmcache.sf.net/>, 
from which it borrows some code. Send bug reports and feedback to 
Hannes Wallnoefer <hannes@helma.at>.

