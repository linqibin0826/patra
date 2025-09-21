# Copilot Instructions · patra-ingest

本仓库采用 **六边形架构（Hexagonal / Ports & Adapters） + DDD 分层**。  
Copilot 在生成或改写代码时必须严格遵守以下约束。

---

## 0. 通用要求

- **强制使用 Lombok 注解**：`@Data`、`@Getter`、`@Setter`、`@Builder`、`@SuperBuilder`、`@EqualsAndHashCode`、`@Value` 等。
    - **禁止**手写 getter/setter/toString/equals/hashCode。
- **MapStruct 转换器**必须被 Spring 管理：`@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)`。
- **Mapper 接口** `extends BaseMapper<xxxDO>`，
---

## 1. 分层职责与依赖

### api 层

- **职责**：对外契约（RPC DTO、IntegrationEvent DTO、协议枚举、错误码、路径常量）。
- **依赖**：仅允许 `jakarta.validation`。
- **禁区**：禁止依赖 Spring/domain/infra/app。
- **规范**：DTO 字段语义清晰，避免暴露内部标识；IntegrationEvent 包含最小字段集（eventId、occurredAt、aggregateId 等）。

### adapter 层

- **职责**：协议适配（REST 控制器、MQ Consumer/Producer、Scheduler）。
- **依赖**：`app` + `api`，`patra-spring-boot-starter-web`、MQ/RPC SDK等。
- **禁区**：禁止依赖 domain/infra。
- **规范**：
    - Controller：只做参数校验/参数映射/转发，不写业务逻辑。
    - Producer：实现 app 的发布端口，映射 AppEvent → IntegrationEvent → 调用 MQ SDK。
    - Consumer：消费 IntegrationEvent DTO → 转换为 command/query → 调用 app。
    - Scheduler：只触发 app 用例，不含业务。

### app 层

- **职责**：用例编排（权限、事务、聚合协作、事件触发）；转换领域异常为应用异常。
- **依赖**：`domain`、`patra-common`、`patra-spring-boot-starter-core`等。
- **禁区**：禁止依赖 adapter/infra/api。

### domain 层

- **职责**：内核（聚合、实体、VO、领域事件）。
- **依赖**：仅允许 `patra-common`（hutool-core）。
- **禁区**：禁止任何 Spring/MyBatis/Web/api 依赖与注解。
- **规范**：
    - 聚合：边界清晰，不变量在构造与变更时校验。
    - 值对象：不可变；推荐 `@Value` 或 `@AllArgsConstructor` + `@EqualsAndHashCode`。
    - 领域事件：仅描述事实，不含技术细节。
    - 枚举：数据库字段枚举必须实现 `CodeEnum<C>`；二值开关用 `boolean`。

### infra 层

- **职责**：持久化与技术落地；实现 `app.port.*`；DO ↔ 聚合映射；Outbox 托管。
- **依赖**：`domain`、`app`, MyBatis-Plus Starter。
- **禁区**：禁止依赖 adapter/api。
- **规范**：
    - DO：继承 `BaseDO`，字段用 Lombok 注解；枚举字段用 domain 枚举；JSON 字段用 `JsonNode` 或 `String`。
    - Mapper：`extends BaseMapper<xxDO>`。
    - RepositoryImpl：组合 baseMapper 与 Converter 。
    - Converter：使用 MapStruct，注解最小化；只做字段映射，不含业务逻辑。
    - Outbox：与聚合同事务落库，Adapter Relay 扫描发布。

---

## 2. 目录结构（统一）

```

api/
    dto/
    rpc/client/
    events/
    enums/
    error/

adapter/
    rest/controller/
    rest/mapping/xxxReq/RespConverter.java
    rest/dto/resp
    rest/dto/req
    scheduler/
    mq/consumer/
    mq/producer/
    config/

app/
    service/
    usecase/{command,query}/
    mapping/xxxAppConverter.java
    security/
    event/publisher/
    port/in/
    port/out/
    tx/
    config/

domain/
    aggregate/
    vo/
    event/
    enums/

infra/
    persistence/{entity,mapper,repository}/
    mapstruct/xxxConverter.java
    config/
```

---

## 3. 事件分层与流转

- **DomainEvent**（domain 内部事实）
- **AppEvent**（app 内部应用事件）
- **IntegrationEvent**（api 定义对外契约）
- **发布链路**：DomainEvent → AppEvent → IntegrationEvent → Adapter Producer 发送。
- **订阅链路**：Adapter Consumer 消费 IntegrationEvent → 转换 → 调用 app → domain 执行业务 → infra 落库。
- **Outbox 模式**：infra 与聚合同事务写入 Outbox，adapter Relay 扫描并发布。

---

## 4. 质量守则

- 分层依赖合法，无跨层耦合。
- Lombok 注解齐全，不手写 getter/setter。
- MapStruct 转换器简洁，必须 Spring 管理。
- DTO 与事件演进采用版本化；新增字段必须向后兼容。
- 测试策略分层明确：
    - domain：单测聚合行为，不依赖 DB。
    - app：测试编排链路。
    - infra：仓储单测 + 集成测试（容器化 DB）。
    - adapter：契约/集成测试。
    - api：保证 DTO/事件序列化正确。  

## 5. 代码注释
- **所有类**必须有 Javadoc，含 `@author linqibin`、`@since 0.1.0`、必要时 `@see/@link`。
- 为**公共类/方法**及**非简单私有方法**编写清晰 Javadoc，解释 **What** 与 **Why**（非 How）。
- 使用 `@param`、`@return`、`@throws` 详尽记录方法签名。
