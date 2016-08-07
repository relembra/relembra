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
                (d/transact! conn [{:init/loading true
                                    :db/id 0
                                    :drawer/open false
                                    :addq/question-text ""
                                    :addq/answer-text ""}])
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

(defn toggle-drawer [b]
  (p/transact! conn [{:db/id 0 :drawer/open b}]))

(defn open-drawer [& args]
  (toggle-drawer true))

(defn close-drawer [& args]
  (toggle-drawer false))

(defn drawer []
  (let [open @(p/q '[:find ?x . :where [0 :drawer/open ?x]] conn)]
    [rui/drawer
     {:docked false
      :width 200
      :open open
      :on-request-change toggle-drawer}
     [rui/menu-item {:on-touch-tap
                     (fn [x]
                        (println "on-touch-tap-1" x)
                        (close-drawer))}
      "Item 1"]
     [rui/menu-item {:on-touch-tap
                     (fn[x]
                        (println "on-touch-tap-2" x)
                        (close-drawer))}
      "Item 2"]]))

(defn add-lembrando []
  [:div
   [rui/app-bar {:title "Acrescenta pergunta"
                 :on-left-icon-button-touch-tap open-drawer}]
   [drawer]
   [:div.container
    [md-editor "Pergunta" :addq/question-text :addq/question-caret]
    [md-editor "Resposta" :addq/answer-text :addq/answer-caret]
    [:div.row {:style {:padding "0px 10px"}}
     [:div.col
      [:div.box
       [rui/flat-button {:label "Acrescentar"
                         :icon (icons/content-add-circle)
                         :on-touch-tap #(println "clicau!")}]]]]]])

(defn loading-screen []
  [:div.container
   [:div.row.center-xs {:style {:margin-top 50 :padding-left "1em"}}
    [:h1 {:style {:font-family "Roboto" :font-weight 300 :color (ui/color :teal600)}} "Carregando..."]]
   [:div.row.center-xs
    [rui/circular-progress]]])

(defn app []
  (let [loading? @(p/q '[:find ?l . :where [0 :init/loading ?l]] conn)]
    [rui/mui-theme-provider
     {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :teal600)}})}
     (if loading?
       [loading-screen]
       [add-lembrando])]))

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
