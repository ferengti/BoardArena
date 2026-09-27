package com.boardarena.core.multiplayer;

import com.boardarena.core.GameSession;
import com.boardarena.core.Move;
import com.boardarena.core.PlayerId;

/**
 * Multiplayer match exposed to the application without exposing any network
 * transport details.
 */
public interface MultiplayerSession<M extends Move> extends GameSession<M> {

    PlayerId localPlayer();

    String roomCode();
}
