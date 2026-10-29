package dev.patra.catalog.infra.publication.batch;

/// PubMed 文件下载或 md5 校验多次失败。整个文件按失败处理。
///
/// @author linqibin
/// @since 0.1.0
public class PubmedFileFetchException extends RuntimeException {

  /// 创建异常。
  ///
  /// @param message 说明
  /// @param cause 最后一次失败的原因，可空
  public PubmedFileFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}
