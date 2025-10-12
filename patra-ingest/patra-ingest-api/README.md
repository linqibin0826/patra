# patra-ingest-api

## Overview
- Consumer-facing artifacts for the Ingest service. Currently contains the public error-code catalog used across modules.

## Contents
- Error codes enum: `patra-ingest/patra-ingest-api/src/main/java/com/patra/ingest/api/error/IngestErrorCode.java:1`
  - Codes follow `ING-NNNN` with HTTP alignment for mapping.

## Usage Example (Java)
```java
import com.patra.ingest.api.error.IngestErrorCode;
import com.patra.common.error.ProblemDetailBuilder;

// Map a domain failure to a ProblemDetail with ING-1401
var pd = ProblemDetailBuilder.with(IngestErrorCode.ING_1401)
    .detail("Scheduler job parameters failed validation")
    .build();
```

## Related Docs
- Service README: `patra-ingest/README.md:1`
- Event contract (TaskReady v1): `docs/contracts/events/task-ready.md:1`
- Docs spec (API module README guidance): `docs/docs-spec.md:1`

## Notes
- No HTTP endpoints are exposed by this module. Keep this README concise and link back to the service README to avoid duplication.
