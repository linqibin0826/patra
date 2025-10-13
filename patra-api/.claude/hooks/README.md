# Claude Code Hooks for Papertrace

This directory contains custom hooks that enforce architectural rules, best practices, and Serena integration for the Papertrace project.

## Active Hooks

### 1. Architecture Layer Dependency Validator

**File**: `check_layer_dependencies.sh`
**Event**: `PreToolUse` (runs before Edit/Write operations)
**Purpose**: Enforces Hexagonal Architecture + DDD dependency rules

### 2. Serena Large File Read Warning

**File**: `serena_large_file_warning.py`
**Event**: `PreToolUse` (runs before Read operations)
**Purpose**: Encourages token-efficient code exploration using Serena's symbolic navigation

### 3. Serena Auto-Activate

**File**: `serena_auto_activate.sh`
**Event**: `SessionStart` (runs when session starts)
**Purpose**: Automatically activates Serena project for current workspace

#### Rules Enforced

| Layer | Allowed Dependencies | Forbidden |
|-------|---------------------|-----------|
| **Domain** | Only `patra-common` | ❌ Spring, JPA, Hibernate, any framework |
| **API** | Pure Java interfaces | ⚠️ Spring Framework (warning) |
| **App** | Domain + `patra-common` + core starter | ❌ Infra, Adapter direct imports |
| **Infra** | Domain + mybatis starter | ❌ Adapter imports |
| **Adapter** | App + API + web starters | ❌ Direct Repository calls |

#### Examples

**✅ Valid Domain Code**
```java
package com.patra.registry.domain.model;
import com.patra.common.base.BaseEntity; // patra-common is OK

public class Provenance extends BaseEntity {
    private String name;
    // Pure Java - no framework dependencies
}
```

**❌ Invalid Domain Code (Blocked)**
```java
package com.patra.registry.domain.model;
import org.springframework.stereotype.Service; // BLOCKED!
import jakarta.persistence.Entity;             // BLOCKED!

@Service // Framework annotation not allowed
public class ProvenanceService {
    // Domain should be framework-agnostic
}
```

**✅ Valid Adapter Code**
```java
package com.patra.registry.adapter.controller;
import com.patra.registry.app.CreateProvenanceOrchestrator; // Use orchestrator

@RestController
public class ProvenanceController {
    private final CreateProvenanceOrchestrator orchestrator; // ✓ Correct
}
```

**❌ Invalid Adapter Code (Blocked)**
```java
package com.patra.registry.adapter.controller;
import com.patra.registry.infra.repository.ProvenanceRepositoryImpl; // BLOCKED!

@RestController
public class ProvenanceController {
    private final ProvenanceRepositoryImpl repository; // ✗ Should use orchestrator
}
```

#### Testing the Hook

Test with invalid domain code:
```bash
cat << 'EOF' | ./.claude/hooks/check_layer_dependencies.sh
{
  "tool_input": {
    "file_path": "patra-registry/patra-registry-domain/src/main/java/Test.java",
    "content": "import org.springframework.stereotype.Service;\npublic class Test {}"
  }
}
EOF
```

Expected: Exit code 2 with error message

Test with valid domain code:
```bash
cat << 'EOF' | ./.claude/hooks/check_layer_dependencies.sh
{
  "tool_input": {
    "file_path": "patra-registry/patra-registry-domain/src/main/java/Test.java",
    "content": "package com.test;\npublic class Test {}"
  }
}
EOF
```

Expected: Exit code 0 (success)

#### Maintenance

To modify rules:
1. Edit `check_layer_dependencies.sh`
2. Test with sample inputs (see Testing section above)
3. Commit changes to git

To disable temporarily:
```bash
# Comment out hook in .claude/settings.json
# Or delete .claude/settings.json temporarily
```

---

### 2. Serena Large File Read Warning

**File**: `serena_large_file_warning.py`
**Event**: `PreToolUse` (before Read operations)
**Purpose**: Encourages token-efficient code exploration using Serena

#### What It Does

- Monitors Read tool calls for Java source files
- Warns when reading files >200 lines without using Serena first
- Suggests using `get_symbols_overview` and `find_symbol` instead
- Only warns once per file per session (creates marker files)

#### Example Output

```
⚠️  SERENA SUGGESTION: Large file (882 lines)
📖 Consider using Serena first for token efficiency:

   1. mcp__serena__get_symbols_overview
      → Get file overview with symbol list

   2. mcp__serena__find_symbol
      → Read specific symbols only

   3. Read full file only if necessary

💡 This saves tokens and focuses on relevant code
```

#### Testing

```bash
cat << 'EOF' | python3 ./.claude/hooks/serena_large_file_warning.py
{
  "tool_input": {
    "file_path": "patra-common/src/main/java/com/patra/common/json/JsonNormalizer.java"
  }
}
EOF
```

Expected: Warning message for large files, exit code 0 (non-blocking)

---

### 3. Serena Auto-Activate

**File**: `serena_auto_activate.sh`
**Event**: `SessionStart` (when Claude Code session starts)
**Purpose**: Automatically activates Serena for current project

#### What It Does

- Runs at session start
- Detects project name and path
- Provides feedback about Serena activation
- Reminds user to run `/serena:health` to verify

#### Example Output

```
🧠 SERENA AUTO-ACTIVATE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📁 Project: Papertrace-api
📍 Path: /Users/linqibin/Desktop/Papertrace-api

🔄 Activating Serena project...

Note: Serena activation happens automatically.
      Use /serena:health to check status.
```

#### Testing

```bash
CLAUDE_PROJECT_DIR="/Users/linqibin/Desktop/Papertrace-api" \
  bash ./.claude/hooks/serena_auto_activate.sh
```

Expected: Informational message, exit code 0

---

## Hook Configuration

Hooks are configured in `.claude/settings.json` (project-level) or `~/.claude/settings.json` (user-level).

Current configuration:
```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/check_layer_dependencies.sh"
          }
        ]
      },
      {
        "matcher": "Read",
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/serena_large_file_warning.py"
          }
        ]
      }
    ],
    "SessionStart": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/serena_auto_activate.sh"
          }
        ]
      }
    ]
  }
}
```

## Resources

- [Claude Code Hooks Documentation](https://docs.claude.com/en/docs/claude-code/hooks)
- [Papertrace Architecture Guide](./../AGENTS-architecture.md)
- [Hexagonal Architecture Reference](https://en.wikipedia.org/wiki/Hexagonal_architecture_(software))
