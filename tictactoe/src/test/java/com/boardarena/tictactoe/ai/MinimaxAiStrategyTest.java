package com.boardarena.tictactoe.ai;

import com.boardarena.core.GameResult;
import com.boardarena.core.GameState;
import com.boardarena.core.ai.Difficulty;
import com.boardarena.tictactoe.TicTacToeMove;
import com.boardarena.tictactoe.TicTacToeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MinimaxAiStrategyTest {

    @Test
    void hardAiNeverLosesToItself() {
        MinimaxAiStrategy ai = new MinimaxAiStrategy();
        GameState<TicTacToeMove> state = TicTacToeState.initial();

        while (state.result().isEmpty()) {
            TicTacToeMove move = ai.chooseMove(state, Difficulty.HARD);
            state = state.applyMove(move);
        }

        assertInstanceOf(GameResult.Draw.class, state.result().orElseThrow(),
                "Perfect play on both sides must end in a draw");
    }
}
