---
allowed-tools: Bash(mvn:*), Bash(find:*), Bash(grep:*)
description: Full verification (compile + test + coverage) - pre-commit quality gate
---

# Full Verification (Quality Gate)

## Context

You are running **full verification** — the complete quality gate check before committing code.

This is equivalent to what CI/CD runs and ensures:
1. ✅ Code compiles cleanly
2. ✅ All tests pass
3. ✅ Coverage thresholds met
4. ✅ No quality violations

### Current Git Status

**Uncommitted changes**:
!`git status --short`

**Changed files**:
!`git diff --name-only HEAD | head -20`

## Your Task

Run comprehensive verification and report all quality checks.

### Step 1: Clean Build

Start fresh:

```bash
mvn clean
```

**Purpose**: Remove all compiled artifacts, ensure clean slate

### Step 2: Compile Check

Verify code compiles:

```bash
mvn compile test-compile
```

**What this checks**:
- Source code compiles (no syntax errors)
- Test code compiles (tests are valid)
- No type errors, missing dependencies

**Expected**: `BUILD SUCCESS`

**If fails**: Stop here, fix compilation errors first

### Step 3: Run Tests

Execute full test suite:

```bash
mvn test
```

**What this checks**:
- All unit tests pass
- No test failures or errors
- Tests are not skipped unintentionally

**Expected**: `BUILD SUCCESS`, `Tests run: X, Failures: 0, Errors: 0`

**If fails**: Fix failing tests before proceeding

### Step 4: Verify Coverage

Run verify goal (includes Jacoco coverage check):

```bash
mvn verify
```

**What this checks**:
- All tests pass (again)
- Integration tests run (if any)
- Coverage thresholds met (75% per patra-parent/pom.xml)
- Jacoco coverage rules enforced

**Expected**: `BUILD SUCCESS`

**Coverage rule** (from patra-parent):
```xml
<limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.75</minimum>  <!-- 75% coverage required -->
</limit>
```

**If fails**: Check which modules are below threshold

### Step 5: Generate Coverage Reports

View coverage reports:

```bash
mvn jacoco:report
```

**Reports location**:
!`find . -name "index.html" -path "*/jacoco/*" | head -10`

**Open in browser** (if running locally):
```bash
open {module}/target/site/jacoco/index.html
```

### Step 6: Analyze Coverage

For each module, check:

**Overall coverage**:
```bash
grep -r "Total" target/site/jacoco/index.html | head -10
```

**Low coverage classes**:
- Find classes below 75%
- Determine if additional tests needed
- Acceptable to have low coverage for:
  - Generated code (MapStruct mappers)
  - Simple DTOs/VOs
  - Configuration classes

### Step 7: Quick Static Analysis (Optional)

Additional quality checks:

**Find TODO/FIXME comments**:
```bash
grep -r "TODO\|FIXME" --include="*.java" src/ | wc -l
```

**Find System.out.println** (should use logger):
```bash
grep -r "System.out.println" --include="*.java" src/ | grep -v test
```

**Find unused imports** (via compile warnings):
```bash
mvn compile 2>&1 | grep "warning.*unused"
```

## Output Format

Provide a comprehensive verification report:

```
# Verification Report (Quality Gate)

**Date**: {current-date}
**Commit**: {git hash}
**Duration**: {X minutes Y seconds}

## Verification Steps

### ✅ Step 1: Compilation
- **Source**: ✅ Compiled successfully
- **Tests**: ✅ Compiled successfully
- **Duration**: {Xs}

### ✅ Step 2: Test Execution
- **Total Tests**: {count}
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Duration**: {Xs}

### ✅ Step 3: Coverage Verification
- **Overall Coverage**: {X}%
- **Threshold**: 75% (required)
- **Status**: ✅ PASS / ❌ FAIL
- **Duration**: {Xs}

## Module Coverage Details

| Module | Line Coverage | Branch Coverage | Status |
|--------|---------------|-----------------|--------|
| patra-common | 82% | 75% | ✅ PASS |
| patra-registry-domain | 85% | 78% | ✅ PASS |
| patra-ingest-domain | 79% | 72% | ✅ PASS |
| patra-ingest-app | 88% | 81% | ✅ PASS |
| ... | ... | ... | ... |

## Quality Issues (if any)

### Coverage Violations

**Module**: patra-{module}

**Coverage**: 68% (below 75% threshold)

**Uncovered classes**:
1. `SomeClass.java` - 45% coverage
2. `AnotherClass.java` - 52% coverage

**Recommendation**: Add unit tests for these classes

### Test Failures

{If any tests failed, list them here}

### Warnings

{If any compile warnings, list them here}

## Overall Status

**Quality Gate**: {✅ PASS / ❌ FAIL}

{If PASS}:
- ✅ All checks passed
- ✅ Safe to commit and push
- ✅ Ready for code review

{If FAIL}:
- ❌ Quality gate failed
- ❌ Fix issues before committing
- ❌ Run `/test-verify` again after fixes

## Next Steps

{If PASS}:
1. Commit changes: `git add . && git commit -m "..."`
2. Push to remote: `git push`
3. Create PR (if needed)

{If FAIL}:
1. Fix identified issues
2. Run `/test-verify` again
3. Repeat until all checks pass
```

## Quality Gate Criteria

For quality gate to pass, ALL must be true:

- ✅ Code compiles without errors
- ✅ All tests pass (0 failures, 0 errors)
- ✅ Coverage ≥ 75% for all modules
- ✅ No critical static analysis issues (optional)

## Integration with Git Hooks

This command can be used as a pre-push hook:

```bash
# .git/hooks/pre-push
#!/bin/bash
echo "Running quality gate..."
mvn clean verify
if [ $? -ne 0 ]; then
    echo "❌ Quality gate failed! Push aborted."
    exit 1
fi
echo "✅ Quality gate passed!"
```

## Performance Tips

**Faster verification** (skip redundant steps):

```bash
# Skip tests in compile phase (they run in test phase anyway)
mvn clean compile -DskipTests

# Run tests and verify together
mvn test verify

# Or combine all in one command
mvn clean verify
```

**Parallel execution**:

```bash
# Run tests in parallel (faster on multi-core)
mvn -T 1C clean verify  # 1 thread per CPU core
```

## Troubleshooting

### Issue: "Coverage check fails but tests pass"

**Cause**: Module coverage below 75%

**Solution**:
1. Check which classes are uncovered
2. Add unit tests for uncovered code
3. If legitimate (DTOs, config), adjust coverage rules

### Issue: "Verify takes too long"

**Optimization**:
1. Skip tests for unchanged modules: `mvn verify -pl :changed-module`
2. Use parallel execution: `mvn -T 1C verify`
3. Run only affected tests

## Begin Execution

Now run full verification and provide the quality gate report.
