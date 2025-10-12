# Registry Internal API Contracts

## `GET /_internal/provenances`
- Purpose: list supported provenances.
- Params: none.
- Response: array of `Provenance` with code, name, capabilities.

## `GET /_internal/provenances/{code}`
- Purpose: get a single provenance by code.
- Params: `code` (path).
- Response: `Provenance`.

## `GET /_internal/provenances/{code}/config`
- Purpose: load effective configuration for a provenance/operation/instant.
- Params:
  - `code` (path)
  - `operationType` (query, required)
  - `at` (query, optional ISO-8601 instant; defaults to now)
- Response: `ProvenanceConfiguration` (HTTP/pagination/retry/rateLimit/windowOffset), expression summary.

## Expression Snapshot (service layer)
- App service: `ExprQueryAppService#loadSnapshot(provenanceCode, operationType, endpointName, at)`.
- Purpose: provide snapshot for expression compiler usage (not exposed as REST in adapter).

## Notes
- See `patra-registry/README.md` for domain model and clients (`ProvenanceClient`, `ExprClient`).
