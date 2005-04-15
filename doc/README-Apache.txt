Implementing Helma Load Balancing with Apache and mod_jk
========================================================

The mod_jk Apache Tomcat connector implements load balancing among backend
servers using the AJP13 protocol and is thus a good choice for setting
up a Helma cluster. The workers.properties file in this directory contains a
simple setup for a mod_jk load balanced worker that distributes the load among
two ajp workers to localhost:8009 and localhost:9009.

Note that since HelmaSwarm uses asynchronous communication among swarm members
(as detailed in the main README.txt file), it is advisable to implement sticky
sessions so that requests from one client will always be routed to the same
Helma instance (unless it becomes unavailable, of which mod_jk will take care
transparently). 

Sticky sessions are enabled by setting the sticky_session property for the
load balanced worker to 1. To make them work you also need to set a
JSESSIONID cookie in your application that lets the load balancer know which
Helma instance a request should be routed to. This cookie should contain a
"." followed by the name of the worker by which the session should be handled.
The part before the dot usually contains the session id for Tomcat instances,
but can be ignored for our purposes. A typical way to set this cookie would be
to use the following code at the beginning of each request (or at the point
where you want a session to become sticky):

   if (!req.data.JSESSIONID) 
       res.setCookie("JSESSIONID", "foo.worker1");

Of course in a real world setup you'd set the worker name in the app.properties
file in order not to have to change the code on each application instance.

To test whether sticky sessions work just make sure all requests from one
browser are handled by the same Helma instance.