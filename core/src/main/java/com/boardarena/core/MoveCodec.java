package com.boardarena.core;

/**
 * Serializes and parses a game's concrete moves for persistence or transport.
 * The core only defines the contract; each game owns its wire representation.
 */
public interface MoveCodec<M extends Move> {

    String encode(M move);

    M decode(String value);
}
