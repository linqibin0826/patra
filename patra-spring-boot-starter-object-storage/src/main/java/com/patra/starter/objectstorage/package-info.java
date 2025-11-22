/// 对象存储框架核心包。
///
/// 本包提供 Patra 项目的统一对象存储抽象层,支持多种对象存储提供商(MinIO、AWS S3)。它封装存储操作的复杂性,提供重试机制、指标收集和存储位置解析等开箱即用的功能。
///
/// ## 职责
///
/// - 定义对象存储操作的统一接口 {@link ObjectStorageOperations}
///   - 提供基于策略模式的多提供商支持 {@link ObjectStorageProvider}
///   - 实现自动重试和可观测性增强的模板类 {@link ObjectStorageTemplate}
///   - 提供存储位置解析和路径生成策略 {@link StorageLocationResolver}
///   - 支持自动配置和多环境配置管理 {@link ObjectStorageAutoConfiguration}
///
/// ## 核心组件
///
/// - {@link ObjectStorageOperations} - 对象存储操作接口,定义 upload 等方法
///   - {@link ObjectStorageTemplate} - 默认实现,集成重试和指标
///   - {@link ObjectStorageProvider} - 存储提供商策略接口
///   - {@link MinioStorageProvider} - MinIO 提供商实现
///   - {@link S3StorageProvider} - AWS S3 提供商实现
///   - {@link StorageLocationResolver} - 存储位置解析器,生成规范化路径
///   - {@link ObjectStorageAutoConfiguration} - 自动配置类
///   - {@link ObjectStorageProperties} - 配置属性类
///
/// ## 设计决策
///
/// - **策略模式:** 使用 `ObjectStorageProvider` 接口抽象不同提供商,支持运行时切换
///   - **模板方法:** `ObjectStorageTemplate` 封装重试、指标收集等横切关注点
///   - **重试策略:** 仅重试瞬时故障(网络错误、超时),不重试验证错误
///   - **指数退避:** 使用指数退避算法避免雪崩效应,基础延迟 100ms,最大延迟 30 秒
///   - **可观测性:** 集成 Micrometer,记录上传时长、成功率、重试次数等指标
///   - **路径规范化:** 统一路径格式 `{profile`/{service}/{generated-key}}
///
/// ## 配置示例
///
/// **MinIO 配置(默认):**
///
/// ```
///
/// patra:
///   object-storage:
///     active-provider: minio  # 默认使用 MinIO
///     max-file-size: 10485760  # 10MB
///     retry:
///       max-attempts: 3
///       wait-duration: 1000
///     providers:
///       minio:
///         endpoint: http://localhost:9000
///         access-key: minioadmin
///         secret-key: minioadmin
///
/// ```
///
/// **AWS S3 配置:**
///
/// ```
///
/// patra:
///   object-storage:
///     active-provider: s3
///     max-file-size: 52428800  # 50MB
///     retry:
///       max-attempts: 5
///       wait-duration: 500
///     providers:
///       s3:
///         endpoint: https://s3.amazonaws.com
///         region: us-east-1
///         access-key: ${AWS_ACCESS_KEY}
///         secret-key: ${AWS_SECRET_KEY}
///
/// ```
///
/// ## 使用示例
///
/// **基本上传操作:**
///
/// ```java
/// @Autowired
/// private ObjectStorageOperations objectStorageOps;
///
/// @Autowired
/// private StorageLocationResolver locationResolver;
///
/// // 解析存储位置
/// StorageContext context = StorageContext.builder()
///     .module("ingest")
///     .category("publication")
///     .originalFilename("paper.pdf")
///     .build();
/// StorageLocation location = locationResolver.resolve(context);
///
/// // 准备元数据
/// ObjectMetadata metadata = ObjectMetadata.builder()
///     .contentLength(file.getSize())
///     .contentType("application/pdf")
///     .build();
///
/// // 上传文件(自动重试网络错误)
/// UploadResult result = objectStorageOps.upload(
///     location.getBucket(),
///     location.getObjectKey(),
///     inputStream,
///     metadata
/// );
///
/// // 获取存储键(用于保存到数据库)
/// String storageKey = result.getStorageKey(); // "prod/patra-ingest/2025/01/15/abc123.pdf"
/// ```
///
/// **自定义对象键生成器:**
///
/// ```java
/// @Bean
/// public ObjectKeyGenerator customKeyGenerator() {
///     return new ObjectKeyGenerator() {
///         @Override
///         public String generate(StorageContext context) {
///             String ext = getExtension(context.getOriginalFilename());
///             return String.format("%s/%s/%s.%s",
///                 LocalDate.now().toString(),
///                 context.getCategory(),
///                 UUID.randomUUID(),
///                 ext
///             );;
/// ```
///
/// **多提供商切换:**
///
/// ```java
/// // 开发环境使用 MinIO
/// // application-dev.yml
/// patra:
///   object-storage:
///     active-provider: minio
///
/// // 生产环境使用 AWS S3
/// // application-prod.yml
/// patra:
///   object-storage:
///     active-provider: s3
/// ```
///
/// ## 重试机制
///
/// **可重试异常(瞬时故障):**
///
/// - `IOException` - 网络 I/O 错误
///   - `SocketTimeoutException` - 读写超时
///   - `ConnectException` - 连接被拒绝
///
/// **不可重试异常(永久故障):**
///
/// - `InvalidUploadRequestException` - 参数验证失败
///   - 认证失败(access-key/secret-key 错误)
///   - 授权失败(bucket 权限不足)
///
/// **重试策略:**
///
/// ```
///
/// 第 1 次重试: 等待 100ms
/// 第 2 次重试: 等待 200ms (100 * 2^1)
/// 第 3 次重试: 等待 400ms (100 * 2^2)
/// 最大延迟: 30 秒
///
/// ```
///
/// ## 指标收集
///
/// **上传成功指标:**
///
/// ```
///
/// 指标名称: object_storage_upload_success_total
/// 标签:
///   - provider: minio/s3
///   - bucket: 存储桶名称
/// 值: 成功次数
///
/// ```
///
/// **上传失败指标:**
///
/// ```
///
/// 指标名称: object_storage_upload_failure_total
/// 标签:
///   - provider: minio/s3
///   - bucket: 存储桶名称
///   - error_type: validation/network/auth/unknown
/// 值: 失败次数
///
/// ```
///
/// **上传时长指标:**
///
/// ```
///
/// 指标名称: object_storage_upload_duration_seconds
/// 标签:
///   - provider: minio/s3
///   - bucket: 存储桶名称
/// 类型: Timer
/// 值: 上传耗时(秒)
///
/// ```
///
/// ## 存储位置规范
///
/// **路径生成规则:**
///
/// ```
///
/// 格式: {profile}/{service}/{module}/{generated-key}
/// 示例: prod/patra-ingest/publication/2025/01/15/abc123.pdf
///
/// 组成部分:
///   - profile: 环境标识(dev/test/prod)
///   - service: 服务名称(从 spring.application.name 读取)
///   - module: 业务模块(如 publication、user-profile)
///   - generated-key: 生成的唯一键(默认使用日期 + UUID)
///
/// ```
///
/// **Bucket 命名规范:**
///
/// ```
///
/// 格式: patra-{environment}
/// 示例:
///   - patra-dev (开发环境)
///   - patra-test (测试环境)
///   - patra-prod (生产环境)
///
/// ```
///
/// ## 异常处理
///
/// <table border="1">
///   <tr><th>异常类型</th><th>原因</th><th>重试</th><th>HTTP 状态</th></tr>
///   <tr><td>InvalidUploadRequestException</td><td>参数验证失败</td><td>否</td><td>400 Bad
// Request</td></tr>
///   <tr><td>UploadFailedException (网络错误)</td><td>网络 I/O 故障</td><td>是</td><td>503 Service
// Unavailable</td></tr>
///   <tr><td>UploadFailedException (认证错误)</td><td>access-key 错误</td><td>否</td><td>500 Internal
// Error</td></tr>
///   <tr><td>UploadFailedException (授权错误)</td><td>bucket 权限不足</td><td>否</td><td>500 Internal
// Error</td></tr>
/// </table>
///
/// ## 扩展指南
///
/// **添加新的存储提供商:**
///
/// ```java
/// // 1. 实现 ObjectStorageProvider 接口
/// public class AliOssStorageProvider implements ObjectStorageProvider {
///     @Override
///     public ProviderType getProviderType() {
///         return ProviderType.ALIYUN_OSS;
///
///     @Override
///     public UploadResult upload(String bucket, String key,
///                                InputStream inputStream,
///                                ObjectMetadata metadata) {
///         // 调用阿里云 OSS SDK
///
/// // 2. 添加自动配置
/// @Bean
/// @ConditionalOnProperty(name = "patra.object-storage.active-provider",
///                        havingValue = "aliyun-oss")
/// public ObjectStorageProvider aliOssProvider() {
///     return new AliOssStorageProvider();
/// ```
///
/// ## 注意事项
///
/// - **InputStream 复用:** 网络错误重试时,InputStream 必须支持 reset(),否则会失败
///   - **文件大小限制:** 通过 `max-file-size` 配置限制单文件大小,默认 10MB
///   - **并发上传:** `ObjectStorageTemplate` 是线程安全的,可并发调用
///   - **Bucket 预创建:** 确保 bucket 已在对象存储服务中创建,框架不会自动创建
///
/// ## 相关模块
///
/// - {@link com.patra.starter.objectstorage.domain} - 领域模型(UploadResult、ObjectMetadata 等)
///   - {@link com.patra.starter.objectstorage.metrics} - 指标收集器
///   - {@link com.patra.common.storage.ObjectKeyGenerator} - 对象键生成器 SPI 接口
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.objectstorage;
