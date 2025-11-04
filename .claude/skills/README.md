# 技能 (Skills)

为 Claude Code 提供的经过生产测试的技能,可基于上下文自动激活。

---

## 什么是 Skills?

Skills 是 Claude 在需要时加载的模块化知识库。它们提供:
- 领域特定指南
- 最佳实践
- 代码示例
- 需要避免的反模式

**问题:** Skills 默认不会自动激活。

**解决方案:** 本项目包含了使它们自动激活的 hooks + 配置。

---

## 可用的 Skills

### skill-developer (元技能)
**目的:** 创建和管理 Claude Code skills

**文件:** 7 个资源文件(共 426 行)

**使用场景:**
- 创建新技能
- 理解技能结构
- 使用 skill-rules.json
- 调试技能激活

**自定义:** ✅ 无需自定义 - 原样复制

**[查看 Skill →](skill-developer/)**

---

### java-backend-guidelines
**目的:** 使用六边形架构 + DDD 的 Java/Spring Boot 后端开发

**文件:** 16 个资源文件(主文件 607 行 + 资源文件)

**涵盖内容:**
- 六边形架构(端口与适配器) + DDD 模式
- 四层架构(Adapter → Application → Domain ← Infrastructure)
- Spring Boot 3.5.7 + Java 25 最佳实践
- MyBatis-Plus 数据库访问模式
- MapStruct DO ↔ Domain 实体映射
- Orchestrator/Coordinator 模式
- 事务管理(@Transactional)
- 验证模式(@Valid)
- Nacos 配置管理
- Outbox 模式用于可靠事件
- 事件驱动架构
- 测试策略(单元、集成、ArchUnit)

**使用场景:**
- 创建 REST 控制器、编排器、领域实体
- 使用 MyBatis-Plus 实现仓储
- 使用六边形架构构建微服务
- 处理聚合、值对象、领域事件
- 设置验证和错误处理
- 性能优化和 N+1 查询预防

**自定义:** ✅ 已为 Patra 配置(patra-* 模块)

**路径模式:**
```json
{
  "pathPatterns": [
    "patra-*/patra-*-adapter/src/**/*.java",
    "patra-*/patra-*-app/src/**/*.java",
    "patra-*/patra-*-domain/src/**/*.java",
    "patra-*/patra-*-infra/src/**/*.java"
  ]
}
```

**[查看 Skill →](java-backend-guidelines/)**

---

### logging-observability
**目的:** Spring Boot 的日志记录、追踪和可观测性模式

**文件:** 1 个主文件(~750 行)

**涵盖内容:**
- SLF4J + Logback 日志模式
- 使用 MDC 的结构化日志
- Micrometer 指标和追踪
- 性能监控
- 使用 @ControllerAdvice 的错误处理
- 数据库查询监控

**使用场景:**
- 向任何代码添加日志
- 设置错误处理
- 跟踪性能指标
- 实现分布式追踪
- 调试生产问题

**自定义:** ✅ 无需自定义 - 适用于任何 Spring Boot 项目

**[查看 Skill →](logging-observability/)**

---

### patra-domain
**目的:** Patra 业务领域知识和工作流模式

**文件:** 7 个资源文件(主文件 465 行 + 资源文件)

**涵盖内容:**
- 核心领域概念: Provenance、Plan、Task、Expression 引擎
- Provenance 配置系统(HTTP、分页、重试、速率限制)
- Plan/Task 生命周期和工作流模式
- 时间切片策略和窗口管理
- Expression 引擎: 将抽象查询映射到提供者特定 API
- 作用域优先级层次结构(TASK > SOURCE > GLOBAL)
- 使用业务键的幂等性模式
- 常见集成模式(PubMed、EPMC、Crossref)
- 常见问题故障排除指南

**使用场景:**
- 处理 Provenance 配置
- 实现 Plan 创建和切片逻辑
- 构建 Task 执行工作流
- 调试表达式渲染问题
- 理解数据源集成
- 排查 Plan/Task 失败

**自定义:** ✅ 已为 Patra 配置(patra-registry, patra-ingest, patra-expr-kernel)

**路径模式:**
```json
{
  "pathPatterns": [
    "patra-registry/**/*.java",
    "patra-ingest/**/*.java",
    "patra-expr-kernel/**/*.java"
  ]
}
```

**[查看 Skill →](patra-domain/)**

---

## 如何向项目添加 Skill

### 快速集成

**对于 Claude Code:**
```
用户: "向我的项目添加 java-backend-guidelines 技能"

Claude 应该:
1. 询问项目结构
2. 复制技能目录
3. 使用项目路径更新 skill-rules.json
4. 验证集成
```

完整说明请参见 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)。

### 手动集成

**步骤 1: 复制技能目录**
```bash
cp -r showcase/.claude/skills/java-backend-guidelines \\
      your-project/.claude/skills/
```

**步骤 2: 更新 skill-rules.json**

如果没有该文件,创建它:
```bash
cp showcase/.claude/skills/skill-rules.json \\
   your-project/.claude/skills/
```

然后为项目自定义 `pathPatterns`:
```json
{
  "skills": {
    "java-backend-guidelines": {
      "fileTriggers": {
        "pathPatterns": [
          "patra-*/patra-*-adapter/src/**/*.java",
          "patra-*/patra-*-app/src/**/*.java",
          "patra-*/patra-*-domain/src/**/*.java",
          "patra-*/patra-*-infra/src/**/*.java"
        ]
      }
    }
  }
}
```

**步骤 3: 测试**
- 编辑后端模块中的 Java 文件
- 技能应该自动激活

---

## skill-rules.json 配置

### 它的作用

基于以下条件定义技能何时应该激活:
- 用户提示中的**关键词**("backend"、"orchestrator"、"domain")
- **意图模式**(正则表达式匹配用户意图)
- **文件路径模式**(编辑 Java 后端文件)
- **内容模式**(代码包含 @RestController、@Service、MyBatis)

### 配置格式

```json
{
  "skill-name": {
    "type": "domain" | "guardrail",
    "enforcement": "suggest" | "block",
    "priority": "high" | "medium" | "low",
    "promptTriggers": {
      "keywords": ["关键词", "列表"],
      "intentPatterns": ["正则表达式模式"]
    },
    "fileTriggers": {
      "pathPatterns": ["patra-*/src/**/*.java"],
      "contentPatterns": ["@RestController", "@Service", "MyBatis"]
    }
  }
}
```

### 强制级别

- **suggest**: 技能显示为建议,不阻止操作
- **block**: 继续之前必须使用技能(守护)

**使用 "block" 用于:**
- 防止架构违规
- 关键数据库操作
- 安全敏感代码

**使用 "suggest" 用于:**
- 一般最佳实践
- 领域指导
- 代码组织

---

## 创建你自己的 Skills

完整指南请参见 **skill-developer** 技能:
- Skill YAML 前置元数据结构
- 资源文件组织
- 触发器模式设计
- 测试技能激活

**快速模板:**
```markdown
---
name: my-skill
description: 这个技能的作用
---

# 我的技能标题

## 目的
[为什么存在这个技能]

## 何时使用此技能
[自动激活场景]

## 快速参考
[关键模式和示例]

## 资源文件
- [topic-1.md](resources/topic-1.md)
- [topic-2.md](resources/topic-2.md)
```

---

## 故障排除

### 技能没有激活

**检查:**
1. 技能目录是否在 `.claude/skills/` 中?
2. 技能是否在 `skill-rules.json` 中列出?
3. `pathPatterns` 是否匹配你的文件?
4. hooks 是否已安装并正常工作?
5. settings.json 是否配置正确?

**调试:**
```bash
# 检查技能是否存在
ls -la .claude/skills/

# 验证 skill-rules.json
cat .claude/skills/skill-rules.json | jq .

# 检查 hooks 是否可执行
ls -la .claude/hooks/*.sh

# 手动测试 hook
./.claude/hooks/skill-activation-prompt.sh
```

### 技能激活过于频繁

更新 skill-rules.json:
- 使关键词更具体
- 缩小 `pathPatterns`
- 增加 `intentPatterns` 的特异性

### 技能从不激活

更新 skill-rules.json:
- 添加更多关键词
- 扩大 `pathPatterns`
- 添加更多 `intentPatterns`

---

## 对于 Claude Code

**为用户集成技能时:**

1. **首先阅读 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)**
2. 询问他们的项目结构
3. 在 skill-rules.json 中自定义 `pathPatterns`
4. 验证技能文件没有硬编码路径
5. 集成后测试激活

**常见错误:**
- 未验证路径模式是否匹配实际项目结构
- 未询问多模块 Maven 项目布局
- 复制 skill-rules.json 时未自定义

---

## 后续步骤

1. **从简单开始:** 添加一个与你工作匹配的技能
2. **验证激活:** 编辑相关文件,技能应该建议
3. **添加更多:** 一旦第一个技能工作,添加其他技能
4. **自定义:** 根据你的工作流调整触发器

**有问题?** 完整集成说明请参见 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)。
