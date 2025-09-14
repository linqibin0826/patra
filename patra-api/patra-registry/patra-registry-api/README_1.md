# `patra-registry-api`

> **接口层（api）**是“对外契约集合”。它只定义跨边界的数据与协议：REST/RPC 的请求/响应 DTO、集成事件（IntegrationEvent）及其 Topic/Channel 常量、以及可选的 Feign 客户端接口。这里不包含任何业务逻辑与技术实现。

---

## 1. 职责边界

* **契约定义**：REST/RPC 输入/输出模型、事件 DTO、错误码与枚举（仅限协议层需要的枚举）。
* **协议无关实现**：不包含控制器、Service、持久化、消息发送/消费等实现；不承载业务规则。
* **跨服务复用**：adapter（入/出站适配）依赖本模块进行序列化/反序列化；其他服务也可安全引入以共享相同契约。

---

## 2. 依赖与禁区

* ✅ 允许依赖：`jakarta.validation`（用于 DTO 校验注解）。
* ⛔ 禁止依赖：`domain`、`infra`、`app`、Spring 框架、持久化/缓存/MQ 等任何实现类库。
* 说明：**不要**在 `api` 使用工具包（hutool、commons 等）；为保持契约纯净与可移植性，**仅**允许无实现副作用的注解类库。

---

## 3. 包结构

**包根**：`com.patra.registry.api`

```
rest/
  dto/
    request/                 // REST/RPC 请求 DTO
    response/                // REST/RPC 响应 DTO（或 View）
  path/
    RegistryPaths.java       // 统一的路径常量（可选）
  error/
    ErrorCodes.java          // 统一错误码枚举/常量（可选）

rpc/                         // 跨服务 RPC/Feign 的契约（可选）
  client/
    RegistryFeignClient.java // Feign 接口（仅接口与注解，无实现）
  dto/                       // 如 RPC 需要独立 DTO，可与 rest.dto 复用或独立

events/
  IntegrationEvent.java      // 对外事件 DTO（基类/接口）
  RegistryChangedEvent.java  // 具体事件 DTO（版本化）
  Topics.java                // Topic/Channel 常量

enums/
  ApiXxxEnum.java            // 协议层专用枚举（与领域枚举解耦）

schema/ (可选)
  *-schema.json              // 关键 DTO/事件的 JSON Schema（如需在网关做校验）
```

---

## 4. DTO 规范（REST/RPC）

* **语义清晰**：请求与响应 DTO 开闭分离；命令式与查询式命名可使用 `*Command` / `*Query` / `*View` / `*Response`。
* **字段规则**：

    * 使用 `jakarta.validation` 注解做最小校验（`@NotBlank`、`@Size`、`@Pattern` 等）。
    * 时间字段使用 `ISO-8601` 字符串（`OffsetDateTime`/`Instant`）或 long epoch；序列化格式在 adapter 层配置。
    * 避免暴露内部标识命名（如聚合内部字段名），对外采用稳定且可读的语义命名。
* **版本演进**：新增字段向后兼容；删除/语义变更走新 DTO（或在字段名中加 `V2`），保留旧版本一段时间。

---

## 5. 事件模型规范（IntegrationEvent）

* **定位**：`IntegrationEvent` 是**对外事件**的最终形态；**不要**把内部的 `AppEvent/DomainEvent` 放入 `api`。
* **命名与版本**：`SomethingChangedEvent`、`SomethingChangedEventV2`；或保留 `version` 字段。
* **最小字段集**（建议）：

    * `eventId`（全局唯一）、`occurredAt`、`version`、`producer`、`aggregateId`/`resourceId`
    * 业务必要的 `payload` 字段（结构化 DTO，避免“一个大 JSON 字符串”）。
* **Topic 常量**：集中在 `events/Topics.java`，避免 adapter 内部硬编码。
* **幂等与溯源**：显式提供去重键（如 `eventId`），为消费端实现幂等提供依据。

---

## 6. 枚举规范（协议层）

* `api` 可定义**协议专用的枚举**（如对外码值/文案），**不要**把领域层的业务枚举（与数据库强绑定的 `CodeEnum`）直接泄漏到对外协议。
* 码值与文案的稳定性由 `api` 维护；需要与领域枚举对齐时在 adapter 做映射。

---

## 7. Feign/RPC 规范（可选）

* **Feign 接口**可以放在 `api/rpc/client`，仅声明接口与注解（`@RequestMapping` / `@GetMapping` / `@PostMapping` / `@FeignClient` 等）。
* **不要**在 `api` 编写任何实现或配置类（如拦截器、Bean 定义），避免把运行时行为“硬塞”进契约层。
* DTO/路径常量统一从本模块引用，保证服务间调用的一致性与可演进性。

---

## 8. 错误码与返回约定（建议）

* **统一错误码**：在 `api/error/ErrorCodes.java` 定义枚举/常量；为每类错误保留稳定码值与简短英文 key（便于多语言）。
* **返回包络**：如果采用统一包络（如 `code`、`message`、`data`、`traceId`），应在 `api` 定义标准响应模型，并在 adapter 统一使用。
* **文档映射**：错误码与 HTTP 状态/业务语义一一对应，配合 OpenAPI/Markdown 文档发布。

---

## 9. 兼容性与文档

* **演进策略**：

    * 新增字段保持兼容，消费方按“容忍新增字段”解析。
    * 破坏性变化采用新路径/新事件名/新版本 DTO，旧版本标记 `@Deprecated` 并设置淘汰期。
* **契约文档**：

    * 推荐在仓库内维护 OpenAPI/AsyncAPI 或 Markdown 文档；
    * 改动走 MR/PR，评审通过后同步更新版本号与变更日志（CHANGELOG）。

---

## 10. 典型协作关系

* **adapter（出站）**：`AppEvent → (Map) → IntegrationEvent (api DTO) → MQ/RPC/HTTP`
* **adapter（入站）**：`IntegrationEvent/Request (api DTO) → (Map) → Command/Query → app 用例`
* **app/domain/infra**：不依赖 `api` 的实现细节；`api` 也不依赖它们的内部类型。

---

> 总之：`api` 只做“合同模板”，**不签合同、不执行合同**；adapter 拿模板去交流，app 编排流程，domain 定规则，infra 管数据与 Outbox。
