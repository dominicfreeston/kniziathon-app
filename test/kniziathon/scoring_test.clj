(ns kniziathon.scoring-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [kniziathon.scoring :as scoring]
            [kniziathon.state :as state]))

(defn reset-state [f]
  (reset! state/app-state {:games {} :players {} :plays {}})
  (f)
  (reset! state/app-state {:games {} :players {} :plays {}}))

(use-fixtures :each reset-state)

;; --- position-points ---

(deftest position-points-table-values
  (testing "2-player game"
    (is (= 4 (scoring/position-points 1 2)))
    (is (= 1 (scoring/position-points 2 2))))
  (testing "4-player game"
    (is (= 6 (scoring/position-points 1 4)))
    (is (= 4 (scoring/position-points 2 4)))
    (is (= 2 (scoring/position-points 3 4)))
    (is (= 1 (scoring/position-points 4 4))))
  (testing "6-player game"
    (is (= 8 (scoring/position-points 1 6)))
    (is (= 1 (scoring/position-points 6 6))))
  (testing "invalid inputs return 0"
    (is (= 0 (scoring/position-points 1 7)))
    (is (= 0 (scoring/position-points 7 6)))))

;; --- calculate-play-score ---

(deftest calculate-play-score-test
  (testing "integer weight"
    (is (= 6 (scoring/calculate-play-score 1 4 1)))
    (is (= 4 (scoring/calculate-play-score 2 4 1))))
  (testing "fractional weight is truncated"
    (is (= 9 (scoring/calculate-play-score 1 4 1.5)))
    (is (= 6 (scoring/calculate-play-score 2 4 1.5))))
  (testing "zero weight gives 0"
    (is (= 0 (scoring/calculate-play-score 1 4 0)))))

;; --- auto-rank-by-scores ---

(deftest auto-rank-by-scores-test
  (testing "ranks players by descending game score"
    (let [results [{:player-id "a" :game-score 10}
                   {:player-id "b" :game-score 30}
                   {:player-id "c" :game-score 20}]
          ranked (scoring/auto-rank-by-scores results)]
      (is (= "b" (:player-id (first ranked))))
      (is (= 1 (:rank (first ranked))))
      (is (= "c" (:player-id (second ranked))))
      (is (= 2 (:rank (second ranked))))
      (is (= "a" (:player-id (nth ranked 2))))
      (is (= 3 (:rank (nth ranked 2))))))
  (testing "players without scores are left at the end unranked"
    (let [results [{:player-id "a" :game-score 10}
                   {:player-id "b"}]
          ranked (scoring/auto-rank-by-scores results)]
      (is (= "a" (:player-id (first ranked))))
      (is (= 1 (:rank (first ranked))))
      (is (= "b" (:player-id (second ranked))))
      (is (nil? (:rank (second ranked))))))
  (testing "all players without scores returns original order unranked"
    (let [results [{:player-id "a"} {:player-id "b"}]
          ranked (scoring/auto-rank-by-scores results)]
      (is (= 2 (count ranked)))
      (is (every? #(nil? (:rank %)) ranked)))))

;; --- state-dependent scoring ---

(deftest player-best-scores-test
  (let [g1 {:id "g1" :name "Chess" :weight 2}
        p1 "player-1"
        play1 {:id "play1" :game-id "g1" :timestamp "2024-01-01"
               :player-results [{:player-id p1 :rank 1}
                                 {:player-id "player-2" :rank 2}]}
        play2 {:id "play2" :game-id "g1" :timestamp "2024-01-02"
               :player-results [{:player-id p1 :rank 2}
                                 {:player-id "player-2" :rank 1}]}]
    (state/add-game! g1)
    (state/add-play! play1)
    (state/add-play! play2)
    (testing "best score is maximum across plays for the game"
      ;; rank 1 in 2-player with weight 2: 4*2=8; rank 2: 1*2=2
      (let [best (scoring/player-best-scores p1)]
        (is (= {"g1" 8} best))))
    (testing "player with no plays has empty best scores"
      (is (= {} (scoring/player-best-scores "nobody"))))))

(deftest player-total-score-test
  (let [g1 {:id "g1" :name "Chess" :weight 1}
        g2 {:id "g2" :name "Go" :weight 1}
        p1 "player-1"]
    (state/add-game! g1)
    (state/add-game! g2)
    (state/add-play! {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id p1 :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (state/add-play! {:id "play2" :game-id "g2" :timestamp "2024-01-01"
                      :player-results [{:player-id p1 :rank 2}
                                       {:player-id "p2" :rank 1}]})
    (testing "total score sums best scores across all games"
      ;; g1: rank 1 in 2-player, weight 1 => 4; g2: rank 2 in 2-player, weight 1 => 1
      (is (= 5 (scoring/player-total-score p1))))
    (testing "player with no plays scores 0"
      (is (= 0 (scoring/player-total-score "nobody"))))))

(deftest leaderboard-data-test
  (let [g1 {:id "g1" :name "Chess" :weight 1}
        p1 {:id "p1" :name "Alice"}
        p2 {:id "p2" :name "Bob"}]
    (state/add-game! g1)
    (state/add-player! p1)
    (state/add-player! p2)
    (state/add-play! {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id "p1" :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (state/add-player! {:id "p3" :name "Carol"})
    (testing "leaderboard excludes players with no plays"
      (let [board (scoring/leaderboard-data)]
        (is (= 2 (count board)))
        (is (not (some #(= "p3" (:player-id %)) board)))))
    (testing "leaderboard is sorted by total score descending"
      (let [board (scoring/leaderboard-data)]
        (is (= "p1" (:player-id (first board))))
        (is (= "p2" (:player-id (second board))))))
    (testing "leaderboard entries have expected keys"
      (let [entry (first (scoring/leaderboard-data))]
        (is (contains? entry :player-id))
        (is (contains? entry :name))
        (is (contains? entry :total-score))
        (is (contains? entry :games-played))
        (is (contains? entry :total-plays))))))
