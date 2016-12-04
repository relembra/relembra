;;; Compojure and Sente routing.

(ns relembra.core
  (:require [clojure.pprint :as pp]
            [compojure.core :refer (defroutes GET POST)]
            [compojure.route :refer (files not-found resources)]
            [environ.core :refer (env)]
            [hiccup.core :refer (html)]
            [datomic.api :as d]
            [relembra.datomic :as datomic]
            [relembra.sente :as sente]
            [relembra.github-login :as github-login]
            [ring.middleware.defaults :refer (wrap-defaults site-defaults)]))

;; DRY XXX: factor out
(def in-development (= (env :in-development) "indeed"))

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
  [{:keys [?data ring-req ?reply-fn]}]
  (?reply-fn (datomic/ops (resolve-placeholders ?data ring-req))))

(defn root [req]
  (if-let [github-name (get-in req [:session :user/github-name])]
    (do
      {:status 200
       :headers {"content-type" "text/html"}
       :session (assoc (:session  req) :uid (datomic/user-id github-name))
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
                     [:script {:type "text/javascript"} "relembra.core.main();"]]])})
    (github-login/login req)))

(defroutes handler

  (GET "/" req (root req))
  (GET "/github-auth-cb" [code state :as req]
    (github-login/github-auth-cb code state (get req :session {})))
  ;; sente
  (GET  "/chsk" req (sente/ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (sente/ring-ajax-post req))

  (resources (if in-development "/public" "/"))
  (files "/")
  (not-found "Page not found."))

(def app
  (wrap-defaults handler site-defaults))

(if in-development
  (sente/start-router!))

;; This is set in nginx.conf as jvm_init_handler_name, so it will get called on
;; startup.
(defn nginx-init! [_]
  (sente/start-router!))
