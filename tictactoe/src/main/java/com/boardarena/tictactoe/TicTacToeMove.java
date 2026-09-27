package com.boardarena.tictactoe;

import com.boardarena.core.Move;

/** A move is just the cell being claimed, addressed 0-2 for row and column. */
public record TicTacToeMove(int row, int col) implements Move {

    public TicTacToeMove {
        if (row < 0 || row > 2 || col < 0 || col > 2) {
            throw new IllegalArgumentException("row/col must be in [0, 2], got (" + row + ", " + col + ")");
        }
    }
}
