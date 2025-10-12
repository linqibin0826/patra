# Documentation Governance & Workflow

- Docs-as-code under `docs/`; per-service README at `patra-<service>/README.md`.
- Architecture-first: maintain ADRs (`docs/adr/`), C4 Context/Container (`docs/architecture/`), contracts (`docs/contracts/`).
- Discoverability: link API/Event contracts from service READMEs and list services in `docs/services/index.md`.
- RACI (selected):
  - ADRs: Architect (A/R), Dev/Test (C)
  - Service README: Dev (A/R), Architect (C), Test (I)
  - API Contracts: Architect (A), Dev (R), Test (C)
  - Test Plans: Test (A/R), Dev (C), Architect (I)
- Change workflow:
  - Meaningful change → update ADRs/contracts/service README and services catalog.
  - Significant architecture change → update C4 docs.
  - Pre-release → `docs/changelog/CHANGELOG.md` update.
- DoD and PR gates: see `docs/process/Workflow.md` and `docs/process/Definition-of-Done.md`.
