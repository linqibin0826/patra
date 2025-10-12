Title: RocketMQ with Outbox Pattern for Async Messaging
Status: Accepted
Date: 2025-10-11

Context
- Ingest workflows rely on asynchronous task signaling and reliable external publication.
- We require ordering guarantees per business key, idempotency, and backpressure-resilient delivery.

Decision
- Use RocketMQ as the message broker for internal events.
- Adopt the Outbox pattern: persist events in a local outbox table within the service boundary and relay to RocketMQ.
- Use partition keys (headers `partitionKey`/`KEYS`) to preserve ordering per business key where required.

Alternatives
- Kafka: mature ecosystem; similar semantics; operational alignment currently favors RocketMQ.
- Direct synchronous calls: simpler but couples services and reduces resilience.

Consequences
- Benefits: reliable delivery, replay capability, ordered processing per key, decoupled consumers.
- Costs: additional relay component and monitoring; outbox table management.

References
- `docs/contracts/events/task-ready.md`
- `docs/architecture/C4-Container.md`
- `patra-ingest/README.md` (Outbox Relay)

