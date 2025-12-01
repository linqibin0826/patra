package com.patra.starter.restclient.download;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.common.error.trait.StandardErrorTrait;
import java.util.Set;

/// 下载异常。
///
/// 表示文件下载过程中发生的错误，携带语义特征以便上层正确处理。
///
/// **设计说明**：
/// 直接实现 `HasErrorTraits` 接口而非继承 `DomainException`，
/// 保持 Starter 模块的技术中立性，避免 Infrastructure 层依赖 Domain 层。
///
/// **常见错误场景**：
/// - 网络超时：{@link StandardErrorTrait#TIMEOUT}
/// - HTTP 错误响应：{@link StandardErrorTrait#DEP_UNAVAILABLE}
/// - IO 错误：{@link StandardErrorTrait#DEP_UNAVAILABLE}
///
/// @author linqibin
/// @since 0.1.0
public class DownloadException extends RuntimeException implements HasErrorTraits {

  private final Set<ErrorTrait> traits;

  /// 创建下载异常。
  ///
  /// @param message 错误消息
  /// @param trait 错误语义特征
  public DownloadException(String message, ErrorTrait trait) {
    super(message);
    this.traits = Set.of(trait);
  }

  /// 创建下载异常（带原因）。
  ///
  /// @param message 错误消息
  /// @param cause 原因异常
  /// @param trait 错误语义特征
  public DownloadException(String message, Throwable cause, ErrorTrait trait) {
    super(message, cause);
    this.traits = Set.of(trait);
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return traits;
  }

  /// 创建 HTTP 错误响应异常。
  ///
  /// @param statusCode HTTP 状态码
  /// @return 下载异常
  public static DownloadException httpError(int statusCode) {
    return new DownloadException(
        "下载失败，HTTP 状态码: " + statusCode, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建网络超时异常。
  ///
  /// @param cause 原因异常
  /// @return 下载异常
  public static DownloadException timeout(Throwable cause) {
    return new DownloadException("下载超时或网络不可达", cause, StandardErrorTrait.TIMEOUT);
  }

  /// 创建 IO 错误异常。
  ///
  /// @param cause 原因异常
  /// @return 下载异常
  public static DownloadException ioError(Throwable cause) {
    return new DownloadException(
        "下载文件时 IO 错误: " + cause.getMessage(), cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
