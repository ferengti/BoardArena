package com.boardarena.network;

import com.boardarena.core.PlayerId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolTest {

    @Test
    void roundTripsHello() {
        Protocol.Hello hello = Protocol.parseHello(Protocol.hello("tic-tac-toe", "123456"));

        assertEquals("tic-tac-toe", hello.gameId());
        assertEquals("123456", hello.roomCode());
    }

    @Test
    void roundTripsMoveAndSupportsPayloadWithDelimiters() {
        String encoded = "0,2|future";
        Protocol.MoveFrame move = Protocol.parseMove(Protocol.move(7, encoded));

        assertEquals(7, move.sequence());
        assertEquals(encoded, move.encodedMove());
    }

    @Test
    void roundTripsWelcome() {
        Protocol.Welcome welcome = Protocol.parseWelcome(
                Protocol.welcome("tic-tac-toe", "123456"));

        assertEquals(PlayerId.PLAYER_TWO, welcome.player());
    }

    @Test
    void roundTripsChat() {
        assertEquals(
                "hello | LAN!",
                Protocol.parseChat(
                        Protocol.chat("hello | LAN!")
                )
        );
    }

    @Test
    void roundTripsRematchStart() {
        assertEquals(
                3,
                Protocol.parseRematchStart(
                        Protocol.rematchStart(3)
                )
        );

        assertEquals(
                Protocol.REMATCH_REQUEST,
                Protocol.rematchRequest()
        );

        assertEquals(
                Protocol.REMATCH_ACCEPT,
                Protocol.rematchAccept()
        );

        assertEquals(
                Protocol.REMATCH_DECLINE,
                Protocol.rematchDecline()
        );
    }
}
