---
paths: patra-*/*-infra/**/src/integrationTest/**/*IT.java, patra-*/*-adapter/**/src/integrationTest/**/*IT.java
---

# 集成测试规范

## 适用范围

- Infrastructure 层：集成测试优先，使用 TestContainers/WireMock
- Adapter 层：切片测试，使用 `@MockitoBean`

## 文件命名

`*IT.java`

## 超时限制

`@Timeout` ≤ 30s（TestContainers 启动需要时间）

## 注意事项

1. 使用 `@MockitoBean` 进行 Mock 注入（`@MockBean` 已在 Spring Boot 4.0 中移除）
2. 统一使用 `patra-spring-boot-starter-test` 提供的测试自动配置
3. 使用 TestContainers 模拟真实中间件，避免使用内存数据库
4. `@DataJpaTest` 中使用 `TestEntityManager` 或 `JpaRepository` 进行数据准备

## REST Endpoint 切片测试

使用 `@WebMvcTest` + `RestTestClient` 进行 REST 接口 HTTP 层测试。

### 测试配置类

由于 adapter 模块不包含 `@SpringBootApplication`，需要提供 `{Module}ITWebMvcConfig`（命名规则见 `patra:test-driven-development` skill）：

```java
// 例：catalog-adapter 的 CatalogAdapterITWebMvcConfig
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({
    CoreErrorAutoConfiguration.class,
    WebErrorAutoConfiguration.class,
    JacksonAutoConfiguration.class
})
public class CatalogAdapterITWebMvcConfig {
    // 空配置类，提供 @SpringBootConfiguration 标记
}
```

> **重要**: 必须使用 `@ImportAutoConfiguration` 显式导入以下配置，
> 因为 `@WebMvcTest` 只加载 MVC 相关的自动配置切片：
> - **错误处理配置**：不会自动加载全局异常处理器
> - **Jackson 自动配置**：确保 Long → String 序列化模块生效（防止前端 JS 精度丢失）

### 测试类注解

```java
@WebMvcTest
@Import(XxxEndpointImpl.class)
@AutoConfigureRestTestClient
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class XxxEndpointIT {

    @Autowired
    private RestTestClient restClient;

    @MockitoBean
    private XxxQueryService queryService;

    @MockitoBean
    private XxxApiConverter converter;
}
```

### 验证要点

- HTTP 状态码（200/400/404/500 等）
- Content-Type（`application/json`）
- 响应体结构（使用 `jsonPath()` 或反序列化验证）
- 路径匹配和参数绑定
- Query Parameter 处理

### 测试示例

```java
@Test
@DisplayName("应该成功返回资源并返回 200 OK")
void shouldReturnResourceWith200() {
    // Given
    when(queryService.find(any())).thenReturn(Optional.of(mockResult));
    when(converter.toResp(any())).thenReturn(expectedResp);

    // When & Then
    restClient
        .get()
        .uri("/api/resources/{id}", resourceId)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(ResourceResp.class)
        .value(resp -> assertThat(resp.id()).isEqualTo(resourceId));
}

@Test
@DisplayName("当资源不存在时应该返回 404 Not Found")
void shouldReturn404WhenResourceNotFound() {
    // Given
    when(queryService.find(any())).thenReturn(Optional.empty());

    // When & Then
    restClient
        .get()
        .uri("/api/resources/{id}", resourceId)
        .exchange()
        .expectStatus().isNotFound();
}
```

### 异常处理说明

领域异常（如 `XxxNotFoundException`）继承自携带 `StandardErrorTrait.NOT_FOUND` 的基类时，
由 `DefaultErrorResolutionEngine` 内置映射自动转换为 HTTP 404。无需在测试中显式加载业务特定的 `ErrorMappingContributor`。

## 测试比例

切片测试 ~20%
