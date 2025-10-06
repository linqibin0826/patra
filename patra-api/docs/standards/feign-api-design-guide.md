# Feign API 设计规范

> 状态：已批准（Approved）— 2025-10-06 架构评审通过，适用于 Papertrace 全平台所有微服务间 RPC 调用。

## 1. 概述

### 1.1 为什么需要统一 Feign API 设计？

在微服务架构中，服务间通信的可靠性和一致性至关重要。未经统一规范的 Feign API 设计会导致：

- **契约不一致**：Provider 端实现与 Consumer 端声明不同步，运行时才发现问题
- **测试困难**：Feign 调用测试与实际 REST 行为不一致，Mock 测试覆盖不全
- **维护成本高**：重构时需要手动同步多处代码，容易遗漏
- **协作障碍**：团队间 API 边界模糊，接口变更影响范围难以评估

### 1.2 设计目标

本规范旨在实现：

✅ **编译时契约保证**：Controller 必须实现 Client 接口，重构时立即发现不一致
✅ **测试行为一致**：Feign 调用与 REST 调用完全相同的行为
✅ **清晰的 API 边界**：Endpoint 作为服务间通信的明确契约
✅ **易于维护演化**：修改 Endpoint 会强制更新所有实现和调用方
✅ **团队协作友好**：API 设计与实现解耦，支持并行开发

## 2. 设计模式：Endpoint + Client 分离

### 2.1 核心架构

```
┌─────────────────────────────────────────────────┐
│              patra-{service}-api                │
│  ┌─────────────────────────────────────────┐   │
│  │  {Service}Endpoint (纯契约接口)          │   │
│  │  - 定义 API 路径、参数、返回值            │   │
│  │  - 无任何框架注解（可选 Spring Web）      │   │
│  └─────────────────────────────────────────┘   │
│                      ▲                          │
│                      │ extends                 │
│  ┌─────────────────────────────────────────┐   │
│  │  {Service}Client (@FeignClient)          │   │
│  │  - 继承 Endpoint                          │   │
│  │  - 添加 @FeignClient 注解                 │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
                      ▲
                      │ implements
┌─────────────────────────────────────────────────┐
│          patra-{service}-adapter                │
│  ┌─────────────────────────────────────────┐   │
│  │  {Service}ClientImpl (@RestController)   │   │
│  │  - 实现 Client 接口（强制契约一致）       │   │
│  │  - 调用 App 层服务编排业务逻辑            │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
                      ▲
                      │ 注入
┌─────────────────────────────────────────────────┐
│          patra-{caller}-infra                   │
│  ┌─────────────────────────────────────────┐   │
│  │  {Service}PortRpcAdapter (出站适配器)    │   │
│  │  - 实现 Domain Port 接口                  │   │
│  │  - 注入 {Service}Client 发起 RPC 调用     │   │
│  │  - 转换响应为 Domain 模型                 │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### 2.2 设计优势

| 优势 | 说明 |
|------|------|
| **编译时校验** | Controller 实现 Client 接口，接口变更时编译器强制更新实现 |
| **测试一致性** | Feign 调用与 REST 调用共享同一接口，测试覆盖更可靠 |
| **清晰边界** | Endpoint 作为纯契约，不包含实现细节，易于理解和维护 |
| **重构友好** | 修改 Endpoint 会触发编译错误，确保所有依赖方同步更新 |
| **团队协作** | API 设计（Endpoint）与实现（Controller）解耦，支持并行开发 |

## 3. 模块职责划分

### 3.1 API 模块（patra-{service}-api）

**职责**：定义服务对外暴露的契约，包括 Endpoint 接口和 Client 接口。

**Endpoint 接口**：
- 纯 Java 接口，定义 API 路径、参数、返回值
- 可使用 Spring Web 注解（`@GetMapping`、`@PostMapping` 等）
- 不包含业务逻辑，仅作为契约声明
- 定义 `BASE_PATH` 常量统一管理路径前缀

**Client 接口**：
- 继承对应的 Endpoint 接口
- 添加 `@FeignClient` 注解声明服务名和上下文 ID
- 不添加额外方法，保持与 Endpoint 完全一致

**示例**：

```java
// 1. Endpoint 接口（纯契约）
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";

    /**
     * Get provenance configuration by code
     *
     * @param code provenance code
     * @return provenance configuration
     */
    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

    /**
     * Get full provenance configuration including all details
     *
     * @param code provenance code
     * @return full configuration with sources and tasks
     */
    @GetMapping(BASE_PATH + "/{code}/full")
    ProvenanceConfigResp getConfiguration(@PathVariable("code") ProvenanceCode code);
}

// 2. Client 接口（继承 Endpoint + @FeignClient）
@FeignClient(
    name = "patra-registry",           // 服务名（Nacos 注册名）
    contextId = "provenanceClient",    // 上下文 ID（避免多 Client 冲突）
    path = ProvenanceEndpoint.BASE_PATH // 可选：统一路径前缀
)
public interface ProvenanceClient extends ProvenanceEndpoint {
    // 不添加额外方法，保持与 Endpoint 完全一致
}
```

### 3.2 Adapter 模块（patra-{service}-adapter）

**职责**：实现 Client 接口，提供 REST 入站能力，作为服务的 HTTP 入口。

**Controller 实现**：
- 使用 `@RestController` 注解
- **必须** `implements` 对应的 Client 接口（强制契约一致性）
- 注入 App 层服务，编排业务逻辑
- 使用 Converter 转换 DTO 与领域模型

**示例**：

```java
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProvenanceClientImpl implements ProvenanceClient {

    private final ProvenanceConfigAppService appService;
    private final ProvenanceApiConverter converter;

    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) {
        log.debug("[REGISTRY][ADAPTER] Get provenance: code={}", code);

        ProvenanceConfig config = appService.findProvenance(code);
        return converter.toResp(config);
    }

    @Override
    public ProvenanceConfigResp getConfiguration(ProvenanceCode code) {
        log.info("[REGISTRY][ADAPTER] Get full configuration: code={}", code);

        ProvenanceConfigSnapshot snapshot = appService.getFullConfiguration(code);
        return converter.toConfigResp(snapshot);
    }
}
```

### 3.3 Infra 模块（patra-{caller}-infra）

**职责**：通过 Port-Adapter 模式实现出站调用，将 Feign 客户端桥接到领域层。

**Port 接口（Domain 层定义）**：

```java
// 位于 patra-{caller}-domain
public interface PatraRegistryPort {
    /**
     * Fetch provenance configuration from registry service
     *
     * @param code provenance code
     * @return configuration snapshot
     */
    ProvenanceConfigSnapshot fetchConfiguration(ProvenanceCode code);
}
```

**RPC Adapter 实现（Infra 层）**：

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ProvenancePortRpcAdapter implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient; // 注入 Feign Client
    private final ProvenanceDomainConverter converter;

    @Override
    public ProvenanceConfigSnapshot fetchConfiguration(ProvenanceCode code) {
        log.debug("[INGEST][INFRA] Fetch configuration from registry: code={}", code);

        try {
            ProvenanceConfigResp resp = provenanceClient.getConfiguration(code);
            return converter.toDomainSnapshot(resp);

        } catch (FeignException e) {
            log.error("[INGEST][INFRA] Failed to fetch configuration: code={}", code, e);
            throw new RegistryCallException("Failed to fetch provenance config", e);
        }
    }
}
```

## 4. 编码规范

### 4.1 命名约定

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| **Endpoint 接口** | `{Service}Endpoint` | `ProvenanceEndpoint`、`ExpressionEndpoint` |
| **Client 接口** | `{Service}Client` | `ProvenanceClient`、`ExpressionClient` |
| **Controller 实现** | `{Service}ClientImpl` | `ProvenanceClientImpl`、`ExpressionClientImpl` |
| **RPC Adapter** | `{Service}PortRpcAdapter` | `ProvenancePortRpcAdapter` |
| **Domain Port** | `Patra{Service}Port` | `PatraRegistryPort`、`PatraExprPort` |

### 4.2 路径管理

**统一使用 BASE_PATH 常量**：

```java
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances"; // 内部 API 使用 /_internal 前缀

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}
```

**路径设计原则**：
- 内部服务间调用使用 `/_internal` 前缀
- 外部 API 使用 `/api/v1` 前缀
- RESTful 风格：资源名复数，HTTP 方法语义化
- 避免路径中包含动作词（如 `/getProvenance`），使用 HTTP 方法表达动作

### 4.3 @FeignClient 注解配置

```java
@FeignClient(
    name = "patra-registry",              // 必填：服务名（Nacos 注册名）
    contextId = "provenanceClient",       // 必填：上下文 ID（多 Client 时避免冲突）
    path = ProvenanceEndpoint.BASE_PATH,  // 可选：统一路径前缀（推荐使用）
    fallbackFactory = ProvenanceClientFallbackFactory.class // 可选：降级工厂
)
public interface ProvenanceClient extends ProvenanceEndpoint {
}
```

**关键参数说明**：
- `name`：目标服务在 Nacos 中的注册名称
- `contextId`：Spring 容器中的 Bean 名称，避免多个 Client 指向同一服务时冲突
- `path`：可选，统一在 Client 层声明路径前缀，Endpoint 中可省略
- `fallbackFactory`：可选，提供降级逻辑（推荐使用 Factory 而非 Fallback）

### 4.4 DTO 设计规范

**Request DTO**：
- 使用 `record` 定义不可变对象
- 添加 Jakarta Validation 注解（`@NotNull`、`@Valid` 等）
- 使用 Swagger 注解增强文档（`@Schema`）

```java
@Schema(description = "Create ingest plan command")
public record CreatePlanCommand(
    @Schema(description = "Plan name", example = "PubMed Daily Ingest")
    @NotBlank(message = "Plan name is required")
    String name,

    @Schema(description = "Provenance code")
    @NotNull(message = "Provenance code is required")
    ProvenanceCode provenanceCode,

    @Schema(description = "Cron expression for scheduling")
    @NotBlank(message = "Cron expression is required")
    String cronExpression
) {}
```

**Response DTO**：
- 使用 `record` 或 `@Data class`
- 字段命名清晰，避免缩写
- 添加字段说明注释

```java
@Schema(description = "Provenance configuration response")
public record ProvenanceConfigResp(
    @Schema(description = "Provenance code")
    ProvenanceCode code,

    @Schema(description = "Display name")
    String name,

    @Schema(description = "Configuration sources")
    List<SourceConfigResp> sources
) {}
```

## 5. 完整示例：Egress Gateway API

### 5.1 API 模块定义

```java
// patra-egress-gateway-api/src/main/java/io/linqibin/papertrace/egress/api/endpoint/EgressEndpoint.java
public interface EgressEndpoint {

    String BASE_PATH = "/_internal/egress";

    /**
     * Execute external HTTP call through egress gateway
     *
     * @param request external call request
     * @return external call response
     */
    @PostMapping(BASE_PATH + "/call")
    EgressCallResp executeCall(@Valid @RequestBody EgressCallReq request);

    /**
     * Get call execution result by correlation ID
     *
     * @param correlationId correlation ID
     * @return call result
     */
    @GetMapping(BASE_PATH + "/result/{correlationId}")
    EgressCallResp getResult(@PathVariable String correlationId);
}

// patra-egress-gateway-api/src/main/java/io/linqibin/papertrace/egress/api/client/EgressGatewayClient.java
@FeignClient(
    name = "patra-egress-gateway",
    contextId = "egressGatewayClient",
    path = EgressEndpoint.BASE_PATH
)
public interface EgressGatewayClient extends EgressEndpoint {
}
```

### 5.2 Adapter 模块实现

```java
// patra-egress-gateway-adapter/src/main/java/io/linqibin/papertrace/egress/adapter/rest/ExternalCallController.java
@RestController
@RequiredArgsConstructor
@Slf4j
public class ExternalCallController implements EgressGatewayClient {

    private final ExternalCallAppService appService;
    private final EgressApiConverter converter;

    @Override
    public EgressCallResp executeCall(EgressCallReq request) {
        log.info("[EGRESS][ADAPTER] Execute external call: method={} url={}",
            request.method(), request.url());

        ExternalCallCommand command = converter.toCommand(request);
        ExternalCallResult result = appService.executeCall(command);

        return converter.toResp(result);
    }

    @Override
    public EgressCallResp getResult(String correlationId) {
        log.debug("[EGRESS][ADAPTER] Get call result: correlationId={}", correlationId);

        ExternalCallResult result = appService.getResult(correlationId);
        return converter.toResp(result);
    }
}
```

### 5.3 Infra 模块出站调用

```java
// patra-ingest-infra/src/main/java/io/linqibin/papertrace/ingest/infra/external/EgressPortRpcAdapter.java
@Component
@RequiredArgsConstructor
@Slf4j
public class EgressPortRpcAdapter implements EgressGatewayPort {

    private final EgressGatewayClient egressClient;
    private final EgressDomainConverter converter;

    @Override
    public HttpCallResult executeHttpCall(HttpCallRequest request) {
        log.debug("[INGEST][INFRA] Execute HTTP call via egress: url={}", request.getUrl());

        try {
            EgressCallReq req = converter.toEgressReq(request);
            EgressCallResp resp = egressClient.executeCall(req);

            return converter.toDomainResult(resp);

        } catch (FeignException e) {
            log.error("[INGEST][INFRA] Egress call failed: url={}", request.getUrl(), e);
            throw new EgressCallException("External call failed", e);
        }
    }
}
```

## 6. 测试策略

### 6.1 单元测试（API 模块）

测试 Endpoint 接口的契约定义：

```java
class ProvenanceEndpointTest {

    @Test
    void endpointMethodsShouldHaveCorrectMappings() {
        Method method = ReflectionUtils.findMethod(
            ProvenanceEndpoint.class,
            "getProvenance",
            ProvenanceCode.class
        );

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping.value()).containsExactly("/_internal/provenances/{code}");
    }
}
```

### 6.2 集成测试（Adapter 模块）

使用 `@WebMvcTest` 测试 Controller 实现：

```java
@WebMvcTest(ProvenanceClientImpl.class)
class ProvenanceClientImplTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProvenanceConfigAppService appService;

    @Test
    void shouldReturnProvenanceWhenCodeExists() throws Exception {
        // Given
        ProvenanceConfig config = ProvenanceConfig.builder()
            .code(ProvenanceCode.PUBMED)
            .name("PubMed")
            .build();
        when(appService.findProvenance(ProvenanceCode.PUBMED)).thenReturn(config);

        // When & Then
        mockMvc.perform(get("/_internal/provenances/PUBMED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("PUBMED"))
            .andExpect(jsonPath("$.name").value("PubMed"));
    }
}
```

### 6.3 契约测试（Feign Client）

使用 WireMock 或 Spring Cloud Contract 验证 Feign Client 行为：

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class ProvenanceClientContractTest {

    @Autowired
    private ProvenanceClient client;

    @Test
    void clientShouldCallCorrectEndpoint() {
        // Given
        stubFor(get(urlEqualTo("/_internal/provenances/PUBMED"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"PUBMED\",\"name\":\"PubMed\"}")));

        // When
        ProvenanceResp resp = client.getProvenance(ProvenanceCode.PUBMED);

        // Then
        assertThat(resp.code()).isEqualTo(ProvenanceCode.PUBMED);
        assertThat(resp.name()).isEqualTo("PubMed");

        verify(getRequestedFor(urlEqualTo("/_internal/provenances/PUBMED")));
    }
}
```

### 6.4 端到端测试（Infra 模块）

测试完整的出站调用链路：

```java
@SpringBootTest
class EgressPortRpcAdapterIntegrationTest {

    @Autowired
    private EgressGatewayPort egressPort;

    @MockBean
    private EgressGatewayClient egressClient; // Mock Feign Client

    @Test
    void shouldExecuteHttpCallSuccessfully() {
        // Given
        HttpCallRequest request = HttpCallRequest.builder()
            .url("https://api.example.com/data")
            .method(HttpMethod.GET)
            .build();

        EgressCallResp mockResp = new EgressCallResp(
            "corr-123",
            200,
            "{\"status\":\"ok\"}",
            null
        );
        when(egressClient.executeCall(any())).thenReturn(mockResp);

        // When
        HttpCallResult result = egressPort.executeHttpCall(request);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getBody()).contains("status");
    }
}
```

## 7. 常见问题（FAQ）

### Q1: 为什么不直接用 @FeignClient 在接口上？

**A**: 直接使用 @FeignClient 会导致契约与框架耦合，且 Controller 无法强制实现接口。Endpoint + Client 分离模式通过编译时检查确保契约一致性。

### Q2: Endpoint 接口必须使用 Spring Web 注解吗？

**A**: 推荐使用，这样 Client 继承后可直接复用注解。如果 API 模块希望零依赖，可以只定义方法签名，在 Client 中添加注解（但会降低可维护性）。

### Q3: 多个服务调用同一个服务时如何避免 Client 冲突？

**A**: 使用 `contextId` 参数区分：

```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {}

@FeignClient(name = "patra-registry", contextId = "expressionClient")
public interface ExpressionClient extends ExpressionEndpoint {}
```

### Q4: 如何处理 Feign 调用失败和降级？

**A**: 推荐使用 `fallbackFactory` 而非 `fallback`，提供更灵活的降级逻辑：

```java
@Component
public class ProvenanceClientFallbackFactory implements FallbackFactory<ProvenanceClient> {

    @Override
    public ProvenanceClient create(Throwable cause) {
        return new ProvenanceClient() {
            @Override
            public ProvenanceResp getProvenance(ProvenanceCode code) {
                log.error("Fallback: getProvenance failed for code={}", code, cause);
                // 返回降级响应或抛出业务异常
                throw new RegistryUnavailableException("Registry service unavailable", cause);
            }
        };
    }
}
```

### Q5: 是否可以在 Endpoint 中定义默认方法？

**A**: 不推荐。Endpoint 应保持纯契约性质，业务逻辑应在 Controller 或 App 层实现。如果需要通用逻辑，考虑使用 Converter 或 Helper 类。

### Q6: 如何版本化 API？

**A**: 建议通过路径版本化：

```java
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/v1/provenances"; // 路径中包含版本号

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}
```

重大变更时创建新版本的 Endpoint（如 `ProvenanceEndpointV2`），逐步迁移调用方。

## 8. 最佳实践

### 8.1 路径设计

✅ **推荐**：
```java
String BASE_PATH = "/_internal/provenances";  // 内部 API
String BASE_PATH = "/api/v1/plans";           // 外部 API（RESTful）
```

❌ **不推荐**：
```java
String BASE_PATH = "/getProvenances";         // 路径包含动作词
String BASE_PATH = "/api/provenance";         // 单数形式（应用复数）
```

### 8.2 异常处理

✅ **推荐**：在 Infra 层捕获 `FeignException`，转换为领域异常：

```java
try {
    EgressCallResp resp = egressClient.executeCall(req);
    return converter.toDomainResult(resp);
} catch (FeignException e) {
    log.error("[INGEST][INFRA] Egress call failed", e);
    throw new EgressCallException("External call failed", e);
}
```

❌ **不推荐**：让 `FeignException` 泄漏到 Domain 层。

### 8.3 日志记录

遵循 [日志规范](./logging-convention.md)，使用两段前缀 `[模块][层]`：

```java
// Adapter 层（入站）
log.info("[REGISTRY][ADAPTER] Get configuration: code={}", code);

// Infra 层（出站）
log.debug("[INGEST][INFRA] Fetch configuration from registry: code={}", code);
```

### 8.4 超时和重试配置

在 `application.yml` 中配置 Feign 客户端：

```yaml
feign:
  client:
    config:
      patra-registry:
        connectTimeout: 5000    # 连接超时 5s
        readTimeout: 10000      # 读取超时 10s
        loggerLevel: basic      # 日志级别
  circuitbreaker:
    enabled: true               # 启用断路器
```

### 8.5 Converter 职责划分

- **API Converter**：DTO ↔ Domain Model（Adapter 层使用）
- **Domain Converter**：External DTO ↔ Domain Model（Infra 层使用）

```java
// Adapter 层：ProvenanceApiConverter
@Mapper(componentModel = "spring")
public interface ProvenanceApiConverter {
    ProvenanceResp toResp(ProvenanceConfig config);
    ProvenanceConfig toModel(CreateProvenanceReq req);
}

// Infra 层：ProvenanceDomainConverter
@Mapper(componentModel = "spring")
public interface ProvenanceDomainConverter {
    ProvenanceConfigSnapshot toDomainSnapshot(ProvenanceConfigResp resp);
}
```

## 9. 迁移指南

### 9.1 识别待迁移 Feign Client

查找所有直接使用 `@FeignClient` 的接口：

```bash
grep -r "@FeignClient" --include="*.java"
```

### 9.2 迁移步骤

**Step 1: 创建 Endpoint 接口**

```java
// 新建：patra-{service}-api/src/main/java/.../endpoint/{Service}Endpoint.java
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}
```

**Step 2: 重构 Client 接口**

```java
// 修改现有 Client：继承 Endpoint，移除重复的方法定义
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
    // 移除所有方法定义，仅保留继承
}
```

**Step 3: Controller 实现 Client**

```java
// 修改现有 Controller：implements Client 接口
@RestController
public class ProvenanceClientImpl implements ProvenanceClient {

    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) {
        // 现有实现
    }
}
```

**Step 4: 验证编译和测试**

```bash
# 编译检查（会发现所有未实现的方法）
mvn clean compile

# 运行单元测试
mvn test

# 运行集成测试
mvn verify
```

### 9.3 迁移检查清单

- [ ] 所有 Feign Client 都继承了对应的 Endpoint 接口
- [ ] 所有 Controller 都实现了对应的 Client 接口
- [ ] Endpoint 接口定义了 `BASE_PATH` 常量
- [ ] `@FeignClient` 注解包含 `contextId` 参数
- [ ] Infra 层的 RPC Adapter 注入 Client 而非直接调用
- [ ] 所有单元测试和集成测试通过
- [ ] 日志记录符合规范（`[模块][层]` 前缀）
- [ ] 异常处理正确（FeignException 转换为领域异常）

## 10. 参考资料

- [Architecture Decision Record - ADR-001](../architecture/ADR-001-feign-api-unified-design.md)
- [日志规范](./logging-convention.md)
- [错误处理规范](./platform-error-handling.md)
- [Feign API Checklist](../templates/feign-api-checklist.md)
- [Spring Cloud OpenFeign 官方文档](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)

---

> 变更记录：
> - 2025-10-06：初始版本，架构评审通过并批准实施
