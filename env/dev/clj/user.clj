(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
   [relembra.config :refer [env]]
   [clojure.spec.alpha :as s]
   [expound.alpha :as expound]
   [mount.core :as mount]
   [relembra.core :refer [start-app]]
   [datomic.api :as d]
   [relembra.db.core :as db]
   [buddy.hashers :as hash]))

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

  (start-app [])
  (def url (:database-url env))
  (require '[datomic.api :as d])
  (d/create-database url)

  (def conn (d/connect url))
  (def norms (:database-norms env))
  (relembra.db.core/install-norms conn norms)

  (d/q '[:find ?n :where [_ :user/name ?n]] (d/db conn))

  (require '[ring.util.anti-forgery :refer [anti-forgery-field]])

  (anti-forgery-field)

  (require '[buddy.hashers :as hash])
  (hash/derive "senha" {:alg :scrypt})
  (def senha "scrypt$0fd12d508beb0681a31e5c3b$65536$8$1$2473302431303038303124794a55584f5a5145796a585233326d364a75693356773d3d244d50775a6c5745416e687154414338364c7a4331512b467854767a657665306f617a55794e3355484764343d")
  (d/transact db/conn [[:db/add [:user/name "manolo"] :user/pass-hash senha]])
  (hash/check "senha" senha)
  (db/username->user "manolo")
  )
