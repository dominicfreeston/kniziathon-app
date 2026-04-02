(ns kniziathon.state
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def default-data-file
  (io/file (System/getProperty "user.home") ".kniziathon" "kniziathon.edn"))

(defn create-state
  "Create a new state atom. When data-file is provided, loads existing state
   from disk and installs a watcher that auto-persists on every change."
  ([] (atom {:games {} :players {} :plays {}}))
  ([data-file]
   (let [initial {:games {} :players {} :plays {}}
         state (atom (if (.exists data-file)
                       (try
                         (let [data (edn/read-string (slurp data-file))]
                           (println "Loaded state from" (str data-file))
                           data)
                         (catch Exception e
                           (println "Error loading state:" (.getMessage e))
                           initial))
                       initial))]
     (add-watch state :persistence
       (fn [_ _ _ _]
         (future
           (io/make-parents data-file)
           (spit data-file (pr-str @state)))))
     state)))

;; Helper functions
(defn add-game! [state game]
  (swap! state assoc-in [:games (:id game)] game)
  game)

(defn update-game! [state id game-data]
  (swap! state update-in [:games id] merge game-data))

(defn delete-game! [state id]
  (swap! state update :games dissoc id))

(defn get-game [state id]
  (get-in @state [:games id]))

(defn get-all-games [state]
  (vals (:games @state)))

(defn add-player! [state player]
  (swap! state assoc-in [:players (:id player)] player)
  player)

(defn update-player! [state id player-data]
  (swap! state update-in [:players id] merge player-data))

(defn delete-player! [state id]
  (swap! state update :players dissoc id))

(defn get-player [state id]
  (get-in @state [:players id]))

(defn get-all-players [state]
  (vals (:players @state)))

(defn add-play! [state play]
  (swap! state assoc-in [:plays (:id play)] play)
  play)

(defn update-play! [state id play-data]
  (swap! state update-in [:plays id] merge play-data))

(defn delete-play! [state id]
  (swap! state update :plays dissoc id))

(defn get-play [state id]
  (get-in @state [:plays id]))

(defn get-all-plays [state]
  (vals (:plays @state)))

(defn get-setting [state k]
  (get-in @state [:settings k]))

(defn toggle-setting! [state k]
  (swap! state update-in [:settings k] not))

(defn clear-all-data! [state]
  (reset! state {:games {} :players {} :plays {}}))

(defn import-data! [state data replace?]
  (if replace?
    (reset! state data)
    (swap! state
           (fn [current new]
             {:games (merge (:games current) (:games new))
              :players (merge (:players current) (:players new))
              :plays (merge (:plays current) (:plays new))})
           data)))
