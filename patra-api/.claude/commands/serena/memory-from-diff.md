Create new Serena memory documenting recent code changes based on git diff.

**Purpose**: Capture architectural decisions, new features, or refactorings in Serena memory for future reference.

**Process:**

1. **Analyze git changes**:
   ```bash
   git diff main...HEAD --stat
   git diff main...HEAD --name-only
   git log main...HEAD --oneline
   ```

2. **Categorize changes**:
   - New modules/features
   - Refactorings
   - Architecture changes
   - Bug fixes
   - Documentation updates

3. **Explore significant changes** using Serena:
   - For new files: `mcp__serena__get_symbols_overview(file)`
   - For new classes: `mcp__serena__find_symbol(class_name)`
   - For modifications: `mcp__serena__find_referencing_symbols()` to understand impact

4. **Propose memory name and content**:
   - Memory name: `feature-{feature-name}` or `refactor-{area}` or `arch-{decision}`
   - Content: Structured documentation

5. **Ask user for approval**: Show proposed memory, allow editing

6. **Create memory**: `mcp__serena__write_memory(memory_name, content)`

**Memory Content Template:**

```markdown
# [Feature/Refactor/Decision Name]

## Overview
Brief description of the change and its purpose

## Changes Made
- New classes/modules added
- Modified components
- Deleted/deprecated items

## Architecture Impact
How this affects the overall architecture:
- Layer changes (Domain/App/Infra/Adapter)
- New patterns introduced
- Dependencies affected

## Key Classes/Interfaces
- ClassName (purpose, location)
- InterfaceName (contract, implementations)

## Related Memories
- Link to related existing memories

## Notes
- Design rationale
- Trade-offs considered
- Future considerations

## Commit Range
[commit-hash-range]
```

**Examples:**

```bash
# After implementing PubMed batch planning
# git diff shows: BatchPlan.java, BatchPlanningOrchestrator.java, etc.
# Memory: feature-pubmed-batch-planning

# After refactoring repository layer
# git diff shows: multiple *RepositoryImpl.java changes
# Memory: refactor-repository-layer-mapstruct

# After adding new hexagonal layer enforcement
# git diff shows: ArchUnit tests, git hooks
# Memory: arch-layer-validation-enforcement
```

**Output Format:**

```
📝 CREATE MEMORY FROM GIT DIFF
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 Analyzing changes: main...HEAD

📊 Changes Summary:
   Files changed: 15
   Insertions: +450
   Deletions: -120

📦 Significant Changes:
   ✓ New feature: PubMed batch planning
   ✓ New domain aggregate: BatchPlan
   ✓ New orchestrator: BatchPlanningOrchestrator
   ✓ New ports: PubmedSearchPort, BatchPlanRepository

💡 Proposed Memory:
   Name: feature-pubmed-batch-planning

   Content Preview:
   ─────────────────────────────────────
   # PubMed Batch Planning Feature

   ## Overview
   Implements batch planning for PubMed literature
   collection with pagination support...
   ─────────────────────────────────────

❓ Create this memory? (y/n/edit)

[After confirmation]
✅ Memory created: feature-pubmed-batch-planning

Next: Run /serena:health to verify
```

**Important**:
- Focus on meaningful changes (not minor fixes)
- Use Serena tools to explore new/modified code
- Suggest descriptive memory names
- Allow user to edit before saving
