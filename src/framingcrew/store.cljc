(ns framingcrew.store
  "SSoT for the ISCO-08 7119 building frame and related trades crew
  scheduling/logistics actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a
  scheduling and materials-logistics robot performs job-site
  coordination under this advisor/governor pair, which never
  dispatches hardware itself, never finalizes a structural-framing-
  execution decision and never overrides a site-safety officer's
  judgment). Modeled on cloud-itonami-isco-3313's
  accountingsupport.store.

  This actor coordinates JOB-SITE SCHEDULING/LOGISTICS ONLY — it never
  performs framing work itself.

  Domain:

    site   — a registered job site {:site-id :name}
    crew   — a registered framing crew working a site {:crew-id
             :site-id :name :max-supply-order-cost number}.
             `:max-supply-order-cost` is the registered ceiling above
             which a materials-procurement proposal always escalates
             to human sign-off (a framing-materials order above the
             crew's registered ceiling is not routine logistics).
    record — a committed operating record (a logged work record, crew
             schedule, safety flag or supply order) — written ONLY
             via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (site [s site-id])
  (crew [s crew-id])
  (records-of [s site-id])
  (ledger [s])
  (register-site! [s site])
  (register-crew! [s c])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (site [_ site-id] (get-in @a [:sites site-id]))
  (crew [_ crew-id] (get-in @a [:crews crew-id]))
  (records-of [_ site-id] (filter #(= site-id (:site-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-site! [s site]
    (swap! a assoc-in [:sites (:site-id site)] site) s)
  (register-crew! [s c]
    (swap! a assoc-in [:crews (:crew-id c)] c) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:sites {} :crews {} :records [] :ledger []}
                                   seed)))))
