---
description: Manage TODO items for a task (add/list/done/delete) (project:serena)
argument-hint: [task-name] [action] [details]
allowed-tools: mcp__serena__read_memory, mcp__serena__write_memory, mcp__serena__list_memories
---

# Task TODO Management

Managing TODO items for task: `$ARGUMENTS`

## Your Role
Parse the command arguments and execute the corresponding TODO operation.

## Command Syntax

The arguments follow this pattern:
- `$1` = task-name
- `$2` = action (add/list/done/delete/show)
- `$3+` = details (varies by action)

## Execution Steps

### Step 1: Parse Arguments

If no arguments:
```
❌ Usage: /task-todo [task-name] [action] [details]

Actions:
  add     - Add new TODO item
  list    - List all TODO items
  done    - Mark TODO as completed
  delete  - Delete a TODO item
  show    - Show TODO details

Examples:
  /task-todo export-feature add "Optimize performance" --priority=high
  /task-todo export-feature list
  /task-todo export-feature done 1
  /task-todo export-feature delete 3
```

If only task-name provided (e.g., `/task-todo export-feature`):
- Default to "list" action

### Step 2: Validate Task
- Use `list_memories` to check task exists
- If not found, show error with available tasks

### Step 3: Read Task Memory
- Use `read_memory("task_$1")` to get current task
- Parse TODO section

### Step 4: Execute Action

#### Action: ADD
**Syntax**: `/task-todo [task] add "description" [--priority=high|medium|low] [--location=file:line]`

Process:
1. Extract description from arguments
2. Extract optional flags (--priority, --location)
3. Generate TODO ID (sequential number)
4. Add to TODO section:
   ```
   - [ ] {id}. {description}
     - Priority: {HIGH|MEDIUM|LOW} (default: MEDIUM)
     - Location: {file:line} (if provided)
     - Created: {date}
     - Status: PENDING
   ```
5. Update memory
6. Confirm:
   ```
   ✅ TODO added to task 'export-feature'

   [ ] 3. Optimize performance
       Priority: HIGH
       Created: 2025-01-16

   Total TODOs: 3 (2 pending, 1 done)
   ```

#### Action: LIST
**Syntax**: `/task-todo [task] list [--status=pending|done|all]`

Process:
1. Filter TODOs by status (default: all)
2. Display formatted list:
   ```
   📝 TODO List for 'export-feature'
   ==================================================

   ⏳ PENDING (2):
     [ ] 1. Optimize export performance (HIGH)
         Location: ExportService.java:45
         Created: 2025-01-16

     [ ] 2. Add unit tests (MEDIUM)
         Created: 2025-01-16

   ✅ COMPLETED (1):
     [x] 3. Implement CSV formatter
         Completed: 2025-01-15

   ==================================================
   Summary: 2 pending, 1 completed, 3 total
   ```

#### Action: DONE
**Syntax**: `/task-todo [task] done [id]`

Process:
1. Find TODO by ID
2. If not found, show error
3. Mark as completed:
   - Change `[ ]` to `[x]`
   - Update Status: PENDING → COMPLETED
   - Add Completed date
4. Update memory
5. Confirm:
   ```
   ✅ Marked as done: Optimize export performance

   Remaining: 1 pending TODO
   ```

#### Action: DELETE
**Syntax**: `/task-todo [task] delete [id]`

Process:
1. Find TODO by ID
2. Confirm: "Delete TODO #1 'Optimize performance'? (y/n)"
3. If confirmed, remove from list
4. Update memory
5. Confirm:
   ```
   ✅ Deleted TODO: Optimize performance

   Remaining: 2 TODOs
   ```

#### Action: SHOW
**Syntax**: `/task-todo [task] show [id]`

Process:
1. Find TODO by ID
2. Display full details:
   ```
   📝 TODO #1
   ==================================================
   Description: Optimize export performance
   Priority: HIGH
   Location: ExportService.java:45
   Created: 2025-01-16
   Status: PENDING

   Related notes:
   - User reported slow export for 10k+ records
   - Consider using streaming approach
   ==================================================
   ```

### Step 5: Update Last Modified Date
- Always update "Last Updated" in task metadata when changing TODOs

## Important Rules
- TODO IDs are sequential and never reused
- Preserve all TODO metadata (priority, location, dates)
- When marking done, preserve the creation date
- Use ISO date format (YYYY-MM-DD)
- If no priority specified, default to MEDIUM
- Support both `[ ]` and `[x]` checkbox format
- Keep the memory structure intact
