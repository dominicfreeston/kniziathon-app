(ns user
  (:require [kniziathon.core :as core]
            [kniziathon.state :as state]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defonce server (atom nil))

(defn start! []
  (state/load-state!)
  (reset! server (run-jetty (wrap-reload #'core/app) {:port 3000 :join? false}))
  (println "Dev server started on http://localhost:3000"))

(defn stop! []
  (when @server
    (.stop ^org.eclipse.jetty.server.Server @server)
    (reset! server nil)
    (println "Dev server stopped")))

(defn restart! []
  (stop!)
  (start!))
