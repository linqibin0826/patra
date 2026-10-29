package dev.patra.catalog.domain.publication.model.vo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 一条 PubMed 记录来自哪个文件：文件名与可比较的位置。
///
/// NLM 的规则是同一个 PMID 后出现的文件取代先出现的。baseline 与 updatefiles 同一年共用一个编号序列，
/// 下一年的 baseline 重新从 1 编号，所以位置 `rank = 年 × 10000 + 序号`（`pubmed26n1333` → `261333`）。
///
/// @param fileName 文件名，如 `pubmed26n1333.xml.gz`
/// @param rank 位置，越大越新
/// @author linqibin
/// @since 0.1.0
public record SourcePosition(String fileName, int rank) {

  /// PubMed 文件名：`pubmed{yy}n{seq4}.xml.gz`。
  private static final Pattern FILE_NAME = Pattern.compile("^pubmed(\\d{2})n(\\d{4})\\.xml\\.gz$");

  /// 由文件名计算位置。
  ///
  /// @param fileName 文件名
  /// @return 位置
  /// @throws IllegalArgumentException 文件名不合规时
  public static SourcePosition ofFileName(String fileName) {
    Matcher m = FILE_NAME.matcher(fileName == null ? "" : fileName);
    if (!m.matches()) {
      throw new IllegalArgumentException("不是 PubMed 文件名：" + fileName);
    }
    return new SourcePosition(
        fileName, Integer.parseInt(m.group(1)) * 10_000 + Integer.parseInt(m.group(2)));
  }

  /// 由已存的文件名与位置重建。
  ///
  /// @param fileName 文件名
  /// @param rank 位置
  /// @return 位置
  public static SourcePosition of(String fileName, int rank) {
    return new SourcePosition(fileName, rank);
  }

  /// 是否比库里记的位置旧。
  ///
  /// @param storedRank 库里记的位置；为 null（还没有位置的旧数据）时视为最旧
  /// @return 只有 `rank < storedRank` 时为 `true`
  public boolean isOlderThan(Integer storedRank) {
    return storedRank != null && rank < storedRank;
  }
}
