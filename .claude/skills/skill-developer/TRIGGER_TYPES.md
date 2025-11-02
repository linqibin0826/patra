# Trigger Types - Complete Guide

Complete reference for configuring skill triggers in Claude Code's skill auto-activation system.

## Table of Contents

- [Keyword Triggers (Explicit)](#keyword-triggers-explicit)
- [Intent Pattern Triggers (Implicit)](#intent-pattern-triggers-implicit)
- [File Path Triggers](#file-path-triggers)
- [Content Pattern Triggers](#content-pattern-triggers)
- [Best Practices Summary](#best-practices-summary)

---

## Keyword Triggers (Explicit)

### How It Works

Case-insensitive substring matching in user's prompt.

### Use For

Topic-based activation where user explicitly mentions the subject.

### Configuration

```json
"promptTriggers": {
  "keywords": ["orchestrator", "mybatis", "hexagonal", "aggregate", "domain event"]
}
```

### Example

- User prompt: "how does the **orchestrator** pattern work?"
- Matches: "orchestrator" keyword
- Activates: `java-backend-guidelines`

### Best Practices

- Use specific, unambiguous terms
- Include common variations ("layout", "layout system", "grid layout")
- Avoid overly generic words ("system", "work", "create")
- Test with real prompts

---

## Intent Pattern Triggers (Implicit)

### How It Works

Regex pattern matching to detect user's intent even when they don't mention the topic explicitly.

### Use For

Action-based activation where user describes what they want to do rather than the specific topic.

### Configuration

```json
"promptTriggers": {
  "intentPatterns": [
    "(create|add|implement).*?(feature|endpoint)",
    "(how does|explain).*?(layout|workflow)"
  ]
}
```

### Examples

**Database Work:**
- User prompt: "add user tracking feature"
- Matches: `(add).*?(feature)`
- Activates: `mybatis-query-verification`, `java-backend-guidelines`

**Orchestrator Creation:**
- User prompt: "create a data ingestion orchestrator"
- Matches: `(create).*?(orchestrator)`
- Activates: `java-backend-guidelines`, `papertrace-domain`

### Best Practices

- Capture common action verbs: `(create|add|modify|build|implement)`
- Include domain-specific nouns: `(feature|endpoint|component|workflow)`
- Use non-greedy matching: `.*?` instead of `.*`
- Test patterns thoroughly with regex tester (https://regex101.com/)
- Don't make patterns too broad (causes false positives)
- Don't make patterns too specific (causes false negatives)

### Common Pattern Examples

```regex
# Database Work
(add|create|implement).*?(mapper|entity|repository|table|query)

# Explanations
(how does|explain|what is|describe).*?

# Hexagonal Architecture
(create|add|make|build).*?(orchestrator|coordinator|aggregate|adapter)

# Error Handling
(fix|handle|catch|debug).*?(error|exception|bug)

# Workflow/Domain Events
(create|add|modify).*?(workflow|orchestrator|domain.*?event|event.*?handler)
```

---

## File Path Triggers

### How It Works

Glob pattern matching against the file path being edited.

### Use For

Domain/area-specific activation based on file location in the project.

### Configuration

```json
"fileTriggers": {
  "pathPatterns": [
    "patra-*/patra-*-adapter/src/main/java/**/*.java",
    "patra-*/patra-*-app/src/main/java/**/*Orchestrator.java"
  ],
  "pathExclusions": [
    "**/*Test.java",
    "**/*IT.java"
  ]
}
```

### Glob Pattern Syntax

- `**` = Any number of directories (including zero)
- `*` = Any characters within a directory name
- Examples:
  - `patra-*/src/main/java/**/*.java` = All .java files in src/main/java and subdirs
  - `**/application.yml` = application.yml anywhere in project
  - `patra-ingest/**/*Orchestrator.java` = All Orchestrator files in patra-ingest

### Example

- File being edited: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/PlanIngestionOrchestrator.java`
- Matches: `patra-*/patra-*-app/src/main/java/**/*Orchestrator.java`
- Activates: `java-backend-guidelines`, `papertrace-domain`

### Best Practices

- Be specific to avoid false positives
- Use exclusions for test files: `**/*Test.java`, `**/*IT.java`
- Consider Maven multi-module structure
- Test patterns with actual file paths
- Use narrower patterns when possible: `patra-ingest/patra-ingest-app/**` not `patra-ingest/**`

### Common Path Patterns

```glob
# Adapter Layer (Controllers, Jobs)
patra-*/patra-*-adapter/src/main/java/**/*.java          # All adapter files
patra-*/patra-*-adapter/src/main/java/**/*Controller.java # REST controllers
patra-*/patra-*-adapter/src/main/java/**/*Job.java       # Scheduled jobs

# Application Layer (Orchestrators)
patra-*/patra-*-app/src/main/java/**/*.java              # All application files
patra-*/patra-*-app/src/main/java/**/*Orchestrator.java  # Orchestrators
patra-*/patra-*-app/src/main/java/**/*Coordinator.java   # Coordinators

# Domain Layer
patra-*/patra-*-domain/src/main/java/**/*.java           # All domain files
patra-*/patra-*-domain/src/main/java/**/*Aggregate.java  # Aggregates

# Infrastructure Layer
patra-*/patra-*-infra/src/main/java/**/*Mapper.java      # MyBatis mappers
patra-*/patra-*-infra/src/main/java/**/*Repository*.java # Repositories

# Configuration
**/application.yml                                       # Spring Boot config
**/application-*.yml                                     # Environment configs
**/mapper/**/*.xml                                       # MyBatis XML mappers

# Test Exclusions
**/*Test.java                                            # Unit tests
**/*IT.java                                              # Integration tests
```

---

## Content Pattern Triggers

### How It Works

Regex pattern matching against the file's actual content (what's inside the file).

### Use For

Technology-specific activation based on what the code imports or uses (Prisma, controllers, specific libraries).

### Configuration

```json
"fileTriggers": {
  "contentPatterns": [
    "import.*mybatis.*plus",
    "BaseMapper<",
    "@Mapper",
    "@Service"
  ]
}
```

### Examples

**MyBatis Detection:**
- File contains: `import com.baomidou.mybatisplus.core.mapper.BaseMapper;`
- Matches: `import.*mybatis.*plus`
- Activates: `mybatis-query-verification`

**Controller Detection:**
- File contains: `@RestController`
- Matches: `@RestController`
- Activates: `java-backend-guidelines`

### Best Practices

- Match imports: `import.*mybatis` (case-insensitive)
- Escape special regex chars: `\\.selectOne\\(` not `.selectOne(`
- Patterns use case-insensitive flag
- Test against real file content
- Make patterns specific enough to avoid false matches

### Common Content Patterns

```regex
# MyBatis-Plus
import.*mybatis.*plus            # MyBatis-Plus imports
BaseMapper<                      # BaseMapper interface
ServiceImpl<                     # ServiceImpl base class
\.selectOne\(|\.selectList\(    # MyBatis-Plus query methods
@Mapper                          # Mapper annotation

# Spring Boot Controllers
@RestController                  # REST controller
@Controller                      # MVC controller
@RequestMapping|@GetMapping      # Request mappings
@PathVariable|@RequestBody       # Request parameters

# Spring Boot Services
@Service                         # Service component
@Transactional                   # Transaction management
@Async                           # Async execution

# Error Handling
try\s*\{                        # Try blocks
catch\s*\(                      # Catch blocks
throw new                        # Throw statements
@ControllerAdvice                # Global exception handler

# Hexagonal Architecture
class.*Orchestrator              # Orchestrator classes
class.*Coordinator               # Coordinator classes
@TransactionalEventListener      # Event listeners
```

---

## Best Practices Summary

### DO:
✅ Use specific, unambiguous keywords
✅ Test all patterns with real examples
✅ Include common variations
✅ Use non-greedy regex: `.*?`
✅ Escape special characters in content patterns
✅ Add exclusions for test files
✅ Make file path patterns narrow and specific

### DON'T:
❌ Use overly generic keywords ("system", "work")
❌ Make intent patterns too broad (false positives)
❌ Make patterns too specific (false negatives)
❌ Forget to test with regex tester (https://regex101.com/)
❌ Use greedy regex: `.*` instead of `.*?`
❌ Match too broadly in file paths

### Testing Your Triggers

**Test keyword/intent triggers:**
```bash
# Using shell script
echo '{"session_id":"test","prompt":"your test prompt"}' | \
  .claude/hooks/skill-activation-prompt.sh

# Or using TypeScript (if available)
echo '{"session_id":"test","prompt":"your test prompt"}' | \
  npx tsx .claude/hooks/skill-activation-prompt.ts
```

**Test file path/content triggers:**
```bash
# Using shell script
cat <<'EOF' | .claude/hooks/skill-verification-guard.sh
{
  "session_id": "test",
  "tool_name": "Edit",
  "tool_input": {"file_path": "patra-ingest-app/src/main/java/com/patra/ingest/app/MyOrchestrator.java"}
}
EOF
```

---

**Related Files:**
- [SKILL.md](SKILL.md) - Main skill guide
- [SKILL_RULES_REFERENCE.md](SKILL_RULES_REFERENCE.md) - Complete skill-rules.json schema
- [PATTERNS_LIBRARY.md](PATTERNS_LIBRARY.md) - Ready-to-use pattern library
