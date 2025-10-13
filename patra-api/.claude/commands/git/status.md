You are about to show the user a comprehensive Git status summary with module breakdown and suggested next actions.

## Your Task

1. **Get Basic Git Status**:
   - Run `git status` to get current state
   - Extract:
     - Current branch name
     - Staged files
     - Unstaged files
     - Untracked files
     - Branch tracking status (ahead/behind)

2. **Get Branch Information**:
   - Run `git rev-parse --abbrev-ref HEAD` for current branch
   - Run `git rev-parse --abbrev-ref --symbolic-full-name @{u}` for upstream branch
   - If no upstream, note this
   - Run `git rev-list --count HEAD ^main` (or ^master) to count commits ahead
   - Run `git rev-list --count main ^HEAD` to count commits behind

3. **Analyze Changed Files by Module**:

   **Step 3.1: Get all changed files**
   - Staged: `git diff --cached --name-only`
   - Unstaged: `git diff --name-only`
   - Untracked: `git ls-files --others --exclude-standard`

   **Step 3.2: Group by module**
   - For each file, determine its module:
     - `patra-ingest/patra-ingest-domain/...` → patra-ingest-domain
     - `patra-registry/patra-registry-app/...` → patra-registry-app
     - `patra-common/...` → patra-common
     - Root files → project root
   - Count files per module

   **Step 3.3: Group by layer (within each module)**
   - domain → Domain layer
   - app → Application layer
   - infra → Infrastructure layer
   - adapter → Adapter layer
   - boot → Boot/Entry layer
   - test → Test files
   - docs → Documentation
   - config → Configuration

   **Step 3.4: Classify file changes**
   - New files (untracked or added)
   - Modified files
   - Deleted files

4. **Analyze Change Type**:
   - **Code changes**: Java files in src/main/
   - **Test changes**: Java files in src/test/
   - **Documentation**: *.md files
   - **Configuration**: pom.xml, *.yaml, *.properties
   - **Build**: Maven, Docker, scripts

5. **Show Organized Status**:

   **Format**:
   ```
   Branch: <branch-name> (<commits> ahead, <commits> behind <upstream>)
   Status: <on-track | behind | diverged | no-upstream>

   Changed Modules:
     <module-name>:
       <layer>: <file-count> files (<staged>/<unstaged>/<untracked>)
       Files:
         • <file-1> (modified, staged)
         • <file-2> (new, untracked)
         • <file-3> (modified, unstaged)

   Summary:
     Staged:    <count> files across <modules> modules
     Unstaged:  <count> files
     Untracked: <count> files

   Recent Commits:
     <last 3 commits with hash and message>

   Next Actions:
     <suggested command based on state>
   ```

6. **Suggest Next Action**:

   Based on current state, suggest the appropriate next command:

   **If unstaged/untracked files**:
   ```
   Next: /git:commit (to commit changes)
   ```

   **If staged files but nothing committed**:
   ```
   Next: /git:commit (to create commit)
   ```

   **If commits ahead but not pushed**:
   ```
   Next: /git:push (to push changes)
   ```

   **If commits pushed but no PR**:
   ```
   Next: /git:pr (to create pull request)
   ```

   **If branch behind upstream**:
   ```
   Next: /git:sync (to sync with upstream)
   ```

   **If clean working tree**:
   ```
   All changes committed and pushed. Ready for PR or continue development.
   ```

## Important Notes

- **Group by module first** - Makes it easy to see what services are affected
- **Show file counts** - Don't list all files if there are many (>10 per module)
- **Classify changes** - Help user understand type of work (feature, fix, docs, etc.)
- **Suggest next action** - Guide the user through the workflow
- **Show recent commits** - Context for what's been done

## Examples

### Example 1: Active Development (Multiple Modules)

```
Branch: feat/pubmed-batch-planner (3 ahead, 0 behind origin/feat/pubmed-batch-planner)
Status: Ready to push

Changed Modules:
  patra-ingest-domain:
    domain: 5 files (2 staged, 3 unstaged)
    Files:
      • BatchPlan.java (new, staged)
      • Task.java (new, staged)
      • PlanEvent.java (modified, unstaged)
      • ValidationRules.java (new, unstaged)
      • ProvenanceId.java (modified, unstaged)

  patra-ingest-app:
    app: 2 files (1 staged, 1 unstaged)
    Files:
      • PlanOrchestrator.java (new, staged)
      • PlanCommand.java (modified, unstaged)

  patra-ingest-infra:
    infra: 3 files (3 staged)
    Files:
      • BatchPlanDO.java (new, staged)
      • BatchPlanRepositoryMpImpl.java (new, staged)
      • BatchPlanMapper.java (new, staged)

  docs:
    1 file (0 staged, 1 unstaged)
    Files:
      • ARCHITECTURE.md (modified, unstaged)

Summary:
  Staged:    8 files across 3 modules
  Unstaged:  5 files across 3 modules
  Untracked: 0 files

Change Type Analysis:
  • Code changes: 10 Java files (domain, app, infra layers)
  • Documentation: 1 markdown file
  • Configuration: 0 files

Recent Commits (on this branch):
  abc1234 feat(ingest): add batch planning aggregate
  def5678 feat(ingest): add plan orchestrator
  ghi9012 test(ingest): add unit tests for BatchPlan

Next Actions:
  1. Stage remaining changes: git add .
  2. Commit: /git:commit
  3. Push: /git:push
  4. Create PR: /git:pr
```

### Example 2: Ready to Push

```
Branch: feat/pubmed-batch-planner (3 ahead, 0 behind origin/feat/pubmed-batch-planner)
Status: Ready to push

All changes committed. Working tree clean.

Commits to push (3):
  abc1234 feat(ingest): add batch planning aggregate and orchestrator
  def5678 test(ingest): add unit tests for BatchPlan
  ghi9012 docs: update architecture guidelines

Changed Modules (since main):
  • patra-ingest-domain: +450 lines, -20 lines
  • patra-ingest-app: +230 lines, -10 lines
  • patra-ingest-infra: +180 lines, -5 lines
  • docs: +50 lines, -15 lines

Total: +910 lines, -50 lines

Next: /git:push (to push changes and run pre-push checks)
```

### Example 3: Behind Upstream

```
Branch: feat/pubmed-batch-planner (3 ahead, 5 behind origin/feat/pubmed-batch-planner)
Status: Diverged from upstream

⚠️  Your branch is behind the remote branch by 5 commits.

Remote commits:
  xyz1234 feat(ingest): add validation rules (by @teammate)
  uvw5678 fix(ingest): correct state transition logic (by @teammate)
  rst9012 test(ingest): add integration tests (by @teammate)
  ... and 2 more

Your local commits:
  abc1234 feat(ingest): add batch planning aggregate
  def5678 test(ingest): add unit tests
  ghi9012 docs: update architecture guidelines

⚠️  You need to sync with upstream before pushing.

Next: /git:sync (to pull latest changes and merge/rebase)
```

### Example 4: Documentation Only

```
Branch: docs/update-architecture (2 ahead, 0 behind origin/docs/update-architecture)
Status: Ready to push

Changed Modules:
  docs:
    3 files (3 staged)
    Files:
      • ARCHITECTURE.md (modified, staged)
      • DEV-GUIDE.md (modified, staged)
      • PRE-COMMIT-HOOKS.md (new, staged)

  .claude:
    2 files (2 staged)
    Files:
      • AGENTS-architecture.md (modified, staged)
      • AGENTS-development.md (modified, staged)

Summary:
  Staged: 5 files
  Total: +250 lines, -50 lines

Change Type Analysis:
  • Documentation only: 5 markdown files
  • No code changes

Recent Commits:
  abc1234 docs: update hexagonal architecture guidelines
  def5678 docs: add pre-commit hooks documentation

Next: /git:push (documentation-only, hooks will be fast)
```

### Example 5: Clean State (PR Created)

```
Branch: feat/pubmed-batch-planner (0 ahead, 0 behind origin/feat/pubmed-batch-planner)
Status: Up to date

Working tree clean. All changes committed and pushed.

Pull Request: #45 (Open)
  Title: feat(ingest): implement PubMed batch planning system
  URL: https://github.com/yourorg/papertrace-api/pull/45
  Status: ✅ CI checks passing

Next:
  • View PR: gh pr view 45 --web
  • Request review: gh pr edit 45 --add-reviewer @teammate
  • Continue development on this branch
  • Or checkout main: git checkout main
```

### Example 6: Untracked Files Only

```
Branch: feat/new-feature (0 ahead, 0 behind origin/feat/new-feature)
Status: Up to date

Untracked Files (5):
  patra-ingest-domain:
    • NewAggregate.java
    • NewValueObject.java
  patra-ingest-app:
    • NewOrchestrator.java
  patra-ingest-infra:
    • NewRepositoryImpl.java
    • NewMapper.java

Summary:
  Staged:    0 files
  Unstaged:  0 files
  Untracked: 5 files

These files are not tracked by Git yet.

Next:
  1. Stage files: git add .
  2. Commit: /git:commit
```

### Example 7: Multiple Types of Changes

```
Branch: feat/mixed-changes (2 ahead, 0 behind origin/feat/mixed-changes)
Status: Ready to push

Changed Modules:
  patra-ingest-domain (5 files):
    • 3 Java files (domain layer)
    • 2 test files

  patra-registry-infra (2 files):
    • 2 Java files (infra layer)

  patra-common (1 file):
    • 1 Java file (utilities)

  Configuration (3 files):
    • pom.xml (2 files)
    • application.yaml (1 file)

  Documentation (2 files):
    • ARCHITECTURE.md
    • README.md

Summary:
  Total: 13 files
  Modules affected: 3 services (ingest, registry, common)
  Layers: domain, infra, config, docs

Change Type Analysis:
  • New features: 6 Java classes
  • Bug fixes: 2 Java classes
  • Tests: 2 test files
  • Config: 3 configuration files
  • Docs: 2 markdown files

Recent Commits:
  abc1234 feat(ingest): add batch planning
  def5678 fix(registry): correct null pointer

Next: /git:push (multiple modules changed, full checks will run)
```

## Handling Edge Cases

### No Upstream Branch

```
Branch: feat/new-feature
Status: No upstream branch

⚠️  This branch is not tracking a remote branch.

To set upstream:
  git push -u origin feat/new-feature

Or use: /git:push (will set upstream automatically)
```

### On Main Branch

```
Branch: main (0 ahead, 0 behind origin/main)
Status: Up to date

⚠️  You're on the main branch.

To start a new feature:
  git checkout -b feat/your-feature-name

Recommended branch naming:
  • feat/<name> - New features
  • fix/<name> - Bug fixes
  • docs/<name> - Documentation
  • refactor/<name> - Code refactoring
```

### Detached HEAD State

```
⚠️  Detached HEAD state

You're not on any branch. Current commit: abc1234

To fix:
  1. Create a branch: git checkout -b feat/branch-name
  2. Or return to main: git checkout main
```

### Merge Conflicts

```
Branch: feat/pubmed-batch-planner
Status: Merge conflicts

⚠️  You have unresolved merge conflicts!

Conflicted files:
  • patra-ingest-domain/src/main/java/.../BatchPlan.java
  • patra-ingest-app/src/main/java/.../PlanOrchestrator.java

To resolve:
  1. Edit conflicted files and resolve markers (<<<, ===, >>>)
  2. Stage resolved files: git add <file>
  3. Continue merge: git merge --continue

Or abort: git merge --abort
```

## Next Action Logic

**Decision tree**:
1. **Merge conflicts?** → Resolve conflicts first
2. **Behind upstream?** → `/git:sync`
3. **Unstaged/untracked files?** → `/git:commit`
4. **Commits ahead?** → `/git:push`
5. **Pushed but no PR?** → `/git:pr`
6. **Everything clean?** → Continue development or review PR
