(ns kniziathon.views
  (:require [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [clojure.string :as str]
            [kniziathon.state :as state]
            [kniziathon.scoring :as scoring]))

(defn layout [title & content]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title title]
     [:link {:rel "stylesheet" :href "/css/pico.min.css"}]
     [:link {:rel "stylesheet" :href "/css/style.css"}]
     [:script {:src "/js/htmx.min.js"}]
     [:script {:src "/js/Sortable.min.js"}]]
    [:body
     [:nav {:class "container"}
      [:ul
       [:li [:strong [:a {:href "/"} "Kniziathon"]]]
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
       [:th {:class "numeric"} "Weight"]
       [:th {:class "actions"} "Actions"]]]
     [:tbody
      (for [game (sort-by :name games)]
        [:tr
         [:td [:a {:href (str "/games/" (:id game) "/plays")} (:name game)]]
         [:td {:class "numeric"} (:weight game)]
         [:td {:class "actions"}
          [:a {:href (str "/games/" (:id game) "/edit")} "Edit"]
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
        (form/text-field {:required true :type "number" :step "1" :min "1"}
                        "weight" (:weight game))
        [:button {:type "submit"} (if editing? "Update Game" "Create Game")]))))

(defn players-list [players leaderboard & [message]]
  (let [score-map (into {} (map (fn [p] [(:player-id p) p]) leaderboard))]
    (layout "Players"
      (when message [:p {:class "success"} message])
      [:h1 "Players"]
      [:div {:style "display: flex; gap: 1rem; margin-bottom: 1rem;"}
       [:a {:href "/players/new" :role "button"} "Add New Player"]
       [:a {:href "/players/merge" :role "button" :class "secondary"} "Merge Players"]]
      [:table
       [:thead
        [:tr
         [:th "Name"]
         [:th {:class "numeric"} "Games Played"]
         [:th {:class "numeric"} "Total Plays"]
         [:th {:class "numeric"} "Total Score"]
         [:th {:class "actions"} "Actions"]]]
       [:tbody
        (for [player (sort-by :name players)]
          (let [stats (get score-map (:id player))]
            [:tr
             [:td [:a {:href (str "/leaderboard/player/" (:id player))} (:name player)]]
             [:td {:class "numeric"} (or (:games-played stats) 0)]
             [:td {:class "numeric"} (or (:total-plays stats) 0)]
             [:td {:class "numeric"} (or (:total-score stats) 0)]
             [:td {:class "actions"}
              [:a {:href (str "/players/" (:id player) "/edit")} "Edit"]
              " "
              [:a {:href (str "/players/" (:id player) "/split")} "Split"]
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
        [:button {:type "submit"} (if editing? "Update Player" "Create Player")]))))

(defn play-header []
  [:tr
   [:th "Game"]
   [:th "Players"]
   [:th "Points"]
   [:th {:class "actions"} "Actions"]])

(defn play-row [play]
  (let [game (state/get-game (:game-id play))
        sorted-results (sort-by :rank (:player-results play))]
    [:tr
     [:td [:a {:href (str "/games/" (:game-id play) "/plays")} (:name game)]
      (str " (" (:weight game) ")")]
     [:td
      (for [pr sorted-results]
        (let [player (state/get-player (:player-id pr))]
          [:div
           (str (:rank pr) ". ")
           [:a {:href (str "/leaderboard/player/" (:player-id pr))} (:name player)]
           (when (:game-score pr) (str " (" (:game-score pr) ")"))]))]
     [:td
      (for [{:keys [rank]} sorted-results
            :let [player-count (count (:player-results play))]]
        [:div
         (str (scoring/calculate-play-score rank player-count (:weight game)) " pts")])]
     [:td {:class "actions"}
      [:a {:href (str "/plays/" (:id play) "/edit")} "Edit"]
      " "
      [:form {:method "post"
              :action (str "/plays/" (:id play) "/delete")
              :style "display: inline;"}
       [:button {:type "submit"
                 :class "delete-btn"
                 :onclick "return confirm('Delete this play? This cannot be undone.')"}
        "Delete"]]]]))

(defn plays-list [plays & [message]]
  (layout "Plays"
    (when message [:p {:class "success"} message])
    [:h1 "Plays"]
    [:a {:href "/plays/new" :role "button"} "Add New Play"]
    [:table
     [:thead (play-header)]
     [:tbody
      (for [play (reverse (sort-by :timestamp plays))]
        (play-row play))]]))

(defn- player-entry-row [i pr num-players players]
  [:div {:class "player-row"
         :data-player-id (:player-id pr)
         :style "padding: 0.75rem; margin-bottom: 0.5rem;"}
   [:div {:style "display: flex; align-items: center; gap: 1rem;"}
    [:div {:class "drag-handle"
           :style "cursor: grab; font-size: 1.2rem; color: #aaa; user-select: none; padding: 0 0.25rem;"}
     "⠿"]
    [:strong {:style "font-size: 1.1rem; min-width: 2rem;"} (str "#" (inc i))]
    [:div {:style "flex: 2; min-width: 200px;"}
     [:label {:for (str "player-" i "-id") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Player"]
     [:select {:name (str "player-" i "-id") :required true}
      [:option {:value ""} "-- Select --"]
      (for [p (sort-by :name players)]
        [:option {:value (:id p) :selected (= (:id p) (:player-id pr))}
         (:name p)])]]
    [:div {:style "flex: 1; min-width: 120px;"}
     [:label {:for (str "player-" i "-score") :style "margin-bottom: 0.25rem; font-size: 0.9rem;"} "Score"]
     [:input {:type "number"
              :name (str "player-" i "-score")
              :class "player-score-input"
              :value (:game-score pr)
              :placeholder "Optional"}]]
    (when (> num-players 1)
      [:button {:type "button"
               :hx-post "/htmx/plays/remove-player"
               :hx-include "[name^='player-'],[name='num-players'],[name='game-id']"
               :hx-target "#player-results"
               :hx-swap "outerHTML"
               :hx-vals (str "{\"remove-idx\": " i "}")
               :style "padding: 0.25rem 0.5rem; font-size: 0.75rem;"}
       "Remove"])
    [:input {:type "hidden" :name (str "player-" i "-idx") :value i}]
    [:input {:type "hidden" :name (str "player-" i "-rank") :value (inc i)}]]])

(defn player-results-fragment [player-results players]
  (let [num-players (count player-results)]
    [:div {:id "player-results"}
     [:input {:type "hidden" :name "num-players" :value num-players}]
     (for [i (range num-players)]
       (player-entry-row i (get player-results i) num-players players))
     (when (< num-players 6)
       [:select {:name "add-player-id"
                 :hx-post "/htmx/plays/add-player"
                 :hx-trigger "change"
                 :hx-include "[name^='player-'],[name='num-players']"
                 :hx-target "#player-results"
                 :hx-swap "outerHTML"
                 :style "margin-top: 0.5rem;"}
        [:option {:value ""} (if (zero? num-players) "-- Select first player --" "-- Add player --")]
        (for [p (sort-by :name players)]
          [:option {:value (:id p)} (:name p)])])
     (when (> num-players 1)
       [:script
        "(function() {
           var el = document.getElementById('player-results');
           Sortable.create(el, {
             animation: 150,
             handle: '.drag-handle',
             filter: 'select,input,button',
             preventOnFilter: false,
             onEnd: function() {
               var rows = el.querySelectorAll('.player-row');
               var values = {'num-players': rows.length};
               rows.forEach(function(row, i) {
                 values['player-' + i + '-id'] = row.querySelector('select').value;
                 values['player-' + i + '-score'] = row.querySelector('.player-score-input').value;
               });
               htmx.ajax('POST', '/htmx/plays/reorder-players', {
                 target: '#player-results',
                 swap: 'outerHTML',
                 values: values
               });
             }
           });
         })();"])]))

(defn play-form [play games players & [errors]]
  (let [editing? (and play (:id play))
        title (if editing? "Edit Play" "New Play")
        player-results (vec (or (:player-results play) []))]
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
           [:option {:value (:id g) :selected (= (:id g) (:game-id play))}
            (:name g)])]
        (player-results-fragment player-results players)
        [:button {:type "button"
                 :hx-post "/htmx/plays/rank-by-score"
                 :hx-include "[name^='player-'],[name='num-players']"
                 :hx-target "#player-results"
                 :hx-swap "outerHTML"}
         "Auto-rank by Score"]
        " "
        [:button {:type "submit"} (if editing? "Update Play" "Create Play")]])))

(defn leaderboard-table [leaderboard-data]
  [:div {:hx-get "/htmx/leaderboard"
         :hx-trigger "every 10s"
         :hx-swap "outerHTML"}
   [:table
    [:thead
     [:tr
      [:th {:class "rank-cell"} "Rank"]
      [:th "Player"]
      [:th {:class "numeric"} "Total Score"]
      [:th {:class "numeric"} "Games Played"]
      [:th {:class "numeric"} "Total Plays"]]]
    [:tbody
     (map-indexed
       (fn [idx player]
         [:tr
          [:td {:class "rank-cell"} (inc idx)]
          [:td [:a {:href (str "/leaderboard/player/" (:player-id player))}
                (:name player)]]
          [:td {:class "numeric"} (:total-score player)]
          [:td {:class "numeric"} (:games-played player)]
          [:td {:class "numeric"} (:total-plays player)]])
       leaderboard-data)]]])

(defn leaderboard [leaderboard-data multi-play?]
  (layout "Leaderboard"
    [:h1 "Kniziathon Leaderboard"]
    [:div {:class "scoring-mode-controls"
           :style "display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap;"}
     [:span {:style "color: #666;"}
      "Scoring: "
      [:strong (if multi-play? "Multi-play (all plays count)" "Standard (best per game)")]]
     [:form {:method "post" :action "/settings/toggle-scoring-mode" :style "margin: 0;"}
      [:button {:type "submit" :class "secondary" :style "padding: 0.25rem 0.75rem; font-size: 0.85rem;"}
       (if multi-play? "Switch to Standard" "Switch to Multi-play")]]]
    [:div
     [:button {:id "fullscreen-btn" :onclick "enterFullscreen()"}
      "Enter Fullscreen"]
     [:script "
       function enterFullscreen() {
         document.documentElement.requestFullscreen();
       }

       document.addEventListener('fullscreenchange', function() {
         var btn = document.getElementById('fullscreen-btn');
         if (document.fullscreenElement) {
           btn.style.display = 'none';
           document.body.classList.add('fullscreen-mode');
         } else {
           btn.style.display = 'block';
           document.body.classList.remove('fullscreen-mode');
         }
       });
     "]]
    (leaderboard-table leaderboard-data)))

(defn- game-plays-table [player detail players-map]
  [:table {:style "margin-top: 0.5rem; width: 100%;"}
   [:thead
    [:tr
     [:th "Date"]
     [:th {:class "numeric"} "Rank"]
     [:th {:class "numeric"} "Score"]
     [:th {:class "numeric"} "Points"]
     [:th "Others"]]]
   [:tbody
    (for [play (:plays detail)]
      (let [pr (first (filter #(= (:player-id %) (:id player)) (:player-results play)))
            others (sort-by :rank (remove #(= (:player-id %) (:id player)) (:player-results play)))
            pts (scoring/calculate-play-score (:rank pr) (count (:player-results play)) (:weight detail))]
        [:tr
         [:td (:timestamp play)]
         [:td {:class "numeric"} (:rank pr)]
         [:td {:class "numeric"} (or (:game-score pr) "—")]
         [:td {:class "numeric"} (str pts " pts")]
         [:td (str/join ", " (map #(let [p (get players-map (:player-id %))]
                                     (str (:name p) " (#" (:rank %) ")"))
                                  others))]]))]])

(defn player-detail [player details players-map]
  (layout (str (:name player) " - Details")
    [:h1 (:name player)]
    [:p [:strong "Total Score: "] (scoring/player-total-score (:id player))]
    [:p [:strong "Total Plays: "] (scoring/player-total-plays (:id player))]
    [:a {:href "/leaderboard"} "← Back to Leaderboard"]
    [:h2 "Game Breakdown"]
    (for [detail details]
      [:details {:style "border: 1px solid #ccc; border-radius: 4px; margin-bottom: 0.5rem; padding: 0.5rem 0.75rem;"}
       [:summary {:style "cursor: pointer; display: flex; gap: 2rem; align-items: baseline;"}
        [:span {:style "flex: 2; font-weight: bold;"}
         [:a {:href (str "/games/" (:game-id detail) "/plays")
              :onclick "event.stopPropagation()"}
          (:game-name detail)]]
        [:span (str "Weight: " (:weight detail))]
        [:span (str "Best: " (:best-score detail) " pts")]
        [:span (str "Rank: " (:rank detail))]
        [:span (str (:num-plays detail) " play" (when (not= 1 (:num-plays detail)) "s"))]]
       (game-plays-table player detail players-map)])))
(defn game-detail [game plays players]
  (layout (str (:name game) " - Plays")
    [:h1 (:name game)]
    [:p [:strong "Weight: "] (:weight game)]
    [:a {:href "/games"} "← Back to Games"]
    [:h2 "Plays"]
    (if (empty? plays)
      [:p "No plays recorded yet."]
      [:table
       [:thead
        [:tr
         [:th "Date"]
         [:th "Players"]
         [:th {:class "actions"} "Actions"]]]
       [:tbody
        (for [play (reverse (sort-by :timestamp plays))]
          (let [sorted-results (sort-by :rank (:player-results play))
                player-map (into {} (map (fn [p] [(:id p) p]) players))]
            [:tr
             [:td (:timestamp play)]
             [:td
              (for [pr sorted-results]
                (let [player (get player-map (:player-id pr))]
                  [:div
                   (str (:rank pr) ". ")
                   [:a {:href (str "/leaderboard/player/" (:player-id pr))} (:name player)]
                   (when (:game-score pr) (str " (" (:game-score pr) ")"))]))]
             [:td {:class "actions"}
              [:a {:href (str "/plays/" (:id play) "/edit")} "Edit"]
              " "
              [:form {:method "post"
                      :action (str "/plays/" (:id play) "/delete")
                      :style "display: inline;"}
               [:button {:type "submit"
                         :class "delete-btn"
                         :onclick "return confirm('Delete this play? This cannot be undone.')"}
                "Delete"]]]]))]])))

(defn split-player-form [player plays games-map players-map & [errors]]
  (layout (str "Split Player: " (:name player))
    [:h1 (str "Split Player: " (:name player))]
    [:p "Create a new player and select which plays to move to them. Unchecked plays stay with " (:name player) "."]
    (when errors
      [:div {:class "error"}
       [:ul (for [err errors] [:li err])]])
    [:form {:method "post" :action (str "/players/" (:id player) "/split")}
     [:label {:for "new-player-name"} "New Player Name"]
     [:input {:type "text" :name "new-player-name" :id "new-player-name" :required true
              :placeholder "Name for the new player"}]
     [:h2 "Plays"]
     (if (empty? plays)
       [:p "This player has no plays to split."]
       [:table
        [:thead
         [:tr
          [:th "Move to new player"]
          [:th "Game"]
          [:th "Date"]
          [:th "Result"]
          [:th "Other Players"]]]
        [:tbody
         (for [play (reverse (sort-by :timestamp plays))]
           (let [game (get games-map (:game-id play))
                 player-result (first (filter #(= (:player-id %) (:id player))
                                              (:player-results play)))
                 others (remove #(= (:player-id %) (:id player)) (:player-results play))]
             [:tr
              [:td {:style "text-align: center;"}
               [:input {:type "checkbox"
                        :name (str "move-play-" (:id play))
                        :value "true"}]]
              [:td [:a {:href (str "/games/" (:game-id play) "/plays")} (:name game)]]
              [:td (:timestamp play)]
              [:td (str "Rank " (:rank player-result) " of " (count (:player-results play))
                        (when (:game-score player-result)
                          (str " (score: " (:game-score player-result) ")")))]
              [:td (str/join ", " (map #(let [p (get players-map (:player-id %))]
                                          (str (:name p) " (#" (:rank %) ")"))
                                       (sort-by :rank others)))]]))]])
     [:button {:type "submit"} "Split Player"]]))

(defn merge-players-form [players leaderboard source-player target-player preview-data & [errors]]
  (let [score-map (into {} (map (fn [p] [(:player-id p) p]) leaderboard))]
    (layout "Merge Players"
      [:h1 "Merge Players"]
      [:p "Combine two duplicate players into one. All plays from the source player will be reassigned to the target player."]
      (when errors
        [:div {:class "error"}
         [:ul (for [err errors] [:li err])]])
      [:form {:method "post" :action "/players/merge"}
        [:label {:for "source-player-id"} "Source Player (will be deleted)"]
        [:select {:name "source-player-id" :id "source-player-id" :required true}
         [:option {:value ""} "-- Select Player to Remove --"]
         (for [p (sort-by :name players)]
           [:option {:value (:id p) :selected (= (:id p) (:id source-player))}
            (:name p)])]
        [:label {:for "target-player-id"} "Target Player (will be kept)"]
        [:select {:name "target-player-id" :id "target-player-id" :required true}
         [:option {:value ""} "-- Select Player to Keep --"]
         (for [p (sort-by :name players)]
           [:option {:value (:id p) :selected (= (:id p) (:id target-player))}
            (:name p)])]
        (if preview-data
          [:div
           [:h2 "Preview Merge"]
           [:div {:style "background: #f8f9fa; padding: 1rem; border-radius: 4px; margin: 1rem 0;"}
            [:h3 "Source Player (will be deleted)"]
            [:p [:strong "Name: "] (:source-name preview-data)]
            [:p [:strong "Games Played: "] (:source-games preview-data)]
            [:p [:strong "Total Score: "] (:source-score preview-data)]
            [:p [:strong "Plays: "] (:source-plays preview-data)]
            [:h3 {:style "margin-top: 1.5rem;"} "Target Player (will be kept)"]
            [:p [:strong "Name: "] (:target-name preview-data)]
            [:p [:strong "Games Played: "] (:target-games preview-data)]
            [:p [:strong "Total Score: "] (:target-score preview-data)]
            [:p [:strong "Current Plays: "] (:target-plays preview-data)]
            [:p [:strong "Plays After Merge: "] (+ (:source-plays preview-data) (:target-plays preview-data))]
            (when (:recalc-warning preview-data)
              [:p {:class "error"}
               [:strong "⚠ Note: "]
               "Scores will be recalculated after merge. The target player's total score may change if they now have better plays for games they've both played."])]
           [:input {:type "hidden" :name "confirm" :value "true"}]
           [:button {:type "submit" :class "delete-btn"} "Confirm Merge"]
           " "
           [:a {:href "/players/merge" :role "button" :class "secondary"} "Cancel"]]
          [:div
           [:button {:type "submit"} "Preview Merge"]
           " "
           [:a {:href "/players" :role "button" :class "secondary"} "Cancel"]])])))

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
         [:option {:value (:id g) :selected (= (:id g) (:id source-game))}
          (:name g)])]
      [:label {:for "target-game-id"} "Target Game (will be kept)"]
      [:select {:name "target-game-id" :id "target-game-id" :required true}
       [:option {:value ""} "-- Select Game to Keep --"]
       (for [g (sort-by :name games)]
         [:option {:value (:id g) :selected (= (:id g) (:id target-game))}
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
         [:a {:href "/games" :role "button" :class "secondary"} "Cancel"]])]))

(defn data-management [& [message]]
  (layout "Data Management"
    (when message [:p {:class "success"} message])
    [:h1 "Data Management"]
    [:h2 "Export Data"]
    [:p "Download all data as a JSON file."]
    (form/form-to [:post "/data/export"]
      [:button {:type "submit"} "Export All Data"])
    [:h2 "Import Data"]
    [:p "Upload a JSON file to import complete data (games, players, and plays)."]
    [:form {:method "post" :action "/data/import" :enctype "multipart/form-data"}
      [:label {:for "file"} "Select JSON file"]
      [:input {:type "file" :name "file" :id "file" :accept ".json" :required true}]
      [:fieldset
       [:legend "Import Mode"]
       [:label
        [:input {:type "radio" :name "mode" :value "replace" :checked true}]
        " Replace all data"]
       [:label
        [:input {:type "radio" :name "mode" :value "merge"}]
        " Merge data (keep existing)"]]
      [:button {:type "submit"} "Import JSON Data"]]
    [:h2 "Import Games from CSV"]
    [:p "Upload a CSV file with columns: " [:code "name,weight"]]
    [:p {:style "font-size: 0.9rem; color: #666;"}
     "Example: " [:code "Modern Art,2"] " (first row should be the header)"]
    [:form {:method "post" :action "/data/import-games-csv" :enctype "multipart/form-data"}
      [:label {:for "games-csv-file"} "Select CSV file"]
      [:input {:type "file" :name "file" :id "games-csv-file" :accept ".csv" :required true}]
      [:button {:type "submit"} "Import Games from CSV"]]
    [:h2 "Import Players from CSV"]
    [:p "Upload a CSV file with column: " [:code "name"]]
    [:p {:style "font-size: 0.9rem; color: #666;"}
     "Example: " [:code "Alice"] " (first row should be the header)"]
    [:form {:method "post" :action "/data/import-players-csv" :enctype "multipart/form-data"}
      [:label {:for "players-csv-file"} "Select CSV file"]
      [:input {:type "file" :name "file" :id "players-csv-file" :accept ".csv" :required true}]
      [:button {:type "submit"} "Import Players from CSV"]]
    [:h2 "Clear All Data"]
    [:p {:style "color: red;"} "WARNING: This will delete all games, players, and plays!"]
    (form/form-to [:post "/data/clear"]
      [:button {:type "submit"
               :class "delete-btn"
               :onclick "return confirm('Are you SURE you want to delete ALL data? This cannot be undone!')"}
       "Clear All Data"])))