# Expression Compiler–Bridge — Start Here

Status: authoritative entrypoint (updated 2025-10-17)

Who should read what
- Developers: 01-overview.md → 02-architecture.md (2.2/2.3/2.8) → 03-compiler-bridge-internals.md (3.2/3.4) → 08-testing-and-smoke.md → 11-acceptance-criteria.md
- Provider onboarding: 12-provider-onboarding.md → 04-providers-quickref.md → 08-testing-and-smoke.md
- Review/Sign-off: 11-acceptance-criteria.md → 02-architecture.md (2.8) → 03-compiler-bridge-internals.md (3.4)
- Migration/Rollout: 07-migration-rollout.md → 08-testing-and-smoke.md

Quick start
1) Read 01-overview.md (Goals, Non-Goals, Success Criteria)
2) Skim 02-architecture.md for component responsibilities and configuration (2.8)
3) Implement or modify: follow 12-provider-onboarding.md
4) Validate locally: 08-testing-and-smoke.md (unit/golden/smoke)
5) Accept: 11-acceptance-criteria.md

What changed in this consolidation
- Reduced the top-level docs to a small set of core guides.
- Merged testing materials into 08-testing-and-smoke.md.
- Merged migration and rollout into 07-migration-rollout.md.
- Merged provider how-to and checklist into 12-provider-onboarding.md.
- Moved provider deep dives and review notes to docs/expr/archive.

If you can only read 5 files
- 01-overview.md
- 02-architecture.md
- 03-compiler-bridge-internals.md
- 08-testing-and-smoke.md
- 11-acceptance-criteria.md
