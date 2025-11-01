# Testing Guide (Unit, Integration, ArchUnit)

## Overview

Papertrace follows the **Testing Pyramid** approach: many fast unit tests, fewer integration tests, and minimal end-to-end tests. Tests are organized by layer (Domain, Application, Infrastructure, Adapter) to match Hexagonal Architecture.

**Core Principle**: Test each layer in isolation with appropriate test doubles (mocks, stubs, test containers).

---

## Testing Pyramid

```
         /\
        /  \      E2E Tests (few, slow)
       /----\     - Full system scenarios
      /      \    - Critical user journeys
     /--------\   Integration Tests (moderate)
    /          \  - Database interactions
   /------------\ - External API calls
  /______________\ Unit Tests (many, fast)
                   - Domain logic
                   - Business rules
                   - Pure Java
```

**Target Distribution**:
- **70% Unit Tests**: Domain + Application layer logic
- **25% Integration Tests**: Infrastructure + Database
- **5% E2E Tests**: Critical workflows only

---

## Test Organization

### Directory Structure

```
patra-{service}/
├── patra-{service}-domain/
│   └── src/
│       ├── main/java/
│       └── test/java/                    # Domain unit tests
│           └── com/patra/{service}/domain/
│               ├── model/
│               │   ├── ProvenanceTest.java
│               │   └── BatchPlanTest.java
│               └── factory/
│                   └── OutboxMessageFactoryTest.java
│
├── patra-{service}-app/
│   └── src/
│       ├── main/java/
│       └── test/java/                    # Application unit tests
│           └── com/patra/{service}/app/
│               └── usecase/
│                   └── plan/
│                       └── PlanIngestionOrchestratorTest.java
│
├── patra-{service}-infra/
│   └── src/
│       ├── main/java/
│       └── test/java/                    # Infrastructure unit tests
│           └── com/patra/{service}/infra/
│               └── persistence/
│                   └── converter/
│                       └── ProvenanceConverterTest.java
│
├── patra-{service}-adapter/
│   └── src/
│       ├── main/java/
│       └── test/java/                    # Adapter unit tests
│           └── com/patra/{service}/adapter/
│               └── rest/
│                   └── ProvenanceControllerTest.java
│
└── patra-{service}-boot/
    └── src/
        ├── main/java/
        └── test/java/                    # Integration tests
            └── com/patra/{service}/
                ├── integration/          # Database integration tests
                │   ├── ProvenanceIntegrationTest.java
                │   └── PlanIngestionIntegrationTest.java
                ├── architecture/         # ArchUnit tests
                │   └── ArchitectureTest.java
                └── e2e/                  # End-to-end tests (optional)
                    └── HarvestWorkflowE2ETest.java
```

---

## Domain Layer Testing (Pure Unit Tests)

### Purpose

Test **business logic** and **business rules** in isolation. No Spring, no database, no mocks needed.

### Characteristics

- ✅ Pure Java tests
- ✅ Fast execution (<100ms per test)
- ✅ No external dependencies
- ✅ Test domain invariants and business rules

### Example: Testing an Aggregate

```java
package com.patra.registry.domain.model.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

class ProvenanceConfigurationTest {

    @Test
    @DisplayName("Should create valid provenance configuration")
    void shouldCreateValidConfiguration() {
        // Given
        Provenance provenance = new Provenance(
            ProvenanceCode.of("PUBMED"),
            "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov"
        );

        WindowOffsetConfig windowOffset = new WindowOffsetConfig(
            OffsetType.RELATIVE,
            Duration.ofDays(1)
        );

        // When
        ProvenanceConfiguration config = new ProvenanceConfiguration(
            provenance,
            windowOffset,
            null, null, null, null, null
        );

        // Then
        assertThat(config.provenance()).isEqualTo(provenance);
        assertThat(config.isComplete()).isFalse();  // Missing required configs
    }

    @Test
    @DisplayName("Should validate completeness correctly")
    void shouldValidateCompleteness() {
        // Given: Complete configuration
        ProvenanceConfiguration config = ProvenanceConfiguration.builder()
            .provenance(createActiveProvenance())
            .windowOffset(createWindowOffset())
            .pagination(createPaginationConfig())
            .build();

        // When & Then
        assertThat(config.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Should reject inactive provenance as incomplete")
    void shouldRejectInactiveProvenance() {
        // Given: Inactive provenance
        Provenance inactiveProvenance = Provenance.builder()
            .provenanceCode(ProvenanceCode.of("PUBMED"))
            .isActive(false)
            .build();

        ProvenanceConfiguration config = ProvenanceConfiguration.builder()
            .provenance(inactiveProvenance)
            .windowOffset(createWindowOffset())
            .pagination(createPaginationConfig())
            .build();

        // When & Then
        assertThat(config.isComplete()).isFalse();
    }

    // Test helper methods
    private Provenance createActiveProvenance() {
        return new Provenance(
            ProvenanceCode.of("PUBMED"),
            "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            true
        );
    }

    private WindowOffsetConfig createWindowOffset() {
        return new WindowOffsetConfig(OffsetType.RELATIVE, Duration.ofDays(1));
    }

    private PaginationConfig createPaginationConfig() {
        return new PaginationConfig(1000, 10000);
    }
}
```

### Testing Value Objects (Records)

```java
package com.patra.registry.domain.model.vo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProvenanceIdTest {

    @Test
    @DisplayName("Should create provenance ID from valid code")
    void shouldCreateFromValidCode() {
        // When
        ProvenanceId id = ProvenanceId.of("PUBMED");

        // Then
        assertThat(id.value()).isEqualTo("PUBMED");
    }

    @Test
    @DisplayName("Should reject null provenance code")
    void shouldRejectNullCode() {
        // When & Then
        assertThatThrownBy(() -> ProvenanceId.of(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Provenance code cannot be null");
    }

    @Test
    @DisplayName("Should enforce equality by value")
    void shouldEnforceValueEquality() {
        // Given
        ProvenanceId id1 = ProvenanceId.of("PUBMED");
        ProvenanceId id2 = ProvenanceId.of("PUBMED");
        ProvenanceId id3 = ProvenanceId.of("EPMC");

        // Then
        assertThat(id1).isEqualTo(id2);  // Same value
        assertThat(id1).isNotEqualTo(id3);  // Different value
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
```

### Testing Domain Events

```java
package com.patra.ingest.domain.event;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlanCreatedEventTest {

    @Test
    @DisplayName("Should create event with required fields")
    void shouldCreateEventWithRequiredFields() {
        // Given
        BatchPlanId planId = BatchPlanId.generate();
        ProvenanceId provenanceId = ProvenanceId.of("PUBMED");

        // When
        PlanCreatedEvent event = new PlanCreatedEvent(
            planId,
            provenanceId,
            Instant.now()
        );

        // Then
        assertThat(event.planId()).isEqualTo(planId);
        assertThat(event.provenanceId()).isEqualTo(provenanceId);
        assertThat(event.occurredAt()).isNotNull();
    }
}
```

---

## Application Layer Testing (Mock Ports)

### Purpose

Test **use case orchestration** and **coordination logic** without database or external dependencies.

### Characteristics

- ✅ Mock domain ports (interfaces)
- ✅ Test transaction boundaries
- ✅ Verify orchestration flow
- ❌ No database (use mocks)

### Dependencies

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### Example: Testing an Orchestrator

```java
package com.patra.ingest.app.usecase.plan;

import com.patra.ingest.domain.port.PlanPort;
import com.patra.ingest.domain.port.ProvenanceConfigPort;
import com.patra.ingest.domain.model.entity.BatchPlan;
import com.patra.ingest.domain.model.aggregate.ProvenanceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {

    @Mock
    private PlanPort planPort;

    @Mock
    private ProvenanceConfigPort configPort;

    @Mock
    private SliceGenerationCoordinator sliceCoordinator;

    @InjectMocks
    private PlanIngestionOrchestrator orchestrator;

    @Test
    @DisplayName("Should create plan with slices successfully")
    void shouldCreatePlanWithSlices() {
        // Given
        CreatePlanCommand command = new CreatePlanCommand(
            ProvenanceId.of("PUBMED"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31)
        );

        ProvenanceConfiguration config = createMockConfig();
        when(configPort.findByProvenance(any())).thenReturn(Optional.of(config));

        BatchPlan savedPlan = createMockPlan();
        when(planPort.save(any())).thenReturn(savedPlan);

        when(sliceCoordinator.generateSlices(any(), any())).thenReturn(10);

        // When
        PlanCreationResult result = orchestrator.createPlan(command);

        // Then
        assertThat(result.planId()).isNotNull();
        assertThat(result.sliceCount()).isEqualTo(10);

        // Verify interactions
        verify(configPort).findByProvenance(command.provenanceId());
        verify(planPort).save(any(BatchPlan.class));
        verify(sliceCoordinator).generateSlices(any(), eq(config));
    }

    @Test
    @DisplayName("Should throw exception when config not found")
    void shouldThrowWhenConfigNotFound() {
        // Given
        CreatePlanCommand command = new CreatePlanCommand(
            ProvenanceId.of("UNKNOWN"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31)
        );

        when(configPort.findByProvenance(any())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orchestrator.createPlan(command))
            .isInstanceOf(ProvenanceConfigNotFoundException.class)
            .hasMessageContaining("UNKNOWN");

        verify(planPort, never()).save(any());
    }

    // Test helpers
    private ProvenanceConfiguration createMockConfig() {
        return ProvenanceConfiguration.builder()
            .provenance(createProvenance())
            .windowOffset(createWindowOffset())
            .build();
    }

    private BatchPlan createMockPlan() {
        return BatchPlan.builder()
            .id(BatchPlanId.generate())
            .status(PlanStatus.PENDING)
            .build();
    }
}
```

### Testing Coordinators

```java
@ExtendWith(MockitoExtension.class)
class SliceGenerationCoordinatorTest {

    @Mock
    private SlicePort slicePort;

    @InjectMocks
    private SliceGenerationCoordinator coordinator;

    @Test
    @DisplayName("Should generate weekly slices for monthly window")
    void shouldGenerateWeeklySlices() {
        // Given
        BatchPlan plan = createPlan(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31)
        );

        SlicingStrategy strategy = SlicingStrategy.weekly();

        // When
        int count = coordinator.generateSlices(plan, strategy);

        // Then
        assertThat(count).isEqualTo(5);  // 4 full weeks + 1 partial
        verify(slicePort, times(5)).save(any(Slice.class));
    }
}
```

---

## Infrastructure Layer Testing (TestContainers)

### Purpose

Test **database interactions** and **repository implementations** with a real database.

### Characteristics

- ✅ Real database (MySQL in Docker)
- ✅ Test SQL queries and mappings
- ✅ Verify MyBatis-Plus and MapStruct
- ⚠️ Slower than unit tests (seconds)

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```

### Example: Repository Integration Test

```java
package com.patra.registry.integration;

import com.patra.registry.domain.model.entity.Provenance;
import com.patra.registry.domain.model.vo.ProvenanceId;
import com.patra.registry.infra.persistence.repository.ProvenanceRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ProvenanceRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("patra_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private ProvenanceRepositoryImpl repository;

    @Test
    void shouldSaveAndFindProvenance() {
        // Given
        Provenance provenance = Provenance.builder()
            .provenanceCode(ProvenanceCode.of("PUBMED"))
            .name("PubMed")
            .baseUrl("https://pubmed.ncbi.nlm.nih.gov")
            .isActive(true)
            .build();

        // When
        repository.save(provenance);
        Optional<Provenance> found = repository.findByCode(
            ProvenanceCode.of("PUBMED")
        );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("PubMed");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void shouldUpdateProvenance() {
        // Given
        Provenance provenance = createAndSaveProvenance("EPMC");

        // When: Update name
        provenance.updateName("Europe PMC Updated");
        repository.save(provenance);

        // Then
        Optional<Provenance> updated = repository.findByCode(
            ProvenanceCode.of("EPMC")
        );
        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("Europe PMC Updated");
    }

    @Test
    void shouldFindActiveProvenances() {
        // Given
        createAndSaveProvenance("PUBMED", true);
        createAndSaveProvenance("EPMC", true);
        createAndSaveProvenance("CROSSREF", false);

        // When
        List<Provenance> activeProvenances = repository.findAllActive();

        // Then
        assertThat(activeProvenances).hasSize(2);
        assertThat(activeProvenances)
            .extracting(Provenance::provenanceCode)
            .extracting(ProvenanceCode::value)
            .containsExactlyInAnyOrder("PUBMED", "EPMC");
    }

    // Test helpers
    private Provenance createAndSaveProvenance(String code) {
        return createAndSaveProvenance(code, true);
    }

    private Provenance createAndSaveProvenance(String code, boolean isActive) {
        Provenance provenance = Provenance.builder()
            .provenanceCode(ProvenanceCode.of(code))
            .name(code + " Name")
            .baseUrl("https://" + code.toLowerCase() + ".com")
            .isActive(isActive)
            .build();
        repository.save(provenance);
        return provenance;
    }
}
```

### Base Test Class for TestContainers

Create a reusable base class to avoid repeating container setup:

```java
package com.patra.registry.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("patra_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);  // Reuse container across tests

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

Then extend it:

```java
class ProvenanceRepositoryIntegrationTest extends BaseIntegrationTest {
    // Test methods...
}
```

---

## Adapter Layer Testing (MockMvc)

### Purpose

Test **REST controllers** without starting the full application.

### Characteristics

- ✅ Test HTTP endpoints
- ✅ Validate request/response
- ✅ Mock application layer
- ❌ No database

### Example: Controller Test

```java
package com.patra.registry.adapter.rest;

import com.patra.registry.app.usecase.provenance.ProvenanceManagementUseCase;
import com.patra.registry.domain.model.entity.Provenance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProvenanceController.class)
class ProvenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProvenanceManagementUseCase useCase;

    @Test
    void shouldCreateProvenance() throws Exception {
        // Given
        String requestJson = """
            {
              "provenanceCode": "PUBMED",
              "name": "PubMed",
              "baseUrl": "https://pubmed.ncbi.nlm.nih.gov"
            }
            """;

        Provenance provenance = createMockProvenance();
        when(useCase.create(any())).thenReturn(provenance);

        // When & Then
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.provenanceCode").value("PUBMED"))
            .andExpect(jsonPath("$.name").value("PubMed"));

        verify(useCase).create(any());
    }

    @Test
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        // Given: Missing required field
        String invalidJson = """
            {
              "name": "PubMed"
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    private Provenance createMockProvenance() {
        return Provenance.builder()
            .provenanceCode(ProvenanceCode.of("PUBMED"))
            .name("PubMed")
            .baseUrl("https://pubmed.ncbi.nlm.nih.gov")
            .build();
    }
}
```

---

## ArchUnit Tests

See [dependency-rules.md](dependency-rules.md) for complete ArchUnit patterns.

**Quick Example**:

```java
@AnalyzeClasses(packages = "com.patra.registry")
class ArchitectureTest {

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
}
```

---

## Test Data Management

### Test Builders (Fluent API)

```java
public class ProvenanceTestBuilder {
    private ProvenanceCode code = ProvenanceCode.of("TEST");
    private String name = "Test Provenance";
    private String baseUrl = "https://test.com";
    private boolean isActive = true;

    public static ProvenanceTestBuilder aProvenance() {
        return new ProvenanceTestBuilder();
    }

    public ProvenanceTestBuilder withCode(String code) {
        this.code = ProvenanceCode.of(code);
        return this;
    }

    public ProvenanceTestBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public ProvenanceTestBuilder inactive() {
        this.isActive = false;
        return this;
    }

    public Provenance build() {
        return Provenance.builder()
            .provenanceCode(code)
            .name(name)
            .baseUrl(baseUrl)
            .isActive(isActive)
            .build();
    }
}
```

**Usage**:

```java
Provenance pubmed = aProvenance()
    .withCode("PUBMED")
    .withName("PubMed")
    .build();

Provenance inactive = aProvenance()
    .withCode("OLD")
    .inactive()
    .build();
```

---

## Best Practices

### 1. Test Naming Conventions

Use descriptive test method names:

```java
// ✅ Good
@Test
void shouldCreatePlanWhenValidCommandProvided() { }

@Test
void shouldThrowExceptionWhenProvenanceNotFound() { }

// ❌ Bad
@Test
void testCreatePlan() { }

@Test
void test1() { }
```

### 2. AAA Pattern (Arrange-Act-Assert)

```java
@Test
void shouldCalculateSliceCount() {
    // Arrange (Given)
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 1, 31);

    // Act (When)
    int count = SliceCalculator.calculateMonthlySlices(start, end);

    // Assert (Then)
    assertThat(count).isEqualTo(1);
}
```

### 3. Use AssertJ for Fluent Assertions

```java
// ✅ Good (AssertJ)
assertThat(result.planId()).isNotNull();
assertThat(result.sliceCount()).isGreaterThan(0);
assertThat(result.status()).isEqualTo(PlanStatus.PENDING);

// ❌ Avoid (JUnit assertions)
assertNotNull(result.planId());
assertTrue(result.sliceCount() > 0);
assertEquals(PlanStatus.PENDING, result.status());
```

### 4. Avoid Test Interdependence

```java
// ❌ Bad: Tests depend on order
@Test
void test1_createUser() {
    user = userService.create("test");
}

@Test
void test2_updateUser() {
    userService.update(user, "updated");  // Depends on test1
}

// ✅ Good: Each test is independent
@Test
void shouldCreateUser() {
    User user = userService.create("test");
    assertThat(user).isNotNull();
}

@Test
void shouldUpdateUser() {
    User user = createTestUser();  // Create fresh user
    userService.update(user, "updated");
}
```

---

## Summary

**Testing Strategy**:
- Domain: Pure unit tests, no mocks
- Application: Mock ports, test orchestration
- Infrastructure: TestContainers, real database
- Adapter: MockMvc, mock application layer
- Architecture: ArchUnit for dependency validation

**Key Tools**:
- JUnit 5: Test framework
- Mockito: Mocking
- AssertJ: Fluent assertions
- TestContainers: Database integration
- ArchUnit: Architecture validation

**See Also**:
- [dependency-rules.md](dependency-rules.md) - ArchUnit patterns
- [architecture-overview.md](architecture-overview.md) - Layer responsibilities
