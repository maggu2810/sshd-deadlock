/*-
 * #%L
 * ash :: Bundles :: Gateway :: Client
 * %%
 * Copyright (C) 2015 - 2016 aleon GmbH
 * %%
 * ***
 * aleon GmbH
 * Sigmannser Weg 2
 * D-88239 Wangen
 * phone: +49 (0)7522 2654-100
 * fax:   +49 (0)7522 2654-149
 * mail:  contact@aleon.eu
 * ***
 * All rights reserved!
 * Proprietary and confidential! Do not redistribute!
 * Unpublished work. All rights reserved under the German copyright laws.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * The software must not be copied, distributed, changed, rewritten and / or
 * re-factored without the express permission of the copyright holder.
 * #L%
 */

package de.maggu2810.test.sshd.deadlock.tunneledclient;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySshClient implements Closeable {

    private static final String MY_HOSTNAME = "localhost";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String serverHost;
    private final int serverPort;
    private final int tunneledPort;
    private final String username;
    private final SshClient client;

    public MySshClient(final String host, final int port, final int tunneledPort, final String username) {
        this.serverHost = host;
        this.serverPort = port;
        this.tunneledPort = tunneledPort;
        this.username = username;
        this.client = createClient();
    }

    /**
     * Start the client handler.
     *
     * @throws IOException
     */
    public boolean start() throws IOException {
        client.start();

        logger.trace("Try to connect");
        final ConnectFuture sessionFuture = client.connect(username, serverHost, serverPort);
        sessionFuture.await();
        logger.trace("Connected");

        final ClientSession session;
        try {
            session = sessionFuture.getSession();
        } catch (final RuntimeSshException ex) {
            final Throwable throwable = ex.getCause();
            if (throwable instanceof ConnectException) {
                logger.info("Cannot get session because of connection error: {}", throwable.getMessage());
                return false;
            } else {
                throw ex;
            }
        }
        if (session == null) {
            logger.info("Received a null session.");
            return false;
        }

        // Use key of clients host key provider
        // session.addPasswordIdentity("foo");

        logger.trace("Try to authenticate");
        final AuthFuture authFuture = session.auth();
        authFuture.await();
        if (!authFuture.isSuccess()) {
            logger.warn("Authentication failed");
            return false;
        }
        logger.trace("Authenticated");

        final SshdSocketAddress local = new SshdSocketAddress(MY_HOSTNAME, tunneledPort);
        final SshdSocketAddress remote = new SshdSocketAddress(MY_HOSTNAME, 0);

        logger.trace("Trigger start of remote port forwarding {remote={}, local={}}", remote, local);
        final SshdSocketAddress bound = session.startRemotePortForwarding(remote, local);
        logger.trace("Start remote port forwarding returned {bound={}}.", bound);

        return true;
    }

    /**
     * Close and stop the client handler.
     */
    @Override
    public void close() {
        client.stop();
    }

    private final SshClient createClient() {
        final SshClient client = SshClient.setUpDefaultClient();

        // Set a heartbeat interval to keep the connection alive
        PropertyResolverUtils.updateProperty(client, ClientFactoryManager.HEARTBEAT_INTERVAL,
                TimeUnit.MINUTES.toMillis(1));

        // Allow a very long idle
        PropertyResolverUtils.updateProperty(client, FactoryManager.IDLE_TIMEOUT, TimeUnit.DAYS.toMillis(1));

        client.setTcpipForwardingFilter(AcceptAllForwardingFilter.INSTANCE);

        client.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

        return client;
    }

}
