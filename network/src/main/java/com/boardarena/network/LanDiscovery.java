package com.boardarena.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class LanDiscovery {

    static final int DISCOVERY_PORT = 38_471;
    private static final String PREFIX = "BOARD_ARENA_DISCOVERY";
    private static final String VERSION = "1";

    private LanDiscovery() {
    }

    static Advertiser advertise(LanGameRoom room) throws SocketException {
        return new Advertiser(room);
    }

    static Collection<LanGameRoom> discover(String gameId, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, LanGameRoom> rooms = new LinkedHashMap<>();

        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
            socket.setBroadcast(true);
            socket.setSoTimeout(200);
            byte[] buffer = new byte[2048];

            while (System.nanoTime() < deadline) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }

                String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                LanGameRoom room = parse(text, packet.getAddress(), gameId);
                if (room != null) {
                    rooms.put(room.host() + ":" + room.tcpPort(), room);
                }
            }
        }
        return rooms.values();
    }

    private static LanGameRoom parse(String packet, InetAddress address, String expectedGameId) {
        String[] parts = packet.split("\\|", -1);
        if (parts.length != 6 || !PREFIX.equals(parts[0]) || !VERSION.equals(parts[1])) {
            return null;
        }
        if (!expectedGameId.equals(parts[2])) {
            return null;
        }
        try {
            int port = Integer.parseInt(parts[4]);
            String displayName = new String(java.util.Base64.getUrlDecoder().decode(parts[5]), StandardCharsets.UTF_8);
            return new LanGameRoom(parts[3], parts[2], displayName, address, port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static final class Advertiser implements AutoCloseable {
        private final LanGameRoom room;
        private final DatagramSocket socket;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "boardarena-lan-discovery");
            t.setDaemon(true);
            return t;
        });

        private Advertiser(LanGameRoom room) throws SocketException {
            this.room = room;
            this.socket = new DatagramSocket();
            this.socket.setBroadcast(true);
            scheduler.scheduleAtFixedRate(this::broadcast, 0, 700, TimeUnit.MILLISECONDS);
        }

        private void broadcast() {
            if (socket.isClosed()) {
                return;
            }
            byte[] payload = advertisement(room).getBytes(StandardCharsets.UTF_8);
            for (InetAddress broadcast : broadcastAddresses()) {
                try {
                    socket.send(new DatagramPacket(payload, payload.length, broadcast, DISCOVERY_PORT));
                } catch (IOException ignored) {
                    // A single unavailable interface must not stop discovery on the others.
                }
            }
        }

        @Override
        public void close() {
            scheduler.shutdownNow();
            socket.close();
        }
    }

    private static String advertisement(LanGameRoom room) {
        String displayName = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(room.displayName().getBytes(StandardCharsets.UTF_8));
        return String.join("|", PREFIX, VERSION, room.gameId(), room.roomCode(), Integer.toString(room.tcpPort()), displayName);
    }

    private static Collection<InetAddress> broadcastAddresses() {
        Map<String, InetAddress> addresses = new LinkedHashMap<>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                var bindings = networkInterface.getInterfaceAddresses();
                for (var binding : bindings) {
                    InetAddress broadcast = binding.getBroadcast();
                    if (broadcast instanceof Inet4Address) {
                        addresses.put(broadcast.getHostAddress(), broadcast);
                    }
                }
            }
        } catch (SocketException ignored) {
        }

        if (addresses.isEmpty()) {
            try {
                InetAddress globalBroadcast = InetAddress.getByName("255.255.255.255");
                addresses.put(globalBroadcast.getHostAddress(), globalBroadcast);
            } catch (IOException ignored) {
            }
        }
        return addresses.values();
    }
}
