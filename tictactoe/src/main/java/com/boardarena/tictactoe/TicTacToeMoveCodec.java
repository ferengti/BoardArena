package com.boardarena.tictactoe;

import com.boardarena.core.MoveCodec;

public final class TicTacToeMoveCodec implements MoveCodec<TicTacToeMove> {

    @Override
    public String encode(TicTacToeMove move) {
        return move.row() + "," + move.col();
    }

    @Override
    public TicTacToeMove decode(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Tic-Tac-Toe move: " + value);
        }
        try {
            return new TicTacToeMove(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Tic-Tac-Toe move: " + value, e);
        }
    }
}
