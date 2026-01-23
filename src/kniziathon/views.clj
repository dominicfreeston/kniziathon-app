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
       .player-row { 
         margin-bottom: 0.5rem; 
         padding: 0.75rem; 
         border: 1px solid #dee2e6; 
         border-radius: 4px; 
         background: #fff;
       }
       .player-row label {
         display: block;
         margin-bottom: 0.25rem;
         font-size: 0.9rem;
         font-weight: 500;
       }
       .player-row select,
       .player-row input[type='number'] {
         width: 100%;
         margin-bottom: 0;
       }
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
    [:div {:style "display: flex; gap: 1rem; margin-bottom: 1rem;"}
     [:a {:href "/games/new" :role "button"} "Add New Game"]
     [:a {:href "/games/merge" :role "button" :class "secondary"} "Merge Games"]]
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
          [:form {:method "post" 
                  :action (str "/games/" (:id game) "/delete")
                  :style "display: inline;"}
           [:button {:type "submit"
                    :class "delete-btn"
                    :onclick (str "return confirm('Delete " (:name game) "? This cannot be undone.')")}
            "Delete"]]]])]]))

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
              [:form {:method "post" 
                      :action (str "/players/" (:id player) "/delete")
                      :style "display: inline;"}
               [:button {:type "submit"
                        :class "delete-btn"
                        :onclick (str "return confirm('Delete " (:name player) "? This cannot be undone.')")}
                "Delete"]]]]))]])))

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
            [:form {:method "post" 
                    :action (str "/plays/" (:id play) "/delete")
                    :style "display: inline;"}
             [:button {:type "submit"
                      :class "delete-btn"
                      :onclick "return confirm('Delete this play? This cannot be undone.')"}
              "Delete"]]]]))]]))

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
                 :hx-get (if editing? 
                          (str "/plays/" (:id play) "/edit")
                          "/plays/new")
                 :hx-target "body"
                 :hx-include "[name='game-id'],[name^='player-']"}
         (for [n [2 3 4 5 6]]
           [:option {:value n :selected (= n num-players)}
            (str n)])]
        
        [:div {:id "player-results"}
         (for [i (range num-players)]
           (let [pr (get (:player-results play) i)]
             [:div {:class "player-row" :key i :style "padding: 0.75rem; margin-bottom: 0.5rem;"}
              [:div {:style "display: flex; align-items: center; gap: 1rem;"}
               ;; Rank number and up/down controls
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
               
               ;; Player selection
               [:div {:style "flex: 2; min-width: 200px;"}
                [:label {:for (str "player-" i "-id") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Player"]
                [:select {:name (str "player-" i "-id") :required true}
                 [:option {:value ""} "-- Select --"]
                 (for [p (sort-by :name players)]
                   [:option {:value (:id p)
                            :selected (= (:id p) (:player-id pr))}
                    (:name p)])]]
               
               ;; Game score
               [:div {:style "flex: 1; min-width: 120px;"}
                [:label {:for (str "player-" i "-score") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Score"]
                [:input {:type "number" 
                        :name (str "player-" i "-score")
                        :value (:game-score pr)
                        :placeholder "Optional"}]]
               
               ;; Hidden fields
               [:input {:type "hidden" :name (str "player-" i "-idx") :value i}]
               [:input {:type "hidden" :name (str "player-" i "-rank") :value (inc i)}]]]))]
        
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

(defn merge-games-form [games source-game target-game preview-data & [errors]]
  (layout "Merge Games"
    [:h1 "Merge Games"]
    [:p "Combine two duplicate games into one. All plays from the source game will be reassigned to the target game."]
    
    (when errors
      [:div {:class "error"}
       [:ul (for [err errors] [:li err])]])
    
    [:form {:method "post" :action "/games/merge"}
      [:label {:for "source-game-id"} "Source Game (will be deleted)"]
      [:select {:name "source-game-id" :id "source-game-id" :required true}
       [:option {:value ""} "-- Select Game to Remove --"]
       (for [g (sort-by :name games)]
         [:option {:value (:id g)
                  :selected (= (:id g) (:id source-game))}
          (:name g)])]
      
      [:label {:for "target-game-id"} "Target Game (will be kept)"]
      [:select {:name "target-game-id" :id "target-game-id" :required true}
       [:option {:value ""} "-- Select Game to Keep --"]
       (for [g (sort-by :name games)]
         [:option {:value (:id g)
                  :selected (= (:id g) (:id target-game))}
          (:name g)])]
      
      (if preview-data
        [:div
         [:h2 "Preview Merge"]
         [:div {:style "background: #f8f9fa; padding: 1rem; border-radius: 4px; margin: 1rem 0;"}
          [:h3 "Source Game (will be deleted)"]
          [:p [:strong "Name: "] (:source-name preview-data)]
          [:p [:strong "Weight: "] (:source-weight preview-data)]
          [:p [:strong "Plays: "] (:source-plays preview-data)]
          
          [:h3 {:style "margin-top: 1.5rem;"} "Target Game (will be kept)"]
          [:p [:strong "Name: "] (:target-name preview-data)]
          [:p [:strong "Weight: "] (:target-weight preview-data)]
          [:p [:strong "Current Plays: "] (:target-plays preview-data)]
          [:p [:strong "Plays After Merge: "] (+ (:source-plays preview-data) (:target-plays preview-data))]
          
          (when (:weight-warning preview-data)
            [:p {:class "error"} 
             [:strong "⚠ Warning: "] 
             "Game weights differ! Source: " (:source-weight preview-data) 
             ", Target: " (:target-weight preview-data)])]
         
         [:input {:type "hidden" :name "confirm" :value "true"}]
         [:button {:type "submit" :class "delete-btn"} "Confirm Merge"]
         " "
         [:a {:href "/games/merge" :role "button" :class "secondary"} "Cancel"]]
        
        [:div
         [:button {:type "submit"} "Preview Merge"]
         " "
         [:a {:href "/games" :role "button" :class "secondary"} "Cancel"]])]
    ))

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
    [:form {:method "post" :action "/data/import" :enctype "multipart/form-data"}
      [:label {:for "file"} "Select EDN file"]
      [:input {:type "file" :name "file" :id "file" :accept ".edn" :required true}]
      
      [:fieldset
       [:legend "Import Mode"]
       [:label
        [:input {:type "radio" :name "mode" :value "replace" :checked true}]
        " Replace all data"]
       [:label
        [:input {:type "radio" :name "mode" :value "merge"}]
        " Merge data (keep existing)"]]
      
      [:button {:type "submit"} "Import Data"]]
    
    [:h2 "Clear All Data"]
    [:p {:style "color: red;"} "WARNING: This will delete all games, players, and plays!"]
    (form/form-to [:post "/data/clear"]
      [:button {:type "submit"
               :class "delete-btn"
               :onclick "return confirm('Are you SURE you want to delete ALL data? This cannot be undone!')"}
       "Clear All Data"])))
