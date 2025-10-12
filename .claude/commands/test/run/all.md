---
allowed-tools: Bash(mvn:*)
description: Run all tests (unit + integration) across entire project with coverage report
---

# Run All Tests

## Your Task

Execute complete test suite (all unit and integration tests) across the entire Papertrace project.

### Step 1: Execute Full Maven Build

Run Maven verify on root POM:

**Command**:
```bash
mvn clean verify \
  -Dmaven.test.failure.ignore=false \
  -Djacoco.skip=false
```

**This executes**:
- Clean all previous builds
- Compile all modules
- Run all unit tests (Surefire)
- Run all integration tests (Failsafe)
- Generate aggregated JaCoCo coverage report
- Fail fast on first test failure

**Duration**: ~2-5 minutes (depending on project size)

### Step 2: Parse Comprehensive Results

**Success Output**:
```
✅ ALL TESTS PASSED

═══════════════════════════════════════════════════════════
COMPLETE TEST SUMMARY
═══════════════════════════════════════════════════════════

UNIT TESTS (Domain + App)
─────────────────────────────────────────────────────────
  ✅ patra-ingest-domain        45 tests (5.2s)
  ✅ patra-ingest-app            38 tests (3.8s)
  ✅ patra-registry-domain       28 tests (2.1s)
  ✅ patra-registry-app          16 tests (1.4s)
  ✅ patra-egress-gateway-domain 12 tests (1.1s)
  ✅ patra-common                18 tests (1.8s)
  ✅ patra-expr-kernel           22 tests (2.3s)
  ─────────────────────────────────────────────────────────
  Subtotal:                     179 tests (17.7s)

INTEGRATION TESTS (Infra + Adapter + Boot)
─────────────────────────────────────────────────────────
  ✅ patra-ingest-infra          28 tests (18.5s)
  ✅ patra-ingest-adapter        22 tests (12.3s)
  ✅ patra-ingest-boot           12 tests (15.0s)
  ✅ patra-registry-infra        18 tests (10.5s)
  ✅ patra-registry-adapter      8 tests (5.2s)
  ✅ patra-registry-boot         10 tests (12.8s)
  ✅ patra-egress-gateway-infra  15 tests (8.7s)
  ✅ patra-egress-gateway-boot   6 tests (6.5s)
  ─────────────────────────────────────────────────────────
  Subtotal:                     119 tests (89.5s)

═══════════════════════════════════════════════════════════
GRAND TOTAL
═══════════════════════════════════════════════════════════
  Total Tests:        298
  Passed:             298 ✅
  Failed:             0
  Errors:             0
  Skipped:            0
  Total Duration:     107.2s (~1.8 minutes)

═══════════════════════════════════════════════════════════
COVERAGE (Aggregated across all modules)
═══════════════════════════════════════════════════════════
  Line Coverage:      82% ✅ (target: 75%)
  Branch Coverage:    73% ✅ (target: 65%)
  Method Coverage:    87% ✅ (target: 80%)
  Class Coverage:     95% ✅

  Aggregated Report: target/site/jacoco-aggregate/index.html

Coverage by Layer:
  Domain Layer:       91% ✅ (critical business logic well-tested)
  App Layer:          85% ✅ (use case orchestration covered)
  Infra Layer:        78% ✅ (persistence tested)
  Adapter Layer:      74% ⚠️  (close to threshold)

═══════════════════════════════════════════════════════════
QUALITY GATE: PASSED ✅
═══════════════════════════════════════════════════════════
  ✅ All tests passed (0 failures)
  ✅ Coverage threshold met (82% > 75%)
  ✅ No errors or exceptions
  ✅ Build successful

Next Steps:
  ✅ All quality checks passed
  - View coverage report: open target/site/jacoco-aggregate/index.html
  - Ready to commit: git add . && git commit -m "..."
  - Ready to push: git push
  - Run final gate check: /gate-check
```

**Failure Output**:
```
❌ TESTS FAILED

═══════════════════════════════════════════════════════════
FAILURE SUMMARY
═══════════════════════════════════════════════════════════

Failed Modules:
  ❌ patra-ingest-domain (2 failures)
  ❌ patra-ingest-infra (1 error)
  ❌ patra-registry-app (1 failure)

═══════════════════════════════════════════════════════════
DETAILED FAILURES
═══════════════════════════════════════════════════════════

1. patra-ingest-domain
   ❌ PlanAggregateTest.testStateTransition (line 145)
      Type: AssertionError
      Expected: SLICING
      Actual: DRAFT

   ❌ TaskAggregateTest.testRetryLogic (line 89)
      Type: AssertionError
      Expected: retry count 1
      Actual: retry count 0

2. patra-ingest-infra
   ❌ PlanRepositoryMpImplTest.testOptimisticLocking (line 203)
      Type: NullPointerException
      Message: Cannot invoke method on null object
      Cause: Database connection failed

3. patra-registry-app
   ❌ ProvenanceOrchestratorTest.testCreateProvenance (line 67)
      Type: MockitoException
      Message: Unnecessary stubbings detected
      Cause: Mock setup mismatch

═══════════════════════════════════════════════════════════
STATISTICS
═══════════════════════════════════════════════════════════
  Total Tests:        298
  Passed:             294 ✅
  Failed:             3 ❌
  Errors:             1 ❌
  Skipped:            0
  Total Duration:     98.3s

  Success Rate:       98.7% (not acceptable, must be 100%)

═══════════════════════════════════════════════════════════
COVERAGE (Partial - some modules excluded due to failures)
═══════════════════════════════════════════════════════════
  Line Coverage:      80% ✅
  Branch Coverage:    71% ✅
  Method Coverage:    84% ✅

  Note: Coverage may be incomplete due to test failures

═══════════════════════════════════════════════════════════
QUALITY GATE: FAILED ❌
═══════════════════════════════════════════════════════════
  ❌ Test failures detected (4 failures/errors)
  ✅ Coverage threshold met (where tests ran)
  ❌ Build unstable

Action Required:
  1. Fix failing tests in patra-ingest-domain
  2. Fix database issue in patra-ingest-infra
  3. Fix mock setup in patra-registry-app
  4. Re-run: /run-all

Specific Commands:
  # Fix and re-test individual modules
  /run-module patra-ingest
  /run-module patra-registry

  # View detailed logs
  cat target/surefire-reports/*.txt

  # Analyze test gaps
  /analyze
```

### Step 3: Generate Coverage Heatmap

Show coverage breakdown by module:

```
═══════════════════════════════════════════════════════════
COVERAGE HEATMAP
═══════════════════════════════════════════════════════════

Module                        Line    Branch  Method  Status
───────────────────────────────────────────────────────────
patra-ingest-domain           91%     85%     95%     ✅
patra-ingest-app              85%     75%     88%     ✅
patra-ingest-infra            78%     68%     82%     ✅
patra-ingest-adapter          74%     62%     79%     ⚠️
patra-registry-domain         94%     90%     97%     ✅
patra-registry-app            88%     78%     91%     ✅
patra-registry-infra          81%     70%     85%     ✅
patra-egress-gateway-domain   87%     76%     89%     ✅
patra-egress-gateway-infra    76%     64%     80%     ✅
patra-common                  89%     80%     92%     ✅
patra-expr-kernel             93%     88%     96%     ✅
───────────────────────────────────────────────────────────
AVERAGE                       85%     76%     88%     ✅

Legend: ✅ Above target  ⚠️ Close to target  ❌ Below target
```

### Step 4: Identify Test Gaps

If coverage < 100%:
```
═══════════════════════════════════════════════════════════
TEST GAPS (Coverage < 75%)
═══════════════════════════════════════════════════════════

Priority: HIGH
  - patra-ingest-adapter: 74% (target: 75%)
    Missing tests for:
      ✗ PlanScheduler.execute()
      ✗ ErrorMappingContributor.handle()

Priority: MEDIUM
  - patra-egress-gateway-infra: 76% (just above target)
    Consider adding tests for:
      ? HttpClientAdapter.buildHttpResponse()

Recommendation:
  /unit patra-ingest-adapter
  /run-all  # re-run after adding tests
```

### Step 5: Provide Final Recommendations

**If all pass**:
```
🎉 PROJECT IS FULLY TESTED AND READY FOR PRODUCTION

Quality Metrics:
  ✅ 100% test success rate
  ✅ 82% line coverage (target: 75%)
  ✅ Zero errors or exceptions
  ✅ All quality gates passed

Recommended Actions:
  1. Commit changes: git commit -am "Add comprehensive test coverage"
  2. Push to remote: git push origin $(git branch --show-current)
  3. Create PR (if on feature branch)
  4. Deploy with confidence
```

**If failures exist**:
```
⚠️  FIX REQUIRED BEFORE PROCEEDING

Critical Issues:
  - 4 test failures across 3 modules
  - Must achieve 100% test success rate

Action Plan:
  Step 1: Fix unit test failures
    /unit patra-ingest-domain
    /run-module patra-ingest

  Step 2: Fix integration test failures
    /integration patra-ingest-infra
    /run-module patra-ingest

  Step 3: Re-run full test suite
    /run-all

  Step 4: Validate quality gate
    /gate-check
```

Begin full test execution now.
