# Delivery Workflow

## Lifecycle (Bootstrap Phase)
- Capture requirements and quality targets (Architect + Stakeholders).
- Record a succinct ADR per key decision.
- Draft or update C4 Context/Container.
- Define acceptance criteria and test plan (Dev + Test).
- Implement feature behind clear service boundaries.
- Update contracts/runbooks/README and add tests.
- Validate against test plan; QA sign-off.
- Update changelog and close milestone.

## Pull Request Checklist
- Linked ADRs/design notes (if applicable).
- Service README updated and links validated.
- Contracts updated (API/Event) or explicitly unchanged.
- Tests added/updated, local runs documented.
- Runbook updates for operational impact.

## Reviews and Gates
- Architecture review: ADRs and boundaries (Architect).
- Contract review: schema and backwards-compatibility (Architect + Test).
- QA review: test plan coverage and results (Test Engineer).
