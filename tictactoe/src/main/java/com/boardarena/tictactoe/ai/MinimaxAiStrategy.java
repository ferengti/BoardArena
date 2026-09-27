package com.boardarena.tictactoe.ai;

import com.boardarena.core.GameResult;
import com.boardarena.core.GameState;
import com.boardarena.core.PlayerId;
import com.boardarena.core.ai.AiStrategy;
import com.boardarena.core.ai.Difficulty;
import com.boardarena.tictactoe.TicTacToeMove;

import java.util.List;
import java.util.Random;

/**
 * Perfect-play minimax with alpha-beta pruning. Tic-Tac-Toe's search space is
 * tiny, so this finishes instantly and plays optimally on HARD (perfect play
 * against it always ends in a draw). EASY and MEDIUM deliberately weaken the
 * AI by mixing in random moves rather than searching worse -- the search
 * itself stays correct, and the same class covers every difficulty.
 */
public final class MinimaxAiStrategy implements AiStrategy<TicTacToeMove> {

    private final Random random = new Random();

    @Override
    public TicTacToeMove chooseMove(GameState<TicTacToeMove> state, Difficulty difficulty) {
        List<TicTacToeMove> legalMoves = state.legalMoves();
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("No legal moves available");
        }

        double randomMoveChance = switch (difficulty) {
            case EASY -> 0.75;
            case MEDIUM -> 0.35;
            case HARD -> 0.0;
        };
        if (random.nextDouble() < randomMoveChance) {
            return legalMoves.get(random.nextInt(legalMoves.size()));
        }
        return bestMove(state);
    }

    private TicTacToeMove bestMove(GameState<TicTacToeMove> state) {
        PlayerId aiPlayer = state.currentPlayer();
        TicTacToeMove best = null;
        int bestScore = Integer.MIN_VALUE;

        for (TicTacToeMove move : state.legalMoves()) {
            int score = minimax(state.applyMove(move), aiPlayer, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    private int minimax(GameState<TicTacToeMove> state, PlayerId aiPlayer, int alpha, int beta, boolean maximizing) {
        var result = state.result();
        if (result.isPresent()) {
            return score(result.get(), aiPlayer);
        }

        if (maximizing) {
            int best = Integer.MIN_VALUE;
            for (TicTacToeMove move : state.legalMoves()) {
                best = Math.max(best, minimax(state.applyMove(move), aiPlayer, alpha, beta, false));
                alpha = Math.max(alpha, best);
                if (alpha >= beta) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (TicTacToeMove move : state.legalMoves()) {
                best = Math.min(best, minimax(state.applyMove(move), aiPlayer, alpha, beta, true));
                beta = Math.min(beta, best);
                if (alpha >= beta) break;
            }
            return best;
        }
    }

    private int score(GameResult result, PlayerId aiPlayer) {
        if (result instanceof GameResult.Draw) {
            return 0;
        }
        GameResult.Win win = (GameResult.Win) result;
        return win.winner() == aiPlayer ? 1 : -1;
    }
}
