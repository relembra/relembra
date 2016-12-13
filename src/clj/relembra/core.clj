;;; Compojure and Sente routing.

(ns relembra.core
  (:gen-class)
  (:require [aleph.http :as aleph]
            [clojure.pprint :as pp]
            [compojure.core :refer (defroutes GET POST)]
            [compojure.route :refer (files not-found resources)]
            [hiccup.core :refer (html)]
            [datomic.api :as d]
            [relembra.datomic :as datomic]
            [relembra.sente :as sente]
            [relembra.util :as util]
            [relembra.github-login :as github-login]
            [ring.middleware.defaults :refer (wrap-defaults site-defaults)])
  (:import
   [io.netty.handler.ssl SslContextBuilder]
   [java.io File]))


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
  (aleph/start-server app {:port 443
                           :ssl-context (.build (SslContextBuilder/forServer
                                                 (File. "/etc/letsencrypt/live/relembra.estevo.eu/fullchain.pem")
                                                 (File. "/etc/letsencrypt/live/relembra.estevo.eu/privkey.pem")))})
  ;;XXX: use aleph.netty/wait-for-close when aleph 0.4.2 is out
  @(promise))
