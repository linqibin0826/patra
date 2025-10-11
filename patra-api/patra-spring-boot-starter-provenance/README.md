Purpose and Responsibilities
- Provenance client utilities for PubMed and Europe PMC; shared configuration, metrics, and gateway request helpers.

Key Components
- Clients: PubMedClient, EPMCClient with NoOp variants.
- Models: request/response types for PubMed/EPMC.
- Config: ProvenanceAutoConfiguration, ProvenanceProperties, rate-limit and retry config.
- Utilities: GatewayRequestBuilder, ApiRequest, ProvenanceObjectMapperFactory.

Configuration Properties
- Root: `patra.provenance.*` (see ProvenanceProperties)
  - `enabled` (default true)
  - `pubmed.base-url`, `epmc.base-url`
  - `*.http.*` (defaultHeaders, timeout*Millis)
  - `*.pagination.*` (pageSizeValue, maxPagesPerExecution)
  - `*.window-offset.*` (windowModeCode, windowSize*, lookback*, overlap*, offsetTypeCode, maxIdsPerWindow)
  - `*.batching.*` (detailFetchBatchSize, maxIdsPerRequest)
  - `*.retry.*` (maxRetryTimes, initialDelayMillis)
  - `*.rate-limit.*` (maxConcurrentRequests, perCredentialQpsLimit)
