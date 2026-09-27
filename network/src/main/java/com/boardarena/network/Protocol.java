package com.boardarena.network;

import com.boardarena.core.PlayerId;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class Protocol {

    static final String VERSION = "1";
    static final String HELLO = "HELLO";
    static final String WELCOME = "WELCOME";
    static final String READY = "READY";
    static final String MOVE = "MOVE";
    static final String MOVE_REQUEST = "MOVE_REQUEST";
    static final String ERROR = "ERROR";
    static final String BYE = "BYE";
    static final String CHAT = "CHAT";
    static final String REMATCH_REQUEST = "REMATCH_REQUEST";
    static final String REMATCH_ACCEPT = "REMATCH_ACCEPT";
    static final String REMATCH_DECLINE = "REMATCH_DECLINE";
    static final String REMATCH_START = "REMATCH_START";

    private Protocol() {
    }

    static String hello(String gameId, String roomCode) {
        return String.join("|", HELLO, VERSION, gameId, roomCode);
    }

    static Hello parseHello(String frame) {
        String[] parts = split(frame, 4);
        if (!HELLO.equals(parts[0])) {
            throw invalid("Expected HELLO");
        }
        if (!VERSION.equals(parts[1])) {
            throw invalid("Unsupported protocol version: " + parts[1]);
        }
        return new Hello(parts[2], parts[3]);
    }

    static String welcome(String gameId, String roomCode) {
        return String.join("|", WELCOME, VERSION, gameId, roomCode, PlayerId.PLAYER_TWO.name());
    }

    static Welcome parseWelcome(String frame) {
        String[] parts = split(frame, 5);
        if (!WELCOME.equals(parts[0])) {
            throw invalid("Expected WELCOME");
        }
        if (!VERSION.equals(parts[1])) {
            throw invalid("Unsupported protocol version: " + parts[1]);
        }
        try {
            return new Welcome(parts[2], parts[3], PlayerId.valueOf(parts[4]));
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid player id");
        }
    }

    static String moveRequest(String encodedMove) {
        return MOVE_REQUEST + "|" + encode(encodedMove);
    }

    static String move(long sequence, String encodedMove) {
        return String.join("|", MOVE, Long.toString(sequence), encode(encodedMove));
    }

    static MoveFrame parseMove(String frame) {
        String[] parts = split(frame, 3);
        if (!MOVE.equals(parts[0])) {
            throw invalid("Expected MOVE");
        }
        try {
            return new MoveFrame(Long.parseLong(parts[1]), decode(parts[2]));
        } catch (NumberFormatException e) {
            throw invalid("Invalid move sequence");
        }
    }

    static String parseMoveRequest(String frame) {
        String[] parts = split(frame, 2);
        if (!MOVE_REQUEST.equals(parts[0])) {
            throw invalid("Expected MOVE_REQUEST");
        }
        return decode(parts[1]);
    }

    static String error(String code, String message) {
        return String.join("|", ERROR, code, encode(message));
    }

    static ErrorFrame parseError(String frame) {
        String[] parts = split(frame, 3);
        if (!ERROR.equals(parts[0])) {
            throw invalid("Expected ERROR");
        }
        return new ErrorFrame(parts[1], decode(parts[2]));
    }

    static String bye() {
        return BYE + "|" + encode("Session closed");
    }

    static String parseBye(String frame) {
        String[] parts = split(frame, 2);
        if (!BYE.equals(parts[0])) {
            throw invalid("Expected BYE");
        }
        return decode(parts[1]);
    }

    static String chat(String message) {
        return CHAT + "|" + encode(message);
    }

    static String parseChat(String frame) {
        String[] parts = split(frame, 2);

        if (!CHAT.equals(parts[0])) {
            throw invalid("Expected CHAT");
        }

        return decode(parts[1]);
    }

    static String rematchRequest() {
        return REMATCH_REQUEST;
    }

    static String rematchAccept() {
        return REMATCH_ACCEPT;
    }

    static String rematchDecline() {
        return REMATCH_DECLINE;
    }

    static String rematchStart(long round) {
        return REMATCH_START + "|" + round;
    }

    static long parseRematchStart(String frame) {
        String[] parts = split(frame, 2);

        if (!REMATCH_START.equals(parts[0])) {
            throw invalid("Expected REMATCH_START");
        }

        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw invalid("Invalid rematch round");
        }
    }

    private static String[] split(String frame, int expectedParts) {
        String[] parts = frame.split("\\|", -1);
        if (parts.length != expectedParts) {
            throw invalid("Malformed frame: " + frame);
        }
        return parts;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid encoded payload");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    record Hello(String gameId, String roomCode) {
    }

    record Welcome(String gameId, String roomCode, PlayerId player) {
    }

    record MoveFrame(long sequence, String encodedMove) {
    }

    record ErrorFrame(String code, String message) {
    }
}
