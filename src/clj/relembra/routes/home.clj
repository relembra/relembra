(ns relembra.routes.home
  (:require
   [buddy.hashers :as hash]
   [clojure.java.io :as io]
   [relembra.middleware :as middleware]
   [ring.util.http-response :as response]
   [relembra.db.core :as db]
   [relembra.layout :as layout]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn process-login [{{:keys [username password]} :params
                      session :session}]
  (let [{:keys [db/id user/pass-hash]}
        (db/username->user username)]
    (cond
      (not id) (layout/okok "NO ID")
      (not (hash/check password pass-hash)) (layout/okok "NO MATCH!")
      :else (-> (layout/okok "YEEES!")
                (assoc :session
                       (assoc session :identity id))))))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/login" {:get (fn [_] (layout/login-page))
              :post process-login}]
   ["/logout" {:get (fn [{:keys [session]}]
                      (-> (layout/okok "Bye!")
                          (assoc :session (dissoc session :identity))))}]
   ["/session" {:get (fn [{ :keys [session]}]
                       (layout/okok (pr-str session)))}]
   ["/secret" {:middleware [middleware/wrap-restricted]
               :get (fn [_] (layout/okok "You got it!"))}]
   ["/docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

