# Kniziathon Tracker

A server-side rendered web application to track scores and rankings for a Kniziathon gaming event.

## Prerequisites

- [Clojure](https://clojure.org/guides/install_clojure)

## Running the Application

From the project root directory:

```bash
clojure -M:run
```

The server will start on http://localhost:3000

## Usage

### Admin Interface

Navigate to http://localhost:3000 to access the main interface:

1. **Games** (`/games`): Add games with their weights (hours of play time)
2. **Players** (`/players`): Add players to the event
3. **Plays** (`/plays`): Record game sessions

### Leaderboard Display

Open http://localhost:3000/leaderboard in a separate browser window or tab:

- Displays current rankings
- Auto-refreshes every 10 seconds
- Click "Toggle Fullscreen" to hide navigation
- Click player names to see detailed game breakdown

### Data Management

Navigate to `/data` to:

- **Export**: Download all data as an EDN file (for backup or sharing)
- **Import**: Upload a previously exported EDN file
- **Clear**: Delete all data (with confirmation)

## Scoring System

### Position Points Table

Points awarded based on rank and number of players:

| Players | 1st | 2nd | 3rd | 4th | 5th | 6th |
|---------|-----|-----|-----|-----|-----|-----|
| 6       | 8   | 6   | 4   | 3   | 2   | 1   |
| 5       | 7   | 5   | 3   | 2   | 1   | -   |
| 4       | 6   | 4   | 2   | 1   | -   | -   |
| 3       | 5   | 3   | 1   | -   | -   | -   |
| 2       | 4   | 1   | -   | -   | -   | -   |

### Calculation

1. Position points × game weight = play score
2. For each game, only the player's best score counts
3. Total score = sum of best scores across all games played

## Troubleshooting

### Port Already in Use

If port 3000 is already in use, modify `src/kniziathon/core.clj`:

```clojure
(run-jetty app {:port 3001 :join? true})  ; Change to 3001 or any available port
```

### Data Not Persisting

Ensure the `data/` directory is writable. The application will create it automatically if it doesn't exist.
