/**
 * 对象存储框架核心包。
 *
 * <p>本包提供 Patra 项目的统一对象存储抽象层,支持多种对象存储提供商(MinIO、AWS S3)。它封装存储操作的复杂性,提供重试机制、指标收集和存储位置解析等开箱即用的功能。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义对象存储操作的统一接口 {@link ObjectStorageOperations}
 *   <li>提供基于策略模式的多提供商支持 {@link ObjectStorageProvider}
 *   <li>实现自动重试和可观测性增强的模板类 {@link ObjectStorageTemplate}
 *   <li>提供存储位置解析和路径生成策略 {@link StorageLocationResolver}
 *   <li>支持自动配置和多环境配置管理 {@link ObjectStorageAutoConfiguration}
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ObjectStorageOperations} - 对象存储操作接口,定义 upload 等方法
 *   <li>{@link ObjectStorageTemplate} - 默认实现,集成重试和指标
 *   <li>{@link ObjectStorageProvider} - 存储提供商策略接口
 *   <li>{@link MinioStorageProvider} - MinIO 提供商实现
 *   <li>{@link S3StorageProvider} - AWS S3 提供商实现
 *   <li>{@link StorageLocationResolver} - 存储位置解析器,生成规范化路径
 *   <li>{@link ObjectStorageAutoConfiguration} - 自动配置类
 *   <li>{@link ObjectStorageProperties} - 配置属性类
 * </ul>
 *
 * <h2>设计决策</h2>
 *
 * <ul>
 *   <li><b>策略模式:</b> 使用 {@code ObjectStorageProvider} 接口抽象不同提供商,支持运行时切换
 *   <li><b>模板方法:</b> {@code ObjectStorageTemplate} 封装重试、指标收集等横切关注点
 *   <li><b>重试策略:</b> 仅重试瞬时故障(网络错误、超时),不重试验证错误
 *   <li><b>指数退避:</b> 使用指数退避算法避免雪崩效应,基础延迟 100ms,最大延迟 30 秒
 *   <li><b>可观测性:</b> 集成 Micrometer,记录上传时长、成功率、重试次数等指标
 *   <li><b>路径规范化:</b> 统一路径格式 {@code {profile}/{service}/{generated-key}}
 * </ul>
 *
 * <h2>配置示例</h2>
 *
 * <p><b>MinIO 配置(默认):</b>
 *
 * <pre>
 * patra:
 *   object-storage:
 *     active-provider: minio  # 默认使用 MinIO
 *     max-file-size: 10485760  # 10MB
 *     retry:
 *       max-attempts: 3
 *       wait-duration: 1000
 *     providers:
 *       minio:
 *         endpoint: http://localhost:9000
 *         access-key: minioadmin
 *         secret-key: minioadmin
 * </pre>
 *
 * <p><b>AWS S3 配置:</b>
 *
 * <pre>
 * patra:
 *   object-storage:
 *     active-provider: s3
 *     max-file-size: 52428800  # 50MB
 *     retry:
 *       max-attempts: 5
 *       wait-duration: 500
 *     providers:
 *       s3:
 *         endpoint: https://s3.amazonaws.com
 *         region: us-east-1
 *         access-key: ${AWS_ACCESS_KEY}
 *         secret-key: ${AWS_SECRET_KEY}
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <p><b>基本上传操作:</b>
 *
 * <pre>{@code
 * @Autowired
 * private ObjectStorageOperations objectStorageOps;
 *
 * @Autowired
 * private StorageLocationResolver locationResolver;
 *
 * // 解析存储位置
 * StorageContext context = StorageContext.builder()
 *     .module("ingest")
 *     .category("publication")
 *     .originalFilename("paper.pdf")
 *     .build();
 * StorageLocation location = locationResolver.resolve(context);
 *
 * // 准备元数据
 * ObjectMetadata metadata = ObjectMetadata.builder()
 *     .contentLength(file.getSize())
 *     .contentType("application/pdf")
 *     .build();
 *
 * // 上传文件(自动重试网络错误)
 * UploadResult result = objectStorageOps.upload(
 *     location.getBucket(),
 *     location.getObjectKey(),
 *     inputStream,
 *     metadata
 * );
 *
 * // 获取存储键(用于保存到数据库)
 * String storageKey = result.getStorageKey(); // "prod/patra-ingest/2025/01/15/abc123.pdf"
 * }</pre>
 *
 * <p><b>自定义对象键生成器:</b>
 *
 * <pre>{@code
 * @Bean
 * public ObjectKeyGenerator customKeyGenerator() {
 *     return new ObjectKeyGenerator() {
 *         @Override
 *         public String generate(StorageContext context) {
 *             String ext = getExtension(context.getOriginalFilename());
 *             return String.format("%s/%s/%s.%s",
 *                 LocalDate.now().toString(),
 *                 context.getCategory(),
 *                 UUID.randomUUID(),
 *                 ext
 *             );
 *         }
 *     };
 * }
 * }</pre>
 *
 * <p><b>多提供商切换:</b>
 *
 * <pre>{@code
 * // 开发环境使用 MinIO
 * // application-dev.yml
 * patra:
 *   object-storage:
 *     active-provider: minio
 *
 * // 生产环境使用 AWS S3
 * // application-prod.yml
 * patra:
 *   object-storage:
 *     active-provider: s3
 * }</pre>
 *
 * <h2>重试机制</h2>
 *
 * <p><b>可重试异常(瞬时故障):</b>
 *
 * <ul>
 *   <li>{@code IOException} - 网络 I/O 错误
 *   <li>{@code SocketTimeoutException} - 读写超时
 *   <li>{@code ConnectException} - 连接被拒绝
 * </ul>
 *
 * <p><b>不可重试异常(永久故障):</b>
 *
 * <ul>
 *   <li>{@code InvalidUploadRequestException} - 参数验证失败
 *   <li>认证失败(access-key/secret-key 错误)
 *   <li>授权失败(bucket 权限不足)
 * </ul>
 *
 * <p><b>重试策略:</b>
 *
 * <pre>
 * 第 1 次重试: 等待 100ms
 * 第 2 次重试: 等待 200ms (100 * 2^1)
 * 第 3 次重试: 等待 400ms (100 * 2^2)
 * 最大延迟: 30 秒
 * </pre>
 *
 * <h2>指标收集</h2>
 *
 * <p><b>上传成功指标:</b>
 *
 * <pre>
 * 指标名称: object_storage_upload_success_total
 * 标签:
 *   - provider: minio/s3
 *   - bucket: 存储桶名称
 * 值: 成功次数
 * </pre>
 *
 * <p><b>上传失败指标:</b>
 *
 * <pre>
 * 指标名称: object_storage_upload_failure_total
 * 标签:
 *   - provider: minio/s3
 *   - bucket: 存储桶名称
 *   - error_type: validation/network/auth/unknown
 * 值: 失败次数
 * </pre>
 *
 * <p><b>上传时长指标:</b>
 *
 * <pre>
 * 指标名称: object_storage_upload_duration_seconds
 * 标签:
 *   - provider: minio/s3
 *   - bucket: 存储桶名称
 * 类型: Timer
 * 值: 上传耗时(秒)
 * </pre>
 *
 * <h2>存储位置规范</h2>
 *
 * <p><b>路径生成规则:</b>
 *
 * <pre>
 * 格式: {profile}/{service}/{module}/{generated-key}
 * 示例: prod/patra-ingest/literature/2025/01/15/abc123.pdf
 *
 * 组成部分:
 *   - profile: 环境标识(dev/test/prod)
 *   - service: 服务名称(从 spring.application.name 读取)
 *   - module: 业务模块(如 literature、user-profile)
 *   - generated-key: 生成的唯一键(默认使用日期 + UUID)
 * </pre>
 *
 * <p><b>Bucket 命名规范:</b>
 *
 * <pre>
 * 格式: patra-{environment}
 * 示例:
 *   - patra-dev (开发环境)
 *   - patra-test (测试环境)
 *   - patra-prod (生产环境)
 * </pre>
 *
 * <h2>异常处理</h2>
 *
 * <table border="1">
 *   <tr><th>异常类型</th><th>原因</th><th>重试</th><th>HTTP 状态</th></tr>
 *   <tr><td>InvalidUploadRequestException</td><td>参数验证失败</td><td>否</td><td>400 Bad Request</td></tr>
 *   <tr><td>UploadFailedException (网络错误)</td><td>网络 I/O 故障</td><td>是</td><td>503 Service Unavailable</td></tr>
 *   <tr><td>UploadFailedException (认证错误)</td><td>access-key 错误</td><td>否</td><td>500 Internal Error</td></tr>
 *   <tr><td>UploadFailedException (授权错误)</td><td>bucket 权限不足</td><td>否</td><td>500 Internal Error</td></tr>
 * </table>
 *
 * <h2>扩展指南</h2>
 *
 * <p><b>添加新的存储提供商:</b>
 *
 * <pre>{@code
 * // 1. 实现 ObjectStorageProvider 接口
 * public class AliOssStorageProvider implements ObjectStorageProvider {
 *     @Override
 *     public ProviderType getProviderType() {
 *         return ProviderType.ALIYUN_OSS;
 *     }
 *
 *     @Override
 *     public UploadResult upload(String bucket, String key,
 *                                InputStream inputStream,
 *                                ObjectMetadata metadata) {
 *         // 调用阿里云 OSS SDK
 *     }
 * }
 *
 * // 2. 添加自动配置
 * @Bean
 * @ConditionalOnProperty(name = "patra.object-storage.active-provider",
 *                        havingValue = "aliyun-oss")
 * public ObjectStorageProvider aliOssProvider() {
 *     return new AliOssStorageProvider();
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 *
 * <ul>
 *   <li><b>InputStream 复用:</b> 网络错误重试时,InputStream 必须支持 reset(),否则会失败
 *   <li><b>文件大小限制:</b> 通过 {@code max-file-size} 配置限制单文件大小,默认 10MB
 *   <li><b>并发上传:</b> {@code ObjectStorageTemplate} 是线程安全的,可并发调用
 *   <li><b>Bucket 预创建:</b> 确保 bucket 已在对象存储服务中创建,框架不会自动创建
 * </ul>
 *
 * <h2>相关模块</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.objectstorage.domain} - 领域模型(UploadResult、ObjectMetadata 等)
 *   <li>{@link com.patra.starter.objectstorage.metrics} - 指标收集器
 *   <li>{@link com.patra.common.storage.ObjectKeyGenerator} - 对象键生成器 SPI 接口
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.objectstorage;
