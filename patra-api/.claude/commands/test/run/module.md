---
allowed-tools: Bash(mvn:*), Bash(find:*)
argument-hint: <module-name>
description: Run all tests (unit + integration) for a specific module
---

# Run Module Tests

## Your Task

Execute all tests (unit and integration) for a specific module.

**Required Argument**: Module name (e.g., `patra-ingest`, `patra-registry`)

**Argument**: $ARGUMENTS

### Step 1: Validate Module Name

Check if module exists:
```bash
# List all modules
find . -name "pom.xml" -type f | grep -v target | grep "$ARGUMENTS"
```

If module not found, show available modules and exit.

### Step 2: Determine Module Structure

For module `patra-{service}`, identify submodules:
- `patra-{service}-domain` (unit tests)
- `patra-{service}-app` (unit tests)
- `patra-{service}-infra` (integration tests)
- `patra-{service}-adapter` (integration tests)
- `patra-{service}-boot` (integration tests)
- `patra-{service}-api` (usually no tests)

### Step 3: Execute All Tests

Run Maven with all submodules:

**Command**:
```bash
mvn clean verify \
  -pl patra-{service}-domain,patra-{service}-app,patra-{service}-infra,patra-{service}-adapter,patra-{service}-boot \
  -am
```

**This executes**:
- Unit tests (Surefire) for domain/app
- Integration tests (Failsafe) for infra/adapter/boot
- Generates JaCoCo coverage report

### Step 4: Parse and Display Comprehensive Results

**Success Output**:
```
✅ All Tests PASSED for patra-{service}

═══════════════════════════════════════════════════════════
UNIT TESTS
═══════════════════════════════════════════════════════════
  ✅ patra-{service}-domain
     Tests: 45 passed, 0 failed
     Duration: 5.2s

  ✅ patra-{service}-app
     Tests: 38 passed, 0 failed
     Duration: 3.8s

═══════════════════════════════════════════════════════════
INTEGRATION TESTS
═══════════════════════════════════════════════════════════
  ✅ patra-{service}-infra
     Tests: 28 passed, 0 failed
     Duration: 18.5s

  ✅ patra-{service}-adapter
     Tests: 22 passed, 0 failed
     Duration: 12.3s

  ✅ patra-{service}-boot
     Tests: 12 passed, 0 failed
     Duration: 15.0s

═══════════════════════════════════════════════════════════
SUMMARY
═══════════════════════════════════════════════════════════
  Total Tests:      145
  Passed:           145 ✅
  Failed:           0
  Errors:           0
  Skipped:          0
  Total Duration:   55.8s

═══════════════════════════════════════════════════════════
COVERAGE (JaCoCo)
═══════════════════════════════════════════════════════════
  Line Coverage:    87% ✅ (target: 75%)
  Branch Coverage:  78% ✅ (target: 65%)
  Method Coverage:  92% ✅ (target: 80%)

  Report: patra-{service}/patra-{service}-boot/target/site/jacoco/index.html

Next Steps:
  ✅ All tests passed for patra-{service}
  ✅ Coverage thresholds met
  - Ready for commit
  - Run quality gate: /gate-check
```

**Failure Output**:
```
❌ Tests FAILED for patra-{service}

═══════════════════════════════════════════════════════════
UNIT TESTS
═══════════════════════════════════════════════════════════
  ❌ patra-{service}-domain (2 failures)
     - PlanAggregateTest.testStateTransition
     - TaskAggregateTest.testRetryLogic

  ✅ patra-{service}-app (38 passed)

═══════════════════════════════════════════════════════════
INTEGRATION TESTS
═══════════════════════════════════════════════════════════
  ❌ patra-{service}-infra (1 error)
     - PlanRepositoryMpImplTest.testOptimisticLocking

  ✅ patra-{service}-adapter (22 passed)
  ✅ patra-{service}-boot (12 passed)

═══════════════════════════════════════════════════════════
SUMMARY
═══════════════════════════════════════════════════════════
  Total Tests:      145
  Passed:           142 ✅
  Failed:           2 ❌
  Errors:           1 ❌
  Skipped:          0
  Total Duration:   52.3s

═══════════════════════════════════════════════════════════
FAILURE ANALYSIS
═══════════════════════════════════════════════════════════
1. patra-{service}-domain/PlanAggregateTest.testStateTransition
   Type: AssertionError
   Location: PlanAggregateTest.java:145
   Message: Expected status SLICING but was DRAFT

2. patra-{service}-domain/TaskAggregateTest.testRetryLogic
   Type: AssertionError
   Location: TaskAggregateTest.java:89
   Message: Expected retry count 1 but was 0

3. patra-{service}-infra/PlanRepositoryMpImplTest.testOptimisticLocking
   Type: NullPointerException
   Location: PlanRepositoryMpImplTest.java:203
   Message: Cannot invoke method on null object

Next Steps:
  1. Fix failing tests (see failure details above)
  2. Re-run module tests: /run-module $ARGUMENTS
  3. View detailed logs: cat patra-{service}/target/surefire-reports/*.txt
```

### Step 5: Show Coverage Details

If coverage data available:
```
═══════════════════════════════════════════════════════════
DETAILED COVERAGE
═══════════════════════════════════════════════════════════
Package: com.patra.{service}.domain.aggregate
  Line Coverage:    95% ✅
  Branch Coverage:  88% ✅
  Classes:          5/5 (100%)

Package: com.patra.{service}.app.usecase
  Line Coverage:    82% ✅
  Branch Coverage:  70% ✅
  Classes:          8/8 (100%)

Package: com.patra.{service}.infra.persistence
  Line Coverage:    79% ✅
  Branch Coverage:  65% ✅
  Classes:          12/12 (100%)

Package: com.patra.{service}.adapter.inbound
  Line Coverage:    75% ✅
  Branch Coverage:  60% ⚠️  (close to threshold)
  Classes:          6/6 (100%)

Low Coverage Areas (< 75%):
  - com.patra.{service}.domain.vo.WindowSpec: 68%
    → Action: Add tests for edge cases

View HTML Report:
  open patra-{service}/patra-{service}-boot/target/site/jacoco/index.html
```

### Step 6: Provide Actionable Next Steps

**If all pass**:
1. Show coverage report location
2. Suggest committing changes
3. Recommend running full quality gate

**If failures exist**:
1. Categorize failures (unit vs integration)
2. Suggest specific fixes
3. Provide re-run command
4. Offer to analyze test gaps

Begin module test execution now.
