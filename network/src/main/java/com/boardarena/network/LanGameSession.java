package com.boardarena.network;

import com.boardarena.core.Game;
import com.boardarena.core.GameEngine;
import com.boardarena.core.GameState;
import com.boardarena.core.Move;
import com.boardarena.core.PlayerId;
import com.boardarena.core.multiplayer.MultiplayerSession;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-authoritative two-player session over TCP. Only the host mutates its
 * engine in response to a remote request; the guest applies accepted moves in
 * the exact order sent by the host.
 */
final class LanGameSession<M extends Move> implements MultiplayerSession<M> {

    private final Game<M> game;
    private final GameEngine<M> engine;
    private final PlayerId localPlayer;
    private final String roomCode;
    private final boolean hostMode;
    private final ServerSocket serverSocket;
    private final LanDiscovery.Advertiser advertiser;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Listener<M>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    private volatile boolean ready;
    private volatile boolean closed;
    private volatile TcpGameTransport transport;
    private long nextSequence = 1;

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
}
