package dev.linqibin.starter.restclient.download;

import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
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

  /// 创建不支持的协议异常。
  ///
  /// @param scheme 协议
  /// @return 下载异常
  public static DownloadException unsupportedScheme(String scheme) {
    return new DownloadException("不支持的协议: " + scheme, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 创建 URL 非法异常。
  ///
  /// @param url 下载地址
  /// @return 下载异常
  public static DownloadException invalidUrl(String url) {
    return new DownloadException("下载地址无效: " + url, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 创建文件已存在异常。
  ///
  /// @param path 文件路径
  /// @return 下载异常
  public static DownloadException fileAlreadyExists(String path) {
    return new DownloadException("目标文件已存在: " + path, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 创建目标路径不是常规文件异常。
  ///
  /// @param path 文件路径
  /// @return 下载异常
  public static DownloadException targetNotRegularFile(String path) {
    return new DownloadException("目标路径不是文件: " + path, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 创建缺少目标路径异常。
  ///
  /// @return 下载异常
  public static DownloadException targetPathMissing() {
    return new DownloadException("下载目标路径缺失，请指定路径或配置默认目录", StandardErrorTrait.RULE_VIOLATION);
  }
}
