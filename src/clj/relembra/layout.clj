(ns relembra.layout
  (:require
   hiccup.core
   [hiccup.form :as form]
   [selmer.parser :as parser]
   [selmer.filters :as filters]
   [markdown.core :refer [md-to-html-string]]
   [ring.util.http-response :refer [content-type ok]]
   [ring.util.anti-forgery :refer [anti-forgery-field]]
   [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(parser/set-resource-path!  (clojure.java.io/resource "html"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))

(defn okok
  [s]
  (-> s ok (content-type "text/html; charset=utf-8")))

(defn ok-hiccup
  [h]
  (okok (hiccup.core/html h)))

(defn render
  "renders the HTML template located relative to resources/html"
  [_ template & [params]]
  (okok
   (parser/render-file
    template
    (assoc params
           :page template
           :csrf-token *anti-forgery-token*))))

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error.html" error-details)})

(defn login-page
  []
  (ok-hiccup
   (form/form-to
    [:post "/login"]
    (anti-forgery-field)
    (form/text-field {:placeholder "username"} "username")
    (form/password-field {:placeholder "password"} "password")
    (form/submit-button "login"))))
