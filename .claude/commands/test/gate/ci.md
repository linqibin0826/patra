---
allowed-tools: Bash(mvn:*), Bash(git:*), Bash(find:*)
description: CI/CD readiness check (comprehensive validation for pipeline)
---

# CI/CD Readiness Gate

## Your Task

Perform comprehensive validation suitable for CI/CD pipeline execution. This is the strictest quality gate.

### Step 1: Environment Validation

Check CI/CD prerequisites:

**Java Environment**:
```bash
java -version  # Should be Java 21
mvn -version   # Should be Maven 3.9+
```

**Git State**:
```bash
git status                          # Clean working directory?
git diff main...HEAD --stat         # Changes summary
git log main..HEAD --oneline        # Commits ahead of main
```

### Step 2: Clean Build from Scratch

Execute full clean build:
```bash
mvn clean verify \
  -U \
  -Dmaven.test.failure.ignore=false \
  -Djacoco.skip=false \
  -Dstyle.skip=false
```

**Flags Explained**:
- `-U`: Force update of dependencies
- `clean`: Remove all previous builds
- `verify`: Run all tests (unit + integration)
- `maven.test.failure.ignore=false`: Fail fast on test failure
- `jacoco.skip=false`: Generate coverage reports
- `style.skip=false`: Run style checks (if configured)

### Step 3: CI/CD Quality Gates

Apply strict CI/CD criteria:

**Gate 1: Build Success** (CRITICAL)
- ✅ PASS: mvn verify returns exit code 0
- ❌ FAIL: Any non-zero exit code

**Gate 2: Test Success** (CRITICAL)
- ✅ PASS: 100% test success (0 failures, 0 errors)
- ❌ FAIL: Any test failure or error
- ❌ FAIL: Flaky tests (inconsistent results)

**Gate 3: Coverage** (CRITICAL)
- ✅ PASS: Line ≥ 75%, Branch ≥ 65%, Method ≥ 80%
- ❌ FAIL: Any threshold not met
- ❌ FAIL: Coverage regression (< main branch)

**Gate 4: Code Quality** (CRITICAL)
- ✅ PASS: No compilation warnings
- ✅ PASS: No TODOs/FIXMEs in production code
- ✅ PASS: No @Ignore/@Disabled tests
- ✅ PASS: No System.out/printStackTrace
- ❌ FAIL: Any quality issue

**Gate 5: Dependency Security** (if configured)
- ✅ PASS: No known vulnerabilities
- ⚠️  WARN: Low/medium severity vulnerabilities
- ❌ FAIL: High/critical severity vulnerabilities

**Gate 6: Build Performance** (WARNING only)
- ✅ PASS: Build completes < 10 minutes
- ⚠️  WARN: Build takes 10-15 minutes
- ⚠️  WARN: Build takes > 15 minutes (needs optimization)

### Step 4: Generate CI/CD Report

**Success Report**:
```
═══════════════════════════════════════════════════════════
CI/CD READINESS GATE: PASSED ✅
═══════════════════════════════════════════════════════════

Environment:
  Java Version:     21.0.1
  Maven Version:    3.9.5
  OS:               macOS 14.2
  Branch:           feat/your-feature
  Commits ahead:    5

Build Summary:
  ✅ Clean build successful
  ✅ All modules compiled (23/23)
  ✅ Dependencies resolved
  ✅ No compilation warnings

Test Results:
  ✅ All tests passed (298/298)
  ✅ Zero failures
  ✅ Zero errors
  ✅ Zero flaky tests
  ✅ Test duration: 107s

Coverage Metrics:
  ✅ Line coverage: 82% (target: 75%) → +7%
  ✅ Branch coverage: 73% (target: 65%) → +8%
  ✅ Method coverage: 87% (target: 80%) → +7%
  ✅ No coverage regressions vs main

Code Quality:
  ✅ Zero compilation warnings
  ✅ No TODOs in production code
  ✅ No ignored/disabled tests
  ✅ No debugging code (System.out, printStackTrace)

Dependency Security:
  ✅ No high/critical vulnerabilities
  ⚠️  2 low severity vulnerabilities (acceptable)

Build Performance:
  ✅ Total build time: 8.5 minutes
  ✅ Within acceptable range (< 10 min)

  Breakdown:
    Compilation:      1.2 min
    Unit tests:       2.5 min
    Integration tests: 4.3 min
    Coverage report:  0.5 min

═══════════════════════════════════════════════════════════
CI/CD READINESS: APPROVED ✅
═══════════════════════════════════════════════════════════

Quality Score: 100/100
  Build:           20/20 ✅
  Tests:           20/20 ✅
  Coverage:        20/20 ✅
  Code Quality:    20/20 ✅
  Security:        18/20 ✅
  Performance:     2/0   ✅ (bonus)

Branch Status:
  - Ready for CI/CD pipeline
  - Ready for merge to main
  - Ready for deployment

Deployment Checklist:
  ✅ All quality gates passed
  ✅ No blocking issues
  ✅ No critical vulnerabilities
  ✅ Performance acceptable

Next Steps:
  1. ✅ Push to remote: git push origin feat/your-feature
  2. ✅ CI/CD pipeline will auto-deploy
  3. ✅ Monitor deployment logs
  4. ✅ Verify in staging environment
```

**Failure Report**:
```
═══════════════════════════════════════════════════════════
CI/CD READINESS GATE: FAILED ❌
═══════════════════════════════════════════════════════════

Environment:
  Java Version:     21.0.1
  Maven Version:    3.9.5
  Branch:           feat/your-feature

Build Summary:
  ❌ Build failed with errors
  ❌ 2 modules failed to compile
  ⚠️  18 compilation warnings

Test Results:
  ❌ Tests failed (294/298 passed)
  ❌ 3 test failures
  ❌ 1 test error
  ⚠️  1 flaky test detected (intermittent failure)

Coverage Metrics:
  ⚠️  Line coverage: 72% (target: 75%) → -3%
  ❌ Branch coverage: 59% (target: 65%) → -6%
  ✅ Method coverage: 84% (target: 80%) → +4%
  ❌ Coverage regression vs main (-5% line coverage)

Code Quality:
  ⚠️  18 compilation warnings
  ❌ 5 TODO comments in production code
  ❌ 2 ignored tests found
  ❌ 1 System.out.println in production code

Dependency Security:
  ❌ 1 critical vulnerability found
  ⚠️  3 high severity vulnerabilities
  ⚠️  8 medium severity vulnerabilities

Build Performance:
  ⚠️  Total build time: 12.3 minutes (exceeds 10 min target)

═══════════════════════════════════════════════════════════
CI/CD READINESS: REJECTED ❌
═══════════════════════════════════════════════════════════

Quality Score: 42/100 (FAILING)
  Build:           8/20  ❌ (compilation issues)
  Tests:           10/20 ❌ (failures + flaky)
  Coverage:        8/20  ❌ (below threshold + regression)
  Code Quality:    6/20  ❌ (warnings + TODOs + debugging code)
  Security:        5/20  ❌ (critical vulnerabilities)
  Performance:     -2/0  ⚠️  (too slow)

Branch Status:
  - NOT ready for CI/CD pipeline
  - NOT ready for merge to main
  - NOT ready for deployment

CRITICAL ISSUES (must fix before CI/CD):
  1. Compilation errors in 2 modules
  2. 4 test failures
  3. Critical security vulnerability
  4. Coverage below threshold
  5. Coverage regression vs main

BLOCKING ISSUES:
  ❌ Build does not compile
  ❌ Tests are failing
  ❌ Critical security vulnerability
  ❌ Coverage regression

═══════════════════════════════════════════════════════════
ACTION PLAN (Priority Order)
═══════════════════════════════════════════════════════════

Step 1: Fix Compilation (CRITICAL - 30 min)
  Module: patra-ingest-domain
    Fix: PlanAggregate.java:145 - symbol not found
  Module: patra-registry-app
    Fix: ProvenanceOrchestrator.java:67 - type mismatch

Step 2: Fix Test Failures (CRITICAL - 45 min)
  /unit patra-ingest-domain
  /integration patra-ingest-infra
  /run-module patra-ingest

Step 3: Fix Security Vulnerability (CRITICAL - 15 min)
  Critical: log4j 2.14.1 → upgrade to 2.17.1
  Command: mvn versions:use-latest-versions -Dincludes=org.apache.logging.log4j
  Verify: mvn dependency:tree | grep log4j

Step 4: Improve Coverage (HIGH - 60 min)
  Add tests to bring coverage above threshold
  Target modules: patra-ingest-adapter, patra-egress-gateway-boot
  /unit patra-ingest-adapter
  /integration patra-egress-gateway-boot

Step 5: Clean Code Quality (MEDIUM - 30 min)
  Remove TODOs, fix warnings, remove debugging code
  Grep commands:
    grep -r "TODO" $(git diff main...HEAD --name-only | grep "src/main/java")
    grep -r "System.out.println" $(git diff main...HEAD --name-only | grep "src/main/java")

Step 6: Re-validate
  /gate-ci

Estimated Total Fix Time: 3-4 hours

DO NOT proceed to CI/CD until all critical issues are resolved.
```

### Step 5: Flaky Test Detection

Identify intermittent test failures:

```
═══════════════════════════════════════════════════════════
FLAKY TEST DETECTION
═══════════════════════════════════════════════════════════

Running tests 3 times to detect flakiness...

Run 1: 298/298 passed ✅
Run 2: 297/298 passed ❌ (PlanAggregateTest.testConcurrency failed)
Run 3: 298/298 passed ✅

Flaky Tests Detected: 1
  ❌ PlanAggregateTest.testConcurrency
     Success rate: 66.7% (2/3)
     Likely cause: Race condition or timing issue
     File: patra-ingest-domain/.../PlanAggregateTest.java:234

Action Required:
  1. Fix flaky test (add proper synchronization)
  2. Or delete if not essential
  3. DO NOT ignore with @Ignore (hides the problem)

Flaky tests are NOT acceptable in CI/CD.
```

### Step 6: Dependency Security Scan

Check for known vulnerabilities:

```
═══════════════════════════════════════════════════════════
DEPENDENCY SECURITY SCAN
═══════════════════════════════════════════════════════════

Scan completed: 287 dependencies analyzed

Critical Vulnerabilities: 0 ✅
High Vulnerabilities:     0 ✅
Medium Vulnerabilities:   2 ⚠️
Low Vulnerabilities:      3 ⚠️

Medium Severity:
  ⚠️  jackson-databind 2.15.0
     CVE-2023-xxxxx (Medium, CVSS 5.9)
     Fix: Upgrade to 2.15.3+
     Command: mvn versions:use-latest-versions -Dincludes=com.fasterxml.jackson.core

  ⚠️  spring-web 6.0.9
     CVE-2023-yyyyy (Medium, CVSS 6.1)
     Fix: Upgrade to 6.0.13+
     Command: Update spring-boot-dependencies to 3.2.1+

Low Severity:
  ℹ️  (3 low severity issues - acceptable for now)

Recommendation:
  ✅ No blocking vulnerabilities
  ⚠️  Consider upgrading medium severity deps before production
  ✅ CI/CD can proceed
```

### Step 7: Performance Analysis

Analyze build performance:

```
═══════════════════════════════════════════════════════════
BUILD PERFORMANCE ANALYSIS
═══════════════════════════════════════════════════════════

Total Build Time: 8.5 minutes

Phase Breakdown:
  Dependency Resolution:    0.8 min  ( 9%)
  Compilation:              1.2 min  (14%)
  Unit Tests:               2.5 min  (29%)  ✅ Fast
  Integration Tests:        4.3 min  (51%)  ⚠️  Slowest phase
  Coverage Report:          0.5 min  ( 6%)
  Packaging:                0.2 min  ( 2%)

Slowest Integration Tests:
  1. patra-ingest-boot: 45s
  2. patra-registry-boot: 38s
  3. patra-egress-gateway-boot: 22s

Optimization Opportunities:
  ⚠️  Integration tests take 51% of build time
  💡 Consider:
     - Parallel test execution (surefire.parallel=classes)
     - Test data caching
     - Testcontainers reuse
     - H2 instead of full MySQL for tests

Estimated improvement: 20-30% faster (6-7 min total)
```

### Step 8: Final CI/CD Decision

**PASS**:
```
✅ CI/CD READINESS: APPROVED FOR DEPLOYMENT

All critical gates passed:
  ✅ Build successful
  ✅ All tests passed
  ✅ Coverage thresholds met
  ✅ No code quality issues
  ✅ No critical vulnerabilities
  ✅ Performance acceptable

This branch is ready for:
  ✅ Merge to main
  ✅ CI/CD pipeline execution
  ✅ Deployment to staging
  ✅ Deployment to production (after staging validation)

Proceed with confidence!
```

**FAIL**:
```
❌ CI/CD READINESS: REJECTED FOR DEPLOYMENT

Critical failures detected:
  ❌ Build errors
  ❌ Test failures
  ❌ Critical security vulnerabilities
  ❌ Coverage below threshold

This branch is NOT ready for:
  ❌ Merge to main
  ❌ CI/CD pipeline execution
  ❌ Any deployment

MUST resolve all critical issues before proceeding.

Estimated remediation time: 3-4 hours

DO NOT attempt to bypass these checks.
Quality gates exist to protect production.
```

Begin CI/CD readiness validation now.
