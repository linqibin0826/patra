Delete obsolete Serena memories based on git diff analysis.

**Purpose**: Clean up memories that document code/features that have been deleted or significantly changed.

**Process:**

1. **Analyze git changes**:
   ```bash
   git diff main...HEAD --name-only --diff-filter=D  # Deleted files
   git diff main...HEAD --name-only --diff-filter=M  # Modified files
   git log main...HEAD --oneline --all              # Recent commits
   ```

2. **List all memories**: `mcp__serena__list_memories()`

3. **For each memory**:
   - Read content: `mcp__serena__read_memory(memory_name)`
   - Extract mentioned files/classes/modules
   - Check if those files still exist or were significantly changed
   - Use `mcp__serena__find_symbol()` to verify classes still exist
   - Use `mcp__serena__search_for_pattern()` to check if patterns still apply

4. **Identify obsolete memories**:
   - Memory documents deleted files/modules
   - Memory documents refactored code (old structure no longer exists)
   - Memory documents deprecated features

5. **Propose deletions**:
   - Show memory name, age, and why it's obsolete
   - Ask user for confirmation

6. **Delete confirmed memories**: `mcp__serena__delete_memory(memory_name)`

**Detection Heuristics:**

| Scenario | Detection Method |
|----------|------------------|
| Deleted module | Memory mentions `patra-xyz` module that doesn't exist |
| Refactored classes | Memory documents `OldClassName` that's been renamed/removed |
| Deprecated features | Commit messages show "remove", "deprecate" related to memory topic |
| Architecture changes | Memory describes old layer structure that changed |

**Output Format:**

```
🗑️  SERENA MEMORY CLEANUP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 Analyzing changes: main...HEAD

📊 Git Changes:
   Deleted files: 5
   Refactored modules: 2
   Commits analyzed: 12

🗂️  Checking X memories for obsolescence...

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

🟡 POTENTIALLY OBSOLETE
   Name: legacy-api-integration
   Age: 90 days

   Reason: Documents LegacyApiClient
   Status: ⚠️  Class still exists but heavily refactored

   Recent commits:
   • "refactor: modernize API client" (3 days ago)
   • "feat: replace legacy client" (5 days ago)

   ❓ Delete or update this memory? (d/u/skip)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ UP-TO-DATE MEMORY
   Name: domain-models-overview
   Status: All referenced classes exist
   Action: Keep

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📊 CLEANUP SUMMARY
   🗑️  Deleted: 1 memory
   🔄 Updated: 1 memory
   ✅ Kept: 8 memories
   ⊘ Skipped: 0 memories

💡 Recommendation: Run /serena:memory:from-diff to document recent changes

Next: Run /serena:health to verify
```

**Safety Features:**

- Always ask for confirmation before deletion
- Show why memory is considered obsolete
- Allow "skip" option for uncertain cases
- Offer "update instead of delete" for partially obsolete memories
- Never auto-delete without user approval

**Important**:
- Use Serena tools to verify symbol existence
- Check git history to understand deletions
- Be conservative - when in doubt, keep the memory
- Suggest updates instead of deletion when appropriate
