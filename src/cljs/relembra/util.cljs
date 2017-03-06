(ns relembra.util
  (:require [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as icons]
            [markdown.core :refer (md->html)]
            [datascript.core :as d]
            [posh.reagent :as p]
            [reagent.core :as r]
            [relembra.posh-conn :as pc]
            [relembra.reagent-hack :refer (synthetic-text-field)]
            [relembra.sente :as sente]
            [taoensso.sente :refer (cb-success?)]))

(defn toggle-drawer [b]
  (pc/set0! :drawer/open b))

(defn open-drawer [& args]
  (toggle-drawer true))

(defn close-drawer [& args]
  (toggle-drawer false))

(defn drawer []
  (let [open (pc/posh-get0 :drawer/open)]
    [rui/drawer
     {:docked false
      :width 200
      :open open
      :on-request-change toggle-drawer}
     [rui/menu-item {:on-touch-tap
                     (fn [x]
                       (pc/set0! :drawer/open false :screen/current :welcome))}
      "Resumo"]
     [rui/menu-item {:on-touch-tap
                     (fn[x]
                       (pc/set0! :drawer/open false :screen/current :add-lembrando))}
      "Acrescenta pergunta"]
     [rui/menu-item {:on-touch-tap
                     (fn[x]
                       (pc/set0! :drawer/open false :screen/current :search))}
      "Procura perguntas"]]))

(defn screen [title & contents]
  [rui/mui-theme-provider
   {:mui-theme (ui/get-mui-theme
                {:palette {:text-color (ui/color :teal700)}})}
   (into [:div
          [rui/app-bar {:title title
                        :on-left-icon-button-touch-tap open-drawer}]
          [drawer]]
         contents)])

(defn text-field [title value-k text]
  [synthetic-text-field
   {:floating-label-text title
    :multi-line true
    :rows 1
    :full-width true
    :value text
    :style {:font-family "Hack, monospace" :font-size "90%"}
    :on-change (fn [e]
                 (let [new-value (.. e -target -value)]
                   (pc/set0! value-k new-value)))}])

(defn typeset [c]
  (js/MathJax.Hub.Queue (array "Typeset" js/MathJax.Hub (r/dom-node c))))

(defn markdown-box [text]
  [:div {:style {:font-family "Yrsa, serif" :font-size "120%"}
         :dangerouslySetInnerHTML {:__html (md->html text :inhibit-separator "$")}}])

(def mathjax-box
  (with-meta markdown-box
    {:component-did-mount typeset
     :component-did-update typeset}))

(defn md-editor [title value-k text]
  [:div.row.around-xs {:style {:margin-top "1em" :margin-bottom "1em"}}
   [:div.col-xs-12
    [text-field title value-k text]]
   [:div.col-xs-12 {:style {:padding-top "0.5em"}}
    [mathjax-box text]]])

(defn edit-modal []
  (let [qid (pc/posh-get0 :modal/editing)
        {:keys [question/body question/answer]}
        (when qid
          @(p/pull pc/conn '[:question/body :question/answer] qid))
        close #(pc/set0! :modal/editing false
                         :edit-modal/question-text false
                         :edit-modal/answer-text false)
        qtext
        (or
         (pc/posh-get0 :edit-modal/question-text)
         body)
        atext
        (or (pc/posh-get0 :edit-modal/answer-text)
            answer)]
    [rui/dialog {:open (boolean qid)
                 :actions [(r/as-element
                            [rui/flat-button
                             {:label "Cancelar"
                              :icon (icons/navigation-cancel)
                              :on-touch-tap close}])
                           (r/as-element
                            [rui/flat-button
                             {:label "Aplicar"
                              :icon (icons/action-done)
                              :on-touch-tap
                              (let [txn
                                    [{:db/id qid
                                      :question/body qtext
                                      :question/answer atext}]]
                                #(sente/send!
                                  [:db/ops [[:transact txn]]]
                                  10000
                                  (fn [resp]
                                    (if (cb-success? resp)
                                      (d/transact! pc/conn txn)
                                      (js/alert "Error tratando de anovar pergunta: "
                                                (pr-str resp)))
                                    (close))))}])]
                 :modal true
                 :onRequestClose close
                 :autoScrollBodyContent false}

     [:div.container
      [md-editor "Pergunta" :edit-modal/question-text qtext]
      [md-editor "Resposta" :edit-modal/answer-text atext]]]))

(defn edit-button [qid]
  [:span
   [ui/flat-button
    {:label "Editar"
     :icon (icons/editor-mode-edit)
     :on-touch-tap #(pc/set0! :modal/editing qid)}]])

(defn delete-lembrando [lembrando]
  (let [txn [[:db.fn/retractEntity lembrando]]]
    (sente/send!
     [:db/ops [[:transact txn]]]
     10000
     (fn [resp]
       (if (cb-success? resp)
         (d/transact! pc/conn txn)
         (js/alert "Error tratando de apagar pergunta: "
                   (pr-str resp)))))))
