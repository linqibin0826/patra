package dev.linqibin.patra.catalog.infra.batch.publication;

import dev.linqibin.patra.catalog.domain.model.enums.DatePrecision;
import dev.linqibin.patra.catalog.domain.model.enums.PublicationDateType;
import dev.linqibin.patra.catalog.domain.model.enums.TranslationType;
import dev.linqibin.patra.catalog.domain.model.vo.publication.MeshQualifier;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationAlternativeAbstract;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationFunding;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationInvestigator;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationKeyword;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationMeshHeading;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationPersonalNameSubject;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationSupplMesh;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationTypeInfo;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.AlternativeAbstractData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.FundingData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.InvestigatorData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.KeywordData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.MeshHeadingData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.PersonalNameSubjectData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.PublicationDateData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.PublicationTypeData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.QualifierData;
import dev.linqibin.patra.catalog.infra.batch.publication.PublicationImportResult.SupplMeshData;
import java.util.List;
import org.springframework.stereotype.Component;

/// 文献导入结果映射器。
///
/// 负责将 Infra 层的 Data 类型转换为 Domain 层的值对象类型。
///
/// **设计说明**：
///
/// 这是一个适配器组件，位于 Infra 层，实现数据格式转换：
/// - **输入**：`PublicationImportResult`（Infra 层，Processor 输出）
/// - **输出**：`PublicationCompleteData`（Domain 层，Repository 输入）
///
/// **为什么需要这个 Mapper**：
///
/// 1. **层间解耦**：Processor 输出的 Data 类型是 Infra 层内部结构，
///    Repository 需要 Domain 层的值对象类型
/// 2. **单一职责**：转换逻辑集中管理，Writer 只负责编排
/// 3. **可测试性**：转换逻辑可独立单元测试
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PublicationImportResultMapper {

  /// 将 PublicationImportResult 转换为 PublicationCompleteData。
  ///
  /// @param result Processor 处理结果
  /// @return Domain 层完整数据对象
  public PublicationCompleteData toCompleteData(PublicationImportResult result) {
    return PublicationCompleteData.builder()
        .publication(result.publication())
        .metadata(result.metadata())
        .meshHeadings(toMeshHeadings(result.meshHeadings()))
        .keywords(toKeywords(result.keywords()))
        .funding(toFunding(result.funding()))
        .publicationTypes(toPublicationTypes(result.publicationTypes()))
        .supplMeshList(toSupplMeshList(result.supplMeshNames()))
        .alternativeAbstracts(toAlternativeAbstracts(result.alternativeAbstracts()))
        .dates(toDates(result.dates()))
        .investigators(toInvestigators(result.investigators()))
        .personalNameSubjects(toPersonalNameSubjects(result.personalNameSubjects()))
        .build();
  }

  // ==================== MeSH 转换 ====================

  /// 转换 MeSH 标引列表。
  private List<PublicationMeshHeading> toMeshHeadings(List<MeshHeadingData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toMeshHeading).toList();
  }

  /// 转换单个 MeSH 标引。
  private PublicationMeshHeading toMeshHeading(MeshHeadingData data) {
    return PublicationMeshHeading.of(
        data.descriptorUi(),
        data.majorTopic(),
        data.headingOrder(),
        toQualifiers(data.qualifiers()));
  }

  /// 转换限定词列表。
  private List<MeshQualifier> toQualifiers(List<QualifierData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toQualifier).toList();
  }

  /// 转换单个限定词。
  private MeshQualifier toQualifier(QualifierData data) {
    return MeshQualifier.of(data.qualifierUi(), data.majorTopic(), data.qualifierOrder());
  }

  // ==================== 关键词转换 ====================

  /// 转换关键词列表。
  private List<PublicationKeyword> toKeywords(List<KeywordData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toKeyword).toList();
  }

  /// 转换单个关键词。
  private PublicationKeyword toKeyword(KeywordData data) {
    return PublicationKeyword.of(
        data.source(), data.term(), data.majorTopic(), data.keywordOrder());
  }

  // ==================== 资助信息转换 ====================

  /// 转换资助信息列表。
  private List<PublicationFunding> toFunding(List<FundingData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toFunding).toList();
  }

  /// 转换单个资助信息。
  private PublicationFunding toFunding(FundingData data) {
    return PublicationFunding.builder()
        .organizationId(data.organizationId())
        .grantId(data.grantId())
        .funderNameRaw(data.funderNameRaw())
        .funderAcronymRaw(data.funderAcronymRaw())
        .funderIdentifierRaw(data.funderIdentifierRaw())
        .countryRaw(data.countryRaw())
        .fundingOrder(data.fundingOrder())
        .provenanceCode(data.provenanceCode())
        .build();
  }

  // ==================== 出版类型转换 ====================

  /// 转换出版类型列表。
  private List<PublicationTypeInfo> toPublicationTypes(List<PublicationTypeData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toPublicationType).toList();
  }

  /// 转换单个出版类型。
  private PublicationTypeInfo toPublicationType(PublicationTypeData data) {
    return PublicationTypeInfo.of(
        data.typeId(), data.typeValue(), data.vocabularySource(), data.typeOrder());
  }

  // ==================== 补充 MeSH 转换 ====================

  /// 转换补充 MeSH 列表。
  private List<PublicationSupplMesh> toSupplMeshList(List<SupplMeshData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toSupplMesh).toList();
  }

  /// 转换单个补充 MeSH。
  private PublicationSupplMesh toSupplMesh(SupplMeshData data) {
    return PublicationSupplMesh.of(data.scrUi(), data.supplOrder());
  }

  // ==================== 翻译摘要转换 ====================

  /// 转换翻译摘要列表。
  private List<PublicationAlternativeAbstract> toAlternativeAbstracts(
      List<AlternativeAbstractData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toAlternativeAbstract).toList();
  }

  /// 转换单个翻译摘要。
  ///
  /// 翻译类型映射规则：
  /// - Publisher → OFFICIAL（官方翻译）
  /// - plain-language-summary → PROFESSIONAL（专业翻译）
  /// - AIMSHP/KIEML/NASA 等 → PROFESSIONAL（专业机构翻译）
  /// - 其他 → OFFICIAL（默认）
  private PublicationAlternativeAbstract toAlternativeAbstract(AlternativeAbstractData data) {
    TranslationType translationType = mapAbstractTypeToTranslationType(data.abstractType());
    boolean isOfficial = "Publisher".equalsIgnoreCase(data.abstractType());

    return PublicationAlternativeAbstract.builder()
        .languageCode(data.languageCode())
        .sourceType(data.abstractType())
        .translationType(translationType)
        .plainText(data.plainText())
        .isOfficial(isOfficial)
        .orderNum(data.abstractOrder())
        .build();
  }

  /// 将 PubMed OtherAbstract Type 映射为 TranslationType 枚举。
  private TranslationType mapAbstractTypeToTranslationType(String abstractType) {
    if (abstractType == null || abstractType.isBlank()) {
      return TranslationType.OFFICIAL;
    }

    String normalizedType = abstractType.trim().toLowerCase();

    if ("publisher".equals(normalizedType)) {
      return TranslationType.OFFICIAL;
    }

    if (normalizedType.contains("plain-language")) {
      return TranslationType.PROFESSIONAL;
    }

    if ("aimshp".equals(normalizedType)
        || "kieml".equals(normalizedType)
        || "nasa".equals(normalizedType)) {
      return TranslationType.PROFESSIONAL;
    }

    return TranslationType.OFFICIAL;
  }

  // ==================== 日期转换 ====================

  /// 转换日期列表。
  private List<PublicationDate> toDates(List<PublicationDateData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toDate).toList();
  }

  /// 转换单个日期。
  private PublicationDate toDate(PublicationDateData data) {
    return PublicationDate.builder()
        .dateType(parsePublicationDateType(data.dateType()))
        .year(data.year())
        .month(data.month())
        .day(data.day())
        .datePrecision(parseDatePrecision(data.datePrecision()))
        .season(data.season())
        .dateString(data.dateString())
        .isPrimary(data.isPrimary())
        .orderNum(data.orderNum())
        .build();
  }

  /// 解析日期类型枚举。
  private PublicationDateType parsePublicationDateType(String dateType) {
    if (dateType == null || dateType.isBlank()) {
      return PublicationDateType.OTHER;
    }
    try {
      return PublicationDateType.fromCode(dateType);
    } catch (IllegalArgumentException e) {
      return PublicationDateType.OTHER;
    }
  }

  /// 解析日期精度枚举。
  private DatePrecision parseDatePrecision(String precision) {
    if (precision == null || precision.isBlank()) {
      return DatePrecision.YEAR;
    }
    try {
      return DatePrecision.fromCode(precision);
    } catch (IllegalArgumentException e) {
      return DatePrecision.YEAR;
    }
  }

  // ==================== 研究者转换 ====================

  /// 转换研究者列表。
  private List<PublicationInvestigator> toInvestigators(List<InvestigatorData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toInvestigator).toList();
  }

  /// 转换单个研究者。
  private PublicationInvestigator toInvestigator(InvestigatorData data) {
    return PublicationInvestigator.builder()
        .lastName(data.lastName())
        .foreName(data.foreName())
        .initials(data.initials())
        .suffix(data.suffix())
        .orcid(data.orcid())
        .affiliationName(data.affiliationName())
        .dedupKey(data.dedupKey())
        .orderNum(data.orderNum())
        .build();
  }

  // ==================== 人物主题转换 ====================

  /// 转换人物主题列表。
  private List<PublicationPersonalNameSubject> toPersonalNameSubjects(
      List<PersonalNameSubjectData> dataList) {
    if (dataList == null || dataList.isEmpty()) {
      return List.of();
    }
    return dataList.stream().map(this::toPersonalNameSubject).toList();
  }

  /// 转换单个人物主题。
  private PublicationPersonalNameSubject toPersonalNameSubject(PersonalNameSubjectData data) {
    return PublicationPersonalNameSubject.builder()
        .lastName(data.lastName())
        .foreName(data.foreName())
        .initials(data.initials())
        .suffix(data.suffix())
        .dates(data.dates())
        .description(data.description())
        .subjectType(data.subjectType())
        .identifier(data.identifier())
        .orderNum(data.orderNum())
        .build();
  }
}
