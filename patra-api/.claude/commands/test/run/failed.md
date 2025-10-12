---
allowed-tools: Bash(mvn:*), Bash(grep:*), Bash(find:*)
description: Re-run only failed tests (fast feedback loop)
---

# Re-Run Failed Tests

## Context

You are re-running **only the tests that failed** in the previous test run.

This provides fast feedback for fixing test failures without running the entire suite.

### Previous Test Run Status

Check for Surefire reports from last run:
!`find . -name "TEST-*.xml" -path "*/surefire-reports/*" -type f | head -5`

### Failed Tests from Last Run

Extract failed test names:
!`grep -r "<failure\|<error" target/surefire-reports/TEST-*.xml 2>/dev/null | grep -oP 'testcase name="\K[^"]+' | head -20`

## Your Task

Re-run only the failed tests for fast feedback.

### Step 1: Identify Failed Tests

Parse Surefire XML reports to find failures:

```bash
# Find all test result XMLs
find . -name "TEST-*.xml" -path "*/surefire-reports/*"

# Extract failed test class names
grep -r "failure\|error" target/surefire-reports/ \
  | grep -oP 'TEST-\K[^.]+' \
  | sort -u
```

**Output**: List of failed test class names

### Step 2: Run Failed Tests Only

Maven Surefire provides built-in failed test rerun:

```bash
mvn surefire:test
```

**How it works**:
- Surefire stores failed test names in `target/surefire-reports/testng-failed.xml`
- `surefire:test` goal re-runs only those tests
- Much faster than full `mvn test`

**Alternative** (manual list):

If you know specific test classes:

```bash
mvn test -Dtest=FailedTest1,FailedTest2,FailedTest3
```

### Step 3: Analyze Results

After re-run, check:

**A. All Fixed** ✅
```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Action**: Celebrate! Run full test suite to verify: `/test-run-all`

**B. Some Still Failing** ⚠️
```
[INFO] Tests run: 5, Failures: 2, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

**Action**:
1. Analyze remaining failures
2. Fix issues
3. Run `/test-failed` again

**C. New Failures** ❌
```
[INFO] Tests run: 5, Failures: 3, Errors: 1, Skipped: 0
```

**Action**: Recent changes broke more tests, investigate

### Step 4: Detailed Failure Analysis

For each still-failing test, extract details:

```bash
# Get full failure message
grep -A 20 "<failure" target/surefire-reports/TEST-FailedTestClass.xml
```

**Analyze**:
- **Assertion failure**: Expected vs actual mismatch
- **Exception**: Unexpected exception thrown
- **Timeout**: Test took too long
- **Setup failure**: @BeforeEach or mocks not initialized

### Step 5: Fix Strategy

Based on failure type:

#### Assertion Failure
```
Expected: "foo"
Actual: "bar"
```

**Root causes**:
- Business logic bug
- Test expectation wrong
- Test data setup incorrect

**Fix**: Update code or test expectation

#### NullPointerException
```
java.lang.NullPointerException: Cannot invoke method on null
```

**Root causes**:
- Mock not initialized
- Dependency not injected
- Test setup incomplete

**Fix**: Check `@BeforeEach`, mock setup

#### Flaky Test
Passes sometimes, fails sometimes

**Root causes**:
- Time-dependent assertions
- Random data in tests
- Thread safety issues
- External dependencies (DB, network)

**Fix**: Make test deterministic

### Step 6: Iterative Workflow

**Fast feedback loop**:

1. Fix one test
2. Run `/test-failed` (fast, 10-30 seconds)
3. If passes, fix next test
4. Repeat until all pass
5. Run `/test-run-all` (comprehensive, 2-5 minutes)

**Benefits**:
- ⚡ Fast feedback (seconds vs minutes)
- 🎯 Focused (only failed tests)
- 🔄 Iterative (fix one, test one)

## Output Format

```
# Failed Tests Re-Run Report

**Date**: {current-date}
**Previous failures**: {count}

## Re-Run Results

**Tests re-run**: {count}
**Duration**: {X seconds}

### Status

| Test Class | Method | Previous | Current | Status |
|------------|--------|----------|---------|--------|
| PlanAggregateTest | shouldTransitionToCompleted | ❌ FAIL | ✅ PASS | Fixed |
| TaskAggregateTest | shouldHandleRetry | ❌ FAIL | ✅ PASS | Fixed |
| OrchestratorTest | shouldPublishEvents | ❌ FAIL | ❌ FAIL | Still failing |

## Summary

- **Fixed**: {count} tests ✅
- **Still failing**: {count} tests ❌
- **New failures**: {count} tests ⚠️

## Still-Failing Tests Analysis

### Test: OrchestratorTest.shouldPublishEvents

**Failure**:
```
Expected: 2 events
Actual: 0 events

Assertion failed at line 45
```

**Root cause**: Mock not returning domain events

**Recommendation**: Update mock setup:
```java
when(mockAggregate.pullDomainEvents())
    .thenReturn(List.of(event1, event2));
```

## Next Steps

{If all fixed}:
- ✅ All tests now passing!
- Run full test suite: `/test-run-all`
- Commit changes if all pass

{If some still failing}:
- ❌ Fix remaining {count} failures
- Run `/test-failed` again after fixes
- Iterate until all pass
```

## Maven Commands Reference

**Re-run last failed tests**:
```bash
mvn surefire:test
```

**Run specific tests**:
```bash
mvn test -Dtest=TestClass1,TestClass2
```

**Run specific test methods**:
```bash
mvn test -Dtest=TestClass#testMethod
```

**Run tests matching pattern**:
```bash
mvn test -Dtest=*IntegrationTest
```

**Run with debug output**:
```bash
mvn test -X -Dtest=FailedTest
```

## Troubleshooting

### Issue: "No previous test run found"

**Cause**: Surefire reports don't exist

**Solution**: Run full test suite first: `mvn test`

### Issue: "Failed tests not detected"

**Cause**: Reports from stale test run

**Solution**:
```bash
# Clean and re-run
mvn clean test
mvn surefire:test
```

### Issue: "Test passes when run alone, fails in full suite"

**Cause**: Test pollution (shared state between tests)

**Symptoms**:
- Test order dependency
- Static fields not cleaned up
- Database state not reset

**Solution**:
- Make tests independent
- Clean up state in `@AfterEach`
- Use `@DirtiesContext` for Spring tests

## Performance

**Time savings**:

| Scenario | Full Suite | Failed Only | Time Saved |
|----------|------------|-------------|------------|
| 2 failures in 500 tests | 5 min | 5 sec | 98% faster |
| 10 failures in 500 tests | 5 min | 15 sec | 95% faster |
| 50 failures in 500 tests | 5 min | 1 min | 80% faster |

## Best Practices

1. **Fix one at a time**: Don't fix all failures, then test
2. **Run frequently**: After each fix, re-run immediately
3. **Full suite before commit**: Always run `/test-run-all` before pushing
4. **Keep tests fast**: If re-run takes > 1 minute, tests too slow

## Begin Execution

Now re-run failed tests and provide detailed failure analysis.
