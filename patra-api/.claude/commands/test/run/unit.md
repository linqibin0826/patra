---
allowed-tools: Bash(mvn:*)
argument-hint: [module-filter]
description: Run unit tests (domain/app layers) with optional module filter
---

# Run Unit Tests

## Your Task

Execute unit tests for domain and app layers.

### Step 1: Determine Test Scope

**Filter**: $ARGUMENTS

If filter provided:
- Run tests only for modules matching filter pattern
- Example: `patra-ingest` runs tests for all patra-ingest-* modules

If no filter:
- Run tests for ALL domain and app modules

### Step 2: Execute Maven Tests

Run Maven Surefire plugin (unit tests):

**Command Template**:
```bash
mvn clean test \
  -pl [modules] \
  -am \
  -DskipITs=true \
  -Dtest='**/*Test.java,!**/*IntegrationTest.java'
```

**Parameters**:
- `-pl`: Project list (comma-separated modules)
- `-am`: Also make dependencies
- `-DskipITs=true`: Skip integration tests
- `-Dtest`: Run *Test.java, exclude *IntegrationTest.java

**Example Commands**:

```bash
# Run all unit tests
mvn clean test -DskipITs=true

# Run unit tests for specific service
mvn clean test -pl patra-ingest-domain,patra-ingest-app -DskipITs=true

# Run unit tests for all domain layers
mvn clean test -pl '*-domain' -DskipITs=true

# Run unit tests for all app layers
mvn clean test -pl '*-app' -DskipITs=true
```

### Step 3: Parse and Display Results

After test execution, parse output and display:

**Success Output**:
```
✅ Unit Tests PASSED

Results:
  Tests run:    127
  Failures:     0
  Errors:       0
  Skipped:      3
  Duration:     12.5s

Modules Tested:
  ✅ patra-ingest-domain      (45 tests, 5.2s)
  ✅ patra-ingest-app          (38 tests, 3.8s)
  ✅ patra-registry-domain     (28 tests, 2.1s)
  ✅ patra-registry-app        (16 tests, 1.4s)

Next Steps:
  - Run integration tests: /run-integration
  - Check coverage: /gate-coverage
  - Run all tests: /run-all
```

**Failure Output**:
```
❌ Unit Tests FAILED

Results:
  Tests run:    127
  Failures:     3
  Errors:       1
  Skipped:      3
  Duration:     10.2s

Failed Tests:
  ❌ patra-ingest-domain
     - PlanAggregateTest.testStateTransition (AssertionError)
     - TaskAggregateTest.testRetryLogic (AssertionError)

  ❌ patra-registry-app
     - ProvenanceOrchestratorTest.testCreateProvenance (NullPointerException)

Next Steps:
  1. Review failure details in logs
  2. Fix failing tests
  3. Re-run: /run-unit $ARGUMENTS
```

### Step 4: Provide Actionable Guidance

Based on results:

**If all tests pass**:
- Proceed to integration tests
- Check test coverage
- Consider quality gate validation

**If tests fail**:
- Show file paths to failing tests
- Suggest debugging commands
- Offer to analyze failure patterns

Begin test execution now.
