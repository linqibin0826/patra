# Serena Quick Start Guide

Quick reference for Serena hooks and commands in Papertrace project.

## 🪝 Active Hooks

| Hook | Event | Purpose | Behavior |
|------|-------|---------|----------|
| **Architecture Validator** | PreToolUse (Edit/Write) | Enforce layer boundaries | ❌ Blocks violations |
| **Large File Warning** | PreToolUse (Read) | Suggest Serena for large files | ⚠️ Warns only |
| **Auto-Activate** | SessionStart | Activate Serena project | ℹ️ Info only |

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
1. Session starts → Auto-activate hook runs
2. /serena:health → Check system status
3. Start coding
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

## 🎯 Hook Behavior Examples

### Large File Warning (Non-Blocking)
```
You: Read JsonNormalizer.java (882 lines)

Hook: ⚠️ SERENA SUGGESTION: Large file
      Consider using:
      1. get_symbols_overview
      2. find_symbol
      3. Read only if necessary

Result: Warning shown, read proceeds
```

### Architecture Validator (Blocking)
```
You: Add @Entity to domain class

Hook: ❌ BLOCKED: Domain cannot have JPA
      Use pure Java instead

Result: Edit blocked, must fix
```

## 💡 Tips

**Token Efficiency**:
- Large file warning helps avoid reading unnecessary code
- Use `mcp__serena__get_symbols_overview` first
- Then `mcp__serena__find_symbol` for specific classes

**Memory Management**:
- Create memories for features: `/serena:memory:from-diff`
- Update monthly: `/serena:memory:sync`
- Clean quarterly: `/serena:memory:cleanup`

**Architecture Safety**:
- Architecture validator prevents layer violations
- Blocks framework deps in domain layer
- Ensures adapters use orchestrators

## 📚 Full Documentation

- **Hooks**: `.claude/hooks/README.md`
- **Commands**: `.claude/commands/serena/README.md`
- **Settings**: `.claude/settings.json`

## 🚀 Quick Actions

```bash
# Check what hooks are active
cat .claude/settings.json

# Test large file warning
cat << 'EOF' | python3 .claude/hooks/serena_large_file_warning.py
{"tool_input": {"file_path": "path/to/large/file.java"}}
EOF

# Test architecture validator
cat << 'EOF' | .claude/hooks/check_layer_dependencies.sh
{"tool_input": {"file_path": "patra-registry/patra-registry-domain/src/main/java/Test.java", "content": "import org.springframework.stereotype.Service;"}}
EOF

# Manual Serena health check (without slash command)
# Use MCP tools: mcp__serena__check_onboarding_performed()
#                mcp__serena__list_memories()
```

## 🔗 Related Files

```
.claude/
├── settings.json                          # Hook configuration
├── hooks/
│   ├── README.md                          # Hook documentation
│   ├── check_layer_dependencies.sh        # Architecture validator
│   ├── serena_large_file_warning.py       # Large file warning
│   └── serena_auto_activate.sh            # Auto-activate
└── commands/
    └── serena/
        ├── README.md                      # Command documentation
        ├── health.md                      # /serena:health
        ├── memory-sync.md                 # /serena:memory:sync
        ├── memory-from-diff.md            # /serena:memory:from-diff
        └── memory-cleanup.md              # /serena:memory:cleanup
```

---

**Remember**: Hooks run automatically, commands run on-demand!
