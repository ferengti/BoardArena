package com.boardarena.app;

import com.boardarena.core.GameEngine;
import com.boardarena.core.GameSession;
import com.boardarena.core.PlayerId;
import com.boardarena.core.ai.Difficulty;
import com.boardarena.core.multiplayer.GameRoom;
import com.boardarena.core.multiplayer.MultiplayerProvider;
import com.boardarena.core.multiplayer.MultiplayerSession;
import com.boardarena.tictactoe.TicTacToeGame;
import com.boardarena.tictactoe.TicTacToeMove;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/** JavaFX shell. Network implementation details are hidden behind core multiplayer contracts. */
public final class App extends Application {

    private MultiplayerProvider multiplayerProvider;

    @Override
    public void start(Stage stage) {
        multiplayerProvider = ServiceLoader.load(MultiplayerProvider.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No multiplayer provider found"));

        stage.setTitle("BoardArena");
        stage.setScene(new Scene(buildMenu(stage), 420, 480));
        stage.show();
    }

    private VBox buildMenu(Stage stage) {
        Label title = new Label("BoardArena");
        title.setStyle("-fx-font-size: 24px;");

        ComboBox<Difficulty> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll(Difficulty.values());
        difficultyBox.setValue(Difficulty.MEDIUM);

        Button playAiButton = new Button("Play vs AI");
        playAiButton.setOnAction(e -> startAiGame(stage, difficultyBox.getValue()));

        Button multiplayerButton = new Button("Play with a friend");
        multiplayerButton.setOnAction(e -> showMultiplayerMenu(stage));

        VBox root = new VBox(16, title, difficultyBox, playAiButton, multiplayerButton);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40px;");
        return root;
    }

    private void startAiGame(Stage stage, Difficulty difficulty) {
        TicTacToeGame game = new TicTacToeGame();
        GameEngine<TicTacToeMove> engine = new GameEngine<>(game.newInitialState());
        Runnable backToMenu = () -> stage.setScene(new Scene(buildMenu(stage), 420, 480));
        Runnable restartGame = () -> startAiGame(stage, difficulty);

        var boardView = new TicTacToeBoardView(
                engine,
                null,
                PlayerId.PLAYER_ONE,
                "AI",
                null,
                () -> maybePlayAiMove(engine, game, difficulty),
                "Restart",
                restartGame,
                backToMenu);
        stage.setScene(new Scene(boardView, 420, 480));
    }

    private void maybePlayAiMove(GameSession<TicTacToeMove> session, TicTacToeGame game, Difficulty difficulty) {
        var state = session.currentState();
        if (!state.isGameOver() && state.currentPlayer() != PlayerId.PLAYER_ONE) {
            session.playMove(game.aiStrategy().chooseMove(state, difficulty));
        }
    }

    private void showMultiplayerMenu(Stage stage) {
        TicTacToeGame game = new TicTacToeGame();

        Label title = new Label("Play with a friend");
        title.setStyle("-fx-font-size: 22px;");
        Label hint = new Label("Create a room or join a PIN found on your LAN.");
        hint.setWrapText(true);

        Button hostButton = new Button("Host game");
        hostButton.setOnAction(e -> hostGame(stage, game));

        TextField pinField = new TextField();
        pinField.setPromptText("6-digit PIN");
        pinField.setMaxWidth(160);

        Button joinButton = new Button("Join by PIN");
        joinButton.setOnAction(e -> joinByPin(stage, game, pinField.getText()));

        Button discoverButton = new Button("Discover rooms");
        ListView<GameRoom> roomList = new ListView<>();
        roomList.setPrefHeight(160);
        roomList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(GameRoom room, boolean empty) {
                super.updateItem(room, empty);
                setText(empty || room == null ? null : room.roomCode() + "  —  " + room.displayName());
            }
        });
        discoverButton.setOnAction(e -> discoverRooms(roomList, game));

        Button joinSelected = new Button("Join selected");
        joinSelected.setOnAction(e -> {
            GameRoom selected = roomList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                joinRoom(stage, game, selected);
            }
        });

        Button backButton = new Button("Back");
        backButton.setOnAction(e -> stage.setScene(new Scene(buildMenu(stage), 420, 480)));

        HBox joinRow = new HBox(8, pinField, joinButton);
        joinRow.setAlignment(Pos.CENTER);
        HBox listActions = new HBox(8, discoverButton, joinSelected);
        listActions.setAlignment(Pos.CENTER);

        VBox root = new VBox(14, title, hint, hostButton, joinRow, listActions, roomList, backButton);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 28px;");
        stage.setScene(new Scene(root, 460, 520));
    }

    private void hostGame(Stage stage, TicTacToeGame game) {
        try {
            MultiplayerSession<TicTacToeMove> session = multiplayerProvider.host(game);
            openMultiplayerGame(stage, session);
        } catch (Exception e) {
            showError("Could not host game", e);
        }
    }

    private void discoverRooms(ListView<GameRoom> roomList, TicTacToeGame game) {
        roomList.getItems().clear();
        CompletableFuture.supplyAsync(() -> {
            try {
                return multiplayerProvider.discover(game, Duration.ofMillis(1_200));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((rooms, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError("LAN discovery failed", unwrap(error));
                return;
            }
            roomList.getItems().setAll(rooms);
        }));
    }

    private void joinByPin(Stage stage, TicTacToeGame game, String rawPin) {
        String pin = rawPin == null ? "" : rawPin.trim();
        if (!pin.matches("\\d{6}")) {
            showError("Invalid PIN", new IllegalArgumentException("Enter a 6-digit room PIN"));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
                    try {
                        List<GameRoom> rooms = multiplayerProvider.discover(game, Duration.ofMillis(1_500));
                        return rooms.stream()
                                .filter(room -> room.roomCode().equals(pin))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Room " + pin + " was not found on this LAN"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).thenAccept(room -> Platform.runLater(() -> joinRoom(stage, game, room)))
                .exceptionally(error -> {
                    Platform.runLater(() -> showError("Could not find room", unwrap(error)));
                    return null;
                });
    }

    private void joinRoom(Stage stage, TicTacToeGame game, GameRoom room) {
        CompletableFuture.supplyAsync(() -> {
                    try {
                        return multiplayerProvider.join(game, room);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).thenAccept(session -> Platform.runLater(() -> openMultiplayerGame(stage, session)))
                .exceptionally(error -> {
                    Platform.runLater(() -> showError("Could not join room", unwrap(error)));
                    return null;
                });
    }

    private void openMultiplayerGame(Stage stage, MultiplayerSession<TicTacToeMove> session) {
        Runnable leave = () -> {
            session.close();
            showMultiplayerMenu(stage);
        };

        var boardView = new TicTacToeBoardView(
                session,
                session,
                session.localPlayer(),
                "Opponent",
                "Room PIN: " + session.roomCode(),
                () -> { },
                "Leave game",
                leave,
                leave);
        stage.setScene(
                new Scene(boardView, 460, 720)
        );
    }

    private void showError(String title, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("BoardArena");
        alert.setHeaderText(title);
        alert.setContentText(error.getMessage() == null ? error.toString() : error.getMessage());
        alert.showAndWait();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current instanceof RuntimeException) {
            current = current.getCause();
        }
        return current;
    }

    static void main(String[] args) {
        launch(args);
    }
}
