package com.boardarena.core;

/**
 * Marker interface for a single move in a game. Each game module defines its
 * own concrete type -- e.g. {@code TicTacToeMove(row, col)}, or eventually a
 * {@code ChessMove} carrying from/to squares and special-move flags.
 */
public interface Move {
}
