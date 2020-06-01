(ns atomist.Main
  (:require [atomist.skill :as skill])
  (:import [com.google.cloud.functions HttpFunction])
  (:gen-class
   :implements [com.google.cloud.functions.HttpFunction]
   :prefix "gcf-"
   :main false))

;; mvn dependency:copy \
;    -Dartifact='com.google.cloud.functions.invoker:java-function-invoker:1.0.1' \
;    -DoutputDirectory=.

(defn gcf-service [_ request response]
  (println request)
  (skill/-main []))
