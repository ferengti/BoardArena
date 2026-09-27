package com.boardarena.core.ai;

import com.boardarena.core.GameState;
import com.boardarena.core.Move;

/**
 * Chooses a move for the AI-controlled side. Each game module provides its
 * own strategy -- e.g. minimax for Tic-Tac-Toe. Chess will need something
 * much heavier (alpha-beta with iterative deepening at minimum, possibly
 * delegating to an external UCI engine for HARD), but it plugs into this
 * exact same interface, so nothing above this layer needs to change when
 * that lands.
 *
 * @param <M> the concrete move type for this game
 */
public interface AiStrategy<M extends Move> {

    M chooseMove(GameState<M> state, Difficulty difficulty);
}
