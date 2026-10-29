package dev.patra.catalog.domain.portal.model.read;

import dev.patra.catalog.domain.portal.model.vo.EvidenceLevel;
import java.util.List;
import lombok.Builder;

/// 文献详情读模型（CQRS 读端，一次返回完整数据集）。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationDetailReadModel(
    Long id,
    String title,
    String originalTitle,
    Long venueId,
    String venueName,
    Integer publicationYear,
    String publishedDate,
    EvidenceLevel evidenceLevel,
    String abstractType,
    List<AbstractSectionView> abstractSections,
    String abstractPlainText,
    String doi,
    String pmid,
    String pmcid,
    String pii,
    String primaryType,
    List<String> publicationTypes,
    Integer citationCount,
    Integer numberOfReferences,
    String conflictOfInterest,
    Boolean isOa,
    String oaStatus,
    String provenanceCode,
    String fullTextUrl,
    boolean retracted,
    boolean hasFullText,
    boolean hasPdf,
    Long pdfSizeBytes,
    Integer wordCount,
    List<AuthorView> authors,
    List<MeshHeadingView> meshHeadings,
    List<String> keywords,
    List<FundingView> funding,
    List<PublicationDateView> dates) {

  public PublicationDetailReadModel {
    abstractSections = abstractSections != null ? List.copyOf(abstractSections) : List.of();
    publicationTypes = publicationTypes != null ? List.copyOf(publicationTypes) : List.of();
    authors = authors != null ? List.copyOf(authors) : List.of();
    meshHeadings = meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
    keywords = keywords != null ? List.copyOf(keywords) : List.of();
    funding = funding != null ? List.copyOf(funding) : List.of();
    dates = dates != null ? List.copyOf(dates) : List.of();
  }

  /// 结构化摘要段落视图。
  ///
  /// @param label 段落标签（如 BACKGROUND、METHODS、RESULTS）
  /// @param text 段落内容
  public record AbstractSectionView(String label, String text) {

    /// 创建摘要段落视图。
    ///
    /// @param label 段落标签
    /// @param text 段落内容
    /// @return 摘要段落视图
    public static AbstractSectionView of(String label, String text) {
      return new AbstractSectionView(label, text);
    }
  }

  /// 作者视图（含机构归属）。
  ///
  /// @param order 作者顺序
  /// @param first 是否第一作者
  /// @param corresponding 是否通讯作者
  /// @param name 作者姓名
  /// @param affiliation 主机构字符串（第一机构，可为 null）
  @Builder
  public record AuthorView(
      int order, boolean first, boolean corresponding, String name, String affiliation) {}

  /// MeSH 主题词视图。
  ///
  /// @param descriptorUi MeSH 主题词 UI
  /// @param term 主题词名称
  /// @param major 是否为主要主题
  public record MeshHeadingView(String descriptorUi, String term, boolean major) {

    /// 创建 MeSH 主题词视图。
    ///
    /// @param descriptorUi MeSH 主题词 UI
    /// @param term 主题词名称
    /// @param major 是否为主要主题
    /// @return MeSH 主题词视图
    public static MeshHeadingView of(String descriptorUi, String term, boolean major) {
      return new MeshHeadingView(descriptorUi, term, major);
    }
  }

  /// 资助信息视图。
  ///
  /// @param funder 资助方名称（原始字符串）
  /// @param grantId 资助编号
  /// @param country 资助国家（原始字符串）
  public record FundingView(String funder, String grantId, String country) {

    /// 创建资助信息视图。
    ///
    /// @param funder 资助方名称
    /// @param grantId 资助编号
    /// @param country 资助国家
    /// @return 资助信息视图
    public static FundingView of(String funder, String grantId, String country) {
      return new FundingView(funder, grantId, country);
    }
  }

  /// 出版日期视图。
  ///
  /// @param type 日期类型（如 published）
  /// @param date 降精度 ISO 日期（`yyyy` / `yyyy-MM` / `yyyy-MM-dd`）
  public record PublicationDateView(String type, String date) {

    /// 创建出版日期视图。
    ///
    /// @param type 日期类型
    /// @param date 日期值
    /// @return 出版日期视图
    public static PublicationDateView of(String type, String date) {
      return new PublicationDateView(type, date);
    }
  }
}
