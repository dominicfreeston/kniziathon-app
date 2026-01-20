(ns kniziathon.handlers
  (:require [ring.util.response :as response]
            [kniziathon.state :as state]
            [kniziathon.views :as views]
            [kniziathon.scoring :as scoring]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [hiccup.core :as hiccup])
  (:import [java.util UUID]
           [java.time Instant]))

;; Utility functions
(defn parse-double [s]
  (when (and s (not (str/blank? s)))
    (try (Double/parseDouble s)
         (catch Exception _ nil))))

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
        weight (parse-double (:weight params))
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
        weight (parse-double (:weight params))
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
  (response/response
    (views/leaderboard-table (scoring/leaderboard-data))))

(defn player-detail [id]
  (if-let [player (state/get-player id)]
    (response/response
      (views/player-detail player (scoring/player-game-details id)))
    (response/not-found "Player not found")))

;; Auto-rank handler - FIXED VERSION
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
                 [:div {:class "player-row"}
                  [:div {:style "display: flex; align-items: center; gap: 1rem;"}
                   [:div {:style "display: flex; flex-direction: column;"}
                    (when (> i 0)
                      [:button {:type "button"
                               :hx-post "/htmx/plays/move-player"
                               :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                               :hx-target "#player-results"
                               :hx-swap "outerHTML"
                               :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"up\"}")
                               :style "padding: 0.25rem 0.5rem; font-size: 0.8rem;"}
                       "↑"])
                    (when (< i (dec num-players))
                      [:button {:type "button"
                               :hx-post "/htmx/plays/move-player"
                               :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                               :hx-target "#player-results"
                               :hx-swap "outerHTML"
                               :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"down\"}")
                               :style "padding: 0.25rem 0.5rem; font-size: 0.8rem;"}
                       "↓"])]
                   [:div {:style "flex: 1;"}
                    [:h4 {:style "margin-top: 0;"} (str "Rank " (inc i) " - Player " (inc i))]
                    [:input {:type "hidden" :name (str "player-" i "-idx") :value i}]
                    [:input {:type "hidden" :name (str "player-" i "-rank") :value (inc i)}]
                    
                    [:label {:for (str "player-" i "-id")} "Player"]
                    [:select {:name (str "player-" i "-id") :required true}
                     [:option {:value ""} "-- Select Player --"]
                     (for [p (sort-by :name (state/get-all-players))]
                       [:option {:value (:id p)
                                :selected (= (:id p) (:player-id pr))}
                        (:name p)])]
                    
                    [:label {:for (str "player-" i "-score")} "Game Score (optional)"]
                    [:input {:type "number" 
                            :name (str "player-" i "-score")
                            :value (:game-score pr)}]]]]))])]
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
                 [:div {:class "player-row"}
                  [:div {:style "display: flex; align-items: center; gap: 1rem;"}
                   [:div {:style "display: flex; flex-direction: column;"}
                    (when (> i 0)
                      [:button {:type "button"
                               :hx-post "/htmx/plays/move-player"
                               :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                               :hx-target "#player-results"
                               :hx-swap "outerHTML"
                               :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"up\"}")
                               :style "padding: 0.25rem 0.5rem; font-size: 0.8rem;"}
                       "↑"])
                    (when (< i (dec num-players))
                      [:button {:type "button"
                               :hx-post "/htmx/plays/move-player"
                               :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
                               :hx-target "#player-results"
                               :hx-swap "outerHTML"
                               :hx-vals (str "{\"move-idx\": " i ", \"direction\": \"down\"}")
                               :style "padding: 0.25rem 0.5rem; font-size: 0.8rem;"}
                       "↓"])]
                   [:div {:style "flex: 1;"}
                    [:h4 {:style "margin-top: 0;"} (str "Rank " (inc i) " - Player " (inc i))]
                    [:input {:type "hidden" :name (str "player-" i "-idx") :value i}]
                    [:input {:type "hidden" :name (str "player-" i "-rank") :value (inc i)}]
                    
                    [:label {:for (str "player-" i "-id")} "Player"]
                    [:select {:name (str "player-" i "-id") :required true}
                     [:option {:value ""} "-- Select Player --"]
                     (for [p (sort-by :name (state/get-all-players))]
                       [:option {:value (:id p)
                                :selected (= (:id p) (:player-id pr))}
                        (:name p)])]
                    
                    [:label {:for (str "player-" i "-score")} "Game Score (optional)"]
                    [:input {:type "number" 
                            :name (str "player-" i "-score")
                            :value (:game-score pr)}]]]]))])]
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
        filename (str "kniziathon-" timestamp ".edn")]
    (-> (response/response (pr-str data))
        (response/content-type "application/edn")
        (response/header "Content-Disposition" 
                        (str "attachment; filename=\"" filename "\"")))))

(defn import-data [params]
  (let [file (get params "file")
        mode (get params "mode")
        replace? (= mode "replace")]
    (if file
      (try
        (let [content (slurp (:tempfile file))
              data (edn/read-string content)]
          (state/import-data! data replace?)
          (response/redirect "/data"))
        (catch Exception e
          (response/response
            (views/data-management (str "Error importing data: " (.getMessage e))))))
      (response/redirect "/data"))))

(defn clear-data []
  (state/clear-all-data!)
  (response/redirect "/data"))
