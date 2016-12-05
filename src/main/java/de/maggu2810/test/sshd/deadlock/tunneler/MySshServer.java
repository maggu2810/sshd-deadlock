
package de.maggu2810.test.sshd.deadlock.tunneler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.forward.DefaultTcpipForwarderFactory;
import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySshServer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int nioWorkers;
    private final PortForwardingEventListener pfel;

    private SshServer sshd;

    public MySshServer(final int nioWorkers, final PortForwardingEventListener pfel) {
        this.nioWorkers = nioWorkers;
        this.pfel = pfel;
    }

    public void start(final int port) throws IOException {
        sshd = createSshd(port);
        sshd.start();
    }

    public void stop() {
        if (sshd != null) {
            try {
                sshd.stop();
            } catch (final IOException ex) {
                logger.error("Error while stopping sshd.", ex);
            }
            sshd = null;
        }
    }

    private final SshServer createSshd(final int portServer) {
        final SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(portServer);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

        // Do not set ANY password authenticator, so it is kept set to null.
        // sshd.setPasswordAuthenticator(new StaticPasswordAuthenticator(false));

        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);

        // Accept all TCP/IP forwarding filters.
        sshd.setTcpipForwardingFilter(AcceptAllForwardingFilter.INSTANCE);

        sshd.setTcpipForwarderFactory(new DefaultTcpipForwarderFactory());

        sshd.addPortForwardingEventListener(pfel);

        // Allow a very long idle
        PropertyResolverUtils.updateProperty(sshd, FactoryManager.IDLE_TIMEOUT, TimeUnit.HOURS.toMillis(12));

        // There should be only one concurrent open session count per username (MAC).
        PropertyResolverUtils.updateProperty(sshd, ServerFactoryManager.MAX_CONCURRENT_SESSIONS, 1);

        // Let's set the number of the NIO workers
        PropertyResolverUtils.updateProperty(sshd, FactoryManager.NIO_WORKERS, nioWorkers);

        return sshd;
    }

}
