package dev.patra.catalog.domain.portal.model.read;

import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import java.util.List;
import lombok.Builder;

/// 导出与复制引用共用的引用记录（CQRS 读模型）。
///
/// @param id 文献主键
/// @param pmid PubMed ID，可空
/// @param pmcid PubMed Central ID，可空
/// @param doi DOI，可空
/// @param title 标题纯文本（已去掉内联标记）
/// @param authors 作者，按 `author_order`；没有作者为空列表
/// @param journalTitle 期刊全称，可空
/// @param journalAbbreviation 期刊缩写：ISO 缩写 → MedlineTA → 全称，可空
/// @param volume 卷，可空
/// @param issue 期，可空
/// @param pagination 页码（恒非 null，可能为空）
/// @param articleNumber 文章号，只在没有页码时有，可空
/// @param publishedDate 出版日期（按实际精度），年份未知时为 null
/// @param language 语言代码（BCP 47），可空
/// @author linqibin
/// @since 0.1.0
@Builder
public record CitationRecord(
    Long id,
    String pmid,
    String pmcid,
    String doi,
    String title,
    List<CitationAuthor> authors,
    String journalTitle,
    String journalAbbreviation,
    String volume,
    String issue,
    Pagination pagination,
    String articleNumber,
    PublicationDate publishedDate,
    String language) {

  /// 紧凑构造器：作者防御性拷贝；页码为空时给空对象。
  public CitationRecord {
    authors = authors != null ? List.copyOf(authors) : List.of();
    pagination = pagination != null ? pagination : Pagination.of(null, null, null);
  }
}
