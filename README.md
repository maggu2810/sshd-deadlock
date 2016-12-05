# SSHD Deadlock demonstration

This little project should demonstrate a deadlock in the current SSHD implementation.
Perhaps it is caused by a wrong usage, I don't know.

## Classes

### de.maggu2810.test.sshd.deadlock.App

That is the main test application.

It creates:
* 1 server that is used for tunneling
* x client servers that is currently a very simple echo service implementation
* x client tunnel connectors that connects to the server and asks for a tunnel to the resp. client simple echo service server
* y clients that are using the tunnels to communicate with the simple echo services

### de.maggu2810.test.sshd.deadlock.client.SimpleClient

A client that connects to a simple echo service (a tunneled socket address is given), writes and reads some bytes and finishes then.

### de.maggu2810.test.sshd.deadlock.tunneledclient.EchoServer

A very simple implementation of a server that reads bytes and try to write the same bytes back.

### de.maggu2810.test.sshd.deadlock.tunneledclient.MySshClient

Try to create a ssh tunnel to forward a port using a ssh connection to a remote port.

### de.maggu2810.test.sshd.deadlock.tunneler.MySshServer

A SSH server that accepts incoming port forwarding requests and inform a listener.

## Description

See: http://www.mail-archive.com/users@mina.apache.org/msg06451.html

As you can see if the number of workers is not big enough after some time all NIO workers are waiting and there is no one to wake them up.