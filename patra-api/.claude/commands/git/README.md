# Git Workflow Commands

> **Automated Git operations** with AI-generated commit messages, PR descriptions, and intelligent decision-making.

---

## 📋 Overview

This command system provides **5 slash commands** for automated Git workflow:

**Key Features**:
- 🤖 **Auto-generated content** (commit messages, PR titles/bodies)
- 🎯 **Intelligent decisions** (skip hooks for docs, choose merge strategy)
- ⚡ **Minimal user input** (all parameters optional)
- ✅ **Safe defaults** (runs hooks by default, can override)
- 📊 **Clear reporting** (shows what was generated and done)

---

## 🚀 Quick Start

### Typical Workflow

```bash
# 1. Check what changed
/git:status

# 2. Commit with auto-generated message
/git:commit

# Output:
# Analyzing changes...
#   • 5 files in patra-ingest-domain
#   • 2 files in patra-ingest-app
#
# Generated: feat(ingest): add batch planning aggregate and orchestrator
# ✅ Committed successfully

# 3. Push with all checks
/git:push

# Output:
# Running pre-push checks...
#   ✅ Tests passed
#   ✅ SpotBugs passed
#
# Pushing to origin/feat/pubmed-batch-planner...
# ✅ Pushed successfully

# 4. Create PR with auto-generated content
/git:pr

# Output:
# Generated PR:
#   Title: "feat(ingest): implement PubMed batch planning system"
#   Body: [auto-generated summary + test plan]
#
# ✅ PR created: https://github.com/user/repo/pull/123
```

---

## 📦 Commands Reference

### `/git:commit [message]`

**Purpose**: Auto-commit with AI-generated Conventional Commit message

**Auto-generates**:
- Commit type (feat/fix/docs/refactor/etc.)
- Scope (affected module)
- Subject (clear, concise description)

**Usage**:
```bash
# Auto-generate message from diff
/git:commit

# Override with custom message
/git:commit "feat(ingest): custom message"
```

**How it works**:
1. Analyzes `git diff --cached` (staged changes)
2. Detects affected modules (patra-ingest, patra-registry, etc.)
3. Classifies change type (new feature, bug fix, refactor, etc.)
4. Generates Conventional Commit format message
5. Runs pre-commit hooks (compile + ArchUnit)
6. Creates commit

---

### `/git:push [--skip-hooks]`

**Purpose**: Push with pre-push checks (tests + SpotBugs)

**Auto-decides**:
- Whether hooks can be safely skipped (docs-only changes)
- Which checks to run based on changed files

**Usage**:
```bash
# Run all checks (default)
/git:push

# Skip hooks explicitly
/git:push --skip-hooks
```

**How it works**:
1. Checks branch status (ahead/behind upstream)
2. Runs pre-push hooks (tests, SpotBugs)
3. If hooks fail, suggests fixes or skip flags
4. Pushes to remote tracking branch

---

### `/git:pr [title]`

**Purpose**: Create GitHub PR with auto-generated title and description

**Auto-generates**:
- PR title from commit history and scope
- PR body with:
  - Summary of changes (by module/layer)
  - Test plan checklist
  - Breaking changes (if any)

**Usage**:
```bash
# Auto-generate title and body
/git:pr

# Override title (body still auto-generated)
/git:pr "Custom PR Title"
```

**How it works**:
1. Analyzes commits since branching from main
2. Analyzes `git diff main...HEAD` for full context
3. Groups changes by module and layer
4. Generates comprehensive PR description
5. Runs all checks (commit + push)
6. Creates PR via `gh pr create`
7. Returns PR URL

---

### `/git:status`

**Purpose**: Show Git status with module breakdown and next actions

**Shows**:
- Changed files grouped by module
- Commit status (ahead/behind upstream)
- Staged vs unstaged changes
- Suggested next command

**Usage**:
```bash
/git:status
```

**Example output**:
```
Branch: feat/pubmed-batch-planner (3 commits ahead of main)

Changed modules:
  patra-ingest-domain:
    • BatchPlan.java (modified)
    • Task.java (new)
  patra-ingest-app:
    • PlanOrchestrator.java (modified)

Staged: 7 files
Unstaged: 2 files

Next: /git:commit (to commit changes)
```

---

### `/git:sync [--rebase|--merge]`

**Purpose**: Sync branch with upstream (main)

**Auto-decides**:
- Rebase vs merge based on branch state
- Whether sync is safe (no conflicts expected)

**Usage**:
```bash
# Auto-decide strategy
/git:sync

# Force rebase
/git:sync --rebase

# Force merge
/git:sync --merge
```

**How it works**:
1. Fetches latest from origin/main
2. Analyzes branch divergence
3. Chooses rebase (clean history) or merge (safer for pushed branches)
4. Performs sync
5. Reports conflicts if any

---

## 🎯 Design Principles

### 1. Auto-Generate Everything

**Claude analyzes** your changes and generates:
- Commit messages with proper type/scope
- PR titles that summarize the work
- PR descriptions with context and test plans

**User input is optional** - only override when you want to customize

### 2. Intelligent Decisions

**Claude decides**:
- When hooks can be safely skipped (docs-only changes)
- Which merge strategy to use (rebase for clean history)
- What checks to run based on changed files

**User flags are optional** - only override when needed

### 3. Safe Defaults

**Default behavior**:
- ✅ Runs all hooks (compile, tests, SpotBugs)
- ✅ Stages all changes before commit
- ✅ Validates Conventional Commit format
- ✅ Runs pre-push checks before pushing

**Override only when needed** (--skip-hooks, custom messages)

---

## 🔄 Recommended Workflows

### Workflow 1: Feature Development

```bash
# During development
/git:status                    # Check progress

# When feature is done
/git:commit                    # Auto-commit with generated message
/git:push                      # Run all checks and push
/git:pr                        # Create PR with auto-generated content
```

### Workflow 2: Quick Fix

```bash
# Fix the bug
/git:commit "fix(registry): null pointer in Provenance lookup"
/git:push --skip-hooks         # Skip if urgent (not recommended)
/git:pr "Fix production bug"   # Custom title for visibility
```

### Workflow 3: Documentation Update

```bash
# Update docs
/git:commit                    # Auto-generates: docs: update architecture guide
/git:push                      # Claude skips tests automatically (docs-only)
# No PR needed, push directly to main
```

### Workflow 4: Sync Before PR

```bash
# Before creating PR
/git:sync                      # Get latest from main
/git:push                      # Push updated branch
/git:pr                        # Create PR
```

---

## 🎯 Best Practices

### DO ✅

1. **Trust auto-generation** - Claude analyzes your code to generate accurate messages
2. **Use `/git:status` frequently** - Understand what will be committed
3. **Let hooks run** - They catch issues early (compile errors, architecture violations)
4. **Review generated content** - Claude shows you what it generated before proceeding
5. **Use `/git:pr` for consistency** - Auto-generated PR descriptions are comprehensive

### DON'T ❌

1. **Don't skip hooks habitually** - Only use `--skip-hooks` for urgent fixes
2. **Don't ignore hook failures** - Fix the issues or ask Claude for help
3. **Don't manually write commit messages** - Let Claude generate them from your changes
4. **Don't force push** - Claude won't do this by default (safe defaults)
5. **Don't bypass PR process** - Even small changes benefit from PR review

---

## 🔧 Integration with Pre-commit Hooks

These commands **leverage your existing Git hooks**:

**Pre-commit** (runs on `/git:commit`):
- ✅ Fast compile of changed modules
- ✅ Auto-format Java with fmt-maven-plugin
- ✅ ArchUnit validation (hexagonal architecture)

**Commit-msg** (runs on `/git:commit`):
- ✅ Conventional Commits format validation

**Pre-push** (runs on `/git:push`):
- ✅ Unit tests for changed modules
- ✅ Formatting compliance check
- ✅ SpotBugs static analysis (optional, currently disabled)
- ✅ Coverage thresholds (opt-in with CHECK_COVERAGE=1)

**Claude respects skip flags** from your hooks:
- `SKIP_COMPILE=1`, `SKIP_TESTS=1`, `SKIP_ARCHUNIT=1`, etc.

---

## 🐛 Troubleshooting

### "Commit message format invalid"

**Cause**: Auto-generated message doesn't match Conventional Commits

**Solution**: Claude should always generate valid format. If it fails, provide custom message:
```bash
/git:commit "feat(module): your message"
```

### "Pre-push hooks failed"

**Cause**: Tests or SpotBugs found issues

**Solution**: Claude will show you the failures and suggest:
- Fix the issues
- Or use skip flags: `/git:push --skip-hooks`

### "No commits to push"

**Cause**: Everything already pushed to remote

**Solution**: Make changes first, or check with `/git:status`

### "gh command not found"

**Cause**: GitHub CLI not installed (needed for `/git:pr`)

**Solution**: Install GitHub CLI:
```bash
# macOS
brew install gh

# Login
gh auth login
```

### "PR creation failed"

**Cause**: Branch not pushed, or no upstream branch

**Solution**: Run `/git:push` first, then `/git:pr`

---

## 📊 Commit Message Format

**Auto-generated format** (Conventional Commits):
```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style (formatting)
- `refactor`: Code refactoring
- `test`: Adding/updating tests
- `chore`: Maintenance (deps, build)
- `perf`: Performance improvement
- `ci`: CI/CD changes
- `build`: Build system changes

**Scopes** (auto-detected from changed modules):
- `ingest`: patra-ingest module
- `registry`: patra-registry module
- `common`: patra-common module
- `gateway`: patra-gateway module
- etc.

**Examples** (auto-generated):
```
feat(ingest): add batch planning aggregate and orchestrator
fix(registry): correct null pointer in Provenance lookup
docs: update hexagonal architecture guidelines
refactor(app): extract validation logic to domain layer
test(domain): add unit tests for BatchPlan aggregate
```

---

## 📝 Command Summary

| Command | Purpose | Input Required |
|---------|---------|----------------|
| `/git:commit` | Auto-commit with generated message | Optional override |
| `/git:push` | Push with checks | Optional --skip-hooks |
| `/git:pr` | Create PR with generated content | Optional title override |
| `/git:status` | Show status and next actions | None |
| `/git:sync` | Sync with upstream | Optional strategy |

---

## 🔗 Related Documentation

- **Pre-commit Hooks**: `docs/PRE-COMMIT-HOOKS.md`
- **Architecture Guidelines**: `.claude/AGENTS-architecture.md`
- **Development Workflow**: `.claude/AGENTS-development.md`
- **Conventional Commits**: https://www.conventionalcommits.org/

---

**Last Updated**: 2025-01-13
**Commands Version**: 1.0.0
**Total Commands**: 5
