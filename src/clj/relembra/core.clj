;;; Compojure and Sente routing.

(ns relembra.core
  (:gen-class)
  (:require [aleph.http :as aleph]
            [clj-time.coerce :as t-coerce]
            [clj-time.core :as t]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [compojure.core :refer (defroutes GET POST)]
            [compojure.route :refer (files not-found resources)]
            [hiccup.core :refer (html)]
            [datomic.api :as d]
            [relembra.datomic :as datomic]
            [relembra.sente :as sente]
            [relembra.util :as util]
            [relembra.github-login :as github-login]
            [ring.middleware.defaults :refer (wrap-defaults site-defaults)]
            [spaced-repetition.sm5 :as sm5])
   (:import
    [io.netty.handler.ssl SslContextBuilder]
    [java.io File]))

(defn now+days [days]
  (t-coerce/to-date
   (t/plus (t/now) (t/days days))))

(defn rate-recall [user lembrando rate]
  (let [db (d/db @datomic/conn)
        lembrando-ent (d/entity db lembrando)
        needs-repeat? (< rate 3)]
    (if (:lembrando/needs-repeat? lembrando-ent)
      (do
        (when-not needs-repeat?
          (d/transact @datomic/conn
                      [[:db/add lembrando :lembrando/needs-repeat? false]]))
        {:new-due-date (:lembrando/due-date lembrando-ent)
         :needs-repeat? needs-repeat?})
      (let [user-remembering-state
            (edn/read-string
             (:user/of-matrix (d/entity db user)))
            lembrando-remembering-state
            (edn/read-string
             (:lembrando/remembering-state lembrando-ent))
            {:keys [days-to-next
                    new-user-state
                    new-item-state]}
            (sm5/next-state rate user-remembering-state lembrando-remembering-state)
            new-due-date (now+days days-to-next)]
        (println "Days to next:" days-to-next)
        (d/transact @datomic/conn
                    [[:db/add user :user/of-matrix (pr-str new-user-state)]
                     {:db/id lembrando
                      :lembrando/remembering-state (pr-str new-item-state)
                      :lembrando/needs-repeat? needs-repeat?
                      :lembrando/due-date new-due-date}
                     {:db/id "datomic.tx"
                      :rate-recall/user user
                      :rate-recall/lembrando lembrando
                      :rate-recall/rate rate}])
        {:new-due-date new-due-date
         :needs-repeat? needs-repeat?}))))

(defn postpone [lembrando]
  (let [new-due-date (now+days 1)]
    (d/transact @datomic/conn [[:db/add lembrando :lembrando/due-date new-due-date]])
    {:new-due-date new-due-date}))

(defn assert-owner [lembrando user]
  (when-not (d/q '[:find ?l .
                   :in $ ?l ?u
                   :where [?u :user/lembrandos ?l]]
                 (d/db @datomic/conn)
                 lembrando
                 user)
    (throw (RuntimeException. (str user " is the wrong owner for " lembrando)))))

(defn requiring-login [f]
  (fn [req & etc]
    (if (get-in req [:session :user/github-name])
      (apply f req etc)
      (github-login/login req))))

(defn resolve-placeholders [spec req]
  (cond
    (and (vector? spec)
         (keyword? (first spec))
         (= "?" (namespace (first spec))))
    (case (first spec)
      :?/ip (:remote-addr req)
      :?/tempid (d/tempid :db.part/user (second spec))
      :else (throw (RuntimeException.
                    (str "unrecognized placeholder: "(pr-str name)))))
    (vector? spec)
    (mapv #(resolve-placeholders % req) spec)
    (map? spec)
    (into {} (for [[k v] spec]
               [k (resolve-placeholders v req)]))
    :else spec))

(defmethod sente/client-msg-handler :db/ops
  [{:keys [?data ring-req uid ?reply-fn]}]
  (?reply-fn (datomic/ops (resolve-placeholders ?data ring-req))))

(defmethod sente/client-msg-handler :relembra/rate-recall
  [{{:keys [lembrando rate]} :?data
    :keys [uid ?reply-fn]}]
  (assert-owner lembrando uid)
  (?reply-fn (rate-recall uid lembrando rate)))

(defmethod sente/client-msg-handler :relembra/postpone
  [{{:keys [lembrando]} :?data
    :keys [uid ?reply-fn]}]
  (assert-owner lembrando uid)
  (?reply-fn (postpone lembrando)))

(def root
  (requiring-login
   (fn [req]
     {:status 200
      :headers {"content-type" "text/html"}
      :session (assoc (:session  req) :uid (-> req :session :user/github-name datomic/user-id))
      :body (html [:head [:title "relembra (WIP)"]
                   [:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/font-hack/2.020/css/hack-extended.min.css"}]
                   [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Yrsa"}]
                   [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto:400,300,500&amp;subset=latin" :media "all"}]
                   [:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/flexboxgrid/6.3.0/flexboxgrid.min.css" :type "text/css"}]
                   [:script {:type "text/x-mathjax-config"}
                    "MathJax.Hub.Config({asciimath2jax: {delimiters: [['¡','¡']]}});"]
                   [:script {:type "text/javascript" :async true
                             :src "https://cdn.mathjax.org/mathjax/latest/MathJax.js?config=AM_CHTML"}]]
                  [:body
                   [:div#app_container
                    [:script {:type "text/javascript" :src "js/main.js"}]
                    [:script {:type "text/javascript"} "relembra.core.main();"]]])})))

(def chsk-get
  (requiring-login sente/ring-ajax-get-or-ws-handshake))

(def chsk-post
  (requiring-login sente/ring-ajax-post))

(defroutes handler

  (GET "/" req (root req))
  (GET "/github-auth-cb" [code state :as req]
    (github-login/github-auth-cb code state (get req :session {})))
  ;; sente
  (GET  "/chsk" req (chsk-get req))
  (POST "/chsk" req (chsk-post req))

  (resources (if util/in-development? "/public" "/"))
  (files "/")
  (not-found "Page not found."))

(def app
  (wrap-defaults handler site-defaults))

(if util/in-development?
  (sente/start-router!))

(defn -main []
  (sente/start-router!)
  (aleph/start-server app {:port 62443
                           :ssl-context (.build (SslContextBuilder/forServer
                                                 (File. "/etc/letsencrypt/live/relembra.icbink.net/fullchain.pem")
                                                 (File. "/etc/letsencrypt/live/relembra.icbink.net/privkey.pem")))})
  ;;XXX: use aleph.netty/wait-for-close when aleph 0.4.2 is out
  @(promise))
