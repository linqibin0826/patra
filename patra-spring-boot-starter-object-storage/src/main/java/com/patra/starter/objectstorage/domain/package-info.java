/// 对象存储领域模型包。
/// 
/// 本包定义对象存储操作的核心领域对象和异常类型,提供强类型的 API 契约,确保参数验证和结果传递的类型安全性。
/// 
/// ## 职责
/// 
/// - 定义上传操作的输入参数 {@link ObjectMetadata}
///   - 定义上传操作的返回结果 {@link UploadResult}
///   - 定义领域异常类型,区分验证错误和操作失败
///   - 提供不可变的值对象,避免并发修改问题
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.objectstorage.domain.ObjectMetadata} - 对象元数据值对象
///   - {@link com.patra.starter.objectstorage.domain.UploadResult} - 上传结果值对象
///   - {@link com.patra.starter.objectstorage.domain.InvalidUploadRequestException} - 参数验证失败异常
///   - {@link com.patra.starter.objectstorage.domain.UploadFailedException} - 上传操作失败异常
/// 
/// ## 设计决策
/// 
/// - **值对象模式:** 所有领域对象使用 `@Builder` 和 final 字段,确保不可变性
///   - **异常分层:** 区分验证错误(不可重试)和操作失败(可重试),支持精确的异常处理策略
///   - **类型安全:** 使用强类型而非 Map,避免运行时类型转换错误
///   - **扩展性:** 使用 Builder 模式支持未来添加新字段而不破坏现有代码
/// 
/// ## 领域对象说明
/// 
/// ### ObjectMetadata
/// 
/// 上传操作的元数据,包含文件大小、内容类型等信息。
/// 
/// <table border="1">
///   <tr><th>字段</th><th>类型</th><th>说明</th><th>必填</th></tr>
///   <tr><td>contentLength</td><td>Long</td><td>文件大小(字节)</td><td>是</td></tr>
///   <tr><td>contentType</td><td>String</td><td>MIME 类型</td><td>否</td></tr>
/// </table>
/// 
/// ### UploadResult
/// 
/// 上传操作的返回结果,包含存储键、ETag 等信息。
/// 
/// <table border="1">
///   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
///   <tr><td>storageKey</td><td>String</td><td>完整存储路径,如 "prod/patra-ingest/2025/01/15/abc.pdf"</td></tr>
///   <tr><td>bucketName</td><td>String</td><td>存储桶名称,如 "patra-prod"</td></tr>
///   <tr><td>objectKey</td><td>String</td><td>对象键,如 "2025/01/15/abc.pdf"</td></tr>
///   <tr><td>etag</td><td>String</td><td>ETag 标识,用于完整性校验</td></tr>
///   <tr><td>fileSize</td><td>long</td><td>实际上传的文件大小(字节)</td></tr>
/// </table>
/// 
/// ## 异常分类
/// 
/// ### InvalidUploadRequestException
/// 
/// 参数验证失败异常,属于客户端错误,不应重试。
/// 
/// **触发场景:**
/// 
/// - bucket 名称为空或格式不正确
///   - objectKey 为空或包含非法字符
///   - inputStream 为 null
///   - contentLength 为负数或超过限制
///   - contentType 格式不正确
/// 
/// ### UploadFailedException
/// 
/// 上传操作失败异常,可能是瞬时故障,支持重试。
/// 
/// **触发场景:**
/// 
/// - 网络 I/O 错误(可重试)
///   - 连接超时(可重试)
///   - 认证失败(不可重试)
///   - 授权失败(不可重试)
///   - 存储服务不可用(可重试)
/// 
/// ## 使用示例
/// 
/// **构造元数据:**
/// 
/// ```java
/// ObjectMetadata metadata = ObjectMetadata.builder()
///     .contentLength(1024L) // 1KB
///     .contentType("application/pdf")
///     .build();
/// ```
/// 
/// **处理上传结果:**
/// 
/// ```java
/// UploadResult result = objectStorageOps.upload(bucket, key, inputStream, metadata);
/// 
/// // 保存到数据库的字段
/// String storageKey = result.getStorageKey(); // "prod/patra-ingest/2025/01/15/abc.pdf"
/// String etag = result.getEtag(); // "d41d8cd98f00b204e9800998ecf8427e"
/// long fileSize = result.getFileSize(); // 1024
/// 
/// // 日志记录
/// log.info("文件上传成功: bucket={, key={, etag={",
///     result.getBucketName(), result.getObjectKey(), result.getEtag());
/// ```
/// 
/// **异常处理:**
/// 
/// ```java
/// try {
///     UploadResult result = objectStorageOps.upload(bucket, key, inputStream, metadata); catch (InvalidUploadRequestException ex) {
///     // 参数验证失败,不重试
///     log.error("上传参数无效: {", ex.getMessage());
///     return ResponseEntity.badRequest().body("文件上传失败: " + ex.getMessage()); catch (UploadFailedException ex) {
///     // 上传失败(已自动重试)
///     log.error("文件上传失败: {", ex.getMessage(), ex);
///     return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
///         .body("文件上传服务暂时不可用,请稍后重试");
/// ```
/// 
/// **参数验证示例:**
/// 
/// ```java
/// public void validateUploadRequest(String bucket, String key, InputStream inputStream,
///                                    ObjectMetadata metadata) {
///     if (bucket == null || bucket.isBlank()) {
///         throw new InvalidUploadRequestException("Bucket 名称不能为空");
///     if (key == null || key.isBlank()) {
///         throw new InvalidUploadRequestException("对象键不能为空");
///     if (inputStream == null) {
///         throw new InvalidUploadRequestException("输入流不能为 null");
///     if (metadata.getContentLength() <= 0) {
///         throw new InvalidUploadRequestException("文件大小必须大于 0");
///     if (metadata.getContentLength() > maxFileSize) {
///         throw new InvalidUploadRequestException(
///             String.format("文件大小 %d 超过限制 %d", metadata.getContentLength(), maxFileSize));
/// ```
/// 
/// ## 扩展指南
/// 
/// **添加新的元数据字段:**
/// 
/// ```java
/// @Getter
/// @Builder
/// public class ObjectMetadata {
///     private final Long contentLength;
///     private final String contentType;
/// 
///     // 新增字段(使用 Builder 模式兼容旧代码)
///     private final String contentEncoding; // 内容编码(如 gzip)
///     private final Map<String, String> userMetadata; // 自定义元数据
/// 
/// // 旧代码仍可正常工作
/// ObjectMetadata old = ObjectMetadata.builder()
///     .contentLength(1024L)
///     .build();
/// 
/// // 新代码使用新字段
/// ObjectMetadata enhanced = ObjectMetadata.builder()
///     .contentLength(1024L)
///     .contentEncoding("gzip")
///     .userMetadata(Map.of("author", "linqibin"))
///     .build();
/// ```
/// 
/// ## 注意事项
/// 
/// - **不可变性:** 所有字段使用 final 修饰,避免并发修改问题
///   - **Builder 模式:** 使用 Lombok `@Builder` 注解简化对象构造
///   - **异常消息:** 异常消息应清晰描述失败原因,便于调试和日志分析
///   - **存储键格式:** storageKey 包含完整路径,objectKey 仅包含对象部分,注意区分
/// 
/// ## 相关模块
/// 
/// - {@link com.patra.starter.objectstorage.ObjectStorageOperations} - 使用领域对象的操作接口
///   - {@link com.patra.starter.objectstorage.ObjectStorageTemplate} - 处理领域异常的模板类
///   - {@link com.patra.starter.objectstorage.metrics.ObjectStorageMetrics} - 基于异常类型记录指标
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.objectstorage.domain;
