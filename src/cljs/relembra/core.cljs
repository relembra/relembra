(ns relembra.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [datascript.core :as d]
            [markdown.core :refer (md->html)]
            [posh.core :as p]
            [reagent.core :as r]
            [taoensso.sente  :as sente :refer (cb-success?)]))

;; sente
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defonce conn (let [conn (d/create-conn)]
                (d/transact! conn [{:db/id 0
                                    :addq/question-text ""
                                    :addq/answer-text ""}])
                (p/posh! conn)
                conn))

(defn typeset [c]
  (js/MathJax.Hub.Queue (array "Typeset" js/MathJax.Hub (r/dom-node c))))

(defn markdown-box [text]
  [:div {:id "preview" :dangerouslySetInnerHTML {:__html (md->html text :inhibit-separator "$")}}])

(def mathjax-box
  (with-meta markdown-box
    {:component-did-mount typeset
     :component-did-update typeset}))

(defn md-editor [k]
  (let [text @(p/q conn '[:find ?c .
                          :in $ ?k
                          :where [0 ?k ?c]]
                   k)]
    [:tr
     [:td>textarea {:rows 20
                    :cols 80
                    :value text
                    :on-change (fn [e]
                                 (p/transact! conn
                                              [{:db/id 0 k (.. e -target -value)}]))} ]
     [:td {:valign "top"} [mathjax-box text]]]))

(defn app []
  [:table>tbody
   [md-editor :addq/question-text]
   [md-editor :addq/answer-text]])

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
