---
description: List all tasks with status overview (project:serena)
argument-hint: [--status=active|archived|all]
allowed-tools: mcp__serena__list_memories, mcp__serena__read_memory
---

# Task List Overview

Display all tasks in the project.

## Your Role
Scan all task memories and present a comprehensive overview.

## Execution Steps

### Step 1: Parse Arguments
- Check for `--status=` flag
- Default: `--status=active` (only active tasks)
- Options: active, archived, all

### Step 2: Scan Task Memories
- Use `list_memories` to get all memory files
- Filter files matching pattern: `task_*.md`
- Separate into active and archived based on status

### Step 3: Read Task Summaries
For each task memory:
- Use `read_memory` to get content
- Extract key metadata:
  - Task name
  - Current stage
  - Status (IN_PROGRESS, BLOCKED, REVIEW, etc.)
  - Created date
  - Last updated date
  - TODO count (pending/total)
  - Completion percentage

### Step 4: Display Task List

#### Format: Grouped by Status

```
📋 Active Tasks (2)
==================================================

🔄 export-feature (IN PROGRESS)
   Stage: 4/6 - MVP Implementation
   TODOs: 2 pending, 3 total
   Created: 2025-01-15 | Updated: 2025-01-16
   Progress: ████████████░░░░░░░░ 67%

🔄 analytics-dashboard (IN PROGRESS)
   Stage: 2/6 - Requirements Review
   TODOs: 0 pending, 0 total
   Created: 2025-01-10 | Updated: 2025-01-14
   Progress: ████░░░░░░░░░░░░░░░░ 33%

==================================================


📦 Archived Tasks (1)
==================================================

✅ user-authentication (COMPLETED)
   Completed: 2025-01-05
   Duration: 12 days
   Archive Date: 2025-01-06

==================================================

Summary:
  Active: 2 tasks
  Archived: 1 task
  Total: 3 tasks

Commands:
  /task-progress [name] - View/update task details
  /task-todo [name] list - View task TODOs
  /task-archive [name] - Archive completed task
```

### Step 5: Handle Empty States

**No Active Tasks**:
```
📋 No active tasks found.

Start a new task with:
  /task-start [task-name]

Or view archived tasks:
  /task-list --status=archived
```

**No Archived Tasks**:
```
📦 No archived tasks.

Archive a completed task with:
  /task-archive [task-name]
```

### Step 6: Additional Filters (Optional)

Support additional filters:
- `--stage=[1-6]` - Filter by current stage
- `--priority=high` - Show high-priority tasks
- `--recent=[days]` - Tasks updated in last N days

Example:
```bash
/task-list --recent=7
# Shows tasks updated in last 7 days
```

## Display Rules

### Progress Calculation
```
progress = (completed_stages / total_stages) * 100
```

### Progress Bar
- 20 characters total
- Filled: █ (completed)
- Empty: ░ (remaining)

### Status Colors (use emojis)
- 🔄 IN PROGRESS
- ⏸️  BLOCKED
- 👀 REVIEW
- ✅ COMPLETED
- ❌ CANCELLED
- 📦 ARCHIVED

### Sorting
- Active tasks: Sort by last updated (newest first)
- Archived tasks: Sort by archive date (newest first)

## Important Rules
- Only show task memories (ignore other memories like tech_stack.md)
- Parse task status from "Status:" field in metadata
- If a task has no updates in 30+ days, add a ⚠️ warning
- Keep the display concise and scannable
- Calculate progress percentage accurately