(ns relembra.reagent-hack
  (:require
   reagent.impl.template
   reagent.impl.component
   reagent.impl.batching
   [reagent.interop :refer-macros [$ $!]]))

;; A better but idiosyncratic way for reagent to locate the real
;; input element of a component. It may be that the component's
;; top-level (outer) element is an <input> already. Or it may be that
;; the outer element is a wrapper, and the real <input> is somewhere
;; within it (as is the case with Material TextFields).
;; Somehow the `:input` property seems to be pre-populated (in version
;; 0.6.0 of Reagent) with a reference to the real <input>, so we use
;; that if available.
;; At the time of writing, future versions of Reagent will likely
;; change this behavior, and a totally different patch will be
;; required for identifying the real <input> element.
(defn get-input-node [this]
  (let [outer-node ($ this :cljsInputElement)
        inner-node (when (and outer-node (.hasOwnProperty outer-node "input"))
                     ($ outer-node :input))]
    (if (and inner-node
             (= "INPUT" ($ inner-node :tagName)))
      inner-node
      outer-node)))

;; This is the same as reagent.impl.template/input-set-value except
;; that the `node` binding uses our `get-input-node` function. Even
;; the original comments are reproduced below.
(defn input-set-value
  [this]
  (when-some [node (get-input-node this)]
    ($! this :cljsInputDirty false)
    (let [rendered-value ($ this :cljsRenderedValue)
          dom-value      ($ this :cljsDOMValue)]
      (when (not= rendered-value dom-value)
        (if-not (and (identical? node ($ js/document :activeElement))
                  (reagent.impl.template/has-selection-api? ($ node :type))
                  (string? rendered-value)
                  (string? dom-value))
          ;; just set the value, no need to worry about a cursor
          (do
            ($! this :cljsDOMValue rendered-value)
            ($! node :value rendered-value))

          ;; Setting "value" (below) moves the cursor position to the
          ;; end which gives the user a jarring experience.
          ;;
          ;; But repositioning the cursor within the text, turns out to
          ;; be quite a challenge because changes in the text can be
          ;; triggered by various events like:
          ;; - a validation function rejecting a user inputted char
          ;; - the user enters a lower case char, but is transformed to
          ;;   upper.
          ;; - the user selects multiple chars and deletes text
          ;; - the user pastes in multiple chars, and some of them are
          ;;   rejected by a validator.
          ;; - the user selects multiple chars and then types in a
          ;;   single new char to repalce them all.
          ;; Coming up with a sane cursor repositioning strategy hasn't
          ;; been easy ALTHOUGH in the end, it kinda fell out nicely,
          ;; and it appears to sanely handle all the cases we could
          ;; think of.
          ;; So this is just a warning. The code below is simple
          ;; enough, but if you are tempted to change it, be aware of
          ;; all the scenarios you have handle.
          (let [node-value ($ node :value)]
            (if (not= node-value dom-value)
              ;; IE has not notified us of the change yet, so check again later
              (reagent.impl.batching/do-after-render #(input-set-value this))
              (let [existing-offset-from-end (- (count node-value)
                                               ($ node :selectionStart))
                    new-cursor-offset        (- (count rendered-value)
                                               existing-offset-from-end)]
                ($! this :cljsDOMValue rendered-value)
                ($! node :value rendered-value)
                ($! node :selectionStart new-cursor-offset)
                ($! node :selectionEnd new-cursor-offset)))))))))


;; This is the same as `reagent.impl.template/input-handle-change`
;; except that the reference to `input-set-value` points to our fn.
(defn input-handle-change
  [this on-change e]
  ($! this :cljsDOMValue (-> e .-target .-value))
  ;; Make sure the input is re-rendered, in case on-change
  ;; wants to keep the value unchanged
  (when-not ($ this :cljsInputDirty)
    ($! this :cljsInputDirty true)
    (reagent.impl.batching/do-after-render #(input-set-value this)))
  (on-change e))


;; This is the same as `reagent.impl.template/input-render-setup`
;; except that the reference to `input-handle-change` points to our fn.
(defn input-render-setup
  [this jsprops]
  ;; Don't rely on React for updating "controlled inputs", since it
  ;; doesn't play well with async rendering (misses keystrokes).
  (when (and (some? jsprops)
          (.hasOwnProperty jsprops "onChange")
          (.hasOwnProperty jsprops "value"))
    (let [v         ($ jsprops :value)
          value     (if (nil? v) "" v)
          on-change ($ jsprops :onChange)]
      (when (nil? ($ this :cljsInputElement))
        ;; set initial value
        ($! this :cljsDOMValue value))
      ($! this :cljsRenderedValue value)
      (js-delete jsprops "value")
      (doto jsprops
        ($! :defaultValue value)
        ($! :onChange #(input-handle-change this on-change %))
        ($! :ref #($! this :cljsInputElement %1))))))


;; This version of `reagent.impl.template/input-component?` is
;; effectively the same as before except that it also detects Material's
;; wrapped components as input components. It does this by looking for
;; a property called "inputStyle" as an indicator. (Perhaps not a
;; robust test...)
;;
;; By identifying input components more liberally, Material textfields
;; are permitted into the code path that manages caret positioning
;; and selection state awareness, in reaction to updates. This alone
;; is necessary but insufficient.
(defn input-component?
  [x]
  (or (= x "input")
    (= x "textarea")
    (when-let [prop-types ($ x :propTypes)]
      ;; Material inputs all have "inputStyle" prop
      (and (aget prop-types "inputStyle")
        ;; But we only want text-fields, so let's exclude radio/check inputs
        (not (aget prop-types "checked"))
        ;; TODO: ... and other non-text-field inputs?
        ))))


;; This is the same as `reagent.impl.template/input-spec` except that
;; the reference to `input-render-setup` points to our fn.
(def input-spec
  {:display-name "ReagentInput"
   :component-did-update input-set-value
   :reagent-render
   (fn [argv comp jsprops first-child]
     (let [this reagent.impl.component/*current-component*]
       (input-render-setup this jsprops)
       (reagent.impl.template/make-element argv comp jsprops first-child)))})


;; This is the same as `reagent.impl.template/reagent-input` except
;; that the reference to `input-spec` points to our definition.
(defn reagent-input []
  (when (nil? reagent.impl.template/reagent-input-class)
    (set! reagent.impl.template/reagent-input-class (reagent.impl.component/create-class input-spec)))
  reagent.impl.template/reagent-input-class)


;; Now we override the existing functions in `reagent.impl.template`
;; with our own definitions.
(defn set-overrides!
  []
  (set! reagent.impl.template/input-component? input-component?)
  (set! reagent.impl.template/input-handle-change input-handle-change)
  (set! reagent.impl.template/input-set-value input-set-value)
  (set! reagent.impl.template/input-render-setup input-render-setup)
  (set! reagent.impl.template/input-spec input-spec)
  (set! reagent.impl.template/reagent-input reagent-input))
