package com.boardarena.tictactoe;

import com.boardarena.core.GameResult;
import com.boardarena.core.PlayerId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicTacToeStateTest {

    @Test
    void detectsRowWin() {
        var state = TicTacToeState.initial()
                .applyMove(new TicTacToeMove(0, 0)) // P1
                .applyMove(new TicTacToeMove(1, 0)) // P2
                .applyMove(new TicTacToeMove(0, 1)) // P1
                .applyMove(new TicTacToeMove(1, 1)) // P2
                .applyMove(new TicTacToeMove(0, 2)); // P1 completes the top row

        assertEquals(new GameResult.Win(PlayerId.PLAYER_ONE), state.result().orElseThrow());
    }

    @Test
    void rejectsMoveAfterGameOver() {
        var state = TicTacToeState.initial()
                .applyMove(new TicTacToeMove(0, 0))
                .applyMove(new TicTacToeMove(1, 0))
                .applyMove(new TicTacToeMove(0, 1))
                .applyMove(new TicTacToeMove(1, 1))
                .applyMove(new TicTacToeMove(0, 2));

        assertThrows(IllegalArgumentException.class, () -> state.applyMove(new TicTacToeMove(2, 2)));
    }

    @Test
    void startsWithNineLegalMoves() {
        assertEquals(9, TicTacToeState.initial().legalMoves().size());
    }
}
