---
allowed-tools: Bash(git:*), Bash(mvn:*), Bash(grep:*), Bash(find:*)
description: Pre-commit validation (fast quality checks before committing)
---

# Pre-Commit Gate

## Your Task

Run fast quality checks before committing code. This is a lightweight version of the full quality gate optimized for speed.

### Step 1: Determine Changed Files

Analyze git changes:
```bash
# Current branch
git branch --show-current

# Changed files (staged + unstaged)
git diff main...HEAD --name-only --diff-filter=AM

# Files to be committed (staged)
git diff --cached --name-only
```

### Step 2: Fast Quality Checks (< 30 seconds)

Run quick validations:

**Check 1: Compilation** (10s)
```bash
mvn compile -q -DskipTests
```
✅ PASS: All code compiles
❌ FAIL: Compilation errors

**Check 2: Changed Module Tests Only** (15s)
```bash
# Identify changed modules
CHANGED_MODULES=$(git diff main...HEAD --name-only | grep -E 'patra-[^/]+' | cut -d/ -f1 | sort -u)

# Run tests only for changed modules
mvn test -pl ${CHANGED_MODULES} -am -q
```
✅ PASS: All tests pass for changed modules
❌ FAIL: Test failures

**Check 3: Code Quality** (5s)
```bash
# Check for common issues
grep -r "System.out.println" $(git diff main...HEAD --name-only | grep "src/main/java")
grep -r "TODO\|FIXME" $(git diff --cached --name-only)
grep -r "@Ignore\|@Disabled" $(git diff main...HEAD --name-only | grep "Test.java")
```
✅ PASS: No issues
⚠️  WARN: Issues found (not blocking)

### Step 3: Generate Pre-Commit Report

**Success Report**:
```
═══════════════════════════════════════════════════════════
PRE-COMMIT GATE: PASSED ✅
═══════════════════════════════════════════════════════════

Changed Files: 12
  - Java files: 8
  - Test files: 4
  - Other: 0

Changed Modules:
  - patra-ingest-domain
  - patra-ingest-app
  - patra-ingest-infra

Fast Checks (completed in 28s):
  ✅ Compilation successful
  ✅ Module tests passed (83/83)
  ✅ No code quality issues

Ready to Commit:
  Staged files: 12
  Unstaged files: 0
  Untracked files: 0

Recommended Commit Command:
  git commit -m "feat: implement plan ingestion with comprehensive tests"

Next Steps:
  1. Commit your changes: git commit
  2. Run full validation: /gate-check (recommended before push)
  3. Push when ready: git push
```

**Failure Report**:
```
═══════════════════════════════════════════════════════════
PRE-COMMIT GATE: FAILED ❌
═══════════════════════════════════════════════════════════

Changed Files: 12
Changed Modules: 3

Fast Checks (completed in 18s):
  ❌ Compilation failed
  ❌ Module tests failed (80/83 passed, 3 failed)
  ⚠️  Code quality issues found

═══════════════════════════════════════════════════════════
COMPILATION ERRORS (2)
═══════════════════════════════════════════════════════════
  1. PlanAggregate.java:145
     Error: Cannot find symbol 'slicingStrategy'
     File: patra-ingest-domain/src/main/java/.../PlanAggregate.java

  2. PlanOrchestrator.java:67
     Error: Incompatible types: PlanResult cannot be converted to Result
     File: patra-ingest-app/src/main/java/.../PlanOrchestrator.java

═══════════════════════════════════════════════════════════
TEST FAILURES (3)
═══════════════════════════════════════════════════════════
  1. PlanAggregateTest.testStateTransition
     Module: patra-ingest-domain
     Error: AssertionError: Expected SLICING but was DRAFT

  2. TaskAggregateTest.testRetryLogic
     Module: patra-ingest-domain
     Error: AssertionError: Expected retry count 1 but was 0

  3. PlanRepositoryMpImplTest.testOptimisticLocking
     Module: patra-ingest-infra
     Error: NullPointerException

═══════════════════════════════════════════════════════════
CODE QUALITY ISSUES (2 warnings)
═══════════════════════════════════════════════════════════
  ⚠️  Found 2 TODO comments:
     - PlanAggregate.java:89
     - PlanOrchestrator.java:123

  ⚠️  Found 1 @Ignore annotation:
     - PlanAggregateTest.java:145

═══════════════════════════════════════════════════════════
ACTION REQUIRED
═══════════════════════════════════════════════════════════

Priority 1: Fix Compilation Errors (CRITICAL)
  These must be fixed before anything else
  1. Fix symbol reference in PlanAggregate.java:145
  2. Fix type mismatch in PlanOrchestrator.java:67

Priority 2: Fix Test Failures
  After compilation is fixed:
  1. Fix state transition test
  2. Fix retry logic test
  3. Fix optimistic locking test

Priority 3: Clean Up Code Quality (Optional)
  Before final commit:
  1. Complete or remove TODO comments
  2. Re-enable or delete ignored tests

Next Steps:
  1. Fix compilation errors
  2. Re-run: /pre-commit
  3. When pass, commit: git commit

DO NOT COMMIT until pre-commit gate passes.
```

### Step 4: Selective Test Execution

Only test changed code for speed:

```
═══════════════════════════════════════════════════════════
SELECTIVE TEST EXECUTION
═══════════════════════════════════════════════════════════

Strategy: Test only changed modules (not entire project)

Changed Modules Detected:
  - patra-ingest-domain    (15 changed files)
  - patra-ingest-app       (8 changed files)
  - patra-ingest-infra     (4 changed files)

Tests Executed:
  patra-ingest-domain:     45 tests ✅ (5.2s)
  patra-ingest-app:        38 tests ✅ (3.8s)
  patra-ingest-infra:      28 tests ✅ (9.5s)

Total Tests: 111 (vs 298 if full suite)
Total Time: 18.5s (vs 107s if full suite)
Time Saved: 88.5s (83% faster)

Note: Run /gate-check for full validation before push.
```

### Step 5: Smart Recommendations

Provide context-aware recommendations:

**If only domain/app changed** (pure business logic):
```
✅ Only domain/app layers changed (pure business logic)

Risk Level: LOW
  - No database changes
  - No API changes
  - No infrastructure changes

Recommendation:
  ✅ Safe to commit after pre-commit passes
  ✅ Consider running /run-integration before push (optional)
  ✅ Full /gate-check before merge to main
```

**If infra/adapter/boot changed** (integration points):
```
⚠️  Infra/adapter/boot layers changed (integration points)

Risk Level: MEDIUM-HIGH
  - Database schema may have changed
  - API contracts may have changed
  - External integrations affected

Recommendation:
  ⚠️  Pre-commit is not sufficient
  ✅ MUST run /run-integration before commit
  ✅ MUST run /gate-check before push
  ✅ Consider manual integration testing
```

**If API module changed** (public contracts):
```
⚠️  API module changed (public contracts)

Risk Level: HIGH
  - Breaking changes may affect consumers
  - API versioning may be required
  - Documentation updates needed

Recommendation:
  ⚠️  Pre-commit is not sufficient
  ✅ MUST verify backward compatibility
  ✅ MUST update API documentation
  ✅ MUST run full /gate-check
  ✅ Consider updating API version
```

### Step 6: Final Pre-Commit Decision

**PASS**:
```
✅ PRE-COMMIT GATE PASSED

Quick validation complete (28s):
  ✅ Code compiles
  ✅ Changed module tests pass
  ✅ No critical code quality issues

You may proceed to commit:
  git commit -m "your message"

Recommended Next Steps:
  1. Commit your changes
  2. Run /gate-check (before push)
  3. Push when full gate passes
```

**FAIL**:
```
❌ PRE-COMMIT GATE FAILED

DO NOT COMMIT

Issues Found:
  ❌ 2 compilation errors
  ❌ 3 test failures
  ⚠️  2 code quality warnings

Fix issues and re-run:
  /pre-commit

Estimated fix time: 10-20 minutes
```

Begin pre-commit validation now.
