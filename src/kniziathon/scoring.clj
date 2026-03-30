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

(defn calculate-play-score [rank num-players game-weight]
  (int (* (position-points rank num-players) game-weight)))

(defn player-plays [player-id]
  "Get all plays where this player participated"
  (filter #(some (fn [pr] (= (:player-id pr) player-id))
                 (:player-results %))
          (state/get-all-plays)))

(defn play-score-for-player [play player-id]
  "Calculate the score a player got in a specific play"
  (let [player-result (first (filter #(= (:player-id %) player-id)
                                    (:player-results play)))
        game (state/get-game (:game-id play))
        num-players (count (:player-results play))]
    (when (and player-result game)
      (calculate-play-score (:rank player-result)
                          num-players
                          (:weight game)))))

(defn player-best-scores [player-id]
  "Return map of {game-id best-score} for this player"
  (let [plays (player-plays player-id)]
    (->> plays
         (group-by :game-id)
         (map (fn [[game-id game-plays]]
                [game-id (->> game-plays
                             (map #(play-score-for-player % player-id))
                             (remove nil?)
                             (apply max 0))]))
         (into {}))))

(defn player-total-score [player-id]
  "Sum of best scores across all games"
  (->> (player-best-scores player-id)
       vals
       (reduce + 0)))

(defn player-total-plays [player-id]
  "Total number of plays this player has participated in"
  (count (player-plays player-id)))

(defn player-games-played [player-id]
  "Count of unique games this player has played"
  (count (player-best-scores player-id)))

(defn leaderboard-data []
  "Return sorted list of players with scores"
  (->> (state/get-all-players)
       (map (fn [player]
              {:player-id (:id player)
               :name (:name player)
               :total-score (player-total-score (:id player))
               :games-played (player-games-played (:id player))
               :total-plays (player-total-plays (:id player))}))
       (filter #(pos? (:total-plays %)))
       (sort-by :total-score >)))

(defn auto-rank-by-scores [player-results]
  "Auto-rank players by their game scores (descending)"
  (let [with-scores (filter :game-score player-results)
        without-scores (remove :game-score player-results)
        sorted (sort-by :game-score > with-scores)
        ranked (map-indexed (fn [idx pr] (assoc pr :rank (inc idx))) sorted)]
    (vec (concat ranked without-scores))))

(defn player-game-details [player-id]
  "Return details of all games played by this player with their best scores"
  (let [best-scores (player-best-scores player-id)
        plays (player-plays player-id)]
    (->> best-scores
         (map (fn [[game-id best-score]]
                (let [game (state/get-game game-id)
                      game-plays (filter #(= (:game-id %) game-id) plays)
                      num-plays (count game-plays)
                      best-play (first (sort-by #(play-score-for-player % player-id) > game-plays))
                      player-result (first (filter #(= (:player-id %) player-id)
                                                  (:player-results best-play)))]
                  {:game-id game-id
                   :game-name (:name game)
                   :weight (:weight game)
                   :best-score best-score
                   :rank (:rank player-result)
                   :num-plays num-plays
                   :timestamp (:timestamp best-play)})))
         (sort-by :best-score >))))
