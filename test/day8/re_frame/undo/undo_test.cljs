(ns day8.re-frame.undo.undo-test
  (:require [cljs.test          :refer-macros [is deftest]]
            [day8.re-frame.undo :as undo]
            [re-frame.db        :as db]
            [re-frame.core      :as re-frame]))

(defn undo-fixtures
  [f]
  (reset! db/app-db {})
  (re-frame/clear-all-events!)
  (undo/register-events-subs!)
  ;; Create undo history
  (undo/undo-config! {:max-undos 5})
  (undo/clear-history!)
  (f))

(cljs.test/use-fixtures :each undo-fixtures)

(deftest test-undos
  (is (not (undo/undos?)))
  (is (not (undo/redos?)))

  (doseq [i (range 10)]
    (undo/store-now! (inc i))
    (reset! db/app-db {:test (inc i)}))

  ;; Check the undo state is correct
  (is (undo/undos?))
  (is (not (undo/redos?)))
  (is (= [5 6 7 8 9 10] (undo/undo-explanations)))
  (is (= [{:test 5} {:test 6} {:test 7} {:test 8} {:test 9}] @undo/undo-list))

  ;; Undo the actions
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 9}))
  (is (undo/redos?))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 8}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 7}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 6}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 5}))
  (is (not (undo/undos?)))
  (is (undo/redos?))

  ;; Redo them again
  (re-frame/dispatch-sync [:redo 5])
  (is (= @db/app-db {:test 10}))
  (is (not (undo/redos?)))
  (is (undo/undos?))
  (is (= [{:test 5} {:test 6} {:test 7} {:test 8} {:test 9}]
         @undo/undo-list))

  ;; Clear history
  (undo/clear-history!)
  (is (not (undo/undos?)))
  (is (not (undo/redos?))))


(deftest test-undos-interceptor

  (re-frame/reg-event-db
    :change-db
    (undo/undoable "change-db")
    (fn [db _]
      (update db :test inc)))

  (doseq [i (range 10)]
    (re-frame/dispatch-sync [:change-db]))

  ;; Check the undo state is correct
  (is (undo/undos?))
  (is (not (undo/redos?)))
  (is (= ["change-db" "change-db" "change-db" "change-db" "change-db" "change-db"]
         (undo/undo-explanations)))
  (is (= [{:test 5} {:test 6} {:test 7} {:test 8} {:test 9}]
         @undo/undo-list))

  ;; Undo the actions
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 9}))
  (is (undo/redos?))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 8}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 7}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 6}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 5}))
  (is (not (undo/undos?)))
  (is (undo/redos?))

  ;; Redo them again
  (re-frame/dispatch-sync [:redo 5])
  (is (= @db/app-db {:test 10}))
  (is (not (undo/redos?)))
  (is (undo/undos?))
  (is (= [{:test 5} {:test 6} {:test 7} {:test 8} {:test 9}]
         @undo/undo-list)))

#_(deftest test-undos-fx

  (re-frame/reg-event-fx
    :change-db-fx
    undo/undo-fx
    (fn [{:keys [db]} _]
      (let [update-num (inc (:test db))]
        {:db (assoc db :test update-num)
         :undo (str "change-db " update-num)})))

  (doseq [i (range 10)]
    (re-frame/dispatch-sync [:change-db-fx]))

  ;; Check the undo state is correct
  (is (undo/undos?))
  (is (not (undo/redos?)))
  (is (= ["change-db 5" "change-db 6" "change-db 7" "change-db 8" "change-db 9"
          "change-db 10"]
         (undo/undo-explanations)))
  (is (= [{:test 5} {:test 6} {:test 7} {:test 8} {:test 9}]
         @undo/undo-list))

  ;; Undo the actions
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 9}))
  (is (undo/redos?))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 8}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 7}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 6}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {:test 5}))
  (is (not (undo/undos?)))
  (is (undo/redos?))

  ;; Redo them again
  (re-frame/dispatch-sync [:redo 5])
  (is (= @db/app-db {:test 10}))
  (is (not (undo/redos?)))
  (is (undo/undos?))
  (is (= [{:test 5} {:test 6} {:test 7} {:test 8} {:test 9}]
         @undo/undo-list)))
