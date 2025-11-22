/// 对象存储指标收集包。
///
/// 本包提供基于 Micrometer 的对象存储操作指标收集功能,支持 Prometheus、Grafana 等监控系统,帮助运维团队实时监控存储服务的健康状态和性能表现。
///
/// ## 职责
///
/// - 记录对象存储操作的成功率、失败率和响应时间
///   - 区分不同提供商(MinIO、S3)和存储桶的指标
///   - 分类错误类型(validation、network、auth、unknown)
///   - 统计重试次数,识别不稳定的网络环境
///   - 集成 Spring Boot Actuator,暴露 Prometheus 端点
///
/// ## 核心组件
///
/// - {@link com.patra.starter.objectstorage.metrics.ObjectStorageMetrics} - 指标收集器
///
/// ## 设计决策
///
/// - **多维标签:** 使用 provider、bucket、error_type 等标签支持细粒度查询
///   - **指标类型:** 使用 Counter(计数)、Timer(时长)、Gauge(瞬时值)等多种类型
///   - **命名规范:** 遵循 Prometheus 命名约定 `object_storage_*`
///   - **可选依赖:** 使用 `ObjectProvider` 支持无 Micrometer 依赖的环境
///
/// ## 收集的指标
///
/// ### 上传成功计数
///
/// ```
///
/// 名称: object_storage_upload_success_total
/// 类型: Counter
/// 标签:
///   - provider: minio / s3
///   - bucket: 存储桶名称
/// 说明: 记录上传成功的总次数
///
/// ```
///
/// ### 上传失败计数
///
/// ```
///
/// 名称: object_storage_upload_failure_total
/// 类型: Counter
/// 标签:
///   - provider: minio / s3
///   - bucket: 存储桶名称
///   - error_type: validation / network / auth / unknown
/// 说明: 记录上传失败的总次数,按错误类型分类
///
/// ```
///
/// ### 上传时长
///
/// ```
///
/// 名称: object_storage_upload_duration_seconds
/// 类型: Timer
/// 标签:
///   - provider: minio / s3
///   - bucket: 存储桶名称
/// 说明: 记录上传操作的耗时(秒)
/// 百分位: p50, p95, p99
///
/// ```
///
/// ### 上传文件大小
///
/// ```
///
/// 名称: object_storage_upload_size_bytes
/// 类型: Summary
/// 标签:
///   - provider: minio / s3
///   - bucket: 存储桶名称
/// 说明: 记录上传文件的大小分布(字节)
///
/// ```
///
/// ### 重试次数
///
/// ```
///
/// 名称: object_storage_retry_total
/// 类型: Counter
/// 标签:
///   - provider: minio / s3
///   - bucket: 存储桶名称
/// 说明: 记录重试操作的总次数
///
/// ```
///
/// ## 错误类型分类
///
/// <table border="1">
///   <tr><th>错误类型</th><th>说明</th><th>示例异常</th></tr>
///   <tr><td>validation</td><td>参数验证失败</td><td>InvalidUploadRequestException</td></tr>
///   <tr><td>network</td><td>网络错误</td><td>IOException, SocketTimeoutException</td></tr>
///   <tr><td>auth</td><td>认证/授权失败</td><td>异常消息包含 "auth" 或 "credential"</td></tr>
///   <tr><td>unknown</td><td>未知错误</td><td>其他未分类异常</td></tr>
/// </table>
///
/// ## Prometheus 查询示例
///
/// **上传成功率(最近 5 分钟):**
///
/// ```
///
/// sum(rate(object_storage_upload_success_total[5m]))
/// /
/// (
///   sum(rate(object_storage_upload_success_total[5m]))
///   + sum(rate(object_storage_upload_failure_total[5m]))
/// )
///
/// ```
///
/// **按提供商分组的上传失败率:**
///
/// ```
///
/// sum by (provider) (rate(object_storage_upload_failure_total[5m]))
///
/// ```
///
/// **按错误类型分组的失败分布:**
///
/// ```
///
/// sum by (error_type) (rate(object_storage_upload_failure_total[5m]))
///
/// ```
///
/// **上传操作 P95 延迟:**
///
/// ```
///
/// histogram_quantile(0.95,
///   sum(rate(object_storage_upload_duration_seconds_bucket[5m])) by (le, provider, bucket)
/// )
///
/// ```
///
/// **重试率(最近 5 分钟):**
///
/// ```
///
/// sum(rate(object_storage_retry_total[5m]))
/// /
/// (
///   sum(rate(object_storage_upload_success_total[5m]))
///   + sum(rate(object_storage_upload_failure_total[5m]))
/// )
///
/// ```
///
/// ## Grafana 面板配置
///
/// **上传成功率面板:**
///
/// ```
///
/// 面板类型: Graph
/// 单位: percent (0-100)
/// 查询: 上传成功率(最近 5 分钟)
/// 阈值:
///   - 绿色: > 99%
///   - 黄色: 95% - 99%
///   - 红色: < 95%
///
/// ```
///
/// **上传延迟分位数面板:**
///
/// ```
///
/// 面板类型: Graph
/// 单位: seconds
/// 查询:
///   - P50 延迟
///   - P95 延迟
///   - P99 延迟
/// 图例: {{provider}} - {{bucket}} - p{{quantile}}
///
/// ```
///
/// **错误类型分布面板:**
///
/// ```
///
/// 面板类型: Pie Chart
/// 查询: sum by (error_type) (rate(object_storage_upload_failure_total[5m]))
/// 图例: {{error_type}}
///
/// ```
///
/// ## 使用示例
///
/// **在模板中记录指标:**
///
/// ```java
/// @Autowired
/// private ObjectStorageMetrics metrics;
///
/// public UploadResult upload(String bucket, String key, InputStream inputStream,
///                            ObjectMetadata metadata) {
///     long start = System.nanoTime();
///     try {
///         UploadResult result = provider.upload(bucket, key, inputStream, metadata);
///
///         // 记录成功指标
///         metrics.recordUploadSuccess(
///             provider.getProviderType(),
///             bucket,
///             System.nanoTime() - start,
///             result.getFileSize()
///         );
///
///         return result; catch (Exception ex) {
///         // 分类错误类型
///         String errorType = classifyError(ex);
///
///         // 记录失败指标
///         metrics.recordUploadFailure(
///             provider.getProviderType(),
///             bucket,
///             errorType
///         );
///
///         throw ex;
/// ```
///
/// **记录重试指标:**
///
/// ```java
/// retryTemplate.execute(context -> {
///     try {
///         return provider.upload(bucket, key, inputStream, metadata); finally {
///         if (context.getRetryCount() > 0) {
///             metrics.recordRetry(
///                 provider.getProviderType(),
///                 bucket,
///                 context.getRetryCount()
///             ););
/// ```
///
/// ## 告警规则示例
///
/// **上传成功率低于 95% 告警:**
///
/// ```
///
/// - alert: ObjectStorageUploadSuccessRateLow
///   expr: |
///     (
///       sum(rate(object_storage_upload_success_total[5m]))
///       /
///       (
///         sum(rate(object_storage_upload_success_total[5m]))
///         + sum(rate(object_storage_upload_failure_total[5m]))
///       )
///     ) < 0.95
///   for: 5m
///   labels:
///     severity: warning
///   annotations:
///     summary: "对象存储上传成功率低于 95%"
///     description: "最近 5 分钟上传成功率为 {{ $value | humanizePercentage }}"
///
/// ```
///
/// **P95 延迟超过 5 秒告警:**
///
/// ```
///
/// - alert: ObjectStorageUploadLatencyHigh
///   expr: |
///     histogram_quantile(0.95,
///       sum(rate(object_storage_upload_duration_seconds_bucket[5m])) by (le, provider)
///     ) > 5
///   for: 5m
///   labels:
///     severity: warning
///   annotations:
///     summary: "对象存储上传延迟过高"
///     description: "{{ $labels.provider }} 的 P95 延迟为 {{ $value | humanizeDuration }}"
///
/// ```
///
/// ## 注意事项
///
/// - **性能影响:** 指标收集开销极小,但应避免在热点路径中创建新的 Meter
///   - **标签基数:** 避免使用高基数标签(如用户 ID、文件名),会导致内存占用增加
///   - **时间单位:** 使用纳秒记录时间,Micrometer 会自动转换为秒
///   - **可选依赖:** 如果 Micrometer 不可用,指标收集器会自动降级为空操作
///
/// ## 相关模块
///
/// - {@link com.patra.starter.objectstorage.ObjectStorageTemplate} - 调用指标收集器
///   - {@link com.patra.starter.objectstorage.ObjectStorageAutoConfiguration} - 自动配置指标收集器 Bean
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.objectstorage.metrics;
