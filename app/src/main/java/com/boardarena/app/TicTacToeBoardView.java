package com.boardarena.app;

import com.boardarena.core.GameEngine;
import com.boardarena.core.GameResult;
import com.boardarena.core.GameState;
import com.boardarena.core.PlayerId;
import com.boardarena.core.ai.Difficulty;
import com.boardarena.tictactoe.TicTacToeGame;
import com.boardarena.tictactoe.TicTacToeMove;
import com.boardarena.tictactoe.TicTacToeState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Renders one Tic-Tac-Toe match and drives it through a {@link GameEngine}.
 * Knows nothing about networking; a networked match would drive the same
 * engine from received moves instead of AI moves.
 */
final class TicTacToeBoardView extends VBox {

    private final GameEngine<TicTacToeMove> engine;
    private final TicTacToeGame game;
    private final Difficulty difficulty;
    private final PlayerId humanPlayer;
    private final Button[][] cells = new Button[3][3];
    private final Label statusLabel = new Label();

    TicTacToeBoardView(GameEngine<TicTacToeMove> engine, TicTacToeGame game,
                        Difficulty difficulty, PlayerId humanPlayer) {
        this.engine = engine;
        this.game = game;
        this.difficulty = difficulty;
        this.humanPlayer = humanPlayer;

        setAlignment(Pos.CENTER);
        setSpacing(16);
        setStyle("-fx-padding: 24px;");
        getChildren().addAll(statusLabel, buildGrid());

        engine.addListener(state -> refresh());
        refresh();
    }

    private GridPane buildGrid() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(4);
        grid.setVgap(4);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Button cell = new Button();
                cell.setPrefSize(100, 100);
                cell.setStyle("-fx-font-size: 28px;");
                int r = row;
                int c = col;
                cell.setOnAction(e -> onCellClicked(r, c));
                cells[row][col] = cell;
                grid.add(cell, col, row);
            }
        }
        return grid;
    }

    private void onCellClicked(int row, int col) {
        GameState<TicTacToeMove> state = engine.currentState();
        if (state.isGameOver() || state.currentPlayer() != humanPlayer) {
            return;
        }
        TicTacToeMove move = new TicTacToeMove(row, col);
        if (!state.legalMoves().contains(move)) {
            return;
        }
        engine.playMove(move);
        maybePlayAiMove();
    }

    private void maybePlayAiMove() {
        GameState<TicTacToeMove> state = engine.currentState();
        if (!state.isGameOver() && state.currentPlayer() != humanPlayer) {
            TicTacToeMove aiMove = game.aiStrategy().chooseMove(state, difficulty);
            engine.playMove(aiMove);
        }
    }

    private void refresh() {
        var state = (TicTacToeState) engine.currentState();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                PlayerId occupant = state.cellAt(row, col);
                cells[row][col].setText(symbolFor(occupant));
                cells[row][col].setDisable(occupant != null || state.isGameOver());
            }
        }
        statusLabel.setText(statusText(state));
    }

    private String symbolFor(PlayerId playerId) {
        if (playerId == null) return "";
        return playerId == humanPlayer ? "X" : "O";
    }

    private String statusText(GameState<TicTacToeMove> state) {
        return state.result().map(result -> switch (result) {
            case GameResult.Win win when win.winner() == humanPlayer -> "You win!";
            case GameResult.Win win -> "AI wins!";
            case GameResult.Draw draw -> "Draw!";
        }).orElseGet(() -> state.currentPlayer() == humanPlayer ? "Your turn" : "AI thinking...");
    }
}
