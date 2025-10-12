Metrics Glossary

- `ingest.plan.duration` (timer, ms)
  - Tags: `service=patra-ingest`, `result`
  - Description: end-to-end plan creation per trigger

- `ingest.execute.duration` (timer, ms)
  - Tags: `service=patra-ingest`, `provenance`, `result`
  - Description: task execution orchestration duration

- `ingest.relay.duration` (timer, ms)
  - Tags: `service=patra-ingest`, `channel`, `result`
  - Description: outbox relay orchestration duration per channel

- `papertrace.outbox.publish.total` (counter)
  - Tags: `service=patra-ingest`, `status`, `channel`
  - Description: outbox publish attempts by status (success|retry|failure)

- `papertrace.outbox.publish.duration` (timer, ms)
  - Tags: `service=patra-ingest`, `channel`, `opType`, `result`
  - Description: time spent publishing outbox messages

- `egress.call.duration` (timer, ms)
  - Tags: `service=patra-egress-gateway`, `method`, `result`
  - Description: end-to-end egress call duration

- `egress.call.retry.count` (counter)
  - Tags: `service=patra-egress-gateway`, `reason`
  - Description: retries attempted by reason

- `errors.by_code` (counter)
  - Tags: `service`, `code`
  - Description: error counts by canonical error code

- `mq.consumer.lag` (gauge, messages)
  - Tags: `service`, `topic`
  - Description: broker consumer lag for key topics

- `db.pool.active` (gauge)
  - Tags: `service`, `pool`
  - Description: active DB connections per pool

