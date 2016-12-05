
package de.maggu2810.test.sshd.deadlock.tunneledclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sshd.common.util.io.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoServer {

    private final Logger logger = LoggerFactory.getLogger(EchoServer.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ServerSocket> server = new AtomicReference<>();

    private final int port;
    private final long sleepRead;
    private final long sleepWrite;

    public EchoServer(final int port, final long sleepRead, final long sleepWrite) {
        this.port = port;
        this.sleepRead = sleepRead;
        this.sleepWrite = sleepWrite;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            final Thread t = new Thread() {
                @Override
                public void run() {
                    final HashMap<ClientHandler, Socket> clients = new HashMap<>();
                    try (final ServerSocket server = new ServerSocket(port)) {
                        EchoServer.this.server.set(server);
                        while (running.get()) {
                            final Socket client = server.accept();
                            final ClientHandler handler = new ClientHandler(client, sleepRead, sleepWrite);
                            clients.put(handler, client);
                            handler.start();
                        }
                    } catch (final IOException ex) {
                        ex.printStackTrace();
                    }
                    for (final Map.Entry<ClientHandler, Socket> entry : clients.entrySet()) {
                        IoUtils.closeQuietly(entry.getValue());
                        // entry.getKey().interrupt();
                        try {
                            entry.getKey().join();
                        } catch (final InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                    server.set(null);
                    running.set(false);
                }
            };
            t.setDaemon(true);
            t.start();
        } else {
            throw new IllegalStateException("Sever is already running");
        }
    }

    public void stop() {
        final ServerSocket server = this.server.get();
        if (server != null) {
            try {
                server.close();
            } catch (final IOException ex) {
                logger.trace("Closing failed. {}", ex);
            }
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket client;
        private final long sleepRead;
        private final long sleepWrite;

        public ClientHandler(final Socket client, final long sleepRead, final long sleepWrite) {
            this.client = client;
            this.sleepRead = sleepRead;
            this.sleepWrite = sleepWrite;
        }

        @Override
        public void run() {
            try {
                final InputStream is = client.getInputStream();
                final OutputStream os = client.getOutputStream();

                int nRead;
                final byte[] data = new byte[16384];

                for (;;) {
                    Thread.sleep(sleepRead);
                    nRead = is.read(data, 0, data.length);
                    if (nRead == -1) {
                        return;
                    }

                    System.out.println("forward: " + Arrays.toString(data));

                    Thread.sleep(sleepWrite);
                    os.write(data);
                }
            } catch (final Exception e) {
                System.err.println("Exception caught: client disconnected.");
            } finally {
                IoUtils.closeQuietly(client);
            }
        }
    }

}
