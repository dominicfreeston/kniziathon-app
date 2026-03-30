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
  (:gen-class))

(defroutes app-routes
  ;; Home
  (GET "/" [] (response/redirect "/games"))
  
  ;; Games
  (GET "/games" [] (handlers/games-list))
  (GET "/games/merge" [] (handlers/merge-games-form-get))
  (POST "/games/merge" {params :params} (handlers/merge-games params))
  (GET "/games/new" [] (handlers/new-game-form))
  (POST "/games" {params :params} (handlers/create-game params))
  (GET "/games/:id/edit" [id] (handlers/edit-game-form id))
  (POST "/games/:id" [id :as {params :params}] (handlers/update-game (assoc params :id id)))
  (POST "/games/:id/delete" [id] (handlers/delete-game id))
  
  ;; Players
  (GET "/players" [] (handlers/players-list))
  (GET "/players/merge" [] (handlers/merge-players-form-get))
  (POST "/players/merge" {params :params} (handlers/merge-players params))
  (GET "/players/new" [] (handlers/new-player-form))
  (POST "/players" {params :params} (handlers/create-player params))
  (GET "/players/:id/edit" [id] (handlers/edit-player-form id))
  (POST "/players/:id" [id :as {params :params}] (handlers/update-player (assoc params :id id)))
  (POST "/players/:id/delete" [id] (handlers/delete-player id))
  
  ;; Plays
  (GET "/plays" [] (handlers/plays-list))
  (GET "/plays/new" {params :params} (handlers/new-play-form params))
  (POST "/plays" {params :params} (handlers/create-play params))
  (GET "/plays/:id/edit" [id :as {params :params}] (handlers/edit-play-form id params))
  (POST "/plays/:id" [id :as {params :params}] (handlers/update-play (assoc params :id id)))
  (POST "/plays/:id/delete" [id] (handlers/delete-play id))
  
  ;; Leaderboard
  (GET "/leaderboard" [] (handlers/leaderboard))
  (GET "/leaderboard/player/:id" [id] (handlers/player-detail id))
  
  ;; Data Management
  (GET "/data" [] (handlers/data-management))
  (POST "/data/export" [] (handlers/export-data))
  (POST "/data/import" {params :params} (handlers/import-data params))
  (POST "/data/import-games-csv" {params :params} (handlers/import-games-csv params))
  (POST "/data/import-players-csv" {params :params} (handlers/import-players-csv params))
  (POST "/data/clear" [] (handlers/clear-data))
  
  ;; htmx routes
  (GET "/htmx/leaderboard" [] (handlers/leaderboard-fragment))
  (POST "/htmx/plays/rank-by-score" {params :params} (handlers/auto-rank-by-score params))
  (POST "/htmx/plays/move-player" {params :params} (handlers/move-player params))
  (POST "/htmx/plays/add-player" {params :params} (handlers/add-player params))
  (POST "/htmx/plays/remove-player" {params :params} (handlers/remove-player params))
  (POST "/htmx/plays/reorder-players" {params :params} (handlers/reorder-players params))
  
  ;; Static resources
  (GET "/css/pico.min.css" [] (response/resource-response "pico.min.css" {:root "public/css"}))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      wrap-keyword-params
      wrap-params
      wrap-multipart-params))

(defn -main [& args]
  (state/load-state!)
  (println "Starting Kniziathon Tracker on http://localhost:3000")
  (run-jetty app {:port 3000 :join? true}))

(comment

  (do 
    (require '[ring.middleware.reload :refer [wrap-reload]])
    (def dev-app (wrap-reload #'app))
    (def server (atom nil)))

  (state/load-state!)

  (.stop @server)
  
  (reset! server
          (run-jetty dev-app {:port 3000
                              :join? false}))
  )
