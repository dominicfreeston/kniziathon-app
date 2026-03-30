# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application (starts server at http://localhost:3000)
lein run

# Run tests
lein test

# Run a single test namespace
lein test kniziathon.scoring-test

# Attempt to auto-fix unbalanced parenthesis
parmezan --file path/to/file.clj --write
```

## Testing

Always run `lein test` after making changes. New functionality must be accompanied by tests.

Test namespaces mirror source namespaces:
- `kniziathon.scoring-test` — pure scoring logic
- `kniziathon.state-test` — CRUD and persistence
- `kniziathon.handlers-test` — HTTP endpoints via `ring.mock.request`

Each test namespace uses a `:each` fixture to reset `app-state` between tests.

## Architecture

A server-side rendered Clojure web app for tracking scores at Kniziathon gaming events. Uses Ring + Compojure (routing), Hiccup (HTML templating), and htmx (dynamic updates). No database — state is held in a single in-memory atom and auto-persisted to `data/kniziathon.edn` via `add-watch`.

**Request flow:**

```
HTTP → core.clj (routes) → handlers.clj (validation/logic) → state.clj (atom CRUD)
                                                            → scoring.clj (points calc)
                                                            → views.clj (Hiccup HTML)
```

**Module responsibilities:**

- `core.clj` — route definitions and server entry point
- `state.clj` — atom-based state, CRUD functions, file persistence
- `scoring.clj` — Knizia position-points table, score calculation, leaderboard generation
- `handlers.clj` — form submission handlers, input validation, HTMX fragment endpoints
- `views.clj` — all HTML rendering via Hiccup

**Data shape in `app-state`:**

```clojure
{:games   {id -> {:id :name :weight}}
 :players {id -> {:id :name}}
 :plays   {id -> {:id :game-id :timestamp :player-results [...]}}}
```

**Scoring:** position-points are looked up from a table (2–6 players, ranks 1–6) and multiplied by the game's weight. The leaderboard uses each player's best score per game (ties broken by newest play), summed across all games.

**HTMX endpoints** (`/htmx/*`) serve partial HTML fragments for auto-rank assignment, player reordering in play forms, and leaderboard auto-refresh (every 10 s).
