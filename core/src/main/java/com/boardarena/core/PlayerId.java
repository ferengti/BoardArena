package com.boardarena.core;

/**
 * Identifies which side is acting. Every game this engine currently targets
 * (Tic-Tac-Toe, and eventually Chess) is a two-player, turn-based game, so a
 * simple two-value id is enough. Individual games map a PlayerId to their
 * own presentation (e.g. "X"/"O" or "White"/"Black") -- the core has no
 * opinion on that.
 */
public enum PlayerId {
    PLAYER_ONE,
    PLAYER_TWO;

    public PlayerId opponent() {
        return this == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
    }
}
