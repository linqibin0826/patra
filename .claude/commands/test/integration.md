---
allowed-tools: Bash(git:*), Bash(find:*), Bash(grep:*), Read, Write, Edit, Glob
argument-hint: [module-filter]
description: Create integration tests for changed infra/adapter/boot classes (auto-detects git diff from main)
---

# Create Integration Tests

## Context

**Git Diff Analysis**:
- Current branch: !`git branch --show-current`
- Base branch: main
- Changed Java files: !`git diff main...HEAD --name-only --diff-filter=AM | grep '\.java$' | grep -E '(infra|adapter|boot)/'`
- Untracked Java files: !`git ls-files --others --exclude-standard | grep '\.java$' | grep -E '(infra|adapter|boot)/'`

**Project Structure**: !`find . -name "pom.xml" -type f | grep -v target | head -20`

## Your Task

Generate comprehensive **integration tests** for all changed Java classes in infra, adapter, and boot layers.

### Step 1: Identify Target Classes and Check Existing Tests

From the git diff above, identify all Java classes that need integration tests:

**Target Layers**:
- **Infra layer**: `*-infra/src/main/java/.../infra/`
  - Repository implementations (*RepositoryMpImpl.java)
  - External service adapters
  - HTTP clients
  - Configuration readers
- **Adapter layer**: `*-adapter/src/main/java/.../adapter/`
  - REST controllers (*Controller.java)
  - Job schedulers (*Scheduler.java)
  - MQ listeners
- **Boot layer**: `*-boot/src/main/java/`
  - Application main class
  - End-to-end flows

**Exclude**: Simple converters, mappers, DTOs.

**CRITICAL - Check Existing Tests**:
For each candidate class:
1. Derive expected test file path:
   - Source: `src/main/java/.../FooRepository.java`
   - Test: `src/test/java/.../FooRepositoryTest.java`
2. Check if test file exists: `test -f {test-path}`
3. **Skip if exists** (do NOT regenerate)
4. **Generate only if missing**

**Filter**: $ARGUMENTS (if provided, only generate tests for modules matching this pattern)

### Step 2: Analyze Each Missing Test Class

For each class **without an existing test**:
1. Read the source file
2. Identify:
   - Integration points (database, HTTP, MQ)
   - Dependencies (auto-wired beans)
   - Contract adherence (port implementations)
   - Data transformation logic

### Step 3: Generate Integration Tests

Create test files based on layer:

#### For Repository Tests (`*-infra/src/test/java`)

**Naming**: `{RepositoryClass}Test.java`

**Template**:
```java
package com.patra.{service}.infra.persistence.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("{RepositoryClass} Integration Tests")
class {RepositoryClass}Test {

    @Autowired
    private {RepositoryClass} repository;

    @Test
    @DisplayName("save() should persist aggregate to database")
    void testSave() {
        // Arrange
        Aggregate aggregate = Aggregate.create(...);

        // Act
        Aggregate saved = repository.save(aggregate);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(..., saved.get...());
    }

    @Test
    @DisplayName("findById() should retrieve persisted aggregate")
    void testFindById() {
        // Arrange
        Aggregate aggregate = repository.save(Aggregate.create(...));

        // Act
        Optional<Aggregate> found = repository.findById(aggregate.getId());

        // Assert
        assertTrue(found.isPresent());
        assertEquals(aggregate.getId(), found.get().getId());
    }

    @Test
    @DisplayName("findByBusinessKey() should query by indexed field")
    void testFindByBusinessKey() {
        // Arrange
        Aggregate aggregate = repository.save(Aggregate.create("KEY123", ...));

        // Act
        Optional<Aggregate> found = repository.findByBusinessKey("KEY123");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("KEY123", found.get().getBusinessKey());
    }

    @Test
    @DisplayName("optimistic locking should prevent concurrent updates")
    void testOptimisticLocking() {
        // Arrange
        Aggregate aggregate = repository.save(Aggregate.create(...));
        Aggregate ref1 = repository.findById(aggregate.getId()).get();
        Aggregate ref2 = repository.findById(aggregate.getId()).get();

        // Act
        ref1.updateField("value1");
        repository.save(ref1);

        ref2.updateField("value2");

        // Assert
        assertThrows(OptimisticLockingFailureException.class, () -> {
            repository.save(ref2);
        });
    }
}
```

#### For Controller Tests (`*-adapter/src/test/java`)

**Naming**: `{ControllerClass}Test.java`

**Template**:
```java
package com.patra.{service}.adapter.inbound.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ControllerClass}.class)
@DisplayName("{ControllerClass} Integration Tests")
class {ControllerClass}Test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private {UseCase}UseCase useCase;

    @Test
    @DisplayName("POST /endpoint should create resource")
    void testCreateResource() throws Exception {
        // Arrange
        when(useCase.execute(any())).thenReturn(new Result(...));

        String requestBody = """
            {
                "field1": "value1",
                "field2": "value2"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/endpoint")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field1").value("value1"));

        verify(useCase).execute(any());
    }

    @Test
    @DisplayName("GET /endpoint/{id} should return resource")
    void testGetResource() throws Exception {
        // Arrange
        when(useCase.findById(1L)).thenReturn(Optional.of(new DTO(...)));

        // Act & Assert
        mockMvc.perform(get("/api/endpoint/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /endpoint with invalid data should return 400")
    void testValidationFailure() throws Exception {
        // Arrange
        String invalidRequest = """
            {
                "field1": ""
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/endpoint")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
}
```

#### For Boot Tests (`*-boot/src/test/java`)

**Naming**: `{Feature}IntegrationTest.java`

**Template**:
```java
package com.patra.{service};

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DisplayName("{Feature} End-to-End Integration Tests")
class {Feature}IntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("should complete end-to-end flow successfully")
    void testEndToEndFlow() {
        // Arrange
        CreateRequest request = new CreateRequest(...);

        // Act - Step 1: Create
        var createResponse = restTemplate.postForEntity(
            "/api/resource", request, CreateResponse.class);

        // Assert - Step 1
        assertEquals(200, createResponse.getStatusCodeValue());
        assertNotNull(createResponse.getBody().getId());

        // Act - Step 2: Retrieve
        Long id = createResponse.getBody().getId();
        var getResponse = restTemplate.getForEntity(
            "/api/resource/" + id, ResourceDTO.class);

        // Assert - Step 2
        assertEquals(200, getResponse.getStatusCodeValue());
        assertEquals(id, getResponse.getBody().getId());
    }
}
```

### Step 4: Test Data Management

For integration tests, provide:
- Test data setup in `@BeforeEach` or inline
- Use `@Transactional` for automatic rollback
- Use test-specific application properties (`application-test.yaml`)

### Step 5: Coverage Focus

Ensure tests cover:
- ✅ Database persistence (CRUD operations)
- ✅ Query correctness (indexes, pagination)
- ✅ Transaction management
- ✅ Optimistic locking
- ✅ HTTP endpoint contracts (status codes, JSON)
- ✅ Input validation (400 errors)
- ✅ Error handling (404, 500)
- ✅ End-to-end flows (multi-step scenarios)

### Step 6: Summary

After generating all tests, provide:

**Tests Created (NEW)**:
```
- patra-{service}-infra/src/test/java/.../PlanRepositoryMpImplTest.java
- patra-{service}-adapter/src/test/java/.../PlanControllerTest.java
- patra-{service}-boot/src/test/java/.../PlanIntegrationTest.java
Total Created: X integration tests
```

**Tests Skipped (EXISTING)**:
```
- patra-{service}-infra/src/test/java/.../TaskRepositoryMpImplTest.java (already exists)
- patra-{service}-adapter/src/test/java/.../TaskControllerTest.java (already exists)
Total Skipped: Y integration tests
```

**Summary**:
- Total classes changed: N
- Tests created: X (new)
- Tests skipped: Y (existing)
- Total test coverage: X + Y tests

**Test execution commands**:
- Repository tests: `mvn test -pl {service}-infra`
- Controller tests: `mvn test -pl {service}-adapter`
- Boot tests: `mvn verify -pl {service}-boot`

**Important Notes**:
- **CRITICAL**: Skip existing tests (do NOT regenerate)
- Only create missing tests
- Report both created and skipped tests in summary
- Integration tests use Spring context (@SpringBootTest, @WebMvcTest)
- Use @Transactional for automatic database rollback
- Mock only external systems, not internal beans
- Tests may be slower (1-5 seconds per test class)
- Ensure test database is configured (H2 or Testcontainers)

Begin generating integration tests now.
