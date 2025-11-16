# patra-object-storage-adapter — 适配器层

## 概述

**patra-object-storage-adapter** 是 patra-object-storage 服务的适配器层,负责处理外部请求并将其转换为应用层命令。作为六边形架构的**入站适配器**(Inbound Adapter),本模块实现 REST 端点,接收来自其他微服务的 Feign 调用,并将 HTTP 请求适配为应用层用例。

在六边形架构中,适配器层是应用与外部世界的接口,负责协议转换、参数验证、异常处理和响应格式化,但不包含业务逻辑。

**核心原则**:
- **协议适配**: 将 HTTP/REST 协议转换为应用层命令
- **验证前置**: 在进入应用层之前完成参数验证
- **异常转换**: 将应用层异常转换为 HTTP 状态码和错误响应
- **无业务逻辑**: 仅负责适配和转换,业务逻辑在应用层和领域层

## 核心职责

- **端点实现**: 实现 API 契约层定义的 `StorageEndpoint` 接口
- **REST 控制器**: 提供 HTTP/JSON 端点供 Feign 客户端调用
- **请求验证**: 通过 `@Valid` 触发 Bean Validation 验证
- **命令构造**: 将 DTO 转换为应用层命令对象
- **响应转换**: 将应用层结果转换为 DTO 响应
- **异常处理**: 捕获并转换异常为合适的 HTTP 状态码

## 模块结构

```
patra-object-storage-adapter/
└── src/main/java/com/patra/storage/adapter/
    └── rest/
        └── internal/
            ├── StorageEndpointImpl.java      # 端点实现
            └── package-info.java             # 包说明
```

## 主要组件

### StorageEndpointImpl (端点实现)

REST 控制器,实现 `StorageEndpoint` 接口,处理文件上传记录请求。

```java
@RestController
@Validated
@RequiredArgsConstructor
public class StorageEndpointImpl implements StorageEndpoint {

    private final RecordUploadOrchestrator orchestrator;

    @Override
    public RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request) {
        // 1. 获取当前 HTTP 请求
        HttpServletRequest httpRequest = getCurrentRequest();

        // 2. 构造应用层命令
        RecordUploadCommand command = buildCommand(request, httpRequest);

        // 3. 调用应用层编排器
        RecordUploadResult result = orchestrator.execute(command);

        // 4. 转换为响应 DTO
        return new RecordUploadResponse(result.metadataId(), result.recordedAt());
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }

    private RecordUploadCommand buildCommand(
        UploadRecordRequest request, HttpServletRequest httpRequest) {
        return new RecordUploadCommand(
            request.bucketName(),
            request.objectKey(),
            request.fileSize(),
            request.contentType(),
            request.md5Hash(),
            request.sha256Hash(),
            request.serviceName(),
            request.businessType(),
            request.businessId(),
            request.correlationData(),
            request.providerType(),
            request.expiresAt(),
            resolveIp(httpRequest),
            request.recordRemarks()
        );
    }

    private byte[] resolveIp(HttpServletRequest request) {
        // 1. 优先从 X-Forwarded-For 头获取真实 IP
        String header = request.getHeader("X-Forwarded-For");
        String source = header != null ? header.split(",")[0].trim() : request.getRemoteAddr();

        if (source == null || source.isBlank()) {
            return null;
        }

        try {
            // 2. 解析为 InetAddress 并转换为字节数组
            return InetAddress.getByName(source).getAddress();
        } catch (UnknownHostException ex) {
            log.warn("无法解析客户端 IP 地址: {}", source, ex);
            return null;
        }
    }
}
```

**设计要点**:
- **@RestController**: Spring MVC REST 控制器
- **@Validated**: 启用方法级别的验证
- **@RequiredArgsConstructor**: Lombok 自动注入依赖
- **实现接口**: 实现 `StorageEndpoint` 接口,确保契约一致性

**职责分层**:
1. **协议层**: 处理 HTTP 请求/响应
2. **适配层**: 将 DTO 转换为命令对象
3. **编排层**: 调用应用层编排器
4. **转换层**: 将结果转换为响应 DTO

### IP 地址解析

适配器层负责解析客户端 IP 地址,支持反向代理场景:

```java
private byte[] resolveIp(HttpServletRequest request) {
    // 1. 检查 X-Forwarded-For 头(反向代理场景)
    String header = request.getHeader("X-Forwarded-For");

    // 2. 提取第一个 IP(多次代理时取最初的客户端 IP)
    String source = header != null ? header.split(",")[0].trim() : request.getRemoteAddr();

    // 3. 解析为字节数组(支持 IPv4/IPv6)
    try {
        return InetAddress.getByName(source).getAddress();
    } catch (UnknownHostException ex) {
        log.warn("无法解析客户端 IP 地址: {}", source, ex);
        return null;  // 无法解析时返回 null,不阻塞主流程
    }
}
```

**支持场景**:
- **直连**: 从 `request.getRemoteAddr()` 获取 IP
- **Nginx 反向代理**: 从 `X-Forwarded-For` 头获取真实 IP
- **多层代理**: 取第一个 IP 作为客户端 IP
- **IPv4/IPv6**: `InetAddress.getAddress()` 返回 4 字节(IPv4)或 16 字节(IPv6)

## 请求验证

### Bean Validation

适配器层通过 Jakarta Validation 注解进行参数验证:

```java
@Override
public RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request) {
    // @Valid 触发验证,验证失败抛出 MethodArgumentNotValidException
}
```

**验证注解**(在 DTO 中定义):
```java
public record UploadRecordRequest(
    @NotBlank String bucketName,        // 必填,非空字符串
    @NotBlank String objectKey,         // 必填,非空字符串
    @PositiveOrZero long fileSize,      // 必须 >= 0
    @Size(max = 128) String contentType,// 最大长度 128
    @NotBlank String md5Hash,           // 必填,非空字符串
    // ...
) {}
```

### 全局异常处理

通过 `@ControllerAdvice` 全局处理验证异常:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex) {
        // 提取验证错误信息
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();

        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse("参数验证失败", errors));
    }
}
```

## 依赖关系

### 上游依赖

- **patra-object-storage-app**: 应用层(用例编排器)
- **patra-object-storage-api**: API 契约层(端点接口、DTO)
- **patra-spring-boot-starter-web**: Patra Web Starter
- **spring-tx**: Spring 事务管理

### 下游消费者

- **patra-object-storage-boot**: 启动模块依赖本模块提供 REST 端点

## 使用示例

### Feign 客户端调用

其他服务通过 Feign 客户端调用本服务:

```java
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final StorageClient storageClient;

    public void recordUpload(String bucket, String key, long size, String md5) {
        // 1. 构造请求 DTO
        var request = new UploadRecordRequest(
            bucket,
            key,
            size,
            "application/pdf",
            md5,
            null,
            "patra-ingest",
            "literature_batch",
            "batch-001",
            Map.of("sourceId", "pubmed"),
            "MINIO",
            null,
            "Initial upload"
        );

        // 2. 调用 Feign 客户端(底层发送 HTTP POST 请求)
        RecordUploadResponse response = storageClient.recordUpload(request);

        // 3. 处理响应
        log.info("文件元数据已记录,ID: {}", response.metadataId());
    }
}
```

### 直接 HTTP 调用

也可以通过 curl 或 Postman 直接调用(用于测试):

```bash
curl -X POST http://localhost:8080/internal/storage/files/record \
  -H "Content-Type: application/json" \
  -d '{
    "bucketName": "test-bucket",
    "objectKey": "test/file.pdf",
    "fileSize": 1024000,
    "contentType": "application/pdf",
    "md5Hash": "5d41402abc4b2a76b9719d911017c592",
    "serviceName": "patra-ingest",
    "businessType": "literature_batch",
    "businessId": "batch-001",
    "correlationData": {"sourceId": "pubmed"},
    "providerType": "MINIO",
    "recordRemarks": "Test upload"
  }'
```

**响应示例**:
```json
{
  "metadataId": 123456,
  "recordedAt": "2024-01-15T10:30:45.123456Z"
}
```

## 异常处理

### 异常映射

适配器层负责将应用层异常映射为合适的 HTTP 状态码:

| 异常类型 | HTTP 状态码 | 说明 |
|---------|------------|------|
| `MethodArgumentNotValidException` | 400 Bad Request | 参数验证失败 |
| `DuplicateUploadException` | 409 Conflict | 重复上传(幂等性冲突) |
| `IllegalArgumentException` | 400 Bad Request | 非法参数 |
| `MetadataNotFoundException` | 404 Not Found | 元数据不存在 |
| `RuntimeException` | 500 Internal Server Error | 服务器内部错误 |

### 错误响应格式

统一的错误响应格式:

```json
{
  "timestamp": "2024-01-15T10:30:45.123456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "参数验证失败",
  "details": [
    "bucketName: 不能为空",
    "fileSize: 必须大于或等于 0"
  ],
  "path": "/internal/storage/files/record"
}
```

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Web | 6.2.5+ | REST 控制器支持 |
| Spring Boot | 3.5.7 | 自动配置 |
| Jakarta Validation | 3.1.0+ | Bean Validation 规范 |
| patra-object-storage-app | 0.1.0-SNAPSHOT | 应用层依赖 |
| patra-object-storage-api | 0.1.0-SNAPSHOT | API 契约层依赖 |

## 最佳实践

### 适配器层开发原则

1. **薄适配层**: 仅负责协议转换,不包含业务逻辑
2. **验证前置**: 在进入应用层之前完成所有参数验证
3. **异常转换**: 将领域/应用异常转换为 HTTP 状态码
4. **日志记录**: 记录请求参数和响应结果,便于排查问题
5. **安全防护**: 验证请求来源,防止恶意攻击

### REST 端点设计

1. **路径设计**: 使用 `/internal/` 前缀标识内部服务 API
2. **HTTP 方法**: POST 用于创建资源,GET 用于查询,PUT 用于更新
3. **请求体**: 使用 JSON 格式,便于跨语言集成
4. **响应体**: 包含必要信息,避免暴露内部实现细节

### 测试策略

1. **集成测试**: 使用 `@SpringBootTest` + `MockMvc` 测试完整流程
2. **Mock 应用层**: 使用 `@MockBean` Mock 应用层编排器
3. **验证测试**: 测试各种验证场景(必填项、格式校验、边界值)
4. **异常测试**: 测试各种异常场景和 HTTP 状态码

**测试示例**:
```java
@SpringBootTest
@AutoConfigureMockMvc
class StorageEndpointImplTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecordUploadOrchestrator orchestrator;

    @Test
    void recordUpload_shouldReturn200WhenValid() throws Exception {
        // Given
        RecordUploadResult result = new RecordUploadResult(123L, Instant.now());
        when(orchestrator.execute(any())).thenReturn(result);

        // When & Then
        mockMvc.perform(post("/internal/storage/files/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bucketName\":\"test\",\"objectKey\":\"key\",...}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadataId").value(123));
    }

    @Test
    void recordUpload_shouldReturn400WhenInvalid() throws Exception {
        // When & Then
        mockMvc.perform(post("/internal/storage/files/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bucketName\":\"\"}"))  // bucketName 为空
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("参数验证失败"));
    }
}
```

## 相关文档

- **API 契约**: 参见 `patra-object-storage-api/README.md` 了解端点定义和 DTO
- **应用层**: 参见 `patra-object-storage-app/README.md` 了解用例编排器
- **启动模块**: 参见 `patra-object-storage-boot/README.md` 了解服务配置
