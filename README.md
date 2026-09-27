# BoardArena

A cross-platform desktop board-game app (Windows, macOS, Linux) built in
Java + JavaFX. Launching with Tic-Tac-Toe (offline vs. AI, LAN multiplayer
next), with Chess planned on the same architecture.

Inspired by [khelo-tic-tac-toe](https://github.com/yash49/khelo-tic-tac-toe)
(LAN play, room PINs, AI difficulty levels) — reimplemented in Java with a
generic core so a second game (Chess) can be added without reworking the
engine, UI shell, or network layer.

## Requirements

- JDK 25 (Eclipse Temurin recommended)
- Gradle 9.x (or just use the included wrapper once generated: `./gradlew`)

## Modules

| Module | Purpose |
|---|---|
| `core` | Game-agnostic contracts: `Move`, `GameState`, `Game`, `AiStrategy`, `GameEngine`. No dependencies on anything else here. |
| `tictactoe` | Tic-Tac-Toe rules + a minimax (alpha-beta) AI with Easy/Medium/Hard difficulty. |
| `chess` | Placeholder — will implement the same `core` contracts. |
| `network` | LAN discovery + room hosting/joining. Interface-only scaffold for now. |
| `app` | JavaFX UI. Currently: a menu and a playable Tic-Tac-Toe-vs-AI board. |

## Running it

```
./gradlew :app:run
```

<img width="419" height="509" alt="image" src="https://github.com/user-attachments/assets/c7a7c7b8-ce0b-43b8-a8dc-f3c7fd7fd8dd" />


## Running tests

```
./gradlew test
```

## Status

- [x] Core engine contracts (`Move`, `GameState`, `Game`, `AiStrategy`, `GameEngine`)
- [x] Tic-Tac-Toe rules + minimax AI (Easy / Medium / Hard)
- [x] Offline Tic-Tac-Toe vs. AI, playable via JavaFX
- [ ] LAN discovery + room PIN join (network module)
- [ ] Rematch / chat (parity with khelo-tic-tac-toe)
- [ ] Chess rules + engine
- [ ] Chess AI (alpha-beta search; "Hard" may delegate to an external UCI engine)

## Design notes

- **Immutable `GameState`**: `applyMove()` returns a new state rather than
  mutating. Makes AI search and future network replay/undo simpler and safer.
- **`PlayerId` is generic (`PLAYER_ONE`/`PLAYER_TWO`)**: the core engine has
  no concept of "X"/"O" or "White"/"Black" — that mapping lives in each
  game's own module and in the UI.
- **`AiStrategy<M>` is the single seam AI plugs into**: Tic-Tac-Toe's
  minimax and Chess's future (much heavier) engine both implement it, so the
  `app` and `network` modules never need to know which game they're driving.
