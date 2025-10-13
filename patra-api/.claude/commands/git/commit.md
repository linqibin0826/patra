You are about to help the user commit their changes with an auto-generated Conventional Commit message.

## Your Task

1. **Analyze Changes**:
   - Run `git diff --cached --stat` to see staged changes
   - If nothing staged, run `git status` to see what's available
   - If nothing to commit, inform user and stop

2. **Check for User-Provided Message**:
   - Check if user provided a message as argument (e.g., `/git:commit "feat(ingest): message"`)
   - If provided, use it directly and skip to step 5
   - If not provided, proceed to auto-generate

3. **Auto-Generate Commit Message**:

   **Step 3.1: Analyze the diff**
   - Run `git diff --cached` or `git diff HEAD` to see actual changes
   - Identify:
     - Which modules changed (patra-ingest, patra-registry, etc.)
     - Which layers changed (domain, app, infra, adapter, boot)
     - Type of change (new files, modifications, deletions)
     - Nature of change (new feature, bug fix, refactor, docs, test, etc.)

   **Step 3.2: Determine commit type**
   - `feat`: New feature (new classes, methods, functionality)
   - `fix`: Bug fix (fixing broken behavior)
   - `docs`: Documentation only (*.md files, comments)
   - `style`: Code style, formatting (no logic change)
   - `refactor`: Code restructuring (no behavior change)
   - `test`: Adding or updating tests
   - `chore`: Maintenance (deps, build, config)
   - `perf`: Performance improvement
   - `ci`: CI/CD changes
   - `build`: Build system changes

   **Step 3.3: Determine scope**
   - Use module name: `ingest`, `registry`, `common`, `gateway`, etc.
   - If multiple modules, pick the primary one
   - If root changes (pom.xml, docker, scripts), use appropriate scope

   **Step 3.4: Generate subject**
   - Start with verb in imperative mood (add, fix, update, remove, etc.)
   - Be specific but concise (50 chars max)
   - Focus on WHAT changed, not HOW
   - Examples:
     - "add batch planning aggregate and orchestrator"
     - "fix null pointer in Provenance lookup"
     - "update architecture documentation"
     - "refactor validation logic to domain layer"

   **Step 3.5: Format message**
   ```
   <type>(<scope>): <subject>
   ```

   **Examples**:
   ```
   feat(ingest): add batch planning aggregate and orchestrator
   fix(registry): correct null pointer in Provenance lookup
   docs: update hexagonal architecture guidelines
   refactor(app): extract validation logic to domain layer
   test(domain): add unit tests for BatchPlan aggregate
   chore(deps): upgrade Spring Boot to 3.2.5
   ```

4. **Stage Changes** (if not already staged):
   - Run `git add .` to stage all changes
   - This ensures everything is committed together

5. **Show Generated Message to User**:
   - Display the auto-generated (or user-provided) message
   - Show summary of changes (files, modules affected)
   - Ask user to confirm or provide feedback

6. **Run Pre-commit Hooks**:
   - Commit using: `git commit -m "message"`
   - Pre-commit hooks will run automatically:
     - Compile changed modules
     - Auto-format Java code
     - ArchUnit validation
   - If hooks fail:
     - Show the error to user
     - Suggest fixes or skip flags (SKIP_COMPILE=1, SKIP_ARCHUNIT=1)
     - Ask if they want to retry or skip

7. **Report Success**:
   - Show commit hash
   - Show commit message
   - Suggest next action: `/git:push` to push changes

## Important Notes

- **ALWAYS analyze the actual diff** - Don't guess what changed
- **Be specific in commit messages** - Avoid vague subjects like "update code" or "fix issues"
- **Follow Conventional Commits strictly** - Type and format must be correct
- **Respect project architecture** - Understand domain/app/infra/adapter layers
- **Don't skip hooks by default** - Only suggest skipping if user explicitly wants to
- **Stage all changes** - Use `git add .` to ensure nothing is left behind

## Examples of Good Auto-Generated Messages

**Example 1: New Feature**
```
Changes detected:
  • patra-ingest-domain/src/main/java/.../BatchPlan.java (new)
  • patra-ingest-domain/src/main/java/.../Task.java (new)
  • patra-ingest-app/src/main/java/.../PlanOrchestrator.java (new)

Analysis:
  • Type: feat (new classes/functionality)
  • Scope: ingest (primary module)
  • Subject: add batch planning aggregate and orchestrator

Generated message:
  feat(ingest): add batch planning aggregate and orchestrator
```

**Example 2: Bug Fix**
```
Changes detected:
  • patra-registry-infra/src/main/java/.../ProvenanceRepositoryImpl.java (modified)

Analysis:
  • Type: fix (fixing null pointer exception)
  • Scope: registry
  • Subject: correct null pointer in Provenance lookup

Generated message:
  fix(registry): correct null pointer in Provenance lookup
```

**Example 3: Documentation**
```
Changes detected:
  • .claude/AGENTS-architecture.md (modified)
  • docs/DEV-GUIDE.md (modified)

Analysis:
  • Type: docs (only documentation files)
  • Scope: none (not module-specific)
  • Subject: update architecture and development guidelines

Generated message:
  docs: update architecture and development guidelines
```

**Example 4: Refactoring**
```
Changes detected:
  • patra-ingest-domain/src/main/java/.../ValidationService.java (deleted)
  • patra-ingest-domain/src/main/java/.../BatchPlan.java (modified)

Analysis:
  • Type: refactor (moving code, no behavior change)
  • Scope: ingest
  • Subject: extract validation logic into aggregate

Generated message:
  refactor(ingest): extract validation logic into aggregate
```

**Example 5: Tests**
```
Changes detected:
  • patra-ingest-domain/src/test/java/.../BatchPlanTest.java (new)
  • patra-ingest-app/src/test/java/.../PlanOrchestratorTest.java (new)

Analysis:
  • Type: test (test files only)
  • Scope: ingest
  • Subject: add unit tests for domain and app layers

Generated message:
  test(ingest): add unit tests for domain and app layers
```

## Error Handling

**Scenario 1: Nothing to commit**
```
No changes detected to commit.
Run /git:status to see current state.
```

**Scenario 2: Pre-commit hooks fail**
```
❌ Pre-commit hooks failed:
  • Compilation error in patra-ingest-domain/BatchPlan.java

To fix:
  1. Fix the compilation error
  2. Run /git:commit again

To skip hooks (not recommended):
  SKIP_COMPILE=1 git commit -m "your message"
```

**Scenario 3: Invalid message format**
```
❌ Commit message format invalid:
  "Added new feature"

Expected format:
  <type>(<scope>): <subject>

Example:
  feat(ingest): add new feature
```

## Integration with Existing Hooks

This command leverages the project's pre-commit hooks:

**Hooks that will run**:
1. **File hygiene** - Check merge conflicts, YAML, JSON
2. **Spelling** - Codespell on docs/comments
3. **Secret scanning** - Gitleaks
4. **Auto-format** - fmt-maven-plugin
5. **Compile** - Changed modules only
6. **ArchUnit** - Architecture validation
7. **Commit-msg** - Conventional Commits validation

**Skip flags available**:
- `SKIP_COMPILE=1` - Skip compilation
- `SKIP_FORMAT=1` - Skip formatting
- `SKIP_ARCHUNIT=1` - Skip ArchUnit
- `SKIP_HOOKS=1` - Skip all hooks

## Next Steps After Commit

After successful commit, suggest:
```
✅ Committed successfully

Commit: abc1234
Message: feat(ingest): add batch planning aggregate and orchestrator

Next steps:
  • Run /git:push to push changes to remote
  • Or continue working and commit more changes
```
