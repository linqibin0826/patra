# Agents

用于复杂、多步骤任务的专业化 agent。

---

## 什么是 Agent?

Agent 是处理特定复杂任务的自主 Claude 实例。与 skill（提供内联指导）不同，agent:
- 作为独立的子任务运行
- 在最少监督下自主工作
- 拥有专门的工具访问权限
- 完成后返回全面的报告

**关键优势:** Agent 是**独立的** - 只需复制 `.md` 文件即可立即使用！

---

## 🔍 Agent 选择决策树

不确定使用哪个 agent? 遵循此决策树:

### 📝 重构场景
- **需要重构计划?** → `refactor-planner`
  - 分析代码结构
  - 识别代码坏味道
  - 输出详细的重构计划文档

- **执行重构?** → `code-refactor-master`
  - 读取重构计划或独立分析
  - 执行文件移动、类拆分
  - 更新所有依赖和导入路径

### 🔎 审查场景
- **审查开发计划?** → `plan-reviewer`
  - **实施前审查**: 在编码开始之前
  - 识别潜在问题和技术风险
  - 提供替代方法建议

- **审查已编写的代码?** → `code-architecture-reviewer`
  - **实施后审查**: 代码完成后
  - 检查架构合规性 (六边形架构 + DDD)
  - 验证最佳实践和代码质量

### 🐛 错误处理
- **编译错误?** → `compile-error-resolver`
  - Maven 构建失败
  - 层边界违规 (例如 Domain 层中的 Spring 注解)
  - 缺少依赖、导入错误

- **运行时错误?** → `runtime-error-diagnostic`
  - 分析日志文件 (`logs/patra-*.log`)
  - 查询 SkyWalking 追踪
  - 动态调整日志级别
  - 可能调用 `compile-error-resolver` 处理编译问题

### 📚 文档和研究
- **创建/更新文档?** → `documentation-architect`
  - API 文档、开发者指南
  - 架构文档、数据流图

- **技术研究?** → `web-research-specialist`
  - 查找错误解决方案
  - 研究最佳实践
  - 比较技术方法

### ✅ 测试生成和审查
- **生成测试代码?** → `test-architect`
  - 识别代码模式 (Orchestrator, Event Handler, Repository 等)
  - 生成单元测试、集成测试
  - 审查测试覆盖率
  - 遵循测试金字塔原则

---

## 可用的 Agent (9个)

### runtime-error-diagnostic ⭐ 新增
**目的:** Papertrace 问题的运行时错误诊断和故障排除

**何时使用:**
- 调试生产错误
- 分析应用日志 (`logs/` 目录)
- 调查 SkyWalking 追踪
- 性能故障排除
- 动态调整日志级别
- 系统性运行时错误调查

**功能:**
- 分析 `logs/patra-*.log` 文件中的日志
- 提取 SkyWalking 追踪信息
- 通过 Spring Boot Actuator 动态启用 DEBUG 日志
- 与 compile-error-resolver 集成处理编译错误
- 引用 papertrace-domain 故障排除指南
- 提供结构化诊断报告

**集成:** ⚠️ 需要启用 Spring Boot Actuator 端点

---

### code-architecture-reviewer
**目的:** 审查代码的架构一致性、最佳实践和六边形架构合规性 (Java/Spring Boot)

**何时使用:**
- 实现新功能后
- 合并重大更改前
- 重构代码时
- 验证架构决策和 DDD 模式
- 确保六边形架构中的适当层边界

**集成:** ✅ 原样复制

---

### code-refactor-master
**目的:** 规划和执行全面的重构 (语言无关)

**何时使用:**
- 重组文件结构
- 拆分大型类/模块
- 移动后更新导入路径
- 提高代码可维护性
- 消除代码重复

**集成:** ✅ 原样复制

---

### documentation-architect
**目的:** 创建全面的文档

**何时使用:**
- 记录新功能
- 创建 API 文档
- 编写开发者指南
- 生成架构概览

**集成:** ✅ 原样复制

---

### test-architect ⭐ 新增
**目的:** 为六边形架构 + DDD 模式生成和审查测试

**何时使用:**
- 实现新功能后 (Orchestrators, Event Handlers, Repositories)
- 审查测试覆盖率
- 为领域逻辑生成单元测试
- 为数据库操作创建集成测试
- 为 REST 端点或定时任务编写测试
- 遵循测试金字塔 (70% 单元, 25% 集成, 5% E2E)

**功能:**
- 自动识别代码模式 (Orchestrator, Event Handler, Repository 等)
- 推荐正确的测试策略 (单元 vs 集成)
- 生成具有适当 mock 的完整测试类
- 遵循 testing-guide.md 最佳实践
- 提供测试覆盖率分析
- 使用 AAA 模式 (Arrange-Act-Assert) 和 AssertJ assertions

**集成:** ✅ 原样复制

---

### plan-reviewer
**目的:** 在实施前审查开发计划

**何时使用:**
- 开始复杂功能之前
- 验证架构计划
- 及早识别潜在问题
- 获取方法的第二意见

**集成:** ✅ 原样复制

---

### refactor-planner
**目的:** 创建全面的重构策略

**何时使用:**
- 规划代码重组
- 现代化遗留代码
- 拆分大型文件
- 改善代码结构

**集成:** ✅ 原样复制

---

### web-research-specialist
**目的:** 在线研究技术问题

**何时使用:**
- 调试模糊错误
- 查找问题解决方案
- 研究最佳实践
- 比较实现方法

**集成:** ✅ 原样复制

---

### compile-error-resolver
**目的:** 自动修复 Spring Boot 项目中的 Java/Maven 编译错误

**何时使用:**
- Maven 编译失败
- 重构后破坏编译
- 六边形架构层违规
- 缺少导入或错误的依赖
- 需要系统性编译错误解决

**集成:** ⚠️ 需要 Maven 并与 maven-compile-check.sh hook 配合使用

---

## 如何集成 Agent

### 标准集成 (大多数 Agent)

**步骤 1: 复制文件**
```bash
cp showcase/.claude/agents/agent-name.md \\
   your-project/.claude/agents/
```

**步骤 2: 验证 (可选)**
```bash
# 检查硬编码路径
grep -n "~/git/\|/root/git/\|/Users/" your-project/.claude/agents/agent-name.md
```

**步骤 3: 使用它**
询问 Claude: "Use the [agent-name] agent to [task]"

就是这样！Agent 立即工作。

---

### 需要自定义的 Agent

**compile-error-resolver:**
- 需要安装 Maven 并在 PATH 中
- 最好与 maven-compile-check.sh hook 配合使用 (参见 `.claude/hooks/`)
- 期望 Maven 多模块项目结构
- 将从 hooks 读取 `.last-compile-failed` 标记文件

---

## 何时使用 Agent vs Skill

| 使用 Agent 当... | 使用 Skill 当... |
|-------------------|-------------------|
| 任务需要多个步骤 | 需要内联指导 |
| 需要复杂分析 | 检查最佳实践 |
| 偏好自主工作 | 想要保持控制 |
| 任务有明确的最终目标 | 进行中的开发工作 |
| 示例: "Review all controllers" | 示例: "Creating a new route" |

**两者可以协同工作:**
- Skill 在开发期间提供模式
- Agent 在完成时审查结果

---

## Agent 快速参考

| Agent | 复杂度 | 自定义 | 要求 |
|-------|-----------|---------------|--------------|
| runtime-error-diagnostic | 高 | ⚠️ Actuator | Spring Boot Actuator |
| code-architecture-reviewer | 中 | ✅ 无 | 无 |
| code-refactor-master | 高 | ✅ 无 | 无 |
| documentation-architect | 中 | ✅ 无 | 无 |
| test-architect | 中 | ✅ 无 | 无 |
| plan-reviewer | 低 | ✅ 无 | 无 |
| refactor-planner | 中 | ✅ 无 | 无 |
| web-research-specialist | 低 | ✅ 无 | 无 |
| compile-error-resolver | 中 | ⚠️ Maven + hooks | Maven in PATH |

---

## 给 Claude Code

**为用户集成 agent 时:**

1. **阅读 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)**
2. **只需复制 .md 文件** - agent 是独立的
3. **检查硬编码路径:**
   ```bash
   grep "~/git/\|/root/" agent-name.md
   ```
4. **如果找到则更新路径** 为 `$CLAUDE_PROJECT_DIR` 或 `.`
5. **对于 compile-error-resolver:** 确保已安装 Maven 并建议设置 hooks

**就是这样!** Agent 是最容易集成的组件。

---

## 创建你自己的 Agent

Agent 是带有可选 YAML frontmatter 的 markdown 文件:

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

**提示:**
- 在指令中非常具体
- 将复杂任务分解为编号步骤
- 准确指定要返回的内容
- 包含良好输出的示例
- 明确列出可用工具

---

## 故障排除

### 找不到 Agent

**检查:**
```bash
# Agent 文件是否存在?
ls -la .claude/agents/[agent-name].md
```

### Agent 因路径错误失败

**检查硬编码路径:**
```bash
grep "~/\|/root/\|/Users/" .claude/agents/[agent-name].md
```

**修复:**
```bash
sed -i 's|~/git/.*project|$CLAUDE_PROJECT_DIR|g' .claude/agents/[agent-name].md
```

---

## 后续步骤

1. **浏览上面的 agent** - 找到对你工作有用的
2. **复制你需要的** - 只需 .md 文件
3. **让 Claude 使用它们** - "Use [agent] to [task]"
4. **创建你自己的** - 按照模式满足你的特定需求

**有问题?** 参见 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)

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
