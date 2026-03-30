(ns kniziathon.state
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defonce app-state
  (atom {:games {}
         :players {}
         :plays {}}))

(defn save-state! []
  (io/make-parents "data/kniziathon.edn")
  (spit "data/kniziathon.edn" (pr-str @app-state)))

(defn load-state! []
  (let [file (io/file "data/kniziathon.edn")]
    (when (.exists file)
      (try
        (reset! app-state (edn/read-string (slurp file)))
        (println "Loaded state from data/kniziathon.edn")
        (catch Exception e
          (println "Error loading state:" (.getMessage e)))))))

;; Auto-save on state changes
(add-watch app-state :persistence
  (fn [_ _ _ _]
    (future (save-state!))))

;; Helper functions
(defn add-game! [game]
  (swap! app-state assoc-in [:games (:id game)] game)
  game)

(defn update-game! [id game-data]
  (swap! app-state update-in [:games id] merge game-data))

(defn delete-game! [id]
  (swap! app-state update :games dissoc id))

(defn get-game [id]
  (get-in @app-state [:games id]))

(defn get-all-games []
  (vals (:games @app-state)))

(defn add-player! [player]
  (swap! app-state assoc-in [:players (:id player)] player)
  player)

(defn update-player! [id player-data]
  (swap! app-state update-in [:players id] merge player-data))

(defn delete-player! [id]
  (swap! app-state update :players dissoc id))

(defn get-player [id]
  (get-in @app-state [:players id]))

(defn get-all-players []
  (vals (:players @app-state)))

(defn add-play! [play]
  (swap! app-state assoc-in [:plays (:id play)] play)
  play)

(defn update-play! [id play-data]
  (swap! app-state update-in [:plays id] merge play-data))

(defn delete-play! [id]
  (swap! app-state update :plays dissoc id))

(defn get-play [id]
  (get-in @app-state [:plays id]))

(defn get-all-plays []
  (vals (:plays @app-state)))

(defn clear-all-data! []
  (reset! app-state {:games {} :players {} :plays {}}))

(defn import-data! [data replace?]
  (if replace?
    (reset! app-state data)
    (swap! app-state 
           (fn [current new]
             {:games (merge (:games current) (:games new))
              :players (merge (:players current) (:players new))
              :plays (merge (:plays current) (:plays new))})
           data)))
