package com.boardarena.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Line-delimited UTF-8 TCP implementation of {@link GameTransport}. */
final class TcpGameTransport implements GameTransport {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile Consumer<String> messageListener = ignored -> {
    };
    private volatile Consumer<Throwable> closedListener = ignored -> {
    };

    private TcpGameTransport(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    static TcpGameTransport connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3_000);
        return new TcpGameTransport(socket);
    }

    static TcpGameTransport accepted(Socket socket) throws IOException {
        return new TcpGameTransport(socket);
    }

    void start() {
        Thread.ofVirtual().name("boardarena-tcp-reader").start(this::readLoop);
    }

    @Override
    public synchronized void send(String frame) throws IOException {
        if (!isOpen()) {
            throw new IOException("Transport is closed");
        }
        if (frame.indexOf('\n') >= 0 || frame.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Frames must not contain line breaks");
        }
        writer.write(frame);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void onMessage(Consumer<String> listener) {
        messageListener = Objects.requireNonNull(listener);
    }

    @Override
    public void onClosed(Consumer<Throwable> listener) {
        closedListener = Objects.requireNonNull(listener);
    }

    @Override
    public boolean isOpen() {
        return open.get() && !socket.isClosed();
    }

    @Override
    public void close() {
        if (!open.getAndSet(false)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void readLoop() {
        Throwable failure = null;
        try {
            String line;
            while (isOpen() && (line = reader.readLine()) != null) {
                messageListener.accept(line);
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            open.set(false);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            closedListener.accept(failure);
        }
    }
}
