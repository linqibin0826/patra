package dev.patra.catalog.domain.fulltext.port.storage;

import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFileInfo;
import java.nio.file.Path;
import java.util.Optional;

/// 全文文件（XML、PDF、图片）的对象存储端口。
///
/// 对象键沿用 PMC 的目录结构：`pmc/{PMCID}/{PMCID}.{v}/{原文件名}`。
///
/// @author linqibin
/// @since 0.11.0
public interface FullTextObjectStoragePort {

  /// 一篇文章一个版本的对象前缀（读写两侧共用）。
  ///
  /// @param pmcid PMCID
  /// @param versionPrefix 版本前缀，如 `PMC1234567.2`
  /// @return 前缀，以 `/` 结尾
  static String objectPrefix(String pmcid, String versionPrefix) {
    return "pmc/" + pmcid + "/" + versionPrefix + "/";
  }

  /// 写入一个本地文件。
  ///
  /// @param objectKey 对象键
  /// @param file 本地文件
  /// @param contentType 内容类型
  void store(String objectKey, Path file, String contentType);

  /// 查对象元信息。
  ///
  /// @param objectKey 对象键
  /// @return 元信息，不存在时为空
  Optional<StoredFileInfo> stat(String objectKey);

  /// 打开对象。
  ///
  /// @param objectKey 对象键
  /// @return 打开的文件，调用方负责关闭
  /// @throws dev.patra.catalog.domain.fulltext.exception.FullTextFileNotFoundException 对象不存在时抛出
  StoredFile open(String objectKey);
}
