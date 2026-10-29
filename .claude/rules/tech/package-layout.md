---
paths: patra-api/**/*.java
---

# 分包规范

Gradle 模块按层，**模块内先按子域、再按组件类型**。由 `patra-tool-test` 的 `SubdomainArchitectureRules` 在 `check` 里强制。设计与推导见 `docs/patra/specs/2026-10-29-backend-package-layout-design.md`。ingest 尚未迁移，暂不适用。

## 模板

`{根}` = 服务根包，`{子域}` = 服务声明的子域名。

```
{根}.domain.{子域}
├─ model.{aggregate | entity | vo(.主题子包) | enums | read}
├─ policy | service | event | exception
└─ port.{repository | read | gateway | <协作者>}

{根}.app.{子域}
├─ usecase.<用例族>     *Command + *Handler + *Result 同包
├─ usecase.query        *QueryService + *View / *Page
├─ service              *GatewayImpl、本子域多个用例复用的类
├─ listener             *Listener
└─ config

{根}.infra.{子域}
├─ persistence(.entity | .dao | .mapper | .converter)   *RepositoryAdapter、*ReadAdapter、*Row、查找适配器
├─ <协作者>             与 domain.{子域}.port.<协作者> 同名
├─ support              本子域 infra 内复用的技术辅助类
└─ config

{根}.adapter.{子域}
├─ rest(.request | .response | .converter)
├─ internal             *EndpointImpl 与它用的 *ApiConverter
├─ scheduler | mq | config

{根}.api.{子域}.{endpoint | dto}
{根}.api.error          服务级错误码（保留包）
{根}、{根}.config       启动类与组装，不属于任何子域
```

- 层下第二级只能是已声明子域（`api.error` 除外）；子域根包不放类；白名单外的包名一律违规。
- infra 下不用 `read`、`repository`、`gateway` 作包名（读端口与仓储的实现放 `persistence`，Gateway 实现在 `app.{子域}.service`）；层外的类只放 `{根}` 与 `{根}.config`。
- `usecase.<用例族>`、`port.<协作者>` 用业务词命名。
- 新增子域要改该服务 `*ArchitectureTest` 的子域清单；每个声明的子域在 domain 层必须有类。

## 子域之间

- **底座**（每服务一个）对同服务其他子域完全公开，依赖它不用声明；底座不依赖任何其他子域；**只放被两个以上子域共用的东西**。
- 普通子域只公开 `domain.{子域}.{port.read, model.read, event}`；app / infra / adapter / api 没有公开包。
- 跨子域依赖要在架构测试里声明：依赖边 `allowingDependency`（全部公开包）或订阅边 `allowingSubscription`（只经 `event`）。子域间无环。
- 跨子域写不调对方仓储：发本子域事件，对方用同步 `@EventListener` 在同一事务里处理。
- 读适配器可以用 SQL 读本服务库里的任何表，但不 import 对方的 entity、dao、Row。

## 用词

| 东西 | 包 | 类名 |
|---|---|---|
| JPA 实体 | `persistence.entity` | `*Entity`（带 `@Entity`） |
| JPA 映射器 | `persistence.mapper` | `*JpaMapper` |
| 接口层转换器 | `rest.converter`；内部 RPC 用的放 `internal` | `*ApiConverter` |
| 事件监听 | `listener` | `*Listener` |
| 定时任务 | `scheduler` | `*ScheduleJob` |
| 内部 RPC 实现 | `internal` | `*EndpointImpl` |
