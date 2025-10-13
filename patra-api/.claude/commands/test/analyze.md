---
allowed-tools: Bash(git:*), Bash(find:*), Bash(grep:*), Bash(mvn:*), Read, Glob
argument-hint: [module-name]
description: Analyze test coverage gaps for changed or specified modules
---

# Analyze Test Coverage Gaps

## Context

**Git Diff Analysis**:
- Current branch: !`git branch --show-current`
- Changed Java files: !`git diff main...HEAD --name-only --diff-filter=AM | grep '\.java$' | grep -v '/test/'`
- Changed test files: !`git diff main...HEAD --name-only --diff-filter=AM | grep '/test/.*\.java$'`

**Existing Tests**: !`find . -path '*/src/test/java/**/*Test.java' -type f | grep -v target | wc -l`

**Recent Coverage Report** (if exists): !`find . -name "jacoco.xml" -path '*/target/site/jacoco/*' -type f | head -5`

## Your Task

Analyze test coverage gaps and provide actionable recommendations for improving test quality.

### Step 1: Map Source to Test Files

For each changed source file:
1. Determine expected test file path
2. Check if test file exists
3. If exists, analyze test completeness
4. If not exists, flag as **missing test**

**Mapping Rules**:
- Source: `{module}/src/main/java/com/patra/{service}/{layer}/{Class}.java`
- Test: `{module}/src/test/java/com/patra/{service}/{layer}/{Class}Test.java`

**Filter**: $ARGUMENTS (if provided, only analyze specified module)

### Step 2: Analyze Test Completeness

For existing test files, check:

**Structure Completeness**:
- [ ] Uses @ExtendWith(MockitoExtension.class) or @SpringBootTest
- [ ] Has @DisplayName at class level
- [ ] Uses @Nested classes for grouping
- [ ] Has @DisplayName for each test method

**Coverage Completeness** (read source and test):
- [ ] All public methods have tests
- [ ] Edge cases covered (null, empty, boundary)
- [ ] Error conditions tested (exceptions)
- [ ] State transitions tested (for aggregates)
- [ ] Validation logic tested

**Quality Indicators**:
- [ ] Uses Arrange-Act-Assert pattern
- [ ] Mocks dependencies appropriately
- [ ] Assertions are specific (not just `assertNotNull()`)
- [ ] Test names are descriptive

### Step 3: Generate Gap Report

Provide detailed gap analysis:

**Missing Tests** (no test file):
```
❌ patra-ingest-domain/src/main/java/.../PlanAggregate.java
   → Missing: patra-ingest-domain/src/test/java/.../PlanAggregateTest.java
   Priority: HIGH (core domain aggregate)

❌ patra-registry-app/src/main/java/.../ProvenanceOrchestrator.java
   → Missing: patra-registry-app/src/test/java/.../ProvenanceOrchestratorTest.java
   Priority: HIGH (critical use case)
```

**Incomplete Tests** (test exists but gaps):
```
⚠️  patra-ingest-domain/src/test/java/.../TaskAggregateTest.java
   Gaps:
   - Missing tests for: markFailed(), retry(), cancel()
   - No state transition tests
   - No validation tests
   Priority: MEDIUM

⚠️  patra-registry-infra/src/test/java/.../ProvenanceRepositoryMpImplTest.java
   Gaps:
   - Missing optimistic locking test
   - No test for complex query: findActiveConfigsByScope()
   Priority: MEDIUM
```

**Well-Tested** (complete coverage):
```
✅ patra-common-domain/src/test/java/.../AggregateRootTest.java
   - All methods tested
   - Edge cases covered
   - Clear @Nested structure
```

### Step 4: Coverage Metrics

If JaCoCo report exists, parse and display:
```
Module: patra-ingest-domain
  Line Coverage:    85% (target: 75%)  ✅
  Branch Coverage:  70% (target: 65%)  ✅
  Method Coverage:  90% (target: 80%)  ✅

Module: patra-ingest-app
  Line Coverage:    60% (target: 75%)  ❌
  Branch Coverage:  45% (target: 65%)  ❌
  Method Coverage:  55% (target: 80%)  ❌
  → ACTION NEEDED: Add tests for 15 untested methods

Module: patra-registry-infra
  Line Coverage:    78% (target: 75%)  ✅
  Branch Coverage:  62% (target: 65%)  ⚠️  (close to target)
  Method Coverage:  82% (target: 80%)  ✅
```

### Step 5: Prioritized Action Plan

Provide prioritized list of actions:

**Priority 1: Critical Gaps (Domain/App layers with no tests)**
```bash
# Generate unit tests for critical domain logic
/unit patra-ingest-domain

# Generate unit tests for orchestrators
/unit patra-ingest-app
```

**Priority 2: Coverage Gaps (Tests exist but incomplete)**
```
1. Add state transition tests to TaskAggregateTest
2. Add validation tests to PlanAggregateTest
3. Add optimistic locking test to ProvenanceRepositoryMpImplTest
```

**Priority 3: Integration Gaps (Infra/Adapter layers)**
```bash
# Generate integration tests for repositories
/integration patra-registry-infra

# Generate integration tests for controllers
/integration patra-ingest-adapter
```

### Step 6: Test Quality Recommendations

**Best Practices to Follow**:
1. Use `@Nested` classes to organize tests by feature
2. Use descriptive `@DisplayName` annotations
3. Follow Arrange-Act-Assert pattern consistently
4. Test behavior, not implementation
5. Mock external dependencies, not internal ones

**Anti-Patterns to Avoid**:
1. ❌ Testing private methods directly
2. ❌ Over-mocking (mocking everything)
3. ❌ Testing framework code (getters/setters)
4. ❌ Brittle tests (coupled to implementation)
5. ❌ No assertions (just checking "no exception thrown")

### Step 7: Summary

**Overall Test Health**:
- Total source files: X
- Total test files: Y (coverage: Y/X = Z%)
- Missing tests: M (HIGH priority: H, MEDIUM: M, LOW: L)
- Incomplete tests: I

**Recommended Commands**:
```bash
# 1. Generate missing unit tests
/unit [module-with-gaps]

# 2. Generate missing integration tests
/integration [module-with-gaps]

# 3. Re-run coverage analysis
mvn clean verify
/analyze

# 4. View coverage report
open patra-{service}/patra-{service}-boot/target/site/jacoco/index.html
```

**Quality Gate Status**:
- Line Coverage: [✅/❌] (current vs 75% target)
- Test Completeness: [✅/❌] (% of classes with tests)
- Test Quality: [✅/⚠️/❌] (based on structure analysis)

Begin analysis now.
