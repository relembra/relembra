(ns relembra.posh-conn
  (:require [datascript.core :as d]
            [posh.reagent :as p]))

(defonce conn (let [conn (d/create-conn)]
                (d/transact! conn [{:db/id 0
                                    :screen/current :loading
                                    :drawer/open false
                                    :addq/question-text ""
                                    :addq/answer-text ""}])
                (p/posh! conn)
                conn))

(def get0-query '[:find ?x . :in $ ?a :where [0 ?a ?x]])

(defn get0 [attr]
  (d/q get0-query @conn attr))

(defn posh-get0 [attr]
  @(p/q get0-query conn attr))

(defn set0! [& args]
  (p/transact! conn [(into {:db/id 0} (map vec (partition 2 args)))]))
