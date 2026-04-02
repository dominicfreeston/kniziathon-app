(ns kniziathon.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as response]
            [kniziathon.state :as state]
            [kniziathon.handlers :as handlers])
  (:import [java.awt Desktop]
           [java.net URI])
  (:gen-class))

(defroutes app-routes
  ;; Home
  (GET "/" [] (response/redirect "/leaderboard"))

  ;; Games
  (GET "/games" request (handlers/games-list request))
  (GET "/games/merge" request (handlers/merge-games-form-get request))
  (POST "/games/merge" request (handlers/merge-games request))
  (GET "/games/new" request (handlers/new-game-form request))
  (POST "/games" request (handlers/create-game request))
  (GET "/games/:id/plays" [id :as request] (handlers/game-detail request id))
  (GET "/games/:id/edit" [id :as request] (handlers/edit-game-form request id))
  (POST "/games/:id" [id :as request] (handlers/update-game (assoc-in request [:params :id] id)))
  (POST "/games/:id/delete" [id :as request] (handlers/delete-game request id))

  ;; Players
  (GET "/players" request (handlers/players-list request))
  (GET "/players/merge" request (handlers/merge-players-form-get request))
  (POST "/players/merge" request (handlers/merge-players request))
  (GET "/players/new" request (handlers/new-player-form request))
  (POST "/players" request (handlers/create-player request))
  (GET "/players/:id/split" [id :as request] (handlers/split-player-form-get request id))
  (POST "/players/:id/split" [id :as request] (handlers/split-player request id))
  (GET "/players/:id/edit" [id :as request] (handlers/edit-player-form request id))
  (POST "/players/:id" [id :as request] (handlers/update-player (assoc-in request [:params :id] id)))
  (POST "/players/:id/delete" [id :as request] (handlers/delete-player request id))

  ;; Plays
  (GET "/plays" request (handlers/plays-list request))
  (GET "/plays/new" request (handlers/new-play-form request))
  (POST "/plays" request (handlers/create-play request))
  (GET "/plays/:id/edit" [id :as request] (handlers/edit-play-form request id))
  (POST "/plays/:id" [id :as request] (handlers/update-play (assoc-in request [:params :id] id)))
  (POST "/plays/:id/delete" [id :as request] (handlers/delete-play request id))

  ;; Leaderboard
  (GET "/leaderboard" request (handlers/leaderboard request))
  (GET "/leaderboard/player/:id" [id :as request] (handlers/player-detail request id))
  (POST "/settings/toggle-scoring-mode" request (handlers/toggle-scoring-mode request))
  (POST "/settings/tie-scoring-mode" request (handlers/set-tie-scoring-mode request))

  ;; Data Management
  (GET "/data" request (handlers/data-management request))
  (POST "/data/export" request (handlers/export-data request))
  (POST "/data/import" request (handlers/import-data request))
  (POST "/data/import-games-csv" request (handlers/import-games-csv request))
  (POST "/data/import-players-csv" request (handlers/import-players-csv request))
  (POST "/data/clear" request (handlers/clear-data request))

  ;; htmx routes
  (GET "/htmx/leaderboard" request (handlers/leaderboard-fragment request))
  (POST "/htmx/plays/rank-by-score" request (handlers/auto-rank-by-score request))
  (POST "/htmx/plays/move-player" request (handlers/move-player request))
  (POST "/htmx/plays/add-player" request (handlers/add-player request))
  (POST "/htmx/plays/remove-player" request (handlers/remove-player request))
  (POST "/htmx/plays/reorder-players" request (handlers/reorder-players request))
  (POST "/htmx/plays/toggle-tie" request (handlers/toggle-tie request))
  (POST "/htmx/plays/create-and-add-player" request (handlers/create-and-add-player request))

  ;; Static resources
  (route/resources "/")
  (route/not-found "Not Found"))

(defn wrap-state [handler app-state]
  (fn [request]
    (handler (assoc request :kniziathon/state app-state))))

(defn create-app [app-state]
  (-> app-routes
      (wrap-state app-state)
      wrap-keyword-params
      wrap-params
      wrap-multipart-params))

(defn open-browser [url]
  (try
    (when (Desktop/isDesktopSupported)
      (.browse (Desktop/getDesktop) (URI. url)))
    (catch Exception e
      (println "Could not open browser:" (.getMessage e)))))

(defn -main [& args]
  (let [app-state (state/create-state state/default-data-file)
        app (create-app app-state)]
    (println "Starting Kniziathon Tracker on http://localhost:3000")
    (let [server (run-jetty app {:port 3000 :join? false})]
      (open-browser "http://localhost:3000")
      (.join server))))

(comment

  (do
    (require '[ring.middleware.reload :refer [wrap-reload]])
    (def dev-state (state/create-state state/default-data-file))
    (def dev-app (wrap-reload #(create-app dev-state)))
    (def server (atom nil)))

  (.stop @server)

  (reset! server
          (run-jetty dev-app {:port 3000
                              :join? false}))
  )
