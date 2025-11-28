---
name: java-development
description: 在编写代码时，你必须使用这个技能，java 开发指南。用于创建DDD组件，Spring组件，配置 MyBatis-Plus、Feign、Nacos，管理 Patra 自定义 Starter 依赖。涵盖六边形架构下的 Spring Boot 3.5.7 开发模式和最佳实践。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__serena__replace_symbol_body, mcp__serena__rename_symbol, mcp__context7__resolve-library-id, mcp__context7__get-library-docs, mcp__ide__getDiagnostics
---

# Spring Boot 微服务开发专家

自动为 Spring Boot 开发任务提供指导：Controller、Service、Repository 开发，MyBatis-Plus 配置，Patra Starter 依赖管理等。

## 核心工作流

### 0. TDD（测试驱动开发）
在开始编码前，编写单元测试和集成测试。

### 1. 创建 REST Controller

当需要创建新的 REST 接口时：
1. 确认所在模块是 `patra-{service}-adapter`
2. 确认已添加 `patra-spring-boot-starter-web` 依赖
3. 参考 [controller-patterns.md](resources/controller-patterns.md) 获取详细代码模板
4. 遵循 RESTful 设计原则

**核心原则**：
- Controller 只负责协议转换，不包含业务逻辑
- 使用 DTO 作为请求响应对象，不暴露领域对象
- 依赖编排层（Orchestrator）处理业务

### 2. 实现编排层（Orchestrator/Coordinator）

编排层负责协调业务流程：
1. 位于 `patra-{service}-app` 模块
2. 使用 `@Service` 和 `@Transactional` 注解
3. 参考 [orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md) 获取模式详情
4. 遵循事务边界管理原则

**核心职责**：
- 组装领域对象
- 编排业务流程
- 管理事务边界
- 发布领域事件

### 3. 数据访问层开发（MyBatis-Plus）

使用 MyBatis-Plus 进行数据访问：
1. 位于 `patra-{service}-infra` 模块
2. 必须使用 `patra-spring-boot-starter-mybatis`
3. 参考 [mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md) 获取详细实现
4. **所有 DO 必须继承 BaseDO**

**开发流程**：
```
创建 DO（继承 BaseDO）→ 创建 Mapper（继承 PatraBaseMapper）→ 实现 Repository
```

### 4. 事务和错误处理

事务管理和错误处理最佳实践：
1. 事务只在 Application 层使用 `@Transactional`
2. 参考 [transaction-error-handling.md](resources/transaction-error-handling.md) 获取详细指导
3. 使用统一异常处理机制
4. 遵循补偿和重试策略

### 5. 事件驱动架构

实现领域事件和消息驱动：
1. 参考 [event-driven-architecture.md](resources/event-driven-architecture.md) 了解事件模式
2. 使用 [outbox-pattern.md](resources/outbox-pattern.md) 保证事件可靠性
3. 在编排层发布事件
4. 在适配层监听外部事件

## 🚨 Patra Starter 使用规范

### 强制规则
**必须使用 Patra 自定义 Starter，禁止直接引入原始依赖。**

### Starter 快速选择

```
判断模块类型：
├── adapter 层 → patra-spring-boot-starter-web
├── infra 层
│   ├── 需要数据库 → patra-spring-boot-starter-mybatis
│   ├── 需要调用服务 → patra-spring-cloud-starter-feign
│   └── 需要对象存储 → patra-spring-boot-starter-object-storage
├── domain 层 → ❌ 不能添加任何 Starter
```

详细使用指南请查看 [patra-starters-guide.md](resources/patra-starters-guide.md)

## 配置管理

使用 Nacos 进行配置管理：
1. 支持动态配置刷新（`@RefreshScope`）
2. 参考 [configuration-management.md](resources/configuration-management.md) 获取详细配置
3. 遵循配置分层和环境隔离原则
4. 敏感配置需要加密处理

## 性能优化建议

1. **数据库优化**：避免 N+1 查询，使用批量操作
2. **缓存策略**：合理使用 Redis 缓存，注意缓存失效
3. **异步处理**：使用 @Async 和 CompletableFuture
4. **连接池配置**：根据负载调整数据库和 HTTP 连接池

## 详细资源索引

### 核心模式
- [Controller 开发模式](resources/controller-patterns.md)
- [编排器和协调器模式](resources/orchestrator-coordinator-patterns.md)
- [MyBatis-Plus 数据访问模式](resources/mybatis-plus-patterns.md)
- [适配层模式](resources/adapter-layer-patterns.md)

### Patra 特定
- [Patra Starter 完整指南](resources/patra-starters-guide.md)

### 架构模式
- [事务和错误处理](resources/transaction-error-handling.md)
- [事件驱动架构](resources/event-driven-architecture.md)
- [Outbox 模式](resources/outbox-pattern.md)

### 配置
- [配置管理指南](resources/configuration-management.md)

## 常见问题快速解答

### Q: Domain 层能否使用 Spring 注解？
**A: 不能**。Domain 层必须保持纯 Java，不依赖任何框架。

### Q: 为什么 DO 必须继承 BaseDO？
**A: BaseDO 提供**：雪花 ID、10 个审计字段、自动填充功能。

### Q: 事务注解应该加在哪一层？
**A: 只在 Application 层**（Orchestrator/Coordinator）使用 @Transactional。

### Q: 如何选择正确的 Starter？
**A: 查看** [Starter 快速选择](#starter-快速选择) 或 [patra-starters-guide.md](resources/patra-starters-guide.md)

### Q: Mapper 接口能否写 SQL？
**A: 不推荐**。简单查询用 LambdaQueryWrapper，复杂查询用 XML。

## 工作检查清单

### 开发新功能前
```
[ ] 确认模块层级（adapter/app/domain/infra）
[ ] 检查并添加正确的 Patra Starter
[ ] 规划包结构和类命名
```

### 编码过程中
```
[ ] Controller 不包含业务逻辑
[ ] 事务只在 Application 层
[ ] DO 继承 BaseDO
[ ] 使用 MapStruct 进行对象转换
```

### 完成后验证
```
[ ] 依赖方向正确（不违反六边形架构）
[ ] 异常处理完整
[ ] 日志记录规范
[ ] 测试覆盖充分
```
