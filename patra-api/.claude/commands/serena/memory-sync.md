Update stale Serena memories for current project.

**Process:**

1. **List all memories**: `mcp__serena__list_memories()`
2. **Identify stale memories**: Find memories older than 30 days
3. **For each stale memory**:
   - Read content: `mcp__serena__read_memory(memory_name)`
   - **Ask user**: "Memory '[name]' is X days old. Review and update? (y/n)"
   - If yes:
     - Explore codebase to verify/update information
     - Use Serena tools: `find_symbol`, `search_for_pattern`, `get_symbols_overview`
     - Rewrite: `mcp__serena__write_memory(memory_name, updated_content)`
4. **Report results**

**User Interaction:**
- Ask for confirmation before updating each memory
- Show diff of changes when possible
- Allow skipping memories that are still valid

**Output Format:**

```
🔄 SERENA MEMORY SYNC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Found X stale memories (>30 days old)

Processing: architecture-overview (45 days old)
  Current: [summary of current content]
  ❓ Update this memory? (y/n)

[After user confirms]
  🔍 Exploring codebase...
  ✓ Verified module structure
  ✓ Added new modules: patra-ingest-batch
  ✓ Updated dependency info
  💾 Memory updated

Processing: domain-models (60 days old)
  Current: [summary]
  ❓ Update this memory? (y/n)

[After user confirms]
  🔍 Checking domain layer...
  ✓ New aggregate: BatchPlan
  ✓ Updated: Provenance model
  💾 Memory updated

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 SYNC SUMMARY
   ✓ Updated: 2 memories
   ⊘ Skipped: 0 memories
   ✗ Failed: 0 memories

Next: Run /serena:health to verify
```

**Important**:
- Always ask user before updating each memory
- Use Serena tools efficiently to gather update information
- Don't read entire files - use symbolic navigation
