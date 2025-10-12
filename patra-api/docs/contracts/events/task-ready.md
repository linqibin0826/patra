Event Name and Version
- TaskReady v1

Topic/Channel
- `INGEST_TASK_READY`

Producer and Consumers
- Producer: Ingest plan publisher/outbox
- Consumer: `ingestTaskReadyConsumer` (Spring Cloud Stream RocketMQ binder)

Schema (JSON)
- taskId: number (required)
- idempotentKey: string (required)
- See JSON Schema: [schemas/task-ready.v1.schema.json](schemas/task-ready.v1.schema.json)

Semantics and Ordering
- Signals a task is ready to execute; consumers load full context by `taskId`.
- Partitioning: producer uses `partitionKey` header (or `KEYS`) to ensure ordering per business key.

Idempotency Key and Retry Policy
- Idempotent per `idempotentKey`; MQ consumer retries with exponential backoff (see `ingest-mq-config.yaml`).
