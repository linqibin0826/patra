# Feign API 开发检查清单

> 本检查清单帮助开发者按照 [Feign API 设计规范](../standards/feign-api-design-guide.md) 正确实现微服务间 RPC 调用。

## 使用说明

- ✅ 已完成：符合规范
- ⚠️ 需检查：可能存在问题
- ❌ 不符合：必须修改
- 🔄 可选：根据具体情况决定

---

## 1. API 模块设计检查（patra-{service}-api）

### 1.1 Endpoint 接口定义

- [ ] **接口命名**：使用 `{Service}Endpoint` 命名约定（如 `ProvenanceEndpoint`）
- [ ] **BASE_PATH 常量**：定义 `String BASE_PATH` 常量统一管理路径前缀
- [ ] **路径规范**：
  - [ ] 内部 API 使用 `/_internal` 前缀
  - [ ] 外部 API 使用 `/api/v{version}` 前缀
  - [ ] 资源名使用复数形式（如 `/provenances` 而非 `/provenance`）
  - [ ] 避免路径中包含动作词（如 `/getProvenance`）
- [ ] **方法注解**：使用 `@GetMapping`、`@PostMapping` 等 Spring Web 注解
- [ ] **参数注解**：正确使用 `@PathVariable`、`@RequestParam`、`@RequestBody`、`@Valid`
- [ ] **JavaDoc**：为每个方法添加清晰的注释说明
- [ ] **无业务逻辑**：接口中不包含默认方法或业务实现

### 1.2 Client 接口定义

- [ ] **接口命名**：使用 `{Service}Client` 命名约定（如 `ProvenanceClient`）
- [ ] **继承 Endpoint**：`extends {Service}Endpoint`
- [ ] **@FeignClient 注解**：
  - [ ] `name` 参数：指定服务名（Nacos 注册名）
  - [ ] `contextId` 参数：指定上下文 ID（避免多 Client 冲突）
  - [ ] 🔄 `path` 参数：可选，统一路径前缀
  - [ ] 🔄 `fallbackFactory` 参数：可选，降级工厂类
- [ ] **不添加额外方法**：保持与 Endpoint 完全一致

### 1.3 DTO 设计

- [ ] **Request DTO**：
  - [ ] 使用 `record` 定义不可变对象（或 `@Data class`）
  - [ ] 添加 Jakarta Validation 注解（`@NotNull`、`@NotBlank`、`@Valid` 等）
  - [ ] 添加 `@Schema` 注解增强 OpenAPI 文档
  - [ ] 字段命名清晰，避免缩写
- [ ] **Response DTO**：
  - [ ] 使用 `record` 或 `@Data class`
  - [ ] 添加 `@Schema` 注解
  - [ ] 包含必要的字段说明注释

---

## 2. Adapter 模块实现检查（patra-{service}-adapter）

### 2.1 Controller 实现

- [ ] **类命名**：使用 `{Service}ClientImpl` 命名约定
- [ ] **实现 Client 接口**：`implements {Service}Client`（**强制要求**）
- [ ] **@RestController 注解**：标记为 REST 控制器
- [ ] **依赖注入**：
  - [ ] 使用 `@RequiredArgsConstructor` 或构造器注入
  - [ ] 注入 App 层服务（`{Service}AppService`）
  - [ ] 注入 Converter（`{Service}ApiConverter`）
- [ ] **方法实现**：
  - [ ] 使用 `@Override` 注解
  - [ ] 调用 App 层服务编排业务逻辑
  - [ ] 使用 Converter 转换 DTO 和 Domain 模型
  - [ ] 不包含业务逻辑（仅作为适配层）

### 2.2 日志记录

- [ ] **类注解**：添加 `@Slf4j` 注解
- [ ] **日志前缀**：使用 `[{MODULE}][ADAPTER]` 格式（如 `[REGISTRY][ADAPTER]`）
- [ ] **日志级别**：
  - [ ] 入站请求使用 `INFO` 或 `DEBUG`（根据频率）
  - [ ] 关键操作使用 `INFO`
  - [ ] 异常使用 `ERROR` 或 `WARN`
- [ ] **日志内容**：
  - [ ] 包含关键参数（如 `code={}`、`id={}`）
  - [ ] 不包含敏感信息
  - [ ] 不包含大对象（`toString()` 超过 2KB）

### 2.3 Converter 设计

- [ ] **接口命名**：使用 `{Service}ApiConverter`
- [ ] **MapStruct 注解**：`@Mapper(componentModel = "spring")`
- [ ] **转换方法**：
  - [ ] DTO → Domain Model：`toModel()` 或 `toDomain()`
  - [ ] Domain Model → DTO：`toResp()` 或 `toDTO()`
  - [ ] 方法命名清晰，避免歧义

---

## 3. Infra 模块出站检查（patra-{caller}-infra）

### 3.1 Domain Port 接口定义

- [ ] **接口位置**：定义在 `patra-{caller}-domain` 模块
- [ ] **接口命名**：使用 `Patra{Service}Port` 命名约定（如 `PatraRegistryPort`）
- [ ] **方法签名**：
  - [ ] 使用领域术语和类型（不使用外部 DTO）
  - [ ] 参数和返回值都是 Domain 模型
  - [ ] 添加清晰的 JavaDoc 注释

### 3.2 RPC Adapter 实现

- [ ] **类命名**：使用 `{Service}PortRpcAdapter` 命名约定
- [ ] **实现 Port 接口**：`implements Patra{Service}Port`
- [ ] **@Component 注解**：标记为 Spring 组件
- [ ] **依赖注入**：
  - [ ] 注入 Feign Client（`{Service}Client`）
  - [ ] 注入 Domain Converter（`{Service}DomainConverter`）
- [ ] **方法实现**：
  - [ ] 调用 Feign Client 发起 RPC 请求
  - [ ] 使用 Converter 转换响应为 Domain 模型
  - [ ] 正确处理异常（捕获 `FeignException`）

### 3.3 异常处理

- [ ] **捕获 FeignException**：不让框架异常泄漏到 Domain 层
- [ ] **转换为领域异常**：如 `RegistryCallException`、`EgressCallException`
- [ ] **日志记录**：
  - [ ] 使用 `ERROR` 级别记录调用失败
  - [ ] 包含关键参数和错误信息
  - [ ] 包含异常堆栈（`log.error("msg", e)`）
- [ ] **错误传播**：
  - [ ] 使用领域异常向上传播
  - [ ] 包含足够的上下文信息
  - [ ] 不丢失原始异常（使用 `cause`）

### 3.4 Domain Converter 设计

- [ ] **接口命名**：使用 `{Service}DomainConverter`
- [ ] **MapStruct 注解**：`@Mapper(componentModel = "spring")`
- [ ] **转换方法**：
  - [ ] 外部 DTO → Domain Model：`toDomain()` 或 `toDomainSnapshot()`
  - [ ] Domain Model → 外部 DTO：`toExternalDTO()` 或 `toEgressReq()`
  - [ ] 方法命名清晰，体现转换方向

---

## 4. 测试覆盖检查

### 4.1 单元测试（API 模块）

- [ ] **Endpoint 测试**：
  - [ ] 验证方法注解（`@GetMapping`、`@PostMapping` 等）
  - [ ] 验证路径映射正确性
  - [ ] 验证参数注解（`@PathVariable`、`@RequestParam` 等）

### 4.2 集成测试（Adapter 模块）

- [ ] **Controller 测试**：
  - [ ] 使用 `@WebMvcTest` 或 `@SpringBootTest`
  - [ ] Mock App 层服务
  - [ ] 验证 HTTP 请求和响应
  - [ ] 验证异常处理逻辑
  - [ ] 验证参数校验（`@Valid`）

### 4.3 契约测试（Feign Client）

- [ ] **Client 测试**：
  - [ ] 使用 WireMock 或 Spring Cloud Contract
  - [ ] 验证 Feign 调用正确性
  - [ ] 验证请求路径和参数
  - [ ] 验证响应解析逻辑

### 4.4 端到端测试（Infra 模块）

- [ ] **RPC Adapter 测试**：
  - [ ] Mock Feign Client
  - [ ] 验证 Port 接口实现
  - [ ] 验证 Domain 模型转换
  - [ ] 验证异常处理和降级逻辑

---

## 5. Code Review 要点

### 5.1 契约一致性

- [ ] **Controller 实现 Client**：确保 `implements {Service}Client`
- [ ] **方法签名一致**：Controller 方法与 Endpoint 完全匹配
- [ ] **注解一致**：路径、参数、返回值注解正确
- [ ] **编译通过**：无编译错误或警告

### 5.2 依赖方向

- [ ] **Adapter → App + API**：Adapter 不直接依赖 Domain 或 Infra
- [ ] **Infra → Domain**：Infra 实现 Domain Port，不依赖 Adapter
- [ ] **API 无框架依赖**：API 模块仅依赖必要的 Spring Web 注解
- [ ] **Domain 零框架依赖**：Domain 仅依赖 `patra-common`

### 5.3 代码质量

- [ ] **命名规范**：遵循统一的命名约定
- [ ] **路径管理**：使用 `BASE_PATH` 常量
- [ ] **日志规范**：使用 `[模块][层]` 前缀
- [ ] **异常处理**：正确捕获和转换异常
- [ ] **无敏感信息**：日志和错误信息不包含密码、Token 等

### 5.4 文档完整性

- [ ] **JavaDoc 注释**：关键类和方法有清晰注释
- [ ] **OpenAPI 注解**：DTO 有 `@Schema` 注解
- [ ] **README 更新**：模块 README 包含 API 说明
- [ ] **ADR 引用**：复杂决策有对应的 ADR 文档

### 5.5 配置和部署

- [ ] **Feign 配置**：`application.yml` 中配置超时和重试
- [ ] **服务注册**：服务名与 `@FeignClient(name)` 一致
- [ ] **降级配置**：🔄 可选，配置 `fallbackFactory`
- [ ] **监控埋点**：🔄 可选，添加 APM 追踪

---



## 7. 常见问题检查

### 7.1 编译错误

- [ ] **接口未实现**：Controller 是否 `implements {Service}Client`？
- [ ] **方法签名不匹配**：参数类型、注解是否与 Endpoint 一致？
- [ ] **依赖缺失**：是否正确引入 `patra-{service}-api` 依赖？

### 7.2 运行时错误

- [ ] **404 Not Found**：路径是否正确？`BASE_PATH` 是否一致？
- [ ] **Feign 调用失败**：服务名是否正确？是否已在 Nacos 注册？
- [ ] **参数绑定失败**：`@PathVariable`、`@RequestParam` 名称是否匹配？
- [ ] **DTO 序列化失败**：DTO 是否有无参构造器（`record` 自动提供）？

### 7.3 性能问题

- [ ] **超时配置**：是否配置合理的连接和读取超时？
- [ ] **重试策略**：是否配置了重试次数和退避策略？
- [ ] **连接池**：是否使用了连接池（默认启用）？

---

## 8. 自检问题清单

在提交 PR 前，请自问以下问题：

1. ✅ 我的 Controller 是否实现了 Client 接口？
2. ✅ 所有方法是否使用了 `@Override` 注解？
3. ✅ Endpoint 接口是否定义了 `BASE_PATH` 常量？
4. ✅ `@FeignClient` 是否包含 `contextId` 参数？
5. ✅ 日志是否使用了 `[模块][层]` 前缀？
6. ✅ 异常处理是否正确（FeignException → Domain Exception）？
7. ✅ 测试是否覆盖了所有关键路径？
8. ✅ 文档是否更新（README、ADR、API 文档）？
9. ✅ 是否有敏感信息泄漏（日志、异常信息）？
10. ✅ 依赖方向是否正确（Adapter → App → Domain）？

---

## 9. 快速参考

### 模块依赖方向

```
adapter → app + api
app → domain + patra-common
infra → domain
domain → patra-common
api → (无框架依赖)
```

### 命名规范速查

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| Endpoint 接口 | `{Service}Endpoint` | `ProvenanceEndpoint` |
| Client 接口 | `{Service}Client` | `ProvenanceClient` |
| Controller 实现 | `{Service}ClientImpl` | `ProvenanceClientImpl` |
| RPC Adapter | `{Service}PortRpcAdapter` | `ProvenancePortRpcAdapter` |
| Domain Port | `Patra{Service}Port` | `PatraRegistryPort` |
| API Converter | `{Service}ApiConverter` | `ProvenanceApiConverter` |
| Domain Converter | `{Service}DomainConverter` | `ProvenanceDomainConverter` |

### 关键注解速查

```java
// Endpoint 接口（API 模块）
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";
    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}

// Client 接口（API 模块）
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {}

// Controller 实现（Adapter 模块）
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProvenanceClientImpl implements ProvenanceClient {
    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) { /*...*/ }
}

// RPC Adapter（Infra 模块）
@Component
@RequiredArgsConstructor
@Slf4j
public class ProvenancePortRpcAdapter implements PatraRegistryPort {
    private final ProvenanceClient provenanceClient;
}
```

---

## 10. 相关资源

- [Feign API 设计规范](../standards/feign-api-design-guide.md)
- [ADR-001: 统一 Feign API 设计模式](../architecture/ADR-001-feign-api-unified-design.md)
- [日志规范](../standards/logging-convention.md)
- [错误处理规范](../standards/platform-error-handling.md)
- [Spring Cloud OpenFeign 文档](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)

---

**版本记录**：
- v1.0.0（2025-10-06）：初始版本
