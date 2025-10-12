# patra-common

## Purpose and Scope
- Reusable building blocks for domain base classes, error contracts, JSON utilities, and messaging conventions. Pure Java only; no Spring or persistence dependencies.

## Packages
- Domain: `AggregateRoot`, `ReadOnlyAggregate`, `DomainEvent` — framework-free base classes/interfaces.
- Errors: `ApplicationException`, `DomainException`, `ErrorCodeLike`, `HttpStdErrors`, `ErrorTrait`, `HasErrorTraits`.
- JSON: `JsonMapperHolder`, `JsonNormalizer`, `JsonNodeMappings`.
- Messaging: `ChannelKey` naming contract for topics/channels.
- Enums/Utils: cross-service enums (`ProvenanceCode`, etc.) and `HashUtils`.

## Domain Base Types
- `AggregateRoot<ID>`: identifier + version, domain-event staging via `addDomainEvent` and `pullDomainEvents()`; `assertInvariants()` hook.
- `ReadOnlyAggregate<ID>`: CQRS read model base (no events/versioning); equality by ID; `assertInvariants()` hook.
- `DomainEvent`: marker with `occurredAt()` for audit/ordering.

## Error Model
- `ErrorCodeLike`: minimal contract for code + HTTP status.
- `HttpStdErrors.of("ING")`: factory of prefixed standard codes, e.g. `ING-0404` → 404.
- `ApplicationException`: attach `ErrorCodeLike` for adapter mapping to ProblemDetail.
- `DomainException`: base for domain-specific failures, kept framework-agnostic.
- `ErrorTrait` + `HasErrorTraits`: semantic hints (NOT_FOUND, CONFLICT, TIMEOUT, …) for error resolution.

## JSON Utilities
- `JsonMapperHolder`: global `ObjectMapper` holder for non-Spring contexts; starters bridge the container-managed mapper.
- `JsonNormalizer`: deterministic normalization for hashing/signing and canonical storage.
  - Features: key sorting, array dedupe + ordering, empty-value policy, boolean/number/time coercion, whitespace cleanup, depth/string guards.
  - Builders: `usingDefault()`, `withConfig(Config)`, `withMapper(ObjectMapper, Config)`.
- `JsonNodeMappings`: convenience conversions between `String`/`Map` and `JsonNode` using the global mapper.

## Messaging
- `ChannelKey`: domain/resource/event triplet with normalized uppercase `domain_resource_event` channel string.

## Examples
- Error codes and exceptions
```java
var http = HttpStdErrors.of("ING");
throw new ApplicationException(http.NOT_FOUND(), "Task not found");
```

- JsonNormalizer quick use
```java
JsonNormalizer.Result r = JsonNormalizer.normalizeDefault(input);
String canonical = r.getCanonicalJson();
String hashHex = HashUtils.sha256Hex(r.getHashMaterial());
```

- JsonNormalizer custom config
```java
JsonNormalizer normalizer = JsonNormalizer.withConfig(
    JsonNormalizer.Config.builder()
        .coerceNumber(true)
        .coerceTime(true)
        .removeEmpty(true)
        .maxDepth(64)
        .build()
);
var res = normalizer.normalize(input);
```

## Usage
- Maven (if not inherited from parent):
```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-common</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
- With the core Spring Boot starter on classpath, `JsonMapperHolder` is bridged to the container `ObjectMapper`.

## Constraints
- Keep this module framework-free; do not add Spring/MyBatis/etc.
- Avoid business-specific DTOs or service contracts; place those in service modules.

## Testing
- Validate aggregate/event semantics, JSON normalization edge cases, error-code mapping, enum parsing.
