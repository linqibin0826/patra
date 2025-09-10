# Patra 平台错误码与异常处理规范

## 1. 设计目标

* **统一**：所有微服务使用同一套错误码体系与返回格式（Problem JSON）。
* **解耦**：错误码语义只在 `*-api` 和资源文件定义，不依赖 Spring。
* **自动化**：运行时由 `starter-core` 自动加载错误码表，`Problems` 工厂补全 `title/http`。
* **易用**：开发者写代码时只关心**可读枚举名**，不用再写 `"REG-C0101"` 这种字符串。

---

## 2. 模块职责

### patra-common-error

平台错误协议内核（纯 Java）：

* `ErrorCode`、`Category`、`PlatformError`
* `ErrorDef` 接口
* `Problems` 工厂（统一构造错误体）
* `Codebook/Loader`、`ModuleRegistry`

### patra-spring-boot-starter-core

运行时自动装配：

* 启动时加载 `module-registry.properties` 和 `codebook-*.properties`
* 注入 `ModuleRegistry`、`Codebook` 为 Spring Bean
* 初始化 `Problems.setCodebookProvider(...)`

### 各服务 `*-api`

只依赖 `patra-common-error`：

* 定义错误枚举 `XXXErrors implements ErrorDef`，**只写 code**
* 其它信息（标题、HTTP 状态、文档链接）统一放在 `META-INF/patra/codebook-<MOD>.properties`

### 各服务 `*-adapter` / `*-boot`

* 依赖 `starter-core`，获得已注入的 Codebook/Problems Provider
* 使用 `Problems.of(ErrorDef)` 构造 `PlatformError`，或抛异常交由 starter-web 处理

---

## 3. 错误码规范

* 格式：`{MODULE}-{CATEGORY}{NNNN}`

    * MODULE：2–4 位大写字母（如 REG/VAU/COM）
    * CATEGORY：单字符（C 客户端 / B 业务 / S 服务端 / N 网络 / U 未知）
    * NNNN：4 位数字

例：`REG-C0101` = Registry 模块，客户端错误，编号 0101。

---

## 4. 使用步骤

### 4.1 在 API 模块定义错误枚举

```java
// patra-registry-api
package com.patra.registry.api.error;

import com.patra.error.core.*;

public enum REGErrors implements ErrorDef {
    MISSING_PROVENANCE_ID("REG-C0101"),
    REGISTRY_NOT_FOUND("REG-C0301"),
    ENTITY_VERSION_CONFLICT("REG-B0101");

    private final ErrorCode code;
    REGErrors(String literal) { this.code = ErrorCode.of(literal); }
    @Override public ErrorCode code() { return code; }
}
```

### 4.2 在资源文件维护码表

`src/main/resources/META-INF/patra/codebook-REG.properties`

```properties
REG-C0101.title=Missing parameter: provenanceId
REG-C0101.http=422
REG-C0301.title=Registry entry not found
REG-C0301.http=404
REG-B0101.title=Entity version conflict
REG-B0101.http=409
```

### 4.3 在业务代码里使用

```java
import static com.patra.registry.api.error.REGErrors.*;

var error = Problems.of(MISSING_PROVENANCE_ID)
        .param("param", "provenanceId")
        .build();

// 交给 starter-web 输出 Problem JSON，或上抛 RemoteException 由 starter-feign 解析
```

---

## 5. Starter 行为

* **starter-core**：应用启动时自动加载码表 → 注册 `Codebook` / `ModuleRegistry` → 初始化 `Problems`。
* **starter-web**：拦截异常，统一输出 `Problem JSON`（字段包含 code、title、status、traceId、service、timestamp 等）。
* **starter-feign**：解析下游返回的 `Problem JSON` → 转换为 `PlatformError` → 抛 `RemoteException`，并打指标。

---

## 6. 团队规范

1. **禁止在代码里写字符串错误码**：一律使用 `XXXErrors` 枚举。
2. **码表是唯一真相来源**：所有 `title/http/doc` 信息维护在 `codebook-<MOD>.properties`。
3. **CI 校验**：每个枚举中的 code 必须存在于该模块的 codebook；禁止 `*-U0000` 出现在生产构建。
4. **日志与监控**：Grafana/日志平台以 `code` 为主维度，做错误统计与告警。

---

## 7. 快速 FAQ

* **Q：我在 adapter 层抛异常，如何带错误码？**
  A：直接 `throw new DomainException(REGErrors.ENTITY_VERSION_CONFLICT);`，starter-web 会转换。

* **Q：我需要自定义返回错误？**
  A：用 `Problems.of(枚举).param(...).detail(...).build()` 构造 `PlatformError`，返回给前端即可。

* **Q：新增错误码流程？**

    1. 在 `codebook-<MOD>.properties` 增加一条
    2. 在 `XXXErrors` 枚举里加一个常量
    3. 提交代码，CI 校验通过

---

## 8. 总结

* **common-error** 提供协议与工具，纯 Java；
* **starter-core** 负责加载与初始化，Spring Boot 环境下统一启用；
* **api 模块只依赖 common-error**，定义强类型枚举；
* **业务代码只写枚举名**，其它信息由 codebook 自动补全。

这样一来，错误码**可读、统一、可治理**，再也不用记 `"REG-C0101"` 是啥了。
