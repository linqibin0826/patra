---
allowed-tools: Bash(git:*), Bash(find:*), Bash(grep:*), Read, Write, Edit, Glob
argument-hint: [module-filter]
description: Auto-detect changes and create both unit and integration tests (one command for complete coverage)
---

# Auto-Create All Tests

## Context

**Git Diff Analysis**:
- Current branch: !`git branch --show-current`
- Base branch: main
- All changed Java files: !`git diff main...HEAD --name-only --diff-filter=AM | grep '\.java$'`
- All untracked Java files: !`git ls-files --others --exclude-standard | grep '\.java$'`

**Project Structure**: !`find . -name "pom.xml" -type f | grep -v target | head -20`

## Your Task

Automatically detect all code changes and generate complete test coverage (both unit and integration tests).

### Phase 1: Categorize Changed Files

From the git diff above, categorize changed files by layer:

**Unit Test Candidates** (domain/app layers):
- Domain classes: `*-domain/src/main/java/`
- App classes: `*-app/src/main/java/`

**Integration Test Candidates** (infra/adapter/boot layers):
- Infra classes: `*-infra/src/main/java/`
- Adapter classes: `*-adapter/src/main/java/`
- Boot classes: `*-boot/src/main/java/`

**Filter**: $ARGUMENTS (if provided, only generate tests for modules matching this pattern)

### Phase 2: Check for Existing Tests

**CRITICAL**: Before generating any tests, check if they already exist.

For each candidate class:
1. Derive expected test file path:
   - Source: `src/main/java/.../Foo.java`
   - Test: `src/test/java/.../FooTest.java`
2. Check if test file exists
3. **Skip if exists** (do NOT regenerate)
4. **Generate only if missing**

**Example Check**:
```bash
# For source file: patra-ingest-domain/src/main/java/com/patra/ingest/domain/aggregate/PlanAggregate.java
# Expected test: patra-ingest-domain/src/test/java/com/patra/ingest/domain/aggregate/PlanAggregateTest.java
test -f patra-ingest-domain/src/test/java/com/patra/ingest/domain/aggregate/PlanAggregateTest.java && echo "EXISTS" || echo "MISSING"
```

### Phase 3: Generate Unit Tests

For each **missing** domain/app test:
1. Analyze class structure (methods, state machine, validation)
2. Generate JUnit 5 + Mockito test
3. Place in `src/test/java` (same module)
4. Follow patterns from `/unit` command

**Focus**:
- Aggregates → state transitions, business rules, domain events
- Value Objects → validation, immutability
- Orchestrators → use case orchestration, mocking ports
- Validators/Builders → logic correctness

### Phase 4: Generate Integration Tests

For each **missing** infra/adapter/boot test:
1. Identify integration points (DB, HTTP, MQ)
2. Generate appropriate Spring Boot test
3. Place in `src/test/java` (same module or boot)
4. Follow patterns from `/integration` command

**Focus**:
- Repositories → CRUD, queries, transactions, optimistic locking
- Controllers → HTTP contracts, validation, error handling
- Schedulers/Jobs → execution flow
- Boot → end-to-end scenarios

### Phase 5: Test Organization

Organize generated tests:
```
{service}/
├─ {service}-domain/
│  └─ src/test/java/
│     └─ com/patra/{service}/domain/
│        ├─ aggregate/{Class}Test.java  (unit)
│        ├─ vo/{Class}Test.java         (unit)
│        └─ event/{Class}Test.java      (unit)
│
├─ {service}-app/
│  └─ src/test/java/
│     └─ com/patra/{service}/app/
│        └─ usecase/{UseCase}OrchestratorTest.java  (unit)
│
├─ {service}-infra/
│  └─ src/test/java/
│     └─ com/patra/{service}/infra/
│        ├─ persistence/repository/{Repo}Test.java  (integration)
│        └─ rpc/{Adapter}Test.java                  (integration)
│
├─ {service}-adapter/
│  └─ src/test/java/
│     └─ com/patra/{service}/adapter/
│        ├─ inbound/rest/{Controller}Test.java  (integration)
│        └─ inbound/scheduler/{Job}Test.java    (integration)
│
└─ {service}-boot/
   └─ src/test/java/
      └─ com/patra/{service}/
         └─ {Feature}IntegrationTest.java  (end-to-end)
```

### Phase 6: Summary Report

After generating all tests, provide comprehensive summary with **skip statistics**:

**Unit Tests**:
```
Created (NEW):
  - patra-{service}-domain/src/test/java/.../PlanAggregateTest.java
  - patra-{service}-app/src/test/java/.../PlanOrchestratorTest.java
  Total Created: X unit tests

Skipped (EXISTING):
  - patra-{service}-domain/src/test/java/.../TaskAggregateTest.java (already exists)
  - patra-{service}-app/src/test/java/.../TaskOrchestratorTest.java (already exists)
  Total Skipped: Y unit tests
```

**Integration Tests**:
```
Created (NEW):
  - patra-{service}-infra/src/test/java/.../PlanRepositoryMpImplTest.java
  - patra-{service}-adapter/src/test/java/.../PlanControllerTest.java
  Total Created: X integration tests

Skipped (EXISTING):
  - patra-{service}-infra/src/test/java/.../TaskRepositoryMpImplTest.java (already exists)
  Total Skipped: Y integration tests
```

**Summary**:
```
Total Classes Changed: N
Total Tests Created: M (new)
Total Tests Skipped: K (existing)
Test Coverage: M created + K existing = (M+K) tests
```

**Test Execution Commands**:
```bash
# Run all unit tests
mvn test

# Run all integration tests
mvn verify

# Run tests for specific module
mvn test -pl patra-{service}-domain,patra-{service}-app
mvn verify -pl patra-{service}-boot

# Run with coverage report
mvn clean verify
# Report: target/site/jacoco/index.html
```

**Estimated Coverage**:
- Total methods in changed classes: N
- Total test methods generated: M
- Estimated coverage: ~XX%

**Next Steps**:
1. Review generated tests for completeness
2. Run tests: `mvn test` (unit) and `mvn verify` (integration)
3. Check coverage report: `open target/site/jacoco/index.html`
4. Add/refine tests for edge cases if needed

**Important Notes**:
- **CRITICAL**: Skip existing tests (do NOT regenerate)
- Only create missing tests
- Report both created and skipped tests in summary
- Unit tests use Mockito (no Spring context)
- Integration tests use @SpringBootTest/@WebMvcTest
- All tests use JUnit 5 with @Nested and @DisplayName
- Tests follow Arrange-Act-Assert pattern
- Focus on behavior, not implementation details

Begin auto-generating tests now.
