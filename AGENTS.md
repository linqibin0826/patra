# AGENTS.md

## Workspace layout

The Patra workspace contains the following sub-projects:

| Dir | Purpose |
|-----|---------|
| **patra-api** | Backend services (microservices + hexagonal architecture + DDD) |
| **patra-infra** | Infrastructure configs (Docker Compose, DB scripts, etc.) |

Each sub-project has its own `.Codex/AGENTS.md`, which Codex loads automatically when you enter the directory.
