(ns relembra.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljsjs.material-ui]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as icons]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [datascript.core :as d]
            [markdown.core :refer (md->html)]
            [posh.reagent :as p]
            [reagent.core :as r]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(defonce conn (let [conn (d/create-conn)]
                (d/transact! conn [{:db/id 0
                                    :test/number 24
                                    :screen/current :loading
                                    :drawer/open false
                                    :addq/question-text ""
                                    :addq/answer-text ""}])
                (p/posh! conn)
                conn))

(def get0-query '[:find ?x . :in $ ?a :where [0 ?a ?x]])

(defn get0 [attr]
  (d/q get0-query conn attr))

(defn posh-get0 [attr]
  @(p/q get0-query conn attr))

(defn set0! [& args]
  (p/transact! conn [(into {:db/id 0} (map vec (partition 2 args)))]))

;; sente
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :ws})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (println "Unhandled klient event: %s" event))

(def lembrandos-query '[:find ((pull ?l [* {:lembrando/question [*]}]) ...)
                        :in $ ?u
                        :where
                        [?u :user/lembrandos ?l]])

(defn lembrando-query-results->txn [results user-id]
  (apply concat
         (for [entry results]
           [(update-in (:lembrando/question entry) [:question/owner] :db/id)
            (update-in entry [:lembrando/question] :db/id)
            [:db/add user-id :user/lembrandos (:db/id entry)]])))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (println "Unhandled klient event: %s" event))

(defn replace-dbids [x]
  (cond
    (and (map? x) (= (ffirst (vec x)) :db/id))
    (second (first (vec x)))
    (map? x) (into {} (for [[k v] x]
                        [k (replace-dbids v)]))
    (vector? x) (mapv replace-dbids x)
    :else x))

(defmethod -event-msg-handler :chsk/state
  [{[_ {:keys [uid] :as new-state-map}] :?data}]
  (if-not (:first-open? new-state-map)
    (.log js/console (str "Channel socket state change: " new-state-map))
    (chsk-send! [:db/ops [[:query lembrandos-query [uid]]]]
                10000
                (fn [ret]
                  (.log js/console (str "Returned: " ret))
                  (when (cb-success? ret)  ; XXX: handle failure!
                    (let [lembrandos (first ret)]
                      (if (= (count lembrandos) 0)
                        (set0! :user/id uid
                               :screen/current :add-lembrando)
                        (p/transact! conn
                                     (into [{:db/id 0
                                             :user/id uid
                                             :screen/current :welcome}]
                                           (lembrando-query-results->txn lembrandos uid))))))))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (println "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (println "Handshake: %s" ?data)))


(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
           ch-chsk event-msg-handler)))

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
                   (set0! value-k new-value)))}])

(defn md-editor [title value-k text]
  [:div.row.around-xs {:style {:margin-top "1em" :margin-bottom "1em"}}
   [:div.col-xs-12.col-sm-5
    [text-field title value-k text]]
   [:div.col-xs-12.col-sm-6 {:style {:padding-top "0.5em" :font-family "Yrsa, serif" :font-size "120%"}}
    [mathjax-box text]]])

(defn toggle-drawer [b]
  (set0! :drawer/open b))

(defn open-drawer [& args]
  (toggle-drawer true))

(defn close-drawer [& args]
  (toggle-drawer false))

(defn drawer []
  (let [open (posh-get0 :drawer/open)]
    [rui/drawer
     {:docked false
      :width 200
      :open open
      :on-request-change toggle-drawer}
     [rui/menu-item {:on-touch-tap
                     (fn [x]
                       (set0! :drawer/open false :screen/current :welcome))}
      "Resumo"]
     [rui/menu-item {:on-touch-tap
                     (fn[x]
                       (set0! :drawer/open false :screen/current :add-lembrando))}
      "Acrescenta pergunta"]]))

(defn screen [title contents]
  [:div
   [rui/app-bar {:title title
                 :on-left-icon-button-touch-tap open-drawer}]
   [drawer]
   contents])


(defn transact-fetch-results [res user-id]
  (.log js/console (str "successful result!" (pr-str res)))
  (p/transact! conn (lembrando-query-results->txn res user-id)))

(defn add-lembrando []
  (let [user-id (posh-get0 :user/id)
        qtext (posh-get0 :addq/question-text)
        atext (posh-get0 :addq/answer-text)]
    [screen "Acrescenta pergunta"
     [:div.container
      [md-editor "Pergunta" :addq/question-text qtext]/
      [md-editor "Resposta" :addq/answer-text atext]
      [:div.row {:style {:padding "0px 10px"}}
       [:div.col
        [:div.box
         [rui/flat-button
          {:label "Acrescentar"
           :icon (icons/content-add-circle)
           :disabled (or (empty? qtext) (empty? atext))
           :on-touch-tap (fn [_]
                           (chsk-send! [:db/ops [[:transact
                                                  [{:db/id [:?/tempid -1]
                                                    :question/body qtext
                                                    :question/answer atext
                                                    :question/owner user-id}
                                                   {:db/id [:?/tempid -2]
                                                    :lembrando/question [:?/tempid -1]}
                                                   [:db/add user-id :user/lembrandos [:?/tempid -2]]]]
                                                 [:query lembrandos-query [user-id]]]]
                                       10000
                                       (fn [resp]
                                         (if-not (cb-success? resp)
                                           (.log js/console (str "Error in transaction/query!: " (pr-str resp)))
                                           (transact-fetch-results (second resp) user-id)))))}]]]]]]))

(defn loading []
  [:div.container
   [:div.row.center-xs {:style {:margin-top 50 :padding-left "1em"}}
    [:h1 {:style {:font-family "Roboto" :font-weight 300 :color (ui/color :teal600)}} "Carregando..."]]
   [:div.row.center-xs
    [rui/circular-progress]]])

(defn welcome []
  (let [lembrandos (d/q '[:find [?l ...] :where [?l :lembrando/question]] @conn)]
    [screen "Bem vindo!"
     (if (> (count lembrandos) 0)
       [:div (str "Tes " (count lembrandos)" lembrandos!")]
       [:div "Nom tes!"])]))

(def screens {:loading loading
              :welcome welcome
              :add-lembrando add-lembrando})

(defn app []
  [rui/mui-theme-provider
   {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :teal600)}})}
   [(screens (posh-get0 :screen/current))]])

(defonce _start-once (start-router!))

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
