# BoardArena

A cross-platform desktop board-game app (Windows, macOS, Linux) built in
Java + JavaFX. Tic-Tac-Toe is playable offline vs. AI and over LAN
multiplayer, with Chess planned on the same architecture.

Inspired by [khelo-tic-tac-toe](https://github.com/yash49/khelo-tic-tac-toe)
(LAN play, room PINs, AI difficulty levels) — reimplemented in Java with a
generic core so a second game (Chess) can be added without reworking the
engine, UI shell, or network layer.

## Screenshots

<!-- TODO: replace with real screenshots once captured -->
| Main menu | LAN lobby | In-game |
|---|---|---|
| <img width="420" height="510" alt="image" src="https://github.com/user-attachments/assets/eae15d6b-96b9-49bf-a839-42ed9f399104" /> | <img width="460" height="551" alt="image" src="https://github.com/user-attachments/assets/1a98ad89-e693-4aca-8394-83dbec8e5498" /> | <img width="462" height="748" alt="Screenshot 2026-09-27 at 18 53 38" src="https://github.com/user-attachments/assets/802898ab-ea34-4a63-aee2-581bba4b15f5" /> |

## Requirements

- JDK 25 (Eclipse Temurin recommended)
- Gradle 9.x (or just use the included wrapper: `./gradlew`)

## Modules

| Module | Purpose |
|---|---|
| `core` | Game-agnostic contracts: `Move`, `GameState`, `Game`, `GameSession`, `AiStrategy`, plus the `multiplayer` package (`MultiplayerProvider`, `GameRoom`, `MultiplayerSession`) that keeps the app decoupled from any concrete network implementation. |
| `tictactoe` | Tic-Tac-Toe rules, move codec, and a minimax (alpha-beta) AI with Easy/Medium/Hard difficulty. |
| `chess` | Placeholder — will implement the same `core` contracts. |
| `network` | LAN multiplayer: UDP discovery/advertising, a small versioned line protocol over TCP, and a server-authoritative game session with room-code (PIN) join. |
| `app` | JavaFX UI: menu, AI match, and a "Play with a friend" flow (host, join by PIN, or discover rooms on the LAN). |

## Running it

```
./gradlew :app:run
```

## Running tests

```
./gradlew test
```

## Status

- [x] Core engine contracts (`Move`, `GameState`, `Game`, `GameSession`, `AiStrategy`)
- [x] Tic-Tac-Toe rules + minimax AI (Easy / Medium / Hard)
- [x] Offline Tic-Tac-Toe vs. AI, playable via JavaFX
- [x] LAN discovery + room-code (PIN) join, host or join a match over Wi-Fi/LAN
- [ ] Rematch / chat in multiplayer (parity with khelo-tic-tac-toe)
- [ ] Chess rules + engine
- [ ] Chess AI (alpha-beta search; "Hard" may delegate to an external UCI engine)

## Design notes

- **Immutable `GameState`**: `applyMove()` returns a new state rather than
  mutating. Makes AI search and multiplayer replay/undo simpler and safer.
- **`PlayerId` is generic (`PLAYER_ONE`/`PLAYER_TWO`)**: the core engine has
  no concept of "X"/"O" or "White"/"Black" — that mapping lives in each
  game's own module and in the UI.
- **`AiStrategy<M>` is the single seam AI plugs into**: Tic-Tac-Toe's
  minimax and Chess's future (much heavier) engine both implement it, so the
  `app` and `network` modules never need to know which game they're driving.
- **`GameSession<M>` unifies local and networked play**: `GameEngine` (local/AI)
  and `LanGameSession` (multiplayer) both implement it, so `TicTacToeBoardView`
  drives either one identically — it has no idea whether it's talking to an
  AI or a TCP socket.
- **Multiplayer is server-authoritative**: the host is the only side that
  ever mutates its own game state directly. A joining client sends a *move
  request*; the host validates it, applies it, and re-broadcasts it with a
  sequence number, which the client then applies. This avoids client/host
  desync without needing rollback logic.
- **LAN discovery has no central server**: the host periodically broadcasts
  a small UDP packet (game id, room code, port); a joining client either
  picks a discovered room from a list or types the room's 6-digit PIN
  directly, mirroring khelo-tic-tac-toe's room-PIN flow.
