---
description: Start a new large-scale task with guided workflow (project:serena)
argument-hint: [task-name]
allowed-tools: mcp__serena__write_memory, mcp__serena__list_memories
---

# Start New Task

You are helping the user initialize a new large-scale development task.

## Task Name
Task: `$ARGUMENTS`

## Your Role
Guide the user through an interactive setup process to create a comprehensive task memory.

## Setup Process

### Step 1: Validate Task Name
- Check if a task with this name already exists using `list_memories`
- If exists, ask: "Task '$ARGUMENTS' already exists. View it, rename, or cancel?"
- If not exists, proceed to Step 2

### Step 2: Gather Background Information
Ask the user these questions one by one:

**Question 1: Background & Goals**
```
Please describe:
- What is this feature/task about?
- What problem does it solve?
- What are the key goals?
```

**Question 2: Scope & Constraints**
```
Please describe:
- Any technical constraints? (performance, compatibility, etc.)
- Any business constraints? (timeline, resources, etc.)
- What is OUT of scope?
```

**Question 3: Expected Outputs**
```
What deliverables are expected?
- New services/components?
- API changes?
- Documentation?
- Tests?
```

### Step 3: Create Task Memory
Create a new memory file with this structure:

```markdown
# Task: {task-name}

## Metadata
- Task ID: {task-name}
- Created: {current-date}
- Last Updated: {current-date}
- Current Stage: 1/6 (Requirements Design)
- Status: IN_PROGRESS

## Workflow Stages
- [ ] 1. Requirements Design
- [ ] 2. Requirements Review
- [ ] 3. Documentation
- [ ] 4. MVP Implementation
- [ ] 5. User Review + TODO Marking
- [ ] 6. TODO Execution

## Background & Goals
{user's answer to Question 1}

## Scope & Constraints
{user's answer to Question 2}

## Expected Outputs
{user's answer to Question 3}

## Key Design Decisions
{empty - to be filled during progress}

## TODO Items
{empty - to be filled during user review}

## Stage Outputs
### Stage 1: Requirements Design (Current)
{empty - to be filled}

## Review Notes
{empty - to be filled during user review}

## Archive Info
{empty - only filled when archived}
```

### Step 4: Confirm Creation
After creating the memory, show:

```
✅ Task created successfully!

📋 Task: {task-name}
🎯 Current Stage: 1/6 (Requirements Design)
📅 Created: {date}

Next Steps:
- Continue working on requirements design
- Use `/task-progress {task-name}` to update progress
- Use `/task-todo {task-name} add "item"` to add TODO items
- Use `/task-list` to see all active tasks

Ready to start? Let me know how I can help with the requirements design!
```

## Important Rules
- Be conversational and friendly during setup
- If user gives incomplete answers, ask follow-up questions
- Save the memory immediately after gathering all information
- Use ISO date format (YYYY-MM-DD)
- Task name should use kebab-case (e.g., export-feature, not Export Feature)
