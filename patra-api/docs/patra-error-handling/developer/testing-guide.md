# 错误处理测试指南

Patra 错误处理系统的测试最佳实践，包括单元测试、集成测试和端到端测试策略。

## 目录

1. [测试策略概览](#测试策略概览)
2. [单元测试](#单元测试)
3. [集成测试](#集成测试)
4. [端到端测试](#端到端测试)
5. [测试工具和框架](#测试工具和框架)
6. [测试最佳实践](#测试最佳实践)

## 测试策略概览

### 测试金字塔

```
    /\
   /  \     E2E Tests (少量)
  /____\    - 完整的用户场景
 /      \   - 真实的错误响应
/__________\ Integration Tests (适量)
            - 组件间交互
            - 错误处理流程
____________ Unit Tests (大量)
            - 错误解析逻辑
            - 异常映射
            - 状态码映射
```

### 测试范围

| 测试类型 | 测试内容 | 工具 | 比例 |
|----------|----------|------|------|
| 单元测试 | 错误解析、映射、格式化 | JUnit 5, Mockito | 70% |
| 集成测试 | 组件协作、配置加载 | Spring Boot Test | 20% |
| 端到端测试 | 完整错误处理流程 | TestContainers, WireMock | 10% |

## 单元测试

### 1. 错误解析服务测试

```java
@ExtendWith(MockitoExtension.class)
class ErrorResolutionServiceTest {
    
    @Mock
    private List<ErrorMappingContributor> contributors;
    
    @Mock
    private StatusMappingStrategy statusMappingStrategy;
    
    @InjectMocks
    private ErrorResolutionService errorResolutionService;
    
    @Test
    void shouldResolveApplicationException() {
        // Given
        ApplicationException exception = new ApplicationException(
            TestErrorCode.VALIDATION_FAILED, 
            "Validation failed"
        );
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode()).isEqualTo(TestErrorCode.VALIDATION_FAILED);
        assertThat(resolution.message()).isEqualTo("Validation failed");
    }
    
    @Test
    void shouldResolveByErrorTraits() {
        // Given
        TestNotFound exception = new TestNotFound("Resource not found");
        when(statusMappingStrategy.mapToHttpStatus(any(), any())).thenReturn(404);
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.httpStatus()).isEqualTo(404);
        assertThat(resolution.errorCode().code()).contains("0404");
    }
    
    @Test
    void shouldUseErrorMappingContributor() {
        // Given
        RuntimeException exception = new RuntimeException("Test error");
        ErrorMappingContributor contributor = mock(ErrorMappingContributor.class);
        when(contributor.mapException(exception))
            .thenReturn(Optional.of(TestErrorCode.CUSTOM_ERROR));
        when(contributors.iterator()).thenReturn(List.of(contributor).iterator());
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode()).isEqualTo(TestErrorCode.CUSTOM_ERROR);
    }
    
    @Test
    void shouldCacheResolutionResults() {
        // Given
        RuntimeException exception1 = new RuntimeException("Test");
        RuntimeException exception2 = new RuntimeException("Test");
        
        // When
        ErrorResolution resolution1 = errorResolutionService.resolve(exception1);
        ErrorResolution resolution2 = errorResolutionService.resolve(exception2);
        
        // Then
        assertThat(resolution1).isSameAs(resolution2); // 缓存生效
    }
}
```

### 2. 错误映射贡献者测试

```java
class RegistryErrorMappingContributorTest {
    
    private RegistryErrorMappingContributor contributor;
    
    @BeforeEach
    void setUp() {
        contributor = new RegistryErrorMappingContributor();
    }
    
    @Test
    void shouldMapDictionaryTypeNotFound() {
        // Given
        DictionaryNotFoundException exception = new DictionaryNotFoundException("COUNTRY");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(RegistryErrorCode.REG_1401);
    }
    
    @Test
    void shouldMapDictionaryItemNotFound() {
        // Given
        DictionaryNotFoundException exception = new DictionaryNotFoundException("COUNTRY", "US");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(RegistryErrorCode.REG_1402);
    }
    
    @Test
    void shouldMapDataLayerExceptions() {
        // Given
        DuplicateKeyException exception = new DuplicateKeyException("Duplicate key");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(RegistryErrorCode.REG_0409);
    }
    
    @Test
    void shouldReturnEmptyForUnmappedException() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("Unknown");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isEmpty();
    }
}
```

### 3. ProblemDetail 构建器测试

```java
@ExtendWith(MockitoExtension.class)
class ProblemDetailBuilderTest {
    
    @Mock
    private List<ProblemFieldContributor> contributors;
    
    @Mock
    private HttpServletRequest request;
    
    @InjectMocks
    private ProblemDetailBuilder builder;
    
    @Test
    void shouldBuildBasicProblemDetail() {
        // Given
        ErrorResolution resolution = new ErrorResolution(
            TestErrorCode.VALIDATION_FAILED, 
            422, 
            "Validation failed"
        );
        when(request.getRequestURI()).thenReturn("/api/test");
        
        // When
        ProblemDetail problem = builder.build(resolution, request);
        
        // Then
        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("TEST-0422");
        assertThat(problem.getDetail()).isEqualTo("Validation failed");
        assertThat(problem.getProperties().get("code")).isEqualTo("TEST-0422");
        assertThat(problem.getProperties().get("path")).isEqualTo("/api/test");
        assertThat(problem.getProperties().get("timestamp")).isNotNull();
    }
    
    @Test
    void shouldIncludeContributorFields() {
        // Given
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.VALIDATION_FAILED, 422, "Test");
        ProblemFieldContributor contributor = mock(ProblemFieldContributor.class);
        doAnswer(invocation -> {
            Map<String, Object> fields = invocation.getArgument(0);
            fields.put("customField", "customValue");
            return null;
        }).when(contributor).contribute(any(), any());
        when(contributors.iterator()).thenReturn(List.of(contributor).iterator());
        
        // When
        ProblemDetail problem = builder.build(resolution, request);
        
        // Then
        assertThat(problem.getProperties().get("customField")).isEqualTo("customValue");
    }
}
```

### 4. Feign 错误解码器测试

```java
class ProblemDetailErrorDecoderTest {
    
    private ProblemDetailErrorDecoder decoder;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        FeignErrorProperties properties = new FeignErrorProperties();
        properties.setTolerant(true);
        decoder = new ProblemDetailErrorDecoder(objectMapper, properties);
    }
    
    @Test
    void shouldDecodeProblemDetailResponse() {
        // Given
        String problemJson = """
            {
                "type": "https://errors.example.com/test-1001",
                "title": "TEST-1001",
                "status": 404,
                "detail": "Resource not found",
                "code": "TEST-1001",
                "traceId": "abc123"
            }
            """;
        Response response = Response.builder()
            .status(404)
            .reason("Not Found")
            .headers(Map.of("content-type", List.of("application/problem+json")))
            .body(problemJson, StandardCharsets.UTF_8)
            .build();
        
        // When
        Exception result = decoder.decode("TestClient#method()", response);
        
        // Then
        assertThat(result).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteEx = (RemoteCallException) result;
        assertThat(remoteEx.getErrorCode()).isEqualTo("TEST-1001");
        assertThat(remoteEx.getHttpStatus()).isEqualTo(404);
        assertThat(remoteEx.getTraceId()).isEqualTo("abc123");
    }
    
    @Test
    void shouldHandleNonProblemDetailResponseInTolerantMode() {
        // Given
        Response response = Response.builder()
            .status(500)
            .reason("Internal Server Error")
            .headers(Map.of("content-type", List.of("text/plain")))
            .body("Internal server error", StandardCharsets.UTF_8)
            .build();
        
        // When
        Exception result = decoder.decode("TestClient#method()", response);
        
        // Then
        assertThat(result).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteEx = (RemoteCallException) result;
        assertThat(remoteEx.getHttpStatus()).isEqualTo(500);
        assertThat(remoteEx.getMessage()).contains("Internal server error");
    }
}
```

## 集成测试

### 1. Web 错误处理集成测试

```java
@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.web.problem.enabled=true",
    "patra.web.problem.type-base-url=https://test-errors.example.com/"
})
class WebErrorHandlingIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnProblemDetailForDomainException() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://test-errors.example.com/test-1001"))
            .andExpect(jsonPath("$.title").value("TEST-1001"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").exists())
            .andExpect(jsonPath("$.code").value("TEST-1001"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.path").value("/api/test/not-found"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
    
    @Test
    void shouldFormatValidationErrors() throws Exception {
        mockMvc.perform(post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("TEST-0422"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[0].field").exists())
            .andExpect(jsonPath("$.errors[0].message").exists())
            .andExpect(jsonPath("$.errors[0].rejectedValue").exists());
    }
    
    @Test
    void shouldMaskSensitiveDataInValidationErrors() throws Exception {
        mockMvc.perform(post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"secret123\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[?(@.field=='password')].rejectedValue").value("***"));
    }
}
```

### 2. Feign 错误处理集成测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.feign.problem.enabled=true",
    "patra.feign.problem.tolerant=true"
})
class FeignErrorHandlingIntegrationTest {
    
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().port(8089))
        .build();
    
    @Autowired
    private TestFeignClient testClient;
    
    @Test
    void shouldDecodeProblemDetailFromDownstream() {
        // Given
        wireMock.stubFor(get(urlEqualTo("/api/test/resource/123"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                        "type": "https://errors.example.com/downstream-1001",
                        "title": "DOWNSTREAM-1001",
                        "status": 404,
                        "detail": "Resource not found",
                        "code": "DOWNSTREAM-1001",
                        "traceId": "test-trace-123"
                    }
                    """)));
        
        // When & Then
        assertThatThrownBy(() -> testClient.getResource("123"))
            .isInstanceOf(RemoteCallException.class)
            .satisfies(ex -> {
                RemoteCallException remoteEx = (RemoteCallException) ex;
                assertThat(remoteEx.getErrorCode()).isEqualTo("DOWNSTREAM-1001");
                assertThat(remoteEx.getHttpStatus()).isEqualTo(404);
                assertThat(remoteEx.getTraceId()).isEqualTo("test-trace-123");
            });
    }
    
    @Test
    void shouldHandleNonProblemDetailResponse() {
        // Given
        wireMock.stubFor(get(urlEqualTo("/api/test/resource/456"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "text/plain")
                .withBody("Internal server error")));
        
        // When & Then
        assertThatThrownBy(() -> testClient.getResource("456"))
            .isInstanceOf(RemoteCallException.class)
            .satisfies(ex -> {
                RemoteCallException remoteEx = (RemoteCallException) ex;
                assertThat(remoteEx.getHttpStatus()).isEqualTo(500);
                assertThat(remoteEx.getMessage()).contains("Internal server error");
            });
    }
}
```

### 3. 配置加载测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.error.enabled=true",
    "patra.web.problem.enabled=true",
    "patra.web.problem.type-base-url=https://test.example.com/",
    "patra.feign.problem.enabled=true",
    "patra.feign.problem.tolerant=false"
})
class ConfigurationLoadingTest {
    
    @Autowired
    private ErrorProperties errorProperties;
    
    @Autowired
    private WebErrorProperties webErrorProperties;
    
    @Autowired
    private FeignErrorProperties feignErrorProperties;
    
    @Test
    void shouldLoadAllProperties() {
        // Error properties
        assertThat(errorProperties.getContextPrefix()).isEqualTo("TEST");
        assertThat(errorProperties.isEnabled()).isTrue();
        
        // Web properties
        assertThat(webErrorProperties.isEnabled()).isTrue();
        assertThat(webErrorProperties.getTypeBaseUrl()).isEqualTo("https://test.example.com/");
        
        // Feign properties
        assertThat(feignErrorProperties.isEnabled()).isTrue();
        assertThat(feignErrorProperties.isTolerant()).isFalse();
    }
}
```

## 端到端测试

### 1. 完整错误处理流程测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EndToEndErrorHandlingTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
    
    @Test
    void shouldHandleCompleteErrorFlow() {
        // Given - 尝试获取不存在的资源
        String resourceId = "nonexistent-resource";
        
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/api/test/resources/" + resourceId, 
            Map.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        
        Map<String, Object> body = response.getBody();
        assertThat(body.get("type")).asString().contains("test-1001");
        assertThat(body.get("title")).isEqualTo("TEST-1001");
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("code")).isEqualTo("TEST-1001");
        assertThat(body.get("traceId")).isNotNull();
        assertThat(body.get("path")).isEqualTo("/api/test/resources/" + resourceId);
        assertThat(body.get("timestamp")).isNotNull();
    }
    
    @Test
    void shouldHandleValidationErrorsEndToEnd() {
        // Given - 发送无效数据
        Map<String, Object> invalidRequest = Map.of(
            "name", "",  // 空名称
            "email", "invalid-email"  // 无效邮箱
        );
        
        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/test/resources", 
            invalidRequest, 
            Map.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        
        Map<String, Object> body = response.getBody();
        assertThat(body.get("code")).isEqualTo("TEST-0422");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertThat(errors).hasSize(2);
        
        // 验证字段错误
        assertThat(errors).anySatisfy(error -> {
            assertThat(error.get("field")).isEqualTo("name");
            assertThat(error.get("message")).asString().contains("empty");
        });
        
        assertThat(errors).anySatisfy(error -> {
            assertThat(error.get("field")).isEqualTo("email");
            assertThat(error.get("message")).asString().contains("email");
        });
    }
}
```

### 2. 服务间调用错误处理测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceToServiceErrorHandlingTest {
    
    @RegisterExtension
    static WireMockExtension downstreamService = WireMockExtension.newInstance()
        .options(wireMockConfig().port(8090))
        .build();
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldPropagateDownstreamErrors() {
        // Given - 下游服务返回错误
        downstreamService.stubFor(get(urlEqualTo("/api/downstream/resource/123"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                        "type": "https://errors.example.com/downstream-1001",
                        "title": "DOWNSTREAM-1001",
                        "status": 404,
                        "detail": "Downstream resource not found",
                        "code": "DOWNSTREAM-1001",
                        "traceId": "downstream-trace-123"
                    }
                    """)));
        
        // When - 调用我们的服务，它会调用下游服务
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/api/test/proxy/resource/123", 
            Map.class
        );
        
        // Then - 应该返回我们自己的错误格式
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        
        Map<String, Object> body = response.getBody();
        assertThat(body.get("code")).isEqualTo("TEST-1002"); // 我们的代理错误代码
        assertThat(body.get("detail")).asString().contains("Downstream resource not found");
        assertThat(body.get("traceId")).isNotNull(); // 应该有我们的trace ID
    }
}
```

## 测试工具和框架

### 1. 测试依赖

```xml
<dependencies>
    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Testcontainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- WireMock -->
    <dependency>
        <groupId>com.github.tomakehurst</groupId>
        <artifactId>wiremock-jre8</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- AssertJ -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 2. 测试配置

```java
@TestConfiguration
public class TestErrorHandlingConfiguration {
    
    @Bean
    @Primary
    public ErrorMappingContributor testErrorMappingContributor() {
        return exception -> {
            if (exception instanceof TestNotFoundException) {
                return Optional.of(TestErrorCode.TEST_1001);
            }
            return Optional.empty();
        };
    }
    
    @Bean
    @Primary
    public TraceProvider testTraceProvider() {
        return () -> Optional.of("test-trace-" + System.currentTimeMillis());
    }
}
```

### 3. 测试工具类

```java
public class ErrorTestUtils {
    
    public static RemoteCallException createRemoteCallException(
            String errorCode, int status, String message) {
        RemoteCallException ex = new RemoteCallException(
            status, message, "TestClient#testMethod()", "test-trace-123"
        );
        ex.setErrorCode(errorCode);
        return ex;
    }
    
    public static void assertProblemDetail(
            Map<String, Object> problemDetail, 
            String expectedCode, 
            int expectedStatus) {
        assertThat(problemDetail.get("code")).isEqualTo(expectedCode);
        assertThat(problemDetail.get("status")).isEqualTo(expectedStatus);
        assertThat(problemDetail.get("title")).isEqualTo(expectedCode);
        assertThat(problemDetail.get("traceId")).isNotNull();
        assertThat(problemDetail.get("timestamp")).isNotNull();
    }
    
    public static MockHttpServletRequest createMockRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setMethod("GET");
        return request;
    }
}
```

## 测试最佳实践

### 1. 测试命名约定

```java
// 单元测试命名：should + 预期行为 + 当 + 条件
@Test
void shouldReturnNotFoundError_whenResourceDoesNotExist() { }

@Test
void shouldMapToConflictStatus_whenDuplicateKeyException() { }

// 集成测试命名：should + 预期行为 + 场景描述
@Test
void shouldReturnProblemDetailResponse_forDomainException() { }

@Test
void shouldPropagateTraceId_throughErrorHandlingChain() { }
```

### 2. 测试数据管理

```java
@TestMethodOrder(OrderAnnotation.class)
class ErrorHandlingDataTest {
    
    @BeforeEach
    void setUp() {
        // 每个测试前清理数据
        testDataManager.cleanUp();
        testDataManager.setupBasicData();
    }
    
    @Test
    @Order(1)
    void shouldHandleDataNotFound() {
        // 测试数据不存在的情况
    }
    
    @Test
    @Order(2)
    void shouldHandleDataConflict() {
        // 先创建数据，再测试冲突
        testDataManager.createTestData();
        // 测试逻辑
    }
}
```

### 3. 异步错误处理测试

```java
@Test
void shouldHandleAsyncErrors() throws Exception {
    // Given
    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
        throw new TestNotFoundException("Async resource not found");
    });
    
    // When & Then
    assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
        .hasCauseInstanceOf(TestNotFoundException.class);
}
```

### 4. 性能测试

```java
@Test
void shouldResolveErrorsEfficiently() {
    // Given
    List<Exception> exceptions = IntStream.range(0, 1000)
        .mapToObj(i -> new TestNotFoundException("Resource " + i))
        .collect(Collectors.toList());
    
    // When
    long startTime = System.currentTimeMillis();
    List<ErrorResolution> resolutions = exceptions.stream()
        .map(errorResolutionService::resolve)
        .collect(Collectors.toList());
    long endTime = System.currentTimeMillis();
    
    // Then
    assertThat(resolutions).hasSize(1000);
    assertThat(endTime - startTime).isLessThan(100); // 应该在100ms内完成
}
```

通过遵循这些测试指南和最佳实践，可以确保错误处理系统的质量和可靠性。