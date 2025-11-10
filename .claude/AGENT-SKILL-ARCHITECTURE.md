# 📦 Claude Code Agent-Skill 架构

## 🏗️ 三层架构设计

```
┌─────────────────────────────────────────────────┐
│                  Hooks 层                        │
│  触发器：skill-activation-prompt.sh              │
│  功能：检测关键词，提示技能激活                  │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│                SubAgents 层                      │
│  位置：.claude/agents/*.md                       │
│  功能：独立上下文的专业助手                      │
│  调用方式：显式调用或自动委派                    │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│                  Skills 层                       │
│  位置：.claude/skills/*/SKILL.md                 │
│  功能：模块化的知识和资源包                      │
│  加载方式：通过 Skill() 工具                     │
└─────────────────────────────────────────────────┘
```

## 📊 当前配置映射

| SubAgent | 加载的 Skill | 核心职责 |
|----------|-------------|---------|
| **java-hexagonal-architecture** | java-hexagonal-architecture | 架构设计、领域建模、方案评审、DDD 专家 |
| **patra-backend-developer** | patra-tdd-development, java-spring-development | TDD 驱动的后端全栈开发、Spring Boot、REST API、事务管理、测试先行 |
| **runtime-error-diagnostic** | java-runtime-diagnostic | 运行时错误诊断、日志分析、性能调优 |
| **code-reviewer** | java-code-reviewer | 代码审查、质量评估、安全检查、性能分析 |
| **documentation-architect** | java-documentation-architect | 文档创建、README 维护、JavaDoc 编写 |
| **web-research-specialist** | 无（通用工具） | 技术调研、问题搜索、解决方案查找 |

## 🎯 工作流程

### 1. Hook 激活提示
```bash
用户输入 → skill-activation-prompt.sh 检测关键词
         → 匹配 skill-rules.json 中的触发规则
         → 输出建议激活的技能列表
```

### 2. SubAgent 委派
```bash
用户/Claude 决定使用某个 SubAgent
         → SubAgent 启动（独立上下文）
         → 通过 Skill() 工具加载对应技能
         → 获取专业知识和资源
         → 执行任务
```

### 3. Skill 资源加载
```bash
SubAgent 调用 Skill("skill-name")
         → 加载 SKILL.md 内容
         → 访问技能资源文件
         → 应用专业知识
```

## 🔧 配置文件

### skill-rules.json
- **作用**：定义技能触发规则
- **内容**：关键词、意图模式、文件模式
- **版本**：2.0（支持废弃和执行策略）

### settings.json
- **作用**：Hook 配置
- **内容**：UserPromptSubmit、PostToolUse、Stop 等事件钩子

### agents/*.md
- **作用**：SubAgent 定义
- **格式**：YAML frontmatter + Markdown
- **必需字段**：name、description
- **可选字段**：tools、model、color

### skills/*/SKILL.md
- **作用**：Skill 定义和资源
- **格式**：YAML frontmatter + Markdown
- **必需字段**：name、description
- **可选字段**：allowed-tools

## 💡 设计优势

1. **清晰的职责分离**
   - Hooks：自动检测和提示
   - SubAgents：任务执行和协调
   - Skills：知识和资源管理

2. **灵活的激活机制**
   - 被动：等待 Claude 自动识别
   - 主动：Hook 提示用户激活
   - 显式：用户直接调用 SubAgent

3. **模块化和可复用**
   - Skills 可被多个 SubAgents 共享
   - SubAgents 可跨项目使用
   - Hooks 规则集中管理

4. **渐进式披露**
   - 仅在需要时加载资源
   - 独立上下文避免污染主对话
   - 按需加载提高效率

## 📌 最佳实践

### DO ✅
- 通过 SubAgent 处理复杂任务
- 使用 Skill 组织专业知识
- 让 Hook 主动提示可用技能
- 保持技能专注单一职责
- 定期更新 skill-rules.json

### DON'T ❌
- 不要在 Skills 中混合多个职责
- 不要忽略 Hook 的激活提示
- 不要在主对话中处理应该委派的任务
- 不要创建过于通用的 SubAgents

## 🚀 使用示例

### 场景 1：架构设计
```
用户：我需要设计一个订单系统的领域模型
Hook：提示激活 java-hexagonal-architecture
用户：使用 java-hexagonal-architecture
SubAgent：加载技能，提供 DDD 设计指导
```

### 场景 2：错误诊断
```
用户：生产环境出现 NullPointerException
Hook：提示激活 java-runtime-diagnostic
用户：使用 runtime-error-diagnostic
SubAgent：加载技能，分析日志和堆栈
```

### 场景 3：TDD 后端开发
```
用户：创建一个用户注册的 REST API
Hook：提示激活 patra-backend-developer
用户：使用 patra-backend-developer
SubAgent：加载技能（TDD+Spring），先写测试再生成 Controller、Orchestrator 等
```

## 📈 未来优化方向

1. **智能激活**：基于上下文自动选择最佳 SubAgent
2. **链式协作**：多个 SubAgents 串联完成复杂任务
3. **学习机制**：根据使用频率优化触发规则
4. **性能监控**：跟踪 SubAgent 执行效率

---

*最后更新：2024-11-06*
*版本：1.0*