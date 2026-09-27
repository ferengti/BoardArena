package com.boardarena.tictactoe;

import com.boardarena.core.GameResult;
import com.boardarena.core.GameState;
import com.boardarena.core.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Immutable 3x3 Tic-Tac-Toe position. {@code cellAt(row, col)} is null for an
 * empty cell, or the {@link PlayerId} occupying it.
 */
public final class TicTacToeState implements GameState<TicTacToeMove> {

    private static final int SIZE = 3;

    private final PlayerId[][] board;
    private final PlayerId currentPlayer;

    private TicTacToeState(PlayerId[][] board, PlayerId currentPlayer) {
        this.board = board;
        this.currentPlayer = currentPlayer;
    }

    public static TicTacToeState initial() {
        return new TicTacToeState(new PlayerId[SIZE][SIZE], PlayerId.PLAYER_ONE);
    }

    public PlayerId cellAt(int row, int col) {
        return board[row][col];
    }

    public int size() {
        return SIZE;
    }

    @Override
    public PlayerId currentPlayer() {
        return currentPlayer;
    }

    @Override
    public List<TicTacToeMove> legalMoves() {
        if (result().isPresent()) {
            return List.of();
        }
        List<TicTacToeMove> moves = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] == null) {
                    moves.add(new TicTacToeMove(row, col));
                }
            }
        }
        return moves;
    }

    @Override
    public GameState<TicTacToeMove> applyMove(TicTacToeMove move) {
        if (board[move.row()][move.col()] != null) {
            throw new IllegalArgumentException("Cell already occupied: " + move);
        }
        PlayerId[][] next = copyBoard();
        next[move.row()][move.col()] = currentPlayer;
        return new TicTacToeState(next, currentPlayer.opponent());
    }

    @Override
    public Optional<GameResult> result() {
        for (int i = 0; i < SIZE; i++) {
            if (lineFilledBy(board[i][0], board[i][1], board[i][2])) {
                return Optional.of(new GameResult.Win(board[i][0]));
            }
            if (lineFilledBy(board[0][i], board[1][i], board[2][i])) {
                return Optional.of(new GameResult.Win(board[0][i]));
            }
        }
        if (lineFilledBy(board[0][0], board[1][1], board[2][2])) {
            return Optional.of(new GameResult.Win(board[0][0]));
        }
        if (lineFilledBy(board[0][2], board[1][1], board[2][0])) {
            return Optional.of(new GameResult.Win(board[0][2]));
        }

        boolean full = true;
        outer:
        for (PlayerId[] row : board) {
            for (PlayerId cell : row) {
                if (cell == null) {
                    full = false;
                    break outer;
                }
            }
        }
        return full ? Optional.of(new GameResult.Draw()) : Optional.empty();
    }

    private static boolean lineFilledBy(PlayerId a, PlayerId b, PlayerId c) {
        return a != null && a == b && b == c;
    }

    private PlayerId[][] copyBoard() {
        PlayerId[][] copy = new PlayerId[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            copy[i] = board[i].clone();
        }
        return copy;
    }
}
