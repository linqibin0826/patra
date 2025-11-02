# skill-rules.json - Complete Reference

Complete schema and configuration reference for `.claude/skills/skill-rules.json`.

## Table of Contents

- [File Location](#file-location)
- [Complete TypeScript Schema](#complete-typescript-schema)
- [Field Guide](#field-guide)
- [Example: Guardrail Skill](#example-guardrail-skill)
- [Example: Domain Skill](#example-domain-skill)
- [Validation](#validation)

---

## File Location

**Path:** `.claude/skills/skill-rules.json`

This JSON file defines all skills and their trigger conditions for the auto-activation system.

---

## Complete TypeScript Schema

```typescript
interface SkillRules {
    version: string;
    skills: Record<string, SkillRule>;
}

interface SkillRule {
    type: 'guardrail' | 'domain';
    enforcement: 'block' | 'suggest' | 'warn';
    priority: 'critical' | 'high' | 'medium' | 'low';

    promptTriggers?: {
        keywords?: string[];
        intentPatterns?: string[];  // Regex strings
    };

    fileTriggers?: {
        pathPatterns: string[];     // Glob patterns
        pathExclusions?: string[];  // Glob patterns
        contentPatterns?: string[]; // Regex strings
        createOnly?: boolean;       // Only trigger on file creation
    };

    blockMessage?: string;  // For guardrails, {file_path} placeholder

    skipConditions?: {
        sessionSkillUsed?: boolean;      // Skip if used in session
        fileMarkers?: string[];          // e.g., ["@skip-validation"]
        envOverride?: string;            // e.g., "SKIP_DB_VERIFICATION"
    };
}
```

---

## Field Guide

### Top Level

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | string | Yes | Schema version (currently "1.0") |
| `skills` | object | Yes | Map of skill name → SkillRule |

### SkillRule Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | "guardrail" (enforced) or "domain" (advisory) |
| `enforcement` | string | Yes | "block" (PreToolUse), "suggest" (UserPromptSubmit), or "warn" |
| `priority` | string | Yes | "critical", "high", "medium", or "low" |
| `promptTriggers` | object | Optional | Triggers for UserPromptSubmit hook |
| `fileTriggers` | object | Optional | Triggers for PreToolUse hook |
| `blockMessage` | string | Optional* | Required if enforcement="block". Use `{file_path}` placeholder |
| `skipConditions` | object | Optional | Escape hatches and session tracking |

*Required for guardrails

### promptTriggers Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `keywords` | string[] | Optional | Exact substring matches (case-insensitive) |
| `intentPatterns` | string[] | Optional | Regex patterns for intent detection |

### fileTriggers Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `pathPatterns` | string[] | Yes* | Glob patterns for file paths |
| `pathExclusions` | string[] | Optional | Glob patterns to exclude (e.g., test files) |
| `contentPatterns` | string[] | Optional | Regex patterns to match file content |
| `createOnly` | boolean | Optional | Only trigger when creating new files |

*Required if fileTriggers is present

### skipConditions Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sessionSkillUsed` | boolean | Optional | Skip if skill already used this session |
| `fileMarkers` | string[] | Optional | Skip if file contains comment marker |
| `envOverride` | string | Optional | Environment variable name to disable skill |

---

## Example: Guardrail Skill

Complete example of a blocking guardrail skill with all features:

```json
{
  "mybatis-query-verification": {
    "type": "guardrail",
    "enforcement": "block",
    "priority": "critical",

    "promptTriggers": {
      "keywords": [
        "mybatis",
        "mapper",
        "database",
        "table",
        "column",
        "schema",
        "query",
        "sql"
      ],
      "intentPatterns": [
        "(add|create|implement).*?(mapper|entity|repository|query)",
        "(modify|update|change).*?(table|column|schema|field|mapper)",
        "database.*?(query|change|update|modify)"
      ]
    },

    "fileTriggers": {
      "pathPatterns": [
        "patra-*/patra-*-infra/src/main/java/**/*Mapper.java",
        "patra-*/patra-*-infra/src/main/java/**/*Repository*.java",
        "patra-*/patra-*-domain/src/main/java/**/*Entity.java",
        "**/mapper/**/*.xml",
        "**/resources/db/migration/**/*.sql"
      ],
      "pathExclusions": [
        "**/*Test.java",
        "**/*IT.java"
      ],
      "contentPatterns": [
        "import.*mybatis.*plus",
        "BaseMapper<",
        "@Mapper",
        "@TableName",
        "\\.selectOne\\(",
        "\\.selectList\\(",
        "\\.insert\\(",
        "\\.update\\(",
        "\\.delete\\(",
        "LambdaQueryWrapper",
        "QueryWrapper"
      ]
    },

    "blockMessage": "⚠️ BLOCKED - Database Operation Detected\n\n📋 REQUIRED ACTION:\n1. Use MCP mysql tool to query database schema\n2. Verify ALL table and column names against actual database\n3. Use DESCRIBE table_name or SHOW COLUMNS to check structure\n4. Then retry this edit\n\nReason: Prevent column name errors in MyBatis queries\nFile: {file_path}\n\n💡 TIP: Add '// @skip-validation' comment to skip future checks",

    "skipConditions": {
      "sessionSkillUsed": true,
      "fileMarkers": [
        "@skip-validation"
      ],
      "envOverride": "SKIP_MYBATIS_VERIFICATION"
    }
  }
}
```

### Key Points for Guardrails

1. **type**: Must be "guardrail"
2. **enforcement**: Must be "block"
3. **priority**: Usually "critical" or "high"
4. **blockMessage**: Required, clear actionable steps
5. **skipConditions**: Session tracking prevents repeated nagging
6. **fileTriggers**: Usually has both path and content patterns
7. **contentPatterns**: Catch actual usage of technology

---

## Example: Domain Skill

Complete example of a suggestion-based domain skill:

```json
{
  "java-backend-guidelines": {
    "type": "domain",
    "enforcement": "suggest",
    "priority": "high",

    "promptTriggers": {
      "keywords": [
        "orchestrator",
        "coordinator",
        "hexagonal",
        "hexagonal architecture",
        "ddd",
        "domain driven design",
        "aggregate",
        "aggregate root",
        "domain event",
        "event handler",
        "spring boot",
        "mybatis",
        "transaction",
        "adapter",
        "application layer",
        "domain layer",
        "infrastructure"
      ],
      "intentPatterns": [
        "(how does|how do|explain|what is|describe).*?(orchestrator|coordinator|hexagonal|aggregate|domain.*?event)",
        "(create|implement|build).*?(orchestrator|coordinator|aggregate|adapter)",
        "(add|create|modify).*?(service|controller|repository|mapper)"
      ]
    },

    "fileTriggers": {
      "pathPatterns": [
        "patra-*/patra-*-app/src/main/java/**/*Orchestrator.java",
        "patra-*/patra-*-app/src/main/java/**/*Coordinator.java",
        "patra-*/patra-*-domain/src/main/java/**/*Aggregate.java",
        "patra-*/patra-*-adapter/src/main/java/**/*Controller.java"
      ],
      "pathExclusions": [
        "**/*Test.java",
        "**/*IT.java"
      ]
    }
  }
}
```

### Key Points for Domain Skills

1. **type**: Must be "domain"
2. **enforcement**: Usually "suggest"
3. **priority**: "high" or "medium"
4. **blockMessage**: Not needed (doesn't block)
5. **skipConditions**: Optional (less critical)
6. **promptTriggers**: Usually has extensive keywords
7. **fileTriggers**: May have only path patterns (content less important)

---

## Validation

### Check JSON Syntax

```bash
cat .claude/skills/skill-rules.json | jq .
```

If valid, jq will pretty-print the JSON. If invalid, it will show the error.

### Common JSON Errors

**Trailing comma:**
```json
{
  "keywords": ["one", "two",]  // ❌ Trailing comma
}
```

**Missing quotes:**
```json
{
  type: "guardrail"  // ❌ Missing quotes on key
}
```

**Single quotes (invalid JSON):**
```json
{
  'type': 'guardrail'  // ❌ Must use double quotes
}
```

### Validation Checklist

- [ ] JSON syntax valid (use `jq`)
- [ ] All skill names match SKILL.md filenames
- [ ] Guardrails have `blockMessage`
- [ ] Block messages use `{file_path}` placeholder
- [ ] Intent patterns are valid regex (test on regex101.com)
- [ ] File path patterns use correct glob syntax
- [ ] Content patterns escape special characters
- [ ] Priority matches enforcement level
- [ ] No duplicate skill names

---

**Related Files:**
- [SKILL.md](SKILL.md) - Main skill guide
- [TRIGGER_TYPES.md](TRIGGER_TYPES.md) - Complete trigger documentation
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Debugging configuration issues
