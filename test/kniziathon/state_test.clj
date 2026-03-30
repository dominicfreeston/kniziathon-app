(ns kniziathon.state-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [kniziathon.state :as state]))

(defn reset-state [f]
  (reset! state/app-state {:games {} :players {} :plays {}})
  (f)
  (reset! state/app-state {:games {} :players {} :plays {}}))

(use-fixtures :each reset-state)

;; --- Games ---

(deftest game-crud
  (let [game {:id "g1" :name "Modern Art" :weight 2}]
    (testing "add and retrieve"
      (state/add-game! game)
      (is (= game (state/get-game "g1"))))
    (testing "get-all-games"
      (is (= [game] (vec (state/get-all-games)))))
    (testing "update"
      (state/update-game! "g1" {:name "Modern Art (updated)" :weight 3})
      (is (= "Modern Art (updated)" (:name (state/get-game "g1"))))
      (is (= 3 (:weight (state/get-game "g1")))))
    (testing "delete"
      (state/delete-game! "g1")
      (is (nil? (state/get-game "g1")))
      (is (empty? (state/get-all-games))))))

;; --- Players ---

(deftest player-crud
  (let [player {:id "p1" :name "Alice"}]
    (testing "add and retrieve"
      (state/add-player! player)
      (is (= player (state/get-player "p1"))))
    (testing "get-all-players"
      (is (= [player] (vec (state/get-all-players)))))
    (testing "update"
      (state/update-player! "p1" {:name "Alice B."})
      (is (= "Alice B." (:name (state/get-player "p1")))))
    (testing "delete"
      (state/delete-player! "p1")
      (is (nil? (state/get-player "p1"))))))

;; --- Plays ---

(deftest play-crud
  (let [play {:id "play1" :game-id "g1" :timestamp "2024-01-01"
              :player-results [{:player-id "p1" :rank 1}
                               {:player-id "p2" :rank 2}]}]
    (testing "add and retrieve"
      (state/add-play! play)
      (is (= play (state/get-play "play1"))))
    (testing "get-all-plays"
      (is (= [play] (vec (state/get-all-plays)))))
    (testing "update"
      (state/update-play! "play1" {:game-id "g2"})
      (is (= "g2" (:game-id (state/get-play "play1")))))
    (testing "delete"
      (state/delete-play! "play1")
      (is (nil? (state/get-play "play1"))))))

;; --- Import / Clear ---

(deftest clear-all-data
  (state/add-game! {:id "g1" :name "Test" :weight 1})
  (state/add-player! {:id "p1" :name "Alice"})
  (state/clear-all-data!)
  (is (empty? (state/get-all-games)))
  (is (empty? (state/get-all-players)))
  (is (empty? (state/get-all-plays))))

(deftest import-data-replace
  (state/add-game! {:id "g1" :name "Old Game" :weight 1})
  (let [new-data {:games {"g2" {:id "g2" :name "New Game" :weight 2}}
                  :players {}
                  :plays {}}]
    (state/import-data! new-data true)
    (is (nil? (state/get-game "g1")))
    (is (= "New Game" (:name (state/get-game "g2"))))))

(deftest import-data-merge
  (state/add-game! {:id "g1" :name "Existing" :weight 1})
  (let [new-data {:games {"g2" {:id "g2" :name "Imported" :weight 3}}
                  :players {}
                  :plays {}}]
    (state/import-data! new-data false)
    (is (= "Existing" (:name (state/get-game "g1"))))
    (is (= "Imported" (:name (state/get-game "g2"))))))

(deftest get-missing-entities-return-nil
  (is (nil? (state/get-game "nonexistent")))
  (is (nil? (state/get-player "nonexistent")))
  (is (nil? (state/get-play "nonexistent"))))
