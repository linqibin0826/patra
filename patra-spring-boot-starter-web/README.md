# patra-spring-boot-starter-web

## Purpose
Web-layer conveniences: RFC7807 error responses, validation formatting, pagination/sorting request models, and response wrappers.

## Auto-Configuration
Registered via imports:
- `com.patra.starter.web.error.config.WebErrorAutoConfiguration`
- `com.patra.starter.web.autoconfig.WebConversionAutoConfiguration`

## Beans and Responsibilities
- `GlobalRestExceptionHandler`
  - Converts exceptions to `ProblemDetail` using the core error pipeline.
  - Adds sanitized validation errors (capped at 100) when applicable.
- `ProblemDetailAdapter` → `DefaultProblemDetailAdapter`
- `ProblemDetailBuilder` (composes core + web field contributors)
- `ValidationErrorsFormatter` → `DefaultValidationErrorsFormatter`
- MVC conversion: `Converter<String, ProvenanceCode>` for `@PathVariable`/`@RequestParam` binding

## Request/Response Models
- Requests: `Pageable`, `Sortable`, `PagingSortable`
- Responses: `ApiResponse<T>`, `PageResult<T>`, `ResultCode`

## Properties
```yaml
patra:
  web:
    problem:
      enabled: true
      type-base-url: https://errors.example.com/
      include-stack: false
```

## Usage Example
```java
@RestController
class DemoController {
  private final HttpStdErrors.Group http = HttpStdErrors.of("ING");

  @GetMapping("/boom")
  ApiResponse<Void> boom() {
    throw new ApplicationException(http.CONFLICT(), "Conflict");
  }
}
```
The handler returns a `ProblemDetail` with an HTTP 409 status. Validation failures are formatted and attached to the `errors` property.

## Extensibility
- Implement `WebProblemFieldContributor` or `ProblemFieldContributor` to add custom fields.
- Override beans via `@Primary` or define your own `ProblemDetailAdapter`/`ValidationErrorsFormatter`.
