package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 文献索引状态枚举。
///
/// 字段映射：cat_publication_metadata.indexing_status
///
/// PubMed/MEDLINE 索引处理状态说明：
///
/// - **PENDING** - 待处理（新导入，尚未开始索引）
/// - **IN_PROCESS** - 处理中（正在进行索引）
/// - **IN_DATA_REVIEW** - 数据审核中
/// - **INDEXED** - 已索引（通用标记）
/// - **MEDLINE** - 已收录到 MEDLINE（完整索引，含 MeSH 词）
/// - **PUBMED_NOT_MEDLINE** - 收录到 PubMed 但不在 MEDLINE
/// - **OLDMEDLINE** - 历史 MEDLINE 数据（1966 年前）
/// - **FAILED** - 索引失败
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum IndexingStatus {

  /// 待处理
  PENDING("pending", "Pending"),

  /// 处理中
  IN_PROCESS("in-process", "In Process"),

  /// 数据审核中
  IN_DATA_REVIEW("in-data-review", "In Data Review"),

  /// 已索引
  INDEXED("indexed", "Indexed"),

  /// MEDLINE 收录（完整索引）
  MEDLINE("medline", "MEDLINE"),

  /// PubMed 收录但非 MEDLINE
  PUBMED_NOT_MEDLINE("pubmed-not-medline", "PubMed Not MEDLINE"),

  /// 历史 MEDLINE 数据
  OLDMEDLINE("oldmedline", "Old MEDLINE"),

  /// 索引失败
  FAILED("failed", "Failed");

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  IndexingStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "pending", "MEDLINE", "pubmed-not-medline"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static IndexingStatus fromCode(String value) {
    Assert.notBlank(value, "索引状态代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (IndexingStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的索引状态：" + value);
  }

  /// 判断是否已完成索引。
  ///
  /// @return true 如果为 INDEXED、MEDLINE、PUBMED_NOT_MEDLINE 或 OLDMEDLINE
  public boolean isIndexed() {
    return this == INDEXED || this == MEDLINE || this == PUBMED_NOT_MEDLINE || this == OLDMEDLINE;
  }

  /// 判断是否包含 MeSH 主题词。
  ///
  /// 仅 MEDLINE 和 OLDMEDLINE 状态的文献会有完整的 MeSH 标注。
  ///
  /// @return true 如果为 MEDLINE 或 OLDMEDLINE
  public boolean hasMeshTerms() {
    return this == MEDLINE || this == OLDMEDLINE;
  }
}
