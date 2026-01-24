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

;; Games handlers
(defn games-list []
  (response/response
    (views/games-list (state/get-all-games))))

(defn new-game-form []
  (response/response
    (views/game-form nil)))

(defn create-game [params]
  (let [name (:name params)
        weight (parse-int (:weight params))
        errors (cond-> []
                 (str/blank? name) (conj "Name is required")
                 (not weight) (conj "Weight must be a valid number")
                 (and weight (<= weight 0)) (conj "Weight must be positive"))]
    (if (seq errors)
      (response/response (views/game-form params errors))
      (do
        (state/add-game! {:id (str (UUID/randomUUID))
                         :name name
                         :weight weight})
        (response/redirect "/games")))))

(defn edit-game-form [id]
  (if-let [game (state/get-game id)]
    (response/response (views/game-form game))
    (response/not-found "Game not found")))

(defn update-game [params]
  (let [id (:id params)
        name (:name params)
        weight (parse-int (:weight params))
        errors (cond-> []
                 (str/blank? name) (conj "Name is required")
                 (not weight) (conj "Weight must be a valid number")
                 (and weight (<= weight 0)) (conj "Weight must be positive"))]
    (if (seq errors)
      (response/response (views/game-form (state/get-game id) errors))
      (do
        (state/update-game! id {:name name :weight weight})
        (response/redirect "/games")))))

(defn delete-game [id]
  (state/delete-game! id)
  (response/redirect "/games"))

;; Game merge handlers
(defn merge-games-form-get []
  (response/response (views/merge-games-form (state/get-all-games) nil nil nil)))

(defn merge-games [params]
  (let [source-id (:source-game-id params)
        target-id (:target-game-id params)
        confirm (:confirm params)
        games (state/get-all-games)]
    (println "Merge params:" params)
    (println "Source:" source-id "Target:" target-id "Confirm:" confirm)
    
    (if (= confirm "true")
      ;; Perform the merge atomically
      (let [errors (cond-> []
                     (str/blank? source-id) (conj "Source game is required")
                     (str/blank? target-id) (conj "Target game is required")
                     (= source-id target-id) (conj "Cannot merge a game into itself")
                     (and source-id (not (state/get-game source-id))) (conj "Source game not found")
                     (and target-id (not (state/get-game target-id))) (conj "Target game not found"))]
        (if (seq errors)
          (response/response 
            (views/merge-games-form games nil nil nil errors))
          (do
            ;; Perform atomic merge
            (swap! state/app-state
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
        (let [source-game (state/get-game source-id)
              target-game (state/get-game target-id)
              all-plays (state/get-all-plays)
              source-plays (count (filter #(= (:game-id %) source-id) all-plays))
              target-plays (count (filter #(= (:game-id %) target-id) all-plays))
              weight-diff (when (and source-game target-game)
                           (Math/abs (- (:weight source-game) (:weight target-game))))
              weight-warning (and weight-diff (> weight-diff (* 0.1 (:weight target-game))))
              errors (cond-> []
                       (not source-game) (conj "Source game not found")
                       (not target-game) (conj "Target game not found")
                       (= source-id target-id) (conj "Cannot merge a game into itself"))]
          (if (seq errors)
            (response/response (views/merge-games-form games source-game target-game nil errors))
            (response/response 
              (views/merge-games-form 
                games 
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
        (response/response (views/merge-games-form games nil nil nil))))))

;; Players handlers
(defn players-list []
  (response/response
    (views/players-list (state/get-all-players) (scoring/leaderboard-data))))

(defn new-player-form []
  (response/response
    (views/player-form nil)))

(defn create-player [params]
  (let [name (:name params)
        errors (when (str/blank? name) ["Name is required"])]
    (if errors
      (response/response (views/player-form params errors))
      (do
        (state/add-player! {:id (str (UUID/randomUUID))
                           :name name})
        (response/redirect "/players")))))

(defn edit-player-form [id]
  (if-let [player (state/get-player id)]
    (response/response (views/player-form player))
    (response/not-found "Player not found")))

(defn update-player [params]
  (let [id (:id params)
        name (:name params)
        errors (when (str/blank? name) ["Name is required"])]
    (if errors
      (response/response (views/player-form (state/get-player id) errors))
      (do
        (state/update-player! id {:name name})
        (response/redirect "/players")))))

(defn delete-player [id]
  (state/delete-player! id)
  (response/redirect "/players"))

;; Player merge handlers
(defn merge-players-form-get []
  (response/response 
    (views/merge-players-form (state/get-all-players) 
                             (scoring/leaderboard-data) 
                             nil nil nil)))

(defn merge-players [params]
  (let [source-id (:source-player-id params)
        target-id (:target-player-id params)
        confirm (:confirm params)
        players (state/get-all-players)
        leaderboard (scoring/leaderboard-data)]
    (println "Merge players params:" params)
    (println "Source:" source-id "Target:" target-id "Confirm:" confirm)
    
    (if (= confirm "true")
      ;; Perform the merge atomically
      (let [errors (cond-> []
                     (str/blank? source-id) (conj "Source player is required")
                     (str/blank? target-id) (conj "Target player is required")
                     (= source-id target-id) (conj "Cannot merge a player into itself")
                     (and source-id (not (state/get-player source-id))) (conj "Source player not found")
                     (and target-id (not (state/get-player target-id))) (conj "Target player not found"))]
        (if (seq errors)
          (response/response 
            (views/merge-players-form players leaderboard nil nil nil errors))
          (do
            ;; Perform atomic merge
            (swap! state/app-state
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
        (let [source-player (state/get-player source-id)
              target-player (state/get-player target-id)
              all-plays (state/get-all-plays)
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

(defn validate-play [game-id player-results]
  (let [player-ids (map :player-id player-results)
        ranks (map :rank player-results)
        num-players (count player-results)]
    (cond-> []
      (not game-id) (conj "Game is required")
      (str/blank? game-id) (conj "Game is required")
      (and game-id (not (str/blank? game-id)) (not (state/get-game game-id))) 
        (conj "Invalid game selected")
      (< num-players 2) (conj "At least 2 players required")
      (some str/blank? player-ids) (conj "All player slots must be filled")
      (not= (count player-ids) (count (set player-ids))) (conj "Duplicate players selected")
      (some nil? ranks) (conj "All ranks must be filled")
      (and (not-any? nil? ranks)
           (not= (sort ranks) (range 1 (inc num-players)))) 
        (conj (str "Ranks must be consecutive from 1 to " num-players)))))

(defn plays-list []
  (response/response
    (views/plays-list (state/get-all-plays))))

(defn new-play-form [params]
  (let [num-players (or (parse-int (:num-players params)) 4)
        game-id (:game-id params)
        ;; Parse existing player data from params
        existing-results (parse-player-results params)
        existing-count (count existing-results)
        ;; Adjust player results based on new count
        player-results (cond
                         ;; Increasing players: keep existing, add empty slots
                         (> num-players existing-count)
                         (vec (concat existing-results 
                                     (repeat (- num-players existing-count) {})))
                         ;; Decreasing players: truncate to new count
                         (< num-players existing-count)
                         (vec (take num-players existing-results))
                         ;; Same count: keep as is
                         :else
                         existing-results)]
    (response/response
      (views/play-form {:game-id game-id :player-results player-results} 
                      (state/get-all-games) 
                      (state/get-all-players)))))

(defn create-play [params]
  (let [game-id (:game-id params)
        player-results (parse-player-results params)
        errors (validate-play game-id player-results)]
    (println "Create play params:" params)
    (println "Game ID:" game-id)
    (println "Player results:" player-results)
    (println "Errors:" errors)
    (if (seq errors)
      (response/response
        (views/play-form {:game-id game-id :player-results player-results}
                        (state/get-all-games)
                        (state/get-all-players)
                        errors))
      (do
        (state/add-play! {:id (str (UUID/randomUUID))
                         :game-id game-id
                         :timestamp (now-timestamp)
                         :player-results player-results})
        (response/redirect "/plays")))))

(defn edit-play-form [id params]
  (if-let [play (state/get-play id)]
    (let [;; If params has num-players, we're adjusting the count
          num-players (parse-int (:num-players params))
          existing-results (if num-players
                            (parse-player-results params)
                            (:player-results play))
          existing-count (count existing-results)
          ;; Adjust player results if num-players was provided
          player-results (if num-players
                          (cond
                            ;; Increasing players: keep existing, add empty slots
                            (> num-players existing-count)
                            (vec (concat existing-results 
                                        (repeat (- num-players existing-count) {})))
                            ;; Decreasing players: truncate to new count
                            (< num-players existing-count)
                            (vec (take num-players existing-results))
                            ;; Same count: keep as is
                            :else
                            existing-results)
                          existing-results)]
      (response/response
        (views/play-form (assoc play :player-results player-results)
                        (state/get-all-games) 
                        (state/get-all-players))))
    (response/not-found "Play not found")))

(defn update-play [params]
  (let [id (:id params)
        game-id (:game-id params)
        player-results (parse-player-results params)
        errors (validate-play game-id player-results)]
    (if (seq errors)
      (response/response
        (views/play-form (assoc (state/get-play id)
                               :game-id game-id
                               :player-results player-results)
                        (state/get-all-games)
                        (state/get-all-players)
                        errors))
      (do
        (state/update-play! id {:game-id game-id
                               :player-results player-results})
        (response/redirect "/plays")))))

(defn delete-play [id]
  (state/delete-play! id)
  (response/redirect "/plays"))

;; Leaderboard handlers
(defn leaderboard []
  (response/response
    (views/leaderboard (scoring/leaderboard-data))))

(defn leaderboard-fragment []
  (-> (hiccup/html (views/leaderboard-table (scoring/leaderboard-data)))
      (response/response)
      (response/content-type "text/html")))

(defn player-detail [id]
  (if-let [player (state/get-player id)]
    (response/response
      (views/player-detail player (scoring/player-game-details id)))
    (response/not-found "Player not found")))

;; Auto-rank handler
(defn auto-rank-by-score [params]
  (let [player-results (parse-player-results params)
        ranked (scoring/auto-rank-by-scores player-results)
        num-players (count ranked)]
    ;; Generate HTML string from Hiccup
    (let [html-string 
          (hiccup/html
            [:div {:id "player-results"}
             (for [i (range num-players)]
               (let [pr (get ranked i)]
                 [:div {:class "player-row" :style "padding: 0.75rem; margin-bottom: 0.5rem;"}
                  [:div {:style "display: flex; align-items: center; gap: 1rem;"}
                   [:div {:style "display: flex; flex-direction: column; align-items: center; min-width: 50px;"}
                    [:strong {:style "font-size: 1.2rem; margin-bottom: 0.25rem;"} (str "#" (inc i))]
                    [:div {:style "display: flex; gap: 0.25rem;"}
                     (when (> i 0)
                       [:button {:type "button"
                                :hx-post "/htmx/plays/move-player"
                                :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                                :hx-target "#player-results"
                                :hx-swap "outerHTML"
                                :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"up\"}")
                                :style "padding: 0.25rem 0.5rem; font-size: 0.75rem;"}
                        "↑"])
                     (when (< i (dec num-players))
                       [:button {:type "button"
                                :hx-post "/htmx/plays/move-player"
                                :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                                :hx-target "#player-results"
                                :hx-swap "outerHTML"
                                :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"down\"}")
                                :style "padding: 0.25rem 0.5rem; font-size: 0.75rem;"}
                        "↓"])]]
                   [:div {:style "flex: 2; min-width: 200px;"}
                    [:label {:for (str "player-" i "-id") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Player"]
                    [:select {:name (str "player-" i "-id") :required true}
                     [:option {:value ""} "-- Select --"]
                     (for [p (sort-by :name (state/get-all-players))]
                       [:option {:value (:id p)
                                :selected (= (:id p) (:player-id pr))}
                        (:name p)])]]
                   [:div {:style "flex: 1; min-width: 120px;"}
                    [:label {:for (str "player-" i "-score") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Score"]
                    [:input {:type "number" 
                            :name (str "player-" i "-score")
                            :value (:game-score pr)
                            :placeholder "Optional"}]]
                   [:input {:type "hidden" :name (str "player-" i "-idx") :value i}]
                   [:input {:type "hidden" :name (str "player-" i "-rank") :value (inc i)}]]]))])]
      ;; Return HTML string as HTTP response
      (-> (response/response html-string)
          (response/content-type "text/html")))))

;; Move player handler
(defn move-player [params]
  (let [player-results (parse-player-results params)
        move-idx (parse-int (:move-idx params))
        direction (:direction params)
        num-players (count player-results)
        ;; Swap players based on direction
        swapped (if (= direction "up")
                  (let [temp (get player-results move-idx)
                        other (get player-results (dec move-idx))]
                    (-> player-results
                        (assoc move-idx other)
                        (assoc (dec move-idx) temp)))
                  (let [temp (get player-results move-idx)
                        other (get player-results (inc move-idx))]
                    (-> player-results
                        (assoc move-idx other)
                        (assoc (inc move-idx) temp))))]
    ;; Generate HTML string from Hiccup
    (let [html-string 
          (hiccup/html
            [:div {:id "player-results"}
             (for [i (range num-players)]
               (let [pr (get swapped i)]
                 [:div {:class "player-row" :style "padding: 0.75rem; margin-bottom: 0.5rem;"}
                  [:div {:style "display: flex; align-items: center; gap: 1rem;"}
                   [:div {:style "display: flex; flex-direction: column; align-items: center; min-width: 50px;"}
                    [:strong {:style "font-size: 1.2rem; margin-bottom: 0.25rem;"} (str "#" (inc i))]
                    [:div {:style "display: flex; gap: 0.25rem;"}
                     (when (> i 0)
                       [:button {:type "button"
                                :hx-post "/htmx/plays/move-player"
                                :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                                :hx-target "#player-results"
                                :hx-swap "outerHTML"
                                :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"up\"}")
                                :style "padding: 0.25rem 0.5rem; font-size: 0.75rem;"}
                        "↑"])
                     (when (< i (dec num-players))
                       [:button {:type "button"
                                :hx-post "/htmx/plays/move-player"
                                :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                                :hx-target "#player-results"
                                :hx-swap "outerHTML"
                                :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"down\"}")
                                :style "padding: 0.25rem 0.5rem; font-size: 0.75rem;"}
                        "↓"])]]
                   [:div {:style "flex: 2; min-width: 200px;"}
                    [:label {:for (str "player-" i "-id") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Player"]
                    [:select {:name (str "player-" i "-id") :required true}
                     [:option {:value ""} "-- Select --"]
                     (for [p (sort-by :name (state/get-all-players))]
                       [:option {:value (:id p)
                                :selected (= (:id p) (:player-id pr))}
                        (:name p)])]]
                   [:div {:style "flex: 1; min-width: 120px;"}
                    [:label {:for (str "player-" i "-score") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Score"]
                    [:input {:type "number" 
                            :name (str "player-" i "-score")
                            :value (:game-score pr)
                            :placeholder "Optional"}]]
                   [:input {:type "hidden" :name (str "player-" i "-idx") :value i}]
                   [:input {:type "hidden" :name (str "player-" i "-rank") :value (inc i)}]]]))])]
      ;; Return HTML string as HTTP response
      (-> (response/response html-string)
          (response/content-type "text/html")))))

;; Data management handlers
(defn data-management []
  (response/response
    (views/data-management)))

(defn export-data []
  (let [data @state/app-state
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

(defn import-data [params]
  (let [file (:file params)
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
          (state/import-data! data replace?)
          (response/redirect "/data"))
        (catch Exception e
          (println "Error importing:" (.getMessage e))
          (.printStackTrace e)
          (response/response
            (views/data-management (str "Error importing data: " (.getMessage e))))))
      (response/response
        (views/data-management "No file selected")))))

(defn clear-data []
  (state/clear-all-data!)
  (response/redirect "/data"))

(defn import-games-csv [params]
  (let [file (:file params)]
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
                  (state/add-game! game))]
          (println "Imported" (count games) "games from CSV")
          (response/redirect "/data"))
        (catch Exception e
          (println "Error importing games CSV:" (.getMessage e))
          (.printStackTrace e)
          (response/response
            (views/data-management (str "Error importing games CSV: " (.getMessage e))))))
      (response/response
        (views/data-management "No file selected")))))

(defn import-players-csv [params]
  (let [file (:file params)]
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
                  (state/add-player! player))]
          (println "Imported" (count players) "players from CSV")
          (response/redirect "/data"))
        (catch Exception e
          (println "Error importing players CSV:" (.getMessage e))
          (.printStackTrace e)
          (response/response
            (views/data-management (str "Error importing players CSV: " (.getMessage e))))))
      (response/response
        (views/data-management "No file selected")))))
