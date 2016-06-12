(ns relembra.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [datascript.core :as d]
            [posh.core :as p]
            [reagent.core :as r]
            [taoensso.sente  :as sente :refer (cb-success?)]
            cljsjs.victory))

;; sente
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defonce conn (let [conn (d/create-conn)]
                (d/transact! conn [{:db/id 0
                                    :counter/value 0}])
                (p/posh! conn)
                conn))

(def bar (r/adapt-react-class js/Victory.VictoryBar))
(def chart (r/adapt-react-class js/Victory.VictoryChart))
(def group (r/adapt-react-class js/Victory.VictoryGroup))
(def line (r/adapt-react-class js/Victory.VictoryLine))
(def pie (r/adapt-react-class js/Victory.VictoryPie))

(defn app []
  (let [counter-value @(p/q conn
                            '[:find ?c .
                              :where [0 :counter/value ?c]])]
    [:div (str "Current value is " counter-value)
     [:div
      [:input {:type "button"
               :value "Click me NOW"
               :on-click #(chsk-send! [:test/inc counter-value]
                                      5000
                                      (fn [resp]
                                        (p/transact! conn [{:db/id 0
                                                            :counter/value resp}])))}]]
     (comment [:div
               "Esta é a tarta:"
               [pie {"data" (clj->js [{"x" "umanos" "y" 24}
                                      {"x" "animais" "y" 12}
                                      {"x" "resto" "y" 60}])}]])
     [:div
      "Este é um group"
      [group {:height 500 :offset 20}
       [bar {:data (clj->js [{:x 1 :y 1} {:x 2 :y 2} {:x 3 :y 3}])}]
       [bar {:data (clj->js [{:x 1 :y 4} {:x 2 :y 2} {:x 3 :y 1}])}]]]
     [:div
      "Este é um rine"
      [line {:data [{:x 0 :y 3} {:x 1 :y 4} {:x 2 :y 2} {:x 3 :y 1}]}]]]))

;; [chart {} ]
;; (r/create-element js/Victory.VictoryLine #js{})

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
