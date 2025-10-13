# Serena Slash Commands

This directory contains slash commands for managing and maintaining Serena (Semantic Code Navigator) in the Papertrace project.

## Available Commands

### `/serena:health` - System Health Check

**Purpose**: Comprehensive health check of Serena system

**What It Does**:
- Checks project onboarding status
- Lists all memories
- Analyzes memory freshness (identifies stale >30 days)
- Provides actionable recommendations

**When to Use**:
- At start of new session
- After major refactoring
- Monthly maintenance check
- Before creating new memories

**Example Output**:
```
🧠 SERENA HEALTH REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📋 Project: Papertrace-api
📍 Status: Active

✅ ONBOARDING STATUS
   [✓] Onboarded

📚 MEMORY STATISTICS
   Total memories: 12
   Fresh (<30 days): 8
   Stale (>30 days): 4

📝 STALE MEMORIES
   • architecture-overview (45 days old)
   • domain-models (60 days old)

💡 RECOMMENDATIONS
   [ ] Run /serena:memory:sync (4 stale memories)
   [ ] Create memory for recent PubMed batch feature
```

---

### `/serena:memory:sync` - Update Stale Memories

**Purpose**: Review and update outdated memories

**What It Does**:
- Identifies memories older than 30 days
- For each stale memory:
  - Shows current content summary
  - Asks for user confirmation
  - Explores codebase using Serena tools
  - Updates memory with current information

**When to Use**:
- After `/serena:health` reports stale memories
- After major refactoring
- Quarterly maintenance
- Before important releases

**User Interaction**:
- Interactive: Asks for confirmation before each update
- Shows what will be updated
- Allows skipping memories that are still valid

**Example Session**:
```
🔄 SERENA MEMORY SYNC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Found 2 stale memories (>30 days old)

Processing: architecture-overview (45 days old)
  Current: Documents 8 modules, hexagonal architecture
  ❓ Update this memory? (y/n)

[User: y]

  🔍 Exploring codebase...
  ✓ Found new module: patra-ingest-batch
  ✓ Updated module count: 9
  💾 Memory updated

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 SYNC SUMMARY
   ✓ Updated: 1 memory
   ⊘ Skipped: 1 memory
```

---

### `/serena:memory:from-diff` - Create Memory from Git Changes

**Purpose**: Document recent code changes as Serena memory

**What It Does**:
- Analyzes `git diff main...HEAD`
- Categorizes changes (features, refactors, architecture)
- Uses Serena to explore significant changes
- Proposes memory name and structured content
- Creates memory after user approval

**When to Use**:
- After completing a feature
- After major refactoring
- Before creating pull request
- To document architectural decisions

**Memory Types Created**:
- `feature-{name}` - New features
- `refactor-{area}` - Refactorings
- `arch-{decision}` - Architecture changes

**Example Session**:
```
📝 CREATE MEMORY FROM GIT DIFF
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 Analyzing changes: main...HEAD

📊 Changes Summary:
   Files changed: 15
   Insertions: +450
   Deletions: -120

📦 Significant Changes:
   ✓ New domain aggregate: BatchPlan
   ✓ New orchestrator: BatchPlanningOrchestrator
   ✓ New ports: PubmedSearchPort, BatchPlanRepository

💡 Proposed Memory:
   Name: feature-pubmed-batch-planning

   Content Preview:
   ─────────────────────────────────────
   # PubMed Batch Planning Feature

   ## Overview
   Implements batch planning for PubMed
   literature collection with pagination...

   ## Key Classes
   - BatchPlan: Domain aggregate
   - BatchPlanningOrchestrator: Use case
   ─────────────────────────────────────

❓ Create this memory? (y/n/edit)
```

**Benefits**:
- Captures architectural decisions while fresh
- Documents feature implementation details
- Helps future developers understand changes
- Improves knowledge transfer

---

### `/serena:memory:cleanup` - Delete Obsolete Memories

**Purpose**: Remove memories for deleted/refactored code

**What It Does**:
- Analyzes `git diff main...HEAD` for deletions
- Lists all memories
- For each memory:
  - Extracts mentioned files/classes
  - Checks if they still exist using Serena
  - Determines if memory is obsolete
- Proposes deletions with reasons
- Deletes after user confirmation

**When to Use**:
- After major refactoring
- After deleting modules/features
- Quarterly cleanup
- After `/serena:health` shows many old memories

**Safety Features**:
- Never auto-deletes without confirmation
- Shows why memory is considered obsolete
- Allows "skip" for uncertain cases
- Offers "update instead of delete" option

**Example Session**:
```
🗑️  SERENA MEMORY CLEANUP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 Analyzing changes: main...HEAD

📊 Git Changes:
   Deleted files: 5
   Refactored modules: 2

🗂️  Checking 12 memories for obsolescence...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔴 OBSOLETE MEMORY FOUND
   Name: old-batch-processor
   Age: 120 days

   Reason: Documents BatchProcessor class
   Status: ✗ Class deleted in commit abc123f

   Memory references:
   • BatchProcessor.java (DELETED)
   • OldBatchService.java (DELETED)

   ❓ Delete this memory? (y/n)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📊 CLEANUP SUMMARY
   🗑️  Deleted: 1 memory
   ✅ Kept: 11 memories
```

---

## Workflow Examples

### Weekly Maintenance
```bash
1. Start session → SessionStart hook auto-activates Serena
2. Run: /serena:health
3. If stale memories: /serena:memory:sync
```

### After Feature Implementation
```bash
1. Complete feature development
2. Run: /serena:memory:from-diff
3. Review and approve memory creation
4. Commit code + memory is automatically created
```

### Monthly Cleanup
```bash
1. Run: /serena:health
2. Run: /serena:memory:cleanup (delete obsolete)
3. Run: /serena:memory:sync (update stale)
4. Run: /serena:health (verify)
```

### Before Pull Request
```bash
1. Run: /serena:memory:from-diff
   → Document what changed
2. Use memory content for PR description
3. Team has context for review
```

---

## Command Reference

| Command | Purpose | Interactive | Git-Aware |
|---------|---------|-------------|-----------|
| `/serena:health` | System health check | No | No |
| `/serena:memory:sync` | Update stale memories | Yes | No |
| `/serena:memory:from-diff` | Create from changes | Yes | Yes |
| `/serena:memory:cleanup` | Delete obsolete | Yes | Yes |

---

## Integration with Hooks

These commands work seamlessly with hooks:

**SessionStart Hook** → Auto-activates Serena
↓
**You run**: `/serena:health` → Check status
↓
**Read Hook** → Warns on large files, suggests Serena
↓
**You run**: `/serena:memory:from-diff` → Document work
↓
**Edit/Write Hook** → Validates architecture

---

## Best Practices

1. **Health Check First**: Always run `/serena:health` at session start
2. **Update Regularly**: Run `/serena:memory:sync` monthly
3. **Document Features**: Use `/serena:memory:from-diff` for all features
4. **Cleanup After Refactors**: Run `/serena:memory:cleanup` after major changes
5. **Review Before Delete**: Always review what cleanup suggests
6. **Edit Before Save**: Use "edit" option in `from-diff` to refine content

---

## Troubleshooting

**Memory creation fails**:
- Check Serena onboarding: `/serena:health`
- Verify git diff has changes: `git diff main...HEAD`

**No stale memories detected**:
- Normal if project is new or well-maintained
- Manually review with: `list_memories`

**Cleanup suggests wrong memories**:
- Use "skip" option
- Memories may reference moved (not deleted) code
- Better to keep than mistakenly delete

---

## Resources

- [Serena MCP Tools](../../AGENTS-mcp-tools.md)
- [Claude Code Hooks](../hooks/README.md)
- [Serena Documentation](https://github.com/serena-ai/serena)
