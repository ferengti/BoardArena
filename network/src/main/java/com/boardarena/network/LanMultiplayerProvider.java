package com.boardarena.network;

import com.boardarena.core.Game;
import com.boardarena.core.Move;
import com.boardarena.core.multiplayer.GameRoom;
import com.boardarena.core.multiplayer.MultiplayerProvider;
import com.boardarena.core.multiplayer.MultiplayerSession;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** LAN provider: UDP discovery plus a TCP game session. */
public final class LanMultiplayerProvider implements MultiplayerProvider {

    @Override
    public String id() {
        return "lan";
    }

    @Override
    public boolean supports(Game<?> game) {
        return game != null;
    }

    @Override
    public <M extends Move> MultiplayerSession<M> host(Game<M> game) throws IOException {
        return LanGameSession.host(game);
    }

    @Override
    public List<GameRoom> discover(Game<?> game, Duration timeout) throws IOException {
        if (timeout.isNegative() || timeout.isZero()) {
            return List.of();
        }
        List<LanGameRoom> rooms = new ArrayList<>(LanDiscovery.discover(game.id(), timeout));
        rooms.sort(Comparator.comparing(LanGameRoom::roomCode));
        return List.copyOf(rooms);
    }

    @Override
    public <M extends Move> MultiplayerSession<M> join(Game<M> game, GameRoom room) throws IOException {
        if (!(room instanceof LanGameRoom lanRoom)) {
            throw new IllegalArgumentException("Room was not created by the LAN provider");
        }
        return LanGameSession.join(game, lanRoom);
    }
}
