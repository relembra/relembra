(ns relembra.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [cljsjs.material-ui]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as icons]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
            [datascript.core :as d]
            [posh.reagent :as p]
            [reagent.core :as r]
            [relembra.posh-conn :as pc :refer (conn get0 posh-get0 set0!)]
            [relembra.search :as search]
            [relembra.sente :as sente]
            [relembra.util :as util :refer (screen)]
            [taoensso.sente :refer (cb-success?)]))

(enable-console-print!)

(def lembrandos-query '[:find ((pull ?l [* {:lembrando/question [*]}]) ...)
                        :in $ ?u
                        :where
                        [?u :user/lembrandos ?l]])

(defn lembrando-query-results->txn [results user-id]
  ;; XXX: doesn't allow removing lembrandos!
  (apply concat
         (for [entry results]
           [(update-in (:lembrando/question entry) [:question/owner] :db/id)
            (update-in entry [:lembrando/question] :db/id)
            [:db/add user-id :user/lembrandos (:db/id entry)]])))

(defn replace-dbids [x]
  (cond
    (and (map? x) (= (ffirst (vec x)) :db/id))
    (second (first (vec x)))
    (map? x) (into {} (for [[k v] x]
                        [k (replace-dbids v)]))
    (vector? x) (mapv replace-dbids x)
    :else x))

(defmethod sente/server-msg-handler :chsk/state
  [{[_ {:keys [uid] :as new-state-map}] :?data}]
  (if-not (:first-open? new-state-map)
    (.log js/console (str "Channel socket state change: " new-state-map))
    (sente/send! [:db/ops [[:query lembrandos-query [uid]]]]
                 10000
                 (fn [ret]
                   (.log js/console (str "Returned: " ret))
                   (if-not (cb-success? ret)
                     (js/alert (str "Erro tentando pegar datos iniciais:\n" ret))
                     (let [lembrandos (first ret)]
                       (if (= (count lembrandos) 0)
                         (set0! :user/id uid
                                :screen/current :add-lembrando)
                         (p/transact! conn
                                      (into [{:db/id 0
                                              :user/id uid
                                              :screen/current :welcome}]
                                            (lembrando-query-results->txn lembrandos uid))))))))))

(defn transact-fetch-results [res user-id]
  (.log js/console (str "successful result!" (pr-str res)))
  (p/transact! conn (lembrando-query-results->txn res user-id)))

(defn new-lembrando-txn [user-id qtext atext]
  [{:db/id [:?/tempid -1]
    :question/body qtext
    :question/answer atext
    :question/owner user-id}
   {:db/id [:?/tempid -2]
    :lembrando/due-date (time-coerce/to-date (time/epoch))
    :lembrando/question [:?/tempid -1]
    :lembrando/needs-repeat? false}
   [:db/add user-id :user/lembrandos [:?/tempid -2]]])

(defn add-lembrando []
  (let [user-id (posh-get0 :user/id)
        qtext (posh-get0 :addq/question-text)
        atext (posh-get0 :addq/answer-text)]
    [screen "Acrescenta pergunta"
     [:div.container
      [util/md-editor "Pergunta" :addq/question-text qtext]
      [util/md-editor "Resposta" :addq/answer-text atext]
      [:div.row {:style {:padding "0px 10px"}}
       [:div.col
        [:div.box
         [rui/flat-button
          {:label "Acrescentar"
           :icon (icons/content-add-circle)
           :disabled (or (empty? qtext) (empty? atext))
           :on-touch-tap (fn [_]
                           (sente/send! [:db/ops [[:transact (new-lembrando-txn user-id qtext atext)]
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

(defn past? [t]
  (time/after? (time/now)
               (time-coerce/from-date t)))

(defn lembrandos []
  (let [all-ids @(p/q '[:find [?l ...] :where [?l :lembrando/question]] conn)
        all-entries (doall (for [id all-ids]
                             @(p/pull conn '[:db/id :lembrando/needs-repeat? :lembrando/due-date] id)))
        due-entries (filter (fn [e] (or (:lembrando/needs-repeat? e)
                                       (past? (:lembrando/due-date e))))
                            all-entries)]
    [all-entries due-entries]))

(defn welcome []
  (let [[all due] (lembrandos)]
    [screen "Bem vindo!"
     [:div
      [rui/paper
       [:div {:style {:padding 20}}
        (if (> (count all) 0)
          (str "Tes " (count all) " perguntas em total, " (count due) " prontas para repassar.")
          "Nom tes!")]
       [:div
        (when (> (count due) 0)
          [rui/flat-button
           {:label "Repassar"
            :icon (icons/social-school)
            :on-touch-tap (fn [_]
                            (set0! :screen/current :review))}])]]]]))

(defn captioned-markdown [caption text]
  [:div {:style {:padding "1em"}}
   [:div {:style {:font-size "80%" :color (ui/color :teal400)}}
    caption]
   [:div {:style {:font-family "Yrsa, serif" :font-size "120%" :color (ui/color :teal900)}}
    [util/mathjax-box text]]])

(defn rate-recall [lembrando rate]
  (sente/send!
   [:relembra/rate-recall {:lembrando lembrando
                           :rate rate}]
   10000
   (fn [resp]
     (if-not (cb-success? resp)
       (js/alert (str "Erro tentando ratear:\n" (pr-str resp)))
       (let [{:keys [needs-repeat? new-due-date]} resp]
         (d/transact! conn [{:db/id 0
                             :review/index (inc (or (get0 :review/index) 0))
                             :review/show-answer? false}
                            {:db/id lembrando
                             :lembrando/due-date new-due-date
                             :lembrando/needs-repeat? needs-repeat?}]))))))

(defn postpone [lembrando]
  (sente/send!
   [:relembra/postpone {:lembrando lembrando}]
   10000
   (fn [resp]
     (if-not (cb-success? resp)
       (js/alert (str "Erro tentando adiar:\n" (pr-str resp)))
       (let [{:keys [new-due-date]} resp]
         (d/transact! conn [[:db/add lembrando :lembrando/due-date new-due-date]]))))))

(defn review []
  (let [[all due] (lembrandos)]
    [screen "Repasso"
     (if (empty? due)
       [rui/paper
        [:div {:style {:padding 20}} "Nada a repassar!"]]
       (let [{need true need-not false}
             (group-by :lembrando/needs-repeat? due)
             group (sort-by :db/id (if (empty? need-not) need need-not))
             index (mod (or (posh-get0 :review/index) 0)
                        (count group))
             lembrando (:db/id (nth group index))
             question (:lembrando/question
                       @(p/pull conn
                               '[{:lembrando/question [*]}]
                               lembrando))
             show-answer? (posh-get0 :review/show-answer?)]
         [rui/paper
          (captioned-markdown "Pergunta" (:question/body question))
          (if show-answer?
            [:div
             (captioned-markdown "Resposta" (:question/answer question))
             [:div
              [ui/flat-button
               {:label "NPI"
                :icon (icons/social-sentiment-very-dissatisfied)
                :on-touch-tap #(rate-recall lembrando 1)}]
              [rui/flat-button
               {:label "Algo sona-me"
                :icon (icons/social-sentiment-dissatisfied)
                :on-touch-tap #(rate-recall lembrando 2)}]
              [rui/flat-button
               {:label "Assi-assi"
                :icon (icons/social-sentiment-neutral)
                :on-touch-tap #(rate-recall lembrando 3)}]
              [rui/flat-button
               {:label "Custou!"
                :icon (icons/social-sentiment-very-satisfied)
                :on-touch-tap #(rate-recall lembrando 4)}]
              [rui/flat-button
               {:label "Bah, chupado"
                :icon (icons/social-sentiment-satisfied)
                :on-touch-tap #(rate-recall lembrando 5)}]]
             [:div
              [util/edit-button (:db/id question)]
              [util/edit-modal]
              [ui/flat-button
               {:label "Apagar"
                :icon (icons/action-delete)
                :on-touch-tap (fn [_]
                                (util/delete-lembrando lembrando)
                                (set0! :review/show-answer? false))}]]]
            [:div
             [rui/flat-button
              {:label "Mostrar resposta"
               :icon (icons/action-visibility)
               :on-touch-tap (fn [_]
                               (set0! :review/show-answer? true))}]
             [rui/flat-button
              {:label "Adiar"
               :icon (icons/av-skip-next)
               :on-touch-tap #(postpone lembrando)}]])]))]))

(def screens {:loading loading
              :welcome welcome
              :review review
              :search search/search
              :add-lembrando add-lembrando})

(defn app []
  [rui/mui-theme-provider
   {:mui-theme (ui/get-mui-theme {:palette {:text-color (ui/color :teal600)}})}
   [(screens (posh-get0 :screen/current))]])

(defn ^:export main []
  (r/render [app]
            (js/document.getElementById "app_container")))
