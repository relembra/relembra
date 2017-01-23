#!/usr/bin/env boot

;; Run in repo root.

(set-env! :dependencies '[[com.datomic/datomic-free "0.9.5544"]])

(require '[datomic.api :as d])
(require '[boot.core :as boot])

(boot/load-data-readers!)

(def schema (read-string (slurp "etc/schema.edn")))

(def conn (d/connect "datomic:free://localhost:4334/relembra"))

(println @(d/transact conn schema))
