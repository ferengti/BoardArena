package com.boardarena.core.multiplayer;

import com.boardarena.core.Game;
import com.boardarena.core.Move;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Entry point for multiplayer implementations. The app depends only on this
 * contract; the concrete LAN implementation is discovered via ServiceLoader.
 */
public interface MultiplayerProvider {

    String id();

    boolean supports(Game<?> game);

    <M extends Move> MultiplayerSession<M> host(Game<M> game) throws IOException;

    List<GameRoom> discover(Game<?> game, Duration timeout) throws IOException;

    <M extends Move> MultiplayerSession<M> join(Game<M> game, GameRoom room) throws IOException;
}
