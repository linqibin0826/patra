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

**Forbidden Dependencies**:
- ❌ `org.springframework.*`
- ❌ `com.baomidou.mybatisplus.*`
- ❌ `jakarta.persistence.*`
- ❌ `com.fasterxml.jackson.*` (except in specific cases)

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
                "com.patra.common..",
                "..domain.."
            )
        )
        .because("Domain layer must be framework-agnostic");
```

**Example Violation**:
```java
// ❌ BAD: Spring annotation in domain
package com.patra.registry.domain.model.aggregate;

import org.springframework.stereotype.Component;  // ← Violation!

@Component  // ← Violation!
public class ProvenanceConfiguration {
    // ...
}
```

**Fix**:
```java
// ✅ GOOD: Pure Java
package com.patra.registry.domain.model.aggregate;

public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset
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
        .should().resideInAPackage("..domain.port..")
        .because("Port interfaces must be defined in domain layer");

@ArchTest
static final ArchRule portsImplementedInInfra =
    classes()
        .that().implement(DescribedPredicate.describe(
            "Port interface",
            clazz -> clazz.getSimpleName().endsWith("Port")
        ))
        .should().resideInAPackage("..infra..")
        .because("Port implementations must be in infrastructure layer");
```

**Example**:
```java
// ✅ Port in domain
package com.patra.registry.domain.port;

public interface ProvenanceConfigPort {
    Optional<ProvenanceConfiguration> findById(ProvenanceId id);
}

// ✅ Implementation in infra
package com.patra.registry.infra.persistence.repository;

@Repository
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigPort {
    // Implementation using MyBatis-Plus
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

---

### Rule 6: Repository Naming

**Rule**: Repository implementations must end with `RepositoryImpl` or `RepositoryMpImpl`.

### ArchUnit Test

```java
@ArchTest
static final ArchRule repositoriesAreNamedCorrectly =
    classes()
        .that().implement(DescribedPredicate.describe(
            "Port interface",
            clazz -> clazz.getSimpleName().endsWith("Port")
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
public class ProvenanceOrchestrator {
    @Autowired  // ← Violation!
    private ProvenancePort provenancePort;
}

// ✅ GOOD: Constructor injection
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class ProvenanceOrchestrator {
    private final ProvenancePort provenancePort;  // ← Injected via constructor
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
            .should().resideInAPackage("..domain.port..");

    @ArchTest
    static final ArchRule portsImplementedInInfra =
        classes()
            .that().implement(DescribedPredicate.describe(
                "Port interface",
                clazz -> clazz.getSimpleName().endsWith("Port")
            ))
            .should().resideInAPackage("..infra..");
}
```

---

## CI/CD Integration

### Maven Configuration

**pom.xml**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0-M9</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/ArchitectureTest.java</include>
        </includes>
    </configuration>
</plugin>
```

### CI Pipeline (GitHub Actions)

```yaml
name: Architecture Validation

on: [push, pull_request]

jobs:
  architecture-test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'
          distribution: 'zulu'

      - name: Run ArchUnit Tests
        run: mvn test -Dtest=ArchitectureTest

      - name: Publish ArchUnit Report
        if: always()
        uses: scacap/action-surefire-report@v1
        with:
          report_paths: '**/target/surefire-reports/TEST-*.xml'
```

---

## Best Practices

### 1. Run ArchUnit Tests Locally

```bash
# Run all tests including ArchUnit
mvn test

# Run only ArchUnit tests
mvn test -Dtest=ArchitectureTest
```

### 2. Fix Violations Immediately

**Don't ignore ArchUnit failures**. They indicate architectural drift.

### 3. Update Rules as Architecture Evolves

If architecture decisions change, update ArchUnit rules to match.

### 4. Document Exceptions

If you need to violate a rule, document why:
```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .and().haveSimpleNameNotEndingWith("JsonSerializer")  // Exception documented
        .should().onlyDependOnClassesThat(...)
        .because("Domain layer must be framework-agnostic (except JSON serializers)");
```

---

## Troubleshooting

### Issue: Test Fails with "Class not found"

**Solution**: Ensure all modules are compiled before running tests.
```bash
mvn clean compile test
```

### Issue: Too Many Violations

**Solution**: Freeze current state, prevent new violations.
```java
@ArchTest
static final ArchRule domainLayerIsIndependent =
    classes()
        .that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat(...)
        .allowEmptyShould(true);  // ← Allows current violations
```

Then fix violations incrementally.

---

## Summary

**ArchUnit Benefits**:
- ✅ Automated architecture validation
- ✅ Prevents architectural drift
- ✅ Documents architectural decisions in code
- ✅ CI/CD integration

**Key Rules**:
1. Domain layer independence
2. Dependency direction (Hexagonal)
3. Port/Adapter pattern enforcement
4. Naming conventions
5. No cyclic dependencies

**Integration**:
- Run in Maven: `mvn test`
- CI/CD pipeline validation
- Fail builds on violations

**See Also**:
- [architecture-overview.md](architecture-overview.md) for architecture principles
- [testing-guide.md](testing-guide.md) for other testing strategies
