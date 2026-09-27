package com.boardarena.app;

import com.boardarena.core.GameResult;
import com.boardarena.core.GameSession;
import com.boardarena.core.GameState;
import com.boardarena.core.PlayerId;
import com.boardarena.tictactoe.TicTacToeMove;
import com.boardarena.tictactoe.TicTacToeState;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.boardarena.core.multiplayer.MultiplayerSession;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * Renders a Tic-Tac-Toe match through the game-session abstraction. It knows
 * nothing about AI, TCP, UDP, LAN discovery, or any concrete network class.
 */
final class TicTacToeBoardView extends VBox {

    private final GameSession<TicTacToeMove> session;
    private final PlayerId localPlayer;
    private final String opponentLabel;
    private final String roomLabel;
    private final Runnable afterLocalMove;
    private final Button[][] cells = new Button[3][3];
    private final Label statusLabel = new Label();
    private final MultiplayerSession<TicTacToeMove> multiplayerSession;
    private final Button rematchButton = new Button("Request rematch");
    private final TextArea chatArea = new TextArea();
    private final TextField chatInput = new TextField();

    private boolean rematchPending;
    private String notice;

    TicTacToeBoardView(
            GameSession<TicTacToeMove> session,
            MultiplayerSession<TicTacToeMove> multiplayerSession,
            PlayerId localPlayer,
            String opponentLabel,
            String roomLabel,
            Runnable afterLocalMove,
            String primaryActionText,
            Runnable onPrimaryAction,
            Runnable onBack) {
        this.session = session;
        this.multiplayerSession = multiplayerSession;
        this.localPlayer = localPlayer;
        this.opponentLabel = opponentLabel;
        this.roomLabel = roomLabel;
        this.afterLocalMove = afterLocalMove;

        Button primaryAction = new Button(primaryActionText);
        Button backButton = new Button("Back to menu");
        primaryAction.setOnAction(e -> onPrimaryAction.run());
        backButton.setOnAction(e -> onBack.run());

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(primaryAction, backButton);

        if (multiplayerSession != null) {
            rematchButton.setDisable(true);
            rematchButton.setOnAction(e -> requestRematch());

            buttons.getChildren().add(1, rematchButton);

            configureMultiplayer();
        }

        setAlignment(Pos.CENTER);
        setSpacing(16);
        setStyle("-fx-padding: 24px;");
        getChildren().addAll(statusLabel, buildGrid(), buttons);

        if (multiplayerSession != null) {
            getChildren().add(buildChat());
        }

        session.addListener(state -> Platform.runLater(this::refresh));
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
        GameState<TicTacToeMove> state = session.currentState();
        if (!session.isReady() || state.isGameOver() || state.currentPlayer() != localPlayer) {
            return;
        }

        TicTacToeMove move = new TicTacToeMove(row, col);
        if (!state.legalMoves().contains(move)) {
            return;
        }

        session.playMove(move);
        afterLocalMove.run();
    }

    private void refresh() {
        var state = (TicTacToeState) session.currentState();
        boolean inputEnabled = session.isReady()
                && !state.isGameOver()
                && state.currentPlayer() == localPlayer;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                PlayerId occupant = state.cellAt(row, col);
                cells[row][col].setText(symbolFor(occupant));
                cells[row][col].setDisable(!inputEnabled || occupant != null);
            }
        }
        statusLabel.setText(statusText(state));

        if (multiplayerSession != null) {
            boolean canRequest =
                    session.isReady()
                            && state.isGameOver()
                            && !rematchPending;

            rematchButton.setDisable(!canRequest);
        }

        statusLabel.setText(
                notice != null
                        ? notice
                        : statusText(state)
        );
    }

    private String symbolFor(PlayerId playerId) {
        if (playerId == null) return "";
        return playerId == localPlayer ? "X" : "O";
    }

    private String statusText(GameState<TicTacToeMove> state) {
        String prefix = roomLabel == null || roomLabel.isBlank() ? "" : roomLabel + "  •  ";
        if (!session.isReady()) {
            return prefix + "Waiting for opponent...";
        }
        return prefix + state.result().map(result -> switch (result) {
            case GameResult.Win win when win.winner() == localPlayer -> "You win!";
            case GameResult.Win win -> opponentLabel + " wins!";
            case GameResult.Draw draw -> "Draw!";
        }).orElseGet(() -> state.currentPlayer() == localPlayer ? "Your turn" : opponentLabel + "'s turn");
    }

    private void configureMultiplayer() {
        multiplayerSession.addChatListener(
                message -> Platform.runLater(
                        () -> appendChat(opponentLabel, message)
                )
        );

        multiplayerSession.addRematchListener(
                new MultiplayerSession.RematchListener() {

                    @Override
                    public void onRequest() {
                        Platform.runLater(
                                TicTacToeBoardView.this::handleRematchRequest
                        );
                    }

                    @Override
                    public void onDeclined() {
                        Platform.runLater(() -> {
                            rematchPending = false;
                            notice = opponentLabel + " declined the rematch.";
                            refresh();
                        });
                    }

                    @Override
                    public void onStarted() {
                        Platform.runLater(() -> {
                            rematchPending = false;
                            notice = null;
                            refresh();
                        });
                    }
                }
        );
    }

    private void sendChat() {
        String message = chatInput.getText();

        if (message == null || message.isBlank()) {
            return;
        }

        String normalized = message.trim();

        multiplayerSession.sendChatMessage(normalized);

        appendChat("You", normalized);
        chatInput.clear();
    }

    private void appendChat(String sender, String message) {
        if (!chatArea.getText().isEmpty()) {
            chatArea.appendText("\n");
        }

        chatArea.appendText(sender + ": " + message);
    }

    private void requestRematch() {
        try {
            multiplayerSession.requestRematch();

            rematchPending = true;
            notice = "Waiting for " + opponentLabel + " to accept...";

            refresh();
        } catch (RuntimeException e) {
            showError("Could not request rematch", e);
        }
    }

    private void handleRematchRequest() {
        if (rematchPending) {
            // Both players requested a rematch.
            multiplayerSession.respondToRematch(true);
            return;
        }

        Alert alert =
                new Alert(Alert.AlertType.CONFIRMATION);

        alert.setTitle("BoardArena");
        alert.setHeaderText(
                opponentLabel + " wants a rematch."
        );
        alert.setContentText(
                "Start a new game?"
        );

        var result = alert.showAndWait();

        boolean accepted =
                result.isPresent()
                        && result.get()
                        == javafx.scene.control.ButtonType.OK;

        multiplayerSession.respondToRematch(accepted);

        if (!accepted) {
            notice = "Rematch declined.";
            refresh();
        }
    }

    private VBox buildChat() {
        Label chatLabel = new Label("Chat");

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefRowCount(4);

        chatInput.setPromptText("Message");
        chatInput.setOnAction(e -> sendChat());

        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendChat());

        HBox input = new HBox(8, chatInput, sendButton);
        input.setAlignment(Pos.CENTER);

        return new VBox(6, chatLabel, chatArea, input);
    }

    private void showError(String title, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("BoardArena");
        alert.setHeaderText(title);

        String message = error.getMessage();

        alert.setContentText(
                message == null
                        ? error.toString()
                        : message
        );

        alert.showAndWait();
    }

}
