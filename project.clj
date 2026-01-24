(defproject kniziathon "0.1.0-SNAPSHOT"
  :description "A Kniziathon gaming event score tracker"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [compojure "1.7.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "2.4.0"]]
  :main kniziathon.core
  :aot [kniziathon.core]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]]}})
