(ns relembra.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljsjs.material-ui]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as icons]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [datascript.core :as d]
            [goog.dom :as gdom]
            [goog.array :as garray]
            [markdown.core :refer (md->html)]
            [posh.reagent :as p]
            [reagent.core :as r]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(defn children [e]
  (garray/toArray (gdom/getChildren e)))

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
                                    :addq/question-caret 0
                                    :addq/answer-text ""
                                    :addq/answer-caret 0}])
                (p/posh! conn)
                conn))

(enable-console-print!)

(defn typeset [c]
  (js/MathJax.Hub.Queue (array "Typeset" js/MathJax.Hub (r/dom-node c))))

(defn markdown-box [text]
  [:div {:dangerouslySetInnerHTML {:__html (md->html text :inhibit-separator "$")}}])

(def mathjax-box
  (with-meta markdown-box
    {:component-did-mount typeset
     :component-did-update typeset}))


(defn text-field [title value-k text]
  [rui/text-field
   {:floating-label-text title
    :multi-line true
    :rows 1
    :full-width true
    :default-value text
    :style {:font-family "Hack, monospace" :font-size "90%"}
    :on-change (fn [e]
                 (let [new-value (.. e -target -value)]
                   (p/transact! conn
                                [{:db/id 0
                                  value-k new-value}])))}])

(defn md-editor [title value-k caret-k]
  (let [query '[:find ?c .
                :in $ ?k
                :where [0 ?k ?c]]
        text @(p/q query conn value-k)]
    [:div.row.around-xs {:style {:margin-top "1em" :margin-bottom "1em"}}
     [:div.col-xs-12.col-sm-5
      [text-field title value-k text]]
     [:div.col-xs-12.col-sm-6 {:style {:padding-top "0.5em" :font-family "Yrsa, serif" :font-size "120%"}}
      [mathjax-box text]]]))

(defn app []
  [:div
   [rui/mui-theme-provider
    {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :cyan900)}})}
    [:div
     [rui/app-bar {:title "Acrescenta pergunta"}]
     [:div.container
      [md-editor "Pergunta" :addq/question-text :addq/question-caret]
      [md-editor "Resposta" :addq/answer-text :addq/answer-caret]
      [:div.row {:style {:padding "0px 10px"}}
       [:div.col
        [:div.box
         [rui/flat-button {:label "Acrescentar"
                           :icon (icons/content-add-circle)
                           :on-touch-tap #(println "clicau!")}]]]]]]]])

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
