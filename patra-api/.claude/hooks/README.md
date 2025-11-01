# Hooks (Papertrace Edition)

Claude Code hooks that enable skill auto-activation, file tracking, and Maven compilation validation for Java/Spring Boot projects.

---

## What Are Hooks?

Hooks are scripts that run at specific points in Claude's workflow:
- **UserPromptSubmit**: When user submits a prompt
- **PreToolUse**: Before a tool executes
- **PostToolUse**: After a tool completes
- **Stop**: When user requests to stop

**Key insight:** Hooks can modify prompts, block actions, and track state - enabling features Claude can't do alone.

---

## Essential Hooks (Start Here)

### skill-activation-prompt (UserPromptSubmit)

**Purpose:** Automatically suggests relevant skills based on user prompts and file context

**How it works:**
1. Reads `skill-rules.json`
2. Matches user prompt against trigger patterns
3. Checks which files user is working with
4. Injects skill suggestions into Claude's context

**Why it's essential:** This is THE hook that makes skills auto-activate.

**Integration:**
```bash
# Copy both files
cp skill-activation-prompt.sh your-project/.claude/hooks/
cp skill-activation-prompt.ts your-project/.claude/hooks/

# Make executable
chmod +x your-project/.claude/hooks/skill-activation-prompt.sh

# Install dependencies
cd your-project/.claude/hooks
npm install
```

**Add to settings.json:**
```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/skill-activation-prompt.sh"
          }
        ]
      }
    ]
  }
}
```

**Customization:** ✅ None needed - reads skill-rules.json automatically

---

### post-tool-use-tracker (PostToolUse)

**Purpose:** Tracks file changes to maintain context across sessions

**How it works:**
1. Monitors Edit/Write/MultiEdit tool calls
2. Records which files were modified
3. Creates cache for context management
4. Auto-detects project structure (services, modules, packages, etc.)

**Why it's essential:** Helps Claude understand what parts of your codebase are active.

**Integration:**
```bash
# Copy file
cp post-tool-use-tracker.sh your-project/.claude/hooks/

# Make executable
chmod +x your-project/.claude/hooks/post-tool-use-tracker.sh
```

**Add to settings.json:**
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/post-tool-use-tracker.sh"
          }
        ]
      }
    ]
  }
}
```

**Customization:** ✅ None needed - auto-detects structure

---

## Java-Specific Hooks (Recommended for Papertrace)

### maven-compile-check (Stop)

**Purpose:** Quick Maven compilation check when user stops Claude

**How it works:**
1. Runs `mvn -T 1C compile -q -DskipTests`
2. Detects compilation errors
3. Displays error summary
4. Creates marker file if compilation fails

**Why it's useful:** Catch compilation errors early before committing code

**⚠️ Note:** This hook runs `mvn compile` which may take a few seconds for large projects.

**Integration:**
```bash
# Copy file
cp maven-compile-check.sh your-project/.claude/hooks/

# Make executable
chmod +x your-project/.claude/hooks/maven-compile-check.sh
```

**Add to settings.json:**
```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/maven-compile-check.sh"
          }
        ]
      }
    ]
  }
}
```

**Requirements:**
- Maven (`mvn`) must be in PATH
- `pom.xml` must exist in project root

**Customization:** ✅ None needed - works with any Maven project

---

### trigger-build-resolver-java (Stop)

**Purpose:** Suggests using auto-error-resolver agent when Maven compilation fails

**How it works:**
1. Checks for `.last-compile-failed` marker file (created by maven-compile-check.sh)
2. Displays friendly prompt suggesting auto-error-resolver agent
3. Cleans up marker file

**Why it's useful:** Automatically prompts user to fix compilation errors with AI assistance

**Dependencies:** Requires `maven-compile-check.sh` to run first

**Integration:**
```bash
# Copy file
cp trigger-build-resolver-java.sh your-project/.claude/hooks/

# Make executable
chmod +x your-project/.claude/hooks/trigger-build-resolver-java.sh
```

**Add to settings.json (after maven-compile-check):**
```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/maven-compile-check.sh"
          },
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/trigger-build-resolver-java.sh"
          }
        ]
      }
    ]
  }
}
```

**Customization:** ✅ None needed

---

## Complete Papertrace Configuration

**Recommended settings.json for Papertrace projects:**

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/skill-activation-prompt.sh"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/post-tool-use-tracker.sh"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/maven-compile-check.sh"
          },
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/trigger-build-resolver-java.sh"
          }
        ]
      }
    ]
  }
}
```

---

## Hook Execution Order

### On User Prompt Submit:
1. `skill-activation-prompt.sh` → Analyzes prompt and injects skill suggestions

### After File Edit:
1. `post-tool-use-tracker.sh` → Records file changes

### On Stop:
1. `maven-compile-check.sh` → Runs Maven compilation
2. `trigger-build-resolver-java.sh` → Suggests agent if compilation failed

---

## Troubleshooting

### Hook Not Executing

**Check permissions:**
```bash
ls -la .claude/hooks/*.sh | grep rwx
```

All `.sh` files should have `x` (executable) permission.

**Fix:**
```bash
chmod +x .claude/hooks/*.sh
```

---

### skill-activation-prompt Not Working

**Check dependencies:**
```bash
cd .claude/hooks
npm install
```

**Verify skill-rules.json exists:**
```bash
ls -la .claude/skills/skill-rules.json
```

---

### maven-compile-check Too Slow

**Option 1: Disable multi-threading**
Edit `maven-compile-check.sh` line 36:
```bash
# Change from:
mvn -T 1C compile -q -DskipTests

# To:
mvn compile -q -DskipTests
```

**Option 2: Skip this hook**
Remove from `settings.json` Stop hooks section.

---

### Maven Not Found

**Error:** `mvn: command not found`

**Solution:** Ensure Maven is installed and in PATH:
```bash
which mvn
mvn --version
```

Install Maven if needed:
```bash
# macOS
brew install maven

# Linux (Ubuntu/Debian)
sudo apt-get install maven
```

---

## For Claude Code

**When setting up hooks for a user:**

1. **Read [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)** first
2. **Always start with the two essential hooks** (skill-activation-prompt + post-tool-use-tracker)
3. **Java projects: Add Maven hooks** for compilation checking
4. **Verify after setup:**
   ```bash
   ls -la .claude/hooks/*.sh | grep rwx
   cd .claude/hooks && npm install
   ```

**Questions?** See [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)
