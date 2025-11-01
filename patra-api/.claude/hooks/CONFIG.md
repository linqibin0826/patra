# Hooks Configuration Guide (Papertrace Edition)

This guide explains how to configure and customize the hooks system for Java/Spring Boot projects.

---

## Quick Start Configuration

### 1. Register Hooks in .claude/settings.json

Create or update `.claude/settings.json` in your project root:

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

### 2. Install Dependencies (for skill-activation-prompt)

```bash
cd .claude/hooks
npm install
```

### 3. Set Execute Permissions

```bash
chmod +x .claude/hooks/*.sh
```

---

## Customization Options

### Project Structure Detection

By default, hooks detect these directory patterns:

**Java Services:** `src/main/java/`, `patra-*/`, `*-service/`
**Resources:** `src/main/resources/`, `src/test/java/`
**Build:** `target/`, `build/`

#### Adding Custom Directory Patterns

Edit `.claude/hooks/post-tool-use-tracker.sh`, function `detect_repo()`:

```bash
case "$repo" in
    # Add your custom service directories here
    patra-custom-service)
        echo "$repo"
        ;;
    my-module)
        echo "$repo"
        ;;
    # ... existing patterns
esac
```

---

### Maven Compilation Configuration

#### Adjusting Maven Compile Command

Edit `.claude/hooks/maven-compile-check.sh`:

```bash
# Default: Multi-threaded compilation
mvn -T 1C compile -q -DskipTests

# Single-threaded (slower but more stable):
mvn compile -q -DskipTests

# With specific modules:
mvn -pl patra-registry,patra-ingest compile -q -DskipTests

# Full clean compile:
mvn clean compile -q -DskipTests
```

#### Adjusting Error Display Limit

Edit `.claude/hooks/maven-compile-check.sh` (line ~47):

```bash
# Default: Show first 20 errors
grep -E "\[ERROR\]|error:|cannot find symbol" "$TEMP_OUTPUT" | head -20

# Show more errors (e.g., 50):
grep -E "\[ERROR\]|error:|cannot find symbol" "$TEMP_OUTPUT" | head -50

# Show all errors (no limit):
grep -E "\[ERROR\]|error:|cannot find symbol" "$TEMP_OUTPUT"
```

---

### Skill Activation Customization

#### Adjusting Skill Trigger Patterns

Edit `.claude/skills/skill-rules.json`:

```json
{
  "java-backend-guidelines": {
    "type": "technical",
    "enforcement": "suggest",
    "priority": "high",
    "description": "Java backend development guide...",
    "promptTriggers": {
      "keywords": [
        "orchestrator",
        "hexagonal",
        "ddd",
        "spring boot"
      ],
      "intentPatterns": [
        "(how|create|implement).*(controller|service|repository)",
        "(transaction|@Transactional)"
      ]
    },
    "fileTriggers": {
      "pathPatterns": [
        "patra-*/src/main/java/**/*.java"
      ],
      "contentPatterns": [
        "@Service",
        "@RestController",
        "@Repository"
      ]
    }
  }
}
```

**See:** [../skills/README.md](../skills/README.md) for complete skill-rules.json documentation.

---

## Environment Variables

### Global Environment Variables

Set in your shell profile (`.bashrc`, `.zshrc`, etc.):

```bash
# Custom project directory (if not using default)
export CLAUDE_PROJECT_DIR=/path/to/your/project

# Maven home (if not in PATH)
export M2_HOME=/usr/local/maven
export PATH=$M2_HOME/bin:$PATH
```

### Per-Session Environment Variables

Set before starting Claude Code:

```bash
CLAUDE_PROJECT_DIR=/path/to/project claude-code
```

---

## Hook Execution Order

Stop hooks run in the order specified in `settings.json`:

```json
"Stop": [
  {
    "hooks": [
      { "command": "...maven-compile-check.sh" },          // Runs FIRST
      { "command": "...trigger-build-resolver-java.sh" }   // Runs SECOND
    ]
  }
]
```

**Why this order matters:**
1. Check compilation first (detect errors)
2. Then suggest agent (if errors found)

---

## Selective Hook Enabling

You don't need all hooks. Choose what works for your project:

### Minimal Setup (Skill Activation Only)

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

### Without Maven Compilation Checks

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
    ]
  }
}
```

### Maven Compilation Only (No Skill Activation)

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

---

## Cache Management

### Cache Location

```
$CLAUDE_PROJECT_DIR/.claude/hooks/.last-compile-failed
```

This marker file is created when Maven compilation fails and removed when suggested agent is displayed.

### Manual Cleanup

```bash
# Remove compilation failure marker
rm -f $CLAUDE_PROJECT_DIR/.claude/hooks/.last-compile-failed
```

---

## Troubleshooting Configuration

### Hook Not Executing

1. **Check registration:** Verify hook is in `.claude/settings.json`
2. **Check permissions:** Run `chmod +x .claude/hooks/*.sh`
3. **Check path:** Ensure `$CLAUDE_PROJECT_DIR` is set correctly
4. **Check dependencies:** Run `cd .claude/hooks && npm install`

### Maven Compilation Too Slow

**Issue:** Hook takes too long to run

**Solutions:**

1. **Disable multi-threading** (line 36 in `maven-compile-check.sh`):
   ```bash
   # Change from:
   mvn -T 1C compile -q -DskipTests

   # To:
   mvn compile -q -DskipTests
   ```

2. **Compile specific modules only**:
   ```bash
   # In maven-compile-check.sh, change line 36:
   mvn -pl patra-registry,patra-ingest compile -q -DskipTests
   ```

3. **Skip this hook entirely** - Remove from `settings.json`

### False Positive Detections

**Issue:** Hook triggers for files it shouldn't

**Solution:** Add skip conditions in `maven-compile-check.sh`:

```bash
# Add at the top, after PROJECT_ROOT is set
if [[ "$CLAUDE_PROJECT_DIR" =~ /test-fixtures/ ]]; then
    exit 0  # Skip test fixture projects
fi
```

### Maven Not Found

**Issue:** `mvn: command not found`

**Solutions:**

1. **Install Maven:**
   ```bash
   # macOS
   brew install maven

   # Linux (Ubuntu/Debian)
   sudo apt-get install maven

   # Verify installation
   mvn --version
   ```

2. **Set MAVEN_HOME environment variable:**
   ```bash
   export M2_HOME=/usr/local/maven
   export PATH=$M2_HOME/bin:$PATH
   ```

### Debugging Hooks

Add debug output to any hook:

```bash
# At the top of the hook script
set -x  # Enable debug mode

# Or add specific debug lines
echo "DEBUG: PROJECT_ROOT=$PROJECT_ROOT" >&2
echo "DEBUG: Running mvn compile..." >&2
```

View hook execution in Claude Code's logs.

---

## Advanced Configuration

### Multi-Module Maven Projects

For projects with selective module compilation:

```bash
# In maven-compile-check.sh, modify line 36
# Detect which modules were changed and compile only those

CHANGED_MODULES=$(git diff --name-only HEAD | grep -oP 'patra-\w+' | sort -u | tr '\n' ',' | sed 's/,$//')

if [[ -n "$CHANGED_MODULES" ]]; then
    mvn -pl "$CHANGED_MODULES" compile -q -DskipTests
else
    mvn -T 1C compile -q -DskipTests
fi
```

### Custom Maven Profiles

To use specific Maven profiles during compilation:

```bash
# In maven-compile-check.sh, line 36
mvn -T 1C compile -q -DskipTests -P dev

# Or multiple profiles:
mvn -T 1C compile -q -DskipTests -P dev,local
```

### Docker/Container Projects

If Maven runs inside a container:

```bash
# In maven-compile-check.sh, replace line 36
docker-compose exec -T app mvn compile -q -DskipTests

# Or with Docker run:
docker run --rm -v "$PROJECT_ROOT":/workspace -w /workspace maven:3.9-eclipse-temurin-25 mvn compile -q -DskipTests
```

---

## Best Practices

1. **Start minimal** - Enable hooks one at a time
2. **Test thoroughly** - Make changes and verify hooks work
3. **Document customizations** - Add comments to explain custom logic
4. **Version control** - Commit `.claude/` directory to git
5. **Team consistency** - Share configuration across team
6. **Performance conscious** - Monitor hook execution time
7. **Graceful degradation** - Hooks should fail silently if tools are missing

---

## Performance Optimization

### Faster Compilation Checks

**Option 1: Compile only changed modules**

Track changed files and compile only affected modules:

```bash
# In maven-compile-check.sh
CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null || echo "")
if [[ -n "$CHANGED_FILES" ]]; then
    # Extract module names from changed files
    MODULES=$(echo "$CHANGED_FILES" | grep -oP 'patra-\w+' | sort -u | paste -sd,)

    if [[ -n "$MODULES" ]]; then
        mvn -pl "$MODULES" -am compile -q -DskipTests
        exit $?
    fi
fi

# Fallback to full compilation
mvn -T 1C compile -q -DskipTests
```

**Option 2: Use Maven Daemon**

Install and use [mvnd](https://github.com/apache/maven-mvnd) for faster builds:

```bash
# Install mvnd
brew install mvnd

# In maven-compile-check.sh, line 36, replace mvn with mvnd:
mvnd -T 1C compile -q -DskipTests
```

---

## See Also

- [README.md](./README.md) - Hooks overview
- [../skills/README.md](../skills/README.md) - Skills configuration
- [../../CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md) - Complete integration guide
