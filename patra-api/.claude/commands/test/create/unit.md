---
allowed-tools: Bash(git:*), Bash(find:*), Bash(grep:*), Read, Write, Edit, Glob
argument-hint: [module-filter]
description: Create unit tests for changed domain/app classes (auto-detects git diff from main)
---

# Create Unit Tests

## Context

**Git Diff Analysis**:
- Current branch: !`git branch --show-current`
- Base branch: main
- Changed Java files: !`git diff main...HEAD --name-only --diff-filter=AM | grep '\.java$' | grep -E '(domain|app)/'`
- Untracked Java files: !`git ls-files --others --exclude-standard | grep '\.java$' | grep -E '(domain|app)/'`

**Project Structure**: !`find . -name "pom.xml" -type f | grep -v target | head -20`

## Your Task

Generate comprehensive **unit tests** for all changed Java classes in domain and app layers.

### Step 1: Identify Target Classes

From the git diff above, identify all Java classes that need unit tests:
- **Domain layer**: `*-domain/src/main/java/.../domain/`
  - Aggregates (extends `AggregateRoot`)
  - Value Objects (records)
  - Domain Events (implements `DomainEvent`)
  - Enums with logic
- **App layer**: `*-app/src/main/java/.../app/`
  - Orchestrators (*Orchestrator.java)
  - Use case implementations
  - Validators, builders, converters

**Exclude**: Interfaces (ports), DTOs without logic, simple POJOs.

**Filter**: $ARGUMENTS (if provided, only generate tests for modules matching this pattern)

### Step 2: Analyze Each Class

For each target class:
1. Read the source file
2. Identify:
   - Public methods (behavior to test)
   - State transitions (for aggregates)
   - Validation logic
   - Business rules
   - Dependencies (for mocking)

### Step 3: Generate JUnit 5 Tests

Create test files in `src/test/java` (same package structure as source).

**Naming Convention**: `{ClassName}Test.java`

**Test Structure**:
```java
package com.patra.{service}.{layer}.{package};

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("{ClassName} Unit Tests")
class {ClassName}Test {

    @Mock
    private DependencyClass dependency;

    @Nested
    @DisplayName("Constructor and Factory Methods")
    class ConstructorTests {
        @Test
        @DisplayName("create() should initialize with valid parameters")
        void testCreateWithValidParams() {
            // Arrange
            // Act
            // Assert
        }
    }

    @Nested
    @DisplayName("Business Logic")
    class BusinessLogicTests {
        @Test
        @DisplayName("methodName() should ... when ...")
        void testMethodNameBehavior() {
            // Arrange
            // Act
            // Assert
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {
        @Test
        @DisplayName("should throw exception when ...")
        void testValidationFailure() {
            // Arrange
            // Act & Assert
            assertThrows(ExpectedException.class, () -> {
                // code that should throw
            });
        }
    }

    @Nested
    @DisplayName("State Transitions (for Aggregates)")
    class StateTransitionTests {
        @Test
        @DisplayName("should transition from DRAFT to ACTIVE")
        void testStateTransition() {
            // Arrange
            // Act
            // Assert
        }
    }
}
```

**Guidelines**:
- Test **behavior**, not implementation details
- Use `@Nested` classes to group related tests
- Use `@DisplayName` for readable test descriptions
- Use **Arrange-Act-Assert** pattern
- Mock external dependencies
- Test edge cases and error conditions
- For aggregates: Test state machines, domain events
- For value objects: Test validation, immutability
- For orchestrators: Mock ports, verify method calls

### Step 4: Coverage Focus

Ensure tests cover:
- ✅ All public methods
- ✅ State transitions (aggregates)
- ✅ Validation logic (fail fast)
- ✅ Business rules enforcement
- ✅ Domain event generation
- ✅ Edge cases (null, empty, boundary values)
- ✅ Error conditions (exceptions)

### Step 5: Summary

After generating all tests, provide:
1. **List of generated test files** (with file paths)
2. **Coverage estimate** (methods tested / total methods)
3. **Test execution command**: `mvn test -pl {module}`

**Important Notes**:
- Do NOT create tests for:
  - Simple getters/setters
  - DTOs without logic
  - Interface definitions (ports)
  - Configuration classes
- Do NOT use Spring context (@SpringBootTest) for unit tests
- Use Mockito for mocking, not real implementations
- Tests should run fast (<1 second per test class)

Begin generating unit tests now.
