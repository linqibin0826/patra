/**
 * 错误解析引擎包。
 *
 * <p>本包提供核心的异常到错误码转换引擎,将任意 Java 异常标准化为平台统一的错误表示。 支持多种解析策略,确保所有错误都能被正确识别和映射。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将任意 {@link Throwable} 转换为 {@link com.patra.starter.core.error.model.ErrorResolution}
 *   <li>遍历异常原因链(cause chain),查找最具体的错误信息
 *   <li>应用多种解析策略(SPI 贡献者、特征映射、类名启发式)
 *   <li>生成错误码、HTTP 状态码和错误消息
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.engine.ErrorResolutionEngine} - 错误解析引擎接口
 *   <li>{@link com.patra.starter.core.error.engine.DefaultErrorResolutionEngine} - 默认解析引擎实现
 * </ul>
 *
 * <h2>解析策略</h2>
 *
 * <p>引擎按以下优先级应用解析策略:
 *
 * <ol>
 *   <li><strong>SPI 贡献者映射</strong> - 通过 {@link
 *       com.patra.starter.core.error.spi.ErrorMappingContributor} 提供的自定义映射
 *   <li><strong>特征映射</strong> - 基于异常实现的接口(如 {@code ErrorCodeLike})进行映射
 *   <li><strong>类名启发式</strong> - 根据异常类名生成错误码(如 {@code IllegalArgumentException} → {@code
 *       ILLEGAL_ARGUMENT})
 * </ol>
 *
 * <h2>原因链遍历</h2>
 *
 * <p>引擎会遍历异常的 {@code cause} 链,查找最具体的错误信息:
 *
 * <pre>
 * RuntimeException("数据库操作失败")
 *   ↓ cause
 * SQLException("连接超时")
 *   ↓ cause
 * SocketTimeoutException("Read timed out")  ← 最具体的根因
 * </pre>
 *
 * <p>可通过 {@code patra.error.engine.max-cause-depth} 配置遍历深度限制(默认 10)。
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * public class ErrorHandlingService {
 *     private final ErrorResolutionEngine engine;
 *
 *     public ErrorResolution handleException(Throwable ex) {
 *         // 引擎自动遍历原因链并应用解析策略
 *         ErrorResolution resolution = engine.resolve(ex);
 *
 *         log.error("错误码: {}, HTTP状态: {}, 消息: {}",
 *             resolution.getErrorCode(),
 *             resolution.getHttpStatus(),
 *             resolution.getMessage()
 *         );
 *
 *         return resolution;
 *     }
 * }
 * }</pre>
 *
 * <h2>扩展自定义映射</h2>
 *
 * <pre>{@code
 * @Component
 * public class MyErrorMappingContributor implements ErrorMappingContributor {
 *     @Override
 *     public Optional<ErrorCodeLike> mapException(Throwable exception) {
 *         if (exception instanceof MyBusinessException ex) {
 *             return Optional.of(new SimpleErrorCode(
 *                 "MY_BUSINESS_ERROR",
 *                 ex.getMessage(),
 *                 HttpStatus.BAD_REQUEST.value()
 *             ));
 *         }
 *         return Optional.empty();  // 传递给下一个策略
 *     }
 * }
 * }</pre>
 *
 * <h2>配置选项</h2>
 *
 * <pre>{@code
 * patra:
 *   error:
 *     engine:
 *       max-cause-depth: 10              # 原因链最大遍历深度
 *       enable-trait-mapping: true       # 启用特征映射
 *       enable-naming-heuristic: true    # 启用类名启发式
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.engine;
