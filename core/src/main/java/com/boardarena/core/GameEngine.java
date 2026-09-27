package com.boardarena.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a single match: holds the current state, applies moves, and
 * notifies listeners (the UI today; later, the network layer broadcasting
 * moves to the other player) after every change. Deliberately has no
 * knowledge of AI, networking, or JavaFX -- only of {@link GameState} and
 * {@link Move}.
 *
 * @param <M> the concrete move type for this game
 */
public final class GameEngine<M extends Move> {

    /** Notified after every successful move. */
    public interface Listener<M extends Move> {
        void onStateChanged(GameState<M> newState);
    }

    private GameState<M> state;
    private final List<Listener<M>> listeners = new ArrayList<>();

    public GameEngine(GameState<M> initialState) {
        this.state = initialState;
    }

    public GameState<M> currentState() {
        return state;
    }

    public void addListener(Listener<M> listener) {
        listeners.add(listener);
    }

    public void playMove(M move) {
        if (state.isGameOver()) {
            throw new IllegalStateException("Game is already over");
        }
        if (!state.legalMoves().contains(move)) {
            throw new IllegalArgumentException("Illegal move: " + move);
        }
        state = state.applyMove(move);
        for (Listener<M> listener : listeners) {
            listener.onStateChanged(state);
        }
    }
}
