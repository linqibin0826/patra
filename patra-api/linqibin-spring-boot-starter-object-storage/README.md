# patra-spring-boot-starter-object-storage

## 概述

对象存储抽象 Starter,提供对 MinIO/S3 对象存储的统一访问接口,集成重试机制、指标收集和自动 Bucket 管理。

本 Starter 为文件存储场景提供开箱即用的对象存储能力,支持多存储提供商无缝切换(MinIO、AWS S3)。

## 核心功能

### 存储操作
- **文件上传**: 支持流式上传,自动重试网络临时故障
- **文件下载**: 支持流式下载和下载到本地文件
- **元数据查询**: 获取对象大小、类型、ETag、最后修改时间
- **存在性检查**: 快速检查对象是否存在

### 基础设施
- **统一存储抽象**: ObjectStorageOperations 接口,屏蔽底层存储提供商差异
- **模板方法模式**: AbstractObjectStorageProvider 封装共享验证逻辑
- **自动重试机制**: 基于 Spring Retry,自动重试网络临时故障(指数退避)
- **指标收集**: 集成 Micrometer,记录上传/下载成功/失败/重试次数/耗时等指标
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
    /// 上传文件到对象存储
    UploadResult upload(String bucket, String key,
                       InputStream inputStream, ObjectMetadata metadata);

    /// 流式下载对象(调用者必须关闭流)
    DownloadResult download(String bucket, String key);

    /// 下载对象到本地文件(自动管理资源)
    Path downloadToFile(String bucket, String key, Path targetPath);

    /// 获取对象元数据(HEAD 请求,不下载内容)
    Optional<ObjectInfo> statObject(String bucket, String key);

    /// 检查对象是否存在(基于 statObject 的便捷方法)
    default boolean exists(String bucket, String key) {
        return statObject(bucket, key).isPresent();
    }
}
```

### AbstractObjectStorageProvider (抽象基类)
使用模板方法模式封装 MinIO/S3 共享的验证逻辑:
- **Bucket 命名验证**: 3-63 字符,仅小写字母/数字/点/短横线,必须以字母或数字开头结尾
- **Object Key 验证**: 最大 1024 字符,不能以 `/` 开头,上传时不能包含 `//`
- **文件大小限制**: 上传前严格验证 `contentLength`(默认 100MB)

子类(MinioStorageProvider、S3StorageProvider)只需实现具体的存储操作。

### Domain 对象

#### DownloadResult (下载结果)
封装下载操作的结果,实现 `Closeable` 接口:
```java
@Getter
@Builder
public class DownloadResult implements Closeable {
    private final InputStream content;     // 对象内容流
    private final String bucketName;       // 存储桶名称
    private final String objectKey;        // 对象键
    private final long contentLength;      // 内容长度(字节)
    private final String contentType;      // MIME 类型
    private final String etag;             // ETag 标识
}
```

**重要**: 调用者必须使用 try-with-resources 关闭流:
```java
try (DownloadResult result = objectStorage.download("bucket", "key")) {
    // 处理 result.content()
}
```

#### ObjectInfo (对象元数据)
封装对象元数据信息(HEAD 请求返回):
```java
@Getter
@Builder
public class ObjectInfo {
    private final String bucketName;       // 存储桶名称
    private final String objectKey;        // 对象键
    private final long contentLength;      // 内容长度(字节)
    private final String contentType;      // MIME 类型
    private final String etag;             // ETag 标识
    private final Instant lastModified;    // 最后修改时间
}
```

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

**配置前缀**: `linqibin.starter.object-storage`

```yaml
linqibin:
  starter:
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

### Gradle 依赖

```kotlin
implementation(project(":patra-spring-boot-starter-object-storage"))
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

linqibin:
  starter:
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

**文件下载服务**:
```java
@Service
@RequiredArgsConstructor
public class DocumentDownloadService {

    private final ObjectStorageOperations storageOps;

    /// 流式下载(适用于大文件或流式处理场景)
    public void downloadToOutputStream(String bucket, String key,
                                       OutputStream outputStream) throws IOException {
        // 使用 try-with-resources 自动关闭流
        try (DownloadResult result = storageOps.download(bucket, key)) {
            result.getContent().transferTo(outputStream);
            log.info("下载完成: bucket={}, key={}, size={} bytes",
                     bucket, key, result.getContentLength());
        }
    }

    /// 下载到本地文件(适用于需要本地处理的场景)
    public Path downloadToLocal(String bucket, String key, Path targetDir) {
        Path targetPath = targetDir.resolve(Paths.get(key).getFileName());
        Path result = storageOps.downloadToFile(bucket, key, targetPath);
        log.info("下载到本地: {}", result);
        return result;
    }

    /// 检查对象是否存在
    public boolean objectExists(String bucket, String key) {
        return storageOps.exists(bucket, key);
    }

    /// 获取对象元数据
    public Optional<ObjectInfo> getObjectInfo(String bucket, String key) {
        return storageOps.statObject(bucket, key);
    }
}

## 指标监控

**Micrometer 指标**:

### 上传指标
- `object_storage_upload_success_total`: 上传成功总数(按 provider、bucket 分组)
- `object_storage_upload_failure_total`: 上传失败总数(按 provider、bucket、errorType 分组)
- `object_storage_upload_duration_seconds`: 上传耗时(分布汇总)
- `object_storage_upload_size_bytes`: 上传文件大小(分布汇总)

### 下载指标
- `object_storage_download_total`: 下载总数(按 provider、bucket、status、error_type 分组)
- `object_storage_download_duration`: 下载耗时(Timer,含 P50/P90/P99 百分位)
- `object_storage_download_size`: 下载文件大小(DistributionSummary)

### 通用指标
- `object_storage_retry_total`: 重试总次数(按 provider、bucket 分组)

**错误类型标签值**:
- `validation`: 参数验证失败
- `not_found`: 对象不存在
- `network`: 网络错误
- `auth`: 认证/授权失败
- `unknown`: 其他未知错误

**Prometheus 查询示例**:
```promql
# 上传成功率
rate(object_storage_upload_success_total[5m])
  / (rate(object_storage_upload_success_total[5m]) + rate(object_storage_upload_failure_total[5m]))

# P95 上传耗时
histogram_quantile(0.95, rate(object_storage_upload_duration_seconds_bucket[5m]))

# 下载成功率
sum(rate(object_storage_download_total{status="success"}[5m]))
  / sum(rate(object_storage_download_total[5m]))

# P95 下载耗时
histogram_quantile(0.95, rate(object_storage_download_duration_bucket[5m]))

# 对象不存在错误率
rate(object_storage_download_total{error_type="not_found"}[5m])

# 重试率
rate(object_storage_retry_total[5m])
```

## 错误处理

### 上传异常
- `InvalidUploadRequestException`: 参数验证失败(不可重试)
  - Bucket 名称格式不合法
  - Object Key 格式不合法
  - 文件大小超限
- `UploadFailedException`: 上传失败(可重试)
  - 网络临时故障
  - 连接超时
  - MinIO/S3 服务端错误

### 下载异常
- `InvalidDownloadRequestException`: 参数验证失败(不可重试)
  - Bucket 名称格式不合法
  - Object Key 格式不合法
- `ObjectNotFoundException`: 对象不存在(不可重试)
  - 指定的 Bucket/Key 不存在
  - 返回 404 状态码
- `DownloadFailedException`: 下载失败(可重试)
  - 网络临时故障
  - 连接超时
  - MinIO/S3 服务端错误

### 异常层次结构
```
RuntimeException
├── InvalidUploadRequestException    (上传参数验证失败)
├── UploadFailedException            (上传操作失败)
└── DownloadFailedException          (下载操作失败)
    ├── InvalidDownloadRequestException  (下载参数验证失败)
    └── ObjectNotFoundException          (对象不存在)
```

### 重试策略
- 仅重试网络临时故障(IOException、SocketTimeoutException、ConnectException)
- 指数退避: 初始 1s,倍增因子 2.0,最大 30s
- 最大重试次数: 3(可配置)
- **不可重试异常**: `InvalidUploadRequestException`、`InvalidDownloadRequestException`、`ObjectNotFoundException`

## 技术栈

- **MinIO**: 8.5.7
- **Spring Retry**: 2.0.10
- **Micrometer**: 1.15.1
- **AWS S3 SDK**: 2.29.31(可选)

---

**最后更新**: 2026-01-14
**维护者**: Patra Team
