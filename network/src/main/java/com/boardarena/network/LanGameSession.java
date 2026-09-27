package com.boardarena.network;

import com.boardarena.core.Game;
import com.boardarena.core.GameEngine;
import com.boardarena.core.GameState;
import com.boardarena.core.Move;
import com.boardarena.core.PlayerId;
import com.boardarena.core.multiplayer.MultiplayerSession;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Server-authoritative two-player session over TCP. Only the host mutates its
 * engine in response to a remote request; the guest applies accepted moves in
 * the exact order sent by the host.
 */
final class LanGameSession<M extends Move> implements MultiplayerSession<M> {

    private final Game<M> game;
    private final PlayerId localPlayer;
    private final String roomCode;
    private final boolean hostMode;
    private final ServerSocket serverSocket;
    private final LanDiscovery.Advertiser advertiser;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Listener<M>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private final List<Consumer<String>> chatListeners =
            new CopyOnWriteArrayList<>();

    private final List<RematchListener> rematchListeners =
            new CopyOnWriteArrayList<>();

    private volatile GameEngine<M> engine;
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile TcpGameTransport transport;
    private long nextSequence = 1;
    private long rematchRound;
    private boolean localRematchRequested;
    private boolean remoteRematchRequested;




    private LanGameSession(
            Game<M> game,
            PlayerId localPlayer,
            String roomCode,
            boolean hostMode,
            ServerSocket serverSocket,
            LanDiscovery.Advertiser advertiser) {
        this.game = Objects.requireNonNull(game);
        this.engine = new GameEngine<>(game.newInitialState());
        this.localPlayer = Objects.requireNonNull(localPlayer);
        this.roomCode = Objects.requireNonNull(roomCode);
        this.hostMode = hostMode;
        this.serverSocket = serverSocket;
        this.advertiser = advertiser;
    }

    static <M extends Move> LanGameSession<M> host(Game<M> game) throws IOException {
        String roomCode = generateRoomCode();
        ServerSocket serverSocket = new ServerSocket(0);
        LanGameRoom room = new LanGameRoom(
                roomCode,
                game.id(),
                game.displayName(),
                serverSocket.getInetAddress(),
                serverSocket.getLocalPort());
        LanDiscovery.Advertiser advertiser = LanDiscovery.advertise(room);
        LanGameSession<M> session = new LanGameSession<>(
                game, PlayerId.PLAYER_ONE, roomCode, true, serverSocket, advertiser);
        session.acceptClients();
        return session;
    }

    static <M extends Move> LanGameSession<M> join(Game<M> game, LanGameRoom room) throws IOException {
        if (!game.id().equals(room.gameId())) {
            throw new IllegalArgumentException("Room belongs to another game: " + room.gameId());
        }

        TcpGameTransport transport = TcpGameTransport.connect(room.host().getHostAddress(), room.tcpPort());
        LanGameSession<M> session = new LanGameSession<>(
                game, PlayerId.PLAYER_TWO, room.roomCode(), false, null, null);
        session.attachTransport(transport);
        transport.start();
        transport.send(Protocol.hello(game.id(), room.roomCode()));
        return session;
    }

    @Override
    public GameState<M> currentState() {
        return engine.currentState();
    }

    @Override
    public PlayerId localPlayer() {
        return localPlayer;
    }

    @Override
    public String roomCode() {
        return roomCode;
    }

    @Override
    public boolean isReady() {
        return ready && !closed;
    }

    @Override
    public void addListener(Listener<M> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void addChatListener(Consumer<String> listener) {
        chatListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void addRematchListener(RematchListener listener) {
        rematchListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void sendChatMessage(String message) {
        String normalized = Objects.requireNonNull(message).trim();

        if (normalized.isEmpty()) {
            return;
        }

        if (normalized.length() > 500) {
            throw new IllegalArgumentException(
                    "Chat message must be at most 500 characters");
        }

        synchronized (lock) {
            ensurePlayable();

            try {
                transport.send(Protocol.chat(normalized));
            } catch (IOException e) {
                disconnect(e);
                throw new IllegalStateException(
                        "Unable to send chat message", e);
            }
        }
    }

    @Override
    public void requestRematch() {
        synchronized (lock) {
            ensurePlayable();

            if (!engine.currentState().isGameOver()) {
                throw new IllegalStateException(
                        "Rematch can only be requested after the game ends");
            }

            if (localRematchRequested) {
                return;
            }

            localRematchRequested = true;

            try {
                transport.send(Protocol.rematchRequest());
            } catch (IOException e) {
                localRematchRequested = false;
                disconnect(e);

                throw new IllegalStateException(
                        "Unable to request rematch", e);
            }
        }
    }

    @Override
    public void respondToRematch(boolean accepted) {
        synchronized (lock) {
            ensurePlayable();

            if (!remoteRematchRequested) {
                throw new IllegalStateException(
                        "No rematch request is pending");
            }

            remoteRematchRequested = false;

            try {
                transport.send(
                        accepted
                                ? Protocol.rematchAccept()
                                : Protocol.rematchDecline()
                );

                if (accepted && hostMode) {
                    startRematch();
                }

            } catch (IOException e) {
                disconnect(e);

                throw new IllegalStateException(
                        "Unable to respond to rematch", e);
            }
        }
    }

    private void startRematch() {
        if (!hostMode) {
            return;
        }

        rematchRound++;
        localRematchRequested = false;
        remoteRematchRequested = false;

        engine = new GameEngine<>(game.newInitialState());
        nextSequence = 1;

        try {
            transport.send(Protocol.rematchStart(rematchRound));
        } catch (IOException e) {
            disconnect(e);
            return;
        }

        notifyStateChanged();
        notifyRematchStarted();
    }

    private void notifyChat(String message) {
        for (Consumer<String> listener : chatListeners) {
            listener.accept(message);
        }
    }

    private void notifyRematchRequest() {
        for (RematchListener listener : rematchListeners) {
            listener.onRequest();
        }
    }

    private void notifyRematchDeclined() {
        for (RematchListener listener : rematchListeners) {
            listener.onDeclined();
        }
    }

    private void notifyRematchStarted() {
        for (RematchListener listener : rematchListeners) {
            listener.onStarted();
        }
    }

    @Override
    public void playMove(M move) {
        Objects.requireNonNull(move);
        synchronized (lock) {
            ensurePlayable();
            if (engine.currentState().currentPlayer() != localPlayer) {
                throw new IllegalStateException("It is not your turn");
            }
            if (!engine.currentState().legalMoves().contains(move)) {
                throw new IllegalArgumentException("Illegal move: " + move);
            }

            if (hostMode) {
                applyHostMove(move);
            } else {
                try {
                    transport.send(Protocol.moveRequest(game.moveCodec().encode(move)));
                } catch (IOException e) {
                    disconnect(e);
                    throw new IllegalStateException("Unable to send move", e);
                }
            }
        }
    }

    private void applyHostMove(M move) {
        engine.playMove(move);
        String frame = Protocol.move(nextSequence++, game.moveCodec().encode(move));
        try {
            transport.send(frame);
        } catch (IOException e) {
            disconnect(e);
            throw new IllegalStateException("Unable to send move", e);
        }
        notifyStateChanged();
    }

    private void acceptClients() {
        executor.submit(() -> {
            try {
                while (!closed) {
                    Socket socket = serverSocket.accept();
                    synchronized (lock) {
                        if (transport != null || ready) {
                            try (socket) {
                                TcpGameTransport rejected = TcpGameTransport.accepted(socket);
                                rejected.send(Protocol.error("ROOM_FULL", "Room already has two players"));
                                rejected.close();
                            }
                            continue;
                        }
                        TcpGameTransport accepted = TcpGameTransport.accepted(socket);
                        attachTransport(accepted);
                        accepted.start();
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    disconnect(e);
                }
            }
        });
    }

    private void attachTransport(TcpGameTransport nextTransport) {
        this.transport = nextTransport;
        nextTransport.onMessage(this::onFrame);
        nextTransport.onClosed(this::onTransportClosed);
    }

    private void onFrame(String frame) {
        try {
            synchronized (lock) {
                if (closed) {
                    return;
                }

                if (hostMode) {
                    handleHostFrame(frame);
                } else {
                    handleGuestFrame(frame);
                }
            }
        } catch (RuntimeException e) {
            disconnect(e);
        }
    }

    private void handleHostFrame(String frame) {
        if (!ready) {
            Protocol.Hello hello = Protocol.parseHello(frame);
            if (!game.id().equals(hello.gameId()) || !roomCode.equals(hello.roomCode())) {
                sendErrorAndClose("BAD_ROOM", "Invalid game or room code");
                return;
            }
            try {
                transport.send(Protocol.welcome(game.id(), roomCode));
                transport.send(Protocol.READY);
                ready = true;
                if (advertiser != null) {
                    advertiser.close();
                }
                notifyStateChanged();
            } catch (IOException e) {
                disconnect(e);
            }
            return;
        }

        if (frame.startsWith(Protocol.MOVE_REQUEST + "|")) {
            if (engine.currentState().currentPlayer() != PlayerId.PLAYER_TWO) {
                sendErrorAndClose("OUT_OF_TURN", "Remote player moved out of turn");
                return;
            }
            String encodedMove = Protocol.parseMoveRequest(frame);
            M move = game.moveCodec().decode(encodedMove);
            applyHostMove(move);
            return;
        }

        if (frame.startsWith(Protocol.BYE + "|")) {
            disconnect(null);
            return;
        }

        if (frame.startsWith(Protocol.CHAT + "|")) {
            notifyChat(Protocol.parseChat(frame));
            return;
        }

        if (Protocol.REMATCH_REQUEST.equals(frame)) {
            if (!engine.currentState().isGameOver()) {
                sendErrorAndClose(
                        "BAD_REMATCH",
                        "Rematch was requested before the game ended");
                return;
            }

            remoteRematchRequested = true;
            notifyRematchRequest();
            return;
        }

        if (Protocol.REMATCH_ACCEPT.equals(frame)) {
            if (!localRematchRequested && !remoteRematchRequested) {
                return;
            }

            localRematchRequested = false;
            remoteRematchRequested = false;

            startRematch();
            return;
        }

        if (Protocol.REMATCH_DECLINE.equals(frame)) {
            if (!localRematchRequested) {
                return;
            }

            localRematchRequested = false;
            notifyRematchDeclined();
            return;
        }

        sendErrorAndClose("BAD_MESSAGE", "Unexpected message");
    }

    private void handleGuestFrame(String frame) {
        if (frame.startsWith(Protocol.WELCOME + "|")) {
            Protocol.Welcome welcome = Protocol.parseWelcome(frame);
            if (!game.id().equals(welcome.gameId())
                    || !roomCode.equals(welcome.roomCode())
                    || welcome.player() != PlayerId.PLAYER_TWO) {
                sendErrorAndClose("BAD_HANDSHAKE", "Invalid host handshake");
            }
            return;
        }

        if (Protocol.READY.equals(frame)) {
            ready = true;
            notifyStateChanged();
            return;
        }

        if (frame.startsWith(Protocol.MOVE + "|")) {
            Protocol.MoveFrame moveFrame = Protocol.parseMove(frame);
            if (moveFrame.sequence() != nextSequence) {
                sendErrorAndClose("BAD_SEQUENCE", "Unexpected move sequence");
                return;
            }
            M move = game.moveCodec().decode(moveFrame.encodedMove());
            engine.playMove(move);
            nextSequence++;
            notifyStateChanged();
            return;
        }

        if (frame.startsWith(Protocol.ERROR + "|")) {
            disconnect(new IOException(Protocol.parseError(frame).message()));
            return;
        }

        if (frame.startsWith(Protocol.BYE + "|")) {
            disconnect(null);
            return;
        }

        if (frame.startsWith(Protocol.CHAT + "|")) {
            notifyChat(Protocol.parseChat(frame));
            return;
        }

        if (Protocol.REMATCH_REQUEST.equals(frame)) {
            if (!engine.currentState().isGameOver()) {
                sendErrorAndClose(
                        "BAD_REMATCH",
                        "Rematch was requested before the game ended");
                return;
            }

            remoteRematchRequested = true;
            notifyRematchRequest();
            return;
        }

        if (Protocol.REMATCH_ACCEPT.equals(frame)) {
            localRematchRequested = false;
            remoteRematchRequested = false;

            // Host owns the authoritative reset.
            return;
        }

        if (Protocol.REMATCH_DECLINE.equals(frame)) {
            if (!localRematchRequested) {
                return;
            }

            localRematchRequested = false;
            notifyRematchDeclined();
            return;
        }

        if (frame.startsWith(Protocol.REMATCH_START + "|")) {
            long round = Protocol.parseRematchStart(frame);

            if (round <= rematchRound) {
                return;
            }

            rematchRound = round;
            localRematchRequested = false;
            remoteRematchRequested = false;

            engine = new GameEngine<>(game.newInitialState());
            nextSequence = 1;

            notifyStateChanged();
            notifyRematchStarted();
            return;
        }

        sendErrorAndClose("BAD_MESSAGE", "Unexpected message");
    }

    private void notifyStateChanged() {
        GameState<M> state = engine.currentState();
        for (Listener<M> listener : listeners) {
            listener.onStateChanged(state);
        }
    }

    private void ensurePlayable() {
        if (closed) {
            throw new IllegalStateException("Session is closed");
        }
        if (!ready) {
            throw new IllegalStateException("Opponent has not joined");
        }
    }

    private void sendErrorAndClose(String code, String message) {
        try {
            if (transport != null && transport.isOpen()) {
                transport.send(Protocol.error(code, message));
            }
        } catch (IOException ignored) {
        } finally {
            disconnect(new IOException(message));
        }
    }

    private void onTransportClosed(Throwable cause) {
        synchronized (lock) {
            disconnect(cause);
        }
    }

    private void disconnect(Throwable ignored) {
        if (closed) {
            return;
        }
        closed = true;
        ready = false;
        if (advertiser != null) {
            advertiser.close();
        }
        if (transport != null) {
            transport.close();
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignoredClose) {
            }
        }
        executor.shutdownNow();
        notifyStateChanged();
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            try {
                if (transport != null && transport.isOpen()) {
                    transport.send(Protocol.bye());
                }
            } catch (IOException ignored) {
            }
            disconnect(null);
        }
    }

    private static String generateRoomCode() {
        return String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
    }

    LanGameRoom localRoom() {
        if (!hostMode || serverSocket == null) {
            throw new IllegalStateException(
                    "Only a host session has a local room");
        }

        return new LanGameRoom(
                roomCode,
                game.id(),
                game.displayName(),
                InetAddress.getLoopbackAddress(),
                serverSocket.getLocalPort());
    }
}
