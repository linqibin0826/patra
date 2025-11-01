# Adapter Layer Patterns (REST, Jobs, Consumers)

**Purpose**: Driving adapters that receive external triggers and delegate to Application layer.

---

## Content Outline

1. **REST Controllers** (`@RestController`)
   - Request/Response mapping
   - `@Valid` validation
   - ProblemDetail error handling
   - OpenAPI documentation

2. **Scheduled Jobs** (XXL-Job)
   - `@XxlJob` configuration
   - Job parameters
   - Error handling and retries

3. **Message Consumers** (RocketMQ, Kafka)
   - `@RocketMQMessageListener`
   - Message deserialization
   - Idempotency handling

4. **Common Patterns**
   - Trace propagation
   - Metrics collection
   - Logging standards

---

## Key Principles

- ✅ Delegate to orchestrators, NO business logic
- ✅ Use `@Valid` for format validation
- ✅ Map domain results to response DTOs
- ❌ NO direct calls to Infrastructure layer

---

**📝 Status**: Content outline created. Full examples from Papertrace code (Provenance, BatchPlan features) to be added.

**See Also**: [architecture-overview.md](architecture-overview.md) for layer responsibilities.
