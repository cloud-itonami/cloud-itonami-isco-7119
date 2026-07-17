# cloud-itonami-isco-7119

Open Occupation Blueprint for **ISCO-08 7119**: Building Frame and Related Trades Workers Not Elsewhere Classified.

This repository designs a forkable OSS business for an independent framing crew: a scheduling and materials-logistics robot coordinates job-site work orders and supply runs under a governor-gated actor, so the crew keeps its own scheduling and safety records instead of renting a closed trades-management SaaS.

**Maturity: `:implemented`.** `src/framingcrew/` implements the
`FramingCrewActor` as a `langgraph.graph/state-graph`
(`framingcrew.actor`) wired to a `Framing Crew Advisor`
(`framingcrew.advisor`) and an independent `FramingCrewGovernor`
(`framingcrew.governor`), following the itonami actor pattern
(ADR-2607011000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 20 tests / 54 assertions green (`clojure -M:test`).

**This actor coordinates JOB-SITE SCHEDULING/LOGISTICS ONLY — it never
performs framing work itself.** The closed proposal-op allowlist is:

- `:log-work-record` — task/materials-usage/progress data logging
- `:schedule-crew-operation` — crew/task scheduling proposal
- `:flag-safety-concern` — surface a structural-integrity/fall-hazard/
  crew-fatigue concern (**always** escalates to human sign-off)
- `:coordinate-supply-order` — framing-materials procurement proposal

HARD invariants (always hold, never overridable): site provenance, a
registered crew basis for any crew-scoped proposal, no-actuation
(`:effect` must be `:propose`), the closed op-allowlist (any op
outside the four above — including one that would directly finalize
a structural-framing-execution decision — is a hard, permanent
block), a second, independently-named `scope-excluded-ops` check that
explicitly rejects a small concretely-named set of finalization/
override ops as defense-in-depth on top of the allowlist, and a
scope-exclusion text check that permanently blocks any proposal
reading as an attempt to proceed with the framing work itself or
override the site safety officer's judgment. Always-escalate (human
sign-off regardless of confidence, mapping this repo's Trust Controls
in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always), and `:coordinate-supply-order` above
the crew's registered `:max-supply-order-cost` ceiling.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a scheduling and materials-logistics robot performs job-site coordination — work-order tracking, crew scheduling, materials staging and safety-flag surfacing — under an actor that proposes
actions and an independent **Framing Crew Governor** that gates them. The governor never
dispatches hardware itself, never finalizes a structural-framing-execution decision and never overrides a site-safety officer's judgment; `:high`/`:safety-critical` actions (such as any surfaced safety concern, or a materials order above the crew's registered cost ceiling) require human sign-off.

## Core Contract

```text
job-site work order + crew roster + materials plan
        |
        v
Framing Crew Advisor -> Framing Crew Governor -> log/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a structural-framing-execution decision, override a
site-safety officer's judgment, suppress an operating record, or
disclose sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7119`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
