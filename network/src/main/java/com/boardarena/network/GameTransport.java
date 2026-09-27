package com.boardarena.network;

/**
 * Scaffold for the networking layer: LAN discovery, hosting a room behind a
 * PIN, and syncing moves between two clients -- mirroring the
 * khelo-tic-tac-toe reference project's multiplayer feature.
 *
 * Not implemented yet; the contract goes in first so {@code app} can be
 * wired against it later without another refactor. Planned shape:
 *   - UDP broadcast for "who's hosting on this LAN" discovery
 *   - TCP for the actual session once a room PIN is entered
 *   - moves serialized as plain strings so any {@code Move} type (Tic-Tac-Toe
 *     today, Chess later) can flow through the same transport
 */
public interface GameTransport {

    void sendMove(String serializedMove);

    void onMoveReceived(MoveListener listener);

    interface MoveListener {
        void onMove(String serializedMove);
    }
}
