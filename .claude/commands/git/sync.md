You are about to help the user sync their branch with the upstream branch (usually main) using an intelligent merge strategy.

## Your Task

1. **Check for User-Provided Strategy**:
   - Check if user provided `--rebase` or `--merge` flag
   - If provided, use that strategy
   - If not provided, proceed to auto-decide (step 4)

2. **Check Current State**:
   - Run `git status` to check working tree
   - If dirty (unstaged/uncommitted changes):
     ```
     ❌ You have uncommitted changes

     Please commit or stash your changes first:
       Commit: /git:commit
       Stash:  git stash
     ```
     Stop here

3. **Get Branch Information**:
   - Run `git rev-parse --abbrev-ref HEAD` for current branch
   - Check if on main/master:
     ```
     ❌ You're on the main branch

     Sync main with remote:
       git pull origin main

     Or create a feature branch:
       git checkout -b feat/your-feature
     ```
     Stop here

   - Get target branch (default: main, or master if main doesn't exist)
   - Run `git fetch origin` to get latest changes

4. **Analyze Divergence**:

   **Step 4.1: Check commits ahead/behind**
   - Run `git rev-list --left-right --count main...HEAD` (or master...HEAD)
   - Parse output: `<behind> <ahead>`
   - Examples:
     - `0 5` → 0 behind, 5 ahead (clean)
     - `3 5` → 3 behind, 5 ahead (diverged)
     - `3 0` → 3 behind, 0 ahead (just need to pull)

   **Step 4.2: Show divergence info**
   ```
   Branch Status:
     Current: feat/pubmed-batch-planner
     Target:  main
     Ahead:   5 commits
     Behind:  3 commits
     Status:  Diverged
   ```

5. **Auto-Decide Strategy** (if user didn't specify):

   **Decision logic**:

   **Case 1: Only behind (0 ahead)**
   ```
   Strategy: Fast-forward (simple pull)
   Reason: No local commits, just need to pull
   ```

   **Case 2: Only ahead (0 behind)**
   ```
   No sync needed. Your branch is ahead.
   Suggestion: Push your changes with /git:push
   ```

   **Case 3: Diverged, branch never pushed**
   ```
   Strategy: Rebase (recommended)
   Reason: Branch not pushed, safe to rewrite history
   Benefit: Clean, linear history
   ```

   **Case 4: Diverged, branch already pushed**
   ```
   Strategy: Merge (safer)
   Reason: Branch already pushed, rebase would require force push
   Benefit: Preserves exact history, no force push needed
   Warning: Creates merge commit
   ```

   **Step 5.1: Check if branch pushed**
   - Run `git rev-parse --abbrev-ref --symbolic-full-name @{u}` to get upstream
   - Run `git log @{u}..HEAD --oneline` to see unpushed commits
   - If all commits are unpushed → prefer rebase
   - If some commits are pushed → prefer merge (or warn about force push)

6. **Show Sync Plan to User**:
   ```
   Sync Plan:
     Strategy: Rebase (recommended)
     Action:   Rebase feat/pubmed-batch-planner onto main

     This will:
       • Fetch latest changes from main
       • Rewind your commits temporarily
       • Apply main's commits first
       • Replay your commits on top
       • Result: Clean, linear history

     Conflicts may occur if main changed the same files.

     Proceed? [Y/n]:
   ```

7. **Execute Sync**:

   **For Fast-forward (only behind)**:
   ```bash
   git pull origin main --ff-only
   ```

   **For Rebase**:
   ```bash
   git rebase main
   ```

   **For Merge**:
   ```bash
   git merge main
   ```

8. **Handle Conflicts** (if any):

   **If conflicts occur**:
   - List conflicted files: `git diff --name-only --diff-filter=U`
   - Show conflict count
   - Provide guidance:
     ```
     ❌ Merge conflicts detected!

     Conflicted files (3):
       • patra-ingest-domain/src/main/java/.../BatchPlan.java
       • patra-ingest-app/src/main/java/.../PlanOrchestrator.java
       • patra-ingest-infra/src/main/java/.../BatchPlanRepositoryImpl.java

     To resolve:
       1. Open each file and resolve conflict markers:
          <<<<<<< HEAD (your changes)
          =======
          >>>>>>> main (upstream changes)

       2. After resolving, stage files:
          git add <file>

       3. Continue:
          For rebase: git rebase --continue
          For merge:  git commit

       4. Or abort:
          For rebase: git rebase --abort
          For merge:  git merge --abort

     Layer-aware tips:
       • Domain layer: Carefully preserve business rules
       • Infra layer: Check for DB schema conflicts
       • Tests: May need to merge both sets of tests
     ```

   **Offer to help**:
   - Show diff for each conflict: `git diff <file>`
   - Explain what each side changed
   - Suggest resolution based on layer/context

9. **Report Success**:
   ```
   ✅ Sync completed successfully!

   Strategy used: Rebase
   Commits replayed: 5
   Conflicts resolved: 0

   Updated branch state:
     Ahead:  5 commits (unchanged)
     Behind: 0 commits (now up to date)

   Your commits are now on top of the latest main branch.

   Next steps:
     • Run tests to ensure everything still works
     • Push updated branch: /git:push (will require force push)
     • Or continue development
   ```

10. **Handle Force Push Warning** (if rebase was used on pushed branch):
    ```
    ⚠️  Branch was rebased and previously pushed

    Your branch's history has been rewritten.
    To push, you need to force push:

      git push --force-with-lease

    ⚠️  WARNING: Only do this if no one else is working on this branch!

    Alternative: Use merge next time for shared branches.

    Proceed with force push? [y/N]:
    ```

## Important Notes

- **Default to safe strategy** - Prefer merge for pushed branches
- **Clean working tree required** - Must commit or stash first
- **Clear conflict guidance** - Help user resolve conflicts by layer
- **Layer-aware conflict resolution** - Understand domain vs infra conflicts
- **Warn about force push** - Make it clear when history is rewritten
- **Explain trade-offs** - Rebase = clean history, Merge = safe

## Examples

### Example 1: Fast-Forward (Only Behind)

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Target: main

Fetching latest changes...

Branch Status:
  Ahead:   0 commits
  Behind:  3 commits
  Status:  Behind main

No local commits. Can fast-forward.

Strategy: Fast-forward pull

Pulling main...
✅ Successfully fast-forwarded to main

Your branch is now up to date with main.

Next: Continue development or /git:status to see current state
```

### Example 2: Rebase (Diverged, Never Pushed)

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Target: main

Fetching latest changes...

Branch Status:
  Ahead:   5 commits (local only, never pushed)
  Behind:  3 commits
  Status:  Diverged

Auto-selected strategy: Rebase
Reason: Branch never pushed, safe to rewrite history

Sync Plan:
  Strategy: Rebase
  Action:   Rebase your 5 commits onto latest main

  Main's new commits (3):
    abc1234 feat(registry): add new provenance source
    def5678 fix(common): fix validation utility
    ghi9012 docs: update API documentation

  Your commits (5):
    111aaaa feat(ingest): add batch planning aggregate
    222bbbb feat(ingest): add plan orchestrator
    333cccc test(ingest): add unit tests
    444dddd fix(ingest): correct state transition
    555eeee docs: update architecture

Proceed with rebase? [Y/n]: y

Rebasing...
Applying: feat(ingest): add batch planning aggregate
Applying: feat(ingest): add plan orchestrator
Applying: test(ingest): add unit tests
Applying: fix(ingest): correct state transition
Applying: docs: update architecture

✅ Rebase completed successfully!

Your 5 commits are now on top of the latest main.

Next: /git:push to push updated branch
```

### Example 3: Rebase with Conflicts

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Target: main

Fetching latest changes...

Auto-selected strategy: Rebase

Rebasing...
Applying: feat(ingest): add batch planning aggregate
Applying: feat(ingest): add plan orchestrator

❌ Merge conflicts detected!

Conflicted files (2):
  • patra-ingest-domain/src/main/java/.../BatchPlan.java
  • patra-ingest-app/src/main/java/.../PlanOrchestrator.java

Conflict Analysis:

1. BatchPlan.java (Domain layer):
   Main changed:  Added new state CANCELLED
   You changed:   Added new state PAUSED
   Resolution:    Need to keep both states

2. PlanOrchestrator.java (App layer):
   Main changed:  Refactored method signature
   You changed:   Added new method
   Resolution:    Apply your changes to new signature

To resolve:
  1. Edit conflicted files
  2. Stage resolved files: git add <file>
  3. Continue: git rebase --continue
  4. Or abort: git rebase --abort

Need help? Let me show you the conflicts...

[Shows git diff for each conflict with explanations]
```

### Example 4: Merge (Diverged, Already Pushed)

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Target: main

Fetching latest changes...

Branch Status:
  Ahead:   5 commits (3 pushed, 2 local)
  Behind:  3 commits
  Status:  Diverged

Auto-selected strategy: Merge (safer)
Reason: Branch already pushed to remote. Rebase would require force push.

Alternative: Use --rebase flag to rebase anyway (requires force push)

Sync Plan:
  Strategy: Merge
  Action:   Merge main into feat/pubmed-batch-planner

  This will:
    • Create a merge commit
    • Preserve exact history
    • No force push needed
    • Result: Non-linear history (but safer)

Proceed with merge? [Y/n]: y

Merging main...
✅ Merge completed successfully!

Merge commit created: 9876543

Next: /git:push to push merged branch (no force push needed)
```

### Example 5: User Chooses Strategy (Override)

```
User ran: /git:sync --rebase

Checking branch status...
Branch: feat/pubmed-batch-planner (already pushed)
Target: main

⚠️  You specified --rebase, but this branch is already pushed.

Rebase will rewrite history and require force push.

Are you sure? [y/N]: y

Proceeding with rebase...

Rebasing...
✅ Rebase completed successfully!

⚠️  Branch history was rewritten!

To push:
  git push --force-with-lease origin feat/pubmed-batch-planner

Or let me push for you: /git:push --force-with-lease

⚠️  WARNING: Notify team members if they have this branch checked out!
```

### Example 6: Already Up to Date

```
Checking branch status...
Branch: feat/pubmed-batch-planner
Target: main

Fetching latest changes...

Branch Status:
  Ahead:   5 commits
  Behind:  0 commits
  Status:  Up to date with main

✓ Your branch is already up to date with main!

No sync needed.

Next:
  • Continue development
  • Or push your changes: /git:push
```

### Example 7: Uncommitted Changes

```
Checking branch status...

❌ You have uncommitted changes

Changed files:
  • patra-ingest-domain/src/main/java/.../BatchPlan.java
  • patra-ingest-app/src/main/java/.../PlanOrchestrator.java

You must commit or stash changes before syncing:

Option 1: Commit changes
  /git:commit

Option 2: Stash changes
  git stash
  /git:sync
  git stash pop

Option 3: Stash with message
  git stash push -m "WIP: batch planning work"
  /git:sync
  git stash pop
```

## Strategy Decision Matrix

| Ahead | Behind | Pushed? | Auto Strategy | Reason |
|-------|--------|---------|---------------|--------|
| 0     | N      | -       | Fast-forward  | No local commits, simple pull |
| N     | 0      | -       | No sync       | Already up to date |
| N     | M      | No      | Rebase        | Never pushed, clean history |
| N     | M      | Yes     | Merge         | Already pushed, no force push |

**User can override** with `--rebase` or `--merge` flags.

## Conflict Resolution Tips by Layer

### Domain Layer Conflicts
```
Domain layer conflict detected in: BatchPlan.java

Priority: HIGH (core business logic)

Tips:
  • Carefully preserve business rules from both sides
  • Ensure invariants are maintained
  • Consider if both changes are compatible
  • Run unit tests after resolution

Common scenarios:
  • New fields added: Usually safe to keep both
  • Method signature changed: Need to reconcile
  • Validation rules changed: May need to merge logic
```

### Application Layer Conflicts
```
App layer conflict detected in: PlanOrchestrator.java

Priority: MEDIUM (orchestration logic)

Tips:
  • Check if both sides call new domain methods
  • Ensure transaction boundaries are correct
  • Verify port interfaces match
  • Check for duplicate orchestration logic
```

### Infrastructure Layer Conflicts
```
Infra layer conflict detected in: BatchPlanRepositoryImpl.java

Priority: HIGH (data access)

Tips:
  • Check for database schema conflicts
  • Verify MyBatis-Plus mappers are compatible
  • Ensure DO ↔ Domain mapping is correct
  • Check for SQL query conflicts
```

### Test Conflicts
```
Test conflict detected in: BatchPlanTest.java

Priority: LOW (tests can be regenerated)

Tips:
  • Often both sets of tests are needed
  • Merge test methods from both sides
  • Check for duplicate test names
  • May need to regenerate with /test:auto after resolution
```

## After Sync - Next Steps

**If rebase was used (history rewritten)**:
```
✅ Sync completed!

⚠️  History was rewritten. To push:
  git push --force-with-lease

Or use: /git:push (will handle force push safely)

Run tests first: mvn test
```

**If merge was used (no history rewrite)**:
```
✅ Sync completed!

To push:
  /git:push

Run tests first: mvn test
```

**Always suggest**:
- Run tests to ensure sync didn't break anything
- Review changes before pushing
- Check for any merge commit artifacts

## Error Handling

### Invalid Target Branch

```
❌ Target branch 'main' not found

Available branches:
  • master (use: /git:sync master)
  • develop (use: /git:sync develop)

Or specify custom target:
  /git:sync <branch-name>
```

### Network Issues

```
❌ Failed to fetch from remote

Error: Could not resolve host: github.com

Check your internet connection and try again.
```

### Rebase Failed (Can't Resolve)

```
❌ Rebase encountered conflicts that couldn't be auto-resolved

You can:
  1. Resolve conflicts manually and continue:
     • Edit files
     • git add <files>
     • git rebase --continue

  2. Abort rebase and try merge instead:
     • git rebase --abort
     • /git:sync --merge

  3. Ask for help resolving specific conflicts
```
