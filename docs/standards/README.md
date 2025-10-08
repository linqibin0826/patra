# 规范文档索引

> Papertrace 开发规范、设计模式、最佳实践

---

## 📄 文档列表

### API 设计规范
- **[Feign API 设计指南](./feign-api-design-guide.md)** - 内部 RPC 契约设计规范
  - 包名规范、DTO 命名、接口设计模式
  - SpringDoc 注解使用（@Operation、@Schema、@ApiResponse）
  - 版本控制、废弃策略

- **[Feign API Checklist](../templates/feign-api-checklist.md)** - API 设计检查清单
  - 设计前检查项、实现中检查项、发布前检查项

### 错误处理规范
- **[平台错误处理规范](./platform-error-handling.md)** - 统一错误模型与处理策略
  - ProblemDetail 格式（RFC 7807）
  - 错误码规范、错误分类
  - 异常映射、重试策略

- **[跨服务错误最佳实践](./cross-service-error-best-practices.md)** - 错误传播与处理
  - Feign 异常解码
  - 错误上下文传递
  - 降级与熔断

### 日志规范
- **[日志规范](./logging-convention.md)** - 统一日志格式与级别
  - 日志级别使用（ERROR/WARN/INFO/DEBUG）
  - 日志格式（结构化日志、JSON）
  - 敏感信息脱敏
  - TraceId 关联

---

## 🔗 相关文档

### 架构文档
- [系统架构总览](../overview/architecture-diagrams.md)
- [六边形架构图](../modules/ingest/architecture-diagram.md)
- [ADR 索引](../architecture/README.md)

### 开发指南
- [CLAUDE.md](../../CLAUDE.md) - Claude 开发规范
- [AGENTS.md](../../AGENTS.md) - Agent 协作手册

---

## 📝 贡献指南

### 添加新规范
1. 创建 Markdown 文件：`docs/standards/{name}.md`
2. 遵循规范模板（见下文）
3. 更新本索引文件
4. 提交 PR 并进行 Code Review

### 规范文档模板
```markdown
# {规范名称}

> 简要说明规范的目的和适用范围

## 1. 规范目标
- 目标1：...
- 目标2：...

## 2. 核心原则
- 原则1：...
- 原则2：...

## 3. 详细规范

### 3.1 {子规范1}
- 规则1：...
- 规则2：...

### 3.2 {子规范2}
- 规则1：...
- 规则2：...

## 4. 示例

### 4.1 正确示例
```java
// 代码示例
```

### 4.2 错误示例
```java
// 代码示例
```

## 5. 检查清单
- [ ] 检查项1
- [ ] 检查项2

## 6. 参考资料
- 链接1
- 链接2

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | YYYY-MM-DD | 初始版本 | 作者 |
```

---

## 🗂️ 开发规范分类

### 代码规范
- **命名规范**：
  - 类名：PascalCase（如：`PlanAggregate`）
  - 方法名：camelCase（如：`createPlan`）
  - 常量：UPPER_SNAKE_CASE（如：`MAX_RETRY_COUNT`）
  - 包名：lowercase（如：`com.patra.ingest.domain`）

- **注释规范**：
  - 类/接口：JavaDoc 注释（必须）
  - 公共方法：JavaDoc 注释（必须）
  - 复杂逻辑：行内注释（推荐）
  - 英文注释（代码层面）

- **代码风格**：
  - 缩进：4 空格
  - 行宽：120 字符
  - 大括号：Allman 风格
  - 导入顺序：静态导入 → Java 标准库 → 第三方库 → 本项目

### 架构规范
- **六边形架构**：
  - Domain 层：纯 Java，无框架依赖
  - Application 层：用例编排，不包含业务规则
  - Infrastructure 层：端口实现
  - Adapter 层：外部适配

- **DDD 规范**：
  - 聚合根：统一入口，保证一致性
  - 值对象：不可变，无标识
  - 领域事件：异步通信，解耦
  - 仓储接口：定义在 Domain，实现在 Infra

- **依赖方向**：
  - Adapter → App + API
  - App → Domain
  - Infra → Domain
  - Domain → 仅依赖 patra-common

### 测试规范
- **单元测试**：
  - 覆盖率：≥80%
  - 命名：`{MethodName}_{Scenario}_{ExpectedBehavior}`
  - 框架：JUnit5 + AssertJ + Mockito

- **集成测试**：
  - 覆盖核心流程
  - 使用 Testcontainers（MySQL、Redis、Kafka）
  - 隔离数据（每个测试独立数据库）

- **E2E 测试**：
  - 覆盖关键业务场景
  - 使用 RestAssured + WireMock
  - 环境独立（不依赖外部服务）

---

## 📊 规范检查工具

### 静态代码检查
```bash
# PMD 检查
mvn pmd:pmd pmd:cpd

# Checkstyle 检查
mvn checkstyle:check

# SpotBugs 检查
mvn spotbugs:check
```

### 依赖检查
```bash
# 检查依赖冲突
mvn dependency:tree

# 检查未使用的依赖
mvn dependency:analyze

# 检查依赖漏洞
mvn org.owasp:dependency-check-maven:check
```

### 测试覆盖率
```bash
# JaCoCo 覆盖率报告
mvn clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

---

## 🔧 最佳实践

### 1. 错误处理
```java
// ✅ 推荐：使用 ProblemDetail
throw new BusinessException(
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid plan state")
        .withProperty("planId", planId)
        .withProperty("currentState", currentState)
);

// ❌ 不推荐：直接抛出异常
throw new RuntimeException("Invalid plan state");
```

### 2. 日志记录
```java
// ✅ 推荐：结构化日志 + 参数化
log.info("[INGEST][APP] Plan created: planId={} sourceCode={} traceId={}", 
         planId, sourceCode, traceId);

// ❌ 不推荐：字符串拼接
log.info("Plan created: " + planId + ", source: " + sourceCode);
```

### 3. 异步处理
```java
// ✅ 推荐：发布领域事件
domainEventPublisher.publish(new PlanCreatedEvent(planId, sourceCode));

// ❌ 不推荐：直接调用下游服务
downstreamService.notifyPlanCreated(planId);
```

### 4. 配置管理
```yaml
# ✅ 推荐：使用 Nacos 集中管理
patra:
  ingest:
    batch-size: ${BATCH_SIZE:1000}

# ❌ 不推荐：硬编码
private static final int BATCH_SIZE = 1000;
```

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：规范文档索引 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
