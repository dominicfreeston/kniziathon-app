(ns kniziathon.handlers-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [ring.mock.request :as mock]
            [kniziathon.core :refer [app]]
            [kniziathon.handlers :as handlers]
            [kniziathon.state :as state]))

(defn reset-state [f]
  (reset! state/app-state {:games {} :players {} :plays {}})
  (f)
  (reset! state/app-state {:games {} :players {} :plays {}}))

(use-fixtures :each reset-state)

;; --- parse-int ---

(deftest parse-int-test
  (testing "valid integers"
    (is (= 5 (#'handlers/parse-int "5")))
    (is (= 0 (#'handlers/parse-int "0")))
    (is (= -1 (#'handlers/parse-int "-1"))))
  (testing "nil and blank return nil"
    (is (nil? (#'handlers/parse-int nil)))
    (is (nil? (#'handlers/parse-int "")))
    (is (nil? (#'handlers/parse-int "  "))))
  (testing "non-numeric returns nil"
    (is (nil? (#'handlers/parse-int "abc")))
    (is (nil? (#'handlers/parse-int "1.5")))))

;; --- parse-player-results ---

(deftest parse-player-results-test
  (testing "parses player id and rank from params"
    (let [params {:num-players "2"
                  :player-0-id "p1" :player-0-rank "1"
                  :player-1-id "p2" :player-1-rank "2"}
          results (#'handlers/parse-player-results params)]
      (is (= 2 (count results)))
      (is (= "p1" (:player-id (first results))))
      (is (= 1 (:rank (first results))))
      (is (= "p2" (:player-id (second results))))
      (is (= 2 (:rank (second results))))))
  (testing "includes game-score when present"
    (let [params {:num-players "1"
                  :player-0-id "p1" :player-0-rank "1" :player-0-score "42"}
          results (#'handlers/parse-player-results params)]
      (is (= 42 (:game-score (first results))))))
  (testing "omits game-score when blank"
    (let [params {:num-players "1"
                  :player-0-id "p1" :player-0-rank "1" :player-0-score ""}
          results (#'handlers/parse-player-results params)]
      (is (not (contains? (first results) :game-score))))))

;; --- validate-play ---

(deftest validate-play-test
  (state/add-game! {:id "g1" :name "Chess" :weight 1})
  (testing "valid play has no errors"
    (let [results [{:player-id "p1" :rank 1} {:player-id "p2" :rank 2}]]
      (is (empty? (#'handlers/validate-play "g1" results)))))
  (testing "missing game-id"
    (let [results [{:player-id "p1" :rank 1} {:player-id "p2" :rank 2}]]
      (is (some #(= "Game is required" %) (#'handlers/validate-play nil results)))
      (is (some #(= "Game is required" %) (#'handlers/validate-play "" results)))))
  (testing "unknown game-id"
    (let [results [{:player-id "p1" :rank 1} {:player-id "p2" :rank 2}]]
      (is (some #(= "Invalid game selected" %) (#'handlers/validate-play "bad-id" results)))))
  (testing "fewer than 2 players"
    (let [results [{:player-id "p1" :rank 1}]]
      (is (some #(= "At least 2 players required" %) (#'handlers/validate-play "g1" results)))))
  (testing "duplicate players"
    (let [results [{:player-id "p1" :rank 1} {:player-id "p1" :rank 2}]]
      (is (some #(= "Duplicate players selected" %) (#'handlers/validate-play "g1" results)))))
  (testing "non-consecutive ranks"
    (let [results [{:player-id "p1" :rank 1} {:player-id "p2" :rank 3}]]
      (is (seq (#'handlers/validate-play "g1" results)))))
  (testing "missing ranks"
    (let [results [{:player-id "p1" :rank nil} {:player-id "p2" :rank 2}]]
      (is (some #(= "All ranks must be filled" %) (#'handlers/validate-play "g1" results))))))

;; --- HTTP integration (ring-mock) ---

(deftest get-games-page
  (testing "GET /games returns 200 with empty state"
    (let [resp (app (mock/request :get "/games"))]
      (is (= 200 (:status resp))))))

(deftest get-games-new-form
  (testing "GET /games/new returns 200"
    (let [resp (app (mock/request :get "/games/new"))]
      (is (= 200 (:status resp))))))

(deftest post-game-valid
  (testing "POST /games with valid data redirects to /games"
    (let [resp (app (-> (mock/request :post "/games")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "name=Chess&weight=2")))]
      (is (= 302 (:status resp)))
      (is (= "/games" (get-in resp [:headers "Location"]))))))

(deftest post-game-invalid
  (testing "POST /games with missing name returns 200 with form"
    (let [resp (app (-> (mock/request :post "/games")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "name=&weight=2")))]
      (is (= 200 (:status resp)))))
  (testing "POST /games with non-numeric weight returns 200 with form"
    (let [resp (app (-> (mock/request :post "/games")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "name=Chess&weight=abc")))]
      (is (= 200 (:status resp))))))

(deftest get-players-page
  (testing "GET /players returns 200"
    (let [resp (app (mock/request :get "/players"))]
      (is (= 200 (:status resp))))))

(deftest post-player-valid
  (testing "POST /players with valid data redirects"
    (let [resp (app (-> (mock/request :post "/players")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "name=Alice")))]
      (is (= 302 (:status resp)))
      (is (= "/players" (get-in resp [:headers "Location"]))))))

(deftest post-player-invalid
  (testing "POST /players with blank name returns 200"
    (let [resp (app (-> (mock/request :post "/players")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "name=")))]
      (is (= 200 (:status resp))))))

(deftest get-leaderboard
  (testing "GET /leaderboard returns 200"
    (let [resp (app (mock/request :get "/leaderboard"))]
      (is (= 200 (:status resp))))))

(deftest get-play-edit-not-found
  (testing "GET /plays/:id/edit with unknown id returns 404"
    (let [resp (app (mock/request :get "/plays/nonexistent/edit"))]
      (is (= 404 (:status resp))))))

(deftest get-game-edit-not-found
  (testing "GET /games/:id/edit with unknown id returns 404"
    (let [resp (app (mock/request :get "/games/nonexistent/edit"))]
      (is (= 404 (:status resp))))))

(deftest htmx-add-player
  (testing "selecting a player appends them to the list"
    (state/add-player! {:id "p1" :name "Alice"})
    (state/add-player! {:id "p2" :name "Bob"})
    (let [resp (app (-> (mock/request :post "/htmx/plays/add-player")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "num-players=1&player-0-id=p1&player-0-rank=1&add-player-id=p2")))]
      (is (= 200 (:status resp)))
      (is (= 2 (count (re-seq #"class=\"player-row\"" (:body resp)))))))
  (testing "blank add-player-id leaves list unchanged"
    (state/add-player! {:id "p1" :name "Alice"})
    (let [resp (app (-> (mock/request :post "/htmx/plays/add-player")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "num-players=1&player-0-id=p1&player-0-rank=1&add-player-id=")))]
      (is (= 200 (:status resp)))
      (is (= 1 (count (re-seq #"class=\"player-row\"" (:body resp)))))))
  (testing "add-player dropdown absent at 6 players"
    (let [body (str/join "&"
                         (concat ["num-players=6"]
                                 (for [i (range 6)]
                                   (str "player-" i "-id=p1&player-" i "-rank=" (inc i)))))
          resp (app (-> (mock/request :post "/htmx/plays/add-player")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body body)))]
      (is (= 200 (:status resp)))
      (is (not (str/includes? (:body resp) "add-player-id"))))))

(deftest htmx-remove-player
  (testing "POST /htmx/plays/remove-player removes the indicated row"
    (state/add-player! {:id "p1" :name "Alice"})
    (state/add-player! {:id "p2" :name "Bob"})
    (state/add-player! {:id "p3" :name "Carol"})
    (let [resp (app (-> (mock/request :post "/htmx/plays/remove-player")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "num-players=3&player-0-id=p1&player-0-rank=1&player-1-id=p2&player-1-rank=2&player-2-id=p3&player-2-rank=3&remove-idx=1")))]
      (is (= 200 (:status resp)))
      ;; 2 rows remain
      (is (= 2 (count (re-seq #"class=\"player-row\"" (:body resp)))))))
  (testing "remove button absent when only 1 player remains"
    (state/add-player! {:id "p1" :name "Alice"})
    (let [resp (app (-> (mock/request :post "/htmx/plays/remove-player")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "num-players=2&player-0-id=p1&player-0-rank=1&player-1-id=&player-1-rank=2&remove-idx=1")))]
      (is (= 200 (:status resp)))
      (is (not (clojure.string/includes? (:body resp) "remove-player"))))))

(deftest game-detail-page
  (testing "GET /games/:id/plays returns 200 and lists plays"
    (state/add-game! {:id "g1" :name "Chess" :weight 2})
    (state/add-player! {:id "p1" :name "Alice"})
    (state/add-player! {:id "p2" :name "Bob"})
    (state/add-play! {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id "p1" :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (let [resp (app (mock/request :get "/games/g1/plays"))]
      (is (= 200 (:status resp)))
      (is (str/includes? (:body resp) "Chess"))
      (is (str/includes? (:body resp) "Alice"))
      (is (str/includes? (:body resp) "Bob"))))
  (testing "GET /games/:id/plays with unknown id returns 404"
    (let [resp (app (mock/request :get "/games/nonexistent/plays"))]
      (is (= 404 (:status resp))))))

(deftest split-player-test
  (testing "GET /players/:id/split shows the form with plays"
    (state/add-game! {:id "g1" :name "Chess" :weight 1})
    (state/add-player! {:id "p1" :name "Alice"})
    (state/add-player! {:id "p2" :name "Bob"})
    (state/add-play! {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id "p1" :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (let [resp (app (mock/request :get "/players/p1/split"))]
      (is (= 200 (:status resp)))
      (is (str/includes? (:body resp) "Alice"))
      (is (str/includes? (:body resp) "Chess"))))
  (testing "GET /players/:id/split with unknown id returns 404"
    (let [resp (app (mock/request :get "/players/nonexistent/split"))]
      (is (= 404 (:status resp)))))
  (testing "POST /players/:id/split creates new player and reassigns checked plays"
    (state/add-game! {:id "g1" :name "Chess" :weight 1})
    (state/add-player! {:id "p1" :name "Alice"})
    (state/add-player! {:id "p2" :name "Bob"})
    (state/add-play! {:id "play1" :game-id "g1" :timestamp "2024-01-01"
                      :player-results [{:player-id "p1" :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (state/add-play! {:id "play2" :game-id "g1" :timestamp "2024-01-02"
                      :player-results [{:player-id "p1" :rank 1}
                                       {:player-id "p2" :rank 2}]})
    (let [resp (app (-> (mock/request :post "/players/p1/split")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "new-player-name=Alice+B&move-play-play1=true")))]
      (is (= 302 (:status resp)))
      ;; play1 should now belong to the new player, not p1
      (let [play1 (state/get-play "play1")
            play2 (state/get-play "play2")
            p1-ids (set (map :player-id (:player-results play1)))
            p2-ids (set (map :player-id (:player-results play2)))]
        (is (not (contains? p1-ids "p1")) "play1 should be reassigned away from p1")
        (is (contains? p2-ids "p1") "play2 should still belong to p1"))))
  (testing "POST /players/:id/split with blank name returns error"
    (state/add-player! {:id "p1" :name "Alice"})
    (let [resp (app (-> (mock/request :post "/players/p1/split")
                        (mock/content-type "application/x-www-form-urlencoded")
                        (mock/body "new-player-name=")))]
      (is (= 200 (:status resp)))
      (is (str/includes? (:body resp) "New player name is required")))))
