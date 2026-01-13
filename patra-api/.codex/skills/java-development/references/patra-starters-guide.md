# Patra Starters 快速参考

## 按模块层分类

| 模块层 | Starters |
|-------|----------|
| **adapter** | `patra-spring-boot-starter-web` |
| **infra（数据库）** | `patra-spring-boot-starter-jpa` |
| **infra（服务调用）** | `patra-spring-boot-starter-http-interface` |
| **infra（对象存储）** | `patra-spring-boot-starter-object-storage` |
| **infra（REST 调用）** | `patra-spring-boot-starter-rest-client` |
| **infra（分布式锁）** | `patra-spring-boot-starter-redisson` |
| **所有层（除 domain）** | `patra-spring-boot-starter-core` |
| **可选增强** | `patra-spring-boot-starter-observability` |

---

## 1. patra-spring-boot-starter-web

**模块坐标**: `com.patra:patra-spring-boot-starter-web`

**适用场景**: `patra-{service}-adapter` 模块

**核心功能**:
- REST Controller（Spring MVC）
- 全局异常处理
- 参数校验（`@Valid`）
- 统一响应模型

**使用示例**:
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

---

## 2. patra-spring-boot-starter-jpa

**模块坐标**: `com.patra:patra-spring-boot-starter-jpa`

**适用场景**: `patra-{service}-infra` 模块（涉及数据库）

**核心功能**:
- Spring Data JPA 自动配置
- `BaseJpaEntity` 基类（雪花 ID + 审计字段 + 乐观锁）
- `SoftDeletableJpaEntity` 基类（在 `BaseJpaEntity` 基础上额外支持软删除）
- 批量操作优化
- Flyway 数据库迁移

**使用示例**:
```java
/// Entity 定义
@Entity
@Table(name = "t_user")
@Getter
@Setter
public class UserEntity extends BaseJpaEntity {
    /// 用户手机号
    @Column(name = "phone")
    private String phone;
}

/// Dao 定义（继承 JpaRepository）
public interface UserDao extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByPhone(String phone);
}

/// Repository 实现
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {
    private final UserDao dao;
    private final UserJpaMapper mapper;

    @Override
    public Optional<User> findById(Long id) {
        return dao.findById(id).map(mapper::toDomain);
    }
}
```

---

## 3. patra-spring-boot-starter-http-interface

**模块坐标**: `com.patra:patra-spring-boot-starter-http-interface`

**适用场景**: `patra-{service}-infra` 模块（调用其他服务）

**核心功能**:
- Spring Framework 7 HTTP Interface 自动配置
- RestClient + @HttpExchange 声明式客户端
- Consul 服务发现（通过 Spring Cloud LoadBalancer）
- RFC 7807 ProblemDetail 错误处理

> **注意**：TraceId 传播由 OpenTelemetry Java Agent 自动处理，无需手动配置。

**Jackson 3 与 ProblemDetail 兼容性**:

Spring Boot 4 + Jackson 3 环境下，`ProblemDetail` 的 JSON 序列化/反序列化**开箱即用**：

- **jackson-annotations 保持原包名**：Jackson 3 的 `jackson-annotations` 模块保留 `com.fasterxml.jackson.annotation` 包名（向后兼容），因此 `@JsonAnySetter` 等注解仍然有效
- **自动 Mixin 注册**：Spring Boot 4 的 `JacksonAutoConfiguration` 通过 `ProblemDetailJsonMapperBuilderCustomizer` 自动注册 `ProblemDetailJacksonMixin`
- **无需手动处理**：可直接使用 `objectMapper.readValue(json, ProblemDetail.class)` 反序列化

**使用示例**:
```java
/// 步骤 1: 在 -api 模块定义 Endpoint 接口（使用 @HttpExchange）
@HttpExchange(url = "/_internal/provenances", accept = "application/json")
public interface ProvenanceEndpoint {
    @GetExchange("/{code}/config")
    ProvenanceConfigResp getConfiguration(
        @PathVariable("code") ProvenanceCode code,
        @RequestParam(value = "operationType", required = false) String operationType
    );
}

/// 步骤 2: 在 -boot 模块的 HttpClientConfiguration 中注册代理
@Configuration
public class HttpClientConfiguration {
    @Bean
    public RestClient registryRestClient(
            @Qualifier("httpInterfaceLoadBalancedRestClientBuilder") RestClient.Builder builder,
            RestClientFactory factory) {
        return factory.createRestClient(builder, "registry", "lb://patra-registry");
    }

    @Bean
    public ProvenanceEndpoint provenanceEndpoint(
            RestClient registryRestClient, RestClientFactory factory) {
        return factory.createProxy(registryRestClient, ProvenanceEndpoint.class);
    }
}

/// 步骤 3: 在 -infra 模块注入使用（直接注入 Endpoint）
@Component
@RequiredArgsConstructor
public class ProvenanceAdapter implements ProvenancePort {
    private final ProvenanceEndpoint provenanceEndpoint;
    @Override
    public ProvenanceConfig getConfig(ProvenanceCode code) {
        return converter.toDomain(provenanceEndpoint.getConfiguration(code, "HARVEST"));
    }
}
```

---

## 4. patra-spring-boot-starter-object-storage

**模块坐标**: `com.patra:patra-spring-boot-starter-object-storage`

**适用场景**: `patra-{service}-infra` 模块（需要对象存储）

**核心功能**:
- MinIO/S3 统一抽象
- `ObjectStorageTemplate` 核心操作
- `StorageLocationResolver` 位置解析
- 自动重试、指标收集、Bucket 自动管理

**使用示例**:
```java
@Component
@RequiredArgsConstructor
public class PublicationStorageAdapter implements PublicationStoragePort {
    private final ObjectStorageTemplate objectStorageTemplate;
    private final StorageLocationResolver storageLocationResolver;

    @Override
    public StorageResult store(List<CanonicalPublication> publications) {
        byte[] payload = objectMapper.writeValueAsBytes(publications);

        StorageLocation location = storageLocationResolver.resolve(
            StorageContext.builder()
                .businessType("publication-batch")
                .filename("batch-001.json")
                .build()
        );

        UploadResult result = objectStorageTemplate.upload(
            location.bucket(), location.objectKey(),
            new ByteArrayInputStream(payload),
            ObjectMetadata.builder().contentType("application/json").build()
        );

        return new StorageResult(result.getStorageKey());
    }
}
```

---

## 5. patra-spring-boot-starter-rest-client

**模块坐标**: `com.patra:patra-spring-boot-starter-rest-client`

**适用场景**: `patra-{service}-infra` 模块（调用外部 REST API、下载文件）

**核心功能**:
- Spring RestClient 自动配置（基于 JDK 21 HttpClient）
- 统一超时控制
- 日志拦截器
- `ClientInterceptor` 扩展点（可观测性集成）

**使用示例**:
```java
@Repository
@RequiredArgsConstructor
public class ExternalApiAdapter implements ExternalApiPort {
    private final RestClient defaultRestClient;

    @Override
    public UserData fetchUser(Long id) {
        return defaultRestClient.get()
            .uri("https://api.example.com/users/{id}", id)
            .retrieve()
            .body(UserData.class);
    }
}
```

---

## 6. patra-spring-boot-starter-redisson

**模块坐标**: `com.patra:patra-spring-boot-starter-redisson`

**适用场景**: `patra-{service}-infra` 模块（需要分布式锁）

**核心功能**:
- RedissonClient 自动配置
- `@DistributedLock` 声明式注解
- SpEL 表达式支持（动态生成锁键）
- 多种锁类型（可重入锁、公平锁、读写锁）
- 可观测性集成（OpenTelemetry、Micrometer）

**使用示例**:
```java
@Service
public class PlanService {

    @DistributedLock(
        key = "plan:#{#planId}",
        leaseTime = 30,
        waitTime = 5
    )
    public void executePlan(Long planId) {
        // 业务逻辑自动受锁保护
    }

    @DistributedLock(
        key = "config:#{#key}",
        type = LockType.READ
    )
    public String getConfig(String key) {
        // 读锁：允许并发读
    }
}
```

---

## 7. patra-spring-boot-starter-core

**模块坐标**: `com.patra:patra-spring-boot-starter-core`

**适用场景**: **所有模块（除了 domain 层）**

**核心功能**:
- JSON/XML 序列化标准化配置
- 错误处理框架与追踪传播
- 统一 UTC 时间源

**强制规范**:
- ✅ 所有非 domain 模块（adapter、app、infra、api）都可以依赖
- ❌ domain 模块禁止依赖

**使用示例**:
```java
// 所有非 domain 模块自动继承以下能力
@Service
public class SomeService {
    // 自动获得：
    // - JSON 序列化配置
    // - 错误处理框架
    // - UTC 时间源
}
```

---

## 8. patra-spring-boot-starter-observability

**模块坐标**: `com.patra:patra-spring-boot-starter-observability`

**适用场景**: 可选依赖（增强可观测性）

**核心功能**:
- **Metrics**（指标）：Micrometer + Prometheus/OTLP
- **Tracing**（追踪）：OpenTelemetry + Micrometer Bridge
- **Logging**（日志）：Logback + OTLP Appender
- **敏感数据脱敏**（P0 级别）
- **插件式架构**：通过扩展点接口集成（`ResolutionInterceptor`、`ClientInterceptor`、`JobExecutionListener`）

**使用示例**:
```java
// 添加依赖后，自动启用可观测性
// 无需修改代码，所有拦截器自动注册

// 自定义 ObservationHandler
@Component
public class CustomHandler implements ObservationHandler<Observation.Context> {
    @Override
    public void onStart(Observation.Context context) {
        // 观察开始时的逻辑
    }

    @Override
    public void onStop(Observation.Context context) {
        // 观察结束时的逻辑
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }
}
```

---

## 依赖检查清单

新建功能时，按以下顺序检查：

1. ✅ **Adapter 层** → 添加 `patra-spring-boot-starter-web`
2. ✅ **Infra 层（数据库）** → 添加 `patra-spring-boot-starter-jpa`
   - 确认 Entity 继承 `BaseJpaEntity`；需要软删除时继承 `SoftDeletableJpaEntity`
3. ✅ **Infra 层（服务调用）** → 添加 `patra-spring-boot-starter-http-interface`(可选)
   - 在 `-api` 模块定义 `@HttpExchange` 接口
   - 在 `-boot` 模块的 `HttpClientConfiguration` 注册代理
   - 在 `-infra` 模块实现 Adapter
4. ✅ **Infra 层（对象存储）** → 添加 `patra-spring-boot-starter-object-storage`(可选)
5. ✅ **Infra 层（REST 调用）** → 添加 `patra-spring-boot-starter-rest-client`(可选)
6. ✅ **Infra 层（分布式锁）** → 添加 `patra-spring-boot-starter-redisson` (可选)
7. ✅ **Domain 层** → ❌ 不能添加任何 Spring Boot Starter
8. ✅ **其他层** → 添加 `patra-spring-boot-starter-core`
9. ✅ **可选** → 添加 `patra-spring-boot-starter-observability`
