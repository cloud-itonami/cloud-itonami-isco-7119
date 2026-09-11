(ns framingcrew.advisor
  "Framing Crew Advisor — the advisor named in this repository's
  README, proposing a job-site scheduling/logistics operation (log a
  work record, schedule a crew operation, flag a safety concern,
  coordinate a framing-materials supply order) from a job-site work
  order, crew roster and materials plan. Swappable mock/llm; the
  advisor ONLY proposes — `framingcrew.governor` independently checks
  site/crew provenance, the closed op-allowlist, the
  structural-framing-execution/site-safety-officer-override scope
  exclusion and the supply-order cost ceiling, and always escalates
  safety flags and over-ceiling supply orders. Modeled on
  cloud-itonami-isco-3313's accountingsupport.advisor.

  This advisor coordinates JOB-SITE SCHEDULING/LOGISTICS ONLY — it
  never proposes to perform framing work itself, finalize a
  structural-framing-execution decision or override a site-safety
  officer's judgment.

  A proposal:
  {:op :log-work-record|:schedule-crew-operation|:flag-safety-concern|:coordinate-supply-order
   :effect :propose :site-id str :crew-id str
   ... op-specific fields (:task-id/:materials-used/:progress-pct,
       :scheduled-start/:scheduled-end, :concern-type/:description,
       :material/:quantity/:cost) carried through from the request ...
   :stake kw :confidence n :rationale str}"
  )

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op site-id stake] :as request}]
  (merge (dissoc request :stake)
         {:op op
          :effect :propose
          :site-id site-id
          :stake (or stake :low)
          :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
          :rationale (str "proposed " (name op) " for site " site-id)}))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a framing-crew scheduling and logistics advisor. Given a
   request, propose an :op from the closed allowlist
   (:log-work-record, :schedule-crew-operation, :flag-safety-concern,
   :coordinate-supply-order), the :site-id, :crew-id and any
   op-specific fields, an honest :confidence and a :stake. You never
   propose to finalize a structural-framing-execution decision or
   override a site-safety officer's judgment — the governor checks
   both against a closed scope-exclusion list and blocks them
   permanently. Safety flags and materials orders above the crew's
   registered cost ceiling always require human sign-off regardless
   of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
