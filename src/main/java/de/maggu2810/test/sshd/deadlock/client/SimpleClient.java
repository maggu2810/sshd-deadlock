
package de.maggu2810.test.sshd.deadlock.client;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

public class SimpleClient {

    private final SocketAddress tunnel;

    public SimpleClient(final SocketAddress tunnel) {
        this.tunnel = tunnel;
    }

    public void exec() {
        try (final Socket s = new Socket()) {
            s.connect(tunnel);
            s.getOutputStream().write(new byte[] { 0, 1, 2, 3, 4 });
            int r;
            while ((r = s.getInputStream().read()) != -1) {
                if (r == 4) {
                    return;
                }
            }
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }

}
