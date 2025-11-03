package com.patra.starter.provenance.common.adapter;

import com.patra.common.model.StandardLiterature;
import java.util.List;
import java.util.Objects;

/**
 * 数据源适配器执行结果包装器
 *
 * <p>由 {@link DataSourceAdapter} 实现返回的统一结果封装。该结果捕获成功状态、数据载荷、游标提示和错误分类, 使采集引擎能够实施细致的重试和失败策略。
 *
 * <p><b>核心职责:</b>
 *
 * <ul>
 *   <li>封装适配器执行的成功/失败状态
 *   <li>携带转换后的标准化文献列表
 *   <li>传递上游API返回的分页游标
 *   <li>记录错误消息和类型以指导重试逻辑
 *   <li>提供获取数量统计用于监控指标
 * </ul>
 *
 * @param success 适配器是否无终止性错误地完成执行
 * @param literatures 标准化文献记录的不可变列表
 * @param nextCursorToken 上游API返回的下一页游标令牌
 * @param errorMessage 人类可读的诊断消息,通常在失败时填充
 * @param fetchedCount 获取或尝试的记录数量,用于度量统计
 * @param errorType 错误分类,指导重试语义
 * @author linqibin
 * @since 0.1.0
 */
public record AdapterResult(
    boolean success,
    List<StandardLiterature> literatures,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount,
    ErrorType errorType) {

  public AdapterResult {
    literatures = literatures == null ? List.of() : List.copyOf(literatures);
    fetchedCount = Math.max(fetchedCount, literatures.size());
    errorType = Objects.requireNonNullElse(errorType, ErrorType.NONE);
  }

  /**
   * 创建成功结果
   *
   * @param literatures 上游数据源返回的载荷
   * @param nextCursorToken 后续请求使用的游标令牌
   * @return 不可变的成功结果
   */
  public static AdapterResult success(
      List<StandardLiterature> literatures, String nextCursorToken) {
    return new AdapterResult(
        true,
        literatures,
        nextCursorToken,
        null,
        literatures == null ? 0 : literatures.size(),
        ErrorType.NONE);
  }

  /**
   * 创建可重试的失败结果,通知调用方可以尝试重试
   *
   * @param errorMessage 诊断消息
   * @return 可重试的失败结果
   */
  public static AdapterResult retriableFailure(String errorMessage) {
    return new AdapterResult(false, List.of(), null, errorMessage, 0, ErrorType.RETRIABLE);
  }

  /**
   * 创建不可重试的失败结果
   *
   * @param errorMessage 诊断消息
   * @return 终止性失败结果
   */
  public static AdapterResult nonRetriableFailure(String errorMessage) {
    return new AdapterResult(false, List.of(), null, errorMessage, 0, ErrorType.NON_RETRIABLE);
  }

  /**
   * 创建部分成功结果,用于某些项目成功但需要记录警告的场景
   *
   * @param literatures 成功转换的载荷
   * @param nextCursorToken 继续分页的游标令牌
   * @param warningMessage 警告详情
   * @param totalAttempted 处理的总项目数(包括失败项)
   * @return 部分成功结果
   */
  public static AdapterResult partialSuccess(
      List<StandardLiterature> literatures,
      String nextCursorToken,
      String warningMessage,
      int totalAttempted) {
    return new AdapterResult(
        true,
        literatures,
        nextCursorToken,
        warningMessage,
        totalAttempted,
        ErrorType.PARTIAL_SUCCESS);
  }

  /**
   * 指示失败是否可重试
   *
   * @return 如果适配器建议重试操作则返回 true
   */
  public boolean isRetriable() {
    return errorType == ErrorType.RETRIABLE;
  }

  /** 适配器报告的错误类型 */
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
