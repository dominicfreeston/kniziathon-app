# Kniziathon Tracker

A server-side rendered web application to track scores and rankings for a Kniziathon gaming event.

## Features

- **Game Management**: Add, edit, and delete games with weights
- **Player Management**: Manage players participating in the event
- **Play Tracking**: Record game plays with scores and rankings
- **Auto-ranking**: Automatically rank players by game score
- **Live Leaderboard**: Auto-refreshing leaderboard display
- **Data Management**: Export/import data as EDN files
- **Position-based Scoring**: Uses Knizia-style position points system

## Project Structure

```
kniziathon/
├── project.clj                          # Leiningen project configuration
├── src/
│   └── kniziathon/
│       ├── core.clj                     # Main entry point and routes
│       ├── state.clj                    # State management and persistence
│       ├── scoring.clj                  # Scoring logic and calculations
│       ├── views.clj                    # Hiccup HTML views
│       └── handlers.clj                 # HTTP request handlers
├── resources/
│   └── public/
│       └── css/
│           └── pico.min.css            # CSS styling
└── data/
    └── kniziathon.edn                  # Persistent data storage (auto-created)
```

## Prerequisites

- Java 8 or higher
- Leiningen (Clojure build tool)

## Installation

1. Install Leiningen if you haven't already:
   - macOS: `brew install leiningen`
   - Linux: Download from https://leiningen.org/
   - Windows: Download from https://leiningen.org/

2. Create the project structure as shown above

3. Ensure all source files are in place

## Running the Application

From the project root directory:

```bash
lein run
```

The server will start on http://localhost:3000

## Usage

### Admin Interface

Navigate to http://localhost:3000 to access the main interface:

1. **Games** (`/games`): Add games with their weights (hours of play time)
2. **Players** (`/players`): Add players to the event
3. **Plays** (`/plays`): Record game sessions
   - Select game and number of players
   - Enter game scores (optional)
   - Click "Auto-rank by Score" to automatically rank players
   - Manually adjust ranks if needed
   - Submit to save

### Leaderboard Display

Open http://localhost:3000/leaderboard in a separate browser window or tab:

- Displays current rankings
- Auto-refreshes every 10 seconds
- Click "Toggle Fullscreen" to hide navigation (great for projectors)
- Click player names to see detailed game breakdown

### Data Management

Navigate to `/data` to:

- **Export**: Download all data as an EDN file (for backup or sharing)
- **Import**: Upload a previously exported EDN file
  - Choose "Replace" to overwrite all data
  - Choose "Merge" to add new data while keeping existing
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

## Example Workflow

1. **Setup**:
   - Add games: "Modern Art" (weight: 1.5), "Ra" (weight: 1.0)
   - Add players: Alice, Bob, Charlie, Diana

2. **Record a Play**:
   - Go to `/plays/new`
   - Select "Modern Art"
   - Select 4 players
   - Enter scores: Alice: 120, Bob: 95, Charlie: 110, Diana: 88
   - Click "Auto-rank by Score" → Ranks: Alice=1, Charlie=2, Bob=3, Diana=4
   - Submit

3. **View Results**:
   - Leaderboard shows: Alice (9 points), Charlie (6), Bob (3), Diana (1.5)
   - Click Alice's name to see her game breakdown

## Development

### REPL Testing

```clojure
lein repl

; Load the namespace
(require '[kniziathon.state :as state])
(require '[kniziathon.scoring :as scoring])

; Test scoring calculations
(scoring/position-points 1 4)  ; => 6
(scoring/calculate-play-score 1 4 1.5)  ; => 9.0
```

### Adding Sample Data

```clojure
(require '[kniziathon.state :as state])

; Add a game
(state/add-game! {:id "game-1" :name "Modern Art" :weight 1.5})

; Add players
(state/add-player! {:id "player-1" :name "Alice"})
(state/add-player! {:id "player-2" :name "Bob"})

; Check state
@state/app-state
```

## Troubleshooting

### Port Already in Use

If port 3000 is already in use, modify `src/kniziathon/core.clj`:

```clojure
(run-jetty app {:port 3001 :join? true})  ; Change to 3001 or any available port
```

### Data Not Persisting

Ensure the `data/` directory is writable. The application will create it automatically if it doesn't exist.

### htmx Not Working

Ensure you have internet connectivity when first loading pages, as htmx is loaded from CDN. For offline use, download htmx.min.js and serve it locally.

## Technical Details

- **Backend**: Clojure with Ring/Compojure
- **Templating**: Hiccup for server-side rendering
- **Interactivity**: htmx for dynamic updates
- **Storage**: In-memory atom with EDN file persistence
- **Auto-save**: Every state change triggers file write

## Future Enhancements

- Mobile-responsive design improvements
- Game duration tracking
- Statistical analysis and charts
- Undo/redo functionality
- Player photos/avatars
- Multiple simultaneous events
- Authentication system

## License

This is a custom application for Kniziathon events.
