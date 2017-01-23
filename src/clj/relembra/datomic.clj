(ns relembra.datomic
  (:require [datomic.api :as d]
            [relembra.util :as util]))

(def conn (delay (d/connect (if util/in-development?
                              "datomic:free://localhost:4334/relembra"
                              "datomic:free://datomic:4334/relembra"))))

(defn query [query args]
  (apply d/q query (d/db @conn) args))

(defn pull [query eid]
  (d/pull (d/db @conn) query eid))

(defn transact [txn-data]
  (try
    (do
      @(d/transact @conn txn-data)
      :ops/transaction-ok)
    (catch Exception e {:datomic/exception (.getMessage e)})))

(defn ops [spec]
  (into [] (for [[cmd & data] spec]
             (apply (case cmd
                      :transact transact
                      :query query
                      :pull pull)
                    data))))

(defn replace-tempids [x]
  (cond
    (map? x) (into {} (for [[k v] x]
                        [k (replace-tempids v)]))
    (and (vector? x) (= :db/tempid (first x)))
    (d/tempid :db.part/user (second x))
    (vector? x) (mapv replace-tempids x)
    :else x))

(defn user-id [github-name]
  (let [tempid (d/tempid :db.part/user -1)
        report @(d/transact @conn [{:db/id tempid :user/github-name github-name}])]
    (d/resolve-tempid (:db-after report) (:tempids report) tempid)))
