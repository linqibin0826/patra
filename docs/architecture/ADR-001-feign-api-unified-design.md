# ADR-001: 统一 Feign API 设计模式

## 元数据

| 属性 | 值 |
|------|-----|
| **状态** | 已批准（Approved） |
| **决策日期** | 2025-10-06 |
| **决策者** | 架构团队 + 开发团队 |
| **影响范围** | 所有微服务间 RPC 调用 |
| **复审时间** | 2025-12-31 |

## 背景（Context）

### 问题陈述

在 Papertrace 平台早期开发中，各微服务间的 Feign API 设计缺乏统一规范，导致以下问题：

1. **契约不一致风险**
   - Provider 端 Controller 实现与 Consumer 端 Feign Client 声明可能不同步
   - 接口变更时需要手动同步多处代码，容易遗漏
   - 只有在运行时才能发现不一致问题，增加了故障风险

2. **测试覆盖不足**
   - Feign Client 的 Mock 测试与实际 REST 调用行为可能不一致
   - 集成测试需要同时验证 Controller 和 Feign Client
   - 契约测试缺乏自动化保障

3. **维护成本高**
   - 重构时需要同时修改 Endpoint、Client 和 Controller
   - API 文档（OpenAPI）与实际实现容易脱节
   - 团队成员对 API 边界理解不一致

4. **协作障碍**
   - API 设计与实现未分离，难以并行开发
   - 服务提供方和消费方耦合紧密
   - 接口变更影响范围难以评估

### 现有实践分析

通过对现有代码的分析，发现了两种主要的设计模式：

#### 模式 1: 直接 Client 模式（patra-ingest）

```java
// 直接定义 Feign Client，包含所有注解
@FeignClient(name = "patra-registry")
public interface ProvenanceClient {
    @GetMapping("/_internal/provenances/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}

// Controller 独立实现，无强制约束
@RestController
public class ProvenanceController {
    @GetMapping("/_internal/provenances/{code}")
    public ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code) {
        // 实现
    }
}
```

**优点**：
- 代码简洁，文件数量少
- 对小型项目足够用

**缺点**：
- ❌ 无编译时契约保证（Controller 可以随意修改而不影响 Client）
- ❌ 测试行为不一致（Mock Client ≠ 实际 Controller）
- ❌ 重构时容易遗漏同步更新
- ❌ API 边界模糊，团队协作困难

#### 模式 2: Endpoint + Client 分离模式（patra-registry）

```java
// Step 1: 定义纯契约接口
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}

// Step 2: Client 继承 Endpoint
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
}

// Step 3: Controller 实现 Client（强制契约一致性）
@RestController
public class ProvenanceClientImpl implements ProvenanceClient {
    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) {
        // 实现
    }
}
```

**优点**：
- ✅ 编译时契约保证（Controller 必须实现 Client）
- ✅ 测试行为一致（Feign 调用 = REST 调用）
- ✅ 重构友好（修改 Endpoint 会强制更新所有实现）
- ✅ API 边界清晰，团队协作友好

**缺点**：
- 代码量略增（约 +10%）
- 需要额外的 Endpoint 接口定义

### 评估依据

基于以下维度对两种模式进行了评估：

| 维度 | 直接 Client 模式 | Endpoint + Client 模式 | 权重 |
|------|-----------------|----------------------|------|
| **编译时契约保证** | ❌ 无 | ✅ 强保证 | 高 |
| **测试行为一致性** | ⚠️ 需额外保证 | ✅ 天然一致 | 高 |
| **重构友好性** | ❌ 易遗漏 | ✅ 编译器强制 | 高 |
| **团队协作** | ⚠️ 边界模糊 | ✅ 边界清晰 | 中 |
| **代码简洁性** | ✅ 简洁 | ⚠️ 略冗余 | 低 |
| **学习成本** | ✅ 低 | ⚠️ 中等 | 低 |

综合评分：**Endpoint + Client 模式明显优于直接 Client 模式**。

## 决策（Decision）

**我们决定在 Papertrace 平台统一采用 "Endpoint + Client 分离模式" 作为微服务间 Feign API 的标准设计模式。**

### 核心设计原则

1. **API 模块（patra-{service}-api）**：
   - 定义 `{Service}Endpoint` 接口（纯契约，可选 Spring Web 注解）
   - 定义 `{Service}Client` 接口（继承 Endpoint，添加 @FeignClient 注解）
   - 定义 Request/Response DTO

2. **Adapter 模块（patra-{service}-adapter）**：
   - `{Service}ClientImpl` Controller **必须** `implements {Service}Client`
   - 负责入站 REST 请求处理
   - 调用 App 层服务编排业务逻辑

3. **Infra 模块（patra-{caller}-infra）**：
   - 定义 `{Service}PortRpcAdapter` 实现 Domain Port 接口
   - 注入 `{Service}Client` 发起 RPC 调用
   - 转换响应为 Domain 模型

### 架构约束

**必须遵守（MUST）**：
1. 所有 Feign Client 必须继承对应的 Endpoint 接口
2. 所有 Controller 必须实现对应的 Client 接口
3. Endpoint 必须定义 `BASE_PATH` 常量统一管理路径前缀
4. `@FeignClient` 必须包含 `contextId` 参数避免冲突
5. Infra 层必须通过 Port-Adapter 模式调用 Feign Client

**应该遵守（SHOULD）**：
1. Endpoint 接口应使用 Spring Web 注解（便于复用）
2. 内部 API 应使用 `/_internal` 路径前缀
3. 外部 API 应使用 `/api/v{version}` 路径前缀
4. 应使用 `fallbackFactory` 而非 `fallback` 处理降级

**可以选择（MAY）**：
1. 可以在 `@FeignClient` 的 `path` 参数中声明 BASE_PATH
2. 可以使用 Spring Cloud Contract 进行契约测试
3. 可以在 Endpoint 中添加 OpenAPI 注解增强文档

## 后果（Consequences）

### 正面后果（Positive）

1. **编译时契约保证**
   - Controller 必须实现 Client 接口，接口变更时编译器立即报错
   - 彻底避免了运行时才发现的契约不一致问题
   - 降低了 API 不一致导致的故障风险

2. **测试行为一致**
   - Feign Client 和 REST Controller 共享同一接口定义
   - Mock 测试与集成测试行为完全一致
   - 提升了测试的可靠性和覆盖率

3. **重构友好**
   - 修改 Endpoint 会触发所有实现和调用方的编译错误
   - IDE 自动提示需要更新的方法
   - 减少了重构时的遗漏风险

4. **团队协作友好**
   - Endpoint 作为清晰的 API 边界
   - 服务提供方和消费方可以并行开发
   - 接口变更影响范围一目了然

5. **易于维护演化**
   - API 版本化更加清晰（通过路径或新 Endpoint 接口）
   - 文档生成更加准确（基于 Endpoint 注解）
   - 降低了长期维护成本

### 负面后果（Negative）

1. **代码量增加**
   - 需要额外定义 Endpoint 接口（约 +10% 代码量）
   - 文件数量增加，目录结构略显复杂

2. **学习成本**
   - 新成员需要理解 Endpoint + Client 分离的设计理念
   - 需要培训和文档支持

3. **迁移成本**
   - 现有服务需要重构以符合新规范
   - 需要制定迁移计划和优先级

### 中性后果（Neutral）

1. **依赖 Spring 框架**
   - Endpoint 使用 Spring Web 注解，增加了对 Spring 的依赖
   - 但考虑到项目已全面使用 Spring Boot，这不算额外负担

2. **模块间依赖**
   - Adapter 和 Infra 都依赖 API 模块
   - 符合六边形架构的依赖方向，无负面影响

## 替代方案（Alternatives Considered）

### 方案 A: 保持现状（直接 Client 模式）

**优点**：
- 无迁移成本
- 代码简洁

**缺点**：
- 无法解决现有问题
- 长期维护成本高

**决策理由**：不采纳，问题会随项目规模增长而恶化。

### 方案 B: 使用 Spring Cloud Contract

**优点**：
- 提供契约测试自动化
- 生成 Stub 供消费方测试

**缺点**：
- 无法提供编译时契约保证
- 需要额外的契约定义和测试编写
- 学习成本更高

**决策理由**：可作为补充，但不能替代 Endpoint + Client 模式的编译时保证。

### 方案 C: 纯接口定义（无 Spring 注解）

**优点**：
- API 模块零依赖
- 更纯粹的契约定义

**缺点**：
- Client 需要重复定义所有注解
- 无法复用 Endpoint 的路径和参数定义
- 维护成本更高

**决策理由**：不采纳，失去了注解复用的便利性。

### 方案 D: 使用代码生成工具（如 OpenAPI Generator）

**优点**：
- 自动生成 Client 和 DTO
- 基于标准的 OpenAPI 规范

**缺点**：
- 需要额外的构建步骤
- 生成代码可能不符合项目规范
- 难以定制和调试

**决策理由**：可作为未来优化方向，但不能替代当前的手动设计模式。



## 成功指标（Success Metrics）

### 定量指标

1. **契约一致性**
   - 目标：100% 的 Feign Client 契约与 Controller 实现一致
   - 测量：通过编译检查和集成测试验证

2. **测试覆盖率**
   - 目标：所有 Feign API 都有对应的单元测试和契约测试
   - 测量：代码覆盖率工具统计



### 定性指标



2. **故障率降低**
   - 目标：API 不一致导致的故障降至 0
   - 测量：生产环境故障分析

3. **团队满意度**
   - 目标：80% 以上开发者认为新规范更易用
   - 测量：团队问卷调查

## 风险与缓解措施（Risks and Mitigation）



### 风险 2: 团队接受度低

**影响**：高
**概率**：低

**缓解措施**：
- 充分沟通设计理念和收益
- 提供培训和文档支持
- 收集反馈，持续优化规范
- 通过试点项目验证效果

### 风险 3: 性能开销

**影响**：低
**概率**：低

**缓解措施**：
- 设计模式仅增加编译时开销，运行时无影响
- 通过基准测试验证性能表现
- 如有问题，优先优化热点路径

## 相关决策（Related Decisions）

- [ADR-002: 微服务通信错误处理规范](./ADR-002-cross-service-error-handling.md)（计划中）
- [ADR-003: API 版本管理策略](./ADR-003-api-versioning-strategy.md)（计划中）

## 参考资料（References）

### 内部文档
- [Feign API 设计规范](../standards/feign-api-design-guide.md)
- [Feign API Checklist](../templates/feign-api-checklist.md)
- [日志规范](../standards/logging-convention.md)
- [错误处理规范](../standards/platform-error-handling.md)

### 外部资源
- [Spring Cloud OpenFeign 官方文档](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)
- [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract)
- [六边形架构模式](https://alistair.cockburn.us/hexagonal-architecture/)
- [RESTful API 设计最佳实践](https://restfulapi.net/)

## 附录：示例代码

### 完整示例：Provenance API

**patra-registry-api**：

```java
// ProvenanceEndpoint.java
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);
}

// ProvenanceClient.java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
}
```

**patra-registry-adapter**：

```java
// ProvenanceClientImpl.java
@RestController
@RequiredArgsConstructor
public class ProvenanceClientImpl implements ProvenanceClient {

    private final ProvenanceConfigAppService appService;
    private final ProvenanceApiConverter converter;

    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) {
        ProvenanceConfig config = appService.findProvenance(code);
        return converter.toResp(config);
    }
}
```

**patra-ingest-infra**：

```java
// ProvenancePortRpcAdapter.java
@Component
@RequiredArgsConstructor
public class ProvenancePortRpcAdapter implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient;

    @Override
    public ProvenanceConfigSnapshot fetchConfiguration(ProvenanceCode code) {
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(code);
        return converter.toDomainSnapshot(resp);
    }
}
```

---

**文档历史**：
- 2025-10-06：初始版本，架构评审通过
- 2025-12-31：计划复审日期
