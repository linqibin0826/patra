# Event Contracts

## Overview
- Keep producer/consumer docs in this directory and JSON Schemas under `schemas/`.

## What to Document
- Event Name and Version
- Topic/Channel
- Producer(s) and Consumer(s)
- Schema (inline or linked)
- Semantics and ordering
- Idempotency key and retry policy

## Schemas Directory
- Place JSON schemas under `docs/contracts/events/schemas/`.

## Conventions
- Naming: `<Domain><Action> vN` (e.g., `TaskReady v1`).
- Versioning: breaking changes create a new `vN`; non-breaking are additive.
- Headers: use `partitionKey`/`KEYS` for ordering by business key; include `traceId`.
