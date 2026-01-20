(ns kniziathon.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as response]
            [kniziathon.state :as state]
            [kniziathon.handlers :as handlers]
            [kniziathon.views :as views])
  (:gen-class))

(defroutes app-routes
  ;; Home
  (GET "/" [] (response/redirect "/games"))
  
  ;; Games
  (GET "/games" [] (handlers/games-list))
  (GET "/games/new" [] (handlers/new-game-form))
  (POST "/games" {params :params} (handlers/create-game params))
  (GET "/games/:id/edit" [id] (handlers/edit-game-form id))
  (POST "/games/:id" {params :params} (handlers/update-game params))
  (POST "/games/:id/delete" [id] (handlers/delete-game id))
  
  ;; Players
  (GET "/players" [] (handlers/players-list))
  (GET "/players/new" [] (handlers/new-player-form))
  (POST "/players" {params :params} (handlers/create-player params))
  (GET "/players/:id/edit" [id] (handlers/edit-player-form id))
  (POST "/players/:id" {params :params} (handlers/update-player params))
  (POST "/players/:id/delete" [id] (handlers/delete-player id))
  
  ;; Plays
  (GET "/plays" [] (handlers/plays-list))
  (GET "/plays/new" [] (handlers/new-play-form))
  (POST "/plays" {params :params} (handlers/create-play params))
  (GET "/plays/:id/edit" [id] (handlers/edit-play-form id))
  (POST "/plays/:id" {params :params} (handlers/update-play params))
  (POST "/plays/:id/delete" [id] (handlers/delete-play id))
  
  ;; Leaderboard
  (GET "/leaderboard" [] (handlers/leaderboard))
  (GET "/leaderboard/player/:id" [id] (handlers/player-detail id))
  
  ;; Data Management
  (GET "/data" [] (handlers/data-management))
  (POST "/data/export" [] (handlers/export-data))
  (POST "/data/import" {params :params} (handlers/import-data params))
  (POST "/data/clear" [] (handlers/clear-data))
  
  ;; htmx routes
  (GET "/htmx/leaderboard" [] (handlers/leaderboard-fragment))
  (POST "/htmx/plays/rank-by-score" {params :params} (handlers/auto-rank-by-score params))
  
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
