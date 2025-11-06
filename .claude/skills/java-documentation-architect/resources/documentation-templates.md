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
/**
 * [聚合根/实体] 包。
 *
 * <p>本包定义 [业务概念] 的领域模型，遵循 DDD 设计原则。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>封装业务规则和不变性约束
 *   <li>管理聚合内的一致性边界
 *   <li>发布领域事件
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link AggregateRoot} - 聚合根，管理一致性边界
 *   <li>{@link Entity} - 实体，有唯一标识
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>不变性约束</strong>: 通过构造函数和方法确保对象始终有效
 *   <li><strong>富领域模型</strong>: 业务逻辑封装在领域对象中
 *   <li><strong>聚合边界</strong>: 仅通过聚合根访问内部实体
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建聚合根
 * var aggregate = AggregateRoot.create(params);
 *
 * // 业务操作
 * aggregate.businessMethod();
 *
 * // 持久化（通过仓储）
 * repository.save(aggregate);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.domain.model.aggregate;
```

#### Domain 层 - Value Object 包

```java
/**
 * [值对象] 包。
 *
 * <p>本包定义 [业务概念] 的值对象，遵循不可变性原则。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>封装无标识的业务概念
 *   <li>提供类型安全的领域值
 *   <li>实现基于值的相等性比较
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link ValueObject1} - [说明]
 *   <li>{@link ValueObject2} - [说明]
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>不可变性</strong>: 所有字段为 final，无 setter
 *   <li><strong>值相等</strong>: 重写 equals() 和 hashCode()
 *   <li><strong>自验证</strong>: 构造时验证有效性
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * var vo = new ValueObject(value);
 * // 修改通过创建新对象
 * var newVo = vo.withNewValue(newValue);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.domain.model.vo;
```

#### Domain 层 - Repository Port 包

```java
/**
 * 仓储端口接口包。
 *
 * <p>本包定义领域层的数据访问契约（Port），由基础设施层实现。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>定义聚合根的持久化接口
 *   <li>提供领域语言的查询方法
 *   <li>隔离领域层与数据访问细节
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link AggregateRepository} - 聚合根仓储接口
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>领域语言</strong>: 方法名使用业务术语
 *   <li><strong>仅聚合根</strong>: 不为实体单独提供仓储
 *   <li><strong>纯接口</strong>: 不包含实现细节
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * public interface ProvenanceRepository {
 *     void save(Provenance aggregate);
 *     Optional<Provenance> findById(ProvenanceId id);
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.domain.port;
```

---

#### App 层 - Orchestrator 包

```java
/**
 * [UseCase名称] 编排器包。
 *
 * <p>本包实现 [UseCase] 的应用层编排逻辑，协调领域对象、仓储和基础设施服务完成业务用例。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>接收来自 Adapter 层的 Command/Query
 *   <li>协调 Domain 层的聚合根和领域服务
 *   <li>通过 Port 接口调用基础设施服务
 *   <li>管理事务边界
 *   <li>发布领域事件（通过 Outbox）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxOrchestrator} - 主编排器
 *   <li>{@link XxxCoordinator} - 协调器（子流程）
 *   <li>{@link XxxUseCase} - 用例接口
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>薄应用层</strong>: 不包含业务逻辑，仅协调
 *   <li><strong>事务一致性</strong>: 使用 @Transactional 管理事务边界
 *   <li><strong>依赖方向</strong>: App → Domain, App → Port, App ← Adapter
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class XxxOrchestrator {
 *     private final XxxRepository repository;
 *
 *     @Transactional
 *     public XxxId execute(XxxCommand command) {
 *         // 1. 调用领域逻辑
 *         var aggregate = Xxx.create(command);
 *
 *         // 2. 持久化
 *         repository.save(aggregate);
 *
 *         // 3. 发布事件
 *         outbox.publish(aggregate.getDomainEvents());
 *
 *         return aggregate.getId();
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.app.usecase.xxx;
```

#### App 层 - Command/DTO 包

```java
/**
 * [UseCase] 命令/DTO 包。
 *
 * <p>本包定义应用层的输入命令和输出 DTO。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>封装用例的输入参数（Command）
 *   <li>封装用例的输出结果（DTO）
 *   <li>提供与领域模型不同的视图
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxCommand} - 输入命令
 *   <li>{@link XxxResult} - 输出结果
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>不可变性</strong>: Command 和 DTO 应为不可变对象
 *   <li><strong>验证</strong>: Command 可包含基本验证逻辑
 *   <li><strong>转换</strong>: 通过 Converter 与领域模型互转
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.app.usecase.xxx.command;
```

---

#### Adapter 层 - REST 包

```java
/**
 * REST API 适配器包。
 *
 * <p>本包实现驱动适配器，接收 HTTP 请求并转换为应用层用例调用。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>实现 API 契约（patra-xxx-api）
 *   <li>验证请求 DTO（@Valid）
 *   <li>委托给应用层编排器
 *   <li>转换领域结果为 API 响应
 *   <li>映射领域异常为 HTTP 响应
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxEndpointImpl} - REST 端点实现
 *   <li>{@link XxxApiConverter} - DTO 转换器
 * </ul>
 *
 * <h2>命名约定</h2>
 * <ul>
 *   <li>端点实现: {@code *EndpointImpl}
 *   <li>API 转换器: {@code *ApiConverter}
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class XxxEndpointImpl implements XxxEndpoint {
 *     private final XxxOrchestrator orchestrator;
 *
 *     @Override
 *     public XxxResponse createXxx(@Valid @RequestBody CreateXxxRequest request) {
 *         var command = converter.toCommand(request);
 *         var result = orchestrator.execute(command);
 *         return converter.toResponse(result);
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.adapter.rest;
```

#### Adapter 层 - Scheduler/MQ 包

```java
/**
 * [Scheduler/MQ] 适配器包。
 *
 * <p>本包实现驱动适配器，处理定时任务或消息队列事件。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>处理定时触发的任务
 *   <li>消费消息队列事件
 *   <li>转换外部事件为应用层命令
 *   <li>处理异步任务
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxScheduler} - 定时任务
 *   <li>{@link XxxListener} - 消息监听器
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.adapter.scheduler;
```

---

#### Infra 层 - Repository 实现包

```java
/**
 * 仓储实现包。
 *
 * <p>本包实现 Domain 层定义的 Repository Port 接口，提供数据持久化能力。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>实现仓储接口（Port）
 *   <li>领域对象与持久化对象互转
 *   <li>封装数据访问技术细节（MyBatis）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxRepositoryImpl} - 仓储实现
 *   <li>{@link XxxConverter} - 对象转换器
 *   <li>{@link XxxMapper} - MyBatis Mapper
 *   <li>{@link XxxDO} - 数据库对象
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>隔离</strong>: DO 对象不能离开基础设施层
 *   <li><strong>转换</strong>: 使用 MapStruct 进行 DO ↔ Domain 转换
 *   <li><strong>封装</strong>: 隐藏 MyBatis 实现细节
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Repository
 * @RequiredArgsConstructor
 * public class XxxRepositoryImpl implements XxxRepository {
 *     private final XxxMapper mapper;
 *     private final XxxConverter converter;
 *
 *     @Override
 *     public void save(Xxx aggregate) {
 *         var entity = converter.toEntity(aggregate);
 *         mapper.insert(entity);
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.infra.persistence.repository;
```

#### Infra 层 - Mapper/Entity 包

```java
/**
 * MyBatis Mapper 和数据库实体包。
 *
 * <p>本包定义 MyBatis Mapper 接口和数据库实体（DO）。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>定义数据库表映射（DO）
 *   <li>定义 SQL 操作接口（Mapper）
 *   <li>支持复杂查询（XML Mapper）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxDO} - 数据库对象
 *   <li>{@link XxxMapper} - MyBatis Mapper
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>表对应</strong>: 一个 DO 对应一个表
 *   <li><strong>审计字段</strong>: 继承 BaseDO 获得审计能力
 *   <li><strong>复杂查询</strong>: 使用 XML Mapper
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.infra.persistence.mapper;
```

---

#### API 层 - DTO 包

```java
/**
 * [功能模块] REST API DTO 包。
 *
 * <p>本包定义对外暴露的 REST API 数据传输对象（DTO）。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>定义请求 DTO（Request）
 *   <li>定义响应 DTO（Response）
 *   <li>定义验证规则（@Valid 注解）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxRequest} - 请求 DTO
 *   <li>{@link XxxResponse} - 响应 DTO
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>不可变性</strong>: DTO 应为不可变对象
 *   <li><strong>验证</strong>: 使用 Bean Validation 注解
 *   <li><strong>版本化</strong>: 通过 URL 版本管理 API 演进
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.{service}.api.dto.xxx;
```

#### API 层 - Endpoint 接口包

```java
/**
 * REST API 端点接口包。
 *
 * <p>本包定义 OpenAPI 端点接口契约，由 Adapter 层实现。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>定义 REST API 契约
 *   <li>使用 OpenAPI 注解描述 API
 *   <li>定义请求/响应格式
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link XxxEndpoint} - API 端点接口
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
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
