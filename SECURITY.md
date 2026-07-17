# Security Policy

This project handles building frame and related trades workers operating workflows. Treat
vulnerabilities as potentially high impact even when the demo data is
synthetic — this occupation's real-world stakes include falls, structural
collapse and equipment injury.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real crew or operator data exposure
- authorization bypass
- Framing Crew Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch
- any path that lets a proposal finalize a structural-framing-execution
  decision or override a site-safety officer's judgment

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
gftdcojp organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on crew data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real crew/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
