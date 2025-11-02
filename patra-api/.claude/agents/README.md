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

## 🔍 Agent Selection Decision Tree

Not sure which agent to use? Follow this decision tree:

### 📝 Refactoring Scenarios
- **Need refactoring plan?** → `refactor-planner`
  - Analyze code structure
  - Identify code smells
  - Output detailed refactoring plan document

- **Execute refactoring?** → `code-refactor-master`
  - Read refactoring plan or analyze independently
  - Execute file moves, class splitting
  - Update all dependencies and import paths

### 🔎 Review Scenarios
- **Review development plan?** → `plan-reviewer`
  - **Pre-implementation review**: Before coding starts
  - Identify potential issues and technical risks
  - Provide alternative approach suggestions

- **Review written code?** → `code-architecture-reviewer`
  - **Post-implementation review**: After code completion
  - Check architectural compliance (Hexagonal + DDD)
  - Verify best practices and code quality

### 🐛 Error Handling
- **Compilation errors?** → `compile-error-resolver`
  - Maven build failures
  - Layer boundary violations (e.g., Spring annotations in Domain layer)
  - Missing dependencies, import errors

- **Runtime errors?** → `runtime-error-diagnostic`
  - Analyze log files (`logs/patra-*.log`)
  - Query SkyWalking traces
  - Dynamically adjust log levels
  - May invoke `compile-error-resolver` for compilation issues

### 📚 Documentation & Research
- **Create/update documentation?** → `documentation-architect`
  - API documentation, developer guides
  - Architecture docs, data flow diagrams

- **Technical research?** → `web-research-specialist`
  - Find error solutions
  - Research best practices
  - Compare technical approaches

### ✅ Test Generation & Review
- **Generate test code?** → `test-architect`
  - Identify code patterns (Orchestrator, Event Handler, Repository, etc.)
  - Generate unit tests, integration tests
  - Review test coverage
  - Follow testing pyramid principles

---

## Available Agents (9)

### runtime-error-diagnostic ⭐ NEW
**Purpose:** Runtime error diagnostic and troubleshooting for Papertrace issues

**When to use:**
- Debugging production errors
- Analyzing application logs (`logs/` directory)
- Investigating SkyWalking traces
- Performance troubleshooting
- Dynamically adjusting log levels
- Systematic runtime error investigation

**Features:**
- Analyzes logs from `logs/patra-*.log` files
- Extracts SkyWalking trace information
- Dynamically enables DEBUG logging via Spring Boot Actuator
- Integrates with compile-error-resolver for compilation errors
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

### test-architect ⭐ NEW
**Purpose:** Generate and review tests for Hexagonal Architecture + DDD patterns

**When to use:**
- After implementing new features (Orchestrators, Event Handlers, Repositories)
- Reviewing test coverage
- Generating unit tests for domain logic
- Creating integration tests for database operations
- Writing tests for REST endpoints or scheduled jobs
- Following testing pyramid (70% unit, 25% integration, 5% E2E)

**Features:**
- Automatically identifies code patterns (Orchestrator, Event Handler, Repository, etc.)
- Recommends correct test strategy (unit vs integration)
- Generates complete test classes with proper mocking
- Follows testing-guide.md best practices
- Provides test coverage analysis
- Uses AAA pattern (Arrange-Act-Assert) and AssertJ assertions

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

### compile-error-resolver
**Purpose:** Automatically fix Java/Maven compilation errors in Spring Boot projects

**When to use:**
- Maven compilation failures
- After refactoring that breaks compilation
- Hexagonal Architecture layer violations
- Missing imports or incorrect dependencies
- Systematic compilation error resolution needed

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

**compile-error-resolver:**
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
| runtime-error-diagnostic | High | ⚠️ Actuator | Spring Boot Actuator |
| code-architecture-reviewer | Medium | ✅ None | No |
| code-refactor-master | High | ✅ None | No |
| documentation-architect | Medium | ✅ None | No |
| test-architect | Medium | ✅ None | No |
| plan-reviewer | Low | ✅ None | No |
| refactor-planner | Medium | ✅ None | No |
| web-research-specialist | Low | ✅ None | No |
| compile-error-resolver | Medium | ⚠️ Maven + hooks | Maven in PATH |

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
5. **For compile-error-resolver:** Ensure Maven is installed and recommend setting up hooks

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

---

## 💡 使用最佳实践

### Agent 协作工作流示例

**完整重构流程：**
```bash
1. refactor-planner           # 生成 refactor-plan.md
2. plan-reviewer              # 审查计划可行性
3. code-refactor-master       # 执行重构
4. code-architecture-reviewer # 审查重构结果
```

**错误诊断 → 修复流程：**
```bash
1. runtime-error-diagnostic  # 诊断运行时错误
   ↓（如发现编译问题）
2. compile-error-resolver    # 修复编译错误
   ↓
3. runtime-error-diagnostic  # 重新验证
```

### 明确职责，避免混淆

| 容易混淆的 Agent | 核心区别 | 选择标准 |
|----------------|---------|---------|
| `refactor-planner` vs `code-refactor-master` | **规划** vs **执行** | 需要计划文档？用 planner；直接重构？用 master |
| `plan-reviewer` vs `code-architecture-reviewer` | **事前** vs **事后** | 代码还没写？用 plan-reviewer；代码写完了？用 code-architecture-reviewer |
| `compile-error-resolver` vs `runtime-error-diagnostic` | **编译期** vs **运行期** | Maven 编译失败？用 compile-error-resolver；程序运行报错？用 runtime-error-diagnostic |

### Agent 调用技巧

**显式调用（推荐）：**
```
Use the code-architecture-reviewer agent to review my recent changes
```

**隐式触发：**
```
I just finished implementing ProvenanceOrchestrator, please review it
（Claude 会自动选择 code-architecture-reviewer）
```

**链式调用：**
```
First use refactor-planner to analyze UserService,
then use code-refactor-master to execute the refactoring
```

---
