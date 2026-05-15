package dev.linqibin.patra.catalog.domain.model.vo.publication;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/// 已存在文献标识集合值对象。
///
/// 用于批量导入场景，承载数据库中已存在的 PMID 与 DOI。
///
/// @param pmids 已存在的 PMID 集合
/// @param dois 已存在的 DOI 集合（建议使用标准化后的值）
/// @author linqibin
/// @since 0.1.0
public record ExistingPublicationKeys(Set<String> pmids, Set<String> dois) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：执行防御性拷贝并保证集合非 null。
  public ExistingPublicationKeys {
    pmids = pmids != null ? Set.copyOf(pmids) : Set.of();
    dois = dois != null ? Set.copyOf(dois) : Set.of();
  }

  /// 创建空结果。
  ///
  /// @return 空的已存在标识集合
  public static ExistingPublicationKeys empty() {
    return new ExistingPublicationKeys(Set.of(), Set.of());
  }

  /// 创建结果对象。
  ///
  /// @param pmids PMID 集合
  /// @param dois DOI 集合
  /// @return 已存在标识集合
  public static ExistingPublicationKeys of(Set<String> pmids, Set<String> dois) {
    return new ExistingPublicationKeys(pmids, dois);
  }
}
