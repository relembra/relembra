(ns relembra.search
  (:require [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.reagent :as rui]
            [cljs-react-material-ui.icons :as icons]
            [posh.reagent :as p]
            [relembra.posh-conn :as pc]
            [relembra.util :as util]))

(enable-console-print!)

(defn string-contains?
  "Case insensitive."
  [s ss]
  (not= -1 (.indexOf (.toLowerCase s) (.toLowerCase ss))))

(defn search []
  (let [search-text (or (pc/posh-get0 :search/text) "")
        results (if (empty? search-text)
                  []
                  @(p/q '[:find ?l ?q ?b ?a
                          :where
                          [?l :lembrando/question ?q]
                          [?q :question/body ?b]
                          [?q :question/answer ?a]]
                        pc/conn))
        results (sort-by first
                         (filter (fn [[lid qid q a]]
                                   (or (string-contains? q search-text)
                                       (string-contains? a search-text)))
                                 results))]
    [util/screen "Procura pergunta"
     [:div
      [util/text-field "Texto a procurar" :search/text search-text]
      (when-not (empty? search-text)
        (if (empty? results)
          [rui/paper {:style {:padding 20}} "Ningum resultado"]
          [:div
           [util/edit-modal]
           (for [[lid qid q a] results]
             [rui/paper {:key lid :style {:color (ui/color :teal900)}}
              [util/mathjax-box q]
              [util/mathjax-box a]
              [util/edit-button qid]
              [ui/flat-button
               {:label "Apagar"
                :icon (icons/action-delete)
                :on-touch-tap (fn [_]
                                (util/delete-lembrando lid))}]])]))]]))
