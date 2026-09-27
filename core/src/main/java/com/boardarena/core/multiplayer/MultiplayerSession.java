package com.boardarena.core.multiplayer;

import com.boardarena.core.GameSession;
import com.boardarena.core.Move;
import com.boardarena.core.PlayerId;

import java.util.function.Consumer;

/**
 * Multiplayer match exposed to the application without exposing any network
 * transport details.
 */
public interface MultiplayerSession<M extends Move> extends GameSession<M> {

    PlayerId localPlayer();

    String roomCode();

    void sendChatMessage(String message);

    void addChatListener(Consumer<String> listener);

    void requestRematch();

    void respondToRematch(boolean accepted);

    void addRematchListener(RematchListener listener);

    interface RematchListener {

        void onRequest();

        void onDeclined();

        void onStarted();
    }
}