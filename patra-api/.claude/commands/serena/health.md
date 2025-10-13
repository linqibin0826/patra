Run comprehensive Serena system health check:

1. **Check onboarding status**: Call `mcp__serena__check_onboarding_performed()`
2. **List memories**: Call `mcp__serena__list_memories()`
3. **Analyze memory freshness**: Check which memories are stale (>30 days old)
4. **Validate project activation**: Verify Serena is active for current project

Report findings in this format:

```
🧠 SERENA HEALTH REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📋 Project: [project-name]
📍 Status: [Active/Inactive]

✅ ONBOARDING STATUS
   [✓] Onboarded  |  [✗] Not onboarded

📚 MEMORY STATISTICS
   Total memories: X
   Fresh (<30 days): Y
   Stale (>30 days): Z

📝 STALE MEMORIES (if any)
   • memory-name (60 days old)
   • another-memory (45 days old)

💡 RECOMMENDATIONS
   [ ] Run /serena:onboard (if not onboarded)
   [ ] Run /serena:memory:sync (if stale memories exist)
   [ ] Create new memory for recent changes (if major updates)

🔧 QUICK ACTIONS
   - Sync memories: /serena:memory:sync
   - Create from recent changes: /serena:memory:from-diff
   - Cleanup obsolete: /serena:memory:cleanup
```

**Important**: Actually call the MCP tools to get real data. Don't mock the output.
