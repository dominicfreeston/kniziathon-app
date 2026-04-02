(ns kniziathon.scoring-test
  (:require [clojure.test :refer [deftest is testing]]
            [kniziathon.scoring :as scoring]
            [kniziathon.state :as state]))

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
      (is (every? #(nil? (:rank %)) ranked))))
  (testing "tied scores get the same rank with next rank skipping"
    (let [results [{:player-id "a" :game-score 20}
                   {:player-id "b" :game-score 20}
                   {:player-id "c" :game-score 10}]
          ranked (scoring/auto-rank-by-scores results)]
      (is (= 1 (:rank (first ranked))))
      (is (= 1 (:rank (second ranked))))
      (is (= 3 (:rank (nth ranked 2))))))
  (testing "three-way tie all get rank 1"
    (let [results [{:player-id "a" :game-score 15}
                   {:player-id "b" :game-score 15}
                   {:player-id "c" :game-score 15}]
          ranked (scoring/auto-rank-by-scores results)]
      (is (every? #(= 1 (:rank %)) ranked)))))

;; --- state-dependent scoring ---

(deftest player-best-scores-test
  (let [s (state/create-state)
        g1 {:id "g1" :name "Chess" :weight 2}
        p1 "player-1"
        play1 {:id "play1" :game-id "g1" :timestamp "2024-01-01"
               :player-results [{:player-id p1 :rank 1}
                                 {:player-id "player-2" :rank 2}]}
        play2 {:id "play2" :game-id "g1" :timestamp "2024-01-02"
               :player-results [{:player-id p1 :rank 2}
                                 {:player-id "player-2" :rank 1}]}]
    (state/add-game! s g1)
    (state/add-play! s play1)
    (state/add-play! s play2)
    (testing "best score is maximum across plays for the game"
      ;; rank 1 in 2-player with weight 2: 4*2=8; rank 2: 1*2=2
      (let [best (scoring/player-best-scores s p1)]
        (is (= {"g1" 8} best))))
    (testing "player with no plays has empty best scores"
      (is (= {} (scoring/player-best-scores s "nobody"))))))

(deftest player-total-score-test
  (let [s (state/create-state)
        g1 {:id "g1" :name "Chess" :weight 1}
        g2 {:id "g2" :name "Go" :weight 1}
        p1 "player-1"]
    (state/add-game! s g1)
    (state/add-game! s g2)
    (state/add-play! s {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id p1 :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (state/add-play! s {:id "play2" :game-id "g2" :timestamp "2024-01-01"
                      :player-results [{:player-id p1 :rank 2}
                                       {:player-id "p2" :rank 1}]})
    (testing "total score sums best scores across all games"
      ;; g1: rank 1 in 2-player, weight 1 => 4; g2: rank 2 in 2-player, weight 1 => 1
      (is (= 5 (scoring/player-total-score s p1))))
    (testing "player with no plays scores 0"
      (is (= 0 (scoring/player-total-score s "nobody"))))
    (testing "multi-play mode sums all play scores instead of best per game"
      ;; Add a second play for g1 where p1 gets rank 2 (score 1)
      ;; Standard: best per game => g1:4 + g2:1 = 5
      ;; Multi-play: all plays => g1 rank1:4 + g1 rank2:1 + g2 rank2:1 = 6
      (state/add-play! s {:id "play3" :game-id "g1" :timestamp "2024-01-02"
                        :player-results [{:player-id p1 :rank 2}
                                         {:player-id "p2" :rank 1}]})
      (is (= 5 (scoring/player-total-score s p1)) "standard mode unchanged")
      (state/toggle-setting! s :multi-play-scoring)
      (is (= 6 (scoring/player-total-score s p1)) "multi-play mode sums all plays")
      (state/toggle-setting! s :multi-play-scoring))))

(deftest leaderboard-data-test
  (let [s (state/create-state)
        g1 {:id "g1" :name "Chess" :weight 1}
        p1 {:id "p1" :name "Alice"}
        p2 {:id "p2" :name "Bob"}]
    (state/add-game! s g1)
    (state/add-player! s p1)
    (state/add-player! s p2)
    (state/add-play! s {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id "p1" :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (state/add-player! s {:id "p3" :name "Carol"})
    (testing "leaderboard excludes players with no plays"
      (let [board (scoring/leaderboard-data s)]
        (is (= 2 (count board)))
        (is (not (some #(= "p3" (:player-id %)) board)))))
    (testing "leaderboard is sorted by total score descending"
      (let [board (scoring/leaderboard-data s)]
        (is (= "p1" (:player-id (first board))))
        (is (= "p2" (:player-id (second board))))))
    (testing "leaderboard entries have expected keys"
      (let [entry (first (scoring/leaderboard-data s))]
        (is (contains? entry :player-id))
        (is (contains? entry :name))
        (is (contains? entry :total-score))
        (is (contains? entry :games-played))
        (is (contains? entry :total-plays))
        (is (contains? entry :rank))))))

(deftest tied-rank-scoring-test
  (testing "tied players both get full points for their shared rank"
    ;; Two players tied at rank 1 in a 3-player game: both get 5 pts (rank 1 in 3-player)
    (let [s (state/create-state)
          g1 {:id "g1" :name "Chess" :weight 1}]
      (state/add-game! s g1)
      (state/add-player! s {:id "p1" :name "Alice"})
      (state/add-player! s {:id "p2" :name "Bob"})
      (state/add-player! s {:id "p3" :name "Carol"})
      (state/add-play! s {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                          :player-results [{:player-id "p1" :rank 1}
                                           {:player-id "p2" :rank 1}
                                           {:player-id "p3" :rank 3}]})
      (is (= 5 (scoring/play-score-for-player s (state/get-play s "play1") "p1"))
          "rank 1 in 3-player = 5 pts")
      (is (= 5 (scoring/play-score-for-player s (state/get-play s "play1") "p2"))
          "tied rank 1 also gets 5 pts")
      (is (= 1 (scoring/play-score-for-player s (state/get-play s "play1") "p3"))
          "rank 3 in 3-player = 1 pt")))

  (testing "tied players with game weight multiplier"
    (let [s (state/create-state)
          g1 {:id "g1" :name "Go" :weight 2}]
      (state/add-game! s g1)
      (state/add-player! s {:id "p1" :name "Alice"})
      (state/add-player! s {:id "p2" :name "Bob"})
      (state/add-play! s {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                          :player-results [{:player-id "p1" :rank 1}
                                           {:player-id "p2" :rank 1}]})
      ;; Both rank 1 in 2-player game = 4 pts * weight 2 = 8
      (is (= 8 (scoring/play-score-for-player s (state/get-play s "play1") "p1")))
      (is (= 8 (scoring/play-score-for-player s (state/get-play s "play1") "p2")))))

  (testing "leaderboard reflects tied in-game ranks correctly"
    (let [s (state/create-state)
          g1 {:id "g1" :name "Chess" :weight 1}]
      (state/add-game! s g1)
      (state/add-player! s {:id "p1" :name "Alice"})
      (state/add-player! s {:id "p2" :name "Bob"})
      (state/add-player! s {:id "p3" :name "Carol"})
      ;; p1 and p2 tie for 1st, p3 is 3rd
      (state/add-play! s {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                          :player-results [{:player-id "p1" :rank 1}
                                           {:player-id "p2" :rank 1}
                                           {:player-id "p3" :rank 3}]})
      (let [board (scoring/leaderboard-data s)]
        ;; p1 and p2 both scored 5, p3 scored 1
        (is (= 5 (:total-score (first board))))
        (is (= 5 (:total-score (second board))))
        (is (= 1 (:total-score (nth board 2))))
        ;; p1 and p2 share leaderboard rank 1, p3 is rank 3
        (is (= 1 (:rank (first board))))
        (is (= 1 (:rank (second board))))
        (is (= 3 (:rank (nth board 2))))))))

(deftest leaderboard-tied-ranks
  (let [s (state/create-state)
        g1 {:id "g1" :name "Chess" :weight 1}
        p1 {:id "p1" :name "Alice"}
        p2 {:id "p2" :name "Bob"}
        p3 {:id "p3" :name "Carol"}]
    (state/add-game! s g1)
    (state/add-player! s p1)
    (state/add-player! s p2)
    (state/add-player! s p3)
    ;; Alice and Bob both win a 2-player game (weight 1) => 4 pts each
    ;; Carol loses both => 1 pt
    (state/add-play! s {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                        :player-results [{:player-id "p1" :rank 1}
                                         {:player-id "p3" :rank 2}]})
    (state/add-play! s {:id "play2" :game-id "g1" :timestamp "2024-01-02"
                        :player-results [{:player-id "p2" :rank 1}
                                         {:player-id "p3" :rank 2}]})
    (let [board (scoring/leaderboard-data s)
          ranks (mapv :rank board)]
      (testing "tied players share the same rank"
        (is (= 1 (:rank (first board))))
        (is (= 1 (:rank (second board)))))
      (testing "next rank skips to correct position"
        (is (= 3 (:rank (nth board 2)))))
      (testing "all three players present"
        (is (= 3 (count board)))))))
