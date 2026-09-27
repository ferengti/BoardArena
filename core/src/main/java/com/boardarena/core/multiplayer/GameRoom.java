package com.boardarena.core.multiplayer;

/**
 * Opaque description of a discoverable multiplayer room. Network-specific
 * details such as IP addresses and ports stay inside the provider module.
 */
public interface GameRoom {

    String roomCode();

    String gameId();

    String displayName();
}
