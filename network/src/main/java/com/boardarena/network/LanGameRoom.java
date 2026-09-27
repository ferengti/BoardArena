package com.boardarena.network;

import com.boardarena.core.multiplayer.GameRoom;

import java.net.InetAddress;

final class LanGameRoom implements GameRoom {

    private final String roomCode;
    private final String gameId;
    private final String displayName;
    private final InetAddress host;
    private final int tcpPort;

    LanGameRoom(String roomCode, String gameId, String displayName, InetAddress host, int tcpPort) {
        this.roomCode = roomCode;
        this.gameId = gameId;
        this.displayName = displayName;
        this.host = host;
        this.tcpPort = tcpPort;
    }

    @Override
    public String roomCode() {
        return roomCode;
    }

    @Override
    public String gameId() {
        return gameId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    InetAddress host() {
        return host;
    }

    int tcpPort() {
        return tcpPort;
    }
}
