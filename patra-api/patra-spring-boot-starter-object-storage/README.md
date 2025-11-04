# patra-spring-boot-starter-object-storage

## 概述

对象存储抽象 Starter,提供对 MinIO/S3 对象存储的统一访问接口,集成重试机制、指标收集和自动 Bucket 管理。

本 Starter 为文件存储场景提供开箱即用的对象存储能力,支持多存储提供商无缝切换(MinIO、AWS S3)。

## 核心功能

- **统一存储抽象**: ObjectStorageOperations 接口,屏蔽底层存储提供商差异
- **自动重试机制**: 基于 Spring Retry,自动重试网络临时故障(指数退避)
- **指标收集**: 集成 Micrometer,记录上传成功/失败/重试次数/耗时等指标
- **存储位置解析**: 自动生成分区化的 Bucket 和 Object Key(支持自定义策略)
- **文件大小限制**: 防止上传过大文件导致 OOM(默认 100MB)
- **自动 Bucket 管理**: 自动创建不存在的 Bucket,并缓存已知 Bucket 以减少网络调用
- **参数验证**: 严格验证 Bucket 名称、Object Key 格式和文件大小

## 自动配置内容

### ObjectStorageAutoConfiguration
提供核心对象存储配置:
- `ObjectStorageTemplate`: 对象存储操作模板(包装 Provider,添加重试和指标)
- `ObjectStorageMetrics`: 指标收集器(上传成功/失败/重试)
- `RetryTemplate`: 重试模板(指数退避,最多 3 次)
- `MinioClient`: MinIO 客户端(默认提供商)
- `MinioStorageProvider`: MinIO 存储提供商实现
- `StorageLocationResolver`: 存储位置解析器(Bucket + Object Key 生成)

### S3StorageAutoConfiguration (可选)
当 AWS SDK 在 classpath 时激活:
- `S3Client`: AWS S3 客户端
- `S3StorageProvider`: AWS S3 存储提供商实现

## 主要组件

### ObjectStorageOperations (统一接口)
对象存储操作的统一抽象:
```java
public interface ObjectStorageOperations {
    /**
     * 上传文件到对象存储
     *
     * @param bucket bucket 名称
     * @param key 对象键(唯一标识)
     * @param inputStream 文件内容流
     * @param metadata 文件元数据(大小、Content-Type)
     * @return 上传结果(storageKey、etag、fileSize)
     */
    UploadResult upload(String bucket, String key,
                       InputStream inputStream, ObjectMetadata metadata);
}
```

**注意**: 当前版本(Phase 1)仅支持上传操作。下载、删除、存在性检查等操作计划在 Phase 2 实现。

### ObjectStorageTemplate (重试和指标包装器)
为存储操作添加横切关注点:
- **重试逻辑**: 自动重试网络临时故障(IOException、SocketTimeoutException、ConnectException)
- **指标记录**: 记录上传成功/失败/重试次数/耗时/文件大小
- **错误分类**: 将异常分类为 validation、network、auth、unknown,用于指标标签

### StorageLocationResolver (存储位置解析器)
自动解析 Bucket 和 Object Key:
- **Bucket 命名**: `{environment}-{service}` (例如: `dev-patra-ingest`)
- **Object Key 生成**: 使用可插拔的 `ObjectKeyGenerator` 策略
  - 默认策略: `DatePartitionedKeyGenerator` (按日期分区)
  - 格式: `{service}/{businessType}/{yyyy/MM/dd}/{businessId}-{uuid}.{ext}`
  - 示例: `patra-ingest/plan/2025/01/12/plan-123-a1b2c3d4.json`

### MinioStorageProvider (MinIO 实现)
MinIO 存储提供商实现,提供以下特性:
- **参数验证**: 严格验证 Bucket 名称(3-63 字符、小写字母/数字/点/短横线)
- **Object Key 验证**: 最大 1024 字符,不能以 `/` 开头,不能包含 `//`
- **文件大小限制**: 默认 100MB,可配置
- **Bucket 缓存**: 本地缓存已知 Bucket,避免重复的 `bucketExists` 调用
- **自动创建 Bucket**: 如果 Bucket 不存在,自动创建

## 配置属性

**配置前缀**: `patra.object-storage`

```yaml
patra:
  object-storage:
    active-provider: minio                        # 活动存储提供商: minio | s3
    max-file-size: 104857600                      # 最大文件大小(字节,默认 100MB)
    retry:
      max-attempts: 3                             # 最大重试次数
      wait-duration: 1000                         # 重试等待时间(毫秒)
    providers:
      minio:
        endpoint: http://localhost:9000           # MinIO 端点
        access-key: minioadmin                    # 访问密钥
        secret-key: minioadmin                    # 密钥
        bucket: default-bucket                    # 默认 Bucket(可选)
      s3:
        region: us-east-1                         # AWS S3 区域
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}
        bucket: my-s3-bucket
```

## 使用方式

### Maven 依赖
```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-object-storage</artifactId>
</dependency>
```

**传递依赖**(自动包含):
- `patra-common-storage`: 存储领域模型和工具
- `minio`: MinIO Java SDK (8.5.7)
- `spring-retry`: Spring 重试机制
- `micrometer-core`: 指标收集
- `aws-s3-sdk`: AWS S3 SDK(可选,仅当使用 S3 时)

### 配置示例

**application.yml**:
```yaml
spring:
  application:
    name: patra-ingest
  profiles:
    active: dev

patra:
  object-storage:
    active-provider: minio
    max-file-size: 104857600  # 100MB
    retry:
      max-attempts: 3
      wait-duration: 1000
    providers:
      minio:
        endpoint: http://localhost:9000
        access-key: minioadmin
        secret-key: minioadmin
```

### 代码示例

**文件上传服务**:
```java
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private final ObjectStorageOperations storageOps;
    private final StorageLocationResolver locationResolver;

    public UploadResult uploadDocument(InputStream fileStream, String filename,
                                      String businessId, long fileSize) {
        // 1. 解析存储位置(Bucket + Object Key)
        StorageContext context = StorageContext.builder()
            .filename(filename)
            .businessType("document")
            .businessId(businessId)
            .date(LocalDate.now())
            .build();

        StorageLocation location = locationResolver.resolve(context);

        // 2. 准备元数据
        ObjectMetadata metadata = ObjectMetadata.builder()
            .contentLength(fileSize)
            .contentType("application/pdf")
            .build();

        // 3. 上传文件(自动重试、指标记录)
        UploadResult result = storageOps.upload(
            location.getBucket(),
            location.getObjectKey(),
            fileStream,
            metadata
        );

        log.info("文件上传成功: storageKey={}, etag={}, size={} bytes",
                 result.getStorageKey(), result.getEtag(), result.getFileSize());

        return result;
    }
}
```

**REST 控制器示例**:
```java
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService storageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("businessId") String businessId
    ) throws IOException {
        UploadResult result = storageService.uploadDocument(
            file.getInputStream(),
            file.getOriginalFilename(),
            businessId,
            file.getSize()
        );

        return ApiResponse.ok(result);
    }
}
```

**自定义 Object Key 生成策略**:
```java
@Component
public class MyCustomKeyGenerator implements ObjectKeyGenerator {
    @Override
    public String generate(ObjectKeyContext context) {
        // 自定义 Key 格式: {service}/{businessType}/{businessId}/{uuid}.{ext}
        return String.format("%s/%s/%s/%s%s",
            context.getServiceName(),
            context.getBusinessType(),
            context.getBusinessId(),
            UUID.randomUUID(),
            context.getExtension()
        );
    }
}
```

## 指标监控

**Micrometer 指标**:
- `object_storage_upload_success_total`: 上传成功总数(按 provider、bucket 分组)
- `object_storage_upload_failure_total`: 上传失败总数(按 provider、bucket、errorType 分组)
- `object_storage_upload_duration_seconds`: 上传耗时(分布汇总)
- `object_storage_upload_size_bytes`: 上传文件大小(分布汇总)
- `object_storage_retry_total`: 重试总次数(按 provider、bucket 分组)

**Prometheus 查询示例**:
```promql
# 上传成功率
rate(object_storage_upload_success_total[5m])
  / (rate(object_storage_upload_success_total[5m]) + rate(object_storage_upload_failure_total[5m]))

# P95 上传耗时
histogram_quantile(0.95, rate(object_storage_upload_duration_seconds_bucket[5m]))

# 重试率
rate(object_storage_retry_total[5m])
```

## 错误处理

**异常类型**:
- `InvalidUploadRequestException`: 参数验证失败(不可重试)
  - Bucket 名称格式不合法
  - Object Key 格式不合法
  - 文件大小超限
- `UploadFailedException`: 上传失败(可重试)
  - 网络临时故障
  - 连接超时
  - MinIO/S3 服务端错误

**重试策略**:
- 仅重试网络临时故障(IOException、SocketTimeoutException、ConnectException)
- 指数退避: 初始 1s,倍增因子 2.0,最大 30s
- 最大重试次数: 3(可配置)

## 技术栈

- **MinIO**: 8.5.7
- **Spring Retry**: 2.0.10
- **Micrometer**: 1.15.1
- **AWS S3 SDK**: 2.29.31(可选)

---

**最后更新**: 2025-01-12
**维护者**: Patra Team
