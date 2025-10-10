---
description: Check and sync all Serena memories with current codebase (project:serena)
allowed-tools: mcp__serena__list_memories, mcp__serena__read_memory, mcp__serena__write_memory, Read(//Users/linqibin/Desktop/Papertrace-api/**), Glob
---

# Serena Memory Sync

You are performing a comprehensive memory synchronization check for the Papertrace-api project.

## Your Task

Execute the following steps systematically:

### Step 1: List All Memories
- Use `list_memories` to get all current memory files

### Step 2: Analyze Each Memory
For each memory file:
1. Use `read_memory` to get current content
2. Identify what the memory is about (tech stack, conventions, structure, etc.)
3. Check relevant files in the codebase to verify accuracy:
   - For `tech_stack.md`: Check `patra-parent/pom.xml` for versions
   - For `codebase_structure.md`: Check root `pom.xml` for modules
   - For `style_and_conventions.md`: Check actual code patterns
   - For `suggested_commands.md`: Verify commands still work
   - For `task_completion.md`: Check if workflow still matches practices
   - For `project_overview.md`: Verify architecture hasn't changed

### Step 3: Report Findings
Present findings in this format:

```
📊 Memory Sync Analysis
=======================

✅ ACCURATE (no changes needed):
  - project_overview.md
  - style_and_conventions.md

⚠️ NEEDS UPDATE (found discrepancies):
  - tech_stack.md
    Change: Spring Boot version 3.2.4 → 3.3.0
    Evidence: patra-parent/pom.xml line 15

  - codebase_structure.md
    Change: Missing new module 'patra-analytics'
    Evidence: root pom.xml shows new module added

🗑️ POSSIBLY OBSOLETE (consider deletion):
  - refactoring_plan.md
    Reason: Refactoring completed 2 weeks ago

=======================
Summary: 2 memories need update, 0 need deletion
```

### Step 4: Wait for User Confirmation
After presenting the report, ask:

```
Would you like me to proceed with updating the 2 memory files?
Type 'yes' to update, 'no' to skip, or specify which ones to update.
```

### Step 5: Execute Updates (Only After Confirmation)
If user confirms:
- Use `write_memory` to update each approved memory
- Show what was updated:
  ```
  ✅ Updated tech_stack.md
  ✅ Updated codebase_structure.md

  Sync complete! All memories are now up-to-date.
  ```

## Important Rules
- **NEVER** update memories without explicit user confirmation
- Show clear evidence (file paths, line numbers) for each proposed change
- If uncertain about a change, mark it as "⚠️ REVIEW NEEDED" and ask user
- Focus on factual discrepancies, not stylistic improvements
- Preserve the original structure and tone of each memory
