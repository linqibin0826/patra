You are about to help the user push their changes to the remote repository with pre-push checks.

## Your Task

1. **Check for Skip Flag**:
   - Check if user provided `--skip-hooks` flag
   - If yes, skip to step 6 (push without hooks)
   - If no, continue with checks

2. **Check Branch Status**:
   - Run `git status` to get current branch
   - Run `git rev-parse --abbrev-ref HEAD` to get branch name
   - Run `git rev-parse --abbrev-ref --symbolic-full-name @{u}` to get upstream branch
   - If no upstream, inform user to set one:
     ```
     git push -u origin <branch-name>
     ```

3. **Check What Will Be Pushed**:
   - Run `git log @{u}..HEAD --oneline` to see commits ahead of upstream
   - If no commits to push:
     ```
     No commits to push. Everything up to date.
     ```
     Stop here
   - Show user the commits that will be pushed

4. **Analyze Changes for Smart Hook Decision**:
   - Run `git diff @{u}...HEAD --name-only` to see changed files
   - Classify changes:
     - **Docs only**: All files are *.md or in docs/
     - **Tests only**: All files are in src/test/
     - **Code changes**: Any Java files in src/main/
     - **Config only**: Only pom.xml, *.yaml, *.properties

   **Smart decision**:
   - If **docs only** → Suggest skipping tests (but still run format check)
   - If **tests only** → Run all tests
   - If **code changes** → Run all checks (tests + SpotBugs)
   - If **config only** → Run compile + tests

5. **Run Pre-push Hooks**:

   **Step 5.1: Format check**
   - Always run format check (fast)
   - Script: `scripts/git/mvn_test_changed_modules.sh` includes format check

   **Step 5.2: Tests**
   - Run unit tests for changed modules
   - Script: `scripts/git/mvn_test_changed_modules.sh`
   - Shows:
     ```
     [pre-push] Comparing against upstream: origin/main
     [pre-push] Affected modules (2):
       - patra-ingest-domain
       - patra-ingest-app
     [pre-push] Running unit tests...
     ✅ Tests passed
     ```

   **Step 5.3: SpotBugs (if enabled)**
   - Check if SpotBugs is enabled in `.pre-commit-config.yaml`
   - Script: `scripts/git/mvn_spotbugs_changed_modules.sh`
   - Currently DISABLED by default, but can be enabled

   **Step 5.4: Coverage (if CHECK_COVERAGE=1)**
   - Only runs if environment variable CHECK_COVERAGE=1
   - Script uses `scripts/git/lib/jacoco_utils.sh`
   - Layer-specific thresholds:
     - domain: 85%
     - app: 75%
     - infra: 70%
     - adapter: 60%

   **If any check fails**:
   - Show the error details
   - Suggest fixes:
     - Fix the issue and retry
     - Skip specific check: `SKIP_TESTS=1 git push`
     - Skip all hooks: `git push --no-verify` or `/git:push --skip-hooks`
   - Ask user what they want to do:
     - [F]ix and retry
     - [S]kip hooks and push
     - [C]ancel

6. **Push to Remote**:
   - Run `git push`
   - If fails (e.g., rejected, need to pull):
     - Show error
     - Suggest:
       - `git pull --rebase` if behind
       - Force push if needed (with confirmation): `git push --force-with-lease`

7. **Report Success**:
   - Show push result
   - Show branch status
   - Suggest next action:
     - If feature branch: `/git:pr` to create pull request
     - If main branch: Deployment info (if applicable)

## Important Notes

- **Default behavior**: ALWAYS run hooks unless `--skip-hooks` specified
- **Smart suggestions**: Suggest skipping hooks ONLY for safe changes (docs-only)
- **Clear errors**: Show what failed and how to fix
- **Safe push**: Never force push without explicit confirmation
- **Respect skip flags**: Support SKIP_TESTS, SKIP_SPOTBUGS, SKIP_HOOKS environment variables

## Examples

### Example 1: Normal Push with All Checks

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Upstream: origin/feat/pubmed-batch-planner

Commits to push (3):
  abc1234 feat(ingest): add batch planning aggregate
  def5678 test(ingest): add unit tests for BatchPlan
  ghi9012 docs: update architecture guidelines

Analyzing changes...
  • 5 Java files in patra-ingest-domain
  • 3 test files in patra-ingest-domain
  • 1 doc file

Running pre-push checks...

[1/2] Checking format compliance...
✅ Format check passed

[2/2] Running unit tests...
[pre-push] Affected modules (2):
  - patra-ingest-domain
  - patra-ingest-app
✅ Tests passed (23 tests, 0 failures)

Pushing to origin/feat/pubmed-batch-planner...
✅ Pushed successfully

Next: Run /git:pr to create pull request
```

### Example 2: Docs-Only Changes (Smart Skip Suggestion)

```
Checking branch status...
Branch: docs/update-architecture
Upstream: origin/docs/update-architecture

Commits to push (1):
  abc1234 docs: update hexagonal architecture guidelines

Analyzing changes...
  • 2 markdown files in docs/
  • 1 markdown file in .claude/

Smart analysis: Only documentation changed
Suggestion: Tests can be safely skipped

Do you want to:
  [R]un all checks (safe, slower)
  [S]kip tests (docs only, faster)
  [C]ancel

> User chooses S

Skipping tests (docs-only changes)...
Pushing to origin/docs/update-architecture...
✅ Pushed successfully
```

### Example 3: Tests Failed

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Commits to push (1):
  abc1234 feat(ingest): add batch planning aggregate

Running pre-push checks...

[1/2] Checking format compliance...
✅ Format check passed

[2/2] Running unit tests...
[pre-push] Affected modules (1):
  - patra-ingest-domain

❌ Tests failed!

Failures:
  • BatchPlanTest.testStateTransition - Expected SLICING but was DRAFT

What do you want to do?
  [F]ix the test and retry
  [S]kip hooks and push (not recommended)
  [C]ancel

> User chooses F

Okay, please fix the test and run /git:push again.

Tip: Run tests locally first:
  mvn test -pl patra-ingest-domain
```

### Example 4: Skip Hooks Explicitly

```
User ran: /git:push --skip-hooks

⚠️  Skipping all pre-push hooks!

This will bypass:
  • Format compliance check
  • Unit tests
  • SpotBugs static analysis

Are you sure? [y/N]: y

Pushing to origin/feat/pubmed-batch-planner...
✅ Pushed successfully

Warning: Hooks were skipped. CI/CD checks may still fail.
```

### Example 5: Need to Pull First

```
Checking branch status...
Branch: feat/pubmed-batch-planner

Running pre-push checks...
✅ All checks passed

Pushing to origin/feat/pubmed-batch-planner...

❌ Push rejected!

Error:
  Updates were rejected because the remote contains work that you do not have locally.
  This is usually caused by another repository pushing to the same ref.

Suggestion:
  1. Pull latest changes:
     git pull --rebase origin feat/pubmed-batch-planner

  2. Or sync with upstream:
     /git:sync --rebase

  3. Then push again:
     /git:push
```

## Skip Flags Reference

User can set environment variables to skip specific checks:

| Flag | Effect | Use Case |
|------|--------|----------|
| `SKIP_TESTS=1` | Skip test execution | Already tested locally |
| `SKIP_FORMAT=1` | Skip format check | Already formatted |
| `SKIP_SPOTBUGS=1` | Skip SpotBugs | Known false positives |
| `SKIP_JACOCO=1` | Skip coverage check | When CHECK_COVERAGE=1 |
| `SKIP_HOOKS=1` | Skip all hooks | Emergency hotfix |

**Usage**:
```bash
# Skip tests only
SKIP_TESTS=1 git push

# Skip all hooks
SKIP_HOOKS=1 git push
# or
git push --no-verify
# or
/git:push --skip-hooks
```

## Integration with Existing Hooks

This command leverages the project's pre-push hooks (configured in `.pre-commit-config.yaml`):

**Hook script**: `scripts/git/mvn_test_changed_modules.sh`

**Checks performed**:
1. **Format compliance** - fmt-maven-plugin:check
2. **Unit tests** - Changed modules only (smart detection)
3. **SpotBugs** - Currently disabled (commented out in config)
4. **Coverage** - Opt-in with CHECK_COVERAGE=1

**Smart module detection**:
- Compares against upstream branch
- Only tests changed modules (not entire codebase)
- Uses parallel processing for speed
- Caches results to avoid redundant checks

## Handling Different Scenarios

### Scenario 1: First Push (No Upstream)

```
❌ No upstream branch configured

To push and set upstream:
  git push -u origin feat/pubmed-batch-planner

Or let me do it for you?
  Running: git push -u origin feat/pubmed-batch-planner
```

### Scenario 2: Force Push Needed (Rewritten History)

```
❌ Push rejected! Updates were rejected because the tip of your current branch is behind.

Did you rebase or amend commits?

Options:
  [P]ull and merge
  [R]ebase on top of remote
  [F]orce push with lease (safe force push)
  [C]ancel

> User chooses F

⚠️  Force pushing with --force-with-lease (safe)...
✅ Pushed successfully

Warning: Force push was used. Notify team members if they have this branch checked out.
```

### Scenario 3: Coverage Check Enabled

```
User has CHECK_COVERAGE=1 environment variable

Running pre-push checks...

[1/3] Checking format compliance...
✅ Format check passed

[2/3] Running unit tests...
✅ Tests passed

[3/3] Checking coverage thresholds...
[jacoco] Affected modules (2):
  - patra-ingest-domain: 88% (threshold: 85%) ✅
  - patra-ingest-app: 78% (threshold: 75%) ✅
✅ Coverage check passed

Pushing to origin/feat/pubmed-batch-planner...
✅ Pushed successfully
```

## After Push - Next Steps

After successful push, suggest appropriate next actions:

**If feature branch**:
```
✅ Pushed successfully to origin/feat/pubmed-batch-planner

Next steps:
  • Create pull request: /git:pr
  • Or continue working and push more changes
```

**If main/master branch**:
```
✅ Pushed successfully to origin/main

⚠️  Pushed to main branch!
  • CI/CD pipeline will start
  • Monitor deployment at [CI/CD URL]
```

**If docs branch**:
```
✅ Pushed successfully to origin/docs/update-architecture

  • Documentation updates are live
  • No further action needed
```
