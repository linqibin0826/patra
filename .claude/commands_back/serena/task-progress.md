---
description: View and update task progress, stages, and decisions (project:serena)
argument-hint: [task-name]
allowed-tools: mcp__serena__read_memory, mcp__serena__write_memory, mcp__serena__list_memories
---

# Task Progress Management

You are helping the user manage progress for task: `$ARGUMENTS`

## Your Role
Display current task status and provide interactive options for updates.

## Execution Steps

### Step 1: Validate Task Exists
- Use `list_memories` to check for task memory files (pattern: `task_*.md`)
- If task `$ARGUMENTS` doesn't exist, show:
  ```
  ❌ Task '$ARGUMENTS' not found.

  Available tasks:
  - task-a
  - task-b

  Use `/task-start $ARGUMENTS` to create it, or check the task name.
  ```

### Step 2: Read Current Task State
- Use `read_memory("task_$ARGUMENTS")` to get current content
- Parse the memory to extract:
  - Current stage (e.g., "3/6 Documentation")
  - Completed stages
  - Pending stages
  - TODO count (completed vs total)
  - Last updated date
  - Key decisions count

### Step 3: Display Current Status
Show a well-formatted status report:

```
📋 Task: {task-name}
==================================================

📊 Progress: Stage {current-stage}/6
🎯 Current Stage: {stage-name}
📅 Last Updated: {date}
⏱️  Duration: {days since creation} days

Workflow:
  ✅ 1. Requirements Design (completed {date})
  ✅ 2. Requirements Review (completed {date})
  ✅ 3. Documentation (completed {date})
  🔄 4. MVP Implementation (IN PROGRESS)
  ⏳ 5. User Review + TODO Marking
  ⏳ 6. TODO Execution

📝 TODO Items: {completed}/{total}
  ✅ Implement core export logic
  ✅ Add CSV formatter
  ⏳ Optimize performance
  ⏳ Add unit tests

💡 Key Decisions: {count}
  - Use streaming export for large datasets
  - Support CSV/Excel/JSON formats

==================================================
```

### Step 4: Present Action Menu
After displaying status, show interactive options:

```
What would you like to do?

  1. ✅ Complete current stage and move to next
  2. 📝 Add stage outputs/notes
  3. 💡 Record a design decision
  4. 🔄 Change stage (back/forward)
  5. 📊 Show detailed view
  6. ✏️  Update task metadata
  7. ❌ Cancel (no changes)

Choose 1-7 or describe your action:
```

### Step 5: Handle User Actions

**Action 1: Complete Current Stage**
- Mark current stage as completed with date
- Move to next stage
- Ask: "Any outputs or notes for this completed stage?"
- Update memory with new status

**Action 2: Add Stage Outputs**
- Ask: "What outputs or notes for current stage?"
- Append to "Stage Outputs" section
- Example outputs:
  - Files created
  - Key code locations
  - Important findings

**Action 3: Record Design Decision**
- Ask: "What decision was made?"
- Ask: "What was the reasoning?"
- Ask: "Any trade-offs or alternatives considered?"
- Add to "Key Design Decisions" section

**Action 4: Change Stage**
- Show stage list with numbers
- Ask: "Which stage to switch to? (1-6)"
- Confirm: "Are you sure? This will change progress tracking."
- Update current stage

**Action 5: Show Detailed View**
- Display full memory content in readable format
- Include all sections

**Action 6: Update Metadata**
- Allow editing task description, constraints, goals
- Confirm changes before saving

**Action 7: Cancel**
- Exit without changes

### Step 6: Confirm Updates
After any modification:
```
✅ Task updated successfully!

📋 Task: {task-name}
🎯 New Stage: {new-stage}
📅 Updated: {date}

Use `/task-progress {task-name}` anytime to check status.
```

## Important Rules
- Always show current status before asking for actions
- Preserve ISO date format (YYYY-MM-DD)
- When completing a stage, automatically update "Last Updated" date
- Keep the memory structure intact
- Be conversational and helpful
- If user wants to add multiple items, loop the action menu