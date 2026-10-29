package dev.patra.catalog.adapter.fulltext.rest.converter;

import dev.patra.catalog.adapter.fulltext.rest.response.PortalFullTextResponse;
import dev.patra.catalog.adapter.kernel.rest.converter.EstimatedReadTime;
import dev.patra.catalog.domain.fulltext.model.read.FullTextReadModel;
import org.springframework.stereotype.Component;

/// 门户全文读模型 → 响应 DTO 转换器。
///
/// @author linqibin
/// @since 0.11.0
@Component
public class PortalFullTextApiConverter {

  /// 全文的数据来源署名（PMC 条款要求标注 NLM 为来源，不使用 PMC 标志）。
  static final String FULL_TEXT_SOURCE = "数据来源：美国国家医学图书馆（NLM）PubMed Central";

  /// 全文读模型 → 响应。许可链接只认常见 CC 许可；数据来源为 PMC 条款要求的固定署名。
  ///
  /// @param model 读模型
  /// @return 响应
  public PortalFullTextResponse toFullTextResponse(FullTextReadModel model) {
    PortalFullTextResponse.License license =
        CcLicenseLinks.urlOf(model.licenseCode())
            .map(url -> PortalFullTextResponse.License.of(model.licenseCode(), url))
            .orElse(null);
    return PortalFullTextResponse.builder()
        .publicationId(String.valueOf(model.publicationId()))
        .pmcid(model.pmcid())
        .pmcUrl("https://pmc.ncbi.nlm.nih.gov/articles/" + model.pmcid() + "/")
        .license(license)
        .source(FULL_TEXT_SOURCE)
        .fetchedAt(model.fetchedAt())
        .wordCount(model.wordCount())
        .estimatedReadMin(EstimatedReadTime.minutes(model.body() != null, model.wordCount()))
        .isManuscript(model.manuscript())
        .body(model.body())
        .build();
  }
}
