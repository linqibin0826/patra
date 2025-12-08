---
description: "实施变更日志模板 - 仅记录与原始计划的偏差和技术决策变更"
version: "1.0"
---

# Implementation Log - {特性名称}

> 📋 本文档**仅记录**实施过程中与原始规划的偏差和技术决策变更
>
> ⚠️ **重要**：正常按计划实施的任务不需要记录在此文档中

## 📊 元数据

| 项目 | 值 |
|------|-----|
| 特性 ID | {从目录名提取，如：001-feature-name} |
| 基线文档 | spec.md, plan.md, tasks.md, data-model.md |
| 创建时间 | {首次记录变更的时间，格式：YYYY-MM-DD HH:mm} |
| 最后更新 | {最后一次记录变更的时间} |
| 变更总数 | {当前变更数量} |

## 📝 变更记录

> 按时间倒序记录（最新的在最上面）
> **只记录变更，不记录正常实施**

---

<!-- ============================================ -->
<!-- 变更记录从这里开始 -->
<!-- 使用下方的变更记录模板追加新的变更 -->
<!-- ============================================ -->

---

## 📋 变更记录模板

### 🔄 变更记录标准格式

```markdown
### 变更-{编号} | {变更类型} | {时间戳}

**任务 ID**: [{相关任务 ID，如：TASK-003}]({tasks.md 中的任务锚点})
**受影响文档**: {列出需要同步更新的文档，如：plan.md, data-model.md}
**状态**: {检测中 | 已记录 | 已同步 | 已解决}

#### 📌 变更描述

{简要描述偏差或变更的内容}

#### 🎯 原计划

{引用原始文档中的计划内容}

#### ⚡ 实际实施

{描述实际采用的方案}

#### 💡 变更原因

{说明为什么需要偏离原计划，可能的原因：}
- 技术限制：{具体限制}
- 依赖冲突：{冲突详情}
- 性能问题：{性能指标}
- 架构合规：{违反的规范}
- 业务需求变化：{需求调整}

#### 🔍 影响范围

- **代码影响**: {列出受影响的类/文件}
- **测试影响**: {是否需要修改测试}
- **文档影响**: {需要更新的文档列表}
- **依赖影响**: {是否影响其他任务或模块}

#### ✅ 同步更新清单

- [ ] 更新 tasks.md：{具体修改内容}
- [ ] 更新 plan.md：{具体修改内容}
- [ ] 更新 data-model.md：{如有数据模型变更}
- [ ] 更新 contracts/：{如有 API 契约变更}
- [ ] 添加引用标记：`<!-- 实施变更：见 implementation-log.md#变更-{编号} -->`

#### 🔗 相关链接

- 相关 Issue/PR: {如有}
- 参考文档: {如有}
- Stack Overflow/GitHub Discussions: {如有}
```

---

## 📚 变更类型示例

### 示例 1: 技术方案偏差

```markdown
### 变更-001 | 技术方案偏差 | 2025-11-09 14:30

**任务 ID**: [TASK-015](tasks.md#task-015)
**受影响文档**: plan.md (技术栈), tasks.md (TASK-015, TASK-016)
**状态**: 已同步

#### 📌 变更描述

Repository 实现从计划的 JPA 改为 MyBatis-Plus

#### 🎯 原计划

plan.md 中计划使用 Spring Data JPA 作为数据访问层：
```
**Persistence**: Spring Data JPA + Hibernate
```

#### ⚡ 实际实施

改用 MyBatis-Plus + MapStruct：
```
**Persistence**: MyBatis-Plus 3.x + MapStruct
```

#### 💡 变更原因

- 技术限制：现有项目已全面采用 MyBatis-Plus，切换到 JPA 会导致技术栈不一致
- 架构合规：patra-parent 已配置 MyBatis-Plus starter，重用现有基础设施

#### 🔍 影响范围

- **代码影响**:
  - `ArticleRepositoryAdapter.java` 需要使用 MyBatis-Plus BaseMapper
  - 增加 `ArticleMapper.xml` 和 `ArticleConverter.java`
- **测试影响**: Repository IT 测试需要使用 @MybatisPlusTest 而非 @DataJpaTest
- **文档影响**: plan.md 技术栈章节
- **依赖影响**: TASK-016（Repository 测试）需要调整

#### ✅ 同步更新清单

- [X] 更新 plan.md：技术栈章节改为 MyBatis-Plus
- [X] 更新 tasks.md：TASK-015 描述改为 "使用 MyBatis-Plus 实现 ArticleRepository"
- [X] 更新 tasks.md：TASK-016 描述改为 "编写 MyBatis-Plus Repository IT 测试"
- [X] 添加引用标记到 plan.md：`<!-- 实施变更：见 implementation-log.md#变更-001 -->`

#### 🔗 相关链接

- MyBatis-Plus 官方文档: https://baomidou.com/
```

---

### 示例 2: 数据模型变更

```markdown
### 变更-002 | 数据模型变更 | 2025-11-09 15:45

**任务 ID**: [TASK-008](tasks.md#task-008)
**受影响文档**: data-model.md, tasks.md (TASK-008, TASK-009)
**状态**: 已同步

#### 📌 变更描述

Article 实体新增 `sourceMetadata` 字段存储来源系统的元数据

#### 🎯 原计划

data-model.md 中 Article 实体定义：
```
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 主键 |
| title | String | 标题 |
| content | String | 内容 |
| sourceId | String | 来源 ID |
```

#### ⚡ 实际实施

增加 `sourceMetadata` 字段：
```
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 主键 |
| title | String | 标题 |
| content | String | 内容 |
| sourceId | String | 来源 ID |
| sourceMetadata | JSON | 来源系统元数据 |
```

#### 💡 变更原因

- 业务需求变化：需要保留 PubMed 的 MeSH Terms、Publication Types 等元数据
- 技术限制：不同来源系统的元数据结构不一致，使用 JSON 字段更灵活

#### 🔍 影响范围

- **代码影响**:
  - `Article.java` 实体增加 `sourceMetadata` 字段
  - `ArticleDO.java` 增加对应字段
  - `ArticleConverter.java` 增加 JSON 转换逻辑
- **测试影响**: ArticleTest 需要增加 sourceMetadata 测试用例
- **文档影响**: data-model.md
- **依赖影响**: TASK-009（Converter 实现）需要处理 JSON 转换

#### ✅ 同步更新清单

- [X] 更新 data-model.md：Article 实体增加 sourceMetadata 字段
- [X] 更新 tasks.md：TASK-008 增加 "包含 sourceMetadata JSON 字段"
- [X] 更新 tasks.md：TASK-009 增加 "实现 sourceMetadata JSON 转换"
- [X] 添加引用标记到 data-model.md：`<!-- 实施变更：见 implementation-log.md#变更-002 -->`

#### 🔗 相关链接

- PubMed MeSH Terms 文档: https://www.nlm.nih.gov/mesh/
```

---

### 示例 3: API 契约变更

```markdown
### 变更-003 | API 契约变更 | 2025-11-09 16:20

**任务 ID**: [TASK-022](tasks.md#task-022)
**受影响文档**: contracts/api.md, tasks.md (TASK-022, TASK-025)
**状态**: 已同步

#### 📌 变更描述

`GET /api/articles/{id}` 响应增加 `_links` HATEOAS 链接

#### 🎯 原计划

contracts/api.md 中定义的响应：
```json
{
  "id": 123,
  "title": "Article Title",
  "content": "...",
  "createdAt": "2025-11-09T10:00:00Z"
}
```

#### ⚡ 实际实施

增加 HATEOAS 链接：
```json
{
  "id": 123,
  "title": "Article Title",
  "content": "...",
  "createdAt": "2025-11-09T10:00:00Z",
  "_links": {
    "self": { "href": "/api/articles/123" },
    "related": { "href": "/api/articles/123/related" }
  }
}
```

#### 💡 变更原因

- 架构合规：遵循 CHK-CODE-004 RESTful API 最佳实践，支持 HATEOAS
- 性能问题：前端需要动态构建相关资源链接，增加 _links 可减少客户端逻辑

#### 🔍 影响范围

- **代码影响**:
  - `ArticleVO.java` 增加 `Links` 字段
  - `ArticleController.java` 增加链接构建逻辑
- **测试影响**: ArticleControllerTest 需要验证 _links 字段
- **文档影响**: contracts/api.md
- **依赖影响**: TASK-025（Controller 测试）需要增加链接断言

#### ✅ 同步更新清单

- [X] 更新 contracts/api.md：响应示例增加 _links
- [X] 更新 tasks.md：TASK-022 增加 "包含 HATEOAS 链接"
- [X] 更新 tasks.md：TASK-025 增加 "验证 _links 字段"
- [X] 添加引用标记到 contracts/api.md：`<!-- 实施变更：见 implementation-log.md#变更-003 -->`

#### 🔗 相关链接

- Spring HATEOAS 文档: https://spring.io/projects/spring-hateoas
```

---

### 示例 4: 任务调整（新增任务）

```markdown
### 变更-004 | 任务调整 | 2025-11-09 17:00

**任务 ID**: 新增 TASK-030
**受影响文档**: tasks.md
**状态**: 已同步

#### 📌 变更描述

新增任务：实现 Article 缓存层（Redis）

#### 🎯 原计划

tasks.md 中未包含缓存相关任务

#### ⚡ 实际实施

新增 TASK-030：
```
- [ ] TASK-030 [Infra] [US1] 实现 Article 缓存层
  - 文件: patra-ingest-infra/src/main/java/com/patra/ingest/infra/cache/ArticleCache.java
  - 使用 Spring Cache + Redis
  - 缓存策略: TTL 1 小时，LRU 淘汰
```

#### 💡 变更原因

- 性能问题：Article 查询频繁，数据库压力大
  - 压测发现：QPS 500 时数据库 CPU 达到 80%
  - 增加缓存后：QPS 1000 时数据库 CPU 降至 20%
- 架构合规：符合 CHK-ARCH-005 性能优化要求

#### 🔍 影响范围

- **代码影响**:
  - 新增 `ArticleCache.java` 和 `ArticleCacheConfig.java`
  - `ArticleRepositoryAdapter.java` 增加缓存注解
- **测试影响**: 新增 `ArticleCacheIT.java` 集成测试
- **文档影响**: tasks.md, plan.md（技术栈增加 Redis）
- **依赖影响**:
  - 依赖 TASK-015（Repository 实现）完成
  - TASK-025（Controller 测试）需要 Mock 缓存

#### ✅ 同步更新清单

- [X] 更新 tasks.md：在 Infrastructure 阶段新增 TASK-030
- [X] 更新 plan.md：技术栈增加 "缓存: Spring Cache + Redis"
- [X] 更新 tasks.md：TASK-025 增加 "Mock ArticleCache"
- [X] 添加引用标记到 tasks.md：`<!-- 实施变更：见 implementation-log.md#变更-004 -->`

#### 🔗 相关链接

- 压测报告: /docs/perf-test-2025-11-09.md
- Spring Cache 文档: https://spring.io/guides/gs/caching/
```

---

### 示例 5: 依赖或配置变更

```markdown
### 变更-005 | 依赖变更 | 2025-11-09 18:15

**任务 ID**: [TASK-002](tasks.md#task-002)
**受影响文档**: plan.md, tasks.md (TASK-002)
**状态**: 已同步

#### 📌 变更描述

pom.xml 新增 Hutool 依赖，替换部分自定义工具类

#### 🎯 原计划

plan.md 中可重用组件：
```
- **Common**: `patra-common-util`（工具类）
```

#### ⚡ 实际实施

增加 Hutool 依赖：
```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.25</version>
</dependency>
```

#### 💡 变更原因

- 技术限制：发现需要 JSON、日期、HTTP 等多种工具类
- 架构合规：遵循 CLAUDE.md 推荐 "重用 Hutool"
- 性能问题：Hutool 经过优化，性能优于自定义实现

#### 🔍 影响范围

- **代码影响**:
  - `patra-ingest-infra/pom.xml` 增加 Hutool 依赖
  - 移除部分自定义工具类（如 JsonUtils, DateUtils）
- **测试影响**: 使用 Hutool 的工具方法不需要额外测试
- **文档影响**: plan.md
- **依赖影响**: 所有后续任务都可以使用 Hutool

#### ✅ 同步更新清单

- [X] 更新 plan.md：可重用组件增加 "Hutool 5.8.25"
- [X] 更新 tasks.md：TASK-002 增加 "引入 Hutool 依赖"
- [X] 添加引用标记到 plan.md：`<!-- 实施变更：见 implementation-log.md#变更-005 -->`

#### 🔗 相关链接

- Hutool 官方文档: https://hutool.cn/
```

---

### 示例 6: 架构决策变更

```markdown
### 变更-006 | 架构决策变更 | 2025-11-09 19:00

**任务 ID**: [TASK-012](tasks.md#task-012)
**受影响文档**: spec.md, plan.md, tasks.md (TASK-012, TASK-013)
**状态**: 已同步

#### 📌 变更描述

从同步调用改为事件驱动架构（领域事件）

#### 🎯 原计划

spec.md 中设计原则：
```
Article 创建成功后，同步调用 NotificationService 发送通知
```

#### ⚡ 实际实施

改为发布领域事件：
```
Article 创建成功后，发布 ArticleCreatedEvent，由 NotificationEventHandler 异步处理
```

#### 💡 变更原因

- 架构合规：
  - 违反 CHK-ARCH-003：Application 层不应直接调用其他服务
  - 符合 DDD 最佳实践：使用领域事件解耦
- 性能问题：同步调用导致 Article 创建接口响应时间 > 500ms
- 技术限制：NotificationService 可能失败，不应阻塞主流程

#### 🔍 影响范围

- **代码影响**:
  - Domain 层：`Article.java` 发布 `ArticleCreatedEvent`
  - Application 层：`ArticleOrchestrator.java` 移除 NotificationService 调用
  - Infrastructure 层：新增 `ArticleEventPublisher.java`
  - Adapter 层：新增 `NotificationEventHandler.java`
- **测试影响**:
  - ArticleOrchestratorTest 改为验证事件发布
  - 新增 NotificationEventHandlerTest
- **文档影响**: spec.md, plan.md, tasks.md
- **依赖影响**:
  - 新增 TASK-013A：实现 ArticleEventPublisher
  - 新增 TASK-013B：实现 NotificationEventHandler

#### ✅ 同步更新清单

- [X] 更新 spec.md：设计原则改为事件驱动
- [X] 更新 plan.md：架构模式增加 "事件驱动（Spring Event）"
- [X] 更新 tasks.md：TASK-012 改为 "发布 ArticleCreatedEvent"
- [X] 更新 tasks.md：新增 TASK-013A, TASK-013B
- [X] 添加引用标记到 spec.md：`<!-- 实施变更：见 implementation-log.md#变更-006 -->`

#### 🔗 相关链接

- Spring Event 文档: https://spring.io/guides/gs/events/
- DDD 领域事件: https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/domain-events-design-implementation
```

---

### 示例 7: 任务失败记录

```markdown
### 变更-007 | 任务失败 | 2025-11-09 20:30

**任务 ID**: [TASK-018](tasks.md#task-018)
**受影响文档**: tasks.md (TASK-018), implementation-log.md
**状态**: 已解决

#### 📌 变更描述

TASK-018（Repository IT 测试）执行失败，TestContainers 无法启动 MySQL 容器

#### 🎯 原计划

tasks.md 中 TASK-018：
```
- [ ] TASK-018 [Infra] [US1] 编写 Repository IT 测试
  - 文件: patra-ingest-boot/src/test/java/com/patra/ingest/infra/ArticleRepositoryIT.java
  - 使用 TestContainers + MySQL
```

#### ⚡ 实际实施

遇到错误：
```
org.testcontainers.containers.ContainerLaunchException:
Container startup failed for image mysql:8.0
```

调研后发现：
- Docker Desktop 版本过旧（4.15.0）
- TestContainers 需要 Docker Desktop >= 4.18.0

解决方案：
1. 升级 Docker Desktop 到 4.25.0
2. 重新执行 TASK-018

#### 💡 变更原因

- 技术限制：本地环境 Docker 版本不满足 TestContainers 要求
- 依赖冲突：TestContainers 1.19.0 需要 Docker API >= 1.42

#### 🔍 影响范围

- **代码影响**: 无（代码正确）
- **测试影响**: TASK-018 延迟 1 小时
- **文档影响**: plan.md 增加环境要求
- **依赖影响**: 阻塞 TASK-019（所有依赖 Repository 的测试）

#### ✅ 同步更新清单

- [X] 更新 plan.md：环境要求增加 "Docker Desktop >= 4.18.0"
- [X] 记录错误到 implementation-log.md
- [X] 创建环境配置文档：`docs/dev-environment.md`
- [X] 添加引用标记到 plan.md：`<!-- 实施变更：见 implementation-log.md#变更-007 -->`

#### 🔗 相关链接

- TestContainers 系统要求: https://www.testcontainers.org/supported_docker_environment/
- Docker Desktop 发布说明: https://docs.docker.com/desktop/release-notes/
- Stack Overflow 相关讨论: https://stackoverflow.com/questions/...
```

---

## 🔍 使用指南

### 何时创建 implementation-log.md

**仅在首次发现变更时创建**，不需要在实施开始时就创建。

**触发条件**（满足任一条件即需记录变更）：
1. ✅ **技术方案偏差**：实际实施与 plan.md 中的技术选型不一致
2. ✅ **任务调整**：需要修改 tasks.md 中的任务描述、依赖或新增/删除任务
3. ✅ **数据模型变更**：实体、字段、关系与 data-model.md 不符
4. ✅ **API 契约变更**：端点、请求/响应格式与 contracts/ 不符
5. ✅ **架构决策变更**：违反或调整 spec.md 中的设计原则
6. ✅ **依赖或配置变更**：新增/修改 pom.xml、application.yml 等关键配置
7. ✅ **任务失败**：任务执行失败，需要记录错误和解决方案

**如果所有任务都按计划实施，无需创建 implementation-log.md**

### 变更记录流程（4 步强制执行）

```
步骤 1: 检测变更
  ↓
  对比实施与原始文档，发现偏差立即暂停
  ↓
步骤 2: 记录变更
  ↓
  使用标准模板记录到 implementation-log.md
  - 变更编号：变更-{递增编号}
  - 变更类型：技术方案偏差 | 任务调整 | 数据模型变更 | API 契约变更 | 架构决策变更 | 依赖变更 | 任务失败
  - 时间戳：YYYY-MM-DD HH:mm
  - 详细信息：按模板填写
  ↓
步骤 3: 同步更新受影响的原始文档
  ↓
  逐一更新并添加引用标记：
  `<!-- 实施变更：见 implementation-log.md#变更-{编号} -->`
  ↓
步骤 4: 继续实施
  ↓
  基于变更后的方案继续执行
```

### 引用标记格式

在受影响的原始文档中添加：

```markdown
<!-- 实施变更：见 implementation-log.md#变更-001 -->
```

**示例**（plan.md）：

```markdown
## Technical Context

**Persistence**: MyBatis-Plus 3.x + MapStruct
<!-- 实施变更：见 implementation-log.md#变更-001 -->
```

**示例**（tasks.md）：

```markdown
- [X] TASK-015 [Infra] [US1] 使用 MyBatis-Plus 实现 ArticleRepository
  <!-- 实施变更：见 implementation-log.md#变更-001 -->
```

### 变更编号规则

- 格式：`变更-{三位数字}`
- 示例：`变更-001`, `变更-002`, `变更-010`, `变更-123`
- 递增规则：按时间顺序递增

### 变更类型清单

| 变更类型 | 说明 | 示例 |
|---------|------|------|
| 技术方案偏差 | 实际技术选型与 plan.md 不一致 | JPA → MyBatis-Plus |
| 任务调整 | 修改/新增/删除 tasks.md 中的任务 | 新增缓存任务 |
| 数据模型变更 | 实体、字段与 data-model.md 不符 | 新增字段 |
| API 契约变更 | 端点、请求/响应与 contracts/ 不符 | 新增响应字段 |
| 架构决策变更 | 违反或调整 spec.md 设计原则 | 同步 → 事件驱动 |
| 依赖变更 | 新增/修改 pom.xml 等配置 | 新增 Hutool |
| 任务失败 | 任务执行失败，记录错误和解决方案 | 环境问题导致失败 |

---

## ✅ 检查清单

**创建 implementation-log.md 前**：
- [ ] 确认发生了变更或偏差（不是正常按计划实施）
- [ ] 明确变更类型和影响范围
- [ ] 准备好变更原因和决策依据

**记录变更后**：
- [ ] 变更编号按顺序递增
- [ ] 变更描述清晰具体
- [ ] 原计划 vs 实际实施对比明确
- [ ] 变更原因充分合理
- [ ] 影响范围完整（代码、测试、文档、依赖）
- [ ] 同步更新清单完整
- [ ] 所有受影响文档已添加引用标记：`<!-- 实施变更：见 implementation-log.md#变更-XXX -->`

**完成实施后**：
- [ ] 所有变更都已记录
- [ ] 所有受影响文档都已同步更新
- [ ] implementation-log.md 元数据已更新（变更总数、最后更新时间）
- [ ] 变更记录按时间倒序排列（最新的在最上面）

---

**注意**：
- ⚠️ 本文档是模板和示例，实际使用时需要根据特性名称和具体变更内容填写
- ⚠️ 正常按计划实施的任务**不需要**记录到 implementation-log.md
- ⚠️ 仅在**首次发现变更时**创建 implementation-log.md，不需要在实施开始时就创建
- ⚠️ 所有变更必须**实时记录**，不允许实施完成后才补记录
