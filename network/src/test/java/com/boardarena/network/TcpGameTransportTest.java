package com.boardarena.network;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcpGameTransportTest {

    @Test
    void sendsOrderedFramesOverTcp() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            ArrayBlockingQueue<String> received = new ArrayBlockingQueue<>(4);

            Thread.ofVirtual().start(() -> {
                try {
                    TcpGameTransport transport = TcpGameTransport.accepted(server.accept());
                    transport.onMessage(received::add);
                    transport.onClosed(ignored -> { });
                    transport.start();
                } catch (Exception ignored) {
                }
            });

            TcpGameTransport client = TcpGameTransport.connect("127.0.0.1", server.getLocalPort());
            client.start();
            client.send("HELLO|1|tic-tac-toe|123456");
            client.send("MOVE|1|MCwy");

            assertEquals("HELLO|1|tic-tac-toe|123456", received.poll(2, TimeUnit.SECONDS));
            assertEquals("MOVE|1|MCwy", received.poll(2, TimeUnit.SECONDS));
            client.close();
        }
    }
}
