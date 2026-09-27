package com.boardarena.core;

import com.boardarena.core.ai.AiStrategy;

/**
 * Describes a playable game: how to start it, and how its AI opponent works.
 * The app module will keep a catalog of these (Tic-Tac-Toe today, Chess once
 * it lands) so the UI and network layers never need to know about a
 * specific game's rules directly.
 *
 * @param <M> the concrete move type for this game
 */
public interface Game<M extends Move> {

    /** Stable machine-readable id, e.g. "tic-tac-toe". Used in network messages and save files. */
    String id();

    /** Human-readable name shown in menus, e.g. "Tic-Tac-Toe". */
    String displayName();

    /** A fresh starting position. */
    GameState<M> newInitialState();

    AiStrategy<M> aiStrategy();
}
