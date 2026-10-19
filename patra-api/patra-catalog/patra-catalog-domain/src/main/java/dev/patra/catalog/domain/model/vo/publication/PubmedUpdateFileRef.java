package dev.patra.catalog.domain.model.vo.publication;

/// PubMed updatefiles 中的一个文件。
///
/// 文件名为 `{前缀}{四位文件号}.xml.gz`，如 `pubmed26n1335.xml.gz`；
/// 前缀随年份变化（`pubmed26n`），文件号在一年内递增。
///
/// @param filePrefix 文件名前缀，如 `pubmed26n`
/// @param seq 文件号（≥ 1）
/// @author linqibin
/// @since 0.11.0
public record PubmedUpdateFileRef(String filePrefix, int seq) {

  /// 校验前缀非空、文件号不小于 1。
  public PubmedUpdateFileRef {
    if (filePrefix == null || filePrefix.isBlank()) {
      throw new IllegalArgumentException("filePrefix 不能为空");
    }
    if (seq < 1) {
      throw new IllegalArgumentException("seq 必须不小于 1：" + seq);
    }
  }

  /// 创建文件引用。
  ///
  /// @param filePrefix 文件名前缀
  /// @param seq 文件号
  /// @return 文件引用
  public static PubmedUpdateFileRef of(String filePrefix, int seq) {
    return new PubmedUpdateFileRef(filePrefix, seq);
  }

  /// 返回文件名，如 `pubmed26n1335.xml.gz`。
  ///
  /// @return 文件名
  public String fileName() {
    return "%s%04d.xml.gz".formatted(filePrefix, seq);
  }

  /// 拼出下载地址，`updateUrl` 末尾有没有 `/` 都可以。
  ///
  /// @param updateUrl updatefiles 目录地址
  /// @return 文件的完整下载地址
  public String downloadUrl(String updateUrl) {
    String base = updateUrl.endsWith("/") ? updateUrl : updateUrl + "/";
    return base + fileName();
  }
}
