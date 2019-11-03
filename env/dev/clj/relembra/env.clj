(ns relembra.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [relembra.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[relembra started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[relembra has shut down successfully]=-"))
   :middleware wrap-dev})
