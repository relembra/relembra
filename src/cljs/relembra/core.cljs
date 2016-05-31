(ns relembra.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [datascript.core :as d]
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
                                    :counter/value 0}])
                (p/posh! conn)
                conn))

(defn app []
  (let [counter-value @(p/q conn
                            '[:find ?c .
                              :where [0 :counter/value ?c]])]
    [:div (str "Current value is " counter-value)
     [:input {:type "button"
              :value "Click me NOW"
              :on-click #(chsk-send! [:test/inc counter-value]
                                     5000
                                     (fn [resp]
                                       (p/transact! conn [{:db/id 0
                                                           :counter/value resp}])))}]]))

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
