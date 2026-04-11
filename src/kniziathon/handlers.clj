(ns kniziathon.handlers
  (:require [ring.util.response :as response]
            [kniziathon.state :as state]
            [kniziathon.views :as views]
            [kniziathon.scoring :as scoring]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [hiccup.core :as hiccup])
  (:import [java.util UUID]
           [java.time Instant]))

;; Utility functions
(defn parse-int [s]
  (when (and s (not (str/blank? s)))
    (try (Integer/parseInt s)
         (catch Exception _ nil))))

(defn now-timestamp []
  (.toString (Instant/now)))

(defn- get-state [request]
  (:kniziathon/state request))

;; Games handlers
(defn games-list [request]
  (let [s (get-state request)
        plays (state/get-all-plays s)
        avg-durations (into {}
                        (for [game (state/get-all-games s)
                              :let [timed (filter :duration-minutes
                                                  (filter #(= (:game-id %) (:id game)) plays))]
                              :when (seq timed)]
                          [(:id game) (Math/round (double (/ (reduce + (map :duration-minutes timed))
                                                             (count timed))))]))  ]
    (response/response
      (views/games-list (state/get-all-games s) avg-durations))))

(defn new-game-form [_request]
  (response/response
    (views/game-form nil)))

(defn create-game [request]
  (let [s (get-state request)
        params (:params request)
        name (:name params)
        weight (parse-int (:weight params))
        errors (cond-> []
                 (str/blank? name) (conj "Name is required")
                 (not weight) (conj "Weight must be a valid number")
                 (and weight (<= weight 0)) (conj "Weight must be positive"))]
    (if (seq errors)
      (response/response (views/game-form params errors))
      (do
        (state/add-game! s {:id (str (UUID/randomUUID))
                           :name name
                           :weight weight})
        (response/redirect "/games")))))

(defn edit-game-form [request id]
  (let [s (get-state request)]
    (if-let [game (state/get-game s id)]
      (response/response (views/game-form game))
      (response/not-found "Game not found"))))

(defn update-game [request]
  (let [s (get-state request)
        params (:params request)
        id (:id params)
        name (:name params)
        weight (parse-int (:weight params))
        errors (cond-> []
                 (str/blank? name) (conj "Name is required")
                 (not weight) (conj "Weight must be a valid number")
                 (and weight (<= weight 0)) (conj "Weight must be positive"))]
    (if (seq errors)
      (response/response (views/game-form (state/get-game s id) errors))
      (do
        (state/update-game! s id {:name name :weight weight})
        (response/redirect "/games")))))

(defn delete-game [request id]
  (state/delete-game! (get-state request) id)
  (response/redirect "/games"))

;; Game merge handlers
(defn- game-play-counts [s]
  (frequencies (map :game-id (state/get-all-plays s))))

(defn merge-games-form-get [request]
  (let [s (get-state request)]
    (response/response (views/merge-games-form (state/get-all-games s) (game-play-counts s) nil nil nil))))

(defn merge-games [request]
  (let [s (get-state request)
        params (:params request)
        source-id (:source-game-id params)
        target-id (:target-game-id params)
        confirm (:confirm params)
        games (state/get-all-games s)
        play-counts (game-play-counts s)]
    (println "Merge params:" params)
    (println "Source:" source-id "Target:" target-id "Confirm:" confirm)

    (if (= confirm "true")
      ;; Perform the merge atomically
      (let [errors (cond-> []
                     (str/blank? source-id) (conj "Source game is required")
                     (str/blank? target-id) (conj "Target game is required")
                     (= source-id target-id) (conj "Cannot merge a game into itself")
                     (and source-id (not (state/get-game s source-id))) (conj "Source game not found")
                     (and target-id (not (state/get-game s target-id))) (conj "Target game not found"))]
        (if (seq errors)
          (response/response
            (views/merge-games-form games play-counts nil nil nil errors))
          (do
            ;; Perform atomic merge
            (swap! s
              (fn [current-state]
                (let [plays (:plays current-state)
                      ;; Update all plays from source to target
                      updated-plays (into {}
                                      (map (fn [[play-id play]]
                                             [play-id
                                              (if (= (:game-id play) source-id)
                                                (assoc play :game-id target-id)
                                                play)])
                                           plays))
                      ;; Remove source game
                      updated-games (dissoc (:games current-state) source-id)]
                  (assoc current-state
                    :plays updated-plays
                    :games updated-games))))
            (response/redirect "/games"))))

      ;; Show preview (no confirm yet)
      (if (and source-id target-id)
        (let [source-game (state/get-game s source-id)
              target-game (state/get-game s target-id)
              source-plays (get play-counts source-id 0)
              target-plays (get play-counts target-id 0)
              weight-diff (when (and source-game target-game)
                           (Math/abs (- (:weight source-game) (:weight target-game))))
              weight-warning (and weight-diff (> weight-diff (* 0.1 (:weight target-game))))
              errors (cond-> []
                       (not source-game) (conj "Source game not found")
                       (not target-game) (conj "Target game not found")
                       (= source-id target-id) (conj "Cannot merge a game into itself"))]
          (if (seq errors)
            (response/response (views/merge-games-form games play-counts source-game target-game nil errors))
            (response/response
              (views/merge-games-form
                games
                play-counts
                source-game
                target-game
                {:source-name (:name source-game)
                 :source-weight (:weight source-game)
                 :source-plays source-plays
                 :target-name (:name target-game)
                 :target-weight (:weight target-game)
                 :target-plays target-plays
                 :weight-warning weight-warning}))))
        ;; Missing source or target, show form again
        (response/response (views/merge-games-form games play-counts nil nil nil))))))

;; Players handlers
(defn players-list [request]
  (let [s (get-state request)]
    (response/response
      (views/players-list (state/get-all-players s) (scoring/leaderboard-data s)))))

(defn new-player-form [_request]
  (response/response
    (views/player-form nil)))

(defn create-player [request]
  (let [s (get-state request)
        params (:params request)
        name (:name params)
        errors (when (str/blank? name) ["Name is required"])]
    (if errors
      (response/response (views/player-form params errors))
      (do
        (state/add-player! s {:id (str (UUID/randomUUID))
                             :name name})
        (response/redirect "/players")))))

(defn edit-player-form [request id]
  (let [s (get-state request)]
    (if-let [player (state/get-player s id)]
      (response/response (views/player-form player))
      (response/not-found "Player not found"))))

(defn update-player [request]
  (let [s (get-state request)
        params (:params request)
        id (:id params)
        name (:name params)
        errors (when (str/blank? name) ["Name is required"])]
    (if errors
      (response/response (views/player-form (state/get-player s id) errors))
      (do
        (state/update-player! s id {:name name})
        (response/redirect "/players")))))

(defn delete-player [request id]
  (state/delete-player! (get-state request) id)
  (response/redirect "/players"))

;; Player merge handlers
(defn merge-players-form-get [request]
  (let [s (get-state request)]
    (response/response
      (views/merge-players-form (state/get-all-players s)
                               (scoring/leaderboard-data s)
                               nil nil nil))))

(defn merge-players [request]
  (let [s (get-state request)
        params (:params request)
        source-id (:source-player-id params)
        target-id (:target-player-id params)
        confirm (:confirm params)
        players (state/get-all-players s)
        leaderboard (scoring/leaderboard-data s)]
    (println "Merge players params:" params)
    (println "Source:" source-id "Target:" target-id "Confirm:" confirm)

    (if (= confirm "true")
      ;; Perform the merge atomically
      (let [errors (cond-> []
                     (str/blank? source-id) (conj "Source player is required")
                     (str/blank? target-id) (conj "Target player is required")
                     (= source-id target-id) (conj "Cannot merge a player into itself")
                     (and source-id (not (state/get-player s source-id))) (conj "Source player not found")
                     (and target-id (not (state/get-player s target-id))) (conj "Target player not found"))]
        (if (seq errors)
          (response/response
            (views/merge-players-form players leaderboard nil nil nil errors))
          (do
            ;; Perform atomic merge
            (swap! s
              (fn [current-state]
                (let [plays (:plays current-state)
                      ;; Update all plays: replace source player with target in player-results
                      updated-plays (into {}
                                      (map (fn [[play-id play]]
                                             [play-id
                                              (update play :player-results
                                                (fn [results]
                                                  (mapv (fn [pr]
                                                         (if (= (:player-id pr) source-id)
                                                           (assoc pr :player-id target-id)
                                                           pr))
                                                       results)))])
                                           plays))
                      ;; Remove source player
                      updated-players (dissoc (:players current-state) source-id)]
                  (assoc current-state
                    :plays updated-plays
                    :players updated-players))))
            (response/redirect "/players"))))

      ;; Show preview (no confirm yet)
      (if (and source-id target-id)
        (let [source-player (state/get-player s source-id)
              target-player (state/get-player s target-id)
              all-plays (state/get-all-plays s)
              source-plays (count (filter (fn [play]
                                           (some #(= (:player-id %) source-id)
                                                 (:player-results play)))
                                         all-plays))
              target-plays (count (filter (fn [play]
                                           (some #(= (:player-id %) target-id)
                                                 (:player-results play)))
                                         all-plays))
              source-stats (first (filter #(= (:player-id %) source-id) leaderboard))
              target-stats (first (filter #(= (:player-id %) target-id) leaderboard))
              errors (cond-> []
                       (not source-player) (conj "Source player not found")
                       (not target-player) (conj "Target player not found")
                       (= source-id target-id) (conj "Cannot merge a player into itself"))]
          (if (seq errors)
            (response/response (views/merge-players-form players leaderboard source-player target-player nil errors))
            (response/response
              (views/merge-players-form
                players
                leaderboard
                source-player
                target-player
                {:source-name (:name source-player)
                 :source-games (or (:games-played source-stats) 0)
                 :source-score (or (:total-score source-stats) 0)
                 :source-plays source-plays
                 :target-name (:name target-player)
                 :target-games (or (:games-played target-stats) 0)
                 :target-score (or (:total-score target-stats) 0)
                 :target-plays target-plays
                 :recalc-warning true}))))
        ;; Missing source or target, show form again
        (response/response (views/merge-players-form players leaderboard nil nil nil))))))

;; Player split handlers
(defn- split-player-view-data [s id]
  (let [player (state/get-player s id)
        plays (scoring/player-plays s id)
        games-map (into {} (map (fn [g] [(:id g) g]) (state/get-all-games s)))
        players-map (into {} (map (fn [p] [(:id p) p]) (state/get-all-players s)))]
    [player plays games-map players-map]))

(defn split-player-form-get [request id]
  (let [s (get-state request)]
    (if-let [player (state/get-player s id)]
      (let [[_ plays games-map players-map] (split-player-view-data s id)]
        (response/response (views/split-player-form player plays games-map players-map)))
      (response/not-found "Player not found"))))

(defn split-player [request id]
  (let [s (get-state request)
        params (:params request)
        [player plays games-map players-map] (split-player-view-data s id)]
    (if (nil? player)
      (response/not-found "Player not found")
      (let [new-name (:new-player-name params)
            errors (cond-> []
                     (str/blank? new-name) (conj "New player name is required"))]
        (if (seq errors)
          (response/response (views/split-player-form player plays games-map players-map errors))
          (let [new-player-id (str (UUID/randomUUID))
                new-player {:id new-player-id :name new-name}
                move-play-ids (into #{} (for [[k v] params
                                              :when (and (str/starts-with? (name k) "move-play-")
                                                         (= v "true"))]
                                          (subs (name k) (count "move-play-"))))]
            (swap! s
              (fn [state]
                (let [updated-plays
                      (into {} (map (fn [[play-id play]]
                                      [play-id
                                       (if (contains? move-play-ids play-id)
                                         (update play :player-results
                                           (fn [results]
                                             (mapv (fn [pr]
                                                     (if (= (:player-id pr) id)
                                                       (assoc pr :player-id new-player-id)
                                                       pr))
                                                   results)))
                                         play)])
                                    (:plays state)))]
                  (-> state
                      (assoc-in [:players new-player-id] new-player)
                      (assoc :plays updated-plays)))))
            (response/redirect "/players")))))))

;; Plays handlers
(defn parse-player-results [params]
  (let [num-players (or (parse-int (:num-players params)) 2)]
    (vec
      (for [i (range num-players)]
        (let [player-id (get params (keyword (str "player-" i "-id")))
              game-score (parse-int (get params (keyword (str "player-" i "-score"))))
              rank (parse-int (get params (keyword (str "player-" i "-rank"))))]
          (cond-> {:player-id player-id
                   :rank rank}
            game-score (assoc :game-score game-score)))))))

(defn valid-competition-ranks?
  "Check that ranks follow standard competition ranking rules.
   Sorted ranks: each rank r appearing at 0-indexed position p must equal p+1,
   and ties (duplicate ranks) are allowed."
  [ranks]
  (let [sorted (sort ranks)]
    (every? identity
      (map-indexed
        (fn [i r]
          (or (zero? i)
              (= r (nth sorted (dec i)))     ;; tied with previous
              (= r (inc i))))                ;; rank equals 1-based position
        sorted))))

(defn reset-ranks
  "Assign ranks 1,2,3...N with no ties."
  [player-results]
  (vec (map-indexed (fn [i pr] (assoc pr :rank (inc i))) player-results)))

(defn toggle-tie-at
  "Toggle a tie between positions idx and idx+1. Recalculates all ranks."
  [player-results idx]
  (let [ranks (mapv :rank player-results)
        currently-tied? (= (nth ranks idx) (nth ranks (inc idx)))
        ;; Build set of tied pairs from current ranks
        tied-set (set (for [i (range (dec (count ranks)))
                           :when (= (nth ranks i) (nth ranks (inc i)))]
                       i))
        new-tied-set (if currently-tied?
                       (disj tied-set idx)
                       (conj tied-set idx))
        ;; Recalculate ranks from tie set
        new-ranks (reduce (fn [result i]
                            (conj result
                                  (if (and (pos? i) (contains? new-tied-set (dec i)))
                                    (peek result)
                                    (inc i))))
                          [1]
                          (range 1 (count player-results)))]
    (vec (map-indexed (fn [i pr] (assoc pr :rank (nth new-ranks i))) player-results))))

(defn- validate-play [s game-id player-results]
  (let [player-ids (map :player-id player-results)
        ranks (map :rank player-results)
        num-players (count player-results)]
    (cond-> []
      (not game-id) (conj "Game is required")
      (str/blank? game-id) (conj "Game is required")
      (and game-id (not (str/blank? game-id)) (not (state/get-game s game-id)))
        (conj "Invalid game selected")
      (< num-players 2) (conj "At least 2 players required")
      (some str/blank? player-ids) (conj "All player slots must be filled")
      (not= (count player-ids) (count (set player-ids))) (conj "Duplicate players selected")
      (some nil? ranks) (conj "All ranks must be filled")
      (and (not-any? nil? ranks)
           (not (valid-competition-ranks? ranks)))
        (conj "Invalid ranks — ties must share a rank and the next rank must skip accordingly"))))

(defn plays-list [request]
  (let [s (get-state request)
        games-map (into {} (map (fn [g] [(:id g) g]) (state/get-all-games s)))
        players-map (into {} (map (fn [p] [(:id p) p]) (state/get-all-players s)))
        tie-mode (or (state/get-setting s :tie-scoring-mode) :full)]
    (response/response
      (views/plays-list (state/get-all-plays s) games-map players-map nil tie-mode))))

(defn new-play-form [request]
  (let [s (get-state request)
        params (:params request)]
    (response/response
      (views/play-form {:game-id (:game-id params) :player-results []}
                       (state/get-all-games s)
                       (state/get-all-players s)))))

(defn create-play [request]
  (let [s (get-state request)
        params (:params request)
        game-id (:game-id params)
        player-results (parse-player-results params)
        duration-minutes (parse-int (:duration-minutes params))
        errors (validate-play s game-id player-results)]
    (println "Create play params:" params)
    (println "Game ID:" game-id)
    (println "Player results:" player-results)
    (println "Errors:" errors)
    (if (seq errors)
      (response/response
        (views/play-form {:game-id game-id :player-results player-results
                          :duration-minutes duration-minutes}
                        (state/get-all-games s)
                        (state/get-all-players s)
                        errors))
      (do
        (state/add-play! s (cond-> {:id (str (UUID/randomUUID))
                                    :game-id game-id
                                    :timestamp (now-timestamp)
                                    :player-results player-results}
                             duration-minutes (assoc :duration-minutes duration-minutes)))
        (response/redirect "/plays")))))

(defn edit-play-form [request id]
  (let [s (get-state request)]
    (if-let [play (state/get-play s id)]
      (response/response
        (views/play-form play
                         (state/get-all-games s)
                         (state/get-all-players s)))
      (response/not-found "Play not found"))))

(defn update-play [request]
  (let [s (get-state request)
        params (:params request)
        id (:id params)
        game-id (:game-id params)
        player-results (parse-player-results params)
        duration-minutes (parse-int (:duration-minutes params))
        errors (validate-play s game-id player-results)]
    (if (seq errors)
      (response/response
        (views/play-form (assoc (state/get-play s id)
                               :game-id game-id
                               :player-results player-results
                               :duration-minutes duration-minutes)
                        (state/get-all-games s)
                        (state/get-all-players s)
                        errors))
      (do
        (state/update-play! s id {:game-id game-id
                                  :player-results player-results
                                  :duration-minutes duration-minutes})
        (response/redirect "/plays")))))

(defn delete-play [request id]
  (state/delete-play! (get-state request) id)
  (response/redirect "/plays"))

;; Leaderboard handlers
(defn game-detail [request id]
  (let [s (get-state request)]
    (if-let [game (state/get-game s id)]
      (let [plays (filter #(= (:game-id %) id) (state/get-all-plays s))]
        (response/response
          (views/game-detail game plays (state/get-all-players s))))
      (response/not-found "Game not found"))))

(defn leaderboard [request]
  (let [s (get-state request)]
    (response/response
      (views/leaderboard (scoring/leaderboard-data s)
                         (state/get-setting s :multi-play-scoring)
                         (or (state/get-setting s :tie-scoring-mode) :full)
                         (state/get-setting s :event-title)))))

(defn toggle-scoring-mode [request]
  (state/toggle-setting! (get-state request) :multi-play-scoring)
  (response/redirect "/leaderboard"))

(defn set-tie-scoring-mode [request]
  (let [mode (keyword (get-in request [:params :mode]))]
    (when (#{:full :average :lower} mode)
      (state/set-setting! (get-state request) :tie-scoring-mode mode))
    (response/redirect "/leaderboard")))

(defn leaderboard-fragment [request]
  (let [s (get-state request)]
    (-> (hiccup/html (views/leaderboard-table (scoring/leaderboard-data s)))
        (response/response)
        (response/content-type "text/html"))))

(defn player-detail [request id]
  (let [s (get-state request)]
    (if-let [player (state/get-player s id)]
      (let [players-map (into {} (map (fn [p] [(:id p) p]) (state/get-all-players s)))]
        (response/response
          (views/player-detail player
                              (scoring/player-game-details s id)
                              players-map
                              (state/get-setting s :multi-play-scoring)
                              (scoring/player-total-score s id)
                              (scoring/player-total-plays s id)
                              (or (state/get-setting s :tie-scoring-mode) :full))))
      (response/not-found "Player not found"))))

(defn- htmx-fragment [hiccup-data]
  (-> (hiccup/html hiccup-data)
      (response/response)
      (response/content-type "text/html")))

;; Auto-rank handler
(defn auto-rank-by-score [request]
  (let [s (get-state request)
        params (:params request)
        ranked (scoring/auto-rank-by-scores (parse-player-results params))]
    (htmx-fragment (views/player-results-fragment ranked (state/get-all-players s)))))

;; Toggle tie handler
(defn toggle-tie [request]
  (let [s (get-state request)
        params (:params request)
        player-results (parse-player-results params)
        tie-idx (parse-int (:tie-idx params))
        toggled (toggle-tie-at player-results tie-idx)]
    (htmx-fragment (views/player-results-fragment toggled (state/get-all-players s)))))

;; Move player handler
(defn move-player [request]
  (let [s (get-state request)
        params (:params request)
        player-results (parse-player-results params)
        move-idx (parse-int (:move-idx params))
        direction (:direction params)
        swapped (if (= direction "up")
                  (let [temp (get player-results move-idx)
                        other (get player-results (dec move-idx))]
                    (-> player-results (assoc move-idx other) (assoc (dec move-idx) temp)))
                  (let [temp (get player-results move-idx)
                        other (get player-results (inc move-idx))]
                    (-> player-results (assoc move-idx other) (assoc (inc move-idx) temp))))]
    (htmx-fragment (views/player-results-fragment (reset-ranks swapped) (state/get-all-players s)))))

;; Reorder players handler
(defn reorder-players [request]
  (let [s (get-state request)
        params (:params request)]
    (htmx-fragment (views/player-results-fragment (reset-ranks (parse-player-results params)) (state/get-all-players s)))))

;; Add / remove player handlers
(defn add-player [request]
  (let [s (get-state request)
        params (:params request)
        player-results (parse-player-results params)
        new-player-id (:add-player-id params)]
    (cond
      (= new-player-id "new")
      (htmx-fragment (views/player-results-fragment player-results (state/get-all-players s) true))

      (str/blank? new-player-id)
      (htmx-fragment (views/player-results-fragment player-results (state/get-all-players s)))

      :else
      (htmx-fragment (views/player-results-fragment
                       (reset-ranks (conj player-results {:player-id new-player-id}))
                       (state/get-all-players s))))))

(defn create-and-add-player [request]
  (let [s (get-state request)
        params (:params request)
        player-results (parse-player-results params)
        new-name (:new-player-name params)]
    (if (str/blank? new-name)
      (htmx-fragment (views/player-results-fragment player-results (state/get-all-players s) true))
      (let [new-player-id (str (UUID/randomUUID))]
        (state/add-player! s {:id new-player-id :name new-name})
        (htmx-fragment (views/player-results-fragment
                         (reset-ranks (conj player-results {:player-id new-player-id}))
                         (state/get-all-players s)))))))

(defn remove-player [request]
  (let [s (get-state request)
        params (:params request)
        player-results (parse-player-results params)
        idx (parse-int (:remove-idx params))
        new-results (reset-ranks (vec (concat (subvec player-results 0 idx)
                                             (subvec player-results (inc idx)))))]
    (htmx-fragment (views/player-results-fragment new-results (state/get-all-players s)))))

;; Data management handlers
(defn data-management [request]
  (let [s (get-state request)]
    (response/response
      (views/data-management (state/get-setting s :event-title)))))

(defn set-event-title [request]
  (let [s (get-state request)
        title (get-in request [:params :event-title])
        title (when-not (clojure.string/blank? title) (clojure.string/trim title))]
    (state/set-setting! s :event-title title)
    (response/redirect "/data")))

(defn export-data [request]
  (let [s (get-state request)
        data @s
        timestamp (.toString (Instant/now))
        filename (str "kniziathon-" timestamp ".json")
        ;; Convert maps to arrays for export
        export-data {:games (vec (vals (:games data)))
                     :players (vec (vals (:players data)))
                     :plays (vec (vals (:plays data)))}
        ;; Convert to JSON
        json-str (json/write-str export-data)]
    (-> (response/response json-str)
        (response/content-type "application/json")
        (response/header "Content-Disposition"
                        (str "attachment; filename=\"" filename "\"")))))

(defn import-data [request]
  (let [s (get-state request)
        params (:params request)
        file (:file params)
        mode (:mode params)
        replace? (= mode "replace")]
    (println "Import params:" params)
    (println "File:" file)
    (println "Mode:" mode "Replace?" replace?)
    (if file
      (try
        (let [content (slurp (:tempfile file))
              ;; Parse JSON - arrays in JSON should become arrays in Clojure
              raw-data (json/read-str content :key-fn keyword)
              ;; Convert arrays back to maps keyed by ID
              ;; Also ensure weights are integers and scores are integers
              data {:games (into {} (map (fn [game]
                                          [(:id game)
                                           (update game :weight
                                                  (fn [w] (if (number? w) (int w) w)))])
                                        (:games raw-data)))
                    :players (into {} (map (fn [player] [(:id player) player]) (:players raw-data)))
                    :plays (into {} (map (fn [play]
                                          [(:id play)
                                           (update play :player-results
                                                  (fn [prs]
                                                    (mapv (fn [pr]
                                                           (if (:game-score pr)
                                                             (update pr :game-score
                                                                    (fn [s] (if (number? s) (int s) s)))
                                                             pr))
                                                         prs)))])
                                        (:plays raw-data)))}]
          (println "Parsed data - games:" (count (:games data))
                   "players:" (count (:players data))
                   "plays:" (count (:plays data)))
          (state/import-data! s data replace?)
          (response/redirect "/data"))
        (catch Exception e
          (println "Error importing:" (.getMessage e))
          (.printStackTrace e)
          (response/response
            (views/data-management (str "Error importing data: " (.getMessage e))))))
      (response/response
        (views/data-management "No file selected")))))

(defn clear-data [request]
  (state/clear-all-data! (get-state request))
  (response/redirect "/data"))

(defn import-games-csv [request]
  (let [s (get-state request)
        params (:params request)
        file (:file params)]
    (println "Import games CSV params:" params)
    (println "File:" file)
    (if file
      (try
        (let [content (slurp (:tempfile file))
              ;; Parse CSV
              csv-data (csv/read-csv content)
              ;; First row is header, skip it
              [header & rows] csv-data
              ;; Create games from rows
              games (for [row rows
                         :when (seq row)  ; Skip empty rows
                         :let [[name weight-str] row
                               weight (parse-int weight-str)]
                         :when (and (not (str/blank? name)) weight)]
                     {:id (str (UUID/randomUUID))
                      :name (str/trim name)
                      :weight weight})
              ;; Add all games
              _ (doseq [game games]
                  (state/add-game! s game))]
          (println "Imported" (count games) "games from CSV")
          (response/redirect "/data"))
        (catch Exception e
          (println "Error importing games CSV:" (.getMessage e))
          (.printStackTrace e)
          (response/response
            (views/data-management (str "Error importing games CSV: " (.getMessage e))))))
      (response/response
        (views/data-management "No file selected")))))

(defn import-players-csv [request]
  (let [s (get-state request)
        params (:params request)
        file (:file params)]
    (println "Import players CSV params:" params)
    (println "File:" file)
    (if file
      (try
        (let [content (slurp (:tempfile file))
              ;; Parse CSV
              csv-data (csv/read-csv content)
              ;; First row is header, skip it
              [header & rows] csv-data
              ;; Create players from rows
              players (for [row rows
                           :when (seq row)  ; Skip empty rows
                           :let [[name] row]
                           :when (not (str/blank? name))]
                       {:id (str (UUID/randomUUID))
                        :name (str/trim name)})
              ;; Add all players
              _ (doseq [player players]
                  (state/add-player! s player))]
          (println "Imported" (count players) "players from CSV")
          (response/redirect "/data"))
        (catch Exception e
          (println "Error importing players CSV:" (.getMessage e))
          (.printStackTrace e)
          (response/response
            (views/data-management (str "Error importing players CSV: " (.getMessage e))))))
      (response/response
        (views/data-management "No file selected")))))
