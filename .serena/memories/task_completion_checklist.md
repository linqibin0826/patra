# Task Completion Checklist (DoD)

## Every Story/PR
- ADR created/updated if architecture decisions changed.
- Service README updated (responsibilities, ports, contracts, runbooks links).
- API/Event contracts updated or explicitly confirmed unchanged.
- Tests added/updated per Test Plan; local CI (build/tests) green.
- Observability: logs/metrics/traces for new flows.
- Changelog: update `docs/changelog/CHANGELOG.md` under Unreleased.

## Pull Request Checklist
- Link ADRs/design notes (if applicable).
- Validate service README links and docs/services catalog entry.
- Regenerate/refresh contracts under `docs/contracts/*` as needed; ensure discoverability.
- Document local test runs and outcomes.
- Update runbooks for operational impact.

## Exit Criteria
- Acceptance criteria verified by Test Engineer.
- No open critical defects; risks documented.
- Rollback plan and operational impact noted in runbooks.

References: `docs/process/Workflow.md`, `docs/process/Definition-of-Done.md`.