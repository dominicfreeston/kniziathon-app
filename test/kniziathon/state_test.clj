(ns kniziathon.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [kniziathon.state :as state]))

;; --- Games ---

(deftest game-crud
  (let [s (state/create-state)
        game {:id "g1" :name "Modern Art" :weight 2}]
    (testing "add and retrieve"
      (state/add-game! s game)
      (is (= game (state/get-game s "g1"))))
    (testing "get-all-games"
      (is (= [game] (vec (state/get-all-games s)))))
    (testing "update"
      (state/update-game! s "g1" {:name "Modern Art (updated)" :weight 3})
      (is (= "Modern Art (updated)" (:name (state/get-game s "g1"))))
      (is (= 3 (:weight (state/get-game s "g1")))))
    (testing "delete"
      (state/delete-game! s "g1")
      (is (nil? (state/get-game s "g1")))
      (is (empty? (state/get-all-games s))))))

;; --- Players ---

(deftest player-crud
  (let [s (state/create-state)
        player {:id "p1" :name "Alice"}]
    (testing "add and retrieve"
      (state/add-player! s player)
      (is (= player (state/get-player s "p1"))))
    (testing "get-all-players"
      (is (= [player] (vec (state/get-all-players s)))))
    (testing "update"
      (state/update-player! s "p1" {:name "Alice B."})
      (is (= "Alice B." (:name (state/get-player s "p1")))))
    (testing "delete"
      (state/delete-player! s "p1")
      (is (nil? (state/get-player s "p1"))))))

;; --- Plays ---

(deftest play-crud
  (let [s (state/create-state)
        play {:id "play1" :game-id "g1" :timestamp "2024-01-01"
              :player-results [{:player-id "p1" :rank 1}
                               {:player-id "p2" :rank 2}]}]
    (testing "add and retrieve"
      (state/add-play! s play)
      (is (= play (state/get-play s "play1"))))
    (testing "get-all-plays"
      (is (= [play] (vec (state/get-all-plays s)))))
    (testing "update"
      (state/update-play! s "play1" {:game-id "g2"})
      (is (= "g2" (:game-id (state/get-play s "play1")))))
    (testing "delete"
      (state/delete-play! s "play1")
      (is (nil? (state/get-play s "play1"))))))

;; --- Import / Clear ---

(deftest clear-all-data
  (let [s (state/create-state)]
    (state/add-game! s {:id "g1" :name "Test" :weight 1})
    (state/add-player! s {:id "p1" :name "Alice"})
    (state/clear-all-data! s)
    (is (empty? (state/get-all-games s)))
    (is (empty? (state/get-all-players s)))
    (is (empty? (state/get-all-plays s)))))

(deftest import-data-replace
  (let [s (state/create-state)]
    (state/add-game! s {:id "g1" :name "Old Game" :weight 1})
    (let [new-data {:games {"g2" {:id "g2" :name "New Game" :weight 2}}
                    :players {}
                    :plays {}}]
      (state/import-data! s new-data true)
      (is (nil? (state/get-game s "g1")))
      (is (= "New Game" (:name (state/get-game s "g2")))))))

(deftest import-data-merge
  (let [s (state/create-state)]
    (state/add-game! s {:id "g1" :name "Existing" :weight 1})
    (let [new-data {:games {"g2" {:id "g2" :name "Imported" :weight 3}}
                    :players {}
                    :plays {}}]
      (state/import-data! s new-data false)
      (is (= "Existing" (:name (state/get-game s "g1"))))
      (is (= "Imported" (:name (state/get-game s "g2")))))))

(deftest get-missing-entities-return-nil
  (let [s (state/create-state)]
    (is (nil? (state/get-game s "nonexistent")))
    (is (nil? (state/get-player s "nonexistent")))
    (is (nil? (state/get-play s "nonexistent")))))

;; --- Persistence round-trip ---

(deftest persistence-round-trip
  (let [tmp (io/file (System/getProperty "java.io.tmpdir")
                     (str "kniziathon-test-" (System/nanoTime) ".edn"))]
    (try
      (let [s1 (state/create-state tmp)]
        (state/add-game! s1 {:id "g1" :name "Tigris" :weight 3})
        (state/add-player! s1 {:id "p1" :name "Alice"})
        (state/add-play! s1 {:id "play1" :game-id "g1" :timestamp "2024-06-01"
                             :player-results [{:player-id "p1" :rank 1}
                                              {:player-id "p2" :rank 2}]})
        ;; wait for the async persistence future to flush
        (Thread/sleep 200)
        (testing "file was written"
          (is (.exists tmp))))

      (testing "new state created from the same file recovers all data"
        (let [s2 (state/create-state tmp)]
          (is (= "Tigris" (:name (state/get-game s2 "g1"))))
          (is (= 3 (:weight (state/get-game s2 "g1"))))
          (is (= "Alice" (:name (state/get-player s2 "p1"))))
          (let [play (state/get-play s2 "play1")]
            (is (= "g1" (:game-id play)))
            (is (= 2 (count (:player-results play)))))))
      (finally
        (when (.exists tmp)
          (.delete tmp))))))
