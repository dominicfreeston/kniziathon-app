(ns user
  (:require [kniziathon.core :as core]
            [kniziathon.state :as state]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defonce server (atom nil))
(defonce dev-state (delay (state/create-state state/default-data-file)))

(defn dev-handler [request]
  ((core/create-app @dev-state) request))

(defn start! []
  (reset! server
          (run-jetty (wrap-reload #'dev-handler)
                     {:port 3000 :join? false}))
  (println "Dev server started on http://localhost:3000"))

(defn stop! []
  (when @server
    (.stop ^org.eclipse.jetty.server.Server @server)
    (reset! server nil)
    (println "Dev server stopped")))

(defn restart! []
  (stop!)
  (start!))
