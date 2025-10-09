---
description: Archive a completed task for historical reference (project:serena)
argument-hint: [task-name]
allowed-tools: mcp__serena__read_memory, mcp__serena__write_memory, mcp__serena__list_memories
---

# Task Archive

Archive task: `$ARGUMENTS`

## Your Role
Mark a task as archived while preserving all historical data for future reference.

## Execution Steps

### Step 1: Validate Task
- Use `list_memories` to check task exists
- Use `read_memory("task_$ARGUMENTS")` to get task content
- If not found, show error

### Step 2: Verify Task is Complete
Check task status and provide appropriate feedback:

**If Status = COMPLETED or all stages done:**
```
✅ Task ready for archive
```

**If Status = IN_PROGRESS or stages incomplete:**
```
⚠️  Warning: Task 'export-feature' is not marked as completed.

Current Status: IN_PROGRESS
Current Stage: 4/6 - MVP Implementation
Pending TODOs: 2

Archive anyway? This will move the task to archived state. (yes/no)
```

**If user says no:**
```
Archive cancelled. Use `/task-progress $ARGUMENTS` to complete remaining work.
```

### Step 3: Prepare Archive Data
Add archive metadata to the task memory:

```markdown
## Archive Info
- Archive Date: {current-date}
- Archive Reason: {ask user, or default to "Task completed"}
- Final Status: {COMPLETED/PARTIAL/CANCELLED}
- Duration: {days from created to archived}
- Final Stage: {current-stage}
- Final TODO Count: {completed}/{total}

## Archive Summary
{ask user for brief summary of outcomes}

Key Achievements:
- {extract from stage outputs}

Lessons Learned:
{ask user, optional}

References:
- Related PRs: {ask user, optional}
- Documentation: {ask user, optional}
```

### Step 4: Interactive Archive Process

**Question 1: Archive Reason** (optional)
```
Why are you archiving this task?
- Task completed successfully
- Task cancelled/deprioritized
- Task partially done but paused
- Other (specify)

Press Enter for default: "Task completed successfully"
```

**Question 2: Outcomes Summary** (recommended)
```
Please provide a brief summary of what was accomplished:
(This helps when reviewing archived tasks later)

Example:
- Successfully implemented export feature with CSV/Excel support
- Achieved 95% test coverage
- Documented in export-api.md

Your summary:
```

**Question 3: Lessons Learned** (optional)
```
Any lessons learned or notes for future similar tasks?
(Press Enter to skip)
```

**Question 4: References** (optional)
```
Add any references (PRs, docs, tickets)?
(Press Enter to skip)

Example:
- PR #123: Main implementation
- docs/export-api.md: API documentation
```

### Step 5: Update Task Status
Modify the task memory:
1. Change Status: IN_PROGRESS → ARCHIVED
2. Add Archive Info section with all metadata
3. Update Last Updated date
4. Keep all original content intact

### Step 6: Confirm Archive
```
✅ Task archived successfully!

📦 Task: export-feature
📅 Archived: 2025-01-16
⏱️  Duration: 12 days
🎯 Final Stage: 6/6 - Completed
✅ TODOs: 3/3 completed

Archive Summary:
Successfully implemented export feature with CSV/Excel support.
Achieved 95% test coverage. Documented in export-api.md.

The task memory has been preserved in:
.serena/memories/task_export_feature.md

View archived tasks: /task-list --status=archived
View this task details: /task-progress export-feature
```

## Important Rules

### What Gets Archived
- ✅ Complete task history
- ✅ All design decisions
- ✅ All TODO items (completed and pending)
- ✅ Stage outputs
- ✅ Review notes
- ✅ Archive metadata

### What Changes
- Status: → ARCHIVED
- Last Updated: → Archive date
- Archive Info section: → Added
- Nothing else changes

### Archive vs Delete
**Archive** (this command):
- Preserves all data
- Task stays in memory for reference
- Shows in `/task-list --status=archived`
- Can be reviewed anytime

**Delete** (use Serena's delete_memory):
- Removes completely
- Cannot be recovered
- Use ONLY for mistakes/duplicates

### Unarchive (Reopen)
If user needs to reopen an archived task:
```
To reopen archived task 'export-feature':
1. Use /task-progress export-feature
2. Choose option to change status
3. Update status from ARCHIVED to IN_PROGRESS

Or manually edit the memory file.
```

## Display Format
When showing archived tasks in other commands:
```
📦 export-feature (ARCHIVED)
   Archived: 2025-01-16 (12 days)
   Final Stage: 6/6 - Completed
   TODOs: 3/3 completed
```