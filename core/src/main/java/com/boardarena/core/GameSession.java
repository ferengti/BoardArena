package com.boardarena.core;

/**
 * A playable match session. Both local/AI play and multiplayer sessions can
 * expose the same surface to the application layer.
 *
 * @param <M> the concrete move type for this game
 */
public interface GameSession<M extends Move> extends AutoCloseable {

    GameState<M> currentState();

    void playMove(M move);

    void addListener(Listener<M> listener);

    /** True when the session accepts local moves. Local games are ready immediately. */
    default boolean isReady() {
        return true;
    }

    @Override
    default void close() {
    }

    @FunctionalInterface
    interface Listener<M extends Move> {
        void onStateChanged(GameState<M> newState);
    }
}
