# Papertrace Testing Commands

> **Streamlined test generation system** for automatic test creation with smart skip-existing logic.

---

## 📋 Overview

This testing command system provides **5 slash commands** for intelligent test generation:

**Key Features**:
- ✅ **Auto-detects git diff** (current branch vs main)
- ✅ **Layer-aware** (domain/app → unit, infra/adapter/boot → integration)
- ✅ **Module-scoped** (test only changed modules)
- ✅ **Smart skip-existing** (never regenerate existing tests)
- ✅ **Detailed reporting** (shows created vs skipped tests)

---

## 🎯 Quick Start

### After Coding

```bash
# Auto-generate all missing tests for changed files
/test:auto

# Output example:
# ✅ Tests Created: 8 (new)
# ⏭️  Tests Skipped: 5 (already exist)
# 📊 Total Coverage: 13 tests
```

### For Specific Layers

```bash
# Generate only unit tests (domain/app)
/test:unit

# Generate only integration tests (infra/adapter/boot)
/test:integration

# Generate for specific module
/test:auto patra-ingest
```

---

## 📦 Commands Reference

### `/test:auto [module-filter]`

**Purpose**: Auto-detect all changes and create both unit and integration tests (one command for complete coverage)

**Best for**: Complete test coverage in one command

**Usage**:
```bash
# Generate all tests for all changed files
/test:auto

# Generate all tests for specific module
/test:auto patra-ingest

# Output:
# Unit Tests:
#   Created: 8 (new)
#   Skipped: 3 (existing)
# Integration Tests:
#   Created: 6 (new)
#   Skipped: 4 (existing)
# Total Coverage: 21 tests
```

**Smart Logic**:
- ✅ Checks if test already exists before generating
- ✅ Skips existing tests (no regeneration)
- ✅ Only creates missing tests
- ✅ Reports both created and skipped

---

### `/test:unit [module-filter]`

**Purpose**: Create unit tests for changed domain/app classes

**Auto-detects**: Java files in `*-domain/` and `*-app/` directories

**Generates**:
- JUnit 5 tests with `@Nested` structure
- Mockito for dependencies
- Tests for: Aggregates, Value Objects, Orchestrators, Validators

**Usage**:
```bash
# Generate unit tests for all changed domain/app files
/test:unit

# Generate unit tests for specific module
/test:unit patra-ingest

# Output:
# Created (NEW):
#   - patra-ingest-domain/src/test/java/.../PlanAggregateTest.java
#   - patra-ingest-app/src/test/java/.../PlanOrchestratorTest.java
#   Total Created: 8 tests
#
# Skipped (EXISTING):
#   - patra-ingest-domain/src/test/java/.../TaskAggregateTest.java
#   Total Skipped: 3 tests
```

**Example Generated Test**:
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanAggregate Unit Tests")
class PlanAggregateTest {

    @Nested
    @DisplayName("State Transitions")
    class StateTransitionTests {
        @Test
        @DisplayName("should transition from DRAFT to SLICING")
        void testStateTransition() {
            // Arrange, Act, Assert
        }
    }
}
```

---

### `/test:integration [module-filter]`

**Purpose**: Create integration tests for changed infra/adapter/boot classes

**Auto-detects**: Java files in `*-infra/`, `*-adapter/`, and `*-boot/` directories

**Generates**:
- Repository tests: `@SpringBootTest` + `@Transactional`
- Controller tests: `@WebMvcTest` + MockMvc
- Boot tests: End-to-end with `TestRestTemplate`

**Usage**:
```bash
# Generate integration tests for all changed infra/adapter/boot files
/test:integration

# Generate integration tests for specific module
/test:integration patra-registry

# Output:
# Created (NEW):
#   - patra-registry-infra/src/test/java/.../ProvenanceRepositoryMpImplTest.java
#   - patra-registry-adapter/src/test/java/.../ProvenanceControllerTest.java
#   Total Created: 6 tests
#
# Skipped (EXISTING):
#   - patra-registry-boot/src/test/java/.../ProvenanceIntegrationTest.java
#   Total Skipped: 2 tests
```

**Example Generated Test**:
```java
@SpringBootTest
@Transactional
@DisplayName("ProvenanceRepositoryMpImpl Integration Tests")
class ProvenanceRepositoryMpImplTest {

    @Autowired
    private ProvenanceRepositoryMpImpl repository;

    @Test
    @DisplayName("save() should persist aggregate with optimistic locking")
    void testSaveWithOptimisticLocking() {
        // Test implementation
    }
}
```

---

### `/test:orchestrator [orchestrator-class-path]`

**Purpose**: Create orchestrator test with mocked dependencies (app layer)

**Usage**:
```bash
# Generate test for specific orchestrator
/test:orchestrator com.patra.ingest.app.plan.PlanOrchestrator

# Output:
# ✅ Created: PlanOrchestratorTest.java
# 📍 Location: patra-ingest-app/src/test/java/.../PlanOrchestratorTest.java
```

**Focus**: Use case orchestration, port mocking, transaction boundaries

---

### `/test:analyze [module-name]`

**Purpose**: Analyze test coverage gaps and provide actionable recommendations

**Generates**:
- Missing test files report
- Incomplete test analysis
- Coverage metrics (if JaCoCo report exists)
- Prioritized action plan

**Usage**:
```bash
# Analyze all changed modules
/test:analyze

# Analyze specific module
/test:analyze patra-ingest-domain

# Output:
# Missing Tests: 3 (HIGH priority)
#   - PlanAggregate.java → needs PlanAggregateTest.java
#   - TaskAggregate.java → needs TaskAggregateTest.java
#
# Incomplete Tests: 2 (MEDIUM priority)
#   - PlanOrchestratorTest.java (only 45% coverage)
#
# Well-Tested: 12 (complete coverage)
#
# Action Plan:
#   1. Create missing tests: /test:unit patra-ingest-domain
#   2. Enhance incomplete tests manually
```

---

## 🔄 Recommended Workflows

### Workflow 1: TDD (Test-Driven Development)

```bash
# 1. Write failing test (manually or generate skeleton)
/test:unit patra-ingest-domain

# 2. Implement feature
# ... code changes ...

# 3. Run tests
mvn test -pl patra-ingest-domain

# 4. Refine until green
# ... repeat 2-3 ...
```

### Workflow 2: Feature Development

```bash
# 1. Implement feature
# ... code changes ...

# 2. Auto-generate all missing tests
/test:auto

# 3. Run all tests
mvn test

# 4. Check for gaps
/test:analyze

# 5. Commit
git add .
git commit -m "feat: your feature with tests"
```

### Workflow 3: Bug Fix

```bash
# 1. Write failing test that reproduces bug
/test:integration patra-ingest-infra

# 2. Fix bug
# ... code changes ...

# 3. Verify fix
mvn test -pl patra-ingest

# 4. Commit
git commit -am "fix: bug description with test"
```

---

## 🎯 Best Practices

### DO ✅

1. **Run `/test:auto` after feature implementation** - Complete test coverage in one command
2. **Review generated tests** - AI generates good tests, but review for completeness
3. **Use module filters** - Test only changed code for speed (`/test:unit patra-ingest`)
4. **Trust skip logic** - Existing tests won't be overwritten
5. **Use `/test:analyze` to find gaps** - Identify missing coverage

### DON'T ❌

1. **Don't manually regenerate existing tests** - The system skips them automatically
2. **Don't commit without tests** - Use `/test:auto` before committing
3. **Don't ignore test failures** - Fix or delete failing tests, don't use `@Ignore`
4. **Don't skip test generation** - Tests are not optional

---

## 📊 Test Structure

**Unit Tests** (domain/app):
```
patra-{service}-domain/src/test/java/
├── aggregate/{Class}Test.java       # State transitions, business rules
├── vo/{Class}Test.java              # Validation, immutability
└── event/{Class}Test.java           # Domain events

patra-{service}-app/src/test/java/
└── usecase/{Orchestrator}Test.java  # Use case orchestration
```

**Integration Tests** (infra/adapter/boot):
```
patra-{service}-infra/src/test/java/
└── repository/{Repo}Test.java       # CRUD, optimistic locking

patra-{service}-adapter/src/test/java/
├── rest/{Controller}Test.java       # HTTP contracts, validation
└── scheduler/{Job}Test.java         # Job execution

patra-{service}-boot/src/test/java/
└── {Feature}IntegrationTest.java    # End-to-end flows
```

---

## 🔧 Running Tests

```bash
# Run all unit tests
mvn test

# Run all integration tests
mvn verify

# Run tests for specific module
mvn test -pl patra-ingest-domain

# Run with coverage report
mvn clean verify
# Report: target/site/jacoco/index.html
```

---

## 🐛 Troubleshooting

### "Test already exists" but I want to regenerate

**Solution**: Manually delete the existing test file first, then run command
```bash
rm patra-ingest-domain/src/test/java/.../PlanAggregateTest.java
/test:unit patra-ingest-domain
```

### No tests generated

**Cause**: All tests already exist (working as designed)

**Solution**: Check output for "Skipped" section, or use `/test:analyze` to find gaps

### Tests are too basic

**Cause**: AI generates templates based on code structure

**Solution**: Review and enhance generated tests with edge cases and business logic verification

---

## 📝 Command Summary

| Command | Purpose | Scope |
|---------|---------|-------|
| `/test:auto` | Generate all missing tests | All layers |
| `/test:unit` | Generate unit tests | domain/app |
| `/test:integration` | Generate integration tests | infra/adapter/boot |
| `/test:orchestrator` | Generate orchestrator test | Specific class |
| `/test:analyze` | Analyze coverage gaps | Module/all |

---

**Last Updated**: 2025-01-13
**Commands Version**: 2.0.0 (Simplified)
**Total Commands**: 5
