;;; Compojure and Sente routing.

(ns relembra.core
  (:require [clojure.pprint :as pp]
            [compojure.core :refer (defroutes GET POST)]
            [compojure.route :refer (files not-found resources)]
            [environ.core :refer (env)]
            [hiccup.core :refer (html)]
            [datomic.api :as d]
            [relembra.datomic :as datomic]
            [relembra.github-login :as github-login]
            [ring.middleware.defaults :refer (wrap-defaults site-defaults)]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :as http-kit-adapter]
            [taoensso.sente.server-adapters.nginx-clojure :as nginx-adapter]))

(def in-development (= (env :in-development) "indeed"))


;; sente setup

(let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (if in-development
                                    http-kit-adapter/sente-web-server-adapter
                                    nginx-adapter/sente-web-server-adapter)
                                  {})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defmulti -sente-handler :id)

(defn sente-handler [event]
  (-sente-handler event))


(defmethod -sente-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (println "Unhandled event:" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-server event}))))

;; test handler
(defmethod -sente-handler :test/inc
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "got request to inc" ?data)
  (?reply-fn (inc ?data)))

(defmethod -sente-handler :db/query
  [{:as ev-msg [query-ex & args] :?data :keys [event id ring-req ?reply-fn send-fn]}]
  (println "query" query-ex "args" args)
  (let [ret (datomic/query query-ex args)]
    (println "responding" ret)
    (Thread/sleep 500)
    (?reply-fn ret)))

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

(defmethod -sente-handler :db/ops
  [{:keys [?data ring-req ?reply-fn]}]
  (?reply-fn (datomic/ops (resolve-placeholders ?data ring-req))))

(defmethod -sente-handler :db/transact
  [{:keys [?data ?reply-fn]}]
  (datomic/transact (datomic/replace-tempids (:txn ?data)))
  (when-let [spec (:post-fetch ?data)]
    (?reply-fn (datomic/fetch spec))))

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-server-chsk-router!
           ch-chsk sente-handler)))


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
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))

  (resources (if in-development "/public" "/"))
  (files "/")
  (not-found "Page not found."))

(def app
  (wrap-defaults handler site-defaults))

(if in-development
  (start-router!))

;; This is set in nginx.conf as jvm_init_handler_name, so it will get called on
;; startup.
(defn nginx-init! [_]
  (start-router!))
