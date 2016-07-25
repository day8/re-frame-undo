(ns day8.re-frame.undo
  (:require-macros [reagent.ratom  :refer [reaction]])
  (:require
    [reagent.core        :as     reagent]
    [re-frame.core       :as     re-frame]
    [re-frame.db         :refer  [app-db]]))


;;  background docs:  https://github.com/Day8/re-frame/wiki/Undo-&-Red


;; -- Configuration ----------------------------------------------------------

(def ^:private config (atom {:max-undos    50   ;; Maximum number of undo states maintained
                             :harvest-fn   deref
                             :reinstate-fn reset!}))

(defn undo-config!
  [new-config]
  (if-let [unknown-keys (seq (clojure.set/difference (-> new-config keys set) (-> @config keys set)))]
    (re-frame/console :warn "re-frame: you called undo-config! within unknown keys: " unknown-keys)
    (swap! config merge new-config)))


(defn max-undos
  []
  (:max-undos @config))



;; -- State history ----------------------------------------------------------

(def ^:private undo-list "A list of history states" (reagent/atom []))
(def ^:private redo-list "A list of future states, caused by undoing" (reagent/atom []))

;; -- Explanations -----------------------------------------------------------
;;
;; Each undo has an associated explanation which can be displayed to the user.
;;
;; Seems really ugly to have mirrored vectors, but ...
;; the code kinda falls out when you do. I'm feeling lazy.
(def ^:private app-explain "Mirrors app-db" (reagent/atom ""))
(def ^:private undo-explain-list "Mirrors undo-list" (reagent/atom []))
(def ^:private redo-explain-list "Mirrors redo-list" (reagent/atom []))

(defn clear-undos!
  []
  (reset! undo-list [])
  (reset! undo-explain-list []))


(defn clear-redos!
  []
  (reset! redo-list [])
  (reset! redo-explain-list []))


(defn clear-history!
  []
  (clear-undos!)
  (clear-redos!)
  (reset! app-explain ""))


(defn store-now!
  "Stores the value currently in app-db, so the user can later undo"
  [explanation]
  (clear-redos!)
  (reset! undo-list (vec (take-last
                           (max-undos)
                           (conj @undo-list ((:harvest-fn @config) app-db)))))
  (reset! undo-explain-list (vec (take-last
                                   (max-undos)
                                   (conj @undo-explain-list @app-explain))))
  (reset! app-explain explanation))


(defn undos?
  "Returns true if undos exist, false otherwise"
  []
  (seq @undo-list))

(defn redos?
  "Returns true if redos exist, false otherwise"
  []
  (seq @redo-list))

(defn undo-explanations
  "Returns a vector of undo descriptions, perhaps empty"
  []
  (if (undos?)
    (conj @undo-explain-list @app-explain)
    []))

;; -- subscriptions  -----------------------------------------------------------------------------

(re-frame/reg-sub-raw
  :undos?                   ;;  usage:  (subscribe [:undos?])
  (fn handler
    ; "returns true if anything is stored in the undo list, otherwise false"
    [_ _]
    (reaction (undos?))))

(re-frame/reg-sub-raw
  :redos?
  (fn handler
    ; "returns true if anything is stored in the redo list, otherwise false"
    [_ _]
    (reaction (redos?))))


(re-frame/reg-sub-raw
  :undo-explanations
  (fn handler
    ; "returns a vector of string explanations ordered oldest to most recent"
    [_ _]
    (reaction (undo-explanations))))

(re-frame/reg-sub-raw
  :redo-explanations
  (fn handler
    ; "returns a vector of string explanations ordered from most recent undo onward"
    [_ _]
    (reaction (deref redo-explain-list))))

;; -- event handlers  ----------------------------------------------------------------------------


(defn undo
  [harvester reinstater undos cur redos]
  (let [u @undos
        r (cons (harvester cur) @redos)]
    (reinstater cur (last u))
    (reset! redos r)
    (reset! undos (pop u))))


(defn undo-n
  "undo n steps or until we run out of undos"
  [n]
  (when (and (pos? n) (undos?))
    (undo (:harvest-fn @config) (:reinstate-fn @config) undo-list app-db redo-list)
    (undo deref reset! undo-explain-list app-explain redo-explain-list)
    (recur (dec n))))

(defn undo-handler
  [_ [_ n]]
  (if-not (undos?)
    (re-frame/console :warn "re-frame: you did a (dispatch [:undo]), but there is nothing to undo.")
    (undo-n (or n 1)))
  {}) ; work is done directly on app-db

(defn redo
  [harvester reinstater undos cur redos]
  (let [u (conj @undos (harvester cur))
        r  @redos]
    (reinstater cur (first r))
    (reset! redos (rest r))
    (reset! undos u)))

(defn redo-n
  "redo n steps or until we run out of redos"
  [n]
  (when (and (pos? n) (redos?))
    (redo (:harvest-fn @config) (:reinstate-fn @config) undo-list app-db redo-list)
    (redo deref reset! undo-explain-list app-explain redo-explain-list)
    (recur (dec n))))

(defn redo-handler
  [_ [_ n]]  ;; if n absent, defaults to 1
    (if-not (redos?)
      (re-frame/console :warn "re-frame: you did a (dispatch [:redo]), but there is nothing to redo.")
      (redo-n (or n 1)))
    {})      ; work is done directly on app-db

(defn purge-redo-handler
  [db _]
  (if-not (redos?)
    (re-frame/console :warn "re-frame: you did a (dispatch [:purge-redos]), but there is nothing to redo.")
    (clear-redos!))
  db)

;; -- Middleware ----------------------------------------------------------

(defn undoable_
  "A Middleware factory which stores an undo checkpoint.
  \"explanation\" can be either a string or a function. If it is a
  function then must be:  (db event-vec) -> string.
  \"explanation\" can be nil. in which case \"\" is recorded.
  "
  [explanation]
  (fn undoable-middleware
    [handler]
    (fn undoable-handler
      [db event-vec]
      (let [explanation (cond
                          (fn? explanation)     (explanation db event-vec)
                          (string? explanation) explanation
                          (nil? explanation)    ""
                          :else (re-frame/console :error "re-frame: \"undoable\" middleware given a bad parameter. Got: " explanation))]
        (store-now! explanation)
        (handler db event-vec)))))

(def undoable (with-meta undoable_ {:re-frame-factory-name "undoable"}))

(defn undo-fx
  "Middleware that wraps the function in an undo but takes its arguements
  from the `:undo` key in the return"
  [handler]
  (fn undo-fx-hander
    [world event-vec]
    (let [ret (handler world event-vec)]
      (store-now! (:undo ret))
      (dissoc ret :undo))))

;; ====== actually register the  events and subscriptions


(defn register-events-subs!
  []
  (re-frame/reg-event-fx
    :undo                     ;; usage:  (dispatch [:undo n])  n is optional, defaults to 1
    undo-handler)
  (re-frame/reg-event-fx
    :redo                     ;; usage:  (dispatch [:redo n])
    redo-handler)
  (re-frame/reg-event
    :purge-redos              ;; usage:  (dispatch [:purge-redos])
    purge-redo-handler))

(register-events-subs!)
