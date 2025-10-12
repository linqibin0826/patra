---
allowed-tools: Bash(mvn:*), Bash(find:*), Bash(cat:*), Bash(grep:*)
description: Check coverage thresholds and generate detailed coverage report
---

# Coverage Gate Check

## Your Task

Validate test coverage against defined thresholds and provide detailed gap analysis.

### Step 1: Generate Coverage Report

Run tests with JaCoCo:
```bash
mvn clean verify -Djacoco.skip=false
```

### Step 2: Parse Coverage Data

Extract coverage metrics from JaCoCo XML reports:
```bash
find . -name "jacoco.xml" -path "*/target/site/jacoco/*" -type f
```

Parse each report for:
- Line coverage
- Branch coverage
- Method coverage
- Class coverage

### Step 3: Apply Coverage Thresholds

**Defined Thresholds**:
- Line Coverage: ≥ 75%
- Branch Coverage: ≥ 65%
- Method Coverage: ≥ 80%

**Evaluation**:
```
═══════════════════════════════════════════════════════════
COVERAGE GATE CHECK
═══════════════════════════════════════════════════════════

Overall Coverage:
  Line Coverage:    82% ✅ (target: 75%, margin: +7%)
  Branch Coverage:  73% ✅ (target: 65%, margin: +8%)
  Method Coverage:  87% ✅ (target: 80%, margin: +7%)

Status: ALL THRESHOLDS MET ✅
```

### Step 4: Module-Level Breakdown

Show coverage for each module:

**Success Case**:
```
═══════════════════════════════════════════════════════════
MODULE COVERAGE BREAKDOWN
═══════════════════════════════════════════════════════════

High Coverage (>= 85%)  ✅
  patra-ingest-domain           91% / 85% / 95%  ✅
  patra-registry-domain         94% / 90% / 97%  ✅
  patra-expr-kernel             93% / 88% / 96%  ✅
  patra-common                  89% / 80% / 92%  ✅

Good Coverage (75-84%)  ✅
  patra-ingest-app              85% / 75% / 88%  ✅
  patra-registry-app            88% / 78% / 91%  ✅
  patra-egress-gateway-domain   87% / 76% / 89%  ✅
  patra-ingest-infra            78% / 68% / 82%  ✅
  patra-registry-infra          81% / 70% / 85%  ✅
  patra-egress-gateway-infra    76% / 64% / 80%  ✅

Acceptable Coverage (>= thresholds)  ✅
  patra-ingest-adapter          74% / 62% / 79%  ⚠️  (close to threshold)

All modules meet minimum thresholds ✅
```

**Failure Case**:
```
═══════════════════════════════════════════════════════════
MODULE COVERAGE BREAKDOWN
═══════════════════════════════════════════════════════════

Above Threshold  ✅
  ... (modules that pass)

Below Threshold  ❌
  patra-ingest-adapter          72% / 59% / 76%  ❌
    Line:   72% < 75% (gap: -3%)
    Branch: 59% < 65% (gap: -6%)
    Method: 76% < 80% (gap: -4%)

  patra-egress-gateway-boot     68% / 55% / 72%  ❌
    Line:   68% < 75% (gap: -7%)
    Branch: 55% < 65% (gap: -10%)
    Method: 72% < 80% (gap: -8%)

Status: 2 modules below threshold ❌
```

### Step 5: Package-Level Analysis

Drill down to package level for low-coverage modules:

```
═══════════════════════════════════════════════════════════
DETAILED COVERAGE (patra-ingest-adapter)
═══════════════════════════════════════════════════════════

Package: com.patra.ingest.adapter.inbound.rest
  Line Coverage:    68% ❌ (below 75%)
  Classes:
    ✅ PlanController           85%
    ❌ TaskController           52%  ← LOW COVERAGE
    ✅ ScheduleController       78%

  Missing Coverage in TaskController:
    Line 45-52:   Error handling logic untested
    Line 78-85:   Validation logic untested
    Line 102-110: Edge case handling untested

Package: com.patra.ingest.adapter.inbound.scheduler
  Line Coverage:    61% ❌ (below 75%)
  Classes:
    ❌ PlanScheduler            61%  ← LOW COVERAGE
    ✅ OutboxRelayScheduler     82%

  Missing Coverage in PlanScheduler:
    Line 34-42:   Exception handling untested
    Line 67-75:   Retry logic untested
```

### Step 6: Actionable Recommendations

Provide specific actions to improve coverage:

```
═══════════════════════════════════════════════════════════
COVERAGE IMPROVEMENT PLAN
═══════════════════════════════════════════════════════════

Priority 1: Critical Gaps (below threshold by > 5%)
  1. patra-egress-gateway-boot (gap: -10% branch)
     Action: /integration patra-egress-gateway-boot
     Focus on: Error handling, edge cases

  2. patra-ingest-adapter.TaskController (gap: -23% line)
     Action: /integration patra-ingest-adapter
     Focus on: Controller error handling, validation

Priority 2: Near Threshold (within 5% of threshold)
  3. patra-ingest-adapter.PlanScheduler (gap: -4% line)
     Action: Add tests for exception handling
     Focus on: Retry logic, error recovery

Estimated Improvement:
  Adding 10-15 test methods across 3 classes
  Expected new coverage: 78-80% (above threshold)
  Estimated effort: 1-2 hours

Commands to Execute:
  /integration patra-egress-gateway-boot
  /integration patra-ingest-adapter
  /run-all
  /gate-coverage  # Re-validate
```

### Step 7: Coverage Trend Analysis

If git history available, show coverage trend:

```
═══════════════════════════════════════════════════════════
COVERAGE TREND
═══════════════════════════════════════════════════════════

main branch:           78% line / 68% branch / 82% method
current branch:        82% line / 73% branch / 87% method
change:               +4%      +5%            +5%      ✅

Status: Coverage improved ✅

Your changes added:
  - 45 new tests
  - 12% more coverage
  - 0 coverage regressions
```

### Step 8: Generate HTML Report

Provide links to detailed coverage reports:

```
═══════════════════════════════════════════════════════════
COVERAGE REPORTS
═══════════════════════════════════════════════════════════

Aggregated Report (All modules):
  open target/site/jacoco-aggregate/index.html

Module Reports:
  patra-ingest-domain:
    open patra-ingest/patra-ingest-domain/target/site/jacoco/index.html

  patra-ingest-app:
    open patra-ingest/patra-ingest-app/target/site/jacoco/index.html

  (... other modules)

Low Coverage Modules (priority review):
  1. open patra-ingest-adapter/target/site/jacoco/index.html
  2. open patra-egress-gateway-boot/target/site/jacoco/index.html
```

### Step 9: Final Verdict

**PASS**:
```
✅ COVERAGE GATE PASSED

All Thresholds Met:
  ✅ Line coverage: 82% >= 75%
  ✅ Branch coverage: 73% >= 65%
  ✅ Method coverage: 87% >= 80%

Coverage Quality: EXCELLENT

You may proceed with confidence.
```

**FAIL**:
```
❌ COVERAGE GATE FAILED

Thresholds Not Met:
  ❌ Line coverage: 72% < 75% (gap: -3%)
  ❌ Branch coverage: 59% < 65% (gap: -6%)

Modules Below Threshold: 2
  - patra-ingest-adapter
  - patra-egress-gateway-boot

Action Required:
  1. Add tests for identified gaps
  2. Focus on error handling and edge cases
  3. Re-run: /gate-coverage

DO NOT commit until coverage gate passes.
```

Begin coverage validation now.
