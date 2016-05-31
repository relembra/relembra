;;; Github OAuth login.
;;;
;;; See https://developer.github.com/v3/oauth/

(ns relembra.github-login
  (:require [cemerick.url :refer (url)]
            [clojure.data.json :as json]
            [clojure.stacktrace :refer (print-stack-trace)]
            [clojure.string :as str]
            crypto.random
            [environ.core :refer (env)]
            [hiccup.core :refer (html)]
            [org.httpkit.client :as http]))

(defn login [req]
  (let [csrf-token (crypto.random/url-part 64)
        redirect-url (str (assoc (url "https://github.com/login/oauth/authorize")
                                 :query
                                 {:client_id (env :github-client-id)
                                  :scope "read:org"
                                  :state csrf-token}))]
    {:status 303
     :headers {"content-type" "text/html"
               "Location" redirect-url}
     :session (assoc (get req :session {})
                     :github-login-original-path "/"
                     :github-csrf-token csrf-token)
     :body (html [:head [:title "Please log in"]]
                 [:body
                  [:h2 "Please log in"]
                  [:p "Redirecting you to " redirect-url " to log in."]])}))

(defn auth-error [reason]
  (throw (RuntimeException. reason)))

(defn github-auth-cb- [code csrf-token session]
  (let [session-csrf-token (:github-csrf-token session)]
    (cond
      (not session-csrf-token) (auth-error "No CSRF token in session")
      (not (= csrf-token session-csrf-token)) (auth-error "CSRF token mismatch")
      :else
      (let [token-resp @(http/post "https://github.com/login/oauth/access_token"
                                   {:headers {"Accept" "application/json"}
                                    :form-params {:client_id (env :github-client-id)
                                                  :client_secret (env :github-client-secret)
                                                  :code code}})
            token-map (json/read-str (:body token-resp))
            access-token (get token-map "access_token")
            orgs-resp @(http/get "https://api.github.com/user/orgs"
                                 {:headers {"Accept" "application/json"
                                            "Authorization" (str "token " access-token)}})
            authorized (and (= (:status orgs-resp) 200)
                            (some #{"relembra"}
                                  (for [org (json/read-str (:body orgs-resp))]
                                    (get org "login"))))]
        (if-not authorized
          (auth-error "Not in relembra group")
          (let [user-resp @(http/get "https://api.github.com/user"
                                     {:headers {"Accept" "application/json"
                                                "Authorization" (str "token " access-token)}})
                user (get (json/read-str (:body user-resp))
                          "login")
                original-path (get session :github-login-original-path "/")]
            {:status 303
             :headers {"content-type" "text/html"
                       "location" original-path}
             :session (assoc session :user (str user))
             :body (html [:head [:title "Login successful"]]
                         [:body [:h1 "Login successful"]
                          [:p "Redirecting you to "
                           [:a {:href original-path} original-path]]])}))))))

(defn github-auth-cb [code csrf-token session]
  ;; This is an internal tool, I don't expect this to fail a lot, and I expect a
  ;; page refresh to fix it 99% of the time, so tracebacks in logs will do for
  ;; error reporting in this MVP until we find out we need more.
  (try (github-auth-cb- code csrf-token session)
       (catch Exception e
         (print-stack-trace e 40)
         (flush)
         {:status 403
          :headers {"content-type" "text/html"}
          :body (html [:head [:title "Not authorized"]]
                      [:body [:h2 "Not authorized"]
                       [:div "Only members of the relembra github organization are allowed to access this page."]])})))
