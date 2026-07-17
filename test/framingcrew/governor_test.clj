(ns framingcrew.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [framingcrew.store :as store]
            [framingcrew.advisor :as advisor]
            [framingcrew.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-site! st {:site-id "site-1" :name "North Ridge Framing Site"})
    (store/register-crew! st {:crew-id "crew-1" :site-id "site-1"
                              :name "Crew Alpha"
                              :max-supply-order-cost 5000})
    st))

(def ^:private req {:site-id "site-1"})

(defn- log-op []
  {:op :log-work-record :effect :propose :site-id "site-1" :crew-id "crew-1"
   :task-id "T-1" :materials-used {:2x4-lumber 40} :progress-pct 65
   :confidence 0.9 :stake :low})

(defn- schedule-op []
  {:op :schedule-crew-operation :effect :propose :site-id "site-1" :crew-id "crew-1"
   :task-id "T-2" :scheduled-start "2026-07-20T07:00:00Z" :scheduled-end "2026-07-20T15:00:00Z"
   :confidence 0.9 :stake :low})

(defn- safety-op []
  {:op :flag-safety-concern :effect :propose :site-id "site-1" :crew-id "crew-1"
   :concern-type :fall-hazard :description "unguarded floor opening on level 3"
   :confidence 0.9 :stake :low})

(defn- supply-op [cost]
  {:op :coordinate-supply-order :effect :propose :site-id "site-1" :crew-id "crew-1"
   :material :2x4-lumber :quantity 200 :cost cost
   :confidence 0.9 :stake :low})

(deftest ok-log-work-record-for-registered-crew
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-schedule-crew-operation-for-registered-crew
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op) st)]
    (is (:ok? v))))

(deftest hard-on-unregistered-site
  (let [st (fresh-store)
        v (governor/check {:site-id "ghost-site"} {} (log-op) st)]
    (is (:hard? v))
    (is (some #(= :no-site (:rule %)) (:violations v)))))

(deftest hard-on-unknown-crew
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :crew-id "crew-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-crew (:rule %)) (:violations v)))))

(deftest hard-on-crew-wrong-site
  (let [st (fresh-store)]
    (store/register-site! st {:site-id "site-2" :name "Other Site"})
    (let [v (governor/check {:site-id "site-2"} {} (log-op) st)]
      (is (:hard? v))
      (is (some #(= :crew-wrong-site (:rule %)) (:violations v))))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-unknown-op-closed-allowlist
  (testing "closed op-allowlist enforced — an op outside the four known proposal ops is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :finalize-framing-installation) st)]
      (is (:hard? v))
      (is (some #(= :unknown-op (:rule %)) (:violations v))))))

(deftest hard-on-named-scope-excluded-op-finalize-framing-execution
  (testing "a concretely-named finalization op is a second, independent hard, permanent block on top of the closed allowlist"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :finalize-framing-execution) st)]
      (is (:hard? v))
      (is (some #(= :unknown-op (:rule %)) (:violations v)))
      (is (some #(= :scope-excluded-op (:rule %)) (:violations v))))))

(deftest hard-on-named-scope-excluded-op-override-site-safety-officer-judgment
  (testing "an op that would directly override the site safety officer's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op) :op :override-site-safety-officer-judgment) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-op (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-proceed-with-framing-work
  (testing "a proposal to directly finalize a structural-framing-execution decision is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :rationale "recommend we proceed with the framing work now") st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-rationale (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-override-safety-officer
  (testing "a proposal to override the site safety officer's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op) :description
                                          "override the site safety officer's judgment and proceed anyway") st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-rationale (:rule %)) (:violations v))))))

(deftest always-escalates-flag-safety-concern-even-at-high-confidence
  (testing "this actor only surfaces safety concerns, it never adjudicates them"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op) :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest ok-supply-order-at-exact-ceiling-boundary
  (testing "the supply-order cost ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (supply-op 5000) st)]
      (is (:ok? v))
      (is (not (:escalate? v))))))

(deftest escalates-supply-order-above-cost-ceiling
  (testing "a framing-materials order above the crew's registered ceiling is not routine logistics"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (supply-op 8000) :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip
  (testing "the governor's scope-exclusion phrases never accidentally match the mock advisor's own default rationale text for any allowlisted op"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:op :log-work-record :site-id "site-1" :crew-id "crew-1"
                     :task-id "T-1" :materials-used {:2x4-lumber 40} :progress-pct 65 :stake :low}
                    {:op :schedule-crew-operation :site-id "site-1" :crew-id "crew-1"
                     :task-id "T-2" :scheduled-start "2026-07-20T07:00:00Z"
                     :scheduled-end "2026-07-20T15:00:00Z" :stake :low}
                    {:op :flag-safety-concern :site-id "site-1" :crew-id "crew-1"
                     :concern-type :fall-hazard :description "unguarded floor opening on level 3" :stake :low}
                    {:op :coordinate-supply-order :site-id "site-1" :crew-id "crew-1"
                     :material :2x4-lumber :quantity 200 :cost 1200 :stake :low}]]
      (doseq [request requests]
        (let [proposal (advisor/-advise adv st request)
              v (governor/check request {} proposal st)]
          (is (not (:hard? v)) (str "op " (:op request) " unexpectedly hard-blocked: " (:violations v)))
          (is (not (some #(= :scope-excluded-rationale (:rule %)) (:violations v)))
              (str "op " (:op request) " self-tripped the scope-exclusion phrase list"))
          (is (not (some #(= :scope-excluded-op (:rule %)) (:violations v)))
              (str "op " (:op request) " self-tripped the named scope-excluded-op list")))))))
