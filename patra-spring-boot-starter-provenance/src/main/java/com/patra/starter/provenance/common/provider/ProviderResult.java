package com.patra.starter.provenance.common.provider;

import com.patra.common.model.DataType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数据源提供者执行结果包装器
 *
 * <p>由 {@link ProvenanceDataProvider} 实现返回的统一结果封装。该结果捕获成功状态、数据载荷、游标提示和错误分类, 使采集引擎能够实施细致的重试和失败策略。
 *
 * <p><strong>核心特性</strong>：
 *
 * <ul>
 *   <li>泛型化：支持任意数据类型（CanonicalLiterature、Journal、Drug等）
 *   <li>数据类型标识：dataType字段标识结果的数据类型
 *   <li>扩展元数据：metadata字段支持自定义元数据
 *   <li>类型安全：工厂方法确保类型一致性
 * </ul>
 *
 * <p><b>核心职责:</b>
 *
 * <ul>
 *   <li>封装提供者执行的成功/失败状态
 *   <li>携带转换后的标准化数据列表（泛型）
 *   <li>传递上游API返回的分页游标
 *   <li>记录错误消息和类型以指导重试逻辑
 *   <li>提供获取数量统计用于监控指标
 * </ul>
 *
 * @param <T> 数据类型（如CanonicalLiterature、Journal、Drug）
 * @param success 提供者是否无终止性错误地完成执行
 * @param data 标准化数据记录的不可变列表
 * @param dataType 数据类型标识
 * @param nextCursorToken 上游API返回的下一页游标令牌
 * @param errorMessage 人类可读的诊断消息,通常在失败时填充
 * @param fetchedCount 获取或尝试的记录数量,用于度量统计
 * @param errorType 错误分类,指导重试语义
 * @param metadata 扩展元数据（如processingTime、recordCount等）
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public record ProviderResult<T>(
    boolean success,
    List<T> data,
    DataType dataType,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount,
    ErrorType errorType,
    Map<String, Object> metadata) {

  public ProviderResult {
    data = data == null ? List.of() : List.copyOf(data);
    fetchedCount = Math.max(fetchedCount, data.size());
    errorType = Objects.requireNonNullElse(errorType, ErrorType.NONE);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

    if (dataType == null) {
      throw new IllegalArgumentException("dataType不能为null");
    }
  }

  /**
   * 创建成功结果
   *
   * @param <T> 数据类型
   * @param data 上游数据源返回的载荷
   * @param dataType 数据类型标识
   * @param nextCursorToken 后续请求使用的游标令牌
   * @return 不可变的成功结果
   */
  public static <T> ProviderResult<T> success(
      List<T> data, DataType dataType, String nextCursorToken) {
    return new ProviderResult<>(
        true,
        data,
        dataType,
        nextCursorToken,
        null,
        data == null ? 0 : data.size(),
        ErrorType.NONE,
        null);
  }

  /**
   * 创建失败结果
   *
   * @param <T> 数据类型
   * @param dataType 数据类型标识
   * @param errorMessage 诊断消息
   * @param errorType 错误类型（RETRIABLE或NON_RETRIABLE）
   * @return 失败结果
   */
  public static <T> ProviderResult<T> failure(
      DataType dataType, String errorMessage, ErrorType errorType) {
    return new ProviderResult<>(false, List.of(), dataType, null, errorMessage, 0, errorType, null);
  }

  /**
   * 创建可重试的失败结果,通知调用方可以尝试重试
   *
   * @param <T> 数据类型
   * @param dataType 数据类型标识
   * @param errorMessage 诊断消息
   * @return 可重试的失败结果
   */
  public static <T> ProviderResult<T> retriableFailure(DataType dataType, String errorMessage) {
    return failure(dataType, errorMessage, ErrorType.RETRIABLE);
  }

  /**
   * 创建不可重试的失败结果
   *
   * @param <T> 数据类型
   * @param dataType 数据类型标识
   * @param errorMessage 诊断消息
   * @return 终止性失败结果
   */
  public static <T> ProviderResult<T> nonRetriableFailure(DataType dataType, String errorMessage) {
    return failure(dataType, errorMessage, ErrorType.NON_RETRIABLE);
  }

  /**
   * 创建部分成功结果,用于某些项目成功但需要记录警告的场景
   *
   * @param <T> 数据类型
   * @param data 成功转换的载荷
   * @param dataType 数据类型标识
   * @param nextCursorToken 继续分页的游标令牌
   * @param warningMessage 警告详情
   * @return 部分成功结果
   */
  public static <T> ProviderResult<T> partialSuccess(
      List<T> data, DataType dataType, String nextCursorToken, String warningMessage) {
    return new ProviderResult<>(
        true,
        data,
        dataType,
        nextCursorToken,
        warningMessage,
        data == null ? 0 : data.size(),
        ErrorType.PARTIAL_SUCCESS,
        null);
  }

  /**
   * 指示失败是否可重试
   *
   * @return 如果提供者建议重试操作则返回 true
   */
  public boolean isRetriable() {
    return errorType == ErrorType.RETRIABLE;
  }

  /** 提供者报告的错误类型 */
  public enum ErrorType {
    /** 无错误 */
    NONE,
    /** 瞬时错误,调用方应遵守退避规则重试 */
    RETRIABLE,
    /** 终止性错误,调用方不应自动重试 */
    NON_RETRIABLE,
    /** 部分成功并带有警告;调用方可记录日志并继续 */
    PARTIAL_SUCCESS
  }
}
