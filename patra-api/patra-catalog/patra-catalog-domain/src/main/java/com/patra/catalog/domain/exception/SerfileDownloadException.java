package com.patra.catalog.domain.exception;

import com.patra.common.error.trait.StandardErrorTrait;

/// Serfile 文件下载异常。
///
/// 当从远程服务器下载 NLM Serfile XML 文件失败时抛出此异常。
/// 携带 `DEP_UNAVAILABLE` 特征表示外部依赖不可用。
///
/// 可能的原因包括：
///
/// - 网络连接超时
/// - 服务器不可用
/// - 文件不存在（404）
/// - 权限不足（403）
///
/// @author linqibin
/// @since 0.1.0
public class SerfileDownloadException extends CatalogException {

  /// 创建下载异常。
  ///
  /// @param message 错误消息
  public SerfileDownloadException(String message) {
    super(message, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建带原因的下载异常。
  ///
  /// @param message 错误消息
  /// @param cause 原因
  public SerfileDownloadException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
