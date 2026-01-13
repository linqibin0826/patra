# Patra API（Codex 项目规范）

本文件用于 Codex 的项目规则加载，内容由 `./.claude/rules/` 规范重新整理汇总而来；原始文件保留不变。

---

## 项目概览

- **项目**：Patra — 医学出版物数据平台（采集、解析、存储 PubMed/EPMC/Crossref 等外部数据源的文献与期刊数据）
- **架构**：微服务 + 六边形架构 + DDD + 事件驱动
- **技术栈**：Java 25、Spring Boot 4.0.1、Spring Data JPA、MySQL 8.x、Consul

### 核心服务

- `patra-registry`：SSOT 注册中心（Provenance 配置、Expression 元数据、字典管理）
- `patra-ingest`：数据采集服务（Plan → Task → 外部 API 调用）
- `patra-catalog`：目录服务（文献、期刊数据索引）
- `patra-gateway-boot`：API 网关（路由、认证、限流）

### 模块结构（六边形分层）

微服务目录约定：

```
patra-{service}/
├── patra-{service}-domain   # 纯 Java 领域模型
├── patra-{service}-app      # 应用层（编排/事务）
├── patra-{service}-infra    # 基础设施（持久化/外部调用）
├── patra-{service}-adapter  # 适配层（Controller/Job/Listener）
├── patra-{service}-api      # 服务契约（DTO/HTTP Interface/常量）
└── patra-{service}-boot     # 启动入口
```

通用与基础设施模块：

- `patra-common-core`：DDD 基类、异常体系、共享枚举、工具类
- `patra-common-model`：Shared Kernel 数据模型
- `patra-common-storage`：对象存储键生成模板

Starter 模块（按需引入，优先使用项目 Starter，避免直接引入原始依赖）：

- `patra-spring-boot-starter-core/web/jpa/batch/rest-client/http-interface/object-storage/observability/redisson/provenance/test`

---

## 架构与分层规则（总则）

- **依赖方向（宏观）**：
  - `domain`：不依赖任何框架；通过 `Port`/`Repository` 抽象外部依赖
  - `app`：编排领域行为与事务边界；依赖 `domain`
  - `infra`：实现端口；依赖 `domain`（不应反向依赖 `app/adapter`）
  - `adapter`：入站协议转换；调用 `CommandBus`（写）或 `QueryService`（读），不直接触达持久化
  - `api`：只定义契约（DTO/HTTP Interface/常量），不承载实现与业务
  - `boot`：唯一启动入口与装配配置

---

## 各层开发规范

### Domain 层（`*-domain`）

- **原则**：纯 Java（允许 Lombok），封装业务规则与不变式
- **禁止**：
  - 禁止使用 Spring/JPA 等框架注解与 API
  - 禁止依赖 Infrastructure/Adapter 代码
  - 禁止加入可观测性实现代码（日志/指标/Tracing 的技术细节由外层处理）
- **异常**：继承 `DomainException`，携带 `StandardErrorTrait` 语义特征
- **聚合**：
  - 子实体/值对象的修改视为聚合根状态变更
  - 修改聚合内任何数据必须递增 `version`
  - 通过 `Repository.save(aggregateRoot)` 持久化整个聚合

### Application 层（`*-app`）

- **职责**：编排领域服务，定义事务边界（`@Transactional`），完成 `Command → Domain → Result` 的协调
- **命名**：
  - `Command`：`{Action}{Entity}Command`
  - `Handler`：`{Action}{Entity}Handler`
  - `Result`：`{Action}{Entity}Result`
- **事务**：
  - 只允许在 Application 层的 `Handler.handle()` 上使用 `@Transactional`
  - 禁止在 `domain/infra/adapter` 声明事务
- **异常**：
  - 使用 `ApplicationException` 包装领域异常
  - 携带 `ErrorCodeLike`，格式：`{SERVICE}-{0xxx}`

### Infrastructure 层（`*-infra`）

- **职责**：实现端口接口（`Repository`/`Port`），完成数据库/消息队列/外部 API 的技术适配
- **命名与目录建议**：
  - `{Entity}Repository` → `{Entity}RepositoryAdapter`（建议放在 `adapter/persistence/`）
  - `{Function}Port` → `{Function}Adapter`（建议放在 `adapter/{function}/`）
- **对象转换**：使用 MapStruct 实现 `JpaEntity ↔ Domain` / `DTO ↔ DO` 转换，禁止手写映射
- **异常映射**：通过 `ErrorMappingContributor` SPI 统一映射第三方异常（SQL/外部 API 等）
- **依赖（Starter）**：数据库用 `patra-spring-boot-starter-jpa`（`starter-jpa`）；对象存储用 `patra-spring-boot-starter-object-storage`（`starter-object-storage`）；REST 调用用 `patra-spring-boot-starter-rest-client`（`starter-rest-client`）；服务间调用用 `patra-spring-boot-starter-http-interface`（`starter-http-interface`）

### Adapter 层（`*-adapter`）

- **职责**：入站适配与协议转换（HTTP/消息/任务调度 → `Command`），并通过 `CommandBus` 分发
- **禁止**：
  - Controller/Job/Listener 中禁止承载业务逻辑
  - 禁止直接调用持久化（Repository/Dao）；写操作必须走 `CommandBus`
  - 请求/响应 DTO 中禁止出现领域对象
  - 禁止直接注入 `CommandHandler`（写入口只注入 `CommandBus`）
- **HTTP Interface 错误处理**：
  - 捕获 `RemoteCallException`，优先基于 `ex.getErrorTraits()` 判断
  - 备选：使用 `RemoteErrorHelper`
  - 禁止直接捕获 `RestClientException`
- **依赖（Starter）**：Web 用 `patra-spring-boot-starter-web`（`starter-web`）；HTTP Interface 用 `patra-spring-boot-starter-http-interface`（`starter-http-interface`）

### API 模块（`*-api`）

- **职责**：定义服务契约（HTTP Interface Endpoint、DTO、常量），用于跨服务共享
- **禁止**：
  - 禁止包含 Controller 实现
  - 禁止包含业务逻辑
  - 禁止依赖 `domain/app/infra`（保持契约的独立性）

### Boot 模块（`*-boot`）

- **职责**：唯一启动入口（`@SpringBootApplication`）、依赖装配与环境配置
- **命名**：启动类使用 `Patra{Service}Application`
- **配置**：
  - 使用 `@ConfigurationProperties` 进行配置绑定
  - 禁止硬编码配置值

---

## 技术规范

### CommandBus（写模型）

- **定位**：`adapter` 与 `app` 之间的统一分发中心；写操作统一通过 `CommandBus.handle(...)`（可用拦截器链处理 Tracing/Logging/Metrics）
- **核心接口**：`Command<R>`、`CommandHandler<C, R>`、`CommandBus`、`CommandInterceptor`（Spring 实现：`SimpleCommandBus` 位于 `patra-spring-boot-starter-core`）
- **Command 定义**：
  - 使用 Java `record`，保持不可变
  - 参数校验放在 compact constructor；`Command` 仅做数据载体，不承载业务逻辑
- **Handler 约束**：
  - 实现 `CommandHandler<C, R>`
  - `handle()` 可使用 `@Transactional`（事务只属于 Application 层）
  - 禁止 `Handler` 调用其他 `Handler`（需要跨用例协作时用事件驱动）
  - 尽量避免让 `Handler` 依赖框架特定类型，保持可测试性
- **Adapter 约束**：写入口只注入 `CommandBus`，禁止注入具体 `Handler`
- **内置拦截器（顺序）**：
  - `TracingCommandInterceptor`（Order=50，依赖 `ObservationRegistry`）
  - `LoggingCommandInterceptor`（Order=100，默认启用）
  - `MetricsCommandInterceptor`（Order=200，依赖 `MeterRegistry`）
- **异步**：需要异步时使用 `CommandBus.handleAsync(...)`；线程池由 `patra.command-bus.async.*` 配置管理
- **配置**：拦截器开关使用 `patra.command-bus.interceptors.{logging|metrics|tracing}`
- **迁移约定**：不再使用 `*Orchestrator`/`*UseCase` 命名；统一使用 `*Handler`（Handler 内部阶段可用 `*Phase` 命名）
- **测试建议**：
  - Handler：纯单元测试，Mock `Repository/Port`，不依赖 Spring
  - Adapter：`@WebMvcTest` + `@MockitoBean CommandBus`，只验证委派到 `CommandBus`
- **特殊约定**：
  - 无返回值命令使用 `Command<Void>`，`Handler` 返回 `null`
  - 若 `Command` 某些字段允许为 `null`（配置覆盖/可选参数），必须在 JavaDoc 说明回退策略

### Query（读模型）

- **规则**：查询不经 `CommandBus`；直接注入查询服务（如 `*QueryService`）或只读仓储即可
- **允许**：同一 `Controller` 可同时注入 `CommandBus`（写）与 `QueryService`（读），各走各的路径

### JPA

- 所有 JPA Entity 必须继承 `BaseJpaEntity`（雪花 ID、审计字段、乐观锁）；需要软删除的实体继承 `SoftDeletableJpaEntity`
- 命名：`{Name}Entity`、`{Name}Dao`、`{Name}JpaMapper`、`{Type}AttributeConverter`
- Entity ↔ Aggregate/DO 转换使用 MapStruct，禁止手写
- 批量保存使用 `saveAll()`；大批量（>500）建议 `flush()` + `clear()` 控制内存
- ID 使用 `SnowflakeIdGenerator.getId()` 预分配，禁止数据库自增
- 事务只在 Application 层使用 `@Transactional`；Infrastructure 层禁止声明事务
- 软删除仅对聚合根和被引用的配置数据启用（继承 `SoftDeletableJpaEntity`）；外部数据快照等子表使用物理删除
- 数据库连接 URL 必须包含 `rewriteBatchedStatements=true`

### 异常处理

- **异常体系**：
  - `DomainException`：领域层异常基类，实现 `HasErrorTraits`，携带 `StandardErrorTrait`
  - `ApplicationException`：应用层异常基类，携带明确的 `ErrorCodeLike`
  - `RemoteCallException`：HTTP Interface 调用失败的统一异常，实现 `HasErrorTraits`
- **分层规则**：
  - Domain：只抛 `DomainException`，禁止依赖框架异常
  - Application：用 `ApplicationException` 包装领域异常并补充错误码
  - Infrastructure：用 `ErrorMappingContributor` 映射第三方异常
  - Adapter：捕获 `RemoteCallException` 并基于语义特征转换为领域语义
- **错误码格式**：`{SERVICE}-{0xxx}`（0xxx 对齐 HTTP 语义，如 0404/0409/0422/0500）
- **HTTP Interface**：禁止捕获 `RestClientException`，必须走 `RemoteCallException`

### 可观测性

- Traces/Metrics/Logs 统一走：OTel Agent → OTLP → OTel Collector
- Spring/Micrometer 指标通过 Agent Bridge 导出；自定义业务指标使用 Micrometer API（`MeterRegistry`、`@Timed`、`@Counted`）
- 公共标签由 `CommonTagsMeterFilter` 注入：`application`、`environment`、`region`、`cluster`
- **禁止**：
  - 禁止直接使用 OpenTelemetry SDK（`io.opentelemetry.api.*`）
  - 禁止手动创建/管理 Span 生命周期

---

## 测试规范

### 测试分层与命名

- 单元测试：`*Test.java`
  - Domain：纯单元测试，无 Mock
  - Application：单元测试，Mock Ports
- 集成/切片测试：`*IT.java`
  - Infrastructure：TestContainers/WireMock 优先
  - Adapter：切片测试，使用 `@MockitoBean`
- E2E：`*E2E.java`（Boot 模块，真实中间件/TestContainers）

### 超时建议

- 单元测试：`@Timeout` ≤ 2s（无 I/O）
- 集成/切片：`@Timeout` ≤ 30s（容器启动）
- E2E：`@Timeout` ≤ 60s；Awaitility `atMost` ≤ 5s

### 约束与注意事项

- 使用 `@MockitoBean` 进行 Mock 注入（`@MockBean` 已在 Spring Boot 4.0 中移除）
- 测试基础设施统一使用 `patra-spring-boot-starter-test`，避免重复造轮子/重复依赖声明
- 优先使用 TestContainers 模拟真实中间件，避免使用内存数据库
- `@DataJpaTest` 数据准备使用 `TestEntityManager` 或 `JpaRepository`
- E2E 测试覆盖核心业务流程；`Db.saveBatch()` 相关验证放在 E2E（切片测试可能受事务隔离影响）

### 测试金字塔目标占比

- 单元测试 ≥ 75%
- 切片/集成测试 ~ 20%
- E2E < 5%

### 覆盖率要求

- 行覆盖率 ≥ 80%，分支覆盖率 ≥ 70%，关键业务逻辑 100%
- 覆盖率排除项：DTO getter/setter、配置类、启动类

---

## 代码风格与命名

- 遵循 Google Java 风格（格式化、命名、类组织）
- 命名必须表达抽象层级：抽象用抽象名（`Repository/Service/Port`），具体实现用具体名（如 `PubMedRepository`）；避免用 `Manager/Helper/Util` 作为业务类名
- 所有方法必须写 JavaDoc，使用 `///` 风格，内容使用 Markdown（避免 HTML 标签）
- 默认禁止使用 Fully Qualified Name，必须 `import`；仅在类名冲突时使用全类名消歧义
- 优先使用 Lombok（`@Getter/@Setter/@Data/@Builder/@AllArgsConstructor/@NoArgsConstructor` 等），仅在需要自定义逻辑时手写
