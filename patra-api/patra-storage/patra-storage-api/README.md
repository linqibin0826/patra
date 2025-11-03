# patra-storage-api — API 契约层

## 概述

**patra-storage-api** 是 patra-storage 服务的外部契约层,定义了供其他微服务调用的 Feign 客户端接口和数据传输对象(DTO)。

本模块在六边形架构中作为**输入端口**的声明,提供类型安全的 RPC 调用接口,确保服务间通信的稳定性和向后兼容性。所有需要与 patra-storage 服务集成的微服务都应依赖此模块。

## 核心职责

- **Feign 接口定义**: 声明 `StorageEndpoint` 接口,定义所有可调用的端点
- **Feign 客户端提供**: 提供 `StorageClient` 接口,继承 `StorageEndpoint` 并添加 `@FeignClient` 注解
- **DTO 定义**: 定义请求对象(`UploadRecordRequest`)和响应对象(`RecordUploadResponse`)
- **契约稳定性**: 作为服务间通信的稳定契约,避免破坏性变更
- **验证规则**: 通过 Jakarta Validation 注解确保请求数据的合法性

## 模块结构

```
patra-storage-api/
└── src/main/java/com/patra/storage/api/
    ├── endpoint/
    │   └── StorageEndpoint.java         # 端点接口定义
    ├── client/
    │   └── StorageClient.java           # Feign 客户端接口
    └── dto/
        ├── UploadRecordRequest.java     # 上传记录请求 DTO
        └── RecordUploadResponse.java    # 上传记录响应 DTO
```

## 主要组件

### StorageEndpoint

端点接口定义,声明所有可调用的 API 方法。

```java
public interface StorageEndpoint {
    String BASE_PATH = "/internal/storage";

    @PostMapping(value = BASE_PATH + "/files/record", consumes = MediaType.APPLICATION_JSON_VALUE)
    RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request);
}
```

**设计要点**:
- 使用 Spring Web 注解(`@PostMapping`),但 scope 为 `provided`,避免传递依赖
- 定义 `BASE_PATH` 常量,统一管理路径前缀
- 使用 `@Valid` 触发 Bean Validation 验证

### StorageClient

Feign 客户端接口,继承 `StorageEndpoint` 并添加服务发现配置。

```java
@FeignClient(name = "patra-storage", contextId = "storageClient")
public interface StorageClient extends StorageEndpoint {}
```

**设计要点**:
- `name = "patra-storage"`: 指向 Nacos 中的服务名
- `contextId = "storageClient"`: 避免多个 Feign Client Bean 冲突
- 继承 `StorageEndpoint`,实现接口复用和契约统一

### UploadRecordRequest

文件上传记录请求对象,包含所有必需和可选字段。

```java
public record UploadRecordRequest(
    @NotBlank String bucketName,
    @NotBlank String objectKey,
    @PositiveOrZero long fileSize,
    @Size(max = 128) String contentType,
    @NotBlank String md5Hash,
    String sha256Hash,
    @NotBlank String serviceName,
    @NotBlank String businessType,
    @NotBlank String businessId,
    Map<String, Object> correlationData,
    @NotBlank String providerType,
    Instant expiresAt,
    @Size(max = 512) String recordRemarks
) {
    // 规范化构造器,确保不可变性
    public UploadRecordRequest {
        correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
    }

    public String storageKey() {
        return bucketName + "/" + objectKey;
    }
}
```

**字段说明**:
- `bucketName`: 存储桶名称(必填)
- `objectKey`: 对象键(必填)
- `fileSize`: 文件大小,字节数(必填,>= 0)
- `contentType`: MIME 类型(可选,最大 128 字符)
- `md5Hash`: MD5 校验和(必填)
- `sha256Hash`: SHA-256 校验和(可选)
- `serviceName`: 调用服务名称(必填)
- `businessType`: 业务分类(必填)
- `businessId`: 业务标识(必填)
- `correlationData`: 关联元数据 JSON(可选)
- `providerType`: 存储提供商类型(必填,如 "MINIO")
- `expiresAt`: 过期时间(可选)
- `recordRemarks`: 审计备注(可选,最大 512 字符)

**设计模式**:
- 使用 Java 16+ **record** 类型,天然不可变
- 规范化构造器防御性复制 `correlationData`
- 提供 `storageKey()` 便捷方法生成完整存储键

### RecordUploadResponse

文件上传记录响应对象,返回元数据 ID 和记录时间。

```java
public record RecordUploadResponse(
    Long metadataId,
    Instant recordedAt
) {}
```

**字段说明**:
- `metadataId`: 生成的元数据记录 ID
- `recordedAt`: 记录时间戳(UTC)

## 依赖关系

### 上游依赖

- **patra-common-core**: 通用工具类和基础设施
- **jakarta.validation-api**: Bean Validation 注解支持
- **spring-web** (provided): Spring Web 注解支持
- **spring-cloud-openfeign-core** (provided): Feign 客户端注解支持

### 下游消费者

以下服务依赖本模块进行 Feign 调用:
- **patra-ingest**: 文献采集服务,上传文件后记录元数据
- **patra-registry**: 注册中心服务,管理数据源配置文件上传
- **其他需要对象存储的服务**: 任何需要记录文件上传的微服务

## 使用示例

### 在其他服务中集成

**1. 添加 Maven 依赖**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-storage-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

**2. 启用 Feign 客户端**:
```java
@SpringBootApplication
@EnableFeignClients(clients = StorageClient.class)
public class MyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyServiceApplication.class, args);
    }
}
```

**3. 注入并调用**:
```java
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private final StorageClient storageClient;

    public void recordDocumentUpload(String bucket, String key, long size, String md5) {
        var request = new UploadRecordRequest(
            bucket,
            key,
            size,
            "application/pdf",
            md5,
            null,  // sha256Hash
            "patra-ingest",
            "literature_batch",
            "batch-2024-01-15-001",
            Map.of("sourceId", "pubmed", "pmcId", "PMC12345678"),
            "MINIO",
            null,  // expiresAt
            "Initial upload"
        );

        RecordUploadResponse response = storageClient.recordUpload(request);
        log.info("文件元数据已记录,ID: {}, 时间: {}",
                 response.metadataId(), response.recordedAt());
    }
}
```

### Feign 配置

**application.yml**:
```yaml
feign:
  client:
    config:
      storageClient:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: basic
  compression:
    request:
      enabled: true
      mime-types: application/json
    response:
      enabled: true
```

## 版本兼容性

本模块遵循**语义化版本**原则:

- **主版本**:破坏性变更(如删除字段、修改接口签名)
- **次版本**:向后兼容的功能增加(如新增可选字段、新增端点)
- **补丁版本**:向后兼容的问题修复

**兼容性承诺**:
- 不会在次版本或补丁版本中删除现有字段
- 新增字段默认为可选(nullable)
- 弃用字段会先标记 `@Deprecated` 并保留至少一个主版本周期

## 最佳实践

### DTO 设计原则

1. **不可变性**: 使用 `record` 类型,所有字段 final
2. **防御性复制**: 对可变集合(如 `correlationData`)进行深拷贝
3. **验证注解**: 使用 Jakarta Validation 注解确保数据合法性
4. **文档完备**: 为每个字段添加 Javadoc,说明用途和约束

### Feign 客户端最佳实践

1. **指定 contextId**: 避免 Bean 名称冲突
2. **配置超时**: 根据业务场景设置合理的连接和读取超时
3. **启用压缩**: 对大 JSON 响应启用 Gzip 压缩
4. **日志级别**: 生产环境使用 `BASIC` 或 `NONE`,开发环境使用 `FULL`

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Jakarta Validation | 3.1.0+ | Bean Validation 规范 |
| Spring Web | 6.2.5+ | Spring Web 注解支持 |
| Spring Cloud OpenFeign | 4.2.5+ | Feign 客户端框架 |
| patra-common-core | 0.1.0-SNAPSHOT | Papertrace 通用基础库 |

## 相关文档

- **服务实现**: 参见 `patra-storage-adapter/README.md` 了解端点实现细节
- **数据模型**: 参见 `patra-storage-domain/README.md` 了解领域模型设计
- **集成示例**: 参见 `patra-ingest` 服务中的实际集成案例
