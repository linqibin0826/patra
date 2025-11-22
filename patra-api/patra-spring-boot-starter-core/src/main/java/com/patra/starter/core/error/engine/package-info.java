/// 错误解析引擎包。
///
/// 本包提供核心的异常到错误码转换引擎,将任意 Java 异常标准化为平台统一的错误表示。 支持多种解析策略,确保所有错误都能被正确识别和映射。
///
/// ## 职责
///
/// - 将任意 {@link Throwable} 转换为 {@link com.patra.starter.core.error.model.ErrorResolution}
///   - 遍历异常原因链(cause chain),查找最具体的错误信息
///   - 应用多种解析策略(SPI 贡献者、特征映射、类名启发式)
///   - 生成错误码、HTTP 状态码和错误消息
///
/// ## 核心组件
///
/// - {@link com.patra.starter.core.error.engine.ErrorResolutionEngine} - 错误解析引擎接口
///   - {@link com.patra.starter.core.error.engine.DefaultErrorResolutionEngine} - 默认解析引擎实现
///
/// ## 解析策略
///
/// 引擎按以下优先级应用解析策略:
///
/// ## 原因链遍历
///
/// 引擎会遍历异常的 `cause` 链,查找最具体的错误信息:
///
/// ```
///
/// RuntimeException("数据库操作失败")
///   ↓ cause
/// SQLException("连接超时")
///   ↓ cause
/// SocketTimeoutException("Read timed out")  ← 最具体的根因
///
/// ```
///
/// 可通过 `patra.error.engine.max-cause-depth` 配置遍历深度限制(默认 10)。
///
/// ## 使用示例
///
/// ```java
/// @Service
/// public class ErrorHandlingService {
///     private final ErrorResolutionEngine engine;
///
///     public ErrorResolution handleException(Throwable ex) {
///         // 引擎自动遍历原因链并应用解析策略
///         ErrorResolution resolution = engine.resolve(ex);
///
///         log.error("错误码: {, HTTP状态: {, 消息: {",
///             resolution.getErrorCode(),
///             resolution.getHttpStatus(),
///             resolution.getMessage()
///         );
///
///         return resolution;
/// ```
///
/// ## 扩展自定义映射
///
/// ```java
/// @Component
/// public class MyErrorMappingContributor implements ErrorMappingContributor {
///     @Override
///     public Optional<ErrorCodeLike> mapException(Throwable exception) {
///         if (exception instanceof MyBusinessException ex) {
///             return Optional.of(new SimpleErrorCode(
///                 "MY_BUSINESS_ERROR",
///                 ex.getMessage(),
///                 HttpStatus.BAD_REQUEST.value()
///             ));
///         return Optional.empty();  // 传递给下一个策略
/// ```
///
/// ## 配置选项
///
/// ```java
/// patra:
///   error:
///     engine:
///       max-cause-depth: 10              # 原因链最大遍历深度
///       enable-trait-mapping: true       # 启用特征映射
///       enable-naming-heuristic: true    # 启用类名启发式
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.engine;
