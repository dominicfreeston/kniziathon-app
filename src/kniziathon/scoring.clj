(ns kniziathon.scoring
  (:require [kniziathon.state :as state]))

(def position-points-table
  {2 {1 4, 2 1}
   3 {1 5, 2 3, 3 1}
   4 {1 6, 2 4, 3 2, 4 1}
   5 {1 7, 2 5, 3 3, 4 2, 5 1}
   6 {1 8, 2 6, 3 4, 4 3, 5 2, 6 1}})

(defn position-points [rank num-players]
  (get-in position-points-table [num-players rank] 0))

(defn calculate-tied-play-score
  "Calculate score for a player in a tie group.
   tie-count: how many players share this rank.
   tie-mode: :full (default), :average, or :lower."
  [rank num-players game-weight tie-count tie-mode]
  (if (= tie-count 1)
    (int (* (position-points rank num-players) game-weight))
    (case tie-mode
      :average (int (Math/ceil (/ (* game-weight
                                     (reduce + (map #(position-points % num-players)
                                                    (range rank (+ rank tie-count)))))
                                  tie-count)))
      :lower   (int (* (position-points (+ rank tie-count -1) num-players) game-weight))
      ;; :full or default
      (int (* (position-points rank num-players) game-weight)))))

(defn player-plays [app-state player-id]
  "Get all plays where this player participated"
  (filter #(some (fn [pr] (= (:player-id pr) player-id))
                 (:player-results %))
          (state/get-all-plays app-state)))

(defn play-score-for-player [app-state play player-id]
  "Calculate the score a player got in a specific play"
  (let [player-result (first (filter #(= (:player-id %) player-id)
                                    (:player-results play)))
        game (state/get-game app-state (:game-id play))
        num-players (count (:player-results play))
        rank (:rank player-result)
        tie-count (count (filter #(= (:rank %) rank) (:player-results play)))
        tie-mode (or (state/get-setting app-state :tie-scoring-mode) :full)]
    (when (and player-result game)
      (calculate-tied-play-score rank num-players (:weight game) tie-count tie-mode))))

(defn player-best-scores [app-state player-id]
  "Return map of {game-id best-score} for this player"
  (let [plays (player-plays app-state player-id)]
    (->> plays
         (group-by :game-id)
         (map (fn [[game-id game-plays]]
                [game-id (->> game-plays
                             (map #(play-score-for-player app-state % player-id))
                             (remove nil?)
                             (apply max 0))]))
         (into {}))))

(defn player-total-score [app-state player-id]
  "Sum of best scores per game (standard) or all play scores (multi-play mode)"
  (if (state/get-setting app-state :multi-play-scoring)
    (->> (player-plays app-state player-id)
         (map #(play-score-for-player app-state % player-id))
         (remove nil?)
         (reduce + 0))
    (->> (player-best-scores app-state player-id)
         vals
         (reduce + 0))))

(defn player-total-plays [app-state player-id]
  "Total number of plays this player has participated in"
  (count (player-plays app-state player-id)))

(defn player-games-played [app-state player-id]
  "Count of unique games this player has played"
  (count (player-best-scores app-state player-id)))

(defn leaderboard-data [app-state]
  "Return sorted list of players with scores and competition ranks (ties share a rank)"
  (let [sorted (->> (state/get-all-players app-state)
                    (map (fn [player]
                           {:player-id (:id player)
                            :name (:name player)
                            :total-score (player-total-score app-state (:id player))
                            :games-played (player-games-played app-state (:id player))
                            :total-plays (player-total-plays app-state (:id player))}))
                    (filter #(pos? (:total-plays %)))
                    (sort-by :total-score >))]
    (first
      (reduce (fn [[result prev-score prev-rank pos] entry]
                (let [rank (if (= (:total-score entry) prev-score)
                             prev-rank
                             pos)]
                  [(conj result (assoc entry :rank rank))
                   (:total-score entry)
                   rank
                   (inc pos)]))
              [[] nil 1 1]
              sorted))))

(defn auto-rank-by-scores [player-results]
  "Auto-rank players by their game scores (descending), with competition ranking for ties"
  (let [with-scores (filter :game-score player-results)
        without-scores (remove :game-score player-results)
        sorted (sort-by :game-score > with-scores)
        ranked (first
                 (reduce (fn [[result prev-score prev-rank pos] pr]
                           (let [rank (if (= (:game-score pr) prev-score) prev-rank pos)]
                             [(conj result (assoc pr :rank rank))
                              (:game-score pr) rank (inc pos)]))
                         [[] nil 1 1]
                         sorted))]
    (vec (concat ranked without-scores))))

(defn player-game-details [app-state player-id]
  "Return details of all games played by this player with their best scores"
  (let [best-scores (player-best-scores app-state player-id)
        plays (player-plays app-state player-id)]
    (->> best-scores
         (map (fn [[game-id best-score]]
                (let [game (state/get-game app-state game-id)
                      game-plays (filter #(= (:game-id %) game-id) plays)
                      num-plays (count game-plays)
                      best-play (first (sort-by #(play-score-for-player app-state % player-id) > game-plays))
                      player-result (first (filter #(= (:player-id %) player-id)
                                                  (:player-results best-play)))]
                  {:game-id game-id
                   :game-name (:name game)
                   :weight (:weight game)
                   :best-score best-score
                   :total-score (->> game-plays
                                     (map #(play-score-for-player app-state % player-id))
                                     (remove nil?)
                                     (reduce + 0))
                   :rank (:rank player-result)
                   :num-plays num-plays
                   :timestamp (:timestamp best-play)
                   :plays (vec (reverse (sort-by :timestamp game-plays)))})))
         (sort-by :best-score >))))
