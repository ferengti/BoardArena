package com.boardarena.tictactoe;

import com.boardarena.core.Game;
import com.boardarena.core.GameState;
import com.boardarena.core.ai.AiStrategy;
import com.boardarena.tictactoe.ai.MinimaxAiStrategy;

public final class TicTacToeGame implements Game<TicTacToeMove> {

    private final AiStrategy<TicTacToeMove> aiStrategy = new MinimaxAiStrategy();

    @Override
    public String id() {
        return "tic-tac-toe";
    }

    @Override
    public String displayName() {
        return "Tic-Tac-Toe";
    }

    @Override
    public GameState<TicTacToeMove> newInitialState() {
        return TicTacToeState.initial();
    }

    @Override
    public AiStrategy<TicTacToeMove> aiStrategy() {
        return aiStrategy;
    }

    @Override
    public TicTacToeMoveCodec moveCodec() {
        return CODEC;
    }

    private static final TicTacToeMoveCodec CODEC = new TicTacToeMoveCodec();
}
