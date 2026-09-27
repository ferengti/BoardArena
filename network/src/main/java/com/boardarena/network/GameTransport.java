package com.boardarena.network;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Reliable, ordered text-frame transport for a game session.
 * The transport deliberately knows nothing about moves or games. Protocol
 * framing and game-specific serialization belong above this layer.
 */
public interface GameTransport extends AutoCloseable {

    void send(String frame) throws IOException;

    void onMessage(Consumer<String> listener);

    void onClosed(Consumer<Throwable> listener);

    boolean isOpen();

    @Override
    void close();
}
