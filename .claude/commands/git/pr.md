You are about to help the user create a GitHub pull request with auto-generated title and description.

## Your Task

1. **Check Prerequisites**:
   - Check if `gh` CLI is installed: `which gh`
   - If not found:
     ```
     GitHub CLI (gh) not found.

     Install:
       macOS: brew install gh
       Linux: See https://github.com/cli/cli#installation

     Then authenticate: gh auth login
     ```
     Stop here

   - Check if authenticated: `gh auth status`
   - If not authenticated: `gh auth login`

2. **Check for User-Provided Title**:
   - Check if user provided title as argument (e.g., `/git:pr "Custom Title"`)
   - If provided, use it for PR title
   - If not provided, proceed to auto-generate (step 4)

3. **Check Current Branch**:
   - Run `git branch --show-current` to get branch name
   - Check if on main/master:
     ```
     ❌ Cannot create PR from main/master branch.
     Create a feature branch first: git checkout -b feat/your-feature
     ```
     Stop here

   - Get upstream branch: `git rev-parse --abbrev-ref --symbolic-full-name @{u}`
   - If no upstream, ask to push first:
     ```
     Branch not pushed yet. Push first with /git:push
     ```
     Stop here

4. **Analyze Commits and Changes**:

   **Step 4.1: Get commit history**
   - Run `git log main..HEAD --oneline` (or `master..HEAD`)
   - Count commits and classify by type:
     - feat: New features
     - fix: Bug fixes
     - docs: Documentation
     - refactor: Refactoring
     - test: Tests
     - chore: Maintenance
   - Example:
     ```
     3 commits: 2 feat, 1 test
     ```

   **Step 4.2: Analyze full diff**
   - Run `git diff main...HEAD --stat` to see file changes
   - Run `git diff main...HEAD` for detailed changes (use sparingly, can be large)
   - Identify:
     - Affected modules (patra-ingest, patra-registry, etc.)
     - Affected layers (domain, app, infra, adapter, boot)
     - Type of changes (new files, modifications, deletions)
     - Lines added/removed

   **Step 4.3: Read commit messages**
   - Run `git log main..HEAD --format=%s` to get all commit subjects
   - Extract themes and patterns
   - Identify main feature/fix being delivered

5. **Auto-Generate PR Title** (if not provided by user):

   **Format**: `<type>(<scope>): <summary>`

   **Rules**:
   - If single commit: Use that commit message as title
   - If multiple commits of same type: Aggregate the theme
   - If mixed types: Use the dominant type

   **Examples**:

   **Single commit**:
   ```
   Commits:
     feat(ingest): add batch planning aggregate and orchestrator

   Generated PR title:
     feat(ingest): add batch planning aggregate and orchestrator
   ```

   **Multiple feat commits**:
   ```
   Commits:
     feat(ingest): add batch planning aggregate
     feat(ingest): add plan orchestrator
     feat(ingest): add repository implementation

   Generated PR title:
     feat(ingest): implement batch planning system
   ```

   **Mixed types**:
   ```
   Commits:
     feat(ingest): add batch planning aggregate
     feat(ingest): add plan orchestrator
     test(ingest): add unit tests for BatchPlan
     docs: update architecture guidelines

   Generated PR title:
     feat(ingest): implement batch planning system with tests
   ```

   **Multiple modules**:
   ```
   Commits:
     feat(ingest): add batch planning
     feat(registry): update Provenance schema
     fix(common): fix validation utility

   Generated PR title:
     feat: implement batch planning and update Provenance schema
   ```

6. **Auto-Generate PR Body**:

   **Template structure**:
   ```markdown
   ## Summary
   <High-level overview of changes>

   <Bullet points of key changes by module/layer>

   ## Changes by Module
   <Module-by-module breakdown>

   ## Technical Details
   <Architecture notes, design decisions, breaking changes>

   ## Test Plan
   <Checklist of testing done/needed>

   ## Related
   <Related issues, dependencies, follow-up tasks>

   🤖 Generated with [Claude Code](https://claude.com/claude-code)
   ```

   **Step 6.1: Generate Summary**
   - 1-2 sentences describing the high-level purpose
   - Focus on business value / functionality added
   - Example:
     ```markdown
     ## Summary
     Implements PubMed batch planning system for slicing large publication queries into optimal chunks.
     This enables efficient processing of large literature searches while respecting API rate limits.
     ```

   **Step 6.2: List Key Changes**
   - Bullet points of major additions/modifications
   - Group by layer (Domain → App → Infra → Adapter)
   - Example:
     ```markdown
     - **Domain**: Add `BatchPlan` aggregate with state machine (DRAFT → SLICING → COMPLETED)
     - **App**: Implement `PlanOrchestrator` for use case coordination
     - **Infra**: Add `BatchPlanRepositoryImpl` with optimistic locking
     - **Tests**: Add comprehensive unit tests for domain layer (85% coverage)
     ```

   **Step 6.3: Changes by Module**
   - Detailed breakdown per module
   - Example:
     ```markdown
     ## Changes by Module

     ### patra-ingest-domain
     - Add `BatchPlan` aggregate with state transitions
     - Add `Task` value object for chunk representation
     - Add `PlanCreatedEvent` domain event

     ### patra-ingest-app
     - Implement `CreatePlanOrchestrator` for plan creation
     - Implement `SlicePlanOrchestrator` for chunking logic

     ### patra-ingest-infra
     - Add `BatchPlanDO` data object with JSON field
     - Add `BatchPlanRepositoryMpImpl` using MyBatis-Plus
     - Add `BatchPlanMapper` for DO ↔ Domain conversion
     ```

   **Step 6.4: Technical Details**
   - Architecture notes (if significant)
   - Breaking changes (if any)
   - Performance considerations
   - Example:
     ```markdown
     ## Technical Details

     **Architecture Compliance**:
     - ✅ Domain layer remains pure Java (no Spring dependencies)
     - ✅ Dependency direction: Adapter → App → Domain ← Infra
     - ✅ Port/Adapter pattern followed

     **Design Decisions**:
     - Used aggregate state machine for plan lifecycle
     - Optimistic locking for concurrent plan updates
     - JSON field for flexible task storage

     **Breaking Changes**: None
     ```

   **Step 6.5: Test Plan**
   - Checklist of testing done
   - Manual testing needed
   - Example:
     ```markdown
     ## Test Plan
     - [x] Unit tests for `BatchPlan` aggregate (85% coverage)
     - [x] Unit tests for orchestrators (78% coverage)
     - [x] Integration tests for repository
     - [x] ArchUnit validation passed
     - [ ] Manual testing with real PubMed API
     - [ ] Performance testing with large batches
     ```

   **Step 6.6: Related**
   - Link to related issues (if any)
   - Dependencies or follow-up tasks
   - Example:
     ```markdown
     ## Related
     - Closes #123 (Implement batch planning)
     - Depends on: #120 (Provenance registry setup)
     - Follow-up: #125 (Add async processing)
     ```

7. **Show Generated Content to User**:
   - Display the auto-generated PR title and body
   - Ask user to confirm or provide feedback
   - Allow edits before creating PR

8. **Ensure Branch is Pushed**:
   - Check if branch is up to date: `git status`
   - If unpushed commits:
     ```
     Branch has unpushed commits. Push first with /git:push
     ```
     Stop here (or offer to run `/git:push` automatically)

9. **Create PR**:
   - Use `gh pr create` with generated content
   - Format:
     ```bash
     gh pr create \
       --title "PR Title" \
       --body "$(cat <<'EOF'
     PR Body here
     EOF
     )"
     ```
   - If base branch is not main, specify: `--base main`

10. **Report Success**:
    - Show PR URL
    - Show PR number
    - Suggest next actions:
      - View PR: `gh pr view`
      - Request reviewers: `gh pr edit --add-reviewer @user`

## Important Notes

- **ALWAYS analyze commits** - Don't generate generic PR descriptions
- **Be specific and detailed** - PR body should provide full context
- **Follow project patterns** - Use hexagonal architecture terminology
- **Include test info** - Always mention testing status
- **Link related items** - Connect to issues, other PRs
- **Add footer tag** - Include Claude Code attribution

## Examples of Auto-Generated PRs

### Example 1: Feature PR (Multiple Commits)

```
Branch: feat/pubmed-batch-planner

Analyzing commits...
  3 commits: 2 feat, 1 test
  Modules: patra-ingest (domain, app, infra)
  +450 lines, -20 lines

Generated PR:

Title: feat(ingest): implement PubMed batch planning system

Body:
---
## Summary
Implements PubMed batch planning system for slicing large publication queries into optimal chunks.
This enables efficient processing of large literature searches while respecting API rate limits.

**Key Changes**:
- **Domain**: Add `BatchPlan` aggregate with state machine (DRAFT → SLICING → COMPLETED)
- **App**: Implement `PlanOrchestrator` for use case coordination
- **Infra**: Add `BatchPlanRepositoryImpl` with optimistic locking
- **Tests**: Add comprehensive unit tests (85% domain coverage)

## Changes by Module

### patra-ingest-domain
- Add `BatchPlan` aggregate with state transitions
- Add `Task` value object for chunk representation
- Add `PlanCreatedEvent` domain event
- Add validation rules for plan parameters

### patra-ingest-app
- Implement `CreatePlanOrchestrator` for plan creation
- Implement `SlicePlanOrchestrator` for chunking logic
- Add `PlanCommand` DTOs for use case inputs

### patra-ingest-infra
- Add `BatchPlanDO` data object with JSON field
- Add `BatchPlanRepositoryMpImpl` using MyBatis-Plus
- Add `BatchPlanMapper` for DO ↔ Domain conversion
- Add database migration for batch_plan table

## Technical Details

**Architecture Compliance**:
- ✅ Domain layer remains pure Java (no Spring dependencies)
- ✅ Dependency direction: Adapter → App → Domain ← Infra
- ✅ Port/Adapter pattern followed
- ✅ ArchUnit validation passed

**Design Decisions**:
- Used aggregate state machine for plan lifecycle management
- Optimistic locking (version field) for concurrent plan updates
- JSON field for flexible task storage without schema changes
- Domain events for cross-aggregate communication

**Performance Considerations**:
- Batch size calculation based on API rate limits
- Efficient chunking algorithm (O(n) complexity)

**Breaking Changes**: None

## Test Plan
- [x] Unit tests for `BatchPlan` aggregate (85% coverage)
- [x] Unit tests for orchestrators (78% coverage)
- [x] Integration tests for repository with TestContainers
- [x] ArchUnit validation passed
- [x] Pre-push hooks passed
- [ ] Manual testing with real PubMed API
- [ ] Performance testing with large batches (10,000+ publications)
- [ ] Integration testing with downstream processing

## Related
- Closes #123 (Implement batch planning for PubMed ingestion)
- Depends on: #120 (Provenance registry setup)
- Follow-up: #125 (Add async processing for batch execution)

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)
---

Creating PR...
✅ PR created: https://github.com/yourorg/papertrace-api/pull/45
```

### Example 2: Bug Fix PR (Single Commit)

```
Branch: fix/registry-null-pointer

Analyzing commits...
  1 commit: 1 fix
  Module: patra-registry-infra
  +15 lines, -5 lines

Generated PR:

Title: fix(registry): correct null pointer in Provenance lookup

Body:
---
## Summary
Fixes null pointer exception when looking up Provenance by source name that doesn't exist.
Previously returned null and caused NPE in caller. Now properly throws `ProvenanceNotFoundException`.

**Key Changes**:
- **Infra**: Add null check in `ProvenanceRepositoryMpImpl.findBySourceName()`
- **Domain**: Throw domain exception instead of returning null
- **Tests**: Add test case for non-existent provenance lookup

## Changes by Module

### patra-registry-infra
- Add null check after MyBatis-Plus query in `ProvenanceRepositoryMpImpl`
- Throw `ProvenanceNotFoundException` when not found
- Update existing tests to expect exception

### patra-registry-domain
- Document exception behavior in `ProvenanceRepository` port

## Technical Details

**Root Cause**:
- MyBatis-Plus `selectOne()` returns null when no rows match
- Calling code didn't check for null before accessing properties
- Led to NPE in downstream services

**Fix**:
- Added explicit null check after query
- Throws domain exception with clear error message
- Maintains domain layer purity (exception defined in domain)

**Breaking Changes**: Yes (minor)
- Previously returned null, now throws exception
- Callers must handle `ProvenanceNotFoundException`
- All known callers updated in this PR

## Test Plan
- [x] Added test case for non-existent provenance
- [x] Updated existing tests to expect exception
- [x] Manual testing with invalid source names
- [x] Verified downstream services handle exception correctly

## Related
- Fixes #234 (NPE in Provenance lookup)
- Related to #230 (General error handling improvements)

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)
---

Creating PR...
✅ PR created: https://github.com/yourorg/papertrace-api/pull/46
```

### Example 3: Documentation PR

```
Branch: docs/update-architecture

Analyzing commits...
  2 commits: 2 docs
  Files: .claude/, docs/
  +200 lines, -50 lines

Generated PR:

Title: docs: update hexagonal architecture and DDD guidelines

Body:
---
## Summary
Updates architecture documentation to reflect current implementation patterns and adds DDD tactical patterns reference.

**Key Changes**:
- Update `.claude/AGENTS-architecture.md` with refined layer responsibilities
- Add design patterns reference (DDD, GoF, Enterprise patterns)
- Update `docs/ARCHITECTURE.md` with concrete examples from codebase
- Add diagrams for aggregate boundaries and event flows

## Changes

### .claude/AGENTS-architecture.md
- Add "Design Patterns Reference" section
- Clarify layer responsibilities with examples
- Add pattern selection guidelines

### docs/ARCHITECTURE.md
- Update with real examples from patra-ingest
- Add aggregate boundary diagrams
- Document event flow patterns

### docs/DEV-GUIDE.md
- Add references to new architecture patterns
- Update development workflow

## Technical Details

**Motivation**:
- New team members need clear pattern guidance
- Current docs lack concrete examples
- Pattern selection guidelines requested in retrospective

**Changes**:
- All documentation-only (no code changes)
- No breaking changes
- Aligned with actual codebase implementation

## Test Plan
- [x] Verified all internal links work
- [x] Spell-checked with codespell
- [x] Reviewed by team lead
- [ ] Publish to team wiki (post-merge)

## Related
- Closes #250 (Update architecture documentation)
- Follow-up: #255 (Create video walkthrough)

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)
---

Creating PR...
✅ PR created: https://github.com/yourorg/papertrace-api/pull/47
```

## Error Handling

### Scenario 1: GitHub CLI Not Installed

```
❌ GitHub CLI (gh) not found

Install GitHub CLI:
  macOS:  brew install gh
  Linux:  See https://github.com/cli/cli#installation
  Windows: winget install GitHub.cli

Then authenticate:
  gh auth login
```

### Scenario 2: Not Authenticated

```
❌ Not authenticated with GitHub

Run: gh auth login

This will open a browser for authentication.
```

### Scenario 3: No Commits to Create PR

```
❌ No commits ahead of main branch

Your branch is up to date with main.
Nothing to create PR for.

Tip: Make changes first, then run:
  /git:commit
  /git:push
  /git:pr
```

### Scenario 4: Branch Not Pushed

```
❌ Branch not pushed to remote yet

Push first with: /git:push

Or let me push for you?
  [Y]es, push and create PR
  [N]o, cancel

> User chooses Y

Running /git:push...
[pre-push checks run...]
✅ Pushed successfully

Now creating PR...
```

### Scenario 5: PR Already Exists

```
❌ Pull request already exists for this branch

View existing PR:
  gh pr view

Update existing PR:
  gh pr edit --title "New Title"
  gh pr edit --body "New Body"

Or create new PR to different base:
  gh pr create --base develop
```

## Integration with GitHub CLI

This command uses GitHub CLI (`gh`) which must be installed and authenticated.

**Required commands**:
- `gh auth status` - Check authentication
- `gh pr create` - Create pull request
- `gh pr view` - View pull request
- `gh pr edit` - Edit pull request

**Installation**:
```bash
# macOS
brew install gh

# Ubuntu/Debian
sudo apt install gh

# Fedora/RHEL
sudo dnf install gh

# Windows
winget install GitHub.cli
```

**Authentication**:
```bash
gh auth login
# Follow prompts to authenticate via browser
```

## After PR Creation - Next Steps

After successful PR creation:

```
✅ PR created successfully!

PR #45: feat(ingest): implement PubMed batch planning system
URL: https://github.com/yourorg/papertrace-api/pull/45

Next steps:
  • View PR: gh pr view 45 --web
  • Request reviewers: gh pr edit 45 --add-reviewer @teammate
  • Add labels: gh pr edit 45 --add-label enhancement
  • Enable auto-merge: gh pr merge 45 --auto --squash

CI/CD checks will run automatically.
```

## Tips for Better PR Descriptions

1. **Be specific** - Mention exact classes, methods, patterns used
2. **Explain why** - Not just what changed, but why the change was made
3. **Link context** - Reference issues, docs, related PRs
4. **List tests** - Show what testing was done
5. **Flag breaking changes** - Make them obvious
6. **Include screenshots** - For UI changes (if applicable)
7. **Suggest reviewers** - Tag domain experts

Claude will automatically generate comprehensive descriptions following these principles.
