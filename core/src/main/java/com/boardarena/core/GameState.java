package com.boardarena.core;

import java.util.List;
import java.util.Optional;

/**
 * An immutable snapshot of a game in progress. Every game module supplies
 * its own implementation. Implementations must never mutate in place --
 * {@link #applyMove(Move)} returns a brand-new state, which keeps AI search
 * (minimax and friends) and future network replay/undo simple and safe.
 *
 * @param <M> the concrete move type for this game
 */
public interface GameState<M extends Move> {

    /** Whose turn it is in this state. */
    PlayerId currentPlayer();

    /** All legal moves from this state, in no particular order. Empty once the game is over. */
    List<M> legalMoves();

    /**
     * Returns a new state with {@code move} applied. Does not modify this state.
     *
     * @throws IllegalArgumentException if the move is not legal in this state
     */
    GameState<M> applyMove(M move);

    /** Empty while the game is still in progress. */
    Optional<GameResult> result();

    default boolean isGameOver() {
        return result().isPresent();
    }
}
