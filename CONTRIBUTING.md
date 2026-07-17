# Contributing

`cloud-itonami-isco-7119` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for policy, audit, store or disclosure
behavior.

## Rules

- Do not commit real crew data, credentials or operating documents.
- Keep production writes and disclosures behind Framing Crew Governor.
- Never widen the closed proposal-op allowlist to include an op that
  finalizes a structural-framing-execution decision or overrides a
  site-safety officer's judgment — that boundary is permanent.
- Treat this occupation's workflows as high-risk: add tests for permission,
  purpose, safety and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
