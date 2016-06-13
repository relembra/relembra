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
                                    :editor/text ""}])
                (p/posh! conn)
                conn))

(defn try-katex [txt]
  (try
    (.renderToString js/katex txt)
    (catch js/Error _ txt)))

(defn app []
  (let [editor-text @(p/q conn
                          '[:find ?c .
                            :where [0 :editor/text ?c]])]
    [:table>tbody>tr
     [:td>textarea {:rows 40
                    :cols 80
                    :id "input"
                    :value editor-text
                    :on-change (fn [e]
                                 (p/transact! conn
                                              [{:db/id 0 :editor/text (.. e -target -value)}]))} ]
     [:td {:valign "top"
           :dangerouslySetInnerHTML {:__html (md->html (try-katex editor-text))}}]]))

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
