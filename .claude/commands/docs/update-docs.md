---
allowed-tools: Bash(git:*), Read, Edit, Write, Glob, Grep
argument-hint: [scope]
description: Update documentation after code changes (scope: api-changes, new-module, refactor, or auto)
---

# Update Documentation After Code Changes

## Context

You are helping maintain Papertrace documentation after code changes have been completed.

### Current Git Status
!`git status`

### Current Git Diff (Staged and Unstaged Changes)
!`git diff HEAD`

### Changed Files Summary
!`git diff --name-only HEAD`

### Recent Commits (Last 5)
!`git log --oneline -5`

### Documentation Standards
Read and follow the standards from: @docs/DOCUMENTATION-GUIDE.md

## Your Task

Based on the code changes above and the documentation guide, **intelligently update the affected documentation**.

### Step 1: Analyze Changes

Determine what type of changes were made:

1. **New Module** - New microservice, shared library, or API module created
2. **API Changes** - New endpoints, DTOs, or API contracts modified
3. **Architecture Changes** - Design patterns, system architecture modified
4. **Use Case Changes** - New orchestrators, complex use cases added
5. **Dependency Changes** - pom.xml dependencies updated
6. **Refactoring** - Code restructuring without functional changes

### Step 2: Identify Affected Documentation

Based on change type, determine which documentation files need updates:

#### If New Module Created:
- [ ] Create `/{module}/README.md` (use template from DOCUMENTATION-GUIDE.md)
- [ ] Update `/README.md` (add to module table)
- [ ] Update `/docs/ARCHITECTURE.md` (system diagram, dependency graph)
- [ ] If API module, create `/{module}-api/README.md`

#### If API Changes:
- [ ] Update `/{service}/{service}-api/README.md` (endpoint, DTOs, examples)
- [ ] Update `/{service}/README.md` (if integration pattern changes)
- [ ] Update consuming service READMEs (if they have integration examples)

#### If Architecture Changes:
- [ ] Update `/docs/ARCHITECTURE.md` (patterns, diagrams, decisions)
- [ ] Update affected service READMEs (if pattern usage changes)
- [ ] Update `/docs/DEV-GUIDE.md` (if recipe changes)

#### If Use Case Changes:
- [ ] Update `/{service}/README.md` (add to use case section)
- [ ] Consider package-level README if complex (7+ steps, 5+ classes)

#### If Dependency Changes:
- [ ] Update `/patra-parent/README.md` (version table)
- [ ] Update affected module READMEs (if breaking changes)

#### If Refactoring Only:
- [ ] Update only if public APIs or patterns changed
- [ ] Most refactorings don't require doc updates

### Step 3: Update Documentation

For each identified file:

1. **Read the current content** (use Read tool)
2. **Determine what needs to change** based on code diff
3. **Make precise updates** (use Edit tool for existing files, Write for new files)
4. **Follow standards** from DOCUMENTATION-GUIDE.md:
   - Use correct emoji conventions (📌 Purpose, 🏗️ Architecture, etc.)
   - Maintain consistent structure (follow templates)
   - Keep tone clear, concise, professional
   - Update "Last Updated" date to today

### Step 4: Validate Updates

Before finishing, verify:

- [ ] **Accuracy**: Code examples match actual implementation
- [ ] **Completeness**: All promised sections are present
- [ ] **Consistency**: Follows DOCUMENTATION-GUIDE.md standards
- [ ] **Cross-references**: All links are correct (no broken links)
- [ ] **Last Updated**: Date is current (YYYY-MM-DD format)

### Step 5: Summary

Provide a clear summary of:
1. **Changes detected**: What type of code changes were made
2. **Files updated**: List of documentation files modified/created
3. **Key updates**: Brief description of what changed in each file
4. **Validation**: Confirm all validation checks passed

## Scope Argument (Optional)

If `$ARGUMENTS` is provided, focus on that specific scope:

- `api-changes`: Only update API-related docs
- `new-module`: Only create new module READMEs
- `refactor`: Minimal doc updates (only if patterns changed)
- `architecture`: Only update architectural docs
- `auto`: (default) Automatically detect and update all affected docs

**Current scope**: ${ARGUMENTS:-auto}

## Important Reminders

1. **Be precise**: Only update files that are truly affected by the changes
2. **Follow patterns**: Use templates and examples from DOCUMENTATION-GUIDE.md
3. **Maintain quality**: Ensure docs are accurate, complete, and helpful
4. **Think holistically**: Consider if changes affect multiple tiers (core → service → package)
5. **Don't over-document**: If code is self-explanatory, minimal docs are fine

## Begin Execution

Now analyze the changes and update the documentation systematically.
