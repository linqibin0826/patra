package dev.patra.catalog.domain.fulltext.model.vo.body;

import java.util.List;
import lombok.Builder;

/// 参考文献条目。门户只给 DOI / PubMed 外链，不做站内跳转。
///
/// @param id JATS `<ref>` 的 id
/// @param label 编号
/// @param content 著录文本（`mixed-citation` 原样转换，或由 `element-citation` 拼出）
/// @param pmid PMID，可空
/// @param doi DOI，可空
/// @author linqibin
/// @since 0.11.0
@Builder
public record Reference(String id, String label, List<Inline> content, String pmid, String doi) {

  /// 防御性拷贝，`null` 视为空。
  public Reference {
    content = content != null ? List.copyOf(content) : List.of();
  }
}
