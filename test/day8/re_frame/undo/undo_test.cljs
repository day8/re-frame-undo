(ns day8.re-frame.undo.undo-test
  (:require [cljs.test          :refer-macros [is deftest]]
            [day8.re-frame.undo :as undo]
            [re-frame.db        :as db]
            [re-frame.core      :as re-frame]))



(deftest test-undos
  (reset! db/app-db {})
  ;; Create undo history
  (undo/undo-config! {:max-undos 5})

  (undo/clear-history!)
  (is (not (undo/undos?)))
  (is (not (undo/redos?)))

  (doseq [i (range 10)]
    (reset! db/app-db {i i})
    (undo/store-now! i))

  ;; Check the undo state is correct
  (is (undo/undos?))
  (is (not (undo/redos?)))
  (is (= [4 5 6 7 8 9] (undo/undo-explanations)))
  (is (= [{5 5} {6 6} {7 7} {8 8} {9 9}] @undo/undo-list))

  ;; Undo the actions
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {9 9}))
  (is (undo/redos?))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {8 8}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {7 7}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {6 6}))
  (re-frame/dispatch-sync [:undo])
  (is (= @db/app-db {5 5}))
  (is (not (undo/undos?)))
  (is (undo/redos?))

  ;; Redo them again
  (re-frame/dispatch-sync [:redo 5])
  (is (= @db/app-db {9 9}))
  (is (not (undo/redos?)))
  (is (undo/undos?))
  (is (= [{5 5} {6 6} {7 7} {8 8} {9 9}] @undo/undo-list))

  ;; Clear history
  (undo/clear-history!)
  (is (not (undo/undos?)))
  (is (not (undo/redos?))))


(deftest test-undos-middleware
  (reset! db/app-db {})
  ;; Create undo history
  (undo/undo-config! {:max-undos 5})

  (undo/clear-history!)
  (is (not (undo/undos?)))
  (is (not (undo/redos?)))

  (re-frame/reg-event
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
         @undo/undo-list))

  ;; Clear history
  (undo/clear-history!)
  (is (not (undo/undos?)))
  (is (not (undo/redos?))))
