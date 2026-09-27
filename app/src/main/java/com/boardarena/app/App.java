package com.boardarena.app;

import com.boardarena.core.GameEngine;
import com.boardarena.core.PlayerId;
import com.boardarena.core.ai.Difficulty;
import com.boardarena.tictactoe.TicTacToeGame;
import com.boardarena.tictactoe.TicTacToeMove;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal JavaFX shell for the first playable slice: Tic-Tac-Toe versus AI,
 * offline. Networked/LAN play plugs in later via the `network` module
 * without changing anything below the GameEngine boundary.
 */
public final class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("BoardArena");
        stage.setScene(new Scene(buildMenu(stage), 420, 480));
        stage.show();
    }

    private VBox buildMenu(Stage stage) {
        Label title = new Label("Tic-Tac-Toe");
        title.setStyle("-fx-font-size: 24px;");

        ComboBox<Difficulty> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll(Difficulty.values());
        difficultyBox.setValue(Difficulty.MEDIUM);

        Button playButton = new Button("Play vs AI");
        playButton.setOnAction(e -> startGame(stage, difficultyBox.getValue()));

        VBox root = new VBox(16, title, difficultyBox, playButton);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40px;");
        return root;
    }

    private void startGame(Stage stage, Difficulty difficulty) {
        var game = new TicTacToeGame();
        var engine = new GameEngine<>(game.newInitialState());
        Runnable backToMenu = () -> stage.setScene(new Scene(buildMenu(stage), 420, 480));
        Runnable restartGame = () -> startGame(stage, difficulty);
        var boardView = new TicTacToeBoardView(engine, game, difficulty, PlayerId.PLAYER_ONE, backToMenu, restartGame);
        stage.setScene(new Scene(boardView, 420, 480));
    }

    static void main(String[] args) {
        launch(args);
    }
}
