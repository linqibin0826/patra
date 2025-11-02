# Dependency Rules & ArchUnit Validation

## Overview

ArchUnit is a Java library that validates architectural rules at compile/test time. Papertrace uses ArchUnit to enforce Hexagonal Architecture + DDD boundaries and prevent architectural drift.

---

## Core Dependency Rules

### Rule 1: Domain Layer Independence

**Rule**: Domain layer must NOT depend on any infrastructure or framework code.

**Allowed Dependencies**:
- ✅ `java.*` (core Java)
- ✅ `patra-common` (shared utilities)
- ✅ `lombok.*` (compile-time code generation)
- ✅ `cn.hutool.*` (Hutool utilities)
- ✅ `com.fasterxml.jackson.*` (for JSON serialization in domain events/snapshots)

**Forbidden Dependencies**:
- ❌ `org.springframework.*`
- ❌ `com.baomidou.mybatisplus.*`
- ❌ `jakarta.persistence.*`

### ArchUnit Test

```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat(
            resideInAnyPackage(
                "java..",
                "lombok..",
                "cn.hutool..",
                "com.fasterxml.jackson..",
                "com.patra.common..",
                "..domain.."
            )
        )
        .because("Domain layer must be framework-agnostic");
```

**Example Violation**:
```java
// ❌ BAD: Spring annotation in domain
package com.patra.registry.domain.model.vo.provenance;

import org.springframework.stereotype.Component;  // ← Violation!

@Component  // ← Violation!
public record Provenance(...) {
    // ...
}
```

**Fix**:
```java
// ✅ GOOD: Pure Java record
package com.patra.registry.domain.model.vo.provenance;

public record Provenance(
    Long id,
    String code,
    String name,
    String baseUrlDefault,
    String timezoneDefault,
    String docsUrl,
    boolean active,
    String lifecycleStatusCode
) {
    // Pure business logic, no framework dependencies
}
```

---

### Rule 2: Dependency Direction (Hexagonal Architecture)

**Rule**: Dependencies flow inward: Adapter → App → Domain ← Infra

```
adapter  →  app + api
app      →  domain
infra    →  domain
domain   →  patra-common ONLY
```

### ArchUnit Test

```java
@ArchTest
static final ArchRule adapterDependsOnApp =
    classes()
        .that().resideInAPackage("..adapter..")
        .should().onlyAccessClassesThat(
            resideInAnyPackage(
                "..app..",
                "..api..",
                "..domain..",
                "org.springframework..",
                "java.."
            )
        )
        .because("Adapter should only depend on App and Domain");

@ArchTest
static final ArchRule appDependsOnDomain =
    classes()
        .that().resideInAPackage("..app..")
        .should().onlyAccessClassesThat(
            resideInAnyPackage(
                "..domain..",
                "com.patra.common..",
                "org.springframework..",
                "java.."
            )
        )
        .because("App should only depend on Domain");

@ArchTest
static final ArchRule infraDependsOnDomain =
    classes()
        .that().resideInAPackage("..infra..")
        .should().onlyAccessClassesThat(
            resideInAnyPackage(
                "..domain..",
                "com.patra.common..",
                "com.baomidou.mybatisplus..",
                "org.springframework..",
                "java.."
            )
        )
        .because("Infra should only depend on Domain");
```

---

### Rule 3: Port Interface Direction

**Rule**: Domain defines ports (interfaces), Infrastructure implements them.

### ArchUnit Test

```java
@ArchTest
static final ArchRule portsDefinedInDomain =
    classes()
        .that().haveSimpleNameEndingWith("Port")
        .or().haveSimpleNameEndingWith("Repository")
        .and().areInterfaces()
        .should().resideInAPackage("..domain.port..")
        .because("Port interfaces must be defined in domain layer");

@ArchTest
static final ArchRule portsImplementedInInfra =
    classes()
        .that().implement(DescribedPredicate.describe(
            "Port interface",
            clazz -> clazz.getSimpleName().endsWith("Port")
                  || clazz.getSimpleName().endsWith("Repository")
        ))
        .and().areNotInterfaces()
        .should().resideInAPackage("..infra..")
        .because("Port implementations must be in infrastructure layer");
```

**Example**:
```java
// ✅ Port in domain
package com.patra.ingest.domain.port;

public interface PatraRegistryPort {
    String fetchConfigSnapshot(String provenanceCode, String operationCode);
    String fetchExprSnapshot(String provenanceCode, String operationCode, Instant at);
}

// ✅ Implementation in infra
package com.patra.ingest.infra.adapter.registry;

@Component
public class PatraRegistryAdapter implements PatraRegistryPort {
    // Implementation using Feign client
}
```

---

### Rule 4: Layered Architecture Validation

**Rule**: Enforce strict layer boundaries.

### ArchUnit Test

```java
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@ArchTest
static final ArchRule layersAreRespected = layeredArchitecture()
    .consideringAllDependencies()

    .layer("Adapter").definedBy("..adapter..")
    .layer("App").definedBy("..app..")
    .layer("Domain").definedBy("..domain..")
    .layer("Infra").definedBy("..infra..")

    .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
    .whereLayer("App").mayOnlyBeAccessedByLayers("Adapter")
    .whereLayer("Domain").mayOnlyBeAccessedByLayers("App", "Infra")
    .whereLayer("Infra").mayNotBeAccessedByAnyLayer()

    .because("Layers must respect Hexagonal Architecture boundaries");
```

---

## Naming Convention Rules

### Rule 5: Orchestrator Naming

**Rule**: Application services must end with `Orchestrator`.

### ArchUnit Test

```java
@ArchTest
static final ArchRule orchestratorsAreNamedCorrectly =
    classes()
        .that().resideInAPackage("..app..")
        .and().areAnnotatedWith(Service.class)
        .should().haveSimpleNameEndingWith("Orchestrator")
        .because("Application services should be named *Orchestrator");
```

**Real Examples**:
- `PlanIngestionOrchestrator` (patra-ingest-app)
- `ProvenanceConfigOrchestrator` (patra-registry-app)
- `ExprQueryOrchestrator` (patra-registry-app)
- `RecordUploadOrchestrator` (patra-storage-app)

---

### Rule 6: Repository Naming

**Rule**: Repository implementations must end with `RepositoryImpl` or `RepositoryMpImpl`.

### ArchUnit Test

```java
@ArchTest
static final ArchRule repositoriesAreNamedCorrectly =
    classes()
        .that().implement(DescribedPredicate.describe(
            "Repository interface",
            clazz -> clazz.getSimpleName().endsWith("Repository")
        ))
        .and().resideInAPackage("..infra..")
        .should().haveSimpleNameMatching(".*Repository(Impl|MpImpl)")
        .because("Repository implementations should end with RepositoryImpl or RepositoryMpImpl");
```

---

### Rule 7: DO (Data Object) Naming

**Rule**: MyBatis-Plus entities must end with `DO`.

### ArchUnit Test

```java
@ArchTest
static final ArchRule dataObjectsAreNamedCorrectly =
    classes()
        .that().areAnnotatedWith(TableName.class)  // MyBatis-Plus annotation
        .should().haveSimpleNameEndingWith("DO")
        .because("MyBatis-Plus entities should end with DO");
```

**Real Examples**:
- `PlanDO` → `@TableName("ing_plan")`
- `PlanSliceDO` → `@TableName("ing_plan_slice")`
- `TaskDO` → `@TableName("ing_task")`
- `OutboxMessageDO` → `@TableName("ing_outbox_message")`
- `RegProvenanceDO` → `@TableName("reg_provenance")`

---

## Anti-Patterns Detection

### Rule 8: No Cyclic Dependencies

**Rule**: Prevent circular dependencies between packages.

### ArchUnit Test

```java
@ArchTest
static final ArchRule noCyclesInPackages =
    slices()
        .matching("com.patra.(*)..")
        .should().beFreeOfCycles()
        .because("Circular dependencies make code hard to understand and maintain");
```

---

### Rule 9: No Field Injection

**Rule**: Use constructor injection, not field injection.

### ArchUnit Test

```java
@ArchTest
static final ArchRule noFieldInjection =
    noFields()
        .that().areDeclaredInClassesThat().resideInAPackage("com.patra..")
        .should().beAnnotatedWith(Autowired.class)
        .because("Use constructor injection instead of field injection");
```

**Example**:
```java
// ❌ BAD: Field injection
@Service
public class PlanIngestionOrchestrator {
    @Autowired  // ← Violation!
    private PatraRegistryPort registryPort;
}

// ✅ GOOD: Constructor injection
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class PlanIngestionOrchestrator {
    private final PatraRegistryPort registryPort;  // ← Injected via constructor
}
```

---

### Rule 10: Repository Isolation

**Rule**: Repositories should NOT be used directly in Adapter layer.

### ArchUnit Test

```java
@ArchTest
static final ArchRule adapterDoesNotUseRepositories =
    noClasses()
        .that().resideInAPackage("..adapter..")
        .should().dependOnClassesThat().resideInAPackage("..infra..")
        .because("Adapter should only use Application layer, not Infrastructure");
```

---

## Complete ArchUnit Test Class

**Location**: `patra-{service}-boot/src/test/java/architecture/ArchitectureTest.java`

```java
package com.patra.registry.architecture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.patra.registry")
class ArchitectureTest {

    // Domain Independence
    @ArchTest
    static final ArchRule domainLayerIsIndependent =
        classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat(
                resideInAnyPackage(
                    "java..",
                    "lombok..",
                    "cn.hutool..",
                    "com.fasterxml.jackson..",
                    "com.patra.common..",
                    "..domain.."
                )
            );

    // Layered Architecture
    @ArchTest
    static final ArchRule layersAreRespected = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Adapter").definedBy("..adapter..")
        .layer("App").definedBy("..app..")
        .layer("Domain").definedBy("..domain..")
        .layer("Infra").definedBy("..infra..")
        .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
        .whereLayer("App").mayOnlyBeAccessedByLayers("Adapter")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("App", "Infra")
        .whereLayer("Infra").mayNotBeAccessedByAnyLayer();

    // Naming Conventions
    @ArchTest
    static final ArchRule orchestratorsAreNamedCorrectly =
        classes()
            .that().resideInAPackage("..app..")
            .and().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Orchestrator");

    @ArchTest
    static final ArchRule repositoriesAreNamedCorrectly =
        classes()
            .that().areAnnotatedWith(Repository.class)
            .and().resideInAPackage("..infra..")
            .should().haveSimpleNameMatching(".*Repository(Impl|MpImpl)");

    @ArchTest
    static final ArchRule dataObjectsAreNamedCorrectly =
        classes()
            .that().areAnnotatedWith(TableName.class)
            .should().haveSimpleNameEndingWith("DO");

    // No Cyclic Dependencies
    @ArchTest
    static final ArchRule noCyclesInPackages =
        slices()
            .matching("com.patra.(*)..")
            .should().beFreeOfCycles();

    // No Field Injection
    @ArchTest
    static final ArchRule noFieldInjection =
        noFields()
            .that().areDeclaredInClassesThat().resideInAPackage("com.patra..")
            .should().beAnnotatedWith(Autowired.class);

    // Ports and Adapters
    @ArchTest
    static final ArchRule portsDefinedInDomain =
        classes()
            .that().haveSimpleNameEndingWith("Port")
            .or().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .should().resideInAPackage("..domain.port..");

    @ArchTest
    static final ArchRule portsImplementedInInfra =
        classes()
            .that().implement(DescribedPredicate.describe(
                "Port interface",
                clazz -> clazz.getSimpleName().endsWith("Port")
                      || clazz.getSimpleName().endsWith("Repository")
            ))
            .and().areNotInterfaces()
            .should().resideInAPackage("..infra..");
}
```

---

## Running ArchUnit Tests Locally

### Maven Commands

```bash
# Run all tests including ArchUnit
mvn test

# Run only ArchUnit tests
mvn test -Dtest=ArchitectureTest

# Compile and test (if test fails with "Class not found")
mvn clean compile test
```

### Maven Configuration (Optional)

If you want to explicitly include ArchUnit tests in Maven Surefire:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/ArchitectureTest.java</include>
        </includes>
    </configuration>
</plugin>
```

---

## Best Practices

### 1. Fix Violations Immediately

**Don't ignore ArchUnit failures**. They indicate architectural drift that becomes harder to fix over time.

### 2. Run Tests Before Committing

Make it a habit to run `mvn test` before committing code changes to catch violations early.

### 3. Update Rules as Architecture Evolves

If architecture decisions change, update ArchUnit rules to match. Rules should reflect the current agreed-upon architecture.

### 4. Document Exceptions

If you need to make an exception to a rule, document why:

```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .and().haveSimpleNameNotEndingWith("JsonSerializer")  // ← Exception documented
        .should().onlyDependOnClassesThat(...)
        .because("Domain layer must be framework-agnostic (except JSON serializers for events)");
```

### 5. Handle Legacy Violations Gradually

If you have too many existing violations, freeze the current state and prevent new ones:

```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat(...)
        .allowEmptyShould(true);  // ← Temporarily allows current violations
```

Then fix violations incrementally in separate refactoring efforts.

---

## Summary

**ArchUnit Benefits**:
- ✅ Automated architecture validation at test time
- ✅ Prevents architectural drift before code is committed
- ✅ Documents architectural decisions as executable code
- ✅ Provides fast feedback on architecture violations

**Key Rules Covered**:
1. **Domain Layer Independence** - No framework dependencies in domain
2. **Dependency Direction** - Hexagonal Architecture boundaries (Adapter → App → Domain ← Infra)
3. **Port/Adapter Pattern** - Ports in domain, implementations in infra
4. **Naming Conventions** - Orchestrators, Repositories, DOs properly named
5. **Anti-Patterns** - No cyclic dependencies, no field injection, no layer violations

**Running Tests**:
```bash
# Run all tests
mvn test

# Run only ArchUnit tests
mvn test -Dtest=ArchitectureTest
```

**Development Workflow**:
1. Write code following architecture principles
2. Run `mvn test` locally before committing
3. Fix any ArchUnit violations immediately
4. Update rules when architecture evolves

**See Also**:
- [architecture-overview.md](architecture-overview.md) - Architecture principles and patterns
- [testing-guide.md](testing-guide.md) - Other testing strategies (unit, integration, E2E)
