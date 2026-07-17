(ns framingcrew.governor
  "FramingCrewGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  job-site scheduling/logistics operation an advisor may propose. The
  governor never dispatches hardware itself, never finalizes a
  structural-framing-execution decision and never overrides a
  site-safety officer's judgment. Modeled on cloud-itonami-isco-3313's
  accountingsupport.governor.

  This actor coordinates JOB-SITE SCHEDULING/LOGISTICS ONLY — it never
  performs framing work itself.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. site provenance     — the job site must be registered.
    2. no-actuation         — proposal :effect must be :propose (the
                              governor never dispatches hardware and
                              never finalizes a
                              structural-framing-execution decision;
                              it only gates what the advisor may
                              propose).
    3. crew basis            — a proposal citing a crew must cite a
                              REGISTERED crew belonging to this site.
    4. closed op-allowlist   — :op must be one of
                              :log-work-record,
                              :schedule-crew-operation,
                              :flag-safety-concern,
                              :coordinate-supply-order. Any other op
                              is a hard, permanent block
                              (`:unknown-op`).
    5. named scope-excluded  — independent, defense-in-depth check on
       op                    top of #4: a small, concretely-named set
                              of ops that would finalize a
                              structural-framing-execution decision or
                              override a site-safety officer's
                              judgment (`scope-excluded-ops` below) is
                              ALSO explicitly rejected
                              (`:scope-excluded-op`), so an audit trail
                              can tell \"simply not on the allowlist\"
                              apart from \"named specifically as a
                              forbidden finalization/override action.\"
                              These ops are never, and will never be,
                              added to the allowlist.
    6. scope-excluded        — a proposal's rationale/description text
       rationale              must never read as an attempt to proceed
                              with the framing work itself or override
                              the site safety officer's judgment.
                              Matched as finalization/execution ACTION
                              phrases (`scope-exclusion-phrases`
                              below), never as bare nouns — a bare noun
                              like \"framing\" or \"safety\" would
                              false-trip on this domain's own op names
                              and default rationale text (e.g.
                              :flag-safety-concern's own rationale
                              literally contains the substring
                              \"safety\"). See the dedicated
                              `default-mock-advisor-proposals-never-self-trip`
                              test in governor_test.clj.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    7. :op :flag-safety-concern (any structural-integrity, fall-hazard
                              or crew-fatigue concern always requires
                              human sign-off — this actor only
                              surfaces the concern, it never
                              adjudicates it).
    8. supply-order cost exceeds the crew's registered
       `:max-supply-order-cost` (a framing-materials order above the
       crew's registered ceiling is not routine logistics).
    9. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [framingcrew.store :as store]))

(def confidence-floor 0.6)

(def ^:private known-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

;; Concretely-named ops that would finalize a structural-framing-
;; execution decision or override a site-safety officer's judgment.
;; None of these are ever, or will ever be, added to `known-ops` — this
;; set exists purely as a second, explicitly-labeled HARD check
;; (`:scope-excluded-op`) layered on top of the generic closed-
;; allowlist check (`:unknown-op`).
(def ^:private scope-excluded-ops
  #{:finalize-framing-execution :authorize-structural-work
    :proceed-with-framing-work :commit-framing-decision
    :finalize-structural-framing-decision
    :override-safety-officer-judgment
    :override-site-safety-officer-judgment})

;; Phrased as finalization/execution ACTIONS, never bare nouns. A bare
;; noun like "framing" or "safety" would match inside this domain's
;; OWN op names / default rationale text (e.g. "proposed
;; flag-safety-concern for site ..." literally contains "safety") and
;; cause the mock advisor's routine default proposals to false-trip
;; this permanent block. Full action phrases avoid that collision.
(def ^:private scope-exclusion-phrases
  ["proceed with the framing work"
   "finalize the framing work"
   "finalize the structural framing decision"
   "commit to the framing decision"
   "override the site safety officer's judgment"
   "override the safety officer's judgment"])

(defn- scope-violation-phrase [proposal]
  (let [text (str/lower-case (str (:rationale proposal) " " (:description proposal)))]
    (some (fn [phrase] (when (str/includes? text phrase) phrase)) scope-exclusion-phrases)))

(defn- hard-violations [{:keys [request proposal]} site-record crew-record]
  (let [{:keys [op crew-id]} proposal]
    (cond-> []
      (nil? site-record)
      (conj {:rule :no-site :detail "未登録 site"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は構造フレーミングの実行判断を直接確定しない）"})

      (not (contains? known-ops op))
      (conj {:rule :unknown-op :detail (str "closed op-allowlist 外の op: " (pr-str op))})

      (contains? scope-excluded-ops op)
      (conj {:rule :scope-excluded-op
             :detail (str "structural-framing-execution の確定または site safety officer の判断の上書きに該当する named op: "
                          (pr-str op))})

      (and crew-id (nil? crew-record))
      (conj {:rule :unknown-crew :detail "未登録 crew への提案は不可"})

      (and crew-id crew-record (not= (:site-id crew-record) (:site-id request)))
      (conj {:rule :crew-wrong-site :detail "crew が別 site のもの"})

      (scope-violation-phrase proposal)
      (conj {:rule :scope-excluded-rationale
             :detail (str "structural-framing-execution の確定または site safety officer の判断の上書きは permanent block: "
                          (scope-violation-phrase proposal))}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `framingcrew.store/Store`. Pure — never
  mutates the store, never finalizes a structural-framing-execution
  decision, never overrides a site-safety officer's judgment."
  [request context proposal store]
  (let [site-record (store/site store (:site-id request))
        crew-record (some->> (:crew-id proposal) (store/crew store))
        hard (hard-violations {:request request :proposal proposal} site-record crew-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        over-ceiling? (and (= :coordinate-supply-order (:op proposal))
                            (number? (:cost proposal))
                            crew-record
                            (number? (:max-supply-order-cost crew-record))
                            (> (:cost proposal) (:max-supply-order-cost crew-record)))
        safety-flag? (= :flag-safety-concern (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not over-ceiling?) (not safety-flag?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? over-ceiling? safety-flag?))}))
