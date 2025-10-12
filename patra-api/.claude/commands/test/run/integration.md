---
allowed-tools: Bash(mvn:*)
argument-hint: [module-filter]
description: Run integration tests (infra/adapter/boot layers) with optional module filter
---

# Run Integration Tests

## Your Task

Execute integration tests for infra, adapter, and boot layers.

### Step 1: Determine Test Scope

**Filter**: $ARGUMENTS

If filter provided:
- Run integration tests only for modules matching filter
- Example: `patra-registry` runs tests for patra-registry-infra, patra-registry-adapter, patra-registry-boot

If no filter:
- Run integration tests for ALL infra, adapter, and boot modules

### Step 2: Execute Maven Integration Tests

Run Maven Failsafe plugin (integration tests):

**Command Template**:
```bash
mvn clean verify \
  -pl [modules] \
  -am \
  -DskipUTs=false \
  -Dit.test='**/*Test.java'
```

**Parameters**:
- `verify`: Maven lifecycle phase that runs integration tests
- `-pl`: Project list (comma-separated modules)
- `-am`: Also make dependencies
- `-DskipUTs`: Whether to skip unit tests (false = run both)
- `-Dit.test`: Integration test pattern

**Example Commands**:

```bash
# Run all integration tests
mvn clean verify

# Run integration tests for specific service (including unit tests)
mvn clean verify -pl patra-ingest-infra,patra-ingest-adapter,patra-ingest-boot

# Run only integration tests (skip unit tests)
mvn clean verify -DskipTests -DskipUTs=true

# Run integration tests for all infra layers
mvn clean verify -pl '*-infra'

# Run integration tests for all boot modules
mvn clean verify -pl '*-boot'
```

### Step 3: Parse and Display Results

After test execution, parse output and display:

**Success Output**:
```
✅ Integration Tests PASSED

Results:
  Tests run:    84
  Failures:     0
  Errors:       0
  Skipped:      2
  Duration:     45.8s

Modules Tested:
  ✅ patra-ingest-infra        (28 tests, 18.5s)
     - Repository tests: 15 passed
     - RPC adapter tests: 13 passed

  ✅ patra-ingest-adapter      (22 tests, 12.3s)
     - Controller tests: 18 passed
     - Scheduler tests: 4 passed

  ✅ patra-ingest-boot         (12 tests, 15.0s)
     - End-to-end tests: 12 passed

  ✅ patra-registry-infra      (18 tests, 10.5s)
  ✅ patra-registry-adapter    (4 tests, 3.2s)

Next Steps:
  - Check coverage report: open target/site/jacoco/index.html
  - Run quality gate: /gate-check
  - Commit changes if all green
```

**Failure Output**:
```
❌ Integration Tests FAILED

Results:
  Tests run:    84
  Failures:     2
  Errors:       1
  Skipped:      2
  Duration:     38.2s

Failed Tests:
  ❌ patra-ingest-infra
     - PlanRepositoryMpImplTest.testOptimisticLocking (AssertionError)
       Expected: OptimisticLockingFailureException
       Actual: No exception thrown

  ❌ patra-ingest-adapter
     - PlanControllerTest.testCreatePlan (MockMvc 400 instead of 200)
       Issue: Request validation failed

  ❌ patra-registry-infra
     - ProvenanceRepositoryMpImplTest.testFindByCode (NullPointerException)
       Issue: Database connection failed

Common Failure Causes:
  1. Database not started (for repository tests)
  2. Test data setup issues
  3. Spring context configuration problems
  4. Transaction rollback issues

Next Steps:
  1. Check logs in target/surefire-reports/
  2. Verify test database configuration
  3. Fix failing tests
  4. Re-run: /run-integration $ARGUMENTS
```

### Step 4: Database/Infrastructure Checks

If tests fail, check common infrastructure issues:

**Database**:
```bash
# Check if test database is configured
grep -r "spring.datasource" */src/test/resources/application*.yaml

# Check if H2/Testcontainers is available
mvn dependency:tree | grep -E "(h2|testcontainers)"
```

**Spring Context**:
```bash
# Check for Spring Boot test dependencies
mvn dependency:tree | grep "spring-boot-starter-test"
```

### Step 5: Provide Actionable Guidance

Based on results:

**If all tests pass**:
- Show coverage report location
- Suggest quality gate validation
- Indicate readiness for commit

**If tests fail**:
- Identify failure category (DB, config, logic)
- Suggest specific fixes
- Provide debugging commands

Begin test execution now.
