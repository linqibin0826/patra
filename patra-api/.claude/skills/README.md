# Skills

Production-tested skills for Claude Code that auto-activate based on context.

---

## What Are Skills?

Skills are modular knowledge bases that Claude loads when needed. They provide:
- Domain-specific guidelines
- Best practices
- Code examples
- Anti-patterns to avoid

**Problem:** Skills don't activate automatically by default.

**Solution:** This showcase includes the hooks + configuration to make them activate.

---

## Available Skills

### skill-developer (Meta-Skill)
**Purpose:** Creating and managing Claude Code skills

**Files:** 7 resource files (426 lines total)

**Use when:**
- Creating new skills
- Understanding skill structure
- Working with skill-rules.json
- Debugging skill activation

**Customization:** ✅ None - copy as-is

**[View Skill →](skill-developer/)**

---

### java-backend-guidelines
**Purpose:** Java/Spring Boot backend development with Hexagonal Architecture + DDD

**Files:** 16 resource files (607 lines main + resources)

**Covers:**
- Hexagonal Architecture (Ports & Adapters) + DDD patterns
- Four-layer architecture (Adapter → Application → Domain ← Infrastructure)
- Spring Boot 3.5.7 + Java 25 best practices
- MyBatis-Plus database access patterns
- MapStruct DO ↔ Domain entity mapping
- Orchestrator/Coordinator patterns
- Transaction management (@Transactional)
- Validation patterns (@Valid)
- Nacos configuration management
- Outbox pattern for reliable events
- Event-driven architecture
- Testing strategies (Unit, Integration, ArchUnit)

**Use when:**
- Creating REST controllers, orchestrators, domain entities
- Implementing repositories with MyBatis-Plus
- Building microservices with Hexagonal Architecture
- Working with aggregates, value objects, domain events
- Setting up validation and error handling
- Performance optimization and N+1 query prevention

**Customization:** ✅ Already configured for Papertrace (patra-* modules)

**Path Patterns:**
```json
{
  "pathPatterns": [
    "patra-*/patra-*-adapter/src/**/*.java",
    "patra-*/patra-*-app/src/**/*.java",
    "patra-*/patra-*-domain/src/**/*.java",
    "patra-*/patra-*-infra/src/**/*.java"
  ]
}
```

**[View Skill →](java-backend-guidelines/)**

---

### logging-observability
**Purpose:** Logging, tracing, and observability patterns for Spring Boot

**Files:** 1 main file (~750 lines)

**Covers:**
- SLF4J + Logback logging patterns
- Structured logging with MDC
- Micrometer metrics and tracing
- Performance monitoring
- Error handling with @ControllerAdvice
- Database query monitoring

**Use when:**
- Adding logging to any code
- Setting up error handling
- Tracking performance metrics
- Implementing distributed tracing
- Debugging production issues

**Customization:** ✅ None needed - works with any Spring Boot project

**[View Skill →](logging-observability/)**

---

### papertrace-domain
**Purpose:** Papertrace business domain knowledge and workflow patterns

**Files:** 7 resource files (465 lines main + resources)

**Covers:**
- Core domain concepts: Provenance, Plan, Task, Expression engine
- Provenance configuration system (HTTP, Pagination, Retry, Rate Limit)
- Plan/Task lifecycle and workflow patterns
- Temporal slicing strategies and window management
- Expression engine: mapping abstract queries to provider-specific APIs
- Scope precedence hierarchy (TASK > SOURCE > GLOBAL)
- Idempotency patterns with business keys
- Common integration patterns (PubMed, EPMC, Crossref)
- Troubleshooting guide for common issues

**Use when:**
- Working with Provenance configurations
- Implementing Plan creation and slicing logic
- Building Task execution workflows
- Debugging expression rendering issues
- Understanding data source integrations
- Troubleshooting Plan/Task failures

**Customization:** ✅ Already configured for Papertrace (patra-registry, patra-ingest, patra-expr-kernel)

**Path Patterns:**
```json
{
  "pathPatterns": [
    "patra-registry/**/*.java",
    "patra-ingest/**/*.java",
    "patra-expr-kernel/**/*.java"
  ]
}
```

**[View Skill →](papertrace-domain/)**

---

## How to Add a Skill to Your Project

### Quick Integration

**For Claude Code:**
```
User: "Add the java-backend-guidelines skill to my project"

Claude should:
1. Ask about project structure
2. Copy skill directory
3. Update skill-rules.json with their paths
4. Verify integration
```

See [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md) for complete instructions.

### Manual Integration

**Step 1: Copy the skill directory**
```bash
cp -r showcase/.claude/skills/java-backend-guidelines \\
      your-project/.claude/skills/
```

**Step 2: Update skill-rules.json**

If you don't have one, create it:
```bash
cp showcase/.claude/skills/skill-rules.json \\
   your-project/.claude/skills/
```

Then customize the `pathPatterns` for your project:
```json
{
  "skills": {
    "java-backend-guidelines": {
      "fileTriggers": {
        "pathPatterns": [
          "patra-*/patra-*-adapter/src/**/*.java",
          "patra-*/patra-*-app/src/**/*.java",
          "patra-*/patra-*-domain/src/**/*.java",
          "patra-*/patra-*-infra/src/**/*.java"
        ]
      }
    }
  }
}
```

**Step 3: Test**
- Edit a Java file in your backend module
- The skill should activate automatically

---

## skill-rules.json Configuration

### What It Does

Defines when skills should activate based on:
- **Keywords** in user prompts ("backend", "orchestrator", "domain")
- **Intent patterns** (regex matching user intent)
- **File path patterns** (editing Java backend files)
- **Content patterns** (code contains @RestController, @Service, MyBatis)

### Configuration Format

```json
{
  "skill-name": {
    "type": "domain" | "guardrail",
    "enforcement": "suggest" | "block",
    "priority": "high" | "medium" | "low",
    "promptTriggers": {
      "keywords": ["list", "of", "keywords"],
      "intentPatterns": ["regex patterns"]
    },
    "fileTriggers": {
      "pathPatterns": ["patra-*/src/**/*.java"],
      "contentPatterns": ["@RestController", "@Service", "MyBatis"]
    }
  }
}
```

### Enforcement Levels

- **suggest**: Skill appears as suggestion, doesn't block
- **block**: Must use skill before proceeding (guardrail)

**Use "block" for:**
- Preventing architectural violations
- Critical database operations
- Security-sensitive code

**Use "suggest" for:**
- General best practices
- Domain guidance
- Code organization

---

## Creating Your Own Skills

See the **skill-developer** skill for complete guide on:
- Skill YAML frontmatter structure
- Resource file organization
- Trigger pattern design
- Testing skill activation

**Quick template:**
```markdown
---
name: my-skill
description: What this skill does
---

# My Skill Title

## Purpose
[Why this skill exists]

## When to Use This Skill
[Auto-activation scenarios]

## Quick Reference
[Key patterns and examples]

## Resource Files
- [topic-1.md](resources/topic-1.md)
- [topic-2.md](resources/topic-2.md)
```

---

## Troubleshooting

### Skill isn't activating

**Check:**
1. Is skill directory in `.claude/skills/`?
2. Is skill listed in `skill-rules.json`?
3. Do `pathPatterns` match your files?
4. Are hooks installed and working?
5. Is settings.json configured correctly?

**Debug:**
```bash
# Check skill exists
ls -la .claude/skills/

# Validate skill-rules.json
cat .claude/skills/skill-rules.json | jq .

# Check hooks are executable
ls -la .claude/hooks/*.sh

# Test hook manually
./.claude/hooks/skill-activation-prompt.sh
```

### Skill activates too often

Update skill-rules.json:
- Make keywords more specific
- Narrow `pathPatterns`
- Increase specificity of `intentPatterns`

### Skill never activates

Update skill-rules.json:
- Add more keywords
- Broaden `pathPatterns`
- Add more `intentPatterns`

---

## For Claude Code

**When integrating a skill for a user:**

1. **Read [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)** first
2. Ask about their project structure
3. Customize `pathPatterns` in skill-rules.json
4. Verify the skill file has no hardcoded paths
5. Test activation after integration

**Common mistakes:**
- Not verifying path patterns match actual project structure
- Not asking about multi-module Maven project layout
- Copying skill-rules.json without customization

---

## Next Steps

1. **Start simple:** Add one skill that matches your work
2. **Verify activation:** Edit a relevant file, skill should suggest
3. **Add more:** Once first skill works, add others
4. **Customize:** Adjust triggers based on your workflow

**Questions?** See [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md) for comprehensive integration instructions.
