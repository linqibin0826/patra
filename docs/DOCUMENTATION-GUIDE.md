# Documentation Maintenance Guide

> **Comprehensive guide** for maintaining and updating Papertrace documentation system.

---

## 📖 Table of Contents

1. [Overview](#overview)
2. [Documentation Architecture](#documentation-architecture)
3. [When to Update Documentation](#when-to-update-documentation)
4. [How to Update Documentation](#how-to-update-documentation)
5. [Common Scenarios](#common-scenarios)
6. [Standards and Conventions](#standards-and-conventions)
7. [Validation Checklist](#validation-checklist)
8. [Documentation Health Check](#documentation-health-check)
9. [Tools and Tips](#tools-and-tips)

---

## Overview

### Why This Approach?

Papertrace uses a **Living README Network** approach:

- **Living**: Documentation lives alongside code, evolves with it
- **README**: Each module has a README.md file
- **Network**: READMEs link to each other, forming a navigable web

**Why NOT a centralized /docs directory?**
- ❌ Centralized docs become stale (far from code)
- ❌ High maintenance burden (update in two places)
- ❌ Difficult to find relevant information

**Why this approach?**
- ✅ Documentation close to code (easier to keep in sync)
- ✅ Discoverable (README.md is first thing developers see)
- ✅ Lower maintenance burden (update in one place)
- ✅ Suitable for bootstrap phase (solo developer)

### Documentation Philosophy

1. **Pattern-focused, not code-detail focused**: Document "how" and "why", not "what" (code shows "what")
2. **Audience: Future you**: Write for yourself 3 months from now
3. **Minimal maintenance burden**: Only document what helps understanding
4. **Always up-to-date**: Update docs in same PR as code changes

---

## Documentation Architecture

### 3-Tier Structure

```
Tier 1: Core Documentation (Central /docs)
  ├─ ARCHITECTURE.md       # System architecture, design patterns
  ├─ DEV-GUIDE.md          # Code recipes for common tasks
  └─ DOCUMENTATION-GUIDE.md # This file

Tier 2: Module Documentation (Service/Shared/API READMEs)
  ├─ Service READMEs
  │  ├─ patra-registry/README.md
  │  ├─ patra-ingest/README.md
  │  └─ patra-gateway-boot/README.md
  │
  ├─ API Module READMEs
  │  ├─ patra-registry/patra-registry-api/README.md
  │  └─ patra-ingest/patra-ingest-api/README.md
  │
  ├─ Shared Module READMEs
  │  ├─ patra-common/README.md
  │  ├─ patra-expr-kernel/README.md
  │  └─ patra-parent/README.md
  │
  └─ Starter READMEs
     ├─ patra-spring-boot-starter-core/README.md
     ├─ patra-spring-boot-starter-web/README.md
     ├─ patra-spring-boot-starter-mybatis/README.md
     ├─ patra-spring-boot-starter-provenance/README.md
     ├─ patra-spring-boot-starter-expr/README.md
     └─ patra-spring-cloud-starter-feign/README.md

Tier 3: Package-Level Documentation (Complex Areas Only)
  ├─ patra-ingest/domain/model/README.md
  ├─ patra-ingest/app/usecase/plan/README.md
  ├─ patra-registry/domain/model/vo/provenance/README.md
  └─ patra-common/domain/README.md
```

### When to Create Each Tier?

| Tier | When to Create | Example |
|------|----------------|---------|
| **Tier 1** | Major architectural changes, new design patterns | Adding new gateway type, changing persistence strategy |
| **Tier 2** | New module created | New microservice, new starter, new API module |
| **Tier 3** | Complex package with 5+ classes AND non-obvious interactions | Plan orchestration with 7 phases, temporal configuration with 6 dimensions |

---

## When to Update Documentation

### Triggers (Update Documentation When...)

#### 1. New Module Created

**What to do**:
- Create Tier 2 README for the module
- Update root README.md (add to module table)
- If API module, create API README

**Files to update**:
- `/{module}/README.md` (new)
- `/README.md` (update module table)
- `/docs/ARCHITECTURE.md` (if affects architecture diagram)

#### 2. API Contract Changed

**What to do**:
- Update API module README
- Update service README (if integration pattern changes)
- Update examples in consuming service READMEs

**Files to update**:
- `/{service}/{service}-api/README.md`
- Consuming service READMEs (if integration example changes)

#### 3. New Use Case Added

**What to do**:
- Update service README (add to use case list)
- If complex (7+ steps), consider package-level README

**Files to update**:
- `/{service}/README.md` (add to use case section)
- `/{service}/app/usecase/{feature}/README.md` (if complex)

#### 4. Architectural Pattern Changed

**What to do**:
- Update ARCHITECTURE.md
- Update affected service READMEs
- Update DEV-GUIDE.md (if pattern recipe changes)

**Files to update**:
- `/docs/ARCHITECTURE.md`
- Service READMEs
- `/docs/DEV-GUIDE.md`

#### 5. Dependency Updated

**What to do**:
- Update patra-parent/README.md (version table)
- If breaking change, update affected READMEs

**Files to update**:
- `/patra-parent/README.md`
- Affected module READMEs

---

## How to Update Documentation

### Step-by-Step Process

#### Scenario 1: Adding a New Microservice

**Example**: Adding `patra-data` microservice.

**Steps**:

1. **Create service README**:
   ```bash
   touch patra-data/README.md
   ```

2. **Use template**:
   ```markdown
   # patra-data — {Brief description}

   > **{One-line purpose}**

   ## 📌 Purpose

   {Explain what this service does, 3-5 bullet points}

   ## 🏗️ Module Structure

   {Show directory tree}

   ## 🔑 Key Domain Concepts

   {Explain core aggregates, VOs, patterns}

   ## 🔄 Use Case Flow

   {Explain major use cases with sequence diagrams}

   ## 🔌 Cross-Module Integration

   {How does this service interact with others?}

   ## 🚀 Running Locally

   {Quick start commands}

   ## 🔗 Related Documentation

   {Links to other relevant docs}
   ```

3. **Update root README.md**:
   ```markdown
   ### Microservices

   | Module | Purpose | Entry Point |
   |--------|---------|-------------|
   | ... existing entries ... |
   | [**patra-data**](./patra-data/README.md) | Data storage and retrieval | `patra-data-boot` |
   ```

4. **Update ARCHITECTURE.md**:
   - Add to system diagram
   - Add to data flow
   - Add to module dependency graph

5. **Verify**:
   ```bash
   # Check all links work
   grep -r "patra-data" README.md docs/ARCHITECTURE.md
   ```

---

#### Scenario 2: Adding a New API Endpoint

**Example**: Adding `POST /api/provenance/plan` to registry service.

**Steps**:

1. **Update API module README**:
   - File: `patra-registry/patra-registry-api/README.md`
   - Add new endpoint到“API Contracts”
   - 增加 DTO 示例与用法

2. **Update service README**:
   - File: `patra-registry/README.md`
   - 在“API Contract”章节补充
   - 如需，更新集成示例

3. **Update consuming service READMEs**:
   - If pattern changes, update consumer examples
   - Example: Update `patra-ingest/README.md` if it uses new batch endpoint

4. **Verify**:
   ```bash
   # 确认文档一致
   grep -r "POST /api/provenance/plan" .
   ```

---

#### Scenario 3: Deprecating a Feature

**Example**: Deprecating `VOLUME` window type in favor of `CURSOR`.

**Steps**:

1. **Mark as deprecated**:
   ```markdown
   ### WindowSpec Types

   - ✅ `TIME`: Time-based windows
   - ✅ `CURSOR`: Cursor-based pagination
   - ⚠️ `VOLUME`: **DEPRECATED** (use `CURSOR` instead)
   - ✅ `SINGLE`: No windowing
   ```

2. **Add migration guide**:
   ```markdown
   ## Migration Guide: VOLUME → CURSOR

   **Before**:
   ```java
   WindowSpec.Volume(1000)
   ```

   **After**:
   ```java
   WindowSpec.CursorLandmark("cursor_value")
   ```

   **Why**: CURSOR provides better ...
   ```

3. **Update all examples**:
   - Search for `WindowSpec.Volume` references
   - Replace with recommended alternative

4. **Set removal date**:
   ```markdown
   ⚠️ **DEPRECATED**: Will be removed in v0.3.0 (2025-06-01)
   ```

---

#### Scenario 4: Complex Use Case Needs Package-Level Doc

**Example**: Adding batch planning logic to `patra-ingest`.

**Decision Tree**:
```
Is use case > 7 steps? → NO → Document in service README
                       ↓ YES
Does it span 5+ classes? → NO → Document in service README
                         ↓ YES
Are interactions non-obvious? → NO → Document in service README
                              ↓ YES
→ CREATE package-level README
```

**Steps**:

1. **Create package README**:
   ```bash
   touch patra-ingest/app/usecase/batch-planning/README.md
   ```

2. **Document**:
   - Phase-by-phase breakdown (7+ phases)
   - Key classes and responsibilities
   - Interaction diagrams
   - Code snippets for each phase

3. **Link from service README**:
   ```markdown
   ## 🔄 Use Case Flow

   ### Batch Planning

   Complex orchestration flow with 10 phases. See detailed breakdown:
   - [Batch Planning Deep Dive](./patra-ingest-app/usecase/batch-planning/README.md)
   ```

---

## Common Scenarios

### Adding a New Shared Library

**Example**: Adding `patra-cache` shared library.

**Checklist**:

- [ ] Create `patra-cache/README.md`
  - Purpose and scope
  - Key classes/interfaces
  - Usage examples
  - Configuration
- [ ] Update root `README.md`
  - Add to "Shared Libraries" table
- [ ] Update `patra-parent/README.md`
  - Add to dependency management table
- [ ] Update `docs/ARCHITECTURE.md`
  - Add to module dependency graph

**Template** (`patra-cache/README.md`):
```markdown
# patra-cache — Caching Abstractions

> Shared caching utilities and abstractions.

## 📌 Purpose

- Unified caching interface
- Redis/Caffeine implementations
- Cache key generation
- TTL management

## 🔧 Key Components

### CacheManager

```java
public interface CacheManager {
    <T> Optional<T> get(String key, Class<T> type);
    void put(String key, Object value, Duration ttl);
    void evict(String key);
}
```

## 🚀 Usage

```java
@Autowired
private CacheManager cacheManager;

public Config getConfig(String key) {
    return cacheManager.get(key, Config.class)
        .orElseGet(() -> loadAndCache(key));
}
```

## 🔗 Related Documentation

- [Main README](../README.md)
```

---

### Updating Error Codes

**Example**: Adding new error code `EGR-1005: Rate limit config invalid`.

**Checklist**:

- [ ] Update `{service}-api/error/{Service}Errors.java`
  - Add new error code constant
  - Add to error code table
- [ ] Update `{service}-api/README.md`
  - Add to error codes table
  - Update exception hierarchy if new exception class
- [ ] Update service README
  - Add to error handling section if pattern changes

---

### Renaming a Module

**Example**: Renaming `patra-gateway-boot` to `patra-api-gateway`.

**Checklist**:

- [ ] Rename directory and update POMs
- [ ] Rename README file (or update in-place)
- [ ] Update **all** references:
  ```bash
  # Find all references
  grep -r "patra-gateway-boot" .

  # Update:
  # - Root README.md (module table)
  # - docs/ARCHITECTURE.md (diagrams, dependency graph)
  # - All service READMEs (cross-references)
  # - API module READMEs (Feign client examples)
  ```
- [ ] Update links:
  ```bash
  # Update markdown links
  sed -i 's/patra-gateway-boot/patra-api-gateway/g' README.md
  ```

---

## Standards and Conventions

### Markdown Style

#### Headers

```markdown
# H1 — Module Title (one per file)

## 📌 H2 — Major Section (with emoji)

### H3 — Subsection

#### H4 — Detail (avoid if possible)
```

#### Emojis (Consistent Usage)

| Emoji | Meaning | Usage |
|-------|---------|-------|
| 📌 | Purpose | "Purpose" section |
| 🏗️ | Architecture | "Architecture" or "Module Structure" |
| 🔑 | Key Concepts | "Key Domain Concepts" |
| 🔄 | Flow/Process | "Use Case Flow", "Data Flow" |
| 🔌 | Integration | "Cross-Module Integration", "API Contracts" |
| 🚀 | Quick Start | "Running Locally", "Usage" |
| ⚙️ | Configuration | "Configuration" |
| 🛡️ | Security | "Security" |
| 📊 | Observability | "Metrics", "Logging", "Tracing" |
| 🧪 | Testing | "Testing" |
| ⚠️ | Warning | Deprecation notices, important notes |
| ✅ | Completed | Status indicators |
| 🚧 | In Progress | Status indicators |
| 📋 | Planned | Status indicators |
| 🔗 | Links | "Related Documentation" |

#### Code Blocks

Always specify language:

```markdown
```java
public class Example {}
```

```yaml
server:
  port: 8080
```

```bash
mvn clean install
```
```

#### Tables

Use tables for comparisons, reference lists:

```markdown
| Column 1 | Column 2 | Column 3 |
|----------|----------|----------|
| Value 1  | Value 2  | Value 3  |
```

#### Links

**Internal links** (relative paths):
```markdown
[Architecture Guide](./ARCHITECTURE.md)
[patra-registry README](../patra-registry/README.md)
```

**Anchor links** (within same file):
```markdown
[See Overview](#overview)
```

**External links** (absolute URLs):
```markdown
[Spring Boot Docs](https://spring.io/projects/spring-boot)
```

### Tone and Voice

- **Clear and Concise**: Avoid verbosity
- **Active Voice**: "Update the README" (not "The README should be updated")
- **Present Tense**: "The service handles..." (not "The service will handle...")
- **Professional**: No slang or colloquialisms
- **Helpful**: Explain "why", not just "what"

**Example (Good)**:
> The provenance starter centralizes provider-specific parameter mapping and adds minimal resilience. This keeps calling code simple and consistent.

**Example (Bad)**:
> It calls some external APIs and it's kinda robust.

### File Naming

- **README.md**: Always uppercase
- **ARCHITECTURE.md, DEV-GUIDE.md**: Uppercase for core docs
- **kebab-case** for other files: `error-handling-guide.md`

### Structure Template (Service README)

All service READMEs should follow this structure:

```markdown
# {service-name} — {Brief Description}

> **{One-line purpose}**

---

## 📌 Purpose

{3-5 bullet points explaining what this service does}

---

## 🏗️ Module Structure

{Directory tree showing packages}

---

## 🔑 Key Domain Concepts

{Explain core aggregates, VOs, patterns}

---

## 🔄 Use Case Flow

{Explain major use cases with sequence}

---

## 🔌 Cross-Module Integration

{How does this service interact with others?}

---

## 🗄️ Database Schema (if applicable)

{Tables, indexes, relationships}

---

## 🚀 Running Locally

{Quick start commands}

---

## 🔗 Related Documentation

{Links to other relevant docs}

---

**Last Updated**: {YYYY-MM-DD}
```

---

## Validation Checklist

Before committing documentation changes, verify:

### Content Checklist

- [ ] **Accuracy**: All code examples compile and run
- [ ] **Completeness**: All promised sections are present
- [ ] **Consistency**: Follows standards and conventions
- [ ] **Currency**: "Last Updated" date is current
- [ ] **Cross-references**: All links work (no 404s)

### Style Checklist

- [ ] Headers use correct emoji conventions
- [ ] Code blocks specify language
- [ ] Tone is clear, concise, professional
- [ ] No typos or grammatical errors
- [ ] Markdown renders correctly (preview in IDE)

### Structure Checklist

- [ ] README follows standard template
- [ ] Tier 1/2/3 placement is correct
- [ ] No duplicate information across files
- [ ] Related docs are linked

### Technical Checklist

- [ ] Code examples follow project conventions
- [ ] File paths are correct and up-to-date
- [ ] Dependency versions match pom.xml
- [ ] Error codes match actual implementation

---

## Documentation Health Check

Perform quarterly (or when team size changes):

### Audit Process

1. **Check for Staleness**:
   ```bash
   # Find READMEs not updated in 6 months
   find . -name "README.md" -mtime +180

   # Review each for accuracy
   ```

2. **Verify Links**:
   ```bash
   # Use a markdown link checker
   npm install -g markdown-link-check
   find . -name "*.md" -exec markdown-link-check {} \;
   ```

3. **Check Coverage**:
   ```bash
   # List all modules
   find . -name "pom.xml" -type f | grep -v target | wc -l

   # List all READMEs
   find . -name "README.md" -type f | grep -v target | wc -l

   # Should be roughly equal
   ```

4. **Review Feedback**:
   - Ask team: "Which docs are confusing?"
   - Check IDE analytics: Which READMEs are rarely opened?
   - Review PR comments: Are reviewers asking questions docs should answer?

### Metrics to Track

| Metric | Target | Current |
|--------|--------|---------|
| **Coverage** | >90% of modules have READMEs | 100% (23/23) |
| **Freshness** | >80% updated in last 3 months | TBD |
| **Link Health** | 100% links work | TBD |
| **Developer Satisfaction** | >80% find docs helpful | TBD (survey) |

---

## Tools and Tips

### AI Assistance

**Claude Code** (or other AI assistants) can help:

1. **Creating new READMEs**:
   - Prompt: "Analyze {module} code and create a README following our template"
   - Review: Always review AI-generated docs for accuracy

2. **Updating existing docs**:
   - Prompt: "Update {README} to reflect changes in {code files}"
   - Verify: Check links and examples

3. **Consistency checks**:
   - Prompt: "Review all READMEs for consistency with our standards"

**Best Practices**:
- ✅ Use AI to draft, human to verify
- ✅ Provide AI with DOCUMENTATION-GUIDE.md as context
- ✅ Review carefully for technical accuracy
- ❌ Don't blindly accept AI-generated content

### Automation Ideas

1. **Link Checker** (CI/CD):
   ```yaml
   # .github/workflows/docs-check.yml
   name: Documentation Link Check
   on: [push, pull_request]
   jobs:
     markdown-link-check:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v2
         - uses: gaurav-nelson/github-action-markdown-link-check@v1
   ```

2. **Freshness Reminder**:
   ```bash
   # docs/scripts/stale-docs-check.sh
   #!/bin/bash
   # Find READMEs not updated in 6 months
   find . -name "README.md" -mtime +180 -exec echo "Stale: {}" \;
   ```

3. **Template Generator**:
   ```bash
   # docs/scripts/new-module-readme.sh
   #!/bin/bash
   MODULE_NAME=$1
   cat > ${MODULE_NAME}/README.md <<EOF
   # ${MODULE_NAME} — {Brief Description}

   > **{One-line purpose}**

   ---

   ## 📌 Purpose

   {TODO: Fill in purpose}

   ---

   **Last Updated**: $(date +%Y-%m-%d)
   EOF
   echo "Created ${MODULE_NAME}/README.md"
   ```

### IDE Tips

**IntelliJ IDEA**:
- Install "Markdown" plugin for live preview
- Use "Find in Path" (Ctrl+Shift+F) to search across all docs
- Set up "File Watchers" to auto-format markdown on save

**VS Code**:
- Install "Markdown All in One" extension
- Use "Markdown Preview Enhanced" for better rendering
- Enable "markdownlint" for style checking

---

## Conclusion

**Remember**:
1. Documentation is code — treat it with same care
2. Update docs in same PR as code changes
3. Keep it simple — only document what helps understanding
4. Review quarterly — docs become stale quickly
5. Use this guide — bookmark and refer to it often

**Questions?**
- Check this guide first
- Ask team for clarification
- Update this guide with new patterns

---

**Last Updated**: 2025-01-12
**Maintainers**: All Papertrace developers
**Review Frequency**: Quarterly (every 3 months)
