# Patra 平台统一错误规范与接入指南

> 目标：让 **所有服务** 在**定义、返回、接收、观测**错误时语义一致、强类型友好、工程可治理。本文覆盖：规范、分层、资源放置、运行机制、配置项、最佳实践、CI/监控与迁移指引。
> 适用栈：Java 21 · Spring Boot 3.2.4 · Spring Cloud 2023.0.1 · Spring Cloud Alibaba 2023.0.1.0 · MyBatis-Plus 3.5.12 · Resilience4j

---

## 1. 总览与分层

### 1.1 设计原则

* **统一语义**：错误码格式、含义、字段一致（服务端/客户端/网关一体化）。
* **强类型开发体验**：业务代码只用**可读枚举**，不写字符串码。
* **契约贴源**：谁拥有错误码，码表就放在哪个模块（`*-api`）；平台保留码放 core。
* **六边形**：核心模型与运行期加载解耦；模型在 `patra-common`，加载/工厂在 `patra-spring-boot-starter-core`。

### 1.2 模块职责

* **patra-common**

    * `com.patra.error.core`: `ErrorCode`、`Category`、`PlatformError`、`ErrorDef`、`ErrorSpec`
    * `com.patra.error.codes`: 枚举（强类型常量），如 `COMErrors`、各服务 `XXXErrors`
    * **不**包含资源与解析逻辑
* **patra-spring-boot-starter-core**

    * `com.patra.error.registry`: `ModuleRegistry`、`Codebook`、`CodebookParser/Loader`
    * `com.patra.error.runtime`: `Problems` 工厂（自动补全 title/http/extras）、自动装配
    * `com.patra.error.codec`: Problem JSON ↔ `PlatformError`
    * 资源：平台保留码 `codebook-COM.properties`
* **patra-spring-boot-starter-web**

    * 统一 `@RestControllerAdvice`：异常→RFC7807 Problem JSON（附扩展字段）
* **patra-spring-cloud-starter-feign**

    * Feign `ErrorDecoder`：Problem JSON→`PlatformError`→统一 `RemoteException`
    * Resilience4j：熔断/限流/超时事件→平台码映射；指标打点带 `code` 维度

---

## 2. 错误码规范

### 2.1 编码格式

```
{模块}-{类别}{四位数字}
```

* 模块：2–4 位大写字母（如 `COM/REG/ING/EXPR/...`）
* 类别：`C`(Client) / `B`(Business) / `S`(Server) / `N`(Network) / `U`(Unknown)
* 数字：`0001–9999`

### 2.2 HTTP 与类别建议映射（仅默认）

* `C`→400 系（400/401/403/404/422…）
* `B`→409
* `S`→500
* `N`→502（也可能 429/503/504）
* `U`→500

> 具体 HTTP 可由 codebook 指定，或由服务端按场景覆盖。

---

## 3. 返回契约（Problem JSON）

所有错误以 **RFC7807 Problem** 返回，并扩展以下字段：

| 字段          | 说明                          |
| ----------- | --------------------------- |
| `type`      | 文档 URI（建议 `/errors/{code}`） |
| `title`     | 错误简述（可本地化）                  |
| `status`    | HTTP 状态                     |
| `detail`    | 错误详情（注意敏感信息脱敏/裁剪）           |
| `instance`  | 请求资源定位                      |
| **`code`**  | 平台错误码（`{MOD}-{CAT}{NNNN}`）  |
| `service`   | 出错服务名                       |
| `traceId`   | 链路 ID                       |
| `timestamp` | ISO-8601                    |
| `extras`    | 结构化上下文（键值对）                 |

---

## 4. 资源放置（固定命名）

* 模块登记：`META-INF/patra/module-registry.properties`
  例：`module.REG.owner=registry-team`
* 码表：`META-INF/patra/codebook-<MODULE>.properties`

    * **平台保留（COM）**：放在 **core**
    * **业务模块（REG/ING/EXPR…）**：放在各自 **`*-api`**

### 4.1 COM 码表示例（放 core）

```properties
# NETWORK (N)
COM-N0001.title=Connect timeout
COM-N0001.http=504
COM-N0002.title=Read timeout
COM-N0002.http=504
COM-N0401.title=Circuit breaker open
COM-N0401.http=503
COM-N0601.title=Rate limited
COM-N0601.http=429

# SERVER (S)
COM-S0001.title=Unexpected server error
COM-S0001.http=500
COM-S0301.title=Database access error
COM-S0301.http=500

# CLIENT (C)
COM-C0101.title=Missing or invalid parameter
COM-C0101.http=400
COM-C0201.title=Validation failed
COM-C0201.http=422
COM-C2001.title=Unauthorized
COM-C2001.http=401
COM-C2101.title=Forbidden
COM-C2101.http=403
COM-C0301.title=Resource not found
COM-C0301.http=404

# BUSINESS (B)
COM-B0101.title=Version conflict
COM-B0101.http=409

# UNKNOWN (U)
COM-U0001.title=Unclassified error
COM-U0001.http=500
```

---

## 5. 强类型开发方式（只写枚举，不写字符串）

* 在 **`patra-common`** 定义平台枚举：`com.patra.error.codes.COMErrors`
* 在各服务 **`*-api`** 定义本服务枚举：`…api.error.REGErrors`
* 枚举实现 `ErrorDef`，**仅绑定 code**；`title/http` 来自 codebook。

**用法：**

```java
import static com.patra.error.codes.COMErrors.*;
import static com.patra.registry.api.error.REGErrors.*;

var e1 = Problems.of(CONNECT_TIMEOUT).build();          // COM
var e2 = Problems.of(MISSING_PROVENANCE_ID)             // REG
        .param("param","provenanceId")
        .build();
```

> `Problems` 在 **core** 中，通过自动装配注入 codebook，自动补全 `title/http/extras`；未命中则按类别默认 HTTP。

---

## 6. 运行机制（端到端）

1. **启动期（core 自动装配）**

* 合并加载：`codebook-*.properties`（可选 `*.json`）
* 冲突检测（同一 code 不同 title/http 可告警/失败）
* 非法码/未知模块前缀校验（可配置）
* 注入 `Problems.setCodebookProvider(...)`

2. **服务端（web starter）**

* 全局 `@RestControllerAdvice` 捕获异常 → 组装 `PlatformError` → 输出 Problem JSON
* 常见映射：

    * Bean Validation → `COM-C0201` + 422
    * 未捕获异常 → `COM-S0001` + 500

3. **客户端（feign starter）**

* `ErrorDecoder`：Problem JSON → `PlatformError` → 抛统一 `RemoteException`
* Resilience4j 事件映射：

    * 熔断打开 → `COM-N0401`
    * 限流拒绝 → `COM-N0601`
    * 连接/读超时 → `COM-N0001/0002`
* 指标：`client|uri|status|code|exception` 维度

---

## 7. 配置项（core / properties 建议）

```yaml
patra:
  error:
    enabled: true            # 默认开启
    log-summary: true        # 启动总结日志（加载了多少文件/条目）
    fail-fast: false         # 资源缺失/读取失败时是否直接失败
    enable-json: false       # 是否同时加载 *.json 码表
    detect-conflict: true    # 合并时检测同 code 冲突
    fail-on-conflict: false  # 冲突是否直接失败
    validate-module-prefix: true  # 码表中的模块前缀是否必须已登记
```

---

## 8. 网关与上游错误对齐

* 网关（`patra-gateway-boot`）将 502/503/504、限流、鉴权等错误统一映射为 **COM** 码，并输出 Problem JSON。
* 这样调用方**无论直接/间接**调用某服务，都能拿到一致的 `code` 语义。

---

## 9. 观测与告警

* **指标维度**：`code`、`service`、`client`、`uri`、`status`
* **看板**：Top 错误码、按 `code` 的错误率/延迟、`COM-N0401`/`COM-N0601` 事件趋势
* **告警规则**：

    * 单一 `code` 在 5 分钟窗口内突增
    * 网络类（`N`）错误连续出现
    * `COM-S0001`（未捕获）连续触发

---

## 10. CI 治理

* **禁止**源代码出现 `^[A-Z]{2,4}-[CBSNU][0-9]{4}$` 字面量（统一用枚举）。
* 校验：枚举中的 `ErrorCode` 必须存在本模块 `codebook-<MOD>.properties`。
* 码表 lint：非法码形态、未知模块前缀、重复 code 冲突检测。
* PR 审阅规则：新增/变更错误码须补充到 `codebook` 与文档。

---

## 11. 迁移指引（从“各写各的”到统一规范）

1. 新增 `patra-spring-boot-starter-core` 与 `patra-spring-boot-starter-web/feign` 到服务依赖。
2. 将历史错误响应统一切换为 Problem JSON（`starter-web` 接管）。
3. 为每个服务补 `codebook-<MOD>.properties` 与 `XXXErrors` 枚举（只写 code）。
4. 替换代码中的字符串码 → 强类型枚举；删除私有异常响应器。
5. 打开 CI 校验与看板告警。

---

## 12. FAQ

* **Q：为什么 COM 的 codebook 放 core，而 COM 的枚举放 common？**
  A：common 只放稳定“语言层”常量；core 负责运行期加载/合并。这样 common 不被资源/解析污染，core 自动装配统一生效。

* **Q：我只写枚举，不写 title/http 行不行？**
  A：完全可行。title/http 放在 codebook，`Problems` 自动补全；未命中走类别默认值。

* **Q：出现未预期的 code/title？**
  A：开启 `detect-conflict`，并将 `fail-on-conflict` 设为 true，在合并期就拦截冲突；同时检查是否有重复的 codebook 资源在类路径上。

* **Q：`U` 类怎么用？**
  A：`COM-U0001` 为临时未归类；上线前尽量收敛到 C/B/S/N 的具体码。

---

## 13. 一页小抄

* **格式**：`{MOD}-{CAT}{NNNN}`
* **放置**：COM 码表在 core；业务码表在各自 `*-api`
* **开发**：只用 `XXXErrors` 枚举 + `Problems.of(...)`
* **服务端**：`starter-web` 输出 Problem JSON
* **客户端**：`starter-feign` 解码 + Resilience4j 映射
* **治理**：CI 校验 + 冲突检测 + 监控告警
