# Papertrace Testing Commands

> **Comprehensive testing command system** for automated test creation, execution, and quality validation.

---

## 📋 Overview

This testing command system provides **12 slash commands** organized into 3 categories:

1. **Test Creation** (4 commands) - Auto-generate unit and integration tests
2. **Test Execution** (4 commands) - Run tests with various scopes
3. **Quality Gates** (4 commands) - Validate quality thresholds

**Key Features**:
- ✅ **Auto-detects git diff** (current branch vs main)
- ✅ **Layer-aware** (domain/app → unit, infra/adapter/boot → integration)
- ✅ **Module-scoped** (test only changed modules)
- ✅ **Coverage-integrated** (JaCoCo reports)
- ✅ **Quality gates** (thresholds, standards, CI/CD readiness)

---

## 🎯 Quick Start

### After Coding

```bash
# 1. Auto-generate all tests for changed files
/auto

# 2. Run all tests
/run-all

# 3. Check quality gate
/gate-check
```

### Before Committing

```bash
# Fast pre-commit validation
/pre-commit

# If passed, commit
git commit -am "feat: your feature with tests"
```

### Before Pushing

```bash
# Full CI/CD readiness check
/gate-ci

# If passed, push
git push origin $(git branch --show-current)
```

---

## 📦 Commands Reference

### Test Creation Commands

#### `/unit [module-filter]`

**Purpose**: Create unit tests for changed domain/app classes

**Auto-detects**: Java files in `*-domain/` and `*-app/` directories

**Generates**:
- JUnit 5 tests with `@Nested` structure
- Mockito for dependencies
- Tests for: Aggregates, Value Objects, Orchestrators, Validators

**Usage**:
```bash
# Generate unit tests for all changed domain/app files
/unit

# Generate unit tests for specific module
/unit patra-ingest

# Output:
# - Created 15 unit tests
# - patra-ingest-domain/src/test/java/.../PlanAggregateTest.java
# - patra-ingest-app/src/test/java/.../PlanOrchestratorTest.java
# - Coverage estimate: ~85%
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

#### `/integration [module-filter]`

**Purpose**: Create integration tests for changed infra/adapter/boot classes

**Auto-detects**: Java files in `*-infra/`, `*-adapter/`, and `*-boot/` directories

**Generates**:
- Repository tests: `@SpringBootTest` + `@Transactional`
- Controller tests: `@WebMvcTest` + MockMvc
- Boot tests: End-to-end with `TestRestTemplate`

**Usage**:
```bash
# Generate integration tests for all changed infra/adapter/boot files
/integration

# Generate integration tests for specific module
/integration patra-registry

# Output:
# - Created 12 integration tests
# - patra-registry-infra/src/test/java/.../ProvenanceRepositoryMpImplTest.java
# - patra-registry-adapter/src/test/java/.../ProvenanceControllerTest.java
# - patra-registry-boot/src/test/java/.../ProvenanceIntegrationTest.java
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

#### `/auto [module-filter]`

**Purpose**: Auto-detect all changes and create both unit and integration tests

**Best for**: Complete test coverage in one command

**Usage**:
```bash
# Generate all tests for all changed files
/auto

# Generate all tests for specific module
/auto patra-ingest

# Output:
# Unit Tests Generated: 15
# Integration Tests Generated: 12
# Total Coverage: ~82%
# Execution: mvn test (unit), mvn verify (integration)
```

---

#### `/analyze [module-name]`

**Purpose**: Analyze test coverage gaps and provide actionable recommendations

**Generates**:
- Missing test files report
- Incomplete test analysis
- Coverage metrics (if JaCoCo report exists)
- Prioritized action plan

**Usage**:
```bash
# Analyze all changed modules
/analyze

# Analyze specific module
/analyze patra-ingest-domain

# Output:
# Missing Tests: 3 (HIGH priority)
# Incomplete Tests: 5 (MEDIUM priority)
# Well-Tested: 12 (complete coverage)
# Action Plan: [prioritized list]
```

---

### Test Execution Commands

#### `/run-unit [module-filter]`

**Purpose**: Run unit tests (domain/app layers) with optional module filter

**Usage**:
```bash
# Run all unit tests
/run-unit

# Run unit tests for specific service
/run-unit patra-ingest

# Output:
# ✅ Unit Tests PASSED
# Tests run: 127, Failures: 0, Duration: 12.5s
```

---

#### `/run-integration [module-filter]`

**Purpose**: Run integration tests (infra/adapter/boot layers)

**Usage**:
```bash
# Run all integration tests
/run-integration

# Run integration tests for specific service
/run-integration patra-registry

# Output:
# ✅ Integration Tests PASSED
# Tests run: 84, Failures: 0, Duration: 45.8s
```

---

#### `/run-module <module-name>`

**Purpose**: Run all tests (unit + integration) for a specific module

**Required Argument**: Module name (e.g., `patra-ingest`)

**Usage**:
```bash
# Run all tests for patra-ingest
/run-module patra-ingest

# Output:
# ✅ All Tests PASSED for patra-ingest
# Unit Tests: 83 passed
# Integration Tests: 62 passed
# Coverage: 87% line, 78% branch
```

---

#### `/run-all`

**Purpose**: Run all tests across entire project with coverage report

**Best for**: Full validation before commit/push

**Usage**:
```bash
# Run complete test suite
/run-all

# Output:
# ✅ ALL TESTS PASSED
# Total Tests: 298, Duration: 107s
# Coverage: 82% line, 73% branch, 87% method
# Report: target/site/jacoco-aggregate/index.html
```

---

### Quality Gate Commands

#### `/gate-check`

**Purpose**: Run complete quality gate validation (tests + coverage + standards)

**Validates**:
- Gate 1: Test Success Rate (must be 100%)
- Gate 2: Coverage Thresholds (75% line, 65% branch, 80% method)
- Gate 3: Code Compilation (all modules compile)
- Gate 4: Code Quality (no TODOs, System.out, @Ignore)

**Usage**:
```bash
# Run full quality gate
/gate-check

# Output:
# ✅ QUALITY GATE: PASSED
# Quality Score: 100/100
# Ready for production
```

---

#### `/gate-coverage`

**Purpose**: Check coverage thresholds and generate detailed coverage report

**Best for**: Focused coverage analysis

**Usage**:
```bash
# Validate coverage
/gate-coverage

# Output:
# ✅ COVERAGE GATE PASSED
# Line: 82% >= 75% ✅
# Branch: 73% >= 65% ✅
# Method: 87% >= 80% ✅
# Report: target/site/jacoco-aggregate/index.html
```

---

#### `/pre-commit`

**Purpose**: Fast quality checks before committing (~30 seconds)

**Validates**:
- Compilation (10s)
- Changed module tests only (15s)
- Code quality (5s)

**Best for**: Quick validation before git commit

**Usage**:
```bash
# Fast pre-commit check
/pre-commit

# If passed:
# ✅ PRE-COMMIT GATE PASSED (28s)
# Ready to commit

# Then:
git commit -am "your message"
```

---

#### `/gate-ci`

**Purpose**: CI/CD readiness check (comprehensive validation for pipeline)

**Validates**:
- Build success (clean build from scratch)
- Test success (100%, no flaky tests)
- Coverage (meets thresholds, no regression)
- Code quality (no warnings, TODOs, debugging code)
- Dependency security (no critical vulnerabilities)
- Build performance (< 10 minutes)

**Best for**: Final validation before push to trigger CI/CD

**Usage**:
```bash
# CI/CD readiness check
/gate-ci

# If passed:
# ✅ CI/CD READINESS: APPROVED
# Ready for deployment

# Then:
git push origin $(git branch --show-current)
```

---

## 🔄 Recommended Workflows

### Workflow 1: TDD (Test-Driven Development)

```bash
# 1. Write failing test
/unit patra-ingest-domain

# 2. Implement feature
# ... code changes ...

# 3. Run tests
/run-unit patra-ingest-domain

# 4. Refine until green
# ... repeat 2-3 ...

# 5. Run full validation
/gate-check
```

### Workflow 2: Feature Development

```bash
# 1. Implement feature
# ... code changes ...

# 2. Auto-generate all tests
/auto

# 3. Run all tests
/run-all

# 4. Check coverage
/gate-coverage

# 5. If gaps exist, analyze and fix
/analyze
/unit [module-with-gaps]

# 6. Final validation
/gate-check

# 7. Commit
/pre-commit
git commit -am "feat: your feature"

# 8. Push
/gate-ci
git push
```

### Workflow 3: Bug Fix

```bash
# 1. Write failing test that reproduces bug
/integration patra-ingest-infra

# 2. Fix bug
# ... code changes ...

# 3. Verify fix
/run-module patra-ingest

# 4. Fast commit
/pre-commit
git commit -am "fix: bug description"

# 5. Push
/gate-ci
git push
```

### Workflow 4: Refactoring

```bash
# 1. Ensure existing tests pass
/run-all

# 2. Refactor code
# ... code changes ...

# 3. Run tests (should still pass)
/run-all

# 4. Check for coverage regression
/gate-coverage

# 5. Commit if no regression
/pre-commit
git commit -am "refactor: description"
```

---

## 🎯 Best Practices

### DO ✅

1. **Run `/auto` after feature implementation** - Complete test coverage in one command
2. **Run `/pre-commit` before every commit** - Catch issues early (30s check)
3. **Run `/gate-ci` before every push** - Ensure CI/CD readiness
4. **Use module filters** - Test only changed code for speed (`/unit patra-ingest`)
5. **Review generated tests** - AI generates good tests, but review for completeness
6. **Fix flaky tests immediately** - Don't ignore with `@Ignore`
7. **Keep coverage above thresholds** - 75% line, 65% branch, 80% method

### DON'T ❌

1. **Don't commit without `/pre-commit` passing** - Breaks CI/CD
2. **Don't push without `/gate-ci` passing** - Deployment will fail
3. **Don't ignore failing tests with `@Ignore`** - Fix or delete
4. **Don't skip test generation** - Tests are not optional
5. **Don't manually write all tests** - Use `/auto` for efficiency
6. **Don't commit with coverage regression** - Coverage must not decrease
7. **Don't merge with TODOs** - Complete or remove before merge

---

## 📊 Coverage Thresholds

**Defined Thresholds**:
- Line Coverage: ≥ 75%
- Branch Coverage: ≥ 65%
- Method Coverage: ≥ 80%

**Why These Numbers**:
- **75% line**: Industry standard for good coverage
- **65% branch**: All major code paths tested
- **80% method**: Most public methods tested

**Coverage by Layer** (typical):
- Domain Layer: ~90% (critical business logic)
- App Layer: ~85% (use case orchestration)
- Infra Layer: ~78% (persistence logic)
- Adapter Layer: ~74% (HTTP endpoints)

---

## 🔧 Configuration

### JaCoCo Configuration

Located in: `patra-parent/pom.xml`

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <configuration>
        <rules>
            <rule>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.75</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

**To adjust thresholds**: Modify `<minimum>0.75</minimum>` in parent POM.

---

## 📝 Command Aliases

Create shell aliases for frequently used commands:

```bash
# Add to ~/.bashrc or ~/.zshrc

alias tc-unit='/unit'
alias tc-int='/integration'
alias tc-auto='/auto'
alias tc-run='/run-all'
alias tc-check='/gate-check'
alias tc-pre='/pre-commit'
alias tc-ci='/gate-ci'
```

---

## 🐛 Troubleshooting

### Tests fail with "Database connection error"

**Solution**: Ensure test database is configured
```bash
# Check test application.yaml
cat */src/test/resources/application*.yaml | grep datasource

# H2 should be configured for tests
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb
```

### Coverage report not generated

**Solution**: Run with JaCoCo enabled
```bash
mvn clean verify -Djacoco.skip=false
```

### Tests are too slow

**Solution**: Enable parallel execution
```bash
# Add to pom.xml
<configuration>
    <parallel>classes</parallel>
    <threadCount>4</threadCount>
</configuration>
```

### Flaky tests detected

**Solution**: Fix or delete (don't ignore)
1. Identify root cause (race condition, timing, external dependency)
2. Fix with proper synchronization or test isolation
3. If unfixable, delete (don't use `@Ignore`)

---

## 📚 Additional Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JaCoCo Maven Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)

---

## 🎓 Learning Path

**Beginner**:
1. Start with `/auto` to generate tests
2. Review generated tests to learn patterns
3. Use `/run-all` to execute
4. Use `/gate-check` to validate

**Intermediate**:
1. Use `/unit` and `/integration` separately
2. Customize generated tests
3. Use `/analyze` to find gaps
4. Use `/run-module` for targeted testing

**Advanced**:
1. Create tests manually following patterns
2. Use `/pre-commit` for fast feedback
3. Use `/gate-ci` for deployment readiness
4. Optimize test performance

---

**Last Updated**: 2025-01-12
**Commands Version**: 1.0.0
**Total Commands**: 12 (4 create + 4 run + 4 gate)
