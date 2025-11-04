package com.patra.starter.core.error.engine;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * 错误解析引擎接口,将任意异常转换为平台统一的错误表示。
 *
 * <p>解析策略(按优先级):
 *
 * <ol>
 *   <li>SPI 贡献者映射: 通过 {@link com.patra.starter.core.error.spi.ErrorMappingContributor} 提供自定义映射
 *   <li>特征映射: 基于异常实现的接口(如 {@link ErrorCodeLike})进行映射
 *   <li>类名启发式: 根据异常类名生成错误码(如 IllegalArgumentException → ILLEGAL_ARGUMENT)
 * </ol>
 *
 * @author Patra Team
 * @since 2.0
 * @see com.patra.starter.core.error.engine.DefaultErrorResolutionEngine
 */
public interface ErrorResolutionEngine {

  /**
   * 将提供的异常解析为结构化错误。
   *
   * @param exception 要解析的异常(永不为 {@code null})
   * @return 解析后的错误结果,包含错误码和 HTTP 状态码
   */
  ErrorResolution resolve(Throwable exception);
}
