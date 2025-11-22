# 文档模板大全

Java 项目文档标准模板，确保文档一致性和完整性。

## 📚 文档层级结构

```
项目根目录/
├── README.md                          # 项目总览
├── ARCHITECTURE.md                    # 架构决策
├── CONTRIBUTING.md                    # 贡献指南
│
├── patra-{service}/                   # 微服务模块
│   ├── README.md                      # 模块说明
│   │
│   ├── patra-{service}-domain/        # 领域层
│   │   ├── README.md                  # 领域模型说明
│   │   └── src/main/java/.../
│   │       └── package-info.java      # 包级文档（每个包必须有）
│   │
│   ├── patra-{service}-app/           # 应用层
│   │   ├── README.md                  # 用例说明
│   │   └── src/main/java/.../
│   │       └── package-info.java      # 包级文档
│   │
│   └── patra-{service}-adapter/       # 适配器层
│       ├── README.md                  # API 文档
│       └── src/main/java/.../
│           └── package-info.java      # 包级文档
```

## 📝 模板集合

### 1. 模块 README.md 模板

```markdown
# patra-{service} 模块

## 📋 概述
[简要描述模块的职责和在系统中的角色]

## 🏗️ 模块结构

```plain
patra-{service}/
├── -api/         # 外部契约
├── -domain/      # 领域模型
├── -app/         # 应用逻辑
├── -infra/       # 基础设施
├── -adapter/     # 适配器
└── -boot/        # 启动器
```

## 🔑 核心概念
- **[概念1]**: [说明]
- **[概念2]**: [说明]

## 🚀 快速开始
### 本地运行
```bash
./mvnw clean install
./mvnw spring-boot:run
```

### 配置说明
| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | 服务端口 |

## 📡 API 接口
### 主要端点
- `POST /api/v1/[resource]` - 创建资源
- `GET /api/v1/[resource]/{id}` - 获取资源

## 🗃️ 数据库设计
### 核心表
- `t_[entity]` - [说明]

## 🔗 依赖关系
- 依赖: patra-common, patra-registry
- 被依赖: [其他模块]

## 🧪 测试
```bash
./mvnw test                 # 单元测试
./mvnw verify               # 集成测试
```

## 📚 相关文档
- [架构设计](../ARCHITECTURE.md)
- [API 规范](./patra-{service}-adapter/README.md)

## 👥 维护者
- [团队/负责人]

---
*最后更新: 2024-11-05*
```

### 2. package-info.java 模板

> ⭐ **创建原则**：必须先阅读代码理解包的职责，参考已有示范，使用中文描述，UTF-8 no BOM 编码

#### 📋 质量标准检查清单
```
✅ 开头简要描述（1-2句话）
✅ <h2>职责</h2> 小节
✅ <h2>核心组件</h2> 小节（列出主要类）
✅ <h2>使用示例</h2> 小节（实际代码）
✅ @author linqibin
✅ @since 0.1.0
✅ 使用中文描述
✅ UTF-8 no BOM 编码
```

---

#### Domain 层 - Aggregate/Entity 包

```java
/// [聚合根/实体] 包。
/// 
/// 本包定义 [业务概念] 的领域模型，遵循 DDD 设计原则。
/// 
/// ## 职责
/// 
/// - 封装业务规则和不变性约束
///   - 管理聚合内的一致性边界
///   - 发布领域事件
/// 
/// ## 核心组件
/// 
/// - {@link AggregateRoot} - 聚合根，管理一致性边界
///   - {@link Entity} - 实体，有唯一标识
/// 
/// ## 设计原则
/// 
/// - **不变性约束**: 通过构造函数和方法确保对象始终有效
///   - **富领域模型**: 业务逻辑封装在领域对象中
///   - **聚合边界**: 仅通过聚合根访问内部实体
/// 
/// ## 使用示例
/// ```java
/// // 创建聚合根
/// var aggregate = AggregateRoot.create(params);
/// 
/// // 业务操作
/// aggregate.businessMethod();
/// 
/// // 持久化（通过仓储）
/// repository.save(aggregate);
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.domain.model.aggregate;
```

#### Domain 层 - Value Object 包

```java
/// [值对象] 包。
/// 
/// 本包定义 [业务概念] 的值对象，遵循不可变性原则。
/// 
/// ## 职责
/// 
/// - 封装无标识的业务概念
///   - 提供类型安全的领域值
///   - 实现基于值的相等性比较
/// 
/// ## 核心组件
/// 
/// - {@link ValueObject1} - [说明]
///   - {@link ValueObject2} - [说明]
/// 
/// ## 设计原则
/// 
/// - **不可变性**: 所有字段为 final，无 setter
///   - **值相等**: 重写 equals() 和 hashCode()
///   - **自验证**: 构造时验证有效性
/// 
/// ## 使用示例
/// ```java
/// var vo = new ValueObject(value);
/// // 修改通过创建新对象
/// var newVo = vo.withNewValue(newValue);
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.domain.model.vo;
```

#### Domain 层 - Repository Port 包

```java
/// 仓储端口接口包。
/// 
/// 本包定义领域层的数据访问契约（Port），由基础设施层实现。
/// 
/// ## 职责
/// 
/// - 定义聚合根的持久化接口
///   - 提供领域语言的查询方法
///   - 隔离领域层与数据访问细节
/// 
/// ## 核心组件
/// 
/// - {@link AggregateRepository} - 聚合根仓储接口
/// 
/// ## 设计原则
/// 
/// - **领域语言**: 方法名使用业务术语
///   - **仅聚合根**: 不为实体单独提供仓储
///   - **纯接口**: 不包含实现细节
/// 
/// ## 使用示例
/// ```java
/// public interface ProvenanceRepository {
///     void save(Provenance aggregate);
///     Optional<Provenance> findById(ProvenanceId id);
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.domain.port;
```

---

#### App 层 - Orchestrator 包

```java
/// [UseCase名称] 编排器包。
/// 
/// 本包实现 [UseCase] 的应用层编排逻辑，协调领域对象、仓储和基础设施服务完成业务用例。
/// 
/// ## 职责
/// 
/// - 接收来自 Adapter 层的 Command/Query
///   - 协调 Domain 层的聚合根和领域服务
///   - 通过 Port 接口调用基础设施服务
///   - 管理事务边界
///   - 发布领域事件（通过 Outbox）
/// 
/// ## 核心组件
/// 
/// - {@link XxxOrchestrator} - 主编排器
///   - {@link XxxCoordinator} - 协调器（子流程）
///   - {@link XxxUseCase} - 用例接口
/// 
/// ## 设计原则
/// 
/// - **薄应用层**: 不包含业务逻辑，仅协调
///   - **事务一致性**: 使用 @Transactional 管理事务边界
///   - **依赖方向**: App → Domain, App → Port, App ← Adapter
/// 
/// ## 使用示例
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class XxxOrchestrator {
///     private final XxxRepository repository;
/// 
///     @Transactional
///     public XxxId execute(XxxCommand command) {
///         // 1. 调用领域逻辑
///         var aggregate = Xxx.create(command);
/// 
///         // 2. 持久化
///         repository.save(aggregate);
/// 
///         // 3. 发布事件
///         outbox.publish(aggregate.getDomainEvents());
/// 
///         return aggregate.getId();
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.app.usecase.xxx;
```

#### App 层 - Command/DTO 包

```java
/// [UseCase] 命令/DTO 包。
/// 
/// 本包定义应用层的输入命令和输出 DTO。
/// 
/// ## 职责
/// 
/// - 封装用例的输入参数（Command）
///   - 封装用例的输出结果（DTO）
///   - 提供与领域模型不同的视图
/// 
/// ## 核心组件
/// 
/// - {@link XxxCommand} - 输入命令
///   - {@link XxxResult} - 输出结果
/// 
/// ## 设计原则
/// 
/// - **不可变性**: Command 和 DTO 应为不可变对象
///   - **验证**: Command 可包含基本验证逻辑
///   - **转换**: 通过 Converter 与领域模型互转
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.app.usecase.xxx.command;
```

---

#### Adapter 层 - REST 包

```java
/// REST API 适配器包。
/// 
/// 本包实现驱动适配器，接收 HTTP 请求并转换为应用层用例调用。
/// 
/// ## 职责
/// 
/// - 实现 API 契约（patra-xxx-api）
///   - 验证请求 DTO（@Valid）
///   - 委托给应用层编排器
///   - 转换领域结果为 API 响应
///   - 映射领域异常为 HTTP 响应
/// 
/// ## 核心组件
/// 
/// - {@link XxxEndpointImpl} - REST 端点实现
///   - {@link XxxApiConverter} - DTO 转换器
/// 
/// ## 命名约定
/// 
/// - 端点实现: `*EndpointImpl`
///   - API 转换器: `*ApiConverter`
/// 
/// ## 使用示例
/// ```java
/// @RestController
/// @RequiredArgsConstructor
/// public class XxxEndpointImpl implements XxxEndpoint {
///     private final XxxOrchestrator orchestrator;
/// 
///     @Override
///     public XxxResponse createXxx(@Valid @RequestBody CreateXxxRequest request) {
///         var command = converter.toCommand(request);
///         var result = orchestrator.execute(command);
///         return converter.toResponse(result);
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.adapter.rest;
```

#### Adapter 层 - Scheduler/MQ 包

```java
/// [Scheduler/MQ] 适配器包。
/// 
/// 本包实现驱动适配器，处理定时任务或消息队列事件。
/// 
/// ## 职责
/// 
/// - 处理定时触发的任务
///   - 消费消息队列事件
///   - 转换外部事件为应用层命令
///   - 处理异步任务
/// 
/// ## 核心组件
/// 
/// - {@link XxxScheduler} - 定时任务
///   - {@link XxxListener} - 消息监听器
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.adapter.scheduler;
```

---

#### Infra 层 - Repository 实现包

```java
/// 仓储实现包。
/// 
/// 本包实现 Domain 层定义的 Repository Port 接口，提供数据持久化能力。
/// 
/// ## 职责
/// 
/// - 实现仓储接口（Port）
///   - 领域对象与持久化对象互转
///   - 封装数据访问技术细节（MyBatis）
/// 
/// ## 核心组件
/// 
/// - {@link XxxRepositoryImpl} - 仓储实现
///   - {@link XxxConverter} - 对象转换器
///   - {@link XxxMapper} - MyBatis Mapper
///   - {@link XxxDO} - 数据库对象
/// 
/// ## 设计原则
/// 
/// - **隔离**: DO 对象不能离开基础设施层
///   - **转换**: 使用 MapStruct 进行 DO ↔ Domain 转换
///   - **封装**: 隐藏 MyBatis 实现细节
/// 
/// ## 使用示例
/// ```java
/// @Repository
/// @RequiredArgsConstructor
/// public class XxxRepositoryImpl implements XxxRepository {
///     private final XxxMapper mapper;
///     private final XxxConverter converter;
/// 
///     @Override
///     public void save(Xxx aggregate) {
///         var entity = converter.toEntity(aggregate);
///         mapper.insert(entity);
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.infra.persistence.repository;
```

#### Infra 层 - Mapper/Entity 包

```java
/// MyBatis Mapper 和数据库实体包。
/// 
/// 本包定义 MyBatis Mapper 接口和数据库实体（DO）。
/// 
/// ## 职责
/// 
/// - 定义数据库表映射（DO）
///   - 定义 SQL 操作接口（Mapper）
///   - 支持复杂查询（XML Mapper）
/// 
/// ## 核心组件
/// 
/// - {@link XxxDO} - 数据库对象
///   - {@link XxxMapper} - MyBatis Mapper
/// 
/// ## 设计原则
/// 
/// - **表对应**: 一个 DO 对应一个表
///   - **审计字段**: 继承 BaseDO 获得审计能力
///   - **复杂查询**: 使用 XML Mapper
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.infra.persistence.mapper;
```

---

#### API 层 - DTO 包

```java
/// [功能模块] REST API DTO 包。
/// 
/// 本包定义对外暴露的 REST API 数据传输对象（DTO）。
/// 
/// ## 职责
/// 
/// - 定义请求 DTO（Request）
///   - 定义响应 DTO（Response）
///   - 定义验证规则（@Valid 注解）
/// 
/// ## 核心组件
/// 
/// - {@link XxxRequest} - 请求 DTO
///   - {@link XxxResponse} - 响应 DTO
/// 
/// ## 设计原则
/// 
/// - **不可变性**: DTO 应为不可变对象
///   - **验证**: 使用 Bean Validation 注解
///   - **版本化**: 通过 URL 版本管理 API 演进
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.api.dto.xxx;
```

#### API 层 - Endpoint 接口包

```java
/// REST API 端点接口包。
/// 
/// 本包定义 OpenAPI 端点接口契约，由 Adapter 层实现。
/// 
/// ## 职责
/// 
/// - 定义 REST API 契约
///   - 使用 OpenAPI 注解描述 API
///   - 定义请求/响应格式
/// 
/// ## 核心组件
/// 
/// - {@link XxxEndpoint} - API 端点接口
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.{service}.api.endpoint;
```

### 3. API 文档模板

```markdown
# API 文档 - patra-{service}

## 基础信息
- **基础路径**: `/api/v1`
- **协议**: HTTPS
- **认证**: Bearer Token

## 接口列表

### 1. 创建资源
**端点**: `POST /api/v1/resources`

**请求头**:
- `Content-Type: application/json`
- `Authorization: Bearer {token}`

**请求体**:
```json
{
  "name": "资源名称",
  "type": "资源类型",
  "config": {}
}
```

**响应**:
- **200 OK**
```json
{
  "id": "12345",
  "name": "资源名称",
  "createdAt": "2024-11-05T10:00:00Z"
}
```

- **400 Bad Request**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "名称不能为空",
  "instance": "/api/v1/resources"
}
```

### 2. 获取资源
**端点**: `GET /api/v1/resources/{id}`

[继续其他端点...]

## 错误码
| 状态码 | 说明 | 处理建议 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查请求格式 |
| 401 | 未认证 | 提供有效 token |
| 403 | 无权限 | 检查用户权限 |
| 404 | 资源不存在 | 检查资源 ID |
| 500 | 服务器错误 | 联系管理员 |

## 示例代码

### cURL
```bash
curl -X POST https://api.patra.com/api/v1/resources \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"name":"测试资源"}'
```

### Java
```java
var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.patra.com/api/v1/resources"))
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer " + token)
    .POST(BodyPublishers.ofString("{\"name\":\"测试资源\"}"))
    .build();
var response = client.send(request, BodyHandlers.ofString());
```
```

### 4. 架构文档模板

```markdown
# 架构决策记录 (ADR)

## ADR-001: 采用六边形架构

### 状态
已采纳

### 背景
需要一个支持业务逻辑与技术实现分离的架构。

### 决策
采用六边形架构（端口与适配器）+ DDD。

### 后果
- ✅ 业务逻辑独立于框架
- ✅ 易于测试
- ✅ 技术可替换
- ⚠️ 初始复杂度较高
- ⚠️ 需要团队培训

### 层级职责
1. **Domain 层**: 纯业务逻辑
2. **Application 层**: 用例编排
3. **Infrastructure 层**: 技术实现
4. **Adapter 层**: 外部接口

---

## ADR-002: 选择 MyBatis-Plus

### 状态
已采纳

[继续其他 ADR...]
```

## 📋 文档检查清单

### 模块文档完整性
```
每个模块必须包含：
□ 模块根目录 README.md
□ 每个子模块 README.md
□ 每个包的 package-info.java
□ API 文档（adapter 模块）
□ 领域模型文档（domain 模块）
```

### package-info.java 必须包含
```
□ 包的职责说明
□ 核心类/接口列表
□ 设计决策说明
□ 使用示例
□ 注意事项
□ @since 标记
□ @author 标记
```

### README.md 必须包含
```
□ 模块概述
□ 目录结构
□ 核心概念
□ 快速开始
□ API 说明（如适用）
□ 配置说明
□ 测试指南
□ 相关文档链接
```

## 📚 参考标准

- [Java 文档注释规范](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)
- [README 最佳实践](https://www.makeareadme.com/)
- [架构决策记录 (ADR)](https://adr.github.io/)
- [RFC 7807 - Problem Details](https://tools.ietf.org/html/rfc7807)

---

**记住**：好的文档是项目成功的关键，package-info.java 是 Java 项目的标准文档实践！
