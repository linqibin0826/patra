# patra-registry-api

## Purpose
Defines the internal RPC contracts, DTOs, and Feign clients used by other services to query Registry data (provenance metadata/config and expression snapshots). This module is the stable integration surface; implementations live in other modules.

## Contracts
- Endpoints
  - `ProvenanceEndpoint`
    - `GET /_internal/provenances`
    - `GET /_internal/provenances/{code}`
    - `GET /_internal/provenances/{code}/config?operationType=&at=`
  - `ExprEndpoint`
    - `GET /_internal/expr/snapshot?provenanceCode=&operationType=&endpointName=&at=`
- DTOs
  - Provenance: `rpc.dto.provenance.*`
  - Expression: `rpc.dto.expr.*`
  - Dictionary: `rpc.dto.dict.*`
- Feign clients
  - `ProvenanceClient` extends `ProvenanceEndpoint`
  - `ExprClient` extends `ExprEndpoint`

See contracts doc for narrative details: docs/contracts/api/registry-internal.md

## Usage (Feign)
```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
interface ProvenanceClient extends ProvenanceEndpoint {}

@Service
class ConfigService {
  private final ProvenanceClient prov;
  ConfigService(ProvenanceClient prov) { this.prov = prov; }

  ProvenanceConfigResp load(ProvenanceCode code) {
    return prov.getConfiguration(code, "HARVEST", Instant.now());
  }
}
```

## Versioning & Compatibility
- Backward-compatible changes (additive fields) are allowed without breaking consumers.
- Breaking API changes require coordination and an ADR; update clients accordingly.

## Related Docs
- Service internals (domain/app/infra): patra-registry/README.md
- Contracts overview: docs/contracts/api/registry-internal.md
