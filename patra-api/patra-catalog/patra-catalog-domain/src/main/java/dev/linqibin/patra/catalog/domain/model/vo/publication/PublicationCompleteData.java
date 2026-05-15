package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import dev.linqibin.patra.catalog.domain.model.aggregate.PublicationAggregate;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Builder;

/// 完整文献数据值对象。
///
/// 封装文献聚合根和所有关联数据，用于 Repository 的批量写入操作。
///
/// **设计说明**：
///
/// 这是一个"数据传输值对象"，用于将 Writer 中的写入逻辑迁移到 Repository：
/// - **聚合根**：PublicationAggregate（主数据）
/// - **关联数据**：MeSH、关键词、资助、出版类型等（补充数据）
///
/// **为什么需要这个类型**：
///
/// 原始设计中，Writer 直接操作多个 DAO 写入关联数据，违反了六边形架构原则。
/// 重构后，Writer 将数据封装为 `PublicationCompleteData`，
/// 通过 `PublicationRepository.insertAllWithAssociations()` 统一写入。
///
/// **数据边界**：
///
/// | 数据类型 | 聚合边界 | 说明 |
/// |----------|----------|------|
/// | PublicationAggregate | 内 | 聚合根，包含核心字段 |
/// | PublicationAbstract | 内 | 摘要，聚合根内的值对象 |
/// | PublicationIdentifier | 内 | 标识符，聚合根内的值对象 |
/// | PublicationMeshHeading | 外 | MeSH 标引，补充数据 |
/// | PublicationKeyword | 外 | 关键词，补充数据 |
/// | PublicationFunding | 外 | 资助信息，补充数据 |
/// | PublicationTypeInfo | 外 | 出版类型，补充数据 |
/// | PublicationSupplMesh | 外 | 补充 MeSH，补充数据 |
/// | PublicationAlternativeAbstract | 外 | 翻译摘要，补充数据 |
/// | PublicationDate | 外 | 日期信息，补充数据 |
/// | PublicationMetadata | 外 | 元数据，补充数据 |
/// | PublicationInvestigator | 外 | 研究者，补充数据 |
/// | PublicationPersonalNameSubject | 外 | 人物主题，补充数据 |
///
/// @param publication 文献聚合根（主数据，必填）
/// @param metadata 文献元数据
/// @param meshHeadings MeSH 标引列表
/// @param keywords 关键词列表
/// @param funding 资助信息列表
/// @param publicationTypes 出版类型列表
/// @param supplMeshList 补充 MeSH 概念列表
/// @param alternativeAbstracts 翻译摘要列表
/// @param dates 日期列表
/// @param investigators 研究者列表
/// @param personalNameSubjects 人物主题列表
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationCompleteData(
    PublicationAggregate publication,
    PublicationMetadata metadata,
    List<PublicationMeshHeading> meshHeadings,
    List<PublicationKeyword> keywords,
    List<PublicationFunding> funding,
    List<PublicationTypeInfo> publicationTypes,
    List<PublicationSupplMesh> supplMeshList,
    List<PublicationAlternativeAbstract> alternativeAbstracts,
    List<PublicationDate> dates,
    List<PublicationInvestigator> investigators,
    List<PublicationPersonalNameSubject> personalNameSubjects)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证聚合根不为空，并进行防御性拷贝。
  public PublicationCompleteData {
    Assert.notNull(publication, "文献聚合根不能为空");

    // 防御性拷贝：确保所有集合不可变
    meshHeadings = meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
    keywords = keywords != null ? List.copyOf(keywords) : List.of();
    funding = funding != null ? List.copyOf(funding) : List.of();
    publicationTypes = publicationTypes != null ? List.copyOf(publicationTypes) : List.of();
    supplMeshList = supplMeshList != null ? List.copyOf(supplMeshList) : List.of();
    alternativeAbstracts =
        alternativeAbstracts != null ? List.copyOf(alternativeAbstracts) : List.of();
    dates = dates != null ? List.copyOf(dates) : List.of();
    investigators = investigators != null ? List.copyOf(investigators) : List.of();
    personalNameSubjects =
        personalNameSubjects != null ? List.copyOf(personalNameSubjects) : List.of();
  }

  /// 创建仅包含聚合根的数据（无关联数据）。
  ///
  /// @param publication 文献聚合根
  /// @return 完整数据对象
  public static PublicationCompleteData ofPublication(PublicationAggregate publication) {
    return PublicationCompleteData.builder().publication(publication).build();
  }

  /// 获取文献 ID。
  ///
  /// @return 文献 ID 值（如果已分配）
  public Long getPublicationId() {
    return publication.getId() != null ? publication.getId().value() : null;
  }

  /// 是否有元数据。
  ///
  /// @return true 如果有元数据
  public boolean hasMetadata() {
    return metadata != null;
  }

  /// 是否有 MeSH 标引。
  ///
  /// @return true 如果有 MeSH 标引
  public boolean hasMeshHeadings() {
    return !meshHeadings.isEmpty();
  }

  /// 是否有关键词。
  ///
  /// @return true 如果有关键词
  public boolean hasKeywords() {
    return !keywords.isEmpty();
  }

  /// 是否有资助信息。
  ///
  /// @return true 如果有资助信息
  public boolean hasFunding() {
    return !funding.isEmpty();
  }

  /// 是否有出版类型。
  ///
  /// @return true 如果有出版类型
  public boolean hasPublicationTypes() {
    return !publicationTypes.isEmpty();
  }

  /// 是否有补充 MeSH 概念。
  ///
  /// @return true 如果有补充 MeSH 概念
  public boolean hasSupplMeshList() {
    return !supplMeshList.isEmpty();
  }

  /// 是否有翻译摘要。
  ///
  /// @return true 如果有翻译摘要
  public boolean hasAlternativeAbstracts() {
    return !alternativeAbstracts.isEmpty();
  }

  /// 是否有日期信息。
  ///
  /// @return true 如果有日期信息
  public boolean hasDates() {
    return !dates.isEmpty();
  }

  /// 是否有研究者。
  ///
  /// @return true 如果有研究者
  public boolean hasInvestigators() {
    return !investigators.isEmpty();
  }

  /// 是否有人物主题。
  ///
  /// @return true 如果有人物主题
  public boolean hasPersonalNameSubjects() {
    return !personalNameSubjects.isEmpty();
  }

  /// 判断是否有任何关联数据。
  ///
  /// @return true 如果有任何关联数据
  public boolean hasAnyAssociations() {
    return hasMetadata()
        || hasMeshHeadings()
        || hasKeywords()
        || hasFunding()
        || hasPublicationTypes()
        || hasSupplMeshList()
        || hasAlternativeAbstracts()
        || hasDates()
        || hasInvestigators()
        || hasPersonalNameSubjects();
  }
}
