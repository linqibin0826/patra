---
allowed-tools: Bash(find:*), Bash(grep:*), Bash(wc:*), Read, Glob
description: Run documentation health check (quarterly audit)
---

# Documentation Health Check

## Context

You are performing a **quarterly documentation health check** for Papertrace.

This command should be run every 3 months to ensure documentation stays accurate, complete, and helpful.

### Documentation Standards
Read the health check section from: @docs/DOCUMENTATION-GUIDE.md

## Your Task

Perform a comprehensive audit of all documentation and report findings.

### Step 1: Check Coverage

**All modules should have READMEs**:

!`find . -name "pom.xml" -type f | grep -v target | wc -l`

!`find . -name "README.md" -type f | grep -v target | wc -l`

**Expected**: Module count ≈ README count

**Analysis**:
- List modules without READMEs
- Determine if they need READMEs (skip test modules, empty modules)
- Report coverage percentage

### Step 2: Check Freshness

**Find stale documentation** (not updated in 6 months):

!`find . -name "README.md" -type f -mtime +180 | grep -v target`

**Analysis**:
- List stale READMEs with last modified date
- Prioritize by importance (service READMEs > package READMEs)
- Recommend updates for top 5 stale docs

### Step 3: Check Links

**Find all markdown files**:

!`find . -name "*.md" -type f | grep -v target | head -20`

**Analysis**:
- Check for broken links (look for common patterns: `./`, `../`, `#`)
- Verify cross-references between docs
- Report top 5 files with most external links (higher risk of breaking)

### Step 4: Check Consistency

**Analyze structure**:

Read a sample of 3 service READMEs and check for:
- [ ] Consistent emoji usage (📌, 🏗️, 🔄, etc.)
- [ ] "Last Updated" date present
- [ ] Standard sections (Purpose, Architecture, etc.)
- [ ] Code blocks have language tags
- [ ] Links are relative (not absolute for internal files)

### Step 5: Check Completeness

**Verify required documentation exists**:

Expected core docs:
- [ ] `/README.md`
- [ ] `/docs/ARCHITECTURE.md`
- [ ] `/docs/DEV-GUIDE.md`
- [ ] `/docs/DOCUMENTATION-GUIDE.md`

Expected service READMEs:
- [ ] `/patra-registry/README.md`
- [ ] `/patra-ingest/README.md`
- [ ] `/patra-gateway-boot/README.md`
- [ ] `/patra-egress-gateway/README.md`

Expected API READMEs:
- [ ] `/patra-egress-gateway/patra-egress-gateway-api/README.md`
- [ ] `/patra-registry/patra-registry-api/README.md`
- [ ] `/patra-ingest/patra-ingest-api/README.md`

### Step 6: Metrics Summary

Calculate and report:

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| **Coverage** | >90% of modules have READMEs | X/Y (Z%) | ✅/⚠️/❌ |
| **Freshness** | >80% updated in last 3 months | X/Y (Z%) | ✅/⚠️/❌ |
| **Link Health** | 100% links work | X/Y (Z%) | ✅/⚠️/❌ |
| **Completeness** | All required docs exist | X/Y | ✅/⚠️/❌ |

### Step 7: Action Items

Based on findings, provide **prioritized action items**:

#### High Priority (Do This Quarter)
1. [Action 1]
2. [Action 2]
3. [Action 3]

#### Medium Priority (Next Quarter)
1. [Action 1]
2. [Action 2]

#### Low Priority (Monitor)
1. [Action 1]
2. [Action 2]

### Step 8: Health Score

Calculate overall documentation health score:

**Formula**: (Coverage × 0.3) + (Freshness × 0.3) + (Link Health × 0.2) + (Completeness × 0.2)

**Score**: X/100

**Grade**:
- 90-100: ✅ Excellent (maintain current practices)
- 70-89: ⚠️ Good (minor improvements needed)
- 50-69: ⚠️ Fair (significant updates required)
- <50: ❌ Poor (urgent attention needed)

## Report Format

Provide a clear, actionable report:

```
# Documentation Health Check Report
**Date**: {current-date}
**Auditor**: Claude Code
**Next Audit**: {date + 3 months}

## Executive Summary
[2-3 sentences on overall health]

## Metrics
[Table from Step 6]

## Findings

### Coverage
[Analysis from Step 1]

### Freshness
[Analysis from Step 2]

### Link Health
[Analysis from Step 3]

### Consistency
[Analysis from Step 4]

### Completeness
[Analysis from Step 5]

## Action Items
[Prioritized list from Step 7]

## Health Score
[Score and grade from Step 8]

## Recommendations
[Top 3 recommendations for next quarter]
```

## Begin Execution

Now perform the health check and generate the report.
