# TaskReady v1

## Topic/Channel
- `INGEST_TASK_READY`

## Producers and Consumers
- Producer: Ingest plan publisher/outbox
- Consumer: `ingestTaskReadyConsumer` (Spring Cloud Stream RocketMQ binder)

## Schema
- `taskId`: number (required)
- `idempotentKey`: string (required)
- JSON Schema: [schemas/task-ready.v1.schema.json](schemas/task-ready.v1.schema.json)

## Semantics and Ordering
- Signals a task is ready to execute; consumers load full context by `taskId`.
- Partitioning: producer uses `partitionKey` header (or `KEYS`) to ensure ordering per business key.

## Idempotency and Retry
- Idempotent per `idempotentKey`; MQ consumer retries with exponential backoff (see `ingest-mq-config.yaml`).
