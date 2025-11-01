# Agents

Specialized agents for complex, multi-step tasks.

---

## What Are Agents?

Agents are autonomous Claude instances that handle specific complex tasks. Unlike skills (which provide inline guidance), agents:
- Run as separate sub-tasks
- Work autonomously with minimal supervision
- Have specialized tool access
- Return comprehensive reports when complete

**Key advantage:** Agents are **standalone** - just copy the `.md` file and use immediately!

---

## Available Agents (8)

### comprehensive-error-diagnostic ⭐ NEW
**Purpose:** Comprehensive error diagnostic and troubleshooting for Papertrace issues

**When to use:**
- Debugging production errors
- Analyzing application logs (`logs/` directory)
- Investigating SkyWalking traces
- Performance troubleshooting
- Dynamically adjusting log levels
- Systematic error investigation

**Features:**
- Analyzes logs from `logs/patra-*.log` files
- Extracts SkyWalking trace information
- Dynamically enables DEBUG logging via Spring Boot Actuator
- Integrates with auto-error-resolver for compilation errors
- References papertrace-domain troubleshooting guide
- Provides structured diagnostic reports

**Integration:** ⚠️ Requires Spring Boot Actuator endpoints enabled

---

### code-architecture-reviewer
**Purpose:** Review code for architectural consistency, best practices, and Hexagonal Architecture compliance (Java/Spring Boot)

**When to use:**
- After implementing a new feature
- Before merging significant changes
- When refactoring code
- To validate architectural decisions and DDD patterns
- To ensure proper layer boundaries in Hexagonal Architecture

**Integration:** ✅ Copy as-is

---

### code-refactor-master
**Purpose:** Plan and execute comprehensive refactoring (language-agnostic)

**When to use:**
- Reorganizing file structures
- Breaking down large classes/modules
- Updating import paths after moves
- Improving code maintainability
- Eliminating code duplication

**Integration:** ✅ Copy as-is

---

### documentation-architect
**Purpose:** Create comprehensive documentation

**When to use:**
- Documenting new features
- Creating API documentation
- Writing developer guides
- Generating architectural overviews

**Integration:** ✅ Copy as-is

---

### plan-reviewer
**Purpose:** Review development plans before implementation

**When to use:**
- Before starting complex features
- Validating architectural plans
- Identifying potential issues early
- Getting second opinion on approach

**Integration:** ✅ Copy as-is

---

### refactor-planner
**Purpose:** Create comprehensive refactoring strategies

**When to use:**
- Planning code reorganization
- Modernizing legacy code
- Breaking down large files
- Improving code structure

**Integration:** ✅ Copy as-is

---

### web-research-specialist
**Purpose:** Research technical issues online

**When to use:**
- Debugging obscure errors
- Finding solutions to problems
- Researching best practices
- Comparing implementation approaches

**Integration:** ✅ Copy as-is

---

### auto-error-resolver
**Purpose:** Automatically fix Java/Maven compilation errors in Spring Boot projects

**When to use:**
- Maven compilation failures
- After refactoring that breaks compilation
- Hexagonal Architecture layer violations
- Missing imports or incorrect dependencies
- Systematic error resolution needed

**Integration:** ⚠️ Requires Maven and works with maven-compile-check.sh hook

---

## How to Integrate an Agent

### Standard Integration (Most Agents)

**Step 1: Copy the file**
```bash
cp showcase/.claude/agents/agent-name.md \\
   your-project/.claude/agents/
```

**Step 2: Verify (optional)**
```bash
# Check for hardcoded paths
grep -n "~/git/\|/root/git/\|/Users/" your-project/.claude/agents/agent-name.md
```

**Step 3: Use it**
Ask Claude: "Use the [agent-name] agent to [task]"

That's it! Agents work immediately.

---

### Agents Requiring Customization

**auto-error-resolver:**
- Requires Maven to be installed and in PATH
- Works best with maven-compile-check.sh hook (see `.claude/hooks/`)
- Expects Maven multi-module project structure
- Will read `.last-compile-failed` marker file from hooks

---

## When to Use Agents vs Skills

| Use Agents When... | Use Skills When... |
|-------------------|-------------------|
| Task requires multiple steps | Need inline guidance |
| Complex analysis needed | Checking best practices |
| Autonomous work preferred | Want to maintain control |
| Task has clear end goal | Ongoing development work |
| Example: "Review all controllers" | Example: "Creating a new route" |

**Both can work together:**
- Skill provides patterns during development
- Agent reviews the result when complete

---

## Agent Quick Reference

| Agent | Complexity | Customization | Requirements |
|-------|-----------|---------------|--------------|
| comprehensive-error-diagnostic | High | ⚠️ Actuator | Spring Boot Actuator |
| code-architecture-reviewer | Medium | ✅ None | No |
| code-refactor-master | High | ✅ None | No |
| documentation-architect | Medium | ✅ None | No |
| plan-reviewer | Low | ✅ None | No |
| refactor-planner | Medium | ✅ None | No |
| web-research-specialist | Low | ✅ None | No |
| auto-error-resolver | Medium | ⚠️ Maven + hooks | Maven in PATH |

---

## For Claude Code

**When integrating agents for a user:**

1. **Read [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)**
2. **Just copy the .md file** - agents are standalone
3. **Check for hardcoded paths:**
   ```bash
   grep "~/git/\|/root/" agent-name.md
   ```
4. **Update paths if found** to `$CLAUDE_PROJECT_DIR` or `.`
5. **For auto-error-resolver:** Ensure Maven is installed and recommend setting up hooks

**That's it!** Agents are the easiest components to integrate.

---

## Creating Your Own Agents

Agents are markdown files with optional YAML frontmatter:

```markdown
# Agent Name

## Purpose
What this agent does

## Instructions
Step-by-step instructions for autonomous execution

## Tools Available
List of tools this agent can use

## Expected Output
What format to return results in
```

**Tips:**
- Be very specific in instructions
- Break complex tasks into numbered steps
- Specify exactly what to return
- Include examples of good output
- List available tools explicitly

---

## Troubleshooting

### Agent not found

**Check:**
```bash
# Is agent file present?
ls -la .claude/agents/[agent-name].md
```

### Agent fails with path errors

**Check for hardcoded paths:**
```bash
grep "~/\|/root/\|/Users/" .claude/agents/[agent-name].md
```

**Fix:**
```bash
sed -i 's|~/git/.*project|$CLAUDE_PROJECT_DIR|g' .claude/agents/[agent-name].md
```

---

## Next Steps

1. **Browse agents above** - Find ones useful for your work
2. **Copy what you need** - Just the .md file
3. **Ask Claude to use them** - "Use [agent] to [task]"
4. **Create your own** - Follow the pattern for your specific needs

**Questions?** See [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)
