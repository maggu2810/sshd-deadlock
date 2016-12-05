
package de.maggu2810.test.sshd.deadlock;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import de.maggu2810.test.sshd.deadlock.client.SimpleClient;
import de.maggu2810.test.sshd.deadlock.tunneledclient.EchoServer;
import de.maggu2810.test.sshd.deadlock.tunneledclient.MySshClient;
import de.maggu2810.test.sshd.deadlock.tunneler.MySshServer;

public class App {

    // private final static int NUM_OF_SSHD_SERVERS_NIO_WORKERS = 25;
    private final static int NUM_OF_SSHD_SERVERS_NIO_WORKERS = 3;
    private final static int NUM_OF_TUNNELED_CLIENTS = 4;
    private final static int SIMPLE_CLIENTS_PER_TUNNELED_CLIENT = 5;
    private final static long ECHO_SERVER_RD_SLEEP_MS = 0;
    private final static long ECHO_SERVER_WR_SLEEP_MS = 0;

    private class PortForwardingEventListenerImpl implements PortForwardingEventListener {
        @Override
        public void establishedExplicitTunnel(final Session session, final SshdSocketAddress local,
                final SshdSocketAddress remote, final boolean localForwarding, final SshdSocketAddress boundAddress,
                final Throwable reason) throws IOException {
            System.out.println(String.format("local: %s, remote: %s, localForwarding: %b, boundAddress: %s, reason: %s",
                    local, remote, localForwarding, boundAddress, reason));

            final SocketAddress addr = boundAddress.toInetSocketAddress();
            for (int i = 0; i < SIMPLE_CLIENTS_PER_TUNNELED_CLIENT; ++i) {
                new ScheduleSimpleClientLoop(scheduler, addr).schedule();
            }
        }
    }

    private class ScheduleSimpleClientLoop implements Runnable {
        private final ScheduledExecutorService scheduler;
        private final SocketAddress addr;

        public ScheduleSimpleClientLoop(final ScheduledExecutorService scheduler, final SocketAddress addr) {
            this.scheduler = scheduler;
            this.addr = addr;
        }

        @Override
        public void run() {
            final SimpleClient sc = new SimpleClient(addr);
            sc.exec();
            schedule();
        }

        public void schedule() {
            scheduler.execute(this::run);
        }
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final PortForwardingEventListener pfel = new PortForwardingEventListenerImpl();

    public App() {
    }

    public void exec() throws InterruptedException, IOException {
        final int tunnelerPort = 12000;

        final MySshServer tunneler = new MySshServer(NUM_OF_SSHD_SERVERS_NIO_WORKERS, pfel);
        tunneler.start(tunnelerPort);

        // Just to give the server some time to start...
        Thread.sleep(2000);

        for (int i = 0; i < NUM_OF_TUNNELED_CLIENTS; ++i) {
            final int port = 10000 + i;
            final EchoServer server = new EchoServer(port, ECHO_SERVER_RD_SLEEP_MS, ECHO_SERVER_WR_SLEEP_MS);
            server.start();

            final MySshClient client = new MySshClient("localhost", tunnelerPort, port, Integer.toString(port));
            client.start();
        }
    }

    public static void main(final String[] args) throws InterruptedException, IOException {
        new App().exec();
    }
}
