(ns kniziathon.views
  (:require [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [kniziathon.state :as state]
            [kniziathon.scoring :as scoring]))

(defn layout [title & content]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title title]
     [:link {:rel "stylesheet" :href "/css/pico.min.css"}]
     [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
     [:style "
       .fullscreen-mode nav { display: none; }
       .fullscreen-mode main { margin-top: 0; }
       table { width: 100%; }
       .actions { white-space: nowrap; }
       .player-row { margin-bottom: 1rem; padding: 1rem; border: 1px solid #ccc; border-radius: 4px; }
       .error { color: red; margin-bottom: 1rem; }
       .success { color: green; margin-bottom: 1rem; }
       .rank-cell { text-align: center; font-weight: bold; }
       .delete-btn { background-color: #d32f2f; }
       .small-input { max-width: 100px; }
     "]]
    [:body
     [:nav {:class "container"}
      [:ul
       [:li [:strong [:a {:href "/"} "Kniziathon Tracker"]]]
       [:li [:a {:href "/games"} "Games"]]
       [:li [:a {:href "/players"} "Players"]]
       [:li [:a {:href "/plays"} "Plays"]]
       [:li [:a {:href "/leaderboard"} "Leaderboard"]]
       [:li [:a {:href "/data"} "Data"]]]]
     [:main {:class "container"}
      content]]))

(defn games-list [games & [message]]
  (layout "Games"
    (when message [:p {:class "success"} message])
    [:h1 "Games"]
    [:a {:href "/games/new" :role "button"} "Add New Game"]
    [:table
     [:thead
      [:tr
       [:th "Name"]
       [:th "Weight"]
       [:th {:class "actions"} "Actions"]]]
     [:tbody
      (for [game (sort-by :name games)]
        [:tr
         [:td (:name game)]
         [:td (:weight game)]
         [:td {:class "actions"}
          [:a {:href (str "/games/" (:id game) "/edit")} "Edit"]
          " "
          [:button {:hx-post (str "/games/" (:id game) "/delete")
                   :hx-confirm (str "Delete " (:name game) "? This cannot be undone.")
                   :class "delete-btn"}
           "Delete"]]])]]))

(defn game-form [game & [errors]]
  (let [editing? (some? game)
        title (if editing? "Edit Game" "New Game")]
    (layout title
      [:h1 title]
      (when errors
        [:div {:class "error"}
         [:ul (for [err errors] [:li err])]])
      (form/form-to [:post (if editing? (str "/games/" (:id game)) "/games")]
        [:label {:for "name"} "Game Name"]
        (form/text-field {:required true} "name" (:name game))
        
        [:label {:for "weight"} "Weight (hours)"]
        (form/text-field {:required true :type "number" :step "0.1" :min "0.1"}
                        "weight" (:weight game))
        
        [:button {:type "submit"} (if editing? "Update Game" "Create Game")]
        " "
        [:a {:href "/games" :role "button" :class "secondary"} "Cancel"]))))

(defn players-list [players leaderboard & [message]]
  (let [score-map (into {} (map (fn [p] [(:player-id p) p]) leaderboard))]
    (layout "Players"
      (when message [:p {:class "success"} message])
      [:h1 "Players"]
      [:a {:href "/players/new" :role "button"} "Add New Player"]
      [:table
       [:thead
        [:tr
         [:th "Name"]
         [:th "Games Played"]
         [:th "Total Score"]
         [:th {:class "actions"} "Actions"]]]
       [:tbody
        (for [player (sort-by :name players)]
          (let [stats (get score-map (:id player))]
            [:tr
             [:td (:name player)]
             [:td (or (:games-played stats) 0)]
             [:td (or (:total-score stats) 0)]
             [:td {:class "actions"}
              [:a {:href (str "/players/" (:id player) "/edit")} "Edit"]
              " "
              [:button {:hx-post (str "/players/" (:id player) "/delete")
                       :hx-confirm (str "Delete " (:name player) "? This cannot be undone.")
                       :class "delete-btn"}
               "Delete"]]]))]])))

(defn player-form [player & [errors]]
  (let [editing? (some? player)
        title (if editing? "Edit Player" "New Player")]
    (layout title
      [:h1 title]
      (when errors
        [:div {:class "error"}
         [:ul (for [err errors] [:li err])]])
      (form/form-to [:post (if editing? (str "/players/" (:id player)) "/players")]
        [:label {:for "name"} "Player Name"]
        (form/text-field {:required true} "name" (:name player))
        
        [:button {:type "submit"} (if editing? "Update Player" "Create Player")]
        " "
        [:a {:href "/players" :role "button" :class "secondary"} "Cancel"]))))

(defn plays-list [plays & [message]]
  (layout "Plays"
    (when message [:p {:class "success"} message])
    [:h1 "Plays"]
    [:a {:href "/plays/new" :role "button"} "Add New Play"]
    [:table
     [:thead
      [:tr
       [:th "Game"]
       [:th "Date/Time"]
       [:th "Players & Results"]
       [:th {:class "actions"} "Actions"]]]
     [:tbody
      (for [play (take 50 (reverse (sort-by :timestamp plays)))]
        (let [game (state/get-game (:game-id play))]
          [:tr
           [:td (:name game)]
           [:td (:timestamp play)]
           [:td
            (for [pr (sort-by :rank (:player-results play))]
              (let [player (state/get-player (:player-id pr))]
                [:div
                 (str (:rank pr) ". " (:name player))
                 (when (:game-score pr) (str " (Score: " (:game-score pr) ")"))]))]
           [:td {:class "actions"}
            [:a {:href (str "/plays/" (:id play) "/edit")} "Edit"]
            " "
            [:button {:hx-post (str "/plays/" (:id play) "/delete")
                     :hx-confirm "Delete this play? This cannot be undone."
                     :class "delete-btn"}
             "Delete"]]]))]]))

(defn play-form [play games players & [errors]]
  (let [editing? (and play (:id play))
        title (if editing? "Edit Play" "New Play")
        num-players (or (count (:player-results play)) 4)]
    (layout title
      [:h1 title]
      (when errors
        [:div {:class "error"}
         [:ul (for [err errors] [:li err])]])
      [:form {:method "post" :action (if editing? (str "/plays/" (:id play)) "/plays")}
        (when editing?
          [:input {:type "hidden" :name "id" :value (:id play)}])
        [:label {:for "game-id"} "Game"]
        [:select {:name "game-id" :id "game-id" :required true}
         [:option {:value ""} "-- Select Game --"]
         (for [g (sort-by :name games)]
           [:option {:value (:id g)
                    :selected (= (:id g) (:game-id play))}
            (:name g)])]
        
        [:label {:for "num-players"} "Number of Players"]
        [:select {:name "num-players" 
                 :id "num-players"
                 :hx-get "/plays/new"
                 :hx-target "body"
                 :hx-include "[name='game-id']"}
         (for [n [2 3 4 5 6]]
           [:option {:value n :selected (= n num-players)}
            (str n)])]
        
        [:div {:id "player-results"}
         (for [i (range num-players)]
           (let [pr (get (:player-results play) i)]
             [:div {:class "player-row" :key i}
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
                 (for [p (sort-by :name players)]
                   [:option {:value (:id p)
                            :selected (= (:id p) (:player-id pr))}
                    (:name p)])]
                
                [:label {:for (str "player-" i "-score")} "Game Score (optional)"]
                [:input {:type "number" 
                        :name (str "player-" i "-score")
                        :value (:game-score pr)}]]]]))]
        
        [:button {:type "button"
                 :hx-post "/htmx/plays/rank-by-score"
                 :hx-include "[name^='player-'],[name='num-players']"
                 :hx-target "#player-results"
                 :hx-swap "outerHTML"}
         "Auto-rank by Score"]
        " "
        [:button {:type "submit"} (if editing? "Update Play" "Create Play")]
        " "
        [:a {:href "/plays" :role "button" :class "secondary"} "Cancel"]])))

(defn leaderboard-table [leaderboard-data]
  [:div {:hx-get "/htmx/leaderboard"
         :hx-trigger "every 10s"
         :hx-swap "outerHTML"}
   [:table
    [:thead
     [:tr
      [:th {:class "rank-cell"} "Rank"]
      [:th "Player"]
      [:th "Total Score"]
      [:th "Games Played"]]]
    [:tbody
     (map-indexed
       (fn [idx player]
         [:tr
          [:td {:class "rank-cell"} (inc idx)]
          [:td [:a {:href (str "/leaderboard/player/" (:player-id player))}
                (:name player)]]
          [:td (:total-score player)]
          [:td (:games-played player)]])
       leaderboard-data)]]])

(defn leaderboard [leaderboard-data]
  (layout "Leaderboard"
    [:h1 "Kniziathon Leaderboard"]
    [:div
     [:button {:onclick "document.body.classList.toggle('fullscreen-mode')"} 
      "Toggle Fullscreen"]]
    (leaderboard-table leaderboard-data)))

(defn player-detail [player details]
  (layout (str (:name player) " - Details")
    [:h1 (:name player)]
    [:p [:strong "Total Score: "] (scoring/player-total-score (:id player))]
    [:a {:href "/leaderboard"} "← Back to Leaderboard"]
    [:h2 "Game Breakdown"]
    [:table
     [:thead
      [:tr
       [:th "Game"]
       [:th "Weight"]
       [:th "Best Score"]
       [:th "Rank"]
       [:th "Date"]]]
     [:tbody
      (for [detail details]
        [:tr
         [:td (:game-name detail)]
         [:td (:weight detail)]
         [:td (:best-score detail)]
         [:td (:rank detail)]
         [:td (:timestamp detail)]])]]))

(defn data-management [& [message]]
  (layout "Data Management"
    (when message [:p {:class "success"} message])
    [:h1 "Data Management"]
    
    [:h2 "Export Data"]
    [:p "Download all data as an EDN file."]
    (form/form-to [:post "/data/export"]
      [:button {:type "submit"} "Export All Data"])
    
    [:h2 "Import Data"]
    [:p "Upload an EDN file to import data."]
    (form/form-to {:enctype "multipart/form-data"} [:post "/data/import"]
      [:label {:for "file"} "Select EDN file"]
      (form/file-upload "file")
      
      [:fieldset
       [:label
        (form/radio-button "mode" false "replace")
        " Replace all data"]
       [:label
        (form/radio-button "mode" true "merge")
        " Merge data (keep existing)"]]
      
      [:button {:type "submit"} "Import Data"])
    
    [:h2 "Clear All Data"]
    [:p {:style "color: red;"} "WARNING: This will delete all games, players, and plays!"]
    (form/form-to [:post "/data/clear"]
      [:button {:type "submit"
               :class "delete-btn"
               :hx-confirm "Are you SURE you want to delete ALL data? This cannot be undone!"}
       "Clear All Data"])))
