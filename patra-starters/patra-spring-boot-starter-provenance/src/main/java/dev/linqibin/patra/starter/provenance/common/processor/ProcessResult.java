package dev.linqibin.patra.starter.provenance.common.processor;

import java.util.List;
import java.util.Map;

/// 数据处理结果
///
/// 封装Processor处理数据后的结果，包括成功、失败、部分成功和验证错误等状态。
///
/// **设计理念**：
///
/// - 使用Record保证不可变性
///   - 提供工厂方法简化创建
///   - 支持元数据扩展
///
/// @param <T> 数据类型
/// @param success 是否成功（SUCCESS或PARTIAL_SUCCESS时为true）
/// @param data 处理后的数据列表
/// @param nextCursor 下一页游标令牌（如果没有下一页则为null）
/// @param errorMessage 错误消息或警告消息
/// @param status 处理状态
/// @param metadata 扩展元数据（如recordCount、processingTime等）
/// @author linqibin
/// @since 0.1.0
public record ProcessResult<T>(
    boolean success,
    List<T> data,
    String nextCursor,
    String errorMessage,
    ProcessStatus status,
    Map<String, Object> metadata) {

  /// 创建成功结果
  ///
  /// @param data 处理后的数据列表
  /// @param nextCursor 下一页游标令牌（最后一页时为null）
  /// @param <T> 数据类型
  /// @return 成功的处理结果
  public static <T> ProcessResult<T> success(List<T> data, String nextCursor) {
    return new ProcessResult<>(true, data, nextCursor, null, ProcessStatus.SUCCESS, null);
  }

  /// 创建带元数据的成功结果
  ///
  /// @param data 处理后的数据列表
  /// @param metadata 扩展元数据
  /// @param <T> 数据类型
  /// @return 成功的处理结果
  public static <T> ProcessResult<T> successWithMetadata(
      List<T> data, Map<String, Object> metadata) {
    return new ProcessResult<>(true, data, null, null, ProcessStatus.SUCCESS, metadata);
  }

  /// 创建失败结果
  ///
  /// @param errorMessage 错误消息
  /// @param <T> 数据类型
  /// @return 失败的处理结果
  public static <T> ProcessResult<T> failure(String errorMessage) {
    return new ProcessResult<>(false, null, null, errorMessage, ProcessStatus.FAILED, null);
  }

  /// 创建部分成功结果
  ///
  /// 当部分数据处理成功，部分失败时使用此方法。
  ///
  /// @param data 成功处理的数据列表
  /// @param nextCursor 下一页游标令牌
  /// @param warningMessage 警告消息（描述失败的部分）
  /// @param <T> 数据类型
  /// @return 部分成功的处理结果
  public static <T> ProcessResult<T> partialSuccess(
      List<T> data, String nextCursor, String warningMessage) {
    return new ProcessResult<>(
        true, data, nextCursor, warningMessage, ProcessStatus.PARTIAL_SUCCESS, null);
  }

  /// 创建验证错误结果
  ///
  /// @param errorMessage 验证错误消息
  /// @param <T> 数据类型
  /// @return 验证错误的处理结果
  public static <T> ProcessResult<T> validationError(String errorMessage) {
    return new ProcessResult<>(
        false, null, null, errorMessage, ProcessStatus.VALIDATION_ERROR, null);
  }
}
