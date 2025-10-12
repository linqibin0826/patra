# C4 Container

## Containers/Services
- patra-ingest — plans, slices, execution, outbox relay
- patra-registry — provenance metadata and configuration
- patra-egress-gateway — outbound HTTP with resilience envelope
- patra-gateway-boot — API Gateway entrypoint (edge)
- Shared libraries — patra-common, expr-kernel, starters

## Responsibilities per Container
- Ingest: orchestrate use cases (plan, execute, outbox), publish/consume events
- Registry: expose RPC for provenance and expr snapshots
- Egress: expose internal RPC to perform external HTTP call with envelope

## Data Stores
- MySQL per service (ingest, registry)
- Redis for leases/checkpoints
- RocketMQ for events/outbox relay

## Communication (sync/async)
- Sync: Feign RPC between services
- Async: RocketMQ topics for task ready and outbox relay

## Cross-Cutting Concerns
- Config via Nacos/env
- Security via gateway and config
- Observability via SkyWalking, SLF4J logs, Micrometer metrics

```mermaid
flowchart TB
  subgraph Ingest
    A[Adapter]-->B[App]
    B-->C[Domain]
    D[Infra]-->C
  end
  subgraph Registry
    A2[Adapter]-->B2[App]
    B2-->C2[Domain]
    D2[Infra]-->C2
  end
  subgraph Egress
    A3[Adapter]-->B3[App]
    B3-->C3[Domain]
    D3[Infra]-->C3
  end
  Ingest-- RPC -->Registry
  Ingest-- MQ -->Ingest
  Egress-- External HTTP -->APIs
```
