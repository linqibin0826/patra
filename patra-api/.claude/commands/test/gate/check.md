---
allowed-tools: Bash(mvn:*), Bash(git:*), Bash(find:*)
description: Run complete quality gate validation (tests + coverage + standards)
---

# Quality Gate Check

## Your Task

Execute comprehensive quality gate validation to determine if code is ready for production.

###

 Step 1: Execute Full Test Suite

Run all tests with coverage:
```bash
mvn clean verify -Djacoco.skip=false
```

### Step 2: Quality Gate Criteria

Check against defined quality gates:

**Gate 1: Test Success Rate**
- ✅ PASS: 100% tests must pass (0 failures, 0 errors)
- ❌ FAIL: Any test failure or error

**Gate 2: Coverage Thresholds**
- ✅ PASS: Line coverage ≥ 75%
- ✅ PASS: Branch coverage ≥ 65%
- ✅ PASS: Method coverage ≥ 80%
- ❌ FAIL: Any threshold not met

**Gate 3: Code Compilation**
- ✅ PASS: All modules compile successfully
- ❌ FAIL: Compilation errors

**Gate 4: No Critical Issues**
- ✅ PASS: No TODO/FIXME in changed files
- ✅ PASS: No System.out.println in production code
- ✅ PASS: No @Ignore/@Disabled tests
- ❌ FAIL: Any critical issue found

### Step 3: Generate Quality Gate Report

**Success Report**:
```
═══════════════════════════════════════════════════════════
QUALITY GATE: PASSED ✅
═══════════════════════════════════════════════════════════

Gate 1: Test Success Rate
  ✅ All tests passed (298/298)
  ✅ Zero failures
  ✅ Zero errors
  ✅ Test success rate: 100%

Gate 2: Coverage Thresholds
  ✅ Line coverage: 82% (target: 75%) → +7%
  ✅ Branch coverage: 73% (target: 65%) → +8%
  ✅ Method coverage: 87% (target: 80%) → +7%
  ✅ All thresholds exceeded

Gate 3: Code Compilation
  ✅ All 23 modules compiled successfully
  ✅ Zero compilation errors
  ✅ Zero warnings

Gate 4: Code Quality
  ✅ No TODO/FIXME in changed files
  ✅ No System.out.println in production code
  ✅ No ignored/disabled tests
  ✅ All quality checks passed

═══════════════════════════════════════════════════════════
OVERALL ASSESSMENT
═══════════════════════════════════════════════════════════
Status: READY FOR PRODUCTION ✅

Quality Score: 100/100
  - Test Success: 25/25 ✅
  - Coverage: 25/25 ✅
  - Compilation: 25/25 ✅
  - Code Quality: 25/25 ✅

Git Status:
  Branch: feat/your-feature
  Ahead of main: 5 commits
  Changed files: 23
  Tests added: 45

Recommendations:
  1. ✅ Ready to commit
  2. ✅ Ready to push
  3. ✅ Ready to create PR
  4. ✅ Ready to deploy

Next Steps:
  git commit -am "Implement feature with comprehensive tests"
  git push origin $(git branch --show-current)
  # Create PR if needed
```

**Failure Report**:
```
═══════════════════════════════════════════════════════════
QUALITY GATE: FAILED ❌
═══════════════════════════════════════════════════════════

Gate 1: Test Success Rate ❌
  ❌ Tests failed (294/298 passed)
  ❌ 3 failures detected
  ❌ 1 error detected
  ❌ Test success rate: 98.7% (must be 100%)

  Failed Tests:
    1. PlanAggregateTest.testStateTransition
    2. TaskAggregateTest.testRetryLogic
    3. PlanRepositoryMpImplTest.testOptimisticLocking

Gate 2: Coverage Thresholds ⚠️
  ✅ Line coverage: 80% (target: 75%) → +5%
  ❌ Branch coverage: 62% (target: 65%) → -3%
  ✅ Method coverage: 84% (target: 80%) → +4%
  ⚠️  1 threshold not met

Gate 3: Code Compilation ✅
  ✅ All modules compiled successfully

Gate 4: Code Quality ⚠️
  ❌ Found 3 TODO comments in changed files
  ✅ No System.out.println
  ❌ Found 1 @Ignore annotation

═══════════════════════════════════════════════════════════
OVERALL ASSESSMENT
═══════════════════════════════════════════════════════════
Status: NOT READY FOR PRODUCTION ❌

Quality Score: 62/100
  - Test Success: 0/25 ❌ (critical failure)
  - Coverage: 17/25 ⚠️  (1 threshold missed)
  - Compilation: 25/25 ✅
  - Code Quality: 15/25 ⚠️  (TODOs and ignored tests)

Critical Issues: 2
  1. Test failures must be fixed (3 failures, 1 error)
  2. Branch coverage below threshold (62% < 65%)

Warnings: 2
  1. Remove TODO comments before committing
  2. Remove @Ignore annotations or delete tests

═══════════════════════════════════════════════════════════
ACTION PLAN
═══════════════════════════════════════════════════════════

Priority 1: Fix Test Failures (CRITICAL)
  Step 1: Fix unit test failures
    /unit patra-ingest-domain
    /run-module patra-ingest

  Step 2: Fix integration test failures
    /integration patra-ingest-infra
    /run-module patra-ingest

Priority 2: Improve Branch Coverage
  Step 3: Add tests for missing branches
    /analyze  # Identify gaps
    /unit [module-with-gaps]

Priority 3: Clean Up Code Quality Issues
  Step 4: Remove TODO comments
    Find: grep -r "TODO" $(git diff main...HEAD --name-only)
    Action: Complete TODOs or remove comments

  Step 5: Handle ignored tests
    Find: grep -r "@Ignore\|@Disabled" $(git diff main...HEAD --name-only)
    Action: Fix and re-enable, or delete if obsolete

Final Step: Re-run Quality Gate
  /gate-check

Estimated Time to Fix: 30-60 minutes
```

### Step 4: Detailed Breakdown

Provide detailed metrics for each gate:

**Test Success Breakdown**:
```
Module                  Tests  Pass  Fail  Error  Success
──────────────────────────────────────────────────────────
patra-ingest-domain       45    43     2      0    95.6%  ❌
patra-ingest-app          38    38     0      0   100.0%  ✅
patra-ingest-infra        28    27     0      1    96.4%  ❌
patra-registry-domain     28    28     0      0   100.0%  ✅
... (other modules)
──────────────────────────────────────────────────────────
TOTAL                    298   294     3      1    98.7%  ❌
```

**Coverage Breakdown**:
```
Module                  Line    Branch  Method  Status
──────────────────────────────────────────────────────────
patra-ingest-domain     91%     85%     95%     ✅
patra-ingest-app        85%     75%     88%     ✅
patra-ingest-infra      78%     68%     82%     ✅
patra-ingest-adapter    74%     62%     79%     ❌ (branch)
... (other modules)
──────────────────────────────────────────────────────────
AGGREGATE               82%     73%     87%     ✅
```

**Code Quality Issues**:
```
Issue Type          Count  Files
─────────────────────────────────────────────────────────
TODO comments         3    PlanAggregate.java
                           TaskAggregate.java
                           PlanOrchestrator.java

@Ignore annotations   1    PlanAggregateTest.java

System.out.println    0    -
```

### Step 5: Final Decision

Provide clear go/no-go decision:

**PASS**:
```
✅ QUALITY GATE PASSED - READY FOR PRODUCTION

All criteria met:
  ✅ 100% test success
  ✅ All coverage thresholds exceeded
  ✅ Clean compilation
  ✅ No critical code quality issues

You may proceed to:
  - Commit your changes
  - Push to remote
  - Create pull request
  - Deploy to production
```

**FAIL**:
```
❌ QUALITY GATE FAILED - NOT READY FOR PRODUCTION

Critical issues must be resolved before proceeding.

DO NOT:
  - Commit failing code
  - Push to remote
  - Create pull request
  - Deploy anywhere

MUST DO:
  1. Fix all test failures
  2. Meet coverage thresholds
  3. Resolve code quality issues
  4. Re-run: /gate-check

Estimated remediation time: 30-60 minutes
```

Begin quality gate validation now.
