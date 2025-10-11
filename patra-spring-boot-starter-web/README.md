Purpose and Responsibilities
- Web error handling, pagination/sorting request models, and API response wrappers.

Key Components
- GlobalRestExceptionHandler transforms exceptions to RFC 7807 ProblemDetail via DefaultProblemDetailAdapter.
- ProblemDetailBuilder and WebProblemFieldContributor SPI to enrich responses.
- Request models: Pageable, Sortable, PagingSortable.
- Response models: ApiResponse, PageResult, ResultCode.
- Auto-config: WebErrorAutoConfiguration and WebConversionAutoConfiguration.

Configuration Properties
- `patra.web.problem.enabled` (default true)
- `patra.web.problem.type-base-url` (default https://errors.example.com/)
- `patra.web.problem.include-stack` (default false)
