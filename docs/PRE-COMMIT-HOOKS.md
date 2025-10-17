# Pre-commit Hooks Documentation

Comprehensive guide to Papertrace's pre-commit hook system for maintaining code quality, architecture compliance, and development velocity.

---

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
- [Hook Stages](#hook-stages)
- [Features](#features)
- [Skip Flags](#skip-flags)
- [Performance Features](#performance-features)
- [Troubleshooting](#troubleshooting)

---

## Overview

Papertrace uses **pre-commit framework** to enforce code quality gates at three stages:

1. **pre-commit**: Fast feedback before committing (compile, format, architecture validation)
2. **commit-msg**: Validate commit message format (Conventional Commits)
3. **pre-push**: Comprehensive checks before pushing (tests, static analysis, optional coverage)

**Design Philosophy:**
- ⚡ **Fast commit-time checks** - Only compile and validate architecture
- 🔍 **Comprehensive push-time checks** - Full test suite, static analysis
- 🎯 **Smart module detection** - Only check changed modules, not entire codebase
- 💾 **Intelligent caching** - Skip redundant operations when files unchanged
- 🚀 **Parallel processing** - Speed up multi-module changes
- 🔓 **Flexible skip flags** - Developer control for fast iteration

---

## Installation

### First-Time Setup

```bash
# Install pre-commit framework (if not already installed)
pip install pre-commit

# Install hooks (run from project root)
pre-commit install --hook-type pre-commit --hook-type commit-msg --hook-type pre-push

# Verify installation
pre-commit run --all-files  # This will take time on first run
```

### Update Hooks

```bash
# After pulling changes to hook scripts
pre-commit install --hook-type pre-commit --hook-type commit-msg --hook-type pre-push --force

# Clear cache after Maven plugin updates
rm -rf .git/hooks/cache
```

---

## Hook Stages

### Stage 1: Pre-commit (Fast)

**Triggers**: When you run `git commit`

**Checks** (runs in parallel):
1. **File hygiene** (check-merge-conflict, check-yaml, etc.)
2. **Spelling** (codespell on docs/comments)
3. **Secret scanning** (gitleaks)
4. **Auto-format Java** (fmt-maven-plugin)
5. **Compile changed modules** (Maven compile)
6. **ArchUnit validation** (hexagonal architecture rules)

**Typical duration**: 5-15 seconds for small changes

### Stage 2: Commit-msg

**Triggers**: After commit message is written

**Checks**:
- **Conventional Commits format** validation

**Format**:
```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Valid types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`

**Examples**:
```
feat(ingest): add PubMed batch planning orchestrator
fix(registry): correct null pointer in Provenance lookup
docs: update architecture guidelines
refactor(app): extract validation logic to domain
test(domain): add unit tests for BatchPlan aggregate
```

### Stage 3: Pre-push (Comprehensive)

**Triggers**: When you run `git push`

**Checks** (runs sequentially):
1. **Formatting compliance** (fmt:check)
2. **Unit tests** (changed modules)
3. **SpotBugs static analysis** (bugs, security, code quality)
4. **Coverage thresholds** (optional, set `CHECK_COVERAGE=1`)

**Typical duration**: 30-120 seconds depending on changes

---

## Features

### 1. ArchUnit - Architectural Rules Enforcement

**What it does**: Validates hexagonal architecture and DDD patterns

**Rules enforced**:
- ✅ Domain layer has NO Spring/framework dependencies (Pure Java only)
- ✅ Dependency direction: Adapter → App → Domain ← Infra
- ✅ Naming conventions: `*Orchestrator`, `*Port`, `*RepositoryImpl`, `*DO`
- ✅ No circular dependencies between packages
- ✅ Controllers/Jobs in Adapter layer only
- ✅ No business logic in Adapter layer

**Example failure**:
```
❌ Rule violated: Domain layer must NOT depend on Spring Framework
    com.patra.ingest.domain.SomeClass depends on org.springframework.stereotype.Service
```

**Skip**: `SKIP_ARCHUNIT=1 git commit -m "..."`

---

<a id="skip-flags"></a>
### 2. Skip Flags - Developer Flexibility

**Environment variables** to bypass checks during fast iteration:

| Flag | Effect | Use Case |
|------|--------|----------|
| `SKIP_HOOKS=1` | Skip ALL hooks | Emergency hotfixes |
| `SKIP_COMPILE=1` | Skip compilation | Already compiled locally |
| `SKIP_FORMAT=1` | Skip formatting | Manual formatting |
| `SKIP_ARCHUNIT=1` | Skip ArchUnit | Refactoring in progress |
| `SKIP_TESTS=1` | Skip tests | Already tested locally |
| `SKIP_SPOTBUGS=1` | Skip SpotBugs | Known false positives |
| `SKIP_JACOCO=1` | Skip coverage check | When `CHECK_COVERAGE=1` |
| `SKIP_COMMIT_MSG=1` | Skip commit msg validation | WIP commits |

**Usage examples**:
```bash
# Quick WIP commit during development
SKIP_COMPILE=1 git commit -m "wip: experimenting with approach"

# Push without running tests (already ran locally)
SKIP_TESTS=1 git push

# Skip all hooks (equivalent to --no-verify)
SKIP_HOOKS=1 git commit -m "hotfix: critical production bug"
git commit --no-verify -m "hotfix: critical production bug"
```

---

### 3. Commit Message Validation (Conventional Commits)

**Why**: Enables automated changelog, semantic versioning, and searchable history

**Format**:
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Validation rules**:
- ✅ Type must be one of: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert
- ✅ Scope is optional but recommended (module or component name)
- ✅ Subject must be present
- ⚠️  Warning if subject > 72 chars (soft limit)
- 💡 Suggestion to use imperative mood

**Auto-skipped**: Merge commits, revert commits, fixup/squash commits

---

### 4. SpotBugs - Static Bug Detection

**What it detects**:
- 🐛 Null pointer dereferences
- 💧 Resource leaks (unclosed streams, connections)
- 🧵 Concurrency issues (thread safety)
- 🔒 Security vulnerabilities (SQL injection, XSS, hardcoded credentials)
- ⚖️ Incorrect implementations (equals/hashCode, compareTo)
- 🎯 Performance issues

**Configuration**: `spotbugs-exclude.xml` (filters false positives)

**Reports**: `target/spotbugs/*.html` (detailed HTML reports)

**Threshold**: Medium and High severity bugs

---

### 5. JaCoCo - Test Coverage Thresholds

**Layer-specific thresholds** (matching hexagonal architecture):
- **domain**: 85% line coverage (core business logic - highest)
- **app**: 75% line coverage (orchestration - high)
- **infra**: 70% line coverage (infrastructure - moderate)
- **adapter**: 60% line coverage (adapters - lower, integration-heavy)

**Status**: **Opt-in** (disabled by default to avoid slowing down pushes)

**Enable**:
```bash
CHECK_COVERAGE=1 git push
```

**Reports**: `target/site/jacoco/index.html` (per module)

**Find reports**:
```bash
find . -name 'index.html' -path '*/jacoco/index.html'
```

---

### 6. Parallel Module Processing

**Automatically activates** when > 10 files changed

**Benefits**:
- 30-50% faster module detection for large changesets
- Utilizes all CPU cores (`-P` flag to xargs)
- Transparent - no configuration needed

**Fallback**: Serial processing for small changesets (< 10 files)

---

### 7. Incremental Check Caching

**What it caches**: Results of compile, format, test, archunit, spotbugs checks

**Cache key**: SHA256 hash of file contents + pom.xml mtime

**Benefits**:
- 2-5x faster for repeated commits with same files
- Automatic cache invalidation when files change
- Separate cache per check type

**Location**: `.git/hooks/cache/`

**Clear cache**:
```bash
rm -rf .git/hooks/cache
# Or use the utility function:
source scripts/git/lib/maven_utils.sh && clear_cache
```

---

## Performance Features

### Smart Module Detection

**Only checks changed modules**, not entire codebase:

```bash
# Example: Change 2 files in patra-ingest/patra-ingest-domain
# Detects: patra-ingest-domain module
# Runs:    mvn -pl patra-ingest-domain -am compile
# Skips:   All other modules (patra-registry, patra-common, etc.)
```

### Parallel Maven Builds

All Maven commands use `-T1C` flag (1 thread per CPU core):

```bash
./mvnw -q -T1C -pl patra-ingest-domain -am compile
```

**Typical speedup**: 2-4x on multi-core machines

### Caching Strategy

**Cache hit scenario**:
```bash
# First commit: Compile takes 8 seconds
git commit -m "feat: add new feature"

# Second commit (no code changes, only docs): Compile takes 0.1 seconds
git commit --amend -m "feat: add new feature (updated docs)"
```

---

## Troubleshooting

### Hook Not Running

**Symptom**: Commits succeed without running checks

**Solution**:
```bash
# Reinstall hooks
pre-commit install --hook-type pre-commit --hook-type commit-msg --hook-type pre-push --force

# Verify pre-commit is installed
which pre-commit

# Check .git/hooks/ directory
ls -la .git/hooks/
```

### ArchUnit Test Failures

**Symptom**: ArchUnit reports architecture violations

**Solution**:
1. Review the violation message carefully
2. Check `.claude/AGENTS-architecture.md` for rules
3. Refactor code to comply with hexagonal architecture
4. If rule is incorrect, update `ArchitectureTest.java`

**Common violations**:
- Domain layer depends on Spring: Remove `@Service`, `@Component`, `@Autowired`
- Infra called from Adapter: Use Orchestrator as intermediary
- Incorrect naming: Rename to match conventions

### SpotBugs False Positives

**Symptom**: SpotBugs reports bugs that are intentional or safe

**Solution**:
1. Review bug report in `target/spotbugs/*.html`
2. If false positive, add exclusion to `spotbugs-exclude.xml`
3. Commit the updated filter

**Example exclusion**:
```xml
<Match>
  <Class name="com.patra.ingest.infra.SomeClass"/>
  <Method name="someMethod"/>
  <Bug pattern="NP_NULL_ON_SOME_PATH"/>
</Match>
```

### Slow Hook Execution

**Symptom**: Hooks take too long to complete

**Solutions**:
1. **Enable caching**: Cache should activate automatically
2. **Check cache hits**: Look for `[CACHE HIT]` messages
3. **Use skip flags during iteration**: `SKIP_TESTS=1`, `SKIP_SPOTBUGS=1`
4. **Verify parallel processing**: Look for "Detecting affected modules..." message
5. **Clear cache if stale**: `rm -rf .git/hooks/cache`

### Coverage Check Failures

**Symptom**: `CHECK_COVERAGE=1` fails with low coverage

**Solution**:
1. View detailed report: `find . -name 'index.html' -path '*/jacoco/index.html'`
2. Add missing tests for uncovered code
3. If threshold too strict, discuss with team to adjust in `jacoco_utils.sh`

### Commit Message Rejected

**Symptom**: `❌ Invalid commit message format!`

**Solution**:
1. Follow format: `<type>(<scope>): <subject>`
2. Use valid type: feat, fix, docs, style, refactor, test, chore, perf, ci, build
3. Example: `feat(ingest): add batch planning`

**Quick bypass** (not recommended):
```bash
SKIP_COMMIT_MSG=1 git commit -m "any message"
# or
git commit --no-verify -m "any message"
```

---

## Advanced Usage

### Running Hooks Manually

```bash
# Run all pre-commit hooks on all files
pre-commit run --all-files

# Run specific hook on all files
pre-commit run mvn-archunit-changed-modules --all-files

# Run specific hook on staged files only
pre-commit run mvn-compile-changed-modules

# Test commit message validation
echo "feat(test): example message" | pre-commit run commit-msg-validation --hook-stage commit-msg
```

### Debugging Hooks

```bash
# Enable verbose output (already enabled in config)
pre-commit run --verbose mvn-compile-changed-modules

# Check hook script directly
bash -x scripts/git/mvn_compile_changed_modules.sh

# View cache contents
ls -lh .git/hooks/cache/
```

### Custom Configuration

**Adjust coverage thresholds**:
Edit `scripts/git/lib/jacoco_utils.sh`:
```bash
declare -A COVERAGE_THRESHOLDS=(
  ["domain"]=90    # Increase domain threshold to 90%
  ["app"]=80       # Increase app threshold to 80%
  # ...
)
```

**Add custom ArchUnit rules**:
Edit `patra-ingest/patra-ingest-boot/src/test/java/com/patra/ingest/architecture/ArchitectureTest.java`

**Customize SpotBugs exclusions**:
Edit `spotbugs-exclude.xml`

---

## CI/CD Integration

Pre-commit hooks complement (not replace) CI/CD pipelines:

**Local (pre-commit)**: Fast feedback, changed modules only
**CI/CD**: Full build, all modules, integration tests, deployment

**Recommended CI pipeline**:
```yaml
- mvn clean install           # Full build
- mvn spotbugs:check           # All modules
- mvn jacoco:check             # Enforce coverage
- Run integration tests
- Deploy artifacts
```

---

## FAQ

**Q: Can I skip hooks for emergency hotfixes?**
A: Yes, use `git commit --no-verify` or `SKIP_HOOKS=1`

**Q: Why is ArchUnit running on commit, not push?**
A: Architecture violations are critical - catch them early before they spread

**Q: Can I disable specific hooks permanently?**
A: Yes, comment out the hook in `.pre-commit-config.yaml` (not recommended)

**Q: How do I update hooks after pulling changes?**
A: Run `pre-commit install --force` and clear cache

**Q: Why isn't JaCoCo coverage enabled by default?**
A: To keep push-time fast. Enable with `CHECK_COVERAGE=1` when needed

**Q: Can I run hooks in CI/CD?**
A: Yes, but recommend using Maven directly for full control: `mvn verify`

---

## References

- **pre-commit framework**: https://pre-commit.com/
- **Conventional Commits**: https://www.conventionalcommits.org/
- **ArchUnit**: https://www.archunit.org/
- **SpotBugs**: https://spotbugs.github.io/
- **JaCoCo**: https://www.jacoco.org/
- **Project architecture**: `.claude/AGENTS-architecture.md`
