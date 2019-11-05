(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
   [relembra.config :refer [env]]
   [clojure.spec.alpha :as s]
   [expound.alpha :as expound]
   [mount.core :as mount]
   [relembra.core :refer [start-app]]
   [datomic.api :as d]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(add-tap (bound-fn* clojure.pprint/pprint))

(defn start
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'relembra.core/repl-server))

(defn stop
  "Stops application."
  []
  (mount/stop-except #'relembra.core/repl-server))

(defn restart
  "Restarts application."
  []
  (stop)
  (start))

(comment

  (restart)

  (def url (:database-url env))
  (require '[datomic.api :as d])
  (d/create-database url)

  (def conn (d/connect url))
  (def norms (:database-norms env))
  (relembra.db.core/install-norms conn norms)

  (d/q '[:find ?n :where [_ :user/name ?n]] (d/db conn))
  )
