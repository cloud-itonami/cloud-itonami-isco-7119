(ns framingcrew.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [framingcrew.actor :as actor]
            [framingcrew.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-site! st {:site-id "site-1" :name "North Ridge Framing Site"})
    (store/register-crew! st {:crew-id "crew-1" :site-id "site-1"
                              :name "Crew Alpha"
                              :max-supply-order-cost 5000})
    st))

(deftest commits-a-log-work-record-for-a-registered-crew
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :log-work-record :site-id "site-1" :crew-id "crew-1"
                 :task-id "T-1" :materials-used {:2x4-lumber 40}
                 :progress-pct 65 :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "site-1"))))))

(deftest holds-a-proposal-for-an-unregistered-crew
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :schedule-crew-operation :site-id "site-1" :crew-id "ghost-crew"
                 :task-id "T-2" :scheduled-start "2026-07-20T07:00:00Z"
                 :scheduled-end "2026-07-20T15:00:00Z" :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "site-1")))))

(deftest interrupts-then-approves-a-safety-flag-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:op :flag-safety-concern :site-id "site-1" :crew-id "crew-1"
                 :concern-type :fall-hazard
                 :description "unguarded floor opening on level 3" :stake :low}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "site-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "site-1")))))))
