package com.boardarena.core;

/**
 * The outcome of a finished game. Sealed so the compiler forces every
 * consumer (UI, network layer, AI scoring) to handle every possible case.
 */
public sealed interface GameResult {

    record Win(PlayerId winner) implements GameResult {
    }

    record Draw() implements GameResult {
    }
}
