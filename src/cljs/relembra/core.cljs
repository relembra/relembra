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

(defn typeset [c]
  (.log js/console "Updating!")
  (js/MathJax.Hub.Queue (array "Typeset" js/MathJax.Hub (r/dom-node c))))

(defn mathjax-box [text]
  [:div {:id "preview" :dangerouslySetInnerHTML {:__html (md->html text)}}])

(def mathjax-box2 (with-meta mathjax-box {:component-did-mount typeset
                                          :component-did-update typeset}))

(defn app []
  (let [editor-text @(p/q conn
                          '[:find ?c .
                            :where [0 :editor/text ?c]])]
    [:div {:id "um"} (str "Esta é a área $a_0$: " editor-text)
     [:table>tbody>tr
      [:td>textarea {:rows 40
                     :cols 80
                     :id "input"
                     :value editor-text
                     :on-change (fn [e]
                                  (p/transact! conn
                                               [{:db/id 0 :editor/text (.. e -target -value)}]))} ]
      [:td {:valign "top"} [mathjax-box2 editor-text]]
      [:td {:valign "top"} editor-text]]]))


(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
