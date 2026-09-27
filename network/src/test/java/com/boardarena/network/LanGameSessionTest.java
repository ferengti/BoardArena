package com.boardarena.network;

import com.boardarena.core.GameResult;
import com.boardarena.core.PlayerId;
import com.boardarena.core.multiplayer.MultiplayerSession;
import com.boardarena.tictactoe.TicTacToeGame;
import com.boardarena.tictactoe.TicTacToeMove;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanGameSessionTest {

    @Test
    void chatAndRematchRoundTripOverTcp() throws Exception {
        TicTacToeGame game = new TicTacToeGame();

        LanGameSession<TicTacToeMove> host =
                LanGameSession.host(game);

        LanGameSession<TicTacToeMove> guest =
                LanGameSession.join(game, host.localRoom());

        try {
            awaitTrue(() -> host.isReady() && guest.isReady());

            CountDownLatch guestChat =
                    new CountDownLatch(1);

            CountDownLatch hostChat =
                    new CountDownLatch(1);

            AtomicReference<String> receivedByGuest =
                    new AtomicReference<>();

            AtomicReference<String> receivedByHost =
                    new AtomicReference<>();

            guest.addChatListener(message -> {
                receivedByGuest.set(message);
                guestChat.countDown();
            });

            host.addChatListener(message -> {
                receivedByHost.set(message);
                hostChat.countDown();
            });

            host.sendChatMessage("hello guest");
            guest.sendChatMessage("hello host");

            assertTrue(
                    guestChat.await(2, TimeUnit.SECONDS)
            );

            assertTrue(
                    hostChat.await(2, TimeUnit.SECONDS)
            );

            assertEquals(
                    "hello guest",
                    receivedByGuest.get()
            );

            assertEquals(
                    "hello host",
                    receivedByHost.get()
            );

            /*
             * Finish a Tic-Tac-Toe game.
             */
            host.playMove(new TicTacToeMove(0, 0));
            awaitTrue(() ->
                    guest.currentState().currentPlayer()
                            == PlayerId.PLAYER_TWO
            );

            guest.playMove(new TicTacToeMove(1, 0));
            awaitTrue(() ->
                    host.currentState().currentPlayer()
                            == PlayerId.PLAYER_ONE
            );

            host.playMove(new TicTacToeMove(0, 1));
            awaitTrue(() ->
                    guest.currentState().currentPlayer()
                            == PlayerId.PLAYER_TWO
            );

            guest.playMove(new TicTacToeMove(1, 1));
            awaitTrue(() ->
                    host.currentState().currentPlayer()
                            == PlayerId.PLAYER_ONE
            );

            host.playMove(new TicTacToeMove(0, 2));

            awaitTrue(() ->
                    host.currentState().isGameOver()
                            && guest.currentState().isGameOver()
            );

            assertEquals(
                    new GameResult.Win(PlayerId.PLAYER_ONE),
                    host.currentState().result().orElseThrow()
            );

            CountDownLatch hostSawRequest =
                    new CountDownLatch(1);

            CountDownLatch hostRematchStarted =
                    new CountDownLatch(1);

            CountDownLatch guestRematchStarted =
                    new CountDownLatch(1);

            host.addRematchListener(
                    new MultiplayerSession.RematchListener() {

                        @Override
                        public void onRequest() {
                            hostSawRequest.countDown();
                            host.respondToRematch(true);
                        }

                        @Override
                        public void onDeclined() {
                        }

                        @Override
                        public void onStarted() {
                            hostRematchStarted.countDown();
                        }
                    }
            );

            guest.addRematchListener(
                    new MultiplayerSession.RematchListener() {

                        @Override
                        public void onRequest() {
                        }

                        @Override
                        public void onDeclined() {
                        }

                        @Override
                        public void onStarted() {
                            guestRematchStarted.countDown();
                        }
                    }
            );

            guest.requestRematch();

            assertTrue(
                    hostSawRequest.await(
                            2,
                            TimeUnit.SECONDS
                    )
            );

            assertTrue(
                    hostRematchStarted.await(
                            2,
                            TimeUnit.SECONDS
                    )
            );

            assertTrue(
                    guestRematchStarted.await(
                            2,
                            TimeUnit.SECONDS
                    )
            );

            assertTrue(
                    guest.currentState().result().isEmpty()
            );

            assertEquals(
                    PlayerId.PLAYER_ONE,
                    guest.currentState().currentPlayer()
            );

            assertEquals(
                    9,
                    guest.currentState().legalMoves().size()
            );

        } finally {
            guest.close();
            host.close();
        }
    }

    private static void awaitTrue(Check check)
            throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + Duration.ofSeconds(2).toNanos();

        while (System.nanoTime() < deadline) {
            if (check.get()) {
                return;
            }

            Thread.sleep(10);
        }

        throw new AssertionError(
                "Condition was not met within 2 seconds"
        );
    }

    @FunctionalInterface
    private interface Check {
        boolean get();
    }
}