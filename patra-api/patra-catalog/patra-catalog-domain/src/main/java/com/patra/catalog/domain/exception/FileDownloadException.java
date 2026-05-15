package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.ErrorTrait;

/// 文件下载异常。
///
/// 触发场景：
///
/// - 网络连接失败（DNS 解析失败、连接超时）
/// - HTTP 状态码错误（4xx/5xx）
/// - 下载超时（读取超时）
/// - 本地 IO 错误（磁盘空间不足、权限不足等）
///
/// 此错误通常归因于外部依赖不可用或网络问题，**可能需要重试**。
///
/// 修复建议：
///
/// - **检查网络连通性**：确保服务器可访问目标 URL
/// - **验证 URL 有效性**：确保 URL 指向有效的资源
/// - **检查磁盘空间**：确保临时目录有足够的存储空间
/// - **调整超时配置**：对于大文件下载，可能需要增加读取超时时间
///
/// @author linqibin
/// @since 0.1.0
public class FileDownloadException extends CatalogException {

  /// 构造文件下载异常。
  ///
  /// @param message 人类可读的错误消息，应说明具体的失败原因
  /// @param traits 语义特征（如 TIMEOUT、DEP_UNAVAILABLE）
  public FileDownloadException(String message, ErrorTrait... traits) {
    super(message, traits);
  }

  /// 构造文件下载异常并附带底层原因。
  ///
  /// 适用场景：包装 IOException、RestClientException 等底层错误。
  ///
  /// @param message 描述性消息
  /// @param cause 底层异常
  /// @param traits 语义特征
  public FileDownloadException(String message, Throwable cause, ErrorTrait... traits) {
    super(message, cause, traits);
  }
}
