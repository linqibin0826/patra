# patra-spring-boot-starter-web

> Web layer auto-configuration providing REST controllers, validation, problem detail error handling, and Feign client support.

## 📌 Purpose

Provides **web-specific** auto-configuration for REST APIs:
- Spring Web MVC configuration
- Validation framework (`@Valid`)
- Problem Detail error mapping (RFC 7807)
- Feign client integration
- CORS configuration
- Request/response logging

## 🔧 Auto-Configurations

### REST Controller Support
- Automatic `@RestControllerAdvice` registration
- Global exception handlers
- Problem Detail responses for all errors

### Validation
- JSR-303 Bean Validation enabled
- Custom validators auto-registered
- Validation error mapping to Problem Detail

### Feign Clients
- Error decoder for Feign errors
- Request interceptors for trace propagation
- Retry policies

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
</dependency>
```

Includes: `patra-spring-boot-starter-core`, Spring Web, Spring Cloud OpenFeign

## 🚀 Usage

### Adapter Layer
```java
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    @PostMapping
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request) {
        // Auto-validated, errors mapped to Problem Detail
        return orchestrator.create(request.toCommand());
    }
}
```

### Error Handling
```java
@Component
public class IngestErrorMapping implements ErrorMappingContributor {
    @Override
    public ProblemDetail map(Exception ex) {
        if (ex instanceof PlanNotFoundException) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        return null;
    }
}
```

---

**Last Updated**: 2025-01-12
