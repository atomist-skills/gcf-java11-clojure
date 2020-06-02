(ns atomist.Main
  (:require [atomist.skill :as skill])
  (:import [com.google.cloud.functions HttpFunction])
  (:gen-class
   :implements [com.google.cloud.functions.HttpFunction]
   :prefix "gcf-"
   :main false))

(defn gcf-service [_ request response]
  (println request)
  (skill/-main []))
