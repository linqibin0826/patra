# `patra-registry-adapter`

> 适配层（adapter）是系统的**边界层**，负责对接外部协议（Web、RPC、MQ、Scheduler 等），将外部请求/事件转为应用层可理解的命令/查询对象，调用 `app` 完成用例；同时将应用层事件或结果，转化为对外契约（`api` 模块定义的 DTO/事件模型），通过具体协议对外发送。

---

## 1. 职责边界

* **入站职责**：接收外部请求/消息，完成参数校验和格式转换，调用 `app` 用例服务。
* **出站职责**：实现 `app` 的事件发布端口，将应用事件映射为 `api` 中的事件 DTO，通过 MQ/RPC/HTTP 等协议对外发送。
* **协议适配**：统一对接 Web 控制器、消息队列消费者、调度任务入口，不承载任何业务规则。
* **契约承载**：只使用 `api` 模块中的 DTO/事件模型作为对外交互的契约格式。

---

## 2. 依赖与禁区

* ✅ 允许依赖：`app`、`api`，可选依赖 `patra-spring-boot-starter-web`、`patra-spring-boot-starter-core`、具体 MQ/RPC SDK。
* ⛔ 禁止依赖：`domain`、`infra`、持久化/缓存/搜索库。
* **外部 SDK 使用原则**：

    * 协议型 SDK（Spring Web、MQ 客户端）可直接在 adapter 使用。
    * 业务相关的外部 SDK（云服务、第三方 API）需在 adapter 封装，通过端口抽象暴露给 app，而不是让 app 直接依赖。

---

## 3. 目录结构

```
rest/
  controller/           // REST 控制器：请求→命令/查询→调用 app
  dto/                  // REST 专用的请求/响应 DTO（如有必要）

scheduler/
  *JobScheduler.java    // 定时任务入口（如 xxl-job）

mq/
  consumer/             // MQ 消费者：消息→调用 app
  producer/             // MQ 生产者：实现 app 的发布端口，用 MQ SDK 发出 api 事件DTO

config/ (可选)
  *AdapterConfig.java   // 协议所需 Bean 配置（Web/MQ/Scheduler）
```

---

## 4. REST 适配规范

* **路径规范**：前缀 `/api/registry/**`；资源名用复数；命令动作用冒号后缀，如：

  ```
  POST /resources/{id}:sync
  ```
* **职责限制**：

    * Controller 只做参数校验、调用 app、返回响应；
    * 不拼装聚合，不写业务逻辑；
    * DTO 校验使用 `jakarta.validation` 注解。
* **日志与追踪**：统一记录调用日志、Tracing 信息，但**不泄露领域对象结构**。

---

## 5. MQ 与 Scheduler 规范

* **MQ Consumer**：

    * 消费 `api` 模块定义的事件 DTO；
    * 转换为 use case 的 command/query；
    * 调用 app，用例逻辑落在 app+domain。

* **MQ Producer**：

    * 实现 `app.event.publisher.*` 中的发布端口接口；
    * 将应用事件映射为 `api` 模块的事件 DTO；
    * 使用 MQ SDK 发送消息。

* **Scheduler**：

    * 只做定时触发，调用 app 用例服务；
    * 定时逻辑不进入 app/domain。

---

## 6. DTO 与转换

* adapter 可定义仅限协议使用的 DTO（REST/MQ 专用），但禁止直接复用领域对象。
* DTO 与 `app` usecase 输入模型（command/query）保持一一对应；如有差异，通过 assembler 映射。
* 对外事件 DTO 必须引用自 `api` 模块，不得在 adapter 重复定义。

---

## 7. 测试策略

* **集成测试**：验证 REST/MQ/Scheduler 接口是否能正确转发请求并得到预期结果。
* **契约测试**：保证对外协议（HTTP API/MQ 事件）的输入输出与文档一致。
* **不测业务规则**：领域规则测试留在 domain，编排测试留在 app。

---

## 8. 代码风格与工具

* 严禁在 adapter 层出现业务逻辑。
* 参数校验推荐 `jakarta.validation`。
* 可用 `hutool-core` 工具类（已通过 `patra-common` 引入）做轻量辅助。
* 命名应体现协议与职责，例如：

    * `XxxController`、`XxxEventConsumer`、`XxxEventProducer`、`XxxJobScheduler`。

---

## 9. 事件流职责分工

* **domain**：产出领域事件（业务事实）。
* **app**：将领域事件转为应用事件，调用发布端口。
* **adapter**：实现发布端口，转换为 `api` 事件 DTO，用 SDK 发出/消费。
* **api**：定义对外契约（事件 DTO、Topic 常量）。
* **infra**：仅做持久化与 Outbox 托管（与聚合同事务），不直接发/收事件。

---

> `adapter` = **边界翻译层**。它像“同声传译”，把外部世界的语言（HTTP/MQ 协议）翻译为内部世界能理解的命令/查询，再把内部结果翻译回对外契约。
