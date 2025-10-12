---
allowed-tools: Bash(mvn:*), Bash(find:*), Bash(grep:*), Bash(open:*)
argument-hint: [module]
description: Generate and analyze coverage report for module (or entire project)
---

# Generate Coverage Report

## Context

You are generating and analyzing **test coverage reports** using JaCoCo.

### Target Scope

**Module argument**: ${ARGUMENTS:-all}

- If module specified: Analyze that module only
- If "all" or empty: Analyze entire project

### Current Project State

**Modules**:
!`find . -name "pom.xml" -type f | grep -v target | cut -d'/' -f2-3 | grep "patra-" | sort`

## Your Task

Generate coverage report and provide detailed analysis.

### Step 1: Run Tests with Coverage

Generate coverage data:

```bash
${ARGUMENTS:+mvn test jacoco:report -pl :$ARGUMENTS}
${ARGUMENTS:-mvn test jacoco:report}
```

**What this does**:
- Runs all tests
- Instruments code to track execution
- Generates `jacoco.exec` coverage data file
- Creates HTML reports in `target/site/jacoco/`

### Step 2: Locate Coverage Reports

Find generated reports:

```bash
find . -name "index.html" -path "*/jacoco/*" | grep -v target/classes
```

**Expected locations**:
- `{module}/target/site/jacoco/index.html` (per module)
- Aggregate report (if configured)

### Step 3: Parse Coverage Data

Extract coverage metrics from HTML:

For each module, parse:
- **Overall coverage**: Package + class + method + line + branch
- **Package-level coverage**: Which packages have low coverage
- **Class-level coverage**: Which specific classes need more tests

**Key metrics**:
```bash
# Extract line coverage percentage
grep -A 2 "Total" {module}/target/site/jacoco/index.html | grep -oP '\d+%'
```

### Step 4: Analyze Coverage by Layer

Papertrace uses hexagonal architecture, analyze coverage by layer:

#### Domain Layer (Should be highest: 85%+)
- Pure business logic
- Easiest to test (no dependencies)
- **Target**: 85%+ coverage

#### Application Layer (Target: 80%+)
- Orchestration logic
- Mocked dependencies
- **Target**: 80%+ coverage

#### Infrastructure Layer (Target: 70%+)
- Repository implementations
- External integrations
- **Target**: 70%+ coverage (some code hard to unit test)

#### Adapter Layer (Target: 75%+)
- Controllers, REST endpoints
- MockMvc tests
- **Target**: 75%+ coverage

### Step 5: Identify Coverage Gaps

Find classes with low coverage (< 75%):

```bash
# Search for classes with low coverage in HTML
grep -r "class=\"el_.*\"" target/site/jacoco/ | grep -E "([0-6][0-9]%|[0-9]%)"
```

**Categorize gaps**:

1. **Critical gaps** (core business logic < 50%)
   - Example: `PlanAggregate.java` at 45%
   - **Action**: High priority, add tests immediately

2. **Acceptable gaps** (generated code, simple DTOs)
   - Example: `SomeDTOMapperImpl.java` at 30% (MapStruct generated)
   - **Action**: No action needed, exclude from coverage

3. **Medium gaps** (utility code, helpers 50-75%)
   - Example: `DateUtils.java` at 65%
   - **Action**: Medium priority, add tests when convenient

### Step 6: Check Against Threshold

**Threshold** (from patra-parent/pom.xml):
- **Required**: 75% line coverage minimum

**Status check**:
```bash
mvn verify  # This will fail if coverage < 75%
```

### Step 7: Open Report (Optional)

If running locally:

```bash
# Open in default browser (macOS)
open {module}/target/site/jacoco/index.html

# Linux
xdg-open {module}/target/site/jacoco/index.html

# Windows
start {module}/target/site/jacoco/index.html
```

## Output Format

Provide a detailed coverage analysis report:

```
# Coverage Analysis Report

**Date**: {current-date}
**Scope**: ${ARGUMENTS:-All modules}
**Threshold**: 75% line coverage (required)

## Executive Summary

**Overall Coverage**: {X}% line, {Y}% branch
**Status**: {✅ Above threshold / ❌ Below threshold}
**Modules Analyzed**: {count}

## Coverage by Module

| Module | Package | Class | Method | Line | Branch | Status |
|--------|---------|-------|--------|------|--------|--------|
| patra-common | 78% | 82% | 80% | 82% | 75% | ✅ |
| patra-registry-domain | 85% | 88% | 86% | 85% | 78% | ✅ |
| patra-ingest-domain | 79% | 82% | 80% | 79% | 72% | ✅ |
| patra-ingest-app | 88% | 90% | 89% | 88% | 81% | ✅ |
| patra-ingest-infra | 68% | 70% | 69% | 68% | 65% | ❌ |

## Coverage by Layer

### Domain Layer
- **Average**: {X}%
- **Target**: 85%+
- **Status**: {✅ Meets / ⚠️ Close / ❌ Below}

**Top 3 well-tested classes**:
1. `PlanAggregate` - 92% coverage ✅
2. `TaskAggregate` - 89% coverage ✅
3. `ProvenanceConfiguration` - 87% coverage ✅

**Top 3 under-tested classes**:
1. `WindowSpec` - 45% coverage ❌
2. `CursorWatermark` - 58% coverage ⚠️
3. `ExecutionContext` - 62% coverage ⚠️

### Application Layer
- **Average**: {X}%
- **Target**: 80%+
- **Status**: {status}

{Similar breakdown}

### Infrastructure Layer
- **Average**: {X}%
- **Target**: 70%+
- **Status**: {status}

{Similar breakdown}

### Adapter Layer
- **Average**: {X}%
- **Target**: 75%+
- **Status**: {status}

{Similar breakdown}

## Critical Coverage Gaps

### 🔴 High Priority (< 50% coverage, core logic)

**Class**: `PlanAssembler.java`
- **Current**: 42% line, 35% branch
- **Layer**: Application
- **Impact**: High (core orchestration logic)
- **Recommendation**: Add orchestrator tests with mocked dependencies
- **Estimated effort**: 2-3 hours

### ⚠️ Medium Priority (50-75% coverage)

**Class**: `CursorRepository.java`
- **Current**: 68% line, 62% branch
- **Layer**: Infrastructure
- **Impact**: Medium (cursor tracking)
- **Recommendation**: Add repository integration tests
- **Estimated effort**: 1 hour

### ℹ️ Acceptable Gaps (generated/simple code)

**Class**: `ProvenanceConverterImpl.java` (MapStruct generated)
- **Current**: 30% coverage
- **Action**: None (generated code, exclude from coverage)

## Recommendations

### Immediate Actions (This Sprint)
1. Add tests for `PlanAssembler` (42% → 80%+ target)
2. Add tests for `WindowSpec` sealed interface (45% → 85%+ target)
3. Add integration tests for `CursorRepository` (68% → 75%+ target)

### Future Actions (Next Sprint)
1. Review all infrastructure layer coverage (current: 68%, target: 70%+)
2. Add edge case tests for domain aggregates
3. Consider property-based testing for complex business rules

### Coverage Exclusions to Configure

Add to JaCoCo configuration:
```xml
<excludes>
    <exclude>**/*MapperImpl.class</exclude>  <!-- MapStruct generated -->
    <exclude>**/*DO.class</exclude>           <!-- Data objects -->
    <exclude>**/*Config.class</exclude>       <!-- Spring config -->
</excludes>
```

## Next Steps

**If coverage meets threshold**:
- ✅ Coverage goals met
- Continue maintaining current quality
- Consider increasing threshold to 80% next quarter

**If coverage below threshold**:
- ❌ Add tests for identified gaps
- Focus on high-priority gaps first
- Run `/test-coverage` again after improvements
- Consider using `/test-unit` to create missing tests

## Report Locations

**HTML Reports**:
{List all found report paths}

**Command to open**:
```bash
open {primary-report-path}
```

## Trends (Optional)

Compare with previous coverage (if available):
- **Previous**: {X}% (date)
- **Current**: {Y}%
- **Change**: {+/- Z}%
- **Trend**: {📈 Improving / 📉 Declining / ➡️ Stable}
```

## Troubleshooting

### Issue: "No coverage data generated"

**Causes**:
- Tests not run before `jacoco:report`
- JaCoCo plugin not configured

**Solution**:
```bash
mvn clean test jacoco:report  # Ensure tests run first
```

### Issue: "Coverage report shows 0% for all classes"

**Causes**:
- `jacoco.exec` file missing or empty
- Instrumentation failed

**Solution**:
Check JaCoCo configuration in pom.xml

### Issue: "Coverage lower than expected"

**Check**:
- Are all tests running? `mvn test` should show test count
- Are tests actually testing code? Check assertions
- Are there skipped tests? Check for `@Disabled` annotations

## Begin Execution

Now generate coverage report and provide detailed analysis.
