# Serena Quick Start Guide

Quick reference for Serena commands and MCP tools in Papertrace project.

## 🔧 Slash Commands

### Daily Use
```bash
/serena:health              # Check Serena system status
```

### Maintenance (Monthly)
```bash
/serena:memory:sync         # Update stale memories
/serena:memory:cleanup      # Delete obsolete memories
```

### Development (Per Feature)
```bash
/serena:memory:from-diff    # Document changes from git diff
```

## 📋 Common Workflows

### Starting a New Session
```bash
1. /serena:health → Check system status
2. Start coding
```

### After Completing a Feature
```bash
1. git diff main...HEAD → Review changes
2. /serena:memory:from-diff → Document feature
3. Review & approve memory
4. Continue with PR
```

### Monthly Maintenance
```bash
1. /serena:health → Identify issues
2. /serena:memory:sync → Update stale
3. /serena:memory:cleanup → Remove obsolete
4. /serena:health → Verify
```

## 💡 Tips

**Token Efficiency**:
- Use `mcp__serena__get_symbols_overview` first to get file overview
- Then `mcp__serena__find_symbol` for specific classes/methods
- Avoid reading entire files when possible

**Memory Management**:
- Create memories for features: `/serena:memory:from-diff`
- Update monthly: `/serena:memory:sync`
- Clean quarterly: `/serena:memory:cleanup`

**MCP Tools**:
- Use Serena's symbolic tools for code navigation
- `find_symbol` and `find_referencing_symbols` help understand code structure
- Symbol-based editing is more precise than full file edits

## 📚 Full Documentation

- **Commands**: `.claude/commands/serena/README.md`
- **MCP Tools**: `.claude/AGENTS-mcp-tools.md`

## 🚀 Quick Actions

```bash
# Manual Serena health check (via MCP tools)
# Use: mcp__serena__check_onboarding_performed()
#      mcp__serena__list_memories()

# List available memories
# /serena:health command shows memory status
```

## 🔗 Related Files

```
.claude/
└── commands/
    └── serena/
        ├── README.md                      # Command documentation
        ├── health.md                      # /serena:health
        ├── memory-sync.md                 # /serena:memory:sync
        ├── memory-from-diff.md            # /serena:memory:from-diff
        └── memory-cleanup.md              # /serena:memory:cleanup
```

---

**Remember**: Commands run on-demand to check status and manage memories!
